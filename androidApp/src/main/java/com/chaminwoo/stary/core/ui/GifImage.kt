package com.chaminwoo.stary.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * 움직이는 GIF 표시(부메랑 움짤) — GIF 디코더는 앱 전역 로더(StaryApplication.newImageLoader)에
 * 포함되어 있어 싱글턴 로더를 그대로 쓴다(전용 로더의 별도 캐시 낭비 제거). 로컬 File/원격 URL 모두 지원.
 */
@Composable
fun GifImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    onLoaded: () -> Unit = {},
) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onSuccess = { onLoaded() },
    )
}

/** URL 이 부메랑 GIF(움짤)인지 — Storage 경로가 `...gif` 로 끝나는 다운로드 URL 판별. */
fun isGifUrl(url: String): Boolean = url.contains(".gif", ignoreCase = true)
