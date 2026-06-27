import SwiftUI

/// 별 상세 — 본문/작성자 + 좋아요·댓글. 가까이 있으면 본문 열람, 멀면 거리 게이팅.
struct DetailScreen: View {
    let diary: Diary
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var location: LocationManager
    @StateObject private var vm: DetailViewModel
    @State private var didCountView = false
    @State private var commentText = ""
    @State private var profileTarget: ProfileTarget?

    init(diary: Diary) {
        self.diary = diary
        _vm = StateObject(wrappedValue: DetailViewModel(diary: diary))
    }

    /// 타인 프로필 진입 대상.
    struct ProfileTarget: Identifiable {
        let userId: String
        let userName: String
        var id: String { userId }
    }

    /// 익명/빈 userId 가 아니면 그 작성자의 프로필을 띄운다.
    private func openProfile(_ userId: String, _ userName: String) {
        guard !userId.isEmpty else { return }
        profileTarget = ProfileTarget(userId: userId, userName: userName)
    }

    private var distanceM: Double {
        let me = location.coordinateOrDefault
        return Geo.distanceMeters(lat1: me.latitude, lng1: me.longitude,
                                  lat2: diary.latitude, lng2: diary.longitude)
    }

    private var isOwner: Bool { diary.userId == auth.uid }
    private var canOpen: Bool { isOwner || distanceM <= AppConfig.diaryOpenRadiusM }

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 18) {
                    StarView(type: diary.starType, colorIndex: diary.starColor, size: 84)
                        .padding(.top, 12)
                    Text(diary.title.isEmpty ? "(제목 없음)" : diary.title)
                        .font(.title2).bold()
                        .foregroundStyle(Theme.textPrimary)
                    if diary.isAnonymous || diary.userId.isEmpty {
                        Text("익명")
                            .font(.subheadline)
                            .foregroundStyle(Theme.textSecondary)
                    } else {
                        Button { openProfile(diary.userId, diary.userName) } label: {
                            HStack(spacing: 4) {
                                Text(diary.userName)
                                Image(systemName: "chevron.right").font(.caption2)
                            }
                            .font(.subheadline)
                            .foregroundStyle(Theme.textSecondary)
                        }
                        .buttonStyle(.plain)
                    }

                    if canOpen, !diary.imageUrl.isEmpty {
                        AsyncImage(url: URL(string: diary.imageUrl)) { image in
                            image.resizable().scaledToFit()
                        } placeholder: {
                            RoundedRectangle(cornerRadius: 14).fill(Theme.surfaceAlt).frame(height: 200)
                        }
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    bodyCard
                    likeBar
                    if canOpen { commentsSection }
                }
                .padding(16)
            }
        }
        .navigationTitle("별")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $profileTarget) { t in
            NavigationStack {
                UserProfileScreen(userId: t.userId, userName: t.userName)
            }
            .environmentObject(auth)
            .environmentObject(store)
            .environmentObject(location) // UserProfile → 별 상세 푸시 시 필요
        }
        .onAppear {
            vm.start(uid: auth.uid)
            MusicManager.shared.playOpenDiary() // 별(다이어리) 열람 효과음
        }
        .onDisappear { vm.stop() }
        .task {
            guard !didCountView, !isOwner, let id = diary.id else { return }
            didCountView = true
            await store.incrementView(id)
        }
    }

    private var bodyCard: some View {
        VStack(spacing: 10) {
            if canOpen {
                Text(diary.content.isEmpty ? "내용이 없어요." : diary.content)
                    .font(.body)
                    .foregroundStyle(Theme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Image(systemName: "lock.fill").foregroundStyle(Theme.textFaint)
                Text("이 별 가까이(\(Int(AppConfig.diaryOpenRadiusM))m)로 가면 열람할 수 있어요.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                Text("현재 약 \(distanceLabel(distanceM)) 떨어져 있어요.")
                    .font(.caption)
                    .foregroundStyle(Theme.textFaint)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16))
    }

    private var likeBar: some View {
        HStack(spacing: 20) {
            Button {
                Task { await vm.toggleLike(uid: auth.uid, userName: auth.displayName) }
            } label: {
                Label("\(vm.likeCount)", systemImage: vm.isLiked ? "heart.fill" : "heart")
                    .foregroundStyle(vm.isLiked ? .pink : Theme.textSecondary)
            }
            Label("\(vm.comments.count)", systemImage: "bubble.right.fill")
                .foregroundStyle(Theme.textSecondary)
            Label("\(diary.viewCount)", systemImage: "eye.fill")
                .foregroundStyle(Theme.textSecondary)
            Spacer()
        }
        .font(.headline)
    }

    private var commentsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                TextField("댓글 달기…", text: $commentText, axis: .vertical)
                    .lineLimit(1...4)
                    .padding(10)
                    .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
                    .foregroundStyle(Theme.textPrimary)
                Button {
                    let t = commentText
                    commentText = ""
                    Task { await vm.addComment(uid: auth.uid, userName: auth.displayName, text: t) }
                } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundStyle(commentText.isEmpty ? Theme.textFaint : Theme.mint)
                }
                .disabled(commentText.trimmingCharacters(in: .whitespaces).isEmpty)
            }

            ForEach(vm.comments) { c in
                HStack(alignment: .top, spacing: 10) {
                    // 인스타식 프로필 아바타 (top 을 사용자 이름 top 에 맞춤) — 탭 시 작성자 프로필
                    Button { openProfile(c.userId, c.userName) } label: {
                        CommentAvatar(userId: c.userId, userName: c.userName)
                            .padding(.top, 2)
                    }
                    .buttonStyle(.plain)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Button { openProfile(c.userId, c.userName) } label: {
                                Text(c.userName).font(.caption).bold().foregroundStyle(Theme.textSecondary)
                            }
                            .buttonStyle(.plain)
                            Spacer()
                            if c.userId == auth.uid {
                                Button {
                                    Task { await vm.deleteComment(c) }
                                } label: {
                                    Image(systemName: "trash").font(.caption2).foregroundStyle(Theme.textFaint)
                                }
                            }
                        }
                        Text(c.content).font(.subheadline).foregroundStyle(Theme.textPrimary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private func distanceLabel(_ m: Double) -> String {
        m < 1000 ? "\(Int(m))m" : String(format: "%.1fkm", m / 1000)
    }
}

/// userId → 프로필 사진 URL 캐시 — 댓글마다 같은 작성자를 반복 조회하지 않게.
@MainActor
final class ProfileImageCache {
    static let shared = ProfileImageCache()
    private var cache: [String: String] = [:]   // userId → url ("" = 사진 없음)

    /// 없으면 nil. 한 번 조회한 userId 는 캐시에서 즉시 반환.
    func url(for userId: String) async -> String? {
        guard !userId.isEmpty else { return nil }
        if let cached = cache[userId] { return cached.isEmpty ? nil : cached }
        let url = ((try? await FirestoreService.users.document(userId).getDocument())?
            .get("profileImageUrl") as? String) ?? ""
        cache[userId] = url
        return url.isEmpty ? nil : url
    }
}

/// 인스타식 댓글 프로필 아바타 — userId 로 사진 조회, 없으면 이니셜 폴백. (Android CommentItem 패리티)
private struct CommentAvatar: View {
    let userId: String
    let userName: String
    @State private var url: String?

    private var initial: String {
        let first = userName.trimmingCharacters(in: .whitespaces).prefix(1).uppercased()
        return first.isEmpty ? "?" : first
    }

    var body: some View {
        Group {
            if let url, !url.isEmpty {
                AsyncImage(url: URL(string: url)) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Theme.surfaceAlt
                }
            } else {
                Theme.surfaceAlt.overlay(
                    Text(initial)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.mint)
                )
            }
        }
        .frame(width: 32, height: 32)
        .clipShape(Circle())
        .overlay(Circle().stroke(Theme.mint.opacity(0.30), lineWidth: 1))
        .task(id: userId) { url = await ProfileImageCache.shared.url(for: userId) }
    }
}
