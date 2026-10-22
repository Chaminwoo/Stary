import FirebaseFirestore
import Foundation

/// 일일 퀘스트 이벤트 — Android `feature/quest/DailyQuests.kt` `QuestEvent` 와 같은 목록.
enum QuestEvent: String {
    case tutorialStar, upload, uploadMedia, viewOther, like, comment, equipTitle, profileEdit, pinStar
    case constellation, filter, globe, meteor, visitAchievements, visitStarlog, visitProfile
    case friendRequest, share, music, focus, unlockKey
}

/// 퀘스트 하나. `hours` = 로컬 "시"(양끝 포함, 24 이상이면 자정을 넘김 — 21...25 = 오후 9:00~새벽 1:59).
struct QuestDef: Equatable {
    let id: String
    let event: QuestEvent
    var target: Int = 1
    let title: L10n
    let hint: L10n
    var hours: ClosedRange<Int>? = nil
    var group: String? = nil

    func matchesHour(_ hour: Int) -> Bool {
        guard let h = hours else { return true }
        let shifted = (hour < h.lowerBound && h.upperBound >= 24) ? hour + 24 : hour
        return h.contains(shifted)
    }
}

/// 일일 퀘스트 정의(2026-10-19) — 하루 3개, 앱 기능을 하나씩 써 보게 하는 튜토리얼 성격. 보상 = 별가루(2026-10-22, 서버 지급).
/// ⚠️ 하루 개수·목표치는 서버 functions/starlight.js QUESTS_PER_DAY / QUEST_TARGETS 와 같아야 한다(서버가 진행도를 확인한다).
/// ⚠️ Android `DailyQuests` 와 **id·이벤트·목표·시간대·풀·뽑는 식(FNV-1a + SplitMix64 + Fisher–Yates)이 같아야 한다** —
///    같은 계정을 두 기기에서 쓰면 서버의 그날 목록을 같이 쓴다.
enum DailyQuests {
    static let perDay = 3
    private static let gUpload = "upload"

    static let tutorial = QuestDef(id: "tutorial_star", event: .tutorialStar, title: .questTutorialStar, hint: .questTutorialStarHint)
    static let upload = QuestDef(id: "upload", event: .upload, title: .questUpload, hint: .questUploadHint, group: gUpload)
    static let viewOther = QuestDef(id: "view_other", event: .viewOther, title: .questViewOther, hint: .questViewOtherHint)
    static let like = QuestDef(id: "like", event: .like, title: .questLike, hint: .questLikeHint)
    static let profileEdit = QuestDef(id: "profile_edit", event: .profileEdit, title: .questProfileEdit, hint: .questProfileEditHint)

    /// 2~4일째 — 기본 기능(Android BASIC 과 같은 순서).
    static let basic: [QuestDef] = [
        upload,
        like,
        QuestDef(id: "view_other3", event: .viewOther, target: 3, title: .questViewOther3, hint: .questViewOtherHint),
        QuestDef(id: "comment", event: .comment, title: .questComment, hint: .questCommentHint),
        QuestDef(id: "equip_title", event: .equipTitle, title: .questEquipTitle, hint: .questEquipTitleHint),
        profileEdit,
        QuestDef(id: "pin_star", event: .pinStar, title: .questPinStar, hint: .questPinStarHint),
        QuestDef(id: "constellation", event: .constellation, title: .questConstellation, hint: .questConstellationHint),
        QuestDef(id: "filter", event: .filter, title: .questFilter, hint: .questFilterHint),
        QuestDef(id: "globe", event: .globe, title: .questGlobe, hint: .questGlobeHint),
        QuestDef(id: "achievements", event: .visitAchievements, title: .questAchievements, hint: .questAchievementsHint),
        QuestDef(id: "user_profile", event: .visitProfile, title: .questUserProfile, hint: .questUserProfileHint),
    ]

    /// 5일째부터 기본 풀에 더해진다(Android ADVANCED 와 같은 순서).
    static let advanced: [QuestDef] = [
        QuestDef(id: "like3", event: .like, target: 3, title: .questLike3, hint: .questLikeHint),
        QuestDef(id: "upload_morning", event: .upload, title: .questUploadMorning, hint: .questUploadMorningHint, hours: 5...10, group: gUpload),
        QuestDef(id: "upload_night", event: .upload, title: .questUploadNight, hint: .questUploadNightHint, hours: 21...25, group: gUpload),
        QuestDef(id: "upload_media", event: .uploadMedia, title: .questUploadMedia, hint: .questUploadMediaHint, group: gUpload),
        QuestDef(id: "meteor", event: .meteor, title: .questMeteor, hint: .questMeteorHint),
        QuestDef(id: "friend_request", event: .friendRequest, title: .questFriendRequest, hint: .questFriendRequestHint),
        QuestDef(id: "share", event: .share, title: .questShare, hint: .questShareHint),
        QuestDef(id: "music", event: .music, title: .questMusic, hint: .questMusicHint),
        QuestDef(id: "star_log", event: .visitStarlog, title: .questStarLog, hint: .questStarLogHint),
        QuestDef(id: "focus", event: .focus, title: .questFocus, hint: .questFocusHint),
        QuestDef(id: "use_key", event: .unlockKey, title: .questUseKey, hint: .questUseKeyHint),
    ]

