import FirebaseFirestore
import Foundation

/// 다이어리 Firestore 접근. (KMP `DiaryRepository` 인터페이스의 iOS 구현격)
///
/// Android 와 동일한 제약을 따른다:
///  - 복합 인덱스 의존을 피하려 정렬은 클라이언트에서(`sorted`).
///  - private 다이어리는 클라이언트에서 필터.
@MainActor
final class DiaryRepository {

    private let col = FirestoreService.diaries
    private var listeners: [ListenerRegistration] = []

    deinit {
        listeners.forEach {
            $0.remove()
        }
    }

    /// 전체 공개 + 내 다이어리 구독.
    /// private 다이어리는 본인 것만 표시.
    func observeAll(
        currentUid: String?,
        onChange: @escaping ([Diary]) -> Void
    ) {

        print("")
        print("🚀 전체 다이어리 구독 시작")
        print("👤 현재 UID:", currentUid ?? "로그인 UID 없음")
        print("📂 Firestore 컬렉션:", col.path)

        // Android observeAllDiaries 와 동일: **최신순(createdAt DESC) 상한 1000** 구독.
        // ⚠️ 정렬 없는 limit 는 문서 ID 순 "임의의 N개"라 방금 만든 다이어리가 창 밖으로
        // 밀려날 수 있다(→ 지도에 새 별이 안 뜸). createdAt 없는 문서는 제외되지만 이는
        // Android 도 동일한 트레이드오프(모든 앱 저장분은 createdAt 을 쓴다).
        let reg = col
            .order(by: "createdAt", descending: true)
            .limit(to: 1000)
            .addSnapshotListener { snapshot, error in

                // Firestore 조회 오류 — Android 와 동일하게 기존 목록을 유지한 채 무시한다.
                // (빈 배열로 덮으면 일시 오류에도 지도의 별이 전부 사라진다)
                if let error {

                    print("")
                    print("❌ 전체 다이어리 Firestore 조회 실패")
                    print("⚠️ 오류:", error)
                    print("⚠️ 오류 내용:", error.localizedDescription)

                    return
                }

                // Snapshot 없음
                guard let snapshot else {

                    print("")
                    print("❌ Firestore snapshot이 nil입니다.")

                    onChange([])

                    return
                }

                print("")
                print("🔥 Firestore 원본 문서 개수:", snapshot.documents.count)

                var decodedDiaries: [Diary] = []

                // 문서별로 직접 디코딩
                for document in snapshot.documents {

                    do {

                        let diary = try document.data(
                            as: Diary.self
                        )

                        decodedDiaries.append(diary)

                        print("")
                        print("✅ Diary 변환 성공")
                        print("📄 ID:", document.documentID)
                        print("⭐ 제목:", diary.title)
                        print("👤 작성자:", diary.userName)
                        print("🕐 createdAt:", diary.createdAt)
                        print("🔒 공개 범위:", diary.visibilityType)

                    } catch {

                        print("")
                        print("❌ Diary 변환 실패")
                        print("📄 문서 ID:", document.documentID)

                        print(
                            "📦 Firestore 원본 데이터:",
                            document.data()
                        )

                        print(
                            "⚠️ 디코딩 오류:",
                            error
                        )
                    }
                }

                print("")
                print(
                    "⭐ Diary 변환 성공 개수:",
                    decodedDiaries.count
                )

                // private는 작성자 본인만 표시 + 최신순 클라이언트 정렬(서버 orderBy 제거 대응)
                let visible = decodedDiaries.filter { diary in

                    diary.visibilityType != "private"
                    || diary.userId == currentUid
                }
                .sorted { $0.createdAt > $1.createdAt }

                print(
                    "👁️ 공개 범위 필터 후:",
                    visible.count
                )

                onChange(visible)
            }

        listeners.append(reg)
    }

