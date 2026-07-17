package com.chaminwoo.stary.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chaminwoo.stary.R

private val Mint = Color(0xFF6EE7B7)
private val Blue = Color(0xFF3B82F6)
// TextMain / TextMuted 는 같은 패키지 StaryComponents.kt 의 공개 색을 사용한다(중복 정의 금지).

/** 신고 사유 — (저장 키, 표시 문자열 res). */
private val REPORT_REASONS = listOf(
    "spam" to R.string.report_reason_spam,
    "abuse" to R.string.report_reason_abuse,
    "inappropriate" to R.string.report_reason_inappropriate,
    "impersonation" to R.string.report_reason_impersonation,
    "other" to R.string.report_reason_other,
)

/**
 * 신고 사유 선택 다이얼로그. [onSubmit] 에 선택한 사유 키("spam" 등)를 넘긴다.
 * 다이어리/댓글/사용자 신고에 공용.
 */
@Composable
fun ReportDialog(
    title: String,
    onSubmit: (reasonKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF121821))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Mint.copy(alpha = 0.5f), Blue.copy(alpha = 0.4f))),
                    RoundedCornerShape(22.dp)
                )
                .padding(18.dp)
        ) {
            Text(title, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(12.dp))
            REPORT_REASONS.forEach { (key, resId) ->
                val isSel = selected == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selected = key }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isSel) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                        contentDescription = null,
                        tint = if (isSel) Mint else TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(resId), color = if (isSel) TextMain else TextMuted, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = TextMuted) }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    enabled = selected != null,
                    onClick = { selected?.let(onSubmit) }
                ) { Text(stringResource(R.string.report_submit), color = Color(0xFFFF6B6B), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
