import SwiftUI
import UIKit

/// 100m 밖 게시물 잠금 UI — Android `feature/diary/screen/DiaryLock.kt` 패리티(2026-09-22 개편).
///
/// - 히어로: 미디어가 **있을 때만** 미디어 로딩 플레이스홀더(loading_dipper) 가운데에 크리스탈 자물쇠.
///   미디어가 없으면 잠금 표시 없이 일반 글과 같은 image_frame.
/// - 본문 자리: 크리스탈 재생 로고(유튜브형) + "100m 이내로 다가가거나, 광고를 통해 열어보세요!" + 현재 거리.
/// - 두 아이콘 모두 [CrystalPullIcon] — 제자리에 고정돼 있다가 잡아당기면 버티며 조금만 끌려오고,
///   놓으면 튕기듯 돌아간다. **탭 = 보상형 광고**. 따로 "광고 보기" 버튼은 없다.
enum DiaryLock {
    /// 잡아당겼을 때 끌려오는 최대 거리 — Android `PULL_MAX`(16dp).
    static let pullMax: CGFloat = 16
    /// 고무줄 강도 — 이만큼 끌면 [pullMax] 의 약 63% 만큼 따라온다. Android `PULL_SOFT`(70dp).
    static let pullSoft: CGFloat = 70

    /// 파편 무늬 시드 — Android `lockSeed` 와 같은 식(Java `String.hashCode` 하위 10비트 × 2 + 슬롯).
    /// ⚠️ Swift `hashValue` 는 실행마다 바뀌어 무늬가 흔들리므로 쓰지 않는다.
    static func seed(_ diaryId: String?, slot: Int) -> Int {
        var h: UInt32 = 0
        for unit in (diaryId ?? "").utf16 { h = h &* 31 &+ UInt32(unit) }
        return Int(h & 0x3FF) * 2 + slot
    }

    /// 고무줄 — 끈 거리가 길수록 덜 따라온다(상한 [pullMax]). Android `rubberBand` 와 같은 식.
    static func rubberBand(_ t: CGSize) -> CGSize {
        let len = (t.width * t.width + t.height * t.height).squareRoot()
        guard len > 0 else { return .zero }
        // CGFloat 수학 함수 오버로드 모호성 회피 — Double 로 계산 후 캐스팅(iOS 컴파일 함정 메모).
        let soft: Double = exp(-Double(len / pullSoft))
        let pulled = pullMax * CGFloat(1 - soft)
        return CGSize(width: t.width * pulled / len, height: t.height * pulled / len)
    }

    /// 유튜브 로고 비율의 재생 아이콘(24×24 뷰포트) — 둥근 몸통(21×15, 반경 4.6) + 재생 삼각형 구멍.
    /// Android `DiaryLock.kt` 의 `PlayLogo` 와 같은 좌표. 구멍은 even-odd 채움으로 뚫린다.
    static let playLogoPath: CGPath = {
        let p = CGMutablePath()
        p.addRoundedRect(in: CGRect(x: 1.5, y: 4.5, width: 21, height: 15), cornerWidth: 4.6, cornerHeight: 4.6)
        p.move(to: CGPoint(x: 9.9, y: 8.6))
        p.addLine(to: CGPoint(x: 15.8, y: 12))
        p.addLine(to: CGPoint(x: 9.9, y: 15.4))
        p.closeSubpath()
        return p
    }()

    /// 크리스탈 자물쇠(히어로용).
    static func lockImage(color: Color, seed: Int, size: CGFloat) -> UIImage {
        StarCrystal.iconImage(systemName: "lock.fill", color: UIColor(color), seed: seed, size: size)
    }

    /// 크리스탈 재생 로고(본문 카드용).
    static func playLogoImage(color: Color, seed: Int, size: CGFloat) -> UIImage {
        StarCrystal.pathIconImage(name: "play-logo", path: playLogoPath, color: UIColor(color), seed: seed, size: size)
    }
}

/// 크리스탈 파편 아이콘 — **제자리 고정 + 고무줄**(Android `CrystalPullIcon` 패리티).
///
/// - 평소엔 움직이지 않는다(뒤 후광만 천천히 숨쉬듯 밝아졌다 어두워짐 — 누를 수 있다는 힌트).
/// - 잡아당기면 손가락을 따라오되 버틴다(최대 16pt) + 당긴 쪽으로 살짝 기울어짐.
/// - 놓으면 스프링으로 튕기듯 제자리. 거의 안 움직이고 떼면 **탭** → [onTap](광고).
/// - 아이콘 위에서 시작한 드래그는 이 뷰가 가져가므로 화면 스크롤로 넘어가지 않는다.
struct CrystalPullIcon: View {
    let image: UIImage
    let color: Color
    let iconSize: CGFloat
    let accessibilityText: String
    let onTap: () -> Void

    @State private var pull: CGSize = .zero
    @State private var pressed = false
    @State private var dragged = false
    @State private var glowOn = false

    /// 탭/드래그 구분 거리(Android 터치 슬롭 ≈ 8dp).
    private let tapSlop: CGFloat = 8

    var body: some View {
        ZStack {
            // 뒤 후광 — 아이콘을 따라 절반만 움직여 깊이감.
            Circle()
                .fill(RadialGradient(colors: [color.opacity(0.30), .clear],
                                     center: .center, startRadius: 0, endRadius: iconSize * 0.85))
                .opacity(glowOn ? 1 : 0.55)
                // 숨쉬기 애니메이션은 이 opacity 에만 — 화면 전환 중 레이아웃까지 반복 애니메이션되지 않게 범위를 좁힌다.
                .animation(.easeInOut(duration: 1.6).repeatForever(autoreverses: true), value: glowOn)
                .offset(x: pull.width * 0.5, y: pull.height * 0.5)
            Image(uiImage: image)
                .resizable()
                .interpolation(.high)
                .frame(width: iconSize, height: iconSize)
                .scaleEffect(pressed ? 1.06 : 1)
                .rotationEffect(.degrees(Double(pull.width / DiaryLock.pullMax) * 6))
                .offset(pull)
        }
        .frame(width: iconSize * 1.7, height: iconSize * 1.7)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { v in
                    if !pressed {
                        withAnimation(.spring(response: 0.25, dampingFraction: 0.6)) { pressed = true }
                    }
                    let t = v.translation
                    if !dragged, (t.width * t.width + t.height * t.height).squareRoot() > tapSlop { dragged = true }
                    if dragged { pull = DiaryLock.rubberBand(t) }
                }
                .onEnded { _ in
                    let wasTap = !dragged
                    dragged = false
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.5)) { pressed = false }
                    // 놓으면 튕기듯 제자리 — Android spring(dampingRatio 0.38, stiffness 380) 과 같은 값
                    // (damping = 0.38 × 2√380 ≈ 14.8).
                    withAnimation(.interpolatingSpring(stiffness: 380, damping: 14.8)) { pull = .zero }
                    if wasTap {
                        Haptics.soft()
                        onTap()
                    }
                }
        )
        .onAppear { glowOn = true }
        .accessibilityElement()
        .accessibilityLabel(accessibilityText)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { onTap() }
    }
}
