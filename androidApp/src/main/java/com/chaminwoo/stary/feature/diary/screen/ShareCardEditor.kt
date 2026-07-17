package com.chaminwoo.stary.feature.diary.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.util.ShareCardHelper
import com.chaminwoo.stary.data.repository.FirebaseDiaryRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 프리뷰 드래그 대상 — 무대(별+지도)/제목/위치/날짜/추가 별. */
private sealed interface DragTarget {
    data object Stage : DragTarget
    data object Title : DragTarget
    data object Location : DragTarget
    data object Date : DragTarget
    data class Extra(val index: Int) : DragTarget
}

/** 새 추가 별의 기본 배치 위치(겹치지 않게 순환). */
private val EXTRA_STAR_PRESETS = listOf(
    0.30f to 0.22f, 0.72f to 0.30f, 0.24f to 0.52f,
    0.76f to 0.58f, 0.50f to 0.18f, 0.34f to 0.68f,
)

/**
 * 공유 카드 편집 화면 — 공유 버튼을 누르면 뜨는 전체 화면 다이얼로그.
 * 미리보기에서 **별(+지도 무대)·제목·위치·날짜·추가 별을 각각 드래그**해 배치하고,
 * 내 다이어리의 별들을 가져와 장식으로 얹을 수 있다(개별 크기 조절/삭제).
 * 렌더 자산(동네 이름·지역 지도)은 1회 로드 후 재사용.
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
    var selectedExtra by remember { mutableStateOf<Int?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    // 내 다이어리의 별 스타일(type,color) 목록 — 피커 최초 오픈 시 1회 로드.
    var myStars by remember { mutableStateOf<List<Pair<Int, Int>>?>(null) }

    // 자산 1회 로드(역지오코딩 + 지역 지도 타일) → 이후 옵션 변경마다 로컬 렌더만.
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

    LaunchedEffect(showPicker) {
        if (showPicker && myStars == null) {
            val uid = GoogleAuthHelper.currentUserId
            myStars = if (uid == null) emptyList() else runCatching {
                withContext(Dispatchers.IO) {
                    FirebaseDiaryRepository().observeMyDiaries(uid).first()
                        .sortedByDescending { it.createdAt }
                        .map { it.starType to it.starColor }
                        .distinct()
                }
            }.getOrDefault(emptyList())
        }
    }

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
                    color = Color(0xFFF0F0F0), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { if (!busy) onDismiss() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close), tint = Color.White)
                }
            }

            // 미리보기 — 요소별 드래그 배치(무대/제목/위치/날짜/추가 별), 탭 = 추가 별 선택
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentAlignment = Alignment.Center
            ) {
                val bmp = preview
                if (bmp != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1080f / 1920f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val t = hitTarget(options, offset.x / size.width, offset.y / size.height)
                                        selectedExtra = (t as? DragTarget.Extra)?.index
                                    }
                                }
                                .pointerInput(Unit) {
                                    // 드래그 시작 지점에서 가장 가까운 요소를 잡아 델타로 이동(점프 없음)
                                    var target: DragTarget = DragTarget.Stage
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            target = hitTarget(options, offset.x / size.width, offset.y / size.height)
                                            (target as? DragTarget.Extra)?.let { selectedExtra = it.index }
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        val dx = dragAmount.x / size.width
                                        val dy = dragAmount.y / size.height
                                        options = moveTarget(options, target, dx, dy)
                                    }
                                }
                        )
                        // 선택된 추가 별 표시 — 점선 링
                        selectedExtra?.let { idx ->
                            options.extraStars.getOrNull(idx)?.let { star ->
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.75f),
                                        radius = (26.dp.toPx() * star.scale).coerceIn(14.dp.toPx(), 64.dp.toPx()),
                                        center = Offset(size.width * star.xFrac, size.height * star.yFrac),
                                        style = Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    com.chaminwoo.stary.core.ui.StarLoadingIndicator(size = 30.dp, color = accent)
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

            // 표시 토글 — 지도 / 위치 / 날짜 + 내 별 가져오기
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

            Spacer(Modifier.height(8.dp))

            // 내 다이어리에서 별 가져오기
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .clickable { showPicker = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.AutoAwesome, contentDescription = null,
                    tint = accent, modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.share_edit_import_stars), color = accent, fontSize = 12.sp)
            }

            Spacer(Modifier.height(10.dp))

            // 별 크기 슬라이더(주인공 별)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.share_edit_star_size),
                    color = Color(0xFF8A93A6), fontSize = 12.sp
                )
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = options.starScale,
                    onValueChange = { options = options.copy(starScale = it) },
                    valueRange = 0.25f..2.2f,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent.copy(alpha = 0.7f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // 선택된 추가 별 — 크기 슬라이더 + 삭제
            selectedExtra?.let { idx ->
                options.extraStars.getOrNull(idx)?.let { star ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StarShapeIcon(type = star.type, colorIndex = star.colorIndex, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.share_edit_extra_star_size),
                            color = Color(0xFF8A93A6), fontSize = 12.sp
                        )
                        Spacer(Modifier.width(12.dp))
                        Slider(
                            value = star.scale,
                            onValueChange = { v ->
                                options = options.copy(
                                    extraStars = options.extraStars.toMutableList().also { list ->
                                        list[idx] = star.copy(scale = v)
                                    }
                                )
                            },
                            valueRange = 0.25f..2.2f,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent.copy(alpha = 0.7f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            options = options.copy(
                                extraStars = options.extraStars.toMutableList().also { it.removeAt(idx) }
                            )
                            selectedExtra = null
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
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
                    if (busy) com.chaminwoo.stary.core.ui.StarLoadingIndicator(size = 18.dp, color = Color(0xFF0B0F18))
                    else Text(stringResource(R.string.share_to_story), fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                    Text(stringResource(R.string.share_as_image), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }

    // 내 별 피커 — 내 다이어리의 별 스타일(모양×색, 중복 제거) 중 하나를 골라 카드에 얹는다.
    if (showPicker) {
        Dialog(onDismissRequest = { showPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF121826))
                    .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Text(
                    stringResource(R.string.share_edit_pick_star),
                    color = Color(0xFFF0F0F0), fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(12.dp))
                val stars = myStars
                when {
                    stars == null -> Box(
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        com.chaminwoo.stary.core.ui.StarLoadingIndicator(size = 24.dp, color = accent)
                    }
                    stars.isEmpty() -> Text(
                        stringResource(R.string.share_edit_no_stars),
                        color = Color(0xFF8A93A6), fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(56.dp),
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(stars) { _, (type, colorIdx) ->
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable {
                                        val p = EXTRA_STAR_PRESETS[options.extraStars.size % EXTRA_STAR_PRESETS.size]
                                        val newIndex = options.extraStars.size
                                        options = options.copy(
                                            extraStars = options.extraStars +
                                                ShareCardHelper.ExtraStar(type, colorIdx, p.first, p.second)
                                        )
                                        selectedExtra = newIndex
                                        showPicker = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                StarShapeIcon(type = type, colorIndex = colorIdx, modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 드래그/탭 지점(정규화 좌표)에서 가장 가까운 요소 — 임계 반경 안에서 최근접, 없으면 무대. */
