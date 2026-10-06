package com.chaminwoo.stary.shared.data.repository

import com.chaminwoo.stary.core.model.AppNotification
import com.chaminwoo.stary.core.model.ChatHidden
import com.chaminwoo.stary.core.model.ChatMessage
import com.chaminwoo.stary.core.model.Comment
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.model.Friend
import com.chaminwoo.stary.core.model.FriendRequest
import com.chaminwoo.stary.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * 플랫폼 공용 데이터 계약(commonMain).
 *
 * 구현은 플랫폼별로 제공한다.
 *  - Android : Firebase Firestore(예: FirebaseDiaryRepository) — androidApp 모듈
 *  - iOS     : Firebase iOS SDK 또는 동일 백엔드 구현 — 추후 iosMain/Swift
 *
 * 이렇게 인터페이스를 commonMain 으로 끌어올려 두면 ViewModel/도메인 로직을
 * 플랫폼에 독립적으로 공유할 수 있다.
 */
interface DiaryRepository {
    fun observeAllDiaries(): Flow<List<Diary>>
    fun observeMyDiaries(userId: String): Flow<List<Diary>>
    suspend fun saveDiary(diary: Diary): Boolean
    suspend fun deleteDiary(diaryId: String): Boolean
    suspend fun getDiaryById(diaryId: String): Diary?
    suspend fun incrementViewCount(diaryId: String)
    suspend fun prefetchDiaries(diaryIds: List<String>)
    suspend fun updateDiary(diary: Diary): Boolean
}

interface CommentRepository {
    fun observeComments(diaryId: String): Flow<List<Comment>>
    suspend fun addComment(
        diaryId: String,
        diaryTitle: String,
        diaryOwnerId: String,
        userId: String,
        userName: String,
        content: String
    )
    suspend fun deleteComment(diaryId: String, commentId: String)
}

interface LikeRepository {
    fun observeIsLiked(diaryId: String, userId: String): Flow<Boolean>
    fun observeLikeCount(diaryId: String): Flow<Int>
    suspend fun toggleLike(
        diaryId: String,
        diaryTitle: String,
        diaryOwnerId: String,
        userId: String,
        userName: String
    )
}

interface NotificationRepository {
    fun observeNotifications(ownerId: String): Flow<List<AppNotification>>
    fun observeUnreadCount(ownerId: String): Flow<Int>
    suspend fun markAllRead(ownerId: String)
    suspend fun deleteNotification(notificationId: String)
}

interface FriendRepository {
    fun observeFriends(userId: String): Flow<List<Friend>>
    fun observeIncomingRequests(userId: String): Flow<List<FriendRequest>>

    /** 내가 보낸 pending 요청 — 검색 결과의 "요청됨" 상태 표시용. */
    fun observeOutgoingRequests(userId: String): Flow<List<FriendRequest>>
    suspend fun searchUsers(query: String, excludeUserId: String): List<UserProfile>
    suspend fun sendRequest(from: UserProfile, to: UserProfile): Boolean
    suspend fun acceptRequest(request: FriendRequest): Boolean
    suspend fun declineRequest(requestId: String)
    suspend fun removeFriend(userId: String, friendId: String)
}

/** 1:1 친구 채팅. */
interface ChatRepository {
    fun observeMessages(chatId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        senderName: String,
        text: String
    ): Boolean

    /** 메시지 완전 삭제(문서 제거 → 상대방 쪽에서도 사라짐). 본인/시간창 검증은 호출부(ViewModel)가 한다. */
    suspend fun deleteMessage(chatId: String, messageId: String): Boolean

    /** 이 방에서 내가 **나에게서만** 지운 것(users/{나}/chatHidden/{chatId}) 실시간. 문서가 없으면 [ChatHidden.NONE]. */
    fun observeHidden(myId: String, chatId: String): Flow<ChatHidden>

    /**
     * 메시지 하나를 나에게서만 삭제(상대 방에는 그대로). 친구 목록 미리보기가 숨긴 메시지를 보여주지 않도록
     * [latestAt](방 전체의 마지막 메시지 시각)과 [remaining](숨긴 뒤 내게 남는 마지막 메시지, 없으면 null)을 함께 기록한다.
     */
    suspend fun hideMessageForMe(
        myId: String,
        chatId: String,
        messageId: String,
        latestAt: Long,
        remaining: ChatMessage?,
    ): Boolean

    /** 대화방을 나에게서만 삭제 — [upTo](그 시점 마지막 메시지 createdAt) 이하 메시지를 내 화면에서 전부 가린다. */
    suspend fun clearChatForMe(myId: String, chatId: String, upTo: Long): Boolean
}

/** 미조회 다이어리 필터용 열람 기록. */
interface ViewedDiaryRepository {
    fun observeViewedIds(userId: String): Flow<Set<String>>
    suspend fun markViewed(userId: String, diaryId: String)
}
