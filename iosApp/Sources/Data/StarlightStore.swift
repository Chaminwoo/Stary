import FirebaseFirestore
import Foundation

/// 별빛 공방(2026-10-22) — 별가루 지갑 · 성운 상자 · 뽑기 풀. Android `feature/starlight/StarlightStore.kt` 패리티(같은 필드·규칙).
///
/// - 지갑 `wallets/{uid}` 는 **읽기만**(규칙상 쓰기 불가). 바꾸는 일은 전부 `request` 로 서버(functions/starlight.js)에 맡긴다:
///   `users/{uid}/starlightOps/{id}` 에 `{op, 인자, status:"pending"}` 을 만들면 서버가 같은 문서에 결과를 쓴다.
/// - 뽑기 풀 `starlightItems` · 설정 `starlightConfig/main` 도 DB 에서 읽는다 — 앱 업데이트 없이 늘리고 확률을 바꾼다.
///   설정 문서가 없으면 `Config.default`(서버 DEFAULT_CONFIG 와 같은 값).
/// - 모양·색 아이템은 `StarCatalog` 에 가르쳐 둔다(도감·업로드 휠에서 그릴 수 있게).
/// - 장착(프레임·배지·오라·칭호)은 서버가 소유를 확인하고 `starlightEquips/{uid}` 에 쓴다. 지도 오라는 그중 오라를 단 사람만 구독(`auraByUser`).
/// - 천체 망원경: 업적 ??? 공개는 `revealed`, 히든 업적 단서는 `hints`(서버가 문구까지 지갑에 넣어 준다).
@MainActor
final class StarlightStore: ObservableObject {
    static let shared = StarlightStore()
    private init() {}

    nonisolated static let rarities = ["common", "rare", "epic", "legendary"]
    /// 공방 칭호는 users.equippedTitle 에 "sl:{아이템 id}" 로 들어간다(업적 칭호 id 와 안 겹치게 — 서버 equip 이 쓴다).
    nonisolated static let titlePrefix = "sl:"
    private nonisolated static let wallets = "wallets"
    private nonisolated static let itemsCol = "starlightItems"
    private nonisolated static let configCol = "starlightConfig"
    private nonisolated static let opsCol = "starlightOps"
    private nonisolated static let equipsCol = "starlightEquips"
    private nonisolated static let opTimeout: Double = 25
    private nonisolated static let resubscribeDelay: UInt64 = 3_000_000_000

    struct Config: Equatable {
        var questStardust = 10
        var bonusStardust = 20
        var keyCost = 10
        var boxCost = 100
        var rarityWeights: [String: Double] = ["common": 60, "rare": 28, "epic": 10, "legendary": 2]
        var pityEpic = 10
        var pityLegendary = 50
        var dupRefund: [String: Int] = ["common": 10, "rare": 25, "epic": 50, "legendary": 100]
        var maxOpen = 10
        /// 단서가 준비된 히든 업적 id — 반사·전파 망원경 버튼은 여기 있는 업적에만.
        var hintTargets: Set<String> = []
        /// 공방 상자 카드에 겹쳐 보여 줄 대표 아이템 id(DB `showcase`) — 비면 전설 아이템 앞 3개.
        var showcase: [String] = []

        static let `default` = Config()

