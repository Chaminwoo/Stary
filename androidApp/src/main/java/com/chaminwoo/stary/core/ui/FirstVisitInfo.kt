package com.chaminwoo.stary.core.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chaminwoo.stary.R

private val Mint = Color(0xFF6EE7B7)
private val Blue = Color(0xFF3B82F6)
private const val ONBOARDING_PREFS = "stary_onboarding"

/**
 * 화면에 **처음 들어왔을 때 1회** 설명 다이얼로그를 띄운다.
 * [seenKey] 로 `stary_onboarding` prefs 에 본 적 있는지 기록(코치마크와 같은 prefs 파일).
 * Dialog 는 자체 윈도우라 호출 위치(레이아웃)는 무관 — 화면 컴포저블 본문 어디서든 호출하면 된다.
 */
@Composable
fun FirstVisitInfo(
    seenKey: String,
    icon: ImageVector,
    title: String,
    message: String,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE) }
    var show by rememberSaveable(seenKey) { mutableStateOf(!prefs.getBoolean(seenKey, false)) }
    if (!show) return

    val dismiss = {
        prefs.edit().putBoolean(seenKey, true).apply()
        show = false
    }
    // 폭을 직접 제어(usePlatformDefaultWidth=false) — 한 줄 20글자가 한 줄에 들어가도록 넉넉히.
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF121821))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Mint.copy(alpha = 0.6f), Blue.copy(alpha = 0.45f))),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 아이콘 뱃지 (민트→블루 그라데이션 원)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Mint, Blue))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color(0xFFF0F0F0), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                color = Color(0xFFB8C0CC),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Mint, Blue)))
                    .clickable(onClick = dismiss)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.onb_button),
                    color = Color(0xFF06121E),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
