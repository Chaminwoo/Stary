package com.chaminwoo.stary.feature.diary.screen

import android.content.Context
import org.json.JSONObject

/**
 * 업로드 화면에서 쓰다 나간 글 — 제목·본문·별 모양/색·공개 범위만 기기에 남긴다(사진·움짤은 다시 고른다).
 * 계정마다 따로 둔다(다른 계정으로 로그인하면 보이지 않게). 저장에 성공하면 지운다. iOS `UploadDraftStore` 패리티.
 */
internal object UploadDraftStore {
    data class Draft(
        val title: String,
        val content: String,
        val starType: Int,
        val starColor: Int,
        val visibility: String,
    ) {
        val hasText: Boolean get() = title.isNotBlank() || content.isNotBlank()
    }

    private const val PREFS = "stary_prefs"
    private fun key(uid: String) = "upload_draft_$uid"

    fun load(context: Context, uid: String): Draft? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(uid), null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            Draft(
                title = o.optString("title"),
                content = o.optString("content"),
                starType = o.optInt("starType"),
                starColor = o.optInt("starColor"),
                visibility = o.optString("visibility", "public"),
            )
        }.getOrNull()?.takeIf { it.hasText }
    }

    fun save(context: Context, uid: String, draft: Draft) {
        val o = JSONObject()
            .put("title", draft.title)
            .put("content", draft.content)
            .put("starType", draft.starType)
            .put("starColor", draft.starColor)
            .put("visibility", draft.visibility)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(uid), o.toString()).apply()
    }

    fun clear(context: Context, uid: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key(uid)).apply()
    }
}
