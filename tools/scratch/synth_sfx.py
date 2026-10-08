#!/usr/bin/env python3
"""Synthesise SkyStrike's world sound effects.

The project has no recordings for explosions, utility effects or impacts, and shipping a
catalogue that points at files which do not exist would fail on the first grenade. So the
packaged world sounds are generated here, in-repo and deterministic, from noise-shaping
primitives — no third-party audio, nothing to attribute beyond this file.

They are placeholders in the honest sense: they are *real*, playable, correctly formatted
assets that make the whole catalogue audible end to end, and a later pass can swap any single
file for a recording without touching code — the catalogue names paths, and
`static_audio_check.py` checks them.

Format matches the existing gun pack: 22050 Hz, mono, 16-bit WAV.

Usage:
    python3 tools/scratch/synth_sfx.py            # writes assets/sfx/world + the manifest
    python3 tools/scratch/synth_sfx.py --check    # writes nothing, verifies the shipped files
"""

from __future__ import annotations

import argparse
import json
import math
import pathlib
import random
import struct
import sys
import wave

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORLD_DIR = ROOT / "assets" / "sfx" / "world"
STATUS_DIR = ROOT / "assets" / "sfx" / "status"
MANIFEST = ROOT / "assets" / "sfx" / "world-sfx.json"

SAMPLE_RATE = 22050
BIT_DEPTH = 16
CHANNELS = 1

# Deterministic by construction: every file's noise comes from a seed derived from its name.
BASE_SEED = 0x5C1F7A11


# --- signal primitives -------------------------------------------------------


def seconds(duration: float) -> int:
    return int(round(duration * SAMPLE_RATE))


def noise(rng: random.Random, count: int) -> list[float]:
    return [rng.uniform(-1.0, 1.0) for _ in range(count)]


def lowpass(signal: list[float], cutoff_hz: float) -> list[float]:
    alpha = 1.0 - math.exp(-2.0 * math.pi * cutoff_hz / SAMPLE_RATE)
    out = []
    state = 0.0
    for value in signal:
        state += alpha * (value - state)
        out.append(state)
    return out


def highpass(signal: list[float], cutoff_hz: float) -> list[float]:
    return [value - low for value, low in zip(signal, lowpass(signal, cutoff_hz))]


def bandpass(signal: list[float], low_hz: float, high_hz: float) -> list[float]:
    return lowpass(highpass(signal, low_hz), high_hz)


def envelope(count: int, attack_seconds: float, decay_seconds: float) -> list[float]:
    """Rising attack, then exponential decay. Both are seconds, so tau is readable."""
    attack = max(1, seconds(attack_seconds))
    decay = max(1e-6, decay_seconds)
    out = []
    for index in range(count):
        rise = 1.0 if index >= attack else index / attack
        out.append(rise * math.exp(-index / (decay * SAMPLE_RATE)))
    return out


def sine(count: int, frequency_hz: float, phase: float = 0.0) -> list[float]:
    step = 2.0 * math.pi * frequency_hz / SAMPLE_RATE
    return [math.sin(phase + step * index) for index in range(count)]


def sweep(count: int, start_hz: float, end_hz: float) -> list[float]:
    """Linear frequency sweep by integrating the instantaneous frequency."""
    out = []
    phase = 0.0
    for index in range(count):
        position = index / max(1, count - 1)
        phase += 2.0 * math.pi * (start_hz + (end_hz - start_hz) * position) / SAMPLE_RATE
        out.append(math.sin(phase))
    return out


def mix(*signals: list[float]) -> list[float]:
    length = max(len(signal) for signal in signals)
    out = [0.0] * length
    for signal in signals:
        for index, value in enumerate(signal):
            out[index] += value
    return out


def gain(signal: list[float], amount: float) -> list[float]:
    return [value * amount for value in signal]


def shaped(signal: list[float], shape: list[float]) -> list[float]:
    count = min(len(signal), len(shape))
    return [signal[index] * shape[index] for index in range(count)]


def add_at(target: list[float], source: list[float], offset_seconds: float) -> None:
    offset = max(0, seconds(offset_seconds))
    for index, value in enumerate(source):
        position = offset + index
        if position >= len(target):
            return
        target[position] += value


def normalise(signal: list[float], peak: float) -> list[float]:
    loudest = max((abs(value) for value in signal), default=0.0)
    if loudest <= 1e-9:
        return signal
    return [value * (peak / loudest) for value in signal]


def fade_edges(signal: list[float], milliseconds: float = 2.0) -> list[float]:
    """Ramp the first and last samples so a truncated burst cannot click."""
    count = len(signal)
    ramp = max(1, seconds(milliseconds / 1000.0))
    out = list(signal)
    for index in range(min(ramp, count)):
        factor = index / ramp
        out[index] *= factor
        out[count - 1 - index] *= factor
    return out


