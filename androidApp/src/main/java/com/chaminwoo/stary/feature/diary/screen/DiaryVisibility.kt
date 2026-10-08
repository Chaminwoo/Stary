package com.chaminwoo.stary.feature.diary.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R

/**
 * 공개 범위 선택지: (key, 라벨 문자열 리소스, 아이콘) — 지구본(전체) / 친구 / 자물쇠(나만).
 * 업로드 화면 선택기, 상세 화면 날짜 옆 아이콘, 수정 다이얼로그 선택기가 함께 쓴다. iOS `VisibilityViews.swift` 와 같은 구성.
 */
internal val VisibilityOptions = listOf(
    Triple("public", R.string.upload_vis_public, Icons.Filled.Public),
    Triple("friends", R.string.upload_vis_friends, Icons.Filled.People),
    Triple("private", R.string.upload_vis_private, Icons.Filled.Lock),
)

/** 알 수 없는 값(옛 문서 등)은 전체공개로 본다 — 앱 전체의 `visibilityType` 기본값과 같다. */
private fun optionFor(visibilityType: String) =
    VisibilityOptions.firstOrNull { it.first == visibilityType } ?: VisibilityOptions.first()

/** 상세 화면 날짜 옆의 공개 범위 아이콘 — 접근성 설명은 "전체공개/친구만/나만보기". */
@Composable
internal fun VisibilityBadge(
    visibilityType: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.secondary,
    size: Dp = 14.dp,
) {
    val (_, labelRes, icon) = optionFor(visibilityType)
    Icon(
        icon,
        contentDescription = stringResource(labelRes),
        tint = tint,
        modifier = modifier.size(size),
    )
}

/** 수정 다이얼로그의 공개 범위 선택 줄 — 업로드 화면 칩과 같은 모양(선택 = 남색 테두리 + 옅은 면). */
@Composable
internal fun VisibilityChooser(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(0xFF9FB3E8)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VisibilityOptions.forEach { (key, labelRes, icon) ->
            val isSelected = selected == key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                    .border(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) accent else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(key) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        icon, null,
                        tint = if (isSelected) accent else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(labelRes),
                        fontSize = 12.sp,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}
