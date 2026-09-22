import MetalKit
import SwiftUI
import UIKit

// 다이어리 열람 파장의 **지도 왜곡** — Android `DiaryOpenWarp`(Canvas.drawBitmapMesh)의 Metal 포팅.
//
// Android 는 지도 스냅샷을 14×14 메시로 그리면서 각 꼭짓점을 "별 위치에서 퍼지는 파면" 기준으로 방사형으로 민다.
// 여기서도 **같은 메시(14×14) · 같은 식 · 같은 픽셀 단위 상수**로 꼭짓점을 매 프레임 CPU 에서 계산하고,
// 삼각형 2개/칸으로 스냅샷 텍스처를 입혀 GPU 로 그린다(= drawBitmapMesh 와 같은 조각별 선형 매핑).
//
// 예전 iOS 는 CIBumpDistortion(볼록 렌즈 1개 근사)을 매 프레임 CPU 로 CGImage 에 다시 구워 24fps 로 돌렸고,
// 이후 "Metal 툴체인 의존" 문제로 왜곡을 통째로 빼고 링만 남겼었다(1061e17). 이 파일은 글로브(GlobeRenderer)와
// 같은 방식으로 **셰이더를 런타임 컴파일**(`makeLibrary(source:)`)해 .metal 빌드 단계 없이 동작한다.
// 셰이더/파이프라인/텍스처 중 하나라도 실패하면 뷰는 아무것도 그리지 않는다(아래 라이브 지도 + 링만 보임).
//
// ⚠️ 상수를 고칠 땐 Android `DiaryOpenWarp.kt` 와 함께(메시 14, 진폭 46·(1−p), 밴드 220, 파수 0.045 — 전부 픽셀).

/// 파장 타이밍 — 링(SwiftUI Canvas)과 메시(Metal)가 **같은 시작 시각·같은 이징**으로 움직이도록 한 곳에 둔다.
/// (격리 없는 enum — 렌더러 draw 와 SwiftUI body 양쪽에서 부른다.)
enum WarpTiming {
    /// 연출 전체 길이(s) — Android `tween(1300)` 과 동일.
    static let duration: Double = 1.3

    /// 시작 후 경과 → 이징된 진행도 0..1 (Android `FastOutSlowInEasing`).
    static func progress(since start: Date, now: Date = Date()) -> Double {
        let t = min(max(now.timeIntervalSince(start) / duration, 0), 1)
        return fastOutSlowIn(t)
    }

    /// Android `FastOutSlowInEasing` = CubicBezier(0.4, 0, 0.2, 1). x(s)=t 를 이분법으로 풀어 y(s).
    static func fastOutSlowIn(_ t: Double) -> Double {
        if t <= 0 { return 0 }
        if t >= 1 { return 1 }
        func bez(_ s: Double, _ a: Double, _ b: Double) -> Double {
            let u = 1 - s
            return 3 * a * s * u * u + 3 * b * s * s * u + s * s * s
        }
        var lo = 0.0, hi = 1.0, s = t
        for _ in 0..<24 {
            s = (lo + hi) / 2
            if bez(s, 0.4, 0.2) < t { lo = s } else { hi = s }
        }
        return bez(s, 0, 1)
    }
}

/// 스냅샷 검사/메시 계산 — 격리 없는 순수 함수(GlobeGeometry 와 같은 이유).
enum WarpMesh {
    /// Android `mw`/`mh`.
    static let cols = 14
    static let rows = 14

    /// 꼭짓점 1개 = [x, y (NDC), u, v].
    static let floatsPerVertex = 4

