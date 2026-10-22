#!/usr/bin/env python3
"""
Axiom Calc - launcher icon generator.

The authoring sandbox has no Android SDK, so `aapt2`, `vector2png` and Android
Studio's Image Asset tool are unavailable. This script produces the raster
launcher icons deterministically instead, with no third-party dependency
(pure standard library: zlib + struct).

Icon design
-----------
  * Background : #0B0E13 deep graphite (the app's dark surface token).
  * Mark       : a bold Greek sigma, #38BDF8 restrained cyan accent.
                 The sigma is the same glyph used by the Scientific tab, so the
                 launcher mark and the in-app identity agree.

Geometry is defined once in a 108x108 space (the Android adaptive-icon canvas).
The safe zone for adaptive icons is a centred circle of diameter 66, so every
control point is kept within a radius of 33 from (54, 54) and the script
asserts that invariant before writing anything.

Outputs
-------
  res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png         (48,72,96,144,192)
  res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_round.png   (circular mask)
  res/drawable/ic_launcher_foreground.xml                 (vector, 108dp)

Run:  python3 tools/generate_launcher_icons.py
Verify: python3 tools/validate_resources.py
"""
from __future__ import annotations

import math
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

CANVAS = 108.0
CENTRE = CANVAS / 2.0
SAFE_RADIUS = 33.0

BG = (0x0B, 0x0E, 0x13, 255)
FG = (0x38, 0xBD, 0xF8, 255)

# Sigma polyline in 108-space: top bar, down-left diagonal, up-right diagonal,
# bottom bar.
SIGMA = [(73.0, 35.0), (41.0, 35.0), (59.0, 54.0), (41.0, 73.0), (73.0, 73.0)]
SIGMA_WIDTH = 8.5

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


# --------------------------------------------------------------------------- #
# distance fields
# --------------------------------------------------------------------------- #
def dist_segment(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    if dx == 0.0 and dy == 0.0:
        return math.hypot(px - ax, py - ay)
    t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
    t = max(0.0, min(1.0, t))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def dist_polyline(px, py, pts, width):
    half = width / 2.0
    d = min(dist_segment(px, py, pts[i][0], pts[i][1],
                         pts[i + 1][0], pts[i + 1][1])
            for i in range(len(pts) - 1))
    # Signed: negative inside the stroke.
    return d - half


def dist_rounded_rect(px, py, cx, cy, hw, hh, r):
    dx = abs(px - cx) - (hw - r)
    dy = abs(py - cy) - (hh - r)
    ax, ay = max(dx, 0.0), max(dy, 0.0)
    outside = math.hypot(ax, ay)
    inside = min(max(dx, dy), 0.0)
    return outside + inside - r


def clamp01(v):
    return 0.0 if v < 0.0 else (1.0 if v > 1.0 else v)


def coverage(sdf):
    """Analytic 1px-wide antialiasing from a signed distance field."""
    return clamp01(0.5 - sdf)


# --------------------------------------------------------------------------- #
# PNG encoder (8-bit RGBA, non-interlaced, no third-party dependency)
# --------------------------------------------------------------------------- #
def write_png(path, size, pixels):
    raw = bytearray()
    for y in range(size):
        raw.append(0)  # filter type 0 (None) per scanline
        row = pixels[y]
        raw.extend(row)
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data +
                struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    png = (b"\x89PNG\r\n\x1a\n" +
           chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)) +
           chunk(b"IDAT", zlib.compress(bytes(raw), 9)) +
           chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)
    return len(png)


