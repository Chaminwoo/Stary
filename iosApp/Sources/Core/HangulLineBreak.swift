import Foundation

/// 한글 줄바꿈 다듬기.
///
/// iOS 의 기본 줄바꿈 전략(`.standard`)은 한글을 **글자 단위**로 끊는다 —
/// "지도에 표시하\n기 위해" 처럼 어절 중간에서 잘려 읽기가 어색하다.
/// SwiftUI `Text` 에는 `NSParagraphStyle.lineBreakStrategy(.hangulWordPriority)` 를 줄 방법이 없어서,
/// **어절(공백으로 나뉜 덩어리) 안에 U+2060 WORD JOINER 를 끼워** 어절 중간 줄바꿈을 막는다.
/// 결과적으로 줄은 공백에서만 바뀌어 한글도 영문처럼 낱말 단위로 접힌다.
/// (한 어절이 한 줄보다 길면 CoreText 가 어쩔 수 없이 끊으므로 넘침 걱정은 없다.)
extension String {

    /// 줄바꿈 금지 결합자 — 폭 0, 화면에는 보이지 않는다.
    private static let wordJoiner: Character = "\u{2060}"

    /// 이 문자열에 한글(음절/자모)이 들어 있는가.
    var containsHangul: Bool {
        unicodeScalars.contains { scalar in
            (0xAC00...0xD7A3).contains(scalar.value)      // 한글 음절
                || (0x1100...0x11FF).contains(scalar.value)  // 한글 자모
                || (0x3130...0x318F).contains(scalar.value)  // 호환 자모
        }
    }

    /// 어절 단위로만 줄이 바뀌도록 다듬은 문자열. 한글이 없으면 원문 그대로.
    ///
    /// - 공백/줄바꿈은 그대로 두어 **줄바꿈 기회는 어절 사이에만** 남긴다.
    /// - `%` 가 든 덩어리(형식 지정자: `%@`, `%d개` 등)는 손대지 않는다 —
    ///   결합자가 끼면 `String(format:)` 이 깨진다.
    var hangulWordWrapped: String {
        guard containsHangul else { return self }
        var out = ""
        out.reserveCapacity(count * 2)
        var token = ""

        func flushToken() {
            guard !token.isEmpty else { return }
            if token.contains("%") || !token.containsHangul {
                out += token
            } else {
                out += token.map { String($0) }.joined(separator: String(Self.wordJoiner))
            }
            token.removeAll(keepingCapacity: true)
        }

        for ch in self {
            if ch.isWhitespace || ch.isNewline {
                flushToken()
                out.append(ch)
            } else {
                token.append(ch)
            }
        }
        flushToken()
        return out
    }
}
