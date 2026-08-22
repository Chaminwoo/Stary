import SwiftUI

/// 실제 하늘 반영 오버레이 — **여명·황혼**을 지도 위에 그린다.
/// (Android `feature/map/screen/SkyOverlay.kt` 패리티 — 같은 계산·같은 수치)
///
/// 해가 지평선 근처일 때(뜨기 직전/진 직후) 화면 아래쪽이 따뜻하게 물들어,
/// 늘 똑같던 밤하늘이 시간대에 따라 조금씩 달라 보인다.
///
/// 장식 전용(`allowsHitTesting(false)`) — 지도 조작을 가리지 않는다.
struct SkyOverlay: View {
    /// 태양 고도 계산용 현재 좌표(nil 이면 아무것도 그리지 않는다).
    var latitude: Double?
    var longitude: Double?

    @State private var twilight: Double = 0

    /// 하늘 상태 재계산 주기(s).
    private let refresh = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    var body: some View {
        Canvas { ctx, size in
            // 여명/황혼: 화면 아래쪽(지평선 방향)에서 따뜻하게 차오른다.
            guard twilight > 0.01 else { return }
            let grad = Gradient(stops: [
                .init(color: .clear, location: 0),
                .init(color: Color(hex: 0x3A1E3B).opacity(0.16 * twilight), location: 0.55),
                .init(color: Color(hex: 0x8C3B2E).opacity(0.22 * twilight), location: 0.85),
                .init(color: Color(hex: 0xE08A4B).opacity(0.26 * twilight), location: 1),
            ])
            ctx.fill(Path(CGRect(origin: .zero, size: size)),
                     with: .linearGradient(grad,
                                           startPoint: .zero,
                                           endPoint: CGPoint(x: 0, y: size.height)))
        }
        .allowsHitTesting(false)
        .onAppear { refreshSky() }
        .onReceive(refresh) { _ in refreshSky() }
    }

    private func refreshSky() {
        guard let lat = latitude, let lng = longitude else {
            twilight = 0
            return
        }
        let nowMs = Date().timeIntervalSince1970 * 1000
        twilight = SkyAlmanac.twilightStrength(
            sunAltitudeDeg: SkyAlmanac.sunAltitudeDeg(nowMs: nowMs, latitude: lat, longitude: lng))
    }
}
