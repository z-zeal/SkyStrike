#!/usr/bin/env python3
"""Static sanity checks for the SkyStrike sources.

This sandbox has no JDK, so nothing here compiles or runs the game. These checks are a
cheap substitute for `javac -Xlint` on the things that bite hardest when you cannot compile:
unresolvable project imports, unbalanced delimiters, unused imports, module-boundary
violations, and references to project classes that do not exist.

Two of them are the map building plan's own milestone gates, checked against the shared model
rather than against a hand-kept list:

  * no consumer (anywhere outside the map model package, in a main source set) may name a
    block kind or test one in a switch — movement, sight, bullets, SDF and rendering read the
    `BlockRules` column that answers their question;
  * the core terrain renderer must draw every `BlockRenderStyle` the table can hand it, so a
    treatment added to the shared enum cannot be silently invisible.

Run: python3 tools/scratch/static_check.py
Exit code 0 = no findings.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
# `tools` is the headless map CLI: it is scanned like any other module so its calls into the
# map model are checked, and so nothing in it can name a block kind either.
MODULES = ["shared", "server", "core", "lwjgl3", "android", "tools"]
PKG_ROOT = "io.github.skystrike"

findings: list[str] = []


def add(path: pathlib.Path, message: str) -> None:
    findings.append(f"{path.relative_to(ROOT)}: {message}")


def java_files() -> list[pathlib.Path]:
    out: list[pathlib.Path] = []
    for module in MODULES:
        base = ROOT / module / "src"
        if base.exists():
            out.extend(sorted(base.rglob("*.java")))
    return out


FILES = java_files()

# fully-qualified-name -> source file/module, for every project type declared at top level.
declared: dict[str, pathlib.Path] = {}
declared_modules: dict[str, str] = {}
for f in FILES:
    text = f.read_text(encoding="utf-8")
    pkg_match = re.search(r"^package\s+([\w.]+);", text, re.M)
    if not pkg_match:
        add(f, "no package declaration")
        continue
    fqn = f"{pkg_match.group(1)}.{f.stem}"
    declared[fqn] = f
    declared_modules[fqn] = f.relative_to(ROOT).parts[0]


def resolve_project_type(reference: str) -> str | None:
    """Resolve a qualified type or member path back to its declared top-level type."""
    parts = reference.split(".")
    for cut in range(len(parts), 3, -1):
        candidate = ".".join(parts[:cut])
        if candidate in declared:
            return candidate
    return None


FORBIDDEN_MODULE_DEPENDENCIES = {
    "shared": {"core", "server", "tools"},
    "server": {"core", "tools"},
    "core": {"server", "tools"},
    "tools": {"core", "server"},
}


def strip_comments_and_strings(text: str) -> str:
    """Remove comments and string/char literals so delimiter counting is meaningful."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":
            while i < n and text[i] != "\n":
                i += 1
        elif c == "/" and nxt == "*":
            i += 2
            while i < n - 1 and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 2
        elif c == '"':
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == '"':
                    i += 1
                    break
                i += 1
            out.append('""')
        elif c == "'":
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
            out.append("''")
        else:
            out.append(c)
            i += 1
    return "".join(out)


def check_delimiters(path: pathlib.Path, code: str) -> None:
    pairs = {"}": "{", ")": "(", "]": "["}
    stack: list[tuple[str, int]] = []
    line = 1
    for ch in code:
        if ch == "\n":
            line += 1
        elif ch in "{([":
            stack.append((ch, line))
        elif ch in "})]":
            if not stack or stack[-1][0] != pairs[ch]:
                add(path, f"unbalanced '{ch}' at line {line}")
                return
            stack.pop()
    if stack:
        opener, opened_line = stack[-1]
        add(path, f"unclosed '{opener}' opened at line {opened_line}")


# android.* is the launcher's own platform SDK: the one module allowed to import it is the
# one CI does not compile, so a stray import there is a real risk and stays checked —
# only the root itself is whitelisted.
JAVA_LANG_OK = re.compile(
    r"^(java|javax|android|com\.badlogic|com\.esotericsoftware|org\.)"
)

