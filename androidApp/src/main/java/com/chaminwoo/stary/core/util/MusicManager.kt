package com.chaminwoo.stary.core.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 앱 전역 배경음악 관리자. (특정 화면에 묶이지 않고 모든 스크린에서 재생)
 *
 * - 기본 켜짐(처음 실행 시 자동 재생). 기본 음악은 [MusicCatalog.DEFAULT_ID].
 * - 멈췄다 재생하면 처음이 아니라 **마지막 위치**에서 이어 재생(위치 보존).
 * - 끝까지 가면 처음부터 반복(isLooping).
 * - on/off 상태와 선택한 트랙([selectedTrackId])은 SharedPreferences 에 저장.
 *
 * 음악 탭(다이얼) 흐름:
 *  - 미리듣기는 [playTrack] (처음부터). 화면을 나갈 때 변경했으면 [commitSelectedTrack] 으로 확정,
 *    안 바꿨으면 [playTrack] 으로 원래 트랙을 원래 위치부터 복원한다.
 */
object MusicManager {
    private const val PREFS = "stary_prefs"
    private const val KEY_ENABLED = "music_enabled"
    private const val KEY_TRACK = "music_track"
    private const val KEY_MUSIC_VOL = "music_volume"
    private const val KEY_SFX_VOL = "sfx_volume"

    private var player: MediaPlayer? = null
    private var positionMs = 0
    private var playingId: String? = null     // 현재 player 가 들고 있는 트랙 id
    private var initialized = false
    private var appContext: Context? = null

    // 효과음(SFX) — 짧은 UI 효과음은 SoundPool 로 미리 로드해 즉시·중복 재생(지연/묵음 방지)
    private var soundPool: SoundPool? = null
    private var windResId = 0
    private var windSoundId = 0
    private var windLoaded = false
    // 다이어리 열람 효과음(open_diary.mp3) — 배경음악보다 작게 출력
    private var openResId = 0
    private var openSoundId = 0
    private var openLoaded = false
    private const val OPEN_VOLUME = 0.35f

    // 다이얼 회전 효과음(turning_dial.mp3) — 겹쳐 재생하지 않고, 한 번 끝났을 때
    // 아직 돌리는 중이면 다시 재생한다. (SoundPool 대신 완료 콜백이 필요해 MediaPlayer 사용)
    private var dialPlayer: MediaPlayer? = null
    private var dialResId = 0
    private var dialTurning = false
    private const val DIAL_VOLUME = 0.6f

    /** Compose 에서 관찰 가능한 on/off 상태. */
    var enabled by mutableStateOf(true)
        private set

    /** 영구 선택된(BGM 으로 재생되는) 트랙 id. 음악 탭에서 변경 확정 시 갱신. */
    var selectedTrackId by mutableStateOf(MusicCatalog.DEFAULT_ID)
        private set

    /** 배경음악 볼륨(0..1). 설정 탭에서 조절. */
    var musicVolume by mutableStateOf(1f)
        private set

