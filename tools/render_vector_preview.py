#!/usr/bin/env python3
"""
Axiom Calc - vector drawable contact-sheet renderer (development aid).

The sandbox has no `aapt2` and no Android device, so the usual way of checking
that an icon is not invisible or mangled (render it on a device) is unavailable.
This tool flattens every res/drawable/*.xml path (M L H V C S Q T A Z) and
rasters it with the same semantics Android uses (non-zero winding fill,
round-capped joins and caps for stroked paths) into a single labelled contact
sheet PNG so a human can eyeball the whole icon set at once.

Pure standard library. Not part of the shipped application; it only inspects it.

Usage:  python3 tools/render_vector_preview.py [out.png]
"""
from __future__ import annotations

import glob
import math
import os
import re
import struct
import sys
import xml.etree.ElementTree as ET
import zlib

A = "{http://schemas.android.com/apk/res/android}"
TOKEN_RE = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")
ARITY = {"M": 2, "m": 2, "L": 2, "l": 2, "H": 1, "h": 1, "V": 1, "v": 1,
         "C": 6, "c": 6, "S": 4, "s": 4, "Q": 4, "q": 4, "T": 2, "t": 2,
         "A": 7, "a": 7, "Z": 0, "z": 0}


# --------------------------------------------------------------------------- #
# path parsing / flattening
# --------------------------------------------------------------------------- #
def flatten(data):
    """-> list of subpaths; each subpath = (points, closed) with curves sampled."""
    toks = TOKEN_RE.findall(data)
    subs, pts, closed = [], [], False
    x = y = sx = sy = 0.0
    cx = cy = 0.0          # last control point (for S/T reflection)
    last_cmd = None
    i, cmd = 0, None

    def push(px, py):
        pts.append((px, py))

    def sample_cubic(x0, y0, c1x, c1y, c2x, c2y, x1, y1, n=24):
        for t_i in range(1, n + 1):
            t = t_i / n
            u = 1 - t
            bx = u**3 * x0 + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t**3 * x1
            by = u**3 * y0 + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t**3 * y1
            push(bx, by)

    def sample_quad(x0, y0, qx, qy, x1, y1, n=24):
        for t_i in range(1, n + 1):
            t = t_i / n
            u = 1 - t
            push(u * u * x0 + 2 * u * t * qx + t * t * x1,
                 u * u * y0 + 2 * u * t * qy + t * t * y1)

    def sample_arc(x0, y0, rx, ry, phi, laf, sf, x1, y1, n=32):
        if rx == 0 or ry == 0:
            push(x1, y1)
            return
        phi = math.radians(phi)
        cosp, sinp = math.cos(phi), math.sin(phi)
        dx, dy = (x0 - x1) / 2, (y0 - y1) / 2
        x1p = cosp * dx + sinp * dy
        y1p = -sinp * dx + cosp * dy
        rx, ry = abs(rx), abs(ry)
        lam = (x1p / rx) ** 2 + (y1p / ry) ** 2
        if lam > 1:
            s = math.sqrt(lam)
            rx, ry = rx * s, ry * s
        num = max(0.0, rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p)
        den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        coef = math.sqrt(num / den) if den else 0.0
        if laf == sf:
            coef = -coef
        cxp, cyp = coef * rx * y1p / ry, -coef * ry * x1p / rx
        cx = cosp * cxp - sinp * cyp + (x0 + x1) / 2
        cy = sinp * cxp + cosp * cyp + (y0 + y1) / 2
        def ang(vx, vy):
            return math.atan2(vy, vx)
        th0 = ang((x1p - cxp) / rx, (y1p - cyp) / ry)
        dth = ang((-x1p - cxp) / rx, (-y1p - cyp) / ry) - th0
        if sf == 0 and dth > 0:
            dth -= 2 * math.pi
        if sf == 1 and dth < 0:
            dth += 2 * math.pi
        for t_i in range(1, n + 1):
            th = th0 + dth * t_i / n
            px = cosp * rx * math.cos(th) - sinp * ry * math.sin(th) + cx
            py = sinp * rx * math.cos(th) + cosp * ry * math.sin(th) + cy
            push(px, py)

    while i < len(toks):
        if toks[i] in ARITY:
            cmd = toks[i]
            i += 1
        n = ARITY[cmd]
        nums = [float(v) for v in toks[i:i + n]]
        i += n
        rel = cmd.islower()
        ox, oy = (x, y) if rel else (0.0, 0.0)
        if cmd in ("M", "m"):
            if pts:
                subs.append((pts, closed))
            pts, closed = [], False
            x, y = ox + nums[0], oy + nums[1]
            sx, sy = x, y
            push(x, y)
            cx, cy = x, y
        elif cmd in ("L", "l"):
            x, y = ox + nums[0], oy + nums[1]
            push(x, y)
        elif cmd in ("H", "h"):
            x = ox + nums[0]
            push(x, y)
        elif cmd in ("V", "v"):
            y = oy + nums[0]
            push(x, y)
        elif cmd in ("C", "c"):
            sample_cubic(x, y, ox + nums[0], oy + nums[1],
                         ox + nums[2], oy + nums[3], ox + nums[4], oy + nums[5])
            cx, cy = ox + nums[2], oy + nums[3]
            x, y = ox + nums[4], oy + nums[5]
        elif cmd in ("S", "s"):
            sample_cubic(x, y, 2 * x - cx, 2 * y - cy, ox + nums[0], oy + nums[1],
                         ox + nums[2], oy + nums[3])
            cx, cy = ox + nums[0], oy + nums[1]
            x, y = ox + nums[2], oy + nums[3]
        elif cmd in ("Q", "q"):
            sample_quad(x, y, ox + nums[0], oy + nums[1], ox + nums[2], oy + nums[3])
            cx, cy = ox + nums[0], oy + nums[1]
            x, y = ox + nums[2], oy + nums[3]
        elif cmd in ("T", "t"):
            qx, qy = 2 * x - cx, 2 * y - cy
            sample_quad(x, y, qx, qy, ox + nums[0], oy + nums[1])
            cx, cy = qx, qy
            x, y = ox + nums[0], oy + nums[1]
        elif cmd in ("A", "a"):
            sample_arc(x, y, nums[0], nums[1], nums[2], nums[3], nums[4],
                       ox + nums[5], oy + nums[6])
            x, y = ox + nums[5], oy + nums[6]
        elif cmd in ("Z", "z"):
            x, y = sx, sy
            closed = True
        last_cmd = cmd
    if pts:
        subs.append((pts, closed))
    return subs


