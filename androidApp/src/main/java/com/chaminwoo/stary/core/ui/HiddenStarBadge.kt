package com.chaminwoo.stary.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.feature.profile.rememberHiddenAchievementsOf

/**
 * 히든 업적 달성자의 **이름 옆 전용 크리스탈 별**.
 *
 * 앱 전체에서 이름이 뜨는 곳(다이어리 작성자·댓글·친구 목록·채팅·프로필 …)에 붙어,
 * "이 사람은 이 히든 업적의 유일한 달성자" 임을 한눈에 보여준다.
 * 업적마다 (모양×색) 조합이 다르다 — `HiddenAchievement.badgeType/badgeColor`.
 *
 * - 달성한 히든이 없으면 **아무것도 그리지 않는다**(레이아웃 영향 없음).
 * - 프로필의 히든 아이콘([com.chaminwoo.stary.feature.profile.HiddenIconWithEffect])과 달리
 *   파티클 없이 정적 렌더 — 리스트 스크롤에서 가볍게.
 * - 별 비트맵은 (모양,색,px) 키로 캐시해 재사용(크리스탈 채움은 path 연산이 많아 매 프레임 렌더 금지).
 * - ⚠️ 익명 다이어리/댓글에는 붙이지 말 것(작성자 은닉이 깨진다).
 *
 * @param userId 배지를 조회할 사용자(비면 아무것도 안 그림)
 * @param max 최대 표시 개수(그 이상 달성해도 이름 옆이 길어지지 않게)
 */
@Composable
fun HiddenStarBadges(
    userId: String,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    max: Int = 3,
) {
    if (userId.isBlank()) return
    val achievements = rememberHiddenAchievementsOf(userId)
    if (achievements.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        achievements.take(max).forEach { ach ->
            HiddenStarBadge(type = ach.badgeType, colorIndex = ach.badgeColor, size = size)
        }
    }
}

/** 배지 별 1개(캐시된 비트맵). 업적 화면 등에서 단독으로도 쓸 수 있다. */
@Composable
fun HiddenStarBadge(type: Int, colorIndex: Int, size: Dp = 12.dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val bitmap = remember(type, colorIndex, px) { badgeBitmap(type, colorIndex, px) }
    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
}

/** (모양,색,px) → 크리스탈 별 비트맵. 프로세스 수명 캐시(종류가 유한: 히든 업적 수 × 몇 가지 크기). */
private val cache = HashMap<String, ImageBitmap>()

private fun badgeBitmap(type: Int, colorIndex: Int, px: Int): ImageBitmap =
    cache.getOrPut("$type/$colorIndex/$px") {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        StarStyle.drawCrystalFill(Canvas(bmp), type, colorIndex, 0f, 0f, px.toFloat())
        bmp.asImageBitmap()
    }
