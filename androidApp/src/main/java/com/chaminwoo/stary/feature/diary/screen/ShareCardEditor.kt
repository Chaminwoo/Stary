package com.chaminwoo.stary.feature.diary.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.util.ShareCardHelper
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 공유 카드 편집 화면 — 공유 버튼을 누르면 뜨는 전체 화면 다이얼로그.
 * 미리보기를 **드래그해 별(+지도 무대) 위치**를 옮기고, 제목/지도/위치/날짜/별 크기를 조정한 뒤
 * 인스타 스토리 또는 일반 공유로 내보낸다. 렌더 자산(동네 이름·정적 지도)은 1회 로드 후 재사용.
 *
 * ⚠️ DetailScreen 본체에 인라인하지 말 것 — dex 메서드 레지스터 한계(VerifyError) 방지를 위해
 * 별도 파일/컴포저블로 유지(ShareDiaryButton 주석 참고).
 */
@Composable
fun ShareCardEditorDialog(diary: Diary, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = StarStyle.colorOf(diary.starColor)

    var assets by remember { mutableStateOf<ShareCardHelper.ShareCardAssets?>(null) }
    var options by remember { mutableStateOf(ShareCardHelper.ShareCardOptions(title = diary.title)) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    // 자산 1회 로드(역지오코딩 + 정적 지도) → 이후 옵션 변경마다 로컬 렌더만.
    LaunchedEffect(Unit) {
        assets = withContext(Dispatchers.IO) { ShareCardHelper.prepareAssets(context, diary) }
    }
    // 옵션/자산 변경 → 미리보기 재렌더(드래그 연속 변경은 60ms 스로틀, 재시작 시 직전 대기 취소).
    LaunchedEffect(options, assets) {
        val a = assets ?: return@LaunchedEffect
        delay(60)
        preview = withContext(Dispatchers.Default) { ShareCardHelper.renderCard(context, diary, a, options) }
    }
    DisposableEffect(Unit) { onDispose { assets?.release() } }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF7090D16))
                .systemBarsPadding()
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 헤더 — 제목 + 닫기
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.share_edit_title),
                    color = Color(0xFFF0F0F0), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { if (!busy) onDismiss() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close), tint = Color.White)
                }
            }

            // 미리보기 — 드래그로 별 위치 이동
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentAlignment = Alignment.Center
            ) {
                val bmp = preview
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1080f / 1920f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    // 델타 기반 이동(점프 없음) — 텍스트 존(하단)과 겹치지 않게 클램프
                                    options = options.copy(
                                        stageXFrac = (options.stageXFrac + dragAmount.x / size.width)
                                            .coerceIn(0.15f, 0.85f),
                                        stageYFrac = (options.stageYFrac + dragAmount.y / size.height)
                                            .coerceIn(0.15f, 0.52f),
                                    )
                                }
                            }
                    )
                } else {
                    CircularProgressIndicator(color = accent, modifier = Modifier.size(30.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.share_edit_hint),
                color = Color(0xFF8A93A6), fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(14.dp))

            // 카드 제목 수정
            OutlinedTextField(
                value = options.title ?: "",
                onValueChange = { options = options.copy(title = it.take(StaryConfig.DIARY_TITLE_MAX_LEN)) },
                label = { Text(stringResource(R.string.share_edit_field_title), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent.copy(alpha = 0.7f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedLabelColor = Color(0xFF8A93A6),
                    unfocusedLabelColor = Color(0xFF8A93A6),
                    cursorColor = accent,
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFF0F0F0))
            )

            Spacer(Modifier.height(12.dp))

            // 표시 토글 — 지도 / 위치 / 날짜
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorTogglePill(stringResource(R.string.share_edit_show_map), options.showMap, accent) {
                    options = options.copy(showMap = !options.showMap)
                }
                EditorTogglePill(stringResource(R.string.share_edit_show_location), options.showLocation, accent) {
                    options = options.copy(showLocation = !options.showLocation)
                }
                EditorTogglePill(stringResource(R.string.share_edit_show_date), options.showDate, accent) {
                    options = options.copy(showDate = !options.showDate)
                }
            }

            Spacer(Modifier.height(10.dp))

            // 별 크기 슬라이더
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.share_edit_star_size),
                    color = Color(0xFF8A93A6), fontSize = 12.sp
                )
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = options.starScale,
                    onValueChange = { options = options.copy(starScale = it) },
                    valueRange = 0.6f..1.6f,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent.copy(alpha = 0.7f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // 공유 실행 — 인스타 스토리 / 일반 공유
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            ShareCardHelper.shareToInstagramStory(context, diary, options, assets)
                            busy = false
                            onDismiss()
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF0B0F18))
                ) {
                    if (busy) CircularProgressIndicator(color = Color(0xFF0B0F18), modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.share_to_story), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            ShareCardHelper.shareDiary(context, diary, options, assets)
                            busy = false
                            onDismiss()
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.10f), contentColor = Color(0xFFF0F0F0)
                    )
                ) {
                    Text(stringResource(R.string.share_as_image), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

/** 표시 토글 알약 버튼 — 활성 시 별색 테두리/글자. */
@Composable
private fun EditorTogglePill(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) accent else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (active) accent else Color(0xFF8A93A6),
            fontSize = 12.sp
        )
    }
}
