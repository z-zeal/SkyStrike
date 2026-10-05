#!/usr/bin/env python3
"""Static sanity checks for the SkyStrike sources.

This sandbox has no JDK, so nothing here compiles or runs the game. These checks are a
cheap substitute for `javac -Xlint` on the things that bite hardest when you cannot compile:
unresolvable project imports, unbalanced delimiters, unused imports, module-boundary
violations, and references to project classes that do not exist.

Run: python3 tools/scratch/static_check.py
Exit code 0 = no findings.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODULES = ["shared", "server", "core", "lwjgl3", "android"]
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

# fully-qualified-name -> file, for every project type declared at top level
declared: dict[str, pathlib.Path] = {}
for f in FILES:
    text = f.read_text(encoding="utf-8")
    pkg_match = re.search(r"^package\s+([\w.]+);", text, re.M)
    if not pkg_match:
        add(f, "no package declaration")
        continue
    declared[f"{pkg_match.group(1)}.{f.stem}"] = f


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


JAVA_LANG_OK = re.compile(r"^(java|javax|com\.badlogic|com\.esotericsoftware|org\.)")

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

        if fqn.startswith(PKG_ROOT):
            target = fqn
            if is_static:
                target = fqn.rsplit(".", 1)[0]
            if target not in declared and target.rsplit(".", 1)[0] not in declared:
                add(f, f"import does not resolve to a project type: {fqn}")
        elif not JAVA_LANG_OK.match(fqn):
            add(f, f"unexpected import root: {fqn}")

        simple = fqn.rsplit(".", 1)[1]
        if simple == "*":
            add(f, f"wildcard import: {fqn}")
            continue
        body = code.split(";", 1)[-1]
        body_wo_imports = re.sub(r"^import[^;]+;", "", body, flags=re.M)
        if not re.search(rf"\b{re.escape(simple)}\b", body_wo_imports):
            add(f, f"unused import: {fqn}")

    # Module boundaries, mirroring the root build.gradle checkModuleDependencies task.
    rel = f.relative_to(ROOT).as_posix()
    if rel.startswith("server/src"):
        if "com.badlogic.gdx" in raw:
            add(f, "server module must not reference com.badlogic.gdx")
        if f"{PKG_ROOT}.core" in raw or re.search(r"import io\.github\.skystrike\.(render|screens|fx|input|net\.Client)", raw):
            add(f, "server module must not reference the core module")
    if rel.startswith("core/src") and f"{PKG_ROOT}.server" in raw:
        add(f, "core module must not reference the server module")
    if rel.startswith("shared/src"):
        if "com.badlogic.gdx" in raw:
            add(f, "shared module must not reference libGDX")

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

print(f"checked {len(FILES)} java files, {len(declared)} declared types")
if findings:
    print(f"\n{len(findings)} finding(s):")
    for item in findings:
        print(f"  - {item}")
    sys.exit(1)
print("no findings")
sys.exit(0)
