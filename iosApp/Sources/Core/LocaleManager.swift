import SwiftUI

/// 인앱 언어(로케일) 전환 — Android `core.util.LocaleManager` 패리티.
///
/// 동작: 선택한 언어 태그를 UserDefaults 에 저장하고, 변경 시 루트 뷰가 `language` 를 관찰해
/// 전체 UI 를 다시 그린다(Android 의 `activity.recreate()` 에 대응 — 루트에 `.id(language)`).
/// 문자열은 [L10n] 의 인코드 딕셔너리에서 현재 언어로 해석한다.
///
/// ⚠️ 코드에 하드코딩된 한국어 문자열은 [L10n] 키로 옮겨야 번역이 반영된다.
///    (현재는 설정/탭 등 주요 chrome 부터 — 나머지는 점진 이관 대상. Android 와 동일 단계적 접근.)
@MainActor
final class LocaleManager: ObservableObject {
    static let shared = LocaleManager()

    private let keyLang = "app_language"

    /// "" = 시스템 기본. 그 외 BCP-47 태그("ko","en","ja").
    static let system = ""

    /// 설정 화면 선택지로 노출할 지원 언어(태그).
    static let supported = ["", "ko", "en", "ja"]

    @Published private(set) var language: String

    private init() {
        language = UserDefaults.standard.string(forKey: keyLang) ?? LocaleManager.system
    }

    /// 실제 적용 언어 — 시스템 기본이면 기기 우선 언어, 그 외 선택 태그.
    var effectiveLanguage: String {
        if language.isEmpty {
            let dev = Locale.preferredLanguages.first ?? "ko"
            if dev.hasPrefix("ko") { return "ko" }
            if dev.hasPrefix("ja") { return "ja" }
            if dev.hasPrefix("en") { return "en" }
            return "ko"
        }
        return language
    }

    /// 날짜/숫자 포맷용 SwiftUI 환경 로케일.
    var swiftLocale: Locale {
        language.isEmpty ? Locale.current : Locale(identifier: language)
    }

    func setLanguage(_ tag: String) {
        guard language != tag else { return }
        language = tag
        UserDefaults.standard.set(tag, forKey: keyLang)
    }

    /// 현재 언어로 문자열 해석.
    func t(_ key: L10n) -> String { key.value(for: effectiveLanguage) }
}

/// 인코드 로컬라이즈 문자열(설정/탭 등). Android res/values-xx 의 iOS 대응.
/// 키 추가 시 ko/en/ja 세 값을 함께 넣는다(기본 폴백 = ko).
enum L10n: String {
    case navSettings
    case settingsSound, settingsNotification, settingsLanguage
    case settingsBgm, settingsBgmDesc, settingsBgmVolume, settingsSfxVolume
    case settingsNotifPopup, settingsNotifPopupDesc, settingsLanguageDesc, settingsAutosave
    case languageDialogTitle, languageSystem, languageKo, languageEn, languageJa
    case tabMap, tabList, tabUpload, tabFriends, tabProfile
    // 타인 프로필(UserProfileScreen) + 미조회 필터 — Android 8.24 리소스화 패리티.
    case userProfileMe, userStatusFriend, userChatAction, userAddFriend, userRequested
    case userNoTitle, userStarsHeader, userNoDiaries, unknownUser, profileTitle
    case statStars, statViews, statLikes
    case filterUnviewed
    // 계정 삭제 + 신고/차단 — Android 차단·신고 라운드 패리티.
    case commonCancel, commonDelete
    case settingsAccount, settingsDeleteAccount, settingsDeleteAccountDesc
    case settingsDeleteConfirmMsg, settingsDeleteFailed, toastAccountDeleted
    case reportUser, reportDiary, reportSubmit
    case reportReasonSpam, reportReasonAbuse, reportReasonInappropriate, reportReasonImpersonation, reportReasonOther
    case toastReported
    case blockAction, unblockAction, toastBlocked, toastUnblocked