    private static let all: [String: QuestDef] = {
        var m: [String: QuestDef] = [:]
        for q in [tutorial, viewOther] + basic + advanced where m[q.id] == nil { m[q.id] = q }
        return m
    }()

    static func byId(_ id: String) -> QuestDef? { all[id] }

    /// 그날의 퀘스트 3개 — Android `DailyQuests.pick` 과 같은 규칙·같은 결과.
    /// 1일째는 이름·프로필 사진 바꾸기 · 별 올리기 · 남의 별 열어 보기(첫 사용 안내 순서 — 2026-10-22, 예전 첫 칸은 웰컴 별).
    static func pick(uid: String, day: String, dayNo: Int, previous: [String]) -> [String] {
        if dayNo <= 1 {
            return [profileEdit.id, upload.id, viewOther.id]
        }
        var pool = dayNo <= 4 ? basic : basic + advanced
        var rng = SplitMix64(state: UInt64(fnv1a("\(uid)|\(day)|\(dayNo)")))
        var i = pool.count - 1
        while i >= 1 {
            let j = rng.nextInt(i + 1)
            pool.swapAt(i, j)
            i -= 1
        }
        var picked: [QuestDef] = []
        for allowPrevious in [false, true] {
            for q in pool {
                if picked.count >= perDay { break }
                if picked.contains(q) { continue }
                if !allowPrevious && previous.contains(q.id) { continue }
                if let g = q.group, picked.contains(where: { $0.group == g }) { continue }
                picked.append(q)
            }
        }
        return picked.map(\.id)
    }

    /// FNV-1a 32bit(UTF-8) — Android `DailyQuests.fnv1a` 와 같은 값.
    static func fnv1a(_ s: String) -> UInt32 {
        var h: UInt32 = 0x811C9DC5
        for b in s.utf8 {
            h ^= UInt32(b)
            h = h &* 16777619
        }
        return h
    }

    struct SplitMix64 {
        var state: UInt64
        mutating func next() -> UInt64 {
            state = state &+ 0x9E3779B97F4A7C15
            var z = state
            z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
            z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
            return z ^ (z >> 31)
        }
        /// 0..<n — `(next >> 1) % n`(Android `(next() ushr 1) % n` 과 같다).
        mutating func nextInt(_ n: Int) -> Int { Int((next() >> 1) % UInt64(n)) }
    }
}

/// `users/{uid}` 필드 이름(Android `QuestStore.FIELD_KEYS/FIELD_QUEST` 와 같음).
private let questFieldKeys = "questKeys"
private let questFieldQuest = "quest"

/// 일일 퀘스트 진행 + 열쇠 지갑(2026-10-19) — Android `feature/quest/QuestStore.kt` 패리티(같은 필드·규칙).
///
/// 저장 위치 = `users/{uid}` 두 필드(MeteorStats 와 같은 방식 — 규칙 변경 없이 소유자 쓰기, 항상 merge):
///  - `questKeys` : 가진 열쇠 수(`FieldValue.increment` 로만 바꾼다)
///  - `quest`     : `{ day: "yyyy-MM-dd"(기기 현지 날짜), dayNo, ids[3], progress{id: n}, claimed[], bonus }`
/// ⚠️ `userName`/`profileImageUrl` 은 건드리지 않는다(CLAUDE.md §2.6).
/// 새 날 목록은 **서버 스냅샷을 본 뒤에만**, 그리고 이전 가입 계정 여부(`AuthManager.returningAccount`)가 정해진 뒤에 만든다.
@MainActor
final class QuestStore: ObservableObject {
    static let shared = QuestStore()
    private init() {}

    struct Daily: Equatable {
        var day: String
        var dayNo: Int
        var ids: [String]
        var progress: [String: Int]
        var claimed: Set<String>
        var bonus: Bool