def fit(signal: list[float], duration_seconds: float) -> list[float]:
    """Pad or trim to the exact declared duration, so the catalogue's numbers stay honest."""
    count = seconds(duration_seconds)
    out = signal[:count]
    if len(out) < count:
        out.extend([0.0] * (count - len(out)))
    return out


def rng_for(name: str, salt: int = 0) -> random.Random:
    seed = BASE_SEED
    for character in name:
        seed = (seed * 131 + ord(character)) & 0xFFFFFFFF
    return random.Random(seed ^ (salt * 0x9E3779B1))


# --- recipes -----------------------------------------------------------------


def explosion(name: str, duration: float, body_hz: float, crack_hz: float, tau: float,
              sub_from: float, sub_to: float, crack_tau: float) -> list[float]:
    count = seconds(duration)
    rng = rng_for(name)
    body = shaped(lowpass(noise(rng, count), body_hz), envelope(count, 0.002, tau))
    crack = shaped(bandpass(noise(rng, count), crack_hz, 8000.0), envelope(count, 0.0005, crack_tau))
    rumble = shaped(lowpass(noise(rng, count), 180.0), envelope(count, 0.01, tau * 2.2))
    sub = shaped(sweep(count, sub_from, sub_to), envelope(count, 0.003, tau * 0.9))
    return fit(fade_edges(mix(body, gain(crack, 0.55), gain(rumble, 0.35), gain(sub, 0.7))), duration)


def hiss(name: str, duration: float, attack: float, tau: float, low: float, high: float,
         warble_hz: float = 0.0, warble_depth: float = 0.0) -> list[float]:
    count = seconds(duration)
    rng = rng_for(name)
    breath = shaped(bandpass(noise(rng, count), low, high), envelope(count, attack, tau))
    if warble_hz > 0.0:
        wobble = [1.0 - warble_depth + warble_depth * (0.5 + 0.5 * math.sin(
            2.0 * math.pi * warble_hz * index / SAMPLE_RATE)) for index in range(count)]
        breath = [value * wobble[index] for index, value in enumerate(breath)]
    release = shaped(lowpass(noise(rng, count, ), 220.0), envelope(count, 0.01, tau * 0.6))
    return fit(fade_edges(mix(breath, gain(release, 0.25))), duration)


def shard(duration: float, partials: tuple[float, ...], tau: float, rng: random.Random) -> list[float]:
    count = seconds(duration)
    out = [0.0] * count
    for index, frequency in enumerate(partials):
        partial = shaped(
            sine(count, frequency * rng.uniform(0.98, 1.03)),
            envelope(count, 0.0004, tau * (1.0 - 0.2 * index)))
        out = [value + partial[index2] for index2, value in enumerate(out)]
    tick = shaped(highpass(noise(rng, count), 3000.0), envelope(count, 0.0003, 0.004))
    return mix(gain(out, 0.5), gain(tick, 0.6))


def molotov_splash(duration: float) -> list[float]:
    count = seconds(duration)
    rng = rng_for("molotov-splash")
    whoosh = shaped(bandpass(noise(rng, count), 400.0, 3500.0), envelope(count, 0.01, 0.22))
    out = gain(whoosh, 0.7)
    for _ in range(18):
        offset = rng.uniform(0.0, 0.30)
        pieces = shard(0.16, (rng.uniform(2200.0, 5200.0), rng.uniform(3200.0, 6400.0)),
                       0.012, rng)
        add_at(out, gain(pieces, rng.uniform(0.25, 0.6)), offset)
    return fit(fade_edges(out), duration)


def claymore_blast(duration: float) -> list[float]:
    count = seconds(duration)
    rng = rng_for("claymore-blast")
    boom = shaped(lowpass(noise(rng, count), 750.0), envelope(count, 0.001, 0.20))
    crack = shaped(bandpass(noise(rng, count), 1500.0, 9000.0), envelope(count, 0.0004, 0.02))
    out = mix(boom, gain(crack, 0.6))
    for _ in range(26):
        offset = rng.uniform(0.02, 0.45)
        pellet = shaped(bandpass(noise(rng, seconds(0.05)), 1200.0, 7000.0),
                        envelope(seconds(0.05), 0.0004, 0.008))
        add_at(out, gain(pellet, rng.uniform(0.15, 0.45)), offset)
    return fit(fade_edges(out), duration)


def flash_detonation(duration: float) -> list[float]:
    count = seconds(duration)
    rng = rng_for("flash-detonation")
    click = shaped(noise(rng, count), envelope(count, 0.0002, 0.0015))
    burst = shaped(bandpass(noise(rng, count), 900.0, 7000.0), envelope(count, 0.0006, 0.06))
    crack = shaped(sine(count, 6200.0), envelope(count, 0.0004, 0.035))
    thump = shaped(lowpass(noise(rng, count), 300.0), envelope(count, 0.001, 0.10))
    return fit(fade_edges(mix(gain(click, 1.0), gain(burst, 0.8), gain(crack, 0.35),
                              gain(thump, 0.5))), duration)


