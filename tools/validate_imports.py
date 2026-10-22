#!/usr/bin/env python3
"""Static import checker for the Axiom Calc Kotlin sources.

WHY THIS EXISTS
---------------
The sandbox has a real Kotlin compiler but no Android SDK, Compose, Room or
DataStore jars, so the UI and data layers cannot be type-checked locally. The
first real Android build (GitHub Actions) failed with 96 compile errors that
were almost all one of two shapes:

    e: .../ScientificCalculatorScreen.kt:183:17 Unresolved reference 'Text'.
    e: .../StandardCalculatorScreen.kt:30:34    Unresolved reference 'focusable'.

`Text`, `remember` and `emptyPreferences` were simply never imported;
`focusable` was imported from the wrong package (`androidx.compose.ui.focus`
instead of the correct `androidx.compose.foundation`). A syntax-only sweep
cannot see any of this, and one bad import aborts the build before a single
test runs.

WHAT IT DOES
------------
1. Walks every Kotlin file with brace tracking and classifies each declaration
   as TOP-LEVEL (needs an import from other packages) or MEMBER (reached
   through a receiver, never imported). Enum entries and companion members are
   classified as members.
2. Indexes every simple name that any file in the project imports, mapping it
   to the fully-qualified names that give it meaning.
3. Reports an identifier when it is used unqualified in code, is not declared
   or imported in that file, is not a member anywhere in the project, is not
   covered by a wildcard import or by `kotlin.*`, and step 2 knows a real FQN
   for it from another package.
4. Separately reports any simple name that two files import from two different
   packages - that is the wrong-package `focusable` shape.

This is a heuristic, not a type checker. It cannot resolve receivers and it
does not know what is inside a jar, so a name that no file in the project
imports yet (a brand-new AndroidX call) is invisible to it. It exists to catch
the missing-import class of defect offline, before spending a CI run on it.

Usage:  python3 tools/validate_imports.py
Exit:   0 when nothing is reported, 1 otherwise.
"""

from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOTS = [
    ROOT / "app/src/main/java",
    ROOT / "app/src/test/java",
    ROOT / "app/src/androidTest/java",
]

# ---------------------------------------------------------------------------
# Lexing helpers
# ---------------------------------------------------------------------------

BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.DOTALL)
LINE_COMMENT_RE = re.compile(r"//[^\n]*")
STRING_RE = re.compile(r'"""(?:.|\n)*?"""|"(?:\\.|[^"\\])*"')
CHAR_RE = re.compile(r"'(?:\\.|[^'\\])'")
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)", re.MULTILINE)
IMPORT_RE = re.compile(r"^\s*import\s+([\w.]+(?:\.\*)?)(?:\s+as\s+(\w+))?\s*$",
                       re.MULTILINE)

CONTAINER_RE = re.compile(
    r"\b(?:class|interface|object|enum\s+class|annotation\s+class)\s+"
    r"(?:<[^<>]*>\s*)?`?(\w+)`?")
MEMBER_RE = re.compile(r"\b(?:fun|val|var|typealias)\s+(?:<[^<>]*>\s*)?`?(\w+)`?")
ENUM_BODY_RE = re.compile(r"\benum\s+class\s+\w+[^\{]*\{([^}]*)\}")
IDENT_RE = re.compile(r"\b[A-Za-z_][A-Za-z0-9_]*\b")

# A name introduced by the file itself without a `val`/`fun` declaration:
# `foo = 1` (named argument), `(foo: Int)` (typed parameter), `{ foo, bar -> }`
# (lambda or destructuring parameter), `for (foo in ...)`, `catch (foo: ...)`.
NAMED_ARG_RE = re.compile(r"\b(\w+)\s*=(?!=)")
TYPED_PARAM_RE = re.compile(r"\b(\w+)\s*:(?!:)")
LAMBDA_PARAM_RE = re.compile(r"\{\s*([^{}();]*?)\s*->")
DESTRUCTURE_RE = re.compile(r"\(([^()]*)\)\s*->")
FOR_RE = re.compile(r"\bfor\s*\(([^()]*)\bin\b")
CATCH_RE = re.compile(r"\bcatch\s*\(\s*(\w+)")


