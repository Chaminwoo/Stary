package com.chaminwoo.stary.core.util

import android.content.Context
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.geo.GeoUtils
import com.chaminwoo.stary.core.geo.LatLng
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.ui.InAppBanner
import com.chaminwoo.stary.shared.config.StaryConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 근처 미조회 별 발견 알림(체크리스트 33) — 앱 사용 중(foreground) 실제 위치가 갱신될 때
 * [StaryConfig.NEARBY_ALERT_RADIUS_M] 안의 "아직 안 본 남의 별"을 찾아 상단 인앱 배너로 알린다.
 * 탭하면 지도에서 그 별로 포커스(카메라+파장).
 *
 * 빈도 제한:
 *  - 같은 별은 평생 1회만(기기 로컬 영구 기록)
 *  - 하루 상한 [StaryConfig.NEARBY_ALERT_DAILY_LIMIT]
 *  - 알림 간 최소 간격 [StaryConfig.NEARBY_ALERT_MIN_INTERVAL_MS]
 *
 * 백그라운드 지오펜스 확장은 후속(위치 권한 정책 부담) — 체크리스트 참고.
 */
object NearbyStarAlert {

    private const val PREFS = "stary_nearby_alert"
    private const val KEY_ALERTED = "alerted_ids"
    private const val KEY_DAY = "day"
    private const val KEY_DAY_COUNT = "day_count"

    private var lastAlertMs = 0L

    /**
     * 근처 미조회 별 검사 — 조건을 만족하는 가장 가까운 별 1개만 알린다.
     * [diaries] 는 지도에 보이는 목록(공개범위 필터 반영분)을 그대로 받는다.
     */
    fun check(
        context: Context,
        me: LatLng,
        diaries: List<Diary>,
        viewedIds: Set<String>,
        myUserId: String?,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastAlertMs < StaryConfig.NEARBY_ALERT_MIN_INTERVAL_MS) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
        val dayCount = if (prefs.getString(KEY_DAY, "") == today) prefs.getInt(KEY_DAY_COUNT, 0) else 0
        if (dayCount >= StaryConfig.NEARBY_ALERT_DAILY_LIMIT) return

        val alerted = prefs.getStringSet(KEY_ALERTED, emptySet()) ?: emptySet()
        val (diary, distance) = diaries.asSequence()
            .filter { it.id.isNotBlank() && it.id !in alerted && it.id !in viewedIds }
            .filter { it.userId.isNotBlank() && it.userId != myUserId }
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .map { it to GeoUtils.distanceBetween(me.latitude, me.longitude, it.latitude, it.longitude) }
            .filter { it.second <= StaryConfig.NEARBY_ALERT_RADIUS_M }
            .minByOrNull { it.second } ?: return

        lastAlertMs = now
        prefs.edit()
            .putStringSet(KEY_ALERTED, alerted + diary.id)
            .putString(KEY_DAY, today)
            .putInt(KEY_DAY_COUNT, dayCount + 1)
            .apply()

        InAppBanner.show(
            title = context.getString(R.string.nearby_star_title),
            body = context.getString(R.string.nearby_star_body, distance.toInt()),
            kind = InAppBanner.Kind.NOTIFICATION,
            key = "nearby_${diary.id}",
        ) {
            MapFocusState.request(diary.id)
        }
    }
}
