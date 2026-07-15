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
    case settingsBgm1, settingsBgmVolume, settingsSfxVolume
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
    // 비로그인 상호작용 잠금 — Android common_login_required 패리티.
    case commonLoginRequired
    case settingsAccount, settingsDeleteAccount, settingsDeleteAccountDesc
    case settingsDeleteConfirmMsg, settingsDeleteFailed, toastAccountDeleted
    case reportUser, reportDiary, reportSubmit
    case reportReasonSpam, reportReasonAbuse, reportReasonInappropriate, reportReasonImpersonation, reportReasonOther
    case toastReported
    case blockAction, unblockAction, toastBlocked, toastUnblocked
    // 도보 길찾기 — 친구 별 탭 진입 라운드.
    case routeDirections, routeCancel, routeMinSuffix
    // 프로필 떠다니는 아이콘/핀 별 라운드.
    case navAchievements, profileMyStars, profilePinTitle, profilePinHint, commonSave
    case profileFriends, profileDiaries, profileAchievements, profileEmptyStars
    // 화면 첫 진입 설명창(FirstVisitInfo).
    case onbButton
    case onbMyDiaryTitle, onbMyDiaryMsg, onbProfileTitle, onbProfileMsg
    case onbAchievementsTitle, onbAchievementsMsg, onbMusicTitle, onbMusicMsg
    case onbFriendsTitle, onbFriendsMsg
    // 3D 행성(글로브) — 지도 하단 버튼으로 진입.
    case globeHint, globeOpen, globeClose
    // 채팅 메시지 완전 삭제(1분 이내, 본인만).
    case chatDeleteTitle, chatDeleteConfirm
    // 닉네임 변경.
    case profileEditNickname, profileNicknameHint
    // 히든 업적.
    case achTabNormal, achTabHidden, achHiddenIntro, achHiddenAchiever, achHiddenUnclaimed, achHiddenByMe
    // 기간별 보기 필터.
    case filterPeriod, periodAll, periodToday, periodWeek, periodMonth, periodYear
    // 다이어리 공유 카드(체크리스트 30).
    case shareDiary, shareDiaryText, shareCardTagline, shareCardUntitled, shareCardAnonymous
    // 친구 초대 보상(체크리스트 31).
    case inviteFriends, inviteFriendsDesc, inviteShareText
    // 주간 개척 퀘스트(체크리스트 32).
    case pioneerCondition
    // 30m 안에서 겹쳐진 별 무리(카드 뷰어).
    case clusterHeader, clusterHint, clusterOpen
    // 친구 목록(메신저형 행).
    case friendNoChatYet
    // 근처 미조회 별 발견 알림(체크리스트 33).
    case nearbyStarTitle, nearbyStarBody
    // 드로어(좌측 메뉴) 내비 — Android MainScreen 대응.
    case navMap, navMyDiary, navMusic, navNotification, navUpload, navDetail, navStarCluster
    case drawerList, drawerLogout, drawerLogin
    // 지도 필터 스피드 다이얼(Android MainListScreen 대응).
    case filterAll

    /// (ko, en, ja).
    private var table: (String, String, String) {
        switch self {
        case .navSettings:          return ("설정", "Settings", "設定")
        case .settingsSound:        return ("사운드", "Sound", "サウンド")
        case .settingsNotification: return ("알림", "Notifications", "通知")
        case .settingsLanguage:     return ("언어", "Language", "言語")
        case .settingsBgm1:         return ("배경음악", "Background music", "BGM")
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
        case .commonLoginRequired:  return ("로그인이 필요해요", "Sign in required", "ログインが必要です")
        case .settingsAccount:      return ("계정", "Account", "アカウント")
        case .settingsDeleteAccount: return ("계정 삭제", "Delete account", "アカウント削除")
        case .settingsDeleteAccountDesc: return ("계정과 모든 데이터를 영구 삭제해요", "Permanently delete your account and all data", "アカウントとすべてのデータを完全に削除します")
        case .settingsDeleteConfirmMsg: return ("삭제를 요청하면 7일 뒤에 완전히 삭제돼요. 그 전에 다시 로그인하면 자동으로 취소됩니다.", "Request deletion and your account is permanently removed after 7 days. Sign in again before then to cancel.", "削除をリクエストすると7日後に完全に削除されます。それまでに再ログインすると自動的にキャンセルされます。")
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
        case .routeDirections:      return ("길찾기", "Directions", "道案内")
        case .routeCancel:          return ("길찾기 취소", "Cancel route", "案内をやめる")
        case .routeMinSuffix:       return ("분", "min", "分")
        case .navAchievements:      return ("업적", "Achievements", "実績")
        case .profileMyStars:       return ("내 별", "My stars", "マイ星")
        case .profilePinTitle:      return ("프로필에 띄울 별", "Pin stars to profile", "プロフィールに飾る星")
        case .profilePinHint:       return ("최대 3개까지 고를 수 있어요", "Choose up to 3", "最大3つまで選べます")
        case .commonSave:           return ("저장", "Save", "保存")
        case .profileFriends:       return ("친구", "Friends", "フレンド")
        case .profileDiaries:       return ("다이어리", "Diaries", "日記")
        case .profileAchievements:  return ("업적", "Achievements", "実績")
        case .profileEmptyStars:    return ("아직 남긴 별이 없어요.", "No stars yet.", "まだ星がありません。")
        case .profileEditNickname:  return ("닉네임 변경", "Edit nickname", "ニックネーム変更")
        case .profileNicknameHint:  return ("닉네임을 입력하세요", "Enter a nickname", "ニックネームを入力")
        case .onbButton:            return ("시작하기", "Get started", "はじめる")
        case .onbMyDiaryTitle:      return ("내 별", "My Stars", "マイ星")
        case .onbMyDiaryMsg:        return ("내가 남긴 다이어리가 모여있어요.\n별을 잡아서 제목을 보거나, 클릭하여 열람하세요",
                                            "Here are the stars you've left. Tap one to open the diary, or use Directions to walk there.",
                                            "あなたが残した星を集めました。星をタップすると日記を開き、「道案内」でそこまで歩いて行けます。")
        case .onbProfileTitle:      return ("프로필", "Profile", "プロフィール")
        case .onbProfileMsg:        return ("사진을 눌러 프로필을 바꾸고,\n떠다니는 아이콘을 잡거나 클릭해 다양한 정보를 볼 수 있어요.\n오른쪽 위 + 버튼을 통해 아끼는 별을 프로필에 띄워보세요.",
                                            "Tap your photo to change it, and see your likes, friends, diaries, and achievements as floating icons. Use + at the top right to pin your favorite stars.",
                                            "写真をタップしてプロフィールを変更でき、浮かぶアイコンでいいね・フレンド・日記・実績をひと目で確認できます。右上の＋でお気に入りの星を飾れます。")
        case .onbAchievementsTitle: return ("업적", "Achievements", "実績")
        case .onbAchievementsMsg:   return ("다이어리를 쓰고 친구를 만들어 업적을 달성하세요. \n 별을 모으면 칭호와 새로운 별 모양과 색이 해금돼요.",
                                            "Writing diaries, making friends, and collecting stars unlock titles and new star shapes and colors. Tap a title to equip it.",
                                            "日記を書き、フレンドを作り、星を集めると称号や新しい星の形・色が解放されます。称号はタップで装着できます。")
        case .onbMusicTitle:        return ("배경음악", "Background music", "BGM")
        case .onbMusicMsg:          return ("원형 다이얼을 돌려 배경음악을 골라보세요. \n 잠긴 음악들은 업적을 통해 해금하세요.",
                                            "Turn the circular dial to pick a cosmic track. Locked tracks open as you earn achievements.",
                                            "円形ダイヤルを回して宇宙の音楽を選びましょう。ロックされたトラックは実績を達成すると開きます。")
        case .onbFriendsTitle:      return ("친구", "Friends", "フレンド")
        case .onbFriendsMsg:        return ("이름으로 친구를 검색해 요청을 보내세요.\n받은 요청을 수락하면 서로의 프로필을 보며, 채팅할 수 있어요.",
                                            "Search friends by name to send requests; accept incoming ones to see each other's stars and chat.",
                                            "名前でフレンドを検索してリクエストを送り、届いたリクエストを承認するとお互いの星を見たりチャットできます。")
        case .globeHint:            return ("드래그로 회전 · 아래를 탭하여 닫기", "Drag to rotate · Tap below to close", "ドラッグで回転 · 下をタップで閉じる")
        case .globeOpen:            return ("지구 보기", "View globe", "地球を見る")
        case .globeClose:           return ("지도로 돌아가기", "Back to map", "地図に戻る")
        case .chatDeleteTitle:      return ("메시지 삭제", "Delete message", "メッセージを削除")
        case .chatDeleteConfirm:    return ("이 메시지를 완전히 삭제할까요? 상대방에게도 사라져요.",
                                            "Delete this message completely? It will disappear for the other person too.",
                                            "このメッセージを完全に削除しますか？相手側からも消えます。")
        case .achTabNormal:         return ("일반", "General", "一般")
        case .achTabHidden:         return ("히든", "Hidden", "隠し")
        case .achHiddenIntro:       return ("앱에서 단 한 명만 가질 수 있는 업적이에요. \n 달성 시 특별한 칭호 및 프로필 아이콘이 제공됩니다.",
                                            "Achievements only ONE person in the whole app can claim. The conditions are a secret!",
                                            "アプリでただ一人だけが手にできる実績。条件は秘密！")
        case .achHiddenAchiever:    return ("달성자: %@", "Achiever: %@", "達成者: %@")
        case .achHiddenUnclaimed:   return ("아직 아무도 달성하지 못했어요", "No one has claimed this yet", "まだ誰も達成していません")
        case .achHiddenByMe:        return ("내가 달성!", "You claimed it!", "あなたが達成！")
        case .filterPeriod:         return ("기간별 보기", "By period", "期間で見る")
        case .periodAll:            return ("전체 기간", "All time", "全期間")
        case .periodToday:          return ("오늘", "Today", "今日")
        case .periodWeek:           return ("최근 7일", "Last 7 days", "過去7日")
        case .periodMonth:          return ("최근 30일", "Last 30 days", "過去30日")
        case .periodYear:           return ("최근 1년", "Last year", "過去1年")
        case .shareDiary:           return ("공유", "Share", "共有")
        case .shareDiaryText:       return ("밤하늘에 별 하나를 남겼어요. 이 별은 그 장소에 가야 열려요 ✦",
                                            "I left a star in the night sky. It only opens at that place ✦",
                                            "夜空に星をひとつ残しました。この星はその場所でしか開けません ✦")
        case .shareCardTagline:     return ("이 별은 그 장소에 가야 열려요",
                                            "This star only opens at that place",
                                            "この星はその場所でしか開けません")
        case .shareCardUntitled:    return ("(제목 없음)", "(Untitled)", "（タイトルなし）")
        case .shareCardAnonymous:   return ("익명의 별", "An anonymous star", "名もなき星")
        case .inviteFriends:        return ("친구를 Stary 로 초대하기", "Invite friends to Stary", "友達をStaryに招待する")
        case .inviteFriendsDesc:    return ("가입한 친구가 초대 링크를 열면 둘 다 칭호를 받아요",
                                            "When an invited friend opens your link, you both earn a title",
                                            "招待した友達がリンクを開くと、ふたりとも称号がもらえます")
        case .inviteShareText:      return ("Stary 에서 함께 별을 남겨요 ✦ 가입 후 이 링크를 다시 열면 우리 둘 다 칭호를 받아요!",
                                            "Leave stars together on Stary ✦ Sign up and open this link again — we both get a title!",
                                            "Staryで一緒に星を残しましょう ✦ 登録後にこのリンクをもう一度開くと、ふたりとも称号がもらえます！")
        case .pioneerCondition:     return ("그 나라에 처음으로 별을 남기기",
                                            "Leave the first star in that country",
                                            "その国に最初の星を残す")
        // ⚠️ clusterHeader 는 %d(별 개수) 를 String(format:) 으로 채운다.
        case .clusterHeader:        return ("이곳에 별 %d개가 겹쳐 있어요",
                                            "%d stars are merged here",
                                            "ここに星が%d個重なっています")
        case .clusterHint:          return ("좌우로 넘겨 별을 살펴보세요",
                                            "Swipe left or right to browse the stars",
                                            "左右にスワイプして星を見てみましょう")
        case .clusterOpen:          return ("탭해서 들여다보기", "Tap to open", "タップして開く")
        case .nearbyStarTitle:      return ("근처에 아직 안 본 별이 있어요 ✦",
                                            "There's an unseen star nearby ✦",
                                            "近くにまだ見ていない星があります ✦")
        // ⚠️ nearbyStarBody 는 %d(거리 m) 를 String(format:) 으로 채운다.
        case .nearbyStarBody:       return ("약 %dm 앞 — 탭하면 지도에서 보여드릴게요",
                                            "About %dm ahead — tap to see it on the map",
                                            "約%dm先 — タップすると地図に表示します")
        case .friendNoChatYet:      return ("아직 대화가 없어요 — 탭해서 시작해 보세요",
                                            "No messages yet — tap to start",
                                            "まだメッセージがありません — タップして始めましょう")
        case .navMap:               return ("지도", "Map", "マップ")
        case .navMyDiary:           return ("내 다이어리", "My Diary", "マイ日記")
        case .navMusic:             return ("배경음악", "Music", "BGM")
        case .navNotification:      return ("알림", "Notifications", "通知")
        case .navUpload:            return ("새 다이어리 기록", "New Diary", "新しい日記")
        case .navDetail:            return ("별 들여다보기", "Look into the Star", "星をのぞく")
        case .navStarCluster:       return ("겹쳐진 별", "Merged Stars", "重なった星")
        case .drawerList:           return ("목록", "Menu", "メニュー")
        case .drawerLogout:         return ("로그아웃", "Sign out", "ログアウト")
        case .drawerLogin:          return ("로그인", "Sign in", "ログイン")
        case .filterAll:            return ("전체보기", "Show all", "すべて表示")
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
