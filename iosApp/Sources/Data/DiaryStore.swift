import Foundation
import SwiftUI

/// 전체/내 다이어리 목록을 구독해 화면들이 공유하는 관찰 가능 저장소.
@MainActor
final class DiaryStore: ObservableObject {
    @Published var diaries: [Diary] = []
    @Published var loading = true

    private let repo = DiaryRepository()
    private var started = false
    private var startedUid: String?

    /// uid 가 바뀔 때만 재구독.
    func startIfNeeded(uid: String?) {
        if started && startedUid == uid { return }
        started = true
        startedUid = uid
        repo.stopAll()
        // 스크린샷 촬영 모드 — 서버 대신 내장 샘플 별(Core/ScreenshotMode.swift).
        if ScreenshotMode.isActive {
            diaries = ScreenshotMode.demoDiaries()
            loading = false
            return
        }
        // 로그인 전에는 구독하지 않는다. 로그아웃이면 목록을 비운다(나만 보기 글이 남아 있으면 안 된다).
        guard let uid, !uid.isEmpty else {
            diaries = []
            loading = true
            return
        }
        loading = true
        repo.observeAll(currentUid: uid) { [weak self] list in
            guard let self else { return }
            self.diaries = list
            self.loading = false
        }
    }

    /// id 로 별 하나 얻기 — 알림 탭(푸시·알림 목록·상단 배너)이 가리키는 별을 열 때 쓴다.
    ///
    /// 구독 목록(최신 1000개)에 있으면 그것을 쓰고, **아직 로드 전(앱을 알림으로 막 켠 경우)이거나
    /// 목록 창 밖이면 Firestore 에서 직접 읽는다.** 예전엔 목록에서만 찾고 없으면 아무 일도 하지 않아
    /// "알림을 눌러도 반응이 없다"로 보였다. 남의 나만 보기 글은 열지 않는다.
    func diary(id: String, viewerUid: String?) async -> Diary? {
        guard DiaryRepository.isValidDocId(id) else { return nil }
        if let d = diaries.first(where: { $0.id == id }) { return d }
        // 읽기가 거부되면(권한 없음) 못 찾은 것으로 처리 — 호출부가 "사라진 별" 안내를 띄운다.
        guard let snap = try? await FirestoreService.diaries.safeDocument(id).getDocument(),
              snap.exists,
              let raw = try? snap.data(as: Diary.self),
              let d = raw.sanitized() else { return nil }
        if d.visibilityType == "private" && d.userId != viewerUid { return nil }
        return d
    }

    func mine(uid: String?) -> [Diary] {
        guard let uid else { return [] }
        return diaries.filter { $0.userId == uid }
    }

    @discardableResult
    func save(_ diary: Diary) async throws -> String { try await repo.save(diary) }
    func delete(_ id: String) async throws { try await repo.delete(id) }
    func incrementView(_ id: String) async {
        guard !ScreenshotMode.isActive else { return } // 샘플 별은 서버에 없다
        guard DiaryRepository.isValidDocId(id) else { return }
        await repo.incrementViewCount(id)
    }

    /// 새 글 작성 시 친구들에게 FRIEND_POST 인앱 알림 생성(Android notifyFriendPost 대응).
    /// (private 글은 호출하지 않음)
    func notifyFriends(uid: String, name: String, diaryId: String, title: String) async {
        do {
            let friends = try await FirestoreService.friends(of: uid).getDocuments()
            guard !friends.isEmpty else { return }
            let now = FirestoreService.nowMillis
            let batch = FirestoreService.db.batch()
            for doc in friends.documents {
                batch.setData([
                    "type": "FRIEND_POST", "diaryId": diaryId, "diaryTitle": title,
                    "diaryOwnerId": doc.documentID, "actorId": uid, "actorName": name,
                    "content": "", "createdAt": now, "read": false,
                ], forDocument: FirestoreService.notifications.document())
            }
            try await batch.commit()
        } catch {}
    }
}
