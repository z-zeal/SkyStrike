#!/usr/bin/env python3
"""Checks static calls and constructor arities against the project's own declarations.

With no JDK in the sandbox, the two mistakes that survive a reading pass are calling a method
that does not exist on a project type and calling a real one with the wrong number of
arguments. This resolves exactly those two cases, for project types only:

  * `Type.method(args)`  -> Type must declare `method` with a compatible arity
  * `new Type(args)`     -> Type must declare a constructor with that arity (records count
                            their components)

Instance calls through variables are ignored: resolving them needs real type inference.
Varargs and overloads are handled by treating "arity >= min declared arity of a varargs
overload" as acceptable.

Run: python3 tools/scratch/static_api_check.py
Exit code 0 = no findings.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODULES = ["shared", "server", "core"]

findings: list[str] = []


def java_files() -> list[pathlib.Path]:
    out: list[pathlib.Path] = []
    for module in MODULES:
        base = ROOT / module / "src"
        if base.exists():
            out.extend(sorted(base.rglob("*.java")))
    return out


def blank_comments_and_strings(text: str) -> str:
    """Replaces comments and literals with spaces, preserving every character offset."""
    out = list(text)
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":
            while i < n and text[i] != "\n":
                out[i] = " "
                i += 1
        elif c == "/" and nxt == "*":
            while i < n and not (text[i] == "*" and i + 1 < n and text[i + 1] == "/"):
                if text[i] != "\n":
                    out[i] = " "
                i += 1
            if i < n:
                out[i] = " "
                if i + 1 < n:
                    out[i + 1] = " "
                i += 2
        elif c in "\"'":
            quote = c
            i += 1
            while i < n:
                if text[i] == "\\":
                    out[i] = " "
                    if i + 1 < n:
                        out[i + 1] = " "
                    i += 2
                    continue
                if text[i] == quote:
                    out[i] = " "
                    i += 1
                    break
                if text[i] != "\n":
                    out[i] = " "
                i += 1
        else:
            i += 1
    return "".join(out)


def match_bracket(text: str, open_index: int, opener: str = "(", closer: str = ")") -> int:
    depth = 0
    for i in range(open_index, len(text)):
        if text[i] == opener:
            depth += 1
        elif text[i] == closer:
            depth -= 1
            if depth == 0:
                return i
    return -1


def split_args(arg_text: str) -> int:
    arg_text = arg_text.strip()
    if not arg_text:
        return 0
    depth = 0
    count = 1
    previous = ""
    for c in arg_text:
        if c in "([{<":
            depth += 1
        elif c in ")]}>":
            # A lambda arrow's '>' closes nothing: counting it drove the depth negative and
            # swallowed every comma after a lambda, which is how a nine-argument call used to be
            # reported as a seven-argument one.
            if not (c == ">" and previous == "-"):
                depth -= 1
        elif c == "," and depth == 0:
            count += 1
        previous = c
    return count


FILES = java_files()

TYPE_DECL = re.compile(r"\b(class|interface|enum|record)\s+(\w+)")
MODIFIERS = r"(?:public|protected|private|static|final|abstract|synchronized|native|default|strictfp|@\w+)\s+"
# A method or constructor signature: modifiers, optional generics, optional return type, name, '('
SIGNATURE = re.compile(
    rf"(?:^|[;{{}}\n])\s*((?:{MODIFIERS})*)"
    r"(?:<[^<>]*(?:<[^<>]*>)?[^<>]*>\s+)?"
    r"((?:[\w.$]+(?:<[^;{}]*?>)?(?:\[\])*\s+)?)"
    r"(\w+)\s*\(",
    re.M,
)

# simple name -> {"methods": {name: set(arity|(min,'var'))}, "ctors": set, "fields": set, "kind": str}
api: dict[str, dict] = {}


def entry_for(name: str, kind: str = "class") -> dict:
    return api.setdefault(name, {"methods": {}, "ctors": set(), "fields": set(), "kind": kind})


for f in FILES:
    raw = f.read_text(encoding="utf-8")
    text = blank_comments_and_strings(raw)

    # 1. Type declarations and their body ranges.
    types: list[tuple[str, str, int, int]] = []  # (name, kind, body_start, body_end)
    for m in TYPE_DECL.finditer(text):
        kind, name = m.group(1), m.group(2)
        brace = text.find("{", m.end())
        if brace < 0:
            continue
        end = match_bracket(text, brace, "{", "}")
        if end < 0:
            end = len(text)
        types.append((name, kind, brace, end))
        e = entry_for(name, kind)
        e["kind"] = kind
        if kind == "record":
            paren = text.find("(", m.end())
            if 0 <= paren < brace:
                close = match_bracket(text, paren)
                if close > 0:
                    e["ctors"].add(split_args(text[paren + 1:close]))
                    for comp in re.findall(r"(\w+)\s*(?:,|$)", text[paren + 1:close]):
                        e["methods"].setdefault(comp, set()).add(0)
        if kind == "enum":
            # Implicit members every enum gets from the compiler.
            for implicit, arity in (("values", 0), ("valueOf", 1), ("ordinal", 0), ("name", 0),
                                    ("compareTo", 1)):
                e["methods"].setdefault(implicit, set()).add(arity)
            semi = text.find(";", brace)
            header_end = semi if 0 < semi < end else end
            for const in re.findall(r"\b([A-Z][A-Z0-9_]{1,})\b", text[brace:header_end]):
                e["fields"].add(const)

    def owner_at(offset: int) -> str | None:
        """Innermost type whose body strictly contains offset."""
        best = None
        best_start = -1
        for name, _kind, start, end in types:
            if start < offset < end and start > best_start:
                best = name
                best_start = start
        return best

    # 2. Methods and constructors.
    for m in SIGNATURE.finditer(text):
        mods, ret, name = m.group(1), m.group(2).strip(), m.group(3)
        if name in {"if", "for", "while", "switch", "catch", "return", "new", "synchronized",
                    "super", "this", "record", "assert", "throw", "yield"}:
            continue
        paren = m.end() - 1
        close = match_bracket(text, paren)
        if close < 0:
            continue
        after = text[close + 1:close + 120].lstrip()
        if not (after.startswith("{") or after.startswith("throws") or after.startswith(";")):
            continue
        # Use the '(' offset: a signature match can begin on the previous type's closing brace.
        owner = owner_at(paren)
        if owner is None:
            continue
        args = text[paren + 1:close]
        arity = split_args(args)
        varargs = "..." in args
        e = entry_for(owner)
        if name == owner and not ret:
            e["ctors"].add(arity)
            if varargs:
                e["ctors"].add(-arity)  # negative marks "at least arity-1"
        elif ret or mods:
            e["methods"].setdefault(name, set()).add(arity)
            if varargs:
                e["methods"][name].add(-arity)

    # 3. Public/protected fields and constants.
    for m in re.finditer(
            r"(?:^|[;{}\n])\s*(?:public|protected)\s+(?:static\s+)?(?:final\s+)?"
            r"[\w.$<>,\[\]]+\s+(\w+)\s*(?:=|;)", text):
        owner = owner_at(m.end())
        if owner:
            entry_for(owner)["fields"].add(m.group(1))

KNOWN_EXTERNAL = {
    "Math", "String", "System", "Integer", "Float", "Long", "Boolean", "Double", "Objects",
    "Arrays", "Collections", "List", "Map", "Set", "Optional", "Thread", "Gdx", "MathUtils",
    "ShapeRenderer", "GL20", "Color", "Files", "Paths", "StandardCharsets", "ByteBuffer",
    "Assertions", "Random", "Character", "StringBuilder", "EnumMap", "HashMap", "ArrayList",
    "ArrayDeque", "LinkedHashMap", "ConcurrentLinkedQueue", "AtomicInteger", "AtomicBoolean",
    "Executors", "TimeUnit", "UUID", "Comparator", "Stream", "IntStream", "Log", "Client",
    "Server", "Kryo", "Connection", "Listener", "Texture", "SpriteBatch", "BitmapFont",
    "OrthographicCamera", "Vector2", "Vector3", "FrameBuffer", "ShaderProgram", "Pixmap",
    "Disposable", "ScreenUtils", "Screen", "Game", "Input", "Keys", "Buttons", "InputAdapter",
    "InputMultiplexer", "GlyphLayout", "Format", "TextureRegion", "Matrix4", "Interpolation",
    "ByteArrayOutputStream", "DataOutputStream", "DataInputStream", "IOException", "FileHandle",
}


def arity_ok(arity: int, declared: set[int]) -> bool:
    if arity in declared:
        return True
    for d in declared:
        if d < 0 and arity >= (-d) - 1:  # varargs
            return True
    return False


for f in FILES:
    raw = f.read_text(encoding="utf-8")
    text = blank_comments_and_strings(raw)
    rel = f.relative_to(ROOT)

    for m in re.finditer(r"\bnew\s+([A-Z]\w*)\s*\(", text):
        type_name = m.group(1)
        if type_name in KNOWN_EXTERNAL or type_name not in api:
            continue
        close = match_bracket(text, m.end() - 1)
        if close < 0:
            continue
        arity = split_args(text[m.end():close])
        ctors = api[type_name]["ctors"]
        if api[type_name]["kind"] == "interface":
            continue
        if not ctors:
            if arity != 0:
                findings.append(f"{rel}: new {type_name}({arity} args) but only the default "
                                f"constructor exists")
            continue
        if not arity_ok(arity, ctors):
            findings.append(
                f"{rel}: new {type_name}({arity} args); declared arities "
                f"{sorted(a for a in ctors if a >= 0)}")

    for m in re.finditer(r"\b([A-Z]\w*)\.(\w+)\s*\(", text):
        type_name, method = m.group(1), m.group(2)
        if type_name in KNOWN_EXTERNAL or type_name not in api:
            continue
        e = api[type_name]
        if method not in e["methods"]:
            if method in e["fields"]:
                continue
            if method in api:
                # Outer.Nested(...): a qualified nested-type constructor.
                close = match_bracket(text, m.end() - 1)
                if close > 0:
                    arity = split_args(text[m.end():close])
                    ctors = api[method]["ctors"]
                    if ctors and not arity_ok(arity, ctors):
                        findings.append(
                            f"{rel}: new {type_name}.{method}({arity} args); declared arities "
                            f"{sorted(a for a in ctors if a >= 0)}")
                continue
            findings.append(f"{rel}: {type_name}.{method}(...) is not declared on {type_name}")
            continue
        close = match_bracket(text, m.end() - 1)
        if close < 0:
            continue
        arity = split_args(text[m.end():close])
        if not arity_ok(arity, e["methods"][method]):
            findings.append(
                f"{rel}: {type_name}.{method}({arity} args); declared arities "
                f"{sorted(a for a in e['methods'][method] if a >= 0)}")

print(f"indexed {len(api)} project types from {len(FILES)} files")
if findings:
    print(f"\n{len(findings)} finding(s):")
    for item in sorted(set(findings)):
        print(f"  - {item}")
    sys.exit(1)
print("no findings")
sys.exit(0)
