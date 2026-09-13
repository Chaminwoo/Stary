import Foundation
import simd

/// 글로브 정점/행렬 순수 함수 모음 — **격리 없는 enum**.
///
/// `GlobeRenderer` 는 MTKViewDelegate 채택으로 SDK 에 따라 메인 액터 격리가 추론될 수 있다. 그러면 그 안의
/// static 함수·상수도 메인 액터 전용이 돼, 백그라운드(다이어리 스프라이트 빌드)에서 부르는 순간 컴파일 에러가 난다
/// (DailyReminderScheduler 에서 한 번 겪은 종류). 그래서 순수 계산은 전부 여기로 뺐다.
/// 값은 전부 Android `GlobeRenderer.kt` 와 동일.
enum GlobeGeometry {
    static let spriteFloats = 12
    /// 선분 1개 = 두께 사각형 6정점 — [a3, b3, color3, end(0|1), side(-1|1)].
    static let lineFloats = 11
    static let spriteCorners: [Float] = [-1, -1, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1]

    // 다이어리 스프라이트(Android companion 상수)
    static let flareMinLikes = 100
    static let flareMax = 500
    static let flareRadius: Float = 1.045
    static let glowRadius: Float = 1.008
    static let glowAlpha: Float = 0.42
    static let glowMax = 5000
    static let flareColors: [UInt32] = [
        0xFF6257, 0x6D9EFF, 0xFF8BD8, 0xFFD966, 0x8FF7E2, 0xC49BFF, 0xFFFFFF,
    ]

    /// 다이어리 → (플레어 스프라이트, 노란 점광 스프라이트) — Android setDiaries 와 같은 규칙.
    static func buildDiarySprites(_ diaries: [Diary]) -> (flare: [Float], glow: [Float]) {
        let valid = diaries.filter { $0.latitude != 0 && $0.longitude != 0 }
        let popular = valid.filter { $0.likeCount >= flareMinLikes }
            .sorted { $0.likeCount > $1.likeCount }
            .prefix(flareMax)
        var flares: [Float] = []
        flares.reserveCapacity(popular.count * 6 * spriteFloats)
        for d in popular {
            let p = latLngToXyz(d.latitude, d.longitude, flareRadius)
            let rgb = flareColors[flareColorIndex(d)]
            let boost = Float(min(d.likeCount, 1000)) / 1000
            let size: Float = 0.034 + 0.026 * boost
            let bright: Float = 0.60 + 0.15 * boost
            let r = Float((rgb >> 16) & 0xFF) / 255 * bright
            let g = Float((rgb >> 8) & 0xFF) / 255 * bright
            let b = Float(rgb & 0xFF) / 255 * bright
            let phase = Float(floorMod(d.latitude * 7 + d.longitude * 13, 1.0))
            addSprite(&flares, p, r, g, b, 1, size: size, phase: phase, mode: 0)
        }
        var glows: [Float] = []
        let rest = valid.filter { $0.likeCount < flareMinLikes }
        glows.reserveCapacity(min(rest.count, glowMax) * 6 * spriteFloats)
        for (i, d) in rest.enumerated() {
            if i >= glowMax { break }
            let p = latLngToXyz(d.latitude, d.longitude, glowRadius)
            let phase = Float(floorMod(d.latitude * 3 + d.longitude * 5, 1.0))
            addSprite(&glows, p, 1.0 * glowAlpha, 0.76 * glowAlpha, 0.36 * glowAlpha, 1,
                      size: 0.030, phase: phase, mode: 0)
        }
        return (flares, glows)
    }

    /// 좌표 기반 결정적 팔레트 인덱스(Android flareColorIndex — Kotlin Double.mod 는 floor 모듈러).
    static func flareColorIndex(_ d: Diary) -> Int {
        Int(floorMod(d.latitude * 7919.0 + d.longitude * 104729.0, Double(flareColors.count)))
    }

    /// 빌보드 사각형(삼각형 2개 = 6 정점) — [center3, corner2, color4, size, phase, mode].
    static func addSprite(_ out: inout [Float], _ p: SIMD3<Float>, _ r: Float, _ g: Float, _ b: Float,
                          _ a: Float, size: Float, phase: Float, mode: Float) {
        for c in 0..<6 {
            out.append(p.x); out.append(p.y); out.append(p.z)
            out.append(spriteCorners[c * 2]); out.append(spriteCorners[c * 2 + 1])
            out.append(r); out.append(g); out.append(b); out.append(a)
            out.append(size); out.append(phase); out.append(mode)
        }
    }

