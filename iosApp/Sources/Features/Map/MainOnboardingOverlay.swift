import SwiftUI

/// 첫 실행 코치마크
///
/// 단계:
/// 0 필터
/// 1 내 위치
/// 2 별자리
/// 3 몰입
/// 4 업로드 FAB
/// 5 메뉴
/// 6 마무리
///
/// 모든 버튼 좌표는 실제 버튼의 .global 좌표를 MainTabView에서 전달받는다.
/// 따라서 기기 크기 / safe area / MapScreen 높이 / topBar 높이가 달라도
/// 임의의 좌표 계산 없이 실제 버튼과 정확히 일치한다.
struct MainOnboardingOverlay: View {

    // MARK: - 실제 버튼 좌표

    let filterCenter: CGPoint
    let locationCenter: CGPoint
    let constellationCenter: CGPoint
    let eyeCenter: CGPoint
    let uploadCenter: CGPoint
    let menuCenter: CGPoint

    // MARK: - 종료

    let onDismiss: () -> Void

    // MARK: - 상태

    @State private var step = 0
    @State private var visible = false

    /// 애니메이션 중인 스포트라이트 중심
    @State private var spotX: CGFloat = 0
    @State private var spotY: CGFloat = 0
    @State private var spotR: CGFloat = 0

    /// 말풍선 실측 크기 — 스포트라이트 원 옆/위/아래에 정확히 붙이려면 크기를 먼저 알아야 한다.
    @State private var pillSize: CGSize = .zero

    private let stepCount = 7

    // MARK: - 반지름

    private let filterRadius: CGFloat = 24
    private let mapButtonRadius: CGFloat = 24
    private let uploadRadius: CGFloat = 28
    private let menuRadius: CGFloat = 24

    var body: some View {

        GeometryReader { geo in

            // 버튼 좌표는 `.frame(in: .global)` 로 잰 값이고, 이 오버레이는 `.ignoresSafeArea()` 로
            // 화면 전체를 덮는다. 두 좌표계의 원점이 어긋나면(상단 안전영역만큼) 스포트라이트가
            // 실제 버튼보다 **위로 밀려** 보인다 → 오버레이 자신의 global 원점을 빼서 항상 맞춘다.
            // (원점이 같으면 0 이라 아무 영향 없음 — 어떤 기기/OS 에서도 안전한 보정.)
            let originGlobal = geo.frame(in: .global).origin

            ZStack {

                // =========================================================
                // 1. 어두운 스크림
                // =========================================================

                ZStack {

                    Color.black.opacity(0.78)

                    if spotR > 0 {

                        let soft = spotR * 1.35

                        RadialGradient(
                            stops: [
                                .init(
                                    color: .white,
                                    location: 0.0
                                ),
                                .init(
                                    color: .white,
                                    location: 0.62
                                ),
                                .init(
                                    color: .clear,
                                    location: 1.0
                                )
                            ],
                            center: .center,
                            startRadius: 0,
                            endRadius: soft
                        )
                        .frame(
                            width: soft * 2,
                            height: soft * 2
                        )
                        .position(
                            x: spotX - originGlobal.x,
                            y: spotY - originGlobal.y
                        )
                        .blendMode(.destinationOut)
                    }
                }
                .compositingGroup()
                .contentShape(Rectangle())
                .onTapGesture {
                    advance()
                }

                // =========================================================
                // 2. 강조 링
                // =========================================================

                if spotR > 0 {

                    Circle()
                        .stroke(
                            Color(hex: 0x6EE7B7).opacity(0.55),
                            lineWidth: 2
                        )
                        .frame(
                            width: spotR * 2,
                            height: spotR * 2
                        )
                        .position(
                            x: spotX - originGlobal.x,
                            y: spotY - originGlobal.y
                        )
                        .allowsHitTesting(false)
                }

                // =========================================================
                // 3. 안내 말풍선
                // =========================================================

                pillLayer(
                    in: geo.size,
                    origin: originGlobal
                )
                .animation(
                    .easeOut(duration: 0.26),
                    value: step
                )

                // =========================================================
                // 4. 건너뛰기
                // =========================================================

                Text("건너뛰기")
                    .font(.minSans(13))
                    .foregroundStyle(.white.opacity(0.6))
                    .padding(6)
                    .frame(
                        maxWidth: .infinity,
                        maxHeight: .infinity,
                        alignment: .topTrailing
                    )
                    .padding(.top, 8)
                    .padding(.trailing, 14)
                    .allowsHitTesting(true)
                    .onTapGesture {
                        finish()
                    }
            }
            .opacity(visible ? 1 : 0)
            .onAppear {

                print("========== COACH MARK ==========")
                print("Overlay geometry:", geo.size)
                print("Overlay global origin:", originGlobal)
                print("Screen:", UIScreen.main.bounds)
                print("filter:", filterCenter)
                print("location:", locationCenter)
                print("constellation:", constellationCenter)
                print("eye:", eyeCenter)
                print("upload:", uploadCenter)
                print("menu:", menuCenter)
                print("================================")

                // 실제 좌표가 아직 전달되지 않았을 경우
                // 일단 화면 중앙에 표시하지 않고 대기한다.
                updateInitialPosition()

                withAnimation(.easeIn(duration: 0.28)) {
                    visible = true
                }
            }
            .onChange(of: filterCenter) { _ in
                if step == 0 {
                    updatePosition(for: 0, animated: false)
                }
            }
            .onChange(of: locationCenter) { _ in
                if step == 1 {
                    updatePosition(for: 1, animated: false)
                }
            }
            .onChange(of: constellationCenter) { _ in
                if step == 2 {
                    updatePosition(for: 2, animated: false)
                }
            }
            .onChange(of: eyeCenter) { _ in
                if step == 3 {
                    updatePosition(for: 3, animated: false)
                }
            }
            .onChange(of: uploadCenter) { _ in
                if step == 4 {
                    updatePosition(for: 4, animated: false)
                }
            }
            .onChange(of: menuCenter) { _ in
                if step == 5 {
                    updatePosition(for: 5, animated: false)
                }
            }
        }
        .frame(
            maxWidth: .infinity,
            maxHeight: .infinity
        )
        .ignoresSafeArea()
    }

