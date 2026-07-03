import SceneKit
import SwiftUI
import UIKit

/// 3D 행성(지구) 화면 — 지도에서 줌을 최소로 빼면 나타나는 전체화면 오버레이.
/// (Android `feature/globe/GlobeScreen+GlobeRenderer` 패리티 — iOS 는 SceneKit 구현.)
///
/// - 드래그: 행성 회전(관성), 3초 무입력 시 느린 자동 회전.
/// - 핀치: 카메라 줌. 최소 거리 밑으로 당기면 지금 정면 지점의 지도(줌인)로 복귀.
///
/// 성능: 씬/텍스처는 이 뷰가 나타날 때만 생성 — 지도 화면 평상시 비용 0.
/// 시티 라이트(노란 점광)와 라이트맵은 노드가 아니라 발광(emission) 텍스처에 베이크해
/// 노드 수를 별 플레어(좋아요 100+)만으로 억제한다.
struct GlobeScreen: View {
    @ObservedObject private var locale = LocaleManager.shared
    let diaries: [Diary]
    let startLat: Double
    let startLng: Double
    let onRequestExit: (_ lat: Double, _ lng: Double) -> Void

    @State private var hintVisible = true

    var body: some View {
        ZStack(alignment: .bottom) {
            GlobeSceneView(diaries: diaries, startLat: startLat, startLng: startLng, onRequestExit: onRequestExit)
                .ignoresSafeArea()
                .background(Color.black)

            // 조작 힌트 — 잠깐 보였다 사라짐 (Android globe_hint 패리티)
            Text(locale.t(.globeHint))
                .font(.footnote)
                .foregroundStyle(.white.opacity(0.75))
                .padding(.horizontal, 16).padding(.vertical, 8)
                .background(Color.black.opacity(0.35), in: Capsule())
                .padding(.bottom, 28)
                .opacity(hintVisible ? 1 : 0)
                .animation(.easeInOut(duration: 0.7), value: hintVisible)
        }
        .task {
            try? await Task.sleep(nanoseconds: 4_200_000_000)
            hintVisible = false
        }
    }
}

