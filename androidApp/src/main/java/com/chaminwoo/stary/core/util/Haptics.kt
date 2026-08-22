package com.chaminwoo.stary.core.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 앱 전역 햅틱(진동) — "보이는 연출"에 손끝 피드백을 붙이는 단일 창구.
 *
 * 설계 의도: 별 탭·열람 파장·좋아요·다이얼 눈금·업적 달성처럼 **이미 시각/청각 연출이 있는 지점에만**
 * 짧게 준다. 아무 버튼에나 넣으면 진동이 배경소음이 되어 오히려 싸구려로 느껴진다.
 *
 * - [MusicManager] 와 같은 전역 싱글턴 패턴(어디서든 `Haptics.tick()`).
 * - [AppSettings.hapticsEnabled] 가 꺼져 있으면 전부 무음 처리(설정 > 사운드 토글).
 * - 진동 모터가 없는 기기(`hasVibrator=false`)면 조용히 무시된다.
 */
object Haptics {

    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        if (vibrator != null) return
        val ctx = context.applicationContext
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }?.takeIf { it.hasVibrator() }
    }

    /** 눈금/스크롤 스냅 — 가장 약한 한 점(다이얼, 휠 피커). */
    fun tick() = oneShot(durationMs = 8, amplitude = 60)

    /** 가벼운 확인 — 토글/칩 선택. */
    fun light() = oneShot(durationMs = 12, amplitude = 100)

    /** 보통 — 좋아요, 친구 수락처럼 "일이 일어난" 순간. */
    fun medium() = oneShot(durationMs = 18, amplitude = 160)

    /** 묵직한 한 방 — 별 열람 파장이 터지는 순간. */
    fun heavy() = oneShot(durationMs = 26, amplitude = 235)

    /**
     * 보상 패턴 — 업적 달성/별 탄생처럼 축하하는 순간.
     * 짧게 두 번 튕긴 뒤 여운(약→강→약)을 남긴다.
     */
    fun celebrate() {
        val v = enabledVibrator() ?: return
        val timings = longArrayOf(0, 14, 60, 22, 40, 34)
        val amplitudes = intArrayOf(0, 120, 0, 190, 0, 90)
        runCatching { v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1)) }
    }

    /**
     * 파장(warp) 전용 — 묵직한 한 방 뒤 파문이 번지듯 잦아든다.
     * 별을 열 때 화면 파장과 길이를 맞춘다.
     */
    fun warp() {
        val v = enabledVibrator() ?: return
        val timings = longArrayOf(0, 24, 70, 16, 90, 12)
        val amplitudes = intArrayOf(0, 220, 0, 130, 0, 70)
        runCatching { v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1)) }
    }

    private fun oneShot(durationMs: Long, amplitude: Int) {
        val v = enabledVibrator() ?: return
        runCatching { v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude)) }
    }

    private fun enabledVibrator(): Vibrator? =
        if (AppSettings.hapticsEnabled) vibrator else null
}
