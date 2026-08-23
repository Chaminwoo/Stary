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

    private let stepCount = 7

    // MARK: - 반지름

    private let filterRadius: CGFloat = 24
    private let mapButtonRadius: CGFloat = 24
    private let uploadRadius: CGFloat = 28
    private let menuRadius: CGFloat = 24

    var body: some View {

        GeometryReader { geo in

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
                            x: spotX,
                            y: spotY
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
                            x: spotX,
                            y: spotY
                        )
                        .allowsHitTesting(false)
                }

                // =========================================================
                // 3. 안내 말풍선
                // =========================================================

                pillLayer()
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

    @ViewBuilder
    private func pillLayer() -> some View {

        ZStack {

            switch step {

            // ---------------------------------------------------------
            // 0. 필터
            // ---------------------------------------------------------

            case 0:

                CoachPill(
                    "보고 싶은 다이어리만 골라서 볼 수 있어요"
                )
                .frame(
                    maxWidth: .infinity,
                    maxHeight: .infinity,
                    alignment: .bottomLeading
                )
                .padding(.leading, 8)
                .padding(.bottom, 88)

            // ---------------------------------------------------------
            // 1. 내 위치
            // ---------------------------------------------------------

            case 1:

                CoachPill(
                    "시점을 현재 내 위치로 이동해요"
                )
                .frame(
                    maxWidth: .infinity,
                    maxHeight: .infinity,
                    alignment: .bottomTrailing
                )
                .padding(.trailing, 72)
                .padding(.bottom, 196)

            // ---------------------------------------------------------
            // 2. 별자리
            // ---------------------------------------------------------

            case 2:

                CoachPill(
                    "별들을 이어 별자리를 만들어요"
                )
                .frame(
                    maxWidth: .infinity,
                    maxHeight: .infinity,
                    alignment: .bottomTrailing
                )
                .padding(.trailing, 72)
                .padding(.bottom, 136)

            // ---------------------------------------------------------
            // 3. 몰입
            // ---------------------------------------------------------

            case 3:

                CoachPill(
                    "지도에만 집중해서 별들을 감상해요"
                )
                .frame(
                    maxWidth: .infinity,
                    maxHeight: .infinity,
                    alignment: .bottomTrailing
                )
                .padding(.trailing, 72)
                .padding(.bottom, 76)

            // ---------------------------------------------------------
            // 4. 업로드
            // ---------------------------------------------------------

            case 4:

                CoachPill(
                    "이 버튼을 눌러 다이어리를 올려요"
                )
                .frame(
                    maxWidth: .infinity,
                    maxHeight: .infinity,
                    alignment: .bottomTrailing
                )
                .padding(.trailing, 72)
                .padding(.bottom, 16)

            // ---------------------------------------------------------
            // 5. 메뉴
            // ---------------------------------------------------------

            case 5:

                CoachPill(
                    "내 다이어리 · 프로필 · 업적 · 친구 등\n여러 설정을 여기서 관리해요"
                )
                .frame(
                    maxWidth: .infinity,
                    maxHeight: .infinity,
                    alignment: .topLeading
                )
                .padding(.leading, 8)
                .padding(.top, 66)

            // ---------------------------------------------------------
            // 6. 마무리
            // ---------------------------------------------------------

            default:

                CoachPill(
                    "지금부터 우주를 탐험하고,\n별들에 이야기를 남겨보세요!",
                    big: true
                )
                .frame(
                    maxWidth: .infinity,
                    maxHeight: .infinity,
                    alignment: .center
                )
                .padding(.horizontal, 24)
            }
        }
        .frame(
            maxWidth: .infinity,
            maxHeight: .infinity
        )
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
