import UIKit

/// 공유 영상 위에 얹는 그림들 — 글 블록 2장(위: 별 아이콘·좌표·제목 / 아래: 날짜·버튼)과
/// 빛(빛기둥·번짐·고리·주인공 별빛) 4장. 전부 한 번만 만들고, 프레임마다 위치·크기·투명도만 바꿔 그린다.
///
/// 빛 4장은 **픽셀마다 식으로 계산**한다(블러 API 를 쓰지 않는다) — Android `ShareVideoOverlay.kt` 와 같은 식이라 두 플랫폼 모양이 같다.
/// 좌표·크기는 디자인 px(1080×1920). 글꼴은 기기 내장(명조 / 고정폭 / 시스템) — 한·영·일 제목을 모두 그릴 수 있고 앱 용량이 늘지 않는다.
struct ShareVideoOverlay: @unchecked Sendable {

    /// 한 장의 글 블록 — `top` 은 블록 그림 맨 위가 놓일 화면 y(디자인 px). 가로는 화면 전체(1080).
    struct Block {
        let image: CGImage
        let top: CGFloat
    }

    let topBlock: Block
    let bottomBlock: Block
    let beam: CGImage
    let glow: CGImage
    let ring: CGImage
    let hero: CGImage

    // 글 블록 치수(디자인 px) — 레퍼런스(360 폭)의 3배. Android 와 같은 값.
    private static let pad: CGFloat = 60          // 그림자·획이 잘리지 않게 블록 둘레에 두는 여백
    private static let icon: CGFloat = 42
    private static let iconGap: CGFloat = 33
    private static let coordGap: CGFloat = 22
    private static let titleWidth: CGFloat = 960
    private static let titleSize: CGFloat = 81
    private static let titleLine: CGFloat = 105
    private static let dateGap: CGFloat = 51
    private static let pillH: CGFloat = 99
    private static let pillPadH: CGFloat = 45
    private static let pillGap: CGFloat = 21
    private static let arrow: CGFloat = 36
    private static let beamW = 96                 // = 2 × ShareVideoSpec.beamHalfW
    private static let beamH = 384
    private static let heroSize = 192

    /// - Parameters:
    ///   - untitled: 제목이 비었을 때 쓸 문구("(제목 없음)").
    ///   - cta: 버튼 문구("Stary 에서 이 별 찾기") — 호출부가 설정 언어의 **원문**(결합자 없는 값)을 넘긴다.
    init?(diary: Diary, untitled: String, cta: String) {
        // 빛 색(0..1 RGB) — 별 색 그대로(그라데이션 별은 두 색 평균). lightRGB 는 여기에 γ 1.5 를 건 값이라 되돌려 쓴다.
        let l = StarStyle.lightRGB(diary.starColor)
        let tint = SIMD3<Float>(powf(l.r, 1 / 1.5), powf(l.g, 1 / 1.5), powf(l.b, 1 / 1.5))
        let title = diary.title.trimmingCharacters(in: .whitespacesAndNewlines)

        guard let top = ShareVideoOverlay.makeTopBlock(diary: diary, title: title.isEmpty ? untitled : title, tint: tint),
              let bottom = ShareVideoOverlay.makeBottomBlock(diary: diary, cta: cta),
              let beam = ShareVideoOverlay.makeBeam(tint),
              let glow = ShareVideoOverlay.radial(128, tint, ShareVideoOverlay.glowAlpha),
              let ring = ShareVideoOverlay.radial(128, tint, ShareVideoOverlay.ringAlpha),
              let hero = ShareVideoOverlay.makeHero(tint)
        else { return nil }
        topBlock = top
        bottomBlock = bottom
        self.beam = beam
        self.glow = glow
        self.ring = ring
        self.hero = hero
    }

    // MARK: - 글 블록

    private static func renderer(height: CGFloat) -> UIGraphicsImageRenderer {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        return UIGraphicsImageRenderer(size: CGSize(width: CGFloat(ShareVideoSpec.w), height: ceil(height)), format: format)
    }

