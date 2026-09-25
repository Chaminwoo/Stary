"""Stary UI 효과음 후보 합성기 — 광고 톤(몽환적 피아노 + 크리스탈)에 맞춘 절차적 사운드.

출력: out/<id>.wav (44.1kHz mono 16bit). 모든 소리는 순수 합성(외부 샘플 없음 → 라이선스 문제 없음).
"""
import os
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt, fftconvolve

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "out")
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)


def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def hp(x, fc, order=2):
    return sosfilt(butter(order, fc, "highpass", fs=SR, output="sos"), x)


def lp(x, fc, order=2):
    return sosfilt(butter(order, fc, "lowpass", fs=SR, output="sos"), x)


def bp(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, hi], "bandpass", fs=SR, output="sos"), x)


def fade(x, fin=0.002, fout=0.02):
    n_in = max(1, int(SR * fin))
    n_out = max(1, int(SR * fout))
    x = x.copy()
    x[:n_in] *= np.linspace(0, 1, n_in)
    x[-n_out:] *= np.linspace(1, 0, n_out) ** 2
    return x


def reverb(x, t60=1.2, mix=0.22, bright=6000, predelay=0.012):
    """지수 감쇠 노이즈 IR 컨볼루션 — 밤하늘 공간감."""
    n = int(SR * t60)
    tt = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-6.9 * tt / t60)
    ir = lp(ir, bright)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    pd = np.zeros(int(SR * predelay))
    wet = fftconvolve(np.concatenate([pd, x]), ir)[: len(x) + n]
    dry = np.concatenate([x, np.zeros(len(wet) - len(x))])
    return dry * (1 - mix) + wet * mix * 1.6


def bell(f, dur, decay=0.6, partials=((1, 1.0), (2.756, 0.32), (5.404, 0.14), (8.933, 0.06)), attack=0.002):
    """유리/첼레스타 계열 비화성 배음 벨."""
    t = t_axis(dur)
    y = np.zeros_like(t)
    for ratio, amp in partials:
        d = decay / (ratio ** 0.55)
        y += amp * np.sin(2 * np.pi * f * ratio * t + rng.uniform(0, 6.28)) * np.exp(-t / d)
    a = np.clip(t / attack, 0, 1)
    return y * a


def pluck(f, dur, brightness=0.5):
    """Karplus-Strong — 하프/오르골 뜯는 소리."""
    n = int(SR * dur)
    period = int(SR / f)
    buf = rng.uniform(-1, 1, period)
    buf = lp(buf, 1500 + 7000 * brightness, 1)
    out = np.zeros(n)
    for i in range(n):
        out[i] = buf[i % period]
        buf[i % period] = 0.4985 * (buf[i % period] + buf[(i + 1) % period])
    return out


def noise_sweep(dur, f0, f1, q=1.4, shape=None):
    """중심 주파수가 f0→f1 로 움직이는 대역 노이즈(블록 단위 필터) — 휙/쉬머."""
    t = t_axis(dur)
    x = rng.standard_normal(len(t))
    out = np.zeros_like(x)
    block = 512
    for s in range(0, len(x), block):
        p = s / len(x)
        fc = f0 * (f1 / f0) ** p
        lo, hi = fc / q, min(fc * q, SR / 2 - 100)
        seg = x[max(0, s - 2048): s + block]
        y = bp(seg, lo, hi)
        out[s: s + block] = y[-len(x[s: s + block]):]
    if shape is not None:
        out *= shape(t)
    return out


def norm(x, peak=0.89):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def trim_tail(x, thresh_db=-58):
    """리버브 꼬리의 무음 구간을 잘라 파일을 짧게."""
    env = np.abs(x) / (np.max(np.abs(x)) + 1e-9)
    idx = np.where(env > 10 ** (thresh_db / 20))[0]
    end = idx[-1] + int(SR * 0.03) if len(idx) else len(x)
    return x[:min(len(x), end)]


def save(name, x, peak=0.89):
    x = fade(norm(trim_tail(x), peak), fout=0.05)
    wavfile.write(os.path.join(OUT, name + ".wav"), SR, (x * 32767).astype(np.int16))
    print(f"{name}: {len(x)/SR:.2f}s")


