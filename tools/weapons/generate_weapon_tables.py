#!/usr/bin/env python3
"""Generates the shared weapon and melee tables from the sprite catalogs.

Reads  assets/sprites/guns.json   (catalog stats, reference units)
       assets/sprites/melee.json

Writes shared/src/main/java/io/github/skystrike/shared/weapons/WeaponId.java
       shared/src/main/java/io/github/skystrike/shared/weapons/WeaponRegistry.java
       shared/src/main/java/io/github/skystrike/shared/weapons/WeaponBallistics.java
       shared/src/main/java/io/github/skystrike/shared/weapons/MeleeId.java
       shared/src/main/java/io/github/skystrike/shared/weapons/MeleeRegistry.java
       docs/WEAPONS_TABLE.md

Only catalog entries with "hasSprite": true become weapons; the queued rest join the
roster when their art lands (append them at the END of the enum — ordinals cross the wire).

The catalog quotes reference-world units (meters, RPM, 0-1 scalars, m/s). The game
simulates in world units (~28 units per meter, a 50-unit player), damages against a
150 HP pool, and tunes spread in degrees and fire rate in Hz. The conversions below are
the single source of truth for that mapping; docs/MECHANICS_PLAN.md quotes them.

Running this script is idempotent: the output depends only on the catalogs and the rules.
"""

import json
import math
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
PKG_DIR = os.path.join(ROOT, "shared/src/main/java/io/github/skystrike/shared/weapons")

# ---------------------------------------------------------------------------------------
# Conversion rules (guns)
# ---------------------------------------------------------------------------------------

# The catalog is balanced around a ~100 HP pool, but its fire rates are realistic RPMs —
# far faster than the old stylised Hz table. A flat 1.5 would halve every time-to-kill, so
# the global scale is picked to keep the familiar shots-to-kill bands at those cadences
# (a mid assault rifle still drops a 150 HP player in five rounds, snipers still two-shot
# the body and one-shot the head). Pellet weapons cycle slowly and live or die on the
# one-shell kill, so their pellets scale harder: most pumps and breaks one-shell at contact,
# the fast autoloaders deliberately do not.
DAMAGE_SCALE = 1.15
PELLET_DAMAGE_SCALE = 1.3

# A burst weapon's catalog RPM is its in-burst mechanical rate. Trigger events per second
# divide by the rounds per burst and the duty cycle of the inter-burst pause.
BURST_ROUNDS = 3
BURST_DUTY_CYCLE = 0.55

# Falloff range: 14 units per meter up to 80 m, compressed to 7 u/m beyond, so the long
# snipers tier without quoting ranges wider than the arena (3000 x 2000 units).
RANGE_UNITS_PER_METER = 14.0
RANGE_KNEE_METERS = 80.0
RANGE_UNITS_PER_METER_PAST_KNEE = 7.0

# Long-gun muzzle speed: 2 units per m/s, floored so nothing crawls and capped at the old
# AWP pace. Sidearms need a separate arena-scale mapping: applying the long-gun conversion to
# their realistic catalog velocities made the slowest rounds only 1.7x faster than a sprinting
# player and slower than the vertical movement cap. The offset mapping preserves differences
# between cartridges while enforcing a 1200 u/s floor (4x run speed, 1.2x every player speed).
MUZZLE_UNITS_PER_MPS = 2.0
MUZZLE_MIN = 520.0
MUZZLE_MAX = 1950.0
SIDEARM_MUZZLE_UNITS_PER_MPS = 1.5
SIDEARM_MUZZLE_OFFSET = 800.0
SIDEARM_MUZZLE_MIN = 1200.0
SIDEARM_MUZZLE_MAX = 1550.0

# Drag per 60 Hz tick, fitted through (370 m/s -> 0.980) and (910 m/s -> 0.998). The fit is
# appropriate for long guns but bled low-velocity handgun rounds to player speed before their
# effective range. Sidearms retain at least 0.995 per tick, keeping even the slowest above the
# player movement cap throughout ordinary combat range without changing range/falloff.
def drag_per_tick(mv, cls):
    drag = clamp(0.980 + 3.3e-5 * (mv - 370.0), 0.980, 0.998)
    return max(drag, 0.995) if cls in ("pistol", "revolver") else drag

