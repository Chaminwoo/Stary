package com.chaminwoo.stary.feature.diary.screen

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.LocalTopBarInset
import com.chaminwoo.stary.core.designsystem.MinSans
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.designsystem.StaryColors
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.ui.ScreenBackground
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.ui.StaryButton
import com.chaminwoo.stary.core.ui.StaryButtonKind
import com.chaminwoo.stary.core.ui.StaryDialog
import com.chaminwoo.stary.core.ui.StarySegmented
import com.chaminwoo.stary.core.ui.fadeEdges
import com.chaminwoo.stary.core.ui.rememberBackgroundScroll
import com.chaminwoo.stary.core.util.BoomerangHelper
import com.chaminwoo.stary.core.util.ImageCropHelper
import com.chaminwoo.stary.core.util.ImageUploadHelper
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import com.chaminwoo.stary.feature.profile.Achievements
import com.chaminwoo.stary.feature.profile.StarUnlocks
import com.chaminwoo.stary.feature.profile.rememberUserStats
import com.chaminwoo.stary.feature.starlight.StarlightStore
import com.chaminwoo.stary.shared.config.StaryConfig
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

// 공개 범위 선택지(VisibilityOptions)는 DiaryVisibility.kt 에 있다 — 상세 화면(아이콘·수정)과 공유.
// 별 모양/색 휠·'모두 보기' 시트는 StarPicker.kt, 쓰다 나간 글 보관은 UploadDraft.kt.

/*
 * 화면 구성(2026-10 새 단장) — 테두리 상자 없이 일기장처럼:
 * 첨부(없으면 얇은 한 줄, 고르면 4:3 크롭 프레임) → 별 도장 + 제목(밑줄 한 줄) → 본문(바탕 없이 글만)
 * → 가는 선 → 별 모양/색 휠(이름 옆 '모두 보기', 아래 '가진 것만 보기') → 가는 선 → 공개 범위(면으로 나눈 선택 줄)
 * → 화면 아래 고정 저장 버튼(키보드가 올라오면 그 위로). iOS UploadScreen.swift 와 같은 순서.
 */

