package com.chaminwoo.stary.core.geo

/**
 * 플랫폼 공용 좌표 타입.
 *
 * 기존에는 네이버맵의 com.naver.maps.geometry.LatLng 를 도메인 좌표 타입으로 직접 사용했으나,
 * 특정 지도 SDK(네이버/구글)에 종속되지 않도록 공용 타입으로 분리했다.
 * 각 플랫폼의 지도 화면에서는 이 타입 <-> 해당 SDK 의 LatLng 로 변환해서 사용한다.
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double
)
