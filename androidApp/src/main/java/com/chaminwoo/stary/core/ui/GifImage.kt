package com.chaminwoo.stary.core.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

/**
 * 움직이는 GIF 표시(부메랑 움짤) — Coil 기본 로더는 GIF 를 정지 프레임으로만 그리므로
 * GIF 디코더를 얹은 전용 ImageLoader 로 애니메이션 재생한다. 로컬 File/원격 URL 모두 지원.
 */
@Composable
fun GifImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val gifLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()
    }
    AsyncImage(
        model = model,
        imageLoader = gifLoader,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

/** URL 이 부메랑 GIF(움짤)인지 — Storage 경로가 `...gif` 로 끝나는 다운로드 URL 판별. */
fun isGifUrl(url: String): Boolean = url.contains(".gif", ignoreCase = true)
