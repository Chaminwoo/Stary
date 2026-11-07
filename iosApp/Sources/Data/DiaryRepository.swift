import FirebaseFirestore
import Foundation

/// 다이어리 Firestore 접근. (KMP `DiaryRepository` 인터페이스의 iOS 구현격)
///
/// 지도 구독은 **세 리스너의 합집합**(id 로 중복 제거, createdAt 내림차순)이다:
///  1. 공개 글   : `visibilityType == "public"` 최신 1000
///  2. 내 글     : `userId == 나`
///  3. 친구 공개 : `visibilityType == "friends"` 이고 `audience` 에 내가 있는 글 최신 300
/// 공개 쿼리가 인덱스 없음(FAILED_PRECONDITION)으로 실패하면 옛 전체 쿼리로 폴백하고, 친구 쿼리가 실패하면 빈 목록으로 둔다.
/// 재시도는 지수 백오프(3초 → 최대 60초). 권한 거부는 몇 번만 다시 해 보고 멈춘다(인증이 바뀌면 호출부가 재구독).
@MainActor
final class DiaryRepository {

    private let col = FirestoreService.diaries

    private enum Kind: Hashable { case publicAll, mine, friends }

    private var regs: [Kind: ListenerRegistration] = [:]
    private var results: [Kind: [Diary]] = [:]
    private var attempts: [Kind: Int] = [:]
    private var retryTasks: [Kind: Task<Void, Never>] = [:]
    private var usePublicFallback = false
    private var publicLoaded = false
    private var generation = 0
    private var viewerUid: String?
    private var onChange: (([Diary]) -> Void)?

    private static let publicLimit = 1000
    private static let friendsLimit = 300
    private static let maxDeniedRetries = 3
    private static let firestoreErrorDomain = "FIRFirestoreErrorDomain"
    private static let codePermissionDenied = 7
    private static let codeFailedPrecondition = 9

    deinit {
        // deinit 은 비격리 — 등록 해제만 한다(원소 접근은 값 복사본으로).
        let held = regs.values
        held.forEach { $0.remove() }
    }

    /// 지도에 올릴 글 구독 시작. `currentUid` 가 없으면 아무것도 구독하지 않는다(로그인 전 구독 금지).
    func observeAll(
        currentUid: String?,
        onChange: @escaping ([Diary]) -> Void
    ) {
        stopAll()
        guard let uid = currentUid, !uid.isEmpty else { return }
        viewerUid = uid
        self.onChange = onChange
        generation += 1
        let gen = generation
        subscribe(.publicAll, uid: uid, gen: gen)
        subscribe(.mine, uid: uid, gen: gen)
        subscribe(.friends, uid: uid, gen: gen)
    }

    // MARK: - 구독

    private func query(_ kind: Kind, uid: String) -> Query {
        switch kind {
        case .publicAll:
            if usePublicFallback {
                return col.order(by: "createdAt", descending: true).limit(to: Self.publicLimit)
            }
            return col.whereField("visibilityType", isEqualTo: "public")
                .order(by: "createdAt", descending: true)
                .limit(to: Self.publicLimit)
        case .mine:
            return col.whereField("userId", isEqualTo: uid)
        case .friends:
            return col.whereField("visibilityType", isEqualTo: "friends")
                .whereField("audience", arrayContains: uid)
                .order(by: "createdAt", descending: true)
                .limit(to: Self.friendsLimit)
        }
    }

    private func subscribe(_ kind: Kind, uid: String, gen: Int) {
        regs[kind]?.remove()
        regs[kind] = query(kind, uid: uid).addSnapshotListener { [weak self] snapshot, error in
            // 디코딩은 리스너 스레드에서, 상태 갱신은 메인 액터에서.
            let list: [Diary]? = snapshot.map { Self.decode($0) }
            let ns = error as NSError?
            let domain = ns?.domain
            let code = ns?.code
            Task { @MainActor in
                self?.handle(kind, gen: gen, list: list, failed: error != nil, domain: domain, code: code)
            }
        }
    }

    private nonisolated static func decode(_ snapshot: QuerySnapshot) -> [Diary] {
        var out: [Diary] = []
        out.reserveCapacity(snapshot.documents.count)
        for document in snapshot.documents {
            // 디코딩 실패·비정상 값(좌표 범위 밖, 먼 미래 시각 등)인 문서는 건너뛴다.
            if let diary = try? document.data(as: Diary.self), let clean = diary.sanitized() {
                out.append(clean)
            }
        }
        return out
    }