    /// 지도 스냅샷이 실제 내용을 담고 있는지 — Metal 지도 레이어를 drawHierarchy 로 못 찍으면 검정/투명이 나오는데,
    /// 그걸 그대로 왜곡하면 1.3초 동안 화면이 까매진다. 12×12 로 줄여 가장 밝은 채널이 거의 0 이면 버린다
    /// (야경 지도의 바탕 #080617 도 파란 채널 23 이라 통과).
    static func isUsable(_ image: UIImage) -> Bool {
        guard let cg = image.cgImage else { return false }
        let n = 12
        var px = [UInt8](repeating: 0, count: n * n * 4)
        let drawn: Bool = px.withUnsafeMutableBytes { buf in
            guard let ctx = CGContext(
                data: buf.baseAddress, width: n, height: n, bitsPerComponent: 8, bytesPerRow: n * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            ctx.interpolationQuality = .low
            ctx.draw(cg, in: CGRect(x: 0, y: 0, width: n, height: n))
            return true
        }
        guard drawn else { return false }
        var brightest: UInt8 = 0
        for i in stride(from: 0, to: px.count, by: 4) {
            brightest = max(brightest, px[i], px[i + 1], px[i + 2])
        }
        return brightest > 4
    }

    /// 삼각형 인덱스(칸마다 2개) — 한 번만 만든다.
    static func indices() -> [UInt16] {
        var out: [UInt16] = []
        out.reserveCapacity(cols * rows * 6)
        let stride = cols + 1
        for row in 0..<rows {
            for col in 0..<cols {
                let a = UInt16(row * stride + col)
                let b = a + 1
                let c = a + UInt16(stride)
                let d = c + 1
                out.append(contentsOf: [a, b, c, b, d, c])
            }
        }
        return out
    }

    /// Android `DiaryOpenWarp` 의 꼭짓점 식 그대로 — [w]×[h] 는 **픽셀**(drawable) 크기, [p] 는 이징된 진행도.
    /// 텍스처 좌표는 변위 전 격자(= drawBitmapMesh 의 균일 격자 매핑).
    static func vertices(w: Float, h: Float, originX: Float, originY: Float, p: Float, into out: inout [Float]) {
        let cx = w * originX
        let cy = h * originY
        let maxR = max(max(hypotf(cx, cy), hypotf(w - cx, cy)),
                       max(hypotf(cx, h - cy), hypotf(w - cx, h - cy)))
        let front = p * maxR
        let amp = 46 * (1 - p)        // 파면이 퍼질수록 약해져 잔잔해짐
        var i = 0
        for row in 0...rows {
            for col in 0...cols {
                let u = Float(col) / Float(cols)
                let v = Float(row) / Float(rows)
                let x = w * u
                let y = h * v
                let dx = x - cx
                let dy = y - cy
                let dist = hypotf(dx, dy)
                let delta = dist - front
                let env = expf(-(delta * delta) / (220 * 220)) // 넓은 밴드
                let disp = sinf(delta * 0.045) * env * amp
                var px = x
                var py = y
                if dist > 0.001 {
                    px += dx / dist * disp
                    py += dy / dist * disp
                }
                out[i] = px / w * 2 - 1       // NDC x
                out[i + 1] = 1 - py / h * 2   // NDC y (위가 +)
                out[i + 2] = u
                out[i + 3] = v
                i += floatsPerVertex
            }
        }
    }

    /// 스냅샷 텍스처를 그대로 입히는 최소 셰이더. setVertexBytes 로 넘기므로 constant 주소 공간.
    static let shaderSource = """
    #include <metal_stdlib>
    using namespace metal;

    struct WarpVertex { float2 pos; float2 uv; };
    struct WarpOut { float4 position [[position]]; float2 uv; };

    vertex WarpOut warpVertex(uint vid [[vertex_id]], constant WarpVertex *verts [[buffer(0)]]) {
        WarpOut o;
        o.position = float4(verts[vid].pos, 0.0, 1.0);
        o.uv = verts[vid].uv;
        return o;
    }

    fragment float4 warpFragment(WarpOut in [[stage_in]],
                                 texture2d<float> tex [[texture(0)]],
                                 sampler smp [[sampler(0)]]) {
        return tex.sample(smp, in.uv);
    }
    """
}

/// 스냅샷 메시 렌더러 — MTKView 의 디스플레이 링크로 매 프레임 꼭짓점만 다시 계산해 그린다.
final class WarpMeshRenderer: NSObject, MTKViewDelegate {
    private let queue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let sampler: MTLSamplerState
    private let texture: MTLTexture
    private let indexBuffer: MTLBuffer
    private let indexCount: Int
    private let originX: Float
    private let originY: Float
    private let startedAt: Date
    private var verts: [Float]

