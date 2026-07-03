import SceneKit
import SwiftUI
import UIKit

/// 3D 행성(지구) 화면 — 지도 하단 "지구 보기" 버튼으로 진입하는 전체화면 오버레이.
/// (Android `feature/globe/GlobeScreen+GlobeRenderer` 패리티 — iOS 는 SceneKit 구현.)
///
/// - 드래그: 행성 회전(관성), 3초 무입력 시 느린 자동 회전.
/// - 핀치: 카메라 줌(최소/최대 클램프) — 화면 전환 없음.
/// - 화면 아래쪽 탭: 닫기(X) 버튼 표시(4초 후 자동 숨김) → 누르면 지금 정면 지점의 지도로 복귀.
///
/// 성능: 씬/텍스처는 이 뷰가 나타날 때만 생성 — 지도 화면 평상시 비용 0.
/// 노란 도시 야경 점광은 노드가 아니라 발광(emission) 텍스처에 베이크해
/// 노드 수를 별 플레어(좋아요 100+)만으로 억제한다.
struct GlobeScreen: View {
    @ObservedObject private var locale = LocaleManager.shared
    let diaries: [Diary]
    let startLat: Double
    let startLng: Double
    let onRequestExit: (_ lat: Double, _ lng: Double) -> Void

    @State private var hintVisible = true
    // 아래쪽 탭으로 나타나는 닫기(X) 버튼 — 4초 무입력 시 자동 숨김(token 으로 최신 탭만 유효).
    @State private var closeVisible = false
    @State private var closeToken = 0
    @StateObject private var exitProxy = GlobeExitProxy()

    var body: some View {
        ZStack(alignment: .bottom) {
            GlobeSceneView(
                diaries: diaries, startLat: startLat, startLng: startLng,
                onRequestExit: onRequestExit,
                onBottomTap: { showClose() },
                exitProxy: exitProxy
            )
            .ignoresSafeArea()
            .background(Color.black)

            // 조작 힌트 — 잠깐 보였다 사라짐 (Android globe_hint 패리티)
            Text(locale.t(.globeHint))
                .font(.footnote)
                .foregroundStyle(.white.opacity(0.75))
                .padding(.horizontal, 16).padding(.vertical, 8)
                .background(Color.black.opacity(0.35), in: Capsule())
                .padding(.bottom, 96) // 닫기(X) 버튼 자리 위
                .opacity(hintVisible ? 1 : 0)
                .animation(.easeInOut(duration: 0.7), value: hintVisible)

            // 닫기(X) 버튼 — 아래쪽 탭으로 표시, 누르면 지금 정면 지점의 지도로 복귀.
            if closeVisible {
                Button {
                    exitProxy.requestExit?()
                } label: {
                    Image(systemName: "xmark")
                        .font(.title3.bold())
                        .frame(width: 52, height: 52)
                        .background(Color.white.opacity(0.14), in: Circle())
                        .foregroundStyle(.white)
                }
                .accessibilityLabel(locale.t(.globeClose))
                .padding(.bottom, 24)
                .transition(.opacity)
            }
        }
        .task {
            try? await Task.sleep(nanoseconds: 4_200_000_000)
            hintVisible = false
        }
    }

    private func showClose() {
        withAnimation(.easeInOut(duration: 0.25)) { closeVisible = true }
        closeToken += 1
        let token = closeToken
        Task {
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            if token == closeToken {
                withAnimation(.easeInOut(duration: 0.25)) { closeVisible = false }
            }
        }
    }
}

/// SwiftUI(X 버튼) → SceneKit Coordinator 의 "정면 좌표로 지도 복귀"를 잇는 프록시.
private final class GlobeExitProxy: ObservableObject {
    var requestExit: (() -> Void)?
}

/// SceneKit 씬 래퍼. 카메라/관성/자동회전 시뮬레이션은 렌더 델리게이트에서 진행.
private struct GlobeSceneView: UIViewRepresentable {
    let diaries: [Diary]
    let startLat: Double
    let startLng: Double
    let onRequestExit: (_ lat: Double, _ lng: Double) -> Void
    /// 화면 아래쪽(55% 이하 영역) 탭 — 닫기(X) 버튼 표시 요청.
    let onBottomTap: () -> Void
    let exitProxy: GlobeExitProxy

