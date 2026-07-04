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
        static let minDist: Float = 1.45     // 카메라 최소 거리(더 바짝 당겨보기 — 화면 전환 없음)
        static let maxDist: Float = 9.5      // 카메라 최대 거리(지구가 작아 보일 만큼 멀리)
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
        /// 지구 재질 — 낮/밤 반구 셰이더의 uSunDir 를 매 프레임 갱신하기 위해 보관.
        private var earthMaterial: SCNMaterial?
        /// 태양 노드 — 광원 방향 하늘에 떠 있는 해(하루 주기로 이동), 매 프레임 위치 갱신.
        private var sunNode: SCNNode?

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

            // 태양 방향 — UTC 기준 하루에 360도 회전(적도 상공, UTC 정오에 경도 0 상공).
            // 지구 좌표계 벡터를 컨테이너 회전(Rx(pitch)·Ry(yaw))으로 월드 공간에 옮긴다.
            // 카메라는 회전하지 않으므로(위치만 이동) 월드 방향 == 뷰 공간 방향 — 셰이더의
            // _surface.normal(뷰 공간)과 바로 내적 가능. (Android drawEarth uSunDir 패리티)
            let dayFrac = Date().timeIntervalSince1970
                .truncatingRemainder(dividingBy: 86_400) / 86_400
            let sunLng = (180.0 - dayFrac * 360.0) * Double.pi / 180
            let mx = Float(sin(sunLng)), mz = Float(cos(sunLng)) // 모델(지구) 좌표계, 적도(y=0)
            let a = yawDeg * Float.pi / 180, b = pitchDeg * Float.pi / 180
            let rx = mx * cos(a) + mz * sin(a)                   // Ry(yaw)
            let rz = -mx * sin(a) + mz * cos(a)
            let wy = -rz * sin(b)                                // Rx(pitch), y'=0
            let wz = rz * cos(b)
            earthMaterial?.setValue(NSValue(scnVector3: SCNVector3(rx, wy, wz)), forKey: "uSunDir")
            // 태양 노드 — 광원 방향 하늘(컨테이너 좌표계 = 모델 좌표계)에 위치.
            sunNode?.position = SCNVector3(mx * 45, 0, mz * 45)
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

            let earth = GlobeBuilder.earthNode(diaries: valid)
            earthMaterial = earth.geometry?.firstMaterial
            containerNode.addChildNode(earth)
            for node in GlobeBuilder.starfieldNodes() { containerNode.addChildNode(node) }
            for node in GlobeBuilder.trailNodes() { containerNode.addChildNode(node) }
            for node in GlobeBuilder.auroraNodes() { containerNode.addChildNode(node) }
            let sun = GlobeBuilder.sunNode()
            sunNode = sun
            containerNode.addChildNode(sun)
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
    /// 낮/밤 반구(Android EARTH_FS 패리티): uSunDir 쪽 반구는 기준 밝기 그대로,
    /// 반대 반구는 30% 감광, 터미네이터는 smoothstep — uSunDir 는 Coordinator 가
    /// 매 프레임 월드(=뷰) 공간으로 갱신한다. 모디파이어 컴파일 실패 시 균일 밝기 폴백.
    static func earthNode(diaries: [Diary]) -> SCNNode {
        let geometry = sphereGeometry(radius: 1, stacks: 64, slices: 128)
        let material = SCNMaterial()
        material.lightingModel = .constant
        let (night, lit) = earthTextures(diaries: diaries)
        material.diffuse.contents = night
        material.emission.contents = lit
        material.shaderModifiers = [
            .surface: """
            #pragma arguments
            float3 uSunDir;
            #pragma body
            float ndl = dot(normalize(_surface.normal), normalize(uSunDir));
            float light = 0.70 + 0.45 * smoothstep(-0.18, 0.22, ndl);
            _surface.diffuse.rgb *= light;
            """
        ]
        // 첫 프레임 전 기본값(0 벡터 normalize 방지) — 카메라 정면 방향
        material.setValue(NSValue(scnVector3: SCNVector3(0, 0, 1)), forKey: "uSunDir")
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
            let size = CGFloat(0.034 + 0.026 * boost) * 2.4 // 한 단계 더 축소(이전 0.040+0.032) — 텍스처 여백 감안 배율
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

    // MARK: 태양

    /// 태양 — 광원 방향 하늘(반지름 45)에 떠 있는 해: 백열 코어 + 금빛 헤일로 + 원경 산광을
    /// 한 장에 베이크한 글로우 빌보드 + 4방 광선 빌보드. additive, 배경층(지구가 가린다).
    /// 위치는 Coordinator 가 매 프레임 광원 방향으로 갱신(Android drawSun 패리티).
    static func sunNode() -> SCNNode {
        let node = SCNNode()
        func plane(_ size: CGFloat, _ image: UIImage, tint: UIColor?) -> SCNNode {
            let geom = SCNPlane(width: size, height: size)
            let material = SCNMaterial()
            material.lightingModel = .constant
            material.diffuse.contents = image
            if let tint { material.multiply.contents = tint }
            material.blendMode = .add
            material.writesToDepthBuffer = false
            geom.materials = [material]
            let child = SCNNode(geometry: geom)
            child.renderingOrder = -8 // 별밭(-12..-10)·오로라(-9) 다음, 지구(0)보다 먼저 — 배경층
            return child
        }
        node.addChildNode(plane(11, makeSunImage(), tint: nil))
        node.addChildNode(plane(6, makeFlareImage(),
                                tint: UIColor(red: 0.85, green: 0.72, blue: 0.45, alpha: 1)))
        let billboard = SCNBillboardConstraint()
        billboard.freeAxes = .all
        node.constraints = [billboard]
        return node
    }

    /// 태양 글로우 텍스처 — 원경 산광(전체) + 금빛 헤일로(42%) + 백열 코어(15%)를
    /// plusLighter 로 겹쳐 베이크. additive 재질이라 검정 = 투명.
    private static func makeSunImage() -> UIImage {
        let s: CGFloat = 256
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: s, height: s), format: format).image { ctx in
            let cg = ctx.cgContext
            UIColor.black.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: s, height: s))
            cg.setBlendMode(.plusLighter)
            let c = CGPoint(x: s / 2, y: s / 2)
            let space = CGColorSpaceCreateDeviceRGB()
            func glow(_ color: UIColor, _ radiusFrac: CGFloat) {
                guard let g = CGGradient(
                    colorsSpace: space,
                    colors: [color.cgColor,
                             color.withAlphaComponent(0.35).cgColor,
                             UIColor.black.cgColor] as CFArray,
                    locations: [0, 0.35, 1]
                ) else { return }
                cg.drawRadialGradient(g, startCenter: c, startRadius: 0,
                                      endCenter: c, endRadius: s / 2 * radiusFrac, options: [])
            }
            glow(UIColor(red: 0.16, green: 0.10, blue: 0.04, alpha: 1), 1.00) // 원경 산광
            glow(UIColor(red: 0.55, green: 0.42, blue: 0.18, alpha: 1), 0.42) // 금빛 헤일로
            glow(UIColor(red: 1.00, green: 0.97, blue: 0.90, alpha: 1), 0.15) // 백열 코어
        }
    }

    // MARK: 배경 별밭

    /// 배경 별밭 — 반지름이 다른 3겹 구면 셸 + 원경 성운 글로우. (Android buildStarfield 패리티)
    /// 카메라가 중심에서 떨어져 있어 회전/줌 시 셸마다 시차가 생겨 "진짜 3D 공간" 깊이감이 난다.
    /// additive 블렌딩이라 텍스처의 검정 배경은 투명으로 합성돼 셸이 겹겹이 비친다.
    static func starfieldNodes() -> [SCNNode] {
        let w = 2048, h = 1024
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        var seed: UInt64 = 7
        func rnd() -> CGFloat { // xorshift — 결정적(진입마다 같은 하늘)
            seed ^= seed << 13; seed ^= seed >> 7; seed ^= seed << 17
            return CGFloat(seed % 10_000) / 10_000
        }

        func shellImage(count: Int, brightMul: CGFloat, nebula: Bool) -> UIImage {
            UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format).image { ctx in
                UIColor.black.setFill()
                ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
                let cg = ctx.cgContext
                if nebula {
                    // 아주 어두운 성운 글로우 — 배경에 색 온도와 깊이(도형이 아니라 '공간'으로 읽히게)
                    let colors: [(CGFloat, CGFloat, CGFloat)] = [
                        (0.055, 0.030, 0.100), (0.040, 0.050, 0.110), (0.070, 0.030, 0.080),
                        (0.030, 0.050, 0.100), (0.060, 0.040, 0.110), (0.050, 0.020, 0.090),
                    ]
                    let space = CGColorSpaceCreateDeviceRGB()
                    func haze(_ c: (CGFloat, CGFloat, CGFloat), _ center: CGPoint, _ radius: CGFloat) {
                        guard let g = CGGradient(
                            colorsSpace: space,
                            colors: [UIColor(red: c.0, green: c.1, blue: c.2, alpha: 1).cgColor,
                                     UIColor.black.cgColor] as CFArray,
                            locations: [0, 1]
                        ) else { return }
                        cg.saveGState()
                        cg.setBlendMode(.plusLighter)
                        cg.drawRadialGradient(g, startCenter: center, startRadius: 0,
                                              endCenter: center, endRadius: radius, options: [])
                        cg.restoreGState()
                    }
                    for c in colors {
                        haze(c, CGPoint(x: rnd() * CGFloat(w), y: CGFloat(h) * (0.2 + rnd() * 0.6)),
                             180 + rnd() * 180)
                    }

                    // 은하수 띠 — 등장방형에서 대원 = 사인 곡선. 잔별 + 어두운 헤이즈
                    let bandPhase = rnd() * 2 * .pi
                    func bandY(_ u: CGFloat) -> CGFloat {
                        CGFloat(h) * 0.5 + sin(u * 2 * .pi + bandPhase) * CGFloat(h) * 0.16
                    }
                    for _ in 0..<10 {
                        let u = rnd()
                        haze((0.020, 0.024, 0.034), CGPoint(x: u * CGFloat(w), y: bandY(u)), 90 + rnd() * 80)
                    }
                    cg.setBlendMode(.plusLighter)
                    for _ in 0..<560 {
                        let u = rnd()
                        let x = u * CGFloat(w)
                        let y = bandY(u) + (rnd() + rnd() - 1) * 34 // 대략 가우시안 스프레드
                        let br = 0.06 + rnd() * 0.22
                        let warm = rnd()
                        let r = 0.5 + rnd() * 1.0
                        UIColor(red: br * (0.88 + 0.12 * warm), green: br * 0.90,
                                blue: br * (1.00 - 0.10 * warm), alpha: 1).setFill()
                        cg.fillEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
                    }

                    // 별자리 — 황도 12궁(레퍼런스 references/zodiac.avif), 궁마다 고유색.
                    // 디자인(밝은 별 + 희미한 연결선)은 기존 그대로, 색만 궁별로 다르게. (Android GlobeRenderer 패리티)
                    func constellation(center: CGPoint, scale: CGFloat, roll: CGFloat,
                                       tint: (CGFloat, CGFloat, CGFloat),
                                       points: [(CGFloat, CGFloat)], segments: [(Int, Int)]) {
                        let cosR = cos(roll), sinR = sin(roll)
                        let pts = points.map { p in
                            CGPoint(x: center.x + (p.0 * cosR - p.1 * sinR) * scale,
                                    y: center.y - (p.0 * sinR + p.1 * cosR) * scale)
                        }
                        cg.setBlendMode(.plusLighter)
                        cg.setStrokeColor(UIColor(red: tint.0 * 0.30, green: tint.1 * 0.30,
                                                  blue: tint.2 * 0.30, alpha: 1).cgColor)
                        cg.setLineWidth(1.5)
                        for s in segments {
                            cg.move(to: pts[s.0]); cg.addLine(to: pts[s.1]); cg.strokePath()
                        }
                        for p in pts {
                            let br = 0.55 + rnd() * 0.25 // 배경보다 또렷한 밝기 + 궁별 틴트
                            UIColor(red: br * (0.45 + 0.55 * tint.0), green: br * (0.45 + 0.55 * tint.1),
                                    blue: br * (0.45 + 0.55 * tint.2), alpha: 1).setFill()
                            cg.fillEllipse(in: CGRect(x: p.x - 2.6, y: p.y - 2.6, width: 5.2, height: 5.2))
                        }
                    }
                    // 12궁 — equirect(2048×1024) 상 경도 30°씩 + 위도 4단 사이클로 골고루 분산
                    // (x=(lng+180)/360·w, y=(90−lat)/180·h — Android 위경도 배치와 동일 지점)
                    // 양자리 — 코랄 레드
                    constellation(center: CGPoint(x: 85, y: 216), scale: 26, roll: -0.14,
                                  tint: (1.00, 0.52, 0.42), points: [
                        (0.0, 0.0), (0.9, 0.3), (1.6, 0.35), (1.9, 0.05),
                    ], segments: [(0, 1), (1, 2), (2, 3)])
                    // 황소자리 — 연두
                    constellation(center: CGPoint(x: 256, y: 410), scale: 27, roll: 0.17,
                                  tint: (0.62, 0.95, 0.55), points: [
                        (0.0, 0.0), (0.6, 0.5), (1.6, 0.9), (0.55, -0.35), (1.5, -0.75),
                    ], segments: [(0, 1), (1, 2), (0, 3), (3, 4)])
                    // 쌍둥이자리 — 옐로
                    constellation(center: CGPoint(x: 427, y: 614), scale: 26, roll: -0.24,
                                  tint: (1.00, 0.88, 0.45), points: [
                        (0.0, 1.0), (0.55, 0.95), (0.05, 0.4), (0.6, 0.35), (0.0, -0.35), (0.65, -0.4),
                    ], segments: [(0, 2), (2, 4), (1, 3), (3, 5), (2, 3)])
                    // 게자리 — 은청
                    constellation(center: CGPoint(x: 597, y: 808), scale: 24, roll: 0.10,
                                  tint: (0.75, 0.85, 1.00), points: [
                        (0.0, 0.65), (0.35, 0.15), (0.05, -0.55), (0.85, 0.3),
                    ], segments: [(0, 1), (1, 2), (1, 3)])
                    // 사자자리 — 골드
                    constellation(center: CGPoint(x: 768, y: 216), scale: 26, roll: 0,
                                  tint: (1.00, 0.72, 0.30), points: [
                        (0.0, 0.0), (0.15, 0.55), (0.5, 0.9), (1.0, 0.9), (1.25, 0.55),
                        (-1.2, 0.35), (-0.7, 0.62), (-0.55, 0.05),
                    ], segments: [(0, 1), (1, 2), (2, 3), (3, 4), (0, 7), (7, 5), (5, 6), (6, 1)])
                    // 처녀자리 — 민트
                    constellation(center: CGPoint(x: 939, y: 410), scale: 27, roll: 0.21,
                                  tint: (0.55, 1.00, 0.80), points: [
                        (0.0, -0.95), (0.15, -0.2), (-0.4, 0.3), (0.5, 0.35), (-0.9, 0.55), (0.95, 0.8), (0.15, 0.9),
                    ], segments: [(0, 1), (1, 2), (1, 3), (2, 4), (3, 5), (2, 6)])
                    // 천칭자리 — 핑크
                    constellation(center: CGPoint(x: 1109, y: 614), scale: 25, roll: -0.10,
                                  tint: (1.00, 0.62, 0.82), points: [
                        (0.0, 0.7), (-0.65, 0.2), (0.6, 0.25), (-0.5, -0.6), (0.55, -0.65),
                    ], segments: [(0, 1), (0, 2), (1, 2), (1, 3), (2, 4)])
                    // 전갈자리 — 크림슨
                    constellation(center: CGPoint(x: 1280, y: 808), scale: 27, roll: 0.14,
                                  tint: (1.00, 0.42, 0.48), points: [
                        (1.35, 0.85), (1.2, 0.55), (1.35, 0.3), (0.95, 0.5), (0.6, 0.3), (0.3, 0.0),
                        (0.15, -0.45), (0.25, -0.85), (0.55, -1.1), (0.9, -1.05), (1.05, -0.85),
                    ], segments: [(0, 3), (1, 3), (2, 3), (3, 4), (4, 5), (5, 6), (6, 7), (7, 8), (8, 9), (9, 10)])
                    // 사수자리 — 퍼플 (주전자)
                    constellation(center: CGPoint(x: 1451, y: 216), scale: 25, roll: -0.17,
                                  tint: (0.72, 0.55, 1.00), points: [
                        (0.0, 0.05), (0.3, 0.3), (0.65, 0.55), (1.0, 0.3), (1.3, 0.0), (1.0, -0.35), (0.3, -0.35),
                    ], segments: [(0, 1), (1, 2), (2, 3), (3, 4), (4, 5), (5, 6), (6, 0), (1, 6), (3, 5)])
                    // 염소자리 — 틸
                    constellation(center: CGPoint(x: 1621, y: 410), scale: 26, roll: 0.07,
                                  tint: (0.45, 0.88, 0.92), points: [
                        (-1.0, 0.5), (-0.45, 0.1), (0.15, -0.2), (0.75, -0.05), (1.05, 0.45),
                    ], segments: [(0, 1), (1, 2), (2, 3), (3, 4), (4, 0)])
                    // 물병자리 — 블루
                    constellation(center: CGPoint(x: 1792, y: 614), scale: 26, roll: -0.07,
                                  tint: (0.50, 0.72, 1.00), points: [
                        (0.0, 0.8), (0.35, 0.95), (0.65, 0.75), (0.95, 0.92), (0.3, 0.3),
                        (-0.25, 0.1), (0.5, -0.3), (0.15, -0.75),
                    ], segments: [(0, 1), (1, 2), (2, 3), (1, 4), (4, 5), (4, 6), (6, 7)])
                    // 물고기자리 — 라벤더
                    constellation(center: CGPoint(x: 1940, y: 808), scale: 27, roll: 0.24,
                                  tint: (0.82, 0.70, 1.00), points: [
                        (1.35, 0.95), (0.95, 0.6), (0.5, 0.3), (0.0, 0.0), (0.5, -0.18), (1.05, -0.28), (1.55, -0.2),
                    ], segments: [(0, 1), (1, 2), (2, 3), (3, 4), (4, 5), (5, 6)])

                    // 4방 광선 반짝별 — 은은한 포인트 몇 개
                    let flare = makeFlareImage()
                    for _ in 0..<9 {
                        let s = 22 + rnd() * 14
                        let x = rnd() * (CGFloat(w) - s * 2) + s
                        let y = CGFloat(h) * (0.12 + rnd() * 0.76)
                        flare.draw(in: CGRect(x: x - s / 2, y: y - s / 2, width: s, height: s),
                                   blendMode: .plusLighter, alpha: 0.30 + rnd() * 0.18)
                    }
                    cg.setBlendMode(.normal)
                }
                for _ in 0..<count {
                    let x = rnd() * CGFloat(w)
                    let y = rnd() * CGFloat(h)
                    let warm = rnd()
                    // 은은한 밝기 상한 — 배경은 깊이감만 주고 지구/별 플레어가 주인공이 되게
                    let bright = (0.15 + rnd() * 0.68) * brightMul
                    let big = rnd()
                    let r = 0.8 + big * big * 2.6 // 대부분 잔별, 소수만 크게
                    UIColor(
                        red: bright * (0.85 + 0.15 * warm),
                        green: bright * (0.85 + 0.10 * warm),
                        blue: bright * (0.95 - 0.15 * warm),
                        alpha: 1
                    ).setFill()
                    cg.fillEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
                }
            }
        }

        func shell(radius: CGFloat, count: Int, brightMul: CGFloat, nebula: Bool, order: Int) -> SCNNode {
            let sphere = SCNSphere(radius: radius)
            sphere.segmentCount = 32
            let material = SCNMaterial()
            material.lightingModel = .constant
            material.diffuse.contents = shellImage(count: count, brightMul: brightMul, nebula: nebula)
            material.blendMode = .add          // 검정 배경 = 투명
            material.cullMode = .front         // 구 안쪽 면을 렌더
            material.writesToDepthBuffer = false
            sphere.materials = [material]
            let node = SCNNode(geometry: sphere)
            node.renderingOrder = order        // 지구보다 먼저(뒤에) 그리기
            return node
        }

        return [
            shell(radius: 12, count: 320, brightMul: 1.00, nebula: false, order: -12), // 근경 — 시차 큼
            shell(radius: 22, count: 620, brightMul: 0.72, nebula: false, order: -11), // 중경
            shell(radius: 38, count: 900, brightMul: 0.52, nebula: true, order: -10),  // 원경 + 성운
        ]
    }

    // MARK: 궤적 트레일

    /// 자유 원호 트레일 — 얇은 코어 라인 + 감싸는 반투명 글로우(레퍼런스풍), 은은한 악센트 세기.
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
            // 전부 행성에서 여유 있게 떨어진 궤도(근접 궤도는 시각적으로 난잡해 롤백)
            let radius = 1.28 + rnd() * 0.50
            let halfW = 0.030 + rnd() * 0.020 // 얇은 선 + 감싸는 글로우 폭
            let tiltX = (-38 + rnd() * 76) * Float.pi / 180
            let tiltZ = (-45 + rnd() * 90) * Float.pi / 180
            let start = rnd() * 360
            let sweep = 130 + rnd() * 150
            let phase = Double(rnd()) * 6.2832 // 트레일별 파동 위상(불규칙성)
            let dir: Double = rnd() < 0.5 ? 1 : -1
            let speed = dir * (0.030 + Double(rnd()) * 0.040) // 느긋하지만 흐름이 느껴지는 속도
            // 앞 2개만 기준 세기, 나머지는 훨씬 옅게 — 궤적이 많아 보이지 않게 위계를 준다
            let intensity = i < 2 ? 1.0 : 0.35 + Double(rnd()) * 0.25
            return trailNode(radius: radius, halfWidth: halfW, tiltX: tiltX, tiltZ: tiltZ,
                             startDeg: start, sweepDeg: sweep, colors: palette[i % palette.count],
                             phase: phase, speed: speed, intensity: intensity)
        }
    }

    private static func trailNode(
        radius: Float, halfWidth: Float, tiltX: Float, tiltZ: Float,
        startDeg: Float, sweepDeg: Float, colors: (UIColor, UIColor), phase: Double, speed: Double,
        intensity: Double
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
        material.diffuse.contents = trailTexture(colorA: colors.0, colorB: colors.1, phase: phase,
                                                 intensity: intensity)
        material.blendMode = .add
        material.isDoubleSided = true
        material.writesToDepthBuffer = false
        // 흐르는 밝기 + 백색 빛무리 펄스 — Android RING_FS 패리티(가우시안 펄스가 궤적을 따라 이동).
        // 셰이더 모디파이어는 런타임 컴파일이라 실패해도 정적 트레일로 안전 폴백된다.
        material.shaderModifiers = [
            .surface: """
            float u = _surface.diffuseTexcoord.x;
            float v = _surface.diffuseTexcoord.y;
            float t = u_time * \(speed);
            float w1 = 0.5 + 0.5 * sin((u - t) * 6.2831 + \(phase));
            float w2 = 0.5 + 0.5 * sin((u * 2.7 + t * 0.7) * 6.2831 + \(phase * 2.3));
            float flow = 0.45 + 0.55 * (0.6 * w1 + 0.4 * w2);
            _surface.diffuse.rgb *= flow;
            float across = sin(v * 3.14159);
            float core = pow(across, 14.0);
            float ends = smoothstep(0.0, 0.20, u) * smoothstep(1.0, 0.80, u);
            float head = fract(t * 2.2 + \(phase * 0.159));
            float d1 = u - head;
            float d2 = u - fract(head + 0.47);
            float pulse = exp(-d1 * d1 * 220.0) + 0.45 * exp(-d2 * d2 * 300.0);
            _surface.diffuse.rgb += float3(core * pulse * \(0.30 * intensity) * ends);
            """
        ]
        geometry.materials = [material]
        return SCNNode(geometry: geometry)
    }

    /// 트레일 텍스처 — 얇은 코어 라인 + 감싸는 반투명 글로우(Android RING_FS 정적 성분):
    /// 폭(v) 방향은 core(=pow 14)·glow(=pow 2) 단면, 양끝(u)은 점점 투명해지며 소멸,
    /// 길이 방향은 A↔B 색 그라데이션. 흐르는 밝기는 셰이더 모디파이어가 런타임에 곱한다.
    /// additive 블렌딩이므로 밝기를 RGB 에 직접 베이크(알파 불사용).
    private static func trailTexture(colorA: UIColor, colorB: UIColor, phase: Double,
                                     intensity: Double) -> UIImage {
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
            let glow = pow(across, 2.0) * 0.07 // 선을 감싸는 아주 옅은 글로우
            let core = pow(across, 14.0)       // 레퍼런스풍 얇은 코어 라인
            for x in 0..<w {
                let u = Double(x) / Double(w - 1)
                // 양 끝은 점점 투명해지며 소멸(확 끊기지 않게 긴 램프)
                let ends = smoothstep(0.0, 0.20, u) * smoothstep(1.0, 0.80, u)
                let mix = 0.5 + 0.5 * sin(u * 2 * .pi + phase)
                // 훨씬 반투명 — 트레일은 배경에 스치는 빛줄기 정도로만(이동 펄스는 모디파이어가 담당)
                // intensity: 트레일별 투명도 차등(일부만 기준 세기, 나머지는 옅게)
                let colored = (glow + core * 0.15) * intensity
                let white = core * 0.03 * intensity
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

    // MARK: 오로라 커튼

    /// 오로라 커튼 세트 — 우주 공간(배경 별밭 셸 사이)에 드리운 빛의 장막 4폭.
    /// 극지 오벌이 아니라 하늘에 떠 있는 자유 커튼: 중심 방향의 접평면(east/north 기저)을 따라
    /// 밑단이 완만한 S 라인으로 물결치고 위로 솟아 소멸한다. 반지름을 폭마다 달리해
    /// 회전/줌 시 별밭 셸처럼 시차(깊이감)가 난다.
    /// v=0(아래 가장자리, 초록 또렷) → v=1(위, 보라/핑크로 소멸).
    /// (Android GlobeRenderer buildAuroras/AURORA_FS 패리티 — 수직 프로파일·양끝 소멸은
    ///  텍스처에 베이크, 주름(fold)·스윕 파동은 셰이더 모디파이어가 u_time 으로 흘린다.)
    static func auroraNodes() -> [SCNNode] {
        // 오로라 팔레트 — 아래(초록/청록)에서 위(보라/핑크/남보라)로 녹아드는 실제 오로라 색
        let green: (CGFloat, CGFloat, CGFloat) = (0.18, 0.95, 0.52)
        let teal: (CGFloat, CGFloat, CGFloat) = (0.20, 0.80, 0.78)
        let violet: (CGFloat, CGFloat, CGFloat) = (0.58, 0.32, 0.95)
        let pink: (CGFloat, CGFloat, CGFloat) = (0.90, 0.38, 0.78)
        let indigo: (CGFloat, CGFloat, CGFloat) = (0.35, 0.40, 0.98)
        var seed: UInt64 = 23
        func rnd() -> Float { // xorshift — 결정적(진입마다 같은 오로라, Android seed 23 패리티)
            seed ^= seed << 13; seed ^= seed >> 7; seed ^= seed << 17
            return Float(seed % 10_000) / 10_000
        }
        func cross(_ a: SCNVector3, _ b: SCNVector3) -> SCNVector3 {
            SCNVector3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
        }
        func norm(_ v: SCNVector3) -> SCNVector3 {
            let len = max(sqrt(v.x * v.x + v.y * v.y + v.z * v.z), 1e-6)
            return SCNVector3(v.x / len, v.y / len, v.z / len)
        }
        func curtain(
            centerLat: Double, centerLng: Double, radius: Float,
            lengthRad: Float, heightRad: Float, rollDeg: Float,
            bot: (CGFloat, CGFloat, CGFloat), top: (CGFloat, CGFloat, CGFloat),
            speed: Double, intensity: Double
        ) -> SCNNode {
            let segs = 160
            let p1 = rnd() * 6.2832, p2 = rnd() * 6.2832, p3 = rnd() * 6.2832
            let c = latLngToXyz(lat: centerLat, lng: centerLng, radius: 1)
            let east = norm(cross(SCNVector3(0, 1, 0), c))
            let north = norm(cross(c, east))
            let roll = rollDeg * Float.pi / 180
            let cosR = cos(roll), sinR = sin(roll)
            func dirAt(_ x0: Float, _ y0: Float) -> SCNVector3 {
                let x = x0 * cosR - y0 * sinR
                let y = x0 * sinR + y0 * cosR
                return norm(SCNVector3(
                    c.x + east.x * x + north.x * y,
                    c.y + east.y * x + north.y * y,
                    c.z + east.z * x + north.z * y
                ))
            }
            var vertices: [SCNVector3] = []
            var uvs: [CGPoint] = []
            for s in 0...segs {
                let u = Float(s) / Float(segs)
                let x = (u - 0.5) * lengthRad
                // 커튼 밑단의 완만한 물결(S 라인) — 자로 잰 듯한 직선이 되지 않게
                let yWave = 0.10 * sin(u * 6.2832 * 1.5 + p1) + 0.045 * sin(u * 6.2832 * 3.7 + p2)
                let h = heightRad * (0.82 + 0.18 * sin(u * 6.2832 * 2 + p3))
                let botDir = dirAt(x, yWave)
                let topDir = dirAt(x, yWave + h)
                vertices.append(SCNVector3(botDir.x * radius, botDir.y * radius, botDir.z * radius))
                uvs.append(CGPoint(x: CGFloat(u), y: 0))
                vertices.append(SCNVector3(topDir.x * radius, topDir.y * radius, topDir.z * radius))
                uvs.append(CGPoint(x: CGFloat(u), y: 1))
            }
            let indices: [Int32] = Array(0..<Int32(vertices.count))
            let geometry = SCNGeometry(
                sources: [SCNGeometrySource(vertices: vertices), SCNGeometrySource(textureCoordinates: uvs)],
                elements: [SCNGeometryElement(indices: indices, primitiveType: .triangleStrip)]
            )
            let material = SCNMaterial()
            material.lightingModel = .constant
            material.diffuse.contents = auroraTexture(bot: bot, top: top, intensity: intensity)
            material.blendMode = .add
            material.isDoubleSided = true
            material.writesToDepthBuffer = false
            let phase = Double(rnd()) * 6.2832
            // 주름(fold) 3파동 간섭 + 커튼을 훑는 저주파 스윕 — Android AURORA_FS 패리티.
            // 셰이더 모디파이어는 런타임 컴파일이라 실패해도 정적 커튼으로 안전 폴백된다.
            material.shaderModifiers = [
                .surface: """
                float u = _surface.diffuseTexcoord.x;
                float t = u_time * \(speed);
                float f1 = 0.5 + 0.5 * sin(u * 6.2831 * 5.0 + t * 1.00 + \(phase));
                float f2 = 0.5 + 0.5 * sin(u * 6.2831 * 12.0 - t * 1.70 + \(phase * 2.7));
                float f3 = 0.5 + 0.5 * sin(u * 6.2831 * 2.5 + t * 0.45 + \(phase * 1.3));
                float folds = 0.22 + 0.78 * (0.45 * f1 + 0.35 * f2 + 0.20 * f3);
                float sweep = 0.45 + 0.55 * (0.5 + 0.5 * sin(u * 6.2831 * 0.8 + t * 0.12 + \(phase * 0.7)));
                _surface.diffuse.rgb *= folds * sweep;
                """
            ]
            geometry.materials = [material]
            let node = SCNNode(geometry: geometry)
            node.renderingOrder = -9 // 별밭 셸(-12..-10) 다음, 지구(0)보다 먼저 — 배경층
            return node
        }
        // 하늘 곳곳에 4폭 — 반지름(시차)/기울기/길이/색이 모두 달라 서로 다른 장막으로 읽힌다
        return [
            curtain(centerLat: 38, centerLng: -60, radius: 26, lengthRad: 1.9, heightRad: 0.42,
                    rollDeg: -16, bot: green, top: violet, speed: 0.65, intensity: 0.95),
            curtain(centerLat: -24, centerLng: 30, radius: 34, lengthRad: 2.4, heightRad: 0.55,
                    rollDeg: 12, bot: teal, top: indigo, speed: -0.5, intensity: 0.60),
            curtain(centerLat: 8, centerLng: 150, radius: 30, lengthRad: 2.1, heightRad: 0.48,
                    rollDeg: -7, bot: green, top: pink, speed: 0.55, intensity: 0.80),
            curtain(centerLat: -48, centerLng: -150, radius: 38, lengthRad: 2.6, heightRad: 0.60,
                    rollDeg: 18, bot: teal, top: violet, speed: 0.35, intensity: 0.45),
        ]
    }

    /// 오로라 커튼 텍스처 — 정적 성분 베이크: 수직(v)은 아래 가장자리 또렷(smoothstep)
    /// → 위로 갈수록 소멸(pow 1.6) + botColor→topColor 그라데이션, 길이(u)는 양끝 소멸 램프.
    /// additive 라 밝기를 RGB 에 직접 베이크. 주름·스윕 파동은 셰이더 모디파이어가 런타임에 곱한다.
    private static func auroraTexture(
        bot: (CGFloat, CGFloat, CGFloat), top: (CGFloat, CGFloat, CGFloat), intensity: Double
    ) -> UIImage {
        let w = 256, h = 128
        func smoothstep(_ e0: Double, _ e1: Double, _ x: Double) -> Double {
            let t = min(max((x - e0) / (e1 - e0), 0), 1)
            return t * t * (3 - 2 * t)
        }
        var pixels = [UInt8](repeating: 0, count: w * h * 4)
        for y in 0..<h {
            // 텍스처 y=0(이미지 위) = texcoord v=0 = 커튼 아래 가장자리
            let v = Double(y) / Double(h - 1)
            let base = smoothstep(0.0, 0.10, v)
            let fadeUp = pow(1.0 - v, 1.6)
            let mix = smoothstep(0.05, 0.90, v)
            let a = base * fadeUp * intensity
            let r = (Double(bot.0) * (1 - mix) + Double(top.0) * mix) * a
            let g = (Double(bot.1) * (1 - mix) + Double(top.1) * mix) * a
            let b = (Double(bot.2) * (1 - mix) + Double(top.2) * mix) * a
            for x in 0..<w {
                let u = Double(x) / Double(w - 1)
                // 양 끝은 점점 투명해지며 소멸(열린 커튼 — 확 끊기지 않게 긴 램프)
                let ends = smoothstep(0.0, 0.14, u) * smoothstep(1.0, 0.86, u)
                let o = (y * w + x) * 4
                pixels[o] = UInt8(min(255, r * ends * 255))
                pixels[o + 1] = UInt8(min(255, g * ends * 255))
                pixels[o + 2] = UInt8(min(255, b * ends * 255))
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