private fun hitTarget(options: ShareCardHelper.ShareCardOptions, fx: Float, fy: Float): DragTarget {
    var best: DragTarget = DragTarget.Stage
    var bestD = Float.MAX_VALUE
    fun consider(t: DragTarget, x: Float, y: Float, threshold: Float) {
        val dx = fx - x
        val dy = fy - y
        val d = dx * dx + dy * dy
        if (d < bestD && d <= threshold * threshold) {
            bestD = d
            best = t
        }
    }
    options.extraStars.forEachIndexed { i, s -> consider(DragTarget.Extra(i), s.xFrac, s.yFrac, 0.09f) }
    consider(DragTarget.Title, options.titleXFrac, options.titleYFrac, 0.11f)
    if (options.showLocation) consider(DragTarget.Location, options.locationXFrac, options.locationYFrac, 0.09f)
    if (options.showDate) consider(DragTarget.Date, options.dateXFrac, options.dateYFrac, 0.08f)
    consider(DragTarget.Stage, options.stageXFrac, options.stageYFrac, 0.30f)
    return best
}

/** 드래그 델타(정규화)를 대상 요소의 위치에 반영(렌더 클램프와 동일 범위). */
private fun moveTarget(
    options: ShareCardHelper.ShareCardOptions,
    target: DragTarget,
    dx: Float,
    dy: Float,
): ShareCardHelper.ShareCardOptions = when (target) {
    DragTarget.Stage -> options.copy(
        stageXFrac = (options.stageXFrac + dx).coerceIn(0.08f, 0.92f),
        stageYFrac = (options.stageYFrac + dy).coerceIn(0.08f, 0.90f),
    )
    DragTarget.Title -> options.copy(
        titleXFrac = (options.titleXFrac + dx).coerceIn(0.08f, 0.92f),
        titleYFrac = (options.titleYFrac + dy).coerceIn(0.05f, 0.95f),
    )
    DragTarget.Location -> options.copy(
        locationXFrac = (options.locationXFrac + dx).coerceIn(0.08f, 0.92f),
        locationYFrac = (options.locationYFrac + dy).coerceIn(0.05f, 0.96f),
    )
    DragTarget.Date -> options.copy(
        dateXFrac = (options.dateXFrac + dx).coerceIn(0.08f, 0.92f),
        dateYFrac = (options.dateYFrac + dy).coerceIn(0.05f, 0.97f),
    )
    is DragTarget.Extra -> {
        val list = options.extraStars.toMutableList()
        list.getOrNull(target.index)?.let { s ->
            list[target.index] = s.copy(
                xFrac = (s.xFrac + dx).coerceIn(0.04f, 0.96f),
                yFrac = (s.yFrac + dy).coerceIn(0.03f, 0.97f),
            )
        }
        options.copy(extraStars = list)
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