    private func handle(_ kind: Kind, gen: Int, list: [Diary]?, failed: Bool, domain: String?, code: Int?) {
        guard gen == generation, let uid = viewerUid else { return }

        if failed {
            let isFirestore = domain == Self.firestoreErrorDomain
            if kind == .publicAll, isFirestore, code == Self.codeFailedPrecondition, !usePublicFallback {
                // 인덱스가 아직 없다 — 옛 전체 쿼리로 폴백.
                usePublicFallback = true
                subscribe(.publicAll, uid: uid, gen: gen)
                return
            }
            regs[kind]?.remove()
            regs[kind] = nil
            if kind == .friends { results[.friends] = [] }
            if kind == .publicAll { publicLoaded = true }
            emit()
            scheduleRetry(kind, uid: uid, gen: gen,
                          denied: isFirestore && code == Self.codePermissionDenied,
                          noIndex: isFirestore && code == Self.codeFailedPrecondition)
            return
        }

        guard let list else { return }
        attempts[kind] = 0
        results[kind] = list
        if kind == .publicAll { publicLoaded = true }
        emit()
    }

    private func scheduleRetry(_ kind: Kind, uid: String, gen: Int, denied: Bool, noIndex: Bool) {
        if noIndex { return } // 인덱스가 생길 때까지 다시 해 봐야 같은 결과 — 다음 구독(앱 재시작/계정 변경)에 맡긴다.
        let n = attempts[kind] ?? 0
        if denied && n >= Self.maxDeniedRetries { return }
        attempts[kind] = n + 1
        let delay = min(60.0, 3.0 * pow(2.0, Double(min(n, 5))))
        retryTasks[kind]?.cancel()
        retryTasks[kind] = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard !Task.isCancelled, let self, self.generation == gen, self.regs[kind] == nil else { return }
            self.subscribe(kind, uid: uid, gen: gen)
        }
    }

    /// 세 결과를 합쳐 화면에 알린다. 공개 목록이 처음 도착(또는 실패 확정)하기 전에는 알리지 않는다(첫 로드 표시 유지).
    private func emit() {
        guard publicLoaded, let onChange else { return }
        let me = viewerUid
        var byId: [String: Diary] = [:]
        for kind in [Kind.publicAll, .friends, .mine] {
            for d in results[kind] ?? [] {
                guard let id = d.id, !id.isEmpty else { continue }
                // 나만 보기는 작성자 본인 것만.
                if d.visibilityType == "private" && d.userId != me { continue }
                byId[id] = d
            }
        }
        onChange(byId.values.sorted { $0.createdAt > $1.createdAt })
    }

    // MARK: - 저장 / 삭제 / 단건

    /// 저장 후 문서 ID 반환.
    @discardableResult
    func save(
        _ diary: Diary
    ) async throws -> String {

        var diary = diary

        if diary.createdAt == 0 {

            diary.createdAt =
                FirestoreService.nowMillis
        }

        if let id = diary.id,
           !id.isEmpty,
           !id.contains("/") {

            try col
                .safeDocument(id)
                .setData(
                    from: diary,
                    merge: true
                )

            return id

        } else {

            diary.id = nil

            let reference =
                try col.addDocument(
                    from: diary
                )

            return reference.documentID
        }
    }

    /// 다이어리 삭제.
    func delete(
        _ diaryId: String
    ) async throws {

        guard Self.isValidDocId(diaryId) else { return }

        try await col
            .safeDocument(diaryId)
            .delete()
    }

    /// ID로 다이어리 조회.
    func diary(
        by id: String
    ) async throws -> Diary? {

        guard Self.isValidDocId(id) else { return nil }

        let document = try await col
            .safeDocument(id)
            .getDocument()

        guard document.exists else {
            return nil
        }

        return try document.data(
            as: Diary.self
        ).sanitized()
    }

    /// 조회수 +1.
    /// 본인 제외 여부는 호출부에서 판단.
    func incrementViewCount(
        _ diaryId: String
    ) async {

        guard Self.isValidDocId(diaryId) else { return }

        do {

            try await col
                .safeDocument(diaryId)
                .updateData([
                    "viewCount":
                        FieldValue.increment(
                            Int64(1)
                        )
                ])

        } catch {

            print("❌ 조회수 증가 실패:", diaryId, error.localizedDescription)
        }
    }

    /// Firestore `document(_:)` 는 빈 문자열·슬래시가 든 경로에서 크래시한다 — 외부에서 온 id 는 반드시 거른다.
    nonisolated static func isValidDocId(_ id: String) -> Bool { FirestoreService.isValidId(id) }

    /// 모든 Firestore 실시간 구독 중지.
    func stopAll() {
        generation += 1
        regs.values.forEach { $0.remove() }
        regs.removeAll()
        retryTasks.values.forEach { $0.cancel() }
        retryTasks.removeAll()
        results.removeAll()
        attempts.removeAll()
        usePublicFallback = false
        publicLoaded = false
        viewerUid = nil
        onChange = nil
    }
}