for f in FILES:
    raw = f.read_text(encoding="utf-8")
    code = strip_comments_and_strings(raw)

    check_delimiters(f, code)

    if not raw.endswith("\n"):
        add(f, "file does not end with a newline")
    if "\t" in raw:
        add(f, "contains a tab character")
    for idx, line in enumerate(raw.splitlines(), start=1):
        if line != line.rstrip():
            add(f, f"trailing whitespace on line {idx}")

    imports = re.findall(r"^import\s+(static\s+)?([\w.]+);", raw, re.M)
    seen: set[str] = set()
    for is_static, fqn in imports:
        if fqn in seen:
            add(f, f"duplicate import {fqn}")
        seen.add(fqn)

        project_type = None
        if fqn.startswith(PKG_ROOT):
            target = fqn.rsplit(".", 1)[0] if is_static else fqn
            project_type = resolve_project_type(target)
            if project_type is None:
                add(f, f"import does not resolve to a project type: {fqn}")
            else:
                module = f.relative_to(ROOT).parts[0]
                owner = declared_modules[project_type]
                if owner in FORBIDDEN_MODULE_DEPENDENCIES.get(module, set()):
                    add(f, f"{module} module must not reference {owner} type {project_type}")
        elif not JAVA_LANG_OK.match(fqn):
            add(f, f"unexpected import root: {fqn}")

        simple = fqn.rsplit(".", 1)[1]
        if simple == "*":
            add(f, f"wildcard import: {fqn}")
            continue
        body = code.split(";", 1)[-1]
        body_wo_imports = re.sub(r"^import[^;]+;", "", body, flags=re.M)
        if re.search(rf"\b{re.escape(simple)}\b", body_wo_imports):
            continue
        # Comments are stripped from `code`, but a javadoc {@link} still needs its import for the
        # documentation to resolve. Count that as a use; prose mentions do not.
        if re.search(rf"\{{@(?:link|linkplain|see)\s+(?:[\w.]*\.)?{re.escape(simple)}\b", raw):
            continue
        add(f, f"unused import: {fqn}")

    # Module boundaries are checked by the owning source type, not by a package-name guess:
    # client code is spread across io.github.skystrike.audio/fx/ui/etc., not a .core package.
    module = f.relative_to(ROOT).parts[0]
    if module in ("shared", "server") and "com.badlogic.gdx" in raw:
        add(f, f"{module} module must not reference libGDX")

    forbidden_owners = FORBIDDEN_MODULE_DEPENDENCIES.get(module, set())
    if forbidden_owners:
        code_without_imports = re.sub(r"^import[^;]+;", "", code, flags=re.M)
        for reference in re.findall(rf"\b({re.escape(PKG_ROOT)}(?:\.\w+)+)\b", code_without_imports):
            project_type = resolve_project_type(reference)
            if project_type is None:
                continue
            owner = declared_modules[project_type]
            if owner in forbidden_owners:
                add(f, f"{module} module must not reference {owner} type {project_type}")

# Cross-check referenced project types that are imported nowhere but used as Pkg.Type
for f in FILES:
    raw = f.read_text(encoding="utf-8")
    for fqn in re.findall(rf"\b({re.escape(PKG_ROOT)}(?:\.\w+)+)\b", raw):
        if fqn in declared:
            continue
        # maybe a nested type or a package prefix: trim trailing segments
        parts = fqn.split(".")
        ok = False
        for cut in range(len(parts), 3, -1):
            if ".".join(parts[:cut]) in declared:
                ok = True
                break
        if not ok and not any(d.startswith(fqn + ".") for d in declared):
            add(f, f"reference to unknown project type: {fqn}")

MAP_MODEL_PACKAGE = "shared/src/main/java/io/github/skystrike/shared/map/"
BLOCK_KIND_SOURCE = MAP_MODEL_PACKAGE + "BlockKind.java"
BLOCK_RENDER_STYLE_SOURCE = MAP_MODEL_PACKAGE + "BlockRenderStyle.java"
TERRAIN_RENDERER_SOURCE = "core/src/main/java/io/github/skystrike/world/TerrainRenderer.java"


def enum_constants(relative_path: str) -> list[str]:
    """The constant names declared by the enum in `relative_path`, in order."""
    text = (ROOT / relative_path).read_text(encoding="utf-8")
    body = text.split("{", 1)[1]
    return re.findall(r"^ {4}([A-Z][A-Z0-9_]*)\s*[,;]?\s*$", body, re.M)


