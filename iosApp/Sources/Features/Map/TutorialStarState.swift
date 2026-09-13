import CoreLocation
import Foundation

/// 첫 실행 기기 전용 "웰컴 별" — Android `core/util/TutorialStarState.kt` 패리티.
///
/// 코치마크 마지막 단계가 안내하는, 서버에 쓰지 않는 클라이언트 전용 튜토리얼 다이어리 마커.
/// - [ensurePlaced] : 실제 위치 fix 가 오면(아직 [done] 이 아니면) 북·동으로 40m씩 떨어진 곳에 1회 고정·저장.
/// - 별을 탭하면 일반 별처럼 파장 → 상세로 가고, `MapScreen` 이 id 를 보고 [TutorialStarDetailScreen] 으로 분기한다.
///   그 화면이 열리는 순간 [markDone] 으로 영구 소비 — 이 기기에서 다시 뜨지 않는다.
///
/// 키 이름/상수는 Android 와 동일(Android 는 `stary_onboarding` prefs, iOS 는 코치마크 `main_coach_seen` 과 같은
/// UserDefaults.standard).
@MainActor
final class TutorialStarState: ObservableObject {
    static let shared = TutorialStarState()

    static let diaryId = "tutorial_star"
    // 지도 마커와 게시물 헤더가 같은 값을 쓴다 — 개척 퀘스트 비콘과 같은 "시스템 안내" 톤(앰버골드).
    static let starType = 3
    static let starColor = 15
    static let authorId = "stary_tutorial"
    static let authorName = "STARY"

    private static let keyDone = "tutorial_star_done"
    private static let keyLat = "tutorial_star_lat"
    private static let keyLng = "tutorial_star_lng"

    // ⚠️ 기본값을 꼭 준다 — init 에서 저장값으로 덮어쓰기 전에 모든 저장 프로퍼티가 초기화돼 있어야 한다.
    /// 튜토리얼이 이미 끝났는지. 코치마크 마지막 문구 분기에도 쓰인다.
    @Published private(set) var done: Bool = false
    /// 배치된 좌표(위도, 경도). nil 이면 아직 안 놨거나(위치 미확보) 이미 소비됨.
    @Published private(set) var placed: (lat: Double, lng: Double)? = nil
    /// 합성 다이어리의 createdAt — 접근할 때마다 "지금"을 새로 넣으면 지도 목록이 body 평가마다 달라져
    /// 마커 갱신이 반복되므로 좌표가 정해질 때 한 번 고정한다(Android `remember(latLng)` 과 같은 효과).
    private var createdAtMs = Int64(Date().timeIntervalSince1970 * 1000)

    private init() {
        let d = UserDefaults.standard
        done = d.bool(forKey: Self.keyDone)
        if !done, d.object(forKey: Self.keyLat) != nil, d.object(forKey: Self.keyLng) != nil {
            placed = (d.double(forKey: Self.keyLat), d.double(forKey: Self.keyLng))
        }
    }

    /// 실제 위치 fix 근방에 1회 배치. 이미 배치됐거나 끝났으면 아무것도 안 한다(Android ensurePlaced 와 같은 40m 오프셋).
    func ensurePlaced(near c: CLLocationCoordinate2D) {
        guard !done, placed == nil else { return }
        let metersToDegLat = 40.0 / 111_320.0
        let metersToDegLng = 40.0 / (111_320.0 * max(cos(c.latitude * .pi / 180), 0.01))
        let p = (lat: c.latitude + metersToDegLat, lng: c.longitude + metersToDegLng)
        createdAtMs = Int64(Date().timeIntervalSince1970 * 1000)
        placed = p
        UserDefaults.standard.set(p.lat, forKey: Self.keyLat)
        UserDefaults.standard.set(p.lng, forKey: Self.keyLng)
    }

    /// 튜토리얼 게시물이 열릴 때 — 영구 소비.
    func markDone() {
        done = true
        placed = nil
        let d = UserDefaults.standard
        d.set(true, forKey: Self.keyDone)
        d.removeObject(forKey: Self.keyLat)
        d.removeObject(forKey: Self.keyLng)
    }

    /// 지도에 덧붙일 합성 다이어리(좌표가 있을 때만). createdAt 은 지금 — 신규 별 오오라도 켜진다(Android 동일).
    /// 제목은 Android(빈 값)와 달리 채워 둔다 — iOS 는 겹친 별 카드 뷰어가 Diary 를 그대로 받아
    /// 웰컴 별이 카드로 뜰 수 있어서(Android 는 Firestore 에서 못 찾아 빠짐) 빈 제목 카드가 되지 않게.
    var diary: Diary? {
        guard let p = placed else { return nil }
        return Diary(
            id: Self.diaryId,
            userId: Self.authorId,
            userName: Self.authorName,
            title: LocaleManager.shared.t(.tutorialStarTitle),
            latitude: p.lat,
            longitude: p.lng,
            createdAt: createdAtMs,
            starType: Self.starType,
            starColor: Self.starColor,
            visibilityType: "public"
        )
    }
}
