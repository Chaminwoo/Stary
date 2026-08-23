import SwiftUI
import UIKit

/// 다이어리 진입 직전 파장 연출 데이터 — Android `DiaryOpenWarpData` 대응.
struct DiaryOpenWarpData: Identifiable {
    let id = UUID()
    /// 탭 순간의 지도 스냅샷(캡처 실패 시 nil — 링/버스트만 재생).
    let snapshot: UIImage?
    /// 파장 시작 위치(화면 비율 0..1).
    let origin: CGPoint
    /// 탭한 별의 머지 멤버 전체(1개면 상세, 2개 이상이면 겹친 별 카드).
    let members: [Diary]
    /// 파장 색 강제 지정(멤버가 없는 업로드 버튼 파장 등) — nil 이면 대표 별색.
    var colorOverride: Int? = nil
    let startedAt = Date()

    var colorIndex: Int { colorOverride ?? members.first?.starColor ?? 0 }
    /// 합쳐진 별 열람 시 파장 중심에서 퍼지는 멤버 별 파티클(최대 12개 — Android 동일).
    var burstStars: [(type: Int, colorIndex: Int)] {
        members.count > 1 ? members.prefix(12).map { ($0.starType, $0.starColor) } : []
    }
}

/// 연출 전체 길이(s) — Android 1300ms 동일.
private let warpDuration: Double = 0.6

/// 다이어리 진입 파장 — 지도 스냅샷을 별 위치에서 퍼지는 물결로 굴절시키고
/// 파장 링(후광+굴절 띠+가장자리 선)과 합쳐진 별 버스트 파티클을 얹는다.
/// (Android `DiaryOpenWarp`(drawBitmapMesh) 대응 — iOS 는 CIBumpDistortion 으로 근사.)
struct DiaryOpenWarpView: View {
    let data: DiaryOpenWarpData
    let onFinished: () -> Void

    var body: some View {
        TimelineView(.animation) { tl in
            let p = CGFloat(min(max(tl.date.timeIntervalSince(data.startedAt) / warpDuration, 0), 1))
            GeometryReader { geo in
                let size = geo.size
                let cx = size.width * data.origin.x
                let cy = size.height * data.origin.y
                let maxR = maxRadius(size: size, cx: cx, cy: cy)
                let front = p * maxR

                // 파장 링 + 합쳐진 별 버스트만 그린다(투명 오버레이 아래로 실제 지도가 그대로 비친다).
                // 지도 스냅샷 굴절(Android drawBitmapMesh)은 Metal 툴체인 의존 때문에 iOS 에선 생략.
                Canvas { ctx, sz in
                    drawEffects(ctx: ctx, size: sz, p: p, cx: cx, cy: cy, front: front)
                }
                .frame(width: size.width, height: size.height)
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .task(id: data.id) {
            try? await Task.sleep(nanoseconds: UInt64(warpDuration * 1_000_000_000) + 50_000_000)
            onFinished()
        }
    }

    private func maxRadius(size: CGSize, cx: CGFloat, cy: CGFloat) -> CGFloat {
        let a = hypot(cx, cy)
        let b = hypot(size.width - cx, cy)
        let c = hypot(cx, size.height - cy)
        let d = hypot(size.width - cx, size.height - cy)
        return max(max(a, b), max(c, d))
    }

    /// 파장 링(후광 + 굴절 띠 + 가장자리 선) + 버스트 별 — Android DiaryOpenWarp 상수 동일.
    private func drawEffects(ctx: GraphicsContext, size: CGSize, p: CGFloat,
                             cx: CGFloat, cy: CGFloat, front: CGFloat) {
        let rippleColor = StarStyle.color(data.colorIndex)
        let center = CGPoint(x: cx, y: cy)

        // 합쳐진 별 파티클 — 황금비 시퀀스로 결정론적으로 흩어진다(Android burstStars 동일).
        if !data.burstStars.isEmpty, p > 0.02 {
            let n = data.burstStars.count
            for (i, star) in data.burstStars.enumerated() {
                let golden = (CGFloat(i) * 0.61803398).truncatingRemainder(dividingBy: 1)
                let ang = CGFloat(i) / CGFloat(n) * 2 * .pi + golden * 0.9
                let dist = (70 + golden * 90) * p
                let x = cx + cos(ang) * dist
                let y = cy + sin(ang) * dist
                let sizePx = (12 + golden * 8) * (1 - 0.35 * p)
                let alpha = min(max((1 - p) * 1.4, 0), 1)
                guard alpha > 0.01, sizePx > 1 else { continue }
                var starCtx = ctx
                starCtx.opacity = alpha
                let img = StarCrystal.image(type: star.type, colorIndex: star.colorIndex, size: 20)
                starCtx.draw(Image(uiImage: img),
                             in: CGRect(x: x - sizePx / 2, y: y - sizePx / 2,
                                        width: sizePx, height: sizePx))
            }
        }

        // 파장 링 — 별 위치에서 퍼지는 빛 테두리(후광 + 굴절 띠 + 가장자리 선).
        if p < 1, front >= 1 {
            let fade = 1 - p
            // 후광(넓은 블러 링 근사 — 두꺼운 반투명 스트로크 2겹).
            ctx.stroke(
                Path(ellipseIn: CGRect(x: cx - front, y: cy - front, width: front * 2, height: front * 2)),
                with: .color(rippleColor.opacity(min(fade * 0.35, 1))),
                lineWidth: max(22 * fade, 3) * 2
            )
            // 굴절 띠 — 반경 방향 그라데이션 스트로크(Android radialGradient 스트로크 대응).
            let bandGrad = Gradient(stops: [
                .init(color: .clear, location: 0),
                .init(color: rippleColor.opacity(fade * 0.22), location: 0.5),
                .init(color: .clear, location: 1),
            ])
            ctx.stroke(
                Path(ellipseIn: CGRect(x: cx - front, y: cy - front, width: front * 2, height: front * 2)),
                with: .radialGradient(bandGrad, center: center,
                                      startRadius: max(front - 30, 0), endRadius: front + 30),
                lineWidth: max(30 * fade, 1)
            )
            // 가장자리 선.
            ctx.stroke(
                Path(ellipseIn: CGRect(x: cx - front, y: cy - front, width: front * 2, height: front * 2)),
                with: .color(rippleColor.opacity(min(fade * 0.7, 1))),
                lineWidth: max(3 * fade, 0.6)
            )
        }
    }
}
