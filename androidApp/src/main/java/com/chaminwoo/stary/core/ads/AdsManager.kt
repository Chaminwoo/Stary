package com.chaminwoo.stary.core.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chaminwoo.stary.BuildConfig
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayConfiguration
import com.unity3d.mediation.LevelPlayInitError
import com.unity3d.mediation.LevelPlayInitListener
import com.unity3d.mediation.LevelPlayInitRequest
import com.unity3d.mediation.rewarded.LevelPlayReward
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd
import com.unity3d.mediation.rewarded.LevelPlayRewardedAdListener
import java.lang.ref.WeakReference

/**
 * 보상형 광고 — 앱에서 광고를 쓰는 **유일한 진입점**. 광고원은 두 곳, **순서가 있다**:
 *
 * 1. **Unity LevelPlay**(미디에이션) — 기본. Unity Ads·ironSource 등을 입찰시켜 이긴 광고(플레이어블 포함)를 보여준다.
 *    Unity Ads SDK 직접 연동은 2026-01-31 로 수익화 지원이 끝났고 새 광고 단위는 헤더 비딩 전용이라 미디에이션이 필수다.
 * 2. **AdMob 폴백**([AdMobRewarded]) — LevelPlay 가 광고를 못 줬을 때만. **왜 넣었나(2026-09-23)**: LevelPlay 로드가
 *    매번 `509 Mediation No fill` 이었는데, 509 는 "이 광고 단위에 입찰한 네트워크가 없다"는 **수요/대시보드** 결과라
 *    앱 코드로는 못 채운다. 그 사이 잠금 해제 흐름 전체가 막히므로 두 번째 광고원을 둔다.
 *    디버그 빌드에서는 값이 없으면 구글 **테스트** 광고 단위라 항상 채워지고, 릴리즈는 실제 값을 넣어야만 켜진다.
 *
 * 쓰는 곳: 100m 밖 게시물의 잠금 해제([com.chaminwoo.stary.core.util.DiaryUnlockStore], DetailScreen `DiaryLock.kt`).
 * 흐름: [init] (앱 시작 1회) → [preload] (잠금 화면 진입 시) → [showRewardedWhenReady] (재생 아이콘 탭).
 *   아직 로드 중이면 최대 [WAIT_FOR_LOAD_MS] 기다렸다가 도착하는 즉시 재생한다.
 *
 * - 앱 키 / 광고 단위 ID 는 `secrets.properties` → BuildConfig 주입(하드코딩 금지).
 *   두 광고원이 **모두** 비어 있으면 [isConfigured] 가 false → 호출부는 아이콘을 탭해도 "지금은 광고를 불러올 수 없어요".
 * - 보상 판정은 LevelPlay 는 `onAdRewarded` 뿐. LevelPlay 는 `onAdRewarded` 가 `onAdClosed` **뒤에** 올 수도 있다고
 *   명시 → 닫힌 뒤 [REWARD_GRACE_MS] 동안 보상 콜백을 기다렸다가 결과를 한 번만 넘긴다(AdMob 은 순서가 보장돼 불필요).
 * - 콜백은 모두 메인 스레드 — 그대로 Compose 상태를 건드려도 된다.
 * - 디버그 빌드: 어댑터 로그 + 통합 검증(validateIntegration) + 테스트 스위트([launchTestSuite], 재생 아이콘 길게 누르기).
 */
object AdsManager {

    private const val TAG = "AdsManager"

    /** LevelPlay 로드 실패 코드 중 "채울 광고가 없음"(= 대시보드에 활성 네트워크 없음/수요 없음). */
    private const val ERROR_NO_FILL = 509

    private val appKey: String get() = BuildConfig.LEVELPLAY_APP_KEY
    private val rewardedAdUnitId: String get() = BuildConfig.LEVELPLAY_REWARDED_AD_UNIT