    func makeCoordinator() -> Coordinator {
        Coordinator(startLat: startLat, startLng: startLng,
                    onRequestExit: onRequestExit, onBottomTap: onBottomTap)
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
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.onTap(_:)))
        view.addGestureRecognizer(pan)
        view.addGestureRecognizer(pinch)
        view.addGestureRecognizer(tap)
        // SwiftUI 쪽 X 버튼이 Coordinator 의 "정면 좌표 복귀"를 호출할 수 있게 연결.
        exitProxy.requestExit = { [weak coordinator = context.coordinator] in
            coordinator?.requestExitToMap()
        }
        return view
    }

    func updateUIView(_ uiView: SCNView, context: Context) {}

    // MARK: - Coordinator (씬 구성 + 제스처 + 프레임 시뮬레이션)

    final class Coordinator: NSObject, SCNSceneRendererDelegate {
        // Android GlobeRenderer 상수 패리티
        static let enterDist: Float = 4.6    // 진입 시작 거리(돌리-인 출발점)
        static let idleDist: Float = 3.25    // 기본 관람 거리
        static let minDist: Float = 2.10     // 카메라 최소 거리(핀치 줌 클램프 — 화면 전환 없음)
        static let maxDist: Float = 6.0
        static let flareMinLikes = 100       // 이 이상 좋아요 → 별 플레어(노드), 미만 → 베이크된 점광
        static let flareMax = 500

        let onRequestExit: (_ lat: Double, _ lng: Double) -> Void
        let onBottomTap: () -> Void

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

        init(
            startLat: Double, startLng: Double,
            onRequestExit: @escaping (_ lat: Double, _ lng: Double) -> Void,
            onBottomTap: @escaping () -> Void
        ) {
            self.onRequestExit = onRequestExit
            self.onBottomTap = onBottomTap
            self.pitchDeg = Float(min(max(startLat, -75), 75))
            self.yawDeg = Float(-startLng)
            super.init()
        }

        /// X 버튼 → 지금 정면 지점의 지도로 복귀.
        func requestExitToMap() { fireExit() }

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
                // 핀치는 카메라 줌만 — 화면 전환(지도 복귀) 없음
                let next = min(max(camDist / factor, Coordinator.minDist), Coordinator.maxDist)
                camDist = next
                dollyTarget = next
            default: break
            }
        }

        /// 화면 아래쪽 탭 → 닫기(X) 버튼 표시 요청.
        @objc func onTap(_ g: UITapGestureRecognizer) {
            guard let v = g.view else { return }
            if g.location(in: v).y >= v.bounds.height * 0.55 {
                DispatchQueue.main.async { self.onBottomTap() }
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
    /// "원본 3/4 밝기" 디퓨즈 + "노란 도시 야경 점광" 이미션 텍스처.
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

    /// 지구 디퓨즈(원본의 3/4 밝기 균일 — 별 근처 지형 밝힘 없음)
    /// + 노란 작은 점광(인류의 도시 야경) 이미션 생성. Android EARTH_FS/글로 스프라이트 패리티.
    private static func earthTextures(diaries: [Diary]) -> (night: UIImage, lit: UIImage) {
        let base = loadEarthImage()
        let w = 2048, h = 1024
        let size = CGSize(width: w, height: h)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1

        // 1) 지구: 원본 × 0.75
        let night = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            base.draw(in: CGRect(origin: .zero, size: size))
            UIColor.black.withAlphaComponent(0.25).setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
        }

        // 2) 이미션 = 노란 작은 점광(좋아요 100 미만 다이어리 1:1) — 도시 야경
        let lit = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            let cg = ctx.cgContext
            cg.setBlendMode(.plusLighter)
            for d in diaries.prefix(4000) where d.likeCount < GlobeSceneView.Coordinator.flareMinLikes {
                let cx = CGFloat((d.longitude + 180) / 360) * CGFloat(w)
                let cy = CGFloat((90 - d.latitude) / 180) * CGFloat(h)
                UIColor(red: 1.0, green: 0.76, blue: 0.36, alpha: 0.5).setFill()
                cg.fillEllipse(in: CGRect(x: cx - 2.2, y: cy - 2.2, width: 4.4, height: 4.4))
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

    /// 별 플레어 팔레트(레퍼런스풍) — 빨강/파랑/분홍/노랑/민트/보라/백색. (Android FLARE_COLORS 패리티)
    private static let flareColors: [UIColor] = [
        UIColor(red: 1.00, green: 0.384, blue: 0.341, alpha: 1), // 빨강 #FF6257
        UIColor(red: 0.427, green: 0.620, blue: 1.00, alpha: 1), // 파랑 #6D9EFF
        UIColor(red: 1.00, green: 0.545, blue: 0.847, alpha: 1), // 분홍 #FF8BD8
        UIColor(red: 1.00, green: 0.851, blue: 0.400, alpha: 1), // 노랑 #FFD966
        UIColor(red: 0.561, green: 0.969, blue: 0.886, alpha: 1), // 민트 #8FF7E2
        UIColor(red: 0.769, green: 0.608, blue: 1.00, alpha: 1), // 보라 #C49BFF
        UIColor.white,
    ]

    /// 좌표 기반 결정적 팔레트 인덱스 — 같은 다이어리는 항상 같은 색. (Android flareColorIndex 패리티)
    private static func flareColorIndex(_ d: Diary) -> Int {
        let n = Double(flareColors.count)
        let h = (d.latitude * 7919.0 + d.longitude * 104729.0).truncatingRemainder(dividingBy: n)
        return Int((h + n).truncatingRemainder(dividingBy: n))
    }

    /// 좋아요 100+ 다이어리 → 구 표면 살짝 바깥의 컬러 별 플레어 빌보드(+트윙클).
    static func flareNodes(diaries: [Diary]) -> [SCNNode] {
        let popular = diaries.filter { $0.likeCount >= GlobeSceneView.Coordinator.flareMinLikes }
            .sorted { $0.likeCount > $1.likeCount }
            .prefix(GlobeSceneView.Coordinator.flareMax)
        guard !popular.isEmpty else { return [] }
        let flareImage = makeFlareImage()

        return popular.map { d in
            let boost = Float(min(d.likeCount, 1000)) / 1000
            let size = CGFloat(0.040 + 0.032 * boost) * 2.4 // 살짝 더 축소(이전 0.046+0.038) — 텍스처 여백 감안 배율
            let plane = SCNPlane(width: size, height: size)
            let material = SCNMaterial()
            material.lightingModel = .constant
            material.diffuse.contents = flareImage
            material.blendMode = .add
            material.writesToDepthBuffer = false
            // 팔레트 tint × 감광 밝기 — 레퍼런스처럼 별마다 다른 색으로 빛남
            let bright = CGFloat(0.60 + 0.15 * boost)
            material.multiply.contents = flareColors[flareColorIndex(d)].withBrightnessScaled(by: bright)
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

    /// 자유 원호 트레일 — 얇은 코어 라인 + 감싸는 반투명 글로우(레퍼런스풍), 일부는 행성 근접 궤도.
    /// 반지름/기울기/호 길이/색/위상 랜덤(시드 고정), 컨테이너와 함께 회전.
    /// (Android buildTrails/RING 셰이더 근사: 단면·양끝 페이드·색은 텍스처에 베이크하고,
    ///  천천히 흐르는 밝기는 SceneKit 셰이더 모디파이어로 애니메이트.)
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
            // 앞의 2개는 행성 근접 궤도(레퍼런스), 나머지는 멀리
            let radius = i < 2 ? 1.10 + rnd() * 0.12 : 1.30 + rnd() * 0.45
            let halfW = 0.030 + rnd() * 0.020 // 얇은 선 + 감싸는 글로우 폭
            let tiltX = (-38 + rnd() * 76) * Float.pi / 180
            let tiltZ = (-45 + rnd() * 90) * Float.pi / 180
            let start = rnd() * 360
            let sweep = 130 + rnd() * 150
            let phase = Double(rnd()) * 6.2832 // 트레일별 파동 위상(불규칙성)
            let dir: Double = rnd() < 0.5 ? 1 : -1
            let speed = dir * (0.018 + Double(rnd()) * 0.030) // 천천히 흐르게
            return trailNode(radius: radius, halfWidth: halfW, tiltX: tiltX, tiltZ: tiltZ,
                             startDeg: start, sweepDeg: sweep, colors: palette[i % palette.count],
                             phase: phase, speed: speed)
        }
    }

    private static func trailNode(
        radius: Float, halfWidth: Float, tiltX: Float, tiltZ: Float,
        startDeg: Float, sweepDeg: Float, colors: (UIColor, UIColor), phase: Double, speed: Double
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
        material.diffuse.contents = trailTexture(colorA: colors.0, colorB: colors.1, phase: phase)
        material.blendMode = .add
        material.isDoubleSided = true
        material.writesToDepthBuffer = false
        // 천천히 흐르는 밝기 — Android RING_FS flow 항 패리티(주파수·위상 다른 파동 조합, 저속).
        // 셰이더 모디파이어는 런타임 컴파일이라 실패해도 정적 트레일로 안전 폴백된다.
        material.shaderModifiers = [
            .surface: """
            float u = _surface.diffuseTexcoord.x;
            float t = u_time * \(speed);
            float w1 = 0.5 + 0.5 * sin((u - t) * 6.2831 + \(phase));
            float w2 = 0.5 + 0.5 * sin((u * 2.7 + t * 0.7) * 6.2831 + \(phase * 2.3));
            float w3 = 0.5 + 0.5 * sin((u * 5.3 - t * 0.35) * 6.2831 + \(phase * 4.1));
            float flow = 0.35 + 0.65 * (0.5 * w1 + 0.3 * w2 + 0.2 * w3);
            _surface.diffuse.rgb *= flow;
            """
        ]
        geometry.materials = [material]
        return SCNNode(geometry: geometry)
    }

    /// 트레일 텍스처 — 얇은 코어 라인 + 감싸는 반투명 글로우(Android RING_FS 정적 성분):
    /// 폭(v) 방향은 core(=pow 14)·glow(=pow 2) 단면, 양끝(u)은 점점 투명해지며 소멸,
    /// 길이 방향은 A↔B 색 그라데이션. 흐르는 밝기는 셰이더 모디파이어가 런타임에 곱한다.
    /// additive 블렌딩이므로 밝기를 RGB 에 직접 베이크(알파 불사용).
    private static func trailTexture(colorA: UIColor, colorB: UIColor, phase: Double) -> UIImage {
        let w = 256, h = 32
        var aR: CGFloat = 0, aG: CGFloat = 0, aB: CGFloat = 0, aA: CGFloat = 0
        var bR: CGFloat = 0, bG: CGFloat = 0, bB: CGFloat = 0, bA: CGFloat = 0
        colorA.getRed(&aR, green: &aG, blue: &aB, alpha: &aA)
        colorB.getRed(&bR, green: &bG, blue: &bB, alpha: &bA)
        func smoothstep(_ e0: Double, _ e1: Double, _ x: Double) -> Double {
            let t = min(max((x - e0) / (e1 - e0), 0), 1)
            return t * t * (3 - 2 * t)
        }
        var pixels = [UInt8](repeating: 0, count: w * h * 4)
        for y in 0..<h {
            let v = Double(y) / Double(h - 1)
            let across = sin(v * .pi)
            let glow = pow(across, 2.0) * 0.22 // 선을 감싸는 은은한 글로우(반투명)
            let core = pow(across, 14.0)       // 레퍼런스풍 얇은 코어 라인
            for x in 0..<w {
                let u = Double(x) / Double(w - 1)
                // 양 끝은 점점 투명해지며 소멸(확 끊기지 않게 긴 램프)
                let ends = smoothstep(0.0, 0.20, u) * smoothstep(1.0, 0.80, u)
                let mix = 0.5 + 0.5 * sin(u * 2 * .pi + phase)
                let colored = glow + core * 0.75
                let white = core * 0.18 // 은은한 백색 심지(이동 하이라이트는 flow 가 담당)
                let r = ((Double(aR) * (1 - mix) + Double(bR) * mix) * colored + white) * ends
                let g = ((Double(aG) * (1 - mix) + Double(bG) * mix) * colored + white) * ends
                let b = ((Double(aB) * (1 - mix) + Double(bB) * mix) * colored + white) * ends
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