    private static func color(_ t: SIMD3<Float>) -> UIColor {
        UIColor(red: CGFloat(t.x), green: CGFloat(t.y), blue: CGFloat(t.z), alpha: 1)
    }

    /// 명조 계열 — 한글은 AppleMyungjo, 가나/한자는 히라기노 명조로 넘어간다(없으면 시스템 글꼴). 굵기는 획(stroke)으로 보탠다.
    private static func titleFont() -> UIFont {
        let cascade = ["HiraMinProN-W6", "TimesNewRomanPS-BoldMT"].map { UIFontDescriptor(name: $0, size: titleSize) }
        let descriptor = UIFontDescriptor(name: "AppleMyungjo", size: titleSize).addingAttributes([.cascadeList: cascade])
        return UIFont(descriptor: descriptor, size: titleSize)
    }

    /// 위쪽: 그 별의 크리스탈 아이콘 → 좌표(별 색, 고정폭) → 제목(명조 굵게, 최대 2줄).
    private static func makeTopBlock(diary: Diary, title: String, tint: SIMD3<Float>) -> Block? {
        let width = CGFloat(ShareVideoSpec.w)
        let coordKern: CGFloat = 27 * 0.2
        let coord = NSAttributedString(string: ShareVideoSpec.coordText(lat: diary.latitude, lng: diary.longitude), attributes: [
            .font: UIFont.monospacedSystemFont(ofSize: 27, weight: .regular),
            .foregroundColor: color(tint),
            .kern: coordKern,
        ])
        let coordSize = coord.size()

        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        paragraph.lineBreakMode = .byWordWrapping
        paragraph.lineBreakStrategy = .hangulWordPriority
        paragraph.minimumLineHeight = titleLine
        paragraph.maximumLineHeight = titleLine
        let shadow = NSShadow()
        shadow.shadowColor = UIColor(red: 2 / 255, green: 3 / 255, blue: 10 / 255, alpha: 0.9)
        shadow.shadowBlurRadius = 40
        shadow.shadowOffset = CGSize(width: 0, height: 6)
        let titleText = NSAttributedString(string: title, attributes: [
            .font: titleFont(),
            .foregroundColor: UIColor.white,
            .strokeColor: UIColor.white,
            .strokeWidth: -2.2,                 // 음수 = 채우기 + 획 → 굵게
            .kern: -0.01 * titleSize,
            .paragraphStyle: paragraph,
            .shadow: shadow,
        ])
        let options: NSStringDrawingOptions = [.usesLineFragmentOrigin, .truncatesLastVisibleLine]
        let maxTitleH = titleLine * 2 + 2
        let measured = titleText.boundingRect(with: CGSize(width: titleWidth, height: maxTitleH), options: options, context: nil)
        let titleH = min(ceil(measured.height), maxTitleH)

        let height = pad + icon + iconGap + ceil(coordSize.height) + coordGap + titleH + pad
        let image = renderer(height: height).image { ctx in
            var y = pad
            StarCrystal.draw(in: ctx.cgContext, type: diary.starType, colorIndex: diary.starColor,
                             rect: CGRect(x: width / 2 - icon / 2, y: y, width: icon, height: icon))
            y += icon + iconGap
            // 자간은 마지막 글자 뒤에도 붙으므로 그 절반만큼 오른쪽으로 밀어 가운데를 맞춘다.
            coord.draw(at: CGPoint(x: (width - coordSize.width) / 2 + coordKern / 2, y: y))
            y += ceil(coordSize.height) + coordGap
            titleText.draw(with: CGRect(x: (width - titleWidth) / 2, y: y, width: titleWidth, height: maxTitleH),
                           options: options, context: nil)
        }
        guard let cg = image.cgImage else { return nil }
        return Block(image: cg, top: CGFloat(ShareVideoSpec.topBlockY) - pad)
    }