@Composable
fun UploadScreen(
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 저장하지 않고 나가기(뒤로 확인 창의 "나가기") — 기본은 저장 후와 같은 pop. */
    onLeave: () -> Unit = onSaveClick,
    diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory())
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var visibilityType by remember { mutableStateOf("public") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    // 부메랑(3초 움짤) — 촬영 화면이 넘겨준 **원본 프레임**을 그대로 들고 있다가 저장 직전에 GIF 로 굽는다.
    // (미리보기 프레임 안에서 사진과 똑같이 위치·확대를 더 조절할 수 있게 하기 위함.) 이미지와 배타.
    var boomerangFrames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    // 촬영 화면에서 정한 크롭 상태(offset 은 프레임 대비 비율) — 프레임 크기를 잰 뒤 그대로 이어받는다.
    var boomerangInit by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) }
    var showBoomerangCapture by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    // 업로드 위치 실패 이유 — null 이 아니면 이유별 안내 창을 띄운다.
    var locationProblem by remember { mutableStateOf<LocationHelper.UploadFix?>(null) }
    // 이번 글의 별 번호 — 저장을 다시 시도해도 같은 번호로 써서 두 번 올라가지 않게(첫 시도는 기기에 쌓여 있다가 올라간다).
    val diaryId = remember { diaryViewModel.newDiaryId() }
    // 마지막으로 저장을 보낸 별 — 서버 응답이 늦으면(SAVE_SLOW) 이걸 그대로(같은 번호·같은 첨부) 다시 보낸다.
    var lastAttempt by remember { mutableStateOf<Diary?>(null) }
    var showSlowSave by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val cameraUri = remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val isLoggedIn = GoogleAuthHelper.currentUserId != null

    // 하루 업로드 제한 판정용 — 내 다이어리 실시간 구독(오늘 올린 개수 계산).
    val myDiaries by diaryViewModel.getMyDiaries(GoogleAuthHelper.currentUserId ?: "").collectAsState()

    // 사진 크롭(고정 비율 프레임 안에서 위치/확대 지정)
    val cropController = remember { CropController() }
    LaunchedEffect(selectedImageUri) {
        val uri = selectedImageUri ?: return@LaunchedEffect
        cropController.setPhoto(null)
        cropController.setPhoto(withContext(Dispatchers.IO) { ImageCropHelper.loadDownsampled(context, uri) })
    }
    // 부메랑 — 촬영 화면에서 정한 위치·확대를 이 화면 프레임 크기에 맞춰 그대로 이어받는다.
    // (프레임 크기를 잰 뒤여야 계산할 수 있으므로 frame 도 키에 넣는다.)
    LaunchedEffect(boomerangFrames, cropController.frame) {
        val init = boomerangInit ?: return@LaunchedEffect
        val bmp = boomerangFrames.firstOrNull() ?: return@LaunchedEffect
        val fw = cropController.frame.width.toFloat()
        val fh = cropController.frame.height.toFloat()
        if (fw <= 0f || fh <= 0f) return@LaunchedEffect
        cropController.minScale = BoomerangHelper.minScaleFor(bmp.width, bmp.height, fw, fh)
        cropController.scale = init.first.coerceIn(cropController.minScale, 4f)
        cropController.offset = Offset(init.second * fw, init.third * fh)
        boomerangInit = null
    }
    val hasMedia = selectedImageUri != null || boomerangFrames.isNotEmpty()
    fun clearMedia() {
        selectedImageUri = null
        boomerangFrames = emptyList()
        boomerangInit = null
        cropController.setPhoto(null)
    }

    // 별 모양/색 — iOS UploadScreen 의 무한 회전 휠(WheelPicker) 구조 패리티(선택 = 항상 정중앙).
    var starType by remember { mutableIntStateOf(0) }
    var starColor by remember { mutableIntStateOf(0) }

    // 업적 해금 상태 — 잠긴 별 모양/색 판정에 사용 (비로그인 시 기본 항목만)
    val unlockedIds: Set<String> =
        if (isLoggedIn) Achievements.unlockedIds(rememberUserStats(GoogleAuthHelper.currentUserId!!))
        else emptySet()

    // 휠 칸 → 별 번호: 내장 0~20 뒤에 별빛 공방(성운 상자)에서 얻은 모양·색을 붙인다.
    val allShapeIds = remember(StarlightStore.ownedShapeTypes) {
        (0 until StarStyle.TYPE_COUNT) + StarlightStore.ownedShapeTypes.filter { it >= StarStyle.TYPE_COUNT }
    }
    val allColorIds = remember(StarlightStore.ownedColorIndices) {
        (0 until StarStyle.COLOR_COUNT) + StarlightStore.ownedColorIndices.filter { it >= StarStyle.COLOR_COUNT }
    }
    // 가진 것만 보기(U4) — 잠긴 칸을 휠에서 뺀다. 지금 고른 칸은 잠겨 있어도 남긴다(통계가 늦게 와도 고른 별이 바뀌지 않게).
    var ownedOnly by remember { mutableStateOf(StarPickerPrefs.ownedOnly(context)) }
    val setOwnedOnly: (Boolean) -> Unit = { ownedOnly = it; StarPickerPrefs.setOwnedOnly(context, it) }
    val shapeIds = if (!ownedOnly) allShapeIds
        else allShapeIds.filter { it == starType || StarUnlocks.lockedShapeAch(it, unlockedIds) == null }
    val colorIds = if (!ownedOnly) allColorIds
        else allColorIds.filter { it == starColor || StarUnlocks.lockedColorAch(it, unlockedIds) == null }

    // '모두 보기' 시트(U1) — 0 = 모양, 1 = 색. 시트 유리가 비출 본문은 아래 hazeSource.
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerTab by remember { mutableIntStateOf(0) }
    val sheetHaze = rememberHazeState()

    // 쓰다 나간 글(S3) — 열 때 한 번 읽어 맨 위에 "이어 쓰기" 줄을 띄운다.
    val draftUid = GoogleAuthHelper.currentUserId ?: "guest"
    var pendingDraft by remember { mutableStateOf(UploadDraftStore.load(context, draftUid)) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) } // 저장에 성공했으면 닫힐 때 임시 저장하지 않는다
    val hasText = title.isNotBlank() || content.isNotBlank()
    val draftNow by rememberUpdatedState(UploadDraftStore.Draft(title, content, starType, starColor, visibilityType))
    // 뒤로·알림으로 이동·앱 전환 어느 쪽으로 나가도 쓴 글은 기기에 남긴다.
    fun keepDraft() {
        if (!saved && draftNow.hasText) UploadDraftStore.save(context, draftUid, draftNow)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { keepDraft() }
    DisposableEffect(Unit) { onDispose { keepDraft() } }
    // 뒤로(시스템·상단바) — 쓴 글이 있으면 한 번 묻는다. 시트·촬영 화면이 떠 있으면 그쪽이 먼저 닫힌다.
    BackHandler(enabled = hasText && !isUploading && !pickerOpen && !showBoomerangCapture) { showLeaveConfirm = true }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) { selectedImageUri = cameraUri.value; boomerangFrames = emptyList() }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedImageUri = it; boomerangFrames = emptyList() }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val tmpFile = java.io.File.createTempFile("diary_img_", ".jpg", context.cacheDir)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmpFile)
            cameraUri.value = uri; cameraLauncher.launch(uri)
        } else com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_camera_permission))
    }

    fun launchCamera() {
        val perm = android.Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
            val f = java.io.File.createTempFile("diary_img_", ".jpg", context.cacheDir)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            cameraUri.value = uri; cameraLauncher.launch(uri)
        } else permissionLauncher.launch(perm)
    }

    if (showImageSourceDialog) {
        StaryDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text(stringResource(R.string.upload_add_photo)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showImageSourceDialog = false; launchCamera() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.CameraAlt, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.upload_take_photo), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = { showImageSourceDialog = false; galleryLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Photo, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.upload_pick_gallery), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    }
                    // 부메랑(3초 움짤) — 파일 선택 대신 커스텀 촬영 화면으로
                    TextButton(onClick = { showImageSourceDialog = false; showBoomerangCapture = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Videocam, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.upload_capture_boomerang), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { StaryButton(stringResource(R.string.common_cancel), { showImageSourceDialog = false }, kind = StaryButtonKind.Ghost, compact = true) }
        )
    }

    if (showLeaveConfirm) {
        StaryDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(stringResource(R.string.upload_leave_title)) },
            text = { Text(stringResource(R.string.upload_leave_msg)) },
            confirmButton = {
                StaryButton(stringResource(R.string.upload_leave_stay), { showLeaveConfirm = false }, compact = true)
            },
            dismissButton = {
                StaryButton(
                    stringResource(R.string.upload_leave_exit),
                    { showLeaveConfirm = false; keepDraft(); onLeave() },
                    kind = StaryButtonKind.Ghost, compact = true,
                )
            },
        )
    }

    // 개척 퀘스트(체크리스트 32) — 저장 성공 시 이 좌표로 선점 시도(자체 스코프라 네비게이션에 안 죽음).
    var pioneerClaimTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // 일일 퀘스트 "사진·영상을 담아 별 남기기" 판정용 — 저장 직전에 정한다.
    var savedWithMedia by remember { mutableStateOf(false) }
    // 일일 퀘스트 "다른 별과 겹치게 남기기" — 저장 직전 위치가 이미 있는 별(머지 반경 안)과 겹치는지.
    var savedStacked by remember { mutableStateOf(false) }
    val allDiaries by diaryViewModel.diaries.collectAsState()
    // LaunchedEffect(Unit) 의 람다는 첫 컴포지션 값을 캡처하므로, 저장 직전에 고른 별을 쓰려면
    // 최신값 참조가 필요하다(선택을 바꾼 뒤 저장해도 그 별로 연출되게).
    val starTypeRef = rememberUpdatedState(starType)
    val starColorRef = rememberUpdatedState(starColor)
    LaunchedEffect(Unit) {
        com.chaminwoo.stary.core.util.StaryAnalytics.log("upload_open", "logged_in" to isLoggedIn)
        diaryViewModel.event.collect { msg ->
            isUploading = false // 저장 결과가 올 때까지 버튼을 잠가 둔다(예전엔 바로 풀려 여러 번 누르면 별이 겹쳐 올라갔다)
            if (msg == com.chaminwoo.stary.feature.diary.DiaryEvent.SAVE_SLOW) {
                uploadFail("save_slow")
                showSlowSave = true
                return@collect
            }
            com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(msg.messageRes))
            if (msg == com.chaminwoo.stary.feature.diary.DiaryEvent.SAVE_FAILED) uploadFail("save")
            if (msg == com.chaminwoo.stary.feature.diary.DiaryEvent.SAVED) {
                com.chaminwoo.stary.core.util.StaryAnalytics.log("upload_success", "media" to savedWithMedia)
                // 올렸으니 쓰다 둔 글은 지운다(이 화면이 닫힐 때 다시 남기지도 않는다).
                saved = true
                UploadDraftStore.clear(context, draftUid)
                pioneerClaimTarget?.let { (la, ln) ->
                    com.chaminwoo.stary.feature.profile.PioneerClaimHelper.attemptClaim(context, la, ln)
                }
                // 별 탄생 연출(34-8) — 저장 성공에서만. 화면이 pop 된 뒤 지도 위에서 이어 재생된다
                // (전역 오버레이 StarBirthHost — MainScreen).
                com.chaminwoo.stary.core.ui.StarBirthState.trigger(starTypeRef.value, starColorRef.value)
                // 내 별에 반응이 올 수 있으니 알림을 켤지 물을 차례(연출이 끝난 뒤 지도에서 — PermissionAsk).
                com.chaminwoo.stary.core.ui.PermissionAsk.requestNotificationAsk()
                // 일일 퀘스트 — 올리기(시간대 퀘스트도 이 이벤트, 올린 시각으로 판정) + 사진/영상.
                com.chaminwoo.stary.feature.quest.QuestStore.report(com.chaminwoo.stary.feature.quest.QuestEvent.UPLOAD)
                if (savedWithMedia) com.chaminwoo.stary.feature.quest.QuestStore.report(com.chaminwoo.stary.feature.quest.QuestEvent.UPLOAD_MEDIA)
                if (savedStacked) com.chaminwoo.stary.feature.quest.QuestStore.report(com.chaminwoo.stary.feature.quest.QuestEvent.UPLOAD_STACK)
                onSaveClick()
            }
        }
    }

    val submit: () -> Unit = submit@{
        if (title.isBlank()) { uploadFail("no_title"); com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_title_required)); return@submit }
        // 본문이 비면 올리지 않는다(최소 1자) — 공백·줄바꿈만 있는 것도 빈 것으로 본다. iOS 와 같은 규칙.
        if (content.isBlank()) { uploadFail("no_content"); com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_content_required)); return@submit }
        // 부적절한 표현 필터(App Store 1.2) — 제목·본문 중 하나라도 걸리면 올리지 않는다.
        if (com.chaminwoo.stary.core.util.ContentFilter.anyObjectionable(title, content)) { uploadFail("filter"); com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.content_blocked)); return@submit }
        // 하루 업로드 제한(로그인 사용자 기준). 오늘 로컬 자정 이후 내가 올린 개수로 선차단.
        if (isLoggedIn) {
            val startOfDay = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val todayCount = myDiaries.count { it.createdAt >= startOfDay }
            if (todayCount >= StaryConfig.DAILY_UPLOAD_LIMIT) {
                uploadFail("daily_limit")
                com.chaminwoo.stary.core.ui.StaryToast.show(
                    context.getString(R.string.upload_daily_limit, StaryConfig.DAILY_UPLOAD_LIMIT)
                )
                return@submit
            }
        }
        StarUnlocks.lockedShapeAch(starType, unlockedIds)?.let {
            com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_unlock_achievement, com.chaminwoo.stary.core.util.LocalizedNames.title(context, it.id, it.name))); return@submit
        }
        StarUnlocks.lockedColorAch(starColor, unlockedIds)?.let {
            com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_unlock_achievement, com.chaminwoo.stary.core.util.LocalizedNames.title(context, it.id, it.name))); return@submit
        }
        coroutineScope.launch {
            isUploading = true
            // 실제 위치를 못 잡으면(권한 없음·위치 꺼짐·모의 위치) 올리지 않는다 — 예전엔 (0,0) 으로 저장돼
            // 지도에서 안 보이는 별이 생겼다(지도는 (0,0) 을 거른다). 2026-10-23 완화: 대략적인 위치·최근 위치도 쓰고,
            // 실패하면 토스트 대신 이유별 안내(권한 허용 / 위치 켜기 / 다시 시도)를 띄운다 — 쓴 글은 그대로 남는다.
            val fix = LocationHelper.locateForUpload(context)
            if (fix !is LocationHelper.UploadFix.Ok) {
                uploadFail(
                    when (fix) {
                        LocationHelper.UploadFix.NoPermission -> "no_location_permission"
                        LocationHelper.UploadFix.LocationOff -> "no_location_off"
                        LocationHelper.UploadFix.Mock -> "no_location_mock"
                        else -> "no_location"
                    }
                )
                locationProblem = fix
                isUploading = false; return@launch
            }
            val lat = fix.latitude
            val lng = fix.longitude
            // 작성자 이름은 서버에서 확인한 닉네임 — 기기 첫 실행 직후 구글 기본 이름이 박히지 않게(CLAUDE.md §2.6).
            // 첨부 업로드 전에 확인해 Storage 에 주인 없는 파일이 남지 않게 한다.
            val uName = if (!isLoggedIn) "익명" else GoogleAuthHelper.requireConfirmedName() ?: run {
                uploadFail("profile_not_ready")
                isUploading = false; return@launch
            }
            // 첨부는 영상/이미지 중 하나(배타). 영상이 있으면 영상 우선.
            var imageUrl = ""
            var videoUrl = ""
            when {
                boomerangFrames.isNotEmpty() -> {
                    // 부메랑 움짤(GIF) — 지금 프레임에서 보이는 그대로 잘라 인코딩한 뒤
                    // videoUrl 필드에 저장(스키마 유지, .gif 로 판별).
                    val gif = withContext(Dispatchers.Default) {
                        runCatching {
                            val cropped = BoomerangHelper.cropFrames(
                                boomerangFrames,
                                cropController.frame.width.toFloat(),
                                cropController.frame.height.toFloat(),
                                cropController.scale, cropController.offset.x, cropController.offset.y,
                            )
                            BoomerangHelper.encodeToFile(context, cropped)
                        }.getOrNull()
                    }
                    if (gif == null) {
                        uploadFail("gif_encode")
                        com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.boomer_failed))
                        isUploading = false; return@launch
                    }
                    val result = ImageUploadHelper.uploadGifResult(context, gif)
                    if (!result.isSuccess) {
                        uploadFail("media_upload")
                        com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_image_upload_failed, result.error ?: ""))
                        isUploading = false; return@launch
                    }
                    videoUrl = result.url!!
                }
                selectedImageUri != null -> {
                    // 크롭 프레임 상태로 잘라낸 이미지를 업로드(실패 시 원본으로 폴백).
                    val uploadUri = withContext(Dispatchers.IO) {
                        val bmp = cropController.first
                        if (bmp != null && cropController.frame.width > 0) {
                            ImageCropHelper.cropToFile(
                                context, bmp,
                                cropController.frame.width.toFloat(), cropController.frame.height.toFloat(),
                                cropController.scale, cropController.offset.x, cropController.offset.y
                            )
                        } else null
                    } ?: selectedImageUri!!
                    val result = ImageUploadHelper.uploadImageResult(context, uploadUri)
                    if (!result.isSuccess) {
                        uploadFail("media_upload")
                        com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_image_upload_failed, result.error ?: ""))
                        isUploading = false; return@launch
                    }
                    imageUrl = result.url!!
                }
            }
            pioneerClaimTarget = lat to lng // 저장 성공 이벤트에서 개척 선점 시도(체크리스트 32)
            savedWithMedia = imageUrl.isNotEmpty() || videoUrl.isNotEmpty()
            savedStacked = allDiaries.any {
                com.chaminwoo.stary.core.util.LocationHelper.distanceBetween(lat, lng, it.latitude, it.longitude) <=
                    com.chaminwoo.stary.shared.config.StaryConfig.STAR_MERGE_RADIUS_M
            }
            val toSave = Diary(
                id = diaryId,
                title = title, content = content, imageUrl = imageUrl, videoUrl = videoUrl,
                userId = GoogleAuthHelper.currentUserId ?: "",
                userName = uName,
                latitude = lat, longitude = lng,
                starType = starType, starColor = starColor,
                visibilityType = visibilityType
            )
            lastAttempt = toSave
            diaryViewModel.saveDiary(toSave) // isUploading 은 저장 결과 이벤트에서 푼다
        }
    }

    // 서버 응답이 20초 넘게 없음 — 쓴 글·첨부는 그대로 두고, 같은 별(번호·첨부)로 다시 보낼 수 있게.
    if (showSlowSave) {
        StaryDialog(
            onDismissRequest = { showSlowSave = false },
            title = { Text(stringResource(R.string.upload_slow_title)) },
            text = { Text(stringResource(R.string.upload_slow_msg)) },
            confirmButton = {
                StaryButton(stringResource(R.string.upload_loc_retry), compact = true, onClick = {
                    showSlowSave = false
                    lastAttempt?.let { isUploading = true; diaryViewModel.saveDiary(it) }
                })
            },
            dismissButton = {
                StaryButton(stringResource(R.string.common_confirm), { showSlowSave = false }, kind = StaryButtonKind.Ghost, compact = true)
            },
        )
    }

    // 업로드 위치 실패 안내 — 이유별 행동(권한 요청 / 위치 설정 / 다시 시도).
    val locPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) {
            // 대략적인 위치로 시작했던 추적이면 새 권한(정확한 위치)에 맞춰 다시 시작
            LocationHelper.stopContinuousUpdates(context)
            LocationHelper.startContinuousUpdates(context)
            submit()
        } else {
            // "다시 묻지 않음"이면 시스템 창이 안 뜬다 → 앱 설정 화면에서 직접 허용하게
            val act = context.findActivity()
            if (act != null && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(act, android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }
            }
        }
    }
    locationProblem?.let { problem ->
        val (titleRes, msgRes, actionRes) = when (problem) {
            LocationHelper.UploadFix.NoPermission -> Triple(R.string.upload_loc_perm_title, R.string.upload_loc_perm_msg, R.string.upload_loc_perm_btn)
            LocationHelper.UploadFix.LocationOff -> Triple(R.string.upload_loc_off_title, R.string.upload_loc_off_msg, R.string.upload_loc_off_btn)
            LocationHelper.UploadFix.Mock -> Triple(R.string.upload_loc_mock_title, R.string.upload_loc_mock_msg, R.string.upload_loc_retry)
            else -> Triple(R.string.upload_loc_none_title, R.string.upload_loc_none_msg, R.string.upload_loc_retry)
        }
        StaryDialog(
            onDismissRequest = { locationProblem = null },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(msgRes)) },
            confirmButton = {
                StaryButton(stringResource(actionRes), compact = true, onClick = {
                    locationProblem = null
                    when (problem) {
                        LocationHelper.UploadFix.NoPermission -> locPermLauncher.launch(
                            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                        LocationHelper.UploadFix.LocationOff -> runCatching {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                        else -> submit()
                    }
                })
            },
            dismissButton = {
                StaryButton(stringResource(R.string.common_cancel), { locationProblem = null }, kind = StaryButtonKind.Ghost, compact = true)
            },
        )
    }

    val bgScroll = rememberBackgroundScroll()
    Box(modifier = modifier.nestedScroll(bgScroll).fillMaxSize()) {
        // 시트 유리가 비출 본문(배경 + 입력) — 시트는 이 Box 의 바깥(형제)에 둔다.
        Box(Modifier.fillMaxSize().hazeSource(sheetHaze)) {
            ScreenBackground(R.drawable.upload_bg, darken = 0.82f, scroll = bgScroll)

            // 아래 여백은 한 번만 — 키보드가 있으면 키보드 높이, 없으면 내비게이션 바(ChatScreen 입력 바와 같은 방식).
            // 저장 버튼은 스크롤 영역 **밖** 아래 칸이라 키보드 위로 따라 올라오고, 입력 중인 줄을 가리지 않는다.
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .fadeEdges(bottom = 28.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = LocalTopBarInset.current)
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp)
                ) {
                    // 쓰다 나간 글 — 이어 쓰거나 지운다(고르기 전엔 아무것도 덮어쓰지 않는다).
                    // 새로 쓰기 시작하면 숨긴다 — 그 상태에서 눌러 지금 쓴 글이 옛 글로 덮이지 않게.
                    AnimatedVisibility(pendingDraft != null && !hasText) {
                        DraftStrip(
                            onRestore = {
                                pendingDraft?.let { d ->
                                    title = d.title; content = d.content
                                    starType = d.starType; starColor = d.starColor
                                    visibilityType = d.visibility
                                }
                                pendingDraft = null
                            },
                            onDiscard = {
                                UploadDraftStore.clear(context, draftUid)
                                pendingDraft = null
                            },
                        )
                    }

                    // ── 첨부 — 없으면 얇은 한 줄, 고르면 상세 화면과 같은 고정 비율 크롭 프레임 ──
                    if (hasMedia) {
                        // 이 프레임이 곧 '잘릴 영역' — 드래그로 위치, 두 손가락으로 확대/축소(사진·3초 움짤 공용).
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(ImageCropHelper.ASPECT)
                                .clip(RoundedCornerShape(16.dp))
                                .background(FieldFill)
                        ) {
                            ImageCropFrame(
                                controller = cropController,
                                modifier = Modifier.matchParentSize(),
                                // 크롭 결과는 저장 시작 시점에 이미 확정되므로, 업로드 중 드래그를 허용하면
                                // 화면만 움직이고 실제 올라가는 그림은 그대로라 어긋난다.
                                enabled = !isUploading,
                            )
                            // 첨부 지우기 — iOS clearMediaButton 패리티.
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .clickable(enabled = !isUploading) { clearMedia() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.upload_clear_media), tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.upload_crop_hint), color = StaryColors.Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            // 업로드 중에는 사진 재선택 잠금.
                            TextButton(onClick = { showImageSourceDialog = true }, enabled = !isUploading) {
                                Text(stringResource(R.string.upload_reselect), color = StaryColors.Accent.copy(alpha = if (isUploading) 0.5f else 1f), fontSize = 13.sp)
                            }
                        }
                    } else {
                        AddMediaRow(enabled = !isUploading) { showImageSourceDialog = true }
                    }

                    Spacer(Modifier.height(26.dp))

                    // ── 제목 — 이 글이 될 별을 도장처럼 앞에 찍는다(휠을 돌리면 같이 바뀐다) ──
                    // 업로드 중 수정 잠금 — 저장에 쓰인 값과 화면이 어긋나지 않게.
                    TitleField(
                        value = title,
                        onValueChange = { title = it.take(StaryConfig.DIARY_TITLE_MAX_LEN) },
                        starType = starType, starColor = starColor,
                        enabled = !isUploading,
                    )
                    Spacer(Modifier.height(14.dp))
                    BodyField(
                        value = content,
                        onValueChange = { content = it.take(StaryConfig.DIARY_CONTENT_MAX_LEN) },
                        enabled = !isUploading,
                    )

                    Hairline(Modifier.padding(top = 18.dp, bottom = 20.dp))

                    // ── 별 모양 휠 — 이름 옆 '모두 보기'(격자 시트) ──
                    SectionHeader(stringResource(R.string.upload_star_shape), enabled = !isUploading) { pickerTab = 0; pickerOpen = true }
                    Spacer(Modifier.height(6.dp))
                    StarWheelPicker(
                        count = shapeIds.size,
                        selection = shapeIds.indexOf(starType).coerceAtLeast(0),
                        onSelect = { starType = shapeIds[it] },
                        itemSize = 40.dp,
                        // 업로드 진행 중에는 별 모양도 공개 범위와 마찬가지로 바꿀 수 없게 잠근다.
                        enabled = !isUploading,
                        onLanded = { pos ->
                            StarUnlocks.lockedShapeAch(shapeIds[pos], unlockedIds)?.let {
                                com.chaminwoo.stary.core.ui.StaryToast.show(
                                    context.getString(R.string.toast_unlock_achievement, com.chaminwoo.stary.core.util.LocalizedNames.title(context, it.id, it.name))
                                )
                            }
                        }
                    ) { pos ->
                        val type = shapeIds[pos]
                        val locked = StarUnlocks.lockedShapeAch(type, unlockedIds) != null
                        Box(contentAlignment = Alignment.Center) {
                            StarShapeIcon(
                                type = type, colorIndex = starColor,
                                modifier = Modifier.size(40.dp).alpha(if (locked) 0.25f else 1f)
                            )
                            if (locked) {
                                Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.cd_locked),
                                    tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // ── 별 색 휠 ──
                    SectionHeader(stringResource(R.string.upload_star_color), enabled = !isUploading) { pickerTab = 1; pickerOpen = true }
                    Spacer(Modifier.height(6.dp))
                    StarWheelPicker(
                        count = colorIds.size,
                        selection = colorIds.indexOf(starColor).coerceAtLeast(0),
                        onSelect = { starColor = colorIds[it] },
                        itemSize = 32.dp,
                        // 업로드 진행 중에는 별 색도 바꿀 수 없게 잠근다.
                        enabled = !isUploading,
                        onLanded = { pos ->
                            StarUnlocks.lockedColorAch(colorIds[pos], unlockedIds)?.let {
                                com.chaminwoo.stary.core.ui.StaryToast.show(
                                    context.getString(R.string.toast_unlock_achievement, com.chaminwoo.stary.core.util.LocalizedNames.title(context, it.id, it.name))
                                )
                            }
                        }
                    ) { pos ->
                        val colorIdx = colorIds[pos]
                        val locked = StarUnlocks.lockedColorAch(colorIdx, unlockedIds) != null
                        val colorList = StarStyle.colorsOf(colorIdx)
                        val colorBrush = if (colorList.size > 1) Brush.linearGradient(colorList) else SolidColor(colorList[0])
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .alpha(if (locked) 0.25f else 1f)
                                    .clip(CircleShape)
                                    .background(colorBrush)
                            )
                            if (locked) {
                                Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.cd_locked),
                                    tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                    OwnedOnlyToggle(
                        checked = ownedOnly, onChange = setOwnedOnly,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
                    )

                    Hairline(Modifier.padding(top = 16.dp, bottom = 20.dp))

                    // ── 공개 범위 — 면으로만 나눈 선택 줄. 업로드 진행 중에는 바꿀 수 없게 잠근다 ──
                    SectionHeader(stringResource(R.string.upload_visibility))
                    Spacer(Modifier.height(10.dp))
                    StarySegmented(
                        options = VisibilityOptions.map { (_, labelRes, icon) -> stringResource(labelRes) to icon },
                        selected = VisibilityOptions.indexOfFirst { it.first == visibilityType }.coerceAtLeast(0),
                        onSelect = { visibilityType = VisibilityOptions[it].first },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isUploading,
                    )
                }

                // ── 저장 — 화면 아래 고정(스크롤해도 늘 보인다) ──
                StaryButton(
                    text = stringResource(R.string.common_save),
                    onClick = submit,
                    enabled = !isUploading,
                    loading = isUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
                )
            }
        }

        StarPickerSheet(
            visible = pickerOpen,
            tab = pickerTab,
            onTab = { pickerTab = it },
            hazeState = sheetHaze,
            starType = starType,
            starColor = starColor,
            shapeIds = allShapeIds,
            colorIds = allColorIds,
            unlockedIds = unlockedIds,
            ownedOnly = ownedOnly,
            onOwnedOnly = setOwnedOnly,
            onPickShape = { starType = it },
            onPickColor = { starColor = it },
            onDismiss = { pickerOpen = false },
        )

        // 부메랑(3초 움짤) 커스텀 촬영 — 전체 화면 오버레이
        if (showBoomerangCapture) {
            BoomerangCaptureScreen(
                onResult = { frames, scale, offsetNormX, offsetNormY ->
                    boomerangFrames = frames
                    boomerangInit = Triple(scale, offsetNormX, offsetNormY)
                    cropController.setBoomerang(frames)
                    selectedImageUri = null
                    showBoomerangCapture = false
                },
                onClose = { showBoomerangCapture = false },
            )
        }
    }
}

