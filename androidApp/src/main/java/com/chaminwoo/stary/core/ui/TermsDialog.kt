package com.chaminwoo.stary.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StaryColors
import com.chaminwoo.stary.shared.config.StaryConfig

/**
 * 이용약관(EULA) 다이얼로그 — App Store Guideline 1.2 대응(2026-09-26). iOS `TermsDialogCard` 패리티.
 *
 * - [requireAgreement] = true : 로그인 전 동의 게이트. 바깥 탭·뒤로가기로 닫히지 않고
 *   "동의하고 계속"([onAgree]) 또는 [declineLabel]([onDismiss]) 중 하나를 골라야 한다.
 * - false : 설정 > 이용약관 보기 전용(확인 버튼 하나).
 * 본문은 무관용 원칙 · 자동 필터 · 신고/차단 · 24시간 내 조치를 담는다(`terms_body` ko/en/ja).
 * 판은 다른 팝업과 같은 유리([StaryPopupPanel]), 동의는 크림 캡슐([StaryButton]) — 2026-10-26 민트 그라데이션에서 바꿈.
 */
@Composable
fun TermsDialog(
    requireAgreement: Boolean,
    onDismiss: () -> Unit,
    onAgree: () -> Unit = {},
    declineLabel: String = stringResource(R.string.terms_decline),
) {
    Dialog(
        onDismissRequest = { if (!requireAgreement) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !requireAgreement,
            dismissOnClickOutside = !requireAgreement,
            usePlatformDefaultWidth = false,
        ),
    ) {
        StaryPopupPanel(maxWidth = 360.dp) {
            Text(stringResource(R.string.terms_title), color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (requireAgreement) {
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.terms_intro), color = StaryColors.Accent, fontSize = 13.sp)
            }
            Spacer(Modifier.size(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(stringResource(R.string.terms_body), color = TextMain.copy(alpha = 0.86f), fontSize = 13.sp, lineHeight = 20.sp)
            }
            Spacer(Modifier.size(10.dp))
            // 위치기반서비스 이용약관 · 개인정보 처리방침 — 동의 전에 열어 볼 수 있어야 한다(위치정보법 §18 · 개인정보보호법 §30). iOS TermsDialogCard 패리티.
            val uriHandler = LocalUriHandler.current
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PolicyLink(stringResource(R.string.settings_location_terms)) {
                    runCatching { uriHandler.openUri(StaryConfig.LOCATION_TERMS_URL) }
                }
                PolicyLink(stringResource(R.string.settings_privacy)) {
                    runCatching { uriHandler.openUri(StaryConfig.PRIVACY_POLICY_URL) }
                }
            }
            if (requireAgreement) {
                Spacer(Modifier.size(8.dp))
                // 한 번의 동의에 무엇이 들어 있는지 — 만 14세 이상(개인정보보호법 §22-2) · 두 약관 · 처리방침 확인.
                Text(stringResource(R.string.terms_consent_note), color = TextMuted, fontSize = 11.5.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.size(16.dp))
            if (requireAgreement) {
                // 동의가 주 동작 — 넓은 캡슐 버튼, 거절은 그 아래 작은 글씨.
                StaryButton(stringResource(R.string.terms_agree), onAgree, Modifier.fillMaxWidth())
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(declineLabel, color = TextMuted, fontSize = 13.sp)
                }
            } else {
                StaryButton(stringResource(R.string.common_confirm), onDismiss, Modifier.fillMaxWidth(), compact = true)
            }
        }
    }
}

/** 약관 창 아래 작은 밑줄 링크 — 브라우저로 연다(web/privacy.html · web/location-terms.html). */
@Composable
private fun PolicyLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = StaryColors.Accent,
        fontSize = 12.sp,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    )
}
