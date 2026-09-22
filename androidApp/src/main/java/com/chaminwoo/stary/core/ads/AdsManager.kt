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
 * 보상형 광고 — **Unity LevelPlay**(미디에이션) 래퍼. 앱에서 광고를 쓰는 **유일한 진입점**.
 *
 * 왜 LevelPlay 인가(2026-09-22 전환): Unity Ads SDK 직접 연동은 2026-01-31 로 수익화 지원이 끝났고, 새 광고 단위는
 * **헤더 비딩**으로만 만들어진다. 비딩 광고 단위는 입찰 결과(adMarkup)를 넘겨줄 미디에이션이 있어야 로드되므로
 * 직접 `UnityAds.load` 는 항상 `adMarkup is missing` 으로 실패했다(실기기 로그). LevelPlay 가 Unity Ads·ironSource Ads
 * 등을 입찰시켜 이긴 광고(플레이어블 포함)를 보여준다.
 *
 * 쓰는 곳: 100m 밖 게시물의 잠금 해제([com.chaminwoo.stary.core.util.DiaryUnlockStore], DetailScreen `DiaryLock.kt`).
 * 흐름: [init] (앱 시작 1회) → [preload] (잠금 화면 진입 시) → [showRewardedWhenReady] (재생 아이콘 탭).
 *   아직 로드 중이면 최대 [WAIT_FOR_LOAD_MS] 기다렸다가 도착하는 즉시 재생한다.
 *
 * - 앱 키 / 보상형 광고 단위 ID 는 `secrets.properties` → BuildConfig 주입(하드코딩 금지).
 *   비어 있으면 [isConfigured] 가 false → 호출부는 아이콘을 탭해도 "지금은 광고를 불러올 수 없어요" 안내.
 * - 보상 판정은 `onAdRewarded` 뿐. ⚠️ LevelPlay 는 `onAdRewarded` 가 `onAdClosed` **뒤에** 올 수도 있다고 명시 →
 *   닫힌 뒤 [REWARD_GRACE_MS] 동안 보상 콜백을 기다렸다가 결과를 한 번만 넘긴다.
 * - LevelPlay 콜백은 메인 스레드 — 그대로 Compose 상태를 건드려도 된다.
 * - 테스트: LevelPlay 대시보드 Testing 에 이 기기를 테스트 기기로 등록하면 실광고 대신 테스트 광고(무효 트래픽 방지).
 */
object AdsManager {

    private const val TAG = "AdsManager"

    /** 앱 키 없이 광고 단위만 있으면 안 되고, 둘 다 있어야 한다. */
    private val appKey: String get() = BuildConfig.LEVELPLAY_APP_KEY
    private val rewardedAdUnitId: String get() = BuildConfig.LEVELPLAY_REWARDED_AD_UNIT

    /** 실제 키가 주입됐는지(placeholder 가 아닌지) — false 면 광고를 띄우지 않는다. */
    val isConfigured: Boolean
        get() = appKey.isNotBlank() && !appKey.startsWith("TODO_") &&
            rewardedAdUnitId.isNotBlank() && !rewardedAdUnitId.startsWith("TODO_")

    /** SDK 초기화 완료 여부(관찰 가능). */
    var initialized by mutableStateOf(false)
        private set

    /** 보상형 광고가 로드돼 바로 보여줄 수 있는 상태인지. */
    var rewardedReady by mutableStateOf(false)
        private set

    /** 광고 재생 중 — 중복 show 호출/버튼 연타 방지. */
    var showing by mutableStateOf(false)
        private set

    /** 마지막 초기화/로드 실패 사유(디버그 안내용). */
    var lastError: String? by mutableStateOf(null)
        private set

    /** 탭했는데 아직 로드 전이면 이만큼 기다린다. */
    private const val WAIT_FOR_LOAD_MS = 8_000L

    /** 광고가 닫힌 뒤 늦게 오는 onAdRewarded 를 기다리는 시간. */
    private const val REWARD_GRACE_MS = 1_500L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var initStarted = false
    private var loading = false
    private var rewardedAd: LevelPlayRewardedAd? = null

