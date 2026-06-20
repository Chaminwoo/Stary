package com.chaminwoo.stary.feature.home.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * "지도만 보기" 몰입 오버레이.
 * - 진입 시 하단 중앙에 원형 X 버튼 표시, 몇 초 후 자동으로 사라짐.
 * - X 가 사라진 뒤 같은 자리(하단 중앙)를 탭하거나 뒤로가기를 누르면 X 가 다시 나타난다.
 * - X 가 보일 때 탭하면 [onExit] → 모든 버튼이 보이는 처음 상태로 복귀.
 *
 * 오버레이 전체는 투명이라 지도 조작을 막지 않고, 하단 중앙 탭 영역만 터치를 가져간다.
 */
@Composable
fun MapOnlyOverlay(onExit: () -> Unit) {
    // poke 가 바뀔 때마다 X 를 다시 보이고 3초 자동 숨김 타이머를 재시작
    var poke by remember { mutableIntStateOf(0) }
    var xVisible by remember { mutableStateOf(true) }
    LaunchedEffectPoke(poke) { xVisible = it }

    // 뒤로가기 → X 다시 표시(앱 종료/이탈 방지). 소비함.
    BackHandler { poke++ }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .size(64.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (xVisible) onExit() else poke++ },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = xVisible,
                enter = fadeIn() + scaleIn(initialScale = 0.7f),
                exit = fadeOut() + scaleOut(targetScale = 0.7f),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xCC141414), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close, "지도만 보기 종료",
                        tint = Color.White, modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/** poke 변경마다 X 를 보이고 3초 후 숨기는 타이머. (LaunchedEffect 분리) */
@Composable
private fun LaunchedEffectPoke(poke: Int, setVisible: (Boolean) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(poke) {
        setVisible(true)
        delay(3000)
        setVisible(false)
    }
}
