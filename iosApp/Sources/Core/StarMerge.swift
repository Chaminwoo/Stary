import CoreLocation
import Foundation

/// 30m 안에서 겹치는 별 합치기(지오 머지) — Android `DiaryMap.mergeByProximity` 패리티.
///
/// 우선순위 1위(좋아요 내림차순 → 오래된 순 → id)를 앵커로 [AppConfig.starMergeRadiusM] 안의
/// 별을 흡수한다(greedy). 대표의 모양/색으로 렌더하고, 크기/밝기는 멤버 전체를 합산해 키운다.
enum StarMerge {

    /// 머지 결과 한 덩어리 — 대표 별 + (우선순위 정렬된) 멤버 전체 + 크기 배율.
    struct MergedStar: Identifiable {
        let rep: Diary
        let members: [Diary]
        /// 좋아요 합산 × 개수 보너스 = 마커 크기/밝기 배율(1 ~ 약 6.6).
        let sizeMult: Double

        var id: String { rep.id ?? "" }
        var isCluster: Bool { members.count > 1 }
    }

    /// 좋아요 100개에서 3배(Android LIKES_FOR_MAX_SIZE / MAX_LIKE_SIZE_MULT).
    private static let likesForMaxSize = 100
    private static let maxLikeSizeMult = 3.0

    /// 대표 선정/카드 정렬 우선순위 — 좋아요 내림차순 → 오래된 순 → id(안정 타이브레이크).
    /// Android `MERGE_PRIORITY` 와 동일해야 양쪽의 대표 별이 일치한다.
    static func precedes(_ a: Diary, _ b: Diary) -> Bool {
        if a.likeCount != b.likeCount { return a.likeCount > b.likeCount }
        if a.createdAt != b.createdAt { return a.createdAt < b.createdAt }
        return (a.id ?? "") < (b.id ?? "")
    }

    /// 좌표가 유효한 별들을 30m 기준으로 합친다.
    static func merge(_ diaries: [Diary]) -> [MergedStar] {
        let valid = diaries.filter { $0.latitude != 0 || $0.longitude != 0 }
        guard !valid.isEmpty else { return [] }

        let sorted = valid.sorted(by: precedes)
        var taken = [Bool](repeating: false, count: sorted.count)
        var result: [MergedStar] = []

        for i in sorted.indices where !taken[i] {
            taken[i] = true
            let anchor = sorted[i]
            let anchorLoc = CLLocation(latitude: anchor.latitude, longitude: anchor.longitude)
            var members: [Diary] = [anchor]

            for j in (i + 1)..<sorted.count where !taken[j] {
                let d = sorted[j]
                let loc = CLLocation(latitude: d.latitude, longitude: d.longitude)
                if anchorLoc.distance(from: loc) <= AppConfig.starMergeRadiusM {
                    taken[j] = true
                    members.append(d) // sorted 순회라 members 도 우선순위 정렬 상태
                }
            }
            result.append(
                MergedStar(rep: anchor, members: members, sizeMult: sizeMult(of: members))
            )
        }
        return result
    }

    /// 합쳐진 별 크기 배율 — 멤버 전체 좋아요 "합산" × 개수 보너스(Android mergeSizeMult 패리티).
    static func sizeMult(of members: [Diary]) -> Double {
        likeSizeMult(members.reduce(0) { $0 + $1.likeCount }) * clusterSizeBoost(members.count)
    }

    /// 좋아요 수 → 크기 배율(1 ~ 3).
    private static func likeSizeMult(_ likeCount: Int) -> Double {
        let capped = Double(min(max(likeCount, 0), likesForMaxSize))
        return 1 + capped / Double(likesForMaxSize) * (maxLikeSizeMult - 1)
    }

    /// 합쳐진 개수 → 가산 배율(과도하지 않게 상한 2.2).
    private static func clusterSizeBoost(_ count: Int) -> Double {
        min(1 + Double(count - 1) * 0.12, 2.2)
    }
}
