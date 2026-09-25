"""STARY 시네마틱 쇼츠 광고 렌더러 — 9:16 1080x1920 · 24fps · 40초 · 무자막 · 앱 화면 없음.

스토리(설정 설명 없이 감정선만 — 궁금함 → 경이 → 그리움 → 희망):
  A  0.0– 5.0  밤 다리 위를 걷는 남자(뒷모습) — 따라가는 푸시인
  B  4.6– 9.6  난간에 멈춰 선 그, 도시 위로 별이 하나 둘 뜬다        (생성 클립 scene1.mp4)
  C  9.4–13.4  다섯 개의 별 — 그의 시선을 따라 별 쪽으로 팬          (생성 클립 scene2.mp4)
  D 13.4–16.4  금빛 별로 다가가며 빛이 화면을 채운다
  E 16.2–25.4  별 속의 하루들: 노을 속 연인 · 벚꽃 · 불꽃놀이 가족 · 아빠와 아이
  F 25.2–40.0  다시 밤. 그의 앞에서 작은 금빛 빛이 솟아 새 별이 되고, 카메라가 하늘로 → 로고 → 페이드아웃

16:9 소스를 9:16 에 담는 방식: 가로를 넓게 자르고 모자라는 위쪽은 같은 톤의 밤하늘로 확장(extend_sky)
→ 도시는 아래, 별이 뜰 하늘은 위. 확장 하늘의 잔별(StarField)은 이미지 하늘까지 이어 그려 경계를 숨긴다.

소스: git 이력(ab247e3^)의 `references/stary 광고 씬 모음/` 스틸·생성 클립(자동 추출 → build/cinematic/src),
앱 로고 `drawable/logo.webp`, BGM `raw/bgm_forgotten_galaxy.mp3`(2:02.3~ — 조용히 시작해 25초 뒤 가장 커지는 구간),
효과음 `raw/sfx_spark_*.mp3`·`sfx_star_birth.mp3`.

사용:
  python tools/ad/cinematic.py                       # 전체 렌더 → references/stary_cinematic_9x16.mp4
  python tools/ad/cinematic.py --stills 3 12 31 37   # 해당 시각(초) 프레임만 PNG 로(검수용, build/cinematic/stills)
"""
import argparse
import math
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy.io import wavfile
from scipy.ndimage import gaussian_filter, gaussian_filter1d
from scipy.signal import butter, sosfilt

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
WORK = os.path.join(ROOT, "build", "cinematic")
SRC = os.path.join(WORK, "src")
RAW = os.path.join(ROOT, "androidApp", "src", "main", "res", "raw")
LOGO = os.path.join(ROOT, "androidApp", "src", "main", "res", "drawable", "logo.webp")
OUT = os.path.join(ROOT, "references", "stary_cinematic_9x16.mp4")
GIT_REV = "ab247e3^"
GIT_DIR = "references/stary 광고 씬 모음/"

W, H, FPS, DUR = 1080, 1920, 24, 40.0
NF = int(DUR * FPS)
SR = 48000

BGM = "bgm_forgotten_galaxy.mp3"
BGM_OFFSET = 122.3

# ---- 타임라인(초) -------------------------------------------------------------------------------
T_A = (0.0, 5.0)
T_B = (4.6, 9.64)       # scene1.mp4 121프레임
T_C = (9.4, 13.4)       # scene2.mp4 96프레임
T_D = (13.4, 16.45)
T_E1 = (16.15, 18.55)
T_E2 = (18.35, 20.55)
T_E3 = (20.35, 22.75)
T_E4 = (22.55, 25.45)
T_F = (25.2, 40.0)
RISE_T0, BLOOM_T = 29.6, 32.8       # 금빛 빛이 솟기 시작 / 별로 피어나는 순간
LOGO_T0 = 35.6
FADE_OUT = (38.9, 40.0)
SPARK_TIMES = [T_B[0] + 0.5, T_B[0] + 1.6, T_B[0] + 1.95, T_C[0] + 0.35]


# ---- 소스 -------------------------------------------------------------------------------------
def src(name):
    """git 이력에서 광고 소스를 한 번만 꺼내 캐시한다(원본 자산은 references 에서 정리됨)."""
    p = os.path.join(SRC, name.replace("/", "_"))
    if not os.path.exists(p):
        os.makedirs(SRC, exist_ok=True)
        data = subprocess.run(["git", "-C", ROOT, "show", f"{GIT_REV}:{GIT_DIR}{name}"],
                              capture_output=True, check=True).stdout
        with open(p, "wb") as f:
            f.write(data)
    return p


def load_rgb(path):
    return np.asarray(Image.open(path).convert("RGB"))


# ---- 수학 -------------------------------------------------------------------------------------
def clamp01(x):
    return min(1.0, max(0.0, x))


def ramp(t, t0, t1):
    return clamp01((t - t0) / (t1 - t0))


def smooth(x):
    x = clamp01(x)
    return x * x * (3 - 2 * x)


def ease_io(x):
    x = clamp01(x)
    return 0.5 - 0.5 * math.cos(math.pi * x)


def ease_out(x):
    x = clamp01(x)
    return 1 - (1 - x) ** 3


def ease_in(x):
    x = clamp01(x)
    return x ** 3


def lerp(a, b, t):
    return a + (b - a) * t


def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float32) / 255


# 앱 StarStyle 팔레트에서 고른 별 색
GOLD = hexc("FFD54F")
AMBER = hexc("FFB74D")
BLUE = hexc("64B5F6")
CORAL = hexc("FF8A65")
ICE = hexc("B3E5FC")
WARM_WHITE = np.array([1.0, 0.93, 0.80], np.float32)


