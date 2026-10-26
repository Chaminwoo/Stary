#!/usr/bin/env python3
"""스크린샷 점검용 — 크기 · 밝기 · ASCII 축소판.

CI 로그/아티팩트는 내려받으려면 로그인이 필요하다. 대신 이 출력을 GitHub 주석(annotation)으로 남기면 공개 API 로 읽을 수 있어서
"검정 화면인지 / 별이 보이는지 / 버튼이 겹치지 않았는지" 를 PNG 를 열지 않고도 대략 가늠할 수 있다.

  shot_stats.py brief <png>            → "2007x2853 mean=12.3 bright=3.10%"
  shot_stats.py ascii <png> [cols]     → 밝기를 글자로 옮긴 축소판(기본 60열)
  shot_stats.py thumb <png> [폭] [최대바이트] → JPEG 축소판을 base64 한 줄로(주석에 실어 공개 API 로 복원해 볼 수 있다)
  shot_stats.py fit <in> <out> <W> <H>  → 가로세로를 덮도록 확대한 뒤 가운데를 W×H 로 잘라 저장(비율이 조금 다를 때)
"""
import struct
import sys

RAMP = " .:-=+*#%@"


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", head[16:24])


def open_image(path):
    try:
        from PIL import Image  # type: ignore
    except Exception:
        return None
    try:
        return Image.open(path).convert("L")
    except Exception:
        return None


def brief(path):
    size = png_size(path)
    img = open_image(path)
    if img is None:
        import os
        w, h = size if size else (0, 0)
        return f"{w}x{h} mean=? bright=? bytes={os.path.getsize(path)}"
    w, h = img.size
    small = img.resize((max(1, w // 8), max(1, h // 8)))
    px = list(small.getdata())
    mean = sum(px) / len(px)
    bright = 100.0 * sum(1 for v in px if v > 32) / len(px)
    return f"{w}x{h} mean={mean:.1f} bright={bright:.2f}%"


def ascii_art(path, cols=60):
    img = open_image(path)
    if img is None:
        return "(Pillow 없음 — 축소판 생략)"
    w, h = img.size
    rows = max(1, int(cols * h / w * 0.5))   # 글자 칸은 세로가 가로의 약 2배
    small = img.resize((cols, rows))
    out = []
    for y in range(rows):
        line = []
        for x in range(cols):
            v = small.getpixel((x, y))
            line.append(RAMP[min(len(RAMP) - 1, v * len(RAMP) // 256)])
        out.append("".join(line).rstrip())
    return "\n".join(out)


def thumb_b64(path, maxw=300, budget=9000):
    """JPEG 축소판 base64 — 용량(budget 바이트)에 맞을 때까지 화질을 낮춘다."""
    import base64
    import io
    try:
        from PIL import Image  # type: ignore
    except Exception:
        return "(Pillow 없음)"
    img = Image.open(path).convert("RGB")
    w, h = img.size
    img = img.resize((maxw, max(1, int(h * maxw / w))))
    data = b""
    for q in (60, 50, 40, 32, 25, 18):
        buf = io.BytesIO()
        img.save(buf, "JPEG", quality=q, optimize=True)
        data = buf.getvalue()
        if len(data) <= budget:
            break
    return base64.b64encode(data).decode()


def fit(src, dst, w, h):
    """cover 확대 + 중앙 자르기 — 왜곡 없이 정확히 w×h 로 만든다."""
    from PIL import Image  # type: ignore
    img = Image.open(src).convert("RGB")
    sw, sh = img.size
    k = max(w / sw, h / sh)
    nw, nh = max(w, round(sw * k)), max(h, round(sh * k))
    img = img.resize((nw, nh), Image.LANCZOS)
    x, y = (nw - w) // 2, (nh - h) // 2
    img.crop((x, y, x + w, y + h)).save(dst, "PNG")


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    cmd, path = argv[1], argv[2]
    if cmd == "fit":
        fit(path, argv[3], int(argv[4]), int(argv[5]))
    elif cmd == "brief":
        print(brief(path))
    elif cmd == "thumb":
        print(thumb_b64(path, int(argv[3]) if len(argv) > 3 else 300, int(argv[4]) if len(argv) > 4 else 9000))
    elif cmd == "ascii":
        print(ascii_art(path, int(argv[3]) if len(argv) > 3 else 60))
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