// 입력칸·첨부 줄의 바탕 — 테두리 없이 반투명 남색 면(별하늘 배경이 은은히 비친다).
private val FieldFill = Color(0x99181E34)
private val Placeholder = StaryColors.Muted.copy(alpha = 0.7f)

/** 첨부가 없을 때의 얇은 한 줄 — 누르면 촬영/갤러리/3초 영상 고르기. 오른쪽 작은 아이콘이 세 가지를 알려 준다. */
@Composable
private fun AddMediaRow(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FieldFill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = StaryColors.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.upload_add_photo), color = StaryColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        listOf(Icons.Filled.CameraAlt, Icons.Filled.Photo, Icons.Filled.Videocam).forEach {
            Icon(it, contentDescription = null, tint = StaryColors.Muted.copy(alpha = 0.7f), modifier = Modifier.padding(start = 8.dp).size(15.dp))
        }
    }
}

/** 별 도장 + 제목 한 줄 + 밑줄(누르고 있으면 별빛색). */
@Composable
private fun TitleField(value: String, onValueChange: (String) -> Unit, starType: Int, starColor: Int, enabled: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val line by animateColorAsState(if (focused) StaryColors.Accent.copy(alpha = 0.7f) else StaryColors.Line, label = "titleLine")
    val style = TextStyle(color = StaryColors.Text, fontFamily = MinSans, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StarShapeIcon(type = starType, colorIndex = starColor, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(StaryColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused }
                    .padding(vertical = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(stringResource(R.string.field_title), style = style.copy(color = Placeholder))
                        inner()
                    }
                },
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(line))
        Counter(value.length, StaryConfig.DIARY_TITLE_MAX_LEN, Modifier.align(Alignment.End))
    }
}

