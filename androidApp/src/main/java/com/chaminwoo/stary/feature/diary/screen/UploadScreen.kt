package com.chaminwoo.stary.feature.diary.screen

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.Mint
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.util.ImageCropHelper
import com.chaminwoo.stary.core.util.ImageUploadHelper
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import com.chaminwoo.stary.feature.profile.Achievements
import com.chaminwoo.stary.feature.profile.StarUnlocks
import com.chaminwoo.stary.feature.profile.rememberUserStats
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

// 공개 범위 선택지: (key, 라벨 문자열 리소스, 아이콘). 라벨은 화면에서 stringResource 로 해석(언어 반영).
private val VisibilityOptions = listOf(
    Triple("public",  R.string.upload_vis_public,  Icons.Filled.Public),
    Triple("friends", R.string.upload_vis_friends, Icons.Filled.People),
    Triple("private", R.string.upload_vis_private, Icons.Filled.Lock),
)


@Composable
fun UploadScreen(
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
    diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory())
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var visibilityType by remember { mutableStateOf("public") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    // 부메랑(3초 움짤) GIF — 커스텀 촬영 화면에서 생성. 이미지와 배타(하나만 첨부).
    var boomerangFile by remember { mutableStateOf<java.io.File?>(null) }
    var showBoomerangCapture by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var isAnonymous by remember { mutableStateOf(false) }
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
        val uri = selectedImageUri
        if (uri == null) {
            cropController.bitmap = null
        } else {
            cropController.reset()
            cropController.bitmap = withContext(Dispatchers.IO) {
                ImageCropHelper.loadDownsampled(context, uri)
            }
        }
    }

    // 별 모양/색 — iOS UploadScreen 의 무한 회전 휠(WheelPicker) 구조 패리티(선택 = 항상 정중앙).
    var starType by remember { mutableIntStateOf(0) }
    var starColor by remember { mutableIntStateOf(0) }

    // 업적 해금 상태 — 잠긴 별 모양/색 판정에 사용 (비로그인 시 기본 항목만)
    val unlockedIds: Set<String> =
        if (isLoggedIn) Achievements.unlockedIds(rememberUserStats(GoogleAuthHelper.currentUserId!!))
        else emptySet()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = MaterialTheme.colorScheme.onBackground,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor    = MaterialTheme.colorScheme.secondary,
        unfocusedLabelColor  = MaterialTheme.colorScheme.secondary,
        cursorColor          = MaterialTheme.colorScheme.onBackground,
    )

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) { selectedImageUri = cameraUri.value; boomerangFile = null }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedImageUri = it; boomerangFile = null }
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
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.upload_add_photo), color = MaterialTheme.colorScheme.onBackground) },
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
            dismissButton = { TextButton(onClick = { showImageSourceDialog = false }) { Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.secondary) } }
        )
    }

    // 개척 퀘스트(체크리스트 32) — 저장 성공 시 이 좌표로 선점 시도(자체 스코프라 네비게이션에 안 죽음).
    var pioneerClaimTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // LaunchedEffect(Unit) 의 람다는 첫 컴포지션 값을 캡처하므로, 저장 직전에 고른 별을 쓰려면
    // 최신값 참조가 필요하다(선택을 바꾼 뒤 저장해도 그 별로 연출되게).
    val starTypeRef = androidx.compose.runtime.rememberUpdatedState(starType)
    val starColorRef = androidx.compose.runtime.rememberUpdatedState(starColor)
    LaunchedEffect(Unit) {
        diaryViewModel.event.collect { msg ->
            com.chaminwoo.stary.core.ui.StaryToast.show(msg)
            if (msg == "저장 완료!") {
                pioneerClaimTarget?.let { (la, ln) ->
                    com.chaminwoo.stary.feature.profile.PioneerClaimHelper.attemptClaim(context, la, ln)
                }
                // 별 탄생 연출(34-8) — 저장 성공에서만. 화면이 pop 된 뒤 지도 위에서 이어 재생된다
                // (전역 오버레이 StarBirthHost — MainScreen).
                com.chaminwoo.stary.core.ui.StarBirthState.trigger(starTypeRef.value, starColorRef.value)
                onSaveClick()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.upload_bg), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.82f), blendMode = BlendMode.Darken)
        )

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            // 이미지 영역 — 디테일 화면과 동일한 고정 비율 프레임.
            // 사진 선택 시 이 프레임이 곧 '크롭될 영역'이며, 드래그/핀치로 위치·확대 지정.
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(ImageCropHelper.ASPECT)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .then(if (selectedImageUri == null && boomerangFile == null) Modifier.clickable { showImageSourceDialog = true } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                when {
                    boomerangFile != null -> com.chaminwoo.stary.core.ui.GifImage(
                        model = boomerangFile, modifier = Modifier.matchParentSize()
                    )
                    selectedImageUri != null -> ImageCropFrame(controller = cropController, modifier = Modifier.matchParentSize())
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CameraAlt, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.upload_add_photo), color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                    }
                }
            }

            if (selectedImageUri != null || boomerangFile != null) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showImageSourceDialog = true }) {
                        Text(stringResource(R.string.upload_reselect), color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(StaryConfig.DIARY_TITLE_MAX_LEN) },
                label = { Text(stringResource(R.string.field_title)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp), colors = fieldColors,
                supportingText = {
                    Text(
                        "${title.length}/${StaryConfig.DIARY_TITLE_MAX_LEN}",
                        color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it.take(StaryConfig.DIARY_CONTENT_MAX_LEN) },
                label = { Text(stringResource(R.string.upload_content_label)) },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = RoundedCornerShape(12.dp), colors = fieldColors,
                supportingText = {
                    Text(
                        "${content.length}/${StaryConfig.DIARY_CONTENT_MAX_LEN}",
                        color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground)
            )

            Spacer(Modifier.height(24.dp))

            // ── 별 모양 휠 — iOS WheelPicker 패리티(중앙 선택·민트 링·무한 순환) ──
            Text(stringResource(R.string.upload_star_shape), color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))

            StarWheelPicker(
                count = StarStyle.TYPE_COUNT,
                selection = starType,
                onSelect = { starType = it },
                itemSize = 40.dp,
                onLanded = { t ->
                    StarUnlocks.lockedShapeAch(t, unlockedIds)?.let {
                        com.chaminwoo.stary.core.ui.StaryToast.show(
                            context.getString(R.string.toast_unlock_achievement, it.name)
                        )
                    }
                }
            ) { type ->
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

            Spacer(Modifier.height(20.dp))

            // ── 별 색상 휠 — iOS WheelPicker 패리티 ──
            Text(stringResource(R.string.upload_star_color), color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))

            StarWheelPicker(
                count = StarStyle.COLOR_COUNT,
                selection = starColor,
                onSelect = { starColor = it },
                itemSize = 32.dp,
                onLanded = { c ->
                    StarUnlocks.lockedColorAch(c, unlockedIds)?.let {
                        com.chaminwoo.stary.core.ui.StaryToast.show(
                            context.getString(R.string.toast_unlock_achievement, it.name)
                        )
                    }
                }
            ) { colorIdx ->
                val locked = StarUnlocks.lockedColorAch(colorIdx, unlockedIds) != null
                val colorList = StarStyle.colorsOf(colorIdx)
                val colorBrush = if (colorList.size > 1) androidx.compose.ui.graphics.Brush.linearGradient(colorList)
                                 else androidx.compose.ui.graphics.SolidColor(colorList[0])
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

            Spacer(Modifier.height(20.dp))

            // ── 공개 범위 ─────────────────────────────────────────────────
            Text(stringResource(R.string.upload_visibility), color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisibilityOptions.forEach { (key, labelRes, icon) ->
                    val selected = visibilityType == key
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) Color(0xFF6EE7B7) else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { visibilityType = key }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(icon, null, tint = if (selected) Color(0xFF6EE7B7) else MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Text(stringResource(labelRes), fontSize = 12.sp, color = if (selected) Color(0xFF6EE7B7) else MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            if (isLoggedIn) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isAnonymous = !isAnonymous }) {
                    Checkbox(
                        checked = isAnonymous, onCheckedChange = { isAnonymous = it },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.onBackground, uncheckedColor = MaterialTheme.colorScheme.secondary)
                    )
                    Text(stringResource(R.string.upload_anonymous), fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (title.isBlank()) { com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_title_required)); return@Button }
                    // 하루 업로드 제한(로그인 사용자 기준). 오늘 로컬 자정 이후 내가 올린 개수로 선차단.
                    if (isLoggedIn) {
                        val startOfDay = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val todayCount = myDiaries.count { it.createdAt >= startOfDay }
                        if (todayCount >= StaryConfig.DAILY_UPLOAD_LIMIT) {
                            com.chaminwoo.stary.core.ui.StaryToast.show(
                                context.getString(R.string.upload_daily_limit, StaryConfig.DAILY_UPLOAD_LIMIT)
                            )
                            return@Button
                        }
                    }
                    StarUnlocks.lockedShapeAch(starType, unlockedIds)?.let {
                        com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_unlock_achievement, it.name)); return@Button
                    }
                    StarUnlocks.lockedColorAch(starColor, unlockedIds)?.let {
                        com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_unlock_achievement, it.name)); return@Button
                    }
                    coroutineScope.launch {
                        isUploading = true
                        val curLatLng = LocationHelper.getCurrentLatLng()
                        val lat = curLatLng?.latitude ?: LocationHelper.getCurrentLocation(context)?.latitude ?: 0.0
                        val lng = curLatLng?.longitude ?: LocationHelper.getCurrentLocation(context)?.longitude ?: 0.0
                        // 첨부는 영상/이미지 중 하나(배타). 영상이 있으면 영상 우선.
                        var imageUrl = ""
                        var videoUrl = ""
                        when {
                            boomerangFile != null -> {
                                // 부메랑 움짤(GIF) — videoUrl 필드에 저장(스키마 유지, .gif 로 판별)
                                val result = ImageUploadHelper.uploadGifResult(context, boomerangFile!!)
                                if (!result.isSuccess) {
                                    com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_image_upload_failed, result.error ?: ""))
                                    isUploading = false; return@launch
                                }
                                videoUrl = result.url!!
                            }
                            selectedImageUri != null -> {
                                // 크롭 프레임 상태로 잘라낸 이미지를 업로드(실패 시 원본으로 폴백).
                                val uploadUri = withContext(Dispatchers.IO) {
                                    val bmp = cropController.bitmap
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
                                    com.chaminwoo.stary.core.ui.StaryToast.show(context.getString(R.string.toast_image_upload_failed, result.error ?: ""))
                                    isUploading = false; return@launch
                                }
                                imageUrl = result.url!!
                            }
                        }
                        val uName = when { !isLoggedIn -> "익명"; isAnonymous -> "익명"; else -> GoogleAuthHelper.currentUserName ?: "알 수 없음" }
                        pioneerClaimTarget = lat to lng // 저장 성공 이벤트에서 개척 선점 시도(체크리스트 32)
                        diaryViewModel.saveDiary(
                            Diary(
                                title = title, content = content, imageUrl = imageUrl, videoUrl = videoUrl,
                                userId = GoogleAuthHelper.currentUserId ?: "",
                                userName = uName, isAnonymous = isAnonymous || !isLoggedIn,
                                latitude = lat, longitude = lng,
                                starType = starType, starColor = starColor,
                                visibilityType = visibilityType
                            )
                        )
                        isUploading = false
                    }
                },
                enabled = !isUploading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)
            ) {
                if (isUploading) com.chaminwoo.stary.core.ui.StarLoadingIndicator(size = 22.dp, color = MaterialTheme.colorScheme.background)
                else Text(stringResource(R.string.common_save), fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }

            Spacer(Modifier.height(32.dp))
        }

        // 부메랑(3초 움짤) 커스텀 촬영 — 전체 화면 오버레이
        if (showBoomerangCapture) {
            BoomerangCaptureScreen(
                onResult = { file ->
                    boomerangFile = file
                    selectedImageUri = null
                    showBoomerangCapture = false
                },
                onClose = { showBoomerangCapture = false },
            )
        }
    }
}

