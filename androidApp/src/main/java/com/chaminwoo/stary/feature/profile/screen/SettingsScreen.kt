package com.chaminwoo.stary.feature.profile.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.PoorStory
import com.chaminwoo.stary.core.ui.StarShapeIcon
import com.chaminwoo.stary.core.util.AppSettings
import com.chaminwoo.stary.core.util.LocaleManager
import com.chaminwoo.stary.core.util.MusicManager

private val Mint = Color(0xFF6EE7B7)
private val Blue = Color(0xFF3B82F6)
private val TextMain = Color(0xFFF0F0F0)
private val TextMuted = Color(0xFF8A8A8A)
private val CardBg = Color(0xCC121821)
private val AccentBrush = Brush.linearGradient(listOf(Mint, Blue))

/**
 * 설정 화면 — 배경음악/효과음 볼륨, 알림 팝업 on/off, 언어 변경.
 * 우주 배경 + 글래스 카드(민트→블루 그라데이션 테두리)로 앱 톤에 맞춤.
 * 값은 [MusicManager]/[AppSettings]/[LocaleManager] 에 즉시 저장되어 전 화면에 반영된다.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // 우주 배경(어둡게 틴트) — 프로필/내 다이어리와 동일 톤
        Image(
            painter = painterResource(R.drawable.mydiary_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.84f), blendMode = BlendMode.Darken)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // ── 사운드 ──
            SectionLabel(stringResource(R.string.settings_sound), Icons.Filled.MusicNote)
            GlassCard {
                ToggleRow(
                    icon = Icons.Filled.MusicNote,
                    label = stringResource(R.string.settings_bgm),
                    description = stringResource(R.string.settings_bgm_desc),
                    checked = MusicManager.enabled,
                    onCheckedChange = { MusicManager.setActive(it) }
                )
                RowDivider()
                VolumeRow(
                    label = stringResource(R.string.settings_bgm_volume),
                    value = MusicManager.musicVolume,
                    enabled = MusicManager.enabled,
                    onValueChange = { MusicManager.updateMusicVolume(it) }
                )
                RowDivider()
                VolumeRow(
                    label = stringResource(R.string.settings_sfx_volume),
                    value = MusicManager.sfxVolume,
                    enabled = true,
                    icon = Icons.Filled.GraphicEq,
                    onValueChange = { MusicManager.updateSfxVolume(it) }
                )
            }

            // ── 알림 ──
            SectionLabel(stringResource(R.string.settings_notification), Icons.Filled.Notifications)
            GlassCard {
                ToggleRow(
                    icon = if (AppSettings.notificationsEnabled) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                    label = stringResource(R.string.settings_notif_popup),
                    description = stringResource(R.string.settings_notif_popup_desc),
                    checked = AppSettings.notificationsEnabled,
                    onCheckedChange = { AppSettings.updateNotificationsEnabled(it) }
                )
            }

            // ── 언어 ──
            SectionLabel(stringResource(R.string.settings_language), Icons.Filled.Language)
            GlassCard {
                NavRow(
                    icon = Icons.Filled.Language,
                    label = stringResource(R.string.settings_language),
                    description = stringResource(R.string.settings_language_desc),
                    value = languageLabel(LocaleManager.getLanguageTag(context)),
                    onClick = { showLanguageDialog = true }
                )
            }

            // 하단 안내 문구
            Text(
                stringResource(R.string.settings_autosave),
                color = TextMuted.copy(alpha = 0.7f),
                fontFamily = PoorStory,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        if (showLanguageDialog) {
            LanguageDialog(
                current = LocaleManager.getLanguageTag(context),
                onDismiss = { showLanguageDialog = false },
                onSelect = { tag ->
                    showLanguageDialog = false
                    if (tag != LocaleManager.getLanguageTag(context)) {
                        LocaleManager.setLanguageTag(context, tag)
                        // 액티비티를 다시 만들어 새 로케일로 전체 UI 를 다시 그린다.
                        context.findActivity()?.recreate()
                    }
                }
            )
        }
    }
}

/** 태그 → 표시 이름. 시스템 기본만 현재 언어로 번역, 나머지는 각 언어의 고유 표기. */
@Composable
private fun languageLabel(tag: String): String = when (tag) {
    "ko" -> stringResource(R.string.language_ko)
    "en" -> stringResource(R.string.language_en)
    "ja" -> stringResource(R.string.language_ja)
    else -> stringResource(R.string.language_system)
}

