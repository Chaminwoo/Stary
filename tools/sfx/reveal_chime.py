"""필터 전환 "빛의 고리" 차임(2026-10-14) — 링이 별에 닿는 순간 울리는 소리.

레퍼런스(references/filtering.mp4)의 소리를 분석해 만든다: 빛의 고리가 행성에 닿을 때마다 울리는 건
기음 1.0~1.9kHz + 비화성 배음(×1.8, ×3.2, 가끔 ×2.4)의 부드러운 유리 벨이고, 닿는 별마다 음높이가 다르다.
앱 BGM 과 어울리게 음높이는 기존 효과음과 같은 C 장조 펜타토닉(C6 D6 E6 G6 A6)을 쓴다.

출력:
  app/sfx_chime_1~5.mp3      — 앱에 넣는 최종본(후보 A)
  out/chime_<A|B|C>_demo.wav — 미리듣기용(링이 8개 별을 훑는 상황)
"""
import os, subprocess, sys
import numpy as np
from synth import *  # noqa

APP = os.path.join(os.path.dirname(__file__), "app")
os.makedirs(APP, exist_ok=True)

NOTES = [84, 86, 88, 91, 93]  # C6 D6 E6 G6 A6
# 앱이 별마다 고르는 순서(MusicManager CHIME_ORDER 와 같은 값) — 오르내리는 짧은 선율.
ORDER = [0, 2, 1, 3, 2, 4, 3, 1]
# 미리듣기: 링(0.9s)이 화면을 훑으며 별 8개에 닿는 시각(초).
HITS = [0.00, 0.09, 0.17, 0.30, 0.38, 0.52, 0.66, 0.84]


def chime_a(n):
    """A. 유리 벨(영상풍) — 부드러운 어택, 길게 남는 울림."""
    x = bell(note(n), 1.2, decay=0.45, attack=0.005,
             partials=((1, 1.0), (1.8, 0.42), (2.4, 0.10), (3.2, 0.20)))
    return reverb(x, t60=1.15, mix=0.28)


def chime_b(n):
    """B. 오르골 — 뜯는 소리 + 옅은 벨 배음, A 보다 짧고 또렷하다."""
    x = pluck(note(n), 1.1, 0.75) * 0.7 + bell(note(n) * 2, 1.1, decay=0.28) * 0.22
    return reverb(x, t60=1.1, mix=0.26)


def chime_c(n):
    """C. 크리스탈 팅 — 한 옥타브 위, 아주 짧고 반짝이는 소리."""
    x = bell(note(n + 12), 0.8, decay=0.16, attack=0.002,
             partials=((1, 1.0), (2.756, 0.30), (5.404, 0.12)))
    return reverb(x, t60=0.9, mix=0.28)


def demo(fn):
    parts = [(t, fn(NOTES[ORDER[i % len(ORDER)]]) * 0.8) for i, t in enumerate(HITS)]
    longest = max(len(p) for _, p in parts) / SR
    return mix_at(HITS[-1] + longest + 0.1, parts)


def to_mp3(wav, mp3):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-ac", "1", "-c:a", "libmp3lame", "-b:a", "128k", mp3],
                   check=True)


if __name__ == "__main__":
    pick = (sys.argv[1] if len(sys.argv) > 1 else "A").upper()
    fns = {"A": chime_a, "B": chime_b, "C": chime_c}
    for key, fn in fns.items():
        save(f"chime_{key}_demo", demo(fn), 0.85)
    for i, n in enumerate(NOTES, start=1):
        name = f"sfx_chime_{i}"
        save(name, fns[pick](n), 0.8)
        to_mp3(os.path.join(OUT, name + ".wav"), os.path.join(APP, name + ".mp3"))
    print("app chime =", pick)
