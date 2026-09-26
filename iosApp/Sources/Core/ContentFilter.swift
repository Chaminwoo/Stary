import Foundation

/// 부적절한 표현 필터 — App Store Guideline 1.2(사용자 생성 콘텐츠) "A method for filtering objectionable content"(2026-09-26).
/// Android `core.util.ContentFilter` 패리티 — **목록·규칙 동일 유지**(값 drift 금지).
///
/// - 올리기 차단: 다이어리(제목·본문, 새 글/수정) · 댓글 · 채팅 메시지 · 닉네임에 걸리면 저장하지 않고 안내.
/// - 보이기 차단: 지도/목록의 남의 다이어리 · 남의 댓글 중 걸리는 것은 숨긴다.
///
/// 판정: 한국어·일본어는 숫자·기호만 지우고 **단어 단위**로 부분 문자열 검사(+ 한 글자 토막이 이어진 부분은 붙여서 —
/// "씨 발"). 공백까지 지우면 "다시 발걸음" 같은 단어 경계 오탐이 생긴다. 영어는 **단어 경계**로만.
enum ContentFilter {

    private static let cjkTerms: [String] = [
        // 한국어 욕설·비하
        "씨발", "시발", "씨바", "씨팔", "시팔", "씨빨", "씨벌", "쓰벌", "ㅅㅂ", "ㅆㅂ", "ㅆㅃ",
        "병신", "븅신", "빙신", "ㅂㅅ", "개새끼", "개새기", "개색기", "개색끼", "개세끼", "개쉐이",
        "좆", "존나", "지랄", "염병", "엠창", "느금마", "니애미", "니미럴", "애미뒤", "애비뒤",
        "창녀", "창놈", "걸레년", "미친년", "미친놈", "썅", "쌍년", "쌍놈", "호로새끼", "후레자식",
        "섹스", "강간", "뒈져", "한남충", "김치녀", "틀딱", "급식충", "짱깨", "쪽바리",
        // 일본어
        "死ね", "氏ね", "殺す", "きちがい", "キチガイ", "基地外", "レイプ", "強姦", "クソ野郎", "くそやろう", "ガイジ", "ちんこ",
    ]

    private static let enWords: [String] = [
        "fuck", "fucks", "fucked", "fucker", "fuckers", "fucking", "motherfucker", "shit", "shitty", "bullshit",
        "bitch", "bitches", "cunt", "cunts", "nigger", "niggers", "nigga", "niggas", "faggot", "faggots", "fag",
        "retard", "retarded", "whore", "whores", "slut", "sluts", "rape", "rapist", "asshole", "assholes",
        "bastard", "dickhead", "porn", "pussy", "kys",
    ]

    /// 걸리는 말이 들어 있지만 정상적으로 쓰이는 단어 — 먼저 지운다.
    private static let allow: [String] = ["시발점", "시발역", "유니섹스"]

    private static let enRegex = try? NSRegularExpression(
        pattern: "\\b(" + enWords.joined(separator: "|") + ")\\b")
    private static let killYourself = try? NSRegularExpression(pattern: "kill\\s+your\\s*self")

    /// 부적절한 표현이 들어 있는가.
    static func isObjectionable(_ text: String?) -> Bool {
        guard let text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return false }
        let lower = text.lowercased()
        let range = NSRange(lower.startIndex..., in: lower)
        if enRegex?.firstMatch(in: lower, range: range) != nil { return true }
        if killYourself?.firstMatch(in: lower, range: range) != nil { return true }
        // 숫자·기호 제거(공백은 유지) → 단어로 나눈다.
        let cleaned = String(lower.filter { $0.isLetter || $0.isWhitespace })
        let tokens = cleaned.split(whereSeparator: { $0.isWhitespace }).map(String.init)
        var chunks = tokens
        // 한 글자 토막이 이어진 곳은 붙여서 한 덩어리로("씨 발" → "씨발").
        var run = ""
        for t in tokens + [""] {
            if t.count == 1 {
                run += t
            } else {
                if run.count > 1 { chunks.append(run) }
                run = ""
            }
        }
        return chunks.contains { chunk in
            var c = chunk
            for a in allow { c = c.replacingOccurrences(of: a, with: "") }
            return cjkTerms.contains { c.contains($0) }
        }
    }

    /// 여러 칸(제목+본문 등) 중 하나라도 걸리면 true.
    static func anyObjectionable(_ texts: String?...) -> Bool {
        texts.contains { isObjectionable($0) }
    }
}