# --------------------------------------------------------------------------- #
# raster
# --------------------------------------------------------------------------- #
class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[0.0] * w for _ in range(h)]

    def blend(self, x, y, cov, rgb=(255, 255, 255)):
        if 0 <= x < self.w and 0 <= y < self.h and cov > 0:
            self.px[y][x] = max(self.px[y][x], cov)

    def fill_subpaths(self, subs, scale):
        # Non-zero winding scanline fill over the flattened geometry.
        edges = []
        for pts, _closed in subs:
            if len(pts) < 3:
                continue
            ring = [(p[0] * scale, p[1] * scale) for p in pts]
            ring.append(ring[0])
            for k in range(len(ring) - 1):
                (x0, y0), (x1, y1) = ring[k], ring[k + 1]
                if y0 != y1:
                    edges.append((y0, y1, x0, x1))
        h = self.h
        for y in range(h):
            yc = y + 0.5
            xs = []
            for (y0, y1, x0, x1) in edges:
                if (y0 <= yc < y1) or (y1 <= yc < y0):
                    t = (yc - y0) / (y1 - y0)
                    xs.append((x0 + t * (x1 - x0), 1 if y1 > y0 else -1))
            xs.sort()
            winding = 0
            for idx in range(len(xs) - 1):
                winding += xs[idx][1]
                if winding != 0:
                    x_start, x_end = xs[idx][0], xs[idx + 1][0]
                    x0i = int(math.floor(x_start))
                    x1i = int(math.ceil(x_end))
                    for xi in range(max(0, x0i), min(self.w, x1i + 1)):
                        cov = min(1.0, max(0.0, min(xi + 1, x_end) - max(xi, x_start)))
                        if cov > 0:
                            self.blend(xi, y, cov)

    def stroke_subpaths(self, subs, scale, width):
        half = width * scale / 2.0
        segs = []
        for pts, closed in subs:
            ring = list(pts) + ([pts[0]] if closed else [])
            for k in range(len(ring) - 1):
                segs.append((ring[k][0] * scale, ring[k][1] * scale,
                             ring[k + 1][0] * scale, ring[k + 1][1] * scale))
        if not segs:
            return
        xs = [c for s in segs for c in (s[0], s[2])]
        ys = [c for s in segs for c in (s[1], s[3])]
        x0 = max(0, int(min(xs) - half - 1))
        x1 = min(self.w - 1, int(max(xs) + half + 1))
        y0 = max(0, int(min(ys) - half - 1))
        y1 = min(self.h - 1, int(max(ys) + half + 1))
        for py in range(y0, y1 + 1):
            for px in range(x0, x1 + 1):
                gx, gy = px + 0.5, py + 0.5
                d = min(_seg_dist(gx, gy, *s) for s in segs)
                cov = max(0.0, min(1.0, 0.5 + (half - d)))
                if cov > 0:
                    self.blend(px, py, cov)