    /** LevelPlay 키가 실제 값인지(placeholder 가 아닌지). */
    private val levelPlayConfigured: Boolean
        get() = appKey.isNotBlank() && !appKey.startsWith("TODO_") &&
            rewardedAdUnitId.isNotBlank() && !rewardedAdUnitId.startsWith("TODO_")

    /** 광고를 시도라도 해볼 수 있는지 — 둘 중 하나라도 살아 있으면 true. */
    val isConfigured: Boolean
        get() = levelPlayConfigured || AdMobRewarded.isConfigured

    /** LevelPlay SDK 초기화 완료 여부(관찰 가능). */
    var initialized by mutableStateOf(false)
        private set

    /** 보상형 광고가 로드돼 바로 보여줄 수 있는 상태인지(광고원 무관). */
    var rewardedReady by mutableStateOf(false)
        private set

    /** 광고 재생 중 — 중복 show 호출/버튼 연타 방지. */
    var showing by mutableStateOf(false)
        private set

    /** 마지막 LevelPlay 초기화/로드 실패 사유. 폴백까지 합친 문구는 [diagnostics]. */
    var lastError: String? by mutableStateOf(null)
        private set

    /** 디버그 안내용 한 줄 진단 — LevelPlay 실패 사유 + 폴백 실패 사유. */
    val diagnostics: String
        get() = listOfNotNull(lastError, AdMobRewarded.lastError?.let { "폴백 " + it })
            .joinToString(" / ")
            .ifEmpty { "원인 불명(로그 태그 AdsManager 확인)" }

    /** 탭했는데 아직 로드 전이면 이만큼 기다린다. LevelPlay 실패 → 폴백 로드까지 이어질 수 있어 넉넉히. */
    private const val WAIT_FOR_LOAD_MS = 12_000L

    /** 광고가 닫힌 뒤 늦게 오는 LevelPlay onAdRewarded 를 기다리는 시간. */
    private const val REWARD_GRACE_MS = 1_500L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var initStarted = false
    private var loading = false
    private var rewardedAd: LevelPlayRewardedAd? = null

    /** 지금 광고를 꺼내 올 수 있는 곳. */
    private enum class Source { LEVELPLAY, ADMOB }

    /** 로드를 기다리는 재생 요청(최대 1개). Activity 는 약참조 — 기다리는 사이 화면이 닫혀도 새지 않게. */
    private class PendingShow(
        val activity: WeakReference<Activity>,
        val onResult: (Boolean) -> Unit,
        val onUnavailable: () -> Unit,
    )
    private var pending: PendingShow? = null

    /** 지금 재생 중인 LevelPlay 광고 1건의 결과 수집 — 보상/닫힘 순서가 뒤바뀌어도 결과는 한 번만. */
    private class ShowSession(val onResult: (Boolean) -> Unit) {
        var rewarded = false
        var closed = false
        var delivered = false
    }
    private var session: ShowSession? = null

