package com.chaminwoo.stary.core.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 플랫폼 공용 지오 계산 유틸.
 *
 * 기존 Android 전용 코드는 android.location.Location.distanceBetween() 을 사용했는데,
 * iOS 와 공용으로 쓰기 위해 순수 Kotlin Haversine 구현으로 대체했다.
 */
object GeoUtils {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 두 좌표 사이 거리(미터). */
    fun distanceBetween(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Float {
        val dLat = (lat2 - lat1).toRadians()
        val dLng = (lng2 - lng1).toRadians()
        val a = sin(dLat / 2).pow(2) +
            cos(lat1.toRadians()) * cos(lat2.toRadians()) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (EARTH_RADIUS_M * c).toFloat()
    }

    fun distanceBetween(a: LatLng, b: LatLng): Float =
        distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude)

    private fun Double.toRadians(): Double = this * kotlin.math.PI / 180.0
}
