import SwiftUI
import UIKit

/// 겹친 별 카드 뷰어를 여는 시트 선택값(`.sheet(item:)` 용).
/// id 는 대표 별 id — 같은 무리를 다시 탭하면 같은 시트로 취급된다.
struct ClusterSelection: Identifiable {
    let members: [Diary]
    var id: String { members.first?.id ?? UUID().uuidString }
}

/// 30m 안에서 합쳐진 별 무리 열람 화면 — Android `StarClusterScreen` 패리티.
///
/// 합쳐진 다이어리들을 우선순위(좋아요 내림차순 → 오래된 순, 지도 대표 선정과 동일) 순서의
/// **좌우 스와이프 카드**로 보여준다. 카드는 간략 정보(별/제목/하트/댓글 수)만, 탭하면 상세로.
/// 스크린 배경은 앱 공통 톤, **카드 각각의 배경은 공유 카드 프레임**(share_card_bg).
struct StarClusterView: View {
    let diaries: [Diary]
    var onOpenDiary: (Diary) -> Void
    var onClose: () -> Void

    @State private var page = 0

    /// 지도 대표 선정과 같은 우선순위로 재정렬(호출부 정렬 상태와 무관하게 안전).
    private var ordered: [Diary] {
        diaries.sorted(by: StarMerge.precedes)
    }

    private var accent: Color {
        StarStyle.color(ordered.first?.starColor ?? 0)
    }

    var body: some View {
        ZStack {
            // Android StarClusterScreen 배경 — 친구 스크린과 동일(mydiary_bg + 검정 0.82 틴트).
            ScreenBackground(name: "mydiary_bg", darken: 0.82)

            VStack(spacing: 0) {
                header
                    .padding(.top, 18)

                // 카드 페이저 — 세로로 긴 카드(포트레이트), 좌우 스와이프.
                TabView(selection: $page) {
                    ForEach(Array(ordered.enumerated()), id: \.element.id) { index, diary in
                        ClusterCard(diary: diary, rank: index + 1) { onOpenDiary(diary) }
                            .padding(.horizontal, 44)
                            .padding(.vertical, 10)
                            .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))

                // 페이지 인디케이터 점
                HStack(spacing: 6) {
                    ForEach(ordered.indices, id: \.self) { i in
                        Circle()
                            .fill(i == page ? accent : Color.white.opacity(0.25))
                            .frame(width: i == page ? 8 : 6, height: i == page ? 8 : 6)
                    }
                }
                .padding(.vertical, 18)
            }

            // 뒤로가기(X) — 좌상단. 화면 밖으로 나가지 않게 안쪽 여백을 준다(#16).
            VStack {
                HStack {
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 40, height: 40)
                            .background(Color.black.opacity(0.35), in: Circle())
                    }
                    Spacer()
                }
                .padding(.leading, 16)
                .padding(.top, 8)
                Spacer()
            }
        }
    }

    /// 헤더 — 겹쳐진 별 모양들을 살짝 겹쳐 보여주고 개수 안내.
    /// 카드를 스와이프하면 **지금 보고 있는 별만 밝아지고 커진다**(34-1, Android 패리티).
    /// 헤더는 5개까지만 그리므로 6번째 이후 카드에선 아무것도 강조되지 않는다(의도).
    private var header: some View {
        VStack(spacing: 10) {
            HStack(spacing: -6) {
                ForEach(Array(ordered.prefix(5).enumerated()), id: \.element.id) { i, d in
                    let active = page == i
                    let side: CGFloat = i == 0 ? 30 : 22
                    ZStack {
                        // 활성 별에만 옅은 후광 — 별색 그대로.
                        if active {
                            RadialGradient(
                                colors: [StarStyle.color(d.starColor).opacity(0.9), .clear],
                                center: .center, startRadius: 0, endRadius: side / 2
                            )
                            .opacity(0.35)
                        }
                        StarView(type: d.starType, colorIndex: d.starColor, size: side)
                            .opacity(active ? 1 : 0.35)
                            .scaleEffect(active ? 1.15 : 1)
                    }
                    .frame(width: side, height: side)
                }
            }
            .animation(.easeOut(duration: 0.2), value: page)
            Text(String(format: LocaleManager.shared.t(.clusterHeader), ordered.count))
                .font(.minSans(17, .semibold))
                .foregroundStyle(Theme.textPrimary)
            Text(LocaleManager.shared.t(.clusterHint))
                .font(.minSans(12))
                .foregroundStyle(Theme.textSecondary)
        }
    }
}

