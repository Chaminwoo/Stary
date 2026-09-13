import SwiftUI

/// 웰컴 별([TutorialStarState]) 게시물 — Android `feature/diary/screen/TutorialStarDetailScreen.kt` 패리티.
///
/// 지도에서 튜토리얼 별을 탭하면 일반 별처럼 파장 → 상세로 오고, `MapScreen` 이 id 가
/// `TutorialStarState.diaryId` 면 [DetailScreen] 대신 이 화면을 push 한다.
///
/// [DetailScreen] 을 재사용하지 않는 이유(Android 와 같음): 좋아요/댓글 리스너·조회수·열람 기록이 전부
/// 다이어리 id 로 Firestore 를 읽고 써서, 서버에 없는 이 글로는 가짜 쓰기가 나간다. 레이아웃만 흉내 낸다
/// (4:3 히어로 + 스크림 + 별·작성자·날짜 → 제목 → 본문 카드). **DetailScreen 레이아웃을 크게 바꾸면 여기도 맞출 것.**
///
/// - 히어로 = `loading_dipper`(상세 로딩 플레이스홀더, 북두칠성이 그려지는 애니메이션 WebP, 640×480 = 4:3).
/// - 화면이 열리는 순간 `markDone()` — 지도가 가려진 동안 마커가 빠져 돌아가면 별이 이미 없다.
/// - 좋아요/공유/댓글 없음. 민트→블루 "확인" = pop.
struct TutorialStarDetailScreen: View {
    @Environment(\.dismiss) private var dismiss
    /// 방금 근처에 놓아 둔 별이라는 설정 — 날짜는 여는 시점(DetailScreen 과 같은 형식).
    @State private var openedAt = Date()

    private var accent: Color { StarStyle.color(TutorialStarState.starColor) }

    private static let createdFmt: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy.MM.dd HH:mm"
        return f
    }()

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 0) {
                    heroHeader

                    VStack(alignment: .leading, spacing: 0) {
                        Spacer().frame(height: 18)
                        Text(LocaleManager.shared.t(.tutorialStarTitle))
                            .font(.minSans(24, .semibold))
                            .foregroundStyle(Theme.textPrimary)
                        Spacer().frame(height: 16)
                        bodyCard
                        Spacer().frame(height: 24)
                        confirmButton
                        Spacer().frame(height: 40)
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
        .navigationTitle(LocaleManager.shared.t(.navDetail))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { TutorialStarState.shared.markDone() }
    }

    // ── 헤더: 별자리 플레이스홀더 영상 + 하단 스크림 + 별/작성자/날짜 (DetailScreen heroHeader 와 같은 구조) ──

    private var heroHeader: some View {
        Color.clear
            .aspectRatio(4.0 / 3.0, contentMode: .fit)
            .overlay {
                if let data = BundleImage.data("loading_dipper") {
                    GifImageView(data: data)
                } else {
                    Theme.surfaceAlt
                }
            }
            .clipped()
            .overlay(
                // 실제 미디어가 있을 때의 스크림(DetailScreen hasMedia 분기와 같은 값).
                LinearGradient(stops: [
                    .init(color: .clear, location: 0),
                    .init(color: .black.opacity(0.4), location: 0.55),
                    .init(color: Theme.background, location: 1),
                ], startPoint: .top, endPoint: .bottom)
            )
            .overlay(alignment: .bottomLeading) {
                // 작성자는 시스템(STARY)이라 프로필 진입이 없다.
                HStack(spacing: 0) {
                    StarView(type: TutorialStarState.starType, colorIndex: TutorialStarState.starColor,
                             size: 18, glow: false)
                    Spacer().frame(width: 8)
                    Text(TutorialStarState.authorName)
                        .font(.minSans(13))
                        .foregroundStyle(Theme.textPrimary.opacity(0.85))
                    Text("  ·  ").font(.minSans(13)).foregroundStyle(Theme.textSecondary)
                    Text(Self.createdFmt.string(from: openedAt))
                        .font(.minSans(13)).foregroundStyle(Theme.textSecondary)
                }
                .padding(20)
            }
    }

    // ── 본문 카드 — DetailScreen bodyCard 와 같은 배경/별색 테두리 ──

    private var bodyCard: some View {
        Text(LocaleManager.shared.t(.tutorialStarMsg))
            .font(.minSans(16))
            .lineSpacing(8)
            .foregroundStyle(Theme.textPrimary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(Color(hex: 0x14181C).opacity(0.8), in: RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16).strokeBorder(
                    LinearGradient(colors: [accent.opacity(0.45), accent.opacity(0.15)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    lineWidth: 1
                )
            )
    }

    /// 확인 → 지도로. 코치마크/첫 진입 안내(FirstVisitInfo)와 같은 민트→블루 버튼.
    private var confirmButton: some View {
        Button { dismiss() } label: {
            Text(LocaleManager.shared.t(.tutorialStarConfirm))
                .font(.minSans(15, .semibold))
                .foregroundStyle(Color(hex: 0x06121E))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background(
                    LinearGradient(colors: [Theme.mint, Theme.mintBlue],
                                   startPoint: .leading, endPoint: .trailing),
                    in: RoundedRectangle(cornerRadius: 14)
                )
        }
        .buttonStyle(.plain)
    }
}
