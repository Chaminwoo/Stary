import SwiftUI

/// 지도 위 땅 빛 · 비네트(2026-10-28 "하나의 우주" 지도 시안 ground.js / world.js 를 수치 그대로 옮김).
///  - 땅 빛: (1) 먼 곳 보랏빛 반사(프레넬) (2) 화면을 비스듬히 가로지르는 넓은 빛띠 두 겹(아주 느리게 흐른다) (3) 한 곳에 모인 부드러운 반사광.
///  - 비네트: 아래쪽 어둠 + 가장자리 둥근 어둠.
/// 2026-10-28 피드백: 하늘 띠 · 위쪽 안개 삭제. 땅 빛은 필터처럼 줌과 무관하게 같은 세기로 유지하고,
/// 비네트만 줌을 빼면(11-) 예전 비네트로 `nearFactor` 를 따라 부드럽게 섞는다(스타일 JSON 의 전환 구간과 같다).
/// ⚠️ 지도(MapLibre)는 별도 표면이라 더하기 합성이 지도와 섞이지 않는다 → 시안의 "더하기 빛 C" 는
///    C 를 가장 밝은 채널로 나눈 색을 불투명도 max(C) 로 덮어 근사한다(어두운 땅에서 ≈ +C).
/// Android `MapSky.kt` 와 같은 수치.
struct MapSkyOverlay: View {
    @ObservedObject private var cam = MapSkyCamera.shared

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.05)) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 100_000)
            Canvas { ctx, size in
                let zoom = cam.zoom
                let near = MapSkyDraw.nearFactor(zoom)
                // 저줌 글로브 전환 구간(줌 4.4→2.4)엔 0 으로 빠진다(Android `1 - hazeAlpha`).
                let haze = 1 - min(max((4.4 - zoom) / 2.0, 0), 1)
                MapSkyDraw.groundLight(&ctx, size, k: haze, t: t)
                MapSkyDraw.vignette(&ctx, size, near: near)
            }
        }
        .allowsHitTesting(false)
    }
}

/// 하늘 오버레이가 보는 카메라 줌 — 지도 코디네이터가 0.05 단위로 바뀔 때만 알린다(이 뷰만 다시 그린다, MapScreen 은 그대로).
final class MapSkyCamera: ObservableObject {
    static let shared = MapSkyCamera()
    @Published private(set) var zoom: Double = 15

    func report(_ z: Double) {
        let q = (z * 20).rounded() / 20
        guard q != zoom else { return }
        zoom = q
    }
}

