package com.chaminwoo.stary.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chaminwoo.stary.MainActivity
import com.chaminwoo.stary.R
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.shared.config.StaryConfig
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 수신 (체크리스트 8 — 클라이언트 부분).
 *
 * ⚠️ 실제 발송은 서버(Cloud Functions)가 필요하다:
 *   다이어리 업로드 시 친구들의 users/{uid}.fcmToken 으로 data 메시지
 *   { diaryId, title, body } 를 보내는 Function 을 배포해야 푸시가 동작한다.
 *   (인앱 알림(FRIEND_POST 문서)은 이미 클라이언트에서 생성됨)
 *
 * 알림 탭 → MainActivity 에 diaryId extra → 해당 다이어리 상세로 딥링크.
 */
class StaryMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // 로그인 상태면 토큰 갱신을 프로필에 반영 (Function 발송용)
        val uid = GoogleAuthHelper.currentUserId ?: return
        staryFirestore.collection(StaryConfig.Collections.USERS)
            .document(uid)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "fcmToken 저장 실패: ${it.localizedMessage}") }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // 앱이 전면이면 인앱 배너(InAppBanner)가 처리하므로 시스템 알림을 띄우지 않는다(이중 표시 방지).
        // 후면/종료 상태에서만 상단 heads-up 시스템 알림을 띄운다.
        if (com.chaminwoo.stary.core.util.AppForeground.isForeground) return

        val diaryId = message.data["diaryId"]
        val chatFriendId = message.data["chatFriendId"]
        val chatFriendName = message.data["chatFriendName"]
        val title = message.data["title"] ?: message.notification?.title ?: "Stary"
        val body = message.data["body"] ?: message.notification?.body ?: "새 소식이 있어요"

        // heads-up 채널 보장(앱 시작 시 이미 만들지만 방어적으로).
        ensureStaryNotificationChannel(this)
        val manager = getSystemService(NotificationManager::class.java)

        // 알림 탭 → 채팅이면 해당 채팅방, 아니면 다이어리 상세로 딥링크.
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (chatFriendId != null) {
                putExtra(MainActivity.EXTRA_CHAT_FRIEND_ID, chatFriendId)
                putExtra(MainActivity.EXTRA_CHAT_FRIEND_NAME, chatFriendName ?: "")
            } else {
                diaryId?.let { putExtra(MainActivity.EXTRA_DIARY_ID, it) }
            }
        }
        val pending = PendingIntent.getActivity(
            this,
            (chatFriendId ?: diaryId)?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 알림 id: 채팅은 방(chatId)별로 묶어 누적되지 않게, 그 외는 diaryId 기준.
        val tag = message.data["chatId"] ?: diaryId
        val notification = NotificationCompat.Builder(this, STARY_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // heads-up (구버전 호환)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(tag?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val TAG = "StaryMessaging"
    }
}