def mix_at(total, parts):
    """parts = [(start_sec, signal)] 를 total 길이 버퍼에 합성."""
    out = np.zeros(int(SR * total))
    for st, sig in parts:
        i = int(SR * st)
        j = min(len(out), i + len(sig))
        out[i:j] += sig[: j - i]
    return out


def note(n):
    """MIDI 번호 → Hz."""
    return 440.0 * 2 ** ((n - 69) / 12)


# 광고 BGM(밝은 피아노)과 어울리게 C 장조 펜타토닉(C D E G A) 고음역만 사용.
PENTA_HI = [84, 86, 88, 91, 93, 96, 98, 100]  # C6 ~ E7

# ───────────── A. 메뉴·버튼 탭 ─────────────
def a1_tok():  # "톡" — 부드러운 나무/마림바 노크
    t = t_axis(0.12)
    f = 780 + 420 * np.exp(-t / 0.006)
    ph = 2 * np.pi * np.cumsum(f) / SR
    body = np.sin(ph) * np.exp(-t / 0.022) + 0.25 * np.sin(2.9 * ph) * np.exp(-t / 0.008)
    click = lp(rng.standard_normal(len(t)), 3500) * np.exp(-t / 0.0015) * 0.35
    return reverb(body + click, t60=0.35, mix=0.12)


def a2_ting():  # "팅" — 작은 유리 틱
    return reverb(bell(2350, 0.35, decay=0.07), t60=0.6, mix=0.2)


def a3_pok():  # "뽁" — 물방울
    t = t_axis(0.14)
    f = 480 + 900 * (1 - np.exp(-t / 0.018))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return reverb(np.sin(ph) * np.exp(-t / 0.03) * np.clip(t / 0.003, 0, 1), t60=0.4, mix=0.15)


# ───────────── B. 필터 전환 — 별 순차 등장 ─────────────
def b_twinkle_single(n):
    return bell(note(n), 0.9, decay=0.28, attack=0.004)


def b1_twinkle_seq():  # 별마다 펜타토닉 방울 1음 — 모이면 반짝이는 선율
    order = [88, 93, 91, 96, 86, 98, 93, 100, 91]
    parts = [(i * 0.13, b_twinkle_single(n) * (0.55 + 0.45 * rng.random())) for i, n in enumerate(order)]
    return reverb(mix_at(2.4, parts), t60=1.6, mix=0.3)


def b2_spark_seq():  # 별마다 아주 짧은 "틱" 반짝임
    def tick():
        t = t_axis(0.09)
        s = hp(rng.standard_normal(len(t)), 5500) * np.exp(-t / 0.006) * 0.6
        s += np.sin(2 * np.pi * rng.uniform(5200, 7400) * t) * np.exp(-t / 0.02)
        return s
    parts = [(i * 0.12 + rng.uniform(-0.02, 0.02), tick() * (0.5 + 0.5 * rng.random())) for i in range(10)]
    return reverb(mix_at(1.8, [(max(0, s), p) for s, p in parts]), t60=1.0, mix=0.28)


def b3_shimmer_once():  # 필터 1회에 하나 — 위로 번지는 쉬머
    dur = 1.7
    t = t_axis(dur)
    sh = noise_sweep(dur, 1800, 9000, q=1.25, shape=lambda t: np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.5) * 0.35
    parts = [(0.08 + i * 0.16, bell(note(n), 0.9, decay=0.3) * 0.5) for i, n in enumerate([84, 88, 91, 96, 100])]
    return reverb(sh + mix_at(dur, parts), t60=1.8, mix=0.32)


# ───────────── C. 별자리 연결 ─────────────
def c1_draw_hum():  # "지이잉" — 선이 그어지는 동안의 빛줄기
    dur = 2.0
    t = t_axis(dur)
    env = np.clip(t / 0.25, 0, 1) * np.clip((dur - t) / 0.7, 0, 1)
    f = 1320 + 660 * (t / dur)
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = (np.sin(ph) + 0.5 * np.sin(1.5 * ph) + 0.25 * np.sin(2.01 * ph)) * 0.18
    tone *= 1 + 0.25 * np.sin(2 * np.pi * 7 * t)  # 가벼운 트레몰로
    air = noise_sweep(dur, 2500, 8000, q=1.3) * 0.5
    return reverb((tone + air) * env, t60=1.6, mix=0.3)


