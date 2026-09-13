import CoreLocation
import SwiftUI
import UIKit

// 다이어리 공유 카드 — Android `core/util/ShareCardHelper.kt` 패리티(인스타 스토리 규격 1080×1920).
//
// 디자인: 밤하늘 배경(share_card_bg.webp, 테두리 장식 포함 — 크롭 없이 늘려 채움) + 별 좌표 중심 나라 지도
// (MapTiler 래스터 타일 z4 스티칭, 원형 페더 마스크) + 정중앙 히어로 별 + 제목·위치 캡슐·날짜 + 비네트.
// ⚠️ 좌표/알파/반경 상수는 Android 와 **같은 값**이다(1080×1920 픽셀 공간). 한쪽만 고치지 말 것.
//
// 2026-07-19 에 iOS 공유 기능을 한 번 걷어냈다가(e6e438e), 2026-09 사용자 결정으로 Android 편집기까지 포함해 재구현.

/// 카드에 추가로 얹는 장식 별(내 다이어리의 별) — Android `ShareCardHelper.ExtraStar`.
struct ShareExtraStar: Equatable {
    var type: Int
    var colorIndex: Int
    var xFrac: CGFloat
    var yFrac: CGFloat
    var scale: CGFloat = 0.6
}

/// 편집 옵션 — Android `ShareCardOptions` 와 같은 필드/기본값(좌표는 카드 내 상대값 0..1, 앵커 = 요소 중심).
struct ShareCardOptions: Equatable {
    var title: String? = nil
    var showMap = true
    var showLocation = true
    var showDate = true
    var stageXFrac: CGFloat = 0.5
    var stageYFrac: CGFloat = 0.40
    var starScale: CGFloat = 1
    var titleXFrac: CGFloat = 0.5
    var titleYFrac: CGFloat = 0.71
    var locationXFrac: CGFloat = 0.5
    var locationYFrac: CGFloat = 0.795
    var dateXFrac: CGFloat = 0.5
    var dateYFrac: CGFloat = 0.85
    var extraStars: [ShareExtraStar] = []
}

/// 렌더용 네트워크 자산(동네 이름 + 지역 지도) — 편집 중 옵션이 바뀔 때마다 다시 받지 않도록 1회 준비.
struct ShareCardAssets {
    let locationHint: String?
    let regionMap: UIImage?
}

enum ShareCard {
    static let width: CGFloat = 1080
    static let height: CGFloat = 1920

    /// 지역 지도 타일 줌/출력 변(px). 줌 4 = 나라+주변국 스케일(Android REGION_MAP_ZOOM/SIDE).
    private static let regionMapZoom = 4
    private static let regionMapSide: CGFloat = 512

    // MARK: - 자산

    /// 편집 미리보기/공유 공용 — 동네 이름(역지오코딩) + 지역 지도를 병렬로 준비.
    static func prepareAssets(diary: Diary, locale: Locale?) async -> ShareCardAssets {
        async let hint = locationHint(lat: diary.latitude, lng: diary.longitude, locale: locale)
        async let map = fetchRegionMap(lat: diary.latitude, lng: diary.longitude)
        return await ShareCardAssets(locationHint: hint, regionMap: map)
    }

    /// 역지오코딩 동네 이름(예: "서울 광진구"). 실패 시 nil → 카드에서 생략(Android resolveLocationHint).
    private static func locationHint(lat: Double, lng: Double, locale: Locale?) async -> String? {
        let location = CLLocation(latitude: lat, longitude: lng)
        guard let placemark = try? await CLGeocoder()
            .reverseGeocodeLocation(location, preferredLocale: locale).first else { return nil }
        var parts: [String] = []
        let area = placemark.locality ?? placemark.subLocality ?? placemark.subAdministrativeArea
        for p in [placemark.administrativeArea, area] {
            if let p, !p.isEmpty, !parts.contains(p) { parts.append(p) }
        }
        return parts.isEmpty ? nil : parts.joined(separator: " ")
    }

