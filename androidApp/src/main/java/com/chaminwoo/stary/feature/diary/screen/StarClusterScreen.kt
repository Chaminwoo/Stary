package com.chaminwoo.stary.feature.diary.screen

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.data.local.DiaryCache
import com.chaminwoo.stary.data.repository.FirebaseDiaryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import com.chaminwoo.stary.core.designsystem.LocalScreenSize
import com.chaminwoo.stary.core.designsystem.staryContentWidth
import kotlin.math.abs

/**
 * 30m 안에서 합쳐진 별 무리 열람 화면 — 합쳐진 다이어리들을 우선순위
 * (좋아요 내림차순 → 오래된 순, 지도 대표 선정과 동일) 순서의 **좌우 스와이프 카드**로 보여준다.
 * 카드는 간략 정보(별/제목/하트/댓글 수)만, 탭하면 세부(Detail) 화면으로 이동한다.
 */
@Composable
fun StarClusterScreen(
    ids: List<String>,
    onOpenDiary: (String) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var diaries by remember(ids) { mutableStateOf<List<Diary>>(emptyList()) }
    var isLoading by remember(ids) { mutableStateOf(true) }
    val repository = remember { FirebaseDiaryRepository() }
    val context = LocalContext.current

    LaunchedEffect(ids) {
        val loaded = ids.mapNotNull { id -> DiaryCache.get(id) ?: repository.getDiaryById(id) }
        // 지도 대표 선정과 같은 우선순위로 재정렬(캐시 시점 차이 방어)
        diaries = loaded.sortedWith(
            compareByDescending<Diary> { it.likeCount }.thenBy { it.createdAt }.thenBy { it.id }
        )
        isLoading = false
    }

    // 카드 배경 — 공유 카드와 같은 AI 밤하늘 프레임(share_card_bg.webp). 실패 시 기존 톤.
    val cardBgImage = remember {
        runCatching {
            context.assets.open("share_card_bg.webp").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.asImageBitmap()
    }

    Box(modifier = modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        // 스크린 배경 — 친구 스크린과 동일(mydiary_bg 어둡게 틴트)
        Image(
            painter = painterResource(R.drawable.mydiary_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(
                Color.Black.copy(alpha = 0.82f),
                blendMode = BlendMode.Darken
            )
        )
        // 뒤로가기 — 좌상단
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 4.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = Color.White
            )
        }
        if (isLoading) {
            com.chaminwoo.stary.core.ui.StarLoadingIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }
        if (diaries.isEmpty()) {
            Text(
                stringResource(R.string.detail_load_failed),
                color = MaterialTheme.colorScheme.secondary, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

        val rep = diaries.first()
        val accent = StarStyle.colorOf(rep.starColor)
        val listState = rememberLazyListState()
        val currentIndex by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val viewportCenter =
                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f

                layoutInfo.visibleItemsInfo
                    .minByOrNull { item ->
                        abs((item.offset + item.size / 2f) - viewportCenter)
                    }
                    ?.index ?: 0
            }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(18.dp))

            // 헤더 — 겹쳐진 별 모양들. 카드를 스와이프하면 **지금 보고 있는 별만 밝아지고 커진다**.
            //  · 4개 이하: 예전처럼 살짝 겹친 고정 배치.
            //  · 5개 이상: 고정 배치로는 6번째부터 밀려서 안 보이므로, **다이얼처럼**
            //    현재 별이 항상 가운데 오도록 좌우로 흘려보낸다([ClusterStarDial]).
            if (diaries.size >= 5) {
                ClusterStarDial(
                    diaries = diaries,
                    currentIndex = currentIndex,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    diaries.forEachIndexed { i, d ->
                        val active = currentIndex == i
                        val emphasis by animateFloatAsState(
                            targetValue = if (active) 1f else 0f,
                            animationSpec = tween(220),
                            label = "cluster_header_emphasis",
                        )
                        val base = if (i == 0) 30.dp else 22.dp
                        Box(
                            modifier = Modifier
                                .size(base)
                                .offset(x = (-6 * i).dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // 활성 별에만 옅은 후광 — 별색 그대로.
                            if (emphasis > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = 0.35f * emphasis }
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    StarStyle.colorOf(d.starColor).copy(alpha = 0.9f),
                                                    Color.Transparent,
                                                )
                                            ),
                                            CircleShape
                                        )
                                )
                            }
                            StarShapeIcon(
                                type = d.starType, colorIndex = d.starColor,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = 0.35f + 0.65f * emphasis
                                        val s = 1f + 0.15f * emphasis
                                        scaleX = s; scaleY = s
                                    }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.cluster_header, diaries.size),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.cluster_hint),
                color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(20.dp))

            // 카드 페이저 — 가운데 카드는 좌우로 좁고 상하로 긴 직사각형(세로 카드).
            // 옆 카드는 바닥 중앙 피벗으로 우측(다음)=시계 / 좌측(이전)=반시계 회전해
            // 위쪽이 바깥으로 기울고, 바깥 밀기+축소를 더해 가운데 카드와 겹치지 않는다.

            // ⚠️ raw `LocalConfiguration.screenWidthDp` 를 쓰면 안 된다 — StaryTheme 이 density 를
            // 스케일하므로 Configuration 의 원본 dp 와 좌표계가 다르다. 폭은 실제 컨테이너 기준인
            // [staryContentWidth](태블릿 폭 상한 반영), 높이는 [LocalScreenSize] 를 쓴다.
            val screenWidth = staryContentWidth()
            // 화면 비율 기반이라 태블릿에서 카드가 과도하게 커지는 것을 막는 상한.
            // 상한값은 일반 폰 화면에서는 절대 걸리지 않도록 여유 있게 잡았다(기존 비율 그대로 유지).
            val cardWidth = (screenWidth * 0.61f).coerceAtMost(300.dp)
            val cardHeight = (LocalScreenSize.current.height * 0.52f).coerceAtMost(560.dp)

            val horizontalPadding = (screenWidth - cardWidth) / 2
            val cardWidthPx = with(LocalDensity.current) { cardWidth.toPx() }


            val snapLayoutInfoProvider = remember(listState) {
                SnapLayoutInfoProvider(listState, SnapPosition.Center)
            }
            val flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider)

            LazyRow(
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(top = 20.dp)
            ){
                itemsIndexed(diaries, key = { _, diary -> diary.id }) { index, diary ->
                    val layoutInfo = listState.layoutInfo
                    val viewportCenter =
                        (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f

                    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    val cardCenter = itemInfo?.let { it.offset + it.size / 2f } ?: viewportCenter

                    val dist =
                        (((cardCenter - viewportCenter) / cardWidthPx).coerceIn(-1f, 1f))
                    val signed = dist

                    ClusterDiaryCard(
                        diary = diary,
                        bgImage = cardBgImage,
                        modifier = Modifier
                            .width(cardWidth)
                            .height(cardHeight)
                            .graphicsLayer {
                                val s = 1.13f - 0.23f * dist.absoluteValue
                                scaleX = s
                                scaleY = s
                                alpha = 1f - 0.42f * dist.absoluteValue
                                rotationZ = 8f * signed
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                translationX = signed * 14.dp.toPx()
                                translationY = dist.absoluteValue * 10.dp.toPx()
                            },
                        onClick = { onOpenDiary(diary.id) }
                    )
                }
            }

            // 페이지 인디케이터 점
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 18.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(diaries.size) { i ->
                    val active = currentIndex == i
                    Box(
                        modifier = Modifier
                            .size(if (active) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (active) accent else Color.White.copy(alpha = 0.25f))
                    )
                }
            }
        }
    }
}

