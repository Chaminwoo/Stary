package com.chaminwoo.stary.feature.diary.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    diaryId: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory())
) {
    var diary by remember { mutableStateOf<Diary?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    val repository = remember { FirebaseDiaryRepository() }

    // 물결 파장 애니메이션 상태 (diaryId 키 → 새 다이어리 진입 시 초기화)
    val ripple1 = remember(diaryId) { Animatable(0f) }
    val ripple2 = remember(diaryId) { Animatable(0f) }
    val ripple3 = remember(diaryId) { Animatable(0f) }
    val contentAlpha = remember(diaryId) { Animatable(0f) }
    val contentScale = remember(diaryId) { Animatable(0.93f) }
    var rippleActive by remember(diaryId) { mutableStateOf(false) }

    LaunchedEffect(diaryId) {
        diary = DiaryCache.get(diaryId) ?: repository.getDiaryById(diaryId)
        isLoading = false
        repository.incrementViewCount(diaryId)
        GoogleAuthHelper.currentUserId?.let { uid ->
            FirebaseViewedRepository().markViewed(uid, diaryId)
        }
    }

    // 다이어리 로드 완료 → 파장 애니메이션 시작
    LaunchedEffect(diary) {
        if (diary == null || rippleActive) return@LaunchedEffect
        rippleActive = true
        // 3개 링 순차 시작 + 콘텐츠 페이드인
        launch { ripple1.animateTo(1f, tween(900, easing = LinearEasing)) }
        kotlinx.coroutines.delay(200)
        launch { ripple2.animateTo(1f, tween(900, easing = LinearEasing)) }
        kotlinx.coroutines.delay(200)
        launch { ripple3.animateTo(1f, tween(900, easing = LinearEasing)) }
        launch { contentAlpha.animateTo(1f, tween(700)) }
        contentScale.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(500) // 마지막 링 완료까지 대기
        rippleActive = false
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

    val currentDiary = diary ?: return

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

    // 콘텐츠 + 파장 오버레이
    Box(modifier = modifier.fillMaxSize()) {
        // 다이어리 본문 (scale + alpha 애니메이션으로 파장과 함께 등장)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = contentScale.value
                    scaleY = contentScale.value
                    alpha = contentAlpha.value
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
                            IconButton(onClick = { interactionVm.toggleLike() }) {
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
                            IconButton(onClick = { interactionVm.addComment(commentInput); commentInput = "" }, enabled = commentInput.isNotBlank()) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send, contentDescription = "전송",
                                    tint = if (commentInput.isNotBlank()) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        comments.forEach { comment ->
                            CommentItem(comment = comment, isMyComment = comment.userId == userId, onDelete = { interactionVm.deleteComment(comment.id) })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        // 파장 오버레이 (다이어리 위에 그려지는 확장 링들)
        if (rippleActive) {
            val rings = listOf(ripple1.value, ripple2.value, ripple3.value)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.maxDimension
                rings.forEach { progress ->
                    if (progress > 0f) {
                        val radius = progress * maxR
                        val alpha = (1f - progress).coerceIn(0f, 1f) * 0.6f
                        val strokeW = (4f * (1f - progress * 0.5f)).coerceAtLeast(1f).dp.toPx()
                        drawCircle(color = Color.White.copy(alpha = alpha), radius = radius, center = center, style = Stroke(width = strokeW))
                    }
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
