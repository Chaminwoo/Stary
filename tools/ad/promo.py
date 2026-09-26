"""Stary 앱 광고 — 앱 화면 녹화 기반 3종 × 15·30초 × 한국어·영어 = 12편 (2026-09-26).

  showcase  기능 쇼케이스 — 지도 별 → 상세/좋아요 → 별자리 → 별 도감 → 지구본 → 엔드카드
  pov       POV 쇼츠 — "100m 안에서만 열리는 일기" 잠김 → 직접 가 봄(거리 카운트다운) → 해금
  collect   수집·게임화 — 별 모양 21종 그리드(코드 렌더) → 업적 → 해금 카드 → 모으기 → 프로필

소스: references/apprecord.mp4 (사용자 실기기 녹화 1080x2340, 상태바/내비바는 잘라낸다)
출력: references/ads/stary_{concept}_{15|30}_{ko|en}.mp4 (1080x1920 · 30fps · H.264 + AAC, 쇼츠/릴스/틱톡 공용)
      ⚠️ mp4 는 커밋하지 않는다(대용량 — 다른 광고 영상과 같은 취급).

    python tools/ad/promo.py                       # 12편 전부(병렬)
    python tools/ad/promo.py --only showcase_15_ko pov_30_en
    python tools/ad/promo.py --stills showcase_15_ko 0.6 2.5 5.5   # 검수용 PNG

개인정보: 녹화에 찍힌 다른 사람 이름(작성자 줄·별 도감 "OO님의 별"·카드 작성자)은 흐리게, 프로필 이름은
가명으로 바꿔 그린다([MASKS]). BGM 은 앱 자체 음원(tools/ad/bgm_analyze.py 로 템포·에너지 분석 → 컷을 박자에 맞춤).
"""
from __future__ import annotations

import argparse
import math
import random
import subprocess
import sys
from dataclasses import dataclass, field
from functools import lru_cache
from multiprocessing import Pool
from pathlib import Path
from typing import Callable

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "references/apprecord.mp4"
RAW = ROOT / "androidApp/src/main/res/raw"
FONT = ROOT / "androidApp/src/main/res/font/min_sans.ttf"
LOGO = ROOT / "iosApp/Sources/Resources/logo.png"
OUT = ROOT / "references/ads"
WORK = ROOT / "build/promo"

W, H, FPS = 1080, 1920, 30
# 녹화 원본에서 쓸 영역 — 상태바(녹화 표시 포함)와 내비게이션 바를 잘라낸다.
CROP_Y, CROP_H = 116, 2080

MINT = (110, 231, 183)
GOLD = (255, 205, 96)
PINK = (244, 143, 177)
SKY = (120, 190, 250)
WHITE = (255, 255, 255)
SUB = (196, 206, 230)


# ─────────────────────────── 기본 수학 ───────────────────────────

def clamp(x, a=0.0, b=1.0):
    return a if x < a else b if x > b else x


def lerp(a, b, u):
    return a + (b - a) * u


def ease_out(u):
    u = clamp(u)
    return 1 - (1 - u) ** 3


def ease_in_out(u):
    u = clamp(u)
    return 4 * u * u * u if u < 0.5 else 1 - (-2 * u + 2) ** 3 / 2


def ease_back(u, s=1.7):
    u = clamp(u)
    return 1 + (s + 1) * (u - 1) ** 3 + s * (u - 1) ** 2


# ─────────────────────────── 글꼴 ───────────────────────────

@lru_cache(maxsize=64)
def font(size: int, weight: int = 700) -> ImageFont.FreeTypeFont:
    f = ImageFont.truetype(str(FONT), size)
    f.set_variation_by_axes([weight])
    return f


def parse_markup(text: str):
    """"지도 위 [별]" → [("지도 위 ", False), ("별", True)] — [] 안은 강조색."""
    out, buf, hl = [], "", False
    for ch in text:
        if ch in "[]":
            if buf:
                out.append((buf, hl))
            buf, hl = "", ch == "["
        else:
            buf += ch
    if buf:
        out.append((buf, hl))
    return out


def render_line(text, size, weight, color, accent, stroke=0, glow=True) -> Image.Image:
    """한 줄 글자 → RGBA(글로우 포함). 강조([..])는 accent 색."""
    f = font(size, weight)
    parts = parse_markup(text)
    widths = [f.getlength(p) for p, _ in parts]
    tw = int(sum(widths)) + 2 * stroke + 8
    asc, desc = f.getmetrics()
    th = asc + desc + 2 * stroke + 8
    pad = 40
    im = Image.new("RGBA", (tw + 2 * pad, th + 2 * pad), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    x = pad + stroke + 4
    for (p, hl), w in zip(parts, widths):
        d.text((x, pad + stroke + 4), p, font=f, fill=accent if hl else color,
               stroke_width=stroke, stroke_fill=(0, 0, 0, 255))
        x += w
    if glow and not stroke:
        a = im.getchannel("A").filter(ImageFilter.GaussianBlur(14))
        shadow = Image.new("RGBA", im.size, (0, 0, 0, 0))
        shadow.putalpha(a.point(lambda v: int(v * 0.75)))
        shadow.alpha_composite(im)
        im = shadow
    return im


def with_alpha(im: Image.Image, a: float) -> Image.Image:
    if a >= 0.999:
        return im
    arr = np.array(im)
    arr[..., 3] = (arr[..., 3].astype(np.float32) * a).astype(np.uint8)
    return Image.fromarray(arr)


# ─────────────────────────── 배경(밤하늘) ───────────────────────────

class Background:
    def __init__(self, seed=7, tint=(0, 0, 0)):
        rng = np.random.default_rng(seed)
        y = np.linspace(0, 1, H)[:, None]
        top = np.array([5, 7, 16], np.float32)
        bot = np.array([13, 17, 38], np.float32)
        base = (top + (bot - top) * y[..., None]) * np.ones((1, W, 1), np.float32)
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        for cx, cy, r, col, a in ((250, 520, 520, (60, 110, 190), 0.20), (860, 1250, 620, (120, 70, 170), 0.16),
                                   (420, 1650, 480, (40, 150, 130), 0.10)):
            g = np.exp(-(((xx - cx) ** 2 + (yy - cy) ** 2) / (2 * r * r)))
            base += g[..., None] * np.array(col, np.float32) * a
        base += np.array(tint, np.float32)
        self.base = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8)).convert("RGBA")
        n = 170
        self.stars = [(float(rng.uniform(0, W)), float(rng.uniform(0, H)), float(rng.uniform(0.6, 2.4)),
                       float(rng.uniform(0, 6.28)), float(rng.uniform(0.8, 2.6)), float(rng.uniform(0.2, 1.0)))
                      for _ in range(n)]

    def frame(self, t) -> Image.Image:
        im = self.base.copy()
        d = ImageDraw.Draw(im)
        for x, y, s, ph, sp, depth in self.stars:
            a = 0.30 + 0.70 * (0.5 + 0.5 * math.sin(t * sp + ph))
            yy = (y - t * 7 * depth) % H
            v = int(255 * a * (0.5 + 0.5 * depth))
            r = s * (0.8 + 0.2 * a)
            d.ellipse((x - r, yy - r, x + r, yy + r), fill=(v, v, min(255, v + 18), 255))
        return im


@lru_cache(maxsize=1)
def top_scrim() -> Image.Image:
    a = np.zeros((H, W), np.float32)
    ys = np.arange(560)
    a[:560] = (0.78 * (1 - ys / 560) ** 1.6)[:, None]
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    im.putalpha(Image.fromarray((a * 255).astype(np.uint8)))
    return im


@lru_cache(maxsize=1)
def vignette() -> Image.Image:
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt(((xx - W / 2) / (W * 0.62)) ** 2 + ((yy - H / 2) / (H * 0.62)) ** 2)
    a = np.clip((d - 0.75) * 0.9, 0, 0.55)
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    im.putalpha(Image.fromarray((a * 255).astype(np.uint8)))
    return im


# ─────────────────────────── 크리스탈 별(앱 StarStyle 이식) ───────────────────────────

BASE_COLORS = ["#FFFFFF", "#FFD54F", "#FF8A65", "#FF5252", "#F48FB1", "#CE93D8", "#9575CD", "#64B5F6",
               "#4DD0E1", "#6EE7B7", "#AED581", "#A1887F", "#E040FB", "#448AFF", "#00E676", "#FFAB00"]
GRADS = [("#FF6FD8", "#8E7BFF"), ("#43E97B", "#38F9D7"), ("#FFD86F", "#FB6F6F"), ("#5EE7FF", "#5B7CFF"),
         ("#101010", "#FFFFFF")]


def hexc(h):
    return np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)], np.float32)


def color_pair(idx):
    if idx >= 16:
        a, b = GRADS[idx - 16]
        return hexc(a), hexc(b)
    c = hexc(BASE_COLORS[idx])
    c = c + (255 - c) * 0.3
    return c, c


def _rot(pts, deg, cx=0.0, cy=0.0):
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in pts]


def _tr(pts, dx, dy):
    return [(x + dx, y + dy) for x, y in pts]


def _sc(pts, k, cx=0.0, cy=0.0):
    return [(cx + (x - cx) * k, cy + (y - cy) * k) for x, y in pts]


def _circle(cx, cy, r, n=72):
    return [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)]


def _ellipse(cx, cy, rx, ry, deg=0.0, n=90):
    pts = [(cx + rx * math.cos(2 * math.pi * i / n), cy + ry * math.sin(2 * math.pi * i / n)) for i in range(n)]
    return _rot(pts, deg, cx, cy) if deg else pts


def _quad(p0, c, p1, n=18):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * p1[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * p1[1]) for t in (i / n for i in range(1, n + 1))]