/** 본문 — 바탕 없이 글만(편지지처럼). 빈 곳을 눌러도 입력이 시작되게 최소 높이를 둔다. */
@Composable
private fun BodyField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    val style = TextStyle(color = StaryColors.Text, fontFamily = MinSans, fontSize = 15.sp, lineHeight = 24.sp)
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = style,
            cursorBrush = SolidColor(StaryColors.Accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(stringResource(R.string.upload_content_label), style = style.copy(color = Placeholder))
                    inner()
                }
            },
        )
        Counter(value.length, StaryConfig.DIARY_CONTENT_MAX_LEN, Modifier.align(Alignment.End))
    }
}

@Composable
private fun Counter(length: Int, max: Int, modifier: Modifier = Modifier) {
    Text("$length/$max", color = Placeholder, fontSize = 11.sp, modifier = modifier.padding(top = 4.dp))
}

/** 묶음 이름(왼쪽) + '모두 보기'(오른쪽, 있을 때만). */
@Composable
private fun SectionHeader(label: String, enabled: Boolean = true, onSeeAll: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = StaryColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            Text(
                stringResource(R.string.upload_see_all),
                color = StaryColors.Accent.copy(alpha = if (enabled) 1f else 0.5f),
                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled, onClick = onSeeAll)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(StaryColors.Line.copy(alpha = 0.7f)))
}

