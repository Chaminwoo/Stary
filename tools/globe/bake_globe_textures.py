"""
3D 글로브(우주에서 보기) 텍스처 굽기 — 은하수 성운 하늘(+ 폐기된 수채 파스텔 지구 `--watercolor`) (2026-10-08).

⚠️ 현재 앱 지구는 유리 지구 — 데이터 텍스처는 tools/globe/bake_globe_land.py. 이 스크립트는 이제 하늘(globe_nebula.jpg)만 굽는다(land_mask 는 land 스크립트가 재사용).

왜 굽나: 수채 질감(물감 얼룩·해안 번짐·팔레트)은 빛과 무관한 "지표 색"이라 미리 텍스처로 만들어 두면
런타임 셰이더는 조명(라벤더 그림자)·대기 테두리만 계산하면 된다. 폰에서 매 프레임 노이즈를 돌리지 않고,
Android(GL)와 iOS(Metal)가 **같은 파일**을 쓰므로 모양이 저절로 같다.

입력: tools/globe/earth_blue_marble.jpg (NASA Blue Marble 4096x2048 — 앱 번들에서는 빠짐, 여기 원본 보관)
출력: androidApp/src/main/assets/globe_watercolor.jpg  (4096x2048, 지표 색)
      androidApp/src/main/assets/globe_nebula.jpg      (2048x1024, 배경 하늘 구)
      iOS 는 iosApp/project.yml 에서 같은 파일을 참조한다.

실행: python tools/globe/bake_globe_textures.py   (numpy + Pillow 만 필요)

좌표계: 앱 지구 메쉬(GlobeRenderer.buildEarthMesh)와 같다 — u: 경도 -180→180, v: 북극(0)→남극(1),
        위치 = (cos φ · sin λ, sin φ, cos φ · cos λ). 노이즈를 이 3D 위치로 계산해서 경도 이음매가 없다.
색 공식은 시안 페이지(claude.ai 아티팩트 "Stary 지구본 무드"의 수채 파스텔 / 은하수 성운)와 같다.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "tools/globe/earth_blue_marble.jpg"
OUT_EARTH = ROOT / "androidApp/src/main/assets/globe_watercolor.jpg"
OUT_SKY = ROOT / "androidApp/src/main/assets/globe_nebula.jpg"

# 앱 은하수 띠의 법선 — buildStarfield 의 Rz(28°)·Rx(62°) 로 돌린 (0,1,0). 성운의 분홍 기운이 은하수를 따라간다.
MILKY_WAY_NORMAL = np.array([-0.2204, 0.4145, 0.8829])


# ── 공용 ────────────────────────────────────────────────────────────────────────
def sphere_grid(w, h):
    """텍셀 중심의 구면 위치(앱 메쉬와 같은 매핑) + 위도 사인."""
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h
    lam = np.radians(-180.0 + 360.0 * u)[None, :]
    phi = np.radians(90.0 - 180.0 * v)[:, None]
    x = np.cos(phi) * np.sin(lam)
    y = np.sin(phi) * np.ones_like(lam)
    z = np.cos(phi) * np.cos(lam)
    return np.stack([x, y, z], -1)


def _hash(ix, iy, iz):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791)
    h = (h ^ (h >> 13)) * 1274126177
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF).astype(np.float64) / float(0x1000000)


def vnoise(p):
    i = np.floor(p).astype(np.int64)
    f = p - i
    f = f * f * (3.0 - 2.0 * f)
    x0, y0, z0 = i[..., 0], i[..., 1], i[..., 2]
    fx, fy, fz = f[..., 0], f[..., 1], f[..., 2]

    def c(dx, dy, dz):
        return _hash(x0 + dx, y0 + dy, z0 + dz)

    x00 = c(0, 0, 0) * (1 - fx) + c(1, 0, 0) * fx
    x10 = c(0, 1, 0) * (1 - fx) + c(1, 1, 0) * fx
    x01 = c(0, 0, 1) * (1 - fx) + c(1, 0, 1) * fx
    x11 = c(0, 1, 1) * (1 - fx) + c(1, 1, 1) * fx
    return (x00 * (1 - fy) + x10 * fy) * (1 - fz) + (x01 * (1 - fy) + x11 * fy) * fz


def fbm(p, octaves=5):
    s = np.zeros(p.shape[:-1])
    a = 0.5
    for _ in range(octaves):
        s += a * vnoise(p)
        p = p * 2.03 + np.array([1.7, 9.2, 3.3])
        a *= 0.5
    return s


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def mix(a, b, t):
    return a + (b - a) * t


def blur(arr01, radius):
    img = Image.fromarray((np.clip(arr01, 0, 1) * 255).astype(np.uint8))
    return np.asarray(img.filter(ImageFilter.GaussianBlur(radius))).astype(np.float64) / 255.0


def upscale(arr, w, h):
    img = Image.fromarray(np.asarray(arr, dtype=np.float32))  # 32비트 실수(F) 모드 — 정밀도 손실 없이 키운다
    return np.asarray(img.resize((w, h), Image.BICUBIC)).astype(np.float64)


def save_jpg(rgb01, path, quality):
    rng = np.random.default_rng(3)
    dither = (rng.random(rgb01.shape) - 0.5) / 255.0  # 어두운 그라데이션 띠(banding) 방지
    img = Image.fromarray((np.clip(rgb01 + dither, 0, 1) * 255 + 0.5).astype(np.uint8))
    img.save(path, quality=quality, subsampling=0, optimize=True)
    print(f"{path.relative_to(ROOT)}  {img.size[0]}x{img.size[1]}  {path.stat().st_size // 1024} KB")


# ── 수채 파스텔 지구 ──────────────────────────────────────────────────────────
def land_mask(rgb):
    """바다 = 파랑이 뚜렷이 우세(b − max(r,g) > 17). 극지 얼음은 위도로, 구름 잔해는 형태 연산으로 정리."""
    h, w = rgb.shape[:2]
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    lat = np.linspace(90, -90, h)[:, None] * np.ones((1, w))
    lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
    polar = (lat > 58) | (lat < -60)
    ocean = ((b - np.maximum(r, g)) > 17) | ((lum > 0.55) & ~polar)
    land = ~ocean | (polar & (lum > 0.33))
    land[lat < -78] = True  # 남극 내륙
    k = max(3, (w // 2048) * 2 + 1)
    m = Image.fromarray((land * 255).astype(np.uint8))
    m = m.filter(ImageFilter.MinFilter(k)).filter(ImageFilter.MaxFilter(k))            # 점 잡음 제거
    m = m.filter(ImageFilter.MaxFilter(k * 2 + 1)).filter(ImageFilter.MinFilter(k * 2 + 1))  # 작은 구멍 메우기
    return np.asarray(m).astype(np.float64) / 255.0


def bake_earth():
    src = Image.open(SRC).convert("RGB")
    W, H = src.size  # 4096x2048
    w2, h2 = W // 2, H // 2
    full = np.asarray(src).astype(np.float64)
    half_img = src.resize((w2, h2), Image.LANCZOS)
    half = np.asarray(half_img).astype(np.float64)

    # 마스크는 원본 해상도(해안선 선명도), 나머지 저주파 정보는 절반 해상도에서 만들어 키운다.
    mask = land_mask(full)
    lt_r = blur(mask, 1.6)
    lt_g = blur(mask, 28)
    arid_h = np.clip((half[..., 0] - half[..., 2] + 22) / 34.0, 0, 1)  # 사막(붉은 기) ↑ / 숲(푸른 기) ↓
    arid = upscale(blur(arid_h, 2.5), W, H)
    # 지형 결(2026-10-08 "퀄리티 높게"): 원본 해상도 밝기에서 중앙값으로 가는 번들거림 선을 먼저 지우고,
    # 산맥·골짜기 크기의 띠(blur 2.5 − blur 14)만 남긴다(더 잘면 원본의 업스케일 소용돌이 무늬가 붓자국처럼 살아난다). 밝은 쪽(하이라이트)은 거의 잘라 비닐 느낌이 돌아오지 않게.
    lum_full = np.asarray(Image.fromarray(full.astype(np.uint8)).convert("L").filter(ImageFilter.MedianFilter(5))).astype(np.float64) / 255
    detail = np.clip(blur(lum_full, 2.5) - blur(lum_full, 14), -0.07, 0.012)
    # 업스케일 번들거림(비닐 같은 하이라이트) 제거본에서 밝기만 — 지형의 큰 명암은 남고 왁스 질감은 사라진다.
    soft = np.asarray(half_img.filter(ImageFilter.MedianFilter(7)).filter(ImageFilter.GaussianBlur(0.9))).astype(np.float64) / 255
    lum = upscale(soft @ np.array([0.3, 0.59, 0.11]), W, H)

    p = sphere_grid(w2, h2)
    n_land = upscale(fbm(p * 7.0 + 2.0), W, H)
    n_ocean = upscale(fbm(p * 2.6 + 7.3), W, H)
    sin_lat = np.abs(np.sin(np.radians(np.linspace(90, -90, H))))[:, None] * np.ones((1, W))

    m = smoothstep(0.42, 0.58, lt_r)[..., None]
    a = arid[..., None]
    lc = mix(np.array([0.60, 0.77, 0.72]), np.array([0.79, 0.84, 0.72]), smoothstep(0.18, 0.45, a))  # 세이지 민트 → 연두
    lc = mix(lc, np.array([0.96, 0.83, 0.74]), smoothstep(0.48, 0.78, a))                             # → 피치(사막)
    lc *= (0.86 + 0.45 * (lum - 0.14))[..., None]
    polar = smoothstep(np.sin(np.radians(57)), np.sin(np.radians(66)), sin_lat)
    lat_deg = np.linspace(90, -90, H)[:, None] * np.ones((1, W))
    south = smoothstep(66.0, 72.0, -lat_deg)  # 남극 대륙은 전부 얼음(내륙 밝기가 낮아도)
    ice = np.maximum(np.maximum(polar * smoothstep(0.22, 0.42, lum), smoothstep(0.48, 0.68, lum)), south)[..., None]
    lc = mix(lc, np.array([0.93, 0.92, 1.0]), ice)                                                   # 눈·얼음 = 라벤더 화이트
    lc *= (0.95 + 0.10 * n_land)[..., None]                                                          # 물감 얼룩(지형 결이 보이게 약하게)
    lc *= (1.0 + 1.5 * detail)[..., None]                                                            # 지형 결(산맥·골짜기 음영)

    oc = mix(np.array([0.16, 0.21, 0.46]), np.array([0.34, 0.57, 0.78]), (smoothstep(0.04, 0.5, lt_g) * 0.9)[..., None])
    oc *= (0.88 + 0.26 * n_ocean)[..., None]

    c = mix(oc, lc, m)
    band = (1.0 - smoothstep(0.0, 0.18, np.abs(lt_r - 0.5)))[..., None]
    c *= 1.0 - band * m * 0.25                                                  # 물감이 고이는 해안 테두리
    foam = (np.exp(-((lt_g - 0.40) / 0.08) ** 2))[..., None] * (1.0 - m)
    c = mix(c, np.array([0.96, 0.95, 0.93]), foam * 0.22)                      # 물가의 흰 번짐
    save_jpg(c, OUT_EARTH, 90)


# ── 은하수 성운 하늘 ─────────────────────────────────────────────────────────
def bake_sky():
    W, H = 2048, 1024
    d = sphere_grid(W, H)
    band = np.exp(-((d @ (MILKY_WAY_NORMAL / np.linalg.norm(MILKY_WAY_NORMAL))) / 0.22) ** 2)[..., None]
    n1 = fbm(d * 2.4 + 3.1)[..., None]
    n2 = fbm(d * 3.6 + 11.7)[..., None]
    n3 = fbm(d * 7.0 + 1.3)[..., None]
    c = (np.array([0.020, 0.022, 0.062])
         + np.array([0.50, 0.20, 0.46]) * smoothstep(0.42, 0.85, n1) * (0.25 + band * 0.9) * 0.55   # 분홍 성운
         + np.array([0.10, 0.34, 0.44]) * smoothstep(0.50, 0.90, n2) * 0.28                         # 청록 성운
         + np.array([0.55, 0.50, 0.66]) * band * smoothstep(0.35, 0.80, n3) * 0.22)                 # 은하수 먼지 빛
    save_jpg(c, OUT_SKY, 92)


if __name__ == "__main__":
    # 2026-10-08: 앱 지구는 "유리 지구"(tools/globe/bake_globe_land.py)로 바뀌어 수채 지표(globe_watercolor.jpg)는 더 쓰지 않는다.
    # 기본은 하늘(성운)만 굽는다. 옛 수채 지구가 필요하면 --watercolor (결과 파일은 앱에 연결돼 있지 않다).
    if "--watercolor" in sys.argv:
        bake_earth()
    bake_sky()
