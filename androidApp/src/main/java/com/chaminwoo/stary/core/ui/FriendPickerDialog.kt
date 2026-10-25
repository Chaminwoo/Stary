package com.chaminwoo.stary.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StaryColors
import com.chaminwoo.stary.core.model.Friend

/**
 * 친구 선택(다중) 다이얼로그 — 지도 필터 "친구 선택"과 별 도감 필터가 공용.
 * 체크는 임시 상태로 들고 있다가 "적용"을 눌러야 [onApply] 로 넘긴다(취소하면 그대로).
 */
@Composable
fun FriendPickerDialog(
    friends: List<Friend>,
    initial: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var tempSelected by remember { mutableStateOf(initial) }
    StaryDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_pick_friends)) },
        text = {
            if (friends.isEmpty()) {
                Text(stringResource(R.string.filter_no_friends))
            } else {
                Column {
                    friends.forEach { friend ->
                        val checked = friend.userId in tempSelected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    tempSelected = if (it) tempSelected + friend.userId
                                    else tempSelected - friend.userId
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = StaryColors.Accent,
                                    uncheckedColor = StaryColors.Muted
                                )
                            )
                            Text(
                                com.chaminwoo.stary.core.util.rememberCurrentUserName(
                                    friend.userId, friend.userName
                                ).ifBlank { friend.userId.take(8) },
                                color = StaryColors.Text, fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { StaryButton(stringResource(R.string.filter_apply), { onApply(tempSelected) }, compact = true) },
        dismissButton = {
            StaryButton(stringResource(R.string.common_cancel), onDismiss, kind = StaryButtonKind.Ghost, compact = true)
        }
    )
}