# Hip spread: the catalog 0-1 scalar maps to degrees; 0.58 is the widest short gospel at
# ~18.6 degrees, matching the old table's widest pump gun.
SPREAD_DEGREES_PER_UNIT = 32.0

FIRE_MODES = {
    "semi": "SEMI", "auto": "AUTO", "burst": "BURST",
    "bolt": "BOLT", "pump": "PUMP", "break": "BREAK",
}

CLASSES = {  # catalog class -> (enum, gravityWeight)
    # Sidearms use modest gravity weights as well as their dedicated speed/drag conversion.
    # Revolvers retain a little more drop than pistols without behaving like thrown explosives.
    "pistol": ("PISTOL", 1.5),
    "revolver": ("REVOLVER", 2.0),
    "smg": ("SMG", 3.5),
    "pdw": ("PDW", 3.2),
    "assault_rifle": ("ASSAULT_RIFLE", 2.5),
    "battle_rifle": ("BATTLE_RIFLE", 2.2),
    "dmr": ("DMR", 1.8),
    "sniper": ("SNIPER", 1.0),
    "shotgun": ("SHOTGUN", 5.0),
    "lmg": ("LMG", 2.4),
}

# Per-class tuning the catalog does not carry: how much ADS tightens the cone, movement
# penalties, spread ceiling/recovery, gravity ramp, falloff floor, and the reserve
# magazines a spawn carries. Values sit inside the WeaponConfig bounds.
CLASS_TUNING = {
    #            adsRatio movSpread ceiling recovery ramp  floor reserveMags
    "pistol":        (0.36, 2.2, 3.6, 5.0, 1.0, 0.55, 5),
    "revolver":      (0.38, 2.3, 3.7, 4.5, 1.0, 0.55, 5),
    "smg":           (0.42, 1.85, 2.8, 9.5, 0.8, 0.42, 5),
    "pdw":           (0.40, 1.8, 2.8, 9.0, 0.8, 0.45, 5),
    "assault_rifle": (0.30, 2.0, 3.0, 7.5, 1.2, 0.70, 4),
    "battle_rifle":  (0.26, 2.2, 3.2, 6.0, 1.3, 0.70, 4),
    "dmr":           (0.22, 2.4, 3.4, 5.5, 1.5, 0.72, 5),
    "sniper":        (0.18, 2.7, 4.0, 4.0, 1.8, 0.80, 5),
    "shotgun":       (0.52, 1.9, 2.7, 6.0, 0.5, 0.35, 8),
    "lmg":           (0.34, 2.1, 3.0, 6.5, 1.2, 0.70, 2),
}

# ---------------------------------------------------------------------------------------
# Conversion rules (melee)
# ---------------------------------------------------------------------------------------

MELEE_DAMAGE_SCALE = 2.8          # same 150 HP pool reasoning, arena cadence
MELEE_RANGE_UNITS_PER_METER = 28.0  # true meters: reach is arm + blade
MELEE_RANGE_BASE_UNITS = 44.0     # arm extension + the centre-to-centre body allowance

def melee_knockback(stun):
    # Concussive weapons shove; light slicers barely move the target. Driven purely by the
    # catalog's stun stat — weightKg is ignored for both guns and melee by design.
    return 5 * round(clamp(90.0 + 1000.0 * stun, 130.0, 400.0) / 5)

# ---------------------------------------------------------------------------------------

def clamp(v, lo, hi):
    return max(lo, min(hi, v))

def const_name(catalog_id):
    return catalog_id.replace("-", "_").upper()

def jfloat(v, places=4):
    s = f"{round(v, places):g}"
    return s + "f"