def c2_node_chime_seq():  # 선이 별에 닿을 때마다 차임 — 오르는 펜타토닉
    parts = [(i * 0.2, bell(note(n), 1.2, decay=0.45) * 0.8) for i, n in enumerate([84, 86, 88, 91, 93, 96])]
    return reverb(mix_at(2.6, parts), t60=1.8, mix=0.3)


def c3_harp_gliss():  # 하프 글리산도
    notes = [72, 74, 76, 79, 81, 84, 86, 88, 91, 93, 96]
    parts = [(i * 0.085, pluck(note(n), 1.6, 0.55) * (0.7 + 0.03 * i)) for i, n in enumerate(notes)]
    return reverb(mix_at(2.6, parts), t60=1.8, mix=0.28)


# ───────────── E. 업로드 완료 — 별 탄생 ─────────────
def e1_birth_bloom():  # 응축→발광: 스웰 + 벨 화음 블룸
    dur = 2.4
    t = t_axis(dur)
    swell_env = np.clip(t / 0.45, 0, 1) ** 2 * np.exp(-np.clip(t - 0.45, 0, None) / 0.35)
    swell = noise_sweep(dur, 600, 5000, q=1.5) * swell_env * 0.6
    chord = [72, 76, 79, 84, 88]
    parts = [(0.42 + i * 0.015, bell(note(n), 2.0, decay=0.9) * 0.55) for i, n in enumerate(chord)]
    sparkle = [(0.46 + rng.uniform(0, 0.5), bell(note(rng.choice(PENTA_HI) + 12), 0.5, decay=0.12) * 0.25) for _ in range(9)]
    return reverb(swell + mix_at(dur, parts + sparkle), t60=2.2, mix=0.35)


def e2_meteor_land():  # 유성이 내려와 안착
    dur = 2.0
    t = t_axis(dur)
    whoosh = noise_sweep(dur, 7000, 900, q=1.6, shape=lambda t: np.clip(t / 0.5, 0, 1) * np.exp(-np.clip(t - 0.5, 0, None) / 0.12)) * 0.7
    parts = [(0.55, bell(note(n), 1.4, decay=0.6) * 0.5) for n in (79, 84, 88)]
    return reverb(whoosh + mix_at(dur, parts), t60=1.8, mix=0.3)


# ───────────── F. 좋아요 ─────────────
def f1_crystal_two():  # "띵-링" 두 음 크리스탈
    parts = [(0.0, bell(note(88), 0.8, decay=0.25)), (0.075, bell(note(95), 0.9, decay=0.3) * 0.8)]
    return reverb(mix_at(1.0, parts), t60=1.0, mix=0.25)


def f2_pop_sparkle():  # "뽀옹" + 반짝
    t = t_axis(0.2)
    f = 380 + 700 * (1 - np.exp(-t / 0.03))
    ph = 2 * np.pi * np.cumsum(f) / SR
    pop = np.sin(ph) * np.exp(-t / 0.05) * np.clip(t / 0.004, 0, 1)
    parts = [(0.0, pop), (0.05, bell(note(100), 0.5, decay=0.12) * 0.35), (0.09, bell(note(103), 0.5, decay=0.12) * 0.25)]
    return reverb(mix_at(0.8, parts), t60=0.8, mix=0.2)


# ───────────── G. 해금(잠금 풀림) ─────────────
def g1_unlock_chime():  # "찰칵" + 오르는 3음
    t = t_axis(0.05)
    click = bp(rng.standard_normal(len(t)), 1800, 5000) * np.exp(-t / 0.004)
    click2 = bp(rng.standard_normal(len(t)), 1200, 3500) * np.exp(-t / 0.005) * 0.6
    parts = [(0.0, click), (0.045, click2)] + [(0.14 + i * 0.09, bell(note(n), 1.3, decay=0.5) * 0.6) for i, n in enumerate([79, 84, 88])]
    return reverb(mix_at(1.6, parts), t60=1.6, mix=0.3)


def g2_unveil():  # "스르륵" 베일 걷히는 스웰
    dur = 1.6
    sw = noise_sweep(dur, 800, 7000, q=1.4, shape=lambda t: np.sin(np.pi * np.clip(t / 0.9, 0, 1)) ** 2) * 0.6
    parts = [(0.55, bell(note(n), 1.1, decay=0.55) * 0.45) for n in (84, 91)]
    return reverb(sw + mix_at(dur, parts), t60=1.8, mix=0.35)