/**
 * 크롭 상태 보유자. 프레임을 항상 덮는 cover 배율 위에 사용자 핀치(scale)와 드래그(offset)를 얹고,
 * 이미지가 프레임을 벗어나 빈 공간이 생기지 않도록 offset 을 클램프한다.
 */
private class CropController {
    var bitmap by mutableStateOf<Bitmap?>(null)
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var frame by mutableStateOf(IntSize.Zero)

    fun reset() { scale = 1f; offset = Offset.Zero }

    fun onTransform(zoomChange: Float, panChange: Offset) {
        val bmp = bitmap ?: return
        val fw = frame.width.toFloat(); val fh = frame.height.toFloat()
        if (fw <= 0f || fh <= 0f) return
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        val dispScale = max(fw / bmp.width, fh / bmp.height) * scale
        val dispW = bmp.width * dispScale
        val dispH = bmp.height * dispScale
        val maxX = ((dispW - fw) / 2f).coerceAtLeast(0f)
        val maxY = ((dispH - fh) / 2f).coerceAtLeast(0f)
        val moved = offset + panChange
        offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
    }
}

/** 고정 비율 프레임 안에서 이미지를 cover-fit 으로 그리고 드래그/핀치로 위치·확대를 받는다. */
@Composable
private fun ImageCropFrame(controller: CropController, modifier: Modifier) {
    val bmp = controller.bitmap
    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { controller.frame = it },
        contentAlignment = Alignment.Center
    ) {
        if (bmp == null) {
            com.chaminwoo.stary.core.ui.StarLoadingIndicator(size = 28.dp, color = Color.White)
            return@Box
        }
        val image = remember(bmp) { bmp.asImageBitmap() }
        Canvas(
            modifier = Modifier.matchParentSize().pointerInput(bmp) {
                detectTransformGestures { _, pan, zoom, _ -> controller.onTransform(zoom, pan) }
            }
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

/**
 * 별 모양/색 선택용 무한 회전 휠 — iOS `UploadScreen.WheelPicker` 구조 패리티.
 * 선택 항목이 항상 정중앙(민트 링), 좌우로 5개 정도 노출, 끝까지 돌리면 처음 항목이 다시 나온다(modulo 순환).
 * 놓은/탭한 지점의 항목을 놓은 자리에서 그대로 중앙으로 스냅하고, 스냅이 끝난 뒤 selection 을 한 번에 갱신
 * (좌표 불연속으로 되돌아가는 현상 방지). 잠긴 항목도 중앙 고정되며, 저장 차단은 호출부가 담당한다.
 */
@Composable
private fun StarWheelPicker(
    count: Int,
    selection: Int,
    onSelect: (Int) -> Unit,
    itemSize: androidx.compose.ui.unit.Dp,
    onLanded: (Int) -> Unit,
    item: @Composable (Int) -> Unit,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    val slotPx = (itemSize.value + 28f) * density   // 슬롯 간격(중앙 ±2 노출)
    val coroutineScope = rememberCoroutineScope()

    val drag = remember { Animatable(0f) }
    var settling by remember { mutableStateOf(false) }

    fun wrap(i: Int): Int = ((i % count) + count) % count

    // 놓은(또는 탭한) 지점의 항목을 놓은 자리에서 중앙으로 스냅 → 끝난 뒤 selection 갱신(시각적 점프 없음).
    fun commit(steps: Int) {
        if (steps == 0) return
        settling = true
        val target = wrap(selection + steps)
        coroutineScope.launch {
            drag.animateTo(-steps * slotPx, tween(240, easing = FastOutSlowInEasing))
            onSelect(target)      // off=steps 였던 항목이 이제 off=0
            drag.snapTo(0f)       // 좌표 동일 → 시각적 점프 없음
            settling = false
            onLanded(target)
        }
    }

    // 화면에 그릴 슬롯 반경 — 기본 3(±3) + 현재 드래그 이동량만큼 여유(많이 돌려도 빈칸 없이 채움).
    val visibleSpan = 3 + ceil(abs(drag.value) / slotPx).toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemSize * 1.7f)
            .clipToBounds()
            .pointerInput(count, selection) {
                detectHorizontalDragGestures(
                    onDragStart = { },
                    onHorizontalDrag = { _, delta ->
                        if (!settling) coroutineScope.launch { drag.snapTo(drag.value + delta) }
                    },
                    onDragEnd = {
                        val steps = (-drag.value / slotPx).roundToInt()
                        commit(steps)
                    },
                    onDragCancel = {
                        val steps = (-drag.value / slotPx).roundToInt()
                        commit(steps)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // 중앙 강조 링(선택 자리).
        Box(
            Modifier
                .size(itemSize + 18.dp)
                .clip(CircleShape)
                .border(2.dp, Mint.copy(alpha = 0.9f), CircleShape)
        )

        for (off in -visibleSpan..visibleSpan) {
            val idx = wrap(selection + off)
            val x = off * slotPx + drag.value
            val dist = abs(x)
            val scale = max(0.55f, 1.25f - dist / slotPx * 0.35f)
            val itemAlpha = max(0.3f, 1f - dist / (slotPx * 2.2f))
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = x
                        scaleX = scale; scaleY = scale
                        alpha = itemAlpha
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { commit(off) },
                contentAlignment = Alignment.Center
            ) {
                item(idx)
            }
        }
    }
}
