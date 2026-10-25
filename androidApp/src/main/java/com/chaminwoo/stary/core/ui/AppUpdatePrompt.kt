package com.chaminwoo.stary.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.MinSans
import com.chaminwoo.stary.core.util.AppUpdateChecker


/**
 * 새 버전 **강제 업데이트** 팝업 호스트 — MainScreen 최상단에 1개. [AppUpdateChecker.showPrompt] 가 켜지면 뜬다.
 * 생김새는 화면 첫 진입 설명창([FirstVisitInfo])과 같은 카드(유리 판 + 달빛 뱃지 + 제목 + 설명 + 크림 버튼).
 * **닫을 수 없다**: "나중에" 버튼 없음, 바깥 탭 무시, 뒤로가기 무시 — 업데이트해야만 앱을 쓸 수 있다(2026-10-09).
 * (예외: 디버그 미리보기 [AppUpdateChecker.debugShow] 만 뒤로가기로 닫힌다.)
 */
@Composable
fun AppUpdatePromptHost() {
    if (!AppUpdateChecker.showPrompt) return
    val context = LocalContext.current
    Dialog(
        onDismissRequest = { AppUpdateChecker.dismiss() },
        properties = DialogProperties(
            dismissOnBackPress = AppUpdateChecker.dismissible,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        StaryPopupPanel(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StaryPopupBadge(Icons.Filled.SystemUpdate)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.update_title),
                color = Color(0xFFF0F0F0), fontFamily = MinSans, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.update_msg),
                color = Color(0xFFB8C0CC), fontFamily = MinSans, fontSize = 14.sp, lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            // 스토어로 보낼 뿐 팝업은 닫지 않는다 — 업데이트 없이 돌아와도 그대로 막혀 있어야 한다.
            StaryButton(stringResource(R.string.update_go), { AppUpdateChecker.openStore(context) }, Modifier.fillMaxWidth())
        }
    }
}