/// 합쳐진 별 카드 — 세로로 긴 직사각형. 배경은 공유 카드 프레임(밤하늘) + 가독성 스크림.
private struct ClusterCard: View {
    let diary: Diary
    let rank: Int
    var onTap: () -> Void

    private var accent: Color { StarStyle.color(diary.starColor) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // 별 영역 — 사진/영상은 띄우지 않고 항상 별만 크게 보여준다(카드의 주인공은 별).
            ZStack {
                RadialGradient(
                    colors: [accent.opacity(0.18), .clear],
                    center: .center, startRadius: 2, endRadius: 140
                )
                StarView(type: diary.starType, colorIndex: diary.starColor, size: 84)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 16))

            Spacer().frame(height: 12)

            HStack(spacing: 8) {
                StarView(type: diary.starType, colorIndex: diary.starColor, size: 18, glow: false)
                Text(diary.title.isEmpty ? LocaleManager.shared.t(.shareCardUntitled) : diary.title)
                    .font(.minSans(17))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(1)
            }
            .padding(.leading, 4)
            Spacer().frame(height: 6)
            Text("#\(rank) · \(dateText)")
                .font(.minSans(12))
                .foregroundStyle(Theme.textSecondary)
                .padding(.leading, 4)

            Spacer().frame(height: 12)

            // 간략 지표 — 하트/댓글 수만
            HStack(spacing: 5) {
                Image(systemName: "heart.fill")
                    .font(.system(size: 13))
                    .foregroundStyle(Color(red: 1.0, green: 0.42, blue: 0.51))
                Text("\(diary.likeCount)")
                    .font(.minSans(13))
                    .foregroundStyle(Theme.textPrimary)
                Spacer().frame(width: 11)
                Image(systemName: "bubble.right")
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.textSecondary)
                Text("\(diary.commentCount)")
                    .font(.minSans(13))
                    .foregroundStyle(Theme.textPrimary)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
    }

    /// 카드 배경 = 공유 카드와 같은 밤하늘 프레임 + 아래로 갈수록 짙은 스크림(텍스트 가독성).
    /// 이미지 로드 실패 시 기존 어두운 톤으로 폴백(카드는 항상 보인다).
    private var cardBackground: some View {
        ZStack {
            Color(red: 0.078, green: 0.094, blue: 0.122)
            if let bg = ShareCardBackground.image {
                Image(uiImage: bg)
                    .resizable()
                    .scaledToFill()
            }
            LinearGradient(
                colors: [
                    Color(red: 0.02, green: 0.027, blue: 0.051).opacity(0.24),
                    Color(red: 0.02, green: 0.027, blue: 0.051).opacity(0.16),
                    Color(red: 0.02, green: 0.027, blue: 0.051).opacity(0.72),
                ],
                startPoint: .top, endPoint: .bottom
            )
        }
    }

    private var dateText: String {
        let f = DateFormatter()
        f.dateFormat = "yyyy.MM.dd"
        return f.string(from: diary.createdDate)
    }
}

/// 공유 카드 밤하늘 배경(`share_card_bg.webp`) — 번들에서 1회 로드 후 재사용.
/// (Android `assets/share_card_bg.webp` 와 같은 파일 — 겹친 별 카드/공유 카드가 공유한다.)
enum ShareCardBackground {
    static let image: UIImage? = {
        guard let url = Bundle.main.url(forResource: "share_card_bg", withExtension: "webp"),
              let data = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: data)
    }()
}
