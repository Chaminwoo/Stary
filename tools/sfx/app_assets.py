"""앱에 넣을 최종 효과음 — 사용자 선택(2026-09-25): B2 톡톡 반짝 / E2 유성 안착 / F2 뽀옹+반짝 / K1 스윽."""
import os, subprocess
import numpy as np
from synth import *  # noqa

APP = os.path.join(os.path.dirname(__file__), "app")
os.makedirs(APP, exist_ok=True)

def tick(freq, seed):
    r = np.random.default_rng(seed)
    t = t_axis(0.09)
    s = hp(r.standard_normal(len(t)), 5500) * np.exp(-t / 0.006) * 0.6
    s += np.sin(2 * np.pi * freq * t) * np.exp(-t / 0.02)
    return reverb(s, t60=0.8, mix=0.26)

def star_birth():
    # E2 를 StarBirth(950ms: 응축 0~0.43s → 발광 0.43s → 착지)에 맞춰 화음이 발광 순간에 오도록 당겼다.
    dur = 1.6
    whoosh = noise_sweep(dur, 7000, 900, q=1.6,
                         shape=lambda t: np.clip(t / 0.38, 0, 1) * np.exp(-np.clip(t - 0.38, 0, None) / 0.10)) * 0.7
    parts = [(0.42, bell(note(n), 1.4, decay=0.6) * 0.5) for n in (79, 84, 88)]
    return reverb(whoosh + mix_at(dur, parts), t60=1.8, mix=0.3)

def export(name, x, peak):
    save(name, x, peak)  # out/<name>.wav
    wav = os.path.join(OUT, name + ".wav")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-ac", "1", "-c:a", "libmp3lame", "-b:a", "128k",
                    os.path.join(APP, name + ".mp3")], check=True)

export("sfx_spark_1", tick(5300, 11), 0.75)
export("sfx_spark_2", tick(6200, 12), 0.75)
export("sfx_spark_3", tick(7300, 13), 0.75)
export("sfx_star_birth", star_birth(), 0.89)
export("sfx_like", f2_pop_sparkle(), 0.8)
export("sfx_drawer", k1_air(), 0.6)
