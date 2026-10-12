#!/usr/bin/env python3
"""Check that `assets/data/arena.sdf` is the bake of the standard arena the source declares.

The SDF is derived data: the map is the truth, the field is a picture of it, and the field carries
a SHA-256 of the map it was baked from so a mismatch is caught rather than drawn. That guard only
works while the shipped asset keeps up with the shipped map text — and the map text lives in
`StandardArenaMap.java` while the asset is a 1.5 MB binary nobody reads in a diff. Editing one and
forgetting the other is exactly the mistake this checks for.

The bake is pure integer and float arithmetic with no JDK-only behaviour, so it can be reproduced
here: this script re-implements `MapHash`, `SdfBaker` (rasterise the kinds the rule table bakes,
then two-pass 8SSEDT) and `SdfField`'s header from the committed Java, and compares the result with
the shipped bytes field for field. `--write` regenerates the asset instead, for the case this
authoring sandbox can hit and a desktop cannot: no JDK, so `tools/.../SdfBakeTool` cannot run.

What it verifies:

  1. `StandardArenaMap.TEXT` parses as the version-1 format (`skystrike-map 1`, one `mirror`, any
     `spawn`s, then `mirrored`/`centred` block lines with the optional `durability=`, `id=` and
     `access=` attributes, parsed under the same rules the Java format enforces).
  2. The semantic map hash from the text equals the hash in the shipped asset's header — the same
     check `SdfCache` makes at load, and the one that fails in front of a player if this is wrong.
  3. Every texel of a fresh bake equals the shipped texel. Not "close": equal, because the asset is
     what the client samples and a bake is deterministic.
  4. The header's dimensions, texel scale and world size match the arena the bake came from.

This is a source-only check: it proves the shipped bytes are right, and proves nothing about how
they are used at runtime. The Java `tools/.../SdfBakeTool` remains the tool of record — regenerate
with it whenever a JDK is available, and run this to catch a commit that edited the map alone.

Usage:
    python3 tools/scratch/arena_sdf_check.py            # verify the shipped asset
    python3 tools/scratch/arena_sdf_check.py --write    # re-bake it in place
Exit code 0 = the asset is the bake of the map, with no findings.
"""

import hashlib
import math
import pathlib
import re
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAP_SOURCE = ROOT / "shared/src/main/java/io/github/skystrike/shared/map/StandardArenaMap.java"
ASSET = ROOT / "assets/data/arena.sdf"

# The shared enums, by ordinal. A kind or material added to the game is a map line this has to
# learn; the mismatch shows up as an unparsable line, not as a silently wrong hash.
KIND_ORDINALS = {"wall": 0, "glass": 1, "door": 2, "breakable": 3, "platform": 4}
MATERIAL_ORDINALS = {"stone": 0, "metal": 1, "wood": 2, "glass": 3, "energy": 4}
# BlockRules.bakesIntoSdf: the kinds that are part of the static field. Glass and platforms are
# never in it, and a door is a dynamic occluder rather than baked geometry.
BAKED_KINDS = {"wall", "breakable"}
# BlockRules.damage: the only kind whose rule row accumulates a durability pool, and therefore the
# only kind a `durability=` attribute may scale. DoorAccess ordinals, for the hash's door triple.
DURABILITY_KINDS = {"breakable"}
DOOR_ACCESS_ORDINALS = {"ALL": 0, "TEAM": 1}
INT_MAX = 2 ** 31 - 1

MAGIC = 0x53534446  # "SSDF"
VERSION = 2
MAP_HASH_BYTES = 32
HEADER_BYTES = 4 * 4 + 3 * 4 + MAP_HASH_BYTES  # int magic/version/width/height, 3 floats, hash
TEXEL_SCALE = 2.0  # VisionConfig.SDF_TEXEL_SCALE
ARENA_WIDTH = 3000.0
ARENA_HEIGHT = 2000.0
DEFAULT_DURABILITY_MULTIPLIER = 1.0
INF = 1_000_000_000


def f32(value):
    """The float32 value of `value`, which is what every Java float arithmetic step produces."""
    return struct.unpack("f", struct.pack("f", value))[0]


def float_bits(value):
    """Float.floatToIntBits, as the unsigned decimal MapHash writes."""
    return struct.unpack("I", struct.pack("f", f32(value)))[0]


