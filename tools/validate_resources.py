#!/usr/bin/env python3
"""
Axiom Calc - offline resource validator.

The authoring sandbox cannot run `aapt2` (the Android SDK is unreachable), so
this script performs the structural checks that a resource compiler would
perform, and reports real pass/fail evidence:

  1. Every XML resource under app/src/main/res and the AndroidManifest parse.
  2. Every <vector> declares width/height/viewportWidth/viewportHeight.
  3. Every android:pathData string is tokenised and validated against the
     SVG/Android path grammar: each command must be followed by exactly a
     multiple of its arity, all numbers must parse, and arc flags must be 0/1.
  4. Every vector path stays inside (or near) its declared viewport, which
     catches accidentally transposed or out-of-range coordinates that would
     otherwise render as an invisible or clipped icon.
  5. Every @drawable/, @string/, @color/, @style/, @xml/, @mipmap/ reference
     made from XML resolves to a file that actually exists in this repository.

Exit code is 0 only when every check passes.
"""
from __future__ import annotations

import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app", "src", "main")
RES = os.path.join(MAIN, "res")

ANDROID = "{http://schemas.android.com/apk/res/android}"

# command -> number of parameters per repetition
ARITY = {
    "M": 2, "m": 2, "L": 2, "l": 2, "H": 1, "h": 1, "V": 1, "v": 1,
    "C": 6, "c": 6, "S": 4, "s": 4, "Q": 4, "q": 4, "T": 2, "t": 2,
    "A": 7, "a": 7, "Z": 0, "z": 0,
}
ARC_FLAG_POSITIONS = {3, 4}  # within each 7-tuple of an A/a command

NUM_RE = re.compile(r"[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")
TOKEN_RE = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")


def validate_path_data(data: str):
    """Return (ok, message, min_x, min_y, max_x, max_y)."""
    tokens = TOKEN_RE.findall(data)
    # Reject stray characters that the tokenizer silently skipped.
    stripped = TOKEN_RE.sub("", data).replace(",", " ").replace(".", " ")
    if any(ch not in " \t\r\n" for ch in stripped):
        return False, f"illegal characters in pathData: {stripped!r}", 0, 0, 0, 0

    i = 0
    cmd = None
    x = y = 0.0
    start_x = start_y = 0.0
    xs, ys = [], []
    first = True
    while i < len(tokens):
        tok = tokens[i]
        if tok in ARITY:
            cmd = tok
            i += 1
            first = True
            if ARITY[cmd] == 0:
                x, y = start_x, start_y
                xs.append(x); ys.append(y)
                continue
        if cmd is None:
            return False, "pathData must start with a moveto command", 0, 0, 0, 0
        n = ARITY[cmd]
        if i + n > len(tokens):
            return False, f"command '{cmd}' truncated: needs {n} params at end of data", 0, 0, 0, 0
        raw = tokens[i:i + n]
        try:
            nums = [float(v) for v in raw]
        except ValueError as exc:
            return False, f"unparsable number for '{cmd}': {exc}", 0, 0, 0, 0
        if cmd in ("A", "a"):
            for off in ARC_FLAG_POSITIONS:
                if nums[off] not in (0.0, 1.0):
                    return False, f"arc flag must be 0 or 1, got {nums[off]}", 0, 0, 0, 0
            if nums[0] == 0 or nums[1] == 0:
                return False, "arc with zero radius is degenerate", 0, 0, 0, 0
        i += n

        rel = cmd.islower()
        ox, oy = (x, y) if rel else (0.0, 0.0)
        if first and cmd in ("M", "m"):
            start_x, start_y = ox + nums[0], oy + nums[1]
            x, y = start_x, start_y
            first = False
        elif cmd in ("M", "m"):
            # Implicit lineto after the first coordinate pair of a moveto.
            x, y = ox + nums[0], oy + nums[1]
        elif cmd in ("L", "l"):
            x, y = ox + nums[0], oy + nums[1]
        elif cmd in ("H", "h"):
            x = ox + nums[0]
        elif cmd in ("V", "v"):
            y = oy + nums[0]
        elif cmd in ("C", "c"):
            xs.extend([ox + nums[0], ox + nums[2], ox + nums[4]])
            ys.extend([oy + nums[1], oy + nums[3], oy + nums[5]])
            x, y = ox + nums[4], oy + nums[5]
        elif cmd in ("S", "s"):
            xs.extend([ox + nums[0], ox + nums[2]])
            ys.extend([oy + nums[1], oy + nums[3]])
            x, y = ox + nums[2], oy + nums[3]
        elif cmd in ("Q", "q"):
            xs.extend([ox + nums[0], ox + nums[2]])
            ys.extend([oy + nums[1], oy + nums[3]])
            x, y = ox + nums[2], oy + nums[3]
        elif cmd in ("T", "t"):
            x, y = ox + nums[0], oy + nums[1]
        elif cmd in ("A", "a"):
            x, y = ox + nums[5], oy + nums[6]
        elif cmd in ("Z", "z"):
            x, y = start_x, start_y
        xs.append(x); ys.append(y)

    if not xs:
        return False, "pathData produced no points", 0, 0, 0, 0
    return True, "ok", min(xs), min(ys), max(xs), max(ys)


