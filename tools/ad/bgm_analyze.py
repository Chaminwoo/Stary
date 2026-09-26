"""BGM 분석 — 템포(BPM)·박자 위상·초당 에너지. 광고 컷을 박자에 맞추고 시작 지점을 고르는 데 쓴다.

    python tools/ad/bgm_analyze.py [bgm 이름 ...]   (기본: 전부)
"""
import subprocess
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "androidApp/src/main/res/raw"
SR = 22050


def load(path: Path) -> np.ndarray:
    out = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
        capture_output=True, check=True).stdout
    return np.frombuffer(out, dtype=np.float32)


def analyze(y: np.ndarray):
    hop, n = 512, 2048
    frames = 1 + (len(y) - n) // hop
    win = np.hanning(n).astype(np.float32)
    idx = np.arange(n)[None, :] + hop * np.arange(frames)[:, None]
    spec = np.abs(np.fft.rfft(y[idx] * win, axis=1))
    logs = np.log1p(spec)
    flux = np.maximum(0, np.diff(logs, axis=0)).sum(axis=1)
    flux = (flux - flux.mean()) / (flux.std() + 1e-9)
    fps = SR / hop
    # 템포 — 온셋 자기상관(60~180 BPM)
    ac = np.correlate(flux, flux, mode="full")[len(flux) - 1:]
    lags = np.arange(len(ac))
    bpm = 60 * fps / np.maximum(lags, 1)
    ok = (bpm >= 70) & (bpm <= 170)
    lag = lags[ok][np.argmax(ac[ok])]
    tempo = 60 * fps / lag
    # 박자 위상 — 한 박 간격 빗(comb)으로 가장 잘 맞는 오프셋
    period = lag
    scores = [flux[o::period].sum() for o in range(period)]
    phase = int(np.argmax(scores)) / fps
    secs = len(y) // SR
    rms = np.sqrt((y[: secs * SR].astype(np.float64) ** 2).reshape(secs, SR).mean(axis=1))
    return tempo, phase, rms


def main():
    names = sys.argv[1:] or [p.stem.replace("bgm_", "") for p in sorted(RAW.glob("bgm_*.mp3"))]
    for name in names:
        y = load(RAW / f"bgm_{name}.mp3")
        tempo, phase, rms = analyze(y)
        db = 20 * np.log10(rms + 1e-9)
        print(f"\n== {name}: {len(y)/SR:.1f}s  tempo≈{tempo:.1f} BPM  첫 박≈{phase:.2f}s")
        print("초당 dB(5초 단위): " + " ".join(f"{int(i*5)}:{db[i*5]:.0f}" for i in range(len(db) // 5)))


if __name__ == "__main__":
    main()