/** 쓰다 나간 글이 있을 때 맨 위 한 줄 — 이어 쓰기 / 지우기. */
@Composable
private fun DraftStrip(onRestore: () -> Unit, onDiscard: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(StaryColors.Accent.copy(alpha = 0.10f))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.EditNote, contentDescription = null, tint = StaryColors.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.upload_draft_found), color = StaryColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDiscard) {
            Text(stringResource(R.string.upload_draft_discard), color = StaryColors.Muted, fontSize = 13.sp)
        }
        TextButton(onClick = onRestore) {
            Text(stringResource(R.string.upload_draft_restore), color = StaryColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * 크롭 상태 보유자. 프레임을 항상 덮는 cover 배율 위에 사용자 핀치(scale)와 드래그(offset)를 얹고,
 * 이미지가 프레임을 벗어나 빈 공간이 생기지 않도록 offset 을 클램프한다.
 *
 * 사진(1장)과 부메랑(3초 움짤, 여러 장)이 **같은 프레임/같은 조작**을 쓴다 —
 * 부메랑은 [minScale] 을 촬영 원본이 통째로 들어오는 배율까지 낮춰 화각을 다 담을 수도 있다.
 */
private class CropController {
    /** 크롭 대상 원본. 사진은 1장, 부메랑은 촬영 프레임 전부. */
    var frames by mutableStateOf<List<Bitmap>>(emptyList())
        private set
    /** 미리보기 재생 시퀀스(부메랑은 정→역, 사진은 그대로 1장). */
    var playback by mutableStateOf<List<Bitmap>>(emptyList())
        private set
    /** 부메랑 재생 위치(사진이면 항상 0). */
    var frameIndex by mutableIntStateOf(0)
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var frame by mutableStateOf(IntSize.Zero)
    /** 축소 하한 — 사진은 1f(프레임을 빈틈없이 덮는다), 부메랑은 화각 전체가 들어오는 배율. */
    var minScale by mutableFloatStateOf(1f)

    val first: Bitmap? get() = frames.firstOrNull()
    val current: Bitmap? get() = playback.getOrNull(if (playback.isEmpty()) 0 else frameIndex % playback.size)

    fun setPhoto(bmp: Bitmap?) {
        frames = listOfNotNull(bmp)
        playback = frames
        frameIndex = 0
        minScale = 1f
        reset()
    }

    /** 부메랑 촬영 프레임을 건다(위치·확대는 호출부가 촬영 화면 상태로 이어붙인다). */
    fun setBoomerang(captured: List<Bitmap>) {
        frames = captured
        playback = BoomerangHelper.boomerangSequence(captured)
        frameIndex = 0
        minScale = 1f   // 실제 하한은 프레임 크기를 잰 뒤 호출부가 채운다.
        reset()
    }

    fun reset() { scale = minScale; offset = Offset.Zero }

    fun onTransform(zoomChange: Float, panChange: Offset) {
        val bmp = first ?: return
        val fw = frame.width.toFloat(); val fh = frame.height.toFloat()
        if (fw <= 0f || fh <= 0f) return
        scale = (scale * zoomChange).coerceIn(minScale, 4f)
        val dispScale = max(fw / bmp.width, fh / bmp.height) * scale
        val dispW = bmp.width * dispScale
        val dispH = bmp.height * dispScale
        val maxX = ((dispW - fw) / 2f).coerceAtLeast(0f)
        val maxY = ((dispH - fh) / 2f).coerceAtLeast(0f)
        val moved = offset + panChange
        offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
    }
}

/**
 * 고정 비율 프레임 안에서 사진(또는 3초 움짤)을 cover-fit 으로 그리고
 * 드래그/핀치로 위치·확대를 받는다. 움짤이면 프레임을 돌려가며 재생한다.
 */
@Composable
private fun ImageCropFrame(
    controller: CropController,
    modifier: Modifier,
    /** false 면 드래그/핀치를 막는다(업로드 진행 중 크롭 변경 방지). */
    enabled: Boolean = true,
) {
    // 움짤 재생 — 사진(1장)일 땐 돌지 않는다.
    val playbackSize = controller.playback.size
    LaunchedEffect(playbackSize) {
        if (playbackSize <= 1) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(BoomerangHelper.FRAME_DELAY_CS * 10L)
            controller.frameIndex = (controller.frameIndex + 1) % playbackSize
        }
    }
    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { controller.frame = it },
        contentAlignment = Alignment.Center
    ) {
        val bmp = controller.current
        if (bmp == null) {
            com.chaminwoo.stary.core.ui.StarLoadingIndicator(size = 28.dp, color = Color.White)
            return@Box
        }
        val image = bmp.asImageBitmap()
        Canvas(
            modifier = Modifier.matchParentSize().then(
                if (enabled) Modifier.pointerInput(controller.frames) {
                    detectTransformGestures { _, pan, zoom, _ -> controller.onTransform(zoom, pan) }
                } else Modifier
            )
        ) {
            val fw = size.width; val fh = size.height
            val dispScale = max(fw / bmp.width, fh / bmp.height) * controller.scale
            val dispW = bmp.width * dispScale
            val dispH = bmp.height * dispScale
            val left = (fw - dispW) / 2f + controller.offset.x
            val top = (fh - dispH) / 2f + controller.offset.y
            drawImage(
                image = image,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(dispW.roundToInt(), dispH.roundToInt())
            )
            // 3분할 크롭 가이드
            val guide = Color.White.copy(alpha = 0.35f)
            val sw = 1.dp.toPx()
            drawLine(guide, Offset(fw / 3f, 0f), Offset(fw / 3f, fh), sw)
            drawLine(guide, Offset(fw * 2f / 3f, 0f), Offset(fw * 2f / 3f, fh), sw)
            drawLine(guide, Offset(0f, fh / 3f), Offset(fw, fh / 3f), sw)
            drawLine(guide, Offset(0f, fh * 2f / 3f), Offset(fw, fh * 2f / 3f), sw)
        }
    }
}

/** 올리기 실패 이벤트(이유별) — 첫 별을 못 올리고 나가는 지점을 찾으려고(StaryAnalytics). */
private fun uploadFail(reason: String) =
    com.chaminwoo.stary.core.util.StaryAnalytics.log("upload_fail", "reason" to reason)

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