def _cubic(p0, c1, c2, p1, n=24):
    out = []
    for i in range(1, n + 1):
        t = i / n
        mt = 1 - t
        out.append((mt ** 3 * p0[0] + 3 * mt * mt * t * c1[0] + 3 * mt * t * t * c2[0] + t ** 3 * p1[0],
                    mt ** 3 * p0[1] + 3 * mt * mt * t * c1[1] + 3 * mt * t * t * c2[1] + t ** 3 * p1[1]))
    return out


def _rrect(x, y, w, h, r, n=10):
    pts = []
    for cx, cy, a0 in ((x + w - r, y + r, -90), (x + w - r, y + h - r, 0), (x + r, y + h - r, 90), (x + r, y + r, 180)):
        for i in range(n + 1):
            a = math.radians(a0 + 90 * i / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def _star(spikes, inner, rot, cx=0.5, cy=0.5, outer=0.475):
    total = spikes * 2

    def pt(i, ln):
        a = math.radians(i * 360 / total + rot)
        return (cx + math.cos(a) * ln, cy + math.sin(a) * ln)

    pts = [pt(0, outer)]
    for i in range(spikes):
        pts += _quad(pts[-1], pt(i * 2 + 1, outer * inner), pt(((i + 1) % spikes) * 2, outer))
    return pts


HEART = lambda: [(0.5, 0.84)] + _cubic((0.5, 0.84), (0.14, 0.62), (0.06, 0.42), (0.14, 0.28)) + \
    _cubic((0.14, 0.28), (0.23, 0.12), (0.43, 0.13), (0.5, 0.30)) + \
    _cubic((0.5, 0.30), (0.57, 0.13), (0.77, 0.12), (0.86, 0.28)) + \
    _cubic((0.86, 0.28), (0.94, 0.42), (0.86, 0.62), (0.5, 0.84))


def shape_parts(t: int):
    """앱 StarStyle.starPath 와 같은 0..1 좌표 → [("add"|"sub", 점들)] + 중심 배율."""
    A, S = "add", "sub"
    if t <= 4:
        spec = [(4, .085, 0), (5, .14, -90), (6, .11, -90), (8, .10, 0), (4, .085, 45)][t]
        return [(A, _star(*spec))], 1.0
    if t == 5:
        parts = [(A, _circle(.5 + .204 * math.cos(math.radians(i * 60 - 90)),
                             .5 + .204 * math.sin(math.radians(i * 60 - 90)), .18)) for i in range(6)]
        return parts + [(S, _circle(.5, .5, .135))], 1.0
    if t == 6:
        pts = [(.31, .11), (.69, .11), (.84, .14), (.97, .40), (.50, .95), (.03, .40), (.16, .14)]
        return [(A, [(.5 + (x - .5) * .56, .5 + (y - .53) * .56) for x, y in pts])], 1.0
    if t == 7:
        return [(A, _rot(_circle(.45, .5, .42), -22, .5, .5)), (S, _rot(_circle(.66, .46, .37), -22, .5, .5))], 1.0
    if t == 8:
        return [(A, _ellipse(.5, .52, .46, .15, -20)), (S, _ellipse(.5, .52, .37, .105, -20)),
                (A, _circle(.5, .52, .26))], 1.0
    if t == 9:
        return [(A, HEART())], 0.82
    if t == 10:
        d = 0.70710677

        def p(a, b):
            return (0.64 - a * d + b * d, 0.64 - a * d - b * d)

        def band(m, c1, e1, c2, e2):
            return [p(*m)] + _quad(p(*m), p(*c1), p(*e1)) + _quad(p(*e1), p(*c2), p(*e2))

        return [(A, _circle(.64, .64, .145)),
                (A, band((0, .145), (.36, .14), (.76, .05), (.40, -.02), (0, -.145))),
                (A, band((.06, .20), (.30, .26), (.62, .20), (.32, .185), (.06, .20))),
                (A, band((.08, -.19), (.28, -.235), (.50, -.10), (.30, -.155), (.08, -.19))),
                (A, _star(4, .12, 0, *p(-.30, 0), outer=.075))], 1.0
    if t == 11:
        parts = [(A, _circle(.5, .5, .10))]
        for i in range(6):
            parts.append((A, _tr(_rot(_rrect(-.04, -.44, .08, .44, .04), i * 60), .5, .5)))
            for sg in (-1, 1):
                pts = _rot(_rrect(-.034, -.15, .068, .15, .034), sg * 55)
                parts.append((A, _tr(_rot(_tr(pts, 0, -.24), i * 60), .5, .5)))
        return parts, 1.0
    if t == 12:
        pet = [(0, -.05)] + _cubic((0, -.05), (-.11, -.11), (-.20, -.27), (-.12, -.44)) + \
            _quad((-.12, -.44), (-.05, -.47), (0, -.39)) + _quad((0, -.39), (.05, -.47), (.12, -.44)) + \
            _cubic((.12, -.44), (.20, -.27), (.11, -.11), (0, -.05))
        return [(A, _tr(_rot(pet, i * 72), .5, .5)) for i in range(5)] + [(A, _circle(.5, .5, .08))], 1.0
    if t == 13:
        ray = [(-.055, -.28)] + _quad((-.055, -.28), (-.02, -.38), (0, -.47)) + _quad((0, -.47), (.02, -.38), (.055, -.28))
        return [(A, _circle(.5, .5, .21))] + [(A, _tr(_rot(ray, i * 45), .5, .5)) for i in range(8)], 1.0
    if t == 14:
        o = [(.52, .06)] + _cubic((.52, .06), (.60, .22), (.80, .34), (.80, .60)) + \
            _cubic((.80, .60), (.80, .80), (.66, .92), (.50, .92)) + _cubic((.50, .92), (.34, .92), (.20, .80), (.20, .62)) + \
            _cubic((.20, .62), (.20, .48), (.26, .40), (.30, .30)) + _cubic((.30, .30), (.34, .40), (.38, .46), (.42, .48)) + \
            _cubic((.42, .48), (.40, .34), (.44, .18), (.52, .06))
        i = [(.5, .54)] + _cubic((.5, .54), (.57, .63), (.63, .69), (.62, .78)) + \
            _cubic((.62, .78), (.61, .86), (.39, .86), (.38, .78)) + _cubic((.38, .78), (.37, .69), (.43, .63), (.5, .54))
        return [(A, o), (S, i)], 0.95
    if t == 15:
        r = lambda pts: _rot(pts, 45, .5, .5)
        return [(A, r(_circle(.5, .24, .18))), (A, r(_rrect(.455, .38, .09, .52, .03))),
                (A, r(_rrect(.5, .72, .18, .06, .02))), (A, r(_rrect(.5, .82, .13, .06, .02))),
                (S, r(_circle(.5, .24, .075)))], 1.0
    if t == 16:
        parts = []
        for i in range(4):
            pts = _tr(_rot(_tr(_sc(_tr(HEART(), -.5, -.84), .5), 0, -.02), i * 90), .5, .5)
            parts.append((A, pts))
        stem = [(.52, .52)] + _quad((.52, .52), (.66, .70), (.80, .87)) + [(.85, .83)] + _quad((.85, .83), (.70, .67), (.57, .49))
        return parts + [(A, stem)], 1.0
    if t == 17:
        return [(A, [(.16, .72), (.12, .30), (.33, .50), (.50, .22), (.67, .50), (.88, .30), (.84, .72)]),
                (A, _rrect(.16, .70, .68, .12, .03)), (A, _circle(.12, .27, .055)), (A, _circle(.5, .19, .06)),
                (A, _circle(.88, .27, .055))], 0.92
    if t == 18:
        parts = [(A, _circle(.5, .5, .11))]
        for k in (0, 1):
            outer, inner = [], []
            for i in range(29):
                u = i / 28
                th = k * math.pi + u * 1.25 * math.pi
                rr = .10 + .34 * u
                ri = max(0, rr - (.13 * (1 - u) ** .8 + .006))
                outer.append((.5 + rr * math.cos(th), .5 + rr * math.sin(th)))
                inner.append((.5 + ri * math.cos(th), .5 + ri * math.sin(th)))
            parts.append((A, outer + inner[::-1]))
        return parts, 1.0
    if t == 19:
        ear = lambda sx: [(.5 + (x - .5) * sx, y) for x, y in
                          [(.2, .5), (.19, .2)] + _quad((.19, .2), (.19, .14), (.25, .17)) + [(.45, .36)]]
        return [(A, _ellipse(.5, .6, .34, .27)), (A, ear(1)), (A, ear(-1)),
                (S, _ellipse(.38, .6, .045, .065)), (S, _ellipse(.62, .6, .045, .065))], 0.95
    return [(A, [(.92, .14), (.07, .47), (.37, .57), (.45, .88), (.56, .66), (.78, .77)])], 1.0


def _hash(n):
    x = math.sin(n * 127.1 + 311.7) * 43758.5453
    return x - math.floor(x)


@lru_cache(maxsize=256)
def star_sprite(t: int, color: int, size: int, locked: bool = False) -> Image.Image:
    """크리스탈 파편 채움 + 후광이 있는 별 스프라이트(RGBA, 한 변 = size*1.5)."""
    ss = 3
    S = size * ss
    parts, k = shape_parts(t)
    mask = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(mask)
    for op, pts in parts:
        pts = _sc(pts, k * 0.94, .5, .5)
        d.polygon([(x * S, y * S) for x, y in pts], fill=255 if op == "add" else 0)
    c1, c2 = color_pair(color)
    if locked:
        g = np.array([70, 76, 92], np.float32)
        c1, c2 = g, g
    img = Image.new("RGB", (S, S), tuple(int(v) for v in c1))
    dd = ImageDraw.Draw(img)
    rings, seg = [0, .15, .31, .5, .8], 16

    def vtx(j, i):
        a = (i + ((_hash(j * 31 + i) - .5) * .5 if j else 0)) / seg * math.pi * 2 - math.pi / 2
        r = rings[j] * ((1 + (_hash(j * 57 + i * 3) - .5) * .22) if j else 0)
        return (.5 + r * math.cos(a), .5 + r * math.sin(a))

    for j in range(len(rings) - 1):
        for i in range(seg):
            pts = [vtx(j, i), vtx(j, (i + 1) % seg), vtx(j + 1, (i + 1) % seg), vtx(j + 1, i)]
            cx = sum(p[0] for p in pts) / 4
            cy = sum(p[1] for p in pts) / 4
            col = c1 + (c2 - c1) * clamp((cx + cy) / 2)
            kk = (_hash(j * 101 + i * 7 + t * 13) - .45) * .5 + (1 - j / len(rings)) * .18
            col = col + (255 - col) * kk if kk > 0 else col * (1 + kk)
            dd.polygon([(x * S, y * S) for x, y in pts], fill=tuple(int(clamp(v, 0, 255)) for v in col),
                       outline=tuple(int(min(255, v * 0.5 + 128)) for v in col))
    # 좌상단 하이라이트
    hl = np.linspace(1, 0, S)[None, :] * np.linspace(1, 0, S)[:, None]
    arr = np.array(img, np.float32)
    arr += (np.clip(hl * 1.6 - 0.55, 0, 1) * 70)[..., None]
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).resize((size, size), Image.LANCZOS)
    m = mask.resize((size, size), Image.LANCZOS)
    star = img.convert("RGBA")
    star.putalpha(m)
    out = Image.new("RGBA", (int(size * 1.5), int(size * 1.5)), (0, 0, 0, 0))
    off = (out.width - size) // 2
    glow_a = Image.new("L", out.size, 0)
    glow_a.paste(m, (off, off))
    glow_a = glow_a.filter(ImageFilter.GaussianBlur(size * 0.09)).point(lambda v: int(v * (0.35 if locked else 0.85)))
    gc = tuple(int(v) for v in (c1 + c2) / 2)
    glow = Image.new("RGBA", out.size, gc + (0,))
    glow.putalpha(glow_a)
    out.alpha_composite(glow)
    out.alpha_composite(star, (off, off))
    return out