/**
 * 겹친 별 헤더 다이얼 — 별이 5개 이상일 때 쓴다.
 *
 * 창(window)은 항상 5칸이고, **현재 카드의 별이 가운데 칸**에 오도록 전체가 옆으로 흐른다
 * (카드를 넘기면 다이얼이 한 칸씩 돌아가는 느낌). 가운데에서 멀수록 작아지고 흐려지며,
 * 창 밖으로 나간 별은 잘려 사라진다 — 6번째 이후 별도 자기 차례엔 반드시 보인다.
 * (iOS `StarClusterView.starDial` 패리티 — 값 drift 금지.)
 */
@Composable
private fun ClusterStarDial(
    diaries: List<Diary>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    /** 칸 간격(dp) — 창 5칸이 헤더 가운데 들어오는 폭. */
    val slot = 30.dp
    /** 가운데 기준 좌우로 그릴 칸 수(창 밖 여유 1칸 포함). */
    val span = 3
    val slotPx = with(LocalDensity.current) { slot.toPx() }
    // 카드 스냅과 같은 결로 부드럽게 도는 위치(정수 index → 실수 위치).
    val dialPos by animateFloatAsState(
        targetValue = currentIndex.toFloat(),
        animationSpec = tween(260),
        label = "cluster_dial_pos",
    )
    Box(
        modifier = modifier
            .width(slot * 5)
            .height(38.dp)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        for (off in -span..span) {
            val i = currentIndex + off
            val d = diaries.getOrNull(i) ?: continue
            val delta = i - dialPos
            val dist = abs(delta)
            // 가운데(=0)에서 한 칸 벗어나면 강조가 0 이 된다.
            val emphasis = (1f - dist).coerceIn(0f, 1f)
            // 창(가운데 ±2.5칸) 가장자리에서 서서히 사라진다.
            val edgeFade = (2.5f - dist).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer {
                        translationX = delta * slotPx
                        val s = 0.85f + 0.35f * emphasis
                        scaleX = s; scaleY = s
                        alpha = (0.35f + 0.65f * emphasis) * edgeFade
                    },
                contentAlignment = Alignment.Center
            ) {
                // 활성 별에만 옅은 후광 — 별색 그대로(고정 배치와 동일 값).
                if (emphasis > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 0.35f * emphasis }
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        StarStyle.colorOf(d.starColor).copy(alpha = 0.9f),
                                        Color.Transparent,
                                    )
                                ),
                                CircleShape
                            )
                    )
                }
                StarShapeIcon(
                    type = d.starType, colorIndex = d.starColor,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * 합쳐진 별 카드 — 좌우로 좁고 상하로 긴 세로 직사각형(포트레이트).
 * 배경은 공유 카드와 같은 밤하늘 프레임([bgImage]) + 가독성 스크림.
 * 미디어(사진 또는 큰 별) 영역이 남는 세로를 모두 차지하고, 하단에 간략 정보만:
 * 별/제목/날짜 + 하트·댓글 수. 탭 → 세부 화면.
 */
@Composable
private fun ClusterDiaryCard(
    diary: Diary,
    bgImage: ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = StarStyle.colorOf(diary.starColor)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE614181F))
            .clickable { onClick() }
    ) {
        // 카드 배경 = 공유 카드 프레임(밤하늘) + 아래로 갈수록 짙은 스크림(텍스트 가독성)
        if (bgImage != null) {
            Image(
                bitmap = bgImage,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x3D05070D), Color(0x2905070D), Color(0xB805070D))
                        )
                    )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // 별 영역 — 카드의 남는 세로 전체. 사진/영상은 띄우지 않고 항상 별만 크게 보여준다.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.radialGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent))
                    ),
                contentAlignment = Alignment.Center
            ) {
                StarShapeIcon(
                    type = diary.starType,
                    colorIndex = diary.starColor,
                    modifier = Modifier.size(84.dp)
                )
            }
            Spacer(Modifier.height(12.dp))

            Text(
                text = diary.title.ifBlank { stringResource(R.string.common_untitled) },
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = diary.userName,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1
            )

            Spacer(Modifier.height(8.dp))

            val dateStr = remember(diary.createdAt) {
                SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(diary.createdAt))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFF6B81).copy(alpha = 0.28f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )

                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFFF6B81),
                        modifier = Modifier.size(13.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))

                Text(
                    text = "${diary.likeCount}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.width(14.dp))

                Icon(
                    Icons.Filled.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(13.dp)
                )

                Spacer(Modifier.width(4.dp))

                Text(
                    text = "${diary.commentCount}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = dateStr,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

