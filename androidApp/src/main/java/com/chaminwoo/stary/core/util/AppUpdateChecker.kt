package com.chaminwoo.stary.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * 새 버전 안내 — 앱을 열었을 때 **Play 스토어에 더 새 버전이 올라와 있으면** 팝업으로 알리고
 * "업데이트하러 가기"로 스토어 상세 화면을 바로 연다(2026-09-25 사용자 요청).
 *
 * - 확인은 Play 인앱 업데이트 API(`AppUpdateManager.appUpdateInfo`)로만 한다 — 서버/Remote Config 불필요.
 *   스토어의 versionCode 가 설치본보다 크면 `UPDATE_AVAILABLE`.
 * - ⚠️ **Play 에서 설치한 앱에서만 동작**한다. USB/디버그 설치본은 "업데이트 없음" 또는 오류(-10 등)라 조용히 넘어간다.
 *   실제 확인은 내부 테스트 트랙: 낮은 versionCode 를 설치해 둔 뒤 더 높은 versionCode 를 올리면 뜬다
 *   (스토어 반영까지 수 분~수 시간 걸릴 수 있다). 팝업 모양만 볼 때는 디버그 빌드에서
 *   `adb shell am start -n com.chaminwoo.stary_ios/com.chaminwoo.stary.MainActivity --ez debug_show_update true`.
 * - 프로세스(콜드 스타트)당 1회만 확인한다. "나중에"를 누르면 이번 실행 동안은 다시 안 뜬다.
 * - iOS 는 `Data/AppUpdateChecker.swift`(App Store lookup API) — 같은 문구/흐름.
 */
object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"
    /** 앱 시작 직후 권한 요청·로그인 영상과 겹치지 않도록 잠깐 뒤에 띄운다. */
    private const val PROMPT_DELAY_MS = 1_500L

    /** 안내 팝업 표시 여부 — [com.chaminwoo.stary.core.ui.AppUpdatePromptHost] 가 관찰한다. */
    var showPrompt by mutableStateOf(false)
        private set

    private var checked = false

    /** 스토어에 새 버전이 있으면 잠시 뒤 팝업을 켠다. 실패·해당 없음은 조용히 무시. */
    suspend fun checkOnce(context: Context) {
        if (checked) return
        checked = true
        val available = try {
            val info = AppUpdateManagerFactory.create(context.applicationContext).appUpdateInfo.await()
            info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
        } catch (e: Exception) {
            // Play 에서 설치하지 않은 빌드(디버그/USB)면 여기로 온다 — 정상.
            Log.d(TAG, "업데이트 확인 불가(스토어 설치본이 아님?): ${e.message}")
            false
        }
        if (!available) return
        delay(PROMPT_DELAY_MS)
        showPrompt = true
    }

    fun dismiss() {
        showPrompt = false
    }

    /** 디버그 전용 — 팝업 모양 확인용(MainActivity 의 debug_show_update 인텐트 extra). */
    fun debugShow() {
        showPrompt = true
    }

    /** 이 앱의 플레이 스토어 상세 화면을 연다(스토어 앱 → 없으면 웹). */
    fun openStore(context: Context) {
        val pkg = context.packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
            .setPackage("com.android.vending")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(web) }
        }
    }
}