private enum MapSkyDraw {
    static let baseTiltDeg = Double(MapLibreView.baseTiltDeg)

    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = min(max((x - a) / (b - a), 0), 1)
        return t * t * (3 - 2 * t)
    }

    static func fract(_ x: Double) -> Double { x - x.rounded(.down) }

    /// 시안 카메라(거리 1.5H, 기울기 40°)에서 화면 y 비율(0=위) → 바닥 깊이(화면 가운데 = 1).
    static func depthAt(_ yFrac: Double) -> Double {
        let s = sin(baseTiltDeg * .pi / 180)
        let c = cos(baseTiltDeg * .pi / 180)
        let u = (0.5 - yFrac) / 1.5
        let den = c - u * s
        return den > 0.02 ? 1 + u * s / den : 4
    }

    /// 예전 지도 → 새(시안) 지도 전환 줌 구간(Android MAP_NEAR_FROM/TO · 스타일 JSON 과 같은 값).
    static let nearFrom = 11.0
    static let nearTo = 13.0

    /// 0(줌 11 이하, 예전 지도) .. 1(줌 13 이상, 새 지도).
    static func nearFactor(_ zoom: Double) -> Double { smooth(nearFrom, nearTo, zoom) }

    /// "더하기 빛" 근사 — 더할 양 (r, g, b) → 정규화 색 + 가장 밝은 채널 값의 불투명도.
    static func addLight(_ r: Double, _ g: Double, _ b: Double, _ k: Double) -> Color {
        let m = max(r, g, b)
        guard m > 1e-4, k > 0 else { return .clear }
        return Color(red: r / m, green: g / m, blue: b / m).opacity(min(max(m * k, 0), 1))
    }

    static func rgb(_ r: Double, _ g: Double, _ b: Double) -> Color {
        Color(red: r / 255, green: g / 255, blue: b / 255)
    }

    // MARK: 땅 빛

    /// 땅 빛 — 줌에 따라 나타났다 사라지면 어색해서(2026-10-28) 필터처럼 줌과 무관하게 시안 줌 15 의 세기로 유지한다.
    static func groundLight(_ ctx: inout GraphicsContext, _ size: CGSize, k: Double, t: Double) {
        guard k > 0.01 else { return }
        let w = size.width, h = size.height
        let full = Path(CGRect(origin: .zero, size: size))
        let near = 1.0 // 필터처럼 줌과 무관(시안 줌 15 의 값)

        // (1) 먼 곳일수록 하늘빛(보랏빛)을 비춘다 — col += (0.07, 0.062, 0.19) · gl0² · 0.5
        let fres: [Gradient.Stop] = (0...8).map { i in
            let y = Double(i) / 8
            let gl0 = min(max((depthAt(y) - 0.55) * 1.15, 0), 1)
            return .init(color: addLight(0.07, 0.062, 0.19, gl0 * gl0 * 0.5 * k), location: y)
        }
        ctx.fill(full, with: .linearGradient(Gradient(stops: fres), startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))

        // (2) 넓은 빛줄기 두 겹 — ph = x/W·0.78 + y/H·0.46 을 따라 가우스 띠가 아주 느리게 흐른다
        let fac = 0.3 + 0.7 * 0.52 + 1.1 * near
        let phMax = 1.24
        let bands: [Gradient.Stop] = (0...48).map { i in
            let s = Double(i) / 48
            let ph = s * phMax
            let b1 = exp(-pow((fract(ph * 0.55 - t * 0.011) - 0.5) * 5.2, 2))
            let b2 = exp(-pow((fract(ph * 0.9 + 0.37 - t * 0.017) - 0.5) * 9.0, 2))
            let a = (b1 * 0.55 + b2 * 0.3) * fac
            return .init(color: addLight(0.075, 0.085, 0.17, a * k), location: s)
        }
        let gx = 0.78 / Double(w), gy = 0.46 / Double(h)
        let g2 = gx * gx + gy * gy
        ctx.fill(full, with: .linearGradient(
            Gradient(stops: bands), startPoint: .zero,
            endPoint: CGPoint(x: gx * phMax / g2, y: gy * phMax / g2)))

        // (3) 한 곳에 모인 부드러운 반사광 — (0.62W, 0.58H), exp(-(dx² + (1.45dy)²)·7)
        let hot = (1 + 1.4 * near) * k
        let gauss: [Gradient.Stop] = (0...12).map { i in
            let r = Double(i) / 12
            return .init(color: addLight(0.045, 0.045, 0.10, exp(-r * r * 7) * hot), location: r)
        }
        let c = CGPoint(x: w * 0.62, y: h * 0.58)
        var hc = ctx
        hc.translateBy(x: c.x, y: c.y)
        hc.scaleBy(x: 1, y: (h / 1.45) / w)
        hc.fill(Path(ellipseIn: CGRect(x: -w, y: -w, width: w * 2, height: w * 2)),
                with: .radialGradient(Gradient(stops: gauss), center: .zero, startRadius: 0, endRadius: w))
    }

    // MARK: 비네트

    /// [near] 0 이면 예전 비네트(가장자리 둥근 어둠 0.30), 1 이면 시안 비네트(아래쪽 190px 어둠 + 가장자리 어둠), 사이는 섞는다.
    static func vignette(_ ctx: inout GraphicsContext, _ size: CGSize, near: Double) {
        let w = size.width, h = size.height
        let full = Path(CGRect(origin: .zero, size: size))
        let old = 1 - near
        if old > 0.003 {
            ctx.fill(full, with: .radialGradient(
                Gradient(stops: [
                    .init(color: .clear, location: 0),
                    .init(color: .clear, location: 0.62),
                    .init(color: Color.black.opacity(0.30 * old), location: 1),
                ]), center: CGPoint(x: w / 2, y: h / 2), startRadius: 0, endRadius: hypot(w, h) / 2))
        }
        guard near > 0.003 else { return }
        let bh = h * 190 / 844
        let dark = rgb(3, 4, 12)
        ctx.fill(Path(CGRect(x: 0, y: h - bh, width: w, height: bh)), with: .linearGradient(
            Gradient(colors: [dark.opacity(0), dark.opacity(0.55 * near)]),
            startPoint: CGPoint(x: 0, y: h - bh), endPoint: CGPoint(x: 0, y: h)))
        let edge = rgb(2, 3, 10)
        ctx.fill(full, with: .radialGradient(
            Gradient(stops: [
                .init(color: edge.opacity(0), location: 0),
                .init(color: edge.opacity(0), location: 0.32 / 0.78),
                .init(color: edge.opacity(0.5 * near), location: 1),
            ]), center: CGPoint(x: w / 2, y: h * 0.55), startRadius: 0, endRadius: h * 0.78))
    }
}
