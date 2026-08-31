import Foundation
import SwiftUI

/// 차단 목록 — 설정 > 안전 에서 진입. 내가 차단한 사용자를 보고 해제한다.
/// (Android `BlockedUsersScreen.kt` 패리티)
///
/// 차단은 `users/{내uid}/blocked/{상대uid}` 한 방향 기록이라 상대는 알 수 없고,
/// 차단된 사용자의 별은 지도/목록에서, 댓글은 상세에서 숨겨진다(MapScreen/ListScreen/DetailScreen).
/// 이름·사진은 차단 시점 스냅샷이라 상대 프로필 문서를 다시 읽지 않는다.
struct BlockedUsersScreen: View {
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var blocks: BlockStore
    @ObservedObject private var locale = LocaleManager.shared
    /// 차단 문서의 이름/사진은 차단 시점 스냅샷 → users/{uid} 의 현재 값으로 표시.
    @ObservedObject private var directory = UserDirectory.shared
    /// 해제 확인 대상(nil = 알림창 닫힘).
    @State private var confirmTarget: BlockedUser?
    @State private var profileTarget: ProfileTarget?

    private struct ProfileTarget: Identifiable {
        let userId: String
        let userName: String
        var id: String { userId }
    }

    var body: some View {
        ZStack {
            // 설정/프로필과 동일한 우주 배경 톤.
            ScreenBackground(name: "mydiary_bg", darken: 0.84)

            if blocks.blockedUsers.isEmpty {
                StaryEmptyState(title: locale.t(.blockedEmpty),
                                description: locale.t(.blockedEmptyDesc),
                                starType: 7,       // 초승달 — 조용히 가려둔 상태
                                starColorIndex: 0) // 화이트
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(locale.t(.blockedHint))
                            .font(.minSans(12.5)).foregroundStyle(Theme.textSecondary)
                            .padding(.bottom, 4)
                        ForEach(blocks.blockedUsers) { user in
                            row(user)
                        }
                    }
                    .padding(20)
                }
            }
        }
        .navigationTitle(locale.t(.navBlockedUsers))
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: Binding(
            get: { profileTarget != nil }, set: { if !$0 { profileTarget = nil } }
        )) {
            if let t = profileTarget {
                UserProfileScreen(userId: t.userId, userName: t.userName)
            }
        }
        // 오탭으로 바로 풀리지 않게 해제도 확인을 거친다(Android 확인 다이얼로그 패리티).
        .staryConfirmDialog(locale.t(.unblockAction),
                            isPresented: Binding(get: { confirmTarget != nil },
                                                 set: { if !$0 { confirmTarget = nil } }),
                            message: confirmTarget.map {
                                String(format: locale.t(.unblockConfirmMsg), displayName($0))
                            },
                            confirmTitle: locale.t(.unblockAction),
                            destructive: true) { [target = confirmTarget] in
            // ⚠️ 확인 시 팝업이 먼저 닫히며 confirmTarget 이 nil 이 되므로 **캡처한 값**을 쓴다.
            if let target { unblock(target) }
        }
        .onAppear { if let uid = auth.uid { blocks.start(uid: uid) } }
    }

    /// 차단 목록 한 줄 — [사진] [이름 / 차단일] [차단 해제]. 사진·이름 탭 = 프로필 열기.
    private func row(_ user: BlockedUser) -> some View {
        HStack(spacing: 12) {
            Button {
                profileTarget = ProfileTarget(userId: user.userId, userName: user.userName)
            } label: {
                HStack(spacing: 12) {
                    avatar(user)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(displayName(user))
                            .font(.minSans(15)).foregroundStyle(Theme.textPrimary)
                            .lineLimit(1)
                        Text(String(format: locale.t(.blockedAtFormat), formatBlockedAt(user.createdAt)))
                            .font(.minSans(12)).foregroundStyle(Theme.textSecondary)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button { confirmTarget = user } label: {
                Text(locale.t(.unblockAction))
                    .font(.minSans(13))
                    .foregroundStyle(softRed)
                    .padding(.horizontal, 14).padding(.vertical, 7)
                    .background(softRed.opacity(0.14), in: Capsule())
                    .overlay(Capsule().strokeBorder(softRed.opacity(0.32), lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
        .padding(10)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16))
    }

    private func avatar(_ user: BlockedUser) -> some View {
        let photo = directory.photoUrl(user.userId, fallback: user.photoUrl)
        return Group {
            if !photo.isEmpty {
                AvatarThumbView(url: photo, pixelSize: 132)
            } else {
                Theme.surfaceAlt.overlay(
                    Text(String(displayName(user).prefix(1)).uppercased())
                        .font(.minSans(16))
                        .foregroundStyle(Theme.navyAccent)
                )
            }
        }
        .frame(width: 44, height: 44)
        .clipShape(Circle())
        .watchUser(user.userId)
    }

    private func unblock(_ user: BlockedUser) {
        guard let myUid = auth.uid else { return }
        confirmTarget = nil
        Task { await ModerationRepository.unblock(userId: myUid, targetId: user.userId) }
    }

    private func displayName(_ user: BlockedUser) -> String {
        let name = directory.name(user.userId, fallback: user.userName)
        return name.isEmpty ? locale.t(.unknownUser) : name
    }

    /// 차단 시각 → yyyy.MM.dd (값이 없으면 "-").
    private func formatBlockedAt(_ millis: Int64) -> String {
        guard millis > 0 else { return "-" }
        let f = DateFormatter()
        f.dateFormat = "yyyy.MM.dd"
        return f.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }

    private let softRed = Color(red: 1.0, green: 0.42, blue: 0.42) // 0xFFFF6B6B
}
