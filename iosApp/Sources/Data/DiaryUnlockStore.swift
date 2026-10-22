import FirebaseAuth
import Foundation
import SwiftUI

/// 해금한(열어 본) 다른 사람의 게시물(다이어리) 기록 — **영구, 계정별**.
///
/// 100m 밖 게시물은 제목만 보이고 미디어/본문/댓글이 잠긴다(DetailScreen). 잠금은
/// ① 100m 이내로 접근 ② 보상형 광고 시청 — 둘 중 하나로 풀리고, **한 번 풀린 게시물은 그 계정에서 계속 열려 있다**
/// (2026-09-22 "다이어리도 아예 해금 형식" 피드백 — 예전 7일 TTL 폐지, 접근 해금도 기록).
/// ⚠️ 해금은 **열람**만 연다. 댓글 작성은 해금 여부와 무관하게 항상 100m 이내에서만 가능하다.
///
/// Android `core.util.DiaryUnlockStore` 패리티 — diaryId → 해금 시각(ms, 기록용 — 존재 = 해금).
/// `ObservableObject` 라 [unlock] 시 관찰 중인 화면이 즉시 갱신된다.
/// 기기 로컬(UserDefaults)이 1차 저장소이고, **서버 사본**(`users/{uid}/viewedDiaries/{id}.unlockedAt`)에도 남긴다
/// (2026-09-26 별 도감 "열람한 모든 다이어리" 요청). `syncWithServer` 가 앱 시작 시 둘을 합친다 —
/// 기기를 바꿔도 복원되고, 잠금이 생기기 전(2026-09-21)에 열람한 글도 "연 것"으로 들어온다.
/// ⚠️ 로컬 저장소는 **계정(앱 uid)마다 따로**(`ad_unlock_store_u_{uid}`, 둘러보기는 `_guest`) — 2026-10-22 수정.
///    예전엔 기기에 하나라 다른 계정으로 로그인해도 별 도감·해금이 그대로 보였다. 로그인 상태가 바뀌면 그 계정 저장소로 갈아탄다.
///    서버 사본도 **앱 uid**(`AuthManager.appUserId` = 구글 sub) 아래 — 예전 `unlock` 은 FirebaseAuth uid 로 써서 구글 계정은 다시 읽히지 않았다.
@MainActor
final class DiaryUnlockStore: ObservableObject {
    static let shared = DiaryUnlockStore()

    /// 계정 구분 전의 기기 공용 저장소 키(7일 TTL 시절 `AdUnlockStore` 이름) — [migrateLegacy] 가 한 번 정리한다.
    private static let legacyKey = "ad_unlock_store"
    /// 예전 공용 저장소를 서버로 처음 올린 계정 uid.
    private static let backfilledKey = "ad_unlock_store_backfilled_uid"
    private static let guest = "guest"

    /// diaryId → 해금 시각(ms) — 지금 로그인한 계정 것.
    @Published private(set) var unlockedAt: [String: Int64] = [:]

    /// 지금 [unlockedAt] 에 올라와 있는 저장소의 계정.
    private var loadedKey: String?
    private var authHandle: AuthStateDidChangeListenerHandle?

    private init() {
        load()
        authHandle = Auth.auth().addStateDidChangeListener { [weak self] _, _ in
            Task { @MainActor in self?.load() }
        }
    }

    private static func accountKey() -> String {
        AuthManager.appUserId(of: Auth.auth().currentUser) ?? guest
    }

    private static func storageKey(_ account: String) -> String {
        account == guest ? "\(legacyKey)_guest" : "\(legacyKey)_u_\(account)"
    }

    /// 지금 계정의 저장소를 올린다(계정이 그대로면 아무 일도 안 한다).
    private func load() {
        let key = Self.accountKey()
        guard loadedKey != key else { return }
        if key != Self.guest { Self.migrateLegacy(to: key) }
        loadedKey = key
        let raw = UserDefaults.standard.dictionary(forKey: Self.storageKey(key)) as? [String: NSNumber] ?? [:]
        unlockedAt = raw.mapValues { $0.int64Value }
    }

