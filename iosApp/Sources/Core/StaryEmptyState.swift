import SwiftUI

/// 빈 화면 공용 표현 — **떠 있는 별 하나 + 문구(+ 선택 액션)**.
/// (Android `core/ui/StaryEmptyState.kt` 패리티 — 부유 주기 6초, 궤도 스파클 3개로 값 동일)
///
/// 알림/친구/내 별/차단 목록 등 빈 상태가 전부 "검은 배경에 회색 한 줄"이라 신규 사용자가
/// 가장 많이 보는 화면이 제일 허전했다. 별 언어(크리스탈 별 + 부유 + 스파클)를 그대로 써서
/// "아직 비어 있음"도 앱의 일부처럼 보이게 한다.
struct StaryEmptyState: View {
    let title: String
    var description: String? = nil
    /// 화면 성격에 맞는 별(예: 알림=골드 스파클, 친구=민트 보석).
    var starType: Int = 1
    var starColorIndex: Int = 9
    var actionLabel: String? = nil
    var onAction: (() -> Void)? = nil

    private var accent: Color { StarStyle.color(starColorIndex) }

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                // 후광 + 궤도 스파클 3개(별보다 느리게 돈다). 장식 전용(터치 통과).
                FloatingHalo(accent: accent)
                    .frame(width: 104, height: 104)
                FloatingStar(type: starType, colorIndex: starColorIndex)
            }
            .frame(width: 104, height: 104)

            Spacer().frame(height: 16)
            Text(title)
                .font(.minSans(15))
                .foregroundStyle(Theme.textPrimary)
                .multilineTextAlignment(.center)
            if let description {
                Spacer().frame(height: 6)
                Text(description)
                    .font(.minSans(12.5))
                    .foregroundStyle(Theme.textSecondary)
                    .multilineTextAlignment(.center)
            }
            if let actionLabel, let onAction {
                Spacer().frame(height: 18)
                Button(action: onAction) {
                    Text(actionLabel)
                        .font(.minSans(13))
                        .foregroundStyle(Theme.navyAccent)
                        .padding(.horizontal, 20).padding(.vertical, 9)
                        .background(Theme.navyAccent.opacity(0.14), in: Capsule())
                        .overlay(Capsule().strokeBorder(Theme.navyAccent.opacity(0.34), lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 40)
        .frame(maxWidth: .infinity)
    }
}

/// 6초 주기로 위아래로 흔들리는 후광 + 궤도 스파클.
private struct FloatingHalo: View {
    let accent: Color

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { ctx, size in
                let t = timeline.date.timeIntervalSinceReferenceDate
                let phase = (t / 6.0).truncatingRemainder(dividingBy: 1) * 2 * .pi
                let r = Double(min(size.width, size.height)) / 2
                let cx = Double(size.width) / 2
                let cy = Double(size.height) / 2 + sin(phase) * 5

                // 후광.
                let haloR = CGFloat(r * 0.9)
                let rect = CGRect(x: CGFloat(cx) - haloR, y: CGFloat(cy) - haloR,
                                  width: haloR * 2, height: haloR * 2)
                ctx.fill(Path(ellipseIn: rect),
                         with: .radialGradient(
                            Gradient(colors: [accent.opacity(0.18), .clear]),
                            center: CGPoint(x: CGFloat(cx), y: CGFloat(cy)),
                            startRadius: 0, endRadius: haloR))

                // 궤도 스파클 3개 — 뒤로 돌 때 더 흐리게(깊이감).
                for i in 0..<3 {
                    let a = phase * 0.45 + Double(i) * (2 * .pi / 3)
                    let orbit = r * 0.66
                    let sx = cx + cos(a) * orbit
                    let sy = cy + sin(a) * orbit * 0.55
                    let depth = (sin(a) + 1) / 2
                    let dotR = CGFloat(1.6 + 1.1 * depth)
                    let dot = CGRect(x: CGFloat(sx) - dotR, y: CGFloat(sy) - dotR,
                                     width: dotR * 2, height: dotR * 2)
                    ctx.fill(Path(ellipseIn: dot),
                             with: .color(accent.opacity(0.18 + 0.42 * depth)))
                }
            }
        }
        .allowsHitTesting(false)
    }
}

/// 같은 위상으로 함께 떠오르는 크리스탈 별(미세 맥동 포함).
private struct FloatingStar: View {
    let type: Int
    let colorIndex: Int

    var body: some View {
        TimelineView(.animation) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate
            let phase = (t / 6.0).truncatingRemainder(dividingBy: 1) * 2 * .pi
            let pulse = 1 + 0.06 * ((sin(phase * 1.7) + 1) / 2)
            Image(uiImage: StarCrystal.image(type: type, colorIndex: colorIndex, size: 42))
                .resizable()
                .frame(width: 42, height: 42)
                .scaleEffect(CGFloat(pulse))
                .offset(y: CGFloat(sin(phase) * 5))
                .opacity(0.92)
        }
        .allowsHitTesting(false)
    }
}