    // MARK: - 좌표 선택

    private func center(for step: Int) -> CGPoint {

        switch step {

        case 0:
            return filterCenter

        case 1:
            return locationCenter

        case 2:
            return constellationCenter

        case 3:
            return eyeCenter

        case 4:
            return uploadCenter

        case 5:
            return menuCenter

        default:
            return .zero
        }
    }

    private func radius(for step: Int) -> CGFloat {

        switch step {

        case 0:
            return filterRadius

        case 1, 2, 3:
            return mapButtonRadius

        case 4:
            return uploadRadius

        case 5:
            return menuRadius

        default:
            return 0
        }
    }

    // MARK: - 최초 위치

    private func updateInitialPosition() {

        let p = center(for: 0)

        guard p != .zero else {
            return
        }

        spotX = p.x
        spotY = p.y
        spotR = radius(for: 0)
    }

    // MARK: - 위치 업데이트

    private func updatePosition(
        for targetStep: Int,
        animated: Bool
    ) {

        if targetStep == 6 {

            if animated {

                withAnimation(
                    .easeOut(duration: 0.34)
                ) {
                    spotR = 0
                }

            } else {

                spotR = 0
            }

            return
        }

        let p = center(for: targetStep)

        guard p != .zero else {
            return
        }

        let r = radius(for: targetStep)

        if animated {

            withAnimation(
                .easeOut(duration: 0.34)
            ) {
                spotX = p.x
                spotY = p.y
                spotR = r
            }

        } else {

            spotX = p.x
            spotY = p.y
            spotR = r
        }
    }

    // MARK: - 말풍선

    /// 이 단계의 문구.
    private func pillText(for step: Int) -> String {

        switch step {

        case 0:
            return "보고 싶은 다이어리만 골라서 볼 수 있어요"

        case 1:
            return "시점을 현재 내 위치로 이동해요"

        case 2:
            return "별들을 이어 별자리를 만들어요"

        case 3:
            return "지도에만 집중해서 별들을 감상해요"

        case 4:
            return "이 버튼을 눌러 다이어리를 올려요"

        case 5:
            return "내 다이어리 · 프로필 · 업적 · 친구 등\n여러 설정을 여기서 관리해요"

        default:
            return "지금부터 우주를 탐험하고,\n별들에 이야기를 남겨보세요!"
        }
    }

    /// 말풍선을 원의 어느 쪽에 붙일지 — 필터는 화면 맨 아래라 위로, 메뉴는 맨 위라 아래로,
    /// 우측 FAB 컬럼(1~4)은 왼쪽 옆에.
    private func pillSide(for step: Int) -> CoachPillSide {

        switch step {

        case 0:
            return .above

        case 1, 2, 3, 4:
            return .leftOf

        case 5:
            return .below

        default:
            return .center
        }
    }

