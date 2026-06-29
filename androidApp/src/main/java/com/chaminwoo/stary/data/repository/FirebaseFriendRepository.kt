package com.chaminwoo.stary.data.repository

import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.core.model.Friend
import com.chaminwoo.stary.core.model.FriendRequest
import com.chaminwoo.stary.core.model.UserProfile
import com.chaminwoo.stary.shared.config.StaryConfig
import com.chaminwoo.stary.shared.data.repository.FriendRepository
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * 공용 [FriendRepository] 의 Firestore 구현.
 *
 * 구조:
 *  - users/{uid}/friends/{friendUid} : 수락된 친구 (양방향 두 문서)
 *  - friendRequests/{id}             : pending 요청. 수락 시 friends 생성 + 요청 삭제.
 *  - users/{uid}.userName            : 검색용 (로그인 시 upsertProfile 로 기록)
 */
class FirebaseFriendRepository : FriendRepository {

    private val db = staryFirestore
    private val users = db.collection(StaryConfig.Collections.USERS)
    private val requests = db.collection(StaryConfig.Collections.FRIEND_REQUESTS)

    /** 단일 사용자의 공개 프로필(users/{uid}) 조회 — 타인 프로필 화면 등에서 사진/이름 표시용. */
    suspend fun getProfile(userId: String): UserProfile? {
        if (userId.isBlank()) return null
        return try {
            val doc = users.document(userId).get().await()
            doc.toObject(UserProfile::class.java)?.copy(userId = doc.id)
        } catch (_: Exception) {
            null
        }
    }

    /** 장착 칭호(업적 id)를 공개 프로필에 기록 — 타인 프로필에서도 칭호를 볼 수 있게. (null=해제) */
    suspend fun setEquippedTitle(userId: String, achievementId: String?) {
        if (userId.isBlank()) return
        try {
            users.document(userId).set(
                mapOf("equippedTitle" to (achievementId ?: "")),
                SetOptions.merge()
            ).await()
        } catch (_: Exception) {}
    }

    /** 프로필에 띄울(핀) 다이어리 id 목록 조회(최대 3). 타인/본인 프로필 공용. */
    suspend fun getPinnedDiaries(userId: String): List<String> {
        if (userId.isBlank()) return emptyList()
        return try {
            val doc = users.document(userId).get().await()
            @Suppress("UNCHECKED_CAST")
            (doc.get("pinnedDiaries") as? List<String>) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 프로필에 띄울 다이어리 id 목록 저장(최대 3). */
    suspend fun setPinnedDiaries(userId: String, diaryIds: List<String>) {
        if (userId.isBlank()) return
        try {
            users.document(userId).set(
                mapOf("pinnedDiaries" to diaryIds.take(3)),
                SetOptions.merge()
            ).await()
        } catch (_: Exception) {}
    }

    /** 로그인 직후 호출 — 사용자 검색이 가능하도록 공개 프로필을 기록한다. */
    suspend fun upsertProfile(profile: UserProfile) {
        try {
            users.document(profile.userId).set(
                mapOf(
                    "userId" to profile.userId,
                    "userName" to profile.userName,
                    "profileImageUrl" to profile.profileImageUrl,
                ),
                SetOptions.merge()
            ).await()
        } catch (_: Exception) {}
    }

    override fun observeFriends(userId: String): Flow<List<Friend>> = callbackFlow {
        val listener = users.document(userId).collection(StaryConfig.Collections.FRIENDS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener // 권한/네트워크 에러로 앱이 죽지 않게 무시
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Friend::class.java)?.copy(userId = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override fun observeIncomingRequests(userId: String): Flow<List<FriendRequest>> = callbackFlow {
        val listener = requests.whereEqualTo("toId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener // 권한/네트워크 에러로 앱이 죽지 않게 무시
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(FriendRequest::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun searchUsers(query: String, excludeUserId: String): List<UserProfile> {
        if (query.isBlank()) return emptyList()
        return try {
            users.whereGreaterThanOrEqualTo("userName", query)
                .whereLessThanOrEqualTo("userName", query + "")
                .limit(20)
                .get().await()
                .documents
                .mapNotNull { it.toObject(UserProfile::class.java)?.copy(userId = it.id) }
                .filter { it.userId != excludeUserId && it.userName.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun sendRequest(from: UserProfile, to: UserProfile): Boolean {
        return try {
            // 중복 요청 방지
            val dup = requests
                .whereEqualTo("fromId", from.userId)
                .whereEqualTo("toId", to.userId)
                .get().await()
            if (!dup.isEmpty) return true
            val doc = requests.document()
            doc.set(
                FriendRequest(
                    id = doc.id,
                    fromId = from.userId,
                    fromName = from.userName,
                    fromPhotoUrl = from.profileImageUrl,
                    toId = to.userId,
                    toName = to.userName,
                    createdAt = System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun acceptRequest(request: FriendRequest): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val batch = db.batch()
            val mine = users.document(request.toId)
                .collection(StaryConfig.Collections.FRIENDS).document(request.fromId)
            val theirs = users.document(request.fromId)
                .collection(StaryConfig.Collections.FRIENDS).document(request.toId)
            batch.set(mine, Friend(request.fromId, request.fromName, request.fromPhotoUrl, now))
            batch.set(theirs, Friend(request.toId, request.toName, "", now))
            batch.delete(requests.document(request.id))
            batch.commit().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun declineRequest(requestId: String) {
        try { requests.document(requestId).delete().await() } catch (_: Exception) {}
    }

    override suspend fun removeFriend(userId: String, friendId: String) {
        try {
            val batch = db.batch()
            batch.delete(users.document(userId).collection(StaryConfig.Collections.FRIENDS).document(friendId))
            batch.delete(users.document(friendId).collection(StaryConfig.Collections.FRIENDS).document(userId))
            batch.commit().await()
        } catch (_: Exception) {}
    }
}