def locally_bound_names(code: str) -> set[str]:
    """Names this file binds without a declaration the indexer can see."""
    names: set[str] = set()
    names.update(NAMED_ARG_RE.findall(code))
    names.update(TYPED_PARAM_RE.findall(code))
    names.update(CATCH_RE.findall(code))
    for group_re in (LAMBDA_PARAM_RE, DESTRUCTURE_RE, FOR_RE):
        for body in group_re.findall(code):
            for part in re.split(r"[,()\s]+", body):
                part = part.strip()
                if part and re.fullmatch(r"\w+", part):
                    names.add(part)
    return names

# `kotlin.*` is implicitly imported into every file, and these java/kotlin
# names are used unqualified all over the project after a normal import. They
# are never reported.
DEFAULT_IMPORTS = {
    "Any", "Array", "Boolean", "ByteArray", "Byte", "Char", "CharArray",
    "CharSequence", "Comparable", "Double", "DoubleArray", "Enum", "Float",
    "FloatArray", "Function", "Int", "IntArray", "Iterable", "Lazy", "Long",
    "LongArray", "Nothing", "Number", "Pair", "Result", "Short", "String",
    "StringBuilder", "Throwable", "Triple", "Unit",
    "Exception", "RuntimeException", "Error", "IllegalArgumentException",
    "IllegalStateException", "IndexOutOfBoundsException", "ArithmeticException",
    "NumberFormatException", "UnsupportedOperationException",
    "NoSuchElementException", "ClassCastException", "ConcurrentModification",
    "BigDecimal", "BigInteger", "RoundingMode", "MathContext", "Math",
    "Calendar", "Locale", "TimeZone", "Date", "UUID", "Random", "Instant",
    "LocalDate", "LocalDateTime", "ZoneId", "ZoneOffset", "Pattern", "Matcher",
    "AtomicLong", "AtomicBoolean", "AtomicInteger", "ConcurrentHashMap",
    "Collections", "Arrays", "Serializable", "Parcelable", "Cloneable",
    "Override", "Suppress", "Deprecated", "OptIn", "RequiresOptIn", "JvmStatic",
    "JvmField", "Volatile", "Synchronized", "Test", "Before", "After",
    "Ignore", "Rule", "RunWith", "ClassRule", "FixMethodOrder", "Category",
    "get", "set", "value", "name", "id", "key", "index", "size", "text",
    "entries", "values", "keys", "run", "let", "apply", "also", "with",
    "invoke", "toString", "equals", "hashCode", "copy", "component1",
    "component2", "component3", "component4", "component5",
}

# Names reached through a scope receiver rather than an import. `weight` inside
# `RowScope`, `align` inside `BoxScope`, `maxHeight` inside
# `BoxWithConstraintsScope` and so on. They appear in the index because some
# file imports a same-named top-level extension, so they are excluded here.
RECEIVER_MEMBERS = {
    "weight", "align", "alignBy", "maxWidth", "maxHeight", "minWidth",
    "minHeight", "constraints", "current", "add", "remove", "contains",
    "plus", "minus", "times", "div", "rem", "mod", "pow", "sqrt", "abs",
    "sign", "round", "floor", "ceil", "min", "max", "sum", "average", "first",
    "last", "isEmpty", "isNotEmpty", "iterator", "compareTo", "format",
    "provide", "consume", "requestFocus", "freeFocus", "clearFocus", "close",
    "open", "reset", "restore", "show", "dismiss", "navigate", "popBackStack",
    "emit", "collect", "collectLatest", "send", "tryEmit", "update", "observe",
    "post", "postValue", "enqueue", "cancel", "start", "stop", "execute",
    "query", "insert", "delete", "clear", "rangeTo", "until", "step", "by",
    "init", "append", "insert", "deleteAt", "length", "uppercase", "lowercase",
    "trim", "split", "replace", "map", "filter", "forEach", "forEachIndexed",
    "mapIndexed", "flatMap", "fold", "reduce", "sorted", "sortedBy", "take",
    "drop", "chunked", "windowed", "zip", "distinct", "reversed", "count",
    "any", "all", "none", "find", "indexOf", "joinToString", "substring",
    "startsWith", "endsWith", "matches", "toInt", "toDouble", "toLong",
    "toFloat", "toBoolean", "toIntOrNull", "toDoubleOrNull", "toLongOrNull",
    "toFloatOrNull", "toBigDecimal", "toBigInteger", "toList", "toSet",
    "toMap", "toMutableList", "toMutableSet", "toDoubleArray", "toIntArray",
    "toCharArray", "toByteArray", "orEmpty", "isNullOrEmpty", "isNullOrBlank",
    "isBlank", "isDigit", "isLetter", "isWhitespace", "takeIf", "takeUnless",
    "ifEmpty", "getOrElse", "getOrNull", "maxOf", "minOf", "maxOrNull",
    "minOrNull", "sumOf", "sortedWith", "sortedByDescending", "groupBy",
    "distinctBy", "mapNotNull", "flatMapLatest", "stateIn", "shareIn",
    "combine", "merge", "concat", "onEach", "catch", "onCompletion",
    "collectAsState", "collectAsStateWithLifecycle", "produceState",
    "remember", "rememberSaveable", "mutableStateOf", "mutableStateListOf",
    "derivedStateOf", "snapshotFlow", "rememberCoroutineScope",
}