@Composable
private fun LanguageDialog(current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF121821))
                .border(1.dp, Brush.linearGradient(listOf(Mint.copy(alpha = 0.5f), Blue.copy(alpha = 0.4f))), RoundedCornerShape(22.dp))
                .padding(vertical = 18.dp, horizontal = 8.dp)
        ) {
            Text(
                stringResource(R.string.language_dialog_title),
                color = TextMain, fontFamily = PoorStory, fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
            )
            LocaleManager.SUPPORTED.forEach { tag ->
                val selected = tag == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(tag) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        languageLabel(tag),
                        color = if (selected) Mint else TextMain,
                        fontFamily = PoorStory, fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Mint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** ContextWrapper 체인을 거슬러 올라가 Activity 를 찾는다(언어 변경 후 recreate 용). */
private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** 섹션 제목 — 작은 그라데이션 아이콘 + 민트 라벨. */
@Composable
private fun SectionLabel(text: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Mint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text, color = Mint, fontFamily = PoorStory,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
        )
    }
}

/** 민트→블루 그라데이션 테두리의 다크 글래스 카드. */
@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .border(
                1.dp,
                Brush.linearGradient(listOf(Mint.copy(alpha = 0.5f), Blue.copy(alpha = 0.4f))),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) { content() }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

/** 원형 민트 아이콘 뱃지. */
@Composable
private fun IconBadge(icon: ImageVector, active: Boolean = true) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) Mint.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (active) Mint.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (active) Mint else Color(0xFF666666),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, active = checked)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextMain, fontFamily = PoorStory, fontSize = 17.sp)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, color = TextMuted, fontFamily = PoorStory, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                { Icon(Icons.Filled.Check, null, modifier = Modifier.size(14.dp), tint = Color(0xFF0D1117)) }
            } else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0D1117),
                checkedTrackColor = Mint,
                checkedBorderColor = Mint,
                uncheckedThumbColor = Color(0xFF8A8A8A),
                uncheckedTrackColor = Color(0xFF1C232E),
                uncheckedBorderColor = Color(0xFF3A434F),
            )
        )
    }
}

/** 탭하면 화면/다이얼로그로 이동하는 행(아이콘 + 라벨 + 현재값 + ›). */
@Composable
private fun NavRow(
    icon: ImageVector,
    label: String,
    description: String? = null,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextMain, fontFamily = PoorStory, fontSize = 17.sp)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, color = TextMuted, fontFamily = PoorStory, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(value, color = Mint, fontFamily = PoorStory, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeRow(
    label: String,
    value: Float,
    enabled: Boolean,
    icon: ImageVector? = null,
    onValueChange: (Float) -> Unit,
) {
    // 볼륨에 따라 아이콘이 변한다(0=음소거, 그 외 볼륨업). 효과음 행은 전달된 아이콘 우선.
    val rowIcon = icon ?: if (value <= 0.01f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(rowIcon, active = enabled)
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                color = if (enabled) TextMain else Color(0xFF5A5A5A),
                fontFamily = PoorStory, fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
            // % 칩
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (enabled) Mint.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    "${(value * 100).toInt()}%",
                    color = if (enabled) Mint else Color(0xFF666666),
                    fontFamily = PoorStory, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Mint,
                activeTrackColor = Mint,
                inactiveTrackColor = Color(0xFF1C232E),
                disabledThumbColor = Color(0xFF555555),
                disabledActiveTrackColor = Color(0xFF3A434F),
                disabledInactiveTrackColor = Color(0xFF181D25),
            ),
            // 핸들(thumb)을 원형 대신 별 모양으로 — 앱 테마(별)에 맞춤.
            thumb = {
                Box(
                    modifier = Modifier.size(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StarShapeIcon(
                        type = 1, // 5각 별
                        color = if (enabled) Mint else Color(0xFF555555),
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            // 그라데이션 활성 트랙 — 민트→블루로 채워져 더 예쁘게.
            track = { state: SliderState ->
                val frac = state.value.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (enabled) Color(0xFF1C232E) else Color(0xFF181D25))
                ) {
                    if (frac > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(frac)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (enabled) AccentBrush else Brush.linearGradient(listOf(Color(0xFF3A434F), Color(0xFF3A434F))))
                        )
                    }
                }
            }
        )
    }
}