/// SceneKit 씬 래퍼. 카메라/관성/자동회전 시뮬레이션은 렌더 델리게이트에서 진행.
private struct GlobeSceneView: UIViewRepresentable {
    let diaries: [Diary]
    let startLat: Double
    let startLng: Double
    let onRequestExit: (_ lat: Double, _ lng: Double) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(startLat: startLat, startLng: startLng, onRequestExit: onRequestExit)
    }

    func makeUIView(context: Context) -> SCNView {
        let view = SCNView(frame: .zero)
        view.backgroundColor = .black
        view.antialiasingMode = .multisampling2X
        view.rendersContinuously = true            // 자동 회전/트윙클 상시 진행
        view.scene = context.coordinator.buildScene(diaries: diaries)
        view.pointOfView = context.coordinator.cameraNode
        view.delegate = context.coordinator

        let pan = UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.onPan(_:)))
        let pinch = UIPinchGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.onPinch(_:)))
        view.addGestureRecognizer(pan)
        view.addGestureRecognizer(pinch)
        return view
    }

    func updateUIView(_ uiView: SCNView, context: Context) {}

    // MARK: - Coordinator (씬 구성 + 제스처 + 프레임 시뮬레이션)

    final class Coordinator: NSObject, SCNSceneRendererDelegate {
        // Android GlobeRenderer 상수 패리티
        static let enterDist: Float = 4.6    // 진입 시작 거리(돌리-인 출발점)
        static let idleDist: Float = 3.25    // 기본 관람 거리
        static let minDist: Float = 2.10     // 이 밑으로 핀치-인 → 지도 복귀
        static let maxDist: Float = 6.0
        static let flareMinLikes = 100       // 이 이상 좋아요 → 별 플레어(노드), 미만 → 베이크된 점광
        static let flareMax = 500

        let onRequestExit: (_ lat: Double, _ lng: Double) -> Void

        // 카메라/인터랙션 상태 (제스처: 메인 스레드 쓰기 / 렌더 스레드 읽기)
        var yawDeg: Float = 0            // 모델 Y 회전(= -경도)
        var pitchDeg: Float = 15         // 모델 X 기울임(= 위도), ±75 클램프
        var camDist: Float = Coordinator.enterDist
        var yawVelDeg: Float = 0         // 드래그 관성(도/초)
        var pitchVelDeg: Float = 0
        var lastInteraction: TimeInterval = 0
        var dollyTarget: Float = Coordinator.idleDist
        var lastFrameTime: TimeInterval = 0
        var exitFired = false
        private var lastPinchScale: CGFloat = 1

        let cameraNode = SCNNode()
        /// 지구+별+트레일+배경 별밭을 담는 회전 컨테이너(Android uModel 대응).
        let containerNode = SCNNode()

        init(startLat: Double, startLng: Double, onRequestExit: @escaping (_ lat: Double, _ lng: Double) -> Void) {
            self.onRequestExit = onRequestExit
            self.pitchDeg = Float(min(max(startLat, -75), 75))
            self.yawDeg = Float(-startLng)
            super.init()
        }

        /// 지금 화면 정면에 보이는 지점 (위도, 경도) — 지도 복귀 좌표.
        func facingLatLng() -> (Double, Double) {
            let lat = Double(pitchDeg)
            var lng = Double(-yawDeg).truncatingRemainder(dividingBy: 360)
            if lng > 180 { lng -= 360 }
            if lng < -180 { lng += 360 }
            return (min(max(lat, -85), 85), lng)
        }

        private func fireExit() {
            guard !exitFired else { return }
            exitFired = true
            let (lat, lng) = facingLatLng()
            DispatchQueue.main.async { self.onRequestExit(lat, lng) }
        }

        // MARK: 제스처

        @objc func onPan(_ g: UIPanGestureRecognizer) {
            guard let v = g.view else { return }
            lastInteraction = CACurrentMediaTime()
            // 드래그 감도: 멀수록(줌아웃) 크게 회전 (Android degPerPx 패리티)
            let degPerPx = 0.075 * ((camDist - 1) / 2.2)
            switch g.state {
            case .changed:
                let t = g.translation(in: v)
                g.setTranslation(.zero, in: v)
                yawDeg += Float(t.x) * degPerPx
                pitchDeg = min(max(pitchDeg + Float(t.y) * degPerPx, -75), 75)
                yawVelDeg = 0
                pitchVelDeg = 0
            case .ended:
                let vel = g.velocity(in: v) // pt/s
                yawVelDeg = Float(vel.x) * degPerPx * 0.55
                pitchVelDeg = Float(vel.y) * degPerPx * 0.55
            default: break
            }
            applyRotation()
        }

        @objc func onPinch(_ g: UIPinchGestureRecognizer) {
            lastInteraction = CACurrentMediaTime()
            switch g.state {
            case .began:
                lastPinchScale = g.scale
            case .changed:
                let factor = Float(g.scale / lastPinchScale)
                lastPinchScale = g.scale
                let next = min(max(camDist / factor, Coordinator.minDist - 0.05), Coordinator.maxDist)
                camDist = next
                dollyTarget = next
                // 핀치-인으로 최소 거리 도달 → 그 지점 지도로 줌인 복귀
                if next <= Coordinator.minDist && factor > 1 { fireExit() }
            default: break
            }
        }

        // MARK: 프레임 시뮬레이션(렌더 스레드) — 관성/자동회전/돌리-인

        func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
            let dt = Float(lastFrameTime == 0 ? 0.016 : min(max(time - lastFrameTime, 0.001), 0.05))
            lastFrameTime = time
            let sinceTouch = CACurrentMediaTime() - lastInteraction

            // 진입 돌리-인(사용자 핀치가 없을 때만 부드럽게 목표 거리로)
            if sinceTouch > 0.25 {
                camDist += (dollyTarget - camDist) * min(1, dt * 2.0)
            }
            // 드래그 관성
            if sinceTouch > 0.06 {
                yawDeg += yawVelDeg * dt
                pitchDeg = min(max(pitchDeg + pitchVelDeg * dt, -75), 75)
                let decay = exp(-2.6 * dt)
                yawVelDeg *= decay
                pitchVelDeg *= decay
            }
            // 자동 느린 회전(3초 이상 무입력)
            if sinceTouch > 3 { yawDeg += dt * 1.7 }

            applyRotation()
            cameraNode.position = SCNVector3(0, 0, camDist)
        }

        /// SceneKit euler 는 (roll→yaw→pitch) 순 적용 = Rx(pitch)·Ry(yaw) — Android uModel 과 동일.
        private func applyRotation() {
            containerNode.eulerAngles = SCNVector3(
                pitchDeg * .pi / 180,
                yawDeg * .pi / 180,
                0
            )
        }

        // MARK: 씬 구성

        func buildScene(diaries: [Diary]) -> SCNScene {
            let scene = SCNScene()
            scene.background.contents = UIColor.black

            let camera = SCNCamera()
            camera.fieldOfView = 42
            camera.zNear = 0.1
            camera.zFar = 100
            cameraNode.camera = camera
            cameraNode.position = SCNVector3(0, 0, camDist)
            scene.rootNode.addChildNode(cameraNode)

            let valid = diaries.filter { $0.latitude != 0 && $0.longitude != 0 }

            containerNode.addChildNode(GlobeBuilder.earthNode(diaries: valid))
            containerNode.addChildNode(GlobeBuilder.starfieldNode())
            for node in GlobeBuilder.trailNodes() { containerNode.addChildNode(node) }
            for node in GlobeBuilder.flareNodes(diaries: valid) { containerNode.addChildNode(node) }

            // 진입 페이드(장면 밝기 0→1, Android fade 패리티)
            containerNode.opacity = 0
            containerNode.runAction(.fadeIn(duration: 1.1))
            applyRotation()
            scene.rootNode.addChildNode(containerNode)
            return scene
        }
    }
}

