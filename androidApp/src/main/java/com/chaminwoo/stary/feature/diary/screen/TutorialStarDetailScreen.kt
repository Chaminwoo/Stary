package com.chaminwoo.stary.feature.diary.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.LocalTopBarInset
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.util.ImageCropHelper
import com.chaminwoo.stary.core.util.TutorialStarState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Mint = Color(0xFF6EE7B7)
private val Blue = Color(0xFF3B82F6)

/**
 * 웰컴 별([TutorialStarState]) 게시물 — 지도에서 튜토리얼 별을 탭하면 일반 별처럼 Detail 라우트로 오고,
 * NavGraph 가 diaryId 가 [TutorialStarState.DIARY_ID] 일 때 [DetailScreen] 대신 이 화면을 띄운다.
 *
 * [DetailScreen] 을 재사용하지 않는 이유: 그쪽은 Firestore 로드·조회수·좋아요/댓글 리스너가 전부 diaryId 에
 * 묶여 있어 서버에 없는 이 글로는 "불러오기 실패"가 뜨거나 가짜 id 로 쓰기가 나간다. 그래서 레이아웃
 * (4:3 히어로 + 하단 스크림 + 별·작성자·날짜 오버레이 → 제목 → 본문 카드)만 똑같이 흉내 낸다.
 * DetailScreen 레이아웃을 크게 바꾸면 여기도 맞춰 줄 것.
 *
 * - 히어로 미디어 = 별 로딩 플레이스홀더(북두칠성이 그려지는 애니메이션 WebP, [R.drawable.loading_dipper],
 *   640×480 = 히어로와 같은 4:3). 실제 글에선 사진이 뜨기 전에 잠깐 보이는 그 영상을 이 별의 "사진"으로 둔다.
 *   ⚠️ 애니메이션 WebP 라 painterResource 불가 — 전역 Coil 로더로 그린다(API 28 미만은 첫 프레임 정지).
 * - 화면이 열리는 순간 [TutorialStarState.markDone] 으로 영구 소비한다. 이 화면이 지도를 가리고 있을 때
 *   마커가 빠지므로 돌아가면 별은 이미 없다(나갈 때 지우면 퇴장 페이드 중에 별이 뚝 사라지는 게 보인다).
 * - 좋아요/공유/댓글은 없다(가짜 글이라 눌러도 갈 곳이 없음) — 대신 "확인" 버튼으로 지도에 돌아간다.
 */
@Composable
fun TutorialStarDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { TutorialStarState.markDone(context) }

    val accent = StarStyle.colorOf(TutorialStarState.STAR_COLOR)
    // 방금 근처에 놓아 둔 별이라는 설정 — 날짜는 여는 시점. 형식은 DetailScreen 과 같게.
    val createdStr = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date()) }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = LocalTopBarInset.current)
        ) {
            // ── 헤더: 별자리 플레이스홀더 영상 + 하단 스크림 + 별/작성자/날짜 오버레이 ──
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(ImageCropHelper.ASPECT)) {
                AsyncImage(
                    model = R.drawable.loading_dipper,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

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

                // 작성자는 시스템(STARY)이라 프로필 진입은 없다.
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StarShapeIcon(
                        type = TutorialStarState.STAR_TYPE,
                        colorIndex = TutorialStarState.STAR_COLOR,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        TutorialStarState.AUTHOR_NAME,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                    )
                    Text("  ·  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                    Text(createdStr, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }

            // ── 본문 영역 ──
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    stringResource(R.string.tutorial_star_title),
                    fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground, lineHeight = 30.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 본문 카드 — DetailScreen 과 같은 배경/별색 그라데이션 테두리.
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
                        stringResource(R.string.tutorial_star_msg),
                        fontSize = 16.sp, lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 확인 → 지도로. 코치마크/첫 진입 안내(FirstVisitInfo)와 같은 민트→블루 버튼.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Mint, Blue)))
                        .clickable(onClick = onBack)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.tutorial_star_confirm),
                        color = Color(0xFF06121E),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