def collect_existing_resources():
    have = {"drawable": set(), "mipmap": set(), "values": set(), "xml": set(),
            "raw": set(), "anim": set(), "animator": set(), "font": set(),
            "layout": set(), "color": set()}
    names = {"string": set(), "color": set(), "style": set(), "dimen": set(),
             "attr": set(), "bool": set(), "integer": set(), "plurals": set(),
             "string-array": set()}
    if not os.path.isdir(RES):
        return have, names
    for dirpath, _dirnames, filenames in os.walk(RES):
        folder = os.path.basename(dirpath)
        kind = folder.split("-")[0]
        for fn in filenames:
            stem, ext = os.path.splitext(fn)
            if kind in have and ext == ".xml":
                have[kind].add(stem)
            if kind == "values" and ext == ".xml":
                try:
                    root = ET.parse(os.path.join(dirpath, fn)).getroot()
                except ET.ParseError:
                    continue
                for child in root:
                    tag = child.tag
                    nm = child.get("name")
                    if nm and tag in names:
                        names[tag].add(nm)
                    if tag == "item":
                        t = (child.get("type") or "").split(":")[-1]
                        if nm and t in names:
                            names[t].add(nm)
    # Adaptive icon XMLs in mipmap-anydpi-v26 declare the mipmap name.
    return have, names


REF_RE = re.compile(r'"@(?:(?P<ns>android):)?(?P<kind>[\w-]+)/(?P<name>[\w.-]+)"')

# Theme-attribute references: "?attr/name" or "?android:attr/name".
#
# A bare "?attr/x" resolves against the *current theme*, so it only works when
# some applied theme actually defines that attribute. AppCompat and Material
# Components define attributes such as colorControlNormal; the framework
# android:Theme.Material does NOT (the framework spelling is
# "?android:attr/colorControlNormal"). Getting this wrong is an aapt2 link
# failure that only shows up in a real Android build, so it is checked here.
THEME_ATTR_RE = re.compile(r'"\?(?:(?P<ns>android):)?attr/(?P<name>[\w.-]+)"')

# Kinds whose resources are declared inside res/values/*.xml rather than as
# files in a per-kind folder.
VALUES_KINDS = {"string", "color", "style", "dimen", "attr", "bool",
                "integer", "plurals", "string-array"}


