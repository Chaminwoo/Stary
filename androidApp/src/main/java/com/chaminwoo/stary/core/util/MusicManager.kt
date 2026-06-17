package com.chaminwoo.stary.core.util

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 앱 전역 배경음악 관리자. (특정 화면에 묶이지 않고 모든 스크린에서 재생)
 *
 * - 기본 켜짐(처음 실행 시 자동 재생).
 * - 멈췄다 재생하면 처음이 아니라 **마지막 위치**에서 이어 재생(위치 보존).
 * - 끝까지 가면 처음부터 반복(isLooping).
 * - on/off 상태는 SharedPreferences 에 저장.
 */
object MusicManager {
    private const val PREFS = "stary_prefs"
    private const val KEY = "music_enabled"

    private var player: MediaPlayer? = null
    private var positionMs = 0
    private var resId = 0
    private var available = false
    private var initialized = false
    private var appContext: Context? = null

    // 효과음(SFX) — 배경음악과 별개로 겹쳐 1회 재생되는 플레이어
    private var sfxPlayer: MediaPlayer? = null
    private var windResId = 0

    /** Compose 에서 관찰 가능한 on/off 상태. */
    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val ctx = context.applicationContext
        appContext = ctx
        enabled = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true) // 기본 켜짐
        resId = ctx.resources.getIdentifier("ambient_music", "raw", ctx.packageName)
        available = resId != 0
        windResId = ctx.resources.getIdentifier("wind", "raw", ctx.packageName)
    }

    /**
     * 바람 효과음(`res/raw/wind.mp3`) 1회 재생 — 내 다이어리 정렬 애니메이션 시작 시 호출.
     * 배경음악과 별개로 겹쳐 재생되며, 음소거(enabled=false) 상태에선 출력하지 않는다.
     */
    fun playWind() {
        if (!enabled) return
        val ctx = appContext ?: return
        if (windResId == 0) {
            windResId = ctx.resources.getIdentifier("wind", "raw", ctx.packageName)
            if (windResId == 0) return
        }
        // 직전 효과음이 남아 있으면 정리(연속 호출 시 중복/누수 방지)
        sfxPlayer?.release()
        sfxPlayer = MediaPlayer.create(ctx, windResId)?.apply {
            setOnCompletionListener { mp ->
                mp.release()
                if (sfxPlayer === mp) sfxPlayer = null
            }
            start()
        }
    }

    /** 토글(FAB) — on/off 저장 후 즉시 반영. */
    fun setActive(value: Boolean) {
        if (enabled == value) return
        enabled = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY, value)?.apply()
        if (value) resume() else pause()
    }

    /** 앱 전면 복귀 or 켜짐 → 마지막 위치에서 재생. */
    fun resume() {
        if (!enabled || !available) return
        val ctx = appContext ?: return
        if (player == null) {
            player = MediaPlayer.create(ctx, resId)?.apply { isLooping = true }
            player?.seekTo(positionMs)
        }
        player?.let { if (!it.isPlaying) it.start() }
    }

    /** 일시정지 — 현재 위치 보존. */
    fun pause() {
        player?.let {
            if (it.isPlaying) {
                positionMs = it.currentPosition
                it.pause()
            }
        }
    }

    /** 앱 종료 — 위치 보존 후 해제. */
    fun release() {
        player?.let {
            positionMs = it.currentPosition
            it.release()
        }
        player = null
        sfxPlayer?.release()
        sfxPlayer = null
    }
}
