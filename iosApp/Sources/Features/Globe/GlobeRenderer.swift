import MetalKit
import QuartzCore
import simd
import UIKit

// 3D 행성(지구) 렌더러 — Android `feature/globe/GlobeRenderer.kt`(OpenGL ES 2.0)의 **Metal 1:1 포팅**.
//
// 예전 iOS 글로브(SceneKit, 2026-08-09 제거)는 별밭·은하수·12궁을 구면 텍스처에 구워 넣어 Android(카메라 정면
// 빌보드 스프라이트 수천 개)와 모양이 달랐다. 이 파일은 Android 와 **같은 셰이더 식 / 같은 정점 데이터 /
// 같은 그리기 순서·블렌딩·깊이 규칙 / 같은 시드의 난수열(java.util.Random 복제)** 로 그려 같은 하늘이 나온다.
// ⚠️ 상수·셰이더를 고칠 땐 Android GlobeRenderer.kt 와 반드시 함께(패리티 표 = docs/code/04-globe.md).
//
// 셰이더는 빌드 타임 .metal 대신 **런타임 컴파일**(`makeLibrary(source:)`) — CI 러너의 최신 Xcode 는 Metal 툴체인이
// 별도 다운로드 컴포넌트라 .metal 파일이 있으면 빌드가 막힐 수 있어서. 컴파일 실패 시 init 이 nil(검정 화면 + 힌트만).
//
// 좌표/수학: 카메라 고정(+Z, camDist), 모델 = Rx(pitch)·Ry(yaw). 텍스처 좌표는 GL(GLUtils 업로드: t=0 이 비트맵 윗줄)과
// Metal(y=0 이 윗줄)이 같은 방향이라 UV 를 그대로 쓴다. 색 공간은 GL 과 같게 **감마 공간 그대로**(.bgra8Unorm, sRGB 해석 없음).

/// `java.util.Random` 과 비트 단위로 같은 난수 생성기 — Android 와 같은 시드로 같은 별 배치/밝기를 만든다.
struct JavaRandom {
    private var seed: Int64
    private var nextNextGaussian: Double = 0
    private var haveNextNextGaussian = false
    private static let multiplier: Int64 = 0x5DEECE66D
    private static let mask: Int64 = (1 << 48) - 1

    init(_ seed: Int64) { self.seed = (seed ^ JavaRandom.multiplier) & JavaRandom.mask }

    private mutating func next(_ bits: Int) -> Int32 {
        seed = (seed &* JavaRandom.multiplier &+ 0xB) & JavaRandom.mask
        return Int32(truncatingIfNeeded: seed >> (48 - bits))
    }

    mutating func nextFloat() -> Float { Float(next(24)) / Float(1 << 24) }

    mutating func nextBoolean() -> Bool { next(1) != 0 }

    mutating func nextDouble() -> Double {
        let hi = Int64(next(26)) << 27
        let lo = Int64(next(27))
        return Double(hi + lo) * 0x1.0p-53
    }

    mutating func nextGaussian() -> Double {
        if haveNextNextGaussian {
            haveNextNextGaussian = false
            return nextNextGaussian
        }
        var v1 = 0.0, v2 = 0.0, s = 0.0
        repeat {
            v1 = 2 * nextDouble() - 1
            v2 = 2 * nextDouble() - 1
            s = v1 * v1 + v2 * v2
        } while s >= 1 || s == 0
        let mul = (-2 * log(s) / s).squareRoot()
        nextNextGaussian = v2 * mul
        haveNextNextGaussian = true
        return v1 * mul
    }
}

final class GlobeRenderer: NSObject, MTKViewDelegate {

    // MARK: 상수 (Android companion object 와 같은 값)

    static let enterDist: Float = 10.4   // 진입 시작 거리(돌리-인 출발점)
    static let idleDist: Float = 9.5     // 진입 정착 거리 = 최소 줌
    static let minDist: Float = 1.45     // 카메라 최소 거리
    static let maxDist: Float = 9.5      // 카메라 최대 거리
    private static let nearPlane: Float = 0.3
    private static let trailCount = 5
    private static let sunDist: Float = 45
    private static let cloudDrift: Float = 0.0035

    private static let meteorSprites = 34
    private static let meteorRollInterval: Float = 30
    private static let meteorSpawnChance: Float = 0.25
    private static let meteorTailFrac: Float = 0.30
    private static let sparkRate: Float = 60
    private static let sparkMax = 340
    private static let sparkLifeMin: Float = 5.0
    private static let sparkLifeVar: Float = 5.0

    private static let meteorTints: [SIMD3<Float>] = [
        SIMD3(1.00, 1.00, 1.00), SIMD3(1.00, 0.60, 0.32), SIMD3(0.55, 1.00, 0.62),
        SIMD3(1.00, 0.45, 0.85), SIMD3(1.00, 0.86, 0.32), SIMD3(0.62, 0.58, 1.00),
    ]
    private static let trailColors: [SIMD3<Float>] = [
        SIMD3(0.55, 0.75, 1.00), SIMD3(1.00, 0.62, 0.42), SIMD3(0.72, 0.55, 1.00),
        SIMD3(0.45, 1.00, 0.80), SIMD3(1.00, 0.80, 0.45),
    ]

    // MARK: 카메라/인터랙션 상태 (메인 스레드 — 제스처와 MTKView draw 모두 메인)

    var yawDeg: Float = 0
    var pitchDeg: Float = 15
    var camDist: Float = GlobeRenderer.enterDist
    var yawVelDeg: Float = 0
    var pitchVelDeg: Float = 0
    /// 마지막 터치 시각(CACurrentMediaTime). 0 = 아직 없음 → 자동 회전 즉시 시작(Android 동일).
    var lastInteraction: CFTimeInterval = 0
    private var dollyTarget: Float = GlobeRenderer.idleDist

    /// 지금 화면 정면에 보이는 지점 (위도, 경도) — 지도 복귀 좌표.
    func facingLatLng() -> (Double, Double) {
        let lat = min(max(Double(pitchDeg), -85), 85)
        var lng = Double(-yawDeg).truncatingRemainder(dividingBy: 360)
        if lng > 180 { lng -= 360 }
        if lng < -180 { lng += 360 }
        return (lat, lng)
    }

    func setInitialFacing(lat: Double, lng: Double) {
        pitchDeg = Float(min(max(lat, -75), 75))
        yawDeg = Float(-lng)
    }

    // MARK: Metal 핸들

    private let device: MTLDevice
    private let queue: MTLCommandQueue
    private let spritePipeline: MTLRenderPipelineState
    private let linePipeline: MTLRenderPipelineState
    private let earthPipeline: MTLRenderPipelineState
    private let cloudPipeline: MTLRenderPipelineState
    private let ringPipeline: MTLRenderPipelineState
    private let depthOff: MTLDepthStencilState
    private let depthTestWrite: MTLDepthStencilState
    private let depthTestOnly: MTLDepthStencilState
    private let samplerRepeat: MTLSamplerState
    private let samplerClamp: MTLSamplerState

    private var earthTex: MTLTexture?
    private var cloudTex: MTLTexture?
    private var flareTex: MTLTexture?
    private var glowTex: MTLTexture?
    private var sunTex: MTLTexture?

    private var earthVB: MTLBuffer?
    private var earthIB: MTLBuffer?
    private var earthIndexCount = 0
    private var starfieldVB: MTLBuffer?
    private var starfieldCount = 0
    private var constLineVB: MTLBuffer?
    private var constLineCount = 0
    private var bgFlareVB: MTLBuffer?
    private var bgFlareCount = 0
    private var sunVB: MTLBuffer?
    private var sunBuiltFrac: Float = -1

    private struct Trail {
        let buffer: MTLBuffer
        let count: Int
        let colorA: SIMD3<Float>
        let colorB: SIMD3<Float>
        let speed: Float
        let phase: Float
        let intensity: Float
    }
    private var trails: [Trail] = []

    /// setDiaries 결과 — 버퍼와 정점 수를 한 묶음으로(Android StarBatch 와 같은 이유).
    private final class StarBatch {
        let flare: [Float]
        let glow: [Float]
        init(flare: [Float], glow: [Float]) { self.flare = flare; self.glow = glow }
    }
    private var pendingStars: StarBatch?
    private var uploadedStars: StarBatch?
    private var flareVB: MTLBuffer?
    private var flareCount = 0
    private var glowVB: MTLBuffer?
    private var glowCount = 0

    // MARK: 유성

    private var meteorVB: MTLBuffer?
    private var meteorData: [Float] = []
    private var meteorStartT: Float = -1
    private var meteorDur: Float = 1.1
    private var nextRollT: Float = 0
    private var meteorStreak = 0
    private var meteorTintIdx = 0
    private var meteorP0 = SIMD3<Float>(0, 0, 0)
    private var meteorDir = SIMD3<Float>(0, 0, 1)
    private var meteorPerp = SIMD3<Float>(0, 1, 0)
    private var meteorBend: Float = 0
    private var meteorLen: Float = 8
    /// 잔류 파장 파편 — [x, y, z, vx, vy, vz, r, g, b, size, birthT, life, waveArg].
    private var meteorSparks: [[Float]] = []
    private var sparkEmitCarry: Float = 0
    private var lastMeteorU: Float = 0

