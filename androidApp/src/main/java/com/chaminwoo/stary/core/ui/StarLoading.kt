package com.chaminwoo.stary.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chaminwoo.stary.core.designsystem.StarStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * 앱 공용 로딩 인디케이터 — 기본 [androidx.compose.material3.CircularProgressIndicator] 대신
 * **크리스탈 별이 맥동하고 그 주위를 스파클 2개가 공전**한다(지도 마커 스파클과 같은 언어).
 *
 * - 별 본체는 [StarStyle.drawCrystalFill] 재사용(신규 렌더러 없음) — 앱의 모든 별과 같은 결정 무늬.
 * - 애니메이션은 [rememberInfiniteTransition] **1개**(회전 위상)로 맥동/공전/트윙클을 파생 — 코루틴 1개.
 *
 * @param size 인디케이터 전체 크기(별 + 궤도 포함). 버튼 안 소형 자리는 16~20dp 권장.
 * @param type 별 모양([StarStyle.starPath] 인덱스). 기본 = 4꼭지 스파클.
 * @param colorIndex 별 색 인덱스([StarStyle] 팔레트). 기본 = 화이트(0).
 * @param color 팔레트 밖 색으로 그릴 때(밝은 버튼 위의 어두운 별 등). 주면 [colorIndex] 대신 이 색을 쓴다.
 */
@Composable
fun StarLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    type: Int = 0,
    colorIndex: Int = 0,
    color: Color? = null,
) {
    val transition = rememberInfiniteTransition(label = "star_loading")
    // 하나의 위상(0..1)에서 공전 각도/맥동/트윙클을 모두 파생한다.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val accent = color ?: StarStyle.colorOf(colorIndex)
    Canvas(modifier = modifier.size(size)) {
        val side = this.size.minDimension
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val angle = phase * 2f * Math.PI.toFloat()

        // 본체 — 궤도가 들어갈 여백을 남기고 중앙에 크리스탈 별. 맥동 = 크기 ±7%.
        val pulse = 1f + 0.07f * sin(angle * 2f)
        val starSize = side * 0.56f * pulse
        // 후광(별 색) — 맥동에 맞춰 옅게 숨쉰다.
        drawCircle(
            color = accent.copy(alpha = 0.14f + 0.06f * sin(angle * 2f)),
            radius = starSize * 0.72f,
            center = center,
        )
        drawIntoCanvas { canvas ->
            val left = center.x - starSize / 2f
            val top = center.y - starSize / 2f
            if (color != null) {
                StarStyle.drawCrystalFill(canvas.nativeCanvas, type, listOf(color.toArgb()), left, top, starSize)
            } else {
                StarStyle.drawCrystalFill(canvas.nativeCanvas, type, colorIndex, left, top, starSize)
            }
        }

        // 공전 스파클 2개 — 반대편에서 같은 궤도를 돌며 각자 트윙클. (본체 색을 따라간다)
        val orbit = side * 0.44f
        repeat(2) { i ->
            val a = angle + i * Math.PI.toFloat()
            val p = Offset(center.x + orbit * cos(a), center.y + orbit * sin(a) * 0.62f) // 살짝 눕힌 타원 궤도
            val twinkle = 0.45f + 0.55f * (0.5f + 0.5f * sin(angle * 3f + i * 1.7f))
            drawCircle(accent.copy(alpha = 0.22f * twinkle), radius = side * 0.075f, center = p)
            drawCircle(accent.copy(alpha = 0.9f * twinkle), radius = side * 0.035f, center = p)
        }
    }
}
