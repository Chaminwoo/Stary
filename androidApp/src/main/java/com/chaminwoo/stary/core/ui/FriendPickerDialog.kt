package com.chaminwoo.stary.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.R
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text(
                stringResource(R.string.filter_pick_friends),
                color = Color(0xFFF0F0F0),
                fontSize = 16.sp
            )
        },
        text = {
            if (friends.isEmpty()) {
                Text(
                    stringResource(R.string.filter_no_friends),
                    color = Color(0xFF8A8A8A),
                    fontSize = 14.sp
                )
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
                                    checkedColor = Color(0xFF9FB3E8),
                                    uncheckedColor = Color(0xFF8A8A8A)
                                )
                            )
                            Text(
                                com.chaminwoo.stary.core.util.rememberCurrentUserName(
                                    friend.userId, friend.userName
                                ).ifBlank { friend.userId.take(8) },
                                color = Color(0xFFF0F0F0), fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(tempSelected) }) {
                Text(stringResource(R.string.filter_apply), color = Color(0xFF9FB3E8))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = Color(0xFF8A8A8A))
            }
        }
    )
}
