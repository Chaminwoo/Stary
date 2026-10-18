"""
웹 링크 미리보기(og:image) 생성기 (2026-10-19).

카카오톡·인스타 DM·X·디스코드에 https://momentdiary-f26c8.web.app/ 링크를 붙였을 때 뜨는 카드 이미지.
키 비주얼·별·지구는 스토어 아트 생성기(tools/store/make_store_art.py)의 렌더러를 그대로 빌려 쓴다(앱과 같은 크리스탈 별).

규격: 1200x630(1.91:1)의 2배 = 2400x1260 JPEG. 카카오톡은 2:1 로 위아래를 조금 자르므로 핵심은 세로 가운데 90% 안에 둔다.

실행: python -I tools/og/make_og.py [이름 ...]      → references/og/og_<이름>.jpg (+ _small 미리보기)
      python -I tools/og/make_og.py final <이름>    → web/og.jpg 로 확정
"""
import importlib.util
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "references" / "og"

_spec = importlib.util.spec_from_file_location("store_art", ROOT / "tools/store/make_store_art.py")
SA = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(SA)

F32 = np.float32
W, H = 2400, 1260
S = H / 1000.0


# ── 이어진 육지 지구(점 격자 대신 면) — SA.render 가 이걸 쓰게 바꿔 끼운다 ──────────────
def render_globe_solid(img, cx, cy, R, rng):
    """유리 지구를 img 에 그린다(안쪽은 덮어쓰고, 대기 빛은 더한다)."""
    h, w = img.shape[:2]
    tex = SA.land_textures()
    th, tw = tex["land"].shape
    pad = int(R * 0.9)
    x0, x1 = max(0, int(cx - R) - pad), min(w, int(cx + R) + pad)
    y0, y1 = max(0, int(cy - R) - pad), min(h, int(cy + R) + pad)
    yy, xx = np.mgrid[y0:y1, x0:x1].astype(F32)
    X, Y = (xx - cx) / R, -(yy - cy) / R
    rr = np.hypot(X, Y)
    inside = rr < 1.0
    Z = np.sqrt(np.clip(1 - rr * rr, 0, 1))

    # 테두리 빛 색 — 오른쪽 아래는 석양빛(분홍·주황), 왼쪽 위는 푸른 빛(앱 아이콘 톤)
    ang = (X * 0.74 - Y * 0.67) / np.maximum(rr, 1e-4)
    tw_ = SA.smooth(-0.55, 0.85, ang)[..., None]
    rim_cool = np.array([0.30, 0.48, 1.00], F32)
    rim_warm = np.array([1.00, 0.46, 0.72], F32)
    rim = rim_cool * (1 - tw_) + rim_warm * tw_
    rim_gain = (0.55 + 0.75 * tw_[..., 0])

    # 화면 → 위경도
    r = math.radians(-SA.ROLL_DEG)
    xv, yv = X * math.cos(r) - Y * math.sin(r), X * math.sin(r) + Y * math.cos(r)
    la0 = math.radians(SA.VIEW_LAT)
    lat = np.arcsin(np.clip(yv * math.cos(la0) + Z * math.sin(la0), -1, 1))
    lon = math.radians(SA.VIEW_LON) + np.arctan2(xv, Z * math.cos(la0) - yv * math.sin(la0))
    latd, lond = np.degrees(lat), (np.degrees(lon) + 180) % 360 - 180

    def sample(name, la_d, lo_d):
        u = (lo_d + 180) / 360 * tw - 0.5
        v = (90 - la_d) / 180 * th - 0.5
        return ndi.map_coordinates(tex[name], [v, u], order=1, mode="grid-wrap")

    land = sample("land", latd, lond)
    line = sample("line", latd, lond)
    glow = sample("glow", latd, lond)

    L = np.array([-0.42, 0.52, 0.74], F32)
    L /= np.linalg.norm(L)
    shade = np.clip(X * L[0] + Y * L[1] + Z * L[2], 0, 1)
    limb = Z ** 0.55

    # 바다 = 깊고 어두운 유리 / 육지 = 살짝 밝은 유리
    col = np.empty(X.shape + (3,), F32)
    depth = (0.35 + 0.65 * shade)[..., None]
    col[:] = np.array([0.026, 0.032, 0.118], F32) * depth + np.array([0.040, 0.016, 0.085], F32) * (1 - Z)[..., None]
    landm = SA.smooth(0.42, 0.58, land)
    col += landm[..., None] * np.array([0.050, 0.058, 0.160], F32) * (0.4 + 0.6 * shade)[..., None] * limb[..., None]

    # 육지 = 점이 아니라 **이어진 면**(OG 용, 2026-10-19) — 빛을 받는 쪽이 밝은 남보라 유리 + 해안 안쪽으로 은은한 번짐
    fill = (0.30 + 0.70 * shade) * limb
    col += (landm * fill)[..., None] * np.array([0.085, 0.100, 0.270], F32)
    col += (landm * glow * fill * 0.55)[..., None] * np.array([0.22, 0.30, 0.80], F32)
    # 해안선 — 유리 모서리처럼 빛나는 선 + 번짐
    col += (line * (0.25 + 0.75 * shade) * limb * 0.80)[..., None] * np.array([0.74, 0.90, 1.00], F32)
    col += (glow * limb * 0.38)[..., None] * np.array([0.34, 0.46, 1.00], F32)

    # 유리 광택(왼쪽 위 넓은 반사) + 프레넬 테두리 빛
    sheen = np.exp(-((X + 0.46) ** 2 + (Y - 0.50) ** 2) / 0.085) * 0.085
    col += sheen[..., None] * np.array([0.62, 0.74, 1.00], F32)
    fres = (1 - Z) ** 3.2
    col += (fres * 0.95 * rim_gain)[..., None] * rim
    col += (((1 - Z) ** 14) * 0.55)[..., None] * (rim * 0.5 + 0.5)

    # 합성 — 원 안쪽은 덮어쓰기(가장자리 안티앨리어싱), 바깥은 대기 빛을 더한다
    alpha = np.clip((1 - rr) * R + 0.5, 0, 1)[..., None]
    sub = img[y0:y1, x0:x1]
    dd = np.clip(rr - 1, 0, None)
    halo = (np.exp(-dd / 0.022) * 0.62 + np.exp(-dd / 0.085) * 0.30 + np.exp(-dd / 0.30) * 0.10) * rim_gain
    sub += (halo * (1 - alpha[..., 0]))[..., None] * rim
    sub[:] = sub * (1 - alpha) + col * alpha