    /**
     * 앱 시작 시 1회(StaryApplication). LevelPlay 키가 없으면 SDK 초기화는 건너뛴다(폴백은 [preload] 가 챙긴다).
     * 초기화에 실패해도 다음 [preload] 가 다시 시도한다.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        if (initStarted || initialized || !levelPlayConfigured) return
        initStarted = true
        if (BuildConfig.DEBUG) {
            // 디버그 전용 진단: 네트워크(어댑터)별 로그 + LevelPlay 통합 테스트 스위트(초기화 **전에** 켜야 한다).
            LevelPlay.setAdaptersDebug(true)
            LevelPlay.setMetaData("is_test_suite", "enable")
            Log.i(TAG, "LevelPlay init - adUnit=" + rewardedAdUnitId + ", pkg=" + context.packageName)
        }
        val request = LevelPlayInitRequest.Builder(appKey).build()
        LevelPlay.init(context.applicationContext, request, object : LevelPlayInitListener {
            override fun onInitSuccess(configuration: LevelPlayConfiguration) {
                initialized = true
                lastError = null
                // 어떤 어댑터가 붙었는지/매니페스트 누락이 있는지 로그로 뱉는다(509 원인 추적용).
                if (BuildConfig.DEBUG) appContext?.let { LevelPlay.validateIntegration(it) }
                createRewardedAd()
                preload()
            }

            override fun onInitFailed(error: LevelPlayInitError) {
                initStarted = false // 다음 preload/탭에서 다시 시도
                lastError = "init " + error.errorCode + ": " + error.errorMessage
                Log.w(TAG, "LevelPlay 초기화 실패: " + lastError)
                loadFallback() // LevelPlay 가 안 서면 폴백으로
            }
        })
    }

    /** 광고 객체는 **초기화 성공 뒤에만** 만든다(LevelPlay 규칙). 리스너는 로드 전에 단다. */
    private fun createRewardedAd() {
        if (rewardedAd != null) return
        rewardedAd = LevelPlayRewardedAd(rewardedAdUnitId).apply {
            setListener(object : LevelPlayRewardedAdListener {
                override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                    loading = false
                    lastError = null
                    refreshReady()
                    resolvePending()
                }

                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    loading = false
                    lastError = describeLoadError(error)
                    Log.w(TAG, "보상형 광고 로드 실패(" + rewardedAdUnitId + "): " + lastError)
                    refreshReady()
                    // LevelPlay 가 못 채웠으면 폴백으로 — 기다리는 요청 정리는 폴백이 끝난 뒤에.
                    loadFallback()
                }

                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {}

                override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
                    val s = session ?: return
                    s.rewarded = true
                    if (s.closed) deliver(s) // 닫힌 뒤에 온 보상
                }

                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                    Log.w(TAG, "보상형 광고 재생 실패: " + error.errorCode + ": " + error.errorMessage)
                    session?.let { it.closed = true; deliver(it) }
                    afterShow()
                }

