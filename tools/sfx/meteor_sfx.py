"""글로브 유성(별똥별) 효과음 후보(2026-10-15) — 글로브에서 유성이 떨어질 때 1회.

유성은 약 1.1초에 화면을 가로지르고 꼬리에 잔별(파장)을 남긴다(GlobeRenderer drawMeteor).
음높이는 기존 효과음과 같은 C 장조 펜타토닉 고음역(PENTA_HI). 연속 유성(스트릭)일수록 반짝임 음을 올리는 변형도 만든다.

출력: out/meteor_<A|B|C>.wav, out/meteor_A_streak<n>.wav (연속 n번째)
"""
import numpy as np
from synth import *  # noqa

METEOR_DUR = 1.1  # GlobeRenderer meteorDur 기본값과 맞춤


def sparkle_tail(start, span, count, shift=0, level=0.22):
    """꼬리 잔별 — 펜타토닉 고음 방울을 흩뿌린다. shift = 펜타토닉 단계 올림(연속 유성)."""
    parts = []
    for _ in range(count):
        idx = min(len(PENTA_HI) - 1, int(rng.integers(0, 5)) + shift)
        parts.append((start + rng.uniform(0, span), bell(note(PENTA_HI[idx]), 0.6, decay=0.16) * level * rng.uniform(0.6, 1.0)))
    return parts


def meteor_a(shift=0):
    """A. 휘익—반짝: 높은 바람이 내려오며 지나가고, 꼬리에 잔별이 반짝."""
    dur = 2.6
    whoosh = noise_sweep(dur, 7500, 1400, q=1.5,
                         shape=lambda t: np.clip(t / 0.35, 0, 1) ** 1.5 * np.exp(-np.clip(t - 0.35, 0, None) / 0.42)) * 0.55
    return reverb(whoosh + mix_at(dur, sparkle_tail(0.35, 0.9, 9, shift)), t60=1.8, mix=0.32)


def meteor_b(shift=0):
    """B. 유리 활강: 유리 방울이 위에서 아래로 빠르게 미끄러지고, 밑에 옅은 바람."""
    dur = 2.6
    seq = [100, 98, 96, 93, 91, 88, 86, 84]
    parts = [(0.05 + i * 0.085, bell(note(n + shift), 0.9, decay=0.32) * (0.42 - i * 0.025)) for i, n in enumerate(seq)]
    air = noise_sweep(dur, 5000, 1800, q=1.7,
                      shape=lambda t: np.clip(t / 0.25, 0, 1) * np.exp(-np.clip(t - 0.25, 0, None) / 0.5)) * 0.25
    return reverb(air + mix_at(dur, parts), t60=1.6, mix=0.3)


def meteor_c(shift=0):
    """C. 별가루 쉬머: 은은하게 부풀었다 사라지는 반짝임 — 가장 조용하고 몽환적."""
    dur = 3.0
    shimmer = noise_sweep(dur, 3000, 8000, q=2.4,
                          shape=lambda t: np.sin(np.pi * np.clip(t / 1.4, 0, 1)) ** 2) * 0.35
    return reverb(shimmer + mix_at(dur, sparkle_tail(0.15, 1.3, 16, shift, level=0.14)), t60=2.4, mix=0.4)


if __name__ == "__main__":
    save("meteor_A", meteor_a(), 0.8)
    save("meteor_B", meteor_b(), 0.8)
    save("meteor_C", meteor_c(), 0.75)
    # 연속 유성 — n번째일수록 잔별이 펜타토닉으로 한 단계씩 높아진다(7연속 = 가장 높음).
    for n in (1, 3, 5, 7):
        save(f"meteor_A_streak{n}", meteor_a(shift=min(3, (n - 1) // 2)), 0.8)


def export_app():
    """사용자 선택(2026-10-15): C 별가루 쉬머 + 연속일수록 음 올리기 → app/sfx_meteor_1~4.mp3.
    1 = 연속 1~2번째, 2 = 3~4번째, 3 = 5~6번째, 4 = 7번째 이상(앱 MusicManager.playMeteor 가 고른다)."""
    import os, subprocess
    app = os.path.join(os.path.dirname(__file__), "app")
    os.makedirs(app, exist_ok=True)
    for i in range(4):
        name = f"sfx_meteor_{i + 1}"
        save(name, meteor_c(shift=i), 0.75)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", os.path.join(OUT, name + ".wav"), "-ac", "1",
                        "-c:a", "libmp3lame", "-b:a", "128k", os.path.join(app, name + ".mp3")], check=True)