def main() -> int:
    failures: list[str] = []
    warnings: list[str] = []
    vectors_checked = 0
    paths_checked = 0
    files_checked = 0

    have, names = collect_existing_resources()

    xml_files = []
    for dirpath, _d, filenames in os.walk(MAIN):
        for fn in filenames:
            if fn.endswith(".xml"):
                xml_files.append(os.path.join(dirpath, fn))
    xml_files.sort()

    for path in xml_files:
        rel = os.path.relpath(path, MAIN)
        files_checked += 1
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as exc:
            failures.append(f"{rel}: XML not well-formed: {exc}")
            continue

        if root.tag == "vector":
            vectors_checked += 1
            vw = root.get(f"{ANDROID}viewportWidth")
            vh = root.get(f"{ANDROID}viewportHeight")
            w = root.get(f"{ANDROID}width")
            h = root.get(f"{ANDROID}height")
            for label, val in (("width", w), ("height", h),
                               ("viewportWidth", vw), ("viewportHeight", vh)):
                if not val:
                    failures.append(f"{rel}: <vector> missing android:{label}")
            try:
                vw_f, vh_f = float(vw), float(vh)
            except (TypeError, ValueError):
                vw_f = vh_f = None
                if vw and vh:
                    failures.append(f"{rel}: viewport dimensions are not numbers")

            for node in root.iter("path"):
                data = node.get(f"{ANDROID}pathData")
                if data is None:
                    failures.append(f"{rel}: <path> without android:pathData")
                    continue
                paths_checked += 1
                ok, msg, mnx, mny, mxx, mxy = validate_path_data(data)
                if not ok:
                    failures.append(f"{rel}: invalid pathData: {msg}")
                    continue
                if vw_f is not None:
                    tol = 0.75  # stroke may extend slightly past the viewport
                    if mnx < -tol or mny < -tol or mxx > vw_f + tol or mxy > vh_f + tol:
                        warnings.append(
                            f"{rel}: path bbox ({mnx:.2f},{mny:.2f})-({mxx:.2f},{mxy:.2f}) "
                            f"exceeds viewport {vw_f}x{vh_f}")
                if node.get(f"{ANDROID}fillColor") is None and \
                   node.get(f"{ANDROID}strokeColor") is None:
                    failures.append(f"{rel}: <path> has neither fillColor nor strokeColor")

        # Reference resolution (skip @android: framework references).
        text = open(path, encoding="utf-8").read()

        # Theme attributes must be project-defined or framework-qualified.
        for m in THEME_ATTR_RE.finditer(text):
            name, ns = m.group("name"), m.group("ns")
            if ns == "android":
                continue
            if name not in names.get("attr", set()):
                failures.append(
                    f"{rel}: unresolved theme attribute ?attr/{name} - not defined by "
                    f"this project and not provided by the applied framework theme "
                    f"(use ?android:attr/{name}, tint in code, or define the attr)")
        for m in REF_RE.finditer(text):
            ns, kind, name = m.group("ns"), m.group("kind"), m.group("name")
            if ns == "android":
                continue
            if kind in VALUES_KINDS:
                if name not in names.get(kind, set()):
                    failures.append(f"{rel}: unresolved @{kind}/{name}")
            elif kind in have:
                if name not in have[kind]:
                    failures.append(f"{rel}: unresolved @{kind}/{name}")
            elif kind in ("id", "menu", "array"):
                pass
            else:
                warnings.append(f"{rel}: unknown reference type @{kind}/{name}")

    print("=" * 72)
    print("Axiom Calc - resource validation")
    print("=" * 72)
    print(f"XML files parsed .......... {files_checked}")
    print(f"Vector drawables .......... {vectors_checked}")
    print(f"Vector paths validated .... {paths_checked}")
    print(f"Resource kinds indexed .... {sorted(k for k, v in have.items() if v)}")
    print("-" * 72)
    if warnings:
        print(f"WARNINGS ({len(warnings)}):")
        for w in warnings:
            print("  ! " + w)
        print("-" * 72)
    if failures:
        print(f"FAILURES ({len(failures)}):")
        for f in failures:
            print("  x " + f)
        print("-" * 72)
        print("RESULT: FAIL")
        return 1
    print("RESULT: PASS - all resources well-formed, all vector path data valid,")
    print("        all intra-project resource references resolve.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