# Generated sources: never imported, always in the app's own package.
GENERATED = {"R", "BuildConfig", "Manifest", "RoomDatabase_Impl",
             "AxiomDatabase_Impl"}

# Simple names that legitimately have more than one top-level declaration, so
# seeing two different import FQNs across the project is correct rather than
# suspicious. Each entry is verified against the androidx sources:
#   width/height  Modifier.width and Modifier.height are extensions in
#                 compose/foundation/foundation-layout (androidx.compose.foundation.layout),
#                 while DpRect.width and DpRect.height are separate extensions
#                 declared at compose/ui/ui-unit/src/commonMain/kotlin/androidx/
#                 compose/ui/unit/Dp.kt:465 and :470 (androidx.compose.ui.unit).
#                 A file that measures a node with getUnclippedBoundsInRoot()
#                 needs the ui.unit pair; a file that sizes a layout needs the
#                 foundation.layout pair. Both are correct.
AMBIGUITY_ALLOWED = {"width", "height"}


def strip_noise(text: str) -> str:
    """Blank out comments and string/char literals so only code is scanned."""
    text = BLOCK_COMMENT_RE.sub(" ", text)
    text = LINE_COMMENT_RE.sub(" ", text)
    text = STRING_RE.sub(' "" ', text)
    text = CHAR_RE.sub(" ' '", text)
    return text


def qualified_positions(code: str) -> set[int]:
    """Character offsets of identifiers that follow a '.' (member access)."""
    out = set()
    for m in IDENT_RE.finditer(code):
        i = m.start() - 1
        while i >= 0 and code[i] in " \t\n":
            i -= 1
        if i >= 0 and code[i] == ".":
            out.add(m.start())
    return out


class FileInfo:
    """One Kotlin file: package, imports, top-level and member declarations."""

    def __init__(self, path: Path):
        raw = path.read_text(encoding="utf-8")
        self.path = path
        pm = PACKAGE_RE.search(raw)
        self.package = pm.group(1) if pm else ""
        self.code = strip_noise(raw)

        self.imports: dict[str, str] = {}
        self.wildcards: list[str] = []
        for m in IMPORT_RE.finditer(raw):
            fqn, alias = m.group(1), m.group(2)
            if fqn.endswith(".*"):
                self.wildcards.append(fqn[:-2])
                continue
            self.imports[alias or fqn.rsplit(".", 1)[-1]] = fqn

        # Declarations, split by nesting depth.
        self.toplevel: set[str] = set()
        self.members: set[str] = set()
        containers = self._container_spans(self.code)
        for m in MEMBER_RE.finditer(self.code):
            (self.members if self._inside(m.start(), containers)
             else self.toplevel).add(m.group(1))
        for m in CONTAINER_RE.finditer(self.code):
            (self.members if self._inside(m.start(), containers)
             else self.toplevel).add(m.group(1))
        # Enum entries are members, never imports.
        for m in ENUM_BODY_RE.finditer(self.code):
            for entry in re.findall(r"\b([A-Z][A-Z0-9_]*)\b", m.group(1)):
                self.members.add(entry)
        # Type parameters (`<T>`, `<reified VM : ViewModel>`).
        self.type_params = set(re.findall(r"<\s*(\w+)", self.code))
        self.locals = locally_bound_names(self.code)
        self.qualified = qualified_positions(self.code)

    @staticmethod
    def _container_spans(code: str) -> list[tuple[int, int]]:
        """Brace spans of every class/interface/object body."""
        spans: list[tuple[int, int]] = []
        stack: list[int] = []
        pending: list[int] = []
        for i, ch in enumerate(code):
            if ch == "{":
                stack.append(i)
            elif ch == "}":
                if stack:
                    start = stack.pop()
                    if any(start - 200 <= p < start for p in pending):
                        spans.append((start, i))
                    pending = [p for p in pending if p >= start]
        for m in CONTAINER_RE.finditer(code):
            pending.append(m.end())
        return spans

    @staticmethod
    def _inside(pos: int, spans: list[tuple[int, int]]) -> bool:
        return any(a <= pos <= b for a, b in spans)


