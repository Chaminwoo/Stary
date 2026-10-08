"""로고 에셋 재생성 — 무손실 마스터(tools/logo/logo_master_1536.png, 1536x1024)에서 앱용 로고를 만든다.

  1) 프리멀티플라이드로 계산(가장자리 색 번짐 방지) → 2배 Lanczos 확대 → 언샤프(글자 가장자리·별 끝 선명화) → 목표 크기로 축소(슈퍼샘플링)
  2) 1152x768 (= 마스터의 0.75, 비율 1.5 동일) — 화면 표시 폭(220dp × 밀도, 최대 ≈ 880px)보다 크되 과하지 않게
  3) Android: res/drawable-nodpi/logo.webp   iOS: Sources/Resources/logo.png   (같은 픽셀)

⚠️ 왜 drawable-nodpi 인가: res/drawable(밀도 구분 없음)에 둔 큰 이미지는 안드로이드가 mdpi(160dpi) 기준으로 보고
   기기 밀도(예: 450dpi)만큼 **확대해서 디코드**한다 — 1536x1024 가 4320x2880(약 50MB)이 된 뒤 다시 618px 로 줄여 그려져
   먼지 같은 잔별과 글자 가장자리가 거칠어지고 메모리도 크게 쓴다. nodpi 는 확대 없이 원본 픽셀 그대로 읽는다.

실행: python -I tools/logo/make_logo.py   (numpy + Pillow + scipy)
"""
import os

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
MASTER = os.path.join(HERE, 'logo_master_1536.png')
ANDROID_OUT = os.path.join(ROOT, 'androidApp', 'src', 'main', 'res', 'drawable-nodpi', 'logo.webp')
IOS_OUT = os.path.join(ROOT, 'iosApp', 'Sources', 'Resources', 'logo.png')

W, H = 1152, 768


def channel_resize(x, size, resample):
    return np.stack([np.asarray(Image.fromarray(x[..., c], 'F').resize(size, resample)) for c in range(4)], axis=2)


def unsharp(x, radius, amount):
    out = []
    for c in range(4):
        blur = gaussian_filter(x[..., c], radius)
        out.append(x[..., c] + amount * (x[..., c] - blur))
    return np.stack(out, axis=2)


def main():
    im = Image.open(MASTER).convert('RGBA')
    arr = np.asarray(im).astype(np.float32)
    a = arr[..., 3:4] / 255.0
    pm = np.concatenate([arr[..., :3] * a, arr[..., 3:4]], axis=2)       # premultiplied

    up = channel_resize(pm, (im.width * 2, im.height * 2), Image.LANCZOS)  # 3072x2048
    up = unsharp(up, radius=2.0, amount=0.55)
    small = channel_resize(up, (W, H), Image.LANCZOS)
    small[..., 3] = np.clip(small[..., 3], 0, 255)
    small[..., :3] = np.clip(small[..., :3], 0, small[..., 3:4])           # 프리멀티플라이드 불변식(rgb ≤ alpha)

    alpha = np.clip(small[..., 3:4] / 255.0, 1e-6, 1)
    straight = np.clip(small[..., :3] / alpha, 0, 255)
    straight = np.where(small[..., 3:4] < 0.5, 0, straight)
    res = Image.fromarray(np.concatenate([straight, small[..., 3:4]], axis=2).round().astype(np.uint8), 'RGBA')

    os.makedirs(os.path.dirname(ANDROID_OUT), exist_ok=True)
    res.save(ANDROID_OUT, 'WEBP', quality=94, alpha_quality=100, method=6)
    res.save(IOS_OUT, 'PNG', optimize=True)
    for p in (ANDROID_OUT, IOS_OUT):
        print(os.path.relpath(p, ROOT), os.path.getsize(p) // 1024, 'KB')


if __name__ == '__main__':
    main()