def convert_gun(g):
    cls = g["class"]
    enum_cls, _ = CLASSES[cls]
    ads_ratio, mov_spread, ceiling, recovery, ramp, floor, reserve_mags = CLASS_TUNING[cls]
    r = g["recoil"]
    mv = g["muzzleVelocity"]
    meters = g["range"]

    pellets = g["pellets"]
    damage = round(g["damage"] * (PELLET_DAMAGE_SCALE if pellets > 1 else DAMAGE_SCALE), 1)
    rate_hz = g["fireRate"] / 60.0
    if g["fireMode"] == "burst":
        rate_hz = rate_hz / BURST_ROUNDS * BURST_DUTY_CYCLE
    rate_hz = round(rate_hz, 4)
    mag = g["mag"]
    reserve = mag * reserve_mags
    reload_s = g["reload"]
    mode = FIRE_MODES[g["fireMode"]]

    base_spread = round(SPREAD_DEGREES_PER_UNIT * g["spread"], 2)
    kick = round(clamp(2.1 + 2.7 * r, 2.1, 4.8), 2)

    ads_recoil = round(clamp(0.45 + 0.42 * (1.0 - r), 0.45, 0.78), 2)
    mov_recoil = round(1.4 + 0.6 * r, 2)
    linear = round(30.0 + 250.0 * r ** 1.5)
    angular = round(0.65 * linear)
    visual = round(1.2 + 6.0 * r, 1)

    if cls in ("pistol", "revolver"):
        speed = round(clamp(
            SIDEARM_MUZZLE_UNITS_PER_MPS * mv + SIDEARM_MUZZLE_OFFSET,
            SIDEARM_MUZZLE_MIN,
            SIDEARM_MUZZLE_MAX))
    else:
        speed = round(clamp(MUZZLE_UNITS_PER_MPS * mv, MUZZLE_MIN, MUZZLE_MAX))
    drag = round(drag_per_tick(mv, cls), 4)
    max_range = round(
        RANGE_UNITS_PER_METER * min(meters, RANGE_KNEE_METERS)
        + RANGE_UNITS_PER_METER_PAST_KNEE * max(0.0, meters - RANGE_KNEE_METERS))

    return {
        "const": const_name(g["id"]), "asset": g["id"], "name": g["name"],
        "class": enum_cls, "damage": damage, "rate": rate_hz, "mag": mag,
        "reserve": reserve, "reload": reload_s, "mode": mode, "pellets": pellets,
        "spread": (base_spread, ads_ratio, mov_spread, kick, ceiling, recovery),
        "recoil": (ads_recoil, mov_recoil, float(linear), float(angular), visual),
        "ballistics": (enum_cls, float(speed), drag, ramp, float(max_range), floor),
        "caliber": g["caliber"], "desc": g["description"],
    }

def convert_melee(m):
    return {
        "const": const_name(m["id"]), "asset": m["id"], "name": m["name"],
        "damage": float(round(m["damage"] * MELEE_DAMAGE_SCALE)),
        "swings": round(m["attackSpeed"] / 60.0, 4),
        "range": float(round(MELEE_RANGE_UNITS_PER_METER * m["range"] + MELEE_RANGE_BASE_UNITS)),
        "knockback": float(melee_knockback(m["stun"])),
        "class": m["class"], "attack": m["attackType"], "hands": m["hands"],
    }

HEADER = "// GENERATED by tools/weapons/generate_weapon_tables.py from assets/sprites/%s.json.\n// Edit the catalog or the generator, then re-run it; do not hand-edit the table.\n"

def write(path, text):
    with open(path, "w") as f:
        f.write(text)
    print("wrote", os.path.relpath(path, ROOT))

def gen_weapon_id(guns):
    lines = []
    current = None
    for g in guns:
        if g["class"] != current:
            current = g["class"]
            lines.append(f"\n    // --- {current} " + "-" * max(1, 88 - len(current)))
        lines.append(f'    {g["const"]}("{g["name"]}", "{g["asset"]}"),')
    body = "\n".join(lines)
    body = body.rstrip(",") + ";"
    return f'''package io.github.skystrike.shared.weapons;

{HEADER % "guns"}
/**
 * Every gun in the game, as a stable identity both sides can name — the sprite-backed rows
 * of the {{@code assets/sprites/guns.json}} catalog (mechanics §5.1).
 *
 * <p>Only identity, the display name and the sprite asset id live here. Ballistics are in
 * {{@link WeaponBallistics}}; the rest of the weapon table (including the ammunition numbers the
 * loadout needs) is in {{@link WeaponRegistry}}, keyed off this enum.
 *
 * <p><b>Ordinals cross the wire</b> (the input packet carries a selection index), so this list is
 * append-only in exactly the way {{@code NetworkRegistration}} is: catalog entries that gain
 * sprites later join at the end, never in catalog order.
 */
public enum WeaponId {{
{body}

    /** The primary of the default loadout — a player is never defenceless. */
    public static final WeaponId DEFAULT = IRON_CARBINE;

    /** The sidearm of the default loadout. */
    public static final WeaponId DEFAULT_SIDEARM = IRON_SIDEARM;

    private final String displayName;
    private final String assetId;

    WeaponId(String displayName, String assetId) {{
        this.displayName = displayName;
        this.assetId = assetId;
    }}

    public String displayName() {{
        return displayName;
    }}

    /** The catalog id, which is also the sprite file name under {{@code sprites/guns/}}. */
    public String assetId() {{
        return assetId;
    }}

    /** Lookup by wire ordinal. Out-of-range values fall back to {{@link #DEFAULT}}. */
    public static WeaponId fromOrdinal(int ordinal) {{
        WeaponId[] ids = values();
        if (ordinal < 0 || ordinal >= ids.length) {{
            return DEFAULT;
        }}
        return ids[ordinal];
    }}

    /** True when {{@code ordinal}} names a real weapon. */
    public static boolean isValidOrdinal(int ordinal) {{
        return ordinal >= 0 && ordinal < values().length;
    }}
}}
'''