@lru_cache(maxsize=64)
def sparkle_sprite(size: int, color: tuple) -> Image.Image:
    """반짝이 파티클 — 4꼭지 별 + 후광."""
    s = max(4, size)
    S = s * 3
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).polygon([(x * S, y * S) for x, y in _star(4, .09, 0)], fill=255)
    m = m.resize((s, s), Image.LANCZOS)
    out = Image.new("RGBA", (s * 2, s * 2), color + (0,))
    a = Image.new("L", out.size, 0)
    a.paste(m, (s // 2, s // 2))
    glow = a.filter(ImageFilter.GaussianBlur(s * 0.25)).point(lambda v: int(v * 0.8))
    core = Image.new("RGBA", out.size, (255, 255, 255, 0))
    core.putalpha(a)
    out.putalpha(glow)
    out.alpha_composite(core)
    return out


def paste_center(canvas: Image.Image, im: Image.Image, cx, cy, alpha=1.0, scale=1.0):
    if scale <= 0.01 or alpha <= 0.01:
        return
    if abs(scale - 1) > 0.01:
        im = im.resize((max(1, int(im.width * scale)), max(1, int(im.height * scale))), Image.BILINEAR)
    im = with_alpha(im, alpha)
    canvas.alpha_composite(im, (int(cx - im.width / 2), int(cy - im.height / 2))) if \
        0 <= int(cx - im.width / 2) and 0 <= int(cy - im.height / 2) and \
        int(cx - im.width / 2) + im.width <= W and int(cy - im.height / 2) + im.height <= H else \
        _paste_clipped(canvas, im, int(cx - im.width / 2), int(cy - im.height / 2))


def _paste_clipped(canvas, im, x, y):
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(W, x + im.width), min(H, y + im.height)
    if x1 <= x0 or y1 <= y0:
        return
    canvas.alpha_composite(im.crop((x0 - x, y0 - y, x1 - x, y1 - y)), (x0, y0))


def sparkle_burst(canvas, cx, cy, u, seed=1, n=16, radius=170, colors=(MINT, GOLD, WHITE, PINK), size=34):
    """u: 0→1 진행도. 중심에서 사방으로 튀어 나가며 사라지는 반짝이."""
    if u <= 0 or u >= 1:
        return
    rng = random.Random(seed)
    for i in range(n):
        ang = rng.uniform(0, math.tau)
        dist = radius * rng.uniform(0.45, 1.0) * ease_out(u)
        sz = int(size * rng.uniform(0.5, 1.2) * (1 - u * 0.6))
        col = colors[i % len(colors)]
        paste_center(canvas, sparkle_sprite(max(6, sz), col), cx + math.cos(ang) * dist,
                     cy + math.sin(ang) * dist, alpha=(1 - u) ** 0.8)


# ─────────────────────────── 녹화 소스 ───────────────────────────

# 개인정보 가리기 — (원본 시각 구간, 원본 좌표(1080x2340 기준) 상자, 방식)
MASKS = [
    ((2.5, 4.2), (30, 960, 330, 1045), "blur"),      # 잠금 상세 작성자 줄
    ((10.8, 18.5), (30, 960, 330, 1045), "blur"),    # 상세(고양이) 작성자 줄
    ((7.6, 10.9), (180, 1780, 360, 1850), "blur"),   # 겹친 별 카드 작성자
    ((7.6, 10.9), (960, 1760, 1080, 1870), "blur"),  # 옆 카드 작성자
    ((42.5, 50.5), (280, 1062, 800, 1130), "blur"),  # 별 도감 "OO님의 별"
    ((31.5, 40.0), (360, 1350, 720, 1475), "name"),  # 프로필 이름 → 가명
    ((106.5, 122.0), (250, 1828, 830, 1922), "erase"),  # 지구본 조작 안내(한국어 UI 글자) — 영문판에도 보여서 지운다
]
ALIAS = {"ko": "새벽별", "en": "Stargazer"}


def apply_masks(img: Image.Image, src_t: float, lang: str):
    for (a, b), (x0, y0, x1, y1), kind in MASKS:
        if not (a <= src_t <= b):
            continue
        box = (x0, y0 - CROP_Y, x1, y1 - CROP_Y)
        if kind == "erase":
            # 글자 자리를 바로 위 배경색으로 메우고 가장자리를 부드럽게 — 흐린 회색 띠가 남지 않게.
            band = np.array(img.crop((x0, max(0, box[1] - 70), x1, box[1])))
            col = tuple(int(v) for v in np.percentile(band.reshape(-1, 3), 35, axis=0))
            fill = Image.new("RGB", (x1 - x0, box[3] - box[1]), col)
            m = Image.new("L", fill.size, 0)
            ImageDraw.Draw(m).rounded_rectangle((8, 8, fill.width - 8, fill.height - 8), 18, fill=255)
            img.paste(fill, box[:2], m.filter(ImageFilter.GaussianBlur(6)))
            continue
        region = img.crop(box).filter(ImageFilter.GaussianBlur(16 if kind == "blur" else 22))
        if kind == "name":
            d = ImageDraw.Draw(region)
            f = font(64, 700)
            name = ALIAS[lang]
            tw = f.getlength(name)
            d.text(((box[2] - box[0] - tw) / 2, 18), name, font=f, fill=(242, 244, 250))
        img.paste(region, box[:2])


class ClipReader:
    """원본 [t0, …) 를 speed 배속으로 30fps 프레임 스트림(RGB)으로 읽는다. hold=True 면 첫 프레임 정지."""

    def __init__(self, t0: float, speed: float, n: int, lang: str, hold=False):
        self.t0, self.speed, self.lang, self.hold = t0, speed, lang, hold
        frames = 1 if hold else n + 2
        vf = f"crop=1080:{CROP_H}:0:{CROP_Y},setpts=(PTS-STARTPTS)/{speed},fps={FPS}"
        self.proc = subprocess.Popen(
            ["ffmpeg", "-v", "error", "-ss", f"{t0:.3f}", "-i", str(SRC), "-vf", vf, "-frames:v", str(frames),
             "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        self.k = 0
        self.last: Image.Image | None = None

    def next(self) -> Image.Image:
        if self.hold and self.last is not None:
            return self.last
        raw = self.proc.stdout.read(1080 * CROP_H * 3)
        if len(raw) == 1080 * CROP_H * 3:
            img = Image.fromarray(np.frombuffer(raw, np.uint8).reshape(CROP_H, 1080, 3))
            apply_masks(img, self.t0 + self.k * self.speed / FPS, self.lang)
            self.last = img
        self.k += 1
        return self.last

    def close(self):
        try:
            self.proc.stdout.close()
            self.proc.kill()
        except Exception:
            pass


# ─────────────────────────── 화면 배치(폰 프레임 ↔ 풀블리드) ───────────────────────────

@dataclass
class Place:
    scale: float
    ax: float = 540      # 소스(잘라낸 1080x2080) 기준점
    ay: float = 1040
    ox: float = 540      # 출력 위치
    oy: float = 880      # 풀블리드: 앱 상단바가 화면 위로 빠지게(아래는 딱 맞음)


FRAMED = Place(0.64, 540, 1040, 540, 1160)
FULL = Place(1.0, 540, 1040, 540, 880)


def mix_place(a: Place, b: Place, u: float) -> Place:
    u = ease_in_out(u)
    return Place(lerp(a.scale, b.scale, u), lerp(a.ax, b.ax, u), lerp(a.ay, b.ay, u), lerp(a.ox, b.ox, u),
                 lerp(a.oy, b.oy, u))


SHELL_W, SHELL_H, SHELL_R = int(1080 * 0.64), int(CROP_H * 0.64), 58


@lru_cache(maxsize=1)
def base_shell():
    return phone_shell(SHELL_W, SHELL_H, SHELL_R)


@lru_cache(maxsize=1)
def base_mask():
    return rounded_mask(SHELL_W, SHELL_H, SHELL_R)


@lru_cache(maxsize=48)
def rounded_mask(w, h, r):
    ss = 2
    m = Image.new("L", (w * ss, h * ss), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * ss - 1, h * ss - 1), r * ss, fill=255)
    return m.resize((w, h), Image.LANCZOS)


@lru_cache(maxsize=48)
def phone_shell(w, h, r):
    """폰 테두리 + 뒤 후광(민트) — RGBA, 화면보다 사방 90px 크다."""
    pad = 90
    shell = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    glow_a = Image.new("L", shell.size, 0)
    ImageDraw.Draw(glow_a).rounded_rectangle((pad - 6, pad - 6, pad + w + 6, pad + h + 6), r + 6, fill=255)
    glow_a = glow_a.filter(ImageFilter.GaussianBlur(38)).point(lambda v: int(v * 0.42))
    glow = Image.new("RGBA", shell.size, (70, 150, 170, 0))
    glow.putalpha(glow_a)
    shell.alpha_composite(glow)
    ss = 2
    bez = Image.new("RGBA", (shell.width * ss, shell.height * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(bez)
    d.rounded_rectangle(((pad - 12) * ss, (pad - 12) * ss, (pad + w + 12) * ss, (pad + h + 12) * ss),
                        (r + 12) * ss, fill=(22, 26, 38, 255), outline=(70, 80, 104, 255), width=3 * ss)
    shell.alpha_composite(bez.resize(shell.size, Image.LANCZOS))
    return shell, pad


def draw_screen(canvas: Image.Image, src: Image.Image, p: Place):
    """녹화 화면을 배치에 맞게 그린다. scale 이 작으면 폰 프레임, 1 근처면 풀블리드."""
    s = p.scale
    w, h = int(1080 * s), int(CROP_H * s)
    x = int(p.ox - p.ax * s)
    y = int(p.oy - p.ay * s)
    framed = clamp((0.99 - s) / 0.13)   # 1 = 완전한 폰 프레임, 0 = 풀블리드
    if framed <= 0.01 and w >= W and h >= H:
        # 풀블리드는 항상 화면을 꽉 채우도록 위치를 가둔다(확대 중 가장자리 빈틈 방지).
        x = int(clamp(x, W - w, 0))
        y = int(clamp(y, H - h, 0))
    img = src.resize((w, h), Image.BILINEAR)
    if framed > 0.01:
        shell, pad = base_shell()
        k = w / SHELL_W
        sh = shell.resize((max(1, int(shell.width * k)), max(1, int(shell.height * k))), Image.BILINEAR)
        paste_center(canvas, sh, x + w / 2, y + h / 2, alpha=framed)
        tile = img.convert("RGBA")
        tile.putalpha(base_mask().resize((w, h), Image.BILINEAR))
        _paste_clipped(canvas, tile, x, y)
    else:
        _paste_clipped(canvas, img.convert("RGBA"), x, y)
    return lambda sx, sy: (x + sx * s, y + (sy - CROP_Y) * s)   # 원본 좌표(1080x2340) → 출력 좌표


# ─────────────────────────── 타임라인 ───────────────────────────

@dataclass
class Shot:
    start: float
    dur: float
    src: tuple | None = None                 # (t0, speed) 또는 (t0, speed, "hold")
    place: tuple = (FRAMED, FRAMED)          # 시작/끝 배치
    draw: Callable | None = None             # 코드 씬: draw(canvas, tl, u, lang)
    trans: str = "punch"                     # punch | fade | cut
    fx: list = field(default_factory=list)   # [(t_local, kind, 원본x, 원본y)] — tap / burst


@dataclass
class Caption:
    start: float
    end: float
    lines: list          # [(text, size, weight, color)]
    y: int = 230
    style: str = "top"   # top | pov | center
    accent: tuple = MINT


@dataclass
class Spec:
    name: str
    dur: float
    lang: str
    shots: list
    captions: list
    overlays: list                 # [(start, end, fn(canvas, tl, lang))]
    bgm: str
    bgm_start: float
    sfx: list                      # [(time, file, gain_db)]
    bg_seed: int = 7


def cap(start, end, l1, l2=None, lang="ko", **kw):
    big = 86 if lang == "ko" else 76
    small = 54 if lang == "ko" else 48
    lines = [(l1, big, 800, WHITE)]
    if l2:
        lines.append((l2, small, 500, SUB))
    return Caption(start, end, lines, **kw)


def pov_cap(start, end, lines, lang="ko", y=250):
    sz = 70 if lang == "ko" else 62
    return Caption(start, end, [(ln, sz, 800, WHITE) for ln in lines], y=y, style="pov", accent=GOLD)


@lru_cache(maxsize=512)
def caption_lines(text, size, weight, color, accent, pov):
    return render_line(text, size, weight, color, accent, stroke=7 if pov else 0, glow=not pov)


def draw_caption(canvas, c: Caption, t):
    if not (c.start <= t < c.end):
        return
    tl = t - c.start
    out_u = clamp((c.end - t) / 0.22)
    y = c.y
    for i, (text, size, weight, color) in enumerate(c.lines):
        im = caption_lines(text, size, weight, color, c.accent, c.style == "pov")
        fit = min(1.0, (W - 20) / im.width)
        u = clamp((tl - i * 0.08) / 0.42)
        e = ease_back(u, 1.4)
        a = clamp(u * 1.8) * out_u
        dy = (1 - e) * 38 - (1 - out_u) * 14
        paste_center(canvas, im, W / 2, y + im.height * fit / 2 - 40 * fit + dy, alpha=a, scale=fit)
        y += int(size * (1.32 if i == 0 else 1.25))


# ─────────────────────────── 코드 씬 ───────────────────────────

GRID_TYPES = list(range(21))
GRID_COLORS = {0: 9, 1: 1, 2: 7, 3: 8, 4: 5, 5: 4, 6: 19, 7: 15, 8: 16, 9: 3, 10: 8, 11: 19, 12: 4, 13: 15,
               14: 2, 15: 1, 16: 14, 17: 15, 18: 16, 19: 5, 20: 0}
BASIC = {0, 1, 2}


GRID_STAR = 130


def grid_pos(i):
    col, row = i % 3, i // 3
    return 210 + col * 330, 700 + row * 165


@lru_cache(maxsize=4)
def lock_glyph(size=34):
    S = size * 3
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((S * .18, S * .45, S * .82, S * .95), S * .1, fill=(210, 216, 232, 255))
    d.arc((S * .30, S * .10, S * .70, S * .62), 180, 360, fill=(210, 216, 232, 255), width=int(S * .09))
    d.line((S * .30, S * .36, S * .30, S * .50), fill=(210, 216, 232, 255), width=int(S * .09))
    d.line((S * .70, S * .36, S * .70, S * .50), fill=(210, 216, 232, 255), width=int(S * .09))
    return im.resize((size, size), Image.LANCZOS)


def counter_pill(canvas, text, y=1500, accent=GOLD, alpha=1.0, pop=0.0):
    f = font(52, 800)
    tw = f.getlength(text)
    w, h = int(tw + 90), 96
    im = Image.new("RGBA", (w + 40, h + 40), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((20, 20, 20 + w, 20 + h), h // 2, fill=(16, 20, 32, 235), outline=accent + (255,), width=3)
    d.text((20 + 45, 20 + 14), text, font=f, fill=accent)
    paste_center(canvas, im, W / 2, y, alpha=alpha, scale=1 + 0.12 * math.sin(math.pi * clamp(pop)))


def grid_scene(reveal_t=0.0, step=0.116, lock_at=None, collect_at=None, collect_step=0.116, show_counter=True):
    """별 모양 21종 그리드. reveal: 순서대로 톡 등장 → lock_at 부터 업적 모양 잠김 → collect_at 부터 하나씩 해금."""
    order = [i for i in GRID_TYPES if i not in BASIC]

    def draw(canvas, tl, u, lang):
        n_lit = 3
        for i, t in enumerate(GRID_TYPES):
            x, y = grid_pos(i)
            appear = clamp((tl - reveal_t - i * step) / 0.3)
            if appear <= 0:
                continue
            sc = ease_back(appear, 2.2)
            locked = False
            if lock_at is not None and t not in BASIC and tl >= lock_at:
                locked = True
                if collect_at is not None:
                    k = order.index(t)
                    if tl >= collect_at + k * collect_step:
                        locked = False
            lit = star_sprite(t, GRID_COLORS[t], GRID_STAR, False)
            if locked:
                lu = clamp((tl - lock_at) / 0.25)
                paste_center(canvas, lit, x, y, alpha=1 - lu, scale=sc)
                paste_center(canvas, star_sprite(t, 0, GRID_STAR, True), x, y, alpha=lu, scale=sc)
                paste_center(canvas, lock_glyph(), x + 40, y + 38, alpha=lu)
            else:
                pulse = 1.0
                if collect_at is not None and t not in BASIC:
                    k = order.index(t)
                    cu = (tl - collect_at - k * collect_step) / 0.35
                    if 0 <= cu <= 1:
                        pulse = 1 + 0.35 * math.sin(math.pi * cu)
                        sparkle_burst(canvas, x, y, cu, seed=i, n=8, radius=110, size=26)
                paste_center(canvas, lit, x, y, scale=sc * pulse)
            if t not in BASIC and not locked and lock_at is not None:
                n_lit += 1
        if show_counter and lock_at is not None and tl >= lock_at:
            lit_count = 3 if collect_at is None else 3 + sum(
                1 for k in range(len(order)) if tl >= collect_at + k * collect_step)
            pop = 0.0
            if collect_at is not None and collect_at <= tl:
                k = int((tl - collect_at) / collect_step)
                pop = ((tl - collect_at) - k * collect_step) / collect_step if k < len(order) else 0
            counter_pill(canvas, f"{min(21, lit_count)} / 21", y=540,
                         accent=GOLD if lit_count >= 21 else SUB, alpha=clamp((tl - lock_at) / 0.2), pop=pop)

    return draw


def hook_star_scene(t=17, color=15):
    def draw(canvas, tl, u, lang):
        a = clamp(tl / 0.45)
        sc = ease_back(a, 2.4)
        spin = math.sin(tl * 1.6) * 6
        spr = star_sprite(t, color, 420)
        im = spr.rotate(spin, resample=Image.BICUBIC)
        paste_center(canvas, im, W / 2, 1120, scale=sc * (1 + 0.03 * math.sin(tl * 5)))
        sparkle_burst(canvas, W / 2, 1120, clamp((tl - 0.12) / 0.9), seed=3, n=20, radius=330, size=40)

    return draw


UNLOCK_TXT = {
    "ko": ("업적 달성!", "대관식", "일반 업적 30개 달성하기", "새 별 모양 해금"),
    "en": ("Achievement unlocked!", "Coronation", "Unlock 30 achievements", "New star shape unlocked"),
}


def unlock_card_scene():
    """앱 AchievementUnlockDialog 재현 — 파편 수렴 · 광선 회전 · 금빛 왕관 별 · 보상 알약."""

    def draw(canvas, tl, u, lang):
        title, name, cond, chip = UNLOCK_TXT[lang]
        dim = Image.new("RGBA", (W, H), (0, 0, 0, int(140 * clamp(tl / 0.2))))
        canvas.alpha_composite(dim)
        pop = ease_back(clamp(tl / 0.45), 1.6)
        cw, ch = 820, 1000
        card = Image.new("RGBA", (cw + 60, ch + 60), (0, 0, 0, 0))
        d = ImageDraw.Draw(card)
        d.rounded_rectangle((30, 30, 30 + cw, 30 + ch), 56, fill=(20, 24, 28, 250), outline=(59, 130, 246, 255), width=4)
        cx, cy = card.width / 2, 30 + 300
        rv = clamp(tl / 0.9)
        settled = clamp((rv - 0.55) / 0.45)
        rays = Image.new("RGBA", card.size, (0, 0, 0, 0))
        rd = ImageDraw.Draw(rays)
        ang0 = tl * 20
        for i in range(12):
            a = math.radians(i * 30 + ang0)
            ln = 250 if i % 2 == 0 else 180
            hw = 9 if i % 2 == 0 else 6
            tip = (cx + math.cos(a) * ln, cy + math.sin(a) * ln)
            s1 = (cx + math.cos(a + math.pi / 2) * hw, cy + math.sin(a + math.pi / 2) * hw)
            s2 = (cx + math.cos(a - math.pi / 2) * hw, cy + math.sin(a - math.pi / 2) * hw)
            rd.polygon([s1, tip, s2], fill=GOLD + (int(60 * settled),))
        card.alpha_composite(rays)
        # 파편 수렴
        rng = random.Random(9)
        for i in range(14):
            ang = i / 14 * math.tau + rng.uniform(-.15, .15)
            delay = rng.uniform(0, .22)
            lu = clamp((rv - delay) / max(0.05, 0.55 - delay))
            if lu < 1:
                dist = 280 * rng.uniform(.85, 1.4) * (1 - lu * lu)
                paste_center_local(card, sparkle_sprite(22, GOLD), cx + math.cos(ang) * dist, cy + math.sin(ang) * dist,
                                   alpha=0.9 * (1 - lu * lu * 0.35))
        appear = clamp((rv - 0.42) / 0.35)
        settle = 0.7 + 0.48 * appear if rv < 0.62 else 1.18 - 0.18 * clamp((rv - 0.62) / 0.38)
        paste_center_local(card, star_sprite(17, 15, 230), cx, cy, alpha=appear, scale=settle)
        # 글자
        y = 30 + 560
        for text, size, weight, color in ((title, 40, 400, (159, 179, 232)), (name, 66, 700, WHITE),
                                          (cond, 34, 400, (160, 166, 180))):
            f = font(size if lang == "ko" else int(size * 0.92), weight)
            tw = f.getlength(text)
            ta = clamp((tl - 0.45) / 0.3)
            d2 = ImageDraw.Draw(card)
            d2.text((cx - tw / 2, y), text, font=f, fill=color + (int(255 * ta),))
            y += int(size * 1.55)
        f = font(32, 500)
        tw = f.getlength(chip)
        pw = tw + 70
        py = y + 24
        d2 = ImageDraw.Draw(card)
        ta = clamp((tl - 0.6) / 0.3)
        d2.rounded_rectangle((cx - pw / 2, py, cx + pw / 2, py + 70), 35, fill=GOLD + (int(30 * ta),),
                             outline=GOLD + (int(120 * ta),), width=2)
        d2.text((cx - tw / 2, py + 15), chip, font=f, fill=GOLD + (int(255 * ta),))
        paste_center(canvas, card, W / 2, 1100, scale=0.8 + 0.2 * pop, alpha=clamp(tl / 0.18))

    return draw


def paste_center_local(target, im, cx, cy, alpha=1.0, scale=1.0):
    if scale <= 0.01 or alpha <= 0.01:
        return
    if abs(scale - 1) > 0.01:
        im = im.resize((max(1, int(im.width * scale)), max(1, int(im.height * scale))), Image.BILINEAR)
    im = with_alpha(im, alpha)
    x, y = int(cx - im.width / 2), int(cy - im.height / 2)
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(target.width, x + im.width), min(target.height, y + im.height)
    if x1 > x0 and y1 > y0:
        target.alpha_composite(im.crop((x0 - x, y0 - y, x1 - x, y1 - y)), (x0, y0))


@lru_cache(maxsize=1)
def logo_img():
    im = Image.open(LOGO).convert("RGBA").crop((12, 0, 1524, 1023))
    return im.resize((860, int(860 * im.height / im.width)), Image.LANCZOS)


END_TXT = {
    "ko": {"showcase": ("당신의 순간을", "밤하늘의 별로"), "pov": ("당신 근처에도", "별이 있어요"),
           "collect": ("첫 번째 별을", "모으러 가 볼까요?"), "cta": "지금 무료로 시작하기", "store": "Google Play · App Store"},
    "en": {"showcase": ("Turn your moments", "into stars"), "pov": ("There's a star", "near you too"),
           "collect": ("Ready to collect", "your first star?"), "cta": "Start free today", "store": "Google Play · App Store"},
}


def end_scene(concept):
    def draw(canvas, tl, u, lang):
        tx = END_TXT[lang]
        rng = random.Random(21)
        conv = clamp(tl / 0.4)
        for i in range(26):
            ang = rng.uniform(0, math.tau)
            dist = rng.uniform(380, 820) * (1 - ease_out(conv))
            if conv < 1:
                paste_center(canvas, sparkle_sprite(int(rng.uniform(16, 34)), (MINT, GOLD, WHITE)[i % 3]),
                             W / 2 + math.cos(ang) * dist, 820 + math.sin(ang) * dist * 0.8, alpha=0.9)
        la = clamp((tl - 0.2) / 0.4)
        if la > 0:
            glow = clamp(1 - (tl - 0.25) / 0.8)
            if glow > 0:
                g = Image.new("RGBA", (W, H), (0, 0, 0, 0))
                ImageDraw.Draw(g).ellipse((W / 2 - 520, 820 - 300, W / 2 + 520, 820 + 300), fill=(255, 236, 190, int(90 * glow)))
                canvas.alpha_composite(g.filter(ImageFilter.GaussianBlur(90)))
            paste_center(canvas, logo_img(), W / 2, 820, alpha=la, scale=0.92 + 0.08 * ease_out(la))
        l1, l2 = tx[concept]
        for i, text in enumerate((l1, l2)):
            a = clamp((tl - 0.4 - i * 0.08) / 0.3)
            im = caption_lines(text, 72 if lang == "ko" else 64, 700, WHITE, MINT, False)
            paste_center(canvas, im, W / 2, 1280 + i * 96 + (1 - ease_out(a)) * 26, alpha=a)
        a = clamp((tl - 0.62) / 0.3)
        f = font(44, 700)
        tw = f.getlength(tx["cta"])
        pw, ph = int(tw + 120), 104
        pill = Image.new("RGBA", (pw + 80, ph + 80), (0, 0, 0, 0))
        pa = Image.new("L", pill.size, 0)
        ImageDraw.Draw(pa).rounded_rectangle((40, 40, 40 + pw, 40 + ph), ph // 2, fill=255)
        grad = np.zeros((pill.height, pill.width, 4), np.uint8)
        xs = np.linspace(0, 1, pill.width)[None, :]
        grad[..., 0] = (110 + (77 - 110) * xs).astype(np.uint8)
        grad[..., 1] = (231 + (208 - 231) * xs).astype(np.uint8)
        grad[..., 2] = (183 + (225 - 183) * xs).astype(np.uint8)
        grad[..., 3] = np.array(pa)
        pill = Image.fromarray(grad)
        halo = Image.new("RGBA", pill.size, MINT + (0,))
        halo.putalpha(pa.filter(ImageFilter.GaussianBlur(22)).point(lambda v: int(v * 0.5)))
        halo.alpha_composite(pill)
        ImageDraw.Draw(halo).text((40 + (pw - tw) / 2, 40 + 24), tx["cta"], font=f, fill=(10, 14, 20, 255))
        pulse = 1 + 0.025 * math.sin(max(0, tl - 1.0) * 5)
        paste_center(canvas, halo, W / 2, 1540, alpha=a, scale=pulse)
        sa = clamp((tl - 0.8) / 0.3)
        f2 = font(34, 500)
        tw2 = f2.getlength(tx["store"])
        st = Image.new("RGBA", (int(tw2) + 20, 60), (0, 0, 0, 0))
        ImageDraw.Draw(st).text((10, 8), tx["store"], font=f2, fill=SUB + (255,))
        paste_center(canvas, st, W / 2, 1665, alpha=sa)

    return draw


def distance_chip(t0, t1, frm_km=102.7, to_m=80.0, y=430):
    """POV — "현재 위치로부터 N" 알약이 줄어들다 100m 안으로 들어오면 민트로 바뀌고 핑."""

    def draw(canvas, tl, lang):
        u = clamp(tl / (t1 - t0))
        e = ease_in_out(u) ** 1.35
        meters = math.exp(math.log(frm_km * 1000) + (math.log(to_m) - math.log(frm_km * 1000)) * e)
        txt = f"{meters / 1000:.1f}km" if meters >= 1000 else f"{int(meters)}m"
        near = meters <= 100
        label = ("현재 위치로부터 " if lang == "ko" else "Distance ") + txt
        col = MINT if near else (255, 255, 255)
        f = font(50, 800)
        tw = f.getlength(label)
        w, h = int(tw + 130), 100
        im = Image.new("RGBA", (w + 60, h + 60), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        d.rounded_rectangle((30, 30, 30 + w, 30 + h), h // 2, fill=(12, 16, 26, 235), outline=col + (255,), width=4)
        # 핀 아이콘
        px, py = 30 + 48, 30 + h / 2
        d.ellipse((px - 16, py - 26, px + 16, py + 6), fill=col)
        d.polygon([(px - 13, py - 4), (px + 13, py - 4), (px, py + 22)], fill=col)
        d.ellipse((px - 6, py - 16, px + 6, py - 4), fill=(12, 16, 26))
        d.text((30 + 84, 30 + 20), label, font=f, fill=col)
        sc = 1.0
        if near:
            k = clamp((u - 0.92) / 0.08)
            sc = 1 + 0.12 * math.sin(math.pi * k)
        paste_center(canvas, im, W / 2, y, alpha=clamp(tl / 0.2), scale=sc)

    return draw


def flash_overlay(strength=0.9, dur=0.35):
    def draw(canvas, tl, lang):
        a = strength * (1 - clamp(tl / dur)) ** 2
        if a > 0.01:
            canvas.alpha_composite(Image.new("RGBA", (W, H), (255, 250, 235, int(255 * a))))

    return draw


def big_word(text_ko, text_en, color=MINT):
    def draw(canvas, tl, lang):
        text = text_ko if lang == "ko" else text_en
        im = render_line(text, 150 if lang == "ko" else 128, 900, color, color)
        a = clamp(tl / 0.12)
        paste_center(canvas, im, W / 2, 900, alpha=a, scale=ease_back(clamp(tl / 0.35), 2.5))

    return draw


# ─────────────────────────── 콘셉트별 스펙 ───────────────────────────

def bars(bpm, n_beats):
    return 60.0 / bpm * n_beats


def spec_showcase(dur, lang):
    beat2 = bars(95.7, 2)   # 1.254s
    T = lambda k: round(k * beat2, 3)
    L = lang
    ko = L == "ko"
    shots, caps, sfx = [], [], []

    def add(k0, k1, **kw):
        shots.append(Shot(T(k0), T(k1) - T(k0), **kw))

    add(0, 1, draw=hook_star_scene(1, 1), trans="cut")
    caps.append(cap(0.05, T(1), "지금 이 자리에" if ko else "Right where you are,", "별 하나를 남겨요" if ko else "leave a star.", L))
    sfx += [(0.0, "sfx_star_birth", -6)]
    if dur == 15:
        plan = [
            (1, 3, dict(src=(0.2, 1.0), place=(FRAMED, Place(0.74, 540, 900, 540, 1180)), fx=[(1.1, "tap", 300, 985)]),
             ("지도 위의 별 하나하나가", "누군가의 [기억]이에요") if ko else ("Every star on the map", "is someone's [memory]")),
            (3, 5, dict(src=(14.5, 1.0), place=(FRAMED, Place(0.7, 540, 1300, 540, 1340)), fx=[(1.7, "burst", 110, 1632)]),
             ("사진과 글을 담고", "마음이 가면 [하트]를") if ko else ("Photos and words,", "and a little [love]")),
            (5, 7, dict(src=(80.0, 3.0), place=(Place(1.0), Place(1.08, 540, 1000, 540, 960))),
             ("가까운 별끼리", "[별자리]로 이어져요") if ko else ("Nearby stars link up", "into [constellations]")),
            (7, 9, dict(src=(44.8, 1.5), place=(FRAMED, Place(0.7, 540, 1000, 540, 1180))),
             ("열어 본 별은", "[별 도감]에 영원히") if ko else ("Every star you open", "stays in your [Star Log]")),
            (9, 10.5, dict(src=(108.5, 2.9), place=(Place(1.0), Place(1.12, 540, 1000, 540, 1000))),
             ("지구 반대편의 별까지", "한눈에") if ko else ("Stars from all over", "the [planet]")),
        ]
        end_k = 10.5
    else:
        plan = [
            (1, 3, dict(src=(0.2, 1.0), place=(FRAMED, Place(0.74, 540, 900, 540, 1180)), fx=[(1.1, "tap", 300, 985)]),
             ("지도 위의 별 하나하나가", "누군가의 [기억]이에요") if ko else ("Every star on the map", "is someone's [memory]")),
            (3, 5, dict(src=(7.9, 1.0), place=(FRAMED, FRAMED)),
             ("한 자리에 겹친 별은", "[카드]로 넘겨 봐요") if ko else ("Stars in the same spot", "flip through as [cards]")),
            (5, 7, dict(src=(14.5, 1.0), place=(FRAMED, Place(0.7, 540, 1300, 540, 1340)), fx=[(1.7, "burst", 110, 1632)]),
             ("사진과 글을 담고", "마음이 가면 [하트]를") if ko else ("Photos and words,", "and a little [love]")),
            (7, 9, dict(src=(90.6, 2.4), place=(FRAMED, FRAMED)),
             ("보고 싶은 별만", "[필터]로 골라서") if ko else ("[Filter] the sky", "to what you want to see")),
            (9, 11, dict(src=(80.0, 3.0), place=(Place(1.0), Place(1.08, 540, 1000, 540, 960))),
             ("가까운 별끼리", "[별자리]로 이어져요") if ko else ("Nearby stars link up", "into [constellations]")),
            (11, 13, dict(src=(23.0, 2.0), place=(FRAMED, Place(0.7, 540, 900, 540, 1180))),
             ("내가 남긴 별은", "[나만의 우주]가 되고") if ko else ("Your stars become", "[your own universe]")),
            (13, 15, dict(src=(44.8, 1.5), place=(FRAMED, Place(0.7, 540, 1000, 540, 1180))),
             ("열어 본 별은", "[별 도감]에 영원히") if ko else ("Every star you open", "stays in your [Star Log]")),
            (15, 17, dict(src=(32.2, 2.0), place=(FRAMED, FRAMED)),
             ("모은 칭호는", "프로필에서 [반짝]") if ko else ("Your titles", "[shine] on your profile")),
            (17, 19, dict(src=(52.6, 2.0), place=(FRAMED, FRAMED)),
             ("[업적 69개] · 별 모양 21종", "모으는 재미까지") if ko else ("[69 achievements] · 21 shapes", "to collect")),
            (19, 21, dict(src=(65.0, 2.2), place=(FRAMED, FRAMED)),
             ("밤하늘 같은", "[배경음악]과 함께") if ko else ("With [music]", "like the night sky")),
            (21, 22.5, dict(src=(108.5, 2.9), place=(Place(1.0), Place(1.12, 540, 1000, 540, 1000))),
             ("지구 반대편의 별까지", "한눈에") if ko else ("Stars from all over", "the [planet]")),
        ]
        end_k = 22.5
    for k0, k1, kw, (l1, l2) in plan:
        add(k0, k1, **kw)
        caps.append(cap(T(k0) + 0.06, T(k1) - 0.02, l1, l2, L))
        sfx.append((T(k0), "sfx_drawer", -12))
    shots.append(Shot(T(end_k), dur - T(end_k), draw=end_scene("showcase"), trans="fade"))
    sfx.append((T(end_k) + 0.4, "sfx_star_birth", -4))
    for s in shots:
        for tl, kind, *_ in s.fx:
            sfx.append((s.start + tl, "sfx_like" if kind == "burst" else "sfx_spark_2", -3 if kind == "burst" else -8))
    return Spec(f"showcase_{dur}_{L}", dur, L, shots, caps, [], "tiny_explorer", 10.45 - 0.0, sfx)


def spec_pov(dur, lang):
    L = lang
    ko = L == "ko"
    shots, caps, ovs, sfx = [], [], [], []
    if dur == 15:
        shots += [
            Shot(0.0, 2.0, src=(0.1, 0.9), place=(Place(1.0), Place(1.06, 540, 500, 540, 900)), trans="cut",
                 fx=[(1.33, "tap", 300, 985)]),
            Shot(2.0, 2.3, src=(2.85, 0.45), place=(Place(1.0), Place(1.16, 540, 1560, 626, 1250))),
            Shot(4.3, 2.7, src=(4.4, 1.05), place=(Place(1.0), Place(1.05))),
            Shot(7.0, 0.6, draw=None, src=(14.45, 0.5), place=(Place(1.0), Place(1.0)), trans="cut"),
            Shot(7.6, 3.6, src=(14.75, 0.75), place=(Place(0.8, 540, 900, 540, 1170), Place(0.74, 540, 1100, 540, 1270)),
                 trans="cut", fx=[(1.93, "burst", 110, 1632)]),
            Shot(11.2, 2.1, src=(45.0, 1.2), place=(FRAMED, Place(0.7, 540, 1000, 540, 1180))),
            Shot(13.3, 1.7, draw=end_scene("pov"), trans="fade"),
        ]
        caps += [
            pov_cap(0.0, 2.0, ["POV:", "100m 안에 가야만", "열리는 [일기]를 발견함"] if ko else
                    ["POV:", "you found a diary that", "only opens [within 100m]"], L),
            pov_cap(2.05, 4.3, ["제목만 보이고", "나머지는 전부 [잠겨] 있음"] if ko else
                    ["Only the title shows.", "Everything else is [locked]."], L),
            pov_cap(4.35, 7.0, ["그래서 [직접] 가 봄"] if ko else ["So I [went there]."], L, y=250),
            pov_cap(7.62, 11.2, ["누군가 이 자리에", "남긴 [기억]이 열림"] if ko else
                    ["A memory someone left", "[right here], unlocked"], L),
            pov_cap(11.25, 13.3, ["한 번 연 별은", "[별 도감]에 영원히 남음"] if ko else
                    ["Once opened, it stays", "in your [Star Log] forever"], L),
        ]
        ovs += [(4.3, 7.0, distance_chip(4.3, 7.0, y=560)), (7.0, 7.6, flash_overlay()),
                (7.0, 7.6, big_word("열렸다!", "Unlocked!"))]
        sfx += [(1.33, "sfx_spark_1", -6), (2.0, "open_diary", -8), (6.75, "sfx_spark_3", -2),
                (7.0, "sfx_star_birth", -2), (7.6 + 1.93, "sfx_like", -4), (11.2, "sfx_drawer", -12),
                (13.7, "sfx_star_birth", -6)]
    else:
        shots += [
            Shot(0.0, 2.6, src=(0.0, 0.7), place=(Place(1.0), Place(1.06, 540, 500, 540, 900)), trans="cut",
                 fx=[(1.86, "tap", 300, 985)]),
            Shot(2.6, 2.6, src=(2.85, 0.4), place=(Place(1.0), Place(1.16, 540, 1560, 626, 1250))),
            Shot(5.2, 1.6, src=(3.3, 0.3), place=(Place(1.16, 540, 1560, 626, 1250), Place(1.3, 540, 1470, 702, 1150)), trans="cut"),
            Shot(6.8, 3.6, src=(4.4, 0.8), place=(Place(1.0), Place(1.06))),
            Shot(10.4, 0.6, src=(7.9, 0.5), place=(Place(1.0), Place(1.0)), trans="cut"),
            Shot(11.0, 2.6, src=(8.0, 1.0), place=(Place(0.8, 540, 1000, 540, 1250), Place(0.74, 540, 1000, 540, 1210)), trans="cut"),
            Shot(13.6, 4.0, src=(14.6, 0.7), place=(Place(0.8, 540, 900, 540, 1170), Place(0.74, 540, 1100, 540, 1270)),
                 fx=[(2.29, "burst", 110, 1632)]),
            Shot(17.6, 3.0, src=(80.0, 2.3), place=(Place(1.0), Place(1.08, 540, 1000, 540, 960))),
            Shot(20.6, 3.0, src=(44.8, 1.3), place=(FRAMED, Place(0.7, 540, 1000, 540, 1180))),
            Shot(23.6, 2.8, src=(108.5, 1.9), place=(Place(1.0), Place(1.12, 540, 1000, 540, 1000))),
            Shot(26.4, 3.6, draw=end_scene("pov"), trans="fade"),
        ]
        caps += [
            pov_cap(0.0, 2.6, ["POV:", "100m 안에 가야만", "열리는 [일기]를 발견함"] if ko else
                    ["POV:", "you found a diary that", "only opens [within 100m]"], L),
            pov_cap(2.65, 5.2, ["제목만 보이고", "나머지는 전부 [잠겨] 있음"] if ko else
                    ["Only the title shows.", "Everything else is [locked]."], L),
            pov_cap(5.22, 6.8, ["광고로 열 수도 있지만…"] if ko else ["I could unlock it with an ad, but…"], L),
            pov_cap(6.85, 10.4, ["[직접] 걸어가 보기로 함"] if ko else ["I decided to [walk there]."], L),
            pov_cap(11.05, 13.6, ["도착하니 별이 [3개]나", "겹쳐 있었음"] if ko else
                    ["Turned out [3 stars]", "were stacked right here"], L),
            pov_cap(13.65, 17.6, ["누군가 이 자리에", "남긴 [기억]이 열림"] if ko else
                    ["A memory someone left", "[right here], unlocked"], L),
            pov_cap(17.65, 20.6, ["주변 별들은", "[별자리]로 이어지고"] if ko else
                    ["Nearby stars link up", "into [constellations]"], L),
            pov_cap(20.65, 23.6, ["한 번 연 별은", "[별 도감]에 영원히 남음"] if ko else
                    ["Once opened, it stays", "in your [Star Log] forever"], L),
            pov_cap(23.65, 26.4, ["지구 어디에나", "누군가의 [별]이 있음"] if ko else
                    ["Everywhere on Earth,", "someone left a [star]"], L),
        ]
        ovs += [(6.8, 10.4, distance_chip(6.8, 10.4, y=560)), (10.4, 11.0, flash_overlay()),
                (10.4, 11.0, big_word("도착!", "Arrived!"))]
        sfx += [(1.86, "sfx_spark_1", -6), (2.6, "open_diary", -8), (10.1, "sfx_spark_3", -2),
                (10.4, "sfx_star_birth", -2), (11.0, "sfx_drawer", -12), (13.6 + 2.29, "sfx_like", -4),
                (17.6, "sfx_drawer", -12), (20.6, "sfx_drawer", -12), (23.6, "sfx_drawer", -12), (26.8, "sfx_star_birth", -6)]
    for s in shots:
        for tl, kind, *_ in s.fx:
            if kind == "tap":
                pass
    return Spec(f"pov_{dur}_{L}", dur, L, shots, caps, ovs, "star_whisper", 17.81, sfx, bg_seed=11)


def spec_collect(dur, lang):
    L = lang
    ko = L == "ko"
    bar = bars(129.2, 4)     # 1.858s
    q = bars(129.2, 0.25)    # 16분음표 0.116s
    shots, caps, ovs, sfx = [], [], [], []
    B = lambda k: round(k * bar, 3)
    hook = ("별 모양 [21종]", "다 모을 수 있을까?") if ko else ("[21] star shapes.", "Can you collect them all?")
    if dur == 15:
        g0, g1 = B(1), B(3)
        shots += [
            Shot(0, B(1), draw=hook_star_scene(17, 15), trans="cut"),
            Shot(g0, g1 - g0, draw=grid_scene(0.0, q, lock_at=2.75), trans="fade"),
            Shot(g1, B(4) - g1, src=(52.8, 2.0), place=(FRAMED, Place(0.7, 540, 1100, 540, 1230))),
            Shot(B(4), B(5) - B(4), draw=unlock_card_scene(), trans="cut"),
            Shot(B(5), B(6) - B(5), draw=grid_scene(-10.0, 0, lock_at=-1, collect_at=0.15, collect_step=q * 0.75), trans="fade"),
            Shot(B(6), 13.0 - B(6), src=(32.4, 1.8), place=(FRAMED, FRAMED)),
            Shot(13.0, 2.0, draw=end_scene("collect"), trans="fade"),
        ]
        caps += [
            cap(0.05, g0 + 2.6, hook[0], hook[1], L),
            cap(g0 + 2.75, g1, "처음엔 [3개]만" if ko else "You start with just [3]", None, L, accent=GOLD),
            cap(g1 + 0.05, B(4), "업적을 깰 때마다" if ko else "Every achievement", "새 모양이 [열려요]" if ko else "[unlocks] a new shape", L),
            cap(B(5) + 0.05, B(6), "모을수록 빛나는" if ko else "The more you collect,", "[밤하늘]" if ko else "the brighter the [sky]", L),
            cap(B(6) + 0.05, 13.0, "칭호와 히든 업적까지" if ko else "Titles and hidden badges", "프로필에서 [반짝]" if ko else "[shine] on your profile", L),
        ]
        sfx += [(0.0, "sfx_star_birth", -6)]
        sfx += [(g0 + i * q, f"sfx_spark_{1 + i % 3}", -9) for i in range(0, 21, 2)]
        sfx += [(g0 + 2.75, "turning_dial", -8), (g1, "sfx_drawer", -12), (B(4), "sfx_star_birth", -3),
                (B(6), "sfx_drawer", -12), (13.4, "sfx_star_birth", -6)]
        sfx += [(B(5) + 0.15 + i * q * 0.75, f"sfx_spark_{1 + i % 3}", -8) for i in range(0, 18, 2)]
    else:
        g0, g1 = B(1), B(3)
        shots += [
            Shot(0, B(1), draw=hook_star_scene(17, 15), trans="cut"),
            Shot(g0, g1 - g0, draw=grid_scene(0.0, q, lock_at=2.75), trans="fade"),
            Shot(g1, B(5) - g1, src=(23.0, 1.35), place=(FRAMED, Place(0.7, 540, 900, 540, 1180))),
            Shot(B(5), B(7) - B(5), src=(52.5, 1.35), place=(FRAMED, Place(0.7, 540, 1100, 540, 1230))),
            Shot(B(7), B(9) - B(7), draw=unlock_card_scene(), trans="cut"),
            Shot(B(9), B(10) - B(9), src=(57.9, 1.0), place=(FRAMED, Place(0.72, 540, 800, 540, 1180))),
            Shot(B(10), B(12) - B(10), draw=grid_scene(-10.0, 0, lock_at=-1, collect_at=0.3, collect_step=q * 1.5), trans="fade"),
            Shot(B(12), B(14) - B(12), src=(32.2, 1.25), place=(FRAMED, FRAMED)),
            Shot(B(14), dur - B(14), draw=end_scene("collect"), trans="fade"),
        ]
        caps += [
            cap(0.05, g0 + 2.6, hook[0], hook[1], L),
            cap(g0 + 2.75, g1, "처음엔 [3개]만" if ko else "You start with just [3]", None, L, accent=GOLD),
            cap(g1 + 0.05, B(5), "내가 남긴 별이 쌓여" if ko else "Every star you leave", "[나만의 우주]가 돼요" if ko else "builds [your own universe]", L),
            cap(B(5) + 0.05, B(7), "[업적 69개]" if ko else "[69 achievements]", "깰 때마다 새 모양이 열려요" if ko else "each one unlocks something new", L),
            cap(B(9) + 0.05, B(10), "전 세계 단 한 명만" if ko else "[Hidden achievements]", "가질 수 있는 [히든 업적]" if ko else "only one person in the world gets", L, accent=GOLD),
            cap(B(10) + 0.05, B(12), "모을수록 빛나는" if ko else "The more you collect,", "[밤하늘]" if ko else "the brighter the [sky]", L),
            cap(B(12) + 0.05, B(14), "모은 칭호는" if ko else "Your titles", "프로필에서 [반짝]" if ko else "[shine] on your profile", L),
        ]
        sfx += [(0.0, "sfx_star_birth", -6)]
        sfx += [(g0 + i * q, f"sfx_spark_{1 + i % 3}", -9) for i in range(0, 21, 2)]
        sfx += [(g0 + 2.75, "turning_dial", -8), (g1, "sfx_drawer", -12), (B(5), "sfx_drawer", -12),
                (B(7), "sfx_star_birth", -3), (B(9), "sfx_drawer", -12), (B(12), "sfx_drawer", -12),
                (B(14) + 0.4, "sfx_star_birth", -6)]
        sfx += [(B(10) + 0.3 + i * q * 1.5, f"sfx_spark_{1 + i % 3}", -8) for i in range(0, 18, 2)]
    return Spec(f"collect_{dur}_{L}", dur, L, shots, caps, ovs, "cosmic_funk", 15.07, sfx, bg_seed=5)


def all_specs():
    out = {}
    for f in (spec_showcase, spec_pov, spec_collect):
        for dur in (15, 30):
            for lang in ("ko", "en"):
                s = f(dur, lang)
                out[s.name] = s
    return out


# ─────────────────────────── 렌더 ───────────────────────────

class Renderer:
    def __init__(self, spec: Spec):
        self.spec = spec
        self.bg = Background(spec.bg_seed)
        self.cur = None
        self.reader: ClipReader | None = None
        self.prev_frame: Image.Image | None = None
        self.last_frame: Image.Image | None = None

    def shot_at(self, t):
        for s in self.spec.shots:
            if s.start <= t < s.start + s.dur:
                return s
        return self.spec.shots[-1]

    def frame(self, t) -> Image.Image:
        sp = self.spec
        s = self.shot_at(t)
        if s is not self.cur:
            if self.reader:
                self.reader.close()
            self.reader = None
            self.prev_frame = self.last_frame
            self.cur = s
            if s.src:
                n = int(round(s.dur * FPS)) + 2
                self.reader = ClipReader(s.src[0], s.src[1], n, sp.lang, hold=len(s.src) > 2)
        tl = t - s.start
        u = clamp(tl / s.dur)
        canvas = self.bg.frame(t)
        mapper = None
        if s.src and self.reader:
            src = self.reader.next()
            p = mix_place(s.place[0], s.place[1], u)
            if s.trans == "punch" and tl < 0.3:
                k = 1 + 0.05 * (1 - ease_out(tl / 0.3))
                p = Place(p.scale * k, p.ax, p.ay, p.ox, p.oy)
            if src is not None:
                mapper = draw_screen(canvas, src, p)
        if s.draw:
            s.draw(canvas, tl, u, sp.lang)
        if mapper:
            for ft, kind, sx, sy in s.fx:
                k = tl - ft
                ox, oy = mapper(sx, sy)
                if kind == "tap" and 0 <= k < 0.55:
                    ring = Image.new("RGBA", (W, H), (0, 0, 0, 0))
                    d = ImageDraw.Draw(ring)
                    r = 18 + 90 * ease_out(k / 0.55)
                    a = int(255 * (1 - k / 0.55))
                    d.ellipse((ox - r, oy - r, ox + r, oy + r), outline=(255, 255, 255, a), width=6)
                    d.ellipse((ox - 18, oy - 18, ox + 18, oy + 18), fill=(255, 255, 255, int(a * 0.6)))
                    canvas.alpha_composite(ring)
                if kind == "burst":
                    sparkle_burst(canvas, ox, oy, k / 0.8, seed=int(ft * 10), n=18, radius=200,
                                  colors=(SKY, WHITE, MINT, PINK))
        for st, en, fn in sp.overlays:
            if st <= t < en:
                fn(canvas, t - st, sp.lang)
        if any(c.start <= t < c.end for c in sp.captions):
            canvas.alpha_composite(top_scrim())
        for c in sp.captions:
            draw_caption(canvas, c, t)
        canvas.alpha_composite(vignette())
        # 전환 — fade: 이전 컷 마지막 프레임과 섞기 / punch: 흰 번쩍임
        if self.prev_frame is not None and tl < 0.25:
            if s.trans == "fade":
                canvas = Image.blend(self.prev_frame, canvas, ease_in_out(tl / 0.25))
            elif s.trans == "punch" and tl < 0.12:
                canvas.alpha_composite(Image.new("RGBA", (W, H), (255, 255, 255, int(70 * (1 - tl / 0.12)))))
        fade_in = clamp(t / 0.25)
        fade_out = clamp((sp.dur - t) / 0.35)
        if fade_in < 1 or fade_out < 1:
            canvas = Image.blend(Image.new("RGBA", (W, H), (0, 0, 0, 255)), canvas, min(fade_in, fade_out))
        self.last_frame = canvas
        return canvas


def mix_audio(spec: Spec, out_wav: Path):
    inputs = ["-ss", f"{spec.bgm_start:.3f}", "-t", f"{spec.dur + 0.5:.3f}", "-i", str(RAW / f"bgm_{spec.bgm}.mp3")]
    parts = [f"[0:a]volume=-3dB,afade=t=in:st=0:d=0.25,afade=t=out:st={spec.dur - 1.3:.3f}:d=1.3[b0]"]
    labels = ["[b0]"]
    for i, (t, name, gain) in enumerate(sorted(spec.sfx)):
        if t >= spec.dur - 0.05:
            continue
        inputs += ["-i", str(RAW / f"{name}.mp3")]
        ms = int(t * 1000)
        parts.append(f"[{i + 1}:a]volume={gain}dB,adelay={ms}|{ms}[s{i}]")
        labels.append(f"[s{i}]")
    parts.append(f"{''.join(labels)}amix=inputs={len(labels)}:normalize=0:dropout_transition=0,"
                 f"atrim=0:{spec.dur:.3f},alimiter=limit=0.9,loudnorm=I=-14:TP=-1.2:LRA=11[out]")
    subprocess.run(["ffmpeg", "-y", "-v", "error", *inputs, "-filter_complex", ";".join(parts), "-map", "[out]",
                    "-ar", "48000", "-ac", "2", str(out_wav)], check=True)


def render_spec(name: str):
    spec = all_specs()[name]
    OUT.mkdir(parents=True, exist_ok=True)
    WORK.mkdir(parents=True, exist_ok=True)
    tmp_v = WORK / f"{name}.video.mp4"
    tmp_a = WORK / f"{name}.audio.wav"
    final = OUT / f"stary_{name}.mp4"
    enc = subprocess.Popen(
        ["ffmpeg", "-y", "-v", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
         "-i", "-", "-c:v", "libx264", "-preset", "slow", "-crf", "17", "-pix_fmt", "yuv420p", "-threads", "2",
         str(tmp_v)], stdin=subprocess.PIPE)
    r = Renderer(spec)
    n = int(round(spec.dur * FPS))
    for i in range(n):
        enc.stdin.write(r.frame(i / FPS).convert("RGB").tobytes())
    enc.stdin.close()
    enc.wait()
    if r.reader:
        r.reader.close()
    mix_audio(spec, tmp_a)
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", str(tmp_v), "-i", str(tmp_a), "-c:v", "copy", "-c:a", "aac",
                    "-b:a", "192k", "-shortest", "-movflags", "+faststart", str(final)], check=True)
    print(f"✓ {final.relative_to(ROOT)}", flush=True)
    return str(final)


def stills(name: str, times: list[float]):
    spec = all_specs()[name]
    r = Renderer(spec)
    want = sorted(times)
    ims = []
    n = int(round(spec.dur * FPS))
    j = 0
    for i in range(n):
        t = i / FPS
        f = r.frame(t)
        while j < len(want) and t >= want[j] - 1e-6:
            ims.append((want[j], f.convert("RGB")))
            j += 1
        if j >= len(want):
            break
    if r.reader:
        r.reader.close()
    w, h = 360, 640
    sheet = Image.new("RGB", (w * len(ims), h + 40), (0, 0, 0))
    d = ImageDraw.Draw(sheet)
    for k, (t, im) in enumerate(ims):
        sheet.paste(im.resize((w, h), Image.LANCZOS), (k * w, 40))
        d.text((k * w + 8, 6), f"{t:.2f}s", font=font(26, 600), fill=(255, 220, 90))
    WORK.mkdir(parents=True, exist_ok=True)
    out = WORK / f"stills_{name}.png"
    sheet.save(out)
    print(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", nargs="*")
    ap.add_argument("--stills", nargs="+")
    ap.add_argument("--jobs", type=int, default=6)
    a = ap.parse_args()
    if a.stills:
        stills(a.stills[0], [float(x) for x in a.stills[1:]])
        return
    names = a.only or list(all_specs().keys())
    if len(names) == 1 or a.jobs <= 1:
        for n in names:
            render_spec(n)
    else:
        with Pool(min(a.jobs, len(names))) as pool:
            pool.map(render_spec, names)


if __name__ == "__main__":
    sys.exit(main())