# ---- 하늘 확장 · 카메라 -------------------------------------------------------------------------
def extend_sky(img, ext, zenith_mul=0.45, state=None, feather=24, decay=90.0):
    """이미지 위로 ext 픽셀만큼 같은 톤의 밤하늘을 잇는다.
    이음새 색 = 맨 윗줄을 가로로 흐린 값. 가로등 번짐 같은 밝은 부분(excess)은 위로 decay px 만에 사라지고,
    바탕은 천정색(zenith)으로 어두워진다. feather 줄만큼 원본 윗부분을 이음새 색과 섞어 경계를 숨긴다."""
    top = img[:10].astype(np.float32).mean(0)
    top = gaussian_filter1d(top, sigma=img.shape[1] * 0.06, axis=0, mode="nearest")
    if state is not None:  # 영상 소스는 프레임마다 윗줄이 떨리므로 지수 평활
        if "top" in state:
            top = state["top"] * 0.85 + top * 0.15
        state["top"] = top
    base = np.median(top, 0)
    excess = top - base
    zen = base * zenith_mul + np.array([1.0, 1.5, 4.0], np.float32)
    dd = np.arange(ext, 0, -1, dtype=np.float32)[:, None, None]
    wgt = (dd / ext) ** 1.15
    sky = base * (1 - wgt) + zen * wgt + excess[None] * np.exp(-dd / decay)
    out = np.concatenate([np.clip(sky, 0, 255).astype(np.uint8), img], 0)
    a = np.linspace(0, 1, feather, dtype=np.float32)
    a = (a * a * (3 - 2 * a))[:, None, None]
    out[ext:ext + feather] = (top[None] * (1 - a) + img[:feather].astype(np.float32) * a).astype(np.uint8)
    return out


class Cam:
    """캔버스 좌표계 → 1080x1920 화면. 앵커 P(캔버스)가 화면점 F 에 오도록 zoom 배율로 자른다."""

    def __init__(self, canvas_w, canvas_h):
        self.cw, self.ch = canvas_w, canvas_h

    def box(self, px, py, fx, fy, z):
        z = max(z, W / self.cw, H / self.ch)
        bw, bh = W / z, H / z
        x0 = px - fx / z
        y0 = py - fy / z
        x0 = min(max(x0, 0), self.cw - bw)
        y0 = min(max(y0, 0), self.ch - bh)
        return (x0, y0, x0 + bw, y0 + bh), z


def view(pil, box):
    im = pil.resize((W, H), Image.BICUBIC, box=box)
    return np.asarray(im, np.float32) / 255


def to_screen(box, z, x, y):
    return (x - box[0]) * z, (y - box[1]) * z


# ---- 별 그리기 --------------------------------------------------------------------------------
class StarField:
    """잔별(가산). 캔버스 좌표로 만들고 화면으로 투영해 카메라와 함께 움직인다."""

    def __init__(self, seed, x0, x1, y0, y1, n, canvas=None, reject=None, dens_top=2.2, faint=0.10, bright_pow=7):
        rng = np.random.default_rng(seed)
        xs, ys = [], []
        lum = None
        if canvas is not None:
            lum = canvas.astype(np.float32).mean(-1) / 255
            lum = gaussian_filter(lum, 3)
        tries = 0
        while len(xs) < n and tries < n * 40:
            tries += 1
            x, y = rng.uniform(x0, x1), rng.uniform(y0, y1)
            # 위로 갈수록 촘촘(도시 광공해 느낌)
            p = (1 + (dens_top - 1) * (1 - (y - y0) / max(1, y1 - y0))) / dens_top
            if rng.random() > p:
                continue
            if lum is not None and lum[int(y), int(x)] > 0.075:
                continue
            if reject is not None and reject(x, y):
                continue
            xs.append(x)
            ys.append(y)
        m = len(xs)
        self.x = np.array(xs, np.float32)
        self.y = np.array(ys, np.float32)
        u = rng.random(m)
        self.b = (faint + 0.9 * u ** bright_pow).astype(np.float32)          # 대부분 희미, 소수만 밝게
        self.s = (0.55 + 0.65 * self.b).astype(np.float32)
        tint = rng.random(m)
        self.col = np.stack([np.where(tint < 0.3, 0.80, 1.0), np.full(m, 0.92),
                             np.where(tint > 0.75, 0.78, 1.0)], 1).astype(np.float32)
        self.tf = rng.uniform(0.25, 1.1, m).astype(np.float32)
        self.tp = rng.uniform(0, 6.28, m).astype(np.float32)
        self.td = rng.uniform(0.15, 0.55, m).astype(np.float32)

    def draw(self, frame, box, z, t, alpha=1.0, mask=None):
        if alpha <= 0.01:
            return
        sx, sy = to_screen(box, z, self.x, self.y)
        vis = (sx > -4) & (sx < W + 4) & (sy > -4) & (sy < H + 4)
        zs = z ** 0.5
        for i in np.nonzero(vis)[0]:
            tw = 1 - self.td[i] * (0.5 + 0.5 * math.sin(self.tf[i] * 6.283 * t + self.tp[i]))
            a = self.b[i] * tw * alpha * 0.85
            sig = self.s[i] * zs
            r = int(math.ceil(sig * 2.6))
            cx, cy = sx[i], sy[i]
            ix, iy = int(cx), int(cy)
            xa, xb = max(ix - r, 0), min(ix + r + 1, W)
            ya, yb = max(iy - r, 0), min(iy + r + 1, H)
            if xa >= xb or ya >= yb:
                continue
            gx = np.exp(-((np.arange(xa, xb) - cx) ** 2) / (2 * sig * sig))
            gy = np.exp(-((np.arange(ya, yb) - cy) ** 2) / (2 * sig * sig))
            k = np.outer(gy, gx)[..., None] * (self.col[i] * a)
            if mask is not None:
                k = k * (1 - mask[ya:yb, xa:xb, None])
            frame[ya:yb, xa:xb] += k


