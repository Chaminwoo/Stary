import CoreText
import SwiftUI
import UIKit

/// 앱 커스텀 폰트. (Android `core/designsystem/Type.kt` 와 **같은 폰트 파일**을 쓴다)
///
/// - MinSans   : 한글 본문·UI 전반(= Android `MinSans`). **가변 폰트**(wght 100~900, 기본 100).
///               기본 인스턴스가 Thin(100) 이라 그냥 쓰면 안드로이드보다 훨씬 얇게 나온다 →
///               항상 [MinSansWeight] 로 wght 축을 지정해서 만든다.
/// - PoetsenOne: 영문 디스플레이/타이틀(Android Typography 의 display*/headline*/titleLarge).
///               Android 도 실사용 화면은 거의 없다(정의만 유지).
///
/// ⚠️ 폰트 파일은 `androidApp/src/main/res/font/min_sans.ttf` **한 곳**을 iOS 타깃이 그대로
///    참조한다(project.yml sources) — 9MB 짜리를 복제해 값이 어긋나는 일을 막기 위함.
enum AppFont {
    /// PostScript 이름(nameID 6). 파일명이 아니라 이 이름으로 조회한다.
    static let display = "PoetsenOne-Regular"

    /// MinSans 조회 이름. min_sans.ttf 의 PostScript 이름은 `MinSansVF-VF`(nameID 6),
    /// 패밀리는 `Min Sans VF VF`(nameID 1) 인데, 폰트 파일을 교체하면 이름이 달라질 수 있어
    /// **등록된 폰트에서 실제로 찾아지는 이름**을 해석해 쓴다(빈 문자열 = 미등록 → 시스템 폰트).
    /// ⚠️ 성공한 결과만 캐시한다 — 아주 이른 시점(앱 init)에 실패해도 다음 호출에서 다시 찾도록.
    private static var resolvedBody: String?
    static var body: String {
        if let cached = resolvedBody { return cached }
        let name = resolveBodyFontName()
        if !name.isEmpty { resolvedBody = name }
        return name
    }

    private static func resolveBodyFontName() -> String {
        let candidates = ["MinSansVF-VF", "Min Sans VF VF", "MinSansVF", "Min Sans VF", "MinSans"]
        for name in candidates where UIFont(name: name, size: 12) != nil { return name }
        // 이름이 바뀌었어도 "minsans" 를 포함하는 패밀리가 있으면 그 첫 서체를 쓴다.
        for family in UIFont.familyNames
        where family.replacingOccurrences(of: " ", with: "").lowercased().contains("minsans") {
            if let first = UIFont.fontNames(forFamilyName: family).first { return first }
        }
        print("⚠️ MinSans 폰트 미등록 — project.yml UIAppFonts / sources 확인(시스템 폰트로 대체)")
        return ""
    }
}

/// Android `Type.kt` 의 "요청 굵기 → 실제 wght 축" 매핑을 그대로 옮긴 값.
/// (가짜 볼드(faux bold) 없이 전체적으로 얇게 — 표 자체가 패리티 기준이라 임의로 올리지 말 것.)
enum MinSansWeight: CGFloat {
    case light = 300        // FontWeight.Light
    case normal = 400       // FontWeight.Normal
    case medium = 450       // FontWeight.Medium
    case semibold = 500     // FontWeight.SemiBold
    case bold = 550         // FontWeight.Bold
}

extension UIFont {
    /// MinSans 가변 폰트 인스턴스. 실패하면 시스템 폰트로 안전 폴백.
    /// (UIFont 생성이 싸지 않아 size+wght 로 캐시 — 목록 셀마다 매번 만들지 않게.)
    static func minSans(_ size: CGFloat, _ weight: MinSansWeight = .normal) -> UIFont {
        let key = "\(size)_\(weight.rawValue)" as NSString
        if let cached = MinSansCache.shared.object(forKey: key) { return cached }

        let font: UIFont
        let name = AppFont.body
        if !name.isEmpty {
            // 'wght' 축(0x77676874)에 원하는 값을 넣어 인스턴스화.
            let variation: [UInt32: CGFloat] = [0x77676874: weight.rawValue]
            let descriptor = UIFontDescriptor(fontAttributes: [
                .name: name,
                UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): variation,
            ])
            font = UIFont(descriptor: descriptor, size: size)
            MinSansCache.shared.setObject(font, forKey: key) // 성공한 것만 캐시
        } else {
            // 폰트 미등록(project.yml UIAppFonts 누락 등) — 레이아웃이 깨지지 않게 시스템 폰트.
            font = .systemFont(ofSize: size, weight: weight.systemWeight)
        }
        return font
    }
}

private enum MinSansCache {
    static let shared: NSCache<NSString, UIFont> = {
        let c = NSCache<NSString, UIFont>()
        c.countLimit = 120
        return c
    }()
}

private extension MinSansWeight {
    /// 폴백(시스템 폰트)용 근사 굵기.
    var systemWeight: UIFont.Weight {
        switch self {
        case .light: return .light
        case .normal: return .regular
        case .medium: return .medium
        case .semibold: return .semibold
        case .bold: return .bold
        }
    }
}

extension Font {
    /// 앱 기본 폰트(MinSans). 크기는 Android 의 sp 값을 그대로 쓴다.
    /// ⚠️ 고정 크기(Dynamic Type 비례 확대 없음) — Android 가 폰트 배율 상한(1.15)을 두는 것과 같은 취지로,
    ///    고정 높이 카드/칩이 많은 이 앱에서 글자가 잘리지 않게 하기 위함.
    static func minSans(_ size: CGFloat, _ weight: MinSansWeight = .normal) -> Font {
        Font(UIFont.minSans(size, weight))
    }

    /// 영문 디스플레이/타이틀용(PoetsenOne).
    static func poetsenOne(_ size: CGFloat) -> Font { .custom(AppFont.display, size: size) }
}
