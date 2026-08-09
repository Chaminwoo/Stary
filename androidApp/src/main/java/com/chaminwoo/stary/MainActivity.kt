package com.chaminwoo.stary

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chaminwoo.stary.core.designsystem.StaryTheme
import com.chaminwoo.stary.core.util.LocaleManager
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.home.screen.MainScreen

class MainActivity : ComponentActivity() {
    // 저장된 인앱 언어를 모든 리소스 해석 전에 적용(시스템 기본이면 원본 그대로).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 영속된 로그인 세션 복원 — 있으면 로그인 화면을 건너뛰고 바로 지도로 진입한다.
        GoogleAuthHelper.restoreSession()
        // 같은 기기에 저장해 둔 커스텀 닉네임이 있으면 표시명에 즉시 반영(기본=구글 닉네임).
        GoogleAuthHelper.applyStoredNickname(this)

        // 앱 시작 즉시 필요한 권한 요청 — 위치(지도 초기 위치)·푸시 알림(API 33+).
        // 위치를 늦게 받으면 초기 지도가 기본 좌표로 떠서 주변 다이어리가 안 보이므로 켜자마자 요청한다.
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
        }

        // 푸시 알림 탭 → 채팅방/다이어리 상세로 딥링크. (콜드 스타트: onCreate, 살아있을 때: onNewIntent)
        handleDeepLinkIntent(intent)
        val initialDiaryId = intent?.getStringExtra(EXTRA_DIARY_ID) ?: diaryIdFromUri(intent)
        val initialChatFriendId = intent?.getStringExtra(EXTRA_CHAT_FRIEND_ID)

        enableEdgeToEdge()
        setContent {
            StaryTheme {
                MainScreen(
                    initialDiaryId = initialDiaryId,
                    initialChatFriendId = initialChatFriendId,
                )
            }
        }
    }

    // 앱이 살아있는(백그라운드) 상태에서 알림 탭 → 새 인텐트로 들어옴(launchMode=singleTop).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        // 푸시 알림(extras) → 상세 화면 딥링크(본인 다이어리 알림이라 게이팅 무관).
        com.chaminwoo.stary.core.util.DeepLinkState.request(
            diaryId = intent?.getStringExtra(EXTRA_DIARY_ID),
            chatFriendId = intent?.getStringExtra(EXTRA_CHAT_FRIEND_ID),
            chatFriendName = intent?.getStringExtra(EXTRA_CHAT_FRIEND_NAME),
            openFriends = intent?.getBooleanExtra(EXTRA_OPEN_FRIENDS, false) == true,
        )
        // 공유 랜딩(stary://diary/{id}) → 상세가 아닌 "지도 포커스"(카메라+파장)로.
        // 상세 직행이면 100m 열람 게이팅이 우회되므로 별 위치만 보여준다(체크리스트 30).
        diaryIdFromUri(intent)?.let { com.chaminwoo.stary.core.util.MapFocusState.request(it) }
        // 친구 초대(stary://invite/{inviterUid}) → 로그인 후 MainScreen 이 리딤(체크리스트 31).
        inviterIdFromUri(intent)?.let { com.chaminwoo.stary.core.util.DeepLinkState.requestInvite(it) }
    }

    /** 공유 랜딩의 stary://diary/{diaryId} 딥링크에서 다이어리 id 추출(체크리스트 30). */
    private fun diaryIdFromUri(intent: Intent?): String? =
        lastSegmentForHost(intent, com.chaminwoo.stary.shared.config.StaryConfig.DEEP_LINK_HOST_DIARY)

    /** 초대 랜딩의 stary://invite/{inviterUid} 딥링크에서 초대자 uid 추출(체크리스트 31). */
    private fun inviterIdFromUri(intent: Intent?): String? =
        lastSegmentForHost(intent, com.chaminwoo.stary.shared.config.StaryConfig.DEEP_LINK_HOST_INVITE)

    private fun lastSegmentForHost(intent: Intent?, host: String): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != com.chaminwoo.stary.shared.config.StaryConfig.DEEP_LINK_SCHEME || uri.host != host) return null
        return uri.lastPathSegment?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val EXTRA_DIARY_ID = "diaryId"
        const val EXTRA_CHAT_FRIEND_ID = "chatFriendId"
        const val EXTRA_CHAT_FRIEND_NAME = "chatFriendName"
        /** 친구 요청 푸시 탭 → 친구 화면(받은 요청)으로. */
        const val EXTRA_OPEN_FRIENDS = "openFriends"
    }
}
