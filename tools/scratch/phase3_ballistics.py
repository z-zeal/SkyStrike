"""Scratch verification for SkyStrike Phase 3 combat tuning.

NOT shipped code and not on any build path. This is the numeric check behind the constants in
`shared/config/CombatConfig.java`, `shared/config/WeaponConfig.java`,
`shared/weapons/WeaponBallistics.java` and `server/weapons/WeaponStats.java`.

Models reproduced here exactly as implemented in Java:

    drag      v *= drag ^ (dt * TICK_RATE_HZ)              (drag quoted per 60 Hz tick)
    gravity   a  = -BULLET_GRAVITY_BASE * classGravity * smoothstep(age / rampSeconds)
    damage    d  = base * (1 + (floor - 1) * min(1, travelled / maxRange))
    spread    s' = clamp(s + kick * recoilMul, .., ceiling); s -> target at recovery deg/s
                   (recovery x1.5 while aiming)
    deviation ~ N(0, (spread/2) / SIGMA_DIVISOR), clamped to +-spread/2

Run:  python3 tools/scratch/phase3_ballistics.py
"""
import math
import random

TICK_RATE_HZ = 60.0
BULLET_GRAVITY_BASE = 150.0      # u/s^2 per unit of class gravity (halved for the big map)
SIGMA_DIVISOR = 2.5
MOVING_ADS_SPREAD_FACTOR = 0.6
ADS_RECOVERY_MULTIPLIER = 1.5

CLASS_GRAVITY = {"sniper": 1.0, "rifle": 2.5, "smg": 3.5, "pistol": 4.0, "shotgun": 5.0}

# id, class, muzzle, drag, ramp, range, damage, floor, rate/s, spread, adsRatio,
# movingMul, kick, ceilMul, recovery
WEAPONS = [
    ("DESERT_EAGLE",  "pistol",  1250, 0.990, 1.00,  620,  60, 0.55,  1.5,  5.20, 0.36, 2.20, 4.2, 3.6, 5.0),
    ("FAMAS",         "rifle",   1350, 0.992, 1.20,  740,  25, 0.70, 10.0,  4.10, 0.32, 2.00, 2.2, 3.2, 8.0),
    ("SCAR_L",        "rifle",   1400, 0.993, 1.20,  780,  32, 0.70,  7.5,  3.80, 0.30, 2.10, 2.5, 3.2, 7.0),
    ("P90",           "smg",     1150, 0.987, 0.80,  420,  18, 0.42, 14.0,  5.50, 0.42, 1.80, 2.1, 2.8, 9.5),
    ("KAR98K",        "sniper",  1800, 0.997, 1.80, 1200,  90, 0.75, 0.75,  1.80, 0.20, 2.70, 4.4, 4.0, 4.5),
    ("AWP",           "sniper",  1950, 0.998, 1.80, 1500, 145, 0.85,  0.5,  1.20, 0.18, 2.80, 4.8, 4.0, 4.0),
    ("HK417",         "rifle",   1500, 0.995, 1.40,  950,  48, 0.72,  4.0,  2.40, 0.26, 2.30, 3.2, 3.4, 5.5),
    ("SAWED_OFF",     "shotgun",  900, 0.980, 0.50,  180, 100, 0.35,  1.2, 15.00, 0.55, 1.90, 3.8, 2.7, 6.0),
    ("BURST_RIFLE",   "rifle",   1320, 0.992, 1.10,  720,  24, 0.70,  3.2,  4.90, 0.32, 2.00, 2.4, 3.0, 6.5),
    ("ASSAULT_RIFLE", "rifle",   1300, 0.991, 1.10,  750,  30, 0.70,  6.0,  6.30, 0.34, 2.00, 2.7, 3.0, 7.5),
    ("SHOTGUN",       "shotgun",  950, 0.982, 0.55,  260,  80, 0.40,  1.2, 18.50, 0.52, 1.90, 3.6, 2.7, 6.0),
    ("SNIPER_RIFLE",  "sniper",  1700, 0.996, 1.70, 1500, 100, 0.78,  0.8,  0.75, 0.22, 2.60, 4.0, 4.0, 4.5),
    ("SMG",           "smg",     1100, 0.986, 0.80,  450,  20, 0.45,  8.0,  7.80, 0.40, 1.85, 2.3, 2.8, 9.0),
]

PLAYER_HEALTH = 150.0
PLAYER_HEIGHT = 50.0