def read_map_text():
    """The canonical arena text, straight out of the committed Java source."""
    source = MAP_SOURCE.read_text()
    marker = 'String.join("\\n",'
    start = source.index(marker, source.index("TEXT =")) + len(marker)
    end = source.index('");', start)
    body = source[start:end]
    return [m for m in re.findall(r'"([^"]*)"', body) if m != "\\n"]


def parse_map(lines):
    """Returns (mirror, spawns, blocks) with blocks already reflected, in the builder's order."""
    mirror = None
    spawns = []
    blocks = []

    for line in lines:
        tokens = line.split()
        if not tokens:
            continue
        if tokens[0] == "skystrike-map":
            if len(tokens) != 2 or tokens[1] != "1":
                raise ValueError("unexpected header line: " + line)
            continue
        if tokens[0] == "mirror":
            mirror = f32(float(tokens[1]))
            continue
        if tokens[0] == "spawn":
            spawns.append((int(tokens[1]), f32(float(tokens[2])), f32(float(tokens[3]))))
            continue

        verb, kind, material = tokens[0], tokens[1], tokens[2]
        if verb not in ("mirrored", "centred"):
            raise ValueError("unknown verb: " + line)
        if kind not in KIND_ORDINALS:
            raise ValueError("unknown kind: " + line)
        if material not in MATERIAL_ORDINALS:
            raise ValueError("unknown material: " + line)
        x, y, width, height = (f32(float(value)) for value in tokens[3:7])

        multiplier = DEFAULT_DURABILITY_MULTIPLIER
        saw_durability = False
        door_id = None
        access = "ALL"
        access_team = 0
        saw_access = False
        for extra in tokens[7:]:
            key, separator, value = extra.partition("=")
            key = key.lower()
            if not separator or not value:
                raise ValueError("unknown block attribute: " + line)
            if key == "durability":
                if saw_durability:
                    raise ValueError("duplicate durability attribute: " + line)
                saw_durability = True
                if kind not in DURABILITY_KINDS:
                    raise ValueError("durability is only meaningful for a kind that accumulates"
                                     " damage: " + line)
                multiplier = f32(float(value))
                if multiplier <= 0.0:
                    raise ValueError("durability multiplier must be positive: " + line)
            elif key == "id":
                if door_id is not None:
                    raise ValueError("duplicate id attribute: " + line)
                if kind != "door":
                    raise ValueError("id is only meaningful on a door: " + line)
                door_id = int(value)
                if door_id <= 0 or door_id > INT_MAX:
                    raise ValueError("door id must be a positive integer: " + line)
            elif key == "access":
                if saw_access:
                    raise ValueError("duplicate access attribute: " + line)
                saw_access = True
                if kind != "door":
                    raise ValueError("access is only meaningful on a door: " + line)
                upper = value.upper()
                if upper == "ALL":
                    access, access_team = "ALL", 0
                elif upper.startswith("TEAM") and upper[4:].isdigit():
                    access = "TEAM"
                    access_team = int(upper[4:])
                    if access_team > INT_MAX:
                        raise ValueError("door team index is out of range: " + line)
                else:
                    raise ValueError("door access must be ALL or TEAM<n>: " + line)
            else:
                raise ValueError("unknown block attribute: " + line)

        door = None
        if kind == "door":
            if door_id is None:
                raise ValueError("a door requires an id=<n> attribute: " + line)
            door = (door_id, DOOR_ACCESS_ORDINALS[access], access_team)
        block = (kind, material, x, y, width, height, multiplier, door)
        blocks.append(block)
        if verb == "mirrored":
            # A reflected door is a second door, not the same one seen from the other side, so it
            # takes the next id — DoorSpec.reflection().
            reflected_door = None if door is None else (door[0] + 1, door[1], door[2])
            blocks.append((kind, material, f32(2.0 * mirror - (x + width)), y, width, height,
                           multiplier, reflected_door))

    door_ids = [block[7][0] for block in blocks if block[7] is not None]
    if len(set(door_ids)) != len(door_ids):
        raise ValueError("duplicate door id in the map text")
    if mirror is None:
        raise ValueError("the map text declares no mirror axis")
    return mirror, spawns, blocks


