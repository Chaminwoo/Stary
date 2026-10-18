package com.chaminwoo.stary.feature.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaminwoo.stary.data.repository.UserRepository
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.shared.config.StaryConfig
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val userId: String) : ViewModel() {
    private val repo = UserRepository()

    /** `users/{uid}.profileImageUrl` — 값이 있을 때만(빈 값/필드 없음 = null). */
    private val _profileImageUrl = MutableStateFlow<String?>(null)
    val profileImageUrl: StateFlow<String?> = _profileImageUrl

    /**
     * 서버에서 `users/{uid}` 를 한 번이라도 확인했는가 — true 인데 [profileImageUrl] 이 null 이면 "사진을 아직 정한 적 없음"이라
     * 그때만 구글 사진을 쓴다. false(아직 모름)일 땐 구글 사진을 먼저 보여 주지 않는다(CLAUDE.md §2.6).
     */
    private val _photoConfirmed = MutableStateFlow(false)
    val photoConfirmed: StateFlow<Boolean> = _photoConfirmed

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    /** 업로드 실패 메시지(1회성). 화면에서 토스트로 노출 후 [clearError] 로 비운다. */
    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError

    fun clearError() { _uploadError.value = null }

    /**
     * 내 프로필 사진은 `users/{uid}` 를 **실시간 구독**해서 받는다(2026-10-19).
     * 예전엔 화면을 열 때 기본 get() 을 **한 번만** 하고, 실패하면(재설치 직후 아직 오프라인·느린 망) null 로 남아
     * 화면이 구글 사진으로 폴백한 채 다시 읽지 않았다 — "재설치하면 프로필 사진이 구글 사진으로 돌아간다"의 원인.
     * 구독은 망이 돌아오면 서버 값을 다시 보내 준다. 캐시 스냅샷(쓰기 대기분만 있는 불완전한 문서일 수 있다)은
     * 사진 값이 **있을 때만** 믿고, "사진 없음"은 서버 스냅샷으로만 확정한다.
     */
    private val listener: ListenerRegistration =
        staryFirestore.collection(StaryConfig.Collections.USERS).document(userId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val url = snap.getString("profileImageUrl")?.takeIf { it.isNotBlank() }
                if (url != null) _profileImageUrl.value = url
                if (!snap.metadata.isFromCache) {
                    _profileImageUrl.value = url
                    _photoConfirmed.value = true
                }
            }

    override fun onCleared() {
        listener.remove()
    }

    fun uploadProfileImage(uri: Uri) {
        viewModelScope.launch {
            _isUploading.value = true
            try {
                _profileImageUrl.value = repo.uploadProfileImage(userId, uri)
            } catch (e: Exception) {
                _uploadError.value = e.localizedMessage ?: e.toString()
            } finally {
                _isUploading.value = false
            }
        }
    }

    companion object {
        fun factory(userId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProfileViewModel(userId) as T
        }
    }
}
