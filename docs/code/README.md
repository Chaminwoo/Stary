# docs/code — 코드 유지보수 문서 (Android + iOS)

> 목적: **처음 보는 사람도** 각 화면/기능의 변수·함수·컴포넌트 연결과
> "iOS 쪽에서는 어디서 어떻게 같은 값을 조절하는지"를 바로 찾을 수 있게 한다.
> 코드 히스토리/결정 배경은 [`../PROJECT_NOTES.md`](../PROJECT_NOTES.md), 작업 규칙은 루트 `CLAUDE.md` 참고.

## 읽는 법

- 문서는 **기능(화면) 단위**로 나뉜다. 한 문서 안에서 Android 파일 → iOS 대응 파일 순서.
- 변수 표기: `` `이름` : 설명 `` — state 변수/뷰모델/파라미터를 한 줄씩 설명한다.
- 각 문서 끝의 **「iOS 패리티 / 값 조절」** 절이 "Android 의 이 값 = iOS 의 저 값" 매핑표다.
  **한쪽 수치를 바꾸면 반드시 반대쪽도 같이 바꾼다(값 drift 금지 — CLAUDE.md §1.5).**
- iOS 는 Windows 에서 컴파일 불가 → 수정 후 push 하면 GitHub Actions `ios.yml`(macOS)가 검증한다.

## 문서 목록

| 문서 | 내용 |
|---|---|
| [01-app-structure-navigation.md](01-app-structure-navigation.md) | 앱 진입점, MainScreen(탑바/드로어), NavGraph/NavRoute, 전역 상태 브리지, iOS RootView/TabRouter |
| [02-design-system.md](02-design-system.md) | 색/폰트/테마, 별 모양·크리스탈 렌더(StarStyle), 공용 UI(토스트/배너/로딩/별탄생 등) |
| [03-map.md](03-map.md) | 지도 화면 전체 — MainListScreen(필터), DiaryMap(마커/애니/길찾기/파장), iOS MapScreen/MapLibreView |
| [04-globe.md](04-globe.md) | 3D 지구본(글로브) — 진입/복귀, 렌더러 |
| [05-upload.md](05-upload.md) | 업로드 화면 — 별 휠 피커, 사진 크롭, 부메랑 움짤 |
| [06-detail-cluster.md](06-detail-cluster.md) | 다이어리 상세, 겹친 별 카드 뷰어, 좋아요/댓글, 공유 카드 |
| [07-profile.md](07-profile.md) | 내/타인 프로필, 떠다니는 아이콘(FloatingStatBox), 핀 별, 내 다이어리 보드 |
| [08-achievements.md](08-achievements.md) | 일반/히든 업적, 칭호, 선점(개척) 로직 |
| [09-music-settings.md](09-music-settings.md) | 배경음악 화면/매니저, 설정 화면(볼륨·알림·언어·탈퇴) |
| [10-friends-chat.md](10-friends-chat.md) | 친구 목록/검색/요청, 채팅 |
| [11-notifications-push.md](11-notifications-push.md) | 알림 화면, 인앱 배너/팝업 감시자, FCM 푸시/딥링크 |
| [12-data-firebase.md](12-data-firebase.md) | Firestore 연결/리포지토리/뷰모델/캐시, shared(KMP) 모델·설정 |
| [13-auth-location-core.md](13-auth-location-core.md) | 로그인/세션, 위치(LocationHelper), 언어, 기타 core 유틸 |

## 빠른 "어디를 고치면 되나"

| 바꾸고 싶은 것 | Android | iOS |
|---|---|---|
| 별 모양/색 팔레트 | `core/designsystem/StarStyle.kt` | `Core/StarStyle.swift` + `Core/StarShape.swift` |
| 지도 마커 크기/부유/스파클 수치 | `feature/map/screen/DiaryMapMarkers.kt` 상수 | `Features/Map/MapLibreView.swift` · `MapStyleEffects.swift` |
| 열람 반경(100m)·기본 좌표·딥링크 스킴 | `shared/.../StaryConfig.kt` (공용) | `Core/AppConfig.swift` (**값 복제 — 함께 수정**) |
| 화면 배경 이미지/틴트 | 각 스크린의 `Image(painterResource(R.drawable...))` + 틴트 alpha | 각 스크린의 `ScreenBackground(name:darken:)` |
| 앱 공통 색 토큰 | `core/designsystem/Color.kt` | `Core/Theme.swift` |
| 폰트 | `core/designsystem/Type.kt` (MinSans 등) | `Core/AppFont.swift` (PoorStory) |
| 문자열(ko/en/ja) | `res/values*/strings.xml` | `Core/LocaleManager.swift` 의 `L10n` 키 |
| 지도 야경 스타일 JSON | `res/raw/maplibre_style.json` | iOS 번들에 같은 파일 복사됨(`MapLibreView.staryStyleURL` 이 로드) |

## 공통 아키텍처 요약

- **Android**: 단일 액티비티 + Compose. `MainScreen` 이 탑바/드로어/오버레이를 쥐고,
  화면 전환은 `NavGraph`(단일 NavHost). **지도는 NavHost 밖 상시 레이어**(2026-07-18 이후) —
  `NavRoute.Main` 은 빈 투명 화면이고 지도는 절대 재생성되지 않는다.
- **iOS**: SwiftUI. `RootView`(로그인 게이트) → `MainTabView`(지도 루트 + 단일 NavigationStack push).
  지도는 NavigationStack 루트라 원래 파괴되지 않는다.
- **화면 간 통신**: 전역 싱글턴 상태 브리지 패턴을 쓴다(각각 01 문서 참고).
  Android `MapFocusState`/`MapUiState`/`UserProfileActionState`/`ProfilePinState`/`DeepLinkState`
  ↔ iOS `MapFocusStore`/`MapChromeState`/`TabRouter`.
- **데이터**: Firestore(named DB `stary-db`). Android 는 repository 클래스,
  iOS 는 `FirestoreService` + `*Store`(ObservableObject) 계층(12 문서 참고).
