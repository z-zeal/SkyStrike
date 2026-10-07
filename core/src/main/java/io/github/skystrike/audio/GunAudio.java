package io.github.skystrike.audio;

import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.model.WeaponItem;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.settings.Settings;
import io.github.skystrike.shared.weapons.FireMode;
import io.github.skystrike.shared.weapons.WeaponDefinition;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import com.badlogic.gdx.utils.Disposable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client presentation bridge for authoritative gun state and projectile snapshots.
 *
 * <p>It never creates or authorises gameplay. A fire sound is emitted only after a projectile is
 * observed in an authoritative snapshot; reload, equip and ADS sounds come from player-state
 * transitions. The one local prediction hook is the dry-fire click, which has no gameplay effect.
 */
public final class GunAudio implements Disposable {

    private static final float PAN_DISTANCE = 900f;
    private static final float MAX_AUDIBLE_DISTANCE = 2400f;
    private static final int MAX_SEEN_PROJECTILES = 2048;

    private record ObservedGun(WeaponId weapon, boolean reloading, boolean ads) {
    }

    private record VolleyKey(int ownerId, WeaponId weapon, int ageBucket) {
    }

    private final AudioSystem audio = new AudioSystem();
    private final SoundCatalog catalog = new SoundCatalog();
    private final Map<Integer, ObservedGun> observedPlayers = new HashMap<>();
    private final Set<Integer> seenProjectileIds = new LinkedHashSet<>();
    private boolean initialSnapshotReceived;

    public GunAudio(Settings settings) {
        updateVolumes(settings);
    }

    /** Applies the existing client settings to the effects bus. */
    public void updateVolumes(Settings settings) {
        if (settings == null) {
            audio.setVolumes(1f, 1f);
        } else {
            audio.setVolumes(settings.masterVolume, settings.effectsVolume);
        }
    }

    /**
     * Consumes a server snapshot on the render thread. The snapshot is already the client's
     * visibility boundary, so no client-authored entity or sound event is trusted here.
     */
    public void onSnapshot(PacketGameState snapshot, int localPlayerId) {
        if (snapshot == null || snapshot.players == null) {
            return;
        }

        Map<Integer, Player> players = new HashMap<>();
        for (Player player : snapshot.players) {
            if (player != null) {
                players.put(player.id, player);
            }
        }
        Player listener = players.get(localPlayerId);

        if (!initialSnapshotReceived) {
            rememberPlayers(snapshot.players);
            rememberProjectiles(snapshot.projectiles);
            initialSnapshotReceived = true;
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
                    playForPlayer(GunSoundEvent.EQUIP, currentWeapon, player, listener);
                }
                if (currentWeapon != null && reloading && !previous.reloading()) {
                    playForPlayer(GunSoundEvent.RELOAD, currentWeapon, player, listener);
                }
                if (currentWeapon != null && player.ads != previous.ads()) {
                    playForPlayer(
                        player.ads ? GunSoundEvent.ADS_IN : GunSoundEvent.ADS_OUT,
                        currentWeapon,
                        player,
                        listener);
                }
            }
            observedPlayers.put(player.id, new ObservedGun(currentWeapon, reloading, player.ads));
        }
        observedPlayers.keySet().retainAll(players.keySet());

        Set<VolleyKey> playedVolleys = new HashSet<>();
        List<Projectile> projectiles = snapshot.projectiles == null ? List.of() : snapshot.projectiles;
        for (Projectile projectile : projectiles) {
            if (projectile == null || !rememberProjectile(projectile.id)) {
                continue;
            }
            WeaponId weapon = gunId(projectile.weaponId);
            if (weapon == null) {
                continue;
            }
            WeaponDefinition definition = WeaponRegistry.of(weapon);
            boolean oneSoundPerVolley = definition.firesPellets() || definition.fireMode() == FireMode.BURST;
            VolleyKey volley = new VolleyKey(
                projectile.ownerId,
                weapon,
                Math.round(projectile.age * 20f));
            if (!oneSoundPerVolley || playedVolleys.add(volley)) {
                playAt(GunSoundEvent.FIRE, weapon, projectile.x, projectile.y, listener);
                if (catalog.hasCycle(weapon)) {
                    playAt(GunSoundEvent.CYCLE, weapon, projectile.x, projectile.y, listener);
                }
            }
        }
    }

    /**
     * The only local prediction sound: clicking an empty gun on a fresh trigger edge. It does not
     * predict fire, reload, ammunition or authority; successful fire remains snapshot-driven.
     */
    public void onLocalInput(PacketPlayerInput input, Player predicted, boolean firePressedEdge) {
        if (input == null || predicted == null || !input.fire || !firePressedEdge || predicted.loadout == null) {
            return;
        }
        WeaponItem item = predicted.loadout.activeItem();
        if (item == null || item.magazine > 0 || predicted.loadout.reloading) {
            return;
        }
        WeaponId weapon = item.weaponId();
        if (weapon != null) {
            playLocal(GunSoundEvent.EMPTY, weapon);
        }
    }

    /** Clears session-scoped observations when the transport leaves a match. */
    public void reset() {
        observedPlayers.clear();
        seenProjectileIds.clear();
        initialSnapshotReceived = false;
    }

    @Override
    public void dispose() {
        audio.dispose();
        reset();
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

    private void rememberProjectiles(List<Projectile> projectiles) {
        if (projectiles == null) {
            return;
        }
        for (Projectile projectile : projectiles) {
            if (projectile != null) {
                rememberProjectile(projectile.id);
            }
        }
    }

    private boolean rememberProjectile(int id) {
        if (!seenProjectileIds.add(id)) {
            return false;
        }
        while (seenProjectileIds.size() > MAX_SEEN_PROJECTILES) {
            seenProjectileIds.remove(seenProjectileIds.iterator().next());
        }
        return true;
    }

    private void playForPlayer(GunSoundEvent event, WeaponId weapon, Player source, Player listener) {
        if (source == null) {
            return;
        }
        playAt(event, weapon, source.x, source.y, listener);
    }

    private void playLocal(GunSoundEvent event, WeaponId weapon) {
        String path = catalog.pathFor(weapon, event);
        if (path != null) {
            audio.play(path, 1f, 1f, 0f);
        }
    }

    private void playAt(
        GunSoundEvent event,
        WeaponId weapon,
        float x,
        float y,
        Player listener
    ) {
        if (listener == null) {
            return;
        }
        String path = catalog.pathFor(weapon, event);
        if (path == null) {
            return;
        }
        float dx = x - listener.x;
        float dy = y - listener.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance > MAX_AUDIBLE_DISTANCE) {
            return;
        }
        float volume = 1f / (1f + distance / PAN_DISTANCE);
        float pan = dx / PAN_DISTANCE;
        audio.play(path, volume, 1f, pan);
    }

    private static WeaponId gunId(int wireId) {
        return WeaponId.isValidOrdinal(wireId) ? WeaponId.fromOrdinal(wireId) : null;
    }
}