SA.render_globe = render_globe_solid


# ── 공용: 글자·칩·로고 ────────────────────────────────────────────────────────
def text_mask(lines, size, weight, x, y, anchor="la", spacing=1.24, align="left"):
    """여러 줄 글자를 L 마스크(0..1)로. anchor 는 첫 줄 기준."""
    layer = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(layer)
    f = SA.font(size, weight)
    ty = y
    for line in lines:
        if align == "center":
            tw = d.textlength(line, font=f)
            d.text((x - tw / 2, ty), line, font=f, fill=255)
        else:
            d.text((x, ty), line, font=f, fill=255)
        ty += size * spacing
    return np.asarray(layer).astype(F32) / 255, ty


def put_text(img, mask, color=(0.985, 0.975, 1.0), shadow=0.55, glow=0.16, glow_col=(0.70, 0.62, 1.0), alpha=1.0):
    sh = ndi.gaussian_filter(mask, 9 * S)
    img *= (1 - shadow * np.clip(sh * 1.6, 0, 1))[..., None]
    if glow:
        g = ndi.gaussian_filter(mask, 16 * S)
        img += (g * glow)[..., None] * np.asarray(glow_col, F32)
    a = (mask * alpha)[..., None]
    img[:] = img * (1 - a) + a * np.asarray(color, F32)