    static func addLineQuad(_ out: inout [Float], _ a: SIMD3<Float>, _ b: SIMD3<Float>, _ c: SIMD3<Float>) {
        let corners: [(Float, Float)] = [(0, -1), (0, 1), (1, 1), (0, -1), (1, 1), (1, -1)]
        for (end, side) in corners {
            out.append(a.x); out.append(a.y); out.append(a.z)
            out.append(b.x); out.append(b.y); out.append(b.z)
            out.append(c.x); out.append(c.y); out.append(c.z)
            out.append(end); out.append(side)
        }
    }

    /// 위경도 → 구 좌표(Android latLngToXyz — λ=0 이 +Z).
    static func latLngToXyz(_ lat: Double, _ lng: Double, _ radius: Float) -> SIMD3<Float> {
        let phi = lat * .pi / 180
        let lam = lng * .pi / 180
        let x = Float(cos(phi) * sin(lam)) * radius
        let y = Float(sin(phi)) * radius
        let z = Float(cos(phi) * cos(lam)) * radius
        return SIMD3<Float>(x, y, z)
    }

    /// Kotlin `mod` (결과가 항상 제수와 같은 부호 = floor 모듈러).
    static func floorMod(_ x: Double, _ m: Double) -> Double { x - m * floor(x / m) }
    static func floorModF(_ x: Float, _ m: Float) -> Float { x - m * floor(x / m) }

    /// 부분 원호 리본(TRIANGLE_STRIP) — [pos3, uv2]. Android buildArc.
    static func buildArc(radius: Float, halfWidth: Float, tiltX: Float, tiltZ: Float,
                         start: Float, sweep: Float) -> [Float] {
        let segs = 192
        let m = rotationZ(tiltZ) * rotationX(tiltX)
        var list: [Float] = []
        list.reserveCapacity((segs + 1) * 2 * 5)
        for s in 0...segs {
            let u = Float(s) / Float(segs)
            let ang = (start + u * sweep) * .pi / 180
            for k in 0...1 {
                let r = radius + (k == 0 ? -halfWidth : halfWidth)
                let p = m * SIMD4<Float>(cos(ang) * r, 0, sin(ang) * r, 1)
                list.append(p.x); list.append(p.y); list.append(p.z)
                list.append(u); list.append(Float(k))
            }
        }
        return list
    }

    // MARK: 행렬 (Android Matrix.rotateM / perspectiveM 과 같은 규약, 열 우선)

    static func rotationX(_ deg: Float) -> simd_float4x4 {
        let a = deg * .pi / 180
        let c = cos(a), s = sin(a)
        return simd_float4x4(columns: (SIMD4<Float>(1, 0, 0, 0), SIMD4<Float>(0, c, s, 0),
                                       SIMD4<Float>(0, -s, c, 0), SIMD4<Float>(0, 0, 0, 1)))
    }

    static func rotationY(_ deg: Float) -> simd_float4x4 {
        let a = deg * .pi / 180
        let c = cos(a), s = sin(a)
        return simd_float4x4(columns: (SIMD4<Float>(c, 0, -s, 0), SIMD4<Float>(0, 1, 0, 0),
                                       SIMD4<Float>(s, 0, c, 0), SIMD4<Float>(0, 0, 0, 1)))
    }

    static func rotationZ(_ deg: Float) -> simd_float4x4 {
        let a = deg * .pi / 180
        let c = cos(a), s = sin(a)
        return simd_float4x4(columns: (SIMD4<Float>(c, s, 0, 0), SIMD4<Float>(-s, c, 0, 0),
                                       SIMD4<Float>(0, 0, 1, 0), SIMD4<Float>(0, 0, 0, 1)))
    }

    static func translation(_ x: Float, _ y: Float, _ z: Float) -> simd_float4x4 {
        simd_float4x4(columns: (SIMD4<Float>(1, 0, 0, 0), SIMD4<Float>(0, 1, 0, 0),
                                SIMD4<Float>(0, 0, 1, 0), SIMD4<Float>(x, y, z, 1)))
    }

    /// 원근 투영 — GL perspectiveM 과 같은 시야각/종횡비, 깊이는 Metal NDC(0..1).
    static func perspective(fovyDeg: Float, aspect: Float, near: Float, far: Float) -> simd_float4x4 {
        let ys = 1 / tan(fovyDeg * .pi / 360)
        let xs = ys / aspect
        let zs = far / (near - far)
        return simd_float4x4(columns: (SIMD4<Float>(xs, 0, 0, 0), SIMD4<Float>(0, ys, 0, 0),
                                       SIMD4<Float>(0, 0, zs, -1), SIMD4<Float>(0, 0, zs * near, 0)))
    }
}
