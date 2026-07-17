package com.chaminwoo.stary.core.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.core.designsystem.MinSans
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 앱 전역 커스텀 토스트.
 *
 * 안드로이드 12+ 에선 `Toast.setView`(커스텀 뷰)가 무시되므로, 시스템 토스트 대신
 * Compose 오버레이로 직접 그린다. [StaryToastHost] 를 화면 최상단(모든 콘텐츠 위)에
 * 한 번 배치해두면, 어디서든 [StaryToast.show] 만 호출하면 된다.
 *
 * 남색 배경 + 본문 폰트(MinSans) + 밝은 글씨.
 */
object StaryToast {
    data class Event(val message: String, val id: Long)

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event

    /** 메시지 표시. 같은 문자열이어도 매번 새 id 라 다시 뜬다. */
    fun show(message: String) {
        _event.value = Event(message, System.nanoTime())
    }

    /** 기존 `Toast.makeText(context, msg, ...)` 호출 자리에 그대로 끼우기 위한 오버로드. */
    fun show(@Suppress("UNUSED_PARAMETER") context: Context?, message: String) = show(message)
}

private val ToastNavy = Color(0xFF1B2546)      // 남색
private val ToastNavyDeep = Color(0xFF131B36)
private const val ToastVisibleMs = 2200L

@Composable
fun StaryToastHost(modifier: Modifier = Modifier) {
    val event by StaryToast.event.collectAsState()
    var visible by remember { mutableStateOf(false) }
    var shownMessage by remember { mutableStateOf("") }

    LaunchedEffect(event) {
        val e = event ?: return@LaunchedEffect
        shownMessage = e.message
        visible = true
        delay(ToastVisibleMs)
        visible = false
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(animationSpec = tween(260)) { it / 2 } + fadeIn(tween(260)),
            exit = slideOutVertically(animationSpec = tween(220)) { it / 2 } + fadeOut(tween(220)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 96.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .shadow(16.dp, RoundedCornerShape(18.dp), clip = false)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(listOf(ToastNavy, ToastNavyDeep))
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = shownMessage,
                        color = Color(0xFFF3F5FF),
                        fontFamily = MinSans,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
