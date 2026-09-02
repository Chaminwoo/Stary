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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Comment
import com.chaminwoo.stary.core.util.rememberCurrentUserName
import com.chaminwoo.stary.core.util.rememberCurrentUserPhoto
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

/** 앱 세션 동안 이미 조회수를 올린 다이어리 id 집합 — 같은 글 재진입 시 중복 카운트를 막는다. */
private object ViewCountSession {
    private val counted = mutableSetOf<String>()
    /** 처음 본 id면 true(이번에 카운트), 이미 본 id면 false. */
    fun markFirstOpen(diaryId: String): Boolean = synchronized(counted) { counted.add(diaryId) }
}

@Composable
fun DetailScreen(
    diaryId: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenProfile: (userId: String, userName: String) -> Unit = { _, _ -> },
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
    val context = LocalContext.current

    // 파장/왜곡 연출은 진입 직전 지도 화면(DiaryMap)에서 처리한다. 세부 화면은 멀쩡하게 표시.

    // 데이터 로드 (보통 캐시에서 즉시 반환).
    LaunchedEffect(diaryId) {
        val loaded = DiaryCache.get(diaryId) ?: repository.getDiaryById(diaryId)
        diary = loaded
        isLoading = false
        val uid = GoogleAuthHelper.currentUserId
        // 조회수는 (1) 본인 글이 아니고 (2) 이번 앱 세션에서 처음 열 때만 1회 증가.
        //   - 본인 글 자가 열람·재진입으로 조회수가 부풀던 문제 + 매 열람 Firestore 쓰기 비용 제거.
        if (loaded != null && loaded.userId != uid && ViewCountSession.markFirstOpen(diaryId)) {
            repository.incrementViewCount(diaryId)
        }
        uid?.let { FirebaseViewedRepository().markViewed(it, diaryId) }
    }

    LaunchedEffect(Unit) {
        diaryViewModel.event.collect { message ->
            if (message == "삭제 완료!") onBack?.invoke()
            if (message == "수정 완료!") diary = repository.getDiaryById(diaryId)
        }
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            com.chaminwoo.stary.core.ui.StarLoadingIndicator()
        }
        return
    }

    val currentDiary = diary
    if (currentDiary == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.detail_load_failed), color = MaterialTheme.colorScheme.secondary, fontSize = 15.sp)
        }
        return
    }

    // 위치가 아직 안 잡혔으면(앱 진입 직후 등) 잠깐 폴링해 채워지면 화면을 갱신한다.
    // (이전엔 위치 null 일 때 거리=MAX 라 "범위 밖"으로 오안내됐다.)
    var locationTick by remember(diaryId) { mutableStateOf(0) }
    LaunchedEffect(diaryId) {
        repeat(12) {
            if (LocationHelper.getCurrentLatLng() != null) return@LaunchedEffect
            kotlinx.coroutines.delay(500)
            locationTick++
        }
    }
    val currentLatLng = remember(locationTick) { LocationHelper.getCurrentLatLng() }
    val locationKnown = currentLatLng != null
    val distance = currentLatLng?.let {
        LocationHelper.distanceBetween(it.latitude, it.longitude, currentDiary.latitude, currentDiary.longitude)
    } ?: Float.MAX_VALUE
    val isNear = distance <= 100f
    val isMyDiary = currentDiary.userId == GoogleAuthHelper.currentUserId
    val userId = GoogleAuthHelper.currentUserId ?: ""
    val userName = GoogleAuthHelper.currentUserName ?: "익명"
    // 비로그인(둘러보기) 상태 — 댓글/좋아요/신고 등 상호작용은 잠그고 안내만 띄운다.
    val isLoggedIn = userId.isNotBlank()
    val requireLogin: () -> Unit = {
        com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.common_login_required))
    }

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
    val allComments by interactionVm.comments.collectAsState()
    // 차단한 사용자의 댓글은 숨긴다.
    val blockedIds by remember(userId) {
        if (userId.isNotBlank()) com.chaminwoo.stary.data.repository.FirebaseModerationRepository().observeBlockedIds(userId)
        else kotlinx.coroutines.flow.flowOf(emptySet())
    }.collectAsState(initial = emptySet())
    val comments = remember(allComments, blockedIds) { allComments.filter { it.userId !in blockedIds } }
    var commentInput by remember { mutableStateOf("") }
    var showReportDialog by remember { mutableStateOf(false) }
    val reportScope = rememberCoroutineScope()
    val reportedMsg = stringResource(R.string.toast_reported)
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // 전송 동작 단일화 — 전송 버튼과 키보드 '보내기' 액션이 같은 경로를 쓴다.
    val submitComment: () -> Unit = {
        if (!isLoggedIn) {
            requireLogin()
        } else if (commentInput.isNotBlank()) {
            interactionVm.addComment(commentInput)
            commentInput = ""
            com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_comment_added))
            keyboardController?.hide()
        }
    }

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
            title = { Text(stringResource(R.string.detail_delete_title), color = MaterialTheme.colorScheme.onBackground) },
            text = { Text(stringResource(R.string.detail_delete_confirm), color = MaterialTheme.colorScheme.secondary) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; diaryViewModel.deleteDiary(currentDiary.id) { onBack?.invoke() } }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.secondary) }
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.detail_edit_title), color = MaterialTheme.colorScheme.onBackground) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it.take(com.chaminwoo.stary.shared.config.StaryConfig.DIARY_TITLE_MAX_LEN) },
                        label = { Text(stringResource(R.string.field_title)) }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp), colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it.take(com.chaminwoo.stary.shared.config.StaryConfig.DIARY_CONTENT_MAX_LEN) },
                        label = { Text(stringResource(R.string.field_content)) }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                        shape = RoundedCornerShape(10.dp), colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showEditDialog = false; diaryViewModel.updateDiary(currentDiary.copy(title = editTitle, content = editContent)) }) {
                    Text(stringResource(R.string.common_save), color = MaterialTheme.colorScheme.onBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.secondary) }
            }
        )
    }

    if (showReportDialog) {
        com.chaminwoo.stary.core.ui.ReportDialog(
            title = stringResource(R.string.report_diary),
            onDismiss = { showReportDialog = false },
            onSubmit = { reasonKey, reasonDetail ->
                showReportDialog = false
                if (userId.isNotBlank()) reportScope.launch {
                    // 관리자가 Console 에서 바로 검토할 수 있도록 다이어리 스냅샷을 함께 등록(체크리스트 28).
                    // "기타" 사유는 신고자가 적은 설명(reasonDetail)도 같이 남긴다.
                    com.chaminwoo.stary.data.repository.FirebaseModerationRepository()
                        .report(
                            userId, "diary", currentDiary.id, currentDiary.userId, reasonKey,
                            mapOf(
                                "targetTitle" to currentDiary.title,
                                "targetContent" to currentDiary.content.take(280),
                                "targetOwnerName" to currentDiary.userName,
                                "targetImageUrl" to currentDiary.imageUrl.ifBlank { currentDiary.videoUrl },
                                "reasonDetail" to reasonDetail.ifBlank { null },
                            )
                        )
                    com.chaminwoo.stary.core.ui.StaryToast.show(reportedMsg)
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // ── 헤더: 사진(또는 placeholder) 위 스크림 + 별/작성자/날짜만 오버레이(제목은 본문으로) ──
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(ImageCropHelper.ASPECT)) {
                // 별이 바뀔 때마다(id 기준) 로딩 배경(loading_dipper)부터 다시 보여준다.
                var mediaLoaded by remember(currentDiary.id) { mutableStateOf(false) }
                when {
                    // 부메랑 움짤(GIF) — 무한 루프 재생. (구버전 mp4 영상은 기존 플레이어 유지)
                    // 사진과 마찬가지로 탭하면 전체화면 뷰어로 열린다.
                    currentDiary.videoUrl.isNotEmpty() && com.chaminwoo.stary.core.ui.isGifUrl(currentDiary.videoUrl) ->
                        com.chaminwoo.stary.core.ui.MediaLoadingFrame(
                            loaded = mediaLoaded,
                            modifier = Modifier.fillMaxSize().clickable { showFullImage = true },
                        ) {
                            com.chaminwoo.stary.core.ui.GifImage(
                                model = currentDiary.videoUrl,
                                modifier = Modifier.fillMaxSize(),
                                onLoaded = { mediaLoaded = true },
                            )
                        }
                    currentDiary.videoUrl.isNotEmpty() -> com.chaminwoo.stary.core.ui.MediaLoadingFrame(
                        loaded = mediaLoaded,
                        modifier = Modifier.fillMaxSize().clickable { showFullImage = true },
                    ) {
                        com.chaminwoo.stary.core.ui.LoopingVideoPlayer(
                            uri = android.net.Uri.parse(currentDiary.videoUrl),
                            modifier = Modifier.fillMaxSize(),
                            muted = true,
                            onFirstFrameRendered = { mediaLoaded = true },
                        )
                    }
                    currentDiary.imageUrl.isNotEmpty() -> com.chaminwoo.stary.core.ui.MediaLoadingFrame(
                        loaded = mediaLoaded,
                        modifier = Modifier.fillMaxSize().clickable { showFullImage = true },
                    ) {
                        AsyncImage(
                            model = currentDiary.imageUrl, contentDescription = stringResource(R.string.cd_view_photo),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onSuccess = { mediaLoaded = true },
                        )
                    }
                    else -> // 사진/영상이 없으면 템플릿 이미지(image_frame)를 대신 띄운다.
                        Image(
                            painter = painterResource(R.drawable.image_frame),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                }

                // 하단 가독성 스크림 (하단 영역만)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )

                // 오버레이 내용
                Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 작성자 id 가 있으면 작성자 영역을 탭해 프로필로 진입할 수 있다.
                        val canOpenProfile = currentDiary.userId.isNotBlank()
                        // 작성자 이름은 저장 시점 스냅샷이 아니라 users/{uid} 의 "현재" 닉네임으로 표시.
                        val authorName =
                            if (canOpenProfile) rememberCurrentUserName(currentDiary.userId, currentDiary.userName)
                            else currentDiary.userName
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = if (canOpenProfile) {
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onOpenProfile(currentDiary.userId, authorName) }
                                    .padding(end = 2.dp)
                            } else Modifier
                        ) {
                            StarShapeIcon(
                                type = currentDiary.starType, colorIndex = currentDiary.starColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                authorName.ifEmpty { stringResource(R.string.common_anonymous) },
                                fontSize = 13.sp, fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                            )
                            // 히든 업적 배지 — 익명 글에는 붙이지 않는다(작성자 은닉 유지).
                            if (canOpenProfile) {
                                com.chaminwoo.stary.core.ui.HiddenStarBadges(
                                    userId = currentDiary.userId,
                                    modifier = Modifier.padding(start = 5.dp),
                                    size = 13.dp,
                                )
                            }
                            if (canOpenProfile) {
                                Icon(
                                    Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.cd_view_profile),
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Text("  ·  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                        val createdStr = remember(currentDiary.createdAt) {
                            SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(java.util.Date(currentDiary.createdAt))
                        }
                        Text(
                            createdStr,
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
                    currentDiary.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
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
                        // 하트 pop + 크리스탈 파편 버스트 + 숫자 롤링(LikeButton). 파편 색은 그 별의 색.
                        com.chaminwoo.stary.core.ui.LikeButton(
                            isLiked = isLiked,
                            count = likeCount,
                            accent = accent,
                            onToggle = {
                                if (!isLoggedIn) requireLogin() else interactionVm.toggleLike()
                            }
                        )
                        ShareDiaryButton(currentDiary)
                        Spacer(modifier = Modifier.weight(1f))
                        if (isMyDiary) {
                            // 수정/삭제는 붙여 둔다 — TextButton 은 최소 폭 58dp 라 둘 사이가 과하게 벌어졌다.
                            CompactTextAction(
                                text = stringResource(R.string.common_edit),
                                color = MaterialTheme.colorScheme.secondary,
                                onClick = { editTitle = currentDiary.title; editContent = currentDiary.content; showEditDialog = true },
                            )
                            CompactTextAction(
                                text = stringResource(R.string.common_delete),
                                color = MaterialTheme.colorScheme.error,
                                onClick = { showDeleteDialog = true },
                            )
                        } else {
                            CompactTextAction(
                                text = stringResource(R.string.report_diary),
                                color = MaterialTheme.colorScheme.secondary,
                                onClick = { if (!isLoggedIn) requireLogin() else showReportDialog = true },
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        stringResource(R.string.detail_comments_count, comments.size), fontSize = 14.sp, fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // 댓글 입력 — 비로그인 시 입력 비활성 + 탭하면 로그인 안내
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = commentInput,
                                onValueChange = { commentInput = it.take(com.chaminwoo.stary.shared.config.StaryConfig.COMMENT_MAX_LEN) },
                                placeholder = { Text(stringResource(R.string.comment_placeholder), color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                enabled = isLoggedIn,
                                shape = RoundedCornerShape(12.dp), colors = fieldColors,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Send
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { submitComment() }),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground)
                            )
                            if (!isLoggedIn) {
                                // 비활성 필드는 터치를 안 받으므로 투명 오버레이로 안내 토스트를 띄운다.
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null
                                        ) { requireLogin() }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { submitComment() },
                            enabled = isLoggedIn && commentInput.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.cd_send),
                                tint = if (isLoggedIn && commentInput.isNotBlank()) accent else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    comments.forEach { comment ->
                        CommentItem(
                            comment = comment,
                            isMyComment = comment.userId == userId,
                            accent = accent,
                            onOpenProfile = {
                                if (comment.userId.isNotBlank()) onOpenProfile(comment.userId, comment.userName)
                            },
                            onDelete = {
                                interactionVm.deleteComment(comment.id)
                                com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_comment_deleted))
                            }
                        )
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
                            if (!locationKnown) stringResource(R.string.detail_locating)
                            else stringResource(R.string.detail_interaction_locked),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // 사진/영상 전체화면 뷰어 — 잘린 헤더가 아니라 원본 전체를 보며 확대/이동 가능.
        // 영상(움짤/mp4)이 있으면 그것을, 없으면 사진을 띄운다(다이어리는 둘 중 하나만 갖는다).
        val fullMediaUrl = currentDiary.videoUrl.ifBlank { currentDiary.imageUrl }
        if (showFullImage && fullMediaUrl.isNotEmpty()) {
            FullScreenMediaViewer(
                mediaUrl = fullMediaUrl,
                // 움짤(GIF)은 Coil 이미지로 재생 — mp4(구버전 영상)만 플레이어가 필요하다.
                isVideo = currentDiary.videoUrl.isNotEmpty() &&
                    !com.chaminwoo.stary.core.ui.isGifUrl(currentDiary.videoUrl),
                onClose = { showFullImage = false }
            )
        }
    }
}

/**
 * 공유 버튼(체크리스트 30) — 탭하면 **공유 카드 편집 화면**(별 위치 드래그/제목/표시 토글/별 크기)이
 * 뜨고, 거기서 인스타 스토리 또는 일반 공유로 내보낸다(ShareCardEditor.kt).
 * ⚠️ DetailScreen 본체에 인라인하면 dex 메서드 레지스터 한계(256)를 넘겨 VerifyError 로
 * 클래스 로드가 거부된다(열람 즉시 크래시) — 반드시 별도 컴포저블로 유지할 것.
 */
/**
 * 인라인 텍스트 액션(수정/삭제/신고) — `TextButton` 대신 쓰는 **좁은** 버전.
 *
 * `TextButton` 은 내부적으로 최소 폭 58dp(`ButtonDefaults.MinWidth`) 를 강제해서
 * "수정"·"삭제" 처럼 짧은 글자에서는 좌우 여백이 크게 남아 두 버튼이 멀찍이 떨어져 보였다.
 * 여기선 글자 폭 + 좌우 8dp 만 차지하고, 터치 높이는 40dp 로 확보한다.
 */
@Composable
private fun CompactTextAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 40.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun ShareDiaryButton(diary: Diary) {
    var editorOpen by remember { mutableStateOf(false) }
    IconButton(onClick = { editorOpen = true }) {
        Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = stringResource(R.string.share_diary),
            tint = MaterialTheme.colorScheme.secondary
        )
    }
    if (editorOpen) {
        ShareCardEditorDialog(diary = diary, onDismiss = { editorOpen = false })
    }
}

/**
 * 사진/움짤/영상을 화면 가득(원본 비율 그대로 Fit) 표시하고 핀치 확대·드래그 이동을 지원하는 오버레이.
 * 탭/뒤로가기로 닫힌다. mp4(구버전 영상)는 VideoView 로 소리와 함께 루프 재생한다.
 */
@Composable
private fun FullScreenMediaViewer(mediaUrl: String, isVideo: Boolean, onClose: () -> Unit) {
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
        val zoomModifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale, scaleY = scale,
                translationX = offset.x, translationY = offset.y
            )
        if (isVideo) {
            com.chaminwoo.stary.core.ui.LoopingVideoPlayer(
                uri = android.net.Uri.parse(mediaUrl),
                modifier = zoomModifier,
                muted = false,
            )
        } else {
            AsyncImage(
                model = mediaUrl,
                contentDescription = stringResource(R.string.cd_photo_original),
                modifier = zoomModifier,
                contentScale = ContentScale.Fit
            )
        }

        // 닫기 버튼
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close), tint = Color.White)
        }
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    isMyComment: Boolean,
    accent: Color,
    onOpenProfile: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateStr = remember(comment.createdAt) { com.chaminwoo.stary.core.util.RelativeTime.format(comment.createdAt) }

    // 작성자 프로필 사진/이름은 users/{uid} 의 "현재" 값으로 표시(저장 시점 스냅샷 아님) — 실시간 갱신.
    val displayName = rememberCurrentUserName(comment.userId, comment.userName)
    val photoUrl = rememberCurrentUserPhoto(comment.userId)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // 작성자 프로필 아바타 (탭 → 작성자 프로필). top 패딩으로 사용자 이름 top 과 맞춤
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onOpenProfile() },
            contentAlignment = Alignment.Center
        ) {
            val url = photoUrl
            if (!url.isNullOrBlank()) {
                // 아바타는 32dp — 원본 대신 저해상도(96px)로 디코드해 빠르게 렌더링.
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(url)
                        .size(96)
                        .build(),
                    contentDescription = stringResource(R.string.cd_profile_photo, displayName.ifBlank { stringResource(R.string.common_user) }),
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    displayName.take(1).uppercase().ifBlank { "?" },
                    color = accent, fontSize = 13.sp, fontWeight = FontWeight.Light
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    displayName, fontSize = 13.sp, fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenProfile() }
                )
                com.chaminwoo.stary.core.ui.HiddenStarBadges(
                    userId = comment.userId,
                    modifier = Modifier.padding(start = 5.dp),
                    size = 12.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(comment.content, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, lineHeight = 20.sp)
        }
        if (isMyComment) {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary) }
        }
    }
}
