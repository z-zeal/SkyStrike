#!/usr/bin/env python3
"""Static checks for the Phase 9 audio catalogue.

There is no JDK in this sandbox, so nothing here plays a sound. What it can do is catch the
class of mistake that only shows up the first time a grenade goes off — a catalogue entry
pointing at an asset that does not exist, a duration that disagrees with the shipped WAV, an
effect type with no entry, or a manifest that lists files nobody shipped. Every one of those is
a runtime failure in a phase that cannot be run here, so they are checked against the real files
on disk instead.

What it verifies:

  1. Every row in `EffectSoundTable` names a WAV that exists, in the project's audio format
     (22050 Hz, mono, 16-bit), and declares a duration that matches the file.
  2. Every `EffectType` constant appears in the catalogue's exhaustive switch — no effect is
     silently missing a decision.
  3. `SoundCatalog`'s looping entries (the stun ring) point at real files too.
  4. The gun catalogue's dynamic rows declare durations that correspond to a real packaged
     recording, since those rows share one asset path per event across thirteen weapons.
  5. Both manifests (`guns-sfx.json`, `world-sfx.json`) list only files that exist, and every
     packaged WAV is listed by one of them — no orphans in either direction.

Usage:
    python3 tools/scratch/static_audio_check.py
Exit code 0 = no findings.
"""

from __future__ import annotations

import json
import pathlib
import re
import struct
import sys
import wave

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "assets"
EFFECT_TABLE = ROOT / "shared/src/main/java/io/github/skystrike/shared/audio/EffectSoundTable.java"
SOUND_CATALOG = ROOT / "core/src/main/java/io/github/skystrike/audio/SoundCatalog.java"
EFFECT_TYPE = ROOT / "shared/src/main/java/io/github/skystrike/shared/effect/EffectType.java"
GUN_MANIFEST = ASSETS / "sfx/guns-sfx.json"
WORLD_MANIFEST = ASSETS / "sfx/world-sfx.json"

EXPECTED_RATE = 22050
EXPECTED_CHANNELS = 1
EXPECTED_WIDTH = 2
DURATION_TOLERANCE_SECONDS = 0.05
GUN_DURATION_TOLERANCE_SECONDS = 0.12

findings: list[str] = []


def add(message: str) -> None:
    findings.append(message)


def wav_info(path: pathlib.Path) -> tuple[int, int, int, float]:
    with wave.open(str(path), "rb") as handle:
        rate = handle.getframerate()
        channels = handle.getnchannels()
        width = handle.getsampwidth()
        frames = handle.getnframes()
        raw = handle.readframes(frames)
    samples = struct.unpack(f"<{len(raw) // 2}h", raw)
    return rate, channels, width, len(samples) / rate


def check_asset(path_text: str, expected_duration: float | None, where: str,
                tolerance: float = DURATION_TOLERANCE_SECONDS) -> None:
    path = ASSETS / path_text
    if not path.exists():
        add(f"{where}: {path_text} does not exist")
        return
    rate, channels, width, duration = wav_info(path)
    if (rate, channels, width) != (EXPECTED_RATE, EXPECTED_CHANNELS, EXPECTED_WIDTH):
        add(f"{where}: {path_text} is {rate}Hz/{channels}ch/{width * 8}bit, "
            f"expected {EXPECTED_RATE}Hz/{EXPECTED_CHANNELS}ch/{EXPECTED_WIDTH * 8}bit")
    if expected_duration is not None and abs(duration - expected_duration) > tolerance:
        add(f"{where}: {path_text} is {duration:.3f}s on disk but the catalogue declares "
            f"{expected_duration:.2f}s")


ROW = re.compile(r"SoundSpec\.oneShot\(\s*(\"[^\"]+\"|[A-Za-z_][\w.]*)\s*,([^;]*?)\);", re.S)
LOOP = re.compile(r"SoundSpec\.looping\(\s*\"([^\"]+)\"[^;]*?\);", re.S)
LITERAL = re.compile(r"^\"([^\"]+)\"$")
CASE = re.compile(r"case\s+([A-Z][A-Z0-9_]*)\s*->")
ENUM_CONSTANT = re.compile(r"^\s{4}([A-Z][A-Z0-9_]*)\(", re.M)


# Argument order of SoundSpec.oneShot: bus, gain, jitter, reference, radius, duration, voices,
# priority. Everything except the bus and the priority literal is a number with an "f" suffix.
BUS = re.compile(r"^AudioBus\.(EFFECTS|MUSIC|UI)$")
PRIORITY = re.compile(r"^SoundPriority\.[A-Z]+$")


def numeric(token: str) -> float | None:
    token = token.strip()
    if token.endswith("f"):
        token = token[:-1]
    try:
        return float(token)
    except ValueError:
        return None


