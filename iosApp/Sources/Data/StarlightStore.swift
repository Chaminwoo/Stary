import FirebaseFirestore
import Foundation

/// 별가루(2026-10-26 개편, 옛 "별빛 공방") — 별가루 지갑 · 우주 항해 · 뽑기 풀. Android `feature/starlight/StarlightStore.kt` 패리티(같은 필드·규칙).
///
/// - 지갑 `wallets/{uid}` 는 **읽기만**(규칙상 쓰기 불가). 바꾸는 일은 전부 `request` 로 서버(functions/starlight.js)에 맡긴다:
///   `users/{uid}/starlightOps/{id}` 에 `{op, 인자, status:"pending"}` 을 만들면 서버가 같은 문서에 결과를 쓴다.
/// - 뽑기 풀 `starlightItems` · 설정 `starlightConfig/main` 도 DB 에서 읽는다 — 앱 업데이트 없이 늘리고 확률을 바꾼다.
///   설정 문서가 없으면 `Config.default`(서버 DEFAULT_CONFIG 와 같은 값).
/// - 우주 항해(`voyage`)는 경로(일반 · 희귀 탐사)마다 값과 확률이 다르다. **천장(확정 보정)은 없다** — 매 항해가 독립.
/// - 광고 보상(`adReward`)은 하루 한도·연속 간격을 서버가 센다(한국 시간 자정에 초기화).
/// - 모양·색 아이템은 `StarCatalog` 에 가르쳐 둔다(업로드 피커·업적 탐사 칸에서 그릴 수 있게).
/// - 장착(프레임·배지·칭호)은 서버가 소유를 확인하고 `starlightEquips/{uid}` 에 쓴다(`equip`).
///   별 고리는 장착이 아니라 **별마다** 고른다(업로드 → `diaries/{id}.ring`, 서버 checkDiaryRing 이 소유 확인).
/// - 천체 망원경: 업적 ??? 공개는 `revealed`, 히든 업적 단서는 `hints`(서버가 문구까지 지갑에 넣어 준다).
@MainActor
final class StarlightStore: ObservableObject {
    static let shared = StarlightStore()
    private init() {}

    nonisolated static let rarities = ["common", "rare", "epic", "legendary"]
    /// 항해 경로 id — 화면 순서. 설정에 다른 경로가 있어도 화면엔 이 둘만(새 경로는 앱 업데이트 때).
    nonisolated static let routes = ["normal", "rare"]
    /// 공방 칭호는 users.equippedTitle 에 "sl:{아이템 id}" 로 들어간다(업적 칭호 id 와 안 겹치게 — 서버 equip 이 쓴다).
    nonisolated static let titlePrefix = "sl:"
    private nonisolated static let wallets = "wallets"
    private nonisolated static let itemsCol = "starlightItems"
    private nonisolated static let configCol = "starlightConfig"
    private nonisolated static let opsCol = "starlightOps"
    private nonisolated static let equipsCol = "starlightEquips"
    private nonisolated static let opTimeout: Double = 25
    private nonisolated static let resubscribeDelay: UInt64 = 3_000_000_000

    /// 항해 경로 하나 — `cost` 한 번 떠나는 별가루, `weights` 등급 확률(합이 100이 아니어도 비율).
    struct Route: Equatable {
        var cost: Int
        var weights: [String: Double]

        /// 등급별 확률(%) — 확률 안내용.
        func percent(_ rarity: String) -> Double {
            let total = weights.values.reduce(0, +)
            return total <= 0 ? 0 : (weights[rarity] ?? 0) * 100 / total
        }
    }

    struct Config: Equatable {
        var questStardust = 10
        var bonusStardust = 20
        var keyCost = 10
        /// 옛 성운 상자 한 개 값 — 남은 상자를 별가루로 돌려줄 때(convertBoxes) 서버가 쓴다.
        var boxCost = 100
        var voyages: [String: Route] = [
            "normal": Route(cost: 50, weights: ["common": 66, "rare": 28, "epic": 5, "legendary": 1]),
            "rare": Route(cost: 300, weights: ["common": 12, "rare": 28, "epic": 50, "legendary": 10]),
        ]
        var dupRefund: [String: Int] = ["common": 10, "rare": 25, "epic": 50, "legendary": 100]
        var maxVoyage = 10
        var maxExchange = 99
        var adStardust = 10
        var adDailyCap = 10
        var adCooldownSec = 10
        /// 단서가 준비된 히든 업적 id — 반사·전파 망원경 버튼은 여기 있는 업적에만.
        var hintTargets: Set<String> = []

