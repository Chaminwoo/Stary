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
    // 비동기 준비(prepareAsync) 무효화 토큰 — playTrack/release 가 올리면 이전 준비 콜백은 폐기된다.
    private var prepareGen = 0
    // 재생 의사 — resume()=true / pause()=false. 준비가 끝나기 전에 pause 되면 시작하지 않기 위함.
    private var wantPlaying = false

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

    // 다이얼 회전 효과음(turning_dial.mp3).
    //  - **돌리는 동안**: [DIAL_REPEAT_MS] 간격으로 처음부터 다시 재생 → 짧게 끊어지며 빠르게 반복된다
    //    (음원 전체 길이만큼 기다리면 간격이 너무 벌어져 "돌아가는 느낌"이 안 난다).
    //  - **놓거나 멈춘 뒤**: 반복만 멈추고 재생 중인 소리는 자르지 않아 **끝까지** 울린다(여운).
    // (SoundPool 대신 위치 제어/완료 콜백이 필요해 MediaPlayer 사용)
    private var dialPlayer: MediaPlayer? = null
    private var dialResId = 0
    private var dialTurning = false
    private const val DIAL_VOLUME = 0.6f

    /** 돌리는 동안 회전음을 다시 트는 간격(ms). 음원 길이보다 짧아야 "빠르게 반복"이 된다. */
    private const val DIAL_REPEAT_MS = 300L

    private val dialHandler = android.os.Handler(android.os.Looper.getMainLooper())
    /** 돌리는 동안만 도는 반복 타이머 — [setDialTurning] false 에서 취소된다. */
    private val dialRepeat = object : Runnable {
        override fun run() {
            if (!dialTurning || !enabled) return
            restartDialSfx()
            dialHandler.postDelayed(this, DIAL_REPEAT_MS)
        }
    }

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
     * 다이얼 회전 상태 알림.
     *  - true(돌리기 시작): 회전음을 재생하고 [DIAL_REPEAT_MS] 간격으로 처음부터 다시 튼다(빠른 반복).
     *  - false(놓음/멈춤): 반복만 멈춘다. **재생 중인 소리는 자르지 않아 끝까지 울린다.**
     * 음소거(enabled=false) 상태에선 출력하지 않는다.
     */
    fun setDialTurning(turning: Boolean) {
        if (turning && !enabled) return
        if (dialTurning == turning) return
        dialTurning = turning
        if (turning) {
            restartDialSfx()
            dialHandler.removeCallbacks(dialRepeat)
            dialHandler.postDelayed(dialRepeat, DIAL_REPEAT_MS)
        } else {
            // 반복만 취소 — 지금 울리는 소리는 그대로 끝까지 둔다(여운).
            dialHandler.removeCallbacks(dialRepeat)
        }
    }

    /** 회전음을 처음부터 다시 재생. player 는 한 번 만들어 두고 seek 으로 되감는다(생성 지연 방지). */
    private fun restartDialSfx() {
        val ctx = appContext ?: return
        if (dialResId == 0) dialResId = ctx.resources.getIdentifier("turning_dial", "raw", ctx.packageName)
        if (dialResId == 0) return // 음원 미존재 → 무동작
        val vol = DIAL_VOLUME * sfxVolume
        val existing = dialPlayer
        if (existing != null) {
            runCatching {
                existing.setVolume(vol, vol)
                existing.seekTo(0)
                if (!existing.isPlaying) existing.start()
            }
            return
        }
        dialPlayer = MediaPlayer.create(ctx, dialResId)?.apply {
            setVolume(vol, vol)
            // 음원이 반복 간격보다 짧을 때도 돌리는 동안 끊기지 않게 이어 붙인다.
            setOnCompletionListener { if (dialTurning && enabled) { it.seekTo(0); it.start() } }
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

    /** MediaPlayer 를 비동기 준비(prepareAsync)해 [onReady] 로 넘긴다 — 동기 create()의
     *  디코더 초기화+파일 읽기가 메인 스레드를 멈추지 않게. 콜백은 메인 루퍼에서 온다. */
    private fun createAsync(ctx: Context, resId: Int, onReady: (MediaPlayer) -> Unit) {
        val mp = MediaPlayer()
        try {
            ctx.resources.openRawResourceFd(resId).use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
        } catch (_: Exception) {
            mp.release()
            return
        }
        mp.setOnPreparedListener { onReady(it) }
        mp.setOnErrorListener { p, _, _ -> p.release(); true }
        mp.prepareAsync()
    }

    /** 앱 전면 복귀 or 켜짐 → 현재 트랙을 마지막 위치에서 재생. */
    fun resume() {
        if (!enabled) return
        val ctx = appContext ?: return
        wantPlaying = true
        val id = playingId ?: selectedTrackId
        val existing = player
        if (existing != null) {
            if (!existing.isPlaying) existing.start()
            return
        }
        val res = resIdFor(id)
        if (res == 0) return
        val gen = prepareGen
        createAsync(ctx, res) { mp ->
            // 준비되는 사이 트랙 교체/해제됐거나 이미 다른 player 가 붙었으면 폐기
            if (gen != prepareGen || player != null) { mp.release(); return@createAsync }
            mp.isLooping = true
            mp.setVolume(musicVolume, musicVolume)
            mp.seekTo(positionMs)
            player = mp
            playingId = id
            if (enabled && wantPlaying) mp.start()
        }
    }

    /** 일시정지 — 현재 위치 보존. */
    fun pause() {
        wantPlaying = false
        player?.let {
            if (it.isPlaying) {
                positionMs = it.currentPosition
                it.pause()
            }
        }
    }

    /**
     * 지정 트랙을 [positionMs0] 위치부터 즉시 재생(기존 player 교체). 미리듣기/복원 공용.
     * 음소거(enabled=false) 상태면 준비만 하고 재생하지 않는다(켜지면 [resume] 으로 이어 재생).
     */
    fun playTrack(id: String, positionMs0: Int = 0) {
        val ctx = appContext ?: return
        val res = resIdFor(id)
        if (res == 0) return
        val gen = ++prepareGen
        player?.release()
        player = null
        playingId = id
        wantPlaying = true
        createAsync(ctx, res) { mp ->
            if (gen != prepareGen) { mp.release(); return@createAsync }
            mp.isLooping = true
            mp.setVolume(musicVolume, musicVolume)
            // 이어 듣기용으로 넘어온 위치가 새 트랙 길이를 넘으면 안으로 보정.
            val dur = mp.duration
            val pos = if (dur > 0) positionMs0.coerceIn(0, (dur - 200).coerceAtLeast(0))
                      else positionMs0.coerceAtLeast(0)
            mp.seekTo(pos)
            if (enabled && wantPlaying) mp.start()
            positionMs = pos
            player = mp
        }
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
        prepareGen++ // 진행 중인 비동기 준비 무효화
        wantPlaying = false
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
        dialHandler.removeCallbacks(dialRepeat)
        dialPlayer?.release()
        dialPlayer = null
        initialized = false
    }
}
