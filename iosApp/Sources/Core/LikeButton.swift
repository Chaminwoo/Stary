import SwiftUI

/// 좋아요 버튼 — 하트 pop + 크리스탈 파편 버스트 + 숫자 롤링.
/// (Android `core/ui/LikeButton.kt` 패리티 — 파편 개수/시간/이징 동일)
///
/// 좋아요를 **켤 때만** 버스트/진동이 나간다(해제는 조용히) — 취소까지 축하하면 과하다.
struct LikeButton: View {
    let isLiked: Bool
    let count: Int
    /// 파편 색 — 그 별의 색을 넘기면 다이어리마다 다른 색으로 터진다.
    var accent: Color = Color(red: 1.0, green: 0.42, blue: 0.54)
    let onToggle: () -> Void

    /// 버스트 1회분 = nonce 증가. 파편 각도/길이는 nonce 로 결정론적 랜덤.
    @State private var burstNonce = 0
    @State private var burstProgress: Double = 1   // 1 = 끝난 상태(안 그림)
    @State private var pop: CGFloat = 1

    private static let burstDuration: Double = 0.62

    var body: some View {
        HStack(spacing: 2) {
            Button {
                if !isLiked {
                    fire()
                    Haptics.medium()
                }
                onToggle()
            } label: {
                Image(systemName: isLiked ? "heart.fill" : "heart")
                    .font(.system(size: 20))
                    .foregroundStyle(isLiked ? accent : Theme.textSecondary)
                    .scaleEffect(pop)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            // ⚠️ 파편은 **레이아웃에 참여하면 안 된다**(`.background` — ZStack 아님).
            // ZStack 자식이던 시절엔 버스트가 뜨는 동안만 72pt 로 커져 행 전체가 밀렸다.
            // `.background` 는 버튼(44pt) 뒤에 그리기만 하고 크기에 영향을 주지 않는다.
            .background {
                if burstProgress < 1 {
                    BurstShards(nonce: burstNonce, progress: burstProgress, accent: accent)
                        .frame(width: 72, height: 72)
                        .allowsHitTesting(false)
                }
            }

            // 숫자 롤링 — 늘면 위로, 줄면 아래로 흐른다.
            Text("\(count)")
                .font(.minSans(14))
                .foregroundStyle(isLiked ? accent.opacity(0.9) : Theme.textSecondary)
                .contentTransition(.numericText())
                .animation(.easeOut(duration: 0.22), value: count)
        }
    }

    private func fire() {
        burstNonce += 1
        burstProgress = 0
        pop = 0.72
        withAnimation(.spring(response: 0.34, dampingFraction: 0.42)) { pop = 1 }
        withAnimation(.linear(duration: Self.burstDuration)) { burstProgress = 1 }
    }
}

/// 파편 개수 — Android LikeButton.SHARD_COUNT 와 동일 값.
private let shardCount = 12

/// 퍼지는 파편 + 링. Canvas 는 장식 전용(터치 통과).
private struct BurstShards: View {
    let nonce: Int
    let progress: Double
    let accent: Color

    var body: some View {
        Canvas { ctx, size in
            let p = progress
            let fade = max(0, 1 - p)
            let r = min(size.width, size.height) / 2
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            // easeOutCubic: 처음엔 빠르게 튀어나가고 끝에서 잦아든다.
            let ease = 1 - pow(1 - p, 3)

            // 0 이 되면 xorshift 가 영원히 0 → 최소 1 보장.
            var seed = UInt64(truncatingIfNeeded: nonce &* 7919) | 1
            func rnd() -> Double {
                // xorshift — Android Random(nonce*7919) 과 같은 "고정 시드" 취지.
                seed ^= seed << 13; seed ^= seed >> 7; seed ^= seed << 17
                return Double(seed % 1000) / 1000.0
            }

            for i in 0..<shardCount {
                let base = Double(i) / Double(shardCount) * 360
                let angle = base + rnd() * 22 - 11
                let distance = 0.62 + rnd() * 0.55
                let sizeMul = 0.5 + rnd() * 0.7
                let spin = rnd() * 220 - 110

                let dist = Double(r) * distance * ease
                let rad = angle * .pi / 180
                // ⚠️ 계산은 Double 로 하고 좌표를 만들 때만 CGFloat 로 — 혼합하면 컴파일 에러(02 문서 규칙).
                let cx = CGFloat(Double(center.x) + cos(rad) * dist)
                let cy = CGFloat(Double(center.y) + sin(rad) * dist)
                let side = CGFloat((5.2 * sizeMul) * (1 - 0.45 * p))

                // 마름모 파편 — 크리스탈 별 파편과 같은 언어.
                var path = Path()
                path.move(to: CGPoint(x: cx, y: cy - side))
                path.addLine(to: CGPoint(x: cx + side * 0.62, y: cy))
                path.addLine(to: CGPoint(x: cx, y: cy + side))
                path.addLine(to: CGPoint(x: cx - side * 0.62, y: cy))
                path.closeSubpath()

                let rotated = path.applying(
                    CGAffineTransform(translationX: cx, y: cy)
                        .rotated(by: CGFloat((angle + spin * p) * .pi / 180))
                        .translatedBy(x: -cx, y: -cy)
                )
                ctx.fill(rotated, with: .color(accent.opacity(0.85 * fade)))
            }

            // 퍼지는 링 — 파편보다 빨리 사라진다.
            let ringP = min(1, p / 0.55)
            if ringP < 1 {
                let radius = CGFloat(Double(r) * (0.25 + 0.8 * ringP))
                let rect = CGRect(x: center.x - radius, y: center.y - radius,
                                  width: radius * 2, height: radius * 2)
                ctx.stroke(Path(ellipseIn: rect),
                           with: .color(accent.opacity(0.30 * (1 - ringP))),
                           lineWidth: CGFloat(2 * (1 - ringP)))
            }
        }
    }
}
