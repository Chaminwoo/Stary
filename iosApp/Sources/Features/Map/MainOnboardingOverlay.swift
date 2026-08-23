import SwiftUI

/// 첫 실행 코치마크 — Android `MainOnboardingOverlay` 완전 패리티.
///
/// 화면을 탭하면 다음 단계로 넘어간다. 스포트라이트가 단계 사이를 부드럽게 이동하고
/// 말풍선이 크로스페이드된다. 우상단 건너뛰기로 즉시 종료.
///
/// 단계:
///  0 필터(좌하단) · 1 내 위치 · 2 별자리 · 3 몰입 · 4 업로드 FAB · 5 메뉴 · 6 마무리
struct MainOnboardingOverlay: View {
    let onDismiss: () -> Void

    @State private var step = 0
    @State private var visible = false

    /// 애니메이션 중인 스포트라이트 중심 / 반지름 (전체화면 좌표)
    @State private var spotX: CGFloat = 0
    @State private var spotY: CGFloat = 0
    @State private var spotR: CGFloat = 0

    /// onAppear 에서 기기 크기로 한 번 계산(회전 고려 안 함 — 지도 화면은 세로 고정)
    @State private var holes: [(x: CGFloat, y: CGFloat, r: CGFloat)] = []

    private let stepCount = 7

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let statusTop = insets.top
            let navBottom = insets.bottom
            let w = geo.size.width
            let h = geo.size.height

            ZStack {
                // ── 다크 스크림 + 구멍 (전체 화면, BlendMode.Clear 대응) ──
                ZStack {
                    Color.black.opacity(0.78)

                    if spotR > 0 {
                        let soft = spotR * 1.35
                        // 소프트 구멍: destinationOut + radial gradient (흰=지우기, 투명=남기기)
                        RadialGradient(
                            stops: [
                                .init(color: .white, location: 0.0),
                                .init(color: .white, location: 0.62),
                                .init(color: .clear,  location: 1.0)
                            ],
                            center: .center,
                            startRadius: 0,
                            endRadius: soft
                        )
                        .frame(width: soft * 2, height: soft * 2)
                        .position(x: spotX, y: spotY)
                        .blendMode(.destinationOut)
                    }
                }
                .compositingGroup()
                .contentShape(Rectangle())
                .onTapGesture { advance() }

                // 강조 링
                if spotR > 0 {
                    Circle()
                        .stroke(Color(hex: 0x6EE7B7).opacity(0.55), lineWidth: 2)
                        .frame(width: spotR * 2, height: spotR * 2)
                        .position(x: spotX, y: spotY)
                        .allowsHitTesting(false)
                }

                // ── 안내 말풍선 — 단계 전환 시 크로스페이드 (Android Crossfade 대응) ──
                pillLayer(statusTop: statusTop, navBottom: navBottom)
                    .animation(.easeOut(duration: 0.26), value: step)

                // ── 건너뛰기 (우상단) ──
                Text("건너뛰기")
                    .font(.minSans(13))
                    .foregroundStyle(.white.opacity(0.6))
                    .padding(6)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    .padding(.top, statusTop + 8)
                    .padding(.trailing, 14)
                    .allowsHitTesting(true)
                    .onTapGesture { finish() }
            }
            .opacity(visible ? 1 : 0)
            .onAppear {
                // 버튼 위치 — Android 수치와 동일
                // rightX: trailing 16 + 반폭 24(48pt 버튼) = width - 40
                // fabRightX: trailing 16 + 반폭 28(56pt FAB) = width - 44
                let right40 = w - 40.0
                let right44 = w - 44.0
                let cb = h - navBottom  // contentBottom(내비 바 위쪽 끝)

                holes = [
                    (40,      cb - 44,         30),  // 0 필터 좌하단
                    (right40, cb - 228,        30),  // 1 내 위치
                    (right40, cb - 168,        30),  // 2 별자리
                    (right40, cb - 108,        30),  // 3 몰입(eye)
                    (right44, cb - 44,         36),  // 4 업로드 FAB
                    (28,      statusTop + 28,  28),  // 5 메뉴(hamburger) — topBar h56 중앙
                    (w / 2,   h / 2,           0),   // 6 마무리(스포트라이트 없음)
                ]

                let first = holes[0]
                spotX = first.x; spotY = first.y; spotR = first.r
                withAnimation(.easeIn(duration: 0.28)) { visible = true }
            }
        }
        .ignoresSafeArea()
    }

    // MARK: - 말풍선 레이어

    @ViewBuilder
    private func pillLayer(statusTop: CGFloat, navBottom: CGFloat) -> some View {
        ZStack {
            switch step {
            case 0:
                CoachPill("보고 싶은 다이어리만 골라서 볼 수 있어요")
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                    .padding(.leading, 8).padding(.bottom, 88 + navBottom)
            case 1:
                CoachPill("시점을 현재 내 위치로 이동해요")
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    .padding(.trailing, 72).padding(.bottom, 196 + navBottom)
            case 2:
                CoachPill("별들을 이어 별자리를 만들어요")
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    .padding(.trailing, 72).padding(.bottom, 136 + navBottom)
            case 3:
                CoachPill("지도에만 집중해서 별들을 감상해요")
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    .padding(.trailing, 72).padding(.bottom, 76 + navBottom)
            case 4:
                CoachPill("이 버튼을 눌러 다이어리를 올려요")
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    .padding(.trailing, 72).padding(.bottom, 16 + navBottom)
            case 5:
                CoachPill("내 다이어리 · 프로필 · 업적 · 친구 등\n여러 설정을 여기서 관리해요")
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    .padding(.leading, 8).padding(.top, 66 + statusTop)
            default:
                CoachPill("지금부터 우주를 탐험하고,\n별들에 이야기를 남겨보세요!", big: true)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                    .padding(.horizontal, 24)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - 상태 전환

    private func advance() {
        let next = step + 1
        if next >= stepCount {
            finish(); return
        }
        guard next < holes.count else { return }
        withAnimation(.easeOut(duration: 0.34)) {
            spotX = holes[next].x
            spotY = holes[next].y
            spotR = holes[next].r
        }
        step = next
    }

    private func finish() {
        withAnimation(.easeOut(duration: 0.28)) { visible = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { onDismiss() }
    }
}

// MARK: - 안내 말풍선 (Android CoachPill 대응)

private struct CoachPill: View {
    let text: String
    var big: Bool = false

    init(_ text: String, big: Bool = false) {
        self.text = text
        self.big = big
    }

    private var accent: LinearGradient {
        LinearGradient(colors: [Color(hex: 0x6EE7B7), Color(hex: 0x3B82F6)],
                       startPoint: .leading, endPoint: .trailing)
    }

    var body: some View {
        Text(text)
            .font(.minSans(big ? 17 : 14))
            .foregroundStyle(.white)
            .lineSpacing(big ? 8 : 5)
            .multilineTextAlignment(.center)
            .padding(.horizontal, big ? 22 : 14)
            .padding(.vertical, big ? 18 : 10)
            .frame(maxWidth: big ? 300 : 232, alignment: .center)
            .background(Color(hex: 0x14181C), in: RoundedRectangle(cornerRadius: big ? 18 : 14))
            .overlay(
                RoundedRectangle(cornerRadius: big ? 18 : 14)
                    .stroke(accent, lineWidth: big ? 1.5 : 1)
            )
    }
}