    /** 로드를 기다리는 재생 요청(최대 1개). Activity 는 약참조 — 기다리는 사이 화면이 닫혀도 새지 않게. */
    private class PendingShow(
        val activity: WeakReference<Activity>,
        val onResult: (Boolean) -> Unit,
        val onUnavailable: () -> Unit,
    )
    private var pending: PendingShow? = null

    /** 지금 재생 중인 광고 1건의 결과 수집 — 보상/닫힘 순서가 뒤바뀌어도 결과는 한 번만. */
    private class ShowSession(val onResult: (Boolean) -> Unit) {
        var rewarded = false
        var closed = false
        var delivered = false
    }
    private var session: ShowSession? = null

    /** 앱 시작 시 1회(StaryApplication). 키가 없으면 조용히 아무 것도 하지 않는다. 실패하면 다음 [preload] 가 재시도. */
    fun init(context: Context) {
        appContext = context.applicationContext
        if (initStarted || initialized || !isConfigured) return
        initStarted = true
        val request = LevelPlayInitRequest.Builder(appKey).build()
        LevelPlay.init(context.applicationContext, request, object : LevelPlayInitListener {
            override fun onInitSuccess(configuration: LevelPlayConfiguration) {
                initialized = true
                lastError = null
                createRewardedAd()
                preload()
            }

            override fun onInitFailed(error: LevelPlayInitError) {
                initStarted = false // 다음 preload/탭에서 다시 시도
                lastError = "init ${error.errorCode}: ${error.errorMessage}"
                Log.w(TAG, "LevelPlay 초기화 실패: $lastError")
                resolvePending(loaded = false)
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
                    rewardedReady = true
                    lastError = null
                    resolvePending(loaded = true)
                }

                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    loading = false
                    rewardedReady = false
                    lastError = "load ${error.errorCode}: ${error.errorMessage}"
                    Log.w(TAG, "보상형 광고 로드 실패($rewardedAdUnitId): $lastError")
                    resolvePending(loaded = false)
                }

                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {}

                override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
                    val s = session ?: return
                    s.rewarded = true
                    if (s.closed) deliver(s) // 닫힌 뒤에 온 보상
                }

                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                    Log.w(TAG, "보상형 광고 재생 실패: ${error.errorCode}: ${error.errorMessage}")
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

    /** 재생이 끝나면(닫힘/실패) 다음 광고를 미리 받아 둔다. */
    private fun afterShow() {
        showing = false
        rewardedReady = false
        preload()
    }

    /** 보상 결과를 호출부에 **한 번만** 넘긴다. */
    private fun deliver(s: ShowSession) {
        if (s.delivered) return
        s.delivered = true
        if (session === s) session = null
        s.onResult(s.rewarded)
    }

    /**
     * 보상형 광고 미리 로드 — 잠긴 상세 화면에 들어온 순간 호출해 두면 탭했을 때 기다림 없이 재생된다.
     * 초기화가 실패했었다면 여기서 다시 초기화부터 시도한다.
     */
    fun preload() {
        if (!isConfigured) return
        if (!initialized) {
            appContext?.let { init(it) }
            return
        }
        val ad = rewardedAd ?: return
        if (loading || showing) return
        if (ad.isAdReady()) {
            rewardedReady = true
            return
        }
        loading = true
        ad.loadAd()
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
        val ad = rewardedAd
        if (initialized && ad != null && ad.isAdReady()) {
            show(activity, ad, onResult)
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

    /** 기다리던 재생 요청 처리 — 로드 성공이면 재생, 아니면 안내. */
    private fun resolvePending(loaded: Boolean) {
        val req = pending ?: return
        pending = null
        val activity = req.activity.get()
        val ad = rewardedAd
        if (loaded && ad != null && ad.isAdReady() && activity != null &&
            !activity.isFinishing && !activity.isDestroyed
        ) {
            show(activity, ad, req.onResult)
        } else {
            req.onUnavailable()
        }
    }

    private fun show(activity: Activity, ad: LevelPlayRewardedAd, onResult: (Boolean) -> Unit) {
        showing = true
        session = ShowSession(onResult)
        ad.showAd(activity)
    }
}