        func progressOf(_ id: String) -> Int { progress[id] ?? 0 }
        func isComplete(_ q: QuestDef) -> Bool { progressOf(q.id) >= q.target }
        var quests: [QuestDef] { ids.compactMap { DailyQuests.byId($0) } }
        /// 완료했지만 아직 보상을 안 받은 수 — 지도 "!" 버튼 배지.
        var claimable: Int { quests.filter { isComplete($0) && !claimed.contains($0.id) }.count + (bonusReady && !bonus ? 1 : 0) }
        /// 보너스 퀘스트(4번째 행)를 받을 수 있는가 — 3개 보상을 모두 받았다.
        var bonusReady: Bool { !ids.isEmpty && ids.allSatisfy { claimed.contains($0) } }
        /// 오늘 것을 보너스까지 전부 받았다.
        var allClaimed: Bool { bonusReady && bonus }
    }


    /// 가진 열쇠 수. 비로그인이면 0.
    @Published private(set) var keys = 0
    /// 오늘 퀘스트. nil = 아직 모름(로딩/비로그인).
    @Published private(set) var daily: Daily?

    private var uid: String?
    private var listener: ListenerRegistration?
    private var serverSeen = false
    private var returning: Bool?
    /// 서버 확인 뒤에도 이전 계정 여부를 끝내 모르면(프로필 읽기 실패) "이전 계정"으로 보고 목록을 만든다(Android 동일).
    private var returningFallback = false
    private var fallbackTask: Task<Void, Never>?
    private var countedKeys = Set<String>()
    private var countedDay: String?

    /// 로그인해서 열쇠를 쓸 수 있는 상태인지(비로그인은 광고로만 연다).
    var available: Bool { uid != nil }

    static func today() -> String {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }

