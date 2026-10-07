package com.chaminwoo.stary.core.util

import android.content.Context

/** 지도 필터 조합(MainListScreen 필터 상태와 1:1). */
data class MapFilters(
    val unviewedOnly: Boolean = false,
    val friendsOnly: Boolean = false,
    val myOnly: Boolean = false,
    val unlockedOnly: Boolean = false,
    val selectedFriendIds: Set<String> = emptySet(),
    /** null=전체 기간, 0=오늘, N=최근 N일. */
    val periodDays: Int? = null,
)

/**
 * 지도 필터 영속(2026-10-07) — 앱을 껐다 켜도 마지막 필터가 그대로 걸려 있게. (iOS `MapFilterStore.swift` 패리티)
 *
 * **계정별**로 저장한다(키 접두사 = uid, 비로그인 = "guest"). 친구 선택(selectedFriendIds)은 uid 목록이라
 * 다른 계정으로 로그인하면 의미가 없고, 필터 취향도 계정마다 다를 수 있어서다.
 * 다이얼 펼침·피커 표시 같은 일시 UI 상태는 저장하지 않는다.
 */
object MapFilterStore {
    private const val PREFS = "stary_map_filters"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun k(uid: String?, name: String) = "${uid ?: "guest"}_$name"

    fun load(context: Context, uid: String?): MapFilters {
        val p = prefs(context)
        return MapFilters(
            unviewedOnly = p.getBoolean(k(uid, "unviewed"), false),
            friendsOnly = p.getBoolean(k(uid, "friends"), false),
            myOnly = p.getBoolean(k(uid, "mine"), false),
            unlockedOnly = p.getBoolean(k(uid, "unlocked"), false),
            // getStringSet 반환 집합은 수정하면 안 되므로 복사해 쓴다.
            selectedFriendIds = p.getStringSet(k(uid, "picked"), emptySet())?.toSet() ?: emptySet(),
            periodDays = p.getInt(k(uid, "period"), -1).takeIf { it >= 0 },
        )
    }

    fun save(context: Context, uid: String?, f: MapFilters) {
        prefs(context).edit()
            .putBoolean(k(uid, "unviewed"), f.unviewedOnly)
            .putBoolean(k(uid, "friends"), f.friendsOnly)
            .putBoolean(k(uid, "mine"), f.myOnly)
            .putBoolean(k(uid, "unlocked"), f.unlockedOnly)
            .putStringSet(k(uid, "picked"), f.selectedFriendIds.toSet())
            .putInt(k(uid, "period"), f.periodDays ?: -1)
            .apply()
    }
}