def gen_weapon_registry(guns):
    rows = []
    for g in guns:
        s = g["spread"]
        r = g["recoil"]
        rows.append(
            f'        put(table, WeaponId.{g["const"]}, {jfloat(g["damage"])}, {jfloat(g["rate"])}, '
            f'{g["mag"]}, {g["reserve"]}, {jfloat(g["reload"])}, FireMode.{g["mode"]}, {g["pellets"]},\n'
            f'            new WeaponDefinition.SpreadProfile({jfloat(s[0])}, {jfloat(s[1])}, {jfloat(s[2])}, '
            f'{jfloat(s[3])}, {jfloat(s[4])}, {jfloat(s[5])}),\n'
            f'            new WeaponDefinition.RecoilProfile({jfloat(r[0])}, {jfloat(r[1])}, {jfloat(r[2])}, '
            f'{jfloat(r[3])}, {jfloat(r[4])}));\n')
    body = "\n".join(rows)
    return f'''package io.github.skystrike.shared.weapons;

{HEADER % "guns"}
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The gun table: id → definition, for every sprite-backed gun of the catalog (mechanics §5.1).
 *
 * <p>Lookups <b>return copies</b>. A shared mutable definition handed to every player would be a
 * cross-player state leak — one player editing their weapon would edit everyone's. The
 * definitions are immutable records today, so the copy is cheap insurance, but the contract is
 * the point: callers may do what they like with what they get.
 *
 * <p>This registry is the single source of truth for per-weapon parameters. The rows are
 * generated from {{@code assets/sprites/guns.json}} by the documented unit conversions in
 * {{@code tools/weapons/generate_weapon_tables.py}}; live state — spread, cooldown, ammunition —
 * stays server-authoritative and never lives here.
 */
public final class WeaponRegistry {{

    private WeaponRegistry() {{
    }}

    /** Fire parameters for one weapon, as a fresh copy. Never null. */
    public static WeaponDefinition of(WeaponId id) {{
        WeaponDefinition definition = TABLE.get(id);
        if (definition == null) {{
            throw new IllegalArgumentException("no definition for weapon: " + id);
        }}
        return definition.copy();
    }}

    /** Fire parameters by wire ordinal, falling back to the default weapon. */
    public static WeaponDefinition ofOrdinal(int ordinal) {{
        return of(WeaponId.fromOrdinal(ordinal));
    }}

    /** The whole table, for validation and tooling. Values are copies. */
    public static Map<WeaponId, WeaponDefinition> all() {{
        Map<WeaponId, WeaponDefinition> out = new EnumMap<>(WeaponId.class);
        for (WeaponId id : WeaponId.values()) {{
            out.put(id, of(id));
        }}
        return Collections.unmodifiableMap(out);
    }}

    /**
     * Display name for any {{@code weaponId}} int on the wire: values below
     * {{@link MeleeId#WIRE_ID_BASE}} are gun ordinals, values at or above it are melee wire ids.
     */
    public static String displayNameForWireId(int wireId) {{
        if (MeleeId.isMeleeWireId(wireId)) {{
            return MeleeId.fromWireId(wireId).displayName();
        }}
        return WeaponId.fromOrdinal(wireId).displayName();
    }}

    private static final Map<WeaponId, WeaponDefinition> TABLE = buildTable();

    private static Map<WeaponId, WeaponDefinition> buildTable() {{
        Map<WeaponId, WeaponDefinition> table = new EnumMap<>(WeaponId.class);

{body}
        return Collections.unmodifiableMap(table);
    }}

    private static void put(
            Map<WeaponId, WeaponDefinition> table,
            WeaponId id,
            float damage,
            float fireRateHz,
            int magazineSize,
            int reserveAmmo,
            float reloadSeconds,
            FireMode fireMode,
            int pelletCount,
            WeaponDefinition.SpreadProfile spread,
            WeaponDefinition.RecoilProfile recoil) {{
        table.put(id, new WeaponDefinition(
            id,
            damage,
            fireRateHz,
            magazineSize,
            reserveAmmo,
            reloadSeconds,
            fireMode,
            pelletCount,
            spread,
            recoil,
            WeaponBallistics.of(id)));
    }}
}}
'''

