package com.chaminwoo.stary.feature.diary.screen

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
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

    LaunchedEffect(diaryId) {
        diary = DiaryCache.get(diaryId) ?: repository.getDiaryById(diaryId)
        isLoading = false
        repository.incrementViewCount(diaryId)
        // 미조회 필터용 열람 기록
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

    // 삭제 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("다이어리 삭제", color = MaterialTheme.colorScheme.onBackground) },
            text = { Text("정말 삭제할까요? 되돌릴 수 없어요.", color = MaterialTheme.colorScheme.secondary) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    diaryViewModel.deleteDiary(currentDiary.id) { onBack?.invoke() }
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소", color = MaterialTheme.colorScheme.secondary)
                }
            }
        )
    }

    // 수정 다이얼로그
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("다이어리 수정", color = MaterialTheme.colorScheme.onBackground) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("제목") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("내용") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    diaryViewModel.updateDiary(currentDiary.copy(title = editTitle, content = editContent))
                }) { Text("저장", color = MaterialTheme.colorScheme.onBackground) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("취소", color = MaterialTheme.colorScheme.secondary)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 이미지
        if (currentDiary.imageUrl.isNotEmpty()) {
            AsyncImage(
                model = currentDiary.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(300.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("사진 없음", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {

            // 제목
            Text(
                text = currentDiary.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 30.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 메타 정보
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
                        .format(java.util.Date(currentDiary.createdAt)),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "  ·  ${currentDiary.userName.ifEmpty { "익명" }}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outline
            )

            // 본문
            Text(
                text = currentDiary.content,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ("위치 보기" 버튼 제거됨 — 100m 밖 다이어리는 지도 마커 클릭 시 도보 길찾기로 연결)

            if (isNear) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))

                // 좋아요 + 수정/삭제 row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { interactionVm.toggleLike() }) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "좋아요",
                            tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        "$likeCount",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (isMyDiary) {
                        TextButton(onClick = {
                            editTitle = currentDiary.title
                            editContent = currentDiary.content
                            showEditDialog = true
                        }) {
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

                // 댓글 헤더
                Text(
                    "댓글 ${comments.size}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))

                // 댓글 입력
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        placeholder = {
                            Text("댓글을 입력하세요", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { interactionVm.addComment(commentInput); commentInput = "" },
                        enabled = commentInput.isNotBlank()
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "전송",
                            tint = if (commentInput.isNotBlank())
                                MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 댓글 목록
                comments.forEach { comment ->
                    CommentItem(
                        comment = comment,
                        isMyComment = comment.userId == userId,
                        onDelete = { interactionVm.deleteComment(comment.id) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun CommentItem(comment: Comment, isMyComment: Boolean, onDelete: () -> Unit) {
    val dateStr = SimpleDateFormat("MM.dd HH:mm", Locale.KOREA)
        .format(java.util.Date(comment.createdAt))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.userName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                comment.content,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 20.sp
            )
        }
        if (isMyComment) {
            TextButton(onClick = onDelete) {
                Text("삭제", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
