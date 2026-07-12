package com.chaminwoo.stary.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * 작은/중요하지 않은 이미지(아바타·목록 썸네일) 고속 로딩 — 원본 대신 [sizePx] 로
 * 다운샘플 디코드해 CPU/메모리를 아끼고, 같은 URL·크기의 메모리 캐시 재사용을 극대화한다.
 * (디스크 캐시 강제/크로스페이드는 전역 로더(StaryApplication) 튜닝을 따른다)
 */
@Composable
fun ThumbAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    sizePx: Int = 128,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(model)
            .size(sizePx)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
