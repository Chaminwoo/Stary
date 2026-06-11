package com.chaminwoo.stary.shared.platform

/**
 * KMP expect/actual 예시 — 플랫폼 식별.
 *
 * 향후 위치/인증/지도/이미지 업로드처럼 플랫폼마다 구현이 다른 기능은
 * 이와 같은 expect/actual 또는 commonMain 인터페이스 + 플랫폼 구현 방식으로 확장한다.
 */
expect class Platform() {
    val name: String
}

fun describePlatform(): String = "Running on ${Platform().name}"