    /// 내 다이어리 구독.
    /// Firestore에서는 userId만 필터하고,
    /// 최신순 정렬은 iOS에서 수행.
    func observeMine(
        userId: String,
        onChange: @escaping ([Diary]) -> Void
    ) {

        print("")
        print("🚀 내 다이어리 구독 시작")
        print("👤 사용자 UID:", userId)
        print("📂 Firestore 컬렉션:", col.path)

        let reg = col
            .whereField(
                "userId",
                isEqualTo: userId
            )
            .addSnapshotListener { snapshot, error in

                // Firestore 오류
                if let error {

                    print("")
                    print("❌ 내 다이어리 Firestore 조회 실패")
                    print("⚠️ 오류:", error)
                    print("⚠️ 오류 내용:", error.localizedDescription)

                    onChange([])

                    return
                }

                guard let snapshot else {

                    print("")
                    print("❌ 내 다이어리 snapshot이 nil입니다.")

                    onChange([])

                    return
                }

                print("")
                print(
                    "🔥 내 다이어리 원본 문서:",
                    snapshot.documents.count
                )

                var decodedDiaries: [Diary] = []

                for document in snapshot.documents {

                    do {

                        let diary = try document.data(
                            as: Diary.self
                        )

                        decodedDiaries.append(diary)

                        print("")
                        print("✅ 내 Diary 변환 성공")
                        print("📄 ID:", document.documentID)
                        print("⭐ 제목:", diary.title)

                    } catch {

                        print("")
                        print("❌ 내 Diary 변환 실패")
                        print("📄 문서 ID:", document.documentID)

                        print(
                            "📦 원본 데이터:",
                            document.data()
                        )

                        print(
                            "⚠️ 디코딩 오류:",
                            error
                        )
                    }
                }

                let mine = decodedDiaries.sorted {
                    $0.createdAt > $1.createdAt
                }

                print("")
                print(
                    "⭐ 최종 내 다이어리 개수:",
                    mine.count
                )

                onChange(mine)
            }

        listeners.append(reg)
    }

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
           !id.isEmpty {

            try col
                .document(id)
                .setData(
                    from: diary,
                    merge: true
                )

            print("✅ Diary 수정 성공:", id)

            return id

        } else {

            diary.id = nil

            let reference =
                try col.addDocument(
                    from: diary
                )

            print(
                "✅ Diary 저장 성공:",
                reference.documentID
            )

            return reference.documentID
        }
    }

    /// 다이어리 삭제.
    func delete(
        _ diaryId: String
    ) async throws {

        try await col
            .document(diaryId)
            .delete()

        print(
            "🗑️ Diary 삭제 성공:",
            diaryId
        )
    }

    /// ID로 다이어리 조회.
    func diary(
        by id: String
    ) async throws -> Diary? {

        let document = try await col
            .document(id)
            .getDocument()

        guard document.exists else {

            print(
                "⚠️ Diary 문서 없음:",
                id
            )

            return nil
        }

        do {

            let diary = try document.data(
                as: Diary.self
            )

            print(
                "✅ Diary 단일 조회 성공:",
                id
            )

            return diary

        } catch {

            print("")
            print("❌ Diary 단일 조회 변환 실패")
            print("📄 문서 ID:", id)
            print("📦 데이터:", document.data() ?? [:])
            print("⚠️ 오류:", error)

            throw error
        }
    }

    /// 조회수 +1.
    /// 본인 제외 여부는 호출부에서 판단.
    func incrementViewCount(
        _ diaryId: String
    ) async {

        do {

            try await col
                .document(diaryId)
                .updateData([
                    "viewCount":
                        FieldValue.increment(
                            Int64(1)
                        )
                ])

            print(
                "👁️ 조회수 증가 성공:",
                diaryId
            )

        } catch {

            print("")
            print("❌ 조회수 증가 실패")
            print("📄 Diary ID:", diaryId)
            print("⚠️ 오류:", error)
        }
    }

    /// 모든 Firestore 실시간 구독 중지.
    func stopAll() {

        listeners.forEach {
            $0.remove()
        }

        listeners.removeAll()

        print(
            "🛑 Diary Firestore 구독 종료"
        )
    }
}