def draw_star(frame, x, y, color, core=3.0, halo=30.0, spike=120.0, inten=1.0, mask=None, diag=0.22):
    """앱 별처럼 4갈래 빛줄기 + 코어 + 후광(가산). mask(화면, 1=가림)가 있으면 그만큼 가린다."""
    if inten <= 0.002:
        return
    R = int(max(halo * 3.2, spike * 1.05, core * 5)) + 2
    xa, xb = max(int(x) - R, 0), min(int(x) + R + 1, W)
    ya, yb = max(int(y) - R, 0), min(int(y) + R + 1, H)
    if xa >= xb or ya >= yb:
        return
    xx = (np.arange(xa, xb, dtype=np.float32) - x)[None, :]
    yy = (np.arange(ya, yb, dtype=np.float32) - y)[:, None]
    r2 = xx * xx + yy * yy
    r = np.sqrt(r2)
    c = np.exp(-r2 / (2 * core * core))
    hl = 0.55 * np.exp(-r / halo) + 0.35 * np.exp(-r2 / (2 * (halo * 0.45) ** 2))
    w = max(0.9, core * 0.32)
    fall = spike * 0.30
    sp = np.exp(-(yy * yy) / (2 * w * w)) * np.exp(-np.abs(xx) / fall) \
        + np.exp(-(xx * xx) / (2 * w * w)) * np.exp(-np.abs(yy) / fall)
    if diag > 0:
        u, v = (xx + yy) * 0.7071, (xx - yy) * 0.7071
        sp = sp + diag * (np.exp(-(v * v) / (2 * w * w)) * np.exp(-np.abs(u) / (fall * 0.45))
                          + np.exp(-(u * u) / (2 * w * w)) * np.exp(-np.abs(v) / (fall * 0.45)))
    white = np.array([1, 1, 1], np.float32)
    rgb = c[..., None] * (white * 0.95 + color * 0.25) + (hl + sp * 0.8)[..., None] * color
    rgb *= inten
    if mask is not None:
        rgb *= (1 - mask[ya:yb, xa:xb])[..., None]
    frame[ya:yb, xa:xb] += rgb


def draw_trail(frame, pts, color, sigma, inten, mask=None):
    """점 목록(화면, 머리→꼬리)을 가우시안으로 이어 그린 빛꼬리. 꼬리로 갈수록 옅어진다."""
    n = len(pts)
    r = int(math.ceil(sigma * 3))
    ax = np.arange(-r, r + 1, dtype=np.float32)
    for j, (x, y) in enumerate(pts):
        a = inten * (1 - j / n) ** 1.6
        ix, iy = int(x), int(y)
        xa, xb = max(ix - r, 0), min(ix + r + 1, W)
        ya, yb = max(iy - r, 0), min(iy + r + 1, H)
        if a <= 0.002 or xa >= xb or ya >= yb:
            continue
        gx = np.exp(-((np.arange(xa, xb) - x) ** 2) / (2 * sigma * sigma))
        gy = np.exp(-((np.arange(ya, yb) - y) ** 2) / (2 * sigma * sigma))
        k = np.outer(gy, gx) * a
        if mask is not None:
            k = k * (1 - mask[ya:yb, xa:xb])
        frame[ya:yb, xa:xb] += k[..., None] * color


def ring(frame, x, y, radius, width, color, inten):
    """별이 피어날 때 번지는 얇은 링(앱의 도착 플래시)."""
    if inten <= 0.002:
        return
    R = int(radius + width * 4) + 2
    xa, xb = max(int(x) - R, 0), min(int(x) + R + 1, W)
    ya, yb = max(int(y) - R, 0), min(int(y) + R + 1, H)
    if xa >= xb or ya >= yb:
        return
    xx = (np.arange(xa, xb, dtype=np.float32) - x)[None, :]
    yy = (np.arange(ya, yb, dtype=np.float32) - y)[:, None]
    d = np.sqrt(xx * xx + yy * yy) - radius
    frame[ya:yb, xa:xb] += (np.exp(-(d * d) / (2 * width * width)) * inten)[..., None] * color