def _seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    if dx == 0 and dy == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - ax - t * dx, py - ay - t * dy)


def render_icon(path, size=96):
    root = ET.parse(path).getroot()
    vw = float(root.get(f"{A}viewportWidth"))
    canvas = Canvas(size, size)
    scale = size / vw
    for node in root.iter("path"):
        data = node.get(f"{A}pathData")
        if not data:
            continue
        subs = flatten(data)
        stroke_w = node.get(f"{A}strokeWidth")
        has_fill = node.get(f"{A}fillColor") not in (None, "#00000000", "#000000")
        has_stroke = node.get(f"{A}strokeColor") not in (None, "#00000000")
        if has_fill:
            canvas.fill_subpaths(subs, scale)
        if has_stroke and stroke_w:
            canvas.stroke_subpaths(subs, scale, float(stroke_w))
    return canvas


def write_png(path, w, h, get):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(get(x, y))
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data +
                struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    png = (b"\x89PNG\r\n\x1a\n" +
           chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) +
           chunk(b"IDAT", zlib.compress(bytes(raw), 9)) +
           chunk(b"IEND", b""))
    open(path, "wb").write(png)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/icon_sheet.png"
    files = sorted(glob.glob(os.path.join(os.path.dirname(
        os.path.dirname(os.path.abspath(__file__))),
        "app/src/main/res/drawable/*.xml")))
    files = [f for f in files if "ic_launcher" not in os.path.basename(f)]
    cell, pad, cols = 96, 10, 7
    rows = math.ceil(len(files) / cols)
    W = cols * (cell + pad) + pad
    H = rows * (cell + pad) + pad
    sheet = Canvas(W, H)
    bg = Canvas(W, H)
    for idx, f in enumerate(files):
        icon = render_icon(f)
        r, c = divmod(idx, cols)
        ox, oy = pad + c * (cell + pad), pad + r * (cell + pad)
        # dark tile background so white icons are visible
        for yy in range(cell):
            for xx in range(cell):
                sheet.px[oy + yy][ox + xx] = -1.0  # marker: background pixel
        for yy in range(cell):
            for xx in range(cell):
                a = icon.px[yy][xx]
                if a > 0:
                    sheet.px[oy + yy][ox + xx] = a
        print(f"{os.path.basename(f):<28} ink={sum(1 for row in icon.px for v in row if v > 0.02):>5}px")

    def get(x, y):
        v = sheet.px[y][x]
        if v < 0:
            return (24, 28, 36, 255)          # tile background
        return (255, 255, 255, int(round(v * 255)))
    write_png(out, W, H, get)
    print(f"\nwrote {out}  ({W}x{H}, {len(files)} icons)")


if __name__ == "__main__":
    main()
