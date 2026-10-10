package io.github.skystrike.audio;

import io.github.skystrike.shared.audio.SoundSpec;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.effect.EffectType;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.WeaponItem;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client presentation bridge for authoritative gun state and fire-cue snapshots (M7), now a
 * client of the shared mixer rather than an owner of its own (Phase 9).
 *
 * <p>It never creates or authorises gameplay. Fire sounds follow the server's authoritative
 * successful-volley cues carried with each snapshot, so a short-lived projectile or hidden muzzle
 * flash cannot drop or delay a report. Reload, equip and ADS sounds come from player-state
 * transitions. The one local prediction hook is the dry-fire click, which has no gameplay effect.
 *
 * <p><b>Why fire cues travel with snapshots.</b> The visual muzzle-flash event is vision-culled and
 * intentionally silent here; the separate sound cue keeps its weapon identity and is spatialised
 * with the catalogue's range/occlusion policy. That keeps audio aligned to an actual server fire
 * tick without exposing hidden visual effects.
 *
 * <p><b>Voice sharing.</b> The mixer, its buses and its voice pool are borrowed, never owned: this
 * class is disposed when the match ends, and the mixer outlives it because the screen owns it.
 */
public final class GunAudio {

    private static final int MAX_SEEN_GUNFIRE_EVENTS = 2048;

    private record ObservedGun(WeaponId weapon, boolean reloading, boolean ads) {
    }

    private final AudioSystem audio;
    private final SoundCatalog catalog;
    private final OcclusionTest occlusion;
    private final Map<Integer, ObservedGun> observedPlayers = new HashMap<>();
    private final Set<Integer> seenGunfireSeeds = new LinkedHashSet<>();
    private boolean initialSnapshotReceived;

    public GunAudio(AudioSystem audio, SoundCatalog catalog, OcclusionTest occlusion) {
        if (audio == null || catalog == null) {
            throw new IllegalArgumentException("audio system and catalogue are required");
        }
        this.audio = audio;
        this.catalog = catalog;
        this.occlusion = occlusion == null ? OcclusionTest.NONE : occlusion;
    }

    /**
     * Consumes a server snapshot on the render thread. The snapshot is already the client's
     * visibility boundary, so no client-authored entity or sound event is trusted here. The
     * listener position is the mixer's, refreshed once per frame by the screen.
     */
    public void onSnapshot(PacketGameState snapshot) {
        if (snapshot == null || snapshot.players == null) {
            return;
        }

        Map<Integer, Player> players = new HashMap<>();
        for (Player player : snapshot.players) {
            if (player != null) {
                players.put(player.id, player);
            }
        }

        if (!initialSnapshotReceived) {
            rememberPlayers(snapshot.players);
            initialSnapshotReceived = true;
            playGunfire(snapshot.gunfireEvents);
            return;
        }

        for (Player player : snapshot.players) {
            if (player == null) {
                continue;
            }
            WeaponId currentWeapon = gunId(player.weaponId);
            boolean reloading = player.loadout != null && player.loadout.reloading;
            ObservedGun previous = observedPlayers.get(player.id);
            if (previous != null) {
                if (currentWeapon != null && !currentWeapon.equals(previous.weapon())) {
                    playAt(GunSoundEvent.EQUIP, currentWeapon, player, player.id * 31 + 1);
                }
                if (currentWeapon != null && reloading && !previous.reloading()) {
                    playAt(GunSoundEvent.RELOAD, currentWeapon, player, player.id * 31 + 2);
                }
                if (currentWeapon != null && player.ads != previous.ads()) {
                    playAt(
                        player.ads ? GunSoundEvent.ADS_IN : GunSoundEvent.ADS_OUT,
                        currentWeapon,
                        player,
                        player.id * 31 + 3);
                }
            }
            observedPlayers.put(
                player.id, new ObservedGun(currentWeapon, reloading, player.ads));
        }
        observedPlayers.keySet().retainAll(players.keySet());

        playGunfire(snapshot.gunfireEvents);
    }