    /// 계정 구분 전의 공용 저장소 정리(1회). Android `migrateLegacy` 패리티.
    ///  - 이미 서버로 올린 적이 있으면 각 계정 해금은 서버 사본에 있으니 버린다(여러 계정 기록이 섞여 있다).
    ///  - 올린 적이 없으면 이 기기에서 처음 로그인한 계정의 것으로 넘긴다(`syncWithServer` 가 서버에도 올린다).
    private static func migrateLegacy(to uid: String) {
        let defaults = UserDefaults.standard
        guard let raw = defaults.dictionary(forKey: legacyKey), !raw.isEmpty else { return }
        if defaults.string(forKey: backfilledKey) == nil {
            var mine = defaults.dictionary(forKey: storageKey(uid)) ?? [:]
            for (k, v) in raw { mine[k] = v }
            defaults.set(mine, forKey: storageKey(uid))
        }
        defaults.removeObject(forKey: legacyKey)
        defaults.removeObject(forKey: backfilledKey)
    }

    /// 이 게시물이 해금돼 있는지.
    func isUnlocked(_ diaryId: String) -> Bool {
        unlockedAt[diaryId] != nil
    }

    /// 광고 시청 완료 또는 100m 이내 접근 → 이 게시물을 영구 해금한다(이미 해금이면 무시). 서버 사본도 남긴다.
    func unlock(_ diaryId: String) {
        load()
        guard unlockedAt[diaryId] == nil else { return }
        let now = FirestoreService.nowMillis
        unlockedAt[diaryId] = now
        save()
        if let uid = AuthManager.appUserId(of: Auth.auth().currentUser) {
            Task { await ViewedRepository.markUnlocked(uid: uid, unlocks: [diaryId: now]) }
        }
    }

    private func save() {
        guard let key = loadedKey else { return }
        UserDefaults.standard.set(
            unlockedAt.mapValues { NSNumber(value: $0) },
            forKey: Self.storageKey(key)
        )
    }

    /// 이번 실행에서 동기화를 마친 uid — 화면마다 불러도 1회만.
    private var syncedUid: String?

    /// 이 계정의 로컬 ↔ 서버 해금 기록을 합친다(실행당 uid 1회, 실패하면 다음 호출 때 재시도). Android `syncWithServer` 패리티.
    ///  ① 서버 `unlockedAt` + 잠금 이전 열람(`viewedAt` < `AppConfig.diaryLockSinceMs`) → 로컬에 합침.
    ///  ② 잠금 이전 열람은 서버에도 `unlockedAt` 으로 굳혀 둔다(다시 열면 viewedAt 이 갱신돼 증거가 사라지므로).
    ///  ③ 로컬에만 있는 기록(서버 쓰기 실패·옛 공용 저장소에서 넘어온 것)은 서버에 올린다 — 저장소가 계정별이라 늘 이 계정 것이다.
    func syncWithServer(uid: String) async {
        guard syncedUid != uid, Self.accountKey() == uid else { return }
        load()
        guard let server = await ViewedRepository.fetchOpened(uid: uid) else { return }
        // 2026-10-22 이전 iOS `unlock` 이 FirebaseAuth uid 아래에 잘못 쓴 해금(구글 계정) — 앱 uid 로 옮겨 온다.
        var misplaced: [String: Int64] = [:]
        if let authUid = Auth.auth().currentUser?.uid, authUid != uid,
           let old = await ViewedRepository.fetchOpened(uid: authUid) {
            misplaced = old.unlocked
        }
        // 서버를 읽는 사이 로그아웃/계정 전환 — 다른 계정 저장소에 섞지 않는다.
        guard Self.accountKey() == uid else { return }
        load()
        if !misplaced.isEmpty {
            unlockedAt.merge(misplaced) { cur, _ in cur }
            save()
        }
        var upload = server.legacy
        for (id, at) in unlockedAt where server.unlocked[id] == nil { upload[id] = at }
        var merged = unlockedAt
        for (id, at) in server.unlocked.merging(server.legacy, uniquingKeysWith: { a, _ in a }) {
            if let cur = merged[id], cur <= at { continue }
            merged[id] = at
        }
        if merged != unlockedAt {
            unlockedAt = merged
            save()
        }
        await ViewedRepository.markUnlocked(uid: uid, unlocks: upload)
        syncedUid = uid
    }
}