def impact_concrete(duration: float) -> list[float]:
    count = seconds(duration)
    rng = rng_for("impact-concrete")
    thud = shaped(lowpass(noise(rng, count), 380.0), envelope(count, 0.0005, 0.045))
    dust = shaped(bandpass(noise(rng, count), 1800.0, 6500.0), envelope(count, 0.001, 0.055))
    return fit(fade_edges(mix(gain(thud, 1.0), gain(dust, 0.35))), duration)


def impact_metal(duration: float) -> list[float]:
    count = seconds(duration)
    rng = rng_for("impact-metal")
    tick = shaped(bandpass(noise(rng, count), 3000.0, 9000.0), envelope(count, 0.0003, 0.005))
    body = [0.0] * count
    for frequency, tau, level in ((1900.0, 0.09, 0.45), (2730.0, 0.07, 0.3), (4110.0, 0.05, 0.2)):
        partial = shaped(sine(count, frequency), envelope(count, 0.0004, tau))
        body = [value + partial[index] * level for index, value in enumerate(body)]
    return fit(fade_edges(mix(gain(tick, 0.7), body)), duration)


def impact_wood(duration: float) -> list[float]:
    count = seconds(duration)
    rng = rng_for("impact-wood")
    knock = shaped(bandpass(noise(rng, count), 300.0, 1900.0), envelope(count, 0.0004, 0.03))
    body = shaped(sine(count, 220.0), envelope(count, 0.001, 0.05))
    return fit(fade_edges(mix(gain(knock, 1.0), gain(body, 0.45))), duration)


def shell_eject(duration: float) -> list[float]:
    count = seconds(duration)
    rng = rng_for("shell-eject")
    out = [0.0] * count
    for offset in (0.0, 0.055):
        pieces = shard(0.22, (rng.uniform(2300.0, 2600.0), rng.uniform(3100.0, 3600.0),
                              rng.uniform(4600.0, 5200.0)), 0.010, rng)
        add_at(out, gain(pieces, 0.6), offset)
    return fit(fade_edges(out), duration)


def tinnitus_ring(duration: float) -> list[float]:
    """A seamless loop: every partial completes a whole number of cycles in the period."""
    count = seconds(duration)
    cycles = duration  # at 22050 Hz and a 2 s period, frequency * 2 s must be an integer
    partials = []
    for frequency, level in ((3968.0, 1.0), (4032.0, 0.85), (1984.0, 0.25)):
        assert abs(frequency * cycles - round(frequency * cycles)) < 1e-9
        partials.append(gain(sine(count, frequency), level))
    # A slow amplitude drift, also whole cycles in the period, so the loop point is invisible.
    drift = [1.0 - 0.06 + 0.06 * math.sin(2.0 * math.pi * index / count) for index in range(count)]
    mixed = mix(*partials)
    return normalise([value * drift[index] for index, value in enumerate(mixed)], 0.55)


# name -> (relative path, duration seconds, peak, builder). The peak is a mix decision: an
# explosion is the loudest event in the game and a casing is texture, and normalising everything to
# full scale would erase that distinction before the catalogue's gains ever applied.
SOUNDS: list[tuple[str, str, float, float, object]] = [
    ("explosion-frag", "sfx/world/explosion-frag.wav", 1.35, 0.95,
     lambda: explosion("explosion-frag", 1.35, 700.0, 2000.0, 0.30, 110.0, 32.0, 0.02)),
    ("explosion-impact", "sfx/world/explosion-impact.wav", 0.85, 0.90,
     lambda: explosion("explosion-impact", 0.85, 1100.0, 2600.0, 0.18, 90.0, 40.0, 0.015)),
    ("claymore-blast", "sfx/world/claymore-blast.wav", 0.90, 0.90,
     lambda: claymore_blast(0.90)),
    ("smoke-burst", "sfx/world/smoke-burst.wav", 1.10, 0.72,
     lambda: hiss("smoke-burst", 1.10, 0.08, 0.45, 800.0, 6500.0)),
    ("poison-burst", "sfx/world/poison-burst.wav", 1.10, 0.62,
     lambda: hiss("poison-burst", 1.10, 0.12, 0.50, 400.0, 3200.0, 9.0, 0.35)),
    ("molotov-splash", "sfx/world/molotov-splash.wav", 0.75, 0.88,
     lambda: molotov_splash(0.75)),
    ("fire-ignite", "sfx/world/fire-ignite.wav", 0.60, 0.55,
     lambda: hiss("fire-ignite", 0.60, 0.10, 0.22, 300.0, 2600.0)),
    ("flash-detonation", "sfx/world/flash-detonation.wav", 0.60, 0.92,
     lambda: flash_detonation(0.60)),
    ("impact-concrete", "sfx/world/impact-concrete.wav", 0.25, 0.62,
     lambda: impact_concrete(0.25)),
    ("impact-metal", "sfx/world/impact-metal.wav", 0.30, 0.72,
     lambda: impact_metal(0.30)),
    ("impact-wood", "sfx/world/impact-wood.wav", 0.25, 0.66,
     lambda: impact_wood(0.25)),
    ("shell-eject", "sfx/world/shell-eject.wav", 0.30, 0.55,
     lambda: shell_eject(0.30)),
]