def rounded_mask(x0, y0, x1, y1, r, angle=0.0):
    """둥근 사각형 마스크(0..1, 가장자리 부드럽게). angle 은 가운데 기준 회전(도)."""
    k = 3
    m = Image.new("L", (W * k // 2, H * k // 2), 0)  # 1.5배로 그려 줄이기(계단 방지)
    sc = k / 2
    ImageDraw.Draw(m).rounded_rectangle((x0 * sc, y0 * sc, x1 * sc, y1 * sc), radius=r * sc, fill=255)
    if angle:
        m = m.rotate(angle, resample=Image.BICUBIC, center=((x0 + x1) / 2 * sc, (y0 + y1) / 2 * sc))
    m = m.resize((W, H), Image.LANCZOS)
    return np.asarray(m).astype(F32) / 255


def glass(img, mask, tint=(0.80, 0.78, 1.0), fill=0.10, blur=26, border=0.38):
    """서리 유리 — 뒤를 흐리고 옅게 밝힌 뒤 가는 테두리."""
    blurred = np.stack([ndi.gaussian_filter(img[..., c], blur * S) for c in range(3)], -1)
    m = mask[..., None]
    img[:] = img * (1 - m) + (blurred * 0.80 + np.asarray(tint, F32) * fill) * m
    edge = np.clip(mask - ndi.grey_erosion(mask, size=(int(4 * S) | 1, int(4 * S) | 1)), 0, 1)
    img += (ndi.gaussian_filter(edge, 0.8 * S) * border)[..., None] * np.array([0.92, 0.92, 1.0], F32)


def lock_icon(img, cx, cy, s, color=(1.0, 0.92, 0.70), gain=1.0):
    """자물쇠 아이콘(몸통 + 고리) — 이모지 대신 직접 그린다."""
    m = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(m)
    bw, bh = s * 0.78, s * 0.58
    d.rounded_rectangle((cx - bw / 2, cy - bh * 0.10, cx + bw / 2, cy + bh * 0.90), radius=s * 0.12, fill=255)
    sw = max(2, int(s * 0.13))
    d.arc((cx - bw * 0.33, cy - bh * 0.95, cx + bw * 0.33, cy + bh * 0.25), 180, 360, fill=255, width=sw)
    d.line((cx - bw * 0.33 + sw / 2 - 1, cy - bh * 0.35, cx - bw * 0.33 + sw / 2 - 1, cy - bh * 0.05), fill=255, width=sw)
    d.line((cx + bw * 0.33 - sw / 2, cy - bh * 0.35, cx + bw * 0.33 - sw / 2, cy - bh * 0.05), fill=255, width=sw)
    d.ellipse((cx - s * 0.07, cy + bh * 0.25, cx + s * 0.07, cy + bh * 0.39), fill=0)
    a = ndi.gaussian_filter(np.asarray(m).astype(F32) / 255, 0.6 * S)
    img[:] = img * (1 - a[..., None] * gain) + (a * gain)[..., None] * np.asarray(color, F32)


def pin_icon(img, cx, cy, s, color=(0.43, 0.91, 0.72)):
    m = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(m)
    r = s * 0.36
    d.ellipse((cx - r, cy - s * 0.42 - r, cx + r, cy - s * 0.42 + r), fill=255)
    d.polygon([(cx - r * 0.86, cy - s * 0.30), (cx + r * 0.86, cy - s * 0.30), (cx, cy + s * 0.32)], fill=255)
    d.ellipse((cx - r * 0.42, cy - s * 0.42 - r * 0.42, cx + r * 0.42, cy - s * 0.42 + r * 0.42), fill=0)
    a = ndi.gaussian_filter(np.asarray(m).astype(F32) / 255, 0.6 * S)
    img[:] = img * (1 - a[..., None]) + a[..., None] * np.asarray(color, F32)


def chip(img, cx, cy, label, size=34, icon=None, icon_col=(1.0, 0.92, 0.70), weight=600, text_col=(0.97, 0.95, 1.0)):
    """가운데 정렬 유리 알약 + 아이콘 + 글자."""
    f = SA.font(size * S, weight)
    tw = ImageDraw.Draw(Image.new("L", (1, 1))).textlength(label, font=f)
    ic = size * S * 1.05 if icon else 0
    gap = size * S * 0.45 if icon else 0
    pw = tw + ic + gap + size * S * 1.5
    ph = size * S * 2.05
    x0, y0 = cx - pw / 2, cy - ph / 2
    glass(img, rounded_mask(x0, y0, x0 + pw, y0 + ph, ph / 2 - 2), fill=0.12, blur=18, border=0.45)
    tx = x0 + size * S * 0.75
    if icon == "lock":
        lock_icon(img, tx + ic / 2, cy - ic * 0.12, ic, icon_col)
    elif icon == "pin":
        pin_icon(img, tx + ic / 2, cy + ic * 0.05, ic, icon_col)
    m, _ = text_mask([label], size * S, weight, tx + ic + gap, cy - size * S * 0.62)
    put_text(img, m, color=text_col, shadow=0.0, glow=0.0)


def logo(img, x, y, width, center=False):
    """금빛 손글씨 로고 — 스토어 아트와 같은 합성(검정 바탕 원본을 스크린)."""
    lg = Image.open(SA.LOGO).convert("RGB").crop((70, 80, 1480, 870))
    lh = int(width * lg.height / lg.width)
    lg = np.asarray(lg.resize((int(width), lh), Image.LANCZOS)).astype(F32) / 255
    soft = np.stack([ndi.gaussian_filter(lg[..., c], width * 0.012) for c in range(3)], -1)
    mix = SA.smooth(0.30, 0.62, lg.max(axis=2))[..., None]
    lg = np.clip((soft * (1 - mix) + lg * mix - 0.035) / 0.965, 0, 1)
    ly, lx = np.mgrid[0:lg.shape[0], 0:lg.shape[1]].astype(F32)
    edge = np.minimum(np.minimum(lx, lg.shape[1] - 1 - lx) / (width * 0.06),
                      np.minimum(ly, lg.shape[0] - 1 - ly) / (width * 0.06))
    lg *= SA.smooth(0.0, 1.0, edge)[..., None]
    if center:
        x -= width / 2
    xi, yi = int(x), int(y)
    sub = img[yi:yi + lh, xi:xi + int(width)]
    sub[:] = 1 - (1 - np.clip(sub, 0, 1)) * (1 - lg[:sub.shape[0], :sub.shape[1]])
    return lh


def hero_star(img, cx, cy, size, typ=1, col=1, ground=None, ring=False):
    """주인공 크리스탈 별 — 번짐 + 빛줄기 + (바닥까지 빛기둥) + 본체 + (잠금 고리)."""
    cols = SA.PALETTE[col]
    c0, c1 = np.asarray(cols[0], F32) / 255, np.asarray(cols[-1], F32) / 255
    cm = (c0 + c1) / 2
    if ground:
        gx, gy = ground
        SA.add_blob(img, gx, gy, size * 0.55, cm, 0.40)
        SA.add_blob(img, gx, gy, size * 0.12, (1, 1, 1), 0.55)
        n = 90
        for k in range(n):
            t = k / (n - 1)
            SA.add_blob(img, gx + (cx - gx) * t, gy + (cy - gy) * t, max(1.0, size * 0.020), cm * 0.55 + 0.45, 0.035 + 0.10 * t)
    SA.add_blob(img, cx, cy, size * 1.6, cm, 0.10)
    SA.add_blob(img, cx, cy, size * 0.70, cm, 0.22)
    SA.add_blob(img, cx, cy, size * 0.32, cm * 0.5 + 0.5, 0.30)
    SA.add_spikes(img, cx, cy, size * 1.25, max(1.0, size * 0.012), cm * 0.5 + 0.5, 0.42)
    if ring:
        ring_layer = Image.new("L", (W, H), 0)
        d = ImageDraw.Draw(ring_layer)
        r = size * 0.92
        n = 72
        for k in range(n):
            if k % 2:
                continue
            a0, a1 = 360 * k / n, 360 * (k + 1) / n - 1.2
            d.arc((cx - r, cy - r, cx + r, cy + r), a0, a1, fill=255, width=max(2, int(3.0 * S)))
        ra = np.asarray(ring_layer).astype(F32) / 255
        img += ndi.gaussian_filter(ra, 0.9 * S)[..., None] * np.array([1.0, 0.92, 0.72], F32) * 0.62
        img += ndi.gaussian_filter(ra, 7.0 * S)[..., None] * np.array([1.0, 0.80, 0.50], F32) * 0.55
    SA.paste_rgba(img, SA.crystal_star(typ, cols, int(size)), cx, cy)
    SA.add_blob(img, cx - size * 0.03, cy - size * 0.05, size * 0.05, (1, 1, 1), 0.40)


def scrim(img, cx, cy, rx, ry, k=0.55):
    yy, xx = np.mgrid[0:H, 0:W].astype(F32)
    s = np.exp(-(((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2))
    img *= (1 - k * s)[..., None]


def to_image(img, seed=3):
    rng = np.random.default_rng(seed)
    img = img + (rng.random((H, W, 1)).astype(F32) - 0.5) / 255 * 1.6
    return Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")


def bg(globe, band, rings=(), seed=7, star_scale=1.0):
    return np.asarray(SA.render(W, H, globe, band, None, seed=seed, star_scale=star_scale, rings=rings)).astype(F32) / 255


# ── 시안 ─────────────────────────────────────────────────────────────────────
def concept_globe():
    """A. 유리 지구 — 별이 뜬 지구(오른쪽) + 로고·문구(왼쪽)."""
    img = bg(globe=(0.765, 0.640, 0.585), band=(0.20, 0.50, 60, 0.22, 1.0),
             rings=[(1.45, -72, 16, 0.0, (205, 338)), (1.78, -78, -12, 0.0, (168,))])
    scrim(img, W * 0.22, H * 0.52, W * 0.34, H * 0.55, 0.58)
    lh = logo(img, W * 0.055, H * 0.13, W * 0.25)
    m, ty = text_mask(["누군가 이곳에", "별을 남겼어요"], 92 * S, 700, W * 0.062, H * 0.13 + lh * 0.95)
    put_text(img, m)
    m2, _ = text_mask(["그 장소에 가야만 열리는 이야기"], 38 * S, 400, W * 0.064, ty + 22 * S)
    put_text(img, m2, color=(0.84, 0.85, 1.0), glow=0, alpha=0.9)
    chip(img, W * 0.064 + 205 * S, ty + 150 * S, "지금 열어보기", size=30, icon="pin")
    return to_image(img)


def concept_locked():
    """B. 잠긴 별 — 지평선 위에 뜬 금빛 별 하나와 잠금 고리. 호기심(누가? 무엇을?)."""
    img = bg(globe=(0.50, 1.78, 1.12), band=(0.62, 0.10, 104, 0.24, 1.0), seed=11)
    # 아래쪽 행성 가장자리에서 별까지 빛기둥
    scrim(img, W * 0.5, H * 0.16, W * 0.40, H * 0.16, 0.45)
    hero_star(img, W * 0.5, H * 0.47, 300 * S, typ=1, col=1, ground=(W * 0.5, H * 0.67), ring=True)
    m, ty = text_mask(["누군가 여기에 별을 남겼어요"], 76 * S, 700, W * 0.5, H * 0.075, align="center")
    put_text(img, m)
    chip(img, W * 0.5, H * 0.875, "이 별은 그 장소에 가야 열려요", size=33, icon="lock")
    # 로고 — 오른쪽 아래 작게
    logo(img, W * 0.885, H * 0.80, W * 0.11, center=True)
    return to_image(img)


def concept_card():
    """C. 다이어리 카드 — 흐릿하게 비치는 누군가의 기록 + 거리 칩. '열어 보고 싶다'."""
    img = bg(globe=(0.96, 1.30, 0.80), band=(0.30, 0.40, 64, 0.24, 1.0), seed=19,
             rings=[(1.45, -72, 16, 0.0, (205,))])
    scrim(img, W * 0.24, H * 0.50, W * 0.34, H * 0.55, 0.55)
    # 카드(오른쪽, 살짝 기울임)
    cx, cy, cw, chh, ang = W * 0.715, H * 0.455, 640 * S, 740 * S, -5.0
    x0, y0, x1, y1 = cx - cw / 2, cy - chh / 2, cx + cw / 2, cy + chh / 2
    mask = rounded_mask(x0, y0, x1, y1, 46 * S, ang)
    glass(img, mask, fill=0.13, blur=30, border=0.55)
    # 카드 안: 흐린 사진 + 흐린 글줄(회전 레이어로 그린다)
    inner = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(inner)
    px0, py0, px1, py1 = x0 + 40 * S, y0 + 40 * S, x1 - 40 * S, y0 + 410 * S
    # 사진 자리 — 노을 진 바다 같은 그라데이션 + 빛망울
    ph = np.zeros((int(py1 - py0), int(px1 - px0), 3), F32)
    v = np.linspace(0, 1, ph.shape[0])[:, None]
    top, mid, bot = np.array([0.20, 0.17, 0.48]), np.array([0.98, 0.56, 0.52]), np.array([0.10, 0.12, 0.30])
    t1 = np.clip(v / 0.58, 0, 1)[..., None]
    t2 = np.clip((v - 0.58) / 0.42, 0, 1)[..., None]
    ph[:] = (top * (1 - t1) + mid * t1) * (1 - t2) + bot * t2
    rng = np.random.default_rng(5)
    for _ in range(26):
        bx, by = rng.uniform(0, ph.shape[1]), rng.uniform(ph.shape[0] * 0.55, ph.shape[0])
        r = rng.uniform(10, 34) * S
        c = [(1.0, 0.85, 0.55), (1.0, 0.62, 0.70), (0.80, 0.85, 1.0)][int(rng.integers(0, 3))]
        yy, xx = np.mgrid[0:ph.shape[0], 0:ph.shape[1]].astype(F32)
        ph += (np.exp(-((xx - bx) ** 2 + (yy - by) ** 2) / (2 * r * r)) * 0.55)[..., None] * np.asarray(c, F32)
    ph = np.stack([ndi.gaussian_filter(ph[..., c], 10 * S) for c in range(3)], -1)
    pim = Image.fromarray((np.clip(ph, 0, 1) * 255).astype(np.uint8), "RGB")
    pm = Image.new("L", pim.size, 0)
    ImageDraw.Draw(pm).rounded_rectangle((0, 0, pim.size[0], pim.size[1]), radius=int(28 * S), fill=235)
    pim.putalpha(pm)
    inner.alpha_composite(pim, (int(px0), int(py0)))
    # 글줄(흐린 막대) + 제목 막대
    ly = py1 + 44 * S
    d.rounded_rectangle((px0, ly, px0 + 380 * S, ly + 30 * S), radius=13 * S, fill=(245, 240, 255, 210))
    ly += 66 * S
    for k, wf in enumerate([0.96, 0.88, 0.93, 0.62]):
        d.rounded_rectangle((px0, ly, px0 + (px1 - px0) * wf, ly + 18 * S), radius=7 * S, fill=(220, 215, 255, 120))
        ly += 40 * S
    inner = inner.filter(ImageFilter.GaussianBlur(radius=3.2 * S))
    inner = inner.rotate(ang, resample=Image.BICUBIC, center=(cx, cy))
    ia = np.asarray(inner).astype(F32) / 255
    a = ia[..., 3:4]
    img[:] = img * (1 - a) + ia[..., :3] * a
    # 카드 위 크리스탈 별(핀처럼) + 거리 칩
    hero_star(img, x1 - 34 * S, y0 + 40 * S, 170 * S, typ=1, col=16)
    chip(img, cx, y1 + 72 * S, "1.2km 떨어진 곳에서 열려요", size=30, icon="pin")
    # 왼쪽 문구 — 로고 + 제목 두 줄을 한 묶음으로 세로 가운데에(부제 없음)
    lw = W * 0.22
    lh0 = lw * 790 / 1410                          # logo() 가 쓰는 원본 크롭(1410x790) 비율
    hs = 84 * S
    block = lh0 * 0.95 + hs * 1.24 + hs           # 로고 + 1줄 행간 + 2줄 글자 높이
    top = (H - block) / 2 - 10 * S
    lh = logo(img, W * 0.055, top, lw)
    m, _ = text_mask(["누군가 남긴 이야기,", "보러 갈래요?"], hs, 700, W * 0.062, top + lh * 0.95)
    put_text(img, m)
    return to_image(img)


def concept_logo():
    """D. 금빛 로고 + 별자리 — 지금 카드의 고급판(지구 지평선 · 크리스탈 별 별자리 · 문구)."""
    img = bg(globe=(0.50, 1.86, 1.16), band=(0.50, 0.30, 96, 0.30, 1.1), seed=23)
    scrim(img, W * 0.5, H * 0.38, W * 0.42, H * 0.30, 0.50)
    lh = logo(img, W * 0.5, H * 0.08, W * 0.50, center=True)
    # 로고 둘레 작은 크리스탈 별 별자리
    pts = [(0.17, 0.30, 0, 7, 70), (0.27, 0.16, 1, 4, 54), (0.80, 0.18, 0, 19, 62), (0.86, 0.38, 2, 5, 52),
           (0.12, 0.58, 4, 9, 46), (0.90, 0.62, 0, 16, 48)]
    line = Image.new("L", (W, H), 0)
    dl = ImageDraw.Draw(line)
    for a_, b_ in [(0, 1), (2, 3), (0, 4), (3, 5)]:
        A, B = pts[a_], pts[b_]
        dl.line([(A[0] * W, A[1] * H), (B[0] * W, B[1] * H)], fill=255, width=max(1, int(2 * S)))
    la = np.asarray(line).astype(F32) / 255
    img += ndi.gaussian_filter(la, 0.9 * S)[..., None] * np.array([0.86, 1.00, 0.95], F32) * 0.55
    img += ndi.gaussian_filter(la, 6 * S)[..., None] * np.array([0.43, 0.91, 0.72], F32) * 0.40
    for x, y, t, c, s in pts:
        hero_star(img, x * W, y * H, s * S * 1.6, typ=t, col=c)
    m, ty = text_mask(["그 장소에 가야만 열리는 별"], 64 * S, 700, W * 0.5, H * 0.08 + lh * 0.98, align="center")
    put_text(img, m)
    m2, _ = text_mask(["지도 위 밤하늘에 남기는 위치 다이어리"], 34 * S, 400, W * 0.5, ty + 14 * S, align="center")
    put_text(img, m2, color=(0.84, 0.85, 1.0), glow=0, alpha=0.9)
    return to_image(img)


CONCEPTS = {"globe": concept_globe, "locked": concept_locked, "card": concept_card, "logo": concept_logo}


def main():
    args = sys.argv[1:]
    OUT.mkdir(parents=True, exist_ok=True)
    if args[:1] == ["final"]:
        name = args[1]
        im = CONCEPTS[name]()
        p = ROOT / "web" / "og.jpg"
        im.save(p, quality=90, optimize=True, progressive=True)
        print("saved", p, im.size, p.stat().st_size // 1024, "KB")
        return
    for name, fn in CONCEPTS.items():
        if args and name not in args:
            continue
        im = fn()
        p = OUT / f"og_{name}.jpg"
        im.save(p, quality=90, optimize=True, progressive=True)
        im.resize((1200, 630), Image.LANCZOS).save(OUT / f"og_{name}_small.jpg", quality=88)
        print("saved", p.name, p.stat().st_size // 1024, "KB")


if __name__ == "__main__":
    main()
