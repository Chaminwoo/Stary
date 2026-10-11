import AVFoundation
import CoreVideo
import Metal
import UIKit

/// 공유 영상 만들기 — 앱의 3D 글로브(`GlobeRenderer`, 스크립트 모드)를 **화면 없이** 한 프레임씩 텍스처에 그리고,
/// 그 위에 빛·글을 CoreGraphics 로 얹어 H.264(mp4)로 묶는다. 실시간 녹화가 아니라 정해진 대본(`ShareVideoSpec`)대로
/// 그리므로 기기 성능과 상관없이 같은 영상이 나온다. Android `ShareVideoRenderer.kt`(GL + MediaCodec) 대응.
///
/// 한 프레임 = ① 글로브(카메라·시각을 대본대로, Metal — 메인 액터) → ② 픽셀 버퍼로 복사 → ③ 고리가 지나가는 자리의 일그러짐
/// → ④ 빛(번짐·고리·빛기둥, 더하기 합성) → ⑤ 주인공 별빛·글 블록(덮어 그리기) → ⑥ 인코더에 추가.
/// ②~⑥은 백그라운드에서 돈다(프레임 사이사이 화면이 멈추지 않게).
enum ShareVideoRenderer {
    private static let width = 1080
    private static let height = 1920

    /// - Returns: 끝까지 만들었으면 true. 취소(호출한 Task 취소)·실패면 false — 만들던 파일은 지운다.
    @MainActor
    static func render(diary: Diary, sky: [Diary], untitled: String, cta: String, to url: URL,
                       onProgress: @escaping (Float) -> Void) async -> Bool {
        guard let device = MTLCreateSystemDefaultDevice(),
              let globe = GlobeRenderer(device: device, colorPixelFormat: .bgra8Unorm, depthPixelFormat: .depth32Float),
              let overlay = ShareVideoOverlay(diary: diary, untitled: untitled, cta: cta)
        else { return false }

        // 그릴 곳 — 색은 CPU 에서 읽어야 하므로 shared, 깊이는 GPU 전용.
        let colorDesc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .bgra8Unorm, width: width, height: height, mipmapped: false)
        colorDesc.usage = [.renderTarget, .shaderRead]
        colorDesc.storageMode = .shared
        let depthDesc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .depth32Float, width: width, height: height, mipmapped: false)
        depthDesc.usage = [.renderTarget]
        depthDesc.storageMode = .private
        guard let color = device.makeTexture(descriptor: colorDesc), let depth = device.makeTexture(descriptor: depthDesc) else { return false }
        let pass = MTLRenderPassDescriptor()
        pass.colorAttachments[0].texture = color
        pass.colorAttachments[0].loadAction = .clear
        pass.colorAttachments[0].clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)
        pass.colorAttachments[0].storeAction = .store
        pass.depthAttachment.texture = depth
        pass.depthAttachment.loadAction = .clear
        pass.depthAttachment.clearDepth = 1
        pass.depthAttachment.storeAction = .dontCare

        // 지구 위 불빛 = 내 다른 별 + 이 별.
        globe.setDiariesNow(sky.filter { $0.id != diary.id } + [diary])
        var script = GlobeRenderer.Script()
        script.ndcShiftY = ShareVideoSpec.ndcShiftY
        script.sunDir = ShareVideoSpec.sunDir(lat: diary.latitude, lng: diary.longitude)

        try? FileManager.default.removeItem(at: url)
        guard let sink = FrameSink(url: url, width: width, height: height, texture: color, overlay: overlay,
                                   lat: diary.latitude, lng: diary.longitude)
        else { return false }

        for i in 0..<ShareVideoSpec.frameCount {
            if Task.isCancelled { sink.cancel(); return false }
            let t = Float(i) / Float(ShareVideoSpec.fps)
            let pose = ShareVideoSpec.pose(t, lat: diary.latitude, lng: diary.longitude)
            globe.yawDeg = pose.yawDeg
            globe.pitchDeg = pose.pitchDeg
            globe.camDist = pose.dist
            script.timeSec = t
            script.fade = min(max(t / ShareVideoSpec.fadeIn, 0), 1)
            guard globe.renderOffscreen(pass: pass, width: width, height: height, script: script) else { sink.cancel(); return false }

            // 합성·인코딩은 메인 스레드 밖에서 — 끝날 때까지 기다린 뒤 다음 프레임을 그린다(같은 텍스처를 다시 쓰므로).
            let ok = await Task.detached(priority: .userInitiated) { sink.append(frame: i, t: t, pose: pose) }.value
            guard ok else { sink.cancel(); return false }
            onProgress(Float(i + 1) / Float(ShareVideoSpec.frameCount))
        }
        return await sink.finish()
    }
}