    /** 효과음(SFX) 볼륨(0..1). 설정 탭에서 조절. 열람/바람/다이얼 효과음에 곱해진다. */
    var sfxVolume by mutableStateOf(1f)
        private set

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_ENABLED, true) // 기본 켜짐
        selectedTrackId = prefs.getString(KEY_TRACK, MusicCatalog.DEFAULT_ID) ?: MusicCatalog.DEFAULT_ID
        musicVolume = prefs.getFloat(KEY_MUSIC_VOL, 1f).coerceIn(0f, 1f)
        sfxVolume = prefs.getFloat(KEY_SFX_VOL, 1f).coerceIn(0f, 1f)
        playingId = selectedTrackId

        // 효과음을 SoundPool 에 미리 로드 (탭 시 지연 없이 재생)
        windResId = ctx.resources.getIdentifier("wind", "raw", ctx.packageName)
        openResId = ctx.resources.getIdentifier("open_diary", "raw", ctx.packageName)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build().apply {
            setOnLoadCompleteListener { _, sampleId, status ->
                if (status != 0) return@setOnLoadCompleteListener
                when (sampleId) {
                    windSoundId -> windLoaded = true
                    openSoundId -> openLoaded = true
                }
            }
        }
        if (windResId != 0) windSoundId = soundPool?.load(ctx, windResId, 1) ?: 0
        if (openResId != 0) openSoundId = soundPool?.load(ctx, openResId, 1) ?: 0
    }

    private fun resIdFor(id: String?): Int {
        val ctx = appContext ?: return 0
        val raw = MusicCatalog.rawName(id) ?: return 0
        return ctx.resources.getIdentifier(raw, "raw", ctx.packageName)
    }

    /**
     * 바람 효과음(`res/raw/wind.mp3`) 1회 재생 — 내 다이어리 정렬 애니메이션 시작 시 호출.
     * 배경음악과 별개로 겹쳐 재생되며, 음소거(enabled=false) 상태에선 출력하지 않는다.
     */
    fun playWind() {
        if (!enabled) return
        val sp = soundPool ?: return
        if (windLoaded && windSoundId != 0) {
            sp.play(windSoundId, sfxVolume, sfxVolume, 1, 0, 1f) // SFX 볼륨, 1회
        } else {
            val ctx = appContext ?: return
            if (windResId == 0) return
            MediaPlayer.create(ctx, windResId)?.apply {
                setVolume(sfxVolume, sfxVolume)
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }

    /**
     * 다이어리 열람 효과음(`res/raw/open_diary.mp3`) 1회 재생 — 상세 화면 진입 시 호출.
     * 배경음악보다 작게([OPEN_VOLUME]) 출력하며, 음소거(enabled=false) 상태에선 출력하지 않는다.
     */
    fun playOpenDiary() {
        if (!enabled) return
        val sp = soundPool ?: return
        val vol = OPEN_VOLUME * sfxVolume
        if (openLoaded && openSoundId != 0) {
            sp.play(openSoundId, vol, vol, 1, 0, 1f)
        } else {
            val ctx = appContext ?: return
            if (openResId == 0) return
            MediaPlayer.create(ctx, openResId)?.apply {
                setVolume(vol, vol)
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }

    /**
     * 다이얼 회전 상태 알림 — true(돌리기 시작)면 회전음 재생, false(놓음)면 더 잇지 않음.
     * 겹쳐 재생하지 않고, 한 번 끝났을 때 아직 [dialTurning] 이면 처음부터 다시 재생한다.
     * 음소거(enabled=false) 상태에선 출력하지 않는다.
     */
    fun setDialTurning(turning: Boolean) {
        if (turning && !enabled) return
        dialTurning = turning
        if (turning) startDialSfxIfNeeded()
    }

    private fun startDialSfxIfNeeded() {
        val ctx = appContext ?: return
        if (dialResId == 0) dialResId = ctx.resources.getIdentifier("turning_dial", "raw", ctx.packageName)
        if (dialResId == 0) return // 음원 미존재 → 무동작
        if (dialPlayer?.isPlaying == true) return // 이미 재생 중이면 겹치지 않음
        dialPlayer?.release()
        dialPlayer = MediaPlayer.create(ctx, dialResId)?.apply {
            setVolume(DIAL_VOLUME * sfxVolume, DIAL_VOLUME * sfxVolume)
            setOnCompletionListener {
                if (dialTurning && enabled) { it.seekTo(0); it.start() } // 아직 돌리는 중이면 이어서
            }
            start()
        }
    }

    /** 토글(FAB) — on/off 저장 후 즉시 반영. */
    fun setActive(value: Boolean) {
        if (enabled == value) return
        enabled = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        if (value) resume() else pause()
    }

    /** 배경음악 볼륨 설정(0..1) — 저장 + 현재 재생 중인 player 에 즉시 반영.
     *  (property setter([musicVolume]) 와 JVM 시그니처 충돌을 피해 함수명을 다르게 둔다 — enabled/setActive 와 동일 패턴) */
    fun updateMusicVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        if (musicVolume == v) return
        musicVolume = v
        player?.setVolume(v, v)
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putFloat(KEY_MUSIC_VOL, v)?.apply()
    }

    /** 효과음 볼륨 설정(0..1) — 저장. 다음 효과음 재생부터 반영. */
    fun updateSfxVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        if (sfxVolume == v) return
        sfxVolume = v
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putFloat(KEY_SFX_VOL, v)?.apply()
    }

    /** 앱 전면 복귀 or 켜짐 → 현재 트랙을 마지막 위치에서 재생. */
    fun resume() {
        if (!enabled) return
        val ctx = appContext ?: return
        val id = playingId ?: selectedTrackId
        if (player == null) {
            val res = resIdFor(id)
            if (res == 0) return
            player = MediaPlayer.create(ctx, res)?.apply { isLooping = true }
            player?.setVolume(musicVolume, musicVolume)
            player?.seekTo(positionMs)
            playingId = id
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

    /** 현재 재생 위치(ms). 일시정지 상태면 보존된 위치. */
    fun currentPositionMs(): Int =
        player?.let { if (it.isPlaying) it.currentPosition else positionMs } ?: positionMs

    /**
     * 지정 트랙을 [positionMs0] 위치부터 즉시 재생(기존 player 교체). 미리듣기/복원 공용.
     * 음소거(enabled=false) 상태면 준비만 하고 재생하지 않는다(켜지면 [resume] 으로 이어 재생).
     */
    fun playTrack(id: String, positionMs0: Int = 0) {
        val ctx = appContext ?: return
        val res = resIdFor(id)
        if (res == 0) return
        player?.release()
        val mp = MediaPlayer.create(ctx, res)
        if (mp != null) {
            mp.isLooping = true
            mp.setVolume(musicVolume, musicVolume)
            // 이어 듣기용으로 넘어온 위치가 새 트랙 길이를 넘으면 안으로 보정.
            val dur = mp.duration
            val pos = if (dur > 0) positionMs0.coerceIn(0, (dur - 200).coerceAtLeast(0))
                      else positionMs0.coerceAtLeast(0)
            mp.seekTo(pos)
            if (enabled) mp.start()
            positionMs = pos
        }
        player = mp
        playingId = id
    }

    /** 영구 선택 트랙 확정(저장). 재생 자체는 이미 [playTrack] 으로 진행 중이다. */
    fun commitSelectedTrack(id: String) {
        selectedTrackId = id
        playingId = id
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_TRACK, id)?.apply()
    }

    /** 앱 종료/재생성 — 위치 보존 후 해제. 다음 [init] 이 SoundPool 등을 다시 로드하도록 [initialized] 도 리셋
     *  (언어 변경 등으로 액티비티가 recreate 되면 dispose→release→init 사이클이 도므로, 안 풀면 효과음이 깨진다). */
    fun release() {
        player?.let {
            positionMs = it.currentPosition
            it.release()
        }
        player = null
        soundPool?.release()
        soundPool = null
        windLoaded = false
        openLoaded = false
        dialTurning = false
        dialPlayer?.release()
        dialPlayer = null
        initialized = false
    }
}
