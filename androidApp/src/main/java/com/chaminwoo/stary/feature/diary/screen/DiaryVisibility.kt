package com.chaminwoo.stary.feature.diary.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.StarySegmented

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

/** 수정 다이얼로그의 공개 범위 선택 줄 — 업로드 화면과 같은 면 선택 줄([StarySegmented]). */
@Composable
internal fun VisibilityChooser(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StarySegmented(
        options = VisibilityOptions.map { (_, labelRes, icon) -> stringResource(labelRes) to icon },
        selected = VisibilityOptions.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        onSelect = { onSelect(VisibilityOptions[it].first) },
        modifier = modifier.fillMaxWidth(),
    )
}