/// 글로브 텍스처 → 픽셀 버퍼 복사 + 빛·글 합성 + mp4 쓰기. 한 번에 한 프레임만(순서대로) 불린다.
private final class FrameSink: @unchecked Sendable {
    private let writer: AVAssetWriter
    private let input: AVAssetWriterInput
    private let adaptor: AVAssetWriterInputPixelBufferAdaptor
    private let texture: MTLTexture
    private let overlay: ShareVideoOverlay
    private let lat: Double
    private let lng: Double
    private let width: Int
    private let height: Int
    private let url: URL

    init?(url: URL, width: Int, height: Int, texture: MTLTexture, overlay: ShareVideoOverlay, lat: Double, lng: Double) {
        guard let writer = try? AVAssetWriter(outputURL: url, fileType: .mp4) else { return nil }
        let compression: [String: Any] = [
            AVVideoAverageBitRateKey: 8_000_000,
            AVVideoMaxKeyFrameIntervalKey: 30,
            AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
        ]
        let settings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: width,
            AVVideoHeightKey: height,
            AVVideoCompressionPropertiesKey: compression,
        ]
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: settings)
        input.expectsMediaDataInRealTime = false
        let attributes: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA),
            kCVPixelBufferWidthKey as String: width,
            kCVPixelBufferHeightKey as String: height,
        ]
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: attributes)
        guard writer.canAdd(input) else { return nil }
        writer.add(input)
        guard writer.startWriting() else { return nil }
        writer.startSession(atSourceTime: .zero)

        self.writer = writer
        self.input = input
        self.adaptor = adaptor
        self.texture = texture
        self.overlay = overlay
        self.lat = lat
        self.lng = lng
        self.width = width
        self.height = height
        self.url = url
    }

    /// 지금 텍스처에 그려진 글로브 한 프레임을 가져와 빛·글을 얹고 인코더에 넘긴다(백그라운드 스레드에서 부른다).
    func append(frame: Int, t: Float, pose: ShareVideoSpec.Pose) -> Bool {
        // 인코더가 밀려 있으면 잠깐 기다린다(실시간 입력이 아니므로 보통 바로 준비된다).
        var waited = 0
        while !input.isReadyForMoreMediaData {
            if writer.status != .writing || waited > 2500 { return false } // 최대 5초
            Thread.sleep(forTimeInterval: 0.002)
            waited += 1
        }
        guard let pool = adaptor.pixelBufferPool else { return false }
        var made: CVPixelBuffer?
        CVPixelBufferPoolCreatePixelBuffer(nil, pool, &made)
        guard let buffer = made else { return false }

        CVPixelBufferLockBaseAddress(buffer, [])
        if let base = CVPixelBufferGetBaseAddress(buffer) {
            let rowBytes = CVPixelBufferGetBytesPerRow(buffer)
            texture.getBytes(base, bytesPerRow: rowBytes, from: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0)
            let star = ShareVideoSpec.project(lat: lat, lng: lng, radius: ShareVideoSpec.starRadius, pose: pose)
            let ring: Float = star.front > 0 ? ShareVideoSpec.ringPhase(t) : -1
            if ring >= 0 {
                applyRipple(base, rowBytes: rowBytes, sx: star.x, sy: star.y,
                            radius: ShareVideoSpec.ringRadius(ring), amp: ShareVideoSpec.rippleAmp(ring))
            }
            // BGRA(알파 무시) 비트맵 컨텍스트 — 메모리 첫 행이 그림의 맨 위, 좌표계는 왼쪽 아래 원점(y 위쪽).
            if let cg = CGContext(data: base, width: width, height: height, bitsPerComponent: 8, bytesPerRow: rowBytes,
                                  space: CGColorSpaceCreateDeviceRGB(),
                                  bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) {
                drawOverlay(cg, t: t, star: star, ring: ring)
            }
        }
        CVPixelBufferUnlockBaseAddress(buffer, [])
        return adaptor.append(buffer, withPresentationTime: CMTime(value: CMTimeValue(frame), timescale: ShareVideoSpec.fps))
    }

    func finish() async -> Bool {
        input.markAsFinished()
        await writer.finishWriting()
        return writer.status == .completed
    }

    func cancel() {
        input.markAsFinished()
        writer.cancelWriting()
        try? FileManager.default.removeItem(at: url)
    }

    /// 고리가 지나가는 자리를 살짝 일그러뜨린다 — 별 주위 정사각 영역을 따로 복사해 두고, 고리 근처 픽셀만
    /// "별 쪽/바깥쪽으로 밀린 자리"의 색을 읽어 와 덮어쓴다(`ShareVideoSpec.rippleShift`, 이웃 4픽셀 보간).
    /// 띠 밖은 건드리지 않는다. Android 는 같은 식을 셰이더로 그린다(`ShareVideoRenderer.Ripple`).
    /// base = BGRA 픽셀 버퍼(첫 행이 그림의 맨 위), sx/sy/radius/amp 는 디자인 px(= 실제 픽셀).
    private func applyRipple(_ base: UnsafeMutableRawPointer, rowBytes: Int, sx: Float, sy: Float, radius: Float, amp: Float) {
        guard amp >= 0.05 else { return }
        // 영역이 화면 밖으로 나가지 않게 줄인다.
        let half = min(ShareVideoSpec.rippleHalf, min(sx, Float(width) - sx), min(sy, Float(height) - sy))
        guard half >= 16 else { return }
        let side = Int(half * 2)
        let x0 = Int(sx - half), y0 = Int(sy - half)
        guard side >= 8, x0 >= 0, y0 >= 0, x0 + side <= width, y0 + side <= height else { return }

        let dst = base.assumingMemoryBound(to: UInt8.self)
        let srcRow = side * 4
        let src = UnsafeMutablePointer<UInt8>.allocate(capacity: side * srcRow)
        defer { src.deallocate() }
        for j in 0..<side {
            memcpy(src + j * srcRow, dst + (y0 + j) * rowBytes + x0 * 4, srcRow)
        }

        let squash = ShareVideoSpec.ringSquash
        let reach = ShareVideoSpec.rippleWidth * 3      // 이보다 멀면 밀림이 0 에 가깝다
        let maxIndex = Float(side - 1)
        for j in 0..<side {
            let py = Float(y0 + j) + 0.5 - sy            // 별 기준 위치(픽셀 중심)
            let qy = py / squash                         // 고리는 세로로 납작한 타원 → 펴서 거리를 잰다
            let out = dst + (y0 + j) * rowBytes + x0 * 4
            for i in 0..<side {
                let px = Float(x0 + i) + 0.5 - sx
                let d = (px * px + qy * qy).squareRoot()
                if d < 0.001 || abs(d - radius) > reach { continue }
                let shift = ShareVideoSpec.rippleShift(dist: d, radius: radius, amp: amp)
                // 읽어 올 자리(영역 안 좌표, 픽셀 중심 기준)
                let fx = min(max(px - px / d * shift + sx - Float(x0) - 0.5, 0), maxIndex)
                let fy = min(max(py - qy / d * squash * shift + sy - Float(y0) - 0.5, 0), maxIndex)
                let ix = min(Int(fx), side - 2), iy = min(Int(fy), side - 2)
                let tx = fx - Float(ix), ty = fy - Float(iy)
                let p00 = src + iy * srcRow + ix * 4
                let p10 = p00 + 4
                let p01 = p00 + srcRow
                let p11 = p01 + 4
                let o = out + i * 4
                for c in 0..<3 {                         // B, G, R (알파 바이트는 그대로)
                    let top = Float(p00[c]) + (Float(p10[c]) - Float(p00[c])) * tx
                    let bottom = Float(p01[c]) + (Float(p11[c]) - Float(p01[c])) * tx
                    o[c] = UInt8(top + (bottom - top) * ty + 0.5)
                }
            }
        }
    }

    /// 글로브 위에 얹는 빛·글 — 순서가 곧 겹치는 순서다. Android `ShareVideoRenderer.drawOverlay` 와 같은 순서·같은 식.
    private func drawOverlay(_ cg: CGContext, t: Float, star: (x: Float, y: Float, front: Float), ring: Float) {
        let fullH = CGFloat(height)
        // 디자인 px(왼쪽 위 원점)의 사각형에 그림 한 장. additive = 더하기 합성(빛), 아니면 그대로 얹기.
        func draw(_ image: CGImage, x: Float, y: Float, w: Float, h: Float, alpha: Float, additive: Bool) {
            guard alpha > 0, w > 0, h > 0 else { return }
            cg.setBlendMode(additive ? .plusLighter : .normal)
            cg.setAlpha(CGFloat(min(alpha, 1)))
            cg.draw(image, in: CGRect(x: CGFloat(x), y: fullH - CGFloat(y) - CGFloat(h), width: CGFloat(w), height: CGFloat(h)))
        }
        func drawBlock(_ block: ShareVideoOverlay.Block, appear: Float) {
            let y = Float(block.top) + (1 - appear) * ShareVideoSpec.textRise
            draw(block.image, x: 0, y: y, w: Float(block.image.width), h: Float(block.image.height), alpha: appear, additive: false)
        }

        let sx = star.x, sy = star.y

        // 별이 켜진다 — 살짝 넘쳤다 자리 잡고, 그 뒤로는 천천히 숨 쉰다.
        let on = ShareVideoSpec.progress(t, at: ShareVideoSpec.starAt, dur: ShareVideoSpec.starDur)
        if on > 0, star.front > 0 {
            let lit = ShareVideoSpec.progress(t, at: ShareVideoSpec.starAt, dur: 0.3)
            let breathe = 1 + 0.06 * sin(t * 5.0)
            let glowR = ShareVideoSpec.glowHalf * ShareVideoSpec.easeOutCubic(on) * breathe
            draw(overlay.glow, x: sx - glowR, y: sy - glowR, w: glowR * 2, h: glowR * 2, alpha: lit, additive: true)

            // 고리 — 3초마다 하나씩 퍼진다(그 자리의 일그러짐은 append 가 먼저 계산해 두었다).
            if ring >= 0 {
                let r = ShareVideoSpec.ringRadius(ring)
                let ry = r * ShareVideoSpec.ringSquash
                draw(overlay.ring, x: sx - r, y: sy - ry, w: r * 2, h: ry * 2, alpha: ShareVideoSpec.ringAlpha(ring), additive: true)
            }

            // 빛기둥 — 별에서 위로 자라난다(그림을 길이에 맞춰 늘리므로 끝은 항상 부드럽다).
            let rise = ShareVideoSpec.easeOutCubic(ShareVideoSpec.progress(t, at: ShareVideoSpec.beamAt, dur: ShareVideoSpec.beamDur))
            if rise > 0 {
                let len = max(sy - ShareVideoSpec.beamTop, 0) * rise
                let shimmer = 0.92 + 0.08 * sin(t * 7.0)
                draw(overlay.beam, x: sx - ShareVideoSpec.beamHalfW, y: sy - len, w: ShareVideoSpec.beamHalfW * 2, h: len,
                     alpha: shimmer, additive: true)
            }

            let half = ShareVideoSpec.starHalf * ShareVideoSpec.easeOutBack(on) * breathe
            draw(overlay.hero, x: sx - half, y: sy - half, w: half * 2, h: half * 2, alpha: lit, additive: false)
        }

        // 글 — 아래에서 살짝 떠오르며 나타난다(위쪽 먼저, 아래쪽이 뒤따른다).
        let topIn = ShareVideoSpec.easeOutCubic(ShareVideoSpec.progress(t, at: ShareVideoSpec.titleAt, dur: ShareVideoSpec.textDur))
        if topIn > 0 { drawBlock(overlay.topBlock, appear: topIn) }
        let bottomIn = ShareVideoSpec.easeOutCubic(ShareVideoSpec.progress(t, at: ShareVideoSpec.ctaAt, dur: ShareVideoSpec.textDur))
        if bottomIn > 0 { drawBlock(overlay.bottomBlock, appear: bottomIn) }
    }
}
