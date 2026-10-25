import SwiftUI

/// 불러오는 동안 자리 표시 — 내용 모양의 흐린 틀 위로 빛이 1.7초마다 지나간다. (Android `Skeleton.kt` 패리티)
/// 빛은 틀 묶음 전체를 한 번에 가로지른다(`shimmering`) — 틀마다 타이머를 돌리지 않아 가볍다.
private let skeletonBase = Color(hex: 0x9FB3E8, alpha: 0.07)
private let skeletonLight = Color(hex: 0x9FB3E8, alpha: 0.20)

private struct Shimmer: ViewModifier {
    @State private var move = false

    func body(content: Content) -> some View {
        content
            .overlay {
                GeometryReader { g in
                    LinearGradient(colors: [.clear, skeletonLight, .clear], startPoint: .leading, endPoint: .trailing)
                        .frame(width: g.size.width * 0.9)
                        .offset(x: move ? g.size.width : -g.size.width * 0.9)
                }
                .mask { content }
                .allowsHitTesting(false)
            }
            .onAppear {
                withAnimation(.linear(duration: 1.7).repeatForever(autoreverses: false)) { move = true }
            }
    }
}

extension View {
    func shimmering() -> some View { modifier(Shimmer()) }
}

/// 목록 행 모양 틀 — (동그라미) + 두 줄 + 오른쪽 짧은 칸. 내 다이어리·알림·친구 목록.
struct SkeletonRows: View {
    var count = 6
    var avatar = true

    var body: some View {
        VStack(spacing: 0) {
            ForEach(0..<count, id: \.self) { _ in
                HStack(spacing: 12) {
                    if avatar {
                        Circle().fill(skeletonBase).frame(width: 40, height: 40)
                    }
                    GeometryReader { g in
                        VStack(alignment: .leading, spacing: 8) {
                            bar.frame(width: g.size.width * 0.72, height: 11)
                            bar.frame(width: g.size.width * 0.46, height: 9)
                        }
                        .frame(maxHeight: .infinity)
                    }
                    .frame(height: 40)
                    bar.frame(width: 36, height: 10)
                }
                .padding(.vertical, 10)
            }
        }
        .shimmering()
    }

    private var bar: some View { RoundedRectangle(cornerRadius: 6).fill(skeletonBase) }
}

/// 별 도감 고리 모양 틀.
struct SkeletonRing: View {
    var dots = 16

    var body: some View {
        GeometryReader { g in
            let radius: CGFloat = min(g.size.width, g.size.height) * 0.36
            ZStack {
                ForEach(0..<dots, id: \.self) { i in
                    let angle: Double = -Double.pi / 2 + 2 * Double.pi * Double(i) / Double(dots)
                    let x: CGFloat = radius * CGFloat(cos(angle))
                    let y: CGFloat = radius * CGFloat(sin(angle))
                    Circle().fill(skeletonBase).frame(width: 14, height: 14).offset(x: x, y: y)
                }
                RoundedRectangle(cornerRadius: 10).fill(skeletonBase).frame(width: 64, height: 40)
            }
            .frame(width: g.size.width, height: g.size.height)
            .shimmering()
        }
    }
}

/// 첫 응답 전엔 자리 표시, 응답이 끝내 안 오면 6초 뒤 빈 화면 안내로 넘어간다 — 자리 표시가 영원히 돌지 않게.
/// (Android `rememberStillLoading` 패리티)
struct LoadingGate<Placeholder: View, Empty: View>: View {
    let loading: Bool
    @ViewBuilder let placeholder: () -> Placeholder
    @ViewBuilder let empty: () -> Empty
    @State private var timedOut = false

    var body: some View {
        Group {
            if loading && !timedOut { placeholder() } else { empty() }
        }
        .task {
            try? await Task.sleep(nanoseconds: 6_000_000_000)
            timedOut = true
        }
    }
}
