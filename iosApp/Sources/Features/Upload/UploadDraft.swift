import Foundation

/// 업로드 화면에서 쓰다 나간 글 — 제목·본문·별 모양/색·공개 범위·별 고리만 기기에 남긴다(사진·움짤은 다시 고른다).
struct UploadDraft: Codable, Equatable {
    var title: String
    var content: String
    var starType: Int
    var starColor: Int
    var visibility: String
    /// 별 고리(2026-10-26) — 옵셔널이라 예전에 남긴 글(키 없음)도 그대로 읽힌다. nil = 미착용.
    var ring: String? = nil

    var hasText: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

/// 계정마다 따로 둔다(다른 계정으로 로그인하면 보이지 않게). 저장에 성공하면 지운다. Android `UploadDraftStore` 패리티.
enum UploadDraftStore {
    private static func key(_ uid: String) -> String { "upload_draft_\(uid)" }

    static func load(uid: String) -> UploadDraft? {
        guard let data = UserDefaults.standard.data(forKey: key(uid)),
              let draft = try? JSONDecoder().decode(UploadDraft.self, from: data),
              draft.hasText else { return nil }
        return draft
    }

    static func save(_ draft: UploadDraft, uid: String) {
        guard let data = try? JSONEncoder().encode(draft) else { return }
        UserDefaults.standard.set(data, forKey: key(uid))
    }

    static func clear(uid: String) {
        UserDefaults.standard.removeObject(forKey: key(uid))
    }
}