    /// 아래쪽: 날짜(고정폭, 옅게) → "Stary 에서 이 별 찾기" 알약 버튼(반투명 유리 + 화살표).
    private static func makeBottomBlock(diary: Diary, cta: String) -> Block? {
        let width = CGFloat(ShareVideoSpec.w)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy.MM.dd"
        let dateKern: CGFloat = 27 * 0.12
        let date = NSAttributedString(string: formatter.string(from: Date(timeIntervalSince1970: Double(diary.createdAt) / 1000)), attributes: [
            .font: UIFont.monospacedSystemFont(ofSize: 27, weight: .regular),
            .foregroundColor: UIColor(white: 1, alpha: 0.72),
            .kern: dateKern,
        ])
        let dateSize = date.size()
        let label = NSAttributedString(string: cta, attributes: [
            .font: UIFont.systemFont(ofSize: 34.5, weight: .semibold),
            .foregroundColor: UIColor.white,
        ])
        let labelSize = label.size()
        let pillW = pillPadH * 2 + labelSize.width + pillGap + arrow

        let height = pad + ceil(dateSize.height) + dateGap + pillH + pad
        let image = renderer(height: height).image { ctx in
            let cg = ctx.cgContext
            var y = pad
            date.draw(at: CGPoint(x: (width - dateSize.width) / 2 + dateKern / 2, y: y))
            y += ceil(dateSize.height) + dateGap

            let left = (width - pillW) / 2
            let pill = CGRect(x: left, y: y, width: pillW, height: pillH)
            UIColor(white: 1, alpha: 0.10).setFill()
            UIBezierPath(roundedRect: pill, cornerRadius: pillH / 2).fill()
            UIColor(white: 1, alpha: 0.30).setStroke()
            let border = UIBezierPath(roundedRect: pill.insetBy(dx: 1.5, dy: 1.5), cornerRadius: pillH / 2 - 1.5)
            border.lineWidth = 3
            border.stroke()
            label.draw(at: CGPoint(x: left + pillPadH, y: pill.midY - labelSize.height / 2))

            // 화살표(→) — 12 단위 그리드의 "M2 6h8 M6.5 2.5 L10 6 L6.5 9.5" 를 arrow 크기로
            let ax = left + pillPadH + labelSize.width + pillGap
            let ay = pill.midY - arrow / 2
            let k = arrow / 12
            cg.setStrokeColor(UIColor.white.cgColor)
            cg.setLineWidth(4.5)
            cg.setLineCap(.round)
            cg.setLineJoin(.round)
            cg.move(to: CGPoint(x: ax + 2 * k, y: ay + 6 * k))
            cg.addLine(to: CGPoint(x: ax + 10 * k, y: ay + 6 * k))
            cg.move(to: CGPoint(x: ax + 6.5 * k, y: ay + 2.5 * k))
            cg.addLine(to: CGPoint(x: ax + 10 * k, y: ay + 6 * k))
            cg.addLine(to: CGPoint(x: ax + 6.5 * k, y: ay + 9.5 * k))
            cg.strokePath()
        }
        guard let cg = image.cgImage else { return nil }
        return Block(image: cg, top: CGFloat(ShareVideoSpec.bottomBlockY) - pad)
    }

    // MARK: - 빛(식으로 계산)

    /// (밝기, 색)을 픽셀마다 계산해 프리멀티플라이드 그림으로 — 더하기 합성하면 "색 × 밝기"가 더해진다. 첫 행 = 그림의 맨 위.
    private static func pixels(width: Int, height: Int,
                               _ at: (_ i: Int, _ j: Int) -> (a: Float, r: Float, g: Float, b: Float)) -> CGImage? {
        func unit(_ v: Float) -> Float { min(max(v, 0), 1) }
        var data = [UInt8](repeating: 0, count: width * height * 4)
        for j in 0..<height {
            for i in 0..<width {
                let p = at(i, j)
                let a = unit(p.a)
                let o = (j * width + i) * 4
                data[o] = UInt8(unit(p.r) * a * 255 + 0.5)
                data[o + 1] = UInt8(unit(p.g) * a * 255 + 0.5)
                data[o + 2] = UInt8(unit(p.b) * a * 255 + 0.5)
                data[o + 3] = UInt8(a * 255 + 0.5)
            }
        }
        guard let provider = CGDataProvider(data: Data(data) as CFData) else { return nil }
        return CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4,
                       space: CGColorSpaceCreateDeviceRGB(),
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                       provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
    }

