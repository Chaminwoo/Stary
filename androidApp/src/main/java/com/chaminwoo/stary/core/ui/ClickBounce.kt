package com.chaminwoo.stary.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * 클릭(탭을 뗀 순간) 시 살짝 커졌다가 원래 크기로 돌아오는 바운스.
 * Initial 패스에서 이벤트를 구경만 하므로 버튼의 클릭 처리(clickable/FAB onClick)와 간섭하지
 * 않는다 — 버튼 modifier 에 붙이기만 하면 된다. 테두리 등 뒤따르는 draw 모디파이어까지 함께
 * 스케일되도록 체인 앞쪽(size 다음, border 앞)에 두는 것을 권장.
 */
fun Modifier.clickBounce(peak: Float = 1.12f): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                if (up != null) scope.launch {
                    scale.snapTo(1f)
                    scale.animateTo(peak, tween(110, easing = FastOutSlowInEasing))
                    scale.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
                }
            }
        }
}
