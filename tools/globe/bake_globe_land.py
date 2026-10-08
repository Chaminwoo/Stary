"""
3D 글로브 "유리 지구" 육지 마스크 굽기 (2026-10-08, 레퍼런스 `references/지구본.jpg`).

지구는 어두운 **유리 구슬** — 육지는 얼음 유리처럼 반사하고, 해안선은 유리 모서리처럼 빛나며, 바다는 깊고 어둡다.
빛·질감은 전부 셰이더가 만들고, 이 텍스처는 "어디가 육지인가" 만 알려 준다.

입력: tools/globe/natural_earth/*.geojson — **Natural Earth 1:50m**(퍼블릭 도메인, https://www.naturalearthdata.com/)
        ne_50m_land.geojson     육지 폴리곤(섬 포함)
        ne_50m_lakes.geojson    큰 호수(육지에서 뺀다 — 오대호·빅토리아·카스피해 등)
      (이전 버전은 NASA Blue Marble 사진을 임계값으로 잘라 마스크를 만들어 해안이 뭉툭하고 섬이 사라졌다 —
       2026-10-08 "세계지도를 참고해 나라별로 테두리를 정확하게 다시 따라" 요청으로 실제 지리 데이터로 교체. earth_blue_marble.jpg 는 더 쓰지 않는다.)
      ⚠️ **국경선은 넣지 않는다**(한 번 넣었다가 "국경은 나누지 마" 로 제거 — 나라 윤곽은 해안선으로만).
출력: androidApp/src/main/assets/globe_land.jpg  (4096x2048, R=G=B=육지 0..255 — 해안은 살짝 번져 있다. 셰이더는 R 채널만 읽는다)
      iOS 는 iosApp/project.yml 에서 같은 파일을 참조한다.

작은 호수/구멍 채우기(2026-10-08 "큰 육지 안쪽의 작은 빈 공간(강이나 호수) 중 너무 작은 것은 채워"):
  위도 보정 면적(deg² × cos 위도)이 MIN_VOID_DEG2 미만인 **호수**와 **육지 폴리곤의 구멍**은 그리지 않아(= 육지로 채워져) 큰 육지 안쪽이 지저분하게 뚫려 보이지 않는다.
  큰 호수(오대호·카스피해 같은 것)만 남는다. 기준을 바꾸려면 MIN_VOID_DEG2 만 고치면 된다.

좌표계: 앱 지구 메쉬와 같다 — u: 경도 -180→180, v: 북극(0)→남극(1) (등장방형 도법).
방법: 3배 슈퍼샘플로 폴리곤을 그린 뒤 BOX 필터로 줄여 안티앨리어싱 → 가우시안 1.4px 로 살짝 번지게(셰이더가 문턱값으로 면/해안 빛 띠를 만든다).
실행: python -I tools/globe/bake_globe_land.py   (numpy + Pillow)
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
NE = HERE / "natural_earth"
OUT = ROOT / "androidApp/src/main/assets/globe_land.jpg"
W, H = 4096, 2048
SS = 3  # 슈퍼샘플 배율
MIN_VOID_DEG2 = 1.5  # 이보다 작은 호수/구멍은 채운다(위도 보정 면적, deg²). ≈ 적도 기준 1.9만 km²


def read(name):
    with open(NE / f"{name}.geojson", encoding="utf-8") as f:
        return json.load(f)["features"]


def polygons(features):
    """모든 폴리곤을 [(바깥 고리, [구멍 고리...])] 로 펼친다. GeoJSON 좌표는 [경도, 위도(, 고도)]."""
    out = []
    for ft in features:
        g = ft["geometry"]
        polys = [g["coordinates"]] if g["type"] == "Polygon" else g["coordinates"]
        for rings in polys:
            out.append((rings[0], rings[1:]))
    return out


def ring_area(ring):
    a = 0.0
    for (x0, y0, *_), (x1, y1, *_) in zip(ring, ring[1:]):
        a += x0 * y1 - x1 * y0
    return abs(a) / 2


def void_area(ring):
    """호수/구멍의 위도 보정 면적(deg²) — 고위도일수록 같은 deg² 가 실제로 더 좁다."""
    mean_lat = sum(p[1] for p in ring) / len(ring)
    return ring_area(ring) * math.cos(math.radians(mean_lat))


def to_px(ring, ww, hh):
    return [((p[0] + 180.0) / 360.0 * ww, (90.0 - p[1]) / 180.0 * hh) for p in ring]


def draw_land(ww, hh):
    img = Image.new("L", (ww, hh), 0)
    d = ImageDraw.Draw(img)
    filled_holes = kept_holes = 0
    # 큰 것부터 — 호수 속 섬처럼 작은 폴리곤이 큰 폴리곤의 구멍 위에 다시 그려지게
    for outer, holes in sorted(polygons(read("ne_50m_land")), key=lambda p: -ring_area(p[0])):
        d.polygon(to_px(outer, ww, hh), fill=255)
        for hole in holes:
            if void_area(hole) < MIN_VOID_DEG2:  # 작은 구멍은 그리지 않는다 = 육지로 채워진다
                filled_holes += 1
                continue
            kept_holes += 1
            d.polygon(to_px(hole, ww, hh), fill=0)
    kept_lakes, filled_lakes = [], 0
    for ft in read("ne_50m_lakes"):
        g = ft["geometry"]
        polys = [g["coordinates"]] if g["type"] == "Polygon" else g["coordinates"]
        for rings in polys:
            if void_area(rings[0]) < MIN_VOID_DEG2:  # 작은 호수는 육지로 채운다
                filled_lakes += 1
                continue
            d.polygon(to_px(rings[0], ww, hh), fill=0)
            kept_lakes.append(ft["properties"].get("name") or "?")
    print(f"호수 {len(kept_lakes)}개 유지 / {filled_lakes}개 채움 · 육지 구멍 {kept_holes}개 유지 / {filled_holes}개 채움")
    print("남은 호수:", ", ".join(sorted(set(kept_lakes))))
    return img


def main():
    land = draw_land(W * SS, H * SS).resize((W, H), Image.BOX).filter(ImageFilter.GaussianBlur(1.4))
    land.convert("RGB").save(OUT, quality=92, subsampling=0, optimize=True)
    print(f"{OUT.relative_to(ROOT)}  {W}x{H}  {OUT.stat().st_size // 1024} KB  (land {np.asarray(land).mean() / 255:.3f})")


if __name__ == "__main__":
    main()
