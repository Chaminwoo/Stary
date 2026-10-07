import Foundation

/// `diaries/{id}.translations` — 서버(Cloud Functions `translateDiaryOn*`)가 채우는 자동 선번역. 읽기 전용.
/// 언어 코드가 key 라서 언어가 늘어도 이 모델은 그대로다(functions/translations.js 머리말 참고).
/// Android `DiaryTranslations`(shared Diary.kt) 패리티 — 표시 판단에 필요한 필드만 읽는다.
struct DiaryTranslations: Decodable, Hashable {
    /// 감지된 원문 언어("ko" 등).
    var sourceLang: String = ""
    var title: [String: String] = [:]
    var content: [String: String] = [:]

    enum CodingKeys: String, CodingKey {
        case sourceLang, title, content
    }

    init(sourceLang: String = "", title: [String: String] = [:], content: [String: String] = [:]) {
        self.sourceLang = sourceLang
        self.title = title
        self.content = content
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        sourceLang = (try? c.decodeIfPresent(String.self, forKey: .sourceLang)) ?? ""
        title = (try? c.decodeIfPresent([String: String].self, forKey: .title)) ?? [:]
        content = (try? c.decodeIfPresent([String: String].self, forKey: .content)) ?? [:]
    }
}

/// 화면에 **보여 줄** 제목/본문 — 앱 언어(ko/en/ja)의 서버 선번역이 있으면 번역, 없으면 원문.
/// 번역이 없는 예전 글·번역 전/실패 글도 같은 호출로 원문이 나오므로 호출부에 분기가 필요 없다.
///
/// ⚠️ **표시 전용.** 수정 입력칸 초기값·신고·공유 카드·알림 제목은 `diary.title`/`diary.content`(원문)를 써야 한다 —
///    번역문이 원문으로 저장되거나 신고에 실리면 안 된다. Android `core/util/DiaryText.kt` 패리티.
extension Diary {

    /// `lang` 으로 보여 줄 수 있는 번역. 아래일 때만 non-nil:
    ///  - 표시 언어가 원문 언어와 다르다(같으면 번역이 필요 없다),
    ///  - 번역이 **지금 원문의 번역**이다 — 번역 맵의 원문 언어 칸은 번역 당시 원문이라, 글을 수정한 직후
    ///    재번역이 끝나기 전의 낡은 번역은 걸러진다.
    func translation(for lang: String) -> DiaryTranslations? {
        guard let t = translations, !t.sourceLang.isEmpty, t.sourceLang != lang else { return nil }
        guard t.title[t.sourceLang] == title, t.content[t.sourceLang] == content else { return nil }
        return t
    }

    func localizedTitle(_ lang: String) -> String {
        if let s = translation(for: lang)?.title[lang], !s.isEmpty { return s }
        return title
    }

    func localizedContent(_ lang: String) -> String {
        if let s = translation(for: lang)?.content[lang], !s.isEmpty { return s }
        return content
    }

    /// `lang` 로 보여 주는 글이 원문과 다른가(= "번역됨" 표시/원문 보기 토글이 필요한가).
    func isTranslated(for lang: String) -> Bool {
        localizedTitle(lang) != title || localizedContent(lang) != content
    }

    // MARK: 앱 언어 기준 (LocaleManager 는 MainActor — 뷰 body 에서 호출)

    @MainActor func titleForDisplay() -> String { localizedTitle(LocaleManager.shared.effectiveLanguage) }
    @MainActor func contentForDisplay() -> String { localizedContent(LocaleManager.shared.effectiveLanguage) }
    @MainActor func isTranslatedForDisplay() -> Bool { isTranslated(for: LocaleManager.shared.effectiveLanguage) }
}
