import SwiftUI

/// 앱에 내장되지 않은 별 모양·색 — starType ≥ `StarStyle.typeCount`, starColor ≥ `StarStyle.colorCount` (2026-10-22).
/// Android `core.designsystem.StarCatalog` 패리티.
///
/// 새 모양·색(성운 상자 보상 등)은 앱 업데이트 없이 DB 로만 늘어난다. 그래서 그런 별은 문서에 그릴 정보를 함께 싣는다:
/// `Diary.starPath`(SVG 경로) · `Diary.starColors`(hex 정지점). 앱은 처음 보는 번호를 여기에 배워 두고 `StarStyle`/`StarShape` 가
/// 이 정의로 그린다. 정보가 없는 번호는 `StarStyle.safeType`/`safeColor` 가 기본 별·흰색으로 바꿔 그린다.
///
/// ⚠️ 경로 규칙 — Android `PathParser` 와 같은 결과여야 한다:
///  - 0~1 단위 정사각형(좌상단 원점, y 는 아래로) 안의 SVG path data. 명령은 **M L H V C S Q T Z**(대·소문자).
///  - **A(호)는 쓰지 않는다**(이 파서 미지원 — 만나면 그 경로는 버린다). 채움 규칙 non-zero.
/// 색 규칙 — "#RRGGBB" 1~3개(1개 = 단색, 2~3개 = 좌상→우하 그라데이션). 흰색을 섞지 않고 그대로 쓴다.
final class StarCatalog: @unchecked Sendable {
    static let shared = StarCatalog()
    private init() {}

    private static let maxStops = 3
    private let lock = NSLock()
    private var shapeSvgs: [Int: String] = [:]
    private var shapePaths: [Int: CGPath] = [:]   // 단위(0~1) 경로
    private var colorHex: [Int: [String]] = [:]
    private var colorRaw: [Int: [UInt32]] = [:]

    /// 별 문서에 실린 그림 정보를 배운다(디코드할 때마다 — 같은 값이면 아무것도 안 한다). 어느 스레드에서 불러도 된다.
    func learn(type: Int, path: String, color: Int, colors: [String]) {
        learnShape(type, path)
        learnColor(color, colors)
    }

    func learnShape(_ type: Int, _ svg: String) {
        guard type >= StarStyle.typeCount, !svg.isEmpty else { return }
        lock.lock(); let same = shapeSvgs[type] == svg; lock.unlock()
        guard !same, let p = Self.parse(svg) else { return }
        lock.lock(); shapeSvgs[type] = svg; shapePaths[type] = p; lock.unlock()
    }

    func learnColor(_ index: Int, _ hexes: [String]) {
        guard index >= StarStyle.colorCount, !hexes.isEmpty else { return }
        let stops = Array(hexes.prefix(Self.maxStops))
        let parsed = stops.compactMap(Self.parseHex)
        guard !parsed.isEmpty else { return }
        lock.lock(); colorHex[index] = stops; colorRaw[index] = parsed; lock.unlock()
    }

    func hasShape(_ type: Int) -> Bool { lock.lock(); defer { lock.unlock() }; return shapePaths[type] != nil }
    func hasColor(_ index: Int) -> Bool { lock.lock(); defer { lock.unlock() }; return colorRaw[index] != nil }

    /// 업로드할 때 문서에 실을 값 — 내장 번호면 nil(싣지 않음).
    func shapeSvg(_ type: Int) -> String? { lock.lock(); defer { lock.unlock() }; return shapeSvgs[type] }
    func colorHexes(_ index: Int) -> [String]? { lock.lock(); defer { lock.unlock() }; return colorHex[index] }

    /// 단위(0~1) 경로 — 호출부가 원하는 크기로 배율을 준다.
    func unitPath(_ type: Int) -> CGPath? { lock.lock(); defer { lock.unlock() }; return shapePaths[type] }

    /// 정지점 원본 hex 값(0xRRGGBB) — nil = 배운 적 없음.
    func rawColors(_ index: Int) -> [UInt32]? { lock.lock(); defer { lock.unlock() }; return colorRaw[index] }

    // MARK: - 파싱

    private static func parseHex(_ s: String) -> UInt32? {
        var h = s.trimmingCharacters(in: .whitespaces)
        if h.hasPrefix("#") { h.removeFirst() }
        guard h.count == 6, let v = UInt32(h, radix: 16) else { return nil }
        return v
    }

