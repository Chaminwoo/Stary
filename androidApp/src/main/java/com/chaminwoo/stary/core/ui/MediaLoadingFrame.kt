package com.chaminwoo.stary.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.chaminwoo.stary.R

/**
 * 별 사진/영상 로딩 중 밑에 깔아 두는 배경([R.drawable.loading_dipper]) 위에 실제 미디어를 얹고,
 * [loaded] 가 true 가 되면 부드럽게 페이드인한다.
 *
 * ⚠️ loading_dipper 는 **애니메이션 WebP** 라 `painterResource` 로 그릴 수 없다
 * (Compose 는 정지 래스터/벡터만 허용 → IllegalArgumentException 으로 화면 진입 시 크래시).
 * 애니메이션 디코더가 등록된 전역 Coil 로더(StaryApplication)를 통해 그린다.
 */
@Composable
fun MediaLoadingFrame(
    loaded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(320),
        label = "media_fade_in",
    )
    Box(modifier = modifier) {
        AsyncImage(
            model = R.drawable.loading_dipper,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(modifier = Modifier.fillMaxSize().alpha(contentAlpha)) {
            content()
        }
    }
}