# --------------------------------------------------------------------------- #
# rendering
# --------------------------------------------------------------------------- #
def render(size, circular):
    """Render one icon at `size` px. Returns a list of bytearrays (rows)."""
    scale = size / CANVAS
    rows = []
    for py in range(size):
        row = bytearray()
        for px in range(size):
            # Sample the centre of the pixel, mapped back into 108-space.
            gx = (px + 0.5) / scale
            gy = (py + 0.5) / scale

            if circular:
                bg_cov = coverage(math.hypot(gx - CENTRE, gy - CENTRE) - CANVAS / 2.0)
            else:
                # Legacy squircle-ish rounded square, radius 18% of the canvas.
                r = CANVAS * 0.18
                bg_cov = coverage(dist_rounded_rect(
                    gx, gy, CENTRE, CENTRE, CANVAS / 2.0, CANVAS / 2.0, r))

            fg_cov = coverage(dist_polyline(gx, gy, SIGMA, SIGMA_WIDTH))

            if bg_cov <= 0.0 and fg_cov <= 0.0:
                row.extend((0, 0, 0, 0))
                continue

            # Composite the accent over the graphite background.
            a_fg = fg_cov * bg_cov
            a_bg = bg_cov * (1.0 - a_fg)
            alpha = a_fg + a_bg
            if alpha <= 0.0:
                row.extend((0, 0, 0, 0))
                continue
            out = []
            for c in range(3):
                v = (FG[c] * a_fg + BG[c] * a_bg) / alpha
                out.append(int(round(max(0, min(255, v)))))
            row.extend((out[0], out[1], out[2], int(round(alpha * 255))))
        rows.append(row)
    return rows


def foreground_vector_xml():
    d = "M73,35L41,35L59,54L41,73L73,73"
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Adaptive launcher foreground for Axiom Calc.
  Generated by tools/generate_launcher_icons.py; kept in source control so the
  icon is reproducible without any image tooling.

  Geometry is defined on the 108dp adaptive-icon canvas. Every control point
  lies inside the 66dp centred safe zone, so the mark survives the circular,
  squircle and rounded-square masks that launchers apply.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FF38BDF8"
        android:strokeWidth="{SIGMA_WIDTH}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="{d}" />
</vector>
'''


def adaptive_icon_xml():
    return '''<?xml version="1.0" encoding="utf-8"?>
<!--
  Adaptive launcher icon (API 26+). The background is a flat colour resource so
  no bitmap is needed for the background layer; the foreground is the vector
  sigma mark. Raster fallbacks for API 24-25 live in res/mipmap-*dpi/.
-->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''


def main() -> int:
    # Safety invariant: mark must fit the adaptive-icon safe zone.
    half = SIGMA_WIDTH / 2.0
    for (x, y) in SIGMA:
        d = math.hypot(x - CENTRE, y - CENTRE) + half
        if d > SAFE_RADIUS + 0.001:
            print(f"FAIL: sigma point ({x},{y}) reaches {d:.2f} > safe radius "
                  f"{SAFE_RADIUS}", file=sys.stderr)
            return 1
    print(f"safe-zone check: max reach "
          f"{max(math.hypot(x-CENTRE, y-CENTRE)+half for x, y in SIGMA):.2f} "
          f"<= {SAFE_RADIUS}  OK")

    total_bytes = 0
    written = []
    for circular, name in ((False, "ic_launcher"), (True, "ic_launcher_round")):
        for density, size in DENSITIES.items():
            rows = render(size, circular)
            path = os.path.join(RES, f"mipmap-{density}", f"{name}.png")
            n = write_png(path, size, rows)
            total_bytes += n
            written.append((os.path.relpath(path, ROOT), size, n))

    fg = os.path.join(RES, "drawable", "ic_launcher_foreground.xml")
    open(fg, "w", encoding="utf-8").write(foreground_vector_xml())
    written.append((os.path.relpath(fg, ROOT), 108, os.path.getsize(fg)))

    os.makedirs(os.path.join(RES, "mipmap-anydpi-v26"), exist_ok=True)
    for name in ("ic_launcher", "ic_launcher_round"):
        p = os.path.join(RES, "mipmap-anydpi-v26", f"{name}.xml")
        open(p, "w", encoding="utf-8").write(adaptive_icon_xml())
        written.append((os.path.relpath(p, ROOT), "adaptive", os.path.getsize(p)))

    print("-" * 68)
    for rel, size, n in written:
        print(f"  {rel:<52} {str(size):>6}px {n:>7}B")
    print("-" * 68)
    print(f"wrote {len(written)} icon assets, {total_bytes} bytes of PNG data")
    return 0


if __name__ == "__main__":
    sys.exit(main())
