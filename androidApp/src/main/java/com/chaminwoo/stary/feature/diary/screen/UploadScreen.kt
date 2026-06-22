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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.R
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToInt

private val VisibilityOptions = listOf(
    Triple("public",  "전체공개", Icons.Filled.Public),
    Triple("friends", "친구만",   Icons.Filled.People),
    Triple("private", "나만보기", Icons.Filled.Lock),
)

private const val INFINITE_PAGES = 10_000

/** 페이지 오프셋(0=중앙) → 0..1 보간 */
private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction.coerceIn(0f, 1f)

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
    var isUploading by remember { mutableStateOf(false) }
    var isAnonymous by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val cameraUri = remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val isLoggedIn = GoogleAuthHelper.currentUserId != null

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

    // 무한 캐러셀 — 초기 페이지를 중간값으로 설정
    val shapePagerState = rememberPagerState(
        initialPage = INFINITE_PAGES / 2 - (INFINITE_PAGES / 2 % StarStyle.TYPE_COUNT),
        pageCount = { INFINITE_PAGES }
    )
    val colorPagerState = rememberPagerState(
        initialPage = INFINITE_PAGES / 2 - (INFINITE_PAGES / 2 % StarStyle.COLOR_COUNT),
        pageCount = { INFINITE_PAGES }
    )
    val starType = shapePagerState.currentPage % StarStyle.TYPE_COUNT
    val starColor = colorPagerState.currentPage % StarStyle.COLOR_COUNT

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
        if (success) selectedImageUri = cameraUri.value
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedImageUri = it }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val tmpFile = java.io.File.createTempFile("diary_img_", ".jpg", context.cacheDir)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmpFile)
            cameraUri.value = uri; cameraLauncher.launch(uri)
        } else com.chaminwoo.stary.core.ui.StaryToast.show("카메라 권한이 필요해요")
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
            title = { Text("사진 추가", color = MaterialTheme.colorScheme.onBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showImageSourceDialog = false; launchCamera() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.CameraAlt, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("카메라로 촬영", color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = { showImageSourceDialog = false; galleryLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Photo, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("갤러리에서 선택", color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showImageSourceDialog = false }) { Text("취소", color = MaterialTheme.colorScheme.secondary) } }
        )
    }

    LaunchedEffect(Unit) {
        diaryViewModel.event.collect { msg ->
            com.chaminwoo.stary.core.ui.StaryToast.show(msg)
            if (msg == "저장 완료!") onSaveClick()
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
                    .then(if (selectedImageUri == null) Modifier.clickable { showImageSourceDialog = true } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    ImageCropFrame(controller = cropController, modifier = Modifier.matchParentSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CameraAlt, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("사진 추가", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                    }
                }
            }

            if (selectedImageUri != null) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showImageSourceDialog = true }) {
                        Text("다시 선택", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text("제목") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp), colors = fieldColors,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = content, onValueChange = { content = it }, label = { Text("이 장소의 기억을 남겨주세요") },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = RoundedCornerShape(12.dp), colors = fieldColors,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground)
            )

            Spacer(Modifier.height(24.dp))

            // ── 별 모양 캐러셀 ────────────────────────────────────────────
            Text("별 모양", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))

            HorizontalPager(
                state = shapePagerState,
                contentPadding = PaddingValues(horizontal = 96.dp),
//                pageSpacing = 6.dp,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxWidth().height(96.dp)
            ) { page ->
                val type = page % StarStyle.TYPE_COUNT
                val rawOffset = (shapePagerState.currentPage - page).toFloat() + shapePagerState.currentPageOffsetFraction
                val absOffset = rawOffset.absoluteValue
                val scale = lerp(0.70f, 1f, 1f - absOffset.coerceIn(0f, 1f))
                val alpha = lerp(0.35f, 1f, 1f - absOffset.coerceIn(0f, 1f))
                val isSelected = type == starType
                val lockAch = StarUnlocks.lockedShapeAch(type, unlockedIds)
                val locked = lockAch != null

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected && !locked) Color.White.copy(alpha = 0.12f) else Color(0xFF14141F))
                            .border(
                                width = if (isSelected && !locked) 2.dp else 1.dp,
                                color = when {
                                    locked -> Color.White.copy(0.10f)
                                    isSelected -> StarStyle.colorOf(starColor)
                                    else -> Color.White.copy(0.12f)
                                },
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                if (locked) {
                                    com.chaminwoo.stary.core.ui.StaryToast.show("‘${lockAch!!.name}’ 업적을 달성하여 해금하세요!")
                                } else {
                                    coroutineScope.launch { shapePagerState.animateScrollToPage(page) }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (locked) {
                            StarShapeIcon(type = type, color = Color.White.copy(0.20f), modifier = Modifier.size(44.dp))
                        } else {
                            StarShapeIcon(type = type, colorIndex = starColor, modifier = Modifier.size(44.dp))
                        }
                        if (locked) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = "잠김",
                                    tint = Color.White.copy(0.85f), modifier = Modifier.size(11.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── 별 색상 캐러셀 ────────────────────────────────────────────
            Text("별 색상", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))

            HorizontalPager(
                state = colorPagerState,
                contentPadding = PaddingValues(horizontal = 104.dp),
//                pageSpacing = 7.dp,
                beyondViewportPageCount = 2,
                modifier = Modifier.fillMaxWidth().height(72.dp)
            ) { page ->
                val colorIdx = page % StarStyle.COLOR_COUNT
                val rawOffset = (colorPagerState.currentPage - page).toFloat() + colorPagerState.currentPageOffsetFraction
                val absOffset = rawOffset.absoluteValue
                val scale = lerp(0.65f, 1f, 1f - absOffset.coerceIn(0f, 1f))
                val alpha = lerp(0.3f, 1f, 1f - absOffset.coerceIn(0f, 1f))
                val isSelected = colorIdx == starColor
                val colorList = StarStyle.colorsOf(colorIdx)
                val colorBrush = if (colorList.size > 1) androidx.compose.ui.graphics.Brush.linearGradient(colorList)
                                 else androidx.compose.ui.graphics.SolidColor(colorList[0])
                val lockAch = StarUnlocks.lockedColorAch(colorIdx, unlockedIds)
                val locked = lockAch != null

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colorBrush)
                            .border(
                                width = if (isSelected && !locked) 3.dp else 1.5.dp,
                                color = when {
                                    locked -> Color.White.copy(0.15f)
                                    isSelected -> Color.White
                                    else -> Color.White.copy(0.2f)
                                },
                                shape = CircleShape
                            )
                            .clickable {
                                if (locked) {
                                    com.chaminwoo.stary.core.ui.StaryToast.show("‘${lockAch!!.name}’ 업적을 달성하여 해금하세요!")
                                } else {
                                    coroutineScope.launch { colorPagerState.animateScrollToPage(page) }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (locked) {
                            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)))
                            Icon(Icons.Filled.Lock, contentDescription = "잠김",
                                tint = Color.White.copy(0.9f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── 공개 범위 ─────────────────────────────────────────────────
            Text("공개 범위", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisibilityOptions.forEach { (key, label, icon) ->
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
                            Text(label, fontSize = 12.sp, color = if (selected) Color(0xFF6EE7B7) else MaterialTheme.colorScheme.secondary)
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
                    Text("익명으로 올리기", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (title.isBlank()) { com.chaminwoo.stary.core.ui.StaryToast.show("제목을 입력해주세요"); return@Button }
                    StarUnlocks.lockedShapeAch(starType, unlockedIds)?.let {
                        com.chaminwoo.stary.core.ui.StaryToast.show("‘${it.name}’ 업적을 달성하여 해금하세요!"); return@Button
                    }
                    StarUnlocks.lockedColorAch(starColor, unlockedIds)?.let {
                        com.chaminwoo.stary.core.ui.StaryToast.show("‘${it.name}’ 업적을 달성하여 해금하세요!"); return@Button
                    }
                    coroutineScope.launch {
                        isUploading = true
                        val curLatLng = LocationHelper.getCurrentLatLng()
                        val lat = curLatLng?.latitude ?: LocationHelper.getCurrentLocation(context)?.latitude ?: 0.0
                        val lng = curLatLng?.longitude ?: LocationHelper.getCurrentLocation(context)?.longitude ?: 0.0
                        val imageUrl = if (selectedImageUri != null) {
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
                                com.chaminwoo.stary.core.ui.StaryToast.show("이미지 업로드 실패: ${result.error}")
                                isUploading = false; return@launch
                            }
                            result.url!!
                        } else ""
                        val uName = when { !isLoggedIn -> "익명"; isAnonymous -> "익명"; else -> GoogleAuthHelper.currentUserName ?: "알 수 없음" }
                        diaryViewModel.saveDiary(
                            Diary(
                                title = title, content = content, imageUrl = imageUrl,
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
                if (isUploading) CircularProgressIndicator(color = MaterialTheme.colorScheme.background, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("저장", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(32.dp))
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
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
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
