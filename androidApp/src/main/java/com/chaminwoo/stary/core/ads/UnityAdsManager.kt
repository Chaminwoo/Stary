package com.chaminwoo.stary.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chaminwoo.stary.BuildConfig
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions

/**
 * Unity Ads(보상형 광고) 래퍼 — 앱에서 광고를 쓰는 **유일한 진입점**.
 *
 * 쓰는 곳: 100m 밖 게시물의 잠금 해제([com.chaminwoo.stary.core.util.DiaryUnlockStore], DetailScreen `DiaryLock.kt`).
 * 흐름: [init] (앱 시작 1회) → [preload] (잠금 화면 진입 시) → [showRewarded] (크리스탈 자물쇠/재생 아이콘 탭).
 *
 * - 게임 ID/배치 ID 는 `secrets.properties` → BuildConfig 주입(하드코딩 금지).
 *   키가 없으면 [isConfigured] 가 false → 호출부는 아이콘을 탭해도 광고 대신 "지금은 광고를 불러올 수 없어요" 안내.
 * - 보상 판정은 `UnityAdsShowCompletionState.COMPLETED` 뿐이다(건너뛰기(SKIPPED)는 보상 없음).
 * - Unity SDK 콜백은 메인 스레드로 온다 — 그대로 Compose 상태를 건드려도 된다.
 */
object UnityAdsManager {

    private const val TAG = "UnityAdsManager"

    /** 보상형 배치 id(Unity Dashboard 의 Placement ID 와 철자까지 같아야 한다). */
    val rewardedPlacementId: String get() = BuildConfig.UNITY_REWARDED_PLACEMENT

    /** 실제 게임 ID 가 주입됐는지(placeholder 가 아닌지) — false 면 광고를 띄우지 않는다. */
    val isConfigured: Boolean
        get() = BuildConfig.UNITY_GAME_ID.isNotBlank() && !BuildConfig.UNITY_GAME_ID.startsWith("TODO_")

    /** SDK 초기화 완료 여부(관찰 가능 — 초기화 전에는 광고 버튼을 '준비 중'으로 둔다). */
    var initialized by mutableStateOf(false)
        private set

    /** 보상형 광고가 로드돼 바로 보여줄 수 있는 상태인지. */
    var rewardedReady by mutableStateOf(false)
        private set

    /** 광고 재생 중 — 중복 show 호출/버튼 연타 방지. */
    var showing by mutableStateOf(false)
        private set

    private var initStarted = false
    private var loading = false

    /** 앱 시작 시 1회(StaryApplication). 키가 없으면 조용히 아무 것도 하지 않는다. */
    fun init(context: Context) {
        if (initStarted || !isConfigured) return
        initStarted = true
        UnityAds.initialize(
            context.applicationContext,
            BuildConfig.UNITY_GAME_ID,
            BuildConfig.UNITY_ADS_TEST_MODE,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    initialized = true
                    preload()
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?,
                ) {
                    Log.w(TAG, "Unity Ads 초기화 실패: $error / $message")
                }
            }
        )
    }

    /**
     * 보상형 광고 미리 로드 — 잠긴 상세 화면에 들어온 순간 호출해 두면
     * 버튼을 눌렀을 때 기다림 없이 바로 재생된다.
     */
    fun preload() {
        if (!isConfigured || !initialized || rewardedReady || loading) return
        loading = true
        UnityAds.load(
            rewardedPlacementId,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String?) {
                    loading = false
                    rewardedReady = true
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String?,
                    error: UnityAds.UnityAdsLoadError?,
                    message: String?,
                ) {
                    loading = false
                    rewardedReady = false
                    Log.w(TAG, "보상형 광고 로드 실패($placementId): $error / $message")
                }
            }
        )
    }

    /**
     * 보상형 광고 재생. [onResult] 는 **항상 1회** 호출된다.
     *  - `true`  : 끝까지 봤다 → 보상 지급(잠금 해제)
     *  - `false` : 건너뜀/실패 → 보상 없음(호출부가 안내 토스트)
     *
     * 재생이 끝나면 다음 요청을 위해 자동으로 다시 [preload] 한다.
     */
    fun showRewarded(activity: Activity, onResult: (rewarded: Boolean) -> Unit) {
        if (!isConfigured || !initialized || showing) {
            onResult(false)
            return
        }
        showing = true
        rewardedReady = false
        UnityAds.show(
            activity,
            rewardedPlacementId,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String?,
                    error: UnityAds.UnityAdsShowError?,
                    message: String?,
                ) {
                    Log.w(TAG, "보상형 광고 재생 실패($placementId): $error / $message")
                    showing = false
                    preload()
                    onResult(false)
                }

                override fun onUnityAdsShowStart(placementId: String?) {}

                override fun onUnityAdsShowClick(placementId: String?) {}

                override fun onUnityAdsShowComplete(
                    placementId: String?,
                    state: UnityAds.UnityAdsShowCompletionState?,
                ) {
                    showing = false
                    val rewarded = state == UnityAds.UnityAdsShowCompletionState.COMPLETED
                    preload()
                    onResult(rewarded)
                }
            }
        )
    }
}
