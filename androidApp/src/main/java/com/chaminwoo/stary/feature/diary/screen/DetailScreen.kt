package com.chaminwoo.stary.feature.diary.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chaminwoo.stary.core.model.Comment
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.data.local.DiaryCache
import com.chaminwoo.stary.data.repository.FirebaseDiaryRepository
import com.chaminwoo.stary.data.repository.FirebaseViewedRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import com.chaminwoo.stary.feature.diary.InteractionViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * 다이어리 열람 시 파장이 시작될 화면상 위치(0..1 비율).
 * 지도에서 별을 누르면 DiaryMap 이 그 별의 화면 위치를 넣어준다(별에서 파장이 퍼지도록).
 * 그 외 경로(마이페이지/딥링크)는 중앙(0.5,0.5) 기본값.
 */
object DiaryOpenRipple {
    var x: Float = 0.5f
    var y: Float = 0.5f
    fun reset() { x = 0.5f; y = 0.5f }
}

@Composable
fun DetailScreen(
    diaryId: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory())
) {
    // diaryId 키로 묶어 재진입 시 상태가 항상 초기화되도록 한다.
    var diary by remember(diaryId) { mutableStateOf<Diary?>(null) }
    var isLoading by remember(diaryId) { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    val repository = remember { FirebaseDiaryRepository() }

    // reveal = 물결 파장(진입 즉시 1초 재생), contentReveal = 파장 후 콘텐츠 등장.
    val reveal = remember(diaryId) { Animatable(0f) }
    val contentReveal = remember(diaryId) { Animatable(0f) }
    // 파장 시작 위치(별 위치) — 진입 시점 값 캡처 후 홀더는 기본값으로 리셋
    val rippleOriginX = remember(diaryId) { DiaryOpenRipple.x }
    val rippleOriginY = remember(diaryId) { DiaryOpenRipple.y }
    LaunchedEffect(diaryId) { DiaryOpenRipple.reset() }

    // 진입 즉시 파장 1초 재생 → 끝나면 콘텐츠를 초점 복원하며 등장.
    LaunchedEffect(diaryId) {
        reveal.snapTo(0f)
        contentReveal.snapTo(0f)
        reveal.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
        contentReveal.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }

    // 데이터 로드는 파장 재생과 병렬로 진행 (보통 캐시에서 즉시 반환).
    LaunchedEffect(diaryId) {
        diary = DiaryCache.get(diaryId) ?: repository.getDiaryById(diaryId)
        isLoading = false
        repository.incrementViewCount(diaryId)
        GoogleAuthHelper.currentUserId?.let { uid ->
            FirebaseViewedRepository().markViewed(uid, diaryId)
        }
    }

    LaunchedEffect(Unit) {
        diaryViewModel.event.collect { message ->
            if (message == "삭제 완료!") onBack?.invoke()
            if (message == "수정 완료!") diary = repository.getDiaryById(diaryId)
        }
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    val currentDiary = diary
    if (currentDiary == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("다이어리를 불러올 수 없어요", color = MaterialTheme.colorScheme.secondary, fontSize = 15.sp)
        }
        return
    }

    val currentLatLng = LocationHelper.getCurrentLatLng()
    val distance = currentLatLng?.let {
        LocationHelper.distanceBetween(it.latitude, it.longitude, currentDiary.latitude, currentDiary.longitude)
    } ?: Float.MAX_VALUE
    val isNear = distance <= 100f
    val isMyDiary = currentDiary.userId == GoogleAuthHelper.currentUserId
    val userId = GoogleAuthHelper.currentUserId ?: ""
    val userName = GoogleAuthHelper.currentUserName ?: "익명"

    val interactionVm: InteractionViewModel = viewModel(
        key = "interaction_$diaryId",
        factory = InteractionViewModel.factory(
            diaryId = diaryId,
            diaryTitle = currentDiary.title,
            diaryOwnerId = currentDiary.userId,
            userId = userId,
            userName = userName
        )
    )
    val isLiked by interactionVm.isLiked.collectAsState()
    val likeCount by interactionVm.likeCount.collectAsState()
    val comments by interactionVm.comments.collectAsState()
    var commentInput by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = MaterialTheme.colorScheme.onBackground,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor    = MaterialTheme.colorScheme.secondary,
        unfocusedLabelColor  = MaterialTheme.colorScheme.secondary,
        cursorColor          = MaterialTheme.colorScheme.onBackground,
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("다이어리 삭제", color = MaterialTheme.colorScheme.onBackground) },
            text = { Text("정말 삭제할까요? 되돌릴 수 없어요.", color = MaterialTheme.colorScheme.secondary) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; diaryViewModel.deleteDiary(currentDiary.id) { onBack?.invoke() } }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소", color = MaterialTheme.colorScheme.secondary) }
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("다이어리 수정", color = MaterialTheme.colorScheme.onBackground) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTitle, onValueChange = { editTitle = it },
                        label = { Text("제목") }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp), colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editContent, onValueChange = { editContent = it },
                        label = { Text("내용") }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                        shape = RoundedCornerShape(10.dp), colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showEditDialog = false; diaryViewModel.updateDiary(currentDiary.copy(title = editTitle, content = editContent)) }) {
                    Text("저장", color = MaterialTheme.colorScheme.onBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("취소", color = MaterialTheme.colorScheme.secondary) }
            }
        )
    }

    // 파장 진행도(오버레이용)
    val p = reveal.value
    // 콘텐츠 등장 진행도 — 파장(1초) 끝난 뒤 0→1
    val c = contentReveal.value
    // 초점 복원 blur: 물 속에서 떠오르듯 흐림→선명 (API 31+ 에서만 실제 blur, 그 외 무시)
    val blurRadius = (16f * (1f - (c / 0.55f).coerceIn(0f, 1f))).dp
    // 렌즈 펀치: 살짝 확대→원래 크기로 수렴
    val baseScale = 1f + 0.05f * (1f - FastOutSlowInEasing.transform(c.coerceIn(0f, 1f)))
    // 젤리 워블: 등장하며 가로/세로가 어긋나게 출렁이다 감쇠
    val wobble = sin(c * Math.PI.toFloat() * 3f) * 0.018f * (1f - c)

    // 콘텐츠 + 파장 오버레이
    Box(modifier = modifier.fillMaxSize()) {
        // 다이어리 본문 — 표시는 항상 보장하고(왜곡만 애니메이션), 펀치/워블/초점 복원 적용
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
                .graphicsLayer {
                    scaleX = baseScale + wobble
                    scaleY = baseScale - wobble
                    alpha = (c / 0.3f).coerceIn(0f, 1f)
                }
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (currentDiary.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = currentDiary.imageUrl, contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(300.dp), contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) { Text("사진 없음", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp) }
                }

                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Text(currentDiary.title, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground, lineHeight = 30.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(java.util.Date(currentDiary.createdAt)),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary
                        )
                        Text("  ·  ${currentDiary.userName.ifEmpty { "익명" }}", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outline)
                    Text(currentDiary.content, fontSize = 16.sp, lineHeight = 26.sp, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(24.dp))

                    if (isNear) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val willLike = !isLiked
                                interactionVm.toggleLike()
                                com.chaminwoo.stary.core.ui.StaryToast.show(if (willLike) "좋아요를 남겼어요 ♥" else "좋아요를 취소했어요")
                            }) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "좋아요",
                                    tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text("$likeCount", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.weight(1f))
                            if (isMyDiary) {
                                TextButton(onClick = { editTitle = currentDiary.title; editContent = currentDiary.content; showEditDialog = true }) {
                                    Text("수정", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                TextButton(onClick = { showDeleteDialog = true }) {
                                    Text("삭제", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("댓글 ${comments.size}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = commentInput, onValueChange = { commentInput = it },
                                placeholder = { Text("댓글을 입력하세요", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp) },
                                modifier = Modifier.weight(1f), singleLine = true,
                                shape = RoundedCornerShape(12.dp), colors = fieldColors,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                interactionVm.addComment(commentInput); commentInput = ""
                                com.chaminwoo.stary.core.ui.StaryToast.show("댓글을 남겼어요")
                            }, enabled = commentInput.isNotBlank()) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send, contentDescription = "전송",
                                    tint = if (commentInput.isNotBlank()) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        comments.forEach { comment ->
                            CommentItem(comment = comment, isMyComment = comment.userId == userId, onDelete = {
                                interactionVm.deleteComment(comment.id)
                                com.chaminwoo.stary.core.ui.StaryToast.show("댓글을 삭제했어요")
                            })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        // 굴절 파장 오버레이 — 별(다이어리) 위치에서 퍼지는 링(밝은 굴절 가장자리 + 안쪽 그림자 + 넓은 띠)
        if (p < 1f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width * rippleOriginX, size.height * rippleOriginY)
                // 시작점에서 가장 먼 모서리까지 덮도록 반경 계산
                val maxR = max(
                    max(hypot(center.x, center.y), hypot(size.width - center.x, center.y)),
                    max(hypot(center.x, size.height - center.y), hypot(size.width - center.x, size.height - center.y))
                )
                val ringCount = 1
                for (i in 0 until ringCount) {
                    val startF = i * 0.12f
                    val rp = ((p - startF) / (1f - startF)).coerceIn(0f, 1f)
                    if (rp <= 0f || rp >= 1f) continue
                    val radius = rp * maxR
                    val fade = (1f - rp)
                    // 넓고 흐린 굴절 띠 (빛이 휘는 듯한 두꺼운 그라데이션 스트로크)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = fade * 0.18f), Color.Transparent),
                            center = center, radius = radius.coerceAtLeast(1f)
                        ),
                        radius = radius,
                        center = center,
                        style = Stroke(width = (26f * fade).coerceAtLeast(1f).dp.toPx())
                    )
                    // 밝은 굴절 가장자리
                    drawCircle(
                        color = Color.White.copy(alpha = fade * 0.55f),
                        radius = radius, center = center,
                        style = Stroke(width = (3f * fade).coerceAtLeast(0.6f).dp.toPx())
                    )
                    // 안쪽 그림자(굴절 음영) — 가장자리 바로 안쪽
                    drawCircle(
                        color = Color.Black.copy(alpha = fade * 0.22f),
                        radius = (radius - 5.dp.toPx()).coerceAtLeast(0f), center = center,
                        style = Stroke(width = 2f.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentItem(comment: Comment, isMyComment: Boolean, onDelete: () -> Unit) {
    val dateStr = SimpleDateFormat("MM.dd HH:mm", Locale.KOREA).format(java.util.Date(comment.createdAt))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.userName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.width(8.dp))
                Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(comment.content, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, lineHeight = 20.sp)
        }
        if (isMyComment) {
            TextButton(onClick = onDelete) { Text("삭제", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary) }
        }
    }
}
