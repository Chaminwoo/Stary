package com.chaminwoo.stary.core.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chaminwoo.stary.R

/**
 * 앱 전역 배경음악 관리자. (특정 화면에 묶이지 않고 모든 스크린에서 재생)
 *
 * [enabled] 는 **모든 소리**(배경음악 + 효과음)의 on/off 다 — 설정의 "모든 소리 끄기"(2026-10-14 이름 변경,
 * 예전 이름 "배경음악"은 효과음까지 꺼지는 게 버그처럼 보였다). 효과음만 따로 줄이려면 효과음 볼륨을 쓴다.
 *
 * - 기본 켜짐(처음 실행 시 자동 재생). 기본 음악은 [MusicCatalog.DEFAULT_ID].
 * - 멈췄다 재생하면 처음이 아니라 **마지막 위치**에서 이어 재생(위치 보존).
 * - 끝까지 가면 처음부터 반복(isLooping).
 * - on/off 상태와 선택한 트랙([selectedTrackId])은 SharedPreferences 에 저장.
 * - 다른 앱이 소리를 내면 배경음악만 쉬고 효과음은 그대로 낸다(오디오 포커스 — 아래 "다른 앱 소리에 양보").
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

    // 다른 앱 소리에 양보(2026-10, S1) — 배경음악만 쉬고 효과음은 그대로 낸다(SoundPool 은 포커스 없이 섞여 울린다).
    //  - 앱을 열 때([resume]) 음악·영상 소리가 이미 나오고 있으면 배경음악을 시작하지 않는다(포커스도 뺏지 않는다).
    //  - 트는 중 전화·알람(LOSS_TRANSIENT)이 오면 멈췄다가 포커스가 돌아오면(GAIN) 이어서.
    //  - 다른 앱이 재생을 시작(LOSS)하면 멈추고, 그 소리가 그치면 이어서 — 이때는 시스템이 GAIN 을 다시 주지 않아서
    //    앱이 전면에 있는 동안만 [QUIET_POLL_MS] 마다 확인한다([waitForQuiet]).
    //  - 내비 안내처럼 잠깐 줄여도 되는 소리(덕킹)는 API 26+ 시스템이 알아서 줄였다 되돌린다.
    //  - 음악 탭 미리듣기([playTrack])는 사용자가 직접 고른 소리라 포커스를 요청한다(다른 앱 음악이 멈춘다).
    //  iOS 는 AVAudioSession.secondaryAudioShouldBeSilencedHint + 힌트/중단 알림으로 같은 동작.
    private val mediaAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var pausedByFocus = false   // 잠깐 뺏겨 멈춤(또는 통화 중이라 미뤄짐) — GAIN 때 시작
    private var waitingQuiet = false    // 다른 앱 소리가 그치길 기다리는 중
    // 기다리는 동안 그 소리를 실제로 들었나 — 못 들었으면 다시 틀지 않는다(소리 없이 포커스만 가져간 녹음 앱 등과 다투지 않게).
    private var heardOthers = false
    private var quietPolls = 0
    private const val QUIET_POLL_MS = 1500L
    private val mainHandler = Handler(Looper.getMainLooper())

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                if (pausedByFocus) {
                    pausedByFocus = false
                    if (enabled && wantPlaying) startPlayer()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasFocus = false
                pausedByFocus = true
                pausePlayer()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausePlayer()
                abandonFocus()
                if (enabled && wantPlaying) waitForQuiet(heard = false)
            }
        }
    }

    /** 두 번 연달아 조용하면 다시 튼다 — 곡 넘김 같은 잠깐의 공백에 끼어들지 않게. */
    private val quietPoll = object : Runnable {
        override fun run() {
            if (!waitingQuiet) return
            if (audioManager?.isMusicActive == true) {
                heardOthers = true
                quietPolls = 0
            } else if (heardOthers && ++quietPolls >= 2) {
                stopWaiting()
                if (enabled && wantPlaying && requestFocus()) startPlayer()
                return
            }
            mainHandler.postDelayed(this, QUIET_POLL_MS)
        }
    }

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

    // 연출용 짧은 효과음(2026-09-25 사용자 선택 — 후보는 tools/sfx 로 합성, 미리듣기 후 확정).
    //  - sfx_spark_1~3 : 별이 하나 둘 떠오를 때(별 도감 원 · 프로필 아이콘) "톡톡 반짝" — 3종 중 무작위.
    //  - sfx_star_birth: 업로드 성공 별 탄생(유성이 내려와 안착) · sfx_like: 좋아요(뽀옹+반짝) · sfx_drawer: 드로어 열기(스윽).
    // 모두 SoundPool 에 미리 로드(즉시·중복 재생). 로드 전 호출은 조용히 건너뛴다(연출 보조음이라 누락 허용).
    private val SPARK_RES = intArrayOf(R.raw.sfx_spark_1, R.raw.sfx_spark_2, R.raw.sfx_spark_3)
    private val EXTRA_SFX_RES = intArrayOf(R.raw.sfx_star_birth, R.raw.sfx_like, R.raw.sfx_drawer)
    private val sfxSoundIds = HashMap<Int, Int>()   // resId → soundId
    private val sfxLoaded = HashSet<Int>()          // 로드 완료된 soundId
    private const val SPARK_VOLUME = 0.30f
    /** 별이 촘촘히 뜰 때 반짝임이 폭주하지 않도록 최소 간격(ms) — 그보다 빠른 호출은 건너뛴다. */
    private const val SPARK_MIN_GAP_MS = 55L
    private var lastSparkAtMs = 0L
    private var lastSparkIdx = -1
    private const val STAR_BIRTH_VOLUME = 0.55f
    private const val LIKE_VOLUME = 0.50f
    private const val DRAWER_VOLUME = 0.32f

    // 필터 전환 "빛의 고리"가 별에 닿을 때의 차임(2026-10-14, references/filtering.mp4 — tools/sfx/reveal_chime.py 로 합성).
    // C 장조 펜타토닉 5음(C6 D6 E6 G6 A6). 별마다 [CHIME_ORDER] 순서로 음을 바꿔 짧은 선율처럼 들린다.
    // iOS MusicManager.playRevealChime 과 같은 볼륨·간격·순서.
    private val CHIME_RES = intArrayOf(
        R.raw.sfx_chime_1, R.raw.sfx_chime_2, R.raw.sfx_chime_3, R.raw.sfx_chime_4, R.raw.sfx_chime_5,
    )
    private val CHIME_ORDER = intArrayOf(0, 2, 1, 3, 2, 4, 3, 1)
    private const val CHIME_VOLUME = 0.34f
    /** 별이 촘촘할 때 차임이 뭉개지지 않도록 최소 간격(ms) — 그보다 빠른 호출은 건너뛴다. */
    private const val CHIME_MIN_GAP_MS = 60L
    private var lastChimeAtMs = 0L
    private var chimeStep = 0

    // 글로브 유성 효과음(2026-10-15 사용자 선택 "별가루 쉬머" — tools/sfx/meteor_sfx.py export_app).
    // 연속일수록 꼬리 반짝임이 펜타토닉으로 한 단계씩 높다: 1 = 연속 1~2번째, 2 = 3~4, 3 = 5~6, 4 = 7번째 이상.
    // iOS MusicManager.playMeteor 와 같은 볼륨·단계.
    private val METEOR_RES = intArrayOf(
        R.raw.sfx_meteor_1, R.raw.sfx_meteor_2, R.raw.sfx_meteor_3, R.raw.sfx_meteor_4,
    )
    private const val METEOR_VOLUME = 0.45f

    // 다이얼(맷돌) 회전 효과음(turning_dial.mp3).
    //  - **돌리는 동안**: [dialTick] 이 실제 각도 눈금을 지날 때마다 호출되어 처음부터 다시 재생 —
    //    고정 타이머가 아니라 호출 빈도 = 회전 속도라서 빠르게 돌리면 "드드드드" 촘촘해진다.
    //  - **놓으면**: [dialRelease] 가 음원을 처음부터 **한 번만 끝까지** 울린다(자르지 않는다).
    // (SoundPool 대신 위치 제어/완료 콜백이 필요해 MediaPlayer 사용)
    private var dialPlayer: MediaPlayer? = null
    private var dialResId = 0
    private const val DIAL_VOLUME = 0.6f

    /** 맷돌 눈금음 최소 간격(ms) — 이보다 빠른 연속 호출은 뭉개짐 방지로 무시한다(체감상 사실상 무제한 속도). */
    private const val DIAL_TICK_MIN_GAP_MS = 40L
    private var lastDialTickAtMs = 0L

    /** Compose 에서 관찰 가능한 on/off 상태 — **모든 소리**(배경음악 + 효과음). false 면 아무 소리도 나지 않는다. */
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
        audioManager = ctx.getSystemService(AudioManager::class.java)

        // 효과음을 SoundPool 에 미리 로드 (탭 시 지연 없이 재생)
        windResId = ctx.resources.getIdentifier("wind", "raw", ctx.packageName)
        openResId = ctx.resources.getIdentifier("open_diary", "raw", ctx.packageName)
        // 별 등장 차임/반짝임이 겹쳐 울리므로 동시 스트림을 넉넉히(초과 시 SoundPool 이 가장 오래된 것을 끊는다).
        soundPool = SoundPool.Builder().setMaxStreams(12).setAudioAttributes(mediaAttrs).build().apply {
            setOnLoadCompleteListener { _, sampleId, status ->
                if (status != 0) return@setOnLoadCompleteListener
                when (sampleId) {
                    windSoundId -> windLoaded = true
                    openSoundId -> openLoaded = true
                    else -> sfxLoaded.add(sampleId)
                }
            }
        }
        if (windResId != 0) windSoundId = soundPool?.load(ctx, windResId, 1) ?: 0
        if (openResId != 0) openSoundId = soundPool?.load(ctx, openResId, 1) ?: 0
        (SPARK_RES + CHIME_RES + EXTRA_SFX_RES + METEOR_RES).forEach { res ->
            soundPool?.load(ctx, res, 1)?.let { sfxSoundIds[res] = it }
        }
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

    /** 미리 로드된 짧은 효과음 1회 재생. 음소거(enabled=false)거나 아직 로드 전이면 무동작. */
    private fun playSfx(res: Int, volume: Float, rate: Float = 1f) {
        if (!enabled) return
        val sp = soundPool ?: return
        val id = sfxSoundIds[res] ?: return
        if (id !in sfxLoaded) return
        val v = (volume * sfxVolume).coerceIn(0f, 1f)
        sp.play(id, v, v, 1, 0, rate)
    }

    /**
     * 별 하나가 "톡" 떠오를 때의 반짝임 — 별 도감 원 등장 등에서 별마다 호출(지도 필터 전환은 [playRevealChime]).
     * 3종 중 직전과 다른 것을 고르고 재생 속도를 살짝 흔들어(음높이 ±) 기계적인 반복감을 없앤다.
     * [SPARK_MIN_GAP_MS] 보다 촘촘한 호출은 건너뛴다(별이 수십 개여도 소리는 적당한 밀도).
     */
    fun playSparkTick() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastSparkAtMs < SPARK_MIN_GAP_MS) return
        lastSparkAtMs = now
        var idx = kotlin.random.Random.nextInt(SPARK_RES.size)
        if (idx == lastSparkIdx) idx = (idx + 1) % SPARK_RES.size
        lastSparkIdx = idx
        val vol = SPARK_VOLUME * (0.75f + 0.25f * kotlin.random.Random.nextFloat())
        playSfx(SPARK_RES[idx], vol, rate = 0.94f + 0.14f * kotlin.random.Random.nextFloat())
    }

    /** 빛의 고리 한 번이 시작될 때 — 차임 선율을 첫 음부터 다시. */
    fun resetRevealChime() {
        chimeStep = 0
    }

    /**
     * 필터 전환 "빛의 고리"가 별 하나에 닿는 순간의 차임 — 지도 등장 루프가 별마다 호출한다.
     * 호출마다 [CHIME_ORDER] 의 다음 음을 울리고, [CHIME_MIN_GAP_MS] 보다 촘촘한 호출은 건너뛴다.
     */
    fun playRevealChime() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastChimeAtMs < CHIME_MIN_GAP_MS) return
        lastChimeAtMs = now
        val idx = CHIME_ORDER[chimeStep % CHIME_ORDER.size]
        chimeStep++
        playSfx(CHIME_RES[idx], CHIME_VOLUME * (0.8f + 0.2f * kotlin.random.Random.nextFloat()))
    }

    /** 글로브에서 유성이 떨어질 때 — [streak] = 이번 유성까지의 연속 횟수(연속일수록 반짝임 음이 높다). 어느 스레드에서 불러도 된다. */
    fun playMeteor(streak: Int) {
        val idx = ((streak - 1) / 2).coerceIn(0, METEOR_RES.size - 1)
        playSfx(METEOR_RES[idx], METEOR_VOLUME)
    }

    /** 업로드 성공 별 탄생 — 유성이 내려와 안착(화음이 StarBirth 발광 순간 ≈0.42s 에 맞춰져 있다). */
    fun playStarBirth() = playSfx(R.raw.sfx_star_birth, STAR_BIRTH_VOLUME)

    /** 좋아요를 눌렀을 때(취소 때는 호출하지 않는다) — 뽀옹 + 반짝. */
    fun playLike() = playSfx(R.raw.sfx_like, LIKE_VOLUME)

    /** 드로어(메뉴)가 열릴 때 — 스윽. 닫힐 때는 무음. */
    fun playDrawer() = playSfx(R.raw.sfx_drawer, DRAWER_VOLUME)

    /**
     * 맷돌(다이얼) 눈금음 — **실제로 돌릴 때마다**(각도 눈금을 지날 때) 호출한다. 고정 타이머가 아니라
     * 호출 빈도 자체가 회전 속도이므로, 빠르게 돌리면 자연히 "드드드드" 촘촘하게, 천천히 돌리면 드문드문
     * 울린다. 가만히 잡고만 있으면(호출이 없으면) 아무 소리도 나지 않는다.
     * 음소거(enabled=false) 상태에선 출력하지 않는다.
     */
    fun dialTick() {
        if (!enabled) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastDialTickAtMs < DIAL_TICK_MIN_GAP_MS) return
        lastDialTickAtMs = now
        restartDialSfx(1f)
    }

    /**
     * 놓았을 때 — 음원을 처음부터 **한 번만 끝까지** 울린다(중간에 자르지 않는다).
     * 예전엔 간격을 벌리며 5회 반복하는 잔향이었는데 너무 길고 어색해서 단발로 바꿨다(2026-09-04).
     * 음소거(enabled=false) 상태에선 출력하지 않는다.
     */
    fun dialRelease() {
        if (!enabled) return
        restartDialSfx(1f)
    }

    /** 회전음을 처음부터 다시 재생. player 는 한 번 만들어 두고 seek 으로 되감는다(생성 지연 방지). */
    private fun restartDialSfx(volumeScale: Float) {
        val ctx = appContext ?: return
        if (dialResId == 0) dialResId = ctx.resources.getIdentifier("turning_dial", "raw", ctx.packageName)
        if (dialResId == 0) return // 음원 미존재 → 무동작
        val vol = (DIAL_VOLUME * sfxVolume * volumeScale.coerceIn(0f, 1f))
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
            setOnCompletionListener { it.seekTo(0) } // 다음 dialTick/dialRelease 가 재사용
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
            mp.setAudioAttributes(mediaAttrs) // 포커스 요청과 같은 용도(준비 전에만 먹는다)
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

    /** 앱 전면 복귀 or 켜짐 → 현재 트랙을 마지막 위치에서 재생. 다른 앱 소리가 나고 있으면 쉬었다가 그치면 이어서. */
    fun resume() {
        if (!enabled) return
        wantPlaying = true
        if (player?.isPlaying == true || waitingQuiet) return
        if (audioManager?.isMusicActive == true) {
            waitForQuiet(heard = true)
            return
        }
        if (requestFocus()) startPlayer()
    }

    /** 준비된 player 를 시작하거나, 없으면 비동기로 준비해 시작한다(포커스는 부르는 쪽이 받아 둔다). */
    private fun startPlayer() {
        stopWaiting()
        val ctx = appContext ?: return
        val existing = player
        if (existing != null) {
            if (!existing.isPlaying) existing.start()
            return
        }
        val id = playingId ?: selectedTrackId
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
            if (enabled && wantPlaying && hasFocus) mp.start()
        }
    }

    /** 일시정지 — 현재 위치 보존. 앱이 뒤로 갔거나 소리를 끈 것이라 포커스도 돌려준다. */
    fun pause() {
        wantPlaying = false
        stopWaiting()
        pausePlayer()
        abandonFocus()
    }

    private fun pausePlayer() {
        player?.let {
            if (it.isPlaying) {
                positionMs = it.currentPosition
                it.pause()
            }
        }
    }

    /** 배경음악 포커스 요청 — 받으면 true. 통화 중이라 미뤄지면(DELAYED) 끝날 때 GAIN 으로 시작한다. */
    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val am = audioManager ?: return false
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(mediaAttrs)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(focusListener, mainHandler)
            .build()
            .also { focusRequest = it }
        return when (am.requestAudioFocus(req)) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> { hasFocus = true; true }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> { pausedByFocus = true; false }
            else -> false
        }
    }

    private fun abandonFocus() {
        hasFocus = false
        pausedByFocus = false
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
    }

    /** 다른 앱 소리가 그치면 다시 틀도록 기다린다 — [heard] = 그 소리를 이미 확인했는지(앱을 열 때). */
    private fun waitForQuiet(heard: Boolean) {
        if (!waitingQuiet) {
            waitingQuiet = true
            heardOthers = false
            quietPolls = 0
            mainHandler.postDelayed(quietPoll, QUIET_POLL_MS)
        }
        if (heard) heardOthers = true
    }

    private fun stopWaiting() {
        waitingQuiet = false
        mainHandler.removeCallbacks(quietPoll)
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
        // 직접 고른 미리듣기 — 다른 앱 소리가 나고 있어도 포커스를 요청한다.
        if (enabled) {
            stopWaiting()
            requestFocus()
        }
        createAsync(ctx, res) { mp ->
            if (gen != prepareGen) { mp.release(); return@createAsync }
            mp.isLooping = true
            mp.setVolume(musicVolume, musicVolume)
            // 이어 듣기용으로 넘어온 위치가 새 트랙 길이를 넘으면 안으로 보정.
            val dur = mp.duration
            val pos = if (dur > 0) positionMs0.coerceIn(0, (dur - 200).coerceAtLeast(0))
                      else positionMs0.coerceAtLeast(0)
            mp.seekTo(pos)
            if (enabled && wantPlaying && hasFocus) mp.start()
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
        stopWaiting()
        abandonFocus()
        player?.let {
            positionMs = it.currentPosition
            it.release()
        }
        player = null
        soundPool?.release()
        soundPool = null
        windLoaded = false
        openLoaded = false
        sfxSoundIds.clear()
        sfxLoaded.clear()
        dialPlayer?.release()
        dialPlayer = null
        initialized = false
    }
}
