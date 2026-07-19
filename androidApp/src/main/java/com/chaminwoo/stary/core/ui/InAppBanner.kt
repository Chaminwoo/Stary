package com.chaminwoo.stary.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.core.designsystem.MinSans
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 앱 전역 인앱 알림 배너 — 채팅 새 메시지·다이어리 알림(좋아요/댓글/친구 새 글)이 도착하면
 * 화면 **상단**에서 슬라이드 인 되는 배너를 띄운다. 탭하면 해당 화면으로 이동, 잠시 후 자동으로 사라진다.
 *
 * [StaryToast](하단 토스트)와 별개의 채널이라 서로 겹치지 않는다.
 * 호스트([InAppBannerHost])를 화면 최상단에 한 번 배치하면, 어디서든 [show] 로 띄울 수 있다.
 */
object InAppBanner {
    enum class Kind { NOTIFICATION, CHAT }

    data class Event(
        val id: Long,
        val title: String,
        val body: String,
        val kind: Kind,
        val onClick: () -> Unit,
    )

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

    // 프로세스 동안 이미 띄운 dedup 키(채팅 "방:updatedAt" / 알림 "notif:id").
    // 와처가 재마운트되어 로컬 dedup 이 리셋되거나 스냅샷이 여러 번 방출돼도, 같은 메시지는 큐에 두 번 들어가지 않는다.
    private val shownKeys = HashSet<String>()

    /**
     * 배너 한 건 추가(큐). 여러 건이 빠르게 와도 순서대로 표시된다.
     * [key] 가 주어지면 프로세스 동안 그 키로 1회만 표시(중복 enqueue 방지).
     */
    fun show(title: String, body: String, kind: Kind, key: String? = null, onClick: () -> Unit) {
        if (key != null && !shownKeys.add(key)) return // 이미 띄운 메시지 → 무시
        _events.value = _events.value + Event(System.nanoTime(), title, body, kind, onClick)
    }

    fun consume(id: Long) {
        _events.value = _events.value.filterNot { it.id == id }
    }
}

private val BannerBg = Color(0xFF18203A)
private val BannerBgDeep = Color(0xFF111733)
private val Mint = Color(0xFF6EE7B7)
private const val BannerVisibleMs = 4000L

@Composable
fun InAppBannerHost(modifier: Modifier = Modifier) {
    val events by InAppBanner.events.collectAsState()
    val current = events.firstOrNull()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(current?.id) {
        if (current == null) { visible = false; return@LaunchedEffect }
        visible = true
        delay(BannerVisibleMs)
        visible = false
        delay(240) // 슬라이드 아웃이 끝난 뒤 큐에서 제거(다음 배너로 교체)
        InAppBanner.consume(current.id)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible && current != null,
            enter = slideInVertically(animationSpec = tween(280)) { -it } + fadeIn(tween(280)),
            exit = slideOutVertically(animationSpec = tween(220)) { -it } + fadeOut(tween(220)),
        ) {
            val e = current ?: return@AnimatedVisibility
            val offsetX = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()

            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .offset {
                            IntOffset(offsetX.value.roundToInt(), 0)
                        }
                        .pointerInput(e.id) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        offsetX.snapTo(offsetX.value + dragAmount)
                                    }
                                },
                                onDragEnd = {
                                    scope.launch {
                                        if (abs(offsetX.value) > 250f) {
                                            visible = false
                                            InAppBanner.consume(e.id)
                                        } else {
                                            offsetX.animateTo(0f)
                                        }
                                    }
                                }
                            )
                        }
                        .fillMaxWidth()
                        .shadow(14.dp, RoundedCornerShape(18.dp), clip = false)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.verticalGradient(listOf(BannerBg, BannerBgDeep)))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                        .clickable {
                            visible = false
                            e.onClick()
                            InAppBanner.consume(e.id)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Mint.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (e.kind) {
                                InAppBanner.Kind.CHAT -> Icons.AutoMirrored.Filled.Chat
                                InAppBanner.Kind.NOTIFICATION -> Icons.Filled.Notifications
                            },
                            contentDescription = null, tint = Mint, modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            e.title.ifBlank { "새 알림" },
                            color = Color(0xFFF3F5FF), fontFamily = MinSans,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                        )
                        if (e.body.isNotBlank()) {
                            Text(
                                e.body, color = Color(0xFFC7CCE6), fontFamily = MinSans,
                                fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}