        /// 등급별 확률(%) — 확률 공개용.
        func percent(_ rarity: String) -> Double {
            let total = rarityWeights.values.reduce(0, +)
            return total <= 0 ? 0 : (rarityWeights[rarity] ?? 0) * 100 / total
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

    /// 장착 칸.
    enum Slot: String, CaseIterable {
        case frame, badge, aura, title
        var kind: String { rawValue }
        static func of(kind: String) -> Slot? { Slot(rawValue: kind) }
    }

    /// 한 사람의 장착 상태(아이템 id).
    struct Equips: Equatable {
        var frame: String?
        var badge: String?
        var aura: String?
        var title: String?

        func of(_ slot: Slot) -> String? {
            switch slot {
            case .frame: return frame
            case .badge: return badge
            case .aura: return aura
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

    /// 상자 하나를 연 결과.
    struct Draw {
        let itemId: String
        let rarity: String
        let kind: String
        let dup: Bool
        let refund: Int
        let keys: Int
    }

    /// code = 서버 오류 코드(insufficient · already · not_ready · no_boxes · empty_pool · invalid · server) 또는 timeout · offline.
    enum OpResult {
        case done([String: Any])
        case failed(String)
    }

    @Published private(set) var stardust = 0
    @Published private(set) var boxes = 0
    /// 가진 아이템 id → 개수(꾸미기는 1, 소모품은 남은 수).
    @Published private(set) var owned: [String: Int] = [:]
    @Published private(set) var sinceEpic = 0
    @Published private(set) var sinceLegendary = 0
    /// 서버 지갑을 한 번이라도 받았는지(받기 전엔 잔액을 "…"로).
    @Published private(set) var walletLoaded = false
    @Published private(set) var config = Config.default
    /// 뽑기 풀 전체(뽑히지 않게 내린 것 포함 — 이미 가진 사람이 계속 그려야 한다). 도감은 `shownItems`.
    @Published private(set) var items: [Item] = []
    /// 내 장착 상태.
    @Published private(set) var myEquips = Equips()
    /// 오라를 단 사람 → 오라 아이템 id(지도에서 그 사람의 별 둘레에 그린다).
    @Published private(set) var auraByUser: [String: String] = [:]
    /// 굴절 망원경으로 조건을 연 업적 id.
    @Published private(set) var revealed: Set<String> = []
    /// 반사·전파 망원경으로 얻은 히든 업적 단서.
    @Published private(set) var hints: [String: Hint] = [:]

    private var itemIndex: [String: Item] = [:]
    private var uid: String?
    private var regs: [ListenerRegistration] = []
    private var retryTask: Task<Void, Never>?

    func item(_ id: String?) -> Item? { id.flatMap { itemIndex[$0] } }

    /// 지금 구독 중인 사용자(= AuthManager.uid). 칭호 기기 사본 키(equipped_title_{uid})에 쓴다.
    var currentUid: String? { uid }

    /// "sl:{id}" 칭호 → 그 아이템(모르면 nil).
    func titleItem(_ titleId: String?) -> Item? {
        guard let t = titleId, t.hasPrefix(Self.titlePrefix) else { return nil }
        return item(String(t.dropFirst(Self.titlePrefix.count)))
    }

    /// 도감에 보일 것 — 지금 뽑히는 것 + 내렸어도 내가 가진 것.
    /// 공방 상자 카드의 대표 아이템 — DB showcase 순서, 없으면 지금 뽑히는 전설 앞 3개(Android `showcaseItems` 패리티).
    var showcaseItems: [Item] {
        let picked = config.showcase.compactMap { item($0) }
        return picked.isEmpty ? Array(items.filter { $0.active && $0.rarity == "legendary" }.prefix(3)) : picked
    }

    var shownItems: [Item] { items.filter { $0.active || (owned[$0.id] ?? 0) > 0 } }

    /// 가진 별 모양 번호(성운 상자) — 업로드 휠에서 내장 0~20 뒤에 붙인다.
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
        stardust = 0; boxes = 0; owned = [:]
        sinceEpic = 0; sinceLegendary = 0
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
                s.stardust = w.stardust; s.boxes = w.boxes; s.owned = w.owned
                s.sinceEpic = w.sinceEpic; s.sinceLegendary = w.sinceLegendary
                s.revealed = w.revealed; s.hints = w.hints
                if w.fromServer { s.walletLoaded = true }
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
        // 오라를 단 사람만(aura != null) — 지도에 그릴 사람 수만큼만 읽는다.
        regs.append(db.collection(Self.equipsCol).whereField("aura", isNotEqualTo: NSNull()).addSnapshotListener { snap, err in
            if err != nil { Task { @MainActor in StarlightStore.shared.retry(uid) }; return }
            guard let snap else { return }
            var map: [String: String] = [:]
            for d in snap.documents {
                if let a = d.get("aura") as? String, !a.isEmpty { map[d.documentID] = a }
            }
            Task { @MainActor in
                guard StarlightStore.shared.uid == uid else { return }
                if StarlightStore.shared.auraByUser != map { StarlightStore.shared.auraByUser = map }
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
        var sinceEpic = 0, sinceLegendary = 0
        var revealed: Set<String> = []
        var hints: [String: Hint] = [:]
        var fromServer = false
    }

    private nonisolated static func parseWallet(_ snap: DocumentSnapshot) -> Wallet {
        var w = Wallet()
        w.stardust = max(0, (snap.get("stardust") as? NSNumber)?.intValue ?? 0)
        w.boxes = max(0, (snap.get("boxes") as? NSNumber)?.intValue ?? 0)
        for (k, v) in (snap.get("items") as? [String: Any]) ?? [:] {
            if let n = (v as? NSNumber)?.intValue { w.owned[k] = n }
        }
        let pity = snap.get("pity") as? [String: Any]
        w.sinceEpic = (pity?["sinceEpic"] as? NSNumber)?.intValue ?? 0
        w.sinceLegendary = (pity?["sinceLegendary"] as? NSNumber)?.intValue ?? 0
        for (k, v) in (snap.get("revealed") as? [String: Any]) ?? [:] where (v as? Bool) == true { w.revealed.insert(k) }
        for (k, v) in (snap.get("hints") as? [String: Any]) ?? [:] {
            guard let m = v as? [String: Any], let t = m["text"] as? [String: Any] else { continue }
            var text: [String: String] = [:]
            for (l, s) in t { if let s = s as? String { text[l] = s } }
            w.hints[k] = Hint(level: (m["level"] as? NSNumber)?.intValue ?? 1, text: text)
        }
        w.fromServer = !snap.metadata.isFromCache
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
        for (k, v) in (snap.get("rarityWeights") as? [String: Any]) ?? [:] {
            if let n = (v as? NSNumber)?.doubleValue { c.rarityWeights[k] = n }
        }
        c.pityEpic = int("pityEpic") ?? c.pityEpic
        c.pityLegendary = int("pityLegendary") ?? c.pityLegendary
        for (k, v) in (snap.get("dupRefund") as? [String: Any]) ?? [:] {
            if let n = (v as? NSNumber)?.intValue { c.dupRefund[k] = n }
        }
        c.maxOpen = int("maxOpen") ?? c.maxOpen
        c.showcase = (snap.get("showcase") as? [Any])?.compactMap { $0 as? String } ?? []
        c.hintTargets = Set((snap.get("hintTargets") as? [Any])?.compactMap { $0 as? String } ?? [])
        return c
    }

    nonisolated static func parseEquips(_ snap: DocumentSnapshot) -> Equips {
        guard snap.exists else { return Equips() }
        func s(_ k: String) -> String? { (snap.get(k) as? String).flatMap { $0.isEmpty ? nil : $0 } }
        return Equips(frame: s("frame"), badge: s("badge"), aura: s("aura"), title: s("title"))
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

    /// openBox 결과 → Draw 목록.
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
