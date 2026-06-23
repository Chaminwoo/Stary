package com.chaminwoo.stary.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlinx.serialization.Serializable

// 화면 경로 및 UI 상태를 정의하는 Type-Safe 라우트
@Serializable
sealed class NavRoute {
    abstract val title: String
    abstract val isRoot: Boolean
    abstract val showTopBar: Boolean
    abstract val showFab: Boolean

    // 현재 목적지와 라우트가 일치하는지 확인
    fun isSelected(destination: NavDestination?): Boolean =
        destination?.hasRoute(this::class) == true

    @Serializable
    data object Login : NavRoute() {
        override val title = "로그인"
        override val isRoot = true
        override val showTopBar = false
        override val showFab = false
    }

    @Serializable
    data object Main : NavRoute() {
        override val title = "지도"
        override val isRoot = true
        override val showTopBar = true
        override val showFab = false
    }

    @Serializable
    data object Upload : NavRoute() {
        override val title = "새 다이어리 기록"
        override val isRoot = false
        override val showTopBar = true
        override val showFab = false
    }

    @Serializable
    data object Friends : NavRoute() {
        override val title = "친구"
        override val isRoot = false
        override val showTopBar = true
        override val showFab = false
    }

    @Serializable
    data object MyDiary : NavRoute() {
        override val title = "내 다이어리"
        override val isRoot = false
        override val showTopBar = true
        override val showFab = false
    }

    @Serializable
    data object Profile : NavRoute() {
        override val title = "프로필"
        override val isRoot = false
        override val showTopBar = true
        override val showFab = false
    }

    @Serializable
    data object Achievements : NavRoute() {
        override val title = "업적"
        override val isRoot = false
        override val showTopBar = true
        override val showFab = false
    }

    @Serializable
    data class Detail(val diaryId: String = "") : NavRoute() {
        override val title = "별 들여다보기"
        override val isRoot = false
        override val showTopBar = true
        override val showFab = false
    }

    @Serializable
    data class Chat(val friendId: String = "", val friendName: String = "") : NavRoute() {
        override val title get() = friendName.ifBlank { "채팅" }
        override val isRoot = false
        override val showTopBar = true
        override val showFab = false
    }

    @Serializable
    data object Notification : NavRoute() {
        override val title = "알림"
        override val isRoot = false
        override val showTopBar = true
        override val showFab = false
    }

    /** 타인(또는 본인) 공개 프로필. 다이어리 작성자 탭 시 진입. */
    @Serializable
    data class UserProfile(val userId: String = "", val userName: String = "") : NavRoute() {
        override val title get() = userName.ifBlank { "프로필" }
        override val isRoot = false
        override val showTopBar = true
        override val showFab = false
    }

}