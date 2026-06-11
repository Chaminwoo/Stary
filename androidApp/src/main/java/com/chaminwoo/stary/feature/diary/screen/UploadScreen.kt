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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Photo
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
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.util.ImageUploadHelper
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import kotlinx.coroutines.launch

@Composable
fun UploadScreen(
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
    diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory())
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var isAnonymous by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val cameraUri = remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val isLoggedIn = GoogleAuthHelper.currentUserId != null

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = MaterialTheme.colorScheme.onBackground,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor    = MaterialTheme.colorScheme.secondary,
        unfocusedLabelColor  = MaterialTheme.colorScheme.secondary,
        cursorColor          = MaterialTheme.colorScheme.onBackground,
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) selectedImageUri = cameraUri.value }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { selectedImageUri = it } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val tmpFile = java.io.File.createTempFile("diary_img_", ".jpg", context.cacheDir)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", tmpFile
            )
            cameraUri.value = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "카메라 권한이 필요해요", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        val permission = android.Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            val tmpFile = java.io.File.createTempFile("diary_img_", ".jpg", context.cacheDir)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", tmpFile
            )
            cameraUri.value = uri
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(permission)
        }
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("사진 추가", color = MaterialTheme.colorScheme.onBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            showImageSourceDialog = false
                            launchCamera()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "카메라로 촬영",
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TextButton(
                        onClick = {
                            showImageSourceDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Photo,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "갤러리에서 선택",
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImageSourceDialog = false }) {
                    Text("취소", color = MaterialTheme.colorScheme.secondary)
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        diaryViewModel.event.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (message == "저장 완료!") onSaveClick()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.upload_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(
                Color.Black.copy(alpha = 0.8f),
                blendMode = BlendMode.Darken
            )
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // 이미지 영역
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable { showImageSourceDialog = true },
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(selectedImageUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "사진 추가",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("제목") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("이 장소의 기억을 남겨주세요") },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        if (isLoggedIn) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { isAnonymous = !isAnonymous }
            ) {
                Checkbox(
                    checked = isAnonymous,
                    onCheckedChange = { isAnonymous = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.onBackground,
                        uncheckedColor = MaterialTheme.colorScheme.secondary
                    )
                )
                Text(
                    "익명으로 올리기",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (title.isBlank()) {
                    Toast.makeText(context, "제목을 입력해주세요", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                coroutineScope.launch {
                    isUploading = true
                    val currentLatLng = LocationHelper.getCurrentLatLng()
                    val lat = currentLatLng?.latitude ?: LocationHelper.getCurrentLocation(context)?.latitude ?: 0.0
                    val lng = currentLatLng?.longitude ?: LocationHelper.getCurrentLocation(context)?.longitude ?: 0.0
                    val imageUrl = if (selectedImageUri != null) {
                        val url = ImageUploadHelper.uploadImage(context, selectedImageUri!!)
                        if (url == null) {
                            Toast.makeText(context, "이미지 업로드에 실패했어요. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                            isUploading = false
                            return@launch
                        }
                        url
                    } else ""
                    val userName = when {
                        !isLoggedIn -> "익명"
                        isAnonymous -> "익명"
                        else -> GoogleAuthHelper.currentUserName ?: "알 수 없음"
                    }
                    diaryViewModel.saveDiary(
                        Diary(
                            title = title,
                            content = content,
                            imageUrl = imageUrl,
                            userId = GoogleAuthHelper.currentUserId ?: "",
                            userName = userName,
                            isAnonymous = isAnonymous || !isLoggedIn,
                            latitude = lat,
                            longitude = lng
                        )
                    )
                    isUploading = false
                }
            },
            enabled = !isUploading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background
            )
        ) {
            if (isUploading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("저장", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
    } // Box
}