                override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                    val s = session
                    afterShow()
                    if (s == null) return
                    s.closed = true
                    if (s.rewarded) deliver(s)
                    else mainHandler.postDelayed({ deliver(s) }, REWARD_GRACE_MS) // 늦은 보상 대기
                }
            })
        }
    }

    /** 509 는 코드가 아니라 대시보드(수요) 문제라 사람이 읽을 수 있게 붙여 준다. */
    private fun describeLoadError(error: LevelPlayAdError): String {
        val base = "load " + error.errorCode + ": " + error.errorMessage
        return if (error.errorCode == ERROR_NO_FILL) {
            base + " — 이 광고 단위에 입찰한 네트워크 없음(LevelPlay 대시보드 인스턴스 연결 필요) → 폴백 시도"
        } else base
    }

    /** 재생이 끝나면(닫힘/실패) 다음 광고를 미리 받아 둔다. */
    private fun afterShow() {
        showing = false
        rewardedReady = false
        preload()
    }

    /** LevelPlay 보상 결과를 호출부에 **한 번만** 넘긴다. */
    private fun deliver(s: ShowSession) {
        if (s.delivered) return
        s.delivered = true
        if (session === s) session = null
        s.onResult(s.rewarded)
    }

    /** 지금 바로 보여줄 수 있는 광고원 — LevelPlay 우선. */
    private fun readySource(): Source? = when {
        rewardedAd?.isAdReady() == true -> Source.LEVELPLAY
        AdMobRewarded.isReady -> Source.ADMOB
        else -> null
    }

    private fun refreshReady() {
        rewardedReady = readySource() != null
    }

    /**
     * 보상형 광고 미리 로드 — 잠긴 상세 화면에 들어온 순간 호출해 두면 탭했을 때 기다림이 없다.
     * LevelPlay 초기화가 실패했었다면 여기서 다시 초기화부터 시도하고, LevelPlay 를 쓸 수 없으면 폴백을 데운다.
     */
    fun preload() {
        if (showing) return
        val ctx = appContext ?: return
        if (levelPlayConfigured) {
            if (!initialized) {
                init(ctx)
                // 초기화가 아직 안 끝났는데 사용자가 아이콘을 눌러 기다리는 중이면 폴백도 같이 데운다
                // (앱 시작 때 네트워크가 없어 init 콜백이 영영 안 오는 경우까지 커버).
                if (!initialized && pending != null) loadFallback()
                return
            }
            val ad = rewardedAd
            if (ad != null) {
                if (ad.isAdReady()) {
                    refreshReady()
                    return
                }
                if (loading) return
                loading = true
                ad.loadAd()
                return
            }
        }
        loadFallback()
    }

    /** 폴백(AdMob)을 한 개 받아 둔다. 결과가 나면 기다리던 재생 요청을 정리한다. */
    private fun loadFallback() {
        val ctx = appContext
        if (ctx == null) {
            resolvePending()
            return
        }
        AdMobRewarded.load(ctx) {
            refreshReady()
            resolvePending()
        }
    }

    /**
     * 탭 → 광고. 이미 로드돼 있으면 바로 재생, 아니면 로드를 걸고 최대 [WAIT_FOR_LOAD_MS] 기다려
     * 도착하는 즉시 재생한다(그동안 [onWaiting] 1회 — "광고를 불러오는 중이에요").
     * 키 없음 / 재생 중 / 로드 실패·시간 초과면 [onUnavailable]. 이미 기다리는 요청이 있으면 무시(연타 방지).
     * [onResult] 는 재생했을 때 **항상 1회**: true = 끝까지 봐서 보상, false = 건너뜀/실패.
     */
    fun showRewardedWhenReady(
        activity: Activity,
        onWaiting: () -> Unit,
        onUnavailable: () -> Unit,
        onResult: (rewarded: Boolean) -> Unit,
    ) {
        if (!isConfigured || showing) {
            onUnavailable()
            return
        }
        val source = readySource()
        if (source != null) {
            show(activity, source, onResult)
            return
        }
        if (pending != null) return
        val req = PendingShow(WeakReference(activity), onResult, onUnavailable)
        pending = req
        onWaiting()
        preload() // 초기화 전이면 초기화부터 — 성공 콜백의 preload 가 이어받는다.
        mainHandler.postDelayed({
            if (pending === req) {
                pending = null
                req.onUnavailable()
            }
        }, WAIT_FOR_LOAD_MS)
    }

    /** 로드가 한 단계 끝날 때마다 호출 — 쓸 광고가 생겼으면 기다리던 요청을 재생한다. */
    private fun resolvePending() {
        val req = pending ?: return
        val source = readySource()
        if (source == null) {
            // 아직 LevelPlay 가 초기화/로드 중이면 기다린다(끝내는 건 WAIT_FOR_LOAD_MS 타임아웃).
            if (loading || (levelPlayConfigured && initStarted && !initialized)) return
            pending = null
            req.onUnavailable()
            return
        }
        val activity = req.activity.get()
        pending = null
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            req.onUnavailable()
            return
        }
        show(activity, source, req.onResult)
    }

    /**
     * **디버그 빌드 전용** — LevelPlay 통합 테스트 스위트(네트워크별 연결 상태 + 네트워크별 테스트 광고 로드/재생).
     * "No fill" 원인(네트워크 미설정/비활성)을 찾을 때 쓴다. 잠금 화면 재생 아이콘을 길게 누르면 열린다.
     */
    fun launchTestSuite(context: Context) {
        if (!BuildConfig.DEBUG || !initialized) return
        LevelPlay.launchTestSuite(context)
    }

    private fun show(activity: Activity, source: Source, onResult: (Boolean) -> Unit) {
        showing = true
        rewardedReady = false
        when (source) {
            Source.LEVELPLAY -> {
                session = ShowSession(onResult)
                rewardedAd?.showAd(activity)
            }
            // AdMob 은 보상 → 닫힘 순서가 보장돼 결과를 그대로 넘기면 된다.
            Source.ADMOB -> AdMobRewarded.show(activity) { rewarded ->
                afterShow()
                onResult(rewarded)
            }
        }
    }
}