    init?(view: MTKView, snapshot: UIImage, origin: CGPoint, startedAt: Date) {
        guard let device = view.device ?? MTLCreateSystemDefaultDevice(),
              let queue = device.makeCommandQueue(),
              let cg = snapshot.cgImage
        else { return nil }
        view.device = device

        let library: MTLLibrary
        do {
            library = try device.makeLibrary(source: WarpMesh.shaderSource, options: nil)
        } catch {
            print("⚠️ Warp shader compile failed: \(error)")
            return nil
        }
        let d = MTLRenderPipelineDescriptor()
        d.vertexFunction = library.makeFunction(name: "warpVertex")
        d.fragmentFunction = library.makeFunction(name: "warpFragment")
        d.colorAttachments[0].pixelFormat = view.colorPixelFormat
        guard let pipeline = try? device.makeRenderPipelineState(descriptor: d) else {
            print("⚠️ Warp pipeline failed")
            return nil
        }

        let sd = MTLSamplerDescriptor()
        sd.minFilter = .linear
        sd.magFilter = .linear
        sd.sAddressMode = .clampToEdge
        sd.tAddressMode = .clampToEdge
        guard let sampler = device.makeSamplerState(descriptor: sd) else { return nil }

        // 스냅샷은 화면 그대로의 감마 값 — sRGB 해석 없이(.bgra8Unorm 에 그대로) 올려야 색이 안 변한다(글로브와 같은 규칙).
        let loader = MTKTextureLoader(device: device)
        let options: [MTKTextureLoader.Option: Any] = [
            .SRGB: false,
            .textureUsage: NSNumber(value: MTLTextureUsage.shaderRead.rawValue),
            .textureStorageMode: NSNumber(value: MTLStorageMode.private.rawValue),
        ]
        guard let texture = try? loader.newTexture(cgImage: cg, options: options) else {
            print("⚠️ Warp texture upload failed")
            return nil
        }

        let idx = WarpMesh.indices()
        guard let ib = device.makeBuffer(bytes: idx, length: idx.count * MemoryLayout<UInt16>.stride,
                                         options: .storageModeShared)
        else { return nil }

        self.queue = queue
        self.pipeline = pipeline
        self.sampler = sampler
        self.texture = texture
        self.indexBuffer = ib
        self.indexCount = idx.count
        self.originX = Float(origin.x)
        self.originY = Float(origin.y)
        self.startedAt = startedAt
        self.verts = [Float](repeating: 0, count: (WarpMesh.cols + 1) * (WarpMesh.rows + 1) * WarpMesh.floatsPerVertex)
        super.init()
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        let w = Float(view.drawableSize.width)
        let h = Float(view.drawableSize.height)
        guard w > 1, h > 1,
              let pass = view.currentRenderPassDescriptor,
              let drawable = view.currentDrawable,
              let cmd = queue.makeCommandBuffer(),
              let enc = cmd.makeRenderCommandEncoder(descriptor: pass)
        else { return }

        let p = Float(WarpTiming.progress(since: startedAt))
        WarpMesh.vertices(w: w, h: h, originX: originX, originY: originY, p: p, into: &verts)

        enc.setRenderPipelineState(pipeline)
        verts.withUnsafeBytes { raw in
            // 225 꼭짓점 × 16B = 3.6KB — setVertexBytes 한도(4KB) 안.
            enc.setVertexBytes(raw.baseAddress!, length: raw.count, index: 0)
        }
        enc.setFragmentTexture(texture, index: 0)
        enc.setFragmentSamplerState(sampler, index: 0)
        enc.drawIndexedPrimitives(type: .triangle, indexCount: indexCount, indexType: .uint16,
                                  indexBuffer: indexBuffer, indexBufferOffset: 0)
        enc.endEncoding()
        cmd.present(drawable)
        cmd.commit()
    }
}

/// 지도 바로 위에 까는 왜곡된 스냅샷 — 투명 배경이라 메시 가장자리가 밀려 생기는 틈으로는 라이브 지도가 비친다
/// (Android 도 스냅샷 캔버스 아래가 라이브 지도). 터치는 받지 않는다.
struct DiaryWarpMeshView: UIViewRepresentable {
    let snapshot: UIImage
    let origin: CGPoint
    let startedAt: Date

    final class Coordinator {
        var renderer: WarpMeshRenderer?
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView(frame: .zero, device: MTLCreateSystemDefaultDevice())
        view.colorPixelFormat = .bgra8Unorm
        view.framebufferOnly = true
        view.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
        view.isOpaque = false
        view.layer.isOpaque = false
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
        view.preferredFramesPerSecond = 60
        view.enableSetNeedsDisplay = false
        view.isPaused = false
        if let r = WarpMeshRenderer(view: view, snapshot: snapshot, origin: origin, startedAt: startedAt) {
            view.delegate = r                 // MTKView.delegate 는 weak — Coordinator 가 소유한다.
            context.coordinator.renderer = r
        } else {
            view.isPaused = true              // 실패 시 아무것도 그리지 않음(투명) — 링 연출만 남는다.
        }
        return view
    }

    func updateUIView(_ uiView: MTKView, context: Context) {}

    static func dismantleUIView(_ uiView: MTKView, coordinator: Coordinator) {
        uiView.isPaused = true
        uiView.delegate = nil
        coordinator.renderer = nil
    }
}
