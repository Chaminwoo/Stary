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
        static let enterDist: Float = 10.4   // 진입 시작 거리(돌리-인 출발점 — 최대 거리 살짝 바깥)
        static let idleDist: Float = 9.5     // 진입 정착 거리 = 최소 줌(maxDist) — 지구가 가장 작게 보이는 상태
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
        /// 구름 레이어 노드 — 확대(camDist 감소) 시 페이드아웃을 위해 보관.
        private var cloudNode: SCNNode?
        /// 구름 재질 — 낮/밤 uSunDir 매 프레임 갱신용.
        private var cloudMaterial: SCNMaterial?
        /// 유성 스포너 — rootNode(컨테이너 밖 = 회전 무관)에 확률 판정으로 유성을 떨어뜨린다.
        /// 30초마다 25% 판정, 성공하면 낙하가 끝나자마자 대기 없이 즉시 재판정(연속 스트릭 가능).
        private weak var sceneRootNode: SCNNode?
        private var nextMeteorRollAt: TimeInterval = 0
        private var meteorStreak = 0

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
            cloudMaterial?.setValue(NSValue(scnVector3: SCNVector3(rx, wy, wz)), forKey: "uSunDir")
            // 태양 노드 — 광원 방향 하늘(컨테이너 좌표계 = 모델 좌표계)에 위치.
            sunNode?.position = SCNVector3(mx * 45, 0, mz * 45)
            // 구름 레이어 — 확대해서 다가가면 페이드아웃(camDist 2.4 이상 = 완전 표시, 1.7 이하 = 소멸)
            if let clouds = cloudNode {
                let f = min(max((camDist - 1.7) / (2.4 - 1.7), 0), 1)
                clouds.opacity = CGFloat(f * f * (3 - 2 * f))
            }
            // 유성 — 30초마다 25% 확률 판정. 성공하면 낙하 종료 즉시 재판정(운 좋으면 연속),
            // 실패하면 다시 30초 대기. 연속 스트릭은 매번 다른 색(meteorStreak 순번 → tint).
            // (rootNode 소속이라 지구 드래그 회전과 무관하게 화면을 가로지른다)
            if time >= nextMeteorRollAt, let root = sceneRootNode {
                if Double.random(in: 0..<1) < GlobeBuilder.meteorSpawnChance {
                    let tintIdx = meteorStreak % GlobeBuilder.meteorTints.count
                    let (node, dur) = GlobeBuilder.meteorNode(camDist: camDist, tintIndex: tintIdx)
                    root.addChildNode(node)
                    meteorStreak += 1
                    nextMeteorRollAt = time + dur
                } else {
                    meteorStreak = 0
                    nextMeteorRollAt = time + GlobeBuilder.meteorRollInterval
                }
            }
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
            if let clouds = GlobeBuilder.cloudNode() {
                cloudNode = clouds
                cloudMaterial = clouds.geometry?.firstMaterial
                containerNode.addChildNode(clouds)
            }
            for node in GlobeBuilder.starfieldNodes() { containerNode.addChildNode(node) }
            for node in GlobeBuilder.trailNodes() { containerNode.addChildNode(node) }
            // 유성 스포너 — 입장 30초 뒤 첫 확률 판정(이후 renderer 에서 30초마다/연속 스트릭 시 즉시 재판정)
            sceneRootNode = scene.rootNode
            nextMeteorRollAt = CACurrentMediaTime() + GlobeBuilder.meteorRollInterval
            meteorStreak = 0
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
    /// "원본 × 0.45 밝기" 디퓨즈 + "노란 도시 야경 점광" 이미션 텍스처.
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
    /// ⚠️ 법선(단위구 방향 벡터 = Android EARTH_VS 의 `vN = aPos`)을 반드시 포함해야 한다 —
    ///    법선 없는 커스텀 메쉬에 `_surface.normal` 셰이더 모디파이어를 얹으면 파이프라인이
    ///    컴파일되지 않아 지구/구름 노드가 통째로 사라진다(#12 지구 안 보임의 원인).
    private static func sphereGeometry(radius: Float, stacks: Int, slices: Int) -> SCNGeometry {
        var vertices: [SCNVector3] = []
        var normals: [SCNVector3] = []
        var uvs: [CGPoint] = []
        vertices.reserveCapacity((stacks + 1) * (slices + 1))
        normals.reserveCapacity((stacks + 1) * (slices + 1))
        for i in 0...stacks {
            let v = Double(i) / Double(stacks)
            let phi = (90 - 180 * v) * .pi / 180 // 북극 → 남극
            for j in 0...slices {
                let u = Double(j) / Double(slices)
                let lam = (-180 + 360 * u) * .pi / 180
                let n = SCNVector3(
                    Float(cos(phi) * sin(lam)),
                    Float(sin(phi)),
                    Float(cos(phi) * cos(lam))
                )
                normals.append(n)
                vertices.append(SCNVector3(n.x * radius, n.y * radius, n.z * radius))
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
            sources: [
                SCNGeometrySource(vertices: vertices),
                SCNGeometrySource(normals: normals),
                SCNGeometrySource(textureCoordinates: uvs),
            ],
            elements: [SCNGeometryElement(indices: indices, primitiveType: .triangles)]
        )
    }

    /// 지구 디퓨즈(원본 × 0.45 균일 감광 — Android EARTH_BRIGHTNESS 동일)
    /// + 노란 작은 점광(인류의 도시 야경) 이미션 생성. Android EARTH_FS/글로 스프라이트 패리티.
    private static func earthTextures(diaries: [Diary]) -> (night: UIImage, lit: UIImage) {
        let base = loadEarthImage()
        let w = 2048, h = 1024
        let size = CGSize(width: w, height: h)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1

        // 1) 지구: 원본 × 0.45 (Android EARTH_BRIGHTNESS = 0.45f — 낮/밤 광원은 셰이더가 따로 곱한다)
        let night = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            base.draw(in: CGRect(origin: .zero, size: size))
            UIColor.black.withAlphaComponent(0.55).setFill()
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

    // MARK: 구름 레이어

    /// 구름 레이어 — 지구 위(반경 1.012) 대기 셸. NASA 구름맵(흑백, 퍼블릭 도메인)의 밝기를
    /// 알파로 변환해 구름 외엔 완전 투명. 노드 자체를 천천히 Y축 회전시켜 대기가 흐르는 느낌.
    /// 낮/밤 광원은 지구와 같은 셰이더 모디파이어, 확대 시 페이드아웃은 Coordinator 가 opacity 로.
    /// 리소스 누락 시 nil 반환(레이어만 생략 — 크래시 없음). (Android drawClouds/CLOUD_FS 패리티)
    static func cloudNode() -> SCNNode? {
        guard let image = makeCloudImage() else { return nil }
        let geometry = sphereGeometry(radius: 1.012, stacks: 48, slices: 96)
        let material = SCNMaterial()
        material.lightingModel = .constant
        material.diffuse.contents = image
        material.isDoubleSided = false
        material.writesToDepthBuffer = false
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
        material.setValue(NSValue(scnVector3: SCNVector3(0, 0, 1)), forKey: "uSunDir")
        geometry.materials = [material]
        let node = SCNNode(geometry: geometry)
        // 경도 드리프트 — 한 바퀴 ≈ 4.8분(Android CLOUD_DRIFT 0.0035 rev/s 패리티)
        node.runAction(.repeatForever(.rotateBy(x: 0, y: 2 * .pi, z: 0, duration: 286)))
        return node
    }

    /// NASA 구름맵(흑백 JPG)을 RGBA 로 변환 — 밝기 = 구름 밀도 = 알파, 색은 은은한 청백.
    /// "구름 이외 부분 투명" 요구를 알파 채널 베이크로 충족한다.
    private static func makeCloudImage() -> UIImage? {
        guard let path = Bundle.main.path(forResource: "earth_clouds", ofType: "jpg"),
              let src = UIImage(contentsOfFile: path)?.cgImage else { return nil }
        let w = 1024, h = 512 // 알파 변환용 다운스케일(밤 지구 위 은은한 레이어라 충분)
        var pixels = [UInt8](repeating: 0, count: w * h * 4)
        guard let ctx = CGContext(
            data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }
        ctx.draw(src, in: CGRect(x: 0, y: 0, width: w, height: h))
        guard let data = ctx.data else { return nil }
        let buf = data.bindMemory(to: UInt8.self, capacity: w * h * 4)
        for i in 0..<(w * h) {
            let lum = Double(buf[i * 4]) / 255.0            // 흑백이라 R = 밝기
            let a = lum * 0.45                               // 구름 최대 불투명도(은은하게)
            // premultiplied alpha — 은은한 청백(0.62, 0.70, 0.82) 틴트
            pixels[i * 4] = UInt8(min(255, 0.62 * a * 255))
            pixels[i * 4 + 1] = UInt8(min(255, 0.70 * a * 255))
            pixels[i * 4 + 2] = UInt8(min(255, 0.82 * a * 255))
            pixels[i * 4 + 3] = UInt8(min(255, a * 255))
        }
        let out = Data(pixels)
        guard let provider = CGDataProvider(data: out as CFData),
              let cg = CGImage(
                width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
              )
        else { return nil }
        return UIImage(cgImage: cg)
    }

    // MARK: 태양

    /// 태양 — 광원 방향 하늘(반지름 45)에 떠 있는 해: 전용 텍스처(원반+코로나 합성, 색은
    /// 텍스처에 베이크) 단일 빌보드 — 인위적인 십자 광선 없이 부드러운 구체감(Android 패리티).
    /// 위치는 Coordinator 가 매 프레임 광원 방향으로 갱신.
    static func sunNode() -> SCNNode {
        let geom = SCNPlane(width: 5.5, height: 5.5)
        let material = SCNMaterial()
        material.lightingModel = .constant
        material.diffuse.contents = makeSunImage()
        material.blendMode = .add
        material.writesToDepthBuffer = false
        geom.materials = [material]
        let node = SCNNode(geometry: geom)
        node.renderingOrder = -8 // 별밭(-12..-10)·유성(-9) 다음, 지구(0)보다 먼저 — 배경층
        let billboard = SCNBillboardConstraint()
        billboard.freeAxes = .all
        node.constraints = [billboard]
        return node
    }

    /// 태양 텍스처 — 원경 산광 → 금빛 코로나 → 백열 원반을 부드러운 다단 그라데이션으로 겹쳐
    /// 실제 우주에서 보이는 태양처럼(둥근 구체감, 인위적 십자 플레어 없이) 합성한다.
    /// (Android makeSunBitmap 패리티) additive 재질이라 검정 = 투명.
    private static func makeSunImage() -> UIImage {
        let s: CGFloat = 256
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: s, height: s), format: format).image { ctx in
            let cg = ctx.cgContext
            let c = CGPoint(x: s / 2, y: s / 2)
            let space = CGColorSpaceCreateDeviceRGB()
            func glow(_ colors: [UIColor], _ locations: [CGFloat], _ radiusFrac: CGFloat) {
                guard let g = CGGradient(
                    colorsSpace: space, colors: colors.map(\.cgColor) as CFArray, locations: locations
                ) else { return }
                cg.drawRadialGradient(g, startCenter: c, startRadius: 0,
                                      endCenter: c, endRadius: s / 2 * radiusFrac, options: [])
            }
            // 원경 산광 — 아주 넓고 옅게 퍼져 우주 공간 속 광원임을 알려준다
            glow([
                UIColor(red: 0.16, green: 0.10, blue: 0.03, alpha: 0.16),
                UIColor(red: 0.08, green: 0.04, blue: 0.02, alpha: 0.08),
                UIColor.black.withAlphaComponent(0),
            ], [0, 0.5, 1], 1.00)
            // 금빛 코로나 — 중간 반경, 따뜻한 주황빛
            glow([
                UIColor(red: 0.55, green: 0.43, blue: 0.18, alpha: 0.69),
                UIColor(red: 0.46, green: 0.33, blue: 0.19, alpha: 0.31),
                UIColor.black.withAlphaComponent(0),
            ], [0, 0.45, 1], 0.46)
            // 백열 원반 — 다단 그라데이션으로 가장자리를 부드럽게(림 다크닝풍) 마감
            glow([
                UIColor(red: 1.00, green: 0.97, blue: 0.91, alpha: 1.0),
                UIColor(red: 1.00, green: 0.94, blue: 0.78, alpha: 1.0),
                UIColor(red: 1.00, green: 0.85, blue: 0.63, alpha: 0.91),
                UIColor(red: 0.91, green: 0.69, blue: 0.38, alpha: 0.38),
                UIColor.black.withAlphaComponent(0),
            ], [0, 0.55, 0.80, 0.94, 1], 0.20)
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

        func shellImage(count: Int, brightMul: CGFloat, sizeMul: CGFloat, nebula: Bool) -> UIImage {
            UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format).image { ctx in
                UIColor.black.setFill()
                ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
                let cg = ctx.cgContext
                if nebula {
                    // 아주 어두운 성운 글로우 — 배경에 색 온도와 깊이(도형이 아니라 '공간'으로 읽히게)
                    // 레퍼런스의 짙푸른 하늘 — 인디고·블루 워시 3종 추가(Android 패리티)
                    let colors: [(CGFloat, CGFloat, CGFloat)] = [
                        (0.055, 0.030, 0.100), (0.040, 0.050, 0.110), (0.070, 0.030, 0.080),
                        (0.030, 0.050, 0.100), (0.060, 0.040, 0.110), (0.050, 0.020, 0.090),
                        (0.014, 0.034, 0.090), (0.010, 0.028, 0.078), (0.016, 0.040, 0.084),
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

                    // 은하수 — 레퍼런스(references/은하수.jpg) 스타일: 청보라 별바다 위로 흐르는
                    // 핑크·마젠타 빛의 강. 백열 코어 라인 + 마젠타 리본 + 바이올렛 외곽 글로우 +
                    // 골드 응집 + 시안 가장자리 미광 + 암흑 균열(감쇠) + 조밀한 잔별. (Android 패리티)
                    let bandPhase = rnd() * 2 * .pi
                    func bandY(_ u: CGFloat) -> CGFloat {
                        CGFloat(h) * 0.5 + sin(u * 2 * .pi + bandPhase) * CGFloat(h) * 0.16
                    }
                    let coreU = rnd() // 은하핵 위치 — 이 근처가 가장 밝고 두껍다
                    func coreness(_ u: CGFloat) -> CGFloat { // 핵에 가까울수록 1(주기 거리 가우시안)
                        let d = min(abs(u - coreU), 1 - abs(u - coreU))
                        return exp(-d * d * 26)
                    }
                    // 암흑 균열 — 리본 속을 세로로 가르는 어두운 결(레퍼런스 리본 안의 어두운 줄)
                    func riftAtten(_ u: CGFloat, _ dy: CGFloat) -> CGFloat {
                        let d = min(abs(u - coreU), 1 - abs(u - coreU))
                        let strength = exp(-d * d * 20) * 0.72
                        if strength < 0.04 { return 1 }
                        let ang = u * 2 * .pi
                        let center = 12 + 14 * sin(ang * 2.3 + 0.8) + 6 * sin(ang * 5.1)
                        let halfW = 15 + 6 * sin(ang * 3.7 + 2.0)
                        let x = (dy - center) / halfW
                        return 1 - strength * exp(-x * x)
                    }
                    // 얼룩(패치) — 밝은 구름과 옅은 구간의 교차(리본이 살아 숨쉬는 질감)
                    func patch(_ u: CGFloat) -> CGFloat {
                        let ang = u * 2 * .pi
                        let p = 0.5 + 0.5 * sin(ang * 7.3 + 1.7) * sin(ang * 3.1 + 4.2)
                        return 0.70 + 0.45 * p
                    }
                    // ① 백열 코어 라인 — 리본 정중앙을 따라 끊김 없이 이어지는 밝은 백핑크 심줄
                    for i in 0..<96 {
                        let u = (CGFloat(i) + rnd() * 0.6) / 96
                        let cn = coreness(u)
                        let dy = (rnd() + rnd() - 1) * 6
                        let a = riftAtten(u, dy)
                        let base = (0.030 + 0.022 * cn) * (0.40 + 0.60 * a) * patch(u)
                        haze((base, base * 0.86, base * 0.94),
                             CGPoint(x: u * CGFloat(w), y: bandY(u) + dy),
                             27 + rnd() * 18 + cn * 15)
                    }
                    // ② 마젠타 리본 — 코어를 감싸며 흐르는 선명한 핑크 빛의 강(레퍼런스의 주인공)
                    for _ in 0..<150 {
                        let u = rnd()
                        let cn = coreness(u)
                        let dy = (rnd() + rnd() - 1) * 20 * (1 + 0.5 * cn)
                        let a = riftAtten(u, dy)
                        let base = (0.022 + 0.020 * cn) * patch(u) * (0.25 + 0.75 * a)
                        haze((base * 1.00, base * 0.30, base * 0.62),
                             CGPoint(x: u * CGFloat(w), y: bandY(u) + dy),
                             48 + rnd() * 45 + cn * 18)
                    }
                    // ③ 바이올렛 외곽 글로우 — 리본 밖으로 넓게 번지는 보랏빛 숨결
                    for _ in 0..<110 {
                        let u = rnd()
                        let cn = coreness(u)
                        let dy = (rnd() + rnd() - 1) * 49 * (1 + 0.6 * cn)
                        let base = (0.009 + 0.009 * cn) * patch(u)
                        haze((base * 0.62, base * 0.30, base * 0.95),
                             CGPoint(x: u * CGFloat(w), y: bandY(u) + dy),
                             84 + rnd() * 56)
                    }
                    // ④ 골드 응집 — 핵 쪽 리본 가장자리에 배는 따뜻한 금빛(레퍼런스 하단의 주황 구름)
                    for _ in 0..<30 {
                        let u = coreU + (rnd() + rnd() - 1) * 0.088
                        let dy = 17 + abs(rnd() + rnd() - 1) * 26
                        haze((0.052, 0.032, 0.011),
                             CGPoint(x: u * CGFloat(w), y: bandY(u) + dy),
                             42 + rnd() * 51)
                    }
                    // ⑤ 시안 가장자리 미광 — 리본 가장자리를 스치는 청록 결(레퍼런스의 시안 하늘빛)
                    for _ in 0..<26 {
                        let u = rnd()
                        let side: CGFloat = rnd() < 0.5 ? 1 : -1
                        let dy = side * (52 + rnd() * 40)
                        haze((0.007, 0.024, 0.028),
                             CGPoint(x: u * CGFloat(w), y: bandY(u) + dy),
                             66 + rnd() * 48)
                    }
                    cg.setBlendMode(.plusLighter)
                    // ⑥ 잔별 밀집 띠 — 3200개(채택-기각으로 핵 쪽 밀도↑). 청백 위주 + 핑크/골드 소수
                    var placed = 0
                    while placed < 3200 {
                        let u = rnd()
                        let cn = coreness(u)
                        if rnd() > 0.34 + 0.66 * cn { continue }
                        placed += 1
                        let x = u * CGFloat(w)
                        let thick = 1 + 0.7 * cn
                        let spreadScale: CGFloat = (rnd() < 0.62 ? 29 : 75) * thick
                        let dy = (rnd() + rnd() - 1) * spreadScale
                        let y = bandY(u) + dy
                        let a = riftAtten(u, dy)
                        let br = (0.09 + rnd() * 0.30) * (0.75 + 0.50 * cn) *
                            patch(u) * (0.30 + 0.70 * a)
                        let roll = rnd()
                        let tint: (CGFloat, CGFloat, CGFloat) =
                            roll < 0.68 ? (0.90, 0.94, 1.00) :       // 청백
                            roll < 0.90 ? (1.00, 0.68, 0.85) :       // 핑크
                                          (1.00, 0.88, 0.62)          // 골드
                        let r = 0.5 + rnd() * 1.2
                        UIColor(red: br * tint.0, green: br * tint.1,
                                blue: br * tint.2, alpha: 1).setFill()
                        cg.fillEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
                    }
                    // ⑦ 전경 밝은 별 — 띠 위에 도드라지는 큰 별(레퍼런스의 빛나는 점들)
                    for _ in 0..<40 {
                        let u = rnd()
                        let cn = coreness(u)
                        let x = u * CGFloat(w)
                        let y = bandY(u) + (rnd() + rnd() - 1) * 58 * (1 + 0.6 * cn)
                        let br = 0.34 + rnd() * 0.40
                        let r = 1.5 + rnd() * 1.2
                        UIColor(red: br * 0.95, green: br * 0.96, blue: br, alpha: 1).setFill()
                        cg.fillEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
                    }

                    // 별자리 — 황도 12궁(레퍼런스 references/zodiac.avif), 궁마다 고유색.
                    // 디자인(밝은 별 + 희미한 연결선)은 기존 그대로, 색만 궁별로 다르게. (Android GlobeRenderer 패리티)
                    func constellation(center: CGPoint, scale: CGFloat, roll: CGFloat,
                                       tint: (CGFloat, CGFloat, CGFloat),
                                       points: [(CGFloat, CGFloat)], segments: [(Int, Int)]) {
                        // "지금보다 조금 멀리" — Android 반지름 36→42 패리티: 모양은 유지하고
                        // 스케일·점·선을 ~14% 줄이고 살짝 어둡게 해 더 먼 하늘로 읽히게 한다.
                        let far: CGFloat = 0.86
                        let s = scale * far
                        let cosR = cos(roll), sinR = sin(roll)
                        let pts = points.map { p in
                            CGPoint(x: center.x + (p.0 * cosR - p.1 * sinR) * s,
                                    y: center.y - (p.0 * sinR + p.1 * cosR) * s)
                        }
                        // 연결선 50% 더 연하게(0.26 → 0.13), 별 자체도 더 작게(반지름 2.2 → 1.6)
                        cg.setBlendMode(.plusLighter)
                        cg.setStrokeColor(UIColor(red: tint.0 * 0.13, green: tint.1 * 0.13,
                                                  blue: tint.2 * 0.13, alpha: 1).cgColor)
                        cg.setLineWidth(1.1)
                        for s in segments {
                            cg.move(to: pts[s.0]); cg.addLine(to: pts[s.1]); cg.strokePath()
                        }
                        for p in pts {
                            let br = (0.55 + rnd() * 0.25) * 0.88 // 배경보다 또렷한 밝기 + 궁별 틴트
                            UIColor(red: br * (0.45 + 0.55 * tint.0), green: br * (0.45 + 0.55 * tint.1),
                                    blue: br * (0.45 + 0.55 * tint.2), alpha: 1).setFill()
                            cg.fillEllipse(in: CGRect(x: p.x - 1.6, y: p.y - 1.6, width: 3.2, height: 3.2))
                        }
                    }
                    // 12궁 — equirect(2048×1024) 상 경도 30°씩 + 위도 4단 사이클로 골고루 분산
                    // (x=(lng+180)/360·w, y=(90−lat)/180·h — Android 위경도 배치와 동일 지점)
                    // ※ 12궁 별 배치/연결은 references/zodiac.avif 를 별 단위로 판독해 그대로 옮긴 것 —
                    //    임의 수정 금지(수정하려면 레퍼런스와 대조). 좌표 [-1,1] 정규화(y=위), Android 패리티.
                    // 양자리 — 코랄 레드
                    constellation(center: CGPoint(x: 85, y: 216), scale: 26, roll: -0.14,
                                  tint: (1.00, 0.52, 0.42), points: [
                        (-1.0, 0.35), (0.45, 0.15), (0.91, -0.08), (1.0, -0.35),
                    ], segments: [(0, 1), (1, 2), (2, 3)])
                    // 황소자리 — 연두 (두 뿔 + V 히아데스 + 꼬리)
                    constellation(center: CGPoint(x: 256, y: 410), scale: 29, roll: 0.17,
                                  tint: (0.62, 0.95, 0.55), points: [
                        (-0.81, 0.83), (-0.33, 0.4), (-1.0, 0.31), (-0.15, 0.13), (-0.3, -0.11),
                        (-0.14, -0.03), (0.0, -0.04), (-0.11, -0.2), (0.05, -0.18), (0.32, -0.41),
                        (0.91, -0.61), (1.0, -0.83),
                    ], segments: [(0, 1), (1, 3), (3, 5), (5, 7), (2, 4), (4, 7), (7, 8), (6, 8),
                                  (8, 9), (9, 10), (10, 11)])
                    // 쌍둥이자리 — 옐로 (나란한 두 사람 직사각 틀)
                    constellation(center: CGPoint(x: 427, y: 614), scale: 27, roll: -0.24,
                                  tint: (1.00, 0.88, 0.45), points: [
                        (-0.77, 0.8), (-0.34, 0.69), (-0.08, 0.52), (-1.0, 0.39), (0.33, 0.32),
                        (0.73, 0.17), (1.0, 0.17), (-0.91, 0.1), (-0.46, -0.08), (0.65, -0.15),
                        (-0.08, -0.16), (0.45, -0.43), (0.36, -0.8),
                    ], segments: [(0, 1), (1, 2), (2, 4), (4, 5), (5, 6), (5, 9), (9, 11), (11, 12),
                                  (11, 10), (10, 8), (8, 7), (7, 3), (3, 0)])
                    // 게자리 — 은청 (Y 자)
                    constellation(center: CGPoint(x: 597, y: 808), scale: 24, roll: 0.10,
                                  tint: (0.75, 0.85, 1.00), points: [
                        (-1.0, 0.96), (-0.38, 0.16), (-0.2, -0.17), (1.0, -0.51), (-0.42, -0.96),
                    ], segments: [(0, 1), (1, 2), (2, 3), (2, 4)])
                    // 사자자리 — 골드 (낫(머리 갈고리) + 몸통·꼬리)
                    constellation(center: CGPoint(x: 768, y: 216), scale: 27, roll: 0,
                                  tint: (1.00, 0.72, 0.30), points: [
                        (1.0, 0.74), (0.64, 0.84), (0.34, 0.47), (0.4, 0.2), (0.77, 0.06),
                        (-0.51, -0.23), (0.85, -0.35), (-0.38, -0.6), (-1.0, -0.84),
                    ], segments: [(0, 1), (1, 2), (2, 3), (3, 4), (4, 6), (3, 5), (5, 8), (8, 7), (7, 6)])
                    // 처녀자리 — 민트 (사각 몸통 + 좌우 팔 + 꼬리)
                    constellation(center: CGPoint(x: 939, y: 410), scale: 30, roll: 0.21,
                                  tint: (0.55, 1.00, 0.80), points: [
                        (-0.09, 0.75), (1.0, 0.68), (0.17, 0.43), (0.72, 0.34), (0.49, 0.24),
                        (-0.2, 0.0), (-0.4, -0.01), (-0.54, -0.05), (0.28, -0.15), (-1.0, -0.28),
                        (0.23, -0.5), (-0.33, -0.6), (-0.26, -0.68), (-0.6, -0.75),
                    ], segments: [(0, 2), (2, 4), (4, 3), (3, 1), (2, 5), (4, 8), (5, 8), (5, 6),
                                  (6, 7), (7, 9), (8, 10), (10, 11), (11, 12), (12, 13)])
                    // 천칭자리 — 핑크 (삼각 접시 + 두 다리)
                    constellation(center: CGPoint(x: 1109, y: 614), scale: 26, roll: -0.10,
                                  tint: (1.00, 0.62, 0.82), points: [
                        (-0.36, 1.0), (0.56, 0.9), (-0.29, 0.26), (0.87, -0.04), (-0.87, -0.29),
                        (0.39, -0.77), (0.51, -1.0),
                    ], segments: [(0, 1), (0, 2), (0, 3), (1, 3), (2, 4), (3, 5), (5, 6)])
                    // 전갈자리 — 크림슨 (머리 갈래 + 굽은 몸통 + 갈고리 꼬리)
                    constellation(center: CGPoint(x: 1280, y: 808), scale: 29, roll: 0.14,
                                  tint: (1.00, 0.42, 0.48), points: [
                        (0.55, 0.8), (0.96, 0.58), (0.56, 0.35), (0.97, 0.3), (0.38, 0.28),
                        (0.27, 0.16), (1.0, 0.06), (0.04, -0.13), (-0.69, -0.22), (-0.86, -0.43),
                        (-1.0, -0.52), (-0.11, -0.71), (-0.76, -0.78), (-0.42, -0.8),
                    ], segments: [(0, 1), (1, 3), (3, 6), (1, 2), (2, 4), (4, 5), (5, 7), (7, 11),
                                  (11, 13), (13, 12), (12, 10), (10, 9), (9, 8)])
                    // 사수자리 — 퍼플 (주전자 + 활, 레퍼런스 전체 형상)
                    constellation(center: CGPoint(x: 1451, y: 216), scale: 31, roll: -0.17,
                                  tint: (0.72, 0.55, 1.00), points: [
                        (-0.66, 0.82), (0.45, 0.81), (-0.09, 0.77), (-0.25, 0.7), (-0.38, 0.66),
                        (0.31, 0.4), (-0.09, 0.39), (-0.63, 0.35), (0.07, 0.28), (-0.25, 0.28),
                        (0.71, 0.13), (-0.9, 0.13), (0.43, 0.13), (-0.1, 0.12), (-1.0, -0.06),
                        (0.4, -0.09), (0.54, -0.25), (-0.7, -0.44), (-0.3, -0.5), (-0.56, -0.67),
                        (-0.12, -0.82), (1.0, 0.4),
                    ], segments: [(0, 4), (4, 3), (3, 2), (2, 6), (6, 9), (9, 7), (7, 11), (11, 14),
                                  (14, 17), (17, 19), (19, 18), (19, 20), (6, 13), (13, 8), (8, 5),
                                  (5, 1), (5, 12), (12, 15), (15, 16), (12, 10), (10, 21)])
                    // 염소자리 — 틸 (아래로 처진 보트형 삼각)
                    constellation(center: CGPoint(x: 1621, y: 410), scale: 27, roll: 0.07,
                                  tint: (0.45, 0.88, 0.92), points: [
                        (1.0, 0.93), (0.93, 0.59), (0.05, -0.18), (-0.76, -0.54), (-1.0, -0.75),
                        (-0.63, -0.81), (-0.21, -0.93), (0.63, -0.93), (0.69, -0.75),
                    ], segments: [(0, 1), (1, 2), (2, 3), (3, 4), (4, 5), (5, 6), (6, 7), (7, 8), (8, 1)])
                    // 물병자리 — 블루 (긴 팔 + 물줄기 지그재그)
                    constellation(center: CGPoint(x: 1792, y: 614), scale: 27, roll: -0.07,
                                  tint: (0.50, 0.72, 1.00), points: [
                        (0.72, 1.0), (-0.52, 0.21), (0.39, 0.05), (-0.05, -0.02), (-0.55, -0.12),
                        (-0.72, -0.14), (-0.72, -0.4), (0.19, -0.58), (-0.17, -0.63), (0.65, -0.79),
                        (-0.32, -1.0),
                    ], segments: [(0, 1), (1, 3), (3, 2), (1, 4), (4, 5), (5, 6), (6, 10), (10, 8),
                                  (8, 7), (7, 9)])
                    // 물고기자리 — 라벤더 (서쪽 물고기 고리 + 두 끈이 만나는 매듭)
                    constellation(center: CGPoint(x: 1940, y: 808), scale: 30, roll: 0.24,
                                  tint: (0.82, 0.70, 1.00), points: [
                        (-0.02, 1.0), (0.18, 0.91), (0.24, 0.7), (0.07, 0.57), (-0.07, 0.6),
                        (-0.23, 0.69), (-0.19, 0.88), (-0.03, 0.32), (0.21, -0.08), (0.26, -0.36),
                        (0.64, -0.77), (0.83, -1.0), (0.53, -0.93), (0.06, -0.82), (-0.36, -0.82),
                        (-0.54, -1.0), (-0.83, -0.85),
                    ], segments: [(0, 1), (1, 2), (2, 3), (3, 4), (4, 5), (5, 6), (6, 0), (4, 7),
                                  (7, 8), (8, 9), (9, 10), (10, 11), (11, 12), (12, 13), (13, 14),
                                  (14, 15), (15, 16)])

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
                    // sizeMul: 먼 셸일수록 작게 — 원근감(Android 셸 sizeBase 차등 패리티)
                    let r = (0.8 + big * big * 2.6) * sizeMul // 대부분 잔별, 소수만 크게
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

        func shell(radius: CGFloat, count: Int, brightMul: CGFloat, sizeMul: CGFloat, nebula: Bool, order: Int) -> SCNNode {
            let sphere = SCNSphere(radius: radius)
            sphere.segmentCount = 32
            let material = SCNMaterial()
            material.lightingModel = .constant
            material.diffuse.contents = shellImage(count: count, brightMul: brightMul, sizeMul: sizeMul, nebula: nebula)
            material.blendMode = .add          // 검정 배경 = 투명
            material.cullMode = .front         // 구 안쪽 면을 렌더
            material.writesToDepthBuffer = false
            sphere.materials = [material]
            let node = SCNNode(geometry: sphere)
            node.renderingOrder = order        // 지구보다 먼저(뒤에) 그리기
            return node
        }

        return [
            // 레퍼런스(references/은하수.jpg)의 "별이 가득한 하늘" — 셸 밀도 상향(Android 패리티).
            // sizeMul: 먼 셸일수록 별을 작게 그려 원근감(가까움=굵고 또렷 / 멂=잘게 반짝).
            shell(radius: 12, count: 460, brightMul: 1.00, sizeMul: 1.15, nebula: false, order: -12), // 근경
            shell(radius: 22, count: 900, brightMul: 0.76, sizeMul: 0.95, nebula: false, order: -11), // 중경
            shell(radius: 38, count: 1400, brightMul: 0.56, sizeMul: 0.75, nebula: true, order: -10), // 원경 + 은하수
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
        var normals: [SCNVector3] = []
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
                // 법선 = 방사 방향(원점 밖 스트립이라 0 벡터 없음) — 조명은 .constant 라 값 자체는
                // 안 쓰지만, 법선 없는 메쉬 + 셰이더 모디파이어 조합은 파이프라인 컴파일에 실패한다.
                let len = max(sqrt(p2.x * p2.x + p2.y * p2.y + p2.z * p2.z), 0.001)
                normals.append(SCNVector3(p2.x / len, p2.y / len, p2.z / len))
                uvs.append(CGPoint(x: CGFloat(u), y: CGFloat(k)))
            }
        }
        let indices: [Int32] = Array(0..<Int32(vertices.count))
        let geometry = SCNGeometry(
            sources: [
                SCNGeometrySource(vertices: vertices),
                SCNGeometrySource(normals: normals),
                SCNGeometrySource(textureCoordinates: uvs),
            ],
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

    // MARK: 유성(별똥별)

    /// 유성 확률 판정 주기(초) / 판정 성공 확률. 입장 후 이 주기마다 판정하고,
    /// 성공하면 낙하가 끝나자마자(대기 없이) 곧바로 재판정한다(연속 스트릭).
    /// (Android GlobeRenderer METEOR_ROLL_INTERVAL/METEOR_SPAWN_CHANCE 패리티)
    static let meteorRollInterval: TimeInterval = 30
    static let meteorSpawnChance: Double = 0.25

    /// 유성 스트릭 색상 팔레트 — 연속으로 떨어질 때마다 순서대로 바뀐다(0=기본 청백).
    /// (Android METEOR_TINTS 패리티)
    static let meteorTints: [(CGFloat, CGFloat, CGFloat)] = [
        (1.00, 1.00, 1.00), // 기본 청백
        (1.00, 0.60, 0.32), // 주황
        (0.55, 1.00, 0.62), // 초록
        (1.00, 0.45, 0.85), // 핑크
        (1.00, 0.86, 0.32), // 골드
        (0.62, 0.58, 1.00), // 보라
    ]

    /// 유성 스트릭 텍스처(팔레트별 사전 생성 캐시) — 왼쪽(꼬리)은 투명해지는 색,
    /// 오른쪽(머리)은 밝은 발광. 부드러운 radial blob 체인으로 그려 가장자리가 자연스럽다.
    private static let meteorStreakTextures: [UIImage] = meteorTints.map { tint in
        let w = 256, h = 32
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format).image { ctx in
            let cg = ctx.cgContext
            let space = CGColorSpaceCreateDeviceRGB()
            func blob(_ center: CGPoint, _ radius: CGFloat, _ color: UIColor) {
                guard let g = CGGradient(
                    colorsSpace: space,
                    colors: [color.cgColor, color.withAlphaComponent(0).cgColor] as CFArray,
                    locations: [0, 1]
                ) else { return }
                cg.drawRadialGradient(g, startCenter: center, startRadius: 0,
                                      endCenter: center, endRadius: radius, options: [])
            }
            cg.setBlendMode(.plusLighter)
            for i in 0..<24 {
                let f = CGFloat(i) / 23 // 0=꼬리 끝 → 1=머리
                let x = 6 + f * (CGFloat(w) - 26)
                let r = 2 + f * 9
                let a = 0.05 + f * f * 0.55
                blob(CGPoint(x: x, y: CGFloat(h) / 2), r,
                     UIColor(red: (0.62 + 0.38 * f) * tint.0, green: (0.72 + 0.28 * f) * tint.1,
                             blue: 1.0 * tint.2, alpha: a))
            }
            // 머리 코어 — 밝은 발광(팔레트 색으로 tint)
            blob(CGPoint(x: CGFloat(w) - 20, y: CGFloat(h) / 2), 13,
                 UIColor(red: tint.0, green: tint.1, blue: tint.2, alpha: 0.95))
        }
    }

    /// 잔류 스파클 파티클 텍스처 — 작은 라디얼 글로우(백색, particleColor 로 tint).
    private static let meteorSparkTexture: UIImage = {
        let s = 32
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: s, height: s), format: format).image { ctx in
            let cg = ctx.cgContext
            let space = CGColorSpaceCreateDeviceRGB()
            let c = CGPoint(x: CGFloat(s) / 2, y: CGFloat(s) / 2)
            if let g = CGGradient(
                colorsSpace: space,
                colors: [UIColor.white.cgColor,
                         UIColor.white.withAlphaComponent(0.4).cgColor,
                         UIColor.white.withAlphaComponent(0).cgColor] as CFArray,
                locations: [0, 0.35, 1]
            ) {
                cg.drawRadialGradient(g, startCenter: c, startRadius: 0,
                                      endCenter: c, endRadius: CGFloat(s) / 2, options: [])
            }
        }
    }()

    /// 유성 노드 — 실제 3D 우주공간(rootNode 좌표 — 컨테이너 회전 무관)을 **약간의 곡선**을
    /// 그리며 가로지른다(경로 수직 방향 2차 휨: p(s)=p0+dir·travel·s+perp·bend·s²).
    ///
    /// 연출(3D 디자인 개편, Android GlobeRenderer drawMeteor/spawnMeteor 패리티):
    /// - 커스텀 액션으로 매 프레임 곡선 위 위치·접선 방향을 갱신(꼬리가 궤적을 따라 휜다).
    /// - 머리 밝기가 고주파로 떨리는 **화려한 반짝임** + 스트릭 2색 텍스처.
    /// - **잔류 스파클**: 월드 공간 방출 SCNParticleSystem — 지나간 자리에 색색의 파편이 남아
    ///   1~2초 반짝이다 사그라든다(낙하 종료 후에도 잔류, 수명 끝나면 노드 자체 제거).
    /// 반환값의 duration 은 호출부가 "낙하가 끝나는 즉시 재판정"하기 위한 스케줄링에 쓰인다.
    static func meteorNode(camDist: Float, tintIndex: Int) -> (node: SCNNode, duration: Double) {
        let tint = meteorTints[tintIndex % meteorTints.count]
        let depth = Float.random(in: 20...36)  // 카메라~통과지점 거리(별밭 셸 사이)
        let kY = depth * 0.384                 // tan(FOV 42°/2) ≈ 그 깊이에서 화면 세로 반높이
        let bounds = UIScreen.main.bounds
        let kX = kY * Float(bounds.width / max(bounds.height, 1))
        let streak = kY * Float.random(in: 0.34...0.50) // 스트릭 길이(기존 대비 2배 — 더 길게)
        let plane = SCNPlane(width: CGFloat(streak), height: CGFloat(streak) * 0.10)
        let material = SCNMaterial()
        material.lightingModel = .constant
        material.diffuse.contents = meteorStreakTextures[tintIndex % meteorStreakTextures.count]
        material.blendMode = .add
        material.isDoubleSided = true
        material.writesToDepthBuffer = false
        plane.materials = [material]
        let streakNode = SCNNode(geometry: plane)
        streakNode.opacity = 0
        streakNode.renderingOrder = -9 // 배경층(별밭 셸 다음, 지구보다 먼저 — 지구 뒤로 가려짐)

        // ── 화면 기준 사선 횡단 경로(Android spawnMeteor 패리티) ──
        // 좌우 어느 한쪽 화면 밖, 상단 ~10% 높이에서 출발해 반대쪽 화면 밖, 하단 50~90%로
        // 빠져나간다(좌→우/우→좌 랜덤). 끝점이 화면 밖 → 중간 소멸 없이 퇴장으로만 사라진다.
        let midZ = camDist - depth              // 카메라(+z) 앞쪽(-z 방향)
        let leftToRight = Bool.random()
        let xEdge = kX * 1.30                   // 화면 가장자리 살짝 밖(꼬리까지 퇴장 여유)
        let x0: Float = leftToRight ? -xEdge : xEdge
        let x1: Float = -x0
        let fTop = Float.random(in: 0.06...0.14)   // 시작 = 상단 ~10%
        let fEnd = Float.random(in: 0.50...0.90)   // 도착 = 하단 50~90%
        let y0 = kY * (1 - 2 * fTop)
        let y1 = kY * (1 - 2 * fEnd)
        let z0 = midZ + Float.random(in: -0.15...0.15) * kY // 깊이 변화(원근, 작게)
        let z1 = midZ - (z0 - midZ)
        let dxv = x1 - x0, dyv = y1 - y0, dzv = z1 - z0
        let travel = sqrt(dxv * dxv + dyv * dyv + dzv * dzv)
        let dx = dxv / travel, dy = dyv / travel, dz = dzv / travel
        // 아치 휨 — 진행 방향에 수직인 화면면 방향. **항상 위쪽(+y)으로 불룩하게 고정**(Android
        // spawnMeteor 패리티) — 부호를 랜덤으로 두면 절반은 "중력이 반대로" 보였다. 실제 포물선은
        // 초반엔 완만하다가 갈수록 가파르게 떨어지므로, 직선 경로 기준 항상 위로 볼록해야
        // "위에서 아래로 중력이 당기는" 자연스러운 낙하로 읽힌다. p(s)=p0+dir·len·s+perp·bend·4s(1-s)
        var px = dy, py = -dx
        let pl = sqrt(px * px + py * py)
        if pl < 0.15 { px = 0; py = 1 } else { px /= pl; py /= pl }
        if py < 0 { px = -px; py = -py } // 항상 +y 쪽으로
        let bend = travel * Float.random(in: 0.05...0.12)
        func pathAt(_ s: Float) -> SCNVector3 {
            let arc = 4 * s * (1 - s)
            return SCNVector3(x0 + dxv * s + px * bend * arc,
                              y0 + dyv * s + py * bend * arc,
                              z0 + dzv * s)
        }
        streakNode.position = pathAt(0)
        streakNode.eulerAngles = SCNVector3(0, 0, atan2(dy, dx))

        // ── 잔류 파장(wake) — 보트가 지나간 뒤의 물결처럼 5~10초 남아 일렁이다 사그라든다.
        //    월드 공간 방출(isLocal=false)이라 지나간 자리에 그대로 남는다. ──
        let trail = SCNParticleSystem()
        trail.birthRate = 60
        trail.particleLifeSpan = 7.5
        trail.particleLifeSpanVariation = 2.5      // 5~10초
        trail.particleSize = CGFloat(streak) * 0.11
        trail.particleSizeVariation = CGFloat(streak) * 0.06
        trail.particleVelocity = CGFloat(streak) * 0.035  // 아주 천천히 벌어지는 물결
        trail.particleVelocityVariation = CGFloat(streak) * 0.025
        trail.spreadingAngle = 180
        trail.particleImage = meteorSparkTexture
        trail.particleColor = UIColor(red: tint.0, green: tint.1, blue: tint.2, alpha: 1)
        trail.particleColorVariation = SCNVector4(0.14, 0.30, 0.10, 0) // 색상·채도 흔들림 = 색색 물결
        trail.blendMode = .additive
        trail.isAffectedByGravity = false
        trail.isLocal = false // 핵심 — 파티클이 방출 지점(월드)에 남는다
        // 수명 곡선 — 지나간 직후 피어오르고, 긴 시간 물결처럼 잦아든다
        let sparkFade = CAKeyframeAnimation()
        sparkFade.values = [0.0, 0.9, 0.55, 0.28, 0.0]
        sparkFade.keyTimes = [0, 0.10, 0.45, 0.75, 1]
        // 크기 곡선 — 파장이 퍼지듯 서서히 커진다
        let sparkGrow = CABasicAnimation()
        sparkGrow.fromValue = 0.7
        sparkGrow.toValue = 1.7
        trail.propertyControllers = [
            .opacity: SCNParticlePropertyController(animation: sparkFade),
            .size: SCNParticlePropertyController(animation: sparkGrow),
        ]
        let emitter = SCNNode()
        emitter.position = streakNode.position
        emitter.addParticleSystem(trail)

        let container = SCNNode()
        container.addChildNode(streakNode)
        container.addChildNode(emitter)

        let dur = Double.random(in: 1.5...2.2)     // 화면 횡단(0→1) 시간
        // 꼬리(streak)까지 화면 밖으로 완전히 퇴장할 만큼 s 를 더 진행시킨다.
        // (꼬리가 길어질수록 더 멀리 가야 완전히 빠져나간다 — Android METEOR_TAIL_FRAC 패리티)
        let sMax: Float = 1 + streak / travel + 0.08
        let total = dur * Double(sMax)
        // 곡선 이동 + 접선 정렬 + 머리 반짝임 — 매 프레임 갱신
        let move = SCNAction.customAction(duration: total) { _, elapsed in
            let s = Float(elapsed) / Float(total) * sMax
            let p = pathAt(s)
            streakNode.position = p
            if s <= 1.02 { emitter.position = p }  // 방출은 화면 안 구간에서만
            let tx = dxv + px * bend * (4 - 8 * s)
            let ty = dyv + py * bend * (4 - 8 * s)
            streakNode.eulerAngles = SCNVector3(0, 0, atan2(ty, tx))
            // 화려한 반짝임 — 머리 밝기가 고주파로 미세하게 떨린다
            let tw = 0.84 + 0.16 * sin(Double(s) * 46 + Double(tintIndex) * 1.7)
            streakNode.geometry?.firstMaterial?.transparency = CGFloat(tw)
        }
        // 중간 소멸 없음 — 빠르게 점화한 뒤 화면 밖 퇴장으로만 사라진다
        streakNode.runAction(.fadeIn(duration: dur * 0.08))
        container.runAction(.sequence([
            move,
            .run { _ in trail.birthRate = 0 }, // 방출 중단 — 잔류 파장은 남아 잦아든다
            .wait(duration: 10.5),             // 파장 최대 수명만큼 대기 후 정리
            .removeFromParentNode(),
        ]))
        return (container, dur)
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
