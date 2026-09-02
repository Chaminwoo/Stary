package com.chaminwoo.stary.core.ui

import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chaminwoo.stary.R

/**
 * 앱 공용 로딩 인디케이터 — 기본 [androidx.compose.material3.CircularProgressIndicator] 대신
 * 별 스피너 애니메이션([R.drawable.spinner_128], 128×128 애니메이션 WebP)을 재생한다.
 *
 * ⚠️ 애니메이션 WebP 라 `painterResource` 로는 못 그린다(Compose 는 정지 래스터/벡터만 허용).
 * 애니메이션 디코더가 등록된 전역 Coil 로더(StaryApplication)를 통해 그린다 — [MediaLoadingFrame] 과 동일.
 *
 * @param size 인디케이터 크기. 버튼 안 소형 자리는 18~22dp 권장.
 * @param color 주면 스피너를 이 색으로 틴트한다(밝은 버튼 위 등 대비가 필요할 때).
 *              기본(null)은 원본 아트워크 색 그대로 — 어두운 배경용.
 */
@Composable
fun StarLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    color: Color? = null,
) {
    // 애니메이션 WebP 디코더(ImageDecoderDecoder)는 API 28+ 에만 등록된다(StaryApplication).
    // 그 아래에서는 첫 프레임만 정지로 떠 스피너가 멈춘 것처럼 보이므로 기본 인디케이터로 돌린다.
    if (Build.VERSION.SDK_INT < 28) {
        CircularProgressIndicator(
            modifier = modifier.size(size),
            color = color ?: MaterialTheme.colorScheme.primary,
        )
        return
    }
    AsyncImage(
        model = R.drawable.spinner_128,
        contentDescription = null,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        colorFilter = color?.let { ColorFilter.tint(it) },
    )
}
