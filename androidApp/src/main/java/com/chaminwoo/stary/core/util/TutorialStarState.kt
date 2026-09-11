package com.chaminwoo.stary.core.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chaminwoo.stary.core.geo.LatLng
import kotlin.math.cos

/**
 * 첫 실행 기기 전용 "웰컴 별" — 코치마크([MainOnboardingOverlay]) 마지막 단계가 안내하는,
 * 서버에 쓰지 않는 클라이언트 전용 튜토리얼 다이어리 마커.
 *
 * [OnboardingReplayState] 와 같은 전역 브리지 패턴: 좌표/완료 여부는 `stary_onboarding`
 * SharedPreferences(코치마크·[FirstVisitInfo] 와 같은 파일)에 저장해 **이 기기에서 1회만** 진행되게 한다.
 * - [ensurePlaced] : 실제 위치 fix 가 잡히고 아직 튜토리얼이 필요하면(= [done] 이 false), 그 근방(약 40m)에
 *   좌표를 1회 고정해 저장한다(재실행돼도 같은 자리를 유지).
 * - 별을 탭하면 일반 별처럼 Detail 라우트로 가고, NavGraph 가 이 id 를 보고 게시물형 튜토리얼 화면
 *   (`TutorialStarDetailScreen`)으로 분기한다. 그 화면이 열리는 순간 [markDone] 으로 영구 소비 — 이후 다시 뜨지 않는다.
 */
object TutorialStarState {
    const val DIARY_ID = "tutorial_star"

    // 지도 마커와 게시물 헤더가 같은 값을 쓴다 — 개척 퀘스트 비콘과 같은 "시스템 안내" 톤(앰버골드).
    const val STAR_TYPE = 3
    const val STAR_COLOR = 15
    const val AUTHOR_ID = "stary_tutorial"
    const val AUTHOR_NAME = "STARY"

    private const val PREFS = "stary_onboarding"
    private const val KEY_DONE = "tutorial_star_done"
    private const val KEY_LAT = "tutorial_star_lat"
    private const val KEY_LNG = "tutorial_star_lng"

    /** 튜토리얼이 이미 끝났는지(= 더는 필요 없는지). 코치마크 마지막 문구 분기에도 쓰인다. */
    var done by mutableStateOf(false)
        private set

    /** 배치된 튜토리얼 별 좌표. null 이면 아직 안 놨거나(위치 미확보) 이미 소비됨. */
    var latLng by mutableStateOf<LatLng?>(null)
        private set

    private var restored = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 앱 진입 시 1회 호출 — 저장된 완료/좌표 상태를 복원한다(MusicManager.init 과 같은 패턴). */
    fun restore(context: Context) {
        if (restored) return
        restored = true
        val p = prefs(context)
        done = p.getBoolean(KEY_DONE, false)
        if (!done) {
            val lat = p.getString(KEY_LAT, null)?.toDoubleOrNull()
            val lng = p.getString(KEY_LNG, null)?.toDoubleOrNull()
            if (lat != null && lng != null) latLng = LatLng(lat, lng)
        }
    }

    /** 실제 위치 fix 근방(약 40m)에 튜토리얼 별을 1회 배치. 이미 배치됐거나 끝났으면 아무것도 안 한다. */
    fun ensurePlaced(context: Context, near: LatLng) {
        if (done || latLng != null) return
        val metersToDegLat = 40.0 / 111_320.0
        val metersToDegLng = 40.0 / (111_320.0 * cos(Math.toRadians(near.latitude)).coerceAtLeast(0.01))
        val placed = LatLng(near.latitude + metersToDegLat, near.longitude + metersToDegLng)
        latLng = placed
        prefs(context).edit()
            .putString(KEY_LAT, placed.latitude.toString())
            .putString(KEY_LNG, placed.longitude.toString())
            .apply()
    }

    /** 튜토리얼 게시물이 열릴 때 — 영구 소비(이 기기에서 다시 뜨지 않음). */
    fun markDone(context: Context) {
        done = true
        latLng = null
        prefs(context).edit()
            .putBoolean(KEY_DONE, true)
            .remove(KEY_LAT)
            .remove(KEY_LNG)
            .apply()
    }
}
