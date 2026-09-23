package com.chaminwoo.stary.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.chaminwoo.stary.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * 보상형 광고 **폴백(backfill)** — Google AdMob. [AdsManager] 가 LevelPlay 에서 광고를 못 받았을 때만 쓴다.
 *
 * 왜 필요한가(2026-09-23): LevelPlay 는 로드할 때마다 `509 Mediation No fill` 이었다. 509 는 "이 광고 단위에
 * 입찰한 네트워크가 하나도 없다"는 **대시보드/수요 쪽** 결과라 앱 코드로는 채울 수가 없다(SDK 에 테스트 모드 플래그도 없다).
 * 그래서 잠금 해제 흐름 자체가 막히지 않도록 두 번째 광고원을 둔다.
 *
 * 어떤 광고가 나오나:
 * - **디버그 빌드 + 값 미설정** → 구글 공식 **테스트** 보상형 단위([TEST_REWARDED_UNIT]). 계정·심사·대시보드 없이
 *   항상 채워지므로 해제 흐름(광고 → 영구 해금)을 바로 확인할 수 있다. 수익은 당연히 0.
 * - **`secrets.properties` 에 `ADMOB_REWARDED_AD_UNIT_ANDROID` 를 넣으면** 디버그/릴리즈 모두 그 실제 단위.
 * - **릴리즈 + 값 미설정** → [isConfigured] = false. 폴백은 아예 꺼진다(테스트 광고가 스토어에 나가지 않게).
 *
 * 앱 ID(`com.google.android.gms.ads.APPLICATION_ID`)는 매니페스트 필수값이라 `build.gradle.kts` 의
 * `ADMOB_APP_ID` 플레이스홀더로 항상 주입된다(미설정 시 구글 테스트 App ID).
 */
internal object AdMobRewarded {

    private const val TAG = "AdsManager"

    /** 구글이 공개한 Android 보상형 **테스트** 광고 단위 — 항상 채워진다(개발용, 비밀 아님). */
    private const val TEST_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917"

    /** 실제 주입값이 있으면 그것, 없으면 디버그에서만 테스트 단위, 릴리즈에서는 빈 값(=폴백 꺼짐). */
    private val adUnitId: String
        get() {
            val injected = BuildConfig.ADMOB_REWARDED_AD_UNIT
            return when {
                injected.isNotBlank() && !injected.startsWith("TODO_") -> injected
                BuildConfig.DEBUG -> TEST_REWARDED_UNIT
                else -> ""
            }
        }

    /** 폴백을 쓸 수 있는 빌드인지. */
    val isConfigured: Boolean get() = adUnitId.isNotEmpty()

    /** 지금 바로 보여줄 광고가 손에 있는지. */
    var isReady: Boolean = false
        private set

    /** 마지막 실패 사유(디버그 토스트용). */
    var lastError: String? = null
        private set

    private var ad: RewardedAd? = null
    private var loading = false
    private var sdkStarted = false

    /**
     * 광고 한 개를 받아 둔다. 끝나면(성공/실패/할 일 없음) [onSettled] 를 **항상 한 번** 부른다 —
     * 호출부([AdsManager])가 "기다리던 재생 요청"을 정리할 수 있게.
     */
    fun load(context: Context, onSettled: () -> Unit) {
        if (!isConfigured || isReady || loading) {
            onSettled()
            return
        }
        val app = context.applicationContext
        if (!sdkStarted) {
            sdkStarted = true
            // 초기화는 1회. 완료를 기다리지 않아도 load 는 큐에 쌓였다가 처리된다.
            MobileAds.initialize(app) {}
        }
        loading = true
        RewardedAd.load(
            app, adUnitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    loading = false
                    ad = loaded
                    isReady = true
                    lastError = null
                    onSettled()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    ad = null
                    isReady = false
                    lastError = "AdMob ${error.code}: ${error.message}"
                    Log.w(TAG, "폴백(AdMob) 보상형 로드 실패: $lastError")
                    onSettled()
                }
            },
        )
    }

    /**
     * 받아 둔 광고를 재생한다. [onResult] 는 **항상 한 번**: true = 끝까지 봐서 보상.
     * AdMob 은 `onUserEarnedReward` 가 닫힘보다 먼저 오는 것이 보장돼 별도 유예 시간이 필요 없다(LevelPlay 와 다른 점).
     */
    fun show(activity: Activity, onResult: (Boolean) -> Unit) {
        val current = ad
        if (current == null) {
            onResult(false)
            return
        }
        // 한 번 쓴 광고 객체는 재사용 불가 — 즉시 손에서 놓는다.
        ad = null
        isReady = false
        var rewarded = false
        var delivered = false
        fun finish() {
            if (delivered) return
            delivered = true
            onResult(rewarded)
        }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finish()

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "폴백(AdMob) 재생 실패: ${adError.code}: ${adError.message}")
                finish()
            }
        }
        current.show(activity) { rewarded = true }
    }
}