    /// 말풍선 **중심** 좌표(오버레이 로컬).
    ///
    /// ⚠️ 예전에는 말풍선을 화면 가장자리 기준 고정 padding 으로 놓았다 — 스포트라이트는 실제 버튼
    ///    좌표를 따라가는데 말풍선만 상수라, 지도 버튼이 바뀌거나 기기 크기가 다르면 둘이 어긋났다.
    ///    지금은 원 좌표 + 말풍선 실측 크기에서 계산하므로 항상 붙어 있다.
    ///    (Android `MainOnboardingOverlay.AnchoredCoachPill` 과 동일한 규칙)
    private func pillCenter(
        in size: CGSize,
        origin: CGPoint
    ) -> CGPoint {

        let margin: CGFloat = 10   // 화면 가장자리 최소 여백
        let gap: CGFloat = 12      // 원 테두리와 말풍선 사이 간격

        let sx = spotX - origin.x
        let sy = spotY - origin.y
        let w = pillSize.width
        let h = pillSize.height

        var cx: CGFloat
        var cy: CGFloat

        switch pillSide(for: step) {

        case .leftOf:
            cx = sx - spotR - gap - w / 2
            cy = sy

        case .above:
            cx = sx - spotR + w / 2
            cy = sy - spotR - gap - h / 2

        case .below:
            cx = sx - spotR + w / 2
            cy = sy + spotR + gap + h / 2

        case .center:
            cx = size.width / 2
            cy = size.height / 2
        }

        let minX = margin + w / 2
        let maxX = max(minX, size.width - margin - w / 2)
        let minY = margin + h / 2
        let maxY = max(minY, size.height - margin - h / 2)

        return CGPoint(
            x: min(max(cx, minX), maxX),
            y: min(max(cy, minY), maxY)
        )
    }

    @ViewBuilder
    private func pillLayer(
        in size: CGSize,
        origin: CGPoint
    ) -> some View {

        let center = pillCenter(in: size, origin: origin)

        CoachPill(
            pillText(for: step),
            big: step >= stepCount - 1
        )
        .background(
            // 크기 실측 — 재기 전(=.zero)에는 숨겨서 좌상단에 한 프레임 번쩍이지 않게 한다.
            GeometryReader { g in
                Color.clear.preference(
                    key: CoachPillSizeKey.self,
                    value: g.size
                )
            }
        )
        .onPreferenceChange(CoachPillSizeKey.self) { newSize in
            if newSize != .zero {
                pillSize = newSize
            }
        }
        .position(center)
        .opacity(pillSize == .zero ? 0 : 1)
        .allowsHitTesting(false)
    }

    // MARK: - 다음 단계

    private func advance() {

        let next = step + 1

        if next >= stepCount {
            finish()
            return
        }

        updatePosition(
            for: next,
            animated: true
        )

        step = next
    }

    // MARK: - 종료

    private func finish() {

        withAnimation(
            .easeOut(duration: 0.28)
        ) {
            visible = false
        }

        DispatchQueue.main.asyncAfter(
            deadline: .now() + 0.3
        ) {
            onDismiss()
        }
    }
}

// MARK: - 말풍선 배치

/// 말풍선을 스포트라이트 원의 어느 쪽에 붙일지(Android `PillSide` 와 동일).
enum CoachPillSide {

    case above
    case below
    case leftOf
    case center
}

/// 말풍선 실측 크기 전달용.
struct CoachPillSizeKey: PreferenceKey {

    static var defaultValue: CGSize = .zero

    static func reduce(
        value: inout CGSize,
        nextValue: () -> CGSize
    ) {
        let next = nextValue()
        if next != .zero {
            value = next
        }
    }
}

// MARK: - 안내 말풍선

private struct CoachPill: View {

    let text: String
    var big: Bool = false

    init(
        _ text: String,
        big: Bool = false
    ) {
        self.text = text
        self.big = big
    }

    private var accent: LinearGradient {

        LinearGradient(
            colors: [
                Color(hex: 0x6EE7B7),
                Color(hex: 0x3B82F6)
            ],
            startPoint: .leading,
            endPoint: .trailing
        )
    }

    var body: some View {

        Text(text)
            .font(.minSans(big ? 17 : 14))
            .foregroundStyle(.white)
            .lineSpacing(big ? 8 : 5)
            .multilineTextAlignment(.center)
            .padding(
                .horizontal,
                big ? 22 : 14
            )
            .padding(
                .vertical,
                big ? 18 : 10
            )
            .frame(
                maxWidth: big ? 300 : 232,
                alignment: .center
            )
            .background(
                Color(hex: 0x14181C),
                in: RoundedRectangle(
                    cornerRadius: big ? 18 : 14
                )
            )
            .overlay(
                RoundedRectangle(
                    cornerRadius: big ? 18 : 14
                )
                .stroke(
                    accent,
                    lineWidth: big ? 1.5 : 1
                )
            )
    }
}