# ---- 후처리 ----------------------------------------------------------------------------------
_rng = np.random.default_rng(3)
_GRAIN = [np.asarray(Image.fromarray(((_rng.standard_normal((H // 2, W // 2)) * 40) + 128).clip(0, 255)
                                     .astype(np.uint8)).resize((W, H), Image.BILINEAR), np.float32) / 255 - 0.502
          for _ in range(12)]
_yy, _xx = np.mgrid[0:H, 0:W].astype(np.float32)
_rr = np.sqrt(((_xx - W / 2) / (W / 2)) ** 2 * 0.8 + ((_yy - H / 2) / (H / 2)) ** 2)
VIGNETTE = (1 - 0.30 * np.clip(_rr / 1.25, 0, 1) ** 2.4)[..., None].astype(np.float32)
YY, XX = _yy, _xx
del _rr


def blur_small(img, factor=4, sigma=8.0):
    h, w = img.shape[0] // factor, img.shape[1] // factor
    small = img[:h * factor, :w * factor].reshape(h, factor, w, factor, 3).mean((1, 3))
    small = gaussian_filter(small, (sigma, sigma, 0))
    out = np.empty_like(img)
    for c in range(3):
        out[..., c] = np.asarray(Image.fromarray(np.ascontiguousarray(small[..., c])).resize((img.shape[1], img.shape[0]), Image.BILINEAR))
    return out


def bloom(img, thresh, amount, sigma=9.0):
    hi = np.clip(img - thresh, 0, None)
    return img + blur_small(hi, 4, sigma) * amount


def grade_night(f):
    f = bloom(f, 0.55, 0.9, 7.0)
    f = f * np.array([0.97, 1.0, 1.05], np.float32) + np.array([0.004, 0.008, 0.016], np.float32)
    return f


def grade_memory(f):
    soft = blur_small(f, 4, 5.0)
    f = f * 0.80 + soft * 0.28                             # 디퓨전(프로미스트)
    f = bloom(f, 0.62, 0.8, 12.0)
    f = f * np.array([1.05, 0.98, 0.88], np.float32) + np.array([0.035, 0.022, 0.010], np.float32)
    return f


def finish(f, i):
    f = f * VIGNETTE
    lum = f.mean(-1, keepdims=True)
    f = f + _GRAIN[i % len(_GRAIN)][..., None] * (0.020 + 0.035 * np.sqrt(np.clip(lum, 0, 1)))
    knee = 0.86
    f = np.where(f < knee, f, knee + (1 - knee) * (1 - np.exp(-(f - knee) / (1 - knee))))
    return (np.clip(f, 0, 1) * 255 + 0.5).astype(np.uint8)


# ---- 클립 리더 --------------------------------------------------------------------------------
class Clip:
    """ffmpeg 로 잘라·키워 순차로 읽는다. 뒤로 가면 해당 프레임부터 다시 연다."""

    def __init__(self, name, x0, cw, out_w, out_h, start=0.0, src_h=None):
        self.path = src(name)
        self.vf = f"crop={cw}:ih:{x0}:0,scale={out_w}:{out_h}:flags=lanczos"
        self.w, self.h = out_w, out_h
        self.start = start
        self.proc = None
        self.idx = -1
        self.cur = None

    def _open(self, i):
        if self.proc:
            self.proc.kill()
        ss = self.start + i / FPS
        self.proc = subprocess.Popen(["ffmpeg", "-v", "error", "-ss", f"{ss:.4f}", "-i", self.path, "-vf", self.vf,
                                      "-f", "rawvideo", "-pix_fmt", "rgb24", "-"],
                                     stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        self.idx = i - 1

    def get(self, i):
        if self.proc is None or i < self.idx or i > self.idx + 48:
            self._open(i)
        n = self.w * self.h * 3
        while self.idx < i:
            buf = self.proc.stdout.read(n)
            if len(buf) < n:
                break  # 끝 → 마지막 프레임 유지
            self.cur = np.frombuffer(buf, np.uint8).reshape(self.h, self.w, 3)
            self.idx += 1
        return self.cur


# ---- 샷 ------------------------------------------------------------------------------------
class ShotA:
    """다리 위를 걷는 뒷모습(scene1.png) — 걸음 박자 흔들림을 섞은 푸시인."""

    def __init__(self):
        img = load_rgb(src("scene1.png"))  # 1672x941
        x0, cw = 330, 870
        s = W / cw
        plate = np.asarray(Image.fromarray(img[:, x0:x0 + cw]).resize((W, round(img.shape[0] * s)), Image.LANCZOS))
        self.ext = H - plate.shape[0] + 20
        canvas = extend_sky(plate, self.ext)
        self.pil = Image.fromarray(canvas)
        self.cam = Cam(W, canvas.shape[0])
        self.s, self.x0 = s, x0
        self.P = ((765 - x0) * s, self.ext + 420 * s)   # 목덜미
        self.F = (W / 2, self.P[1] - (canvas.shape[0] - H))
        self.stars = StarField(11, 0, W, 0, self.ext + 175 * s, 330, canvas)

    def render(self, t):
        u = t / (T_A[1] - T_A[0])
        z = 1.0 + 0.11 * ease_io(u)
        bob = 2.4 * math.sin(6.283 * 1.7 * t)
        sway = 1.6 * math.sin(6.283 * 0.85 * t + 0.6)
        box, z = self.cam.box(self.P[0] + sway, self.P[1] + bob, self.F[0], self.F[1], z)
        f = view(self.pil, box)
        self.stars.draw(f, box, z, t)
        return grade_night(f)


class ShotClipSky:
    """생성 클립 + 하늘 확장. pan: (cx0, cx1) 캔버스 x 중심 이동, zoom: (z0, z1)."""

    def __init__(self, name, x0, cw, scale, headroom, pan, zoom, seed, star_limit_y, t_range, reject=None,
                 n_stars=380, feather=170, star_kw=None):
        self.out_w = round(cw * scale / 2) * 2
        self.img_h = round(1080 * scale / 2) * 2
        self.clip = Clip(name, x0, cw, self.out_w, self.img_h)
        self.ext = H - self.img_h + headroom
        self.cam = Cam(self.out_w, self.ext + self.img_h)
        self.pan, self.zoom = pan, zoom
        self.state = {}
        self.t0, self.t1 = t_range
        self.feather = feather
        first = self.clip.get(0)
        canvas0 = extend_sky(first, self.ext, state={}, feather=feather)
        self.stars = StarField(seed, 0, self.out_w, 0, self.ext + star_limit_y * scale, n_stars, canvas0, reject,
                               **(star_kw or {}))
        self.last_canvas = None

    def canvas(self, i):
        fr = self.clip.get(i)
        return extend_sky(fr, self.ext, state=self.state, feather=self.feather)

    def cam_at(self, t):
        u = ease_io(t / (self.t1 - self.t0))
        cx = lerp(self.pan[0], self.pan[1], u)
        z = lerp(self.zoom[0], self.zoom[1], t / (self.t1 - self.t0))
        cy = self.cam.ch - H / 2
        return self.cam.box(cx, cy, W / 2, H / 2 + (self.cam.ch - H / 2 - cy), z)

    def render(self, t):
        i = int(round(t * FPS))
        canvas = self.canvas(i)
        self.last_canvas = canvas
        box, z = self.cam_at(t)
        f = view(Image.fromarray(canvas), box)
        self.stars.draw(f, box, z, t)
        return grade_night(f)


class ShotD:
    """C 의 마지막 프레임에서 금빛 별로 밀고 들어가 빛이 화면을 채운다."""

    def __init__(self, shot_c, star_src_xy):
        self.c = shot_c
        self.star = star_src_xy
        self.pil = None

    def render(self, t):
        c = self.c
        if self.pil is None:
            last = c.clip.get(95)
            canvas = extend_sky(last, c.ext, state=dict(c.state), feather=c.feather)
            self.pil = Image.fromarray(canvas)
        dur = T_D[1] - T_D[0]
        u = t / dur
        box0, z0 = c.cam_at(T_C[1] - T_C[0])
        P = self.star
        F0 = to_screen(box0, z0, *P)
        zin = ease_in(ramp(u, 0.05, 1.0))
        z = z0 * (1 + 5.0 * zin)
        F = (lerp(F0[0], W / 2, ease_io(u)), lerp(F0[1], H * 0.42, ease_io(u)))
        cam = Cam(c.cam.cw, c.cam.ch)
        box, z = cam.box(P[0], P[1], F[0], F[1], z)
        f = view(self.pil, box)
        c.stars.draw(f, box, z, T_C[1] - T_C[0] + t, alpha=1 - ramp(u, 0.3, 0.7))
        sx, sy = to_screen(box, z, *P)
        g = 0.35 + 1.6 * ease_in(ramp(u, 0.0, 1.0))
        draw_star(f, sx, sy, GOLD, core=3.0 + 10 * zin, halo=28 + 260 * zin, spike=150 + 900 * zin, inten=g)
        f = grade_night(f)
        R = lerp(90, 2400, ease_in(ramp(u, 0.2, 1.0)) ** 0.8)
        gi = 1.5 * ease_in(ramp(u, 0.2, 1.0)) ** 0.7
        r2 = (XX - sx) ** 2 + (YY - sy) ** 2
        f = f + (np.exp(-r2 / (R * R)) * gi)[..., None] * np.array([1.0, 0.74, 0.46], np.float32)
        wf = ease_in(ramp(u, 0.8, 1.0))
        return f * (1 - wf) + WARM_WHITE * wf * 1.05


class ShotStill:
    """회상용 스틸(Ken Burns). crop=(x0, cw) 원본 px, 캔버스 높이 1920."""

    def __init__(self, name, x0, cw, path_fn):
        img = load_rgb(src(name))
        s = H / img.shape[0]
        crop = img[:, x0:x0 + cw]
        self.pil = Image.fromarray(np.asarray(Image.fromarray(crop).resize((round(cw * s), H), Image.LANCZOS)))
        self.cam = Cam(self.pil.width, H)
        self.path_fn = path_fn  # t -> (cx, cy, z) 캔버스

    def render(self, t):
        cx, cy, z = self.path_fn(t)
        box, z = self.cam.box(cx, cy, W / 2, H / 2, z)
        return grade_memory(view(self.pil, box))


class ShotMemClip:
    def __init__(self, name, start, x0, cw, path_fn, max_i=10 ** 6):
        s = H / 720
        self.max_i = max_i
        self.w = round(cw * s / 2) * 2
        self.clip = Clip(name, x0, cw, self.w, H, start=start)
        self.cam = Cam(self.w, H)
        self.path_fn = path_fn

    def render(self, t):
        fr = self.clip.get(min(self.max_i, int(round(t * FPS))))
        cx, cy, z = self.path_fn(t)
        box, z = self.cam.box(cx, cy, W / 2, H / 2, z)
        return grade_memory(view(Image.fromarray(fr), box))


# scene2.png 속 남자 실루엣(원본 px) — 솟는 빛을 그의 몸 뒤로 가리는 데 쓴다
MAN_POLY = [(482, 941), (488, 800), (498, 720), (512, 650), (532, 600), (560, 560), (620, 525), (688, 497),
            (688, 480), (676, 455), (662, 425), (655, 385), (657, 340), (672, 305), (705, 286), (745, 280),
            (790, 287), (825, 305), (845, 335), (848, 375), (840, 410), (830, 440), (815, 468), (800, 488),
            (800, 497), (840, 512), (890, 538), (928, 572), (950, 620), (965, 690), (975, 780), (985, 941)]


class ShotF:
    """다시 밤(scene2.png). 강물 일렁임 · 기존 별 5개 · 솟는 금빛 빛 → 새 별 · 틸트업 · 로고."""

    def __init__(self):
        img = load_rgb(src("scene2.png"))  # 1670x941
        self.x0, cw = 222, 1036
        s = self.s = W / cw
        plate = np.asarray(Image.fromarray(img[:, self.x0:self.x0 + cw]).resize((W, round(img.shape[0] * s)), Image.LANCZOS))
        self.ext = H - plate.shape[0] + 460
        self.canvas = extend_sky(plate, self.ext)
        self.ch = self.canvas.shape[0]
        self.cam = Cam(W, self.ch)
        # 실루엣 마스크(캔버스)
        m = Image.new("L", (W, self.ch), 0)
        ImageDraw.Draw(m).polygon([self.c(x, y) for x, y in MAN_POLY], fill=255)
        m = np.asarray(m, np.float32) / 255
        self.mask = gaussian_filter(m, 1.6)
        self.mask_pil = Image.fromarray((self.mask * 255).astype(np.uint8))
        # 물결 영역(원본 y 552~705)
        self.wy0, self.wy1 = int(self.c(0, 552)[1]), int(self.c(0, 705)[1])
        man = self.mask[self.wy0:self.wy1]
        self.water_keep = np.clip(man * 1.2, 0, 1)[..., None]
        feather = np.ones(self.wy1 - self.wy0, np.float32)
        n = 10
        feather[:n] = np.linspace(0, 1, n)
        feather[-n:] = np.linspace(1, 0, n)
        self.water_feather = feather[:, None, None]
        # 별
        head = self.c(0, 280)[1]

        def reject(x, y):
            return self.mask[int(y), int(x)] > 0.02 or y > head - 6

        self.stars = StarField(21, 0, W, 0, self.c(0, 300)[1], 620, self.canvas, reject)
        # 기존 별 5개(캔버스) — 끝 화면 기준으로 로고 자리를 피해 배치
        top = self.ext
        self.mem = [  # x, y, color, core, halo, spike, twinkle phase
            (850, top - 1130, GOLD, 3.4, 34, 150, 0.0),
            (215, top - 980, BLUE, 3.0, 28, 120, 1.7),
            (110, top - 360, AMBER, 2.4, 22, 90, 3.1),
            (975, top - 520, CORAL, 2.6, 24, 100, 4.4),
            (760, top - 140, ICE, 2.0, 18, 70, 2.2),
        ]
        self.new_star = (525, top - 660)
        self.rise_from = self.c(792, 610)
        logo = Image.open(LOGO).convert("RGBA")
        lw = 780
        logo = logo.resize((lw, round(logo.height * lw / logo.width)), Image.LANCZOS)
        la = np.asarray(logo, np.float32) / 255
        self.logo_rgb = la[..., :3] * la[..., 3:4]
        self.logo_a = la[..., 3:4]
        self.logo_pos = ((W - lw) // 2, 1000 - 270)

    def c(self, x, y):
        """원본 scene2.png px → 캔버스 px."""
        return (x - self.x0) * self.s, self.ext + y * self.s

    def cam_at(self, t):
        """t = F 로컬. (앵커 cx, cy, zoom) — 푸시인 → 솟는 빛을 따라 틸트업 → 천천히 더 오르며 풀백."""
        gt = t + T_F[0]
        base_cy = self.ch - H / 2
        k1 = ease_io(ramp(gt, T_F[0], RISE_T0 + 0.4))
        k2 = ease_io(ramp(gt, RISE_T0 - 0.2, BLOOM_T + 1.2))
        k3 = smooth(ramp(gt, BLOOM_T - 0.6, DUR + 1.5))
        z = lerp(1.13, 1.19, k1) - 0.12 * k2 - 0.07 * k3
        cy = base_cy + 40 - 10 * k1 - 330 * k2 - 170 * k3
        return W / 2, cy, z

    def water(self, t):
        cv = self.canvas.copy()
        reg = cv[self.wy0:self.wy1].astype(np.float32)
        n = reg.shape[0]
        yy = np.arange(n, dtype=np.float32)
        depth = yy / n
        amp = 0.6 + 3.2 * depth
        dx = amp * (0.65 * np.sin(yy * 0.55 + 2.3 * t) + 0.35 * np.sin(yy * 0.21 - 1.4 * t + 1.3))
        X = np.arange(W, dtype=np.float32)[None, :] + dx[:, None]
        X = np.clip(X, 0, W - 1.001)
        xi = X.astype(np.int32)
        fr = (X - xi)[..., None]
        rows = np.arange(n)[:, None]
        moved = reg[rows, xi] * (1 - fr) + reg[rows, xi + 1] * fr
        glint = 1 + 0.10 * np.sin(yy[:, None] * 0.9 + X * 0.05 + 3.1 * t)[..., None]
        moved = moved * glint
        out = moved * (1 - self.water_keep) + reg * self.water_keep
        out = reg + (out - reg) * self.water_feather
        cv[self.wy0:self.wy1] = np.clip(out, 0, 255).astype(np.uint8)
        return cv

    def render(self, t, gt):
        cx, cy, z = self.cam_at(t)
        box, z = self.cam.box(cx, cy, W / 2, H / 2, z)
        f = view(Image.fromarray(self.water(t)), box)
        mask = np.asarray(self.mask_pil.resize((W, H), Image.BILINEAR, box=box), np.float32) / 255
        self.stars.draw(f, box, z, gt)
        bt = BLOOM_T
        # 새 별이 피는 순간 기존 별들이 차례로 반짝 응답
        for k, (x, y, col, core, halo, spike, ph) in enumerate(self.mem):
            sx, sy = to_screen(box, z, x, y)
            tw = 0.85 + 0.15 * math.sin(gt * 1.3 + ph)
            resp = math.exp(-((gt - (bt + 0.35 + 0.18 * k)) ** 2) / 0.05) * 0.8
            draw_star(f, sx, sy, col, core * z, halo * z, spike * z * (1 + 0.4 * resp), inten=tw + resp)
        # 솟는 빛 — 그와 도시 사이에서 떠오르므로 항상 그의 실루엣 뒤로 가려진다
        if gt >= RISE_T0:
            p0, p1 = self.rise_from, self.new_star

            def path(tt):
                uu = ramp(tt, RISE_T0, bt)
                e = ease_io(uu) ** 0.85
                return lerp(p0[0], p1[0], e) + 30 * math.sin(e * 3.3) * (1 - e), lerp(p0[1], p1[1], e)

            sx, sy = to_screen(box, z, *path(gt))
            if gt < bt:
                fade_in = ease_out(ramp(gt, RISE_T0, RISE_T0 + 0.6))
                grow = 0.75 + 0.35 * ramp(gt, RISE_T0, bt)
                flick = 1 + 0.10 * math.sin(gt * 37)
                trail = []  # 지나온 자리의 잔광 — 화면에서 3px 간격으로 이어 그림
                tt, last = gt, (sx, sy)
                while tt > max(RISE_T0, gt - 0.55) and len(trail) < 160:
                    tt -= 0.004
                    q = to_screen(box, z, *path(tt))
                    if (q[0] - last[0]) ** 2 + (q[1] - last[1]) ** 2 >= 9:
                        trail.append(q)
                        last = q
                draw_trail(f, trail, AMBER, 1.7, 0.10 * fade_in, mask)
                draw_trail(f, trail[:40], GOLD, 5.0, 0.035 * fade_in, mask)
                draw_star(f, sx, sy, GOLD, core=2.3 * grow, halo=24 * grow, spike=40 * grow,
                          inten=0.95 * fade_in * flick, mask=mask, diag=0)
            else:
                k = gt - bt
                pop = 1 + 1.6 * math.exp(-k / 0.22)
                settle = 1 + 0.08 * math.sin(gt * 1.1)
                draw_star(f, sx, sy, GOLD, core=3.6 * z, halo=40 * z * pop, spike=210 * z * pop,
                          inten=(1.25 + 0.9 * math.exp(-k / 0.35)) * settle)
                ring(f, sx, sy, 16 + 150 * ease_out(ramp(k, 0, 0.7)), 2.4, GOLD, 0.45 * (1 - ramp(k, 0.05, 0.7)))
        # 로고
        la = ease_out(ramp(gt, LOGO_T0, LOGO_T0 + 1.4))
        if la > 0:
            lx, ly = self.logo_pos
            lh, lw = self.logo_rgb.shape[:2]
            dy = int(14 * (1 - la))
            reg = f[ly + dy:ly + dy + lh, lx:lx + lw]
            reg[:] = reg * (1 - self.logo_a * la * 0.9) + self.logo_rgb * la * 1.08
        f = grade_night(f)
        # 회상에서 돌아올 때: 따뜻한 빛이 밤으로 식는다
        wf = 1 - ramp(gt, T_E4[1] - 0.1, T_E4[1] + 1.5)
        if wf > 0:
            R = lerp(160, 2600, wf ** 1.6)
            r2 = (XX - W / 2) ** 2 + (YY - H * 0.30) ** 2
            f = f + (np.exp(-r2 / (R * R)) * 1.6 * wf ** 1.3)[..., None] * np.array([1.0, 0.80, 0.56], np.float32)
            u = smooth(ramp(wf, 0.55, 1.0))
            f = f * (1 - u) + WARM_WHITE * u * 1.02
        return f


# ---- 합성 ------------------------------------------------------------------------------------
class Film:
    def __init__(self):
        self.A = ShotA()
        # scene1.mp4 1916x1080 — 남자 0.36~0.44, 별 0.45~0.78, 워터마크 0.88~ (잘라냄)
        self.B = ShotClipSky("영상/scene1.mp4", x0=300, cw=1250, scale=1.08, headroom=30,
                             pan=(760, 640), zoom=(1.0, 1.06), seed=31, star_limit_y=150, t_range=T_B)
        # scene2.mp4 1920x1080 — 남자 0.21, 별 0.33~0.68, 워터마크 0.906 (잘라냄)
        self.C = ShotClipSky("영상/scene2.mp4", x0=110, cw=1540, scale=1.15, headroom=30,
                             pan=(560, 1190), zoom=(1.0, 1.03), seed=41, star_limit_y=110, t_range=T_C,
                             n_stars=1500, star_kw=dict(dens_top=1.3, faint=0.16, bright_pow=9))
        gold_c = ((1290 - 110) * 1.15, self.C.ext + 158 * 1.15)
        self.D = ShotD(self.C, gold_c)
        self.E1 = ShotStill("scene6-1.png", 470, 720, lambda t: (lerp(560, 760, t / 2.4), H / 2 + 20, lerp(1.0, 1.06, t / 2.4)))
        self.E2 = ShotMemClip("영상/Cherry_blossom_petals_drifting.mp4", 3.98, 560, 470,
                              lambda t: (lerp(600, 660, t / 2.2), H / 2, lerp(1.02, 1.08, t / 2.2)),
                              max_i=48)  # 6.08s 에서 남자 클로즈업으로 컷 → 그 전에서 멈춤
        self.E3 = ShotMemClip("영상/Fireworks_blooming_over_the_ni.mp4", 5.8, 400, 480,
                              lambda t: (640, lerp(H / 2 + 60, H / 2 - 20, t / 2.4), lerp(1.08, 1.0, t / 2.4)))
        self.E4 = ShotStill("scene6-4.png", 0, 1536,
                            lambda t: (lerp(640, 2020, ease_io(t / 2.9)), H / 2, lerp(1.0, 1.05, t / 2.9)))
        self.F = ShotF()

    def frame(self, i):
        t = i / FPS
        layers = []

        def add(shot, rng, fn, fade_in=0.0, fade_out=0.0):
            t0, t1 = rng
            if t0 <= t < t1 or (i == NF - 1 and t1 >= DUR):
                w = 1.0
                if fade_in > 0:
                    w = min(w, ramp(t, t0, t0 + fade_in))
                if fade_out > 0:
                    w = min(w, 1 - ramp(t, t1 - fade_out, t1))
                layers.append((w, lambda: fn(t - t0)))

        add(self.A, T_A, self.A.render, fade_out=0.4)
        add(self.B, T_B, self.B.render, fade_in=0.4, fade_out=0.24)
        add(self.C, T_C, self.C.render, fade_in=0.24)
        add(self.D, T_D, self.D.render, fade_out=0.3)
        for shot, rng in ((self.E1, T_E1), (self.E2, T_E2), (self.E3, T_E3), (self.E4, T_E4)):
            add(shot, rng, shot.render, fade_in=0.2 if shot is not self.E1 else 0.3, fade_out=0.2)
        add(self.F, T_F, lambda tl: self.F.render(tl, t), fade_in=0.25)

        # 페이드 가중치 정규화: 겹치는 두 샷은 크로스페이드
        out = np.zeros((H, W, 3), np.float32)
        tot = 0.0
        for w, fn in layers:
            if w <= 0.001:
                continue
            out += fn() * w
            tot += w
        if tot > 0:
            out /= max(tot, 1e-6)

        # 회상 사이 빛샘 플래시 · E4 끝 노을 번짐
        flash = 0.0
        for tc in (T_E2[0] + 0.1, T_E3[0] + 0.1, T_E4[0] + 0.1):
            flash += 0.22 * math.exp(-((t - tc) ** 2) / 0.006)
        if t < T_E4[1]:
            flash += 0.9 * ease_in(ramp(t, T_E4[1] - 0.9, T_E4[1] - 0.1))
        if flash > 0:
            out = out + (WARM_WHITE - out * 0.4) * min(flash, 1.0)
        fo = ramp(t, *FADE_OUT)
        out *= (1 - ease_io(fo))
        return finish(out, i)


# ---- 오디오 ----------------------------------------------------------------------------------
def decode(path, ss=0.0, dur=None):
    args = ["ffmpeg", "-v", "error", "-ss", str(ss), "-i", path]
    if dur:
        args += ["-t", str(dur)]
    args += ["-ac", "2", "-ar", str(SR), "-f", "f32le", "-"]
    raw = subprocess.run(args, capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).reshape(-1, 2).copy()


def place(bus, x, t, gain):
    i = int(t * SR)
    n = min(len(x), len(bus) - i)
    if n > 0:
        bus[i:i + n] += x[:n] * gain


def env(n, pts):
    tt = np.arange(n) / SR
    xs, ys = zip(*pts)
    return np.interp(tt, xs, ys).astype(np.float32)[:, None]


def synth_shimmer(dur, f0, f1, seed=5):
    """위로 번지는 쉬머(금빛 별로 다가갈 때 · 빛이 솟을 때). 사인 군집 + 밴드 노이즈."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    tt = np.arange(n) / SR
    y = np.zeros(n, np.float32)
    for k in range(9):
        ratio = 2 ** (rng.integers(0, 4) + rng.choice([0, 4, 7, 9]) / 12)
        f = f0 * ratio * (f1 / f0) ** (tt / dur)
        ph = 2 * np.pi * np.cumsum(f) / SR + rng.uniform(0, 6.28)
        trem = 0.6 + 0.4 * np.sin(2 * np.pi * rng.uniform(5, 11) * tt + rng.uniform(0, 6))
        y += (np.sin(ph) * trem / (1 + k * 0.4)).astype(np.float32)
    noise = rng.standard_normal(n).astype(np.float32)
    noise = sosfilt(butter(2, [3000, 9000], "bandpass", fs=SR, output="sos"), noise).astype(np.float32)
    y = y * 0.12 + noise * 0.5
    shape = (tt / dur) ** 1.8
    y *= shape * (1 - np.clip((tt - dur + 0.06) / 0.06, 0, 1))
    st = np.stack([y, np.roll(y, int(0.011 * SR))], 1)
    return st


def synth_bell_chord(freqs, dur=3.5):
    n = int(dur * SR)
    tt = np.arange(n) / SR
    y = np.zeros(n, np.float32)
    for j, f in enumerate(freqs):
        d = tt - j * 0.06
        on = d >= 0
        for ratio, amp in ((1, 1.0), (2.756, 0.25), (5.404, 0.08)):
            y += np.where(on, amp * np.sin(2 * np.pi * f * ratio * d) * np.exp(-np.maximum(d, 0) / (1.6 / ratio ** 0.6)), 0)
    y *= 0.18
    return np.stack([y, np.roll(y, int(0.017 * SR))], 1).astype(np.float32)


def ambience(n, seed=9):
    rng = np.random.default_rng(seed)
    x = rng.standard_normal((n, 2)).astype(np.float32)
    x = sosfilt(butter(2, [70, 520], "bandpass", fs=SR, output="sos"), x, axis=0)
    lfo = 1 + 0.25 * np.sin(2 * np.pi * 0.11 * np.arange(n) / SR)[:, None]
    return (x * lfo * 0.5).astype(np.float32)


def build_audio(path):
    n = int((DUR + 0.2) * SR)
    bus = np.zeros((n, 2), np.float32)
    bgm = decode(os.path.join(RAW, BGM), BGM_OFFSET, DUR + 0.2)
    bgm = bgm[:n]
    bgm *= env(len(bgm), [(0, 0.0), (0.5, 1.0), (FADE_OUT[0] - 0.4, 1.0), (DUR, 0.0)])[:len(bgm)] ** 1.5
    # 새 별이 피는 순간 음악을 비켜 준다(별 탄생음이 묻히지 않게) → 로고까지 살짝 낮게 유지
    duck = env(len(bgm), [(0, 1.0), (BLOOM_T - 0.5, 1.0), (BLOOM_T - 0.1, 0.5), (BLOOM_T + 1.4, 0.55),
                          (BLOOM_T + 2.6, 0.8), (DUR, 0.8)])
    bus[:len(bgm)] += bgm * duck * 0.9
    amb = ambience(n) * env(n, [(0, 0.5), (0.8, 1), (T_E1[0] - 0.4, 1), (T_E1[0] + 0.4, 0), (T_F[0], 0),
                                (T_F[0] + 1.5, 1), (FADE_OUT[0], 1), (DUR, 0)])
    bus += amb * 0.05
    sparks = [decode(os.path.join(RAW, f"sfx_spark_{k}.mp3")) for k in (1, 2, 3)]
    for j, ts in enumerate(SPARK_TIMES):
        place(bus, sparks[j % 3], ts, 0.45)
    # 금빛 별로 다가가며 번지는 쉬머 → 빛이 화면을 채움
    place(bus, synth_shimmer(T_D[1] - T_D[0] + 0.15, 700, 1500, seed=5), T_D[0], 0.55)
    # 새 별: 빛이 솟는 동안 옅은 쉬머, 피는 순간 앱의 별 탄생음(화음이 파일 0.42s 지점)
    place(bus, synth_shimmer(BLOOM_T - RISE_T0, 520, 1100, seed=8), RISE_T0, 0.32)
    place(bus, decode(os.path.join(RAW, "sfx_star_birth.mp3")), BLOOM_T - 0.42, 1.0)
    for k in range(3):
        place(bus, sparks[k], BLOOM_T + 0.35 + 0.18 * k, 0.26)
    place(bus, synth_bell_chord([523.25, 659.25, 783.99, 1046.5]), LOGO_T0 + 0.1, 0.5)
    bus[int(DUR * SR):] = 0
    wavfile.write(path, SR, bus[:int(DUR * SR)])


# ---- 메인 -----------------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--stills", nargs="*", type=float)
    ap.add_argument("--out", default=OUT)
    ap.add_argument("--audio-only", action="store_true", help="영상은 build/cinematic/video.mp4 재사용, 소리만 다시 입힘")
    args = ap.parse_args()
    os.makedirs(WORK, exist_ok=True)
    vtmp = os.path.join(WORK, "video.mp4")
    atmp = os.path.join(WORK, "audio.wav")
    if args.audio_only:
        build_audio(atmp)
        mux(vtmp, atmp, args.out)
        return
    film = Film()
    if args.stills is not None:
        d = os.path.join(WORK, "stills")
        os.makedirs(d, exist_ok=True)
        for s in args.stills:
            i = min(NF - 1, int(round(s * FPS)))
            Image.fromarray(film.frame(i)).save(os.path.join(d, f"t{s:05.2f}.png"))
            print("still", s)
        return
    build_audio(atmp)
    enc = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
                            "-r", str(FPS), "-i", "-", "-c:v", "libx264", "-preset", "slow", "-crf", "16",
                            "-x264-params", "aq-mode=3", "-pix_fmt", "yuv420p", vtmp], stdin=subprocess.PIPE)
    for i in range(NF):
        enc.stdin.write(film.frame(i).tobytes())
        if i % 48 == 0:
            print(f"frame {i}/{NF}", flush=True)
    enc.stdin.close()
    enc.wait()
    mux(vtmp, atmp, args.out)


def mux(vtmp, atmp, out):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", vtmp, "-i", atmp, "-c:v", "copy",
                    "-af", "loudnorm=I=-14:TP=-1.5:LRA=11", "-ar", "48000", "-c:a", "aac", "-b:a", "192k",
                    "-shortest", "-movflags", "+faststart", out], check=True)
    print("done", out)


if __name__ == "__main__":
    main()