    /** Plays one report (and any applicable cycle) per authoritative successful volley. */
    private void playGunfire(List<EffectSpawn> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (EffectSpawn event : events) {
            if (event == null || event.type != EffectType.MUZZLE_FLASH) {
                continue;
            }
            WeaponId weapon = gunId(event.weaponId);
            if (weapon == null || !seenGunfireSeeds.add(event.seed)) {
                continue;
            }
            while (seenGunfireSeeds.size() > MAX_SEEN_GUNFIRE_EVENTS) {
                seenGunfireSeeds.remove(seenGunfireSeeds.iterator().next());
            }
            playAt(GunSoundEvent.FIRE, weapon, event.x, event.y, event.seed, true);
            if (catalog.hasCycle(weapon)) {
                playAt(GunSoundEvent.CYCLE, weapon, event.x, event.y, event.seed * 31 + 5);
            }
        }
    }

    /**
     * The only local prediction sound: clicking an empty gun on a fresh trigger edge. It does not
     * predict fire, reload, ammunition or authority; successful fire remains snapshot-driven.
     *
     * <p>Your own gun is head-locked, so it skips spatialisation entirely: it cannot be behind a
     * wall from its owner, and a one-frame listener lag must never muffle the click that tells you
     * the magazine is empty.
     */
    public void onLocalInput(PacketPlayerInput input, Player predicted, boolean firePressedEdge) {
        if (input == null
            || predicted == null
            || !input.fire
            || !firePressedEdge
            || predicted.loadout == null) {
            return;
        }
        WeaponItem item = predicted.loadout.activeItem();
        if (item == null || item.magazine > 0 || predicted.loadout.reloading) {
            return;
        }
        WeaponId weapon = item.weaponId();
        if (weapon == null) {
            return;
        }
        SoundSpec spec = catalog.specFor(weapon, GunSoundEvent.EMPTY);
        if (!spec.isSilent()) {
            audio.play(spec);
        }
    }

    /** Clears session-scoped observations when the transport leaves a match. */
    public void reset() {
        observedPlayers.clear();
        seenGunfireSeeds.clear();
        initialSnapshotReceived = false;
    }

    /**
     * Ends this bridge's match-scoped state. The shared mixer is <em>not</em> disposed here: it
     * belongs to the screen, which outlives the match.
     */
    public void dispose() {
        reset();
    }

    private void playAt(GunSoundEvent event, WeaponId weapon, Player source, int seed) {
        if (source == null) {
            return;
        }
        playAt(event, weapon, source.x, source.y, seed);
    }

    private void playAt(GunSoundEvent event, WeaponId weapon, float x, float y, int seed) {
        playAt(event, weapon, x, y, seed, false);
    }

    private void playAt(
            GunSoundEvent event, WeaponId weapon, float x, float y, int seed, boolean freshAttack) {
        SoundSpec spec = catalog.specFor(weapon, event);
        if (spec.isSilent()) {
            return;
        }
        boolean blocked = occlusion.isBlocked(audio.listenerX(), audio.listenerY(), x, y);
        float pitch = spec.pitchForSeed(seed);
        if (freshAttack) {
            audio.playAtFresh(spec, x, y, blocked, pitch, 1f);
        } else {
            audio.playAt(spec, x, y, blocked, pitch, 1f);
        }
    }

    private void rememberPlayers(List<Player> players) {
        observedPlayers.clear();
        for (Player player : players) {
            if (player != null) {
                observedPlayers.put(
                    player.id,
                    new ObservedGun(
                        gunId(player.weaponId),
                        player.loadout != null && player.loadout.reloading,
                        player.ads));
            }
        }
    }

    private static WeaponId gunId(int wireId) {
        return WeaponId.isValidOrdinal(wireId) ? WeaponId.fromOrdinal(wireId) : null;
    }
}