def gen_weapon_ballistics(guns):
    rows = []
    for g in guns:
        b = g["ballistics"]
        rows.append(
            f'        table.put(WeaponId.{g["const"]},\n'
            f'            new WeaponBallistics(WeaponClass.{b[0]}, {jfloat(b[1])}, {jfloat(b[2])}, '
            f'{jfloat(b[3])}, {jfloat(b[4])}, {jfloat(b[5])}));')
    body = "\n".join(rows)
    return f'''package io.github.skystrike.shared.weapons;

{HEADER % "guns"}
import io.github.skystrike.shared.config.CombatConfig;
import java.util.EnumMap;
import java.util.Map;

/**
 * Per-weapon flight behaviour: muzzle speed, drop, drag and damage falloff (mechanics §4.2).
 *
 * <p>Shared on purpose. The server resolves hits with these numbers, and the client draws
 * tracers and (from Phase 7) a predicted point of impact with them; a second copy of the table
 * would quietly put the two pictures out of step.
 *
 * @param weaponClass    family, which fixes the relative drop weight
 * @param muzzleSpeed    units per second at the muzzle; sidearms have a dedicated 1200 floor
 * @param dragPerTick    speed retained per 60 Hz tick, 0.980 (shotguns) to 0.998 (snipers)
 * @param gravityRampSeconds time over which drop ramps in, so rounds fly flat up close
 * @param maxRange       distance at which damage reaches its floor, from the weapon table
 * @param minDamageRatio damage retained at maximum range, 0.35 (shotguns) to 0.80 (snipers)
 */
public record WeaponBallistics(
    WeaponClass weaponClass,
    float muzzleSpeed,
    float dragPerTick,
    float gravityRampSeconds,
    float maxRange,
    float minDamageRatio
) {{

    public WeaponBallistics {{
        if (muzzleSpeed <= 0f) {{
            throw new IllegalArgumentException("muzzle speed must be positive: " + muzzleSpeed);
        }}
        if (dragPerTick <= 0f || dragPerTick > 1f) {{
            throw new IllegalArgumentException("drag must be in (0, 1]: " + dragPerTick);
        }}
        if (gravityRampSeconds <= 0f) {{
            throw new IllegalArgumentException("gravity ramp must be positive: " + gravityRampSeconds);
        }}
        if (maxRange <= 0f) {{
            throw new IllegalArgumentException("max range must be positive: " + maxRange);
        }}
        if (minDamageRatio <= 0f || minDamageRatio > 1f) {{
            throw new IllegalArgumentException("damage floor must be in (0, 1]: " + minDamageRatio);
        }}
    }}

    /** Downward acceleration weight for this weapon's family (sniper 1.0 → shotgun 5.0). */
    public float gravityWeight() {{
        return weaponClass.gravityWeight();
    }}

    /** Fully ramped-in drop acceleration in units/s², always negative. */
    public float terminalGravity() {{
        return -CombatConfig.BULLET_GRAVITY_BASE * gravityWeight();
    }}

    private static final Map<WeaponId, WeaponBallistics> TABLE = buildTable();

    /** The flight profile of one weapon. Never null. */
    public static WeaponBallistics of(WeaponId id) {{
        WeaponBallistics ballistics = TABLE.get(id);
        if (ballistics == null) {{
            throw new IllegalArgumentException("no ballistics for weapon: " + id);
        }}
        return ballistics;
    }}

    /** The whole table, for validation and tooling. */
    public static Map<WeaponId, WeaponBallistics> all() {{
        return TABLE;
    }}

    private static Map<WeaponId, WeaponBallistics> buildTable() {{
        Map<WeaponId, WeaponBallistics> table = new EnumMap<>(WeaponId.class);
{body}
        return java.util.Collections.unmodifiableMap(table);
    }}
}}
'''