def map_hash(mirror, spawns, blocks):
    """MapHash.of(ArenaMap): the canonical text, sorted, hashed."""
    spawn_key = lambda s: (s[0], float_bits(s[1]), float_bits(s[2]))
    block_key = lambda b: (
        KIND_ORDINALS[b[0]], MATERIAL_ORDINALS[b[1]],
        float_bits(b[2]), float_bits(b[3]), float_bits(b[4]), float_bits(b[5]),
        # Doors are the one kind whose identity is not their geometry, so ordering by id keeps the
        # hash a function of the contents rather than of the line order they were authored in; a
        # block that is not a door sorts on MapHash's DoorSpec.NONE id of 0.
        b[7][0] if b[7] is not None else 0)

    canonical = ["skystrike-map-hash 1", "mirror %d" % float_bits(mirror)]
    ordered_spawns = sorted(spawns, key=spawn_key)
    canonical.append("spawns %d" % len(ordered_spawns))
    for team, x, y in ordered_spawns:
        canonical.append("%d %d %d" % (team, float_bits(x), float_bits(y)))
    ordered_blocks = sorted(blocks, key=block_key)
    canonical.append("blocks %d" % len(ordered_blocks))
    for kind, material, x, y, width, height, multiplier, door in ordered_blocks:
        row = "%d %d %d %d %d %d" % (
            KIND_ORDINALS[kind], MATERIAL_ORDINALS[material],
            float_bits(x), float_bits(y), float_bits(width), float_bits(height))
        # The authored multiplier participates only when the map actually authored one, which is
        # what keeps the hash of an all-default map identical to what it was before the field.
        if f32(multiplier) != DEFAULT_DURABILITY_MULTIPLIER:
            row += " %d" % float_bits(multiplier)
        # A door's id, access and team are authored content exactly as its rectangle is, encoded
        # only for a real door so every block of a map without doors hashes as it did before doors
        # existed.
        if door is not None:
            row += " door %d %d %d" % (door[0], door[1], door[2])
        canonical.append(row)

    return hashlib.sha256(("\n".join(canonical) + "\n").encode("utf-8")).digest()


def rasterize(blocks, width, height):
    """SdfBaker's step 1: which texels are solid, for the kinds that bake."""
    solid = bytearray(width * height)
    for kind, _material, x, y, w, h, _multiplier, _door in blocks:
        if kind not in BAKED_KINDS:
            continue
        left, right, bottom, top = x, x + w, y, y + h
        min_x = max(0, math.floor(left / TEXEL_SCALE))
        max_x = min(width - 1, math.ceil(right / TEXEL_SCALE))
        min_y = max(0, math.floor(bottom / TEXEL_SCALE))
        max_y = min(height - 1, math.ceil(top / TEXEL_SCALE))
        for ty in range(min_y, max_y + 1):
            world_y = (ty + 0.5) * TEXEL_SCALE
            if not bottom <= world_y <= top:
                continue
            row = ty * width
            for tx in range(min_x, max_x + 1):
                world_x = (tx + 0.5) * TEXEL_SCALE
                if left <= world_x <= right:
                    solid[row + tx] = 1
    return solid


def distance_transform(solid, width, height, target_is_solid):
    """SdfBaker's 8SSEDT: forward and backward passes propagating nearest features."""
    total = width * height
    nearest_x = [0] * total
    nearest_y = [0] * total
    dist_sq = [0] * total

    for index in range(total):
        is_feature = (solid[index] == 1) if target_is_solid else (solid[index] == 0)
        if is_feature:
            nearest_x[index] = index % width
            nearest_y[index] = index // width
        else:
            dist_sq[index] = INF
            nearest_x[index] = -1
            nearest_y[index] = -1

    def relax(cur_x, cur_y, cur_index, nx, ny):
        if nx < 0 or nx >= width or ny < 0 or ny >= height:
            return
        neighbour = ny * width + nx
        rx = nearest_x[neighbour]
        if rx == -1:
            return
        ry = nearest_y[neighbour]
        dx = cur_x - rx
        dy = cur_y - ry
        distance = dx * dx + dy * dy
        if distance < dist_sq[cur_index]:
            dist_sq[cur_index] = distance
            nearest_x[cur_index] = rx
            nearest_y[cur_index] = ry

    for y in range(height):
        row = y * width
        for x in range(width):
            index = row + x
            relax(x, y, index, x - 1, y)
            relax(x, y, index, x, y - 1)
            relax(x, y, index, x - 1, y - 1)
            relax(x, y, index, x + 1, y - 1)
    for y in range(height - 1, -1, -1):
        row = y * width
        for x in range(width - 1, -1, -1):
            index = row + x
            relax(x, y, index, x + 1, y)
            relax(x, y, index, x, y + 1)
            relax(x, y, index, x + 1, y + 1)
            relax(x, y, index, x - 1, y + 1)

    return dist_sq