    // MARK: 프레임 상태

    private var fade: Float = 0
    private var lastFrame: CFTimeInterval = 0
    private let startTime = CACurrentMediaTime()
    private var screenAspect: Float = 0.55
    private var viewportSize = SIMD2<Float>(1, 1)
    private var lineWidthPx: Float = 2

    // MARK: 초기화

    /// Metal 장치/셰이더/자산 준비. 실패하면 nil(호출부는 검정 배경만 둔다).
    init?(view: MTKView) {
        guard let device = view.device ?? MTLCreateSystemDefaultDevice(),
              let queue = device.makeCommandQueue()
        else { return nil }
        self.device = device
        self.queue = queue
        view.device = device

        let library: MTLLibrary
        do {
            library = try device.makeLibrary(source: GlobeRenderer.shaderSource, options: nil)
        } catch {
            print("⚠️ Globe shader compile failed: \(error)")
            return nil
        }

        func pipeline(_ vs: String, _ fs: String, _ blend: Int) -> MTLRenderPipelineState? {
            let d = MTLRenderPipelineDescriptor()
            d.vertexFunction = library.makeFunction(name: vs)
            d.fragmentFunction = library.makeFunction(name: fs)
            d.colorAttachments[0].pixelFormat = view.colorPixelFormat
            d.depthAttachmentPixelFormat = view.depthStencilPixelFormat
            let ca = d.colorAttachments[0]!
            if blend == 1 { // additive — GL_ONE, GL_ONE
                ca.isBlendingEnabled = true
                ca.rgbBlendOperation = .add
                ca.alphaBlendOperation = .add
                ca.sourceRGBBlendFactor = .one
                ca.destinationRGBBlendFactor = .one
                ca.sourceAlphaBlendFactor = .one
                ca.destinationAlphaBlendFactor = .one
            } else if blend == 2 { // alpha — GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA
                ca.isBlendingEnabled = true
                ca.rgbBlendOperation = .add
                ca.alphaBlendOperation = .add
                ca.sourceRGBBlendFactor = .sourceAlpha
                ca.destinationRGBBlendFactor = .oneMinusSourceAlpha
                ca.sourceAlphaBlendFactor = .sourceAlpha
                ca.destinationAlphaBlendFactor = .oneMinusSourceAlpha
            }
            do {
                return try device.makeRenderPipelineState(descriptor: d)
            } catch {
                print("⚠️ Globe pipeline \(vs)/\(fs) failed: \(error)")
                return nil
            }
        }
        guard let sprite = pipeline("spriteVertex", "spriteFragment", 1),
              let line = pipeline("lineVertex", "lineFragment", 1),
              let earth = pipeline("earthVertex", "earthFragment", 0),
              let cloud = pipeline("earthVertex", "cloudFragment", 2),
              let ring = pipeline("ringVertex", "ringFragment", 1)
        else { return nil }
        spritePipeline = sprite
        linePipeline = line
        earthPipeline = earth
        cloudPipeline = cloud
        ringPipeline = ring

        func depth(_ test: Bool, _ write: Bool) -> MTLDepthStencilState? {
            let d = MTLDepthStencilDescriptor()
            d.depthCompareFunction = test ? .less : .always
            d.isDepthWriteEnabled = write
            return device.makeDepthStencilState(descriptor: d)
        }
        guard let off = depth(false, false), let tw = depth(true, true), let to = depth(true, false) else { return nil }
        depthOff = off
        depthTestWrite = tw
        depthTestOnly = to

        func sampler(repeatS: Bool) -> MTLSamplerState? {
            let d = MTLSamplerDescriptor()
            d.minFilter = .linear
            d.magFilter = .linear
            d.mipFilter = .linear
            d.sAddressMode = repeatS ? .repeat : .clampToEdge
            d.tAddressMode = .clampToEdge
            return device.makeSamplerState(descriptor: d)
        }
        guard let sr = sampler(repeatS: true), let sc = sampler(repeatS: false) else { return nil }
        samplerRepeat = sr
        samplerClamp = sc

        super.init()

        buildEarthMesh()
        earthTex = loadBundleTexture("earth_blue_marble")
        cloudTex = loadBundleTexture("earth_clouds")
        flareTex = makeGeneratedTexture(size: 128, draw: GlobeRenderer.drawFlareTexture)
        glowTex = makeGeneratedTexture(size: 64, draw: GlobeRenderer.drawGlowTexture)
        sunTex = makeGeneratedTexture(size: 256, draw: GlobeRenderer.drawSunTexture)
        buildStarfield()
        buildTrails()
        // 글로브 입장 30초 뒤 첫 확률 판정(Android onSurfaceCreated 동일).
        nextRollT = GlobeRenderer.meteorRollInterval
    }

    // MARK: 다이어리

    /// 다이어리 → 플레어(좋아요 100+)/노란 점광 스프라이트. 무거운 빌드는 백그라운드, 결과만 메인에서 교체.
    func setDiaries(_ diaries: [Diary]) {
        // ⚠️ 중첩 클로저가 바깥 [weak self] 캡처 변수를 읽으면 동시성 검사에서 에러가 날 수 있어,
        //    적용 클로저를 먼저 만들고(여기서 weak 캡처) 백그라운드 → 메인으로 넘긴다.
        //    빌더는 격리 없는 enum(GlobeGeometry) 이라 백그라운드에서 불러도 액터 격리 에러가 없다.
        let apply: (StarBatch) -> Void = { [weak self] batch in self?.pendingStars = batch }
        DispatchQueue.global(qos: .userInitiated).async {
            let built = GlobeGeometry.buildDiarySprites(diaries)
            let batch = StarBatch(flare: built.flare, glow: built.glow)
            DispatchQueue.main.async { apply(batch) }
        }
    }

    private func uploadStars() {
        uploadedStars = pendingStars
        guard let batch = pendingStars else {
            flareCount = 0
            glowCount = 0
            return
        }
        flareVB = makeBuffer(batch.flare)
        flareCount = batch.flare.count / GlobeGeometry.spriteFloats
        glowVB = makeBuffer(batch.glow)
        glowCount = batch.glow.count / GlobeGeometry.spriteFloats
    }