def gen_melee_id(melee):
    lines = [f'    {m["const"]}("{m["name"]}", "{m["asset"]}"),' for m in melee]
    body = "\n".join(lines).rstrip(",") + ";"
    return f'''package io.github.skystrike.shared.weapons;

{HEADER % "melee"}
/**
 * Every melee weapon in the game — the sprite-backed rows of the
 * {{@code assets/sprites/melee.json}} catalog (mechanics §5.2).
 *
 * <p>Melee has slot 3 to itself and can never be removed, so a player is never defenceless.
 * Only identity, the display name and the sprite asset id live here; the numbers are in
 * {{@link MeleeRegistry}}.
 *
 * <p><b>On the wire</b> a melee weapon is encoded as {{@link #WIRE_ID_BASE}} plus the ordinal, so
 * the single {{@code weaponId}} int carried by players, damage events and kill events can name
 * either family: values below the base are {{@link WeaponId}} ordinals, values at or above it are
 * melee. The encoding is part of the protocol and therefore append-only in exactly the way
 * {{@code NetworkRegistration}} is.
 */
public enum MeleeId {{

{body}

    /** Wire encoding offset: {{@code wireId = WIRE_ID_BASE + ordinal}}. Never reuse or renumber. */
    public static final int WIRE_ID_BASE = 1000;

    /** The melee weapon a player carries before any loadout choice exists. */
    public static final MeleeId DEFAULT = TRENCH_KNUCKLE;

    private final String displayName;
    private final String assetId;

    MeleeId(String displayName, String assetId) {{
        this.displayName = displayName;
        this.assetId = assetId;
    }}

    public String displayName() {{
        return displayName;
    }}

    /** The catalog id, which is also the sprite file name under {{@code sprites/melee/}}. */
    public String assetId() {{
        return assetId;
    }}

    /** The value this weapon takes in a {{@code weaponId}} field on the wire. */
    public int wireId() {{
        return WIRE_ID_BASE + ordinal();
    }}

    /** True when {{@code wireId}} encodes a melee weapon rather than a gun. */
    public static boolean isMeleeWireId(int wireId) {{
        return wireId >= WIRE_ID_BASE && wireId < WIRE_ID_BASE + values().length;
    }}

    /** Inverse of {{@link #wireId()}}. Out-of-range values fall back to {{@link #DEFAULT}}. */
    public static MeleeId fromWireId(int wireId) {{
        return fromOrdinal(wireId - WIRE_ID_BASE);
    }}

    /** Lookup by ordinal. Out-of-range values fall back to {{@link #DEFAULT}}. */
    public static MeleeId fromOrdinal(int ordinal) {{
        MeleeId[] ids = values();
        if (ordinal < 0 || ordinal >= ids.length) {{
            return DEFAULT;
        }}
        return ids[ordinal];
    }}

    /** True when {{@code ordinal}} names a real melee weapon. */
    public static boolean isValidOrdinal(int ordinal) {{
        return ordinal >= 0 && ordinal < values().length;
    }}
}}
'''

