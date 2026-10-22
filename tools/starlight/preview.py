"""
별빛 공방 아이템 미리보기 시트 — node items.js --json 의 출력을 받아 PNG 로 그린다(앱과 같은 non-zero 채움).

  node tools/starlight/items.js --json > out.json
  python tools/starlight/preview.py out.json sheet.png [kind]

앱 파서와 같은 명령(M L H V C S Q T Z)만 읽고, 곡선을 잘게 나눠 직선으로 만든 뒤 화소마다 감김수(winding)를 센다.
"""
import json
import math
import re
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

TOKEN = re.compile(r"[MLHVCSQTZmlhvcsqtz]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")


def flatten(d, steps=24):
    """SVG path → 닫힌 폴리라인 목록."""
    toks = TOKEN.findall(d)
    i = 0
    polys, cur_poly = [], []
    cur = (0.0, 0.0)
    start = (0.0, 0.0)
    cmd = None
    last_c = None
    last_q = None

    def num():
        nonlocal i
        v = float(toks[i])
        i += 1
        return v

    while i < len(toks):
        t = toks[i]
        if re.fullmatch(r"[A-Za-z]", t):
            cmd = t
            i += 1
            if cmd in "Zz":
                if cur_poly:
                    polys.append(cur_poly)
                cur_poly = []
                cur = start
                last_c = last_q = None
                continue
        rel = cmd.islower()
        U = cmd.upper()

        def P(x, y):
            return (cur[0] + x, cur[1] + y) if rel else (x, y)

        if U == "M":
            if cur_poly:
                polys.append(cur_poly)
            cur = P(num(), num())
            start = cur
            cur_poly = [cur]
            cmd = "l" if rel else "L"
            last_c = last_q = None
        elif U == "L":
            cur = P(num(), num())
            cur_poly.append(cur)
            last_c = last_q = None
        elif U == "H":
            x = num()
            cur = (cur[0] + x if rel else x, cur[1])
            cur_poly.append(cur)
            last_c = last_q = None
        elif U == "V":
            y = num()
            cur = (cur[0], cur[1] + y if rel else y)
            cur_poly.append(cur)
            last_c = last_q = None
        elif U in "CS":
            if U == "C":
                c1 = P(num(), num())
            else:
                c1 = (2 * cur[0] - last_c[0], 2 * cur[1] - last_c[1]) if last_c else cur
            c2 = P(num(), num())
            e = P(num(), num())
            p0 = cur
            for k in range(1, steps + 1):
                tt = k / steps
                a = (1 - tt) ** 3
                b = 3 * (1 - tt) ** 2 * tt
                c = 3 * (1 - tt) * tt * tt
                dd = tt ** 3
                cur_poly.append((a * p0[0] + b * c1[0] + c * c2[0] + dd * e[0], a * p0[1] + b * c1[1] + c * c2[1] + dd * e[1]))
            cur = e
            last_c, last_q = c2, None
        elif U in "QT":
            if U == "Q":
                q = P(num(), num())
            else:
                q = (2 * cur[0] - last_q[0], 2 * cur[1] - last_q[1]) if last_q else cur
            e = P(num(), num())
            p0 = cur
            for k in range(1, steps + 1):
                tt = k / steps
                cur_poly.append(((1 - tt) ** 2 * p0[0] + 2 * (1 - tt) * tt * q[0] + tt * tt * e[0],
                                 (1 - tt) ** 2 * p0[1] + 2 * (1 - tt) * tt * q[1] + tt * tt * e[1]))
            cur = e
            last_q, last_c = q, None
        else:
            raise ValueError("unsupported command " + cmd)
    if cur_poly:
        polys.append(cur_poly)
    return polys


def mask(d, size, ss=3):
    """non-zero 채움 마스크(0~1 float, 슈퍼샘플링)."""
    n = size * ss
    ys, xs = np.mgrid[0:n, 0:n]
    px = (xs + 0.5) / n
    py = (ys + 0.5) / n
    wind = np.zeros((n, n), dtype=np.int32)
    for poly in flatten(d):
        pts = poly + [poly[0]]
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            if y0 == y1:
                continue
            cross = (x1 - x0) * (py - y0) - (px - x0) * (y1 - y0)
            up = (y0 <= py) & (py < y1) & (cross > 0)
            down = (y1 <= py) & (py < y0) & (cross < 0)
            wind += up.astype(np.int32) - down.astype(np.int32)
    m = (wind != 0).astype(np.float32)
    return m.reshape(size, ss, size, ss).mean(axis=(1, 3))


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def gradient_fill(size, colors):
    cs = [np.array(hexrgb(c), dtype=np.float32) for c in colors] or [np.array((235, 240, 248), dtype=np.float32)]
    if len(cs) == 1:
        return np.ones((size, size, 3), dtype=np.float32) * cs[0]
    ys, xs = np.mgrid[0:size, 0:size]
    t = (xs + ys) / (2 * (size - 1))
    seg = len(cs) - 1
    out = np.zeros((size, size, 3), dtype=np.float32)
    for k in range(seg):
        lo, hi = k / seg, (k + 1) / seg
        sel = (t >= lo) & (t <= hi)
        u = ((t - lo) / (hi - lo))[sel][:, None]
        out[sel] = cs[k] * (1 - u) + cs[k + 1] * u
    return out


STAR5 = None


def star5():
    pts = []
    for i in range(10):
        r = 0.46 if i % 2 == 0 else 0.2
        a = math.radians(-90 + i * 36)
        pts.append((0.5 + r * math.cos(a), 0.53 + r * math.sin(a)))
    return "M" + "L".join(f"{x:.4f} {y:.4f}" for x, y in pts) + "Z"


def render_cell(item, size):
    bg = np.ones((size, size, 3), dtype=np.float32) * np.array((18, 24, 33), dtype=np.float32)
    kind = item["kind"]
    p = item.get("payload", {})
    svg, colors = None, None
    if kind == "shape":
        svg, colors = p["path"], ["#6EE7B7", "#B7F5DD"]
    elif kind == "color":
        svg, colors = star5(), p["colors"]
    elif kind == "badge":
        svg, colors = p["path"], p.get("colors")
    elif kind == "telescope":
        svg, colors = p.get("icon"), p.get("colors") or ["#C3CAD6"]
    if svg:
        m = mask(svg, size)[..., None]
        fill = gradient_fill(size, colors)
        bg = bg * (1 - m) + fill * m
    return Image.fromarray(bg.clip(0, 255).astype(np.uint8))


def main():
    data = json.load(open(sys.argv[1], encoding="utf-8"))
    out = sys.argv[2]
    only = sys.argv[3] if len(sys.argv) > 3 else None
    items = [it for it in data["items"] if it["kind"] in ("shape", "color", "badge", "telescope") and (not only or it["kind"] == only)]
    size = 180 if only else 120
    cols = 4 if only else 6
    font = ImageFont.truetype("C:/Windows/Fonts/malgun.ttf", 15)
    rows = math.ceil(len(items) / cols)
    W, H = cols * (size + 16) + 16, rows * (size + 44) + 16
    sheet = Image.new("RGB", (W, H), (10, 12, 22))
    dr = ImageDraw.Draw(sheet)
    for idx, it in enumerate(items):
        r, c = divmod(idx, cols)
        x, y = 16 + c * (size + 16), 16 + r * (size + 44)
        sheet.paste(render_cell(it, size), (x, y))
        dr.text((x, y + size + 4), f"{it['name']['ko']} [{it['rarity'][0]}]", fill=(230, 230, 240), font=font)
    sheet.save(out)
    print("saved", out, len(items))


if __name__ == "__main__":
    main()
