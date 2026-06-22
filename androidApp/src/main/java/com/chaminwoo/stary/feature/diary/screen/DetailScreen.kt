package com.chaminwoo.stary.feature.diary.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Comment
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.util.ImageCropHelper
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.data.local.DiaryCache
import com.chaminwoo.stary.data.repository.FirebaseDiaryRepository
import com.chaminwoo.stary.data.repository.FirebaseViewedRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import com.chaminwoo.stary.feature.diary.InteractionViewModel
import java.text.SimpleDateFormat
import java.util.Locale

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
    var showFullImage by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    val repository = remember { FirebaseDiaryRepository() }

    // 파장/왜곡 연출은 진입 직전 지도 화면(DiaryMap)에서 처리한다. 세부 화면은 멀쩡하게 표시.

    // 데이터 로드 (보통 캐시에서 즉시 반환).
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

    // 별 색을 화면 강조색으로 사용해 지도 마커와 시각 일관성을 준다.
    val accent = StarStyle.colorOf(currentDiary.starColor)

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
        focusedBorderColor   = accent.copy(alpha = 0.7f),
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor    = MaterialTheme.colorScheme.secondary,
        unfocusedLabelColor  = MaterialTheme.colorScheme.secondary,
        cursorColor          = accent,
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

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // ── 헤더: 사진(또는 placeholder) 위 스크림 + 별/작성자/날짜만 오버레이(제목은 본문으로) ──
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(ImageCropHelper.ASPECT)) {
                if (currentDiary.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = currentDiary.imageUrl, contentDescription = "사진 크게 보기",
                        modifier = Modifier.fillMaxSize().clickable { showFullImage = true },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // 사진이 없으면 템플릿 이미지(image_frame)를 대신 띄운다.
                    Image(
                        painter = painterResource(R.drawable.image_frame),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // 하단 가독성 스크림
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color(0x66000000),
                            1f to MaterialTheme.colorScheme.background
                        )
                    )
                )

                // 오버레이 내용
                Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StarShapeIcon(
                            type = currentDiary.starType, colorIndex = currentDiary.starColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            currentDiary.userName.ifEmpty { "익명" },
                            fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                        )
                        Text("  ·  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                        Text(
                            SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(java.util.Date(currentDiary.createdAt)),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // ── 본문 영역 ──
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {

                Spacer(modifier = Modifier.height(18.dp))

                // 제목(사진 밖으로 분리)
                Text(
                    currentDiary.title, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground, lineHeight = 30.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 본문 카드
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC14181C))
                        .border(
                            1.dp,
                            Brush.linearGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.15f))),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(18.dp)
                ) {
                    Text(
                        currentDiary.content, fontSize = 16.sp, lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isNear) {
                    // 좋아요 + (내 글이면) 수정/삭제
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

                    Text(
                        "댓글 ${comments.size}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // 댓글 입력
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
                                tint = if (commentInput.isNotBlank()) accent else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    comments.forEach { comment ->
                        CommentItem(comment = comment, isMyComment = comment.userId == userId, accent = accent, onDelete = {
                            interactionVm.deleteComment(comment.id)
                            com.chaminwoo.stary.core.ui.StaryToast.show("댓글을 삭제했어요")
                        })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp))
                    }
                } else {
                    // 100m 밖: 상호작용 잠금 안내
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn, contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "100m 이내에서 좋아요와 댓글을 남길 수 있어요",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // 사진 전체화면 뷰어 — 실제 크기로 보며 확대/이동 가능.
        if (showFullImage && currentDiary.imageUrl.isNotEmpty()) {
            FullScreenImageViewer(
                imageUrl = currentDiary.imageUrl,
                onClose = { showFullImage = false }
            )
        }
    }
}

/** 사진을 화면 가득 표시하고 핀치 확대/드래그 이동을 지원하는 오버레이. 탭/뒤로가기로 닫힌다. */
@Composable
private fun FullScreenImageViewer(imageUrl: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    // 확대 상태에서만 이동 허용. 원배율로 돌아오면 위치 리셋.
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClose() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "사진 원본",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale, scaleY = scale,
                    translationX = offset.x, translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )

        // 닫기 버튼
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "닫기", tint = Color.White)
        }
    }
}

@Composable
private fun CommentItem(comment: Comment, isMyComment: Boolean, accent: Color, onDelete: () -> Unit) {
    val dateStr = SimpleDateFormat("MM.dd HH:mm", Locale.KOREA).format(java.util.Date(comment.createdAt))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // 작성자 색 점 아바타
        Box(
            modifier = Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(accent.copy(alpha = 0.8f))
        )
        Spacer(modifier = Modifier.width(10.dp))
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