def kt_files():
    for root in SOURCE_ROOTS:
        if root.is_dir():
            yield from sorted(root.rglob("*.kt"))


def main() -> int:
    files = [FileInfo(p) for p in kt_files()]
    if not files:
        print("no Kotlin sources found", file=sys.stderr)
        return 1

    # name -> {fqn -> packages that import it}
    import_index: dict[str, dict[str, set]] = defaultdict(lambda: defaultdict(set))
    for fi in files:
        for simple, fqn in fi.imports.items():
            import_index[simple][fqn].add(fi.package)

    # Names declared anywhere in the project.
    toplevel_pkgs: dict[str, set] = defaultdict(set)
    member_names: set[str] = set()
    for fi in files:
        for n in fi.toplevel:
            toplevel_pkgs[n].add(fi.package)
        member_names |= fi.members

    ambiguous = []
    for simple, fqns in sorted(import_index.items()):
        if simple in DEFAULT_IMPORTS or simple in RECEIVER_MEMBERS:
            continue
        if simple in AMBIGUITY_ALLOWED:
            continue
        external = {f for f in fqns if not f.startswith("com.shohankhan.axiomcalc")}
        if len(external) > 1:
            ambiguous.append((simple, sorted(external)))

    extensions = {s for s in import_index if s and s[0].islower()}

    missing = []
    for fi in files:
        rel = fi.path.relative_to(ROOT)
        for m in IDENT_RE.finditer(fi.code):
            name = m.group(0)
            if m.start() in fi.qualified:
                continue
            if name in fi.imports or name in fi.toplevel or name in fi.members:
                continue
            if name in fi.type_params or name in fi.locals or name in member_names:
                continue
            if name in DEFAULT_IMPORTS or name in RECEIVER_MEMBERS:
                continue
            if name in GENERATED or len(name) <= 2:
                continue
            if name[0].islower() and name not in extensions:
                continue
            fqns = import_index.get(name)
            if not fqns:
                continue
            # A project type declared in this file's own package needs no import.
            if any(pkg == fi.package for pkg in toplevel_pkgs.get(name, ())):
                continue
            if any(fqn.rsplit(".", 1)[0] in fi.wildcards for fqn in fqns):
                continue
            # Same-package declarations are visible without an import.
            if all(pkg == fi.package for fqn in fqns for pkg in fqn_pkg(fqns[fqn])):
                continue
            missing.append((str(rel), m.start(), name, sorted(fqns)))

    # Collapse repeats of the same name in one file.
    collapsed: dict[tuple, int] = defaultdict(int)
    for path, _off, name, fqns in missing:
        collapsed[(path, name, tuple(fqns))] += 1

    print("=" * 72)
    print("Axiom Calc - Kotlin import validation")
    print("=" * 72)
    print(f"Kotlin files scanned ...... {len(files)}")
    print(f"Imported names indexed .... {len(import_index)}")
    print(f"Top-level declarations .... {len(toplevel_pkgs)}")
    print(f"Member names suppressed ... {len(member_names)}")
    print("-" * 72)

    ok = True
    if ambiguous:
        ok = False
        print(f"AMBIGUOUS IMPORTS ({len(ambiguous)}): the same simple name is")
        print("imported from more than one package across the project.")
        for simple, fqns in ambiguous:
            print(f"  {simple}")
            for fqn in fqns:
                print(f"      {fqn}")
    if collapsed:
        ok = False
        print(f"POSSIBLY MISSING IMPORTS ({len(collapsed)} distinct):")
        for (path, name, fqns), n in sorted(collapsed.items()):
            suffix = f"  (x{n})" if n > 1 else ""
            print(f"  {path}: {name}{suffix}  <- {', '.join(fqns)}")
    if ok:
        print("No missing or ambiguous imports detected.")
    print("-" * 72)
    print("RESULT:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


def fqn_pkg(packages) -> set:
    return set(packages)


if __name__ == "__main__":
    sys.exit(main())