def parse_rows(text: str, where: str) -> list[dict]:
    rows = []
    for match in ROW.finditer(text):
        path_text, remainder = match.group(1), match.group(2)
        literal = LITERAL.match(path_text)
        tokens = [token.strip() for token in remainder.split(",")]
        if len(tokens) != 8:
            add(f"{where}: row for {path_text} has {len(tokens)} arguments after the path, "
                f"expected 8 (bus, gain, jitter, reference, radius, duration, voices, priority)")
            continue
        bus, priority = tokens[0], tokens[7]
        if not BUS.match(bus):
            add(f"{where}: row for {path_text} has no mix bus ({bus})")
        if not PRIORITY.match(priority):
            add(f"{where}: row for {path_text} has no priority ({priority})")
        numbers = [numeric(token) for token in tokens[1:7]]
        if any(number is None for number in numbers):
            add(f"{where}: row for {path_text} has a non-numeric playback argument: "
                f"{tokens[1:7]}")
            continue
        rows.append({
            "path": literal.group(1) if literal else None,
            "label": path_text,
            "bus": bus,
            "priority": priority,
            "gain": numbers[0],
            "jitter": numbers[1],
            "reference": numbers[2],
            "radius": numbers[3],
            "duration": numbers[4],
            "voices": numbers[5],
        })
    return rows


def main() -> int:
    if not EFFECT_TABLE.exists() or not SOUND_CATALOG.exists():
        print("catalogue sources are missing; run from the repository root")
        return 1

    table_text = EFFECT_TABLE.read_text(encoding="utf-8")
    catalog_text = SOUND_CATALOG.read_text(encoding="utf-8")

    # 1. World catalogue rows: real file, real format, honest duration.
    rows = parse_rows(table_text, "EffectSoundTable")
    if len(rows) < 12:
        add(f"EffectSoundTable: found {len(rows)} rows, expected the whole effect catalogue — "
            f"the row shape the checker parses may have changed")
    for row in rows:
        path = row["path"]
        if path is None:
            add(f"EffectSoundTable: row {row['label']} is not a literal path")
            continue
        if not (0.0 < row["gain"] <= 1.0):
            add(f"EffectSoundTable: {path} has gain {row['gain']}")
        if not (0.0 <= row["jitter"] < 1.0):
            add(f"EffectSoundTable: {path} has pitch jitter {row['jitter']}")
        if not (0.0 < row["reference"] < row["radius"]):
            add(f"EffectSoundTable: {path} has reference {row['reference']} "
                f"outside radius {row['radius']}")
        if row["voices"] < 1:
            add(f"EffectSoundTable: {path} can never hold a voice")
        check_asset(path, row["duration"], "EffectSoundTable")

    # 2. Every effect type has a case in the exhaustive switch.
    effect_constants = ENUM_CONSTANT.findall(EFFECT_TYPE.read_text(encoding="utf-8"))
    cases = set(CASE.findall(table_text))
    if not effect_constants:
        add("EffectType: no enum constants found; the checker's parse may have changed")
    for constant in effect_constants:
        if constant not in cases:
            add(f"EffectSoundTable: no switch case for EffectType.{constant}")

    # 3. Looping entries (the stun ring).
    loops = LOOP.findall(catalog_text)
    if not loops:
        add("SoundCatalog: no looping entry found; the stun ring must be in the catalogue")
    for path in loops:
        check_asset(path, None, "SoundCatalog")

    # 4. Weapon rows share one asset per event, so their duration must match a real recording.
    gun_durations = []
    for gun in sorted((ASSETS / "sfx/guns").rglob("*.wav")):
        gun_durations.append((gun, wav_info(gun)[3]))
    gun_rows = parse_rows(catalog_text, "SoundCatalog")
    if len(gun_rows) < 6:
        add(f"SoundCatalog: found {len(gun_rows)} weapon rows, expected one per event")
    for row in gun_rows:
        declared = row["duration"]
        if not any(abs(declared - duration) <= GUN_DURATION_TOLERANCE_SECONDS
                   for _, duration in gun_durations):
            add(f"SoundCatalog: no packaged gun recording is {declared:.2f}s "
                f"(row {row['label']})")

    # 5. Manifests describe the shipped files, and nothing is shipped unlisted.
    listed: set[str] = set()
    for manifest_path in (GUN_MANIFEST, WORLD_MANIFEST):
        if not manifest_path.exists():
            add(f"missing manifest {manifest_path.relative_to(ROOT)}")
            continue
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        entries = []
        entries.extend(manifest.get("soundVariants", {}).values())
        entries.extend(manifest.get("fireVariants", {}).values())
        entries.extend(manifest.get("shared", {}).values())
        entries.extend(manifest.get("sounds", {}).values())
        entries.extend(manifest.get("loops", {}).values())
        for entry in entries:
            if isinstance(entry, dict):
                listed.add(entry["path"])
                check_asset(entry["path"], entry.get("durationSeconds"), manifest_path.name,
                            tolerance=GUN_DURATION_TOLERANCE_SECONDS)
            else:
                listed.add(entry)
                check_asset(entry, None, manifest_path.name)
    for gun in sorted((ASSETS / "sfx").rglob("*.wav")):
        relative = gun.relative_to(ASSETS).as_posix()
        if relative not in listed:
            add(f"{relative} is shipped but no manifest lists it")

    if findings:
        print(f"{len(findings)} finding(s):")
        for finding in findings:
            print(f"  - {finding}")
        return 1
    print(
        f"audio catalogue: {len(rows)} world entries, {len(gun_rows)} weapon events, "
        f"{len(loops)} loop(s), {len(effect_constants)} effect types, "
        f"{len(listed)} manifest entries — all paths, formats and durations agree")
    return 0


if __name__ == "__main__":
    sys.exit(main())
