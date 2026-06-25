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

    init(diary: Diary) {
        self.diary = diary
        _vm = StateObject(wrappedValue: DetailViewModel(diary: diary))
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
                    Text(diary.isAnonymous ? "익명" : diary.userName)
                        .font(.subheadline)
                        .foregroundStyle(Theme.textSecondary)

                    bodyCard
                    likeBar
                    if canOpen { commentsSection }
                }
                .padding(16)
            }
        }
        .navigationTitle("별")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { vm.start(uid: auth.uid) }
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
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(c.userName).font(.caption).bold().foregroundStyle(Theme.textSecondary)
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
