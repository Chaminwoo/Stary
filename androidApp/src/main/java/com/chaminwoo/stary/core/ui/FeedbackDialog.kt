package com.chaminwoo.stary.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaminwoo.stary.BuildConfig
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.util.LocaleManager
import com.chaminwoo.stary.data.repository.FeedbackRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import kotlinx.coroutines.launch

/**
 * "고칠 점 보내기" 입력 창 — 평점 요청 카드([ReviewPromptHost])와 설정 화면 행이 같이 쓴다. 로그인한 사용자에게만 연다.
 * 앞뒤 공백을 자르고, 비어 있으면 보내기 비활성, 최대 [FeedbackRepository.MAX_LENGTH]자. 보내는 동안 버튼이 도는 별이 되고
 * 창은 닫히지 않는다. 성공하면 토스트 + 닫힘, 실패하면 오류 토스트만 띄우고 **입력과 창을 그대로 둔다**.
 * 문자열은 이 컴포저블의 Context(LocalContext)로 만든다(§2.5.5 — 뷰모델이 아니라 화면에서).
 */
@Composable
fun FeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    StaryDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.feedback_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(FeedbackRepository.MAX_LENGTH) },
                    placeholder = { Text(stringResource(R.string.feedback_hint), color = TextMuted, fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    enabled = !sending,
                    minLines = 5,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TextMain.copy(alpha = 0.6f),
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.35f),
                        cursorColor = TextMain,
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain,
                        disabledTextColor = TextMuted,
                        disabledBorderColor = TextMuted.copy(alpha = 0.25f),
                    ),
                )
                Text(
                    "${text.length}/${FeedbackRepository.MAX_LENGTH}",
                    color = TextMuted, fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 2.dp),
                    textAlign = TextAlign.End,
                )
            }
        },
        confirmButton = {
            StaryButton(
                stringResource(R.string.feedback_send),
                onClick = {
                    val uid = GoogleAuthHelper.currentUserId
                    val message = text.trim()
                    if (uid == null || message.isEmpty() || sending) return@StaryButton
                    sending = true
                    scope.launch {
                        val ok = FeedbackRepository.send(
                            uid = uid,
                            message = message,
                            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            locale = LocaleManager.pushLanguage(),
                        )
                        sending = false
                        if (ok) {
                            StaryToast.show(context.getString(R.string.feedback_sent))
                            onDismiss()
                        } else {
                            // 실패: 입력과 창 유지 — 다시 누르면 재시도.
                            StaryToast.show(context.getString(R.string.feedback_failed))
                        }
                    }
                },
                compact = true,
                enabled = text.isNotBlank(),
                loading = sending,
            )
        },
        dismissButton = {
            StaryButton(
                stringResource(R.string.common_cancel), onDismiss,
                enabled = !sending, kind = StaryButtonKind.Ghost, compact = true,
            )
        },
    )
}
