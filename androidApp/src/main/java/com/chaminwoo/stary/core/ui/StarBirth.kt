package com.chaminwoo.stary.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.chaminwoo.stary.core.designsystem.StarStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * 별 탄생 연출(34-8) — 다이어리 저장이 **성공한 순간** 화면 중앙에서 별이 응축·발광하고
 * 스파클이 퍼진 뒤, 작아지며 지도(내 위치 = 화면 중앙)로 내려앉는다.
 *
 * 전역 오버레이인 이유: 저장 직후 업로드 화면은 곧바로 pop 되므로, 화면 안에서 재생하면 연출이 잘린다.
 * [StarBirthState.trigger] 만 호출하면 [StarBirthHost](MainScreen 최상단)가 **지도 위에서** 이어 재생해
 * "내가 만든 별이 실제로 그 자리에 심긴다"는 체감을 준다. (지도 열람 파장 `DiaryOpenWarp` 과 같은 결.)
 *
 * 실패(업로드 에러)에는 트리거하지 않는다 — 성공 경로에서만 호출할 것.
 */
object StarBirthState {
    /** 재생 중인 별(모양, 색). null = 연출 없음. */
    var pending by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    fun trigger(starType: Int, starColor: Int) {
        pending = starType to starColor
    }

    fun clear() {
        pending = null
    }
}

/** 연출 전체 길이(ms). */
private const val BIRTH_MS = 950

/** 퍼지는 스파클 개수. */
private const val BIRTH_SPARKLES = 9

/**
 * 별 탄생 오버레이 호스트 — 앱 최상단(MainScreen)에 1개만 둔다.
 * 장식 전용: 터치를 받지 않으므로 연출 중에도 지도 조작이 막히지 않는다.
 */
@Composable
fun StarBirthHost() {
    val star = StarBirthState.pending ?: return
    val (type, colorIndex) = star

    val progress = remember(star) { Animatable(0f) }
    LaunchedEffect(star) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(BIRTH_MS, easing = LinearEasing))
        StarBirthState.clear()
    }

    val p = progress.value // Animatable.value 는 상태 읽기 — 프레임마다 리컴포즈된다.
    val accent = StarStyle.colorOf(colorIndex)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // 응축 시작점(화면 중앙보다 살짝 위) → 착지점(중앙 = 지도의 내 위치).
            val startY = h * 0.40f
            val endY = h * 0.50f
            val cx = w * 0.5f

            // ── 1단계(0~0.45): 응축 — 크게 흐릿하던 별이 작아지며 또렷해진다.
            // ── 2단계(0.45~0.62): 발광 링이 퍼진다.
            // ── 3단계(0.62~1): 별이 작아지며 내려앉아 사라진다.
            val condense = (p / 0.45f).coerceIn(0f, 1f)
            val settle = ((p - 0.62f) / 0.38f).coerceIn(0f, 1f)

            val baseSize = 108.dp.toPx()
            val scale = when {
                p < 0.45f -> 2.4f - 1.4f * condense              // 2.4 → 1.0
                p < 0.62f -> 1f + 0.10f * ((p - 0.45f) / 0.17f)  // 살짝 부푼다(발광 순간)
                else -> 1.10f - 0.92f * settle                   // 1.10 → 0.18
            }
            val alpha = when {
                p < 0.45f -> condense
                p < 0.62f -> 1f
                else -> 1f - settle
            }
            val cy = startY + (endY - startY) * settle
            val starSize = baseSize * scale

            // 후광 — 발광 순간 가장 밝다.
            val glowPeak = 1f - kotlin.math.abs(p - 0.53f) / 0.53f
            drawCircle(
                color = accent.copy(alpha = 0.30f * alpha * glowPeak.coerceIn(0f, 1f)),
                radius = starSize * 0.95f,
                center = Offset(cx, cy),
            )

            // 발광 링 — 별이 완성되는 순간 바깥으로 퍼진다.
            if (p in 0.42f..0.85f) {
                val ringT = ((p - 0.42f) / 0.43f).coerceIn(0f, 1f)
                drawCircle(
                    color = accent.copy(alpha = 0.35f * (1f - ringT)),
                    radius = starSize * (0.6f + 1.7f * ringT),
                    center = Offset(cx, cy),
                    style = Stroke(width = 3.dp.toPx() * (1f - ringT) + 0.5f),
                )
            }

            // 스파클 — 별에서 방사형으로 흩어지며 사라진다.
            if (p > 0.40f) {
                val sp = ((p - 0.40f) / 0.55f).coerceIn(0f, 1f)
                repeat(BIRTH_SPARKLES) { i ->
                    val a = (i / BIRTH_SPARKLES.toFloat()) * 6.28318f + 0.35f
                    val dist = starSize * (0.55f + 1.5f * sp)
                    val px = cx + cos(a) * dist
                    val py = cy + sin(a) * dist * 0.9f
                    val r = (3.2f - 2.2f * sp).dp.toPx().coerceAtLeast(0.5f)
                    drawCircle(Color.White.copy(alpha = 0.55f * (1f - sp)), radius = r, center = Offset(px, py))
                }
            }

            // 별 본체 — 앱의 모든 별과 같은 크리스탈 렌더.
            if (alpha > 0.01f && starSize > 1f) {
                drawIntoCanvas { canvas ->
                    StarStyle.drawCrystalFill(
                        canvas.nativeCanvas, type, colorIndex,
                        cx - starSize / 2f, cy - starSize / 2f, starSize,
                        alpha = (255 * alpha).toInt().coerceIn(0, 255),
                    )
                }
            }
        }
    }
}