LOOP = ("tinnitus-ring", "sfx/status/tinnitus-ring.wav", 2.00, 0.55,
        lambda: tinnitus_ring(2.00))


def read_wav(path: pathlib.Path) -> tuple[int, int, int, bytes]:
    with wave.open(str(path), "rb") as handle:
        return (handle.getframerate(), handle.getnchannels(), handle.getsampwidth(),
                handle.readframes(handle.getnframes()))


def write_wav(path: pathlib.Path, samples: list[float]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    frames = bytearray()
    for value in samples:
        clipped = max(-1.0, min(1.0, value))
        frames += struct.pack("<h", int(round(clipped * 32767.0)))
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(CHANNELS)
        handle.setsampwidth(BIT_DEPTH // 8)
        handle.setframerate(SAMPLE_RATE)
        handle.writeframes(bytes(frames))


def build() -> int:
    manifest = {
        "version": 1,
        "source": "Procedurally synthesised in-repo by tools/scratch/synth_sfx.py",
        "license": "CC0 1.0 Universal (public domain dedication) - generated, no third-party audio",
        "format": "wav",
        "sampleRate": SAMPLE_RATE,
        "channels": CHANNELS,
        "bitDepth": BIT_DEPTH,
        "root": "sfx/world",
        "note": "Placeholder-quality but real assets: every catalogue path resolves and plays. "
                "Any single file can be replaced by a recording without touching code.",
        "sounds": {},
        "loops": {},
    }

    for name, relative, duration, peak, builder in SOUNDS:
        samples = normalise(builder(), peak)
        target = ROOT / "assets" / relative
        write_wav(target, samples)
        manifest["sounds"][name] = {
            "path": relative,
            "durationSeconds": round(len(samples) / SAMPLE_RATE, 4),
        }
        print(f"wrote {relative} ({len(samples) / SAMPLE_RATE:.3f}s, {len(samples)} frames)")

    name, relative, duration, peak, builder = LOOP
    samples = normalise(builder(), peak)
    write_wav(ROOT / "assets" / relative, samples)
    manifest["loops"][name] = {
        "path": relative,
        "durationSeconds": round(len(samples) / SAMPLE_RATE, 4),
        "seamless": True,
    }
    print(f"wrote {relative} ({len(samples) / SAMPLE_RATE:.3f}s, {len(samples)} frames)")

    MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {MANIFEST.relative_to(ROOT)}")
    return 0


def check() -> int:
    """Verify the shipped files match the manifest's format, durations and loop assumption."""
    failures = []
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    entries = {**manifest["sounds"], **manifest["loops"]}
    for name, entry in sorted(entries.items()):
        path = ROOT / "assets" / entry["path"]
        if not path.exists():
            failures.append(f"{name}: missing {entry['path']}")
            continue
        rate, channels, width, frames = read_wav(path)
        samples = struct.unpack(f"<{len(frames) // 2}h", frames)
        duration = len(samples) / rate
        if (rate, channels, width) != (SAMPLE_RATE, CHANNELS, BIT_DEPTH // 8):
            failures.append(f"{name}: format {rate}Hz/{channels}ch/{width * 8}bit")
        if abs(duration - entry["durationSeconds"]) > 0.01:
            failures.append(f"{name}: {duration:.3f}s on disk, {entry['durationSeconds']}s declared")
        loudest = max((abs(value) for value in samples), default=0) / 32767.0
        if loudest > 0.98:
            failures.append(f"{name}: clips at {loudest:.3f}")
        if loudest < 0.1:
            failures.append(f"{name}: effectively silent (peak {loudest:.3f})")
        if entry.get("seamless"):
            step = max(abs(samples[index + 1] - samples[index]) for index in range(len(samples) - 1))
            seam = abs(samples[0] - samples[-1])
            if seam > step * 1.5:
                failures.append(
                    f"{name}: loop point jumps {seam} against a max step of {step}")
    if failures:
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print(f"checked {len(entries)} generated sound(s): format and durations agree")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="verify the shipped WAVs instead of writing them")
    arguments = parser.parse_args()
    return check() if arguments.check else build()


if __name__ == "__main__":
    sys.exit(main())
