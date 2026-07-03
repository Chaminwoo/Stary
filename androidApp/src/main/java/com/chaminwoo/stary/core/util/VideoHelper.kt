package com.chaminwoo.stary.core.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/** 다이어리 짧은 영상 관련 헬퍼(길이 추출 등). */
object VideoHelper {

    /** 영상 길이(ms) 추출. 실패 시 null. (업로드 전 3초 이내 검증에 사용) */
    fun durationMs(context: Context, uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