    /// 빛기둥 — 아래(별)에서 가장 밝고 위로 갈수록 가늘어지며 사라진다. 가는 흰 심지 + 별 색의 넓은 번짐.
    /// 가로 1텍셀 = 1px(반폭 `ShareVideoSpec.beamHalfW`), 세로는 그릴 때 길이에 맞춰 늘린다.
    private static func makeBeam(_ tint: SIMD3<Float>) -> CGImage? {
        pixels(width: beamW, height: beamH) { i, j in
            let u = 1 - (Float(j) + 0.5) / Float(beamH)                 // 0 = 별(아래), 1 = 끝(위)
            let along: Float = u < 0.5 ? 0.95 - 0.57 * (u / 0.5) : 0.38 * (1 - (u - 0.5) / 0.5)
            let sigmaCore: Float = 3.0 - 1.8 * u
            let sigmaHalo: Float = 26 - 15 * u
            let x = (Float(i) + 0.5) - Float(beamW) / 2
            let core = expf(-(x / sigmaCore) * (x / sigmaCore))
            let halo = 0.62 * expf(-(x / sigmaHalo) * (x / sigmaHalo))
            let hot = core * 0.85                                        // 심지는 흰빛으로
            return (along * (core + halo), tint.x + (1 - tint.x) * hot, tint.y + (1 - tint.y) * hot, tint.z + (1 - tint.z) * hot)
        }
    }

    /// 별 주위의 부드러운 번짐 — 중심 0.55 → 35% 지점 0.19 → 가장자리 0.
    private static func glowAlpha(_ r: Float) -> Float {
        if r >= 1 { return 0 }
        if r < 0.35 { return 0.55 - 0.3575 * (r / 0.35) }
        return 0.1925 * (1 - (r - 0.35) / 0.65)
    }

    /// 퍼지는 고리 — 반지름 86% 지점의 가는 선.
    private static func ringAlpha(_ r: Float) -> Float {
        let d = (r - 0.86) / 0.045
        return expf(-d * d)
    }

    /// 중심에서의 거리(0..1)로 밝기가 정해지는 둥근 빛(번짐·고리).
    private static func radial(_ size: Int, _ tint: SIMD3<Float>, _ alphaAt: (Float) -> Float) -> CGImage? {
        pixels(width: size, height: size) { i, j in
            let x = (Float(i) + 0.5) / Float(size) * 2 - 1
            let y = (Float(j) + 0.5) / Float(size) * 2 - 1
            return (alphaAt((x * x + y * y).squareRoot()), tint.x, tint.y, tint.z)
        }
    }

    /// 주인공 별빛 — 글로브의 인기 별과 같은 모양(`GlobeGeometry.sparkCoverage`)을 pinFragment 와 같은 식으로 색칠한다
    /// (불투명도 = 밝기^1.6, 색 = 별 색조 → 심지 쪽은 흰색).
    private static func makeHero(_ tint: SIMD3<Float>) -> CGImage? {
        let mx = max(max(tint.x, tint.y), max(tint.z, 0.001))
        let n = tint / mx
        return pixels(width: heroSize, height: heroSize) { i, j in
            let x = (Float(i) + 0.5) / Float(heroSize) * 2 - 1
            let y = (Float(j) + 0.5) / Float(heroSize) * 2 - 1
            let cov = GlobeGeometry.sparkCoverage(x, y, mainLen: GlobeGeometry.starMainLen,
                                                  crossLen: GlobeGeometry.starCrossLen, diagWeight: GlobeGeometry.starDiagWeight)
            let e = min(max((cov - 0.75) / 0.25, 0), 1)
            let white = e * e * (3 - 2 * e) * 0.7
            return (powf(cov, 1.6), n.x + (1 - n.x) * white, n.y + (1 - n.y) * white, n.z + (1 - n.z) * white)
        }
    }
}
