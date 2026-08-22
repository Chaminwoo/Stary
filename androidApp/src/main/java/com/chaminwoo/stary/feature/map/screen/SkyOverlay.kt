package com.chaminwoo.stary.feature.map.screen

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import com.chaminwoo.stary.core.sky.SkyAlmanac
import kotlinx.coroutines.delay

/** 하늘 상태 재계산 주기(ms) — 태양 고도는 분 단위로 충분히 느리게 변한다. */
private const val SKY_REFRESH_MS = 60_000L

/**
 * 실제 하늘 반영 오버레이 — **여명·황혼**을 지도 위에 그린다.
 *
 * 해가 지평선 근처일 때(뜨기 직전/진 직후) 화면 아래쪽이 따뜻하게 물들어,
 * 늘 똑같던 밤하늘이 시간대에 따라 조금씩 달라 보인다.
 *
 * 계산은 [SkyAlmanac](공용 KMP) — 네트워크/권한 없이 순수 계산이다.
 * 장식 전용이라 터치를 받지 않는다.
 *
 * @param latitude/[longitude] 태양 고도 계산용 현재 좌표(없으면 아무것도 그리지 않는다).
 */
@Composable
fun SkyOverlay(
    latitude: Double?,
    longitude: Double?,
    modifier: Modifier = Modifier,
) {
    var twilight by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(latitude, longitude) {
        while (true) {
            twilight = if (latitude != null && longitude != null) {
                val now = System.currentTimeMillis()
                SkyAlmanac.twilightStrength(SkyAlmanac.sunAltitudeDeg(now, latitude, longitude)).toFloat()
            } else 0f
            delay(SKY_REFRESH_MS)
        }
    }

    Canvas(modifier = modifier) {
        // 여명/황혼: 화면 아래쪽(지평선 방향)에서 따뜻하게 차오른다.
        if (twilight > 0.01f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.55f to Color(0xFF3A1E3B).copy(alpha = 0.16f * twilight),
                    0.85f to Color(0xFF8C3B2E).copy(alpha = 0.22f * twilight),
                    1.0f to Color(0xFFE08A4B).copy(alpha = 0.26f * twilight),
                ),
                blendMode = BlendMode.Screen,
            )
        }
    }
}