    /// 로그인 사용자 문서를 구독한다(uid 가 바뀌면 다시, nil 이면 끊고 비운다). RootView 가 MeteorStats 와 같은 자리에서 부른다.
    func start(uid: String?) {
        guard uid != self.uid else { return }
        listener?.remove()
        listener = nil
        self.uid = uid
        serverSeen = false
        returningFallback = false
        fallbackTask?.cancel()
        fallbackTask = nil
        keys = 0
        daily = nil
        guideDone = false
        guideKeyGranted = false
        guideLoaded = false
        guideOpenShown = false
        guard let uid, !uid.isEmpty else { self.uid = nil; return }
        // includeMetadataChanges — 캐시 값이 서버와 같으면 "서버 확인됨"은 메타데이터만 바뀐 이벤트로 온다.
        // 이걸 안 받으면 serverSeen 이 영영 false 라 오늘 목록을 못 만들어 퀘스트 창이 무한 로딩이었다(2026-10-19).
        listener = FirestoreService.users.document(uid).addSnapshotListener(includeMetadataChanges: true) { snap, error in
            guard error == nil, let snap else { return }
            // 값만 꺼내 메인 액터로 넘긴다(스냅샷 객체는 넘기지 않는다).
            let parsed = Self.parse(snap)
            let fromServer = !snap.metadata.isFromCache
            Task { @MainActor in
                let store = QuestStore.shared
                guard store.uid == uid else { return }
                if fromServer && !store.serverSeen {
                    store.serverSeen = true
                    store.fallbackTask = Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 8_000_000_000)
                        guard !Task.isCancelled, QuestStore.shared.uid == uid else { return }
                        QuestStore.shared.returningFallback = true
                        QuestStore.shared.ensureToday()
                    }
                }
                store.keys = parsed.keys
                store.daily = parsed.daily
                // 이 기기가 막 true 로 바꾼 값을 뒤늦은 캐시 스냅샷이 되돌리지 않게 — 한 번 true 면 유지.
                store.guideDone = store.guideDone || parsed.guideDone
                store.guideKeyGranted = store.guideKeyGranted || parsed.guideKey
                if fromServer { store.guideLoaded = true }
                store.ensureToday()
            }
        }
    }

    /// 이전 가입 계정 여부 — 정해지면 1일째 목록을 만들 수 있다(RootView `.onChange(of: auth.returningAccount)`).
    func setReturning(_ value: Bool?) {
        returning = value
        if value != nil { ensureToday() }
    }

    private nonisolated static func parse(_ snap: DocumentSnapshot) -> (keys: Int, daily: Daily?, guideDone: Bool, guideKey: Bool) {
        let keys = max(0, (snap.get(questFieldKeys) as? NSNumber)?.intValue ?? 0)
        let guideDone = (snap.get(fieldGuideDone) as? Bool) ?? false
        let guideKey = (snap.get(fieldGuideKey) as? Bool) ?? false
        guard let m = snap.get(questFieldQuest) as? [String: Any], let day = m["day"] as? String else {
            return (keys, nil, guideDone, guideKey)
        }
        var progress: [String: Int] = [:]
        for (k, v) in (m["progress"] as? [String: Any]) ?? [:] {
            if let n = (v as? NSNumber)?.intValue { progress[k] = n }
        }
        let daily = Daily(
            day: day,
            dayNo: (m["dayNo"] as? NSNumber)?.intValue ?? 1,
            ids: (m["ids"] as? [String]) ?? [],
            progress: progress,
            claimed: Set((m["claimed"] as? [String]) ?? []),
            bonus: (m["bonus"] as? Bool) ?? false
        )
        return (keys, daily, guideDone, guideKey)
    }

    /// 날짜가 바뀌었으면 새 목록을 만든다(서버 확인 후). 자정을 넘겨 켜 둔 경우를 위해 RootView 가 1분마다도 부른다.
    func ensureToday() {
        guard let uid, serverSeen, returning != nil || returningFallback else { return }
        let today = Self.today()
        if let cur = daily, cur.day == today { return }
        let dayNo = (daily?.dayNo ?? 0) + 1
        let ids = DailyQuests.pick(uid: uid, day: today, dayNo: dayNo, previous: daily?.ids ?? [])
        daily = Daily(day: today, dayNo: dayNo, ids: ids, progress: [:], claimed: [], bonus: false)
        // mergeFields = quest 필드만 통째로 교체(어제 progress/claimed 가 섞이지 않게).
        FirestoreService.users.document(uid).setData([
            questFieldQuest: [
                "day": today, "dayNo": dayNo, "ids": ids,
                "progress": [String: Int](), "claimed": [String](), "bonus": false,
            ] as [String: Any],
        ], mergeFields: [questFieldQuest])
    }

    /// "이걸 했다" — 오늘 목록에 그 이벤트가 있으면 진행도 +1(목표 이상은 안 셈).
    /// `key` 를 주면 같은 날 같은 key 는 한 번만 센다(예: 다이어리 id). 어느 스레드에서 불러도 된다.
    nonisolated static func report(_ event: QuestEvent, key: String? = nil) {
        Task { @MainActor in shared.reportNow(event, key: key) }
    }

    private func reportNow(_ event: QuestEvent, key: String?) {
        // 첫 사용 안내의 "프로필 바꾸기" 단계는 오늘 목록과 상관없이 끝난다.
        if event == .profileEdit, let uid { FirstStarGuideProgress.shared.markProfileDone(uid) }
        guard let uid, var d = daily else { return }
        if d.day != Self.today() { ensureToday(); return }
        if countedDay != d.day { countedDay = d.day; countedKeys.removeAll() }
        if let key, !countedKeys.insert("\(event.rawValue):\(key)").inserted { return }
        let hour = Calendar.current.component(.hour, from: Date())
        var updates: [String: Int] = [:]
        var finished: QuestDef?
        for q in d.quests where q.event == event && q.matchesHour(hour) {
            let cur = d.progressOf(q.id)
            if cur >= q.target { continue }
            updates[q.id] = cur + 1
            if cur + 1 >= q.target, finished == nil { finished = q }
        }
        guard !updates.isEmpty else { return }
        for (k, v) in updates { d.progress[k] = v }
        daily = d
        FirestoreService.users.document(uid).setData([questFieldQuest: ["progress": updates]], merge: true)
        if let q = finished {
            Haptics.medium()
            let l = LocaleManager.shared
            GlobalToast.shared.show(String(format: l.t(.questDoneToast), l.t(q.title)))
        }
    }

    /// 완료한 퀘스트의 보상(별가루)을 받는다. 보너스는 따로 [claimBonus].
    /// 지급은 서버가 한다(`StarlightStore.request("claimQuest")` — 진행도 확인 · 하루 1번 · `quest.claimed` 기록까지).
    /// 버튼이 바로 "받음"으로 바뀌게 먼저 화면에 반영하고, 서버가 거절하면 되돌린다.
    /// 반환 = 받은 별가루(성공), 0(이미 받음/받을 수 없음), nil(실패 — 안내 토스트는 호출부). Android `claim` 패리티.
    func claim(_ id: String) async -> Int? {
        guard var d = daily, let q = d.quests.first(where: { $0.id == id }),
              d.isComplete(q), !d.claimed.contains(id) else { return 0 }
        let day = d.day
        d.claimed.insert(id)
        daily = d
        let r = await StarlightStore.shared.request("claimQuest", ["day": day, "questId": id])
        return settle(r) {
            if var cur = daily, cur.day == day { cur.claimed.remove(id); daily = cur }
        }
    }

    /// 보너스 퀘스트(4번째 행) — 3개 보상을 모두 받았으면 별가루 보너스. 서버가 `questClearDays` 에도 그날을 남긴다
    /// (퀘스트 보상 칭호 — 날짜 집합이라 두 기기가 같은 날 받아도 한 번만 센다). Android `claimBonus` 패리티.
    func claimBonus() async -> Int? {
        guard var d = daily, d.bonusReady, !d.bonus else { return 0 }
        let day = d.day
        d.bonus = true
        daily = d
        let r = await StarlightStore.shared.request("claimBonus", ["day": day])
        return settle(r) {
            if var cur = daily, cur.day == day { cur.bonus = false; daily = cur }
        }
    }

    /// 서버 응답 → 받은 별가루. 응답이 늦으면(timeout) 요청이 남아 있어 나중에 처리되므로 화면은 그대로 둔다.
    private func settle(_ r: StarlightStore.OpResult, revert: () -> Void) -> Int? {
        switch r {
        case .done(let result): return (result["gained"] as? NSNumber)?.intValue ?? 0
        case .failed(let code):
            switch code {
            case "already": return 0
            case "timeout": return nil
            default: revert(); return nil
            }
        }
    }

    /// 광고 보상 — 열쇠 1개(퀘스트 창 열쇠 칩 → "광고를 보고 열쇠를 얻으시겠어요?").
    func grantAdKey() {
        guard let uid else { return }
        keys += 1
        FirestoreService.users.document(uid).setData([questFieldKeys: FieldValue.increment(Int64(1))], merge: true)
    }

    /// 열쇠 1개를 쓴다(별 해금). 없으면 false.
    func spendKey() -> Bool {
        guard let uid, keys > 0 else { return false }
        keys -= 1
        let firstUse = !guideDone
        guideDone = true
        var data: [String: Any] = [questFieldKeys: FieldValue.increment(Int64(-1))]
        // 열쇠를 처음 쓰면 첫 별 안내(FirstStarGuide)도 끝난다.
        if firstUse { data[Self.fieldGuideDone] = true }
        FirestoreService.users.document(uid).setData(data, merge: true)
        StaryAnalytics.log("key_used", ["first": firstUse])
        if firstUse && guideOpenShown {
            StaryAnalytics.log("guide_done", ["via": "key"])
            GlobalToast.shared.show(LocaleManager.shared.t(.guideDone))
        }
        return true
    }

    // MARK: - 첫 별 안내(FirstStarGuide) 상태 — users/{uid} 필드, 이 구독으로 함께 읽는다(Android QuestStore 패리티)

    nonisolated static let fieldGuideDone = "guideDone"
    nonisolated static let fieldGuideKey = "guideKeyGranted"

    /// 안내를 끝냈는지(열쇠를 한 번이라도 썼거나 열어 볼 별이 없음). 기기를 바꿔도 다시 뜨지 않게 서버에 둔다.
    @Published private(set) var guideDone = false
    /// 안내용 열쇠를 이미 받았는지 — 퀘스트에 "별 올리기"가 없는 날 올린 경우의 대체 보상(1회).
    @Published private(set) var guideKeyGranted = false
    /// 서버 값을 한 번이라도 받았는지 — 받기 전엔 안내를 띄우지 않는다(이미 끝낸 계정에 잠깐 뜨지 않게).
    @Published private(set) var guideLoaded = false
    /// "열쇠로 별 열기" 단계 카드를 이번 실행에서 보여 줬는지 — 그때만 열쇠 사용을 "안내 완료"로 축하한다.
    var guideOpenShown = false

    /// 안내를 끝낸다.
    func markGuideDone(via: String) {
        guard let uid, !guideDone else { return }
        guideDone = true
        StaryAnalytics.log("guide_done", ["via": via])
        FirestoreService.users.document(uid).setData([Self.fieldGuideDone: true], merge: true)
    }

    /// 첫 별을 올렸는데 오늘 퀘스트에 "올리기"가 없어 받을 열쇠가 없을 때 — 안내용 열쇠 1개(계정당 1회).
    func grantGuideKey() -> Bool {
        guard let uid, !guideKeyGranted else { return false }
        guideKeyGranted = true
        keys += 1
        FirestoreService.users.document(uid).setData([
            questFieldKeys: FieldValue.increment(Int64(1)),
            Self.fieldGuideKey: true,
        ], merge: true)
        return true
    }
}
