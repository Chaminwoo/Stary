import FirebaseAuth
import Foundation
import SwiftUI

/// 해금한(열어 본) 다른 사람의 게시물(다이어리) 기록 — **영구**.
///
/// 100m 밖 게시물은 제목만 보이고 미디어/본문/댓글이 잠긴다(DetailScreen). 잠금은
/// ① 100m 이내로 접근 ② 보상형 광고 시청 — 둘 중 하나로 풀리고, **한 번 풀린 게시물은 이 기기에서 계속 열려 있다**
/// (2026-09-22 "다이어리도 아예 해금 형식" 피드백 — 예전 7일 TTL 폐지, 접근 해금도 기록).
/// ⚠️ 해금은 **열람**만 연다. 댓글 작성은 해금 여부와 무관하게 항상 100m 이내에서만 가능하다.
///
/// Android `core.util.DiaryUnlockStore` 패리티 — diaryId → 해금 시각(ms, 기록용 — 존재 = 해금).
/// `ObservableObject` 라 [unlock] 시 관찰 중인 화면이 즉시 갱신된다.
/// 기기 로컬(UserDefaults)이 1차 저장소이고, **서버 사본**(`users/{uid}/viewedDiaries/{id}.unlockedAt`)에도 남긴다
/// (2026-09-26 별 도감 "열람한 모든 다이어리" 요청). `syncWithServer` 가 앱 시작 시 둘을 합친다 —
/// 기기를 바꿔도 복원되고, 잠금이 생기기 전(2026-09-21)에 열람한 글도 "연 것"으로 들어온다.
@MainActor
final class DiaryUnlockStore: ObservableObject {
    static let shared = DiaryUnlockStore()

    /// 키 이름은 7일 TTL 시절(`AdUnlockStore`) 그대로 — 그때 광고로 연 기록도 영구 해금으로 승계된다.
    private static let prefsKey = "ad_unlock_store"

    /// diaryId → 해금 시각(ms).
    @Published private(set) var unlockedAt: [String: Int64] = [:]

    private init() {
        let raw = UserDefaults.standard.dictionary(forKey: Self.prefsKey) as? [String: NSNumber] ?? [:]
        unlockedAt = raw.mapValues { $0.int64Value }
    }

    /// 이 게시물이 해금돼 있는지.
    func isUnlocked(_ diaryId: String) -> Bool {
        unlockedAt[diaryId] != nil
    }

    /// 광고 시청 완료 또는 100m 이내 접근 → 이 게시물을 영구 해금한다(이미 해금이면 무시). 서버 사본도 남긴다.
    func unlock(_ diaryId: String) {
        guard unlockedAt[diaryId] == nil else { return }
        let now = FirestoreService.nowMillis
        unlockedAt[diaryId] = now
        save()
        if let uid = Auth.auth().currentUser?.uid {
            Task { await ViewedRepository.markUnlocked(uid: uid, unlocks: [diaryId: now]) }
        }
    }

    private func save() {
        UserDefaults.standard.set(
            unlockedAt.mapValues { NSNumber(value: $0) },
            forKey: Self.prefsKey
        )
    }

    /// 이번 실행에서 동기화를 마친 uid — 화면마다 불러도 1회만.
    private var syncedUid: String?
    /// 로컬 기록을 서버로 올리는 1회성 이관을 마친 uid(UserDefaults 키).
    private static let backfilledKey = "ad_unlock_store_backfilled_uid"

    /// 로컬 ↔ 서버 해금 기록을 합친다(실행당 uid 1회, 실패하면 다음 호출 때 재시도). Android `syncWithServer` 패리티.
    ///  ① 서버 `unlockedAt` + 잠금 이전 열람(`viewedAt` < `AppConfig.diaryLockSinceMs`) → 로컬에 합침.
    ///  ② 잠금 이전 열람은 서버에도 `unlockedAt` 으로 굳혀 둔다(다시 열면 viewedAt 이 갱신돼 증거가 사라지므로).
    ///  ③ 서버 사본이 생기기 전의 로컬 기록은 이 기기에서 처음 동기화하는 계정으로 한 번만 올린다.
    func syncWithServer(uid: String) async {
        guard syncedUid != uid else { return }
        guard let server = await ViewedRepository.fetchOpened(uid: uid) else { return }
        var merged = unlockedAt
        for (id, at) in server.unlocked.merging(server.legacy, uniquingKeysWith: { a, _ in a }) {
            if let cur = merged[id], cur <= at { continue }
            merged[id] = at
        }
        if merged != unlockedAt {
            unlockedAt = merged
            save()
        }
        var upload = server.legacy
        let backfilled = UserDefaults.standard.string(forKey: Self.backfilledKey) != nil
        if !backfilled {
            for (id, at) in unlockedAt where server.unlocked[id] == nil { upload[id] = at }
        }
        await ViewedRepository.markUnlocked(uid: uid, unlocks: upload)
        if !backfilled { UserDefaults.standard.set(uid, forKey: Self.backfilledKey) }
        syncedUid = uid
    }
}