        static let `default` = Config()

        func route(_ id: String) -> Route {
            voyages[id] ?? Config.default.voyages[id] ?? Route(cost: 0, weights: [:])
        }
    }

    /// 뽑기 풀의 아이템 하나. `payload` 는 종류별(kind) 그림/효과 정의 — StarlightArt.swift 가 해석한다.
    struct Item: Identifiable, @unchecked Sendable {
        let id: String
        let kind: String
        let rarity: String
        let name: [String: String]
        let desc: [String: String]
        let payload: [String: Any]
        let order: Int
        var active = true

        func name(in lang: String) -> String { name[lang] ?? name["ko"] ?? id }
        func desc(in lang: String) -> String { desc[lang] ?? desc["ko"] ?? "" }
        func int(_ key: String) -> Int? { (payload[key] as? NSNumber)?.intValue }
        func num(_ key: String) -> Double? { (payload[key] as? NSNumber)?.doubleValue }
        func str(_ key: String) -> String? { payload[key] as? String }
        func bool(_ key: String) -> Bool { (payload[key] as? Bool) ?? false }
        /// payload.colors — "#RRGGBB" 목록(읽을 수 없는 값은 버린다).
        var hexColors: [String] {
            ((payload["colors"] as? [Any]) ?? []).compactMap { v in
                guard let s = v as? String else { return nil }
                let h = s.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "#", with: "")
                guard h.count == 6, UInt32(h, radix: 16) != nil else { return nil }
                return "#" + h.uppercased()
            }
        }
    }

    /// 장착 칸 — 별 고리는 별마다 고르므로 칸이 없다(서버 equip 은 옛 앱용 aura 칸을 아직 받는다).
    enum Slot: String, CaseIterable {
        case frame, badge, title
        var kind: String { rawValue }
        static func of(kind: String) -> Slot? { Slot(rawValue: kind) }
    }

    /// 한 사람의 장착 상태(아이템 id).
    struct Equips: Equatable {
        var frame: String?
        var badge: String?
        var title: String?

        func of(_ slot: Slot) -> String? {
            switch slot {
            case .frame: return frame
            case .badge: return badge
            case .title: return title
            }
        }
    }

    /// 히든 업적 단서 — level 1 흐릿 · 2 선명, text 언어별 문구.
    struct Hint: Equatable {
        let level: Int
        let text: [String: String]
        func text(in lang: String) -> String { text[lang] ?? text["ko"] ?? "" }
    }

    /// 항해 한 번에서 찾아온 것.
    struct Draw {
        let itemId: String
        let rarity: String
        let kind: String
        let dup: Bool
        let refund: Int
        let keys: Int
    }

    /// code = 서버 오류 코드(insufficient · invalid · empty_pool · ad_limit · ad_cooldown · already · not_ready · server)
    /// 또는 timeout · offline.
    enum OpResult {
        case done([String: Any])
        case failed(String)
    }

    /// 지갑 문서의 별가루(서버 값).
    @Published private var serverStardust = 0
    /// 받는 중인 보상을 더한 "적어도 이만큼"(퀘스트·광고 보상이 누르는 즉시 보이게, 2026-10-27) — 받기 요청이 모두 끝나면 0.
    /// 서버 값과 더하지 않고 큰 쪽을 보여 준다 — 지갑 갱신과 요청 완료 중 무엇이 먼저 와도 두 번 더해 보이지 않게.
    @Published private var creditFloor = 0
    private var crediting = 0
    var stardust: Int { max(serverStardust, creditFloor) }
    /// 옛 성운 상자(개편 전에 만들어 둔 것) — 별가루 화면을 열면 `convertBoxes` 로 별가루가 된다.
    @Published private(set) var boxes = 0
    /// 가진 아이템 id → 개수(꾸미기는 1, 소모품은 남은 수).
    @Published private(set) var owned: [String: Int] = [:]
    /// 오늘(한국 시간) 받은 광고 보상 횟수 · 마지막으로 받은 시각(ms).
    @Published private(set) var adsDay = ""
    @Published private(set) var adsN = 0
    @Published private(set) var adsLast: Int64 = 0
    /// 지갑을 한 번이라도 받았는지(받기 전엔 잔액을 "…"로). 기기 캐시 값도 받은 것으로 친다(2026-10-27) — 예전엔 서버 확정 값만
    /// 기다렸는데, 캐시와 서버 값이 같으면 다시 알림이 오지 않아 "…"가 계속 남았다. 문서가 없으면(아직 한 번도 받은 적 없음) 0.
    @Published private(set) var walletLoaded = false
    @Published private(set) var config = Config.default
    /// 뽑기 풀 전체(뽑히지 않게 내린 것 포함 — 이미 가진 사람이 계속 그려야 한다).
    @Published private(set) var items: [Item] = []
    /// 내 장착 상태.
    @Published private(set) var myEquips = Equips()
    /// 굴절 망원경으로 조건을 연 업적 id.
    @Published private(set) var revealed: Set<String> = []
    /// 반사·전파 망원경으로 얻은 히든 업적 단서.
    @Published private(set) var hints: [String: Hint] = [:]

    private var itemIndex: [String: Item] = [:]
    private var uid: String?
    private var regs: [ListenerRegistration] = []
    private var retryTask: Task<Void, Never>?

    func item(_ id: String?) -> Item? { id.flatMap { $0.isEmpty ? nil : itemIndex[$0] } }

    /// 지금 구독 중인 사용자(= AuthManager.uid). 칭호 기기 사본 키(equipped_title_{uid})에 쓴다.
    var currentUid: String? { uid }

    /// "sl:{id}" 칭호 → 그 아이템(모르면 nil).
    func titleItem(_ titleId: String?) -> Item? {
        guard let t = titleId, t.hasPrefix(Self.titlePrefix) else { return nil }
        return item(String(t.dropFirst(Self.titlePrefix.count)))
    }

    /// 오늘(한국 시간) 광고 보상을 더 받을 수 있는 횟수.
    var adsLeft: Int { max(0, config.adDailyCap - (adsDay == Self.kstDay() ? adsN : 0)) }

    /// 지금 뽑히는 아이템 중 rarity 인 것의 수 — 확률 안내("n종").
    func poolCount(_ rarity: String) -> Int { items.filter { $0.active && $0.rarity == rarity }.count }

    /// 가진 별 모양 번호(우주 항해) — 업로드 휠에서 내장 0~20 뒤에 붙인다.
    var ownedShapeTypes: [Int] {
        Array(Set(ownedOf("shape").compactMap { $0.int("type") })).sorted()
    }
    var ownedColorIndices: [Int] {
        Array(Set(ownedOf("color").compactMap { $0.int("index") })).sorted()
    }

    /// 가진 아이템 중 kind 인 것.
    func ownedOf(_ kind: String) -> [Item] { items.filter { $0.kind == kind && (owned[$0.id] ?? 0) > 0 } }

    /// 가진 망원경 중 mode(reveal / hint) 인 것, 효과가 약한 것부터(아껴 쓰게).
    func telescopes(_ mode: String) -> [Item] {
        ownedOf("telescope").filter { $0.str("mode") == mode }.sorted { ($0.int("level") ?? 0) < ($1.int("level") ?? 0) }
    }

    /// 서버 adReward 와 같은 "하루" — 한국 시간 날짜(yyyy-MM-dd).
    nonisolated static func kstDay(_ now: Date = Date()) -> String {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 9 * 3600) ?? .current
        let c = cal.dateComponents([.year, .month, .day], from: now)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    // MARK: - 구독

    /// 로그인 사용자로 구독(uid 가 바뀌면 다시, nil 이면 끊고 비운다). RootView 가 QuestStore 와 같은 자리에서 부른다.
    func start(uid: String?) {
        guard uid != self.uid || (uid != nil && regs.isEmpty) else { return }
        stop()
        guard let uid, !uid.isEmpty else { return }
        self.uid = uid
        subscribe(uid)
    }

    func stop() {
        regs.forEach { $0.remove() }
        regs.removeAll()
        retryTask?.cancel()
        retryTask = nil
        uid = nil
        serverStardust = 0; creditFloor = 0; crediting = 0; boxes = 0; owned = [:]
        adsDay = ""; adsN = 0; adsLast = 0
        walletLoaded = false
        myEquips = Equips()
        revealed = []
        hints = [:]
    }

    private func subscribe(_ uid: String) {
        let db = FirestoreService.db
        regs.append(db.collection(Self.wallets).document(uid).addSnapshotListener { snap, err in
            if err != nil { Task { @MainActor in StarlightStore.shared.retry(uid) }; return }
            guard let snap else { return }
            let w = Self.parseWallet(snap)
            Task { @MainActor in
                let s = StarlightStore.shared
                guard s.uid == uid else { return }
                s.serverStardust = w.stardust; s.boxes = w.boxes; s.owned = w.owned
                s.adsDay = w.adsDay; s.adsN = w.adsN; s.adsLast = w.adsLast
                s.revealed = w.revealed; s.hints = w.hints
                s.walletLoaded = true
            }
        })
        regs.append(db.collection(Self.itemsCol).addSnapshotListener { snap, err in
            if err != nil { Task { @MainActor in StarlightStore.shared.retry(uid) }; return }
            guard let snap else { return }
            let list = snap.documents.compactMap(Self.parseItem).sorted {
                let a = Self.rarities.firstIndex(of: $0.rarity) ?? 0, b = Self.rarities.firstIndex(of: $1.rarity) ?? 0
                return a != b ? a < b : $0.order < $1.order
            }
            list.forEach(Self.teach)
            Task { @MainActor in
                let s = StarlightStore.shared
                guard s.uid == uid else { return }
                s.items = list
                s.itemIndex = Dictionary(list.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            }
        })
        regs.append(db.collection(Self.equipsCol).document(uid).addSnapshotListener { snap, err in
            if err != nil { Task { @MainActor in StarlightStore.shared.retry(uid) }; return }
            guard let snap else { return }
            let e = Self.parseEquips(snap)
            Task { @MainActor in
                guard StarlightStore.shared.uid == uid else { return }
                StarlightStore.shared.myEquips = e
            }
        })
        regs.append(db.collection(Self.configCol).document("main").addSnapshotListener { snap, err in
            if err != nil { Task { @MainActor in StarlightStore.shared.retry(uid) }; return }
            guard let snap else { return }
            let c = Self.parseConfig(snap)
            Task { @MainActor in
                guard StarlightStore.shared.uid == uid else { return }
                StarlightStore.shared.config = c
            }
        })
    }

    /// 거부된 구독은 다시 살아나지 않는다(로그인 직후 인증이 붙는 순간 등) — 잠시 뒤 통째로 다시 붙인다.
    private func retry(_ uid: String) {
        guard self.uid == uid, retryTask == nil else { return }
        retryTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: Self.resubscribeDelay)
            let s = StarlightStore.shared
            s.retryTask = nil
            guard !Task.isCancelled, s.uid == uid else { return }
            s.regs.forEach { $0.remove() }
            s.regs.removeAll()
            s.subscribe(uid)
        }
    }

    // MARK: - 파싱

    private struct Wallet {
        var stardust = 0, boxes = 0
        var owned: [String: Int] = [:]
        var adsDay = "", adsN = 0
        var adsLast: Int64 = 0
        var revealed: Set<String> = []
        var hints: [String: Hint] = [:]
    }

    private nonisolated static func parseWallet(_ snap: DocumentSnapshot) -> Wallet {
        var w = Wallet()
        w.stardust = max(0, (snap.get("stardust") as? NSNumber)?.intValue ?? 0)
        w.boxes = max(0, (snap.get("boxes") as? NSNumber)?.intValue ?? 0)
        for (k, v) in (snap.get("items") as? [String: Any]) ?? [:] {
            if let n = (v as? NSNumber)?.intValue { w.owned[k] = n }
        }
        let ads = snap.get("ads") as? [String: Any]
        w.adsDay = (ads?["day"] as? String) ?? ""
        w.adsN = (ads?["n"] as? NSNumber)?.intValue ?? 0
        w.adsLast = (ads?["last"] as? NSNumber)?.int64Value ?? 0
        for (k, v) in (snap.get("revealed") as? [String: Any]) ?? [:] where (v as? Bool) == true { w.revealed.insert(k) }
        for (k, v) in (snap.get("hints") as? [String: Any]) ?? [:] {
            guard let m = v as? [String: Any], let t = m["text"] as? [String: Any] else { continue }
            var text: [String: String] = [:]
            for (l, s) in t { if let s = s as? String { text[l] = s } }
            w.hints[k] = Hint(level: (m["level"] as? NSNumber)?.intValue ?? 1, text: text)
        }
        return w
    }

    private nonisolated static func parseConfig(_ snap: DocumentSnapshot) -> Config {
        var c = Config.default
        guard snap.exists else { return c }
        func int(_ k: String) -> Int? { (snap.get(k) as? NSNumber)?.intValue }
        c.questStardust = int("questStardust") ?? c.questStardust
        c.bonusStardust = int("bonusStardust") ?? c.bonusStardust
        c.keyCost = int("keyCost") ?? c.keyCost
        c.boxCost = int("boxCost") ?? c.boxCost
        // 경로마다 기본값 위에 덮는다(서버 mergeVoyages 와 같은 규칙).
        for (id, v) in (snap.get("voyages") as? [String: Any]) ?? [:] {
            guard let m = v as? [String: Any] else { continue }
            var r = c.voyages[id] ?? Route(cost: 0, weights: [:])
            if let cost = (m["cost"] as? NSNumber)?.intValue { r.cost = cost }
            for (k, w) in (m["weights"] as? [String: Any]) ?? [:] {
                if let n = (w as? NSNumber)?.doubleValue { r.weights[k] = n }
            }
            c.voyages[id] = r
        }
        for (k, v) in (snap.get("dupRefund") as? [String: Any]) ?? [:] {
            if let n = (v as? NSNumber)?.intValue { c.dupRefund[k] = n }
        }
        c.maxVoyage = int("maxVoyage") ?? c.maxVoyage
        c.maxExchange = int("maxExchange") ?? c.maxExchange
        c.adStardust = int("adStardust") ?? c.adStardust
        c.adDailyCap = int("adDailyCap") ?? c.adDailyCap
        c.adCooldownSec = int("adCooldownSec") ?? c.adCooldownSec
        c.hintTargets = Set((snap.get("hintTargets") as? [Any])?.compactMap { $0 as? String } ?? [])
        return c
    }

    nonisolated static func parseEquips(_ snap: DocumentSnapshot) -> Equips {
        guard snap.exists else { return Equips() }
        func s(_ k: String) -> String? { (snap.get(k) as? String).flatMap { $0.isEmpty ? nil : $0 } }
        return Equips(frame: s("frame"), badge: s("badge"), title: s("title"))
    }

    private nonisolated static func parseItem(_ doc: QueryDocumentSnapshot) -> Item? {
        guard let kind = doc.get("kind") as? String,
              let rarity = doc.get("rarity") as? String, rarities.contains(rarity) else { return nil }
        func strMap(_ k: String) -> [String: String] {
            var m: [String: String] = [:]
            for (key, v) in (doc.get(k) as? [String: Any]) ?? [:] { if let s = v as? String { m[key] = s } }
            return m
        }
        return Item(
            id: doc.documentID, kind: kind, rarity: rarity, name: strMap("name"), desc: strMap("desc"),
            payload: (doc.get("payload") as? [String: Any]) ?? [:],
            order: (doc.get("order") as? NSNumber)?.intValue ?? 0,
            active: (doc.get("active") as? Bool) ?? true
        )
    }

    /// 모양·색 아이템의 정의를 StarCatalog 에 가르친다 — 아직 그 번호로 올린 별을 본 적 없어도 그릴 수 있게.
    private nonisolated static func teach(_ item: Item) {
        switch item.kind {
        case "shape":
            if let t = item.int("type"), let p = item.str("path") { StarCatalog.shared.learnShape(t, p) }
        case "color":
            if let i = item.int("index") {
                StarCatalog.shared.learnColor(i, ((item.payload["colors"] as? [Any]) ?? []).compactMap { $0 as? String })
            }
        default: break
        }
    }

    // MARK: - 서버 요청

    /// 다른 사람(또는 나)의 장착 상태 구독 — 프로필 화면용. 반환값으로 해제.
    nonisolated static func watchEquips(_ uid: String, _ onChange: @escaping @MainActor (Equips) -> Void) -> ListenerRegistration {
        FirestoreService.db.collection(equipsCol).document(uid).addSnapshotListener { snap, _ in
            guard let snap else { return }
            let e = parseEquips(snap)
            Task { @MainActor in onChange(e) }
        }
    }

    /// 장착/해제(서버가 소유 확인).
    func equip(_ slot: Slot, _ itemId: String?) async -> OpResult {
        var args: [String: Any] = ["slot": slot.rawValue]
        if let itemId { args["itemId"] = itemId }
        return await request("equip", args)
    }

    /// 우주 항해 — route 경로로 count 번(서버가 별가루를 빼고 결과를 정한다).
    func voyage(_ route: String, _ count: Int) async -> OpResult {
        await request("voyage", ["route": route, "count": count])
    }

    /// 열쇠 구매 — 별가루 `Config.keyCost` × count.
    func buyKeys(_ count: Int) async -> OpResult {
        await request("exchange", ["to": "key", "count": count])
    }

    /// 광고를 끝까지 본 뒤 — 별가루 `Config.adStardust`(하루 한도·간격은 서버가 센다). 받는 즉시 화면에 더한다.
    func adReward() async -> OpResult { await requestCredit("adReward", [:], config.adStardust) }

    /// 별가루를 받는 요청 — 서버가 처리하기 전에 amount 를 화면에 먼저 더해 두고(`creditFloor`), 끝나면(성공·실패 모두) 걷는다.
    /// 성공이면 그 사이 지갑 구독이 서버 값을 이미 가져왔고, 실패면 원래 값으로 돌아간다. Android `requestCredit` 패리티.
    func requestCredit(_ op: String, _ args: [String: Any], _ amount: Int) async -> OpResult {
        creditFloor = stardust + max(0, amount)
        crediting += 1
        let r = await request(op, args)
        // 성공 결과의 잔액은 서버 확정 값 — 지갑 알림보다 요청 완료가 먼저 와도 바로 맞춘다.
        if case .done(let result) = r, let s = (result["stardust"] as? NSNumber)?.intValue, s > serverStardust { serverStardust = s }
        crediting -= 1
        if crediting <= 0 { crediting = 0; creditFloor = 0 }
        return r
    }

    /// 남은 옛 성운 상자를 별가루로(상자 값 그대로). 상자가 없으면 서버가 "already".
    func convertBoxes() async -> OpResult { await request("convertBoxes") }

    /// 천체 망원경 사용 — target = 업적 id(굴절: ??? 업적 / 반사·전파: 히든 업적).
    func useTelescope(_ itemId: String, target: String) async -> OpResult {
        await request("useItem", ["itemId": itemId, "target": target])
    }

    /// 서버에 요청을 보내고 결과를 기다린다(최대 25초). 시간이 넘으면 .failed("timeout") —
    /// 요청은 이미 남아 있어 망이 돌아오면 서버가 처리하고, 지갑 구독으로 결과가 반영된다.
    func request(_ op: String, _ args: [String: Any] = [:]) async -> OpResult {
        guard let uid else { return .failed("offline") }
        let ref = FirestoreService.users.document(uid).collection(Self.opsCol).document()
        var body = args
        body["op"] = op
        body["status"] = "pending"
        body["createdAt"] = Int64(Date().timeIntervalSince1970 * 1000)
        ref.setData(body) { err in
            if let err { print("[Starlight] 요청 기록 실패 \(op): \(err)") }
        }
        return await withCheckedContinuation { (cont: CheckedContinuation<OpResult, Never>) in
            let waiter = OpWaiter(cont)
            waiter.attach(ref.addSnapshotListener { snap, err in
                if err != nil { waiter.finish(.failed("server")); return }
                switch snap?.get("status") as? String {
                case "done": waiter.finish(.done((snap?.get("result") as? [String: Any]) ?? [:]))
                case "error": waiter.finish(.failed((snap?.get("error") as? String) ?? "server"))
                default: break
                }
            })
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.opTimeout) { waiter.finish(.failed("timeout")) }
        }
    }

    /// voyage 결과 → Draw 목록.
    nonisolated static func draws(_ result: [String: Any]) -> [Draw] {
        ((result["results"] as? [Any]) ?? []).compactMap { r in
            guard let m = r as? [String: Any], let id = m["id"] as? String else { return nil }
            return Draw(
                itemId: id,
                rarity: (m["rarity"] as? String) ?? "common",
                kind: (m["kind"] as? String) ?? "",
                dup: (m["dup"] as? Bool) ?? false,
                refund: (m["refund"] as? NSNumber)?.intValue ?? 0,
                keys: (m["keys"] as? NSNumber)?.intValue ?? 0
            )
        }
    }
}

/// 요청 하나의 결과 기다리기 — 리스너·시간 초과 중 먼저 온 쪽으로 한 번만 끝낸다.
private final class OpWaiter: @unchecked Sendable {
    private let lock = NSLock()
    private var cont: CheckedContinuation<StarlightStore.OpResult, Never>?
    private var reg: ListenerRegistration?
    private var finished = false

    init(_ cont: CheckedContinuation<StarlightStore.OpResult, Never>) { self.cont = cont }

    func attach(_ r: ListenerRegistration) {
        lock.lock()
        if finished { lock.unlock(); r.remove(); return }
        reg = r
        lock.unlock()
    }

    func finish(_ result: StarlightStore.OpResult) {
        lock.lock()
        guard !finished else { lock.unlock(); return }
        finished = true
        let c = cont, r = reg
        cont = nil; reg = nil
        lock.unlock()
        r?.remove()
        c?.resume(returning: result)
    }
}