    /// (ko, en, ja).
    private var table: (String, String, String) {
        switch self {
        case .navSettings:          return ("설정", "Settings", "設定")
        case .settingsSound:        return ("사운드", "Sound", "サウンド")
        case .settingsNotification: return ("알림", "Notifications", "通知")
        case .settingsLanguage:     return ("언어", "Language", "言語")
        case .settingsBgm:          return ("배경음악", "Background music", "BGM")
        case .settingsBgmDesc:      return ("별들 사이를 떠다니는 우주의 소리", "Cosmic sounds drifting among the stars", "星々の間を漂う宇宙の音")
        case .settingsBgmVolume:    return ("배경음악 볼륨", "Music volume", "BGMの音量")
        case .settingsSfxVolume:    return ("효과음 볼륨", "Sound effects volume", "効果音の音量")
        case .settingsNotifPopup:   return ("알림 팝업", "Notification banners", "通知バナー")
        case .settingsNotifPopupDesc: return ("채팅·다이어리 알림을 상단 배너로 알려드려요", "Show chat and diary alerts as a top banner", "チャットや日記の通知を上部バナーで表示します")
        case .settingsLanguageDesc: return ("앱에 표시되는 언어를 선택해요", "Choose the language shown in the app", "アプリに表示される言語を選択します")
        case .settingsAutosave:     return ("설정은 자동으로 저장돼요", "Settings are saved automatically", "設定は自動的に保存されます")
        case .languageDialogTitle:  return ("언어 선택", "Select language", "言語を選択")
        case .languageSystem:       return ("시스템 기본", "System default", "システムのデフォルト")
        case .languageKo:           return ("한국어", "한국어", "한국어")
        case .languageEn:           return ("English", "English", "English")
        case .languageJa:           return ("日本語", "日本語", "日本語")
        case .tabMap:               return ("지도", "Map", "地図")
        case .tabList:              return ("목록", "List", "リスト")
        case .tabUpload:            return ("올리기", "Post", "投稿")
        case .tabFriends:           return ("친구", "Friends", "フレンド")
        case .tabProfile:           return ("프로필", "Profile", "プロフィール")
        case .userProfileMe:        return ("내 프로필", "My profile", "マイプロフィール")
        case .userStatusFriend:     return ("친구", "Friend", "フレンド")
        case .userChatAction:       return ("채팅하기", "Chat", "チャットする")
        case .userAddFriend:        return ("친구 추가", "Add friend", "フレンド追加")
        case .userRequested:        return ("요청됨", "Requested", "リクエスト済み")
        case .userNoTitle:          return ("칭호 없음", "No title", "称号なし")
        case .userStarsHeader:      return ("별 목록", "Stars", "星のリスト")
        case .userNoDiaries:        return ("아직 볼 수 있는 별이 없어요.", "No stars to show yet.", "表示できる星がまだありません。")
        case .unknownUser:          return ("알 수 없음", "Unknown", "不明")
        case .profileTitle:         return ("프로필", "Profile", "プロフィール")
        case .statStars:            return ("별", "Stars", "星")
        case .statViews:            return ("조회", "Views", "閲覧")
        case .statLikes:            return ("좋아요", "Likes", "いいね")
        case .filterUnviewed:       return ("미조회만", "Unviewed", "未読のみ")
        case .commonCancel:         return ("취소", "Cancel", "キャンセル")
        case .commonDelete:         return ("삭제", "Delete", "削除")
        case .settingsAccount:      return ("계정", "Account", "アカウント")
        case .settingsDeleteAccount: return ("계정 삭제", "Delete account", "アカウント削除")
        case .settingsDeleteAccountDesc: return ("계정과 모든 데이터를 영구 삭제해요", "Permanently delete your account and all data", "アカウントとすべてのデータを完全に削除します")
        case .settingsDeleteConfirmMsg: return ("정말 계정을 삭제할까요? 다이어리·친구·프로필이 모두 사라지며 되돌릴 수 없어요.", "Delete your account? Your diaries, friends, and profile will all be removed and this can't be undone.", "本当にアカウントを削除しますか？日記・フレンド・プロフィールがすべて消え、元に戻せません。")
        case .settingsDeleteFailed: return ("계정 삭제에 실패했어요. 다시 로그인 후 시도해 주세요.", "Couldn't delete the account. Please sign in again and retry.", "アカウントの削除に失敗しました。再ログイン後にもう一度お試しください。")
        case .toastAccountDeleted:  return ("계정이 삭제되었어요", "Your account was deleted", "アカウントを削除しました")
        case .reportUser:           return ("사용자 신고", "Report user", "ユーザーを報告")
        case .reportDiary:          return ("다이어리 신고", "Report diary", "日記を報告")
        case .reportSubmit:         return ("신고", "Report", "報告")
        case .reportReasonSpam:     return ("스팸·광고", "Spam or ads", "スパム・広告")
        case .reportReasonAbuse:    return ("욕설·혐오", "Abuse or hate", "暴言・ヘイト")
        case .reportReasonInappropriate: return ("부적절한 콘텐츠", "Inappropriate content", "不適切なコンテンツ")
        case .reportReasonImpersonation: return ("사칭", "Impersonation", "なりすまし")
        case .reportReasonOther:    return ("기타", "Other", "その他")
        case .toastReported:        return ("신고가 접수되었어요", "Report submitted", "報告を受け付けました")
        case .blockAction:          return ("차단", "Block", "ブロック")
        case .unblockAction:        return ("차단 해제", "Unblock", "ブロック解除")
        case .toastBlocked:         return ("차단했어요", "Blocked", "ブロックしました")
        case .toastUnblocked:       return ("차단을 해제했어요", "Unblocked", "ブロックを解除しました")
        }
    }

    func value(for lang: String) -> String {
        let (ko, en, ja) = table
        switch lang {
        case "en": return en
        case "ja": return ja
        default: return ko
        }
    }
}