    /// SVG path data → CGPath. 지원 명령: M L H V C S Q T Z(대·소문자). 그 밖(A 등)이 나오면 nil.
    static func parse(_ d: String) -> CGPath? {
        var tokens = SVGTokens(d)
        let path = CGMutablePath()
        var cur = CGPoint.zero
        var start = CGPoint.zero
        var lastCubicCtrl: CGPoint?
        var lastQuadCtrl: CGPoint?
        var cmd: Character?
        var started = false

        while let next = tokens.peekCommandOrNumber() {
            if case .command(let c) = next {
                tokens.consumeCommand()
                cmd = c
                if c == "Z" || c == "z" {
                    if started { path.closeSubpath() }
                    cur = start
                    lastCubicCtrl = nil; lastQuadCtrl = nil
                    continue
                }
            }
            guard let c = cmd else { return nil }
            let rel = c.isLowercase
            func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { rel ? CGPoint(x: cur.x + x, y: cur.y + y) : CGPoint(x: x, y: y) }

            switch c.uppercased().first! {
            case "M":
                guard let x = tokens.number(), let y = tokens.number() else { return nil }
                cur = pt(x, y); start = cur
                path.move(to: cur); started = true
                // M 뒤에 이어지는 좌표 쌍은 L 로 본다(SVG 규칙).
                cmd = rel ? "l" : "L"
                lastCubicCtrl = nil; lastQuadCtrl = nil
            case "L":
                guard started, let x = tokens.number(), let y = tokens.number() else { return nil }
                cur = pt(x, y); path.addLine(to: cur)
                lastCubicCtrl = nil; lastQuadCtrl = nil
            case "H":
                guard started, let x = tokens.number() else { return nil }
                cur = CGPoint(x: rel ? cur.x + x : x, y: cur.y); path.addLine(to: cur)
                lastCubicCtrl = nil; lastQuadCtrl = nil
            case "V":
                guard started, let y = tokens.number() else { return nil }
                cur = CGPoint(x: cur.x, y: rel ? cur.y + y : y); path.addLine(to: cur)
                lastCubicCtrl = nil; lastQuadCtrl = nil
            case "C":
                guard started, let x1 = tokens.number(), let y1 = tokens.number(), let x2 = tokens.number(),
                      let y2 = tokens.number(), let x = tokens.number(), let y = tokens.number() else { return nil }
                let c1 = pt(x1, y1), c2 = pt(x2, y2), end = pt(x, y)
                path.addCurve(to: end, control1: c1, control2: c2)
                cur = end; lastCubicCtrl = c2; lastQuadCtrl = nil
            case "S":
                guard started, let x2 = tokens.number(), let y2 = tokens.number(),
                      let x = tokens.number(), let y = tokens.number() else { return nil }
                let c1 = lastCubicCtrl.map { CGPoint(x: 2 * cur.x - $0.x, y: 2 * cur.y - $0.y) } ?? cur
                let c2 = pt(x2, y2), end = pt(x, y)
                path.addCurve(to: end, control1: c1, control2: c2)
                cur = end; lastCubicCtrl = c2; lastQuadCtrl = nil
            case "Q":
                guard started, let x1 = tokens.number(), let y1 = tokens.number(),
                      let x = tokens.number(), let y = tokens.number() else { return nil }
                let q = pt(x1, y1), end = pt(x, y)
                path.addQuadCurve(to: end, control: q)
                cur = end; lastQuadCtrl = q; lastCubicCtrl = nil
            case "T":
                guard started, let x = tokens.number(), let y = tokens.number() else { return nil }
                let q = lastQuadCtrl.map { CGPoint(x: 2 * cur.x - $0.x, y: 2 * cur.y - $0.y) } ?? cur
                let end = pt(x, y)
                path.addQuadCurve(to: end, control: q)
                cur = end; lastQuadCtrl = q; lastCubicCtrl = nil
            default:
                return nil // A 등 미지원 명령
            }
        }
        // non-zero 기준으로 정규화 — 그리는 쪽(지도 마커 후광 등)이 even-odd 로 채워도 같은 모양이 되게(내장 모양과 같은 성질).
        return started ? path.normalized(using: .winding) : nil
    }
}

/// SVG path data 토크나이저 — 명령 글자와 숫자(부호·소수점·지수, 쉼표/공백 구분, "1.5.5"·"1-2" 같은 붙여 쓰기 포함).
private struct SVGTokens {
    enum Token { case command(Character), number }
    private let chars: [Character]
    private var i = 0

    init(_ s: String) { chars = Array(s) }

    private mutating func skipSeparators() {
        while i < chars.count, chars[i] == " " || chars[i] == "," || chars[i] == "\n" || chars[i] == "\t" || chars[i] == "\r" { i += 1 }
    }

    mutating func peekCommandOrNumber() -> Token? {
        skipSeparators()
        guard i < chars.count else { return nil }
        let c = chars[i]
        if c.isLetter && c != "e" && c != "E" { return .command(c) }
        return .number
    }

    mutating func consumeCommand() { i += 1 }

    mutating func number() -> CGFloat? {
        skipSeparators()
        guard i < chars.count else { return nil }
        let startIndex = i
        if chars[i] == "+" || chars[i] == "-" { i += 1 }
        var sawDot = false, sawDigit = false
        while i < chars.count {
            let c = chars[i]
            if c.isNumber { sawDigit = true; i += 1 }
            else if c == "." && !sawDot { sawDot = true; i += 1 }
            else { break }
        }
        if i < chars.count, chars[i] == "e" || chars[i] == "E" {
            var j = i + 1
            if j < chars.count, chars[j] == "+" || chars[j] == "-" { j += 1 }
            if j < chars.count, chars[j].isNumber {
                i = j
                while i < chars.count, chars[i].isNumber { i += 1 }
            }
        }
        guard sawDigit, let v = Double(String(chars[startIndex..<i])) else { i = startIndex; return nil }
        return CGFloat(v)
    }
}