def switch_selectors_and_bodies(code: str) -> list[tuple[str, str]]:
    """Every `switch (selector) { body }` in comment-free `code`, as (selector, body) pairs."""
    found: list[tuple[str, str]] = []
    for match in re.finditer(r"\bswitch\s*\(", code):
        open_paren = code.index("(", match.start())
        close_paren = matching_delimiter(code, open_paren, "(", ")")
        if close_paren < 0:
            continue
        open_brace = code.find("{", close_paren)
        if open_brace < 0:
            continue
        close_brace = matching_delimiter(code, open_brace, "{", "}")
        if close_brace < 0:
            continue
        found.append((code[open_paren + 1:close_paren - 1], code[open_brace + 1:close_brace - 1]))
    return found


def matching_delimiter(code: str, start: int, open_ch: str, close_ch: str) -> int:
    """Index just past the delimiter closing the one at `start`, or -1 when it never closes."""
    depth = 0
    for i in range(start, len(code)):
        if code[i] == open_ch:
            depth += 1
        elif code[i] == close_ch:
            depth -= 1
            if depth == 0:
                return i + 1
    return -1


def check_no_consumer_names_a_block_kind() -> None:
    """Map plan §9 gate: no consumer tests a kind by name, anywhere but the model package.

    The model package is where a kind may be named — the enum itself, the rule table, the format
    parser and the shared queries. Everywhere else the kind's behaviour has to come from a
    `BlockRules` column, because a consumer that names a kind stops following the table the day the
    kind changes. Test sources are exempt deliberately: authoring a mixed-kind map is how the
    table's rows get proved one by one. The authoring surfaces that arrive with milestone 5 (the
    in-game editor palette and MapTool) need kind *identity*, not kind behaviour; they are added to
    this exemption list one path at a time when they land, as a reviewable decision.
    """
    kinds = set(enum_constants(BLOCK_KIND_SOURCE))
    if not kinds:
        # A parse that finds nothing would make this gate pass vacuously, which is worse than
        # not having it: fail loudly instead.
        add(ROOT / BLOCK_KIND_SOURCE, "no block kinds parsed: the kind-name gate would be blind")
        return
    for f in FILES:
        relative = f.relative_to(ROOT).as_posix()
        if "/src/test/" in relative or relative.startswith(MAP_MODEL_PACKAGE):
            continue
        raw = f.read_text(encoding="utf-8")
        if re.search(r"\bBlockKind\b", raw):
            add(f, "names BlockKind: a consumer must ask BlockRules for its column, not a kind")
        for selector, body in switch_selectors_and_bodies(strip_comments_and_strings(raw)):
            if not re.search(r"\bkind\b", selector):
                continue
            # `case GLASS, DOOR ->` lists two labels, so take the whole label list, not one name.
            for labels in re.findall(r"\bcase\s+([^:>]*?)\s*(?:->|:)", body):
                for constant in re.findall(r"\b([A-Z][A-Z0-9_]*)\b", labels):
                    if constant in kinds:
                        add(f, f"tests the block kind {constant} by name in a switch")


def check_renderer_covers_every_treatment() -> None:
    """Map plan §9 gate: the core renderer draws every treatment the shared table declares.

    The list comes from the shared enum, not from this file, so adding a `BlockRenderStyle` fails
    here until some part of the renderer draws it.
    """
    styles = enum_constants(BLOCK_RENDER_STYLE_SOURCE)
    if not styles:
        add(
            ROOT / BLOCK_RENDER_STYLE_SOURCE,
            "no render styles parsed: the treatment gate would be blind",
        )
        return
    path = ROOT / TERRAIN_RENDERER_SOURCE
    if not path.exists():
        add(path, "the terrain renderer is missing")
        return
    raw = path.read_text(encoding="utf-8")
    for style in styles:
        if not re.search(rf"\b{style}\b", raw):
            add(path, f"no drawing code for BlockRenderStyle.{style}")


# Core consumes capabilities, never launcher/application-type branches.
for path in FILES:
    relative = path.relative_to(ROOT).as_posix()
    if relative.startswith("core/src/") and "/platform/" not in relative:
        if re.search(r"\bApplicationType\b", path.read_text(encoding="utf-8")):
            add(path, "core must query platform capabilities, not ApplicationType")

check_no_consumer_names_a_block_kind()
check_renderer_covers_every_treatment()

print(f"checked {len(FILES)} java files, {len(declared)} declared types")
if findings:
    print(f"\n{len(findings)} finding(s):")
    for item in findings:
        print(f"  - {item}")
    sys.exit(1)
print("no findings")
sys.exit(0)