/// 씬 지오메트리/텍스처 빌더 — 전부 순수 함수(진입 시 1회 실행).
private enum GlobeBuilder {

    // MARK: 지구

    /// 지구 노드: 수동 UV 구체 메쉬(경도 -180 → u=0, [latLngToXyz] 와 동일 규약)에
    /// "밤 지구" 디퓨즈 + "다이어리 근처 발광" 이미션 텍스처.
    static func earthNode(diaries: [Diary]) -> SCNNode {
        let geometry = sphereGeometry(radius: 1, stacks: 64, slices: 128)
        let material = SCNMaterial()
        material.lightingModel = .constant
        let (night, lit) = earthTextures(diaries: diaries)
        material.diffuse.contents = night
        material.emission.contents = lit
        geometry.materials = [material]
        return SCNNode(geometry: geometry)
    }

    /// 위경도 → 단위구 좌표(반지름 radius). Android latLngToXyz 와 동일(λ=0 이 +Z).
    static func latLngToXyz(lat: Double, lng: Double, radius: Float) -> SCNVector3 {
        let phi = lat * .pi / 180
        let lam = lng * .pi / 180
        return SCNVector3(
            Float(cos(phi) * sin(lam)) * radius,
            Float(sin(phi)) * radius,
            Float(cos(phi) * cos(lam)) * radius
        )
    }

    /// UV 를 직접 제어하는 구체 메쉬(u: λ=-180→180, v: 북극→남극) — 텍스처/좌표 정합 보장.
    private static func sphereGeometry(radius: Float, stacks: Int, slices: Int) -> SCNGeometry {
        var vertices: [SCNVector3] = []
        var uvs: [CGPoint] = []
        vertices.reserveCapacity((stacks + 1) * (slices + 1))
        for i in 0...stacks {
            let v = Double(i) / Double(stacks)
            let phi = (90 - 180 * v) * .pi / 180 // 북극 → 남극
            for j in 0...slices {
                let u = Double(j) / Double(slices)
                let lam = (-180 + 360 * u) * .pi / 180
                vertices.append(SCNVector3(
                    Float(cos(phi) * sin(lam)) * radius,
                    Float(sin(phi)) * radius,
                    Float(cos(phi) * cos(lam)) * radius
                ))
                uvs.append(CGPoint(x: u, y: v))
            }
        }
        var indices: [Int32] = []
        indices.reserveCapacity(stacks * slices * 6)
        for i in 0..<stacks {
            for j in 0..<slices {
                let a = Int32(i * (slices + 1) + j)
                let b = a + Int32(slices + 1)
                indices.append(contentsOf: [a, b, a + 1, a + 1, b, b + 1])
            }
        }
        return SCNGeometry(
            sources: [SCNGeometrySource(vertices: vertices), SCNGeometrySource(textureCoordinates: uvs)],
            elements: [SCNGeometryElement(indices: indices, primitiveType: .triangles)]
        )
    }