def smoothstep(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


def fly(speed, drag, ramp, class_g, horizon, dt=1.0 / 60.0, max_time=3.0):
    """Flat shot along +X. Returns (time, drop, speed, travelled) at `horizon` of path length."""
    x = y = 0.0
    vx, vy = speed, 0.0
    age = travelled = 0.0
    while age < max_time:
        decay = drag ** (dt * TICK_RATE_HZ)
        vx *= decay
        vy *= decay
        vy -= BULLET_GRAVITY_BASE * class_g * smoothstep(age / ramp) * dt
        nx, ny = x + vx * dt, y + vy * dt
        step = math.hypot(nx - x, ny - y)
        if travelled + step >= horizon:
            f = (horizon - travelled) / step
            return age + dt * f, y + (ny - y) * f, math.hypot(vx, vy), horizon
        x, y, travelled, age = nx, ny, travelled + step, age + dt
    return age, y, math.hypot(vx, vy), travelled


def falloff(dmg, floor, travelled, rng):
    return dmg * (1.0 + (floor - 1.0) * min(1.0, travelled / rng))


def rule(title):
    print()
    print(title)
    print("-" * len(title))


rule("1. Trajectory: flat up close, arcing at range (mechanics 4.2)")
print(f"{'weapon':<15}{'drop@25%':>9}{'drop@50%':>9}{'drop@max':>9}{'cone r':>8}{'drop/cone':>10}"
      f"{'t@max':>7}{'v@max':>7}{'v kept':>8}")
for wid, cls, sp, dr, rm, rng, dmg, fl, rate, spread, *_ in WEAPONS:
    d25 = fly(sp, dr, rm, CLASS_GRAVITY[cls], rng * 0.25)[1]
    d50 = fly(sp, dr, rm, CLASS_GRAVITY[cls], rng * 0.50)[1]
    t, dmax, v, _ = fly(sp, dr, rm, CLASS_GRAVITY[cls], rng)
    cone = rng * math.tan(math.radians(spread / 2.0))
    print(f"{wid:<15}{d25:>9.2f}{d50:>9.2f}{dmax:>9.1f}{cone:>8.1f}{abs(dmax) / cone:>10.2f}"
          f"{t:>7.3f}{v:>7.0f}{100 * v / sp:>7.0f}%")
print("expect: |drop| < 1 u at half range (flat), 1.5-15 u at max range (visible arc)")

rule("2. Damage falloff to the per-weapon floor (mechanics 4.2)")
print(f"{'weapon':<15}{'muzzle':>8}{'@50%':>8}{'@max':>8}{'floor':>8}{'body shots @max':>17}")
for wid, cls, sp, dr, rm, rng, dmg, fl, *_ in WEAPONS:
    print(f"{wid:<15}{dmg:>8.0f}{falloff(dmg, fl, rng * 0.5, rng):>8.1f}"
          f"{falloff(dmg, fl, rng, rng):>8.1f}{fl:>8.2f}"
          f"{math.ceil(PLAYER_HEALTH / falloff(dmg, fl, rng, rng)):>17}")

rule("3. TTK against 150 HP (mechanics 5.1 balance intent)")
print(f"{'weapon':<15}{'body':>6}{'time':>7}{'head':>6}{'1-shot HS?':>12}{'point blank volley':>20}")
for wid, cls, sp, dr, rm, rng, dmg, fl, rate, *_ in WEAPONS:
    pellets = 6 if cls == "shotgun" else 1
    body = math.ceil(PLAYER_HEALTH / (dmg * pellets))
    head = math.ceil(PLAYER_HEALTH / (2.0 * dmg * pellets))
    print(f"{wid:<15}{body:>6}{(body - 1) / rate:>6.2f}s{head:>6}"
          f"{('yes' if 2 * dmg * pellets >= PLAYER_HEALTH else 'no'):>12}{dmg * pellets:>20.0f}")

rule("4. Spread: spray opens the cone over ~1 s, tapping does not (mechanics 4.3)")


def spread_sim(base, kick, ceil_mul, recovery, rate, seconds, ads=False, ads_ratio=1.0,
               moving=False, moving_mul=1.0, recoil_mul=1.0, dt=1.0 / 60.0):
    target = base * (ads_ratio if ads else 1.0)
    if moving:
        target *= moving_mul * (MOVING_ADS_SPREAD_FACTOR if ads else 1.0)
    ceiling = base * ceil_mul
    spread = target
    rec = recovery * (ADS_RECOVERY_MULTIPLIER if ads else 1.0)
    cooldown = 0.0
    t = 0.0
    shots = 0
    while t < seconds:
        cooldown -= dt
        if cooldown <= 0.0:
            spread = min(ceiling, spread + kick * recoil_mul)
            cooldown = 1.0 / rate
            shots += 1
        step = rec * dt
        spread += max(-step, min(step, target - spread))
        t += dt
    return spread, ceiling, target, shots


print(f"{'weapon':<15}{'base':>7}{'ceil':>7}{'spray 0.5s':>12}{'spray 1s':>10}{'spray 2s':>10}"
      f"{'tap 2/s 1s':>12}{'ADS spray 1s':>14}")
for wid, cls, sp, dr, rm, rng, dmg, fl, rate, base, ads_ratio, mv, kick, ceil_mul, rec in WEAPONS:
    s05 = spread_sim(base, kick, ceil_mul, rec, rate, 0.5)[0]
    s10 = spread_sim(base, kick, ceil_mul, rec, rate, 1.0)[0]
    s20 = spread_sim(base, kick, ceil_mul, rec, rate, 2.0)[0]
    tap = spread_sim(base, kick, ceil_mul, rec, min(rate, 2.0), 1.0)[0]
    ads = spread_sim(base, kick, ceil_mul, rec, rate, 1.0, ads=True, ads_ratio=ads_ratio,
                     recoil_mul=0.6)[0]
    print(f"{wid:<15}{base:>7.2f}{base * ceil_mul:>7.2f}{s05:>12.2f}{s10:>10.2f}{s20:>10.2f}"
          f"{tap:>12.2f}{ads:>14.2f}")
print("expect: automatics near the ceiling after ~0.5-1.0 s of spray, tapping close to base,")
print("        ADS spray clearly tighter than hip spray")

rule("5. Stance table (mechanics 4.3) for SCAR-L")
base, ads_ratio, moving_mul = 3.80, 0.30, 2.10
print(f"  still hip   {base:.2f} deg")
print(f"  still ADS   {base * ads_ratio:.2f} deg")
print(f"  moving hip  {base * moving_mul:.2f} deg")
print(f"  moving ADS  {base * ads_ratio * moving_mul * MOVING_ADS_SPREAD_FACTOR:.2f} deg")

rule("6. Gaussian deviation stays inside the cone (mechanics 4.3)")
random.seed(7)
for spread in (1.2, 3.8, 15.0):
    half = spread / 2.0
    sigma = half / SIGMA_DIVISOR
    samples = [max(-half, min(half, random.gauss(0.0, sigma))) for _ in range(200000)]
    clamped = sum(1 for s in samples if abs(abs(s) - half) < 1e-9)
    inside_half = sum(1 for s in samples if abs(s) <= half / 2.0)
    print(f"  cone {spread:>5.1f} deg  mean {sum(samples) / len(samples):+.4f}  "
          f"sigma {sigma:.3f}  within inner 50% of cone {100 * inside_half / len(samples):.1f}%  "
          f"clamped at rim {100 * clamped / len(samples):.3f}%")
print("expect: mean ~0, heavy clustering near the centre, rim clamping well under 1%")

rule("7. Burst and pellet patterns (mechanics 4.5)")
print("  3-round burst, 0.55 deg fixed spacing:", [round(-0.55 + i * 0.55, 2) for i in range(3)])
for spread, jitter, label in ((15.0, 1.5, "sawed-off hip"), (15.0, 0.8, "sawed-off ADS")):
    half = spread / 2.0
    offs = [-half + i * (spread / 5.0) for i in range(6)]
    print(f"  6 pellets across {spread:.1f} deg ({label}, +-{jitter} deg jitter): "
          + ", ".join(f"{o:+.2f}" for o in offs))

rule("8. Tunnelling: per-tick travel at 60 Hz vs the 14 u tunnel roof (mechanics 4.2)")
for wid, cls, sp, *_ in WEAPONS:
    step = sp / TICK_RATE_HZ
    print(f"  {wid:<15}{step:>6.1f} u/tick   "
          f"{'point test MISSES the roof -> sweep required' if step > 14 else 'thicker than a tick step'}")
print(f"  sweep threshold is 100 u/s = {100 / TICK_RATE_HZ:.2f} u/tick; every gun is far above it")

rule("9. Recoil channels (mechanics 4.4)")
print("  multiplier = lerp(1, adsMul, adsBlend) * (moving ? movingMul : 1) * (air ? 0.25 : 1)"
      " * (burst ? 1.20 : 1)")
for adsb, moving, air, burst in ((0, False, False, False), (1, False, False, False),
                                 (0, True, False, False), (1, True, False, False),
                                 (0, False, True, False), (0, False, False, True)):
    mul = (1.0 + (0.45 - 1.0) * adsb) * (1.7 if moving else 1.0) * (0.25 if air else 1.0) \
        * (1.20 if burst else 1.0)
    print(f"  ads={adsb} moving={int(moving)} air={int(air)} burst={int(burst)} -> x{mul:.3f}"
          f"   AWP push {240 * mul:>6.1f} u/s, spin {150 * mul:>6.1f} deg/s,"
          f" gun kick {6.0 * mul:>5.2f} deg")
print(f"  visual kick cap 35 deg decays at 120 deg/s -> {35 / 120:.3f} s to settle from the cap")
print("  firing straight down while airborne: AWP adds "
      f"{240 * 0.25:.0f} u/s of lift (jump is 420 u/s)")
