package com.chaminwoo.stary.feature.diary.screen

import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.util.ImageUploadHelper
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

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
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.8f), blendMode = BlendMode.Darken)
        )

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            // 이미지 영역
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .clickable { showImageSourceDialog = true },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    Image(rememberAsyncImagePainter(selectedImageUri), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CameraAlt, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("사진 추가", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
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
                            .background(if (isSelected) Color.White.copy(alpha = 0.12f) else Color(0xFF1A1A2E))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) StarStyle.colorOf(starColor) else Color.White.copy(0.12f),
                                shape = RoundedCornerShape(18.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        StarShapeIcon(type = type, color = StarStyle.colorOf(starColor), modifier = Modifier.size(44.dp))
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
                val color = StarStyle.colorOf(colorIdx)

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
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.5.dp,
                                color = if (isSelected) Color.White else Color.White.copy(0.2f),
                                shape = CircleShape
                            )
                    )
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
                    coroutineScope.launch {
                        isUploading = true
                        val curLatLng = LocationHelper.getCurrentLatLng()
                        val lat = curLatLng?.latitude ?: LocationHelper.getCurrentLocation(context)?.latitude ?: 0.0
                        val lng = curLatLng?.longitude ?: LocationHelper.getCurrentLocation(context)?.longitude ?: 0.0
                        val imageUrl = if (selectedImageUri != null) {
                            val result = ImageUploadHelper.uploadImageResult(context, selectedImageUri!!)
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