    /// 밤 지구 디퓨즈 + 다이어리 라이트 이미션 생성.
    /// Android: 지구를 크게 감광하고, 다이어리 위치 소프트 스플랫(좋아요 100+ 크게)으로만 밝힘
    /// + 노란 점광(도시 야경)은 여기 이미션에 함께 베이크.
    private static func earthTextures(diaries: [Diary]) -> (night: UIImage, lit: UIImage) {
        let base = loadEarthImage()
        let w = 2048, h = 1024
        let size = CGSize(width: w, height: h)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1

        // 1) 밤 지구: 원본을 크게 감광 + 살짝 푸른 톤(EARTH_FS night 항 근사)
        let night = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            base.draw(in: CGRect(origin: .zero, size: size))
            UIColor(red: 0, green: 0.01, blue: 0.04, alpha: 0.92).setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
        }

        // 2) 라이트맵(마스크): 투명 배경 + 다이어리 위치 알파 스플랫(destinationIn 용).
        //    날짜변경선(±180°)은 양쪽 중복 드로우.
        let mask = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            let cg = ctx.cgContext
            let colors = [UIColor.white.cgColor, UIColor.white.withAlphaComponent(0).cgColor] as CFArray
            guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) else { return }
            for d in diaries.prefix(4000) {
                let big = d.likeCount >= GlobeSceneView.Coordinator.flareMinLikes
                let r = CGFloat(big ? 60 : 30) // 2048 폭 기준(Android 1024 폭의 30/15 스케일 일치)
                let alpha = CGFloat(big ? 0.82 : 0.47)
                let cx = CGFloat((d.longitude + 180) / 360) * CGFloat(w)
                let cy = CGFloat((90 - d.latitude) / 180) * CGFloat(h)
                func splat(_ x: CGFloat) {
                    cg.saveGState()
                    cg.setAlpha(alpha)
                    cg.drawRadialGradient(
                        gradient,
                        startCenter: CGPoint(x: x, y: cy), startRadius: 0,
                        endCenter: CGPoint(x: x, y: cy), endRadius: r,
                        options: []
                    )
                    cg.restoreGState()
                }
                splat(cx)
                if cx < r { splat(cx + CGFloat(w)) }
                if cx > CGFloat(w) - r { splat(cx - CGFloat(w)) }
            }
        }

        // 3) 이미션 = 지구 × 라이트맵(따뜻한 톤) + 노란 점광 코어
        let lit = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            let cg = ctx.cgContext
            base.draw(in: CGRect(origin: .zero, size: size))
            // 따뜻한 톤(EARTH_FS lit 항 1.10/1.00/0.82 근사) — multiply
            UIColor(red: 1.0, green: 0.91, blue: 0.75, alpha: 1).setFill()
            cg.setBlendMode(.multiply)
            cg.fill(CGRect(origin: .zero, size: size))
            // 라이트맵 마스크 적용(밝힌 곳만 남김)
            cg.setBlendMode(.destinationIn)
            mask.draw(in: CGRect(origin: .zero, size: size), blendMode: .destinationIn, alpha: 1)
            // 노란 점광(좋아요 100 미만 다이어리 1:1) — 도시 야경 코어
            cg.setBlendMode(.plusLighter)
            for d in diaries.prefix(4000) where d.likeCount < GlobeSceneView.Coordinator.flareMinLikes {
                let cx = CGFloat((d.longitude + 180) / 360) * CGFloat(w)
                let cy = CGFloat((90 - d.latitude) / 180) * CGFloat(h)
                UIColor(red: 1.0, green: 0.76, blue: 0.36, alpha: 0.55).setFill()
                cg.fillEllipse(in: CGRect(x: cx - 3, y: cy - 3, width: 6, height: 6))
            }
        }
        return (night, lit)
    }

    private static func loadEarthImage() -> UIImage {
        if let path = Bundle.main.path(forResource: "earth_blue_marble", ofType: "jpg"),
           let img = UIImage(contentsOfFile: path) {
            return img
        }
        // 리소스 누락 시에도 크래시 없이 어두운 남색 구.
        return UIGraphicsImageRenderer(size: CGSize(width: 4, height: 2)).image { ctx in
            UIColor(red: 0.02, green: 0.05, blue: 0.12, alpha: 1).setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 4, height: 2))
        }
    }

    // MARK: 별 플레어(좋아요 100+)

    /// 좋아요 100+ 다이어리 → 구 표면 살짝 바깥의 컬러 별 플레어 빌보드(+트윙클).
    static func flareNodes(diaries: [Diary]) -> [SCNNode] {
        let popular = diaries.filter { $0.likeCount >= GlobeSceneView.Coordinator.flareMinLikes }
            .sorted { $0.likeCount > $1.likeCount }
            .prefix(GlobeSceneView.Coordinator.flareMax)
        guard !popular.isEmpty else { return [] }
        let flareImage = makeFlareImage()

        return popular.map { d in
            let boost = Float(min(d.likeCount, 1000)) / 1000
            let size = CGFloat(0.055 + 0.045 * boost) * 2.4 // 텍스처 여백 감안한 플레인 배율
            let plane = SCNPlane(width: size, height: size)
            let material = SCNMaterial()
            material.lightingModel = .constant
            material.diffuse.contents = flareImage
            material.blendMode = .add
            material.writesToDepthBuffer = false
            // 정점색 tint 대응 — 별 색 × 감광 밝기
            let bright = CGFloat(0.60 + 0.15 * boost)
            material.multiply.contents = UIColor(StarStyle.color(d.starColor)).withBrightnessScaled(by: bright)
            plane.materials = [material]

            let node = SCNNode(geometry: plane)
            node.position = latLngToXyz(lat: d.latitude, lng: d.longitude, radius: 1.045)
            let billboard = SCNBillboardConstraint()
            billboard.freeAxes = .all
            node.constraints = [billboard]

            // 트윙클(위상은 좌표 기반 결정적 — Android phase 패리티)
            let phase = ((d.latitude * 7 + d.longitude * 13).truncatingRemainder(dividingBy: 1) + 1)
                .truncatingRemainder(dividingBy: 1)
            let period = 1.4 + phase * 1.6
            node.opacity = 0.85
            node.runAction(.repeatForever(.sequence([
                .fadeOpacity(to: 0.55, duration: period / 2),
                .fadeOpacity(to: 1.0, duration: period / 2),
            ])))
            return node
        }
    }

    /// 4-포인트 별 플레어 텍스처(흰색 — multiply 로 tint). Android makeFlareBitmap 패리티.
    private static func makeFlareImage() -> UIImage {
        let s: CGFloat = 128
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: s, height: s), format: format).image { ctx in
            let cg = ctx.cgContext
            let c = s / 2
            let space = CGColorSpaceCreateDeviceRGB()
            // 중심 코어
            if let core = CGGradient(
                colorsSpace: space,
                colors: [UIColor.white.cgColor,
                         UIColor.white.withAlphaComponent(0.36).cgColor,
                         UIColor.white.withAlphaComponent(0).cgColor] as CFArray,
                locations: [0, 0.22, 1]
            ) {
                cg.drawRadialGradient(core, startCenter: CGPoint(x: c, y: c), startRadius: 0,
                                      endCenter: CGPoint(x: c, y: c), endRadius: s * 0.26, options: [])
            }
            // 4방향 광선(수직/수평 길게 + 대각 짧게) — 가운데가 불룩한 렌즈꼴
            func ray(lenFrac: CGFloat, thickFrac: CGFloat, angleDeg: CGFloat) {
                cg.saveGState()
                cg.translateBy(x: c, y: c)
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
                if let g = CGGradient(
                    colorsSpace: space,
                    colors: [UIColor.white.withAlphaComponent(0).cgColor,
                             UIColor.white.withAlphaComponent(0.7).cgColor,
                             UIColor.white.withAlphaComponent(0).cgColor] as CFArray,
                    locations: [0, 0.5, 1]
                ) {
                    cg.drawLinearGradient(g, start: CGPoint(x: -half, y: 0), end: CGPoint(x: half, y: 0), options: [])
                }
                cg.restoreGState()
            }
            ray(lenFrac: 0.98, thickFrac: 0.09, angleDeg: 0)
            ray(lenFrac: 0.98, thickFrac: 0.09, angleDeg: 90)
            ray(lenFrac: 0.52, thickFrac: 0.055, angleDeg: 45)
            ray(lenFrac: 0.52, thickFrac: 0.055, angleDeg: 135)
        }
    }

    // MARK: 배경 별밭

    /// 배경 별밭 — 컨테이너와 함께 회전하는 내부-시점 구(반지름 28)에 별 텍스처.
    /// (Android 는 스프라이트 1600개 — iOS 는 노드 수 절약을 위해 텍스처 베이크.)
    static func starfieldNode() -> SCNNode {
        let w = 2048, h = 1024
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        var seed: UInt64 = 7
        func rnd() -> CGFloat { // xorshift — 결정적(진입마다 같은 하늘)
            seed ^= seed << 13; seed ^= seed >> 7; seed ^= seed << 17
            return CGFloat(seed % 10_000) / 10_000
        }
        let image = UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format).image { ctx in
            UIColor.black.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
            for _ in 0..<1600 {
                let x = rnd() * CGFloat(w)
                let y = rnd() * CGFloat(h)
                let warm = rnd()
                let bright = 0.18 + rnd() * 0.82
                let big = rnd()
                let r = 0.8 + big * big * 2.6 // 대부분 잔별, 소수만 크게
                UIColor(
                    red: bright * (0.85 + 0.15 * warm),
                    green: bright * (0.85 + 0.10 * warm),
                    blue: bright * (0.95 - 0.15 * warm),
                    alpha: 0.9
                ).setFill()
                ctx.cgContext.fillEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
            }
        }
        let sphere = SCNSphere(radius: 28)
        sphere.segmentCount = 32
        let material = SCNMaterial()
        material.lightingModel = .constant
        material.diffuse.contents = image
        material.cullMode = .front           // 구 안쪽 면을 렌더
        material.writesToDepthBuffer = false
        sphere.materials = [material]
        let node = SCNNode(geometry: sphere)
        node.renderingOrder = -10            // 지구보다 먼저(뒤에) 그리기
        return node
    }

    // MARK: 궤적 트레일

    /// 자유 원호 리본 트레일 — 반지름/기울기/호 길이/색 랜덤(시드 고정), 컨테이너와 함께 회전.
    /// (Android buildTrails/RING 셰이더의 정적 근사: A→B 그라데이션 + 양끝 페이드 베이크.)
    static func trailNodes() -> [SCNNode] {
        let palette: [(UIColor, UIColor)] = [
            (UIColor(red: 0.55, green: 0.75, blue: 1.00, alpha: 1), UIColor(red: 0.72, green: 0.55, blue: 1.00, alpha: 1)),
            (UIColor(red: 1.00, green: 0.62, blue: 0.42, alpha: 1), UIColor(red: 1.00, green: 0.80, blue: 0.45, alpha: 1)),
            (UIColor(red: 0.72, green: 0.55, blue: 1.00, alpha: 1), UIColor(red: 0.55, green: 0.75, blue: 1.00, alpha: 1)),
            (UIColor(red: 0.45, green: 1.00, blue: 0.80, alpha: 1), UIColor(red: 0.55, green: 0.75, blue: 1.00, alpha: 1)),
            (UIColor(red: 1.00, green: 0.80, blue: 0.45, alpha: 1), UIColor(red: 1.00, green: 0.62, blue: 0.42, alpha: 1)),
        ]
        var seed: UInt64 = 11
        func rnd() -> Float {
            seed ^= seed << 13; seed ^= seed >> 7; seed ^= seed << 17
            return Float(seed % 10_000) / 10_000
        }
        return (0..<5).map { i in
            let radius = 1.28 + rnd() * 0.50
            let halfW = 0.016 + rnd() * 0.014
            let tiltX = (-38 + rnd() * 76) * Float.pi / 180
            let tiltZ = (-45 + rnd() * 90) * Float.pi / 180
            let start = rnd() * 360
            let sweep = 130 + rnd() * 150
            return trailNode(radius: radius, halfWidth: halfW, tiltX: tiltX, tiltZ: tiltZ,
                             startDeg: start, sweepDeg: sweep, colors: palette[i % palette.count])
        }
    }

    private static func trailNode(
        radius: Float, halfWidth: Float, tiltX: Float, tiltZ: Float,
        startDeg: Float, sweepDeg: Float, colors: (UIColor, UIColor)
    ) -> SCNNode {
        let segs = 96
        var vertices: [SCNVector3] = []
        var uvs: [CGPoint] = []
        for s in 0...segs {
            let u = Float(s) / Float(segs)
            let ang = (startDeg + u * sweepDeg) * Float.pi / 180
            for k in 0...1 {
                let r = radius + (k == 0 ? -halfWidth : halfWidth)
                // XZ 평면 원 → Z 축 기울임 → X 축 기울임 (Android buildArc 회전 순서 패리티)
                let p0 = SCNVector3(cos(ang) * r, 0, sin(ang) * r)
                let p1 = SCNVector3( // rotate Z
                    p0.x * cos(tiltZ) - p0.y * sin(tiltZ),
                    p0.x * sin(tiltZ) + p0.y * cos(tiltZ),
                    p0.z
                )
                let p2 = SCNVector3( // rotate X
                    p1.x,
                    p1.y * cos(tiltX) - p1.z * sin(tiltX),
                    p1.y * sin(tiltX) + p1.z * cos(tiltX)
                )
                vertices.append(p2)
                uvs.append(CGPoint(x: CGFloat(u), y: CGFloat(k)))
            }
        }
        let indices: [Int32] = Array(0..<Int32(vertices.count))
        let geometry = SCNGeometry(
            sources: [SCNGeometrySource(vertices: vertices), SCNGeometrySource(textureCoordinates: uvs)],
            elements: [SCNGeometryElement(indices: indices, primitiveType: .triangleStrip)]
        )
        let material = SCNMaterial()
        material.lightingModel = .constant
        material.diffuse.contents = trailTexture(colorA: colors.0, colorB: colors.1)
        material.blendMode = .add
        material.isDoubleSided = true
        material.writesToDepthBuffer = false
        geometry.materials = [material]
        return SCNNode(geometry: geometry)
    }

    /// 트레일 텍스처: 길이(u) 방향 A↔B 색 + 양끝 페이드, 폭(v) 방향 소프트 글로우+얇은 코어.
    /// additive 블렌딩이므로 밝기를 RGB 에 직접 베이크(알파 불사용).
    private static func trailTexture(colorA: UIColor, colorB: UIColor) -> UIImage {
        let w = 256, h = 16
        var aR: CGFloat = 0, aG: CGFloat = 0, aB: CGFloat = 0, aA: CGFloat = 0
        var bR: CGFloat = 0, bG: CGFloat = 0, bB: CGFloat = 0, bA: CGFloat = 0
        colorA.getRed(&aR, green: &aG, blue: &aB, alpha: &aA)
        colorB.getRed(&bR, green: &bG, blue: &bB, alpha: &bA)
        var pixels = [UInt8](repeating: 0, count: w * h * 4)
        for y in 0..<h {
            let v = Double(y) / Double(h - 1)
            let across = sin(v * .pi)
            let glow = pow(across, 1.8) * 0.14
            let core = pow(across, 9.0) * 0.55
            let strength = glow + core
            for x in 0..<w {
                let u = Double(x) / Double(w - 1)
                let mix = 0.5 + 0.5 * sin(u * 2 * .pi)
                let endFade = pow(sin(u * .pi), 0.6) // 양끝 자연 페이드
                let r = (Double(aR) * (1 - mix) + Double(bR) * mix) * strength * endFade
                let g = (Double(aG) * (1 - mix) + Double(bG) * mix) * strength * endFade
                let b = (Double(aB) * (1 - mix) + Double(bB) * mix) * strength * endFade
                let o = (y * w + x) * 4
                pixels[o] = UInt8(min(255, r * 255))
                pixels[o + 1] = UInt8(min(255, g * 255))
                pixels[o + 2] = UInt8(min(255, b * 255))
                pixels[o + 3] = 255
            }
        }
        let data = Data(pixels)
        guard let provider = CGDataProvider(data: data as CFData),
              let cg = CGImage(
                width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
              )
        else { return UIImage() }
        return UIImage(cgImage: cg)
    }
}

private extension UIColor {
    /// 밝기만 스케일한 색(additive tint 용).
    func withBrightnessScaled(by factor: CGFloat) -> UIColor {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        return UIColor(red: r * factor, green: g * factor, blue: b * factor, alpha: a)
    }
}