    /// 별 좌표 중심 나라+주변국 지도 — MapTiler 래스터 타일(dataviz-dark, z4)을 이어붙여 512px 이미지.
    /// ⚠️ 정적 지도 API 는 이 키/플랜에서 403 이라 타일 스티칭(Android 와 같은 이유). 키 없음/실패 → nil(지도 없이 별만).
    private static func fetchRegionMap(lat: Double, lng: Double) async -> UIImage? {
        let key = ((Bundle.main.object(forInfoDictionaryKey: "MAPTILER_KEY") as? String) ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty, !key.hasPrefix("TODO") else { return nil }

        let side = regionMapSide
        let n = 1 << regionMapZoom
        // 웹 메르카토르: 좌표 → 전역 픽셀(타일 한 변 = side 로 정규화)
        let latRad = min(max(lat, -85.05), 85.05) * .pi / 180
        let px = (lng + 180) / 360 * Double(n) * Double(side)
        let py = (1 - log(tan(latRad) + 1 / cos(latRad)) / .pi) / 2 * Double(n) * Double(side)
        let left = px - Double(side) / 2
        let top = py - Double(side) / 2
        let tx0 = Int(floor(left / Double(side)))
        let ty0 = Int(floor(top / Double(side)))

        var tiles: [(image: UIImage, rect: CGRect)] = []
        for ty in ty0...(ty0 + 1) where ty >= 0 && ty < n {
            for tx in tx0...(tx0 + 1) {
                let wrapX = ((tx % n) + n) % n // 날짜변경선 래핑
                guard let url = URL(string:
                    "https://api.maptiler.com/maps/dataviz-dark/\(regionMapZoom)/\(wrapX)/\(ty).png?key=\(key)")
                else { continue }
                var req = URLRequest(url: url)
                req.timeoutInterval = 6
                // ⚠️ `guard let (data, _) = …` 처럼 조건절에서 튜플을 분해하면 컴파일 에러 — 통째로 받는다.
                guard let result = try? await URLSession.shared.data(for: req),
                      let img = UIImage(data: result.0) else { continue }
                let rect = CGRect(x: CGFloat(Double(tx) * Double(side) - left),
                                  y: CGFloat(Double(ty) * Double(side) - top),
                                  width: side, height: side)
                tiles.append((img, rect))
            }
        }
        guard !tiles.isEmpty else { return nil }

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { rc in
            UIColor(rgb: 0x10141F).setFill() // 타일 결손/극지 폴백(스타일 바다색 근사)
            rc.fill(CGRect(x: 0, y: 0, width: side, height: side))
            for t in tiles { t.image.draw(in: t.rect) }
        }
    }

    // MARK: - 렌더

    /// 카드 렌더(1080×1920, scale 1). 편집 미리보기에서도 호출 — 무거우므로 백그라운드에서 부를 것.
    /// [untitled] 는 제목이 비었을 때 문구(메인 액터의 LocaleManager 에서 미리 뽑아 넘긴다).
    static func render(diary: Diary, assets: ShareCardAssets, options: ShareCardOptions, untitled: String) -> UIImage {
        let W = width, H = height
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: W, height: H), format: format).image { rc in
            let cg = rc.cgContext
            let accent = UIColor(StarStyle.color(diary.starColor))
            let regionMap = options.showMap ? assets.regionMap : nil

            // 중앙 무대 좌표 — 지도 원과 별이 겹쳐 앉는다(편집에서 드래그로 이동).
            let stageCx = W * clamp(options.stageXFrac, 0.08, 0.92)
            let stageCy = H * clamp(options.stageYFrac, 0.08, 0.90)
            let mapRadius: CGFloat = 250

            drawBackground(cg, accent: accent, stage: CGPoint(x: stageCx, y: stageCy))
            if let regionMap {
                drawRegionMap(cg, map: regionMap, center: CGPoint(x: stageCx, y: stageCy), radius: mapRadius, accent: accent)
            }
            // 추가 별 — 히어로 별 아래 레이어.
            for star in options.extraStars { drawExtraStar(cg, star: star) }
            drawHeroStar(cg, diary: diary, accent: accent, center: CGPoint(x: stageCx, y: stageCy),
                         hasMap: regionMap != nil, scale: clamp(options.starScale, 0.2, 2.5))

            // 제목 — 최대 2줄, 가운데 정렬, 은은한 위→아래 라이트닝 + 별색 글로우.
            let rawTitle = options.title ?? diary.title
            let title = rawTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? untitled : rawTitle
            drawTitle(cg, text: title, accent: accent,
                      center: CGPoint(x: W * clamp(options.titleXFrac, 0.08, 0.92),
                                      y: H * clamp(options.titleYFrac, 0.05, 0.95)))

            if options.showLocation, let hint = assets.locationHint {
                drawLocationPill(cg, text: "✦ \(hint)", accent: accent,
                                 center: CGPoint(x: W * clamp(options.locationXFrac, 0.08, 0.92),
                                                 y: H * clamp(options.locationYFrac, 0.05, 0.96)))
            }

            if options.showDate {
                let f = DateFormatter()
                f.dateFormat = "yyyy. MM. dd"
                drawCenteredText(f.string(from: diary.createdDate),
                                 font: .minSans(40), color: UIColor.white.withAlphaComponent(150 / 255),
                                 center: CGPoint(x: W * clamp(options.dateXFrac, 0.08, 0.92),
                                                 y: H * clamp(options.dateYFrac, 0.05, 0.97)))
            }

            // 네 모서리를 아주 은은하게 눌러 "액자" 마무리감.
            radial(cg, center: CGPoint(x: W / 2, y: H / 2), radius: H * 0.75,
                   colors: [UIColor.clear, UIColor(red: 0, green: 0, blue: 4 / 255, alpha: 70 / 255)],
                   locations: [0.72, 1], afterEnd: true)
        }
    }

    /// 배경 이미지(크롭 없이 카드 전체) + 하단 텍스트 스크림 + 무대의 별색 무드.
    private static func drawBackground(_ cg: CGContext, accent: UIColor, stage: CGPoint) {
        let W = width, H = height
        if let bg = ShareCardBackground.image {
            bg.draw(in: CGRect(x: 0, y: 0, width: W, height: H))
        } else {
            linear(cg, rect: CGRect(x: 0, y: 0, width: W, height: H),
                   colors: [UIColor(rgb: 0x0C1130), UIColor(rgb: 0x070B1E), UIColor(rgb: 0x04050D)],
                   locations: [0, 0.5, 1])
        }
        let scrimTop = H * 0.60
        linear(cg, rect: CGRect(x: 0, y: scrimTop, width: W, height: H - scrimTop),
               colors: [UIColor.clear,
                        UIColor(red: 0, green: 0, blue: 8 / 255, alpha: 96 / 255),
                        UIColor(red: 0, green: 0, blue: 8 / 255, alpha: 130 / 255)],
               locations: [0, 0.45, 1])
        radial(cg, center: stage, radius: H * 0.36,
               colors: [accent.withAlphaComponent(30 / 255), UIColor.clear], locations: [0, 1])
    }

    /// 나라 지도 — 원형 페더 마스크 + 반투명(은하수가 비치게) + 별색 후광 + 이중 링.
    private static func drawRegionMap(_ cg: CGContext, map: UIImage, center c: CGPoint, radius: CGFloat, accent: UIColor) {
        radial(cg, center: c, radius: radius * 1.02,
               colors: [accent.withAlphaComponent(46 / 255), UIColor.clear], locations: [0.55, 1], beforeStart: true)

        let d = radius * 2
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let layer = UIGraphicsImageRenderer(size: CGSize(width: d, height: d), format: format).image { rc in
            let lc = rc.cgContext
            map.draw(in: CGRect(x: 0, y: 0, width: d, height: d))
            // 페더: 중심 불투명 → 가장자리 투명 — destinationIn 으로 알파만 남긴다(Android DST_IN).
            lc.setBlendMode(.destinationIn)
            radial(lc, center: CGPoint(x: radius, y: radius), radius: radius,
                   colors: [UIColor.white, UIColor.white, UIColor.clear], locations: [0, 0.68, 1], afterEnd: true)
        }
        layer.draw(in: CGRect(x: c.x - radius, y: c.y - radius, width: d, height: d), blendMode: .normal, alpha: 150 / 255)

        cg.saveGState()
        cg.setLineWidth(1)
        cg.setStrokeColor(UIColor.white.withAlphaComponent(60 / 255).cgColor)
        cg.strokeEllipse(in: circleRect(c, radius * 0.665))
        cg.setLineWidth(2)
        cg.setStrokeColor(accent.withAlphaComponent(80 / 255).cgColor)
        cg.strokeEllipse(in: circleRect(c, radius * 0.68))
        cg.restoreGState()
    }

    /// 주인공 별 — 후광 + 이중 글로우(넓고 옅게 + 좁고 진하게) + 크리스탈 본체 + 궤도 스파클 2개.
    private static func drawHeroStar(_ cg: CGContext, diary: Diary, accent: UIColor, center c: CGPoint,
                                     hasMap: Bool, scale: CGFloat) {
        let starSize: CGFloat = 150 * scale
        let haloR = hasMap ? starSize * 1.5 : starSize * 2.2
        radial(cg, center: c, radius: haloR,
               colors: [accent.withAlphaComponent((hasMap ? 150 : 90) / 255), UIColor.clear], locations: [0, 1])

        let rect = CGRect(x: c.x - starSize / 2, y: c.y - starSize / 2, width: starSize, height: starSize)
        let path = StarShape(type: diary.starType).path(in: rect).cgPath
        glowFill(cg, path: path, color: accent.withAlphaComponent(130 / 255), blur: 28)
        glowFill(cg, path: path, color: accent, blur: 12)
        StarCrystal.draw(in: cg, type: diary.starType, colorIndex: diary.starColor, rect: rect)

        drawSparkle(cg, center: CGPoint(x: c.x - starSize * 0.78, y: c.y - starSize * 0.52), r: 9,
                    color: UIColor.white.withAlphaComponent(210 / 255))
        drawSparkle(cg, center: CGPoint(x: c.x + starSize * 0.82, y: c.y + starSize * 0.18), r: 7,
                    color: accent.withAlphaComponent(230 / 255))
    }

    /// 추가 별 — 은은한 후광 + 글로우 + 크리스탈 본체.
    private static func drawExtraStar(_ cg: CGContext, star: ShareExtraStar) {
        let color = UIColor(StarStyle.color(star.colorIndex))
        let size: CGFloat = 150 * clamp(star.scale, 0.2, 2.5)
        let c = CGPoint(x: width * clamp(star.xFrac, 0.04, 0.96), y: height * clamp(star.yFrac, 0.03, 0.97))
        radial(cg, center: c, radius: size * 1.3,
               colors: [color.withAlphaComponent(70 / 255), UIColor.clear], locations: [0, 1])
        let rect = CGRect(x: c.x - size / 2, y: c.y - size / 2, width: size, height: size)
        glowFill(cg, path: StarShape(type: star.type).path(in: rect).cgPath, color: color, blur: 12)
        StarCrystal.draw(in: cg, type: star.type, colorIndex: star.colorIndex, rect: rect)
    }

    /// 작은 4꼭지 스파클(장식) — 흐린 글로우 + 또렷한 본체.
    private static func drawSparkle(_ cg: CGContext, center c: CGPoint, r: CGFloat, color: UIColor) {
        let path = StarShape(type: 0).path(in: circleRect(c, r)).cgPath
        glowFill(cg, path: path, color: color, blur: 3)
        cg.saveGState()
        cg.addPath(path)
        cg.setFillColor(color.cgColor)
        cg.fillPath()
        cg.restoreGState()
    }

    /// 제목 — 폭 82%, 최대 2줄(넘치면 말줄임), 행간 1.06, 한글은 어절 단위 줄바꿈.
    private static func drawTitle(_ cg: CGContext, text: String, accent: UIColor, center: CGPoint) {
        let font = UIFont.minSans(90)
        let para = NSMutableParagraphStyle()
        para.alignment = .center
        para.lineHeightMultiple = 1.06
        para.lineBreakMode = .byWordWrapping
        para.lineBreakStrategy = .hangulWordPriority
        let attrs: [NSAttributedString.Key: Any] = [
            .font: font, .foregroundColor: UIColor.white, .paragraphStyle: para,
        ]
        let str = NSAttributedString(string: text, attributes: attrs)
        let boxW = width * 0.82
        let full = str.boundingRect(with: CGSize(width: boxW, height: .greatestFiniteMagnitude),
                                    options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
        let twoLines = ceil(font.lineHeight * 1.06 * 2) + 4
        let boxH = min(ceil(full.height), twoLines)
        let box = CGRect(x: center.x - boxW / 2, y: center.y - boxH / 2, width: boxW, height: boxH)

        cg.saveGState()
        cg.setShadow(offset: .zero, blur: 24, color: accent.withAlphaComponent(90 / 255).cgColor)
        cg.beginTransparencyLayer(auxiliaryInfo: nil)
        str.draw(with: box, options: [.usesLineFragmentOrigin, .usesFontLeading, .truncatesLastVisibleLine], context: nil)
        // 글자 모양 안에만 흰색 → 별색 14% 섞인 흰색 세로 그라데이션(Android titlePaint.shader).
        cg.setBlendMode(.sourceAtop)
        linear(cg, rect: box, colors: [UIColor.white, blend(UIColor.white, accent, 0.14)], locations: [0, 1])
        cg.endTransparencyLayer()
        cg.restoreGState()
    }

    /// 위치 캡슐 — 별색 옅은 채움 + 상단 유리질 하이라이트 + 별색 테두리 + 가운데 텍스트.
    private static func drawLocationPill(_ cg: CGContext, text: String, accent: UIColor, center c: CGPoint) {
        let font = UIFont.minSans(44)
        let textColor = blend(accent, UIColor.white, 0.25)
        let textW = (text as NSString).size(withAttributes: [.font: font]).width
        let padH: CGFloat = 46
        let pillH: CGFloat = 92
        let rect = CGRect(x: c.x - textW / 2 - padH, y: c.y - pillH / 2, width: textW + padH * 2, height: pillH)

        cg.saveGState()
        accent.withAlphaComponent(26 / 255).setFill()
        UIBezierPath(roundedRect: rect, cornerRadius: pillH / 2).fill()
        UIColor.white.withAlphaComponent(24 / 255).setFill()
        UIBezierPath(roundedRect: CGRect(x: rect.minX + 2, y: rect.minY + 2,
                                         width: rect.width - 4, height: pillH * 0.5 - 2),
                     cornerRadius: pillH / 2).fill()
        let border = UIBezierPath(roundedRect: rect, cornerRadius: pillH / 2)
        border.lineWidth = 2
        accent.withAlphaComponent(140 / 255).setStroke()
        border.stroke()
        cg.restoreGState()

        drawCenteredText(text, font: font, color: textColor, center: c)
    }

    private static func drawCenteredText(_ text: String, font: UIFont, color: UIColor, center: CGPoint) {
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let size = (text as NSString).size(withAttributes: attrs)
        (text as NSString).draw(at: CGPoint(x: center.x - size.width / 2, y: center.y - size.height / 2),
                                withAttributes: attrs)
    }

    // MARK: - 그리기 헬퍼

    private static func glowFill(_ cg: CGContext, path: CGPath, color: UIColor, blur: CGFloat) {
        cg.saveGState()
        cg.setShadow(offset: .zero, blur: blur, color: color.cgColor)
        cg.addPath(path)
        cg.setFillColor(color.cgColor)
        cg.fillPath()
        cg.restoreGState()
    }

    private static func radial(_ cg: CGContext, center: CGPoint, radius: CGFloat, colors: [UIColor],
                               locations: [CGFloat], beforeStart: Bool = false, afterEnd: Bool = false) {
        guard radius > 0,
              let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                                 colors: colors.map(\.cgColor) as CFArray, locations: locations)
        else { return }
        var opts: CGGradientDrawingOptions = []
        if beforeStart { opts.insert(.drawsBeforeStartLocation) }
        if afterEnd { opts.insert(.drawsAfterEndLocation) }
        cg.drawRadialGradient(g, startCenter: center, startRadius: 0, endCenter: center, endRadius: radius, options: opts)
    }

    /// 세로 선형 그라데이션을 [rect] 안에만.
    private static func linear(_ cg: CGContext, rect: CGRect, colors: [UIColor], locations: [CGFloat]) {
        guard let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                                 colors: colors.map(\.cgColor) as CFArray, locations: locations)
        else { return }
        cg.saveGState()
        cg.clip(to: rect)
        cg.drawLinearGradient(g, start: CGPoint(x: rect.midX, y: rect.minY), end: CGPoint(x: rect.midX, y: rect.maxY),
                              options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        cg.restoreGState()
    }

    private static func circleRect(_ c: CGPoint, _ r: CGFloat) -> CGRect {
        CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)
    }

    private static func clamp(_ v: CGFloat, _ lo: CGFloat, _ hi: CGFloat) -> CGFloat { min(max(v, lo), hi) }

    /// a→b 로 t 만큼 섞은 색(Android ColorUtils.blendARGB).
    private static func blend(_ a: UIColor, _ b: UIColor, _ t: CGFloat) -> UIColor {
        var ar: CGFloat = 0, ag: CGFloat = 0, ab: CGFloat = 0, aa: CGFloat = 0
        var br: CGFloat = 0, bg: CGFloat = 0, bb: CGFloat = 0, ba: CGFloat = 0
        a.getRed(&ar, green: &ag, blue: &ab, alpha: &aa)
        b.getRed(&br, green: &bg, blue: &bb, alpha: &ba)
        return UIColor(red: ar + (br - ar) * t, green: ag + (bg - ag) * t,
                       blue: ab + (bb - ab) * t, alpha: aa + (ba - aa) * t)
    }

    // MARK: - 공유 실행

    /// 공유 본문(링크 포함). ⚠️ `LocaleManager.t` 는 한글에 U+2060 결합자를 끼워 넣으므로(화면 줄바꿈용)
    /// 다른 앱으로 나가는 문자열은 원문 값을 쓴다.
    @MainActor
    private static func shareText(link: String) -> String {
        String(format: L10n.shareDiaryWithLink.value(for: LocaleManager.shared.effectiveLanguage), link)
    }

    /// 일반 공유 시트 — 카드 이미지 + 링크 본문. 시트가 닫히면 [onFinish].
    @MainActor
    static func shareAsImage(_ image: UIImage, diaryId: String, onFinish: @escaping () -> Void) {
        let link = AppConfig.shareLink(diaryId: diaryId)
        let vc = UIActivityViewController(activityItems: [image, shareText(link: link)], applicationActivities: nil)
        vc.completionWithItemsHandler = { _, _, _, _ in onFinish() }
        guard let top = topViewController() else { onFinish(); return }
        // iPad 는 팝오버 앵커가 없으면 크래시.
        vc.popoverPresentationController?.sourceView = top.view
        vc.popoverPresentationController?.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY,
                                                              width: 0, height: 0)
        top.present(vc, animated: true)
    }

    /// 인스타 스토리 직접 공유 — 카드를 스토리 배경으로. 인스타가 없으면 false(→ 호출부가 일반 공유로 폴백).
    ///
    /// 링크 스티커(contentURL)는 Meta 앱 ID(`source_application`)가 있어야만 붙는다 — 없으면 인스타가 무시하므로
    /// 링크를 같은 페이스트보드 항목의 텍스트로도 넣고 "링크 스티커로 붙여넣기" 안내를 띄운다(Android 와 같은 정책).
    @MainActor
    static func shareToInstagramStory(_ image: UIImage, diaryId: String) -> Bool {
        let appId = ((Bundle.main.object(forInfoDictionaryKey: "INSTAGRAM_APP_ID") as? String) ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        var comps = URLComponents()
        comps.scheme = "instagram-stories"
        comps.host = "share"
        if !appId.isEmpty { comps.queryItems = [URLQueryItem(name: "source_application", value: appId)] }
        guard let url = comps.url, UIApplication.shared.canOpenURL(url), let png = image.pngData() else { return false }

        let link = AppConfig.shareLink(diaryId: diaryId)
        var item: [String: Any] = [
            "com.instagram.sharedSticker.backgroundImage": png,
            "public.utf8-plain-text": link,
        ]
        if !appId.isEmpty { item["com.instagram.sharedSticker.contentURL"] = link }
        UIPasteboard.general.setItems([item], options: [.expirationDate: Date().addingTimeInterval(300)])
        GlobalToast.shared.show(LocaleManager.shared.t(.shareStoryLinkCopied), seconds: 3.5)
        UIApplication.shared.open(url)
        return true
    }

    /// 공유 시트를 띄울 최상단 뷰컨트롤러(편집기 fullScreenCover 위).
    @MainActor
    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

private extension UIColor {
    /// 0xRRGGBB 불투명 색.
    convenience init(rgb: UInt32) {
        self.init(red: CGFloat((rgb >> 16) & 0xFF) / 255, green: CGFloat((rgb >> 8) & 0xFF) / 255,
                  blue: CGFloat(rgb & 0xFF) / 255, alpha: 1)
    }
}
