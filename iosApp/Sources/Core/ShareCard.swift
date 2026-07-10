import CoreLocation
import SwiftUI
import UIKit

/// 다이어리 공유 카드(체크리스트 30) — Android `ShareCardHelper` 패리티.
/// 밤하늘 배경 + 그 별의 모양/색 + 제목/장소 힌트를 세로형(360×640pt × scale3 = 1080×1920px)
/// 이미지로 렌더해 시스템 공유 시트로 내보낸다. 텍스트에 웹 랜딩 링크를 붙인다.
enum ShareCard {

    /// 카드 렌더 → UIActivityViewController. 실패 시 조용히 무시.
    @MainActor
    static func share(diary: Diary) async {
        guard let diaryId = diary.id, !diaryId.isEmpty else { return }
        let hint = await locationHint(lat: diary.latitude, lng: diary.longitude)
        let renderer = ImageRenderer(content: ShareCardView(diary: diary, locationHint: hint))
        renderer.scale = 3
        guard let image = renderer.uiImage else { return }
        let text = LocaleManager.shared.t(.shareDiaryText) + "\n" + AppConfig.shareLink(diaryId: diaryId)
        let vc = UIActivityViewController(activityItems: [image, text], applicationActivities: nil)
        topViewController()?.present(vc, animated: true)
    }

    /// 역지오코딩 동네 힌트(예: "서울 광진구"). 실패 시 nil → 카드에서 생략.
    private static func locationHint(lat: Double, lng: Double) async -> String? {
        let location = CLLocation(latitude: lat, longitude: lng)
        guard let placemark = try? await CLGeocoder().reverseGeocodeLocation(location).first else { return nil }
        var parts: [String] = []
        for p in [placemark.administrativeArea, placemark.locality ?? placemark.subLocality] {
            if let p, !p.isEmpty, !parts.contains(p) { parts.append(p) }
        }
        return parts.isEmpty ? nil : parts.joined(separator: " ")
    }

    /// 공유 시트를 띄울 최상단 뷰컨트롤러.
    @MainActor
    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

/// 카드 본체 — ImageRenderer 로만 쓰는 오프스크린 뷰.
struct ShareCardView: View {
    let diary: Diary
    let locationHint: String?

    private var accent: Color { StarStyle.color(diary.starColor) }

    var body: some View {
        ZStack {
            // 1) 밤하늘 그라데이션
            LinearGradient(
                colors: [Color(red: 0.043, green: 0.063, blue: 0.149),
                         Color(red: 0.027, green: 0.039, blue: 0.094),
                         Color(red: 0.016, green: 0.020, blue: 0.047)],
                startPoint: .top, endPoint: .bottom
            )

            // 2) 잔별 — 다이어리 id 시드 고정(같은 별 = 같은 하늘)
            Canvas { ctx, size in
                var rng = SeededRandom(seed: UInt64(bitPattern: Int64(diary.id.hashValue)))
                for _ in 0..<170 {
                    let x = rng.nextFloat() * size.width
                    let y = rng.nextFloat() * size.height
                    let r = 0.3 + CGFloat(pow(rng.nextFloat(), 2)) * 1.1
                    let alpha = 0.15 + rng.nextFloat() * 0.65
                    ctx.fill(Path(ellipseIn: CGRect(x: x, y: y, width: r * 2, height: r * 2)),
                             with: .color(.white.opacity(alpha)))
                }
            }

            VStack(spacing: 0) {
                Spacer().frame(height: 130)
                // 3) 중앙 별 — 지도 마커와 같은 정의(StarShape/StarStyle)
                StarView(type: diary.starType, colorIndex: diary.starColor, size: 150)
                    .shadow(color: accent.opacity(0.8), radius: 34)
                Spacer().frame(height: 96)

                // 4) 제목 + 작성자·날짜 + 장소 힌트
                Text(diary.title.isEmpty ? LocaleManager.shared.t(.shareCardUntitled) : diary.title)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .padding(.horizontal, 40)
                Spacer().frame(height: 14)
                Text("\(authorName) · \(dateText)")
                    .font(.system(size: 13))
                    .foregroundStyle(.white.opacity(0.7))
                if let locationHint {
                    Spacer().frame(height: 12)
                    Text("✦ \(locationHint)")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(accent)
                }

                Spacer()

                // 5) 태그라인 + 브랜드
                Text(LocaleManager.shared.t(.shareCardTagline))
                    .font(.system(size: 14))
                    .foregroundStyle(.white.opacity(0.8))
                Spacer().frame(height: 10)
                Text("STARY")
                    .font(.system(size: 19, weight: .bold))
                    .kerning(6)
                    .foregroundStyle(.white)
                Spacer().frame(height: 70)
            }
        }
        .frame(width: 360, height: 640)
    }

    private var authorName: String {
        (diary.isAnonymous || diary.userName.isEmpty)
            ? LocaleManager.shared.t(.shareCardAnonymous) : diary.userName
    }

    private var dateText: String {
        let f = DateFormatter()
        f.dateFormat = "yyyy.MM.dd"
        return f.string(from: diary.createdDate)
    }
}

/// 시드 고정 난수(xorshift64) — 카드 잔별 배치용.
private struct SeededRandom {
    private var state: UInt64
    init(seed: UInt64) { state = seed == 0 ? 0x9E3779B97F4A7C15 : seed }
    mutating func next() -> UInt64 {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
    mutating func nextFloat() -> CGFloat { CGFloat(next() % 10_000) / 10_000 }
}