def gen_melee_registry(melee):
    width = max(len(m["const"]) for m in melee)
    rows = []
    for m in melee:
        pad = " " * (width - len(m["const"]))
        rows.append(
            f'        put(table, MeleeId.{m["const"]},{pad} {jfloat(m["damage"])}, '
            f'{jfloat(m["swings"])}, {jfloat(m["range"])}, {jfloat(m["knockback"])});')
    body = "\n".join(rows)
    return f'''package io.github.skystrike.shared.weapons;

{HEADER % "melee"}
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The melee table: id → definition, for every sprite-backed melee weapon of the catalog
 * (mechanics §5.2).
 *
 * <p>Like {{@link WeaponRegistry}}, lookups <b>return copies</b>: definitions are shared, static
 * truth; live state (swing cooldowns) belongs to whoever is swinging. The rows are generated
 * from {{@code assets/sprites/melee.json}} by the documented unit conversions in
 * {{@code tools/weapons/generate_weapon_tables.py}}.
 */
public final class MeleeRegistry {{

    private MeleeRegistry() {{
    }}

    /** Definition of one melee weapon, as a fresh copy. Never null. */
    public static MeleeDefinition of(MeleeId id) {{
        MeleeDefinition definition = TABLE.get(id);
        if (definition == null) {{
            throw new IllegalArgumentException("no definition for melee weapon: " + id);
        }}
        return definition.copy();
    }}

    /** Definition by ordinal, falling back to the default melee weapon. */
    public static MeleeDefinition ofOrdinal(int ordinal) {{
        return of(MeleeId.fromOrdinal(ordinal));
    }}

    /** The whole table, for validation and tooling. Values are copies. */
    public static Map<MeleeId, MeleeDefinition> all() {{
        Map<MeleeId, MeleeDefinition> out = new EnumMap<>(MeleeId.class);
        for (MeleeId id : MeleeId.values()) {{
            out.put(id, of(id));
        }}
        return Collections.unmodifiableMap(out);
    }}

    private static final Map<MeleeId, MeleeDefinition> TABLE = buildTable();

    private static Map<MeleeId, MeleeDefinition> buildTable() {{
        Map<MeleeId, MeleeDefinition> table = new EnumMap<>(MeleeId.class);
        //                                    damage  swings/s  range  knockback
{body}
        return Collections.unmodifiableMap(table);
    }}

    private static void put(
            Map<MeleeId, MeleeDefinition> table,
            MeleeId id,
            float damage,
            float swingsPerSecond,
            float range,
            float knockback) {{
        table.put(id, new MeleeDefinition(id, damage, swingsPerSecond, range, knockback));
    }}
}}
'''

def gen_docs(guns, melee):
    out = ["# Weapon tables (generated)", "",
           "Generated by `tools/weapons/generate_weapon_tables.py` from `assets/sprites/guns.json`",
           "and `assets/sprites/melee.json` — the conversion rules live there and are summarised in",
           "`docs/MECHANICS_PLAN.md` §5. Do not hand-edit.", "",
           "> **Validation status:** The sidearm ballistics update is written but uncompiled locally",
           "> because the authoring sandbox has no JDK; Java compilation and tests are delegated to CI.", "",
           "## Guns (" + str(len(guns)) + ")", "",
           "| Weapon | Class | Damage | Rate (/s) | Range | Mag | Reserve | Reload | Spread | Mode | Pellets | Speed |",
           "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |"]
    for g in guns:
        b = g["ballistics"]
        s = g["spread"]
        out.append(
            f'| {g["name"]} | {g["class"].lower()} | {g["damage"]:g} | {g["rate"]:g} | {b[4]:g} | '
            f'{g["mag"]} | {g["reserve"]} | {g["reload"]:g} s | {s[0]:g}° | {g["mode"].lower()} | '
            f'{g["pellets"]} | {b[1]:g} |')
    out += ["", "## Melee (" + str(len(melee)) + ")", "",
            "| Weapon | Damage | Swings/s | Range | Knockback |",
            "| --- | --- | --- | --- | --- |"]
    for m in melee:
        out.append(f'| {m["name"]} | {m["damage"]:g} | {m["swings"]:g} | {m["range"]:g} | {m["knockback"]:g} |')
    out.append("")
    return "\n".join(out)

def main():
    with open(os.path.join(ROOT, "assets/sprites/guns.json")) as f:
        catalog = json.load(f)
    with open(os.path.join(ROOT, "assets/sprites/melee.json")) as f:
        melee_catalog = json.load(f)

    guns = [convert_gun(g) for g in catalog["guns"] if g.get("hasSprite")]
    melee = [convert_melee(m) for m in melee_catalog["weapons"] if m.get("hasSprite")]

    write(os.path.join(PKG_DIR, "WeaponId.java"), gen_weapon_id(guns))
    write(os.path.join(PKG_DIR, "WeaponRegistry.java"), gen_weapon_registry(guns))
    write(os.path.join(PKG_DIR, "WeaponBallistics.java"), gen_weapon_ballistics(guns))
    write(os.path.join(PKG_DIR, "MeleeId.java"), gen_melee_id(melee))
    write(os.path.join(PKG_DIR, "MeleeRegistry.java"), gen_melee_registry(melee))
    write(os.path.join(ROOT, "docs/WEAPONS_TABLE.md"), gen_docs(guns, melee))
    print(f"{len(guns)} guns, {len(melee)} melee")

if __name__ == "__main__":
    main()