    // MARK: MTKViewDelegate

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
        let w = Float(max(size.width, 1))
        let h = Float(max(size.height, 1))
        viewportSize = SIMD2(w, h)
        screenAspect = w / h
        // Android glLineWidth(2px, 밀도 ≈2.6x) ≈ 0.77pt — 기기 배율로 환산한 같은 물리 두께.
        lineWidthPx = 0.77 * Float(view.contentScaleFactor)
    }

    func draw(in view: MTKView) {
        let now = CACurrentMediaTime()
        let dt: Float = lastFrame == 0 ? 0.016 : Float(min(max(now - lastFrame, 0.001), 0.05))
        lastFrame = now
        let t = Float(now - startTime)
        if viewportSize.x <= 1 { mtkView(view, drawableSizeWillChange: view.drawableSize) }

        stepSimulation(dt: dt, now: now)
        if pendingStars !== uploadedStars { uploadStars() }

        guard let pass = view.currentRenderPassDescriptor,
              let drawable = view.currentDrawable,
              let cmd = queue.makeCommandBuffer(),
              let enc = cmd.makeRenderCommandEncoder(descriptor: pass)
        else { return }
        enc.setCullMode(.none)

        let proj = GlobeGeometry.perspective(fovyDeg: 42, aspect: screenAspect, near: GlobeRenderer.nearPlane, far: 100)
        let viewM = GlobeGeometry.translation(0, 0, -camDist)
        let vp = proj * viewM
        let model = GlobeGeometry.rotationX(pitchDeg) * GlobeGeometry.rotationY(yawDeg)
        let mvp = vp * model
        let sun = sunDirection()

        // 1) 배경 별밭 → 1.5) 별자리 선 + 반짝별 → 1.7) 유성 → 1.8) 태양 (전부 깊이 무시, additive)
        drawSprites(enc, starfieldVB, starfieldCount, glowTex, vp: vp, model: model, t: t)
        drawConstellationLines(enc, mvp: mvp)
        drawSprites(enc, bgFlareVB, bgFlareCount, flareTex, vp: vp, model: model, t: t)
        drawMeteor(enc, vp: vp, t: t, dt: dt)
        drawSun(enc, vp: vp, model: model, t: t, sun: sun)

        // 2) 지구(불투명, 깊이 기록) → 2.5) 구름
        drawEarth(enc, mvp: mvp, sun: sun)
        drawClouds(enc, mvp: mvp, sun: sun, t: t)

        // 3) 트레일(깊이 테스트만 — 행성 뒤로 가려짐)
        for tr in trails { drawTrail(enc, tr, mvp: mvp, t: t) }

        // 4) 노란 불빛 → 5) 플레어 — 깊이 테스트 없이(지평선 컷은 셰이더 vis). Android 주석의 두 가지 깨짐 이유 동일.
        drawSprites(enc, glowVB, glowCount, glowTex, vp: vp, model: model, t: t)
        drawSprites(enc, flareVB, flareCount, flareTex, vp: vp, model: model, t: t)

        enc.endEncoding()
        cmd.present(drawable)
        cmd.commit()
    }

    /// 관성/자동회전/돌리인/페이드 진행(Android stepSimulation).
    private func stepSimulation(dt: Float, now: CFTimeInterval) {
        fade = min(1, fade + dt / 1.1)
        let sinceTouch = now - lastInteraction
        if sinceTouch > 0.25 {
            camDist += (dollyTarget - camDist) * min(1, dt * 2.0)
        } else {
            dollyTarget = camDist
        }
        if sinceTouch > 0.06 {
            yawDeg += yawVelDeg * dt
            pitchDeg = min(max(pitchDeg + pitchVelDeg * dt, -75), 75)
            let decay = exp(-2.6 * dt)
            yawVelDeg *= decay
            pitchVelDeg *= decay
        }
        if sinceTouch > 3 { yawDeg += dt * 1.7 }
    }

    // MARK: 그리기

    private func setBytes(_ enc: MTLRenderCommandEncoder, vertex values: [Float], index: Int) {
        values.withUnsafeBytes { raw in
            if let base = raw.baseAddress { enc.setVertexBytes(base, length: raw.count, index: index) }
        }
    }

    private func setBytes(_ enc: MTLRenderCommandEncoder, fragment values: [Float], index: Int) {
        values.withUnsafeBytes { raw in
            if let base = raw.baseAddress { enc.setFragmentBytes(base, length: raw.count, index: index) }
        }
    }

    /// 스프라이트(빌보드) — Android drawSprites(depthTest = false). 모든 호출이 깊이 무시.
    private func drawSprites(_ enc: MTLRenderCommandEncoder, _ buffer: MTLBuffer?, _ count: Int,
                             _ texture: MTLTexture?, vp: simd_float4x4, model: simd_float4x4, t: Float) {
        guard let buffer, count > 0, let texture else { return }
        enc.setRenderPipelineState(spritePipeline)
        enc.setDepthStencilState(depthOff)
        enc.setVertexBuffer(buffer, offset: 0, index: 0)
        var u: [Float] = []
        u.reserveCapacity(36)
        GlobeRenderer.append(vp, to: &u)
        GlobeRenderer.append(model, to: &u)
        u.append(0); u.append(0); u.append(camDist); u.append(t)
        setBytes(enc, vertex: u, index: 1)
        setBytes(enc, fragment: [fade], index: 0)
        enc.setFragmentTexture(texture, index: 0)
        enc.setFragmentSamplerState(samplerClamp, index: 0)
        enc.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: count)
    }

    private func drawEarth(_ enc: MTLRenderCommandEncoder, mvp: simd_float4x4, sun: SIMD3<Float>) {
        guard let vb = earthVB, let ib = earthIB, let tex = earthTex, earthIndexCount > 0 else { return }
        enc.setRenderPipelineState(earthPipeline)
        enc.setDepthStencilState(depthTestWrite)
        enc.setVertexBuffer(vb, offset: 0, index: 0)
        var u: [Float] = []
        GlobeRenderer.append(mvp, to: &u)
        u.append(1.0)
        setBytes(enc, vertex: u, index: 1)
        setBytes(enc, fragment: [sun.x, sun.y, sun.z, fade], index: 0)
        enc.setFragmentTexture(tex, index: 0)
        enc.setFragmentSamplerState(samplerRepeat, index: 0)
        enc.drawIndexedPrimitives(type: .triangle, indexCount: earthIndexCount, indexType: .uint32,
                                  indexBuffer: ib, indexBufferOffset: 0)
    }

    /// 구름 — 지구 메쉬를 1.012 배로. 확대(camDist 1.7 이하)하면 사라진다(Android drawClouds).
    private func drawClouds(_ enc: MTLRenderCommandEncoder, mvp: simd_float4x4, sun: SIMD3<Float>, t: Float) {
        guard let vb = earthVB, let ib = earthIB, let tex = cloudTex, earthIndexCount > 0 else { return }
        let f = min(max((camDist - 1.7) / (2.4 - 1.7), 0), 1)
        let zoomAlpha = f * f * (3 - 2 * f)
        if zoomAlpha < 0.01 { return }
        enc.setRenderPipelineState(cloudPipeline)
        enc.setDepthStencilState(depthTestOnly)
        enc.setVertexBuffer(vb, offset: 0, index: 0)
        var u: [Float] = []
        GlobeRenderer.append(mvp, to: &u)
        u.append(1.012)
        setBytes(enc, vertex: u, index: 1)
        setBytes(enc, fragment: [sun.x, sun.y, sun.z, fade, zoomAlpha, t * GlobeRenderer.cloudDrift], index: 0)
        enc.setFragmentTexture(tex, index: 0)
        enc.setFragmentSamplerState(samplerRepeat, index: 0)
        enc.drawIndexedPrimitives(type: .triangle, indexCount: earthIndexCount, indexType: .uint32,
                                  indexBuffer: ib, indexBufferOffset: 0)
    }

    private func drawTrail(_ enc: MTLRenderCommandEncoder, _ tr: Trail, mvp: simd_float4x4, t: Float) {
        enc.setRenderPipelineState(ringPipeline)
        enc.setDepthStencilState(depthTestOnly)
        enc.setVertexBuffer(tr.buffer, offset: 0, index: 0)
        var u: [Float] = []
        GlobeRenderer.append(mvp, to: &u)
        setBytes(enc, vertex: u, index: 1)
        setBytes(enc, fragment: [t, tr.speed, fade, tr.phase, tr.intensity,
                                 tr.colorA.x, tr.colorA.y, tr.colorA.z,
                                 tr.colorB.x, tr.colorB.y, tr.colorB.z], index: 0)
        enc.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: tr.count)
    }

    /// 별자리 연결선 — Android GL_LINES(굵기 2px)를 화면 공간 두께 사각형으로.
    private func drawConstellationLines(_ enc: MTLRenderCommandEncoder, mvp: simd_float4x4) {
        guard let vb = constLineVB, constLineCount > 0 else { return }
        enc.setRenderPipelineState(linePipeline)
        enc.setDepthStencilState(depthOff)
        enc.setVertexBuffer(vb, offset: 0, index: 0)
        var u: [Float] = []
        GlobeRenderer.append(mvp, to: &u)
        u.append(viewportSize.x); u.append(viewportSize.y); u.append(lineWidthPx)
        setBytes(enc, vertex: u, index: 1)
        setBytes(enc, fragment: [fade], index: 0)
        enc.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: constLineCount)
    }

    /// 태양 방향(지구 좌표계) — UTC 기준 하루 360도(적도 상공, UTC 정오에 경도 0 상공).
    private func sunDirection() -> SIMD3<Float> {
        let dayFrac = Date().timeIntervalSince1970.truncatingRemainder(dividingBy: 86_400) / 86_400
        let lam = (180.0 - dayFrac * 360.0) * .pi / 180
        return SIMD3(Float(sin(lam)), 0, Float(cos(lam)))
    }

    /// 태양 — 광원 방향 하늘(반지름 45) 단일 스프라이트(mode 2 = 트윙클 없음). 1분마다만 재배치.
    private func drawSun(_ enc: MTLRenderCommandEncoder, vp: simd_float4x4, model: simd_float4x4,
                         t: Float, sun: SIMD3<Float>) {
        let dayFrac = Float(Date().timeIntervalSince1970.truncatingRemainder(dividingBy: 86_400) / 86_400)
        if sunBuiltFrac < 0 || abs(dayFrac - sunBuiltFrac) > 1 / 1440 {
            sunBuiltFrac = dayFrac
            var list: [Float] = []
            GlobeGeometry.addSprite(&list, sun * GlobeRenderer.sunDist, 1, 1, 1, 1, size: 1.5, phase: 0.13, mode: 2)
            sunVB = makeBuffer(list)
        }
        drawSprites(enc, sunVB, 6, sunTex, vp: vp, model: model, t: t)
    }

    // MARK: 유성 (Android drawMeteor / emitSparks / spawnMeteor)

    private func meteorPathAt(_ s: Float) -> SIMD3<Float> {
        let arc = 4 * s * (1 - s)
        return meteorP0 + meteorDir * (meteorLen * s) + meteorPerp * (meteorBend * arc)
    }

    private func drawMeteor(_ enc: MTLRenderCommandEncoder, vp: simd_float4x4, t: Float, dt: Float) {
        if meteorStartT < 0, t >= nextRollT {
            if Float.random(in: 0..<1) < GlobeRenderer.meteorSpawnChance {
                meteorTintIdx = meteorStreak % GlobeRenderer.meteorTints.count
                spawnMeteor(t)
                meteorStreak += 1
            } else {
                meteorStreak = 0
                nextRollT = t + GlobeRenderer.meteorRollInterval
            }
        }

        meteorData.removeAll(keepingCapacity: true)

        // 잔류 파장 — 경로 양옆으로 벌어지며 물결처럼 일렁이다 5~10초에 걸쳐 사그라든다.
        if !meteorSparks.isEmpty {
            meteorSparks.removeAll { t - $0[10] >= $0[11] }
            for s in meteorSparks {
                let age = t - s[10]
                let f = age / s[11]
                let bloom = min(1, age / 0.35)
                let fadeS = bloom * (1 - f) * (1 - f)
                let wave = 0.55 + 0.45 * sin(s[12] - t * 2.2)
                let k = fadeS * wave
                let pos = SIMD3<Float>(s[0] + s[3] * age, s[1] + s[4] * age, s[2] + s[5] * age)
                let size = s[9] * (0.7 + 0.6 * (1 - f))
                GlobeGeometry.addSprite(&meteorData, pos, s[6] * k, s[7] * k, s[8] * k, 1,
                                        size: size, phase: s[12] * 0.159, mode: 1)
            }
        }

        // 본체(머리 + 휘어지는 꼬리) — 화면 밖으로 완전히 나갈 때까지.
        if meteorStartT >= 0 {
            let u = (t - meteorStartT) / meteorDur
            if u >= 1 + GlobeRenderer.meteorTailFrac + 0.06 {
                meteorStartT = -1
                nextRollT = t // 낙하가 끝나면 대기 없이 즉시 재도전
            } else {
                let ignite = min(1, u / 0.08)
                let tints = GlobeRenderer.meteorTints
                let tint = tints[meteorTintIdx]
                let tint2 = tints[(meteorTintIdx + 1) % tints.count]
                let tailFrac = GlobeRenderer.meteorTailFrac * (0.4 + 0.6 * ignite)
                let n = GlobeRenderer.meteorSprites
                for i in 0..<n {
                    let back = Float(i) / Float(n - 1)
                    let fall = 1 - back
                    let bright = ignite * (0.06 + 0.94 * fall * fall)
                    let pos = meteorPathAt(max(u - tailFrac * back, -0.05))
                    let mixT = back * 0.60
                    let c = tint + (tint2 - tint) * mixT
                    GlobeGeometry.addSprite(&meteorData, pos,
                                            bright * (0.72 + 0.28 * fall) * c.x,
                                            bright * (0.80 + 0.20 * fall) * c.y,
                                            bright * c.z, 1,
                                            size: 0.05 + 0.10 * fall,
                                            phase: Float(i) * 0.37,
                                            mode: i == 0 ? 2 : 1)
                }
                let uc = min(u, 1)
                emitSparks(t: t, dt: dt, u: uc, tint: tint, tint2: tint2)
                lastMeteorU = uc
            }
        }

        let floatCount = meteorData.count
        if floatCount == 0 { return }
        let bytes = floatCount * MemoryLayout<Float>.stride
        if meteorVB == nil || meteorVB!.length < bytes {
            meteorVB = device.makeBuffer(length: max(bytes, 8192 * 4), options: .storageModeShared)
        }
        guard let vb = meteorVB else { return }
        meteorData.withUnsafeBytes { raw in
            if let base = raw.baseAddress { vb.contents().copyMemory(from: base, byteCount: bytes) }
        }
        drawSprites(enc, vb, floatCount / GlobeGeometry.spriteFloats, glowTex,
                    vp: vp, model: matrix_identity_float4x4, t: t)
    }

    private func emitSparks(t: Float, dt: Float, u: Float, tint: SIMD3<Float>, tint2: SIMD3<Float>) {
        sparkEmitCarry += dt * GlobeRenderer.sparkRate
        let n = Int(sparkEmitCarry)
        if n <= 0 { return }
        sparkEmitCarry -= Float(n)
        let jitter = meteorLen * 0.008
        for _ in 0..<n {
            if meteorSparks.count >= GlobeRenderer.sparkMax { meteorSparks.removeFirst() }
            let s = min(max(lastMeteorU + (u - lastMeteorU) * Float.random(in: 0..<1), 0), 1)
            let pos = meteorPathAt(s)
            let m1 = Float.random(in: 0..<1) * 0.8
            let m2 = Float.random(in: 0..<1) * 0.45
            var c = tint + (tint2 - tint) * m1
            c += (SIMD3<Float>(1, 1, 1) - c) * m2
            let soft = Float.random(in: 0..<1) < 0.70
            let br: Float = soft ? 0.16 + Float.random(in: 0..<1) * 0.18 : 0.40 + Float.random(in: 0..<1) * 0.34
            let size: Float = soft ? 0.036 + Float.random(in: 0..<1) * 0.030 : 0.016 + Float.random(in: 0..<1) * 0.016
            let side: Float = Bool.random() ? 1 : -1
            let drift = meteorLen * (0.0035 + Float.random(in: 0..<1) * 0.0055) * side
            let jx = (Float.random(in: 0..<1) * 2 - 1) * jitter
            let jy = (Float.random(in: 0..<1) * 2 - 1) * jitter
            let jz = (Float.random(in: 0..<1) * 2 - 1) * jitter
            let life = GlobeRenderer.sparkLifeMin + Float.random(in: 0..<1) * GlobeRenderer.sparkLifeVar
            let waveArg = s * 18 + Float.random(in: 0..<1) * 0.9
            let spark: [Float] = [
                pos.x + jx, pos.y + jy, pos.z + jz,
                meteorPerp.x * drift, meteorPerp.y * drift, meteorPerp.z * drift,
                c.x * br, c.y * br, c.z * br,
                size, t, life, waveArg,
            ]
            meteorSparks.append(spark)
        }
    }

    /// 화면 기준 사선 횡단 — 좌/우 화면 밖 상단 ~10% 에서 출발, 반대쪽 하단 50~90% 로 퇴장. 항상 위로 불룩한 아치.
    private func spawnMeteor(_ t: Float) {
        meteorStartT = t
        meteorDur = 1.5 + Float.random(in: 0..<1) * 0.7
        lastMeteorU = 0
        sparkEmitCarry = 0
        let depthZ: Float = 20 + Float.random(in: 0..<1) * 16
        let kY = depthZ * 0.384
        let kX = kY * screenAspect
        let midZ = camDist - depthZ
        let leftToRight = Bool.random()
        let xEdge = kX * 1.30
        let x0 = leftToRight ? -xEdge : xEdge
        let x1 = -x0
        let fTop: Float = 0.06 + Float.random(in: 0..<1) * 0.08
        let fEnd: Float = 0.50 + Float.random(in: 0..<1) * 0.40
        let y0 = kY * (1 - 2 * fTop)
        let y1 = kY * (1 - 2 * fEnd)
        let z0 = midZ + (Float.random(in: 0..<1) - 0.5) * 0.30 * kY
        let z1 = midZ - (z0 - midZ)
        meteorP0 = SIMD3(x0, y0, z0)
        let delta = SIMD3<Float>(x1 - x0, y1 - y0, z1 - z0)
        meteorLen = simd_length(delta)
        meteorDir = delta / meteorLen
        var px = meteorDir.y
        var py = -meteorDir.x
        let pl = (px * px + py * py).squareRoot()
        if pl < 0.15 {
            px = 0
            py = 1
        } else {
            px /= pl
            py /= pl
        }
        if py < 0 {
            px = -px
            py = -py
        }
        meteorPerp = SIMD3(px, py, 0)
        meteorBend = meteorLen * (0.05 + Float.random(in: 0..<1) * 0.07)
    }

    // MARK: 지오메트리 빌드

    private func buildEarthMesh() {
        let stacks = 96, slices = 192
        var verts: [Float] = []
        verts.reserveCapacity((stacks + 1) * (slices + 1) * 5)
        for i in 0...stacks {
            let v = Float(i) / Float(stacks)
            let phi = (90.0 - 180.0 * Double(v)) * .pi / 180
            for j in 0...slices {
                let u = Float(j) / Float(slices)
                let lam = (-180.0 + 360.0 * Double(u)) * .pi / 180
                verts.append(Float(cos(phi) * sin(lam)))
                verts.append(Float(sin(phi)))
                verts.append(Float(cos(phi) * cos(lam)))
                verts.append(u)
                verts.append(v)
            }
        }
        var idx: [UInt32] = []
        idx.reserveCapacity(stacks * slices * 6)
        for i in 0..<stacks {
            for j in 0..<slices {
                let a = UInt32(i * (slices + 1) + j)
                let b = a + UInt32(slices + 1)
                idx.append(a); idx.append(b); idx.append(a + 1)
                idx.append(a + 1); idx.append(b); idx.append(b + 1)
            }
        }
        earthVB = makeBuffer(verts)
        earthIB = idx.withUnsafeBytes { raw in
            raw.baseAddress.flatMap { device.makeBuffer(bytes: $0, length: raw.count, options: .storageModeShared) }
        }
        earthIndexCount = idx.count
    }

    /// 트레일 — Random(11) 로 반지름/기울기/호 길이/색/위상을 섞은 자유 원호 5개(Android buildTrails 와 같은 호출 순서).
    private func buildTrails() {
        trails.removeAll()
        var rnd = JavaRandom(11)
        for i in 0..<GlobeRenderer.trailCount {
            let radius: Float = 1.28 + rnd.nextFloat() * 0.50
            let halfW: Float = 0.030 + rnd.nextFloat() * 0.020
            let tiltX: Float = -38 + rnd.nextFloat() * 76
            let tiltZ: Float = -45 + rnd.nextFloat() * 90
            let start: Float = rnd.nextFloat() * 360
            let sweep: Float = 130 + rnd.nextFloat() * 150
            let data = GlobeGeometry.buildArc(radius: radius, halfWidth: halfW, tiltX: tiltX, tiltZ: tiltZ,
                                              start: start, sweep: sweep)
            let dir: Float = rnd.nextBoolean() ? 1 : -1
            let speed = dir * (0.030 + rnd.nextFloat() * 0.040)
            let phase = rnd.nextFloat() * 6.2832
            let intensity: Float = i < 2 ? 1 : 0.35 + rnd.nextFloat() * 0.25
            guard let buffer = makeBuffer(data) else { continue }
            let colors = GlobeRenderer.trailColors
            trails.append(Trail(buffer: buffer, count: data.count / 5,
                                colorA: colors[i % colors.count], colorB: colors[(i + 2) % colors.count],
                                speed: speed, phase: phase, intensity: intensity))
        }
    }

    /// 배경 별밭 3겹 + 성운 + 은하수 7층 + 12궁 + 반짝별 — Android buildStarfield 를 **같은 난수 호출 순서**로.
    private func buildStarfield() {
        var rnd = JavaRandom(7)
        var list: [Float] = []
        list.reserveCapacity(10_500 * 6 * GlobeGeometry.spriteFloats)

        func randomOnSphere(_ radius: Float) -> SIMD3<Float> {
            let z = rnd.nextFloat() * 2 - 1
            let ang = rnd.nextFloat() * 6.2832
            let r = (1 - z * z).squareRoot()
            return SIMD3(r * cos(ang) * radius, z * radius, r * sin(ang) * radius)
        }
        func addShell(radius: Float, count: Int, sizeBase: Float, sizeVar: Float, brightMul: Float) {
            for _ in 0..<count {
                let p = randomOnSphere(radius)
                let warm = rnd.nextFloat()
                let bright = (0.15 + rnd.nextFloat() * 0.68) * brightMul
                let big = rnd.nextFloat()
                let phase = rnd.nextFloat()
                GlobeGeometry.addSprite(&list, p,
                                        bright * (0.85 + 0.15 * warm),
                                        bright * (0.85 + 0.10 * warm),
                                        bright * (0.95 - 0.15 * warm), 1,
                                        size: sizeBase + big * big * sizeVar, phase: phase, mode: 1)
            }
        }
        addShell(radius: 12, count: 460, sizeBase: 0.022, sizeVar: 0.070, brightMul: 1.00)
        addShell(radius: 22, count: 900, sizeBase: 0.026, sizeVar: 0.088, brightMul: 0.76)
        addShell(radius: 38, count: 1400, sizeBase: 0.032, sizeVar: 0.105, brightMul: 0.56)

        let nebulaColors: [SIMD3<Float>] = [
            SIMD3(0.055, 0.030, 0.100), SIMD3(0.040, 0.050, 0.110), SIMD3(0.070, 0.030, 0.080),
            SIMD3(0.030, 0.050, 0.100), SIMD3(0.060, 0.040, 0.110), SIMD3(0.050, 0.020, 0.090),
            SIMD3(0.014, 0.034, 0.090), SIMD3(0.010, 0.028, 0.078), SIMD3(0.016, 0.040, 0.084),
        ]
        for c in nebulaColors {
            let p = randomOnSphere(41)
            let size: Float = 6.5 + rnd.nextFloat() * 4.0
            let phase = rnd.nextFloat()
            GlobeGeometry.addSprite(&list, p, c.x, c.y, c.z, 1, size: size, phase: phase, mode: 1)
        }

        // ── 은하수 ──
        let bandM = GlobeGeometry.rotationZ(28) * GlobeGeometry.rotationX(62)
        func bandPoint(_ radius: Float, _ spread: Float, _ ang: Float) -> SIMD3<Float> {
            let pin = SIMD4<Float>(cos(ang) * cos(spread), sin(spread), sin(ang) * cos(spread), 0)
            let pout = bandM * pin
            return SIMD3(pout.x * radius, pout.y * radius, pout.z * radius)
        }
        let coreAng: Float = 1.2
        let pi = Float.pi
        func angDist(_ ang: Float) -> Float { abs(GlobeGeometry.floorModF(ang - coreAng + pi, 6.2832) - pi) }
        func coreness(_ ang: Float) -> Float {
            let d = angDist(ang)
            return exp(-d * d / 1.5)
        }
        func riftAtten(_ ang: Float, _ spread: Float) -> Float {
            let d = angDist(ang)
            let strength = exp(-d * d / 1.9) * 0.72
            if strength < 0.04 { return 1 }
            let c1: Float = 0.024 * sin(ang * 2.3 + 0.8)
            let c2: Float = 0.011 * sin(ang * 5.1)
            let center: Float = 0.020 + c1 + c2
            let halfW: Float = 0.026 + 0.011 * sin(ang * 3.7 + 2.0)
            let x = (spread - center) / halfW
            return 1 - strength * exp(-x * x)
        }
        func patch(_ ang: Float) -> Float {
            let p: Float = 0.5 + 0.5 * sin(ang * 7.3 + 1.7) * sin(ang * 3.1 + 4.2)
            return 0.70 + 0.45 * p
        }
        // ① 백열 코어 라인
        for i in 0..<96 {
            let ang = Float(i) / 96 * 6.2832 + (rnd.nextFloat() - 0.5) * 0.04
            let cn = coreness(ang)
            let spread = Float(rnd.nextGaussian() * 0.010)
            let a = riftAtten(ang, spread)
            let base = (0.030 + 0.022 * cn) * (0.40 + 0.60 * a) * patch(ang)
            let size: Float = 0.9 + rnd.nextFloat() * 0.6 + 0.5 * cn
            let phase = rnd.nextFloat()
            GlobeGeometry.addSprite(&list, bandPoint(40, spread, ang), base, base * 0.86, base * 0.94, 1,
                                    size: size, phase: phase, mode: 1)
        }
        // ② 마젠타 리본
        for _ in 0..<150 {
            let ang = rnd.nextFloat() * 6.2832
            let cn = coreness(ang)
            let spread = Float(rnd.nextGaussian() * 0.035 * Double(1 + 0.5 * cn))
            let a = riftAtten(ang, spread)
            let base = (0.022 + 0.020 * cn) * patch(ang) * (0.25 + 0.75 * a)
            let size: Float = 1.6 + rnd.nextFloat() * 1.5 + 0.6 * cn
            let phase = rnd.nextFloat()
            GlobeGeometry.addSprite(&list, bandPoint(40, spread, ang), base * 1.00, base * 0.30, base * 0.62, 1,
                                    size: size, phase: phase, mode: 1)
        }
        // ③ 바이올렛 외곽 글로우
        for _ in 0..<110 {
            let ang = rnd.nextFloat() * 6.2832
            let cn = coreness(ang)
            let spread = Float(rnd.nextGaussian() * 0.085 * Double(1 + 0.6 * cn))
            let base = (0.009 + 0.009 * cn) * patch(ang)
            let size: Float = 2.8 + rnd.nextFloat() * 1.9
            let phase = rnd.nextFloat()
            GlobeGeometry.addSprite(&list, bandPoint(40.5, spread, ang), base * 0.62, base * 0.30, base * 0.95, 1,
                                    size: size, phase: phase, mode: 1)
        }
        // ④ 골드 응집
        for _ in 0..<30 {
            let ang = coreAng + Float(rnd.nextGaussian() * 0.55)
            let spread: Float = 0.030 + Float(abs(rnd.nextGaussian() * 0.045))
            let size: Float = 1.4 + rnd.nextFloat() * 1.7
            let phase = rnd.nextFloat()
            GlobeGeometry.addSprite(&list, bandPoint(40, spread, ang), 0.052, 0.032, 0.011, 1,
                                    size: size, phase: phase, mode: 1)
        }
        // ⑤ 시안 가장자리 미광
        for _ in 0..<26 {
            let ang = rnd.nextFloat() * 6.2832
            let side: Float = rnd.nextBoolean() ? 1 : -1
            let spread = side * (0.09 + rnd.nextFloat() * 0.07)
            let size: Float = 2.2 + rnd.nextFloat() * 1.6
            let phase = rnd.nextFloat()
            GlobeGeometry.addSprite(&list, bandPoint(40.5, spread, ang), 0.007, 0.024, 0.028, 1,
                                    size: size, phase: phase, mode: 1)
        }
        // ⑥ 잔별 밀집 띠 3200개(채택-기각)
        var placed = 0
        while placed < 3200 {
            let ang = rnd.nextFloat() * 6.2832
            let cn = coreness(ang)
            if rnd.nextFloat() > 0.34 + 0.66 * cn { continue }
            placed += 1
            let thick: Float = 1 + 0.7 * cn
            let sigma: Float = (rnd.nextFloat() < 0.62 ? 0.05 : 0.13) * thick
            let spread = Float(rnd.nextGaussian() * Double(sigma))
            let a = riftAtten(ang, spread)
            let brBase: Float = 0.09 + rnd.nextFloat() * 0.30
            let br = brBase * (0.75 + 0.50 * cn) * patch(ang) * (0.30 + 0.70 * a)
            let roll = rnd.nextFloat()
            let tint: SIMD3<Float> = roll < 0.68 ? SIMD3(0.90, 0.94, 1.00)
                : (roll < 0.90 ? SIMD3(1.00, 0.68, 0.85) : SIMD3(1.00, 0.88, 0.62))
            let s1 = rnd.nextFloat()
            let s2 = rnd.nextFloat()
            let size: Float = 0.032 + s1 * s2 * 0.11
            let phase = rnd.nextFloat()
            GlobeGeometry.addSprite(&list, bandPoint(39, spread, ang), br * tint.x, br * tint.y, br * tint.z, 1,
                                    size: size, phase: phase, mode: 1)
        }
        // ⑦ 전경 밝은 별
        for _ in 0..<40 {
            let ang = rnd.nextFloat() * 6.2832
            let cn = coreness(ang)
            let spread = Float(rnd.nextGaussian() * 0.10 * Double(1 + 0.6 * cn))
            let br: Float = 0.34 + rnd.nextFloat() * 0.40
            let size: Float = 0.12 + rnd.nextFloat() * 0.10
            let phase = rnd.nextFloat()
            GlobeGeometry.addSprite(&list, bandPoint(39, spread, ang), br * 0.95, br * 0.96, br, 1,
                                    size: size, phase: phase, mode: 1)
        }

        // ── 황도 12궁 ── (references/zodiac.avif 판독값 — 임의 수정 금지, Android 와 같은 좌표/색)
        var lines: [Float] = []
        func addConstellation(_ centerLat: Double, _ centerLng: Double, _ scaleDeg: Float, _ rollDeg: Float,
                              _ tint: SIMD3<Float>, _ p: [Float], _ s: [Int]) {
            let radius: Float = 42
            let c = GlobeGeometry.latLngToXyz(centerLat, centerLng, 1)
            let east = simd_normalize(simd_cross(SIMD3<Float>(0, 1, 0), c))
            let north = simd_normalize(simd_cross(c, east))
            let sc = scaleDeg * .pi / 180
            let roll = rollDeg * .pi / 180
            let cosR = cos(roll), sinR = sin(roll)
            var pts: [SIMD3<Float>] = []
            for k in 0..<(p.count / 2) {
                let px = p[k * 2], py = p[k * 2 + 1]
                let x = (px * cosR - py * sinR) * sc
                let y = (px * sinR + py * cosR) * sc
                let d = simd_normalize(c + east * x + north * y)
                pts.append(d * radius)
            }
            for q in pts {
                let br: Float = 0.55 + rnd.nextFloat() * 0.25
                let size: Float = 0.14 + rnd.nextFloat() * 0.056
                let phase = rnd.nextFloat()
                GlobeGeometry.addSprite(&list, q,
                                        br * (0.45 + 0.55 * tint.x),
                                        br * (0.45 + 0.55 * tint.y),
                                        br * (0.45 + 0.55 * tint.z), 1,
                                        size: size, phase: phase, mode: 1)
            }
            let lc = tint * 0.15 // 연결선 밝기(Android 0.15)
            for k in 0..<(s.count / 2) {
                GlobeGeometry.addLineQuad(&lines, pts[s[k * 2]], pts[s[k * 2 + 1]], lc)
            }
        }
        addConstellation(52.0, -165.0, 4.5, -8, SIMD3(1.00, 0.52, 0.42),
                         [-1.0, 0.35, 0.45, 0.15, 0.91, -0.08, 1.0, -0.35],
                         [0, 1, 1, 2, 2, 3])
        addConstellation(18.0, -135.0, 5.0, 10, SIMD3(0.62, 0.95, 0.55),
                         [-0.81, 0.83, -0.33, 0.4, -1.0, 0.31, -0.15, 0.13, -0.3, -0.11,
                          -0.14, -0.03, 0.0, -0.04, -0.11, -0.2, 0.05, -0.18, 0.32, -0.41,
                          0.91, -0.61, 1.0, -0.83],
                         [0, 1, 1, 3, 3, 5, 5, 7, 2, 4, 4, 7, 7, 8, 6, 8, 8, 9, 9, 10, 10, 11])
        addConstellation(-18.0, -105.0, 4.8, -14, SIMD3(1.00, 0.88, 0.45),
                         [-0.77, 0.8, -0.34, 0.69, -0.08, 0.52, -1.0, 0.39, 0.33, 0.32,
                          0.73, 0.17, 1.0, 0.17, -0.91, 0.1, -0.46, -0.08, 0.65, -0.15,
                          -0.08, -0.16, 0.45, -0.43, 0.36, -0.8],
                         [0, 1, 1, 2, 2, 4, 4, 5, 5, 6, 5, 9, 9, 11, 11, 12, 11, 10, 10, 8, 8, 7, 7, 3, 3, 0])
        addConstellation(-52.0, -75.0, 4.2, 6, SIMD3(0.75, 0.85, 1.00),
                         [-1.0, 0.96, -0.38, 0.16, -0.2, -0.17, 1.0, -0.51, -0.42, -0.96],
                         [0, 1, 1, 2, 2, 3, 2, 4])
        addConstellation(52.0, -45.0, 4.8, 0, SIMD3(1.00, 0.72, 0.30),
                         [1.0, 0.74, 0.64, 0.84, 0.34, 0.47, 0.4, 0.2, 0.77, 0.06,
                          -0.51, -0.23, 0.85, -0.35, -0.38, -0.6, -1.0, -0.84],
                         [0, 1, 1, 2, 2, 3, 3, 4, 4, 6, 3, 5, 5, 8, 8, 7, 7, 6])
        addConstellation(18.0, -15.0, 5.2, 12, SIMD3(0.55, 1.00, 0.80),
                         [-0.09, 0.75, 1.0, 0.68, 0.17, 0.43, 0.72, 0.34, 0.49, 0.24,
                          -0.2, 0.0, -0.4, -0.01, -0.54, -0.05, 0.28, -0.15, -1.0, -0.28,
                          0.23, -0.5, -0.33, -0.6, -0.26, -0.68, -0.6, -0.75],
                         [0, 2, 2, 4, 4, 3, 3, 1, 2, 5, 4, 8, 5, 8, 5, 6, 6, 7, 7, 9, 8, 10, 10, 11, 11, 12, 12, 13])
        addConstellation(-18.0, 15.0, 4.6, -6, SIMD3(1.00, 0.62, 0.82),
                         [-0.36, 1.0, 0.56, 0.9, -0.29, 0.26, 0.87, -0.04, -0.87, -0.29,
                          0.39, -0.77, 0.51, -1.0],
                         [0, 1, 0, 2, 0, 3, 1, 3, 2, 4, 3, 5, 5, 6])
        addConstellation(-52.0, 45.0, 5.0, 8, SIMD3(1.00, 0.42, 0.48),
                         [0.55, 0.8, 0.96, 0.58, 0.56, 0.35, 0.97, 0.3, 0.38, 0.28,
                          0.27, 0.16, 1.0, 0.06, 0.04, -0.13, -0.69, -0.22, -0.86, -0.43,
                          -1.0, -0.52, -0.11, -0.71, -0.76, -0.78, -0.42, -0.8],
                         [0, 1, 1, 3, 3, 6, 1, 2, 2, 4, 4, 5, 5, 7, 7, 11, 11, 13, 13, 12, 12, 10, 10, 9, 9, 8])
        addConstellation(52.0, 75.0, 5.4, -10, SIMD3(0.72, 0.55, 1.00),
                         [-0.66, 0.82, 0.45, 0.81, -0.09, 0.77, -0.25, 0.7, -0.38, 0.66,
                          0.31, 0.4, -0.09, 0.39, -0.63, 0.35, 0.07, 0.28, -0.25, 0.28,
                          0.71, 0.13, -0.9, 0.13, 0.43, 0.13, -0.1, 0.12, -1.0, -0.06,
                          0.4, -0.09, 0.54, -0.25, -0.7, -0.44, -0.3, -0.5, -0.56, -0.67,
                          -0.12, -0.82, 1.0, 0.4],
                         [0, 4, 4, 3, 3, 2, 2, 6, 6, 9, 9, 7, 7, 11, 11, 14, 14, 17, 17, 19, 19, 18,
                          19, 20, 6, 13, 13, 8, 8, 5, 5, 1, 5, 12, 12, 15, 15, 16, 12, 10, 10, 21])
        addConstellation(18.0, 105.0, 4.8, 4, SIMD3(0.45, 0.88, 0.92),
                         [1.0, 0.93, 0.93, 0.59, 0.05, -0.18, -0.76, -0.54, -1.0, -0.75,
                          -0.63, -0.81, -0.21, -0.93, 0.63, -0.93, 0.69, -0.75],
                         [0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 1])
        addConstellation(-18.0, 135.0, 4.8, -4, SIMD3(0.50, 0.72, 1.00),
                         [0.72, 1.0, -0.52, 0.21, 0.39, 0.05, -0.05, -0.02, -0.55, -0.12,
                          -0.72, -0.14, -0.72, -0.4, 0.19, -0.58, -0.17, -0.63, 0.65, -0.79,
                          -0.32, -1.0],
                         [0, 1, 1, 3, 3, 2, 1, 4, 4, 5, 5, 6, 6, 10, 10, 8, 8, 7, 7, 9])
        addConstellation(-52.0, 165.0, 5.2, 14, SIMD3(0.82, 0.70, 1.00),
                         [-0.02, 1.0, 0.18, 0.91, 0.24, 0.7, 0.07, 0.57, -0.07, 0.6,
                          -0.23, 0.69, -0.19, 0.88, -0.03, 0.32, 0.21, -0.08, 0.26, -0.36,
                          0.64, -0.77, 0.83, -1.0, 0.53, -0.93, 0.06, -0.82, -0.36, -0.82,
                          -0.54, -1.0, -0.83, -0.85],
                         [0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 0, 4, 7, 7, 8, 8, 9, 9, 10, 10, 11,
                          11, 12, 12, 13, 13, 14, 14, 15, 15, 16])
        constLineVB = makeBuffer(lines)
        constLineCount = lines.count / GlobeGeometry.lineFloats

        // ── 배경 반짝별 9개(4방 광선 텍스처) ──
        var bg: [Float] = []
        for _ in 0..<9 {
            let p = randomOnSphere(36)
            let br: Float = 0.16 + rnd.nextFloat() * 0.16
            let warm = rnd.nextFloat()
            let size: Float = 0.30 + rnd.nextFloat() * 0.28
            let phase = rnd.nextFloat()
            GlobeGeometry.addSprite(&bg, p, br * (0.90 + 0.10 * warm), br * 0.93, br * (1.05 - 0.15 * warm), 1,
                                    size: size, phase: phase, mode: 1)
        }
        bgFlareVB = makeBuffer(bg)
        bgFlareCount = bg.count / GlobeGeometry.spriteFloats

        starfieldVB = makeBuffer(list)
        starfieldCount = list.count / GlobeGeometry.spriteFloats
    }

    private static func append(_ m: simd_float4x4, to out: inout [Float]) {
        for col in [m.columns.0, m.columns.1, m.columns.2, m.columns.3] {
            out.append(col.x); out.append(col.y); out.append(col.z); out.append(col.w)
        }
    }

    // MARK: 버퍼/텍스처

    private func makeBuffer(_ values: [Float]) -> MTLBuffer? {
        guard !values.isEmpty else { return nil }
        return values.withUnsafeBytes { raw in
            raw.baseAddress.flatMap { device.makeBuffer(bytes: $0, length: raw.count, options: .storageModeShared) }
        }
    }

    /// 번들 JPG(earth_blue_marble / earth_clouds) — Android assets 와 **같은 파일**(project.yml 참조).
    /// SRGB 해석 없이(GL 과 같은 감마 공간 값) + 밉맵. 없으면 nil(해당 레이어만 생략).
    private func loadBundleTexture(_ name: String) -> MTLTexture? {
        guard let url = Bundle.main.url(forResource: name, withExtension: "jpg") else {
            print("⚠️ Globe texture missing: \(name).jpg")
            return nil
        }
        let loader = MTKTextureLoader(device: device)
        let options: [MTKTextureLoader.Option: Any] = [
            .SRGB: false,
            .generateMipmaps: true,
            .textureUsage: NSNumber(value: MTLTextureUsage.shaderRead.rawValue),
            .textureStorageMode: NSNumber(value: MTLStorageMode.private.rawValue),
        ]
        do {
            return try loader.newTexture(URL: url, options: options)
        } catch {
            print("⚠️ Globe texture load failed \(name): \(error)")
            return nil
        }
    }

    /// CoreGraphics 로 그린 정사각 텍스처(프리멀티플라이드 RGBA — Android Bitmap 업로드와 같은 값) + 밉맵.
    private func makeGeneratedTexture(size: Int, draw: (CGContext, CGFloat) -> Void) -> MTLTexture? {
        guard let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: size * 4,
                                  space: CGColorSpaceCreateDeviceRGB(),
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { return nil }
        ctx.clear(CGRect(x: 0, y: 0, width: size, height: size))
        draw(ctx, CGFloat(size))
        guard let data = ctx.data else { return nil }
        let desc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba8Unorm, width: size, height: size,
                                                            mipmapped: true)
        desc.usage = [.shaderRead]
        guard let tex = device.makeTexture(descriptor: desc) else { return nil }
        tex.replace(region: MTLRegionMake2D(0, 0, size, size), mipmapLevel: 0, withBytes: data, bytesPerRow: size * 4)
        if let cmd = queue.makeCommandBuffer(), let blit = cmd.makeBlitCommandEncoder() {
            blit.generateMipmaps(for: tex)
            blit.endEncoding()
            cmd.commit()
            cmd.waitUntilCompleted()
        }
        return tex
    }

    private static func radial(_ cg: CGContext, _ s: CGFloat, radius: CGFloat, alphas: [CGFloat], stops: [CGFloat]) {
        let colors = alphas.map { UIColor(white: 1, alpha: $0).cgColor }
        guard let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray,
                                 locations: stops) else { return }
        let c = CGPoint(x: s / 2, y: s / 2)
        cg.drawRadialGradient(g, startCenter: c, startRadius: 0, endCenter: c, endRadius: radius, options: [])
    }

    /// 4-포인트 별 플레어(흰색 — 정점색으로 tint). Android makeFlareBitmap.
    private static func drawFlareTexture(_ cg: CGContext, _ s: CGFloat) {
        radial(cg, s, radius: s * 0.26, alphas: [1, 0x5C / 255.0, 0], stops: [0, 0.22, 1])
        func ray(_ lenFrac: CGFloat, _ thickFrac: CGFloat, _ angleDeg: CGFloat) {
            cg.saveGState()
            cg.translateBy(x: s / 2, y: s / 2)
            cg.rotate(by: angleDeg * .pi / 180)
            let half = s * lenFrac / 2
            let t = s * thickFrac / 2
            let path = CGMutablePath()
            path.move(to: CGPoint(x: -half, y: 0))
            path.addQuadCurve(to: CGPoint(x: half, y: 0), control: CGPoint(x: 0, y: -t))
            path.addQuadCurve(to: CGPoint(x: -half, y: 0), control: CGPoint(x: 0, y: t))
            path.closeSubpath()
            cg.addPath(path)
            cg.clip()
            let colors = [UIColor(white: 1, alpha: 0).cgColor, UIColor(white: 1, alpha: 0xB4 / 255.0).cgColor,
                          UIColor(white: 1, alpha: 0).cgColor]
            if let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray,
                                  locations: [0, 0.5, 1]) {
                cg.drawLinearGradient(g, start: CGPoint(x: -half, y: 0), end: CGPoint(x: half, y: 0), options: [])
            }
            cg.restoreGState()
        }
        ray(0.98, 0.09, 0)
        ray(0.98, 0.09, 90)
        ray(0.52, 0.055, 45)
        ray(0.52, 0.055, 135)
    }

    /// 부드러운 원형 글로우(흰색 — 노란 불빛/배경 별/유성 공용). Android makeGlowBitmap.
    private static func drawGlowTexture(_ cg: CGContext, _ s: CGFloat) {
        radial(cg, s, radius: s / 2, alphas: [1, 0x66 / 255.0, 0], stops: [0, 0.35, 1])
    }

    /// 태양 — 원경 산광 → 금빛 코로나 → 백열 원반(Android makeSunBitmap 의 ARGB 값 그대로).
    private static func drawSunTexture(_ cg: CGContext, _ s: CGFloat) {
        func argb(_ v: UInt32) -> CGColor {
            UIColor(red: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255,
                    blue: CGFloat(v & 0xFF) / 255, alpha: CGFloat((v >> 24) & 0xFF) / 255).cgColor
        }
        func glow(_ radiusFrac: CGFloat, _ colors: [UInt32], _ stops: [CGFloat]) {
            guard let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                                     colors: colors.map(argb) as CFArray, locations: stops) else { return }
            let c = CGPoint(x: s / 2, y: s / 2)
            cg.drawRadialGradient(g, startCenter: c, startRadius: 0, endCenter: c,
                                  endRadius: s / 2 * radiusFrac, options: [])
        }
        glow(1.00, [0x2A28_1808, 0x140A_0604, 0x0000_0000], [0, 0.5, 1])
        glow(0.46, [0xB08C_6E2E, 0x5075_5530, 0x0000_0000], [0, 0.45, 1])
        glow(0.20, [0xFFFF_F8E8, 0xFFFF_EFC8, 0xE8FF_D9A0, 0x60E8_B060, 0x0000_0000], [0, 0.55, 0.80, 0.94, 1])
    }

    // MARK: 셰이더 (Metal Shading Language — Android GLSL 과 같은 식)

    private static let shaderSource = """
    #include <metal_stdlib>
    using namespace metal;

    static float4x4 matAt(constant float *u, uint o) {
        return float4x4(float4(u[o],     u[o + 1],  u[o + 2],  u[o + 3]),
                        float4(u[o + 4], u[o + 5],  u[o + 6],  u[o + 7]),
                        float4(u[o + 8], u[o + 9],  u[o + 10], u[o + 11]),
                        float4(u[o + 12], u[o + 13], u[o + 14], u[o + 15]));
    }

    // ── 스프라이트(빌보드) — SPRITE_VS / SPRITE_FS ──
    struct SpriteOut {
        float4 position [[position]];
        float4 color;
        float2 uv;
    };

    vertex SpriteOut spriteVertex(uint vid [[vertex_id]],
                                  constant float *v [[buffer(0)]],
                                  constant float *u [[buffer(1)]]) {
        uint b = vid * 12;
        float3 center = float3(v[b], v[b + 1], v[b + 2]);
        float2 corner = float2(v[b + 3], v[b + 4]);
        float4 color = float4(v[b + 5], v[b + 6], v[b + 7], v[b + 8]);
        float size = v[b + 9];
        float phase = v[b + 10];
        float mode = v[b + 11];
        float4x4 vp = matAt(u, 0);
        float4x4 model = matAt(u, 16);
        float3 camPos = float3(u[32], u[33], u[34]);
        float time = u[35];
        float3 wc = (model * float4(center, 1.0)).xyz;
        float tw = 0.82 + 0.28 * sin(time * (1.1 + phase * 2.3) + phase * 6.2831);
        if (mode > 1.5) { tw = 1.0; }
        float vis = 1.0;
        if (mode < 0.5) {
            float3 n = normalize(wc);
            float3 toCam = normalize(camPos - wc);
            vis = smoothstep(-0.02, 0.22, dot(n, toCam));
        }
        SpriteOut o;
        o.color = color * (tw * vis);
        o.uv = corner * 0.5 + 0.5;
        float3 offs = (float3(1.0, 0.0, 0.0) * corner.x + float3(0.0, 1.0, 0.0) * corner.y) * size * (0.88 + 0.22 * tw);
        o.position = vp * float4(wc + offs, 1.0);
        return o;
    }

    fragment float4 spriteFragment(SpriteOut in [[stage_in]],
                                   texture2d<float> tex [[texture(0)]],
                                   sampler smp [[sampler(0)]],
                                   constant float *f [[buffer(0)]]) {
        return tex.sample(smp, in.uv) * in.color * f[0];
    }

    // ── 지구 / 구름 — EARTH_VS·EARTH_FS / CLOUD_FS ──
    struct EarthOut {
        float4 position [[position]];
        float2 uv;
        float3 n;
    };

    vertex EarthOut earthVertex(uint vid [[vertex_id]],
                                constant float *v [[buffer(0)]],
                                constant float *u [[buffer(1)]]) {
        uint b = vid * 5;
        float3 p = float3(v[b], v[b + 1], v[b + 2]);
        EarthOut o;
        o.uv = float2(v[b + 3], v[b + 4]);
        o.n = p;
        o.position = matAt(u, 0) * float4(p * u[16], 1.0);
        return o;
    }

    fragment float4 earthFragment(EarthOut in [[stage_in]],
                                  texture2d<float> tex [[texture(0)]],
                                  sampler smp [[sampler(0)]],
                                  constant float *f [[buffer(0)]]) {
        float3 sunDir = float3(f[0], f[1], f[2]);
        float ndl = dot(normalize(in.n), sunDir);
        float light = 0.70 + 0.45 * smoothstep(-0.18, 0.22, ndl);
        return float4(tex.sample(smp, in.uv).rgb * 0.45 * light * f[3], 1.0);
    }

    fragment float4 cloudFragment(EarthOut in [[stage_in]],
                                  texture2d<float> tex [[texture(0)]],
                                  sampler smp [[sampler(0)]],
                                  constant float *f [[buffer(0)]]) {
        float3 sunDir = float3(f[0], f[1], f[2]);
        float cloud = tex.sample(smp, float2(in.uv.x + f[5], in.uv.y)).r;
        float ndl = dot(normalize(in.n), sunDir);
        float light = 0.70 + 0.45 * smoothstep(-0.18, 0.22, ndl);
        float3 col = float3(0.62, 0.70, 0.82) * light;
        return float4(col * f[3], cloud * 0.45 * f[4] * f[3]);
    }

    // ── 궤적 트레일 — RING_VS / RING_FS ──
    struct RingOut {
        float4 position [[position]];
        float2 uv;
    };

    vertex RingOut ringVertex(uint vid [[vertex_id]],
                              constant float *v [[buffer(0)]],
                              constant float *u [[buffer(1)]]) {
        uint b = vid * 5;
        RingOut o;
        o.uv = float2(v[b + 3], v[b + 4]);
        o.position = matAt(u, 0) * float4(v[b], v[b + 1], v[b + 2], 1.0);
        return o;
    }

    fragment float4 ringFragment(RingOut in [[stage_in]],
                                 constant float *f [[buffer(0)]]) {
        float uTime = f[0];
        float uSpeed = f[1];
        float uFade = f[2];
        float uPhase = f[3];
        float uIntensity = f[4];
        float3 colorA = float3(f[5], f[6], f[7]);
        float3 colorB = float3(f[8], f[9], f[10]);
        float across = max(sin(in.uv.y * 3.14159), 0.0);
        float glow = pow(across, 2.0) * 0.07;
        float core = pow(across, 14.0);
        // GLSL smoothstep(1.0, 0.80, x) 와 같은 값 — MSL 은 edge0 >= edge1 이 정의되지 않아 뒤집어 계산.
        float ends = smoothstep(0.0, 0.20, in.uv.x) * (1.0 - smoothstep(0.80, 1.0, in.uv.x));
        float t = uTime * uSpeed;
        float w1 = 0.5 + 0.5 * sin((in.uv.x - t) * 6.2831 + uPhase);
        float w2 = 0.5 + 0.5 * sin((in.uv.x * 2.7 + t * 0.7) * 6.2831 + uPhase * 2.3);
        float flow = 0.45 + 0.55 * (0.6 * w1 + 0.4 * w2);
        float head = fract(t * 2.2 + uPhase * 0.159);
        float d1 = in.uv.x - head;
        float d2 = in.uv.x - fract(head + 0.47);
        float pulse = exp(-d1 * d1 * 220.0) + 0.45 * exp(-d2 * d2 * 300.0);
        float3 col = mix(colorA, colorB, 0.5 + 0.5 * sin(in.uv.x * 6.2831 + uTime * 0.15 + uPhase));
        float3 c = col * (glow * flow + core * (0.10 + 0.10 * flow)) + float3(1.0) * core * pulse * 0.30;
        return float4(c * ends * uFade * uIntensity, 1.0);
    }

    // ── 별자리 연결선 — LINE_VS / LINE_FS (GL_LINES 2px → 화면 공간 두께 사각형) ──
    struct LineOut {
        float4 position [[position]];
        float3 color;
    };

    vertex LineOut lineVertex(uint vid [[vertex_id]],
                              constant float *v [[buffer(0)]],
                              constant float *u [[buffer(1)]]) {
        uint b = vid * 11;
        float4x4 mvp = matAt(u, 0);
        float4 ca = mvp * float4(v[b], v[b + 1], v[b + 2], 1.0);
        float4 cb = mvp * float4(v[b + 3], v[b + 4], v[b + 5], 1.0);
        LineOut o;
        o.color = float3(v[b + 6], v[b + 7], v[b + 8]);
        if (ca.w < 0.01 || cb.w < 0.01) {
            // 카메라 뒤로 넘어간 선분은 통째로 클립 공간 밖으로(GL 라인 클리핑 대응).
            o.position = float4(0.0, 0.0, 2.0, 1.0);
            return o;
        }
        float2 vpSize = float2(u[16], u[17]);
        float2 pa = ca.xy / ca.w * vpSize * 0.5;
        float2 pb = cb.xy / cb.w * vpSize * 0.5;
        float2 dir = pb - pa;
        float len = length(dir);
        dir = len > 0.0001 ? dir / len : float2(1.0, 0.0);
        float2 nrm = float2(-dir.y, dir.x);
        float4 cur = v[b + 9] < 0.5 ? ca : cb;
        float2 offs = nrm * v[b + 10] * u[18] / vpSize;
        cur.xy += offs * cur.w;
        o.position = cur;
        return o;
    }

    fragment float4 lineFragment(LineOut in [[stage_in]],
                                 constant float *f [[buffer(0)]]) {
        return float4(in.color * f[0], 1.0);
    }
    """
}
