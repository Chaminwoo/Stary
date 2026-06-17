package com.chaminwoo.stary.feature.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaminwoo.stary.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val userId: String) : ViewModel() {
    private val repo = UserRepository()

    private val _profileImageUrl = MutableStateFlow<String?>(null)
    val profileImageUrl: StateFlow<String?> = _profileImageUrl

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    /** 업로드 실패 메시지(1회성). 화면에서 토스트로 노출 후 [clearError] 로 비운다. */
    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError

    fun clearError() { _uploadError.value = null }

    init {
        viewModelScope.launch {
            try {
                _profileImageUrl.value = repo.getProfileImageUrl(userId)
            } catch (_: Exception) {}
        }
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
