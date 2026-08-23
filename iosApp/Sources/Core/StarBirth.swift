import SwiftUI
import UIKit

/// 별 탄생 연출(34-8) — 다이어리 저장이 **성공한 순간** 화면 중앙에서 별이 응축·발광하고
/// 스파클이 퍼진 뒤, 작아지며 지도(내 위치 = 화면 중앙)로 내려앉는다.
/// (Android `core/ui/StarBirth.kt` 패리티 — 단계 타이밍/상수 동일.)
///
/// 전역 오버레이인 이유: 저장 직후 지도 탭으로 전환되므로 업로드 화면 안에서 재생하면 연출이 잘린다.
/// [StarBirthStore.trigger] 만 호출하면 [StarBirthHost](MainTabView 최상단)가 지도 위에서 이어 재생한다.
/// 실패(업로드 에러)에는 트리거하지 않는다 — 성공 경로에서만 호출할 것.
@MainActor
final class StarBirthStore: ObservableObject {
    static let shared = StarBirthStore()
    private init() {}

    struct Event: Identifiable {
        let id = UUID()
        let type: Int
        let colorIndex: Int
        let startedAt = Date()
    }

    /// 재생 중인 별. nil = 연출 없음.
    @Published private(set) var event: Event?

    func trigger(starType: Int, starColor: Int) {
        event = Event(type: starType, colorIndex: starColor)
        Haptics.celebrate() // 별이 응축·발광하는 순간에 맞춘 축하 진동
    }

    func clear() { event = nil }
}

/// 연출 전체 길이(s) / 퍼지는 스파클 개수.
private let birthDuration: Double = 0.95
private let birthSparkles = 9

/// 별 탄생 오버레이 호스트 — 앱 최상단(MainTabView)에 1개만 둔다.
/// 장식 전용: 터치를 받지 않으므로 연출 중에도 지도 조작이 막히지 않는다.
struct StarBirthHost: View {
    @ObservedObject private var store = StarBirthStore.shared

    var body: some View {
        if let e = store.event {
            StarBirthOverlay(event: e)
                .allowsHitTesting(false)
                .task(id: e.id) {
                    try? await Task.sleep(nanoseconds: UInt64(birthDuration * 1_000_000_000) + 50_000_000)
                    store.clear()
                }
        }
    }
}

private struct StarBirthOverlay: View {
    let event: StarBirthStore.Event

    var body: some View {
        TimelineView(.animation) { tl in
            let p = CGFloat(min(max(tl.date.timeIntervalSince(event.startedAt) / birthDuration, 0), 1))
            Canvas { ctx, size in
                draw(ctx: ctx, size: size, p: p)
            }
        }
        .ignoresSafeArea()
    }

    private func draw(ctx: GraphicsContext, size: CGSize, p: CGFloat) {
        let w = size.width
        let h = size.height
        let accent = StarStyle.color(event.colorIndex)
        // 응축 시작점(화면 중앙보다 살짝 위) → 착지점(중앙 = 지도의 내 위치).
        let startY = h * 0.40
        let endY = h * 0.50
        let cx = w * 0.5

        // 1단계(0~0.45) 응축 → 2단계(0.45~0.62) 발광 링 → 3단계(0.62~1) 축소·소멸.
        let condense = min(max(p / 0.45, 0), 1)
        let settle = min(max((p - 0.62) / 0.38, 0), 1)

        let baseSize: CGFloat = 108
        let scale: CGFloat
        if p < 0.45 { scale = 2.4 - 1.4 * condense }            // 2.4 → 1.0
        else if p < 0.62 { scale = 1 + 0.10 * ((p - 0.45) / 0.17) } // 발광 순간 살짝 부푼다
        else { scale = 1.10 - 0.92 * settle }                    // 1.10 → 0.18
        let alpha: CGFloat
        if p < 0.45 { alpha = condense }
        else if p < 0.62 { alpha = 1 }
        else { alpha = 1 - settle }
        let cy = startY + (endY - startY) * settle
        let starSize = baseSize * scale

        // 후광 — 발광 순간 가장 밝다.
        let glowPeak = min(max(1 - abs(p - 0.53) / 0.53, 0), 1)
        ctx.fill(Path(ellipseIn: circleRect(cx, cy, starSize * 0.95)),
                 with: .color(accent.opacity(0.30 * alpha * glowPeak)))

        // 발광 링 — 별이 완성되는 순간 바깥으로 퍼진다.
        if p >= 0.42, p <= 0.85 {
            let ringT = min(max((p - 0.42) / 0.43, 0), 1)
            ctx.stroke(
                Path(ellipseIn: circleRect(cx, cy, starSize * (0.6 + 1.7 * ringT))),
                with: .color(accent.opacity(0.35 * (1 - ringT))),
                lineWidth: 3 * (1 - ringT) + 0.5
            )
        }

        // 스파클 — 별에서 방사형으로 흩어지며 사라진다.
        if p > 0.40 {
            let sp = min(max((p - 0.40) / 0.55, 0), 1)
            for i in 0..<birthSparkles {
                let a = CGFloat(i) / CGFloat(birthSparkles) * 6.28318 + 0.35
                let dist = starSize * (0.55 + 1.5 * sp)
                let px = cx + cos(a) * dist
                let py = cy + sin(a) * dist * 0.9
                let r = max(3.2 - 2.2 * sp, 0.5)
                ctx.fill(Path(ellipseIn: circleRect(px, py, r)),
                         with: .color(Color.white.opacity(0.55 * (1 - sp))))
            }
        }

        // 별 본체 — 앱의 모든 별과 같은 크리스탈 렌더(비트맵 1회 굽고 스케일만 변경).
        if alpha > 0.01, starSize > 1 {
            var starCtx = ctx
            starCtx.opacity = alpha
            let img = StarCrystal.image(type: event.type, colorIndex: event.colorIndex, size: baseSize)
            starCtx.draw(
                Image(uiImage: img),
                in: CGRect(x: cx - starSize / 2, y: cy - starSize / 2, width: starSize, height: starSize)
            )
        }
    }

    private func circleRect(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> CGRect {
        CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)
    }
}