def java_round(value):
    """Math.round(float): floor(x + 0.5)."""
    return math.floor(value + 0.5)


def bake(blocks):
    """SdfBaker.bake: rasterise, transform inside and outside, encode signed distances."""
    width = round(ARENA_WIDTH / TEXEL_SCALE)
    height = round(ARENA_HEIGHT / TEXEL_SCALE)
    solid = rasterize(blocks, width, height)
    dist_out = distance_transform(solid, width, height, True)
    dist_in = distance_transform(solid, width, height, False)

    data = bytearray(width * height)
    for index in range(width * height):
        signed = -math.sqrt(dist_in[index]) if solid[index] else math.sqrt(dist_out[index])
        # Java: (float) Math.sqrt(...) then * texelScale, both float32; the float32 rounding is
        # reproduced because the encoded byte is a rounded value and a single bit can move it.
        world_distance = f32(f32(signed) * TEXEL_SCALE)
        clamped = java_round(max(-127.0, min(127.0, world_distance)))
        data[index] = (clamped + 128) & 0xFF
    return width, height, data


def main():
    write = "--write" in sys.argv[1:]

    lines = read_map_text()
    mirror, spawns, blocks = parse_map(lines)
    digest = map_hash(mirror, spawns, blocks)
    width, height, data = bake(blocks)
    header = struct.pack(">ii", MAGIC, VERSION) + digest + struct.pack(
        ">iifff", width, height, f32(TEXEL_SCALE), f32(ARENA_WIDTH), f32(ARENA_HEIGHT))
    fresh = header + bytes(data)

    print("map: mirror %.1f, %d spawns, %d blocks (%d mirrored pairs' worth)"
          % (mirror, len(spawns), len(blocks), sum(
              1 for block in blocks if block[0] in BAKED_KINDS)))
    print("map hash: %s" % digest.hex())
    print("bake: %dx%d texels, %.1f units/texel, %d bytes"
          % (width, height, TEXEL_SCALE, len(fresh)))

    if write:
        ASSET.write_bytes(fresh)
        print("wrote %s" % ASSET.relative_to(ROOT))
        return 0

    if not ASSET.exists():
        print("FINDING: %s does not exist" % ASSET.relative_to(ROOT))
        return 1
    shipped = ASSET.read_bytes()

    findings = []
    if len(shipped) < HEADER_BYTES:
        findings.append("the asset is shorter than its own header")
    else:
        shipped_magic, shipped_version = struct.unpack(">ii", shipped[0:8])
        if shipped_magic != MAGIC:
            findings.append("bad magic in the asset header")
        if shipped_version != VERSION:
            findings.append("unsupported asset version: %d" % shipped_version)
        shipped_hash = shipped[8:8 + MAP_HASH_BYTES]
        if shipped_hash != digest:
            findings.append(
                "asset names map %s, the map text hashes to %s"
                % (shipped_hash.hex(), digest.hex()))
        shipped_width, shipped_height, shipped_scale, shipped_world_w, shipped_world_h = (
            struct.unpack(">iifff", shipped[40:60]))
        for name, shipped_value, expected in (
                ("width", shipped_width, width),
                ("height", shipped_height, height),
                ("texel scale", shipped_scale, f32(TEXEL_SCALE)),
                ("world width", shipped_world_w, f32(ARENA_WIDTH)),
                ("world height", shipped_world_h, f32(ARENA_HEIGHT))):
            if shipped_value != expected:
                findings.append("asset %s is %s, the bake gives %s"
                                % (name, shipped_value, expected))

        body = shipped[HEADER_BYTES:]
        if len(body) != len(data):
            findings.append("asset holds %d texels, the bake gives %d" % (len(body), len(data)))
        else:
            differing = [i for i, (a, b) in enumerate(zip(body, data)) if a != b]
            if differing:
                first = differing[0]
                findings.append(
                    "asset differs from a fresh bake in %d of %d texels, first at (%d, %d):"
                    " asset %d, bake %d"
                    % (len(differing), len(body), first % width, first // width,
                       body[first], data[first]))

    if findings:
        for finding in findings:
            print("FINDING: %s" % finding)
        print("run with --write to re-bake, then re-run this check")
        return 1

    print("the shipped asset is the bake of the committed map: header hash, header shape and"
          " every texel agree")
    return 0


if __name__ == "__main__":
    sys.exit(main())
