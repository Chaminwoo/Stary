import SwiftUI
import UIKit

/// 100m 밖 게시물 잠금 UI — Android `feature/diary/screen/DiaryLock.kt` 패리티(2026-09-22 개편).
///
/// - 히어로: 미디어가 **있을 때만** 미디어 로딩 플레이스홀더(loading_dipper) + 우하단 작은 캡션 "이 사진/영상은 잠겨 있어요".
///   미디어가 없으면 잠금 표시 없이 일반 글과 같은 image_frame.
/// - 본문 자리: 좌상단·우하단 십자 코너([CornerCrossFrame]) 안에 크리스탈 재생 로고(유튜브형)
///   + "100m 이내로 다가가거나, 광고를 통해 열어보세요!" + 현재 거리.
/// - 재생 로고는 [CrystalPullIcon] — 제자리에 고정돼 있다가 잡아당기면 버티며 조금만 끌려오고,
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

    /// 잠긴 히어로 캡션 색 — 플레이스홀더 별자리 선의 청보라를 글자로 읽힐 만큼 밝힌 색 / 그 번짐.
    /// Android `LOCK_CAPTION_COLOR`(0xE6AEBBDF) / `LOCK_CAPTION_GLOW`(0x997F93CC) 와 같은 값.
    static let captionColor = Color(hex: 0xAEBBDF).opacity(0.90)
    static let captionGlow = Color(hex: 0x7F93CC).opacity(0.60)

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

/// 십자 코너 프레임 — **좌상단·우하단 두 모서리에만** 가는 십자선(천체 관측 레티클 / 인쇄 재단선 느낌).
/// Android `DiaryLock.kt` `cornerCrossFrame` 과 같은 수치: 헤어라인 0.75pt, 안쪽 팔 가로 64 · 세로 40(끝으로 사라짐),
/// 바깥 팔 7, 교차점 점 반지름 1.3 + 반경 6 의 아주 옅은 광. 렌즈 플레어·큰 후광은 일부러 넣지 않는다.
/// 바깥 팔이 요소 밖으로 7pt 나가므로 캔버스를 사방 [bleed] 만큼 키워 그린다(잘림 방지).
struct CornerCrossFrame: View {
    let color: Color

    private let armH: CGFloat = 64
    private let armV: CGFloat = 40
    private let stub: CGFloat = 7
    private let bleed: CGFloat = 10

    var body: some View {
        Canvas { ctx, size in
            let b = bleed
            cross(ctx, at: CGPoint(x: b, y: b), sx: 1, sy: 1)
            cross(ctx, at: CGPoint(x: size.width - b, y: size.height - b), sx: -1, sy: -1)
        }
        .padding(-bleed)
        .allowsHitTesting(false)
    }

    private func fadingLine(_ ctx: GraphicsContext, _ from: CGPoint, _ to: CGPoint, alpha: Double) {
        var path = Path()
        path.move(to: from)
        path.addLine(to: to)
        ctx.stroke(
            path,
            with: .linearGradient(
                Gradient(colors: [color.opacity(alpha), color.opacity(0)]),
                startPoint: from, endPoint: to
            ),
            lineWidth: 0.75
        )
    }

    /// [p] 에 십자 하나 — [sx]/[sy] 는 요소 안쪽 방향(+1/−1).
    private func cross(_ ctx: GraphicsContext, at p: CGPoint, sx: CGFloat, sy: CGFloat) {
        fadingLine(ctx, p, CGPoint(x: p.x + sx * armH, y: p.y), alpha: 0.70)   // 안쪽 가로(길게)
        fadingLine(ctx, p, CGPoint(x: p.x, y: p.y + sy * armV), alpha: 0.70)   // 안쪽 세로
        fadingLine(ctx, p, CGPoint(x: p.x - sx * stub, y: p.y), alpha: 0.45)   // 바깥 가로(짧게)
        fadingLine(ctx, p, CGPoint(x: p.x, y: p.y - sy * stub), alpha: 0.45)   // 바깥 세로
        let glowR: CGFloat = 6
        ctx.fill(
            Path(ellipseIn: CGRect(x: p.x - glowR, y: p.y - glowR, width: glowR * 2, height: glowR * 2)),
            with: .radialGradient(
                Gradient(colors: [color.opacity(0.22), .clear]),
                center: p, startRadius: 0, endRadius: glowR
            )
        )
        let r: CGFloat = 1.3
        ctx.fill(
            Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)),
            with: .color(color.blended(with: .white, fraction: 0.45))
        )
    }
}

/// 십자 프레임 **안쪽 읽기 면** — 테두리도 둥근 모서리도 없이 한 톤만 띄운다.
/// Android `DiaryLock.kt` `Modifier.readingSurface()` 패리티(같은 색 `#15191F` 70%, 같은 18pt 페이드).
///
/// 왜: 카드를 걷어내고 십자 코너만 남기니 잠금 안내문(짧음)은 좋아졌는데 **해금된 긴 본문**은
/// 순흑 배경 위에서 글자가 번져 보이고 문단 경계도 사라져 읽기 힘들었다. 카드로 되돌리지 않고
/// 면만 깔되 위아래를 페이드해 가로 경계선이 생기지 않게 한다.
///
/// ⚠️ 쓰는 순서: `.background(CornerCrossFrame(...)).background(ReadingSurface())` —
/// SwiftUI 는 **나중에 붙인 background 가 더 뒤**라, 이 순서라야 십자가 면 위에 온다.
struct ReadingSurface: View {
    /// 위아래로 면이 사라지는 구간(pt) — Android 와 같은 값.
    private let fade: CGFloat = 18

    var body: some View {
        GeometryReader { geo in
            let h = max(geo.size.height, 1)
            let f = min(fade / h, 0.45)
            LinearGradient(
                stops: [
                    .init(color: .clear, location: 0),
                    .init(color: Self.surface, location: f),
                    .init(color: Self.surface, location: 1 - f),
                    .init(color: .clear, location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )
        }
        .allowsHitTesting(false)
    }

    /// 순흑 배경(#0D0D0D)보다 한 톤만 밝은 남빛 — 옛 본문 카드(#14181C)의 역할을 대신한다.
    private static let surface = Color(hex: 0x15191F).opacity(0.70)
}
