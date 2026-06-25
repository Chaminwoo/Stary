import SwiftUI

/// 별 상세 — 본문/작성자/조회수. 가까이 있으면 본문 열람, 멀면 흐리게(거리 게이팅).
struct DetailScreen: View {
    let diary: Diary
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var location: LocationManager
    @State private var didCountView = false

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
                    stats
                }
                .padding(16)
            }
        }
        .navigationTitle("별")
        .navigationBarTitleDisplayMode(.inline)
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

    private var stats: some View {
        HStack(spacing: 24) {
            stat("좋아요", diary.likeCount, "heart.fill")
            stat("댓글", diary.commentCount, "bubble.right.fill")
            stat("조회", diary.viewCount, "eye.fill")
        }
        .foregroundStyle(Theme.textSecondary)
    }

    private func stat(_ label: String, _ value: Int, _ icon: String) -> some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
            Text("\(value)").font(.headline).foregroundStyle(Theme.textPrimary)
            Text(label).font(.caption2)
        }
    }

    private func distanceLabel(_ m: Double) -> String {
        m < 1000 ? "\(Int(m))m" : String(format: "%.1fkm", m / 1000)
    }
}
