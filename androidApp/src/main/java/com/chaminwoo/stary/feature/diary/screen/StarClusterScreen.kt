package com.chaminwoo.stary.feature.diary.screen

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
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
import coil.compose.AsyncImage
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

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 스크린 배경 — 친구 스크린과 동일(mydiary_bg 어둡게 틴트)
        Image(
            painter = painterResource(R.drawable.mydiary_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.82f), blendMode = BlendMode.Darken)
        )
        // 뒤로가기 — 좌상단
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 4.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = Color.White
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onBackground,
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
        val pagerState = rememberPagerState(pageCount = { diaries.size })

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(18.dp))

            // 헤더 — 겹쳐진 별 모양들을 살짝 겹쳐 보여주고 개수 안내
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                diaries.take(5).forEachIndexed { i, d ->
                    StarShapeIcon(
                        type = d.starType, colorIndex = d.starColor,
                        modifier = Modifier
                            .size(if (i == 0) 30.dp else 22.dp)
                            .offset(x = (-6 * i).dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.cluster_header, diaries.size),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
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
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 58.dp, vertical = 8.dp),
                pageSpacing = 18.dp,
                modifier = Modifier.weight(1f)
            ) { page ->
                val diary = diaries[page]
                // 부호 있는 오프셋: 우측(다음) = +1, 좌측(이전) = -1 방향
                val signed = ((page - pagerState.currentPage) - pagerState.currentPageOffsetFraction)
                    .coerceIn(-1f, 1f)
                val dist = signed.absoluteValue
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ClusterDiaryCard(
                        diary = diary,
                        rank = page + 1,
                        bgImage = cardBgImage,
                        modifier = Modifier
                            .fillMaxHeight(0.97f)
                            .aspectRatio(0.54f, matchHeightConstraintsFirst = true)
                            .graphicsLayer {
                                val s = 1f - 0.10f * dist
                                scaleX = s; scaleY = s
                                alpha = 1f - 0.42f * dist
                                rotationZ = 8f * signed
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                translationX = signed * 14.dp.toPx()
                                translationY = dist * 10.dp.toPx()
                            },
                        onClick = { onOpenDiary(diary.id) }
                    )
                }
            }

            // 페이지 인디케이터 점
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(diaries.size) { i ->
                    val active = pagerState.currentPage == i
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
 * 합쳐진 별 카드 — 좌우로 좁고 상하로 긴 세로 직사각형(포트레이트).
 * 배경은 공유 카드와 같은 밤하늘 프레임([bgImage]) + 가독성 스크림.
 * 미디어(사진 또는 큰 별) 영역이 남는 세로를 모두 차지하고, 하단에 간략 정보만:
 * 별/제목/날짜 + 하트·댓글 수. 탭 → 세부 화면.
 */
@Composable
private fun ClusterDiaryCard(
    diary: Diary,
    rank: Int,
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
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        // 미디어 영역 — 카드의 남는 세로 전체(세로로 긴 카드의 주인공)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (diary.imageUrl.isNotEmpty()) {
                // 카드 썸네일 — 원본 대신 512px 다운샘플(스와이프 시 빠른 표시)
                com.chaminwoo.stary.core.ui.ThumbAsyncImage(
                    model = diary.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    sizePx = 512,
                )
            } else {
                // 사진이 없으면 별을 큼직하게 — 카드의 주인공은 별
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.18f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    StarShapeIcon(type = diary.starType, colorIndex = diary.starColor, modifier = Modifier.size(84.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            StarShapeIcon(type = diary.starType, colorIndex = diary.starColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                diary.title.ifBlank { stringResource(R.string.common_untitled) },
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp, fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(6.dp))
        val dateStr = remember(diary.createdAt) {
            SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(diary.createdAt))
        }
        Text("#$rank · $dateStr", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)

        Spacer(Modifier.height(12.dp))

        // 간략 지표 — 하트/댓글 수만
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Favorite, contentDescription = null,
                tint = Color(0xFFFF6B81), modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text("${diary.likeCount}", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            Spacer(Modifier.width(16.dp))
            Icon(
                Icons.Filled.ChatBubbleOutline, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text("${diary.commentCount}", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
        }
        }
    }
}