# ───────────── H. 업적 달성 ─────────────
def h1_magic_arp():  # 마법 아르페지오 + 쉬머 꼬리
    notes = [72, 76, 79, 84, 88, 91, 96]
    parts = [(i * 0.07, bell(note(n), 2.0, decay=0.8) * 0.6) for i, n in enumerate(notes)]
    tail = noise_sweep(2.6, 4000, 9000, q=1.3, shape=lambda t: np.clip((t - 0.35) / 0.3, 0, 1) * np.exp(-np.clip(t - 0.65, 0, None) / 0.6)) * 0.22
    return reverb(mix_at(2.6, parts) + tail, t60=2.2, mix=0.35)


def h2_music_box():  # 오르골 짧은 악구
    phrase = [(0.0, 84), (0.18, 88), (0.36, 91), (0.54, 96), (0.72, 93), (0.9, 96)]
    parts = [(st, (pluck(note(n), 1.5, 0.8) * 0.6 + bell(note(n) * 2, 1.5, decay=0.35) * 0.2)) for st, n in phrase]
    return reverb(mix_at(2.5, parts), t60=1.6, mix=0.28)


# ───────────── I. 메시지·댓글 전송 ─────────────
def i1_swish():  # "슉" 위로 날아가는
    dur = 0.32
    return reverb(noise_sweep(dur, 900, 6500, q=1.5, shape=lambda t: np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 2), t60=0.5, mix=0.18)


def i2_pong():  # "퐁" 부드러운 방울
    t = t_axis(0.3)
    f = note(79) * (1 + 0.35 * np.exp(-t / 0.015))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return reverb(np.sin(ph) * np.exp(-t / 0.07) * np.clip(t / 0.003, 0, 1) + 0.2 * np.sin(2 * ph) * np.exp(-t / 0.03), t60=0.6, mix=0.2)


# ───────────── J. 인앱 알림 배너 ─────────────
def j1_doorbell():  # "띵-동" 두 음(부드럽게)
    parts = [(0.0, bell(note(91), 1.1, decay=0.4)), (0.16, bell(note(84), 1.3, decay=0.5) * 0.85)]
    return reverb(mix_at(1.6, parts), t60=1.4, mix=0.28)


def j2_ping():  # 단일 유리 핑
    return reverb(bell(note(96), 1.2, decay=0.4), t60=1.3, mix=0.28)


# ───────────── K. 드로어(메뉴) 열기 ─────────────
def k1_air():  # "스윽" 공기
    dur = 0.38
    return reverb(noise_sweep(dur, 500, 2600, q=1.6, shape=lambda t: np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.6) * 0.8, t60=0.5, mix=0.15)


if __name__ == "__main__":
    save("A1_tok", a1_tok(), 0.8)
    save("A2_ting", a2_ting(), 0.7)
    save("A3_pok", a3_pok(), 0.8)
    save("B1_twinkle_seq", b1_twinkle_seq())
    save("B1_twinkle_single", reverb(b_twinkle_single(91), t60=1.4, mix=0.3), 0.7)
    save("B2_spark_seq", b2_spark_seq(), 0.75)
    save("B3_shimmer_once", b3_shimmer_once())
    save("C1_draw_hum", c1_draw_hum(), 0.8)
    save("C2_node_chime_seq", c2_node_chime_seq())
    save("C3_harp_gliss", c3_harp_gliss())
    save("E1_birth_bloom", e1_birth_bloom())
    save("E2_meteor_land", e2_meteor_land())
    save("F1_crystal_two", f1_crystal_two(), 0.8)
    save("F2_pop_sparkle", f2_pop_sparkle(), 0.8)
    save("G1_unlock_chime", g1_unlock_chime())
    save("G2_unveil", g2_unveil())
    save("H1_magic_arp", h1_magic_arp())
    save("H2_music_box", h2_music_box())
    save("I1_swish", i1_swish(), 0.7)
    save("I2_pong", i2_pong(), 0.8)
    save("J1_doorbell", j1_doorbell(), 0.8)
    save("J2_ping", j2_ping(), 0.75)
    save("K1_air", k1_air(), 0.6)
