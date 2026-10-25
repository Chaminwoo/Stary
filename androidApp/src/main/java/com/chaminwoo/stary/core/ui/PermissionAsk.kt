package com.chaminwoo.stary.core.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.util.LocationHelper
import kotlinx.coroutines.delay

/**
 * 권한은 이유를 먼저 보여 주고 묻는다(2026-10, S2). 예전엔 앱을 켜자마자(로그인 전) 위치·알림 창이 연달아 떴다.
 *  - 위치: 지도에 들어왔을 때 이유 카드 → "허용하기"를 눌러야 시스템 창. 실행당 한 번, "나중에"면 3일 뒤에 다시.
 *  - 알림(Android 13+): 별을 올렸거나 친구를 추가한 뒤에 카드 → "알림 받기"를 눌러야 시스템 창. "나중에"면 7일 뒤.
 *  - 시스템 창이 더는 안 뜨는 상태(두 번 거절)면 카드도 띄우지 않는다. 그 뒤엔 업로드 화면의 위치 안내와
 *    설정 화면의 알림 안내 행이 설정 앱으로 보낸다.
 * iOS `PermissionAsk.swift` 와 같은 규칙.
 */
object PermissionAsk {
    private const val PREFS = "stary_prefs"
    private const val KEY_LOC_LATER = "perm_loc_later_at"
    private const val KEY_LOC_OFF = "perm_loc_off"
    private const val KEY_NOTIF_LATER = "perm_notif_later_at"
    private const val KEY_NOTIF_OFF = "perm_notif_off"
    private const val LOC_COOLDOWN_MS = 3L * 24 * 60 * 60 * 1000
    private const val NOTIF_COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000

    /** 이번 실행에서 위치 카드에 답했는지(실행당 한 번). */
    private var locationAnswered by mutableStateOf(false)

    /** 알림 카드를 띄울 차례 — 별을 올렸거나 친구를 추가했을 때 켜진다. */
    var notificationPending by mutableStateOf(false)
        private set

    /** 떠 있는 축하 팝업(업적·히든 업적) 수 — 알림 카드는 그게 다 닫힌 뒤에 뜬다(축하 → 알림 순서). */
    var celebrations by mutableIntStateOf(0)
        private set

    fun locationDue(context: Context): Boolean =
        !locationAnswered && !LocationHelper.hasPermission(context) &&
            !prefs(context).getBoolean(KEY_LOC_OFF, false) && cooled(context, KEY_LOC_LATER, LOC_COOLDOWN_MS)

    /** 별을 올렸거나 친구를 추가했을 때 — 알림을 켤지 물을 차례로 둔다(이미 켰는지는 [PermissionAskHost] 가 본다). */
    fun requestNotificationAsk() {
        notificationPending = true
    }

    /** 축하 팝업이 떠 있는 동안 부른다 — 그동안 알림 카드를 미룬다. */
    @Composable
    fun HoldForCelebration() {
        DisposableEffect(Unit) {
            celebrations++
            onDispose { celebrations-- }
        }
    }

    internal fun notificationDue(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs(context).getBoolean(KEY_NOTIF_OFF, false) && cooled(context, KEY_NOTIF_LATER, NOTIF_COOLDOWN_MS)

    internal fun answerLocation(context: Context, later: Boolean) {
        locationAnswered = true
        if (later) prefs(context).edit().putLong(KEY_LOC_LATER, System.currentTimeMillis()).apply()
    }

    internal fun answerNotification(context: Context, later: Boolean) {
        notificationPending = false
        if (later) prefs(context).edit().putLong(KEY_NOTIF_LATER, System.currentTimeMillis()).apply()
    }

    /** 시스템 창에서 거절 — 한 번 더 물을 수 있으면 "나중에"와 같게, 더는 창이 안 뜨면 카드를 끈다. */
    internal fun denied(context: Context, permission: String, location: Boolean) {
        val act = context.findActivity()
        val canAskAgain = act != null && ActivityCompat.shouldShowRequestPermissionRationale(act, permission)
        val edit = prefs(context).edit()
        if (canAskAgain) edit.putLong(if (location) KEY_LOC_LATER else KEY_NOTIF_LATER, System.currentTimeMillis())
        else edit.putBoolean(if (location) KEY_LOC_OFF else KEY_NOTIF_OFF, true)
        edit.apply()
    }

    private fun cooled(context: Context, key: String, cooldownMs: Long): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(key, 0L) >= cooldownMs

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** 알림 카드를 띄우기 전 잠깐 — 별 탄생 연출·저장 토스트가 지나간 뒤에. */
private const val NOTIF_ASK_DELAY_MS = 1_200L

/**
 * 위치 이유 카드 + 알림 카드. MainScreen 에 늘 붙여 둔다 — 권한 요청 런처가 결과를 받기 전에
 * 사라지면 결과가 버려지므로 카드가 안 떠 있어도 컴포지션에 남아 있어야 한다.
 * [showLocation] = 위치 카드를 띄울 때(MainScreen 이 지도·약관·코치마크까지 따진다).
 * [notificationAllowed] = 알림 카드를 띄워도 되는 때(로그인 + 다른 안내·연출이 없음).
 */
@Composable
fun PermissionAskHost(showLocation: Boolean, notificationAllowed: Boolean) {
    val context = LocalContext.current
    // 허용됐으면 지도(MainListScreen)가 ON_RESUME 에서 위치 추적을 시작한다.
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.none { it }) PermissionAsk.denied(context, Manifest.permission.ACCESS_COARSE_LOCATION, location = true)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionAsk.denied(context, Manifest.permission.POST_NOTIFICATIONS, location = false)
        }
    }

    if (showLocation) {
        PermissionCard(
            title = R.string.perm_loc_title,
            message = R.string.perm_loc_msg,
            allow = R.string.perm_allow,
            onAllow = {
                PermissionAsk.answerLocation(context, later = false)
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onLater = { PermissionAsk.answerLocation(context, later = true) },
        )
    }

    val pending = PermissionAsk.notificationPending
    var showNotification by remember { mutableStateOf(false) }
    LaunchedEffect(pending, notificationAllowed) {
        showNotification = false
        if (!pending || !notificationAllowed) return@LaunchedEffect
        if (!PermissionAsk.notificationDue(context)) {
            PermissionAsk.answerNotification(context, later = false)
            return@LaunchedEffect
        }
        delay(NOTIF_ASK_DELAY_MS)
        showNotification = true
    }
    if (showNotification && !showLocation) {
        PermissionCard(
            title = R.string.perm_notif_title,
            message = R.string.perm_notif_msg,
            allow = R.string.perm_notif_allow,
            onAllow = {
                showNotification = false
                PermissionAsk.answerNotification(context, later = false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onLater = {
                showNotification = false
                PermissionAsk.answerNotification(context, later = true)
            },
        )
    }
}

@Composable
private fun PermissionCard(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes allow: Int,
    onAllow: () -> Unit,
    onLater: () -> Unit,
) {
    StaryDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { StaryButton(stringResource(allow), onAllow, compact = true) },
        dismissButton = { StaryButton(stringResource(R.string.perm_later), onLater, kind = StaryButtonKind.Ghost, compact = true) },
    )
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
