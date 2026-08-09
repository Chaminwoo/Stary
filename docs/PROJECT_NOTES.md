# PROJECT_NOTES.md — Stary-Project 코드 분석 / 작업 핸드오프

> 목적: **다음 작업 시 코드를 처음부터 다시 읽지 않고** 바로 시작할 수 있도록 구조·연동·결정사항을 정리.
> 업데이트 규칙: 빌드+테스트 성공 때마다 갱신(자세한 건 `CLAUDE.md` 참고).
> 최종 갱신: **8.45 크로스플랫폼 버그 7건**(카메라/갤러리/3초영상 3지선다 · 휠 스냅 · 업로드 키보드 ·
> **채팅 규칙(첫 메시지 거부) 근본 수정** · 채팅 입력바 인셋 · **iOS 푸시(FCM/APNs) 신설** · 친구 요청 알림)
> **+ 8.45-2 iOS 폰트를 Android 와 동일하게(PoorStory → MinSans 가변폰트)**
> — Android BUILD SUCCESSFUL(2026-08-09), iOS 는 push 후 CI 검증 + **사용자 액션 3건 필요**(아래 8.45).
> 이전: **8.44 iOS 패리티 라운드3(버그 7건 + 미완 6건)** — **계정 통일(uid=Google sub)**/**@DocumentID id=nil 버그(7모델)**/구독 최신순 1000/시뮬레이터 서울 고정/달·행성 boolean 모양/친구 요청됨+토스트/후광 2겹 별색/타인 프로필 핀 별만/틸트 25°/마지막 카메라 복원/채팅 별가루 삭제 — Android BUILD SUCCESSFUL, **iOS CI(macOS) BUILD SUCCESS `003b2b3`**(2026-07-16) — 아래 8.44 참고.
> 이전: **8.43 iOS 패리티 라운드2 (R2-1~R2-3 + 마무리 5건)** — anonymous 디코딩/줌 별크기/후광·파티클/100m 게이팅/미디어/열람 애니메이션/몰입·별자리 버튼/친구 db + 프로필 아이콘 크리스탈화/음악 별자리 보드 스타일/글로브 지구 복원/미번역 전수 + Android 업로드 휠 피커 — **CI(macOS) BUILD SUCCESS `f6428d2`**(2026-07-16) — 아래 8.43 참고.
> 이전: **8.42 iOS UI 전면 패리티(1~7차 완료)** — 드로어 내비/테마/야경 지도/지도 크롬/상세 재구성/필터 확장/별자리 보드/L10n — **CI(macOS) BUILD SUCCESS `9cd867b`**(2026-07-15) — 아래 8.42 참고.
> 이전: **8.41 다른 컴퓨터 iOS 작업 합류(로그인 화면 전면 개편 + 커스텀 폰트) + 번들 ID `com.chaminwoo.stary.ios` 확정** — 아래 8.41 참고.
> 이전: **8.40 마감 라운드(1.3.1) + 전환 잔상 삭제 + iOS 패리티 일괄(33·34 라운드)** — Android BUILD SUCCESSFUL(2026-07-14), iOS 는 push 후 CI 검증 — 아래 8.40 참고.
> 이전: **8.38-iOS 패리티**(크리스탈 별 / 30m 머지·겹친별 카드 / 친구 메신저형 행 / 이미지 캐시) — **CI(macOS) BUILD SUCCESS `a173f7e`** — 아래 8.38-iOS 참고.
> 이전: **8.38 크리스탈 별 렌더·스파클(궤도·개수·위성)·공유카드 편집 확장·겹친별 배경·이미지 고속화·친구 스크린 개편** — Android **테스트 완료·push(`fbf7470`)** — 아래 8.38 참고.
> 이전: **8.37 제한·30m 별 합치기·공유카드 편집·인스타 링크·마커 스파클 5건 라운드** — Android BUILD SUCCESSFUL(2026-07-12) — 아래 8.37 참고.
> 이전: **8.36-iOS CI 그린 복구 + BGM 설명 문구 정정** — SettingsScreen 컴파일 에러 수정 + 부메랑 문구 패리티 + BGM 설명(의도적 삭제분) 양쪽 완전 제거. **CI(macOS) BUILD SUCCESS `0367fcb`**, Android `compileDebugKotlin` **BUILD SUCCESSFUL** — 아래 8.36-iOS 참고.
> 이전: **8.36 Seedance 2.0 광고 마스터 기획 + 광고 자산 정리**(기획안 4종 + 씬별 i2v 프롬프트, 코드 변경 없음 — 커밋 `db12725`) — 아래 8.36 참고.
> 이전: **8.35 글로브 유성·은하수 3D 연출 전면 개편 + 레퍼런스 재작업**(곡선 유성+잔류 스파클 / 은하수.jpg 스타일 핑크 은하수 / zodiac.avif 별 단위 12궁 — Android BUILD SUCCESSFUL, 테스트 대기) — 아래 8.35 참고.
> 이전: **8.34 4건 라운드 — 음악·칭호 다국어화 / 부메랑 3초 움짤 촬영 / 기간별 필터 / 프사·이름 현재값 표시**(테스트 완료·push, 후속 수정 포함) — 아래 8.34 참고.
> 이전: **8.33 지도 야경 스타일 전면 개편 + 글로브 오로라 삭제→은하수 격상/유성 추가**(테스트 완료) — 아래 8.33 참고. Android **BUILD SUCCESSFUL**(라운드별 확인), iOS 컴파일 CI 대기.
> 이전: **8.32 히든 업적 후속 3건 — 심연의 별 아이콘 교체 + 어드민 선점 해제(자가치유) + 친구 프로필 히든 아이콘**(테스트 완료) — 아래 8.32 참고. Android **BUILD SUCCESSFUL**, iOS 컴파일 CI 대기.
> 이전: **8.31 로그아웃 버튼 zIndex + 히든 칭호 금색『』 + 하루 업로드 10개 제한 + 어드민 히든선점 제외**(체크리스트 19~22) — 아래 8.31 참고.
> 이전: **8.30 채팅 FCM 알림(백그라운드/종료) + 딥링크**(heads-up 채널 사전생성, 알림 탭→해당 채팅방, singleTop+onNewIntent+DeepLinkState, 서버 data에 friendId/name) — 아래 8.30 참고. Android **BUILD SUCCESSFUL**, 실기기+Functions 배포 검증 대기. Android 전용(iOS APNs 후속).
> 이전: **8.29 히든 업적(앱 전체 1명 선착순) + 프로필 아이콘·파티클**(업적 화면 일반/히든 2탭, 조건 `???`→달성 시 공개+달성자, 트랜잭션 선점, 안드+iOS) — 아래 8.29 참고.
> 이전: **8.28 닉네임 변경 + 닉네임 친구 검색(공통친구 정렬)**(프로필 이름 탭→변경, 기본=구글 닉네임; 검색 결과 2명↑이면 나와 공통 친구 많은 순 정렬, 안드+iOS) — 아래 8.28 참고.
> 이전: **8.27 화면 첫 진입 설명창**(내 다이어리·프로필·업적·배경음악·친구 5개 화면에 1회 안내 다이얼로그, 안드+iOS) — 아래 8.27 참고.
> 이전: **8.26-iOS 길찾기 진입 + 프로필 부유아이콘 패리티 + 핀별 파동·길찾기** — 아래 8.26-iOS 참고. iOS 컴파일 = **CI(macOS) BUILD SUCCESS `e787ce8`**.
> 이전: **8.25 체크리스트 TODO 3건**(인앱 배너 반복 dedup, 미조회 아이콘 FiberNew, 설정 음량 슬라이더 별 thumb) BUILD SUCCESSFUL — 아래 8.25 참고.
> 이전: **8.24 안드로이드 언어 리소스화 마무리**(DiaryMap FAB/토스트·UserProfileScreen 하드코딩 → strings.xml ko/en/ja, BUILD SUCCESSFUL) — 아래 8.24 참고.
> 이전: **8.23-iOS 미조회 필터 + 조회 기록**(ViewedStore/markViewed + Map·List "미조회만", CI(macOS) BUILD SUCCESS e89904a) — 아래 8.23-iOS 참고.
> 이전: **8.22-iOS 패리티**(위 5개 항목 SwiftUI 구현, CI(macOS) BUILD SUCCESS 40424d0) — 아래 8.22-iOS 참고.
> 이전: **8.22 위치/로그인/팝업/설정 라운드**(실시간 위치+내 위치 카메라, 로그인 유지, 채팅·알림 인앱 배너, 댓글 프로필, 설정 탭) — 아래 8.22 참고.
> 이전: **8.11 채팅/크롭/전환/모양 라운드**(친구 채팅, 사진 4:3 크롭, 화면 전환 깊이감 줌, 다이아몬드 재현+행성 추가) — 아래 8.11 참고.
> 이전: **기능 배치 3**(업로드 별모양/색상 무한 캐러셀, 지도 필터 스피드다이얼 FAB, 맵 워터마크 제거)
> 이전: **기능 배치 2**(파장 애니메이션, 공개범위, 나만보기/친구선택 필터, 별자리, 배경음악, 마이페이지 별 모양)
> 이전: **기능 배치 1**(별 마커 5종×12색 Path 렌더, 친구, 미조회/친구 필터, 별 선택 업로드, FRIEND_POST 인앱 알림)
> + **named DB(stary-db) 연결 + firebase-bom 33.7.0 + Firebase Auth(Google/익명)** + 크래시 방어.
> ℹ️ 배경음악: 8.21 에서 멀티트랙(`raw/bgm_*.mp3` 6개)+음악 선택 화면으로 개편(구 `ambient_music.mp3` 삭제). 아래 8.21 참고.
> 이전: MapLibre+MapTiler 전환, applicationId 분리(`com.chaminwoo.stary_ios`), Firebase `momentdiary-f26c8`.

---

## 8.45 크로스플랫폼 버그 7건 (Android BUILD SUCCESSFUL 2026-08-09, iOS CI 검증 대기)

사용자 보고 7건. Android 변경분은 패리티 규칙(§1.5)에 따라 iOS 와 같은 단위로 처리.

**⚠️ 사용자 액션 3건 — 이거 안 하면 고쳐도 동작 안 함**
1. **Firestore 규칙 배포**: `firebase deploy --only firestore:rules`
   (#4 채팅 불통의 근본 원인이 서버 규칙이라, 배포 전에는 앱만 고쳐도 그대로 막힌다.)
2. **Cloud Functions 재배포**: `cd functions && npm install && cd .. && firebase deploy --only functions`
   (친구 요청 푸시 분기 + iOS APNs 옵션 추가분.)
3. **iOS 푸시 서버 설정**: Firebase 콘솔 > 프로젝트 설정 > 클라우드 메시징에 **APNs 인증 키(.p8)** 등록
   (Apple Developer > Keys 에서 발급, Key ID/Team ID 함께 입력). Mac 에서는 `cd iosApp && xcodegen generate`
   재실행 필요(신규 파일 `CameraPicker.swift`/`PushManager.swift` + entitlements 반영).
   시뮬레이터는 원격 푸시 불가 — **실기기**로 확인할 것.

**#1 첨부 3지선다(iOS)**: 갤러리+부메랑 2버튼 → Android 와 동일한 "사진 추가" 1버튼 →
`confirmationDialog`(카메라 촬영 / 갤러리에서 선택 / 3초 영상 촬영) + 첨부 후 "다시 선택".
`Features/Upload/CameraPicker.swift` 신설(UIImagePickerController `.camera`, 긴 변 1600px JPEG 0.85 축소,
권한/시뮬레이터 미지원 토스트). 촬영 화면 2종은 fullScreenCover **하나**(`captureSheet` item)로 통합.

**#2 휠 피커 스냅(iOS+Android)**: `commit(steps)` 이 `steps == 0`(반 슬롯 미만 드래그)에서 그냥 return 해
`drag` 잔여값이 남아 **항목 사이에 멈춘 채 고정**됐다 → 0 이면 원위치로 애니메이션(0.18s/180ms).

**#3 업로드 키보드(iOS)**: `@FocusState` + 키보드 툴바 "완료"(본문은 여러 줄이라 리턴키가 줄바꿈) +
제목 `submitLabel(.done)` + `scrollDismissesKeyboard(.interactively)`. 배경을 ZStack 형제 →
`.background { ScreenBackground }` 로 옮겨 키보드가 올라올 때 스크롤 영역이 실제로 줄어들게 함.

**#4 iOS↔Android 채팅 불통(근본 원인 = 서버 규칙)**: `firestore.rules` 의 chats 규칙이
`resource.data.participants` / `get(chats/{chatId}).data.participants` 를 봤는데, **방 문서가 없는 첫
메시지**에서는 둘 다 null → create 가 항상 거부. 즉 "이미 방이 있는 사이"만 채팅이 됐고 새 상대
(iOS↔Android 조합)는 영원히 불통. → **chatId 문자열로 참여자 판정**(`isChatMember` = `myAppUserId() in
chatId.split('_')`; chatId = 정렬 결합된 두 appUserId, 둘 다 '_' 없음). get() 도 사라져 읽기 비용 감소.
클라이언트도 **방 메타 먼저 → 메시지** 순서로 통일했고, iOS `send` 는 Bool 반환 + 실패 시 입력 복원·토스트
(`chatSendFailed`) — 조용한 실패 금지.

> **후속(같은 라운드에서 재수정) — 규칙은 판정 방식을 용도별로 섞어야 한다**
> 위 수정을 chats 문서에까지 적용했더니 이번엔 **친구 화면 마지막 메시지가 전부 사라졌다**("아직 채팅이 없어요").
> 원인: 친구 화면/인앱 배너는 `chats.whereArrayContains("participants", 나)` 로 **컬렉션 쿼리(list)** 를 하는데,
> 규칙이 "문서 id 안에 내 id 가 있는가"만 보면 규칙 엔진이 **쿼리 결과를 증명할 수 없어 쿼리 전체를 거부**한다
> (rules 는 필터가 아니다 — 쿼리 제약과 규칙 조건이 같은 형태여야 통과). 메시지 하위 컬렉션은 경로에 chatId 가
> 확정돼 있어 영향이 없어서 "채팅은 되는데 목록만 안 보이는" 형태로 나타났다.
> **최종 규칙**: 방 문서 read/update/delete·목록 쿼리 = `resource.data.participants` 기준,
> 방 문서 create + `messages` 하위 = `isChatMember(chatId)` 기준. 한쪽으로 통일하면 반드시 다른 쪽이 깨진다.

**#5 채팅 입력바 인셋**:
- Android: `navigationBarsPadding() + imePadding()` 이어 붙이기 → 키보드 위로 내비바 높이만큼 더 뜸.
  `windowInsetsPadding(WindowInsets.safeDrawing.only(Bottom))` 한 번으로 교체(둘 중 큰 값) +
  Manifest 에 `windowSoftInputMode="adjustResize"` 명시(창이 통째로 밀리는 것 방지 — edge-to-edge 조합 정석).
- iOS: `ZStack { ScreenBackground(ignoresSafeArea); VStack{...} }` 구조가 **스택을 키보드 영역까지 키워서**
  입력 바가 키보드 뒤에 깔렸다 → `.background { ScreenBackground }` 로 변경(레이아웃 비관여).
  입력 바 배경만 `.ignoresSafeArea(.container, edges: .bottom)` — **regions 를 `.all` 로 쓰면 재발**.

**#6 친구 요청 알림(Android+iOS)**: 기존엔 `friendRequests` 문서만 만들어 **알림도 푸시도 없었다**
(친구 화면에 들어가야만 보임). → 요청 전송 시 `notifications` 문서(type=**FRIEND_REQUEST**, 수신자=toId,
diaryId 없음)를 함께 생성 → 목록 + 인앱 배너 + FCM 푸시가 한 번에 동작. shared `NotificationType` 에 항목 추가,
Functions `notifyOnNotificationCreate` 에 분기(제목 "{이름}님의 친구 요청"). 탭 → 친구 화면
(Android: `openFriends` extra + `DeepLinkState.friendsNonce` / iOS: `PushRoute.friends`).

**#7 iOS 푸시 자체가 없었음 → `Data/PushManager.swift` 신설**: iOS 에는 FirebaseMessaging/APNs 코드가
**전혀 없어서** users/{uid}.fcmToken 이 비어 있었다(서버는 토큰 없는 사용자를 조용히 건너뜀) — 친구 새 글도
친구 요청도 팝업이 올 수 없었다. 구성: `configure()`(AppDelegate) → `setUser(uid)`(RootView, 권한 요청 +
registerForRemoteNotifications + 토큰 저장) → 전면 `willPresent` 는 `[]`(인앱 배너가 담당, Android 정책 동일) →
탭은 `PushRouter` → RootView `.onReceive`(구독 시 현재 값도 받아 콜드 스타트 처리). `project.yml` 에
FirebaseMessaging SPM + `UIBackgroundModes: remote-notification` + entitlements(`aps-environment`,
`iosApp/Stary.entitlements` — 생성물이라 gitignore).
Android 쪽 보강: `GoogleAuthHelper.syncFcmToken(uid)` 를 **세션 복원(앱 재시작)에서도** 호출(예전엔 로그인
화면을 실제로 거친 순간에만 저장 → 토큰 회전 시 조용히 끊길 수 있었음).

**남은 것**: 실기기 확인(iOS 푸시/카메라, Android 채팅 인셋), 규칙·Functions 배포 후 채팅 재검증.

### 8.45-4 채팅 백그라운드 푸시 미수신 — 진단 계측 추가

"채팅은 가는데 백그라운드 알림이 안 온다" 보고. 코드 경로(트리거 문서 경로/컬렉션명/채널 id/수신자
산출)는 전수 확인 결과 정상이라, **어디서 끊기는지 보이게** 만드는 쪽으로 처리했다.
경로가 길어서(문서 생성 → 트리거 → 토큰 조회 → FCM → 기기 표시) 한 칸만 비어도 조용히 아무 일도 안 난다.
- **Functions 로그 강화**: `chat {id}: A → B 푸시 시도` / `fcmToken 없음 → 발송 생략` /
  `발송 성공 {messageId}` / `발송 실패 (code)`. users 문서 부재, senderId 가 chatId 에 없는 경우도 경고.
- **토큰 저장 로그**: Android `fcmToken 저장 완료 users/…`, iOS `✅ fcmToken 저장 완료 …`.
  iOS 는 델리게이트 콜백만 믿지 않고 APNs 등록 직후 `Messaging.token` 을 **명시 조회**(순서 문제 방어),
  권한 거부/토큰 발급 실패도 콘솔에 남긴다.
- **Android 기본 알림 채널 메타데이터** 추가(`default_notification_channel_id=stary_default`) —
  channelId 없이 온 메시지가 저중요도 '기타' 채널로 빠져 배너가 안 뜨는 경우 방지.
- **Functions 런타임 nodejs20 → 22**(20 은 지원 종료 예정 — 배포가 막히는 원인이 될 수 있음).
- 확인 순서는 `docs/code/11-notifications-push.md` 의 "푸시가 안 올 때" 절 참고.
- ⚠️ 전면(포그라운드)에서는 **의도적으로** 시스템 배너를 막고 인앱 배너를 띄운다(양 플랫폼 동일) —
  반드시 앱을 백그라운드/종료 상태로 두고 테스트할 것.

### 8.45-2 iOS 폰트를 Android 와 동일하게(MinSans) — 사용자 지시

iOS 는 8.41 에서 들어온 **PoorStory** 를 앱 전역 폰트로 쓰고 있었는데 Android 에는 그 폰트가 아예 없다
(Android UI 폰트 = **MinSans**, `res/font/min_sans.ttf`). → iOS 를 MinSans 로 전면 교체.

- **폰트 파일은 복제하지 않는다**: `project.yml` sources 에 `../androidApp/src/main/res/font/min_sans.ttf`
  를 `buildPhase: resources` 로 추가(9MB 짜리 중복 커밋 방지) + `UIAppFonts: min_sans.ttf`.
  `iosApp/Sources/Resources/poor_story_regular.ttf` 는 삭제.
- **가변 폰트 함정(중요)**: min_sans.ttf 는 wght 100~900 가변 폰트인데 **기본값이 100(Thin)** 이다.
  그냥 `UIFont(name:)` 로 쓰면 안드로이드보다 훨씬 얇게 나온다 → `AppFont.swift` 가
  `kCTFontVariationAttribute` 의 'wght' 축을 직접 지정해 인스턴스를 만든다.
  굵기 매핑은 Android `Type.kt` 표와 동일: light 300 / normal 400 / medium 450 / semibold 500 / bold 550.
  폰트 이름은 런타임 해석(`MinSansVF-VF` → 패밀리 후보 → "minsans" 포함 패밀리), 실패 시 시스템 폰트 폴백.
- **호출부 일괄 교체**: `.font(.poorStory(n))` → `.font(.minSans(n[, weight]))` (117곳).
  SwiftUI 시맨틱 폰트(`.headline/.caption/.subheadline/.footnote/...`)와 `.font(.system(size:))` 로
  남아 있던 **Text** 들도 전부 MinSans 로(= Android 처럼 앱 전체가 한 서체). ⚠️ `Image(systemName:)` 의
  `.font(.system(size:))`/`.font(.caption2)` 는 **SF Symbol 크기 지정이라 그대로 둔다**.
- **크기 정렬(Android sp 그대로)**: 상단바 제목 18 SemiBold, 드로어 헤더 15 SemiBold, 드로어 항목 17 SemiBold,
  본문 기본 16, 상세 제목 24 SemiBold, 상세 본문 16, 댓글 14, 채팅 말풍선 15, 캡션 12/11.
  push 화면 내비바(`configureNavigationBarAppearance`)도 MinSans 18 SemiBold + 뒤로가기 라벨 16.
- **글자 잘림 대책**: `Font(UIFont)` 기반이라 Dynamic Type 비례 확대가 없어졌다(고정 크기 —
  Android 의 폰트 배율 상한 1.15 와 같은 취지). 고정폭 프레임에 갇힌 Text 는 없고(전수 확인),
  고정 높이 컨테이너(탑바 56 / 드로어 항목 52 / 진행 밴드 52)는 새 줄높이로도 여유가 있다.
  ⚠️ 참고 수치: 같은 pt 에서 MinSans 는 PoorStory 대비 **x-height 1.42배, 줄높이 1.07배** —
  글자가 더 크고 꽉 차 보이는 게 정상(안드로이드와 같은 모습). 작아 보이게 되돌리지 말 것.

### 8.45-3 iOS 로그인 인트로 — 로고/버튼 1초 빨리 노출 (사용자 지시)

iOS 는 인트로 영상이 **끝나야** 로그인 UI 를 띄우고 있었다(Android 는 영상과 무관하게 1.5초 고정 노출).
- 인트로 실측: 미디어 7.83초를 속도 곡선(2.5x→1.8x→0.5x)으로 재생 = **실제 약 4.85초**.
- `IntroVideoView.Coordinator.earlyRevealSeconds = 1.0` 신설 — 영상이 끝나기 1초(실제 시간) 전에
  `onEnded`(=UI 노출)를 발화. → **약 3.85초에 노출**(+페이드 0.8초). 영상은 그 뒤로도 계속 재생돼
  UI 가 그 위로 페이드인된다(Android 도 재생 중 노출이라 동작이 같아짐).
- ⚠️ 종반이 0.5배속까지 감속하므로 "남은 미디어 시간"으로 계산하면 크게 어긋난다 →
  속도 곡선을 수치 적분하는 `remainingWallSeconds` 로 **실제 남은 시간**을 구한다.
  속도 곡선은 `rate(atProgress:)` 한 곳으로 모아 적분과 실제 배속이 항상 같은 값을 쓰게 했다.
- **남은 차이**: Android 1.5초 vs iOS 약 3.85초 — 더 줄이려면 `earlyRevealSeconds` 를 키우거나
  "재생 시작 후 1.5초 타이머" 방식으로 바꾸면 된다(사용자 요청은 1초 단축이라 여기까지만).

---

## 8.44 iOS 패리티 라운드3 — 계정 통일 + 버그/미완 일괄 (Android BUILD SUCCESSFUL, iOS CI BUILD SUCCESS `003b2b3`, 2026-07-16)

**CI 시행착오 2건(다음에 반복 금지)**: ① MapLibre 6.x 에서도 NSExpression JSON 초기화 라벨은
**`mglJSONObject:`**(MGL 접두사 유지 — `mlnJSONObject` 아님). ② SwiftUI `Path.union/subtracting` 은
**iOS 17+** — 배포 타깃 16 에선 `CGPath.union/subtracting`(iOS 16+)으로 우회(`unionCompat`/`subtractingCompat`).

사용자 보고 버그 7건 + 미완료 6건. 브랜치 `feat/ios-parity-round2` 이어서 작업.

**⚠️ 최우선 사용자 액션 — "DiaryWarpData 선언 없음"(#1)**: `DiaryWarpData` 는
`iosApp/Sources/Features/Map/DiaryOpenWarpView.swift:7` 에 있고 CI(f6428d2)도 그린이었다.
Mac 의 `.xcodeproj` 가 옛 파일 목록(신규 파일 미포함)이라 나는 에러 —
**`cd iosApp && xcodegen generate` 재실행 후 빌드**하면 해결(신규 Swift 파일이 생길 때마다 필요).
"별 후광/파티클 미완"(R2-3 구현됨)·"글로브 미완"(8.43 지구 복원)도 같은 원인(옛 빌드)일 가능성이 큼 —
재생성 빌드로 확인 후 남는 차이만 후속.

**#7 같은 구글 계정 = 같은 유저(근본 수정, iOS)**: Android 는 userId=**Google sub**(JWT subject),
iOS 는 FirebaseAuth uid 를 쓰고 있었음 → 같은 구글 계정이 OS 별로 다른 계정으로 갈라짐.
`AuthManager.appUserId(of:)` 신설(= providerData google.com 의 uid, 익명은 FirebaseAuth uid 폴백 —
Android `restoreSession` 규칙 동일) → 상태 리스너/ensureProfile/requestDeletion(문서 id=sub,
authUid=FirebaseAuth uid 기록)/InviteStore 전부 이 값 사용. users 문서에 authUid 병행 기록.
⚠️ **기존 iOS 계정(FirebaseAuth uid 문서)의 데이터는 새 uid 로 승계되지 않음**(테스트 데이터라 정리 대상).
- 이 수정으로 #5(맵 미표시)·#6(댓글/하트 먹통)의 핵심 원인도 해소: uid 불일치로 내 글이 isOwner=false 가 되고,
  시뮬레이터 미국 위치(아래 #3) 때문에 100m 게이팅에 걸려 상호작용이 잠겨 있었음.

**#3 시뮬레이터 위치 = 서울 고정(iOS)**: 시뮬레이터 기본 시뮬레이션 위치(쿠퍼티노)가 서울 폴백을
덮어씀 → `LocationManager.didUpdateLocations` 를 시뮬레이터 빌드에서 무시(서울=건국대 유지).
업로드 좌표(`coordinateOrDefault`)도 서울이 되므로 "방금 만든 다이어리가 맵에 안 보임"(#5) 해결.

**#2 달·행성(+꽃·보석) 모양(iOS)**: even-odd 근사 → **진짜 boolean 연산**(iOS16 `Path.subtracting/union`,
Android `Path.Op` 패리티). 초승달=차집합(삐져나온 안쪽 원 채움 제거), 행성=본체∪고리 밴드(겹침 구멍 제거),
꽃=꽃잎 합집합−가운데 원, 보석=실루엣−패싯 컷 라인(`strokedPath`, Android gemPath 세그먼트 동일).

**#4 친구 요청 피드백(Android+iOS)**: 검색 결과에 "요청됨" 상태 칩 —
shared `FriendRepository.observeOutgoingRequests`(fromId==나) 신설 + Android `FriendViewModel.outgoingRequests`
+ `friend_status_requested`(ko/en/ja). iOS `FriendsViewModel.outgoingIds` 리스너 + 상태 칩(친구/요청됨)
+ **전송 토스트**(`friendRequestSent`/`friendRequestFail` — Android StaryToast 문구 패리티).

**미완 항목**:
- **후광 업그레이드(iOS)**: 단일 민트 CircleLayer → Android 패리티 2겹(**별색** `auraColor`) —
  바닥광(반경 0.6→7×sizeMult, blur 1.4, y+8) + 오오라(반경 2→26×sizeMult, 불투명도 sizeMult 1→0/1.4→0.12/3→0.42).
  데이터 주도 색/줌 보간은 `NSExpression(mlnJSONObject:)` JSON 표현식 사용(⚠️ CI 로 API 명 검증 필요).
  레이어 순서 파티클→별자리→바닥광→오오라(Android 동일).
- **타인 프로필 = 핀 별만(iOS)**: `UserProfileScreen` 부유 별을 전체 공개 다이어리(10개) →
  `users/{uid}.pinnedDiaries` 핀 별만(Android 동일). 내 프로필 핀 피커는 기존 구현 유지.
- **친구 행 사진 탭=프로필(iOS)**: 행 탭=채팅 유지, 아바타 위 투명 버튼 오버레이 → `UserProfileScreen` push.
  검색 결과/받은 요청 행 아바타도 동일(Android PersonCard onClick 패리티).
- **지도 틸트(iOS)**: `MapLibreView.baseTiltDeg=25`(Android BASE_TILT_DEG) — 초기 카메라 pitch 25° +
  회전/틸트 제스처 잠금(Android uiSettings 동일).
- **마지막 카메라 복원(Android+iOS)**: 카메라 idle 마다 중심+줌 저장(Android `LocationHelper.persistCameraState`
  2s 스로틀 / iOS `LocationManager.persistCameraState`) → 앱 시작 초기 카메라 = 마지막 본 곳(fix 오면 기존
  didAutoCenter 가 내 위치로 1회 이동하는 동작은 유지).
- **채팅 별가루 삭제(Android+iOS)**: `ChatStardust` 호출+정의 양쪽 제거(사용자 지시 — 떠다니는 원).

**후속 수정(사용자 재현: "미국 카메라에서도 새 별 안 보임") — #5/#6 의 진짜 원인 2건**:
- **⚠️ 전 모델 id 디코딩 버그(치명)**: R2-1 커스텀 `init(from:)` 이 `@DocumentID` 합성 주입을
  **대체**하면서 문서 안 "id" **필드**만 읽었다 → id 필드를 저장하지 않는 **iOS 생성 문서 전부 id=nil**
  (Android 는 id 필드를 함께 저장 + 읽을 때 doc.id 로 덮어써서 무증상). 영향: Diary/Comment/
  AppNotification/Friend/FriendRequest/UserProfile/ChatMessage 7종 — 좋아요·댓글·공유·상세 리스너·
  요청 수락·메시지 삭제가 전부 `guard let id` 에서 **조용히** 실패(#6), Friend Hashable 충돌 등.
  수정: 7곳 모두 `_id = (try? c.decode(DocumentID<String>.self, forKey: .id)) ?? 필드 폴백` —
  합성 디코더와 동일하게 문서 참조에서 id 주입(기존 id 없는 문서도 소급 정상화).
  ⚠️ **교훈: @DocumentID 모델에 커스텀 init(from:) 을 쓰면 _id 를 반드시 명시 디코드할 것.**
- **observeAll 쿼리**: 정렬 없는 `limit(500)` = 문서 ID 오름차순 임의 500개 → 새 문서가 창 밖으로
  밀려 지도에 안 뜰 수 있음(#5). Android observeAllDiaries 와 동일하게 **createdAt DESC limit 1000**
  + 에러 시 빈 배열 덮어쓰기 제거(기존 목록 유지 — Android 동일).

**남은 것(후속)**: 사용자 Mac xcodegen 재생성 후 실빌드 확인(글로브/파티클 실측), iOS 계정 통일 후
구 uid 데이터 정리, MyDiaryBoard 드래그 물리(8.42 잔여).

---

## 8.43 iOS 패리티 라운드2 — 데이터 연동/연출/글로브 + Android 업로드 휠 (CI BUILD SUCCESS `f6428d2`, 2026-07-16)

사용자 지시: **"Android UI 와 동일하게, 항목별 완벽 완성"** 14건 + Android 변경 1건. 브랜치 `feat/ios-parity-round2`.
이전 세션 로컬 커밋(R2-1~R2-3)은 미push 상태였음 → push 시 첫 CI 컴파일 에러 1건 수정 후 그린. 이후 마무리 5건 추가.

**첫 CI 수정** (`73193ff`): `LocationManager.lastSavedCoordinate` 가 `@MainActor` 격리인데 지도 델리게이트
(비격리 autoclosure)에서 참조 → 컴파일 에러. UserDefaults 만 읽으므로 `nonisolated static var` 로 변경.

**R2-1~R2-3 (이전 세션 커밋, 이번에 push·검증):**
- **R2-1** 전 모델 방어 디코딩 확장(Comment/AppNotification/ChatMessage/UserProfile 에 flexString/flexMillis —
  타입 드리프트로 문서 통째 누락 방지, Diary/Friend 와 동일 정책) + 알림 행 Android NotificationItem 구조/문구 패리티.
- **R2-2** 100m 열람 게이팅+토스트(위치불명=map_waiting_fix / 밖=map_open_range 반경·현재거리 / 이내만 열림),
  `DiaryOpenWarpView`(지도 스냅샷 CIBumpDistortion 굴절+파장 링+겹친별 버스트, Android DiaryOpenWarp 1.3s 대응),
  줌 별 크기(iconSize 보간 8→0.3/12→0.55/15→1.0 을 어노테이션 뷰 transform 으로), 미디어 항상 표시.
- **R2-3** 별가루 파티클(`MapStyleEffects.swift`, 400개/20km/시드42/트윙클 4위상)+별 후광 레이어(민트 CircleLayer)+
  별자리 3겹 라인 토글(halo/glow/line 페이드)+몰입(지도만 보기) 모드(우하단 내위치→별자리→몰입 버튼, 하단 X 종료).

**마무리 5건 (이번 세션):**
- **#8 프로필 아이콘 크리스탈화**: `FloatingStatBox` 의 비-별 아이콘(하트/친구/조회/업적)을 SF Symbol 틴트 →
  별과 같은 크리스탈 파편 채움. `StarCrystal.iconImage(systemName:color:seed:size:)` 신설 —
  심볼을 흰색으로 그려 알파 마스크로 쓰고 `drawFacets`(실루엣 없는 사각형 파편)를 `.sourceIn` 으로 얹어
  아이콘 모양 안에만 파편. 무늬 정적 → NSCache 1회 베이크(2배 해상도). Android `bakeCrystalIcon` 패리티.
  - ⚠️ `StarCrystal.draw` 를 `drawMesh(salt:silhouette:...)` 로 리팩터 — 별(실루엣=StarShape)과 아이콘(실루엣 nil)
    이 같은 파편 로직 공유. `salt` = 해시 솔트(별=모양타입 / 아이콘=인덱스).
- **#9 음악 별자리 = 내 다이어리 보드 스타일**: `MusicConstellationView` 를 `ConstellationBackgroundView` 와
  동일 렌더로 — 선택 플래시 1.7→0.78(easeOut 0.9s), 그라데이션 후광(radialGradient), mag 가중 밝기·트윙클(3.4s),
  `flashKey`(=selectedIndex) 로 곡 변경 시 번쩍. Android `MusicConstellationBackground` 패리티.
  - ⚠️ Canvas 수식 CGFloat·Double 혼합 '+' 모호성 → Double 로 계산 후 마지막에 CGFloat(8.42 7차와 같은 함정).
- **#12 글로브 지구 안 보임(구름만) — 근본 원인 수정**: 커스텀 UV 구체(`sphereGeometry`)·트레일(`trailNode`) 메쉬에
  **법선(normals) 소스 누락**. 지구/구름 재질이 `_surface.normal` 을 쓰는 셰이더 모디파이어(낮/밤 반구)를 얹는데,
  **법선 없는 메쉬 + 셰이더 모디파이어 = 파이프라인 컴파일 실패 → 노드 통째 소멸**. 두 메쉬에 방사방향 단위법선 추가로 복원.
  지구 감광도 원본×0.25 → **원본×0.45 감광(Android EARTH_BRIGHTNESS=0.45f 동일)**. (별밭/은하수/유성은 원래 정상)
- **#14 미번역 전수**: `L10n` 8키 추가(ko/en/ja, Android strings.xml 값) —
  musicDragHint/musicLockedHint/commonSecret(music_*), boomerRetake/boomerUse(boomer_*),
  listEmptyUnviewed/listEmpty/listSortNearby(ListScreen). MusicScreen 부제·BoomerangCaptureView 버튼·ListScreen
  빈상태/정렬/제목·익명 하드코딩 제거. (DiaryCard 제목=shareCardUntitled, 익명=commonAnonymous 재사용)

**Android 변경(사용자 지시): 업로드 별 모양/색 피커를 iOS 휠 구조로** (`7f1c69b`, `:androidApp:compileDebugKotlin` BUILD SUCCESSFUL):
- `UploadScreen.kt` 의 `HorizontalPager` 무한 캐러셀 2개 → **`StarWheelPicker`**(파일 하단 신설, iOS `WheelPicker` 이식):
  선택 항목 항상 정중앙(민트 링), 좌우 5개 노출, modulo 순환. 놓은/탭한 지점 항목을 **놓은 자리에서 중앙으로 스냅 →
  스냅 끝난 뒤 selection 일괄 갱신**(좌표 불연속 되돌아감 방지). `Animatable`(drag)+`detectHorizontalDragGestures`.
  잠긴 항목 흐림(alpha 0.25)+자물쇠+토스트, 저장 차단은 기존대로 호출부.
- `starType`/`starColor` 를 pagerState 파생값 → `mutableIntStateOf` 로 단순화. INFINITE_PAGES/lerp 상수 제거.

**남은 것(후속)**: 실기기 실제 동작 확인(지구본 텍스처·크리스탈 아이콘·휠 피커 촉감), MyDiaryBoard 드래그 물리(8.42 5차 잔여).

## 8.42 iOS UI 전면 패리티 1~7차 (CI BUILD SUCCESS `9cd867b`, 2026-07-15)

**후속 라운드(2~7차) 요약** — 아래 1차(토대) 위에 순차 push, 전부 CI 그린:
- **2차 지도 크롬**: 좌상단 줌 +/−(44pt, 0x1A1A1A) + 우하단 내 위치(48pt, 생성 FAB 위) —
  `MapLibreView` 에 zoomRequest/recenterNonce 커맨드 채널. 우상단 칩 제거 → **좌하단 필터 스피드 다이얼**.
- **3차 상세 재구성**: 4:3 히어로 헤더(미디어/image_frame + 스크림 + 별·작성자·배지·날짜 오버레이),
  제목 본문 분리, 본문 카드(0xCC14181C + accent 그라데이션 테두리), 인라인 좋아요/공유/**수정·삭제(신규)**/신고,
  댓글 "댓글 N" 헤더 + 구분선 스타일, 잠금 pill(map_open_range).
- **4차 지도 필터 확장**: 친구만/나만보기/친구선택(FriendFilterPicker 시트) — Android 상호배타 로직 동일.
- **5차 내 다이어리 별자리 보드**(`MyDiaryBoardScreen.swift` 신설, 구 MyStarsScreen 대체):
  별자리 3종(좌표/연결선 Android CONSTELLATIONS 동일) + 트윙클/선택 번쩍임, **바나나 다이얼**(포물선 기하·드래그·탭,
  최신/인기/거리) + `MusicManager.playWind()` 신설, 부유 별 보드(간이 — 결정론 배치+개별 부유, ⚠️ 드래그 물리는 후속),
  1열 리스트(0x66161B22 행).
- **6차 L10n 이관**: Upload/Friends/Chat/Login/Notifications/Achievements/Profile 하드코딩 한국어 →
  L10n(ko/en/ja, Android strings.xml 값). 업로드 저장 버튼 = common_save, 공개범위 vis* 매핑.
- **7차 수정**: `MyDiaryBoardScreen:379` CGFloat/Double 혼합 "ambiguous use of '+'" 컴파일 에러 —
  ⚠️ **Canvas 수식에서 CGFloat(모델값)와 Double(시간값)을 한 식에 섞지 말 것**(Double 통일 후 CGFloat 변환).
- **CI 개선**: `ios.yml` 이 xcodebuild 실패 시 " error: " 줄을 `::error::` 어노테이션으로 노출 —
  로그 다운로드 권한 없이 check-runs annotations(공개 API)로 컴파일 에러 확인 가능.
- **남은 것**: DiaryStarBox 드래그 물리, 지도만보기(몰입) 모드, 별자리 라인 토글(지도), 온보딩 코치마크,
  ListScreen(고아 — 미사용) 정리, BoomerangCaptureView 문구 2건 L10n.

### 1차 — 드로어 내비 + 지도 스타일 + 배경/테마 (토대)
사용자 지시: **"Android UI 와 iOS UI 를 모든 스크린에서 최대한 완전히 같게"**. 1차분(구조/토대):

- **테마 토큰 동기화**: `Theme.swift` 를 Android `Color.kt` 와 1:1 로(Bg 0x0D0D0D / Surface1 0x1A1A1A /
  Surface2 0x242424 / Outline / Mint / MintBlue / AccentRed / TextPrimary 0xF0F0F0 / TextSub 0x8A8A8A).
- **내비 구조 전환(핵심)**: 5-탭 TabView 폐기 → Android MainScreen 과 동일한
  **지도 루트 + 상단바(햄버거 · "지도" · 알림 하트+빨간점) + 좌측 드로어(0x111111, 우측 라운드 24,
  내다이어리/프로필/업적/배경음악/친구/설정/로그아웃) + 민트→블루 그라데이션 글쓰기 FAB**.
  하위 화면은 **루트 단일 NavigationStack push**(Android 단일 NavHost 대응) — Friends/Upload/Profile 의
  내부 NavigationStack 제거, ProfileScreen 은 path → `navigationDestination(isPresented:)` bool 로 전환,
  프로필 탑바는 Android 처럼 "+"(핀)만 남기고 음악/설정/알림 진입점은 드로어/하트로 이동.
  상세/겹친별/타인 프로필도 시트 → push(NavRoute.Detail/StarCluster/UserProfile 대응).
  `TabRouter` 는 (tab, nonce) 요청 라우터로 개편(호출부 시그니처 유지 — map=pop-to-root, 그 외=push).
  push 화면 공통 탑바 톤은 `StaryApp.configureNavigationBarAppearance()`(0x0D0D0D 불투명/PoorStory 20/0xF0F0F0).
- **야경 지도 스타일**: Android `res/raw/maplibre_style.json` 을 iOS 번들로 복사,
  `MapLibreView.staryStyleURL` 이 `__MAPTILER_KEY__` 를 Info.plist `MAPTILER_KEY`(project.yml 주입, 빈 값 허용)로
  치환해 임시 파일 URL 로 로드. 키 없으면 demotiles 폴백. ⚠️ 실기기에서 야경 스타일 보려면
  Xcode 빌드 설정(또는 CI)에 MAPTILER_KEY 채울 것(Android secrets.properties 와 같은 값).
- **배경 이미지 이식**: mydiary_bg/mypage_bg/upload_bg/image_frame(.webp) + wind.mp3 번들 복사,
  `Core/BundleImage.swift`(NSCache 로더) + `ScreenBackground(name:darken:)` 신설.
  적용(다크 틴트 Android 값 그대로): Upload=upload_bg 0.82, Friends/Profile/MyStars/Achievements/Music/
  UserProfile/StarCluster=mydiary_bg 0.82, Settings 0.84, Chat 0.85.
- L10n: navMap/navMyDiary/navMusic/navNotification/navUpload/navDetail/navStarCluster/drawerList/drawerLogout/drawerLogin.
- **남은 것(후속)**: 지도 화면 크롬(내위치/필터 스피드다이얼/지도만보기 FAB 배치), MyDiary 별자리 보드,
  화면별 세부 레이아웃(업로드 피커 캐러셀, 상세 카드 구성, 설정 별 thumb 슬라이더 등), 하드코딩 한국어 문자열 L10n 이관.

---

## 8.41 다른 컴퓨터 iOS 작업 합류 — 로그인 화면 개편 + 커스텀 폰트 + 번들 ID 확정 (로컬 커밋만, 2026-07-15)

**배경**: 다른 컴퓨터(Xcode 신규 설치)에서 이 레포를 클론해 `main` 브랜치에 독립적으로 iOS 작업(`84e7a91`)을 커밋·푸시함.
그 브랜치는 `feat/moderation-profile-round`(이 세션 전체 iOS 패리티 작업)가 갈라지기 **전**의 옛 지점(`cafec9d`, 6/28)에서
시작된 것이라 8.27~8.40 라운드 기능이 전부 빠져 있었음 — 로그인/폰트/색상/드로어 네비/지도 스타일 5가지를 새로 얹은 상태였음.
사용자 지시: **로그인 화면·커스텀 폰트만 그쪽 것을 채택, 나머지(색상/드로어 네비/지도 스타일)는 전부 버리고 이 세션 브랜치 유지**.

- **로컬 `main` 을 `feat/moderation-profile-round` 로 리셋**(첫 자동 병합이 최신 기능들을 조용히 옛 버전으로 되돌려서 재작업) 후,
  파일 단위가 아니라 **diff hunk 단위로 로그인/폰트만 이식** — 두 브랜치의 diff 를 전부 대조해 폰트 전용 줄(`.font(...)` 만 바뀐 곳)만
  골라 현재 구조(스파클/히든배지/34-라운드 UI 등) 위에 얹었고, 화면 전체가 다시 설계된 파일(`ProfileScreen`/`RootView` 드로어/
  `MapScreen` 글로브·길찾기·클러스터/`MapLibreView` 커스텀 스타일)은 통째로 스킵(원래 이 세션 버전 그대로).
- **로그인 화면 전면 교체**(`LoginView.swift` 통째로 채택): 무음 인트로 영상(`login_video.mp4`, 재생속도 2.5x→감속 곡선) →
  빛나는 후광 로고(`logo.png`) + 크림색 그라데이션 "Google 계정으로 로그인" 캡슐 버튼 + "로그인 없이 둘러보기".
- **커스텀 폰트 도입**(`Core/AppFont.swift` 신설): `PoetsenOne-Regular`(영문 타이틀, 미사용) / `PoorStory-Regular`(한글 본문·UI 전반).
  `RootView.body` 최상단에 `.font(.poorStory(16))` 앱 기본값 적용 + 12개 화면(Detail/Friends/List/Music/Notifications/
  Settings/UserProfile/Upload/InAppBanner/ContentView/MapScreen 필터칩)에서 기존 `.headline`/`.caption` 류를
  `.poorStory(size)` 로 1:1 교체(구조/로직은 그대로, 폰트만). `project.yml` 에 `UIAppFonts` 등록 필요(추가함).
- **번들 ID 최종 확정 = `com.chaminwoo.stary.ios`**(점 표기, 어제 `com.chaminwoo.stary` 로 잠정 설정했던 것 대체) —
  다른 컴퓨터가 이미 이 ID로 Firebase `momentdiary-f26c8` 에 iOS 앱을 등록하고 실제 `GoogleService-Info.plist` 를 받아왔음.
  `project.yml`(`PRODUCT_BUNDLE_IDENTIFIER`+`GOOGLE_REVERSED_CLIENT_ID` 실값) / `fastlane/Appfile` / 문서 4종 / `CLAUDE.md` 동기화.
- **`GoogleService-Info.plist` 커밋 유지(사용자 명시 결정)**: `iosApp/Sources/GoogleService-Info.plist` **1곳만**(레포 루트·`iosApp/` 바로 밑
  중복 사본은 삭제) — `iosApp/.gitignore` 에 `!Sources/GoogleService-Info.plist` 예외 추가. Firebase iOS 클라이언트 설정은
  앱 바이너리에 항상 포함되는 값이라 CLAUDE.md 의 "민감값 하드코딩 금지"와는 별개로 취급하기로 함(자세한 근거는 CLAUDE.md §0 참고).
- ⚠️ **iOS TODO**: `UIAppFonts` 를 아직 CI 로 검증 안 함(다음 push 시 `ios.yml` 확인). Android 쪽엔 이 폰트/로그인 개편을 반영하지
  않음(요청 범위 밖 — 필요하면 별도 라운드로 Android 패리티 진행할 것).

---

## 8.40 마감 라운드(1.3.1) + 전환 잔상 삭제 + iOS 패리티 일괄 (Android BUILD SUCCESSFUL 2026-07-14, iOS CI 검증 대기)
두 커밋으로 분리: ① 이전 세션분 마감 라운드(안드+iOS), ② 전환 잔상 삭제 + iOS 패리티 일괄(33·34 라운드).

**① 마감 라운드(1.3.1, versionCode 6)** — 안드+iOS 동시:
- **사진/움짤/영상 전체화면 뷰어**: 상세의 미디어 탭 → 원본 비율(Fit) 전체화면 + 핀치 확대/드래그.
  안드 `DetailScreen.FullScreenMediaViewer` / iOS `DetailScreen.swift` 동명 뷰(+`RemoteGifFitView`).
- **부메랑 전체 화각 크롭**: 조정 단계 진입 시 "찍힌 화면 전체가 들어오는 최소 배율"로 시작(잘림 없음, 남는 자리 검정).
  안드 `BoomerangHelper.minScaleFor`+크롭이 캔버스에 그대로 그림 / iOS `BoomerangConfig.minScale`+`cropFrames` 동일 + 프리뷰 `resizeAspect`.
- **친구 행 최근 별(34-6)**: 행 최우측 = 그 친구의 최근 공개 별(비공개/익명 제외), 탭 → 지도 파동+도보 길찾기.
  안드 `FriendScreen`+`NavGraph(onOpenDiaryOnMap)` / iOS `FriendsScreen`(store.diaries 필터).
- **겹친별 위성 부유(iOS)**: `MapLibreView.MergedStarAnnotationView` — 멤버 2개 이상 머지 마커는 뷰 어노테이션으로,
  대표+위성 미니어처가 함께 상하 float + 위성별 독립 드리프트(Android 위성 부유 패리티, CABasicAnimation 벽시계 위상).
- **세로 고정**: 안드 `screenOrientation="portrait"` / iOS `UISupportedInterfaceOrientations` Portrait 만.
- **35-1 리팩토링**: `DiaryMap.kt`(2079줄) → DiaryMap / DiaryMapMarkers / DiaryOpenWarp 3파일 분할 + 주석 다이어트(체크리스트 35 기준).

**② 전환 잔상 삭제 + iOS 패리티 일괄** (`:androidApp:assembleDebug` BUILD SUCCESSFUL):
- **34-10 화면 전환 별 잔상 완전 삭제(사용자 지시)**: `core/ui/RouteStreak.kt` 삭제 + `MainScreen` 오버레이 호출 제거. iOS 미구현 유지.
- **iOS 신설 4파일**(`Core/`): `StarLoadingView.swift`(34-9 로딩 별 — 비트맵 1회 굽고 스케일만, 팔레트 밖 색 별도 캐시) /
  `StarBirth.swift`(34-8 — `StarBirthStore.shared`+`StarBirthHost`, 업로드 성공 → 지도 탭 전환 후 재생) /
  `HiddenStarBadge.swift`(34-4 — 이름 옆 전용 크리스탈 배지, `StarCrystal.image` NSCache 재사용).
- **34-4**: `HiddenAchievements.swift` 에 `badgeType/badgeColor` 추가(**값 Android 동일** — drift 금지) +
  **칭호 fallback drift 정정**(iOS 구 칭호 "은하의 밀사" 등 → Android 정본 "별의 암호" 등. 표시는 원래 `LocalizedNames` 라 영향 없음).
  `HiddenAchievementStore` 에 `static shared` 승격(전역 리스너 1개, Android HiddenClaimStore 패턴) + `achievements(of:)`.
  배지 삽입: Detail 작성자/댓글 · 친구 행/검색 · 채팅 타이틀(principal 툴바) · 내/타인 프로필. 달성자 이름 = `UserDirectory` 현재값(34-4a).
- **34-1** `StarClusterView` 헤더 별 ↔ 페이지 연동(활성만 밝게+확대+후광, easeOut 0.2s) / **34-2** 
- **34-5** `NebulaProgressBand`(**Animatable 보간** Canvas — blob/잔별 배치 Android 동일) / **34-7** `ChatStardust`(시드/주기 동일).
- **닉네임 20자 클램프(iOS)**: `ProfileScreen` — alert TextField 는 화면 레벨 `.onChange(of: nicknameDraft)` 로 선차단 + 저장 시 prefix.
- ⚠️ 주의: iOS 장식 Canvas 는 전부 `TimelineView(.animation)` + `allowsHitTesting(false)`; 크리스탈은 매 프레임 파편 렌더 금지 → `StarCrystal.image`(NSCache) 재사용.
- **남은 iOS TODO(기존 유지)**: 마커 스파클(궤도/개수 티어), 공유카드 편집+인스타 스토리, 지도 야경 커스텀 스타일(demotiles), 설정 음량 슬라이더 별 thumb.

---

## 8.38 크리스탈 별 + 스파클 궤도 + 공유카드 편집 확장 + 겹친별 배경 + 이미지 고속화 + 친구 스크린 (Android BUILD SUCCESSFUL 2026-07-12, 테스트 대기)
"디자인 전문가" 라운드 + 추가 2건(이미지 로딩, 친구 스크린). Android 전면 구현 — **테스트 완료 후 iOS 패리티 착수(사용자 지시)**.

- **① 별 = 수정 결정(크리스탈) 렌더**: `StarStyle.drawCrystalFill(canvas, type, colorIndex|colors, left, top, sizePx, alpha)` 신설 —
  실루엣(starPath)은 **그대로 clip** 하고 내부만 파편으로 갈라 조각마다 색을 달리함. 2차 피드백("중심에서 일정하게 퍼지는 패턴 금지, 불규칙 + 가운데 볼록")까지 반영한 최종 구조:
  - **불규칙 파편 메시(무패턴)**: 중심은 한 점이 아니라 **불규칙 코어 다각형**(스포크 소멸) + 링 3겹(코어 r 0.09~0.19 / 중간 r 0.24~0.39 / 외곽=clip)
    꼭짓점을 **중간 링 반 스텝 시프트 + 각도 지터 ±스텝 70%** 로 어긋나게 → 엇갈린 삼각형 스트립(경계선이 이어지지 않음). 조각 수 = 1+4n (`facetDensity` n=10~14 → 41~57).
  - **볼록 돔 셰이딩**: 명도 구배(코어 +0.15 → 외곽 −0.09) + 좌상단 치우친 하이라이트 + 실루엣 가장자리 음영(RadialGradient 2장).
  - 색 변주: hue ±24°·채도 0.8~1.2·명도 해시±0.18, 6% "글린트"(백색 55% 혼합) 조각. 옅은 흰 능선은 파편 경계 전체를 Path 1개로 스트로크. 전부 결정론적 해시(같은 별=같은 무늬).
  적용처: 지도 마커(`starBitmap` — 글로우 유지+본체 교체), `StarShapeIcon` 2종(피커/목록/클러스터 카드 전부 자동 반영), 공유카드 히어로별,
  `DiaryStarBox`/`FloatingStatBox`(프로필), 파장 burstStars. ⚠️ 새 별 type 추가 시 `facetDensity` 에 밀도 추가.
- **② 마커 스파클 확대 + 궤도 별 크기 비례** (3차 피드백 "궤도가 너무 크게 돎" → `starVisualRadiusPx` 기반으로 재작업했으나 **4차 피드백으로도 여전히 궤도가 크고 파티클은 작다** → 최종적으로 물리 공식 기반을 포기하고 dp 로 직접 못박는 방식으로 재설계):
  - **궤도 = dp 로 직접 지정(물리 공식 아님)**: `orbitTargetDp(set, sizeMult) = base + growth·ln(sizeMult)` (안쪽 base 5dp/growth 3.2, 바깥 7.5dp/growth 4.6 — 로그 성장이라 sizeMult 1..6.6 전체에서 최대 ~13/19dp 로 폭주하지 않음). `sparkleOrbitOffsetExpression` 이 이 dp 값을 **화면 밀도**(`screenDensity = context.resources.displayMetrics.density`, DiaryMap 컴포저블에서 `remember`)로 스프라이트 px 로 환산 후 스파클 자신의 icon-size 배율로 나눠 offset-unit 산출 — icon-offset 이 icon-size 와 같은 스프라이트 픽셀 공간에서 정의되고 최종적으로 함께 밀도로 나뉘어 표시되므로, 이렇게 하면 **기기 밀도와 무관하게 항상 목표 dp 반경**으로 보인다. (이전 `starVisualRadiusPx` 시도는 별 렌더 공식[near/far·pulse·MARKER_SIDE_PX]을 그대로 따라가려다 오히려 스파클 하나가 별 자체만큼 커지는 문제가 있었음 — 폐기.) 7개 sizeMult 티어 step 은 유지.
  - **큰 별 = 파티클 모양도 그 별을 닮음**: `sparkleStarBitmap(type,colorIdx)`(32px, `drawCrystalFill` 미니 크리스탈) 신규 — 마커 아이콘 등록 루프에서 `sparkleStarIconId(type,color)` 로 함께 addImage. sparkle 레이어의 `iconImage` 를 `switchCase(sizeMult ≥ SPARKLE_BIG_STAR_THRESHOLD(1.75), get("sparkleIcon"), literal(SPARKLE_ICON_ID))` 데이터 주도 표현식으로 — 합쳐진/인기 별(1.75배 이상)은 흰 4꼭지 대신 자기 모양·색의 미니 크리스탈이 돈다. `diaryFeature` 에 `sparkleIcon` 프로퍼티 추가.
  - **파티클 크기**: 기본 배율(`sparkleSizeBase`) 0.46/0.34 → **0.90/0.68**(4차 피드백 "너무 작음"으로 약 2배 상향) + sizeMult 지수 `SPARKLE_SIZE_POW=0.8`(`Expression.pow`) 로 큰 별 곁에서 더 커짐.
  - **개수도 별 크기에 따라 증가(5차 피드백)** — `SPARKLE_SETS=3`, 세트별 등장 최소 sizeMult(`sparkleSetMinSize`):
    set 0(안쪽 궤도, 항상) → set 1(바깥 역방향 궤도, **1.6 이상**) → set 2(**위성**, **2.6 이상**).
    즉 작은 별 1개 / 좀 큰 별 2개 / 더 큰 별 2개 + 위성. 게이팅은 레이어 추가/삭제 없이 `sparkleOpacityExpression`(iconOpacity 에 `step(sizeMult)` 0/1 게이트 곱)으로 데이터 주도 처리.
    **위성(set 2)** = 별이 아니라 **set 1 파티클을 부모로 삼아 그 주위를 도는 주전원**(부모 궤도 벡터 + `satelliteOrbitDp`(3.2dp+1.5·ln) 짜리 빠른(3.4rad/s) 소궤도). 항상 흰 4꼭지·작게(`sparkleSizeBase(2)=0.42`) 유지해 "달"로 읽히게.
  - 궤도 오프셋 함수는 세트별 dp 벡터를 받는 범용형(`sparkleOffsetExpression(set, zoomFactor, density, dpAt)`)으로 일반화 — 위성의 주전원 합성 좌표도 같은 경로로 처리.
  - 부유(floatDy)는 iconTranslate 유지. `sparkleZoomFactor` = sparkleSizeExpression 줌 보간의 코드 미러(수정 시 함께).
- **③ 공유카드 지도 안 보임 원인 = MapTiler 정적 지도 API 403**: 이 키/플랜에서 `/maps/{style}/static/...` 전 스타일 403(타일 엔드포인트는 정상 — curl 로 확인).
  → `fetchRegionMap` 을 **래스터 타일 스티칭**으로 교체: dataviz-dark z4 타일(512px 셀) 2×2 를 웹 메르카토르 전역픽셀 기준으로 이어붙여 좌표 중심 512px 비트맵 생성(날짜변경선 x 래핑, 극지 클램프, 전부 실패 시 null).
- **④ 공유카드 편집 확장**(`ShareCardOptions` 확장 — 기존 필드 유지):
  - 별 크기 슬라이더 0.6..1.6 → **0.25..2.2**(렌더 클램프 0.2..2.5).
  - **제목/위치/날짜 자유 배치**: `titleX/YFrac·locationX/YFrac·dateX/YFrac`(앵커=요소 중심) — 렌더가 각자 위치에 그림(하단 고정 플로우 제거).
  - **내 다이어리에서 별 가져오기**: `ExtraStar(type,colorIndex,x,y,scale)` 리스트 — `observeMyDiaries(uid).first()` 에서 (모양×색) 중복 제거 후 그리드 피커, 프리셋 위치 순환 배치. 히어로별 아래 레이어로 후광+크리스탈 렌더.
  - 에디터: 드래그 시작점에서 최근접 요소 히트테스트(`hitTarget` — 추가별 0.09/제목 0.11/위치 0.09/날짜 0.08/무대 0.30, 정규화 반경) 후 델타 이동.
    탭 = 추가 별 선택(점선 링 표시) → 전용 크기 슬라이더+삭제 버튼.
- **⑤ 겹친 별 카드(StarClusterScreen)**: **스크린 배경 = 친구 스크린과 동일**(mydiary_bg + 검정 0.82 Darken 틴트),
  **카드 각각의 배경 = 공유카드 프레임**(`assets/share_card_bg.webp` Crop + 세로 스크림, 사용자 피드백으로 1차본의 "스크린 배경 교체"에서 정정).
  **좌상단 뒤로가기**(`onBack` — NavGraph `navigateUp`) 추가. 카드 썸네일 512px 다운샘플. 카드 테두리(별색 border) 는 3차 피드백으로 제거.
- **⑥ 이미지 로딩 고속화**: `StaryApplication : ImageLoaderFactory` — 전역 Coil 로더(**respectCacheHeaders(false)** ← Firebase Storage 의 보수적 Cache-Control 무시하고 항상 디스크 캐시 = 재방문 즉시 표시, 메모리 25%+디스크 256MB, crossfade 120ms, GIF 디코더 포함).
  `core/ui/ThumbAsyncImage`(다운샘플 요청 공용) — 친구 아바타 96px, 클러스터 카드 512px, 프로필/타인 프로필/마이 프사 256~384px 적용. `GifImage` 는 전용 로더 제거(싱글턴 재사용).
- **⑦ 친구 스크린 메신저형 개편**: 친구 행에서 채팅/삭제 버튼 제거 → [사진 52dp(텍스트 2줄보다 조금 큼)] [이름 / "마지막 채팅 · 상대시간(RelativeTime)"] [우측 **미읽음 파란 점**(0xFF4C8DFF)]. **행 탭=채팅, 사진 탭=프로필**. 친구 삭제 UI 는 현재 진입점 없음(요청에 따라 제거 — `vm.remove` 는 유지).
  - 미읽음 판정: `observeMyChats` 메타(updatedAt/lastSenderId) × **`core/util/ChatReadStore`**(신설, SharedPreferences+mutableStateMap — chatId→마지막 열람 시각, 기기 로컬). ChatScreen 이 열람 중 `markRead`(messages.size 변화마다), 행 탭 시에도 즉시 markRead.
- **strings(ko/en/ja)**: `share_edit_import_stars/pick_star/no_stars/extra_star_size`, `friend_no_chat_yet`, `share_edit_hint` 문구 갱신.
- Android 테스트 완료 → push(`fbf7470`). iOS 패리티는 아래 8.38-iOS.

---

## 8.38-iOS 패리티 — 크리스탈 별 / 30m 머지·겹친별 카드 / 친구 메신저형 행 / 이미지 캐시 (CI(macOS) BUILD SUCCESS `a173f7e`, 2026-07-12)
8.38 Android 라운드의 iOS 반영(§1.5). 커밋 `a173f7e` — **iOS CI(ios.yml) 그린 확인 완료**.

- **크리스탈 별**: **`Core/StarCrystal.swift` 신설** — Android `StarStyle.drawCrystalFill` 포팅.
  실루엣은 `StarShape` 그대로 clip(even-odd) 하고 내부만 불규칙 파편 메시(코어 다각형 + 어긋난 링 3겹) + 볼록 돔 셰이딩.
  **해시식(`sin(seed*12.9898)*43758.5453` fract)·상수·`facetDensity` 가 Android 와 동일** → 같은 별이 두 플랫폼에서 같은 무늬로 보인다(수정 시 양쪽 함께).
  HSL 변환은 `UIColor.hsl` / `UIColor(hsl:)`(androidx `ColorUtils` 공식과 동일)로 자체 구현.
  적용: `StarView`(→ 상세/목록/업로드/프로필/로그인/공유카드 전부 자동), 지도 마커(`StarImageRenderer` — 글로우 + 크리스탈 본체, 캔버스의 78%가 본체),
  프로필 부유 별(`FloatingStatBox`). ⚠️ **`Canvas(symbols:)` 의 심볼 자리엔 Canvas 를 중첩하지 말 것**(렌더 불안정) →
  `StarCrystal.image(type:colorIndex:size:)`(NSCache) 로 비트맵을 구워 `Image(uiImage:)` 로 넘긴다.
- **30m 지오 머지 + 겹친 별 카드(iOS 최초 구현)**:
  - **`Core/StarMerge.swift` 신설** — 대표 우선순위(`precedes` = 좋아요↓ → 오래된 순 → id)·`sizeMult`(좋아요 합산 × 개수 보너스, 상수 Android 동일)로 30m greedy 머지.
  - `MapLibreView`: 어노테이션을 **머지 단위**로 생성(`DiaryAnnotation(merged:)` 이 `members`/`sizeMult` 보유).
    마커 이미지 크기 = `40pt × sizeMult`(0.25 단위 **양자화** → `imageKey` 로 재사용, 최대 2.5배) — 합쳐질수록 큰 별.
    콜백을 `onTapDiary(Diary)` → **`onTapStar([Diary])`**(멤버 전체)로 변경.
  - **`Features/Map/StarClusterView.swift` 신설** — 헤더(겹친 별 아이콘+개수) + `TabView(.page)` 스와이프 카드 + 인디케이터, 좌상단 뒤로가기.
    **카드 각각의 배경 = 공유카드 프레임**(`Resources/share_card_bg.webp` — Android assets 와 같은 파일을 iOS 번들에 복사, `ShareCardBackground.image` 로 1회 로드).
    `MapScreen` 이 멤버 2개 이상이면 `ClusterSelection` 시트로 열고, 카드 탭 → 시트 교체(0.35s 뒤 Detail).
  - ⚠️ **마커 곁 스파클(궤도/위성)은 iOS 미구현** — iOS 지도는 어노테이션 이미지 기반이라 per-frame 레이어 애니메이션이 없다(GeoJSON 소스 + SymbolLayer 로 재작성해야 가능). iOS TODO.
- **친구 스크린 메신저형 행**: `FriendsScreen` 행 = [사진 52pt] [이름 / "마지막 채팅 · 상대시간(`RelativeTime`)"] [미읽음 파란 점(0xFF4C8DFF)]. 행 탭 = 채팅(기존 NavigationLink 유지).
  - **`Data/ChatReadStore.swift` 신설**(UserDefaults + `@Published` — Android `core/util/ChatReadStore` 패리티, 기기 로컬 기준).
    `ChatScreen` 이 onAppear/메시지 증가/onDisappear 마다 `markRead`, 친구 행 탭 시에도 즉시 markRead(`simultaneousGesture`).
  - `FriendsViewModel` 에 채팅방 메타 구독 추가(`chatSummaries: [친구uid: ChatSummary]`) — `InAppWatcher` 와 같은 쿼리지만 용도가 달라 별도 리스너.
- **이미지 로딩 고속화**: **`Core/ImageCache.swift` 신설** — 디스크 256MB `URLSession`(**`.returnCacheDataElseLoad`** = Firebase Storage 의 보수적 Cache-Control 무시, Android `respectCacheHeaders(false)` 대응) + `AvatarThumbView`(다운샘플 표시, Android `ThumbAsyncImage` 대응).
  `AvatarThumbCache` 캐시 키에 **크기 포함**(같은 URL 을 다른 크기로 써도 흐려지지 않게) + 세션 교체.
- **공유카드**: 배경을 Android 와 같은 밤하늘 이미지(`share_card_bg.webp`)로 통일(+하단 스크림, 실패 시 기존 절차적 하늘로 폴백). 별은 `StarView` 경유라 크리스탈 자동 반영.
  ⚠️ **공유카드 편집 다이얼로그(드래그 배치·내 별 가져오기·지역 지도)는 iOS 미구현** — Android 전용. iOS TODO.
- **`LocaleManager`**: `clusterHeader`(`%d`)/`clusterHint`/`clusterOpen`/`friendNoChatYet` 추가(ko/en/ja).
- **iOS 남은 TODO**: 마커 스파클(궤도/개수 티어/위성), 공유카드 편집 다이얼로그, 지도 야경 커스텀 스타일(여전히 `demotiles` placeholder), 닉네임 20자 클램프.

---

## 8.37 입력 제한 + 30m 별 합치기 + 공유카드 편집 + 인스타 링크 + 마커 스파클 (Android BUILD SUCCESSFUL 2026-07-12, 테스트 대기)
사용자 5건 일괄 요청("테스트 이전까지 한번에"). Android 전면 구현, iOS 는 상수/입력 제한만 패리티(나머지 TODO).

- **① 입력 글자수 제한**: `StaryConfig` 에 `DIARY_TITLE_MAX_LEN 30 / DIARY_CONTENT_MAX_LEN 2000 / COMMENT_MAX_LEN 300 / CHAT_MESSAGE_MAX_LEN 500 / NICKNAME_MAX_LEN 20` (+iOS `AppConfig` 동기화). 적용: `UploadScreen`(제목/내용, supportingText 로 `n/max` 카운터), `DetailScreen`(수정 다이얼로그 + 댓글 입력), `ChatScreen`(메시지), `ProfileScreen.NicknameEditDialog`(기존 20 하드코딩 → 상수). 초과분은 `take()` 로 잘라 선차단. 하루 10개 업로드 제한은 기존(8.31) 유지. iOS: Upload 제목/내용·Detail 댓글·Chat 입력에 `.onChange` 클램프(닉네임 alert TextField 는 미적용 — TODO).
- **② 30m 별 합치기(지오 머지)**: `StaryConfig.STAR_MERGE_RADIUS_M=30`. `DiaryMap.mergeByProximity` — 우선순위(`MERGE_PRIORITY` = 좋아요 내림차순→오래된 순→id) 1위를 앵커로 30m 내 흡수(greedy). **대표의 모양/색**으로 렌더, 크기/밝기 = `mergeSizeMult` = `likeSizeMult(멤버 좋아요 합산)` × `clusterSizeBoost(개수)`. 파이프라인: 지오 머지(줌 무관) → 기존 화면 클러스터링(4dp) 앞단에 삽입, 별자리 라인도 머지 반영. 머지 그룹은 `mergeGroupsState`(대표 id→멤버들)로 클릭 리스너에서 참조.
  - **열람 연출**: 파장(`DiaryOpenWarp`)에 `burstStars`(멤버 별 모양/색 최대 12개) — 파장 중심에서 황금비 시퀀스로 작은 별 파티클이 퍼지며 페이드.
  - **카드 뷰어**: 파장 후 그룹 2개 이상이면 `NavRoute.StarCluster(ids=","연결 문자열)` → `StarClusterScreen`(신설, feature/diary/screen) — 헤더(겹친 별 아이콘들+개수) + `HorizontalPager` 카드(썸네일 또는 큰 별/제목/#순위·날짜/**하트·댓글 수만**) + 인디케이터, 카드 탭→`navigateToDetail`. 정렬 = 대표 선정과 동일 우선순위. 데이터는 `DiaryCache`→`getDiaryById` 폴백.
  - 배선: `DiaryMap(onClusterClick)` → `MainListScreen(onOpenCluster)` → NavGraph. `MainScreen.localizedTitle` 에 `nav_star_cluster` 추가.
- **③ 별 마커 곁 마이크로 스파클**: `SPARKLE_SETS=2`(안쪽 13dp/바깥 19dp 역방향 타원 궤도) × PHASE_GROUPS 레이어(`diary-sparkle-<set>-<group>`, DIARY_SOURCE 재사용). `sparkleBitmap`(24px 4꼭지 흰 별), 줌 11 이하 숨김(`sparkleSizeExpression`). 기존 50ms 애니메이션 루프에서 `iconTranslate`(궤도+별 부유 floatDy 동기)/`iconOpacity`(트윙클×alpha) 갱신 — 추가 소스/GeoJSON 없음.
- **④ 공유 카드 편집**: `ShareCardHelper` 리팩토링 — `ShareCardOptions(title/showMap/showLocation/showDate/stageXFrac/stageYFrac/starScale)` + `ShareCardAssets`(`prepareAssets` 1회 로드: 역지오코딩+정적지도, `release()` 로 해제) + `renderCard` public(자산 재활용 안 함). `ShareCardEditorDialog`(**ShareCardEditor.kt 신설**): 전체화면 다이얼로그 — 프리뷰 **드래그로 별(+지도 무대) 위치 이동**(델타 기반, x 0.15..0.85 / y 0.15..0.52 클램프), 카드 제목 수정, 지도/위치/날짜 토글 알약, 별 크기 슬라이더(0.6..1.6), 하단 [스토리 공유]/[이미지 공유]. 렌더는 60ms 스로틀로 Default 디스패처 재렌더. `DetailScreen.ShareDiaryButton` 이 드롭다운 대신 편집 다이얼로그를 연다. ⚠️ dex VerifyError(레지스터 한계) 재발 방지 — 편집 UI 는 반드시 별도 파일 유지.
- **⑤ 인스타 스토리 링크 안 뜨던 원인**: **`secrets.properties` 에 `INSTAGRAM_APP_ID` 미설정** → 빈 `source_application` 이면 인스타가 `content_url`(링크스티커)을 조용히 무시. 수정: 빈 값이면 두 extra 자체를 생략 + **항상 공유 링크를 클립보드에 복사**하고 시스템 토스트(`share_story_link_copied`, 앱이 배경으로 가도 보이게 StaryToast 아님)로 "스토리 '링크 스티커'로 붙여넣기" 안내. **근본 해결(사용자 액션)**: developers.facebook.com 에서 앱 생성 → 앱 ID를 `secrets.properties` 의 `INSTAGRAM_APP_ID` 로 설정(+Meta 측 앱 활성화). 그래야 자동 링크스티커가 붙는다.
- **strings(ko/en/ja)**: `share_story_link_copied`, `share_edit_title/hint/field_title/show_map/show_location/show_date/star_size`, `nav_star_cluster`, `cluster_header/hint/open`.
- **iOS TODO(후속)**: 지도 30m 머지 렌더+클러스터 카드 뷰어(iOS 지도는 아직 데모 스타일 placeholder 단계), 공유 카드 기능 자체(iOS 미구현), 마커 스파클, 닉네임 20자 클램프.

---

## 8.36-iOS CI 그린 복구 + BGM 설명 문구 정정 (CI(macOS) BUILD SUCCESS `0367fcb`, 2026-07-08)
- **7/7부터 iOS CI 레드**였던 원인 1건: `SettingsScreen.swift:24` — BGM 토글 `toggleRow(...)` 호출에 필수 `description` 인자 누락(컴파일 에러).
  → 최초엔 `L10n.settingsBgmDesc` 키를 추가해 문구를 채워 넣었으나(`25232a6`), **BGM 설명 문구는 사용자가 의도적으로 지운 것**이었음 —
  잘못된 방향이라 되돌림. **올바른 수정**: Android `ToggleRow`가 원래 `description: String? = null`(옵셔널)로 설계돼 있던 것과 동일하게
  iOS `toggleRow`도 `description: String? = nil`로 바꾸고, BGM 행은 description 을 아예 전달하지 않도록 정정(`L10n.settingsBgmDesc` 케이스 삭제).
  Android 쪽도 `settings_bgm_desc` 문자열 리소스(ko/en/ja)와 `ToggleRow` 호출의 description 전달을 함께 제거해 완전히 지움.
- `BoomerangCaptureView.swift` 완료 버튼 문구 "이 장면 사용" → **"자르기 완료"** (Android `boomer_use` 패리티, `13c674b`).
- CI 로그 확인 요령(이번에 확립): 레포 public → `git credential fill` 토큰으로 GitHub API 인증 →
  `/actions/workflows/ios.yml/runs?head_sha=<full sha>` 로 상태, `/actions/runs/<id>/logs`(zip) 로 에러 검색. `gh` CLI 는 미설치.
  ⚠️ 익명 API 는 60회/시 제한 — 폴링은 반드시 인증 토큰으로.

---

## 8.36 Seedance 2.0 광고 마스터 기획 + 광고 자산 정리 (docs·자산만, 코드 변경 없음)
앱 광고 영상 제작 라운드. 마스터 문서 = **`references/stary 광고 씬 모음/SEEDANCE2_광고기획.md`** (커밋 `db12725`) —
구 `storyboard.md`(씬 서사) + `STARY_commercial_master.md`(Veo용)를 Seedance 2.0 전용으로 계승·통합.

- **기획안 4종**: A「별이 된 기억」30s 감성 필름(★메인) / B「지구는 일기장」글로브 스케일(→A 엔딩 S8로 흡수) /
  C「그 자리의 별」15s 세로 티저(★보조, 릴스·쇼츠) / D「3초의 마법」부메랑 기능 범퍼(추후 리타게팅).
- **문서 구성**: §1 Seedance 2.0 스펙(이미지 9장·@멘션 문법·4~15초·첫프레임 비율 따라감·실사 얼굴 정책) /
  §3 메인 S4~S8 i2v 프롬프트(복붙용) / §4 세로 티저 V1·V3·V4 프롬프트 / §5 카피(한/영, 후반 편집에서만 — AI 한글 생성 금지) /
  §6 신규 생성 필요 이미지 4장 프롬프트(글로브·세로 3장) / §7 BGM 매핑(앱 `raw/bgm_*.mp3` 그대로 = 사운드 브랜딩) / §8 워크플로·문제해결.
- **광고 자산 현황**(`references/stary 광고 씬 모음/`):
  - `scene1~8.png` 스틸 확보 완료. **scene7 = 인물 없는 와이드 야경 플레이트로 교체**(S7 풀백 프레이밍 참조용),
    **scene8 = S7 첫 프레임**(손 든 남자 + 도시에서 솟는 골드 파티클 → 별). scene6-1~4·scene8 은 3:2 → 16:9 크롭 필요(§3.0).
  - `영상/scene1~3.mp4` — S1~S3 생성 완료 컷(잔여 S4~S8 은 §3 프롬프트로 생성).
  - `references/은하수.jpg` — 글로브 은하수 레퍼런스(8.35 라운드에서 사용, 이번에 커밋).
- **2차 개정(같은 날)**: 사용자가 **scene5(눈)·scene8(손+파티클) 스틸 삭제**(품질 불만) →
  ① 전 클립 **5초 단위 생성**으로 타임라인 재편(S6 몽타주 8초 1회 → **S6a/S6b 5초×2**, 최종 30초 유지)
  ② S5 = 프롬프트-온리 t2v, S7 = scene7(플레이트)+scene1(인물 identity) 만능 레퍼런스 모드(첫 프레임 없음)
  ③ S5→S6 전환 개편: 화이트 블룸 → **눈 속 노란(골드) 별로 푸시인, 마지막 프레임 = 금빛 글로우** →
    그 프레임을 캡처해 S6a 첫 프레임으로 쓰는 **마지막 프레임 릴레이**로 "별 안에서 회상이 열리는" 연속 샷.
- **다음 단계**: ① S4→S5→S6a→S6b→S7→S8 순서로 클립 생성(릴레이 순서 고정) → ② 30s/15s 편집 → ③ 세로 티저(신규 이미지 §6-②~④ 먼저) → ④ 범퍼 D.
  앱 실화면 촬영 리스트(글로브 최소 줌 30s·별 탭→다이어리·부메랑 플로우)는 §8-6.

---

## 8.35 글로브 유성·은하수 3D 연출 전면 개편 (커밋 `6e54883`, Android BUILD SUCCESSFUL, 테스트 대기)
"3D 디자인 전문가" 요청 라운드 — 안드 `GlobeRenderer.kt` + iOS `GlobeScreen.swift` 동시 반영.

- **유성 개편**:
  - **곡선 낙하**: 직선 → 경로 수직(화면면, dir×ẑ) 2차 휨 `p(s)=p0+dir·len·s+perp·bend·s²`, bend=len×(0.10~0.24), 부호 랜덤. 꼬리 스프라이트가 경로를 따라 샘플되어 **궤적 전체가 휜다**. 접선 방향 정렬(iOS 는 커스텀 액션에서 매 프레임).
  - **화려한 반짝임**: 머리=정광, 꼬리=본색→다음 팔레트색 2색 그라데이션 + 트윙클(mode=1). iOS 는 머리 밝기 고주파 떨림(transparency 변조).
  - **잔류 궤적**: 안드 `meteorSparks`(pos/rgb/size/birth/life/phase 파티클 리스트, 초당 85개 방출·최대 240, 본색↔보조색↔백색 랜덤 혼합, (1-age)² 잔광 + 트윙클, **유성 소멸 후에도 1~2초 남아 반짝임**) / iOS `SCNParticleSystem`(isLocal=false 월드공간 방출 → 지나간 자리에 잔류, colorVariation 색 반짝임, opacity 수명 곡선, 방출 종료 후 2초 뒤 노드 제거).
- **은하수 실사화**(자료 조사: 골든 코어·Great Rift·mottled 스타클라우드·H-II 핑크·색 온도 구배가 실제 은하수 사진의 5대 특징):
  - **Great Rift**: additive 라 어둠을 직접 못 그림 → 균열 자리(구불구불한 중심선 ±폭, 핵 쪽 절반에서 강함)의 별·유광 밝기를 가우시안 감쇠(`riftAtten`) → 주변이 빛나는 만큼 상대적으로 어두운 균열로 보임.
  - **은하핵 벌지**: 골든·오렌지 글로우 22장 + 대형 심장 후광 2장, 핵 근처 별 밀도(채택-기각)·띠 두께(×1.8) 증가.
  - **질감**: 잔별 1500→2600 + 전경 밝은 별 26 + 스타 클라우드 150(얼룩 `patch()` 사인 곱으로 mottled) + 연속 유광 리본 72(끊김 방지).
  - **색**: `bandTint(warm)` — 핵(골든 r0.84+0.19w …) → 외곽(청백), H-II 핑크 반점 9개.
- ⚠️ 성능: 전부 정적 VBO(1회 빌드)/정적 텍스처라 프레임 비용 증가는 유성 활성 시 스파클 240개 수준 — 미미. iOS 는 push 후 CI 검증.
- **레퍼런스 재작업 라운드(`2994bcc`)** — 사용자가 `references/은하수.jpg`(우유니 스타일 핑크 은하수)와 `references/zodiac.avif` 정합을 요구:
  - **은하수 = 핑크·마젠타 빛의 강**(실사풍 회갈색 → 레퍼런스 판타지 스타일): ①백열 코어 라인(96, 백핑크 심줄) ②마젠타 리본(150, 1.0/0.30/0.62) ③바이올렛 외곽 글로우(110) ④골드 응집(30, 핵 쪽 가장자리) ⑤시안 가장자리 미광(26) ⑥잔별 3200(청백68/핑크22/골드10%) ⑦전경 밝은 별 40. 암흑 균열은 감쇠 0.72로 유지(리본 속 어두운 결). 배경 셸 460/900/1400 + 인디고·블루 워시 3종.
  - **12궁 별 단위 재배치**: zodiac.avif 를 침식(erosion) 기반 별점 자동 검출 + 4배 확대 판독으로 **별 하나하나 좌표·연결선**을 추출, [-1,1] 정규화 좌표로 안드 `addConstellation`/iOS `constellation` 전면 교체(양4·황소12·쌍둥이13·게5·사자9·처녀14·천칭7·전갈14·사수22·염소9·물병11·물고기17성). ⚠️ **코드 주석에 "임의 수정 금지(레퍼런스와 대조)" 명시** — 판독 스크립트·크롭은 scratchpad(세션 소멸), 재판독 시 같은 방법(침식 검출→확대 육안 대조) 사용.
- **원근/거리 라운드(`54d0e83`)**: 렌더 범위 검증(far plane 100, 최원거리 ≈50 — 여유 2배). 먼 셸일수록 별 크기 축소(안드 sizeBase 0.022/0.026/0.032, iOS sizeMul 1.15/0.95/0.75 — 과거엔 먼 셸을 크게 줘 원근 상쇄), 은하수 잔별 최소 크기 0.032, **별자리 반지름 36→42**(모양 유지, iOS 는 스케일·점·선 14% 축소+감광).
- **유성 화면 횡단 + 뱃길 파장 라운드(`4c814f7`)**: 궤적을 화면 기준으로 — 좌우 한쪽 **화면 밖 상단 ~10%** 높이에서 출발해 **반대쪽 화면 밖 하단 50~90%** 로 사선 횡단(좌↔우 랜덤), 끝점 고정 아치(`p0+dir·len·s+perp·bend·4s(1-s)`), **중간 소멸 없음**(꼬리까지 u=1.2 퇴장 후 정리). 잔류는 **5~10초 파장(wake)**: 경로 양옆 V자 드리프트 + 경로 위상 물결(`sin(waveArg−2.2t)`) + 유광70%/반짝이30% 2계층(안드 스파크 13필드: pos·vel·rgb·size·birth·life·waveArg). iOS 파티클 수명 7.5±2.5 + 크기 성장 0.7→1.7 + 수명 곡선, 방출 종료 후 10.5초 대기 정리.
- **유성 중력·꼬리 + 별자리 축소 + 트레일 공전 라운드(`3a1af11`, 사용자 2차 피드백)**:
  - 포물선 휨 부호가 랜덤이라 절반은 "중력이 반대로 작용"하는 것처럼 보였던 버그 수정 — perp 벡터를 **항상 +y(화면 위쪽)** 로 고정(초반 완만→후반 급락하는 실제 포물선 모양). iOS 도 동일(`if py < 0 { px=-px; py=-py }`).
  - 꼬리 길이 2배: 안드 `METEOR_TAIL_FRAC 0.15→0.30` + `METEOR_SPRITES 22→34`, iOS `streak 0.17~0.25→0.34~0.50`. iOS 퇴장 여유 `sMax`를 `1+streak/travel+0.08`로 꼬리 비율에 비례하게 재계산(꼬리까지 화면 밖으로 완전히 나갈 때까지 유지).
  - 별자리 축소·연하게: 별 크기 -30%(안드 0.20~0.28→0.14~0.196 / iOS 반지름 2.2→1.6), 연결선 밝기 -50%(안드 0.30→0.15 / iOS 0.26→0.13).
  - **트레일 자체 공전 — 추가했다가 롤백(`6b1950e`)**: `Trail.orbitAxis/orbitDegPerSec` + 안드 `drawTrail`의 `trailMvp = vp·model·rotate(...)` + iOS `trailNodes()`의 `SCNAction.rotate(repeatForever)`로 지구 자체 회전과 별개의 공전을 구현했으나, 사용자가 "지구 궤적은 이전 버전으로 롤백"을 요청 → 다시 `uMVP=vp·model`(지구 자체 회전에만 종속)로 원복. 트레일은 지구를 드래그하거나 3초 이상 무입력 자동 회전이 걸릴 때만 같이 돈다(기존 동작). 유성 중력 방향 고정/꼬리 2배, 별자리 축소·연하게는 유지.

---

## 8.34 4건 라운드 — 음악·칭호 다국어화 / 부메랑 3초 움짤 / 기간별 필터 / 프사·이름 현재값 (테스트 완료·push)
안드+iOS 동시 반영(§1.5). 커밋 2건: `9fe4bbd`(안드), `e3f63ea`(iOS).

- **① 음악 이름·칭호 다국어화(언어 변경 시 적용)**:
  - 트랙/업적 정의(id·판정·한국어 원문)는 공용 데이터로 유지하고 **표시할 때만** id → 로케일 해석.
  - 안드 `core/util/LocalizedNames.kt`(음악 6 + 칭호 19 매핑 → `strings.xml` ko/en/ja `music_*`/`title_*` 키) — 적용: ProfileScreen(장착 칭호+히든 버블 라벨)/UserProfileScreen/AchievementsScreen(칭호 행·히든 행·장착 토스트)/MusicScreen(트랙명+잠금 힌트 업적명)/HiddenAchievementWatcher(달성 팝업).
  - iOS `Core/LocalizedNames.swift`(같은 매핑 내장 ko/en/ja 튜플, `LocaleManager.effectiveLanguage` 기준) — 같은 5개 화면 적용.
  - ⚠️ **새 트랙/칭호 추가 시 안드 strings.xml 3벌 + iOS LocalizedNames 매핑을 함께 추가**해야 함. 업적 이름(보상형)·조건 문구는 기존 방침대로 비번역(후속 대상).
- **② 부메랑식 3초 움짤(GIF) 커스텀 촬영** — "내 파일에서 3초 영상 선택" 완전 대체:
  - 안드: `BoomerangCaptureScreen.kt`(CameraX Preview+ImageAnalysis RGBA, 전체화면 오버레이 — 하단 좌측 전환/가운데 셔터, LIVE→CAPTURING→PROCESSING→REVIEW(다시찍기/사용)). 12프레임×125ms(≈1.5초)버스트 → `BoomerangHelper`(회전/전면미러/4:3 센터크롭/400×300 다운스케일, 정→역 22프레임) → `GifEncoder.kt`(자체 GIF89a: 6×7×6 고정 팔레트+Bayer 디더+LZW(ppmtogif 포팅), 무한루프, 0.13s/프레임 ≈ 2.9초).
  - 업로드: `ImageUploadHelper.uploadGifResult` → **`diary_images/{uuid}.gif`**(contentType image/gif → 기존 storage 규칙 image/* 통과, 규칙 재배포 불필요). URL 은 **기존 `videoUrl` 필드 재사용**(스키마 무변경, `.gif` 포함 여부로 판별 `isGifUrl`).
  - 표시: `core/ui/GifImage.kt`(coil-gif 디코더 로더) — 업로드 미리보기(파일)+DetailScreen(.gif 분기, 구버전 mp4 는 기존 LoopingVideoPlayer 유지).
  - 의존성 추가: `androidx.camera:*:1.4.1`(core/camera2/lifecycle/view), `io.coil-kt:coil-gif:2.6.0`. `VideoHelper.kt` 삭제(파일 영상 검증 불용).
  - iOS: `BoomerangCamera.swift`(AVCaptureSession vga640x480+VideoDataOutput BGRA, connection 에서 portrait/전면미러 처리, 4:3 크롭+400px, **renderer scale=1 필수**(레티나 3배 용량 방지)) + `BoomerangCaptureView.swift`(fullScreenCover UI) + ImageIO GIF 인코딩(`UTType.gif`+LoopCount 0) + `GifImageView`/`RemoteGifView`(animatedImage) + `ImageUploader.uploadGif`. UploadScreen 영상 PhotosPicker 제거→촬영 버튼, DetailScreen `.gif` 분기.
  - ⚠️ Kotlin 함정: **KDoc 블록 주석 안의 `image/*` 문자열이 중첩 주석 시작(`/*`)으로 파싱**돼 파일 뒷부분 전체가 주석 처리됨(Unclosed comment) — 주석에 `xxx/*` 패턴 금지.
- **③ 필터에 기간별 보기**: 안드 `MainListScreen` 스피드다이얼에 "기간별 보기"(Schedule 아이콘) → 다이얼로그(전체/오늘/최근 7·30일/1년, 라디오). `periodDays: Int?`(null=전체, 0=오늘 자정 이후, N=지금-N일) → `filteredDiaries` 컷오프. "전체보기" 가 기간도 리셋. **기존 필터 라벨/친구선택 다이얼로그 하드코딩도 리소스화**(`filter_*`/`period_*` ko/en/ja). iOS: MapScreen 우상단 칩 아래 기간 Menu + ListScreen 툴바 Menu(L10n `filterPeriod`/`period*` 6키).
- **④ 프사·이름 현재 상태로 표시(스냅샷 제거)**: 다이어리/댓글 문서의 userName 스냅샷 대신 **표시 시점에 `users/{uid}` 현재값**.
  - 안드 `core/util/UserDirectory.kt`(uid별 스냅샷 리스너 1개 → `mutableStateMapOf` 캐시, `rememberCurrentUserName/Photo`) — DetailScreen 작성자 이름(익명 제외)+댓글 이름/아바타(기존 UserRepository 단발 조회 대체).
  - iOS `Data/UserDirectory.swift`(@MainActor ObservableObject 동일 구조) — DetailScreen 작성자/댓글/CommentAvatar. (FriendsScreen 의 ProfileImageCache 는 그대로.)
  - 참고: UserProfileScreen 은 원래 진입 시 현재값 조회라 무변경. 알림 actorName·채팅 발신자명 스냅샷은 범위 밖(이벤트 메시지).
- **기타**: `androidApp/build.gradle.kts` 의 `minSdk = 26claude` 오타(빌드 불가) 수정.
- **후속 수정 라운드(사용자 1차 피드백, `488908f`)**:
  - **Detail 백스택 1개만**: 알림/배너로 같은 다이어리를 여러 번 열면 Detail 이 겹겹이 쌓여 뒤로가기를 여러 번 눌러야 했음 → `NavRoute.kt` 의 `NavHostController.navigateToDetail(diaryId)`(popUpTo<Detail> inclusive + launchSingleTop)로 5개 진입점(NavGraph 3 + MainScreen 딥링크/배너 2) 통일. iOS 는 시트 기반이라 해당 없음.
  - **촬영 화면 개편**: 타이틀/X/안내 문구 삭제, 프리뷰 **풀스크린**(닫기 = 시스템 뒤로가기, BackHandler). 캡처는 전체 프레임(긴 변 640 작업 해상도)으로 모으고, **촬영 후 ADJUST 단계에서 4:3 프레임에 사진 크롭처럼 드래그/핀치 조정**(좌표 모델 = `ImageCropHelper` 동일) → 확정 시 `BoomerangHelper.cropFrames`(400×300)→GIF 인코딩. 상태 머신 LIVE→CAPTURING→ADJUST→ENCODING. iOS 동일 구조(`cropFrames`+제스처, 단 좌상단 반투명 X 는 유지 — iOS 엔 시스템 뒤로가기 없음). 라벨 "3초 영상 촬영" 통일, `boomer_title/hint/capturing/processing` 문자열 삭제.
  - **기간 필터 동작**: 다이얼로그에서 "전체 기간" 제거, **활성 칩을 다시 탭하면 해제**(안드 스피드다이얼 + iOS 지도 칩/목록 툴바 동일).
  - **댓글 아바타 저화질 고속 렌더**: 안드 Coil `ImageRequest.size(96)` 다운샘플 / iOS `AvatarThumbCache`(CGImageSource 96px 썸네일 + 메모리 캐시, AsyncImage 대체).
- 검증: 안드 `:androidApp:assembleDebug` **BUILD SUCCESSFUL**(후속 라운드 포함). iOS 는 push 후 `ios.yml`(macOS CI) 검증 예정.

---

## 8.33 지도 야경 스타일 전면 개편 + 글로브 오로라 삭제→은하수 격상/유성 추가 (테스트 완료)
사용자가 "지도가 3D 글로브와 분위기 차이가 많이 난다"고 지적 → 지도를 글로브와 이어지는 "위성 야경" 컨셉으로 재작업. 이어서 글로브 쪽도 오로라 제거 + 은하수/유성으로 교체. 여러 라운드에 걸친 시행착오(동적 카메라 틸트·바닥 유리가루 파티클은 시도 후 롤백) 끝에 아래가 최종 상태.

- **지도 스타일(`maplibre_style.json`, Android 전용 — iOS 는 아직 데모 placeholder, 하단 참고)**:
  - 레이어 확장: 기존 background/water/road-major 3개 → landcover(숲/초지)·landuse(도심)·park 미세 톤 텍스처 추가(벡터 타일에 이미 포함된 데이터라 다운로드 증가 없음).
  - 도로 재구성 = "밤의 불빛" 위계: motorway/trunk/primary(앰버 글로우 2겹 + 코어 + 스페큘러 하이라이트 + 흐르는 노란 알갱이 `road-glint`), secondary/tertiary(딤 골드), minor/service(파란 슬레이트였다가 최종적으로 땅 톤에 가까운 어두운 웜 그레이 `#161310→#2E2822`). 전체 밝기를 별 마커보다 낮게 캡해 별이 항상 시선의 정점.
  - `road-glint`(흐르는 빛 알갱이): `DiaryMap.kt` 애니메이션 루프가 `line-dasharray` 위상을 매 틱 흘려 빛이 도로를 따라 흐르게 함. 위상이 한 바퀴 돌아 재생성되는 순간 전후 0.2초씩 삼각 envelope 로 부드럽게 페이드(끊김 방지, `roadGlintOpacityExpression`). 사용자 피드백으로 `minzoom`/페이드 줌 스톱을 11·12·16 → **13·15·17** 로 올려 줌아웃 시 더 빨리 사라지게 튜닝.
  - 비네트 + 저줌 대기 헤이즈: 화면 가장자리 상시 비네트 + 줌 4.4→2.4(글로브 진입줌) 사이 파란 대기가 차오르는 Compose Canvas 오버레이(터치 통과) — 지도→글로브 전환이 한 장면처럼 이어짐.
  - **바닥 유리가루(ground-glints) 파티클은 추가했다가 전량 제거**(사용자가 삭제 요청) — 도로의 유리 질감(하이라이트+글린트)만 유지.
  - 카메라 틸트: 줌 연동 동적 틸트(14~17줌에서 0→42°, `onCameraIdle` 트리거)를 만들었으나 사용자가 롤백 요청 → 최종은 **줌 무관 고정 10° 틸트**(`BASE_TILT_DEG`, 카메라를 세팅하는 5곳에 일괄 적용).
- **별 마커 개선**:
  - 5·6각 별의 직선 스파이크가 4·8각(곡선)과 비교해 투박해 보인다는 지적 → 전부 곡선(quad) 스파이크로 통일(`StarStyle.starPath` / iOS `StarShape.swift` 동기, innerRatio 0.14/0.11로 조정).
  - "별이 지도에 박혀 보인다" → 바닥 빛 웅덩이(`diary-ground-light-N`, 앵커 고정 CircleLayer, 별 색 옅은 원형광)를 각 별 아래 추가. 별은 `iconTranslate` 로 부유하지만 이 빛은 지점에 고정되어 시차가 생겨 "떠 있음"이 읽힘 + 별이 내려올 때 살짝 밝아지는 미세 연출. 부유 진폭도 3→4dp 로 소폭 상향.
- **글로브(`GlobeRenderer.kt` / iOS `GlobeScreen.swift`, 안드+iOS 동시 반영)**:
  - **오로라 커튼 4폭 완전 삭제**(안드 GL 지오메트리/전용 셰이더/팔레트, iOS `auroraNodes`/`auroraTexture` 배선까지 전부 제거).
  - **은하수 격상**: 기존 잔별 560개+헤이즈 10개(거의 안 보이는 수준) → 잔별 1500개(이중 가우시안 두께: 얇은 심+넓은 외곽) + 띠를 따라 끊김 없는 청백 헤이즈 리본 + **은하핵 벌지**(한쪽에 따뜻한 대형 글로우 응집, 그 근처 별이 더 밝고 따뜻한 색) — 뚜렷한 "빛의 강"으로.
  - **유성 신규 추가**: 처음엔 화면 평면(뷰 공간, 고정 깊이)에서 슬라이드하는 방식이었으나 "2D처럼 보인다"는 지적 → **깊이 성분 포함 완전 랜덤 3D 방향의 직선 경로**로 실제 우주공간을 가로지르게 재작업(원근으로 다가오거나 멀어짐). 크기는 최종적으로 초기안의 절반. 방향은 화면상 수직 성분이 항상 0 이하가 되도록 제한해 **위로 올라가는 각도는 완전히 배제**(아래~사선만).
  - **유성 출현 = 확률 스트릭 구조**: 글로브 입장 30초 뒤 첫 판정, 이후 30초마다 25% 확률 판정. 성공하면 낙하 시작 → 낙하가 끝나자마자 대기 없이 곧바로 재판정(운이 좋으면 연속으로 계속 떨어짐), 실패하면 스트릭 리셋 + 다시 30초 대기. 연속 스트릭 중엔 매번 다른 색(기본 청백→주황→초록→핑크→골드→보라 순환, `METEOR_TINTS`/iOS `meteorTints`).
- ⚠️ **iOS 지도(`MapLibreView.swift`)는 아직 MapTiler 데모 스타일(`demotiles.maplibre.org`) placeholder 단계** — 이번 야경 스타일/도로 글린트/틸트/바닥빛/헤이즈는 전부 Android 전용. iOS 에 MapTiler 키 주입 + 커스텀 스타일 번들링부터 필요(기존 TODO에 계속 누적 중). 글로브(오로라 삭제/은하수/유성)는 안드+iOS 양쪽 다 반영됨(별개 렌더러라 패리티 완료).
- Android **BUILD SUCCESSFUL**(라운드마다 확인). iOS는 Windows 컴파일 불가 → push 후 GitHub Actions(macOS) `ios.yml` 로 검증.

---

## 8.32 히든 업적 후속 3건 — 아이콘·어드민 선점 해제·친구 프로필 (테스트 완료)
- **심연의 별(place_trench) 아이콘 교체**: 물방울(`Water`/`drop.fill`) → **물결(`Waves`/`water.waves`)**. 안드 `HiddenIcon.TRENCH` / iOS `HiddenIcon.trench.systemImage`.
- **어드민이 과거 선점한 히든 자동 해제(자가치유)**: 8.31의 어드민 제외 로직이 생기기 **전에** 어드민(chaalsdn0217@gmail.com)이 `lone_observer`(홀로 빛나는 별)를 서버에 선점해 전역 잠김 → `HiddenAchievementRepository.releaseOwnedBy(uid)`(안드) / `HiddenAchievementStore.releaseOwnedBy(uid:)`(iOS) 추가: `achieverId==uid` 인 `hiddenAchievements` 문서 전부 삭제. **어드민 로그인일 때만** 호출(안드 `HiddenAchievementWatcher` LaunchedEffect / iOS `ProfileScreen.task`) — 일반 유저의 정당한 선점은 안 건드림. 어드민이 앱(프로필 탭)을 열면 슬롯이 풀린다. ⚠️ Firestore 규칙이 delete 를 막으면 무시되므로 그 경우 규칙 확인.
- **친구(타인) 프로필에 히든 아이콘 표시**: `UserProfileScreen` 이 히든 현황을 구독 안 해서 안 보였음 → 안드: `hiddenRepo.observe()` 구독 후 대상 uid 의 달성 히든을 `FloatingStatBox` 버블(오라/잔상/버스트)로 추가, 핀별/히든 인덱스 경계(`pinnedStart=5`) 정리(히든 탭은 버스트만, 화면 이동 없음). iOS: `UserProfileScreen` 에 `HiddenAchievementStore` 구독 + `hiddenSection`(HiddenIconBadge 가로줄, 달성 있을 때만) 추가.

---

## 8.31 로그아웃 버튼 + 히든 칭호 표기 + 하루 업로드 제한 + 어드민 선점 제외 (체크리스트 19~22, Android BUILD SUCCESSFUL)
- **19. 로그아웃 버튼(안드)**: 8.29에서 히든 아이콘을 전체화면 `FloatingStatBox` 오버레이로 넣으며 하단 로그아웃 버튼 위를 덮어 터치가 막힐 수 있었음 → `ProfileScreen` 로그아웃 `Column` 에 `Modifier.zIndex(1f)`(오버레이 위, 하단 얇은 밴드만 차지). 중앙 아바타/이름 Column 은 넓어서 zIndex 미적용(버블 상호작용 보존). iOS 는 히트영역 분리 구조라 원래 정상.
- **20. 히든 칭호 구분**: 히든 칭호는 **금색(0xFFD86F)+`『 』`+강한 후광+Bold**, 일반은 민트. 판별 `HiddenAchievements.byId(equippedId) != null`. 안드 `ProfileScreen`/`UserProfileScreen`, iOS `ProfileScreen`(titleDisplayText/Color/equippedTitleIsHidden)/`UserProfileScreen`.
- **21. 하루 업로드 10개**: `StaryConfig.DAILY_UPLOAD_LIMIT=10` / iOS `AppConfig.dailyUploadLimit`. 로그인 사용자 기준 그날(로컬 자정 이후) 내 업로드 수로 선차단. 안드 `UploadScreen`(구독한 `getMyDiaries` 로 카운트, 저장 버튼에서 막고 `upload_daily_limit` ko/en/ja 토스트) / iOS `UploadScreen.save()`(`store.mine`+`Calendar.startOfDay`). ⚠️ 서버 강제는 후속(클라 차단만).
- **22. 어드민 히든 선점 제외**: 어드민 이메일(`StaryConfig.ADMIN_EMAILS`={chaalsdn0217@gmail.com}, `isAdminEmail` / iOS `AppConfig`). `HiddenAchievementRepository.claim`(안드)·`HiddenAchievementStore.claim`(iOS) 최상단에서 어드민이면 **쓰기 skip + false** → 히든이 계속 "달성자 없음" 유지(실유저가 첫 달성). 이메일: 안드 `GoogleAuthHelper.currentUserEmail`(로그인/세션복원 시 `FirebaseAuth.currentUser?.email`, 로그아웃 null) / iOS `Auth.auth().currentUser?.email`.

---

## 8.30 채팅 FCM 알림(백그라운드/종료) + 채팅방 딥링크 (Android BUILD SUCCESSFUL, 실기기+배포 검증 대기)
체크리스트 18. 앱 백그라운드/종료 시 새 채팅 → 상단 heads-up, 탭 시 해당 채팅방으로 이동. **대부분 인프라는 기존에 있었고(수신 서비스·서버 함수·토큰·권한)**, 빠진 딥링크/채널만 보강.
- **핵심 동작**: 서버 `sendToUser` 가 **notification+data 혼합** + `android.priority:"high"` + `channelId:"stary_default"` 로 보냄 → 앱 후면/종료면 **시스템(Play services)이 직접 heads-up 표시**(onMessageReceived 안 불림), 전면이면 `onMessageReceived` 가 `AppForeground` 로 skip → 인앱 배너. 탭하면 data 가 런처 인텐트 extra 로 들어옴.
- **heads-up 보장**: `push/NotificationChannels.kt`(`ensureStaryNotificationChannel`, `STARY_CHANNEL_ID`, `IMPORTANCE_HIGH`) 를 **`StaryApplication.onCreate` 에서 사전 생성**. 채널이 영속되어야 종료 상태 시스템 알림도 상단 배너로 뜬다(안 만들어두면 첫 종료-알림이 heads-up 안 됨). 서버 channelId 와 값 일치 필수.
- **딥링크 = 채팅방**: 단일 Activity+Compose 구조(별도 ChatActivity 없음). `MainActivity` `EXTRA_CHAT_FRIEND_ID/EXTRA_CHAT_FRIEND_NAME` + `launchMode=singleTop` + `onNewIntent`(앱 살아있을 때 탭). `core/util/DeepLinkState`(mutableStateOf, 콜드=onCreate·웜=onNewIntent 공용) → `MainScreen` 이 `LaunchedEffect(DeepLinkState.diaryId/chatFriendId)` 로 관찰→`consume`→`NavRoute.Chat(friendId,friendName)`/`Detail` 이동(+로그인 오버레이 skip). 기존 diaryId 딥링크도 DeepLinkState 로 통일(param 은 오버레이 skip 판정에만).
- **서버**: `functions/index.js` `notifyOnChatMessage` data 에 `chatFriendId(=senderId)`/`chatFriendName` 추가(수신자 입장에서 발신자와의 방을 염). `StaryMessagingService` 도 채팅이면 채팅 extra, 아니면 diaryId 로 인텐트 구성.
- **권한**: `POST_NOTIFICATIONS`(API33+) 요청은 기존 `MainActivity` 에 이미 있음.
- ⚠️ **배포 필요(사용자)**: `firebase deploy --only functions`(Blaze). 미배포면 백그라운드/종료 푸시 안 옴(전면 인앱 배너는 동작). node `--check` 통과.
- **iOS**: APNs 인프라 별도 → 후속. 이번은 Android 전용(안드로이드 스튜디오/Kotlin 프로젝트 대상 요청).

---

## 8.29 히든 업적(앱 전체 1명 선착순) + 프로필 아이콘·파티클 (Android BUILD SUCCESSFUL, iOS 컴파일 CI 대기)
기존 '???' 칭호를 포함해 **히든 업적** 도입. 업적 화면을 **일반/히든 2탭**으로. 히든은 **앱 전체에서 단 한 명만** 달성(선착순), 달성 전엔 칭호·아이콘·이펙트만 노출하고 **조건은 `???`**, 달성되면 조건 공개 + `달성자: 이름`. 달성자 프로필엔 **전용 아이콘 + 파티클**이 뜨고 칭호가 자동 장착된다.
- **단 한 명 보장**: Firestore `hiddenAchievements/{id}` 문서를 **트랜잭션**으로 선점(주인 없으면 기록, 있으면 그게 나면 유지). 동시 시도 시 재시도로 한쪽만 성공. ⚠️ 완전한 도용 방지는 보안 규칙 `create-only`(존재 시 update 금지) 권장 — **아직 미적용**.
- **정의 11개**(Android `feature/profile/HiddenAchievements.kt` = iOS `Core/HiddenAchievements.swift` — 값/조건/제목 동일 유지 필수. 제목은 사용자 조정 반영: 빙하의 주인/사막의 신기루/심해의 지배자/죽음의 바다/우주의 완성/항성 탐험가 등):
  - 자동판정 8종: `secret_word`(제목에 '우주먼지'), **장소 4종**(오지 반경 300km, region 별 분리) `remote_place`=빙하(에베레스트/남극)·`place_desert`=사하라·`place_trench`=마리아나 해구·`place_triangle`=버뮤다, `all_rounder`(히든 제외 전 업적), `cosmic_rascal`(타인 글 300 열람·이관), `lone_observer`(친구 0 + 글 50·이관).
  - 이벤트형 3종(정의만, 화면 연동은 후속): `heart_frenzy`(프로필 하트 100), `melomaniac`(전곡 감상), `earth_pilgrim`(관광지 별+타인 열람=교차사용자).
- **UserStats 확장**: `secretKeywordTitle`(Bool) + `remoteRegions: Set<String>`(도달 오지 region: glacier/desert/trench/triangle). 안드 `rememberUserStats` / iOS `Achievements.computeStats` 에서 `RemoteLandmark(region,…)` 반경 판정으로 파생.
- **감시·선점**: 안드 `HiddenAchievementWatcher`(MainScreen 최상위 마운트 → 어느 화면에서든 동작). 자동조건 충족 & 미선점이면 트랜잭션 선점, 성공 시 특별 팝업 + 칭호 자동장착(StigmaStore+users.equippedTitle). iOS 는 전역 워처 부재 → ProfileScreen/AchievementsScreen `.task`+`onChange` 에서 `HiddenAchievementStore.attemptAutoClaims` 로 선점(프로필/업적 방문 시 판정 — **파리티 갭**), 성공 시 `.alert`.
- **저장소**: 안드 `data/repository/HiddenAchievementRepository`(claim 트랜잭션 + observe Flow). iOS `Data/HiddenAchievementStore`(@MainActor: claims 실시간 구독 + claim(withCheckedContinuation+runTransaction) + attemptAutoClaims, `attempted` 세션 가드로 중복 방지).
- **아이콘/파티클**: 안드 `HiddenParticles.kt`(Canvas + `withFrameNanos`, 효과별 orbit/rise/fall, `.layout` 로 넘쳐 그리기) + `HiddenIconWithEffect`(업적 목록·팝업용). iOS `HiddenIconBadge`/`HiddenParticlesView`(`TimelineView(.animation)`+`Canvas`). 효과: STARDUST/SNOW/AURORA/EMBER/SHADOW/HEART/MUSIC/ORBIT/BUBBLE.
- **프로필 히든 아이콘 = 떠다니는 버블**(사용자 요청): 정적 배지 행을 없애고 **`FloatingStatBox` 에 편입** — 하트/다이어리처럼 부유·회전·클릭·드래그. `StatBubble.hiddenEffect` 추가 시 ⓐ 궤도 스파클 **오라**(`drawHiddenAura`/`drawAura`) ⓑ 잡거나 빠를 때 **잔상(trail, `Body.trail` 최근 12위치)** ⓒ 탭 시 **화려한 버스트**(파티클 24개 + 흰 스파클). 탭 → 업적 화면. 안드 `FloatingStatBox.kt` / iOS `FloatingStatBox.swift` 동일 구조. 프로필 items 순서 = 기본4 + 핀별 + 히든, `onTap` 은 pinnedStart/hiddenStart 로 분기.
- **칭호 통합 조회**: `equippedTitleName(id)`(일반+히든 통합) → ProfileScreen/UserProfileScreen 칭호 표시가 히든 칭호도 해석. 히든 탭에선 내가 달성한 칭호를 장착/해제 가능.
- **상수/컬렉션**: `StaryConfig.Collections.HIDDEN_ACHIEVEMENTS` / iOS `AppConfig.Collections.hiddenAchievements` + `FirestoreService.hiddenAchievements`.
- **문자열**: 안드 `ach_tab_normal/ach_tab_hidden/ach_hidden_intro/ach_hidden_achiever/ach_hidden_unclaimed/ach_hidden_by_me`(ko/en/ja) / iOS `L10n` 동일 키. 업적명·조건·칭호는 기존 방침대로 비번역(데이터).
- ⚠️ **iOS LocaleManager 버그 수정**: `.tabMap` 케이스가 `return ("지도"` 로 튜플이 안 닫혀 있어 **iOS 전체 컴파일 불가** 상태였음 → `("지도","Map","地図")` 로 수정. (HEAD=dbc6997 커밋 자체가 깨져 있었음. 이번 롤백에서 워킹트리에 있던 수정본이 함께 버려진 것으로 보임 — 다른 미커밋 수정이 있었다면 유실됐을 수 있으니 확인 필요.)
- **남은 TODO**: 이벤트형 3종 화면 연동(하트100/전곡감상/관광지 교차사용자), 타인 프로필의 히든 배지 표시, iOS 전역 워처(항상 판정), 보안규칙 create-only, 파티클 모양 다양화(하트/음표 등).

---

## 8.28 닉네임 변경 + 닉네임 친구 검색(공통친구 정렬) (Android BUILD SUCCESSFUL, iOS 컴파일 CI 대기)
기본 닉네임은 구글 닉네임. **내 프로필에서 이름을 누르면 변경**(UI 레이아웃은 그대로, 입력만 다이얼로그/alert). 변경값은 `users/{uid}.userName`(검색·친구목록 표시 소스) + 로컬 캐시에 저장 → 친구 검색은 그 닉네임으로 동작. **검색 결과가 2명 이상이면 "나와 겹치는 친구가 많은 순"으로 정렬**(동률은 이름순 유지).
- **닉네임 저장 소스**: `users/{uid}.userName`(Firestore=진짜 소스, 검색 prefix 쿼리 필드) + 기기 캐시(즉시 복원). ⚠️ **로그인 시 구글 이름으로 덮어쓰지 않게** 보강 — 안드 `GoogleAuthHelper.signInWithGoogle` 가 upsert 전에 기존 `userName` 을 읽어 우선, iOS `AuthManager.ensureProfile` 도 기존 `userName` 우선(없을 때만 구글 이름). 다른 기기 재로그인에도 닉네임 유지.
- **Android**: `core/util/NicknameStore.kt`(prefs `stary_nickname`, uid별) 신설. `GoogleAuthHelper.applyStoredNickname(context)`(앱 시작 시 캐시 반영, `MainActivity.onCreate` 에서 restoreSession 직후 호출) + `setNickname(context, name)`(메모리 `currentUserName`+prefs+Firestore 갱신). `ProfileScreen` 이름 `Text` 에 리플 없는 `clickable` + `NicknameEditDialog`(BasicTextField 최대 20자) — `currentUserName` 이 일반 var 라 화면 로컬 `displayName` state 로 즉시 반영. 문구 `strings.xml` `profile_edit_nickname/profile_nickname_hint`(ko/en/ja).
- **iOS**: `AuthManager` 에 `setNickname(_:)`(@Published `displayName`+UserDefaults `nickname_<uid>`+Firestore) + 상태리스너에서 캐시 닉네임 즉시 반영. `ProfileScreen` 이름 `Text` 에 `.onTapGesture` → `.alert`(iOS16 TextField) 로 변경(시스템 alert 라 레이아웃 무영향). L10n `profileEditNickname/profileNicknameHint`.
- **검색 정렬**: 안드 `FirebaseFriendRepository.searchUsers`(excludeUserId=myUid) — 결과 2명↑이면 `friendIds(myUid)` ∩ `friendIds(each)` 개수로 `sortedByDescending`(async 병렬 `coroutineScope`+`awaitAll`). iOS `FriendsViewModel.search` 동일(순차 await + 인덱스 타이브레이크로 안정 정렬). `friendIds(uid)` = `users/{uid}/friends` 문서 id 집합. 검색 자체는 기존 `userName` prefix(`query`..`query+`) 그대로.

---

## 8.27 화면 첫 진입 설명창 (Android BUILD SUCCESSFUL, iOS 컴파일 CI 대기)
**내 다이어리·프로필·업적·배경음악·친구** 화면에 처음 들어가면 그 화면을 설명하는 안내 다이얼로그를 1회 띄운다.
- **Android `core/ui/FirstVisitInfo.kt`(신설)**: `FirstVisitInfo(seenKey, icon, title, message)` — 기존 코치마크와 같은 prefs(`stary_onboarding`)에 `seenKey` 로 1회 기록. 민트→블루 그라데이션 테두리 다크 카드 + 아이콘 뱃지 + "시작하기" 버튼(또는 바깥 탭) 닫기. Dialog 라 호출 위치(레이아웃) 무관 → 각 화면 루트 Box 안에서 호출.
  - 적용: `MyDiaryScreen`(info_mydiary)·`ProfileScreen`(info_profile)·`AchievementsScreen`(info_achievements)·`MusicScreen`(info_music)·`FriendScreen`(info_friends). 문구는 `res/values(-en/-ja)/strings.xml` `onb_*` 키(ko/en/ja).
- **iOS `Features/FirstVisitInfo.swift`(신설)**: `.firstVisitInfo(key:systemImage:title:message:)` ViewModifier — `UserDefaults` `onb_<key>` 1회 기록, `.onAppear` 게이팅 + 딤 오버레이 카드(시트 대신 오버레이라 탭 전환 안전). 적용: `MyStarsScreen`(mydiary)·`ProfileScreen`(profile)·`AchievementsScreen`(achievements)·`MusicScreen`(music)·`FriendsScreen`(friends). 문구는 `L10n` `onb*`(ko/en/ja).
- 재노출하려면 prefs 삭제(앱 데이터 초기화) 또는 `stary_onboarding`/`onb_<key>` 제거.

---

## 8.26-iOS 길찾기 진입 + 프로필 부유아이콘 패리티 (CI(macOS) BUILD SUCCESS e787ce8)
브랜치 `feat/moderation-profile-round` 의 **안드 전용 잔여분 2건**을 iOS(SwiftUI)로 이관(§1.5 패리티). Android/shared 파일은 한 줄도 안 바꿈 → `:androidApp:compileDebugKotlin` UP-TO-DATE(BUILD SUCCESSFUL). iOS 는 Windows 컴파일 불가 → push 후 `ios.yml`(macOS) 검증.

### ① 도보 길찾기 진입(친구 별) + 실시간 부분경로 — iOS
- **`Features/Map/MapFocusStore.swift`(신설)**: `MapFocusStore`(전역 `pendingDiaryId`+`withRoute`, request/consume — Android `MapFocusState` 미러) + `TabRouter`(5탭 선택 전역 전환, map=0…profile=4). 둘 다 메인스레드 전용이라 `@MainActor` 미부여(비격리 콜백에서 호출 위해).
- **진입 = "친구 별 길찾기 버튼"**: 안드는 친구 별-보드(`UserDiaryStarsScreen`) 탭이지만 iOS 엔 그 보드가 없음 → `UserProfileScreen` 의 그 사람 별 목록 각 행에 **`figure.walk` 버튼**(본인 글 제외) 추가. 누르면 `MapFocusStore.request(diaryId, withRoute:true)` + `dismiss()`.
- **`MapScreen.swift`**: `@ObservedObject focus` 관찰 → `.onChange(pendingDiaryId)`(이미 지도 탭일 때) + `.onAppear`(다른 탭에서 전환돼 나타날 때 — 숨김 중 onChange 미수신 대비, handleFocus 는 idempotent)에서 `store.diaries` 로 좌표 찾아 `focusTarget` 설정. **파동 후 길찾기**: `MapWarpOverlay`(동심원 물결 1회, 별 색) 재생 + `withRoute` 면 ~0.65s 뒤 `OrsRouting.walkingRoute` 로 전체 경로(`fullRoute`) 받음(물결이 먼저 퍼진 뒤 경로). **실시간 부분경로**: `partialRoute`(computed) 가 `location.coordinate` 변할 때마다 `partialRouteFrom(full,me)`(최근접 투영점→목적지, 안드 동일 알고리즘 포팅)로 갱신 → `MapLibreView.route`. 하단 **요약 칩 + X 취소** 오버레이.
- **`MapLibreView.swift`**: `focusTarget` 파라미터 추가 — 좌표 바뀌면 `setCenter(zoom15, animated)` 1회(Coordinator `lastFocus` 중복 가드). 경로 폴리라인 색 `#86EFAC`/width 5 로(안드 ROUTE_LAYER 일치).
- **파동(warp) 연출**: `MapWarpOverlay`(MapScreen.swift) = 화면 중앙(카메라가 별을 중앙에 둠)에서 동심원 링 3겹 + 중앙 발광이 `easeOut 1.0s` 로 퍼짐. 매 포커스마다 `.id(warpId)` 로 재생. ⚠️ 안드 `DiaryOpenWarp`(지도 스냅샷 메시 왜곡)의 **간이판**(스냅샷 굴절 대신 링 파동) — 점진 정교화 대상.
- **시트/탭 정리**: `MainTabView` 가 `TabView(selection:$router.selected)`+`.tag` + `.onChange(pendingDiaryId)` 에서 지도 탭 전환 & `chatTarget/diaryTarget` 닫기. `DetailScreen` 도 `.onChange` 로 `profileTarget` 닫음(작성자 프로필 시트 경유 진입 대비).
- ⚠️ **키 필요**: `project.yml` `ORS_API_KEY`(이미 추가됨, 빌드설정 주입). 미설정 시 `OrsRouting.isConfigured==false` → 경로 안 뜸(조용히).

### ② 프로필 떠다니는 통계 아이콘 + 핀 별 — iOS
- **`Features/Profile/FloatingStatBox.swift`(신설)**: Android `FloatingStatBox`(Compose 물리)를 **TimelineView(.animation)+Canvas+버블별 DragGesture** 로 포팅. `StatBubble`(아이콘/별·수·색·라벨·burst·showCount). 물리 엔진 `FloatingEngine`(부유/잡기 확대1.7/똑바로정렬/던지기 감속/벽 튕김/아이콘 충돌/탭 버스트 — 상수·식 안드 동일). **히트테스트 분리**: Canvas 는 `.allowsHitTesting(false)`(렌더 전용), 버블 위치마다 투명 `Color.clear`+`contentShape(Circle())` 뷰가 제스처 수신 → 그래야 아래 아바타/로그아웃이 눌림. 별 모양은 `StarShape` 심볼 resolve, 후광/회전/확대는 Canvas 가 그림.
- **`ProfileScreen.swift`(재작성)**: 스크롤 리스트 → **중앙 아바타(글로우+그라데이션 링, 탭=사진 변경) + 이름 + 칭호(탭→업적) + FloatingStatBox(좋아요/친구/다이어리/업적 + 핀 별) + 하단 로그아웃**. 우상단 툴바 `+`(핀 picker)/`gearshape`(설정)/`bell`(알림), 좌상단 `music.note`. 버블 탭: 친구→친구탭(`TabRouter`), 다이어리→`MyStarsScreen` push, 업적→`AchievementsScreen` push, **핀 별→`MapFocusStore.request(withRoute:true)`(지도 전환→파동→길찾기, 다이어리 클릭처럼)**. `NavigationStack(path:)`+`ProfileRoute` enum + `navigationDestination(for: Diary.self)`.
  - **핀 별 = 길찾기(안드+iOS)**: 사용자 요청으로 프로필 핀 별 탭도 친구 별처럼 파동+길찾기. 안드는 `NavGraph` ProfileScreen `onOpenDiary` 를 `MapFocusState.request(diaryId, withRoute=true)` 로(BUILD SUCCESSFUL). ⚠️ 길찾기 실작동엔 ORS 키 필요(안드 `secrets.properties` 설정됨 / iOS 빌드설정 `ORS_API_KEY` 주입 필요).
- **핀 다이어리**: `users/{uid}.pinnedDiaries`(최대 3, 안드 `FirebaseFriendRepository.get/setPinnedDiaries` 와 동일 필드) — ProfileScreen `.task` 로드 / `PinDiaryPicker`(별+제목 토글, 저장) `setData(merge:)`.
- **`AchievementsScreen.swift`(신설)**: 기존 ProfileScreen 인라인 업적 목록+칭호 장착을 분리(스탯 재계산, `equippedTitleId` 바인딩으로 프로필 칭호 즉시 반영, `equippedTitle` Firestore 기록). `AboutView()` 도 여기로 옮겨 KMP Shared 링크 유지.
- **`MyStarsScreen`**(ProfileScreen.swift 내): 내 별 카드 목록(탭→상세) — 안드 MyDiaryScreen 의 간이 iOS 버전(부유 보드/드래그는 미이관, 점진).
- **L10n 신규키**(LocaleManager): `routeDirections/routeCancel/routeMinSuffix`, `navAchievements/profileMyStars/profilePinTitle/profilePinHint/commonSave/profileFriends/profileDiaries/profileAchievements/profileEmptyStars`(ko/en/ja).

### 남은 iOS 점진 이관(후속)
- 친구 별-보드(`UserDiaryStarsScreen`)·내 다이어리 부유 보드(`DiaryStarBox` 드래그)·별자리·배경음악 멀티트랙/원형 다이얼·사진 4:3 크롭·앱아이콘·길찾기 파동(warp) 연출.

---

## 1. 개요
- 앱: "Stary" — 지도 기반 위치 다이어리. 지도에 별(star) 마커로 다이어리가 뜨고, **100m 이내**에서만 열람 가능.
  좋아요/댓글/알림, Google 로그인, 프로필 이미지 업로드 기능.
- 원본: Android 전용(Jetpack Compose + Firebase + 네이버맵).
- 이 분기: **Android + iOS 확장형 KMP** 구조 + **MapLibre + MapTiler 지도**(구 네이버/Google Maps 대체) + 민감값 주입.
- 코드 패키지(namespace): `com.chaminwoo.stary` (androidApp), 공용은 동일 패키지 재사용 + `com.chaminwoo.stary.shared.*`.
- ⚠️ **applicationId = `com.chaminwoo.stary_ios`** (namespace와 다름). 원본 앱 `com.chaminwoo.stary`와 충돌/Firebase 분리를 위해 분기. 액티비티 풀네임은 여전히 `com.chaminwoo.stary.MainActivity`.

## 2. 기술 스택
- Kotlin 2.2.10, AGP 9.1.1, Gradle 9.3.1, Compose BOM 2024.09.00, minSdk 26 / compileSdk 36(.1).
- Firebase: Firestore(**named DB `stary-db`** — `StaryConfig.FIRESTORE_DB_ID`, 모든 접근은 `data/StaryFirestore.kt`의
  `staryFirestore` 사용. 기본 `Firebase.firestore` 금지: (default) DB 없음→NOT_FOUND), Storage,
  **FirebaseAuth(Google signInWithCredential + 비로그인 익명)**, firebase-bom **33.7.0**(named DB API 필요).
- 지도: **MapLibre GL Native 11.11.0**(`org.maplibre.gl:android-sdk`, Google Maps 대체) + MapTiler 벡터 타일(OpenMapTiles v3), `play-services-location`.
- 기타: Coil(이미지), android-gif-drawable(로그인 GIF), kotlinx-serialization-json, kotlinx-coroutines.

## 3. 모듈 / 소스 트리
```
:shared (KMP)                         com.android.kotlin.multiplatform.library + kotlin.multiplatform
  commonMain/
    core/model/        Diary, Comment, Like, AppNotification, NotificationType  (순수 Kotlin, createdAt: Long)
    core/geo/          LatLng(공용 좌표), GeoUtils(Haversine distanceBetween)
    shared/platform/   Platform (expect class) + describePlatform()
    shared/config/     StaryConfig(컬렉션명/반경/기본좌표 상수), Secrets(민감값 인터페이스 계약)
    shared/data/repository/Repositories.kt
                       DiaryRepository / CommentRepository / LikeRepository / NotificationRepository (인터페이스)
  androidMain/ shared/platform/Platform.android.kt   (actual, android.os.Build)
  iosMain/     shared/platform/Platform.ios.kt       (actual, UIDevice) — macOS에서만 컴파일

:androidApp (com.android.application + AGP 내장 Kotlin + kotlin.compose)
  com/chaminwoo/stary/
    MainActivity.kt            ComponentActivity → StaryTheme { MainScreen() }
    StaryApplication.kt        (네이버 init 제거됨; Firebase 자동초기화)
    navigation/NavGraph.kt, NavRoute.kt    화면 라우팅. onLocationClick → LocationHelper.cameraTarget(공용 LatLng)
    core/designsystem/         Color, Theme(StaryTheme), Type
    core/ui/StaryComponents.kt DiaryCard 등 공통 컴포저블 (createdAt 포맷)
    core/util/
      LocationHelper.kt        FusedLocation 기반 현재위치/연속추적, 공용 LatLng, 거리계산은 GeoUtils 위임
      ImageUploadHelper.kt     Firebase Storage 업로드 (diary_images/*)
      TestDataHelper.kt        seed() — 전국 장소 더미 다이어리 생성(테스트용)
    data/local/DiaryCache.kt   메모리 캐시(id→Diary)
    data/repository/
      Firebase{Diary,Comment,Like,Notification}Repository.kt  ← 공용 인터페이스의 Firestore 구현
      UserRepository.kt        프로필 이미지 URL get/upload (android.net.Uri 사용 → 공용 인터페이스 미적용, Android 전용)
    feature/
      auth/GoogleAuthHelper.kt + screen/LoginScreen.kt   Google 로그인, WEB_CLIENT_ID=BuildConfig 주입
      home/screen/MainScreen.kt, MainListScreen.kt        메인/지도 홈
      map/screen/DiaryMap.kt                              ★지도 본체 (MapLibre + MapTiler, AndroidView)
      diary/DiaryViewModel.kt, InteractionViewModel.kt, NotificationViewModel.kt
      diary/screen/DetailScreen.kt, UploadScreen.kt, NotificationScreen.kt
      profile/ProfileViewModel.kt + screen/MyScreen.kt
```

## 4. 데이터 모델 (commonMain, 모두 순수 Kotlin data class)
- `Diary(id,userId,userName,isAnonymous,title,content,imageUrl,latitude,longitude,createdAt:Long,likeCount,commentCount,viewCount,starType,starColor,visibilityType)`
- `Comment(id,diaryId,userId,userName,content,createdAt:Long)`
- `Like(userId,userName,createdAt:Long)`
- `AppNotification(id,type,diaryId,diaryTitle,diaryOwnerId,actorId,actorName,content,createdAt:Long,isRead)`
- `NotificationType { LIKE, COMMENT }`
- ⚠️ **createdAt 은 epoch millis(Long)**. (원본은 Firebase `Timestamp`였음.) 생성 시점은 Firebase 구현부에서
  `System.currentTimeMillis()` 로 설정. 화면 포맷은 `java.util.Date(createdAt)`.
  Firestore `toObject()` reflection 으로 매핑되므로 필드명이 Firestore 문서와 일치해야 함.

## 5. Firestore 구조 (StaryConfig.Collections)
- `diaries/{id}` : 다이어리. 하위 컬렉션 `comments/{id}`, `likes/{userId}`.
- `notifications/{id}` : `diaryOwnerId` 로 조회, `isRead` 로 미읽음 카운트.
- `users/{userId}` : `profileImageUrl`.
- 좋아요/댓글은 batch/transaction 으로 카운트(`likeCount`/`commentCount`) 동시 갱신 + 알림 생성.
- Storage: `diary_images/{uuid}.jpg`, `profile_images/{userId}.jpg`.

## 6. 지도 (MapLibre + MapTiler) 핵심
- `DiaryMap(diaries, currentLatLng:공용LatLng, isFollowing, onGestureDetected, onRefollowClick, onItemClick, onCreateClick)` — `feature/map/screen/DiaryMap.kt`.
- 엔진: **MapLibre GL Native**. Compose는 `AndroidView`로 `MapView` 래핑 + `rememberMapViewWithLifecycle()`(생명주기 연결). `MapLibre.getInstance()`는 MapView 생성 전 1회. 좌표 변환 `LatLng.toMl()`.
- 스타일: `res/raw/maplibre_style.json`(자체 작성, "위성 야경" 컨셉 — 상세 8.33 참고). 소스=MapTiler `tiles/v3?key=__MAPTILER_KEY__`(BuildConfig.MAPTILER_KEY 치환). 레이어 = background/water(fill) + landcover(숲/초지)·landuse(도심)·park(fill, 미세 톤 텍스처) + 도로 6겹(road-minor/mid/major-glow/major/highlight/glint) → 건물·POI·라벨은 없음(다운로드·렌더 안 함 = 경량, 텍스처는 벡터 타일에 이미 포함돼 추가 다운로드는 없음).
- 도로: motorway/trunk/primary(앰버 글로우+코어+하이라이트+흐르는 `road-glint`), secondary/tertiary(딤 골드), minor/service(땅 톤 웜 그레이). `minzoom` 7(major)~13(minor). `road-glint` 는 `DiaryMap.kt` 애니메이션 루프가 `line-dasharray` 위상을 흘려 빛이 흐르게 함(재생성 순간 0.2초 페이드), `minzoom`13/페이드 줌 13·15·17.
- 줌 색 보간: 각 레이어 `paint` 색이 `["interpolate",["linear"],["zoom"],...]` 로 부드럽게 변함(레이어별 줌 범위 상이 — 위 8.33 참고).
- 비네트 + 저줌 대기 헤이즈: Compose Canvas 오버레이(터치 통과) — 상시 비네트 + 줌 4.4→2.4(글로브 진입줌) 파란 대기.
- 카메라 틸트: 줌 무관 고정 10°(`BASE_TILT_DEG`) — 카메라를 세팅하는 모든 지점에 적용.
- 내 위치: GeoJSON source(`current-location`) + CircleLayer. "내 위치로" FAB = 카메라 이동.
- **다이어리 별 마커**: GeoJSON source(`diaries`) + SymbolLayer(`diary-stars`).
  - 아이콘 = `StarStyle.starPath`(5종: 십자/5각/6각/8각/대각 스파클 — **전부 곡선(quad) 스파이크로 통일**, 8.33)
    × 12색, 글로우(blur)+본체+흰 하이라이트로 비트맵 생성(`starBitmap`), 사용 조합만 `style.addImage`.
  - **바닥 빛 웅덩이**(`diary-ground-light-N`, CircleLayer, 8.33): 별 아래 지점 고정 광 — 별의 부유(iconTranslate)와 시차가 생겨 "떠 있음" 강조.
  - ⚠️ **PNG(star_1~5)를 마커로 쓰지 말 것** — 에뮬레이터에서 PNG→GL 텍스처가 대각선 빗금으로 깨짐. Path 렌더 유지.
  - ⚠️ 비트맵은 정사각+4의 배수 변(현재 160px). addImage 는 기기밀도로 나눠 표시(화면크기 ≈ 160/density × iconSize).
  - near(100m 이내) = feature bool 속성 → iconSize 확대 + pulse, 전체 float 애니메이션(50ms 루프 setProperties, 진폭 4dp).
  - 클릭: queryRenderedFeatures → 100m 이내 열람 / 밖 거리 토스트. (길찾기 기능은 사용자 요청으로 삭제)
- **별가루 파티클**: GeoJSON source(`star-particles`) + SymbolLayer 4개(`star-particles-0..3`, phaseGroup 필터).
  - Compose Canvas(`StarParticleOverlay`) 는 **삭제됨** — 실좌표 마커라 카메라 동기화 코드 불필요, 컬링은 MapLibre 가 담당.
  - 시드 42 고정, 400개를 초기 currentLatLng 반경 20km 면적 균등 분포로 1회 생성(이후 setGeoJson 없음).
    feature 속성: phase / twinkleSpeed / depth(0.5~1.0 크기 배율) / phaseGroup.
  - 아이콘 = 24px 흰 점(글로우+코어) 비트맵. iconSize = 줌 보간(6→0, 10→0.4, 15→0.8) × depth.
    iconOpacity = 줌 보간(6→0, 10→twinkle) — **줌 6 이하 완전 숨김**(사용자 튜닝: 8→6).
  - 반짝임 = 기존 50ms 애니메이션 루프에서 레이어 4개의 iconOpacity 만 위상/주기 달리 갱신(GeoJSON 재생성 금지).
- 초기 카메라(현재 위치 중심 / `LocationHelper.cameraTarget` 경계)는 DiaryMap이 style 로드 시 처리.
- ⚠️ 키 없으면(placeholder) 타일 안 뜸. `secrets.properties`의 `MAPTILER_KEY` 필요.

## 7. 민감값 주입 배선 (하드코딩 없음)
- `secrets.properties`(루트, gitignore) 에서 읽음. 파일/키 없으면 build.gradle 의 `?:` 기본 placeholder 사용.
  - ⚠️ 과거의 `secrets.defaults.properties` / `secrets.properties.example` 는 **삭제됨**(실제 키 혼입 우려 + gitignore 추가). 폴백 로직은 `takeIf{exists}` 라 없어도 무방.
- `androidApp/build.gradle.kts` 가 `secrets.properties` 읽어서:
  - `MAPTILER_KEY` → `buildConfigField` → `BuildConfig.MAPTILER_KEY` → `res/raw/maplibre_style.json`의 `__MAPTILER_KEY__` 치환.
  - `GOOGLE_WEB_CLIENT_ID` → `buildConfigField` → `BuildConfig.GOOGLE_WEB_CLIENT_ID` → `GoogleAuthHelper` 사용.
  - (구 `MAPS_API_KEY` / Google geo `API_KEY` 메타데이터는 MapLibre 전환으로 제거됨.)
- **Firebase 프로젝트**: 이 포크 = `momentdiary-f26c8`(번호 7962996464) / 앱 `com.chaminwoo.stary_ios`.
  원본(연결 금지) = `momentdiary-52b78` / `com.chaminwoo.stary`.
- `google-services.json`(androidApp/, gitignore): f26c8 실파일 사용 중. **로그인 3종(json·웹클라ID·SHA-1)은 반드시 같은 프로젝트(f26c8).**
  - 웹 클라이언트 ID(`secrets.properties` GOOGLE_WEB_CLIENT_ID)는 json 의 `client_type:3` 값(`7962996464-...`)이어야 함. 다른 프로젝트 ID 넣으면 28444.
  - 지도 타일 안 뜨면: `secrets.properties`의 `MAPTILER_KEY` 확인(placeholder면 타일 미표시).
- 디버그 SHA-1: `F3:48:0A:53:FA:F3:EF:D7:60:1D:E7:A2:CA:EA:37:9C:E2:DE:A5:D0`.

## 8. 빌드 시스템 특이점 (재확인용)
- `settings.gradle.kts`: `:shared`, `:androidApp` 포함. 네이버 maven 저장소 제거.
- `gradle/libs.versions.toml`: 네이버·Google Maps 제거, **MapLibre**(`org.maplibre.gl:android-sdk`)·KMP·coroutines.
- `:shared` → `com.android.kotlin.multiplatform.library` + `kotlin { android { } }` + iOS framework(baseName "Shared").
- `:androidApp` → `kotlin.android` 명시 금지(AGP 내장 Kotlin과 충돌). AppCompat 의존성 명시 추가
  (themes.xml 이 `Theme.AppCompat.Light.NoActionBar` 상속 — 과거 네이버 의존성이 transitive 로 제공하던 것).
- `gradle.properties`: `kotlin.native.ignoreDisabledTargets=true`, `android.useAndroidX=true`.

## 8.8 안드로이드 릴리즈 서명 + R8 (실기기 릴리즈 테스트 완료, 2026-06-19)
- **릴리즈 서명**: 루트 `keystore.properties`(gitignore)에서 `STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD`
  읽어 `signingConfigs.release` 구성. keystore 없으면 unsigned 로 폴백(빌드는 됨). 템플릿 `keystore.properties.example`(커밋됨).
  - 실제 keystore: `stary-release.jks`(루트, gitignore), **별칭 `mykey`**. SHA-1 은 Firebase `momentdiary-f26c8` Android 앱에 등록 완료.
  - ⚠️ `*.jks`/`keystore.properties` 분실 시 Play 업데이트 영구 불가 — 별도 백업 필수.
- **R8 활성**: `release { isMinifyEnabled = true }`. ProGuard 룰(`androidApp/proguard-rules.pro`)에 keep 추가:
  - Firestore POJO(`doc.toObject(Diary::class.java)`): `com.chaminwoo.stary.core.model.**` / `core.geo.**` 전체 keep(+`<init>()`,`<fields>`).
  - **auth0 jwtdecode + Gson**: `GoogleAuthHelper.getUserIdFromToken()` 가 `com.auth0.android.jwt.JWT` 로 idToken 의 `sub`(=앱의 userId)를
    파싱하는데 Gson 리플렉션 의존 → R8 가 지우면 **null 반환 → currentUserId null → 릴리즈에서만 "로그인 안 됨 + 다이어리 필터 깨짐"**.
    `com.auth0.android.jwt.**` + `com.google.gson.**` keep 으로 해결(릴리즈 실기기 검증 완료). ※디버그는 R8 미적용이라 증상 없음.
  - 그 외: kotlinx.serialization, MapLibre(`org.maplibre.android.**`), Coil/gif keep.
- 산출물: `androidApp/build/outputs/bundle/release/androidApp-release.aab`(Play 업로드용), `.../apk/release/androidApp-release.apk`(사이드로드 테스트용).
- 다음: **Play Console($25) 등록 → AAB 업로드 → 데이터 보안/개인정보처리방침/스크린샷 → 내부테스트 → 프로덕션**.

## 8.9 첫 실행 코치마크 + 위치/권한 + 다이어리 로딩 안정화 (2026-06-19)
- **첫 실행 코치마크** `feature/home/screen/MainOnboardingOverlay.kt` (MainScreen 최상위 오버레이, prefs `stary_onboarding/main_coach_seen` 로 1회):
  스포트라이트(어두운 스크림 + 타깃만 원형으로 뚫기, `BlendMode.Clear`+`CompositingStrategy.Offscreen`) 7단계 —
  메뉴(좌상단)/위치필터(좌하단)/내위치·별자리·음악·업로드(우측 FAB)/마무리(중앙 메시지). 단계 탭 이동, Crossfade·fade in/out.
  - ⚠️ 마지막 단계는 반지름 0 → `Brush.radialGradient(radius<=0)` 는 `IllegalArgumentException("ending radius must be > 0")` 크래시.
    반드시 `if (r > 0f)` 가드 후 그릴 것. 우측 FAB 는 컬럼 CenterHorizontally(업로드 56dp 기준) 라 48dp 버튼도 중심 end 44dp.
- **다이어리 열람 파장 색** `DetailScreen`: 흰색 → `StarStyle.colorOf(diary.starColor)` (별 색).
- **위치 권한**: `MainActivity.onCreate` 에서 앱 시작 즉시 위치(FINE/COARSE)+알림 요청.
  `MainListScreen` 은 권한 허용 시 위치 추적 시작+현재위치 반영을 **ON_RESUME 생명주기 + 최초 1회**로 처리
  (이전엔 허용 후 시작 코드가 없어 기본 좌표에 멈췄음 → 지도가 엉뚱한 곳).
- **다이어리 로딩 버그 2건**:
  - `observeAllDiaries` 가 **로그인 전(지도 미리 렌더)에 시작→PERMISSION_DENIED 로 리스너 사망→복구 안 됨**.
    → `FirebaseAuth.AuthStateListener` 로 **auth 변경 시 재구독**(`ListenerRegistration` 교체). 메인 지도 마커가 안 뜨던 핵심 원인.
  - `observeMyDiaries` 복합 인덱스(userId+createdAt) 의존 제거 → 서버는 `whereEqualTo(userId)` 만, **정렬은 클라이언트**(`sortedByDescending`).
- 참고: Firestore 경고 `No setter/field for anonymous`(Diary.isAnonymous ↔ "anonymous" 매핑) 는 무해(기본 false).

## 8.22 위치/로그인/팝업/설정 라운드 (BUILD SUCCESSFUL 2026-06-27, 실기기 테스트 대기)
사용자 자율 진행 지시(확인 없이, 실패 시 잘게 쪼개 직접 해결). Android 5건 구현 + 빌드 성공.
- **실시간 위치 렌더링**: `LocationHelper` 의 내부 `currentLocation` 을 **`MutableStateFlow<LatLng?>`**(`location`)로 전환.
  연속 콜백/일회성 fix 모두 flow 에 반영. `MainListScreen` 이 `LocationHelper.location.collectAsState()` 로 관찰 →
  `currentLatLng` 가 연속 업데이트마다 따라옴(예전엔 진입 시 1회만 채워 파란 점이 안 움직였음). `getCurrentLatLng()=location.value`.
- **최초 진입 시 내 위치로 카메라**: `DiaryMap` 에 `didAutoCenter` 1회 가드 LaunchedEffect 추가 —
  스타일 로드 시점엔 위치 fix 가 없어 기본 좌표로 뜨므로, **실제 fix(`getCurrentLatLng()!=null`)가 들어오면** 그 위치로
  `animateCamera`(700ms) 1회. `focusDiary` 가 있으면(알림 포커스) 생략(그쪽이 카메라를 직접 다룸).
- **로그인 유지("한 번 로그인하면 바로 지도")**: `GoogleAuthHelper.currentUserId`(=Google sub)는 **메모리 var 라 앱 재시작 시 null**
  → FirebaseAuth 세션은 디스크 영속이어도 로그인 화면이 다시 떴음. `GoogleAuthHelper.restoreSession()` 추가 —
  영속된 `FirebaseUser.providerData`(google.com, uid=Google sub)에서 식별자/이름/사진 복원. `MainActivity.onCreate` 에서 `setContent` 전에 호출.
  `MainScreen` 의 `showLogin`/`contentReady` 초기값을 `currentUserId!=null`(=로그인 유지) 기준으로 → 로그인 상태면 영상·로그인 오버레이 건너뛰고 즉시 지도.
- **인앱 팝업(채팅·다이어리 알림 배너)**: `core/ui/InAppBanner.kt`(전역 큐 `show`/`consume` + `InAppBannerHost` = **상단 슬라이드 배너**,
  탭→이동, 4초 자동 사라짐, 하단 `StaryToast` 와 별개 채널). 감시기 `feature/diary/InAppPopupWatchers.kt`:
  - `NotificationPopupWatcher(notifications, onOpen)` — 최초 구독은 기준선만, 이후 새 알림만 배너. `MainScreen` 의 `notifVm.notifications` 사용.
  - `ChatPopupWatcher(userId, suppressChatWith, onOpenChat)` — `FirebaseChatRepository.observeMyChats(userId)` 신설
    (`whereArrayContains("participants",uid)`, **orderBy 서버 금지**→클라 판단). 마지막 메시지가 내가 보낸 게 아니고 updatedAt 증가 시 배너.
    지금 그 채팅을 보고 있으면(`suppressChatWith==friendId`) 생략. `sendMessage` 메타에 `lastSenderName` 추가(배너 발신자명). `ChatSummary` data class 신설.
  - `MainScreen` 에 두 와처 + `InAppBannerHost()` 배선. `AppSettings.notificationsEnabled` 가 false 면 배너 미표시.
- **댓글 작성자 프로필 조회**: `DetailScreen.CommentItem` 에 `onOpenProfile` 추가 — 아바타/이름 탭 시 `onOpenProfile(comment.userId, comment.userName)`
  → 기존 `DetailScreen(onOpenProfile)`→`NavRoute.UserProfile` 배선 재사용(다이어리 작성자와 동일 경로). `comment.userId` 있을 때만.
- **설정 탭**: `NavRoute.Settings`("설정") + `feature/profile/screen/SettingsScreen.kt`(드로어 "설정", Icons.Settings) —
  배경음악 on/off, **배경음악 볼륨/효과음 볼륨 슬라이더**, **알림 팝업 on/off**.
  - `MusicManager`: `musicVolume`/`sfxVolume`(0..1, prefs 영속) + `updateMusicVolume`/`updateSfxVolume`. player.setVolume + SFX(open/wind/dial)에 sfxVolume 곱.
    ⚠️ property `var musicVolume by mutableStateOf`(private set) 가 합성 `setMusicVolume` 생성 → 함수명을 `update*` 로(JVM 시그니처 충돌 회피, enabled/setActive 와 동일 패턴).
  - `core/util/AppSettings.kt`(신설): `notificationsEnabled`(prefs) + `updateNotificationsEnabled`. `MainScreen` 에서 `MusicManager.init` 옆 `AppSettings.init`.
  - 설정 UI 는 우주 배경(`mydiary_bg`)+글래스 카드(민트→블루 테두리)+원형 아이콘 뱃지+그라데이션 볼륨 슬라이더(`Slider` `track` 슬롯 커스텀, `@OptIn(ExperimentalMaterial3Api)`)+동적 볼륨/알림 아이콘+커스텀 스위치.
- **언어 변경(인앱 로케일)**: `core/util/LocaleManager.kt`(신설) — 선택 언어 태그를 prefs 저장 + `MainActivity.attachBaseContext` 에서 `wrap()`
  (`createConfigurationContext` 로 로케일 덮어쓰기) → 모든 리소스가 그 언어로 해석. 변경 시 `activity.recreate()` 로 즉시 재적용. 지원: 시스템 기본/ko/en/ja.
  - **문자열 리소스화**: `res/values/strings.xml`(ko 기본) + `values-en` + `values-ja`. 리소스화 범위 =
    **설정 화면 + 드로어/탑바 제목/공통 contentDescription**(MainScreen `localizedTitle()`) +
    **상세(DetailScreen)·업로드(UploadScreen)·친구(FriendScreen)·프로필(ProfileScreen) 화면 전체 UI 문자열**(2026-06-27 추가).
    - 비-Composable 람다(클릭/콜백) 토스트는 `context.getString(R.string.x, args)`, Composable 은 `stringResource`. `UploadScreen.VisibilityOptions` 는 라벨을 string res id 로 보유→화면에서 해석.
    - **2차 확장(같은 라운드)**: 채팅(ChatScreen)·알림(NotificationScreen)·내 다이어리(MyDiaryScreen, `DiarySort` 라벨→`sortLabel()` 리소스)·배경음악(MusicScreen)·업적(AchievementsScreen) 화면 UI 도 리소스화.
    - 의도적으로 **번역 안 함(=content/data)**: 다이어리 제목/내용·작성자명(`익명`/`알 수 없음`)·채팅 메시지·**업적 이름/조건/칭호명**(`Achievements.kt`)·**음악 트랙명**(`MusicCatalog`)·`DiaryViewModel`/`FriendViewModel` event 토스트(`저장 완료!` 등)·`RelativeTime`/시간 포맷. (업적/트랙명은 공용 데이터 모델 + iOS 공유라 별도 작업 대상.)
    ⚠️ `DiaryMap`(지도 FAB contentDescription)·`UserProfileScreen` 등 일부는 아직 하드코딩.
- **지도 우하단 버튼 교체**: 배경음악 토글 FAB 제거 → **몰입(지도만 보기) FAB**(`Icons.Filled.Fullscreen` → `MapUiState.enterMapOnly()`). 좌하단 필터 스피드다이얼의 "지도만 보기" 항목도 삭제(중복 제거). 음악 on/off 는 이제 설정 화면에서.
- **인앱 팝업 1회 보장 + 앱 종료 시 상단 알림(요청)**:
  - `core/util/AppForeground.kt`(신설) — `StaryApplication` 이 ActivityLifecycleCallbacks 로 전면/후면 추적.
  - **이중 표시 방지**: 전면이면 인앱 배너(InAppBanner)만, 후면/종료면 FCM 시스템 알림만. `StaryMessagingService` 가 `AppForeground.isForeground` 면 시스템 알림 skip + **IMPORTANCE_HIGH/PRIORITY_HIGH(heads-up 상단)**. 와처들도 `AppForeground.isForeground` 일 때만 `InAppBanner.show`(후면 알림도 seen 처리해 복귀 시 폭주 방지).
  - **채팅 1회**: `ChatPopupWatcher` dedup 을 "방:updatedAt" 키 집합(`shownKeys`)으로 — 스냅샷 재방출/리컴포지션에도 같은 메시지 두 번 안 뜸.
  - **FCM 발송 함수 추가(`functions/index.js`)**: `notifyOnChatMessage`(chats/{chatId}/messages onCreate → 상대방 토큰 푸시), `notifyOnNotificationCreate`(notifications onCreate → diaryOwnerId 푸시, LIKE/COMMENT만; FRIEND_POST 는 기존 diary 함수 담당=이중 방지). `sendToUser` 헬퍼(단건 send + 만료 토큰 정리). data 메시지 값은 전부 string.
    ⚠️ **실제 "앱 꺼져도 알림"은 Cloud Functions 배포 필요**(Blaze + `cd functions && npm install` + `firebase deploy --only functions`, REGION=stary-db 리전 일치). 미배포 시 후면/종료 푸시는 안 옴(전면 인앱 배너는 동작). node `--check` 문법 통과.
  - 설정에 "언어" 섹션 + `LanguageDialog`(현재 선택 체크) 추가. `Context.findActivity()` 로 recreate.
  - ⚠️ **recreate 부작용 방지**: `MusicManager.release()` 가 `initialized=false`(+`openLoaded=false`) 로 풀어 dispose→release→init 사이클에서 SoundPool 재로드(효과음 안 깨지게).
- **남은 iOS TODO(이번 라운드 패리티)**: 로그인 유지·실시간 위치는 iOS 이미 동작(`AuthManager.addStateDidChangeListener` 영속 복원 + `LocationManager.startUpdatingLocation`).
  미반영: ① 최초 진입 내 위치 카메라(MapScreen/MapLibreView center 변경 시 재센터), ② 댓글 작성자 프로필 탭(iOS UserProfile 화면 부재 — 화면부터 필요), ③ 설정 화면(iOS MusicManager 볼륨 musicVolume/sfxVolume + AppSettings + SettingsScreen + 탭/프로필 진입), ④ 인앱 배너+채팅/알림 와처(observeMyChats 포함), ⑤ 언어 변경(iOS 는 Bundle.main.localizations + Localizable.strings, 또는 SwiftUI environment locale). CI(macOS)로 검증 예정.

## 8.25 체크리스트 TODO 3건 — 배너 dedup/미조회 아이콘/별 슬라이더 (BUILD SUCCESSFUL 2026-06-28)
`SETUP_CHECKLIST.md` "📝 다음 작업(2026-06-27)" 3건 구현.
- **① 인앱 배너 반복 버그**: 와처(`ChatPopupWatcher`/`NotificationPopupWatcher`)는 `if (userId!=null && !showLogin)` 안에 마운트돼
  조건 토글/재마운트 시 로컬 `remember { shownKeys }`·`baselineDone` 이 리셋 → 같은 메시지가 큐에 중복 enqueue 되어 순차 표시(=반복)되던 게 원인.
  → **`InAppBanner.show(key=...)` 에 프로세스 영속 dedup `HashSet`** 추가(원인 무관 1회 보장). 채팅 key=`방:updatedAt`, 알림 key=`notif:id`.
  와처 로컬 dedup/baseline 은 "앱 켤 때 과거 항목 억제" 용으로 유지. **iOS 동일 미러**(`InAppBanner.show(key:)` + `InAppWatcher`, CI(macOS) BUILD SUCCESS d5bfb45).
- **② 미조회 필터 아이콘**: `MainListScreen` "미조회만" 칩 아이콘 `Icons.Filled.Visibility`(상세/카드 조회수 눈과 의미 충돌) → **`Icons.Filled.FiberNew`**(NEW 뱃지). 라벨 유지, `Visibility` import 제거.
- **③ 설정 음량 슬라이더 별 thumb**: `SettingsScreen.VolumeRow` 의 M3 `Slider` 에 `thumb` 슬롯 추가 — `StarThumb` 컴포저블(22dp 5각 별 + **후광**).
  - **후광/반응형(추가 라운드)**: 별 뒤 `drawBehind` 민트 `radialGradient` 후광(평상시 alpha 0.4, 비활성 0). Slider 와 `thumb` 가 **같은 `MutableInteractionSource`** 공유 →
    `collectIsDraggedAsState`/`collectIsPressedAsState` 로 누름·드래그 감지 시 `animateFloatAsState` 로 `graphicsLayer` scale 1.0→1.3 + 후광 alpha→0.9(발광). 기존 그라데이션 `track` 슬롯과 공존.
  - ⚠️ iOS 는 SwiftUI `Slider` 가 커스텀 thumb 미지원 → 완전 커스텀 슬라이더 필요. iOS TODO 로 보류(①은 미러 완료).

## 8.24 안드로이드 언어 리소스화 마무리 — DiaryMap/UserProfile (BUILD SUCCESSFUL 2026-06-28)
8.22 에서 언어 전환을 넣었지만 일부 화면이 한국어 하드코딩이라 번역이 안 됐던 것을 마저 리소스화.
- **DiaryMap.kt**: FAB contentDescription(확대/축소/내 위치로/별자리/지도만 보기/다이어리 생성) + 100m 밖 열람 토스트를
  `stringResource`/`context.getString(R.string.map_open_range, 반경, 거리)` 로. `import androidx.compose.ui.res.stringResource` 추가.
  - 토스트는 비-Composable 람다(`map.snapshot`/클릭 핸들러)라 `context`(이미 `LocalContext.current` 보유)로 `getString` 포맷.
- **UserProfileScreen.kt**: 아바타 contentDescription·이름/칭호 폴백·친구 액션(내 프로필/친구/채팅하기/요청됨/친구 추가)·통계 라벨(좋아요/친구/다이어리)·"업적·칭호"·"볼 수 있는 다이어리가 없어요"·"(제목 없음)" 전부 `stringResource`. import 추가.
- **strings.xml(ko/en/ja) 신규 키**: `cd_zoom_in/cd_zoom_out/cd_my_location/map_constellation/map_only/cd_create_diary/map_open_range`(지도) +
  `user_profile_me/user_chat_action/user_add_friend/user_requested/user_no_title/user_ach_titles/user_no_diaries/common_untitled`(타인 프로필). 기존 키 재사용(`cd_profile_photo`,`cd_default_profile`,`common_user`,`friend_no_name`,`friend_status_friend`,`common_friend`,`profile_stat_likes`,`profile_stat_diaries`).
- **iOS 패리티(§1.5)**: 같은 문자열을 iOS `L10n` 딕셔너리에 14키 추가(타인 프로필 + 미조회 칩) + `UserProfileScreen`/`MapScreen`/`ListScreen` 이
  `locale.t(...)` 로 표시(`@ObservedObject LocaleManager.shared`). iOS 의 DiaryMap FAB 류는 부재(지도 단순)라 미러 대상 아님. CI(macOS) BUILD SUCCESS deaa432.
- ⚠️ 남은 하드코딩(후속): `DiaryViewModel`/`FriendViewModel` 이벤트 토스트, 업적/트랙명(공용 데이터·의도적 비번역), iOS DetailScreen 등 나머지 화면은 8.22 방침대로 점진 이관.

## 8.23-iOS 미조회 필터 + 조회 기록 (CI(macOS) BUILD SUCCESS e89904a, 2026-06-28)
iOS 남은 패리티 중 **미조회(unviewed) 필터** 구현(Android MainListScreen "미조회만" + FirebaseViewedRepository 패리티).
- **`Data/ViewedStore.swift` 신설**: `ViewedRepository.markViewed(uid,diaryId)`(fire-and-forget, `users/{uid}/viewedDiaries/{diaryId}` 에 `viewedAt` 기록) +
  `ViewedStore`(@MainActor ObservableObject — 그 컬렉션 실시간 구독해 `viewedIds: Set<String>` 노출). `FirestoreService.viewedDiaries(of:)` 헬퍼 추가.
- **열람 기록**: `DetailScreen` 에 둘째 `.task` — 진입 시 `markViewed`(본인 글 포함 무조건, Android 와 동일).
- **필터 UI**: `MapScreen` 우상단 "미조회만" 칩(토글 시 `viewedIds` 에 없는 별만 마커 표시), `ListScreen` 툴바 좌측 "미조회만" 토글(+빈 상태 문구 분기). `MainTabView` 가 `ViewedStore` 시작(uid)/주입.
- shared 무변경. ⚠️ 별가루/별자리·사진 4:3 크롭·앱아이콘·FCM·언어 전체 이관은 여전히 iOS TODO.

## 8.22-iOS 8.22 라운드 iOS 패리티 (CI(macOS) BUILD SUCCESS 40424d0, 2026-06-27)
위 5개 미반영 항목 전부 SwiftUI 로 구현. Windows 라 로컬 컴파일 불가 → push 후 `ios.yml`(macOS) 검증.
- **① 최초 진입 내 위치 카메라**: `MapLibreView` 에 `userLocation: CLLocationCoordinate2D?`(실제 fix, 없으면 nil) 추가 +
  `Coordinator.didAutoCenter` 1회 가드. `makeUIView` 는 fix 없으면 기본 좌표(AppConfig.default)로 시작, fix 가 처음 들어오면 `updateUIView` 에서 그 위치로 `setCenter(zoom 14, animated)` 1회. `MapScreen` 이 `center:`→`userLocation: location.coordinate`(옵셔널)로 전달.
- **② 댓글 작성자 프로필 + UserProfile 화면**: `Features/Profile/UserProfileScreen.swift` 신설 — 아바타/이름/장착 칭호(users/{uid} 조회) +
  친구 상태별 액션(본인=내 프로필 / 친구=채팅하기 푸시 / 그 외=친구 추가, friendRequests 중복체크 후 setData) + **그 사람의 공개 별 목록**(store.diaries 에서 userId 필터 + 비공개 제외·친구공개는 친구일 때만, 탭→Detail 푸시). `DetailScreen` 작성자명/댓글 아바타·이름 탭 → `profileTarget` `.sheet` 로 진입(익명/빈 userId 비활성). 시트에 auth/store/location 주입(Detail 푸시 대비).
- **③ 설정 화면**: `MusicManager` 에 `musicVolume`/`sfxVolume`(@Published, UserDefaults `music_volume`/`sfx_volume`) + `updateMusicVolume`/`updateSfxVolume`. resume/playTrack 에 musicVolume, open/dial 효과음에 sfxVolume 곱. `Core/AppSettings.swift`(notificationsEnabled, @MainActor ObservableObject). `Features/Profile/SettingsScreen.swift`(사운드 토글+BGM/효과음 볼륨 슬라이더, 알림 팝업 토글, 언어 선택) — ProfileScreen 툴바에 `gearshape` 진입.
- **④ 인앱 배너 + 와처**: `Features/InAppBanner.swift`(`InAppBanner` 싱글톤 큐 + `InAppBannerHost` 상단 슬라이드 4초). `Features/InAppWatcher.swift`(`InAppWatcher` @MainActor — chats `arrayContains` + notifications `diaryOwnerId` 구독, 최초=기준선, dedup, `AppSettings.notificationsEnabled` 게이팅 / `ChatSummary` / `ChatPresence`(보고 있는 방 억제)). `ChatViewModel.send` 메타에 `lastSenderName` 추가. `ChatScreen` 이 `friendId/friendName` 기반(+`ChatPresence` set/clear). `MainTabView` 가 와처 시작 + `InAppBannerHost` 오버레이 + 배너 탭→채팅/상세 `.sheet`.
- **⑤ 언어 변경**: `Core/LocaleManager.swift`(@MainActor, prefs `app_language`, system/ko/en/ja) + `L10n` 인코드 딕셔너리(설정/탭 문자열 ko/en/ja). `RootView` 가 `.environment(\.locale,)` + `.id(language)`(Android recreate 대응 = 전체 재구성). SettingsScreen 언어 picker(`confirmationDialog`). ⚠️ Android 처럼 **점진 이관** — 설정/탭만 우선 번역, 나머지 화면 문자열은 아직 한국어 하드코딩.
- 변경 파일: 신설 `AppSettings/LocaleManager/SettingsScreen/UserProfileScreen/InAppBanner/InAppWatcher.swift`, 수정 `MusicManager/MapLibreView/MapScreen/RootView/ProfileScreen/DetailScreen/ChatScreen/ChatViewModel.swift`. shared(commonMain) 무변경 → Android 빌드 영향 없음.

## 8.21 배경음악 멀티트랙 + 원형 다이얼 + 로그인 게이팅 (BUILD SUCCESSFUL 2026-06-26)
- **배경음악 멀티트랙화**: `ambient_music.mp3` 삭제 → `core/util/MusicCatalog.kt`(6트랙: star_whisper/tiny_explorer/
  celestial_drift/cosmic_funk/forgotten_galaxy/nebula_garden). 트랙별 색·별 모양(StarStyle type)·해금 업적
  (first_step/storyteller/popular/star_traveler/companion). 기본 해금 = star_whisper.
- **음악 선택 화면**(`feature/profile/screen/MusicScreen.kt`, `NavRoute.Music` + 드로어 "배경음악"):
  **원형 로터리 다이얼**(별이 원 둘레, 드래그=회전(atan2), 위쪽에 온 트랙 선택, 탭=그 별 위로) +
  원 안쪽 중앙에 트랙별 **별자리**(`MUSIC_CONSTELLATIONS` 6종). 잠긴 트랙 자물쇠+토스트, 미리듣기/확정 안 됨.
- **이어듣기**: 트랙 전환 시 `playTrack(id, currentPositionMs())` 로 듣던 위치 이어받음(처음부터 X).
  이탈 시 바꿨으면 확정(위치 유지), 안 바꿨으면 현재 재생 무간섭. `playTrack` 위치 클램프(트랙 길이 초과 보정).
- **효과음**: 다이얼 회전음 `turning_dial.mp3`(`MusicManager.setDialTurning`, MediaPlayer+완료콜백 — 겹침 없이,
  끝났을 때 아직 돌리는 중이면 재생). 다이어리 열람음 `open_diary.mp3` 는 **열람 애니메이션(DiaryMap 파장) 시작 시점**에 재생.
- **로그인 게이팅**: 코치마크를 첫 실행 → **첫 로그인 시**(userId!=null) 1회. 비로그인 시 업로드 FAB 숨김
  (`DiaryMap.showCreate` = MainListScreen userId!=null). 음악 탭도 비로그인 시 "로그인이 필요해요".
- **알림 삭제 collapse**: 알림 셀 `Modifier.animateItem()` → 스와이프 삭제 시 셀 제거 + 아래 셀이 빈자리 부드럽게 채움.
- raw 음원: `bgm_*.mp3` 6개 + `open_diary.mp3` + `turning_dial.mp3` 추가, `ambient_music.mp3` 삭제.
- **남은 iOS TODO**: 위 배경음악 멀티트랙/원형 다이얼/회전·열람 효과음/로그인 게이팅/알림 collapse 를 iOS(SwiftUI)에 반영.

## 8.20 iOS 기능 확장 — 소셜 + 미디어 (CI 그린 2026-06-25)
- **좋아요/댓글/알림** (`DetailViewModel`, `NotificationsViewModel`/`Screen`): Android Like/Comment/Notification 리포지토리와 동일 스키마.
  ⚠️ 알림 읽음 필드는 `read`(Kotlin isRead 직렬화), 수신자는 `diaryOwnerId`. 좋아요/댓글 시 상대에게 알림 생성.
- **친구/채팅** (`FriendsViewModel`/`Screen`, `ChatViewModel`/`Screen`): 사용자 검색(userName 범위쿼리)·요청/수락/거절·친구목록·1:1 채팅(chats/{chatId}/messages). 친구 탭 신설.
- **사진 첨부** (`ImageUploader`): Storage `diary_images/{uuid}.jpg`(JPEG 0.8), PhotosPicker(iOS16). 카드 썸네일+상세 AsyncImage.
- **새 글 친구 알림**: `DiaryStore.notifyFriends` — 공개/친구 글 작성 시 friends 에 FRIEND_POST batch(private 제외). `save()` 가 문서 ID 반환.
- **iOS 컴파일 함정 추가**: Firestore `data(as:)` 는 누락 비옵셔널 필드에서 throw → 부분 문서(UserProfile) 필드는 Optional.
  `addDocument(data:)` 는 async 컨텍스트에서 async throws 오버로드 선택(try await). cos/sin 은 CGFloat 캐스팅.
- 쓰기는 batch+딕셔너리, 읽기는 data(as:) Codable. UI: TabView 5탭(지도/목록/올리기/친구/프로필) + 프로필 알림 벨.
- **업적·별 해금**(`Achievements.swift`): UserStats/Reward/Achievement + StarUnlocks 포팅. 업로드 피커 잠금(미해금 dim+자물쇠+토스트),
  프로필 업적 진행도(unlocked/total, 보상 배지). 통계는 내 다이어리+친구 수 기반(열람 수=0, ViewedRepo 미구현).
- **프로필 사진/칭호**: `ImageUploader.uploadProfile`(profile_images/{uid}.jpg + users.profileImageUrl), PhotosPicker 아바타 변경,
  해금 칭호 장착/해제 → users.equippedTitle(업적 id), 이름 아래 칭호 칩.
- **남은 iOS TODO**: 지도 별자리/배경음악, 사진 4:3 크롭, 미조회 필터(ViewedRepo), 타인 프로필 화면, 앱아이콘/스플래시, FCM 푸시.
- **CI 검증 메모**: 레포 public → `Invoke-RestMethod`로 런 상태 조회. 로그는 토큰 필요(`git credential-manager get` 으로 추출).
  폴 스크립트: `scratchpad/poll_ci.ps1 <sha>`(완료까지 폴링 후 error 줄 추출). 6개 기능 배치 모두 BUILD SUCCESS.

## 8.19 iOS CI 그린 달성 — macOS 컴파일 통과 (BUILD SUCCESS 2026-06-25)
- `.github/workflows/ios.yml` build 잡(macos-15, 시뮬레이터, 서명 없음)이 **3bfa81c 에서 성공**. iOS 코어 슬라이스가 실제로 컴파일/링크됨.
- **CI 통과까지 발견한 함정(다음에도 주의)**:
  1. XcodeGen 2.45 산출물이 프로젝트 포맷 77(Xcode 16) → macos-14 기본 Xcode 15.4 로는 못 엶. **runner=macos-15 + setup-xcode latest-stable** 필요.
  2. `gradlew` 가 Windows 에서 커밋되어 **exec 비트 없음** → 프리빌드 스크립트 `./gradlew Permission denied`. `git update-index --chmod=+x gradlew` 로 해결.
  3. iOS 프레임워크(:shared) 빌드 시 Gradle 이 **:androidApp 까지 구성** → AGP 가 SDK 위치 못 찾음. CI 에서 `echo "sdk.dir=$ANDROID_HOME" > local.properties` 선행.
  4. workflow `on.push.paths` 에 `gradlew`/`gradle/**` 없으면 wrapper 수정이 CI 트리거 안 됨 → paths 에 추가.
  5. Swift: `FirebaseApp` 은 `import FirebaseCore` 필요. `cos/sin`(Double) 을 CGFloat 와 섞으면 'ambiguous' → `CGFloat(cos(a))` 캐스팅.
- 로그 확인: 레포 public 이라 GitHub REST API(`/actions/runs`, `/actions/jobs/{id}/logs`)로 조회 가능(logs 는 토큰 필요 — git credential-manager).

## 8.18 iOS 앱 1차 구현 — SwiftUI 코어 슬라이스 (작성 완료, CI 컴파일 검증 대기 2026-06-25)
- **마일스톤 0(스캐폴드)에서 코어 앱으로 확장.** Windows 라 로컬 컴파일 불가 → push 후 `.github/workflows/ios.yml`(macOS) 가 검증.
- **project.yml(XcodeGen)**: SPM 의존성 추가 — Firebase(Auth/Firestore/Storage) 11.6+, GoogleSignIn 8+, MapLibre 6.7+.
  deploymentTarget **16.0** 로 상향(NavigationStack/TextField(axis:) 등). Info.plist 권한 설명·URL 스킴(`$(GOOGLE_REVERSED_CLIENT_ID)`)·다크모드.
- **새 Swift 소스(`iosApp/Sources/`)**:
  - `Core/`: `AppConfig`(StaryConfig 미러), `Geo`(Haversine), `Theme`(밤하늘 톤+Color hex/blend),
    `StarStyle`(팔레트 21색·그라데이션 포팅), `StarShape`(별 0~4 정밀 + 5~8 even-odd 근사 Path), `StarView`, `LocationManager`.
  - `Data/`: `Models`(Diary 등 Firestore Codable, @DocumentID), `FirestoreService`(named DB stary-db), `AuthManager`(익명+구글),
    `DiaryRepository`(observeAll/Mine·save·viewCount), `DiaryStore`(ObservableObject 구독).
  - `Features/`: `RootView`(인증 게이트+4탭), `LoginView`, `Map/`(`MapLibreView` UIViewRepresentable+별 마커 `StarImageRenderer`, `MapScreen`),
    `List/ListScreen`(+DiaryCard), `Upload/UploadScreen`(별 모양·색·공개범위 피커), `Detail/DetailScreen`(거리 게이팅·조회수), `Profile/ProfileScreen`.
  - `ContentView.swift` → `AboutView`(KMP `PlatformKt.describePlatform()` 호출로 Shared 링크 유지).
- **컴파일 리스크(CI 확인 예정)**: MapLibre/Firebase/GoogleSignIn SPM API 명칭, @DocumentID 합성(→ Diary 는 id 기반 수동 Hashable/Equatable).
- **남은 iOS TODO**: 사진 첨부(Storage+PhotosPicker), 친구/채팅/알림/댓글·좋아요 화면, 별자리/배경음악, 업적·해금(StarUnlocks), 별 마커 그라데이션 채움, 앱아이콘/스플래시.
- ⚠️ **패리티 규칙(CLAUDE.md §1.5)**: 이후 Android 변경은 iOS 에도 반영.

## 8.17 흑백 그라데이션 별 색 + 업적 (BUILD SUCCESSFUL 2026-06-23, 실기기 테스트 대기)
- **검정→하양 그라데이션 색 추가**(`StarStyle`): `COLOR_COUNT 20→21`, `gradients` 에 인덱스 20 = `0xFF101010→0xFFFFFFFF`(흑백/밤→여명).
  모든 사용처가 `StarStyle.COLOR_COUNT` 상수를 참조해 업로드 피커·지도·카드·내다이어리에 자동 반영(하드코딩 색 개수 없음).
- **해금 업적 추가**(`Achievements.rewardAchievements`): `color_grad_dawn` "여명을 기다린 자" — 자정~새벽(0~4시) 10회 기록(`nightPosts>=10`)
  → `Reward.StarColor(20)`. `StarUnlocks.color[20]` 자동 도출로 피커 잠금/해금 토스트 반영. ⚠️ 흑백은 glow=colorOf(20)=검정이라
  어두운 쪽은 발광 약함(의도된 흑백 대비).

## 8.16 타인 프로필 = 내 프로필급 정보 (BUILD SUCCESSFUL 2026-06-23, 실기기 테스트 대기)
- **UserProfileScreen 전면 확장**: 아바타/이름/친구액션에 더해 **통계(좋아요·조회수·다이어리)·업적 진행도(unlocked/total 바)·장착 칭호**,
  그리고 **그 사람의 다이어리 목록**(탭→Detail)을 표시. ProfileScreen 과 동일 레이아웃(GradientCard/StatCell).
  - 통계/업적: `rememberUserStats(userId, diaryVm)` 가 임의 userId 로 동작(그 사람 diaries/viewed/friends 관찰) → `Achievements.unlockedIds`.
  - 다이어리: `diaryVm.getMyDiaries(userId)`. **공개범위 필터**(private=본인만 / friends=본인·친구만 / 그 외 공개)로 타인 비공개 보호.
  - `NavGraph`: UserProfile 에 `onOpenDiary`→Detail 배선.
- **장착 칭호 공개화**(타인도 보이게): 칭호는 원래 로컬 `StigmaStore`(기기 prefs)에만 있어 타인이 못 봄 →
  `UserProfile.equippedTitle`(commonMain) 필드 추가 + `FirebaseFriendRepository.setEquippedTitle(userId,achId)`(users/{uid} merge).
  - 장착 시점(`AchievementsScreen` onToggleEquip)에서 Firestore 동기화(fire-and-forget) + **내 ProfileScreen 진입 시 백필**(LaunchedEffect 로 현재 장착값 push).
  - `getProfile` 가 `equippedTitle` 까지 반환(toObject 자동 매핑). UserProfileScreen 이 `Achievements.byId(id)?.titleName` 로 표시.
  - ⚠️ shared 모듈(UserProfile) 변경이라 :shared 재컴파일됨. expect/actual Beta 경고는 기존 무해.

## 8.15 알림 지도포커스 + 타인 프로필/친구추가 (BUILD SUCCESSFUL 2026-06-23, 실기기 테스트 대기)
- **새 다이어리 알림 → 지도 카메라 이동 + 파장 1회**:
  - `core/util/MapUiState.kt` 에 `MapFocusState`(전역 `pendingDiaryId`, request/consume) 추가.
  - `NotificationScreen`: 알림 타입이 `FRIEND_POST` 면 `onFocusDiaryOnMap(diaryId)`, 그 외(LIKE/COMMENT)는 기존대로 `onOpenDiary`(Detail).
  - `NavGraph`: `onFocusDiaryOnMap` → `MapFocusState.request(id)` + `Main` 으로 popUpTo 이동.
  - `MainListScreen`: `MapFocusState.pendingDiaryId` 를 **전체(diaries, 필터 무관)** 에서 좌표 조회 → `DiaryFocusTarget` 으로 `DiaryMap` 에 전달, `onFocusHandled={consume()}`.
  - `DiaryMap`: `focusDiary`/`onFocusHandled` 파라미터 + `DiaryFocusTarget(lat,lng,colorIndex,diaryId)`. `LaunchedEffect(focusDiary,mapRef)` 가
    `animateCamera(800ms)` → `CancelableCallback.onFinish` 에서 `map.snapshot` → 화면 중앙(0.5,0.5) `DiaryOpenWarp` 재생. `DiaryOpenWarpData.navigateAfter`
    플래그 추가(별 탭=true→세부 이동 / 알림 포커스=false→파장만, consume). ⚠️ 필터로 가려진 별이면 카메라/파장은 동작하나 별 자체는 미표시.
- **타인 다이어리 → 작성자 프로필 진입 + 친구추가**:
  - `NavRoute.UserProfile(userId,userName)` 추가(title=userName). `feature/profile/screen/UserProfileScreen.kt` 신규 —
    아바타(공개프로필 사진 `FirebaseFriendRepository.getProfile` 로드)/이름 + **친구 상태별 액션**(본인="내 프로필" / 이미친구="친구"칩+"채팅하기" / 그외="친구 추가"→`FriendViewModel.sendRequest`, 누르면 "요청됨"). `FriendViewModel` 재사용.
  - `FirebaseFriendRepository.getProfile(userId)` 추가(users/{uid} 단건 조회).
  - `DetailScreen(onOpenProfile)` — 헤더 작성자(별+이름) 영역을 탭하면 진입(익명/빈 userId 면 비활성, 탭 가능 시 ChevronRight 표시).
  - `NavGraph`: Detail.onOpenProfile→`UserProfile` 내비, `composable<UserProfile>`(onOpenChat→Chat). `MainScreen` currentRoute 매핑에 `UserProfile` 추가(탑바 제목/뒤로가기).

## 8.14 친구검색/색상수/접근성 라운드 (BUILD SUCCESSFUL 2026-06-23, 실기기 테스트 대기)
- **중앙 브랜드 색**: `core/designsystem/Color.kt` 에 `Mint(0xFF6EE7B7)`, `MintBlue(0xFF3B82F6)` 추가(흩어진 리터럴의 단일 출처).
  `FriendScreen.Green` 을 중앙 `Mint` 참조로 정리. ⚠️ 나머지 인라인 `Color(0xFF6EE7B7)`(DiaryMap/MainListScreen/MainScreen 등 20곳)은
  미치환(후속 정리 대상) — 값은 동일하므로 동작 영향 없음.
- **친구 검색 UX**(`FriendScreen`): 입력 디바운스 350ms **자동 검색**(엔터 불필요, `LaunchedEffect(query)`),
  검색했는데 결과 0건이면 "'{query}' 검색 결과가 없어요" **빈 상태** 표시. `lastSearched` 로 디바운스 중 깜빡임 방지.
- **접근성**: 친구 아바타 `AsyncImage` 에 `contentDescription="{이름} 프로필 사진"` 부여(스크린리더 대응).

## 8.13 사용감 다듬기 라운드 (BUILD SUCCESSFUL 2026-06-23, 실기기 테스트 대기)
- **알림 스와이프 삭제 강화**(`NotificationScreen.SwipeToDeleteNotification`): 놓는 순간 오프셋 3분기 —
  `<= -revealPx*0.85`(최대까지 당김) → 화면 폭(`dismissPx`)만큼 밀어내고 `onDelete()` / `< -revealPx/2` → 버튼 노출 유지(탭 삭제) / 그 외 닫기.
- **알림 탭 → 다이어리 열기**: `NotificationScreen(onOpenDiary)` 추가, `NavGraph` 의 `Notification` 라우트에서 `Detail(diaryId)` 로 내비.
  `NotificationItem(onClick)` — `notif.diaryId` 가 있으면 행 클릭 가능(없으면 비활성).
- **상대 시간 표기**: `core/util/RelativeTime`(방금 전/N분·시간·일 전, 1주↑은 yyyy.MM.dd 폴백). 알림·댓글에 적용
  (`NotificationItem`, `DetailScreen.CommentItem`). ⚠️ 상세화면 헤더 작성일은 절대 날짜 유지.
- **댓글 IME 전송**(`DetailScreen`): 입력창 `ImeAction.Send` + `KeyboardActions(onSend)`, 전송 버튼과 동일 경로(`submitComment`)로 단일화,
  전송 후 `LocalSoftwareKeyboardController.hide()`.
- 참고(미진행): 좋아요/댓글 실패 토스트는 Firestore 오프라인 영속성(쓰기 로컬 큐 보존+리스너 낙관 반영)으로 데이터 유실이 아니라 보류.

## 8.12 사용감/최적화 정리 라운드 (BUILD SUCCESSFUL 2026-06-22, 실기기 테스트 대기)
- **다이어리 구독 상한**: `FirebaseDiaryRepository.observeAllDiaries` 에 `.limit(MAX_OBSERVED_DIARIES=1000)` 추가.
  전 컬렉션 무제한 실시간 구독(비용/메모리/렌더 선형 증가) 가드. ⚠️ 최신순 상한이라 1000개 초과 시 오래된 글은 지도에서 제외됨 →
  추후 뷰포트/지오해시 쿼리로 대체 예정(TODO).
- **조회수 합리화**(`DetailScreen`): `incrementViewCount` 를 **본인 글 제외 + 앱 세션당 1회**(`ViewCountSession` in-memory set)로 변경.
  자가 열람/재진입 부풀림 + 매 열람 Firestore 쓰기 제거.
- **WASD 위치 치트 디버그 한정**(`MainListScreen`): 위치 이동 키 입력/`focusRequester` 포커스 탈취를 `BuildConfig.DEBUG` 에서만.
  릴리즈에선 `devKeyModifier = Modifier`(no-op).
- **지도 애니메이션 루프 절전**(`DiaryMap`): 별 0개 + 파티클 숨김(zoom<9)일 때 50ms 루프를 `delay(250)` 으로 쉼.
- **위치 확인중 상태**(`DetailScreen`): 위치 null 일 때 "범위 밖" 오안내 대신 "위치를 확인하는 중이에요…" 표시 +
  최대 6초 폴링(`locationTick`)으로 위치 잡히면 갱신.
- **클러스터링/별자리 디바운스**(`DiaryMap`): cameraIdle 재계산 LaunchedEffect 앞에 `delay(90)` — 연속 팬/줌 시 O(n²) 재계산 빈도 완화.
- **날짜 포맷 remember**(`DetailScreen`): 헤더/댓글의 `SimpleDateFormat().format()` 을 `remember(createdAt)` 로 캐시(리컴포지션 할당 제거).
- ⚠️ 미적용(후속): 비공개/친구공개 글이 `firestore.rules` 가 `auth!=null` 만 게이팅해 raw Firestore 에선 노출됨(클라 필터만).
  userId=Google sub 라 규칙 레벨 소유자 강제 불가 → 별도 인증 구조 재설계 필요(미착수).

## 8.11 채팅/크롭/전환/모양 라운드 (2026-06-22)
- **친구 1:1 채팅**: commonMain `ChatMessage`(core/model) + `ChatRepository`(observeMessages/sendMessage) +
  `StaryConfig.CHATS/MESSAGES` 상수 + `chatId(a,b)`(두 ID 정렬·결합 결정적 방 ID). Android `FirebaseChatRepository`
  (`chats/{chatId}/messages/{id}` createdAt 오름차순 구독 + 방 메타 머지). `feature/chat/ChatViewModel` + `screen/ChatScreen`
  (말풍선 좌/우, IME/내비바 패딩, 새 메시지 자동 스크롤). `NavRoute.Chat(friendId, friendName)`(title=친구명) + NavGraph 배선 +
  FriendScreen 행에 "채팅" pill(`onOpenChat`) + MainScreen currentRoute 매핑(toRoute).
- **Firestore 규칙 파일화**: 루트 `firestore.rules`(앱이 쓰는 전 컬렉션 + chats, `request.auth != null` 게이팅 — userId=Google sub라
  auth.uid 강제 불가) + `firebase.json` 에 `firestore.database="stary-db"`. 배포: `firebase deploy --only firestore:rules`.
  ⚠️ 콘솔 기존 규칙을 대체하므로 배포 전 대조 필요(아직 미배포 — 채팅 동작하려면 배포해야 함).
- **알림 화면**: 빈 상태 "알림이 없습니다"(🔔). **왼쪽 스와이프 = 고정 폭(84dp) 삭제 버튼 드러내기**(Animatable offset +
  draggable, `coerceIn(-revealPx,0)` 로 버튼 폭까지만, 절반 기준 스냅). 삭제 버튼 왼쪽 면 둥글게(RoundedCornerShape topStart/bottomStart),
  소프트레드 `0xFFE57373`. `NotificationRepository.deleteNotification` + VM `delete()` 추가.
- **사진 크롭(고정 4:3)**: `core/util/ImageCropHelper`(ASPECT=4/3, EXIF 보정+다운샘플 `loadDownsampled`, `cropToFile`) +
  `androidx.exifinterface:1.3.7`. UploadScreen 이미지 영역을 `aspectRatio(ASPECT)` 프레임으로 — 드래그 위치+핀치 확대(cover-fit 클램프,
  3분할 가이드), 저장 시 크롭본 업로드(실패 시 원본 폴백). `CropController`+`ImageCropFrame`(Canvas drawImage).
  DetailScreen 헤더도 `aspectRatio(ASPECT)` 로 통일(추가 크롭 없음). 사진 없으면 `R.drawable.image_frame` 템플릿.
- **DetailScreen UI 리팩토링**: 헤더 사진 위 스크림 + **작성자/날짜만 오버레이**(제목은 사진 밖 본문 상단으로 분리). 별 색을 강조색으로
  통일(테두리/포커스/전송/댓글 점). **사진 탭 → 전체화면 뷰어**(핀치 줌 1~5, 드래그, 더블탭, 탭/뒤로 닫기, `FullScreenImageViewer`).
- **화면 전환**: NavHost 기본 전환 = 깊이감 줌(scaleIn 0.93+fadeIn / scaleOut 1.06+fadeOut, pop 대칭, 320/300ms FastOutSlowIn).
  Upload 만 모달 슬라이드업(slideInVertically{it}, pop slideOut). 별 줌인 물결 연출 → Detail 확대 등장과 연결.
- **지도 float 진폭 줌 연동**: DiaryMap 별 부유 애니메이션 진폭에 `zoomAmp=((zoom-6)/9).coerceIn(0.1,1)` 곱(줌 작을수록 덜 흔들림).
- **별 모양 추가/수정**(`StarStyle`, TYPE_COUNT 8→9):
  - 꽃(5): 0.8배 축소 + 가운데 빈 원(반지름 0.135·s).
  - 다이아몬드(6): `references/diamond.jpg` 재현 — 테이블·어깨·거들·컬릿 외곽 + 크라운 중앙 X자 패싯, 패싯선은 `getFillPath`로
    두께 줘 DIFFERENCE 로 빈 공간(컷) 처리.
  - **행성(8 신규)**: `references/planet.jpeg` — 본체 원 + 기울어진(−20°) 타원 고리 밴드 UNION. 업적 `shape_planet`("나만의 행성",
    서로 다른 30일 기록 = distinctDays≥30) 추가 → `StarUnlocks` 자동 도출로 피커/업적화면 반영.
  - ⚠️ 참조 이미지는 `res/drawable` 금지(리소스명 충돌로 빌드 실패). `references/`(빌드 제외)에 보관.

## 8.10 몰입/연출/업적 라운드 (2026-06-20)
- **다이어리 진입 연출 이동**: 세부 화면(DetailScreen)의 파장/왜곡 **제거**(이제 멀쩡하게 진입).
  대신 지도 마커 탭 시 `DiaryMap` 이 **현재 지도를 `map.snapshot()` 으로 캡처 → 1.3초간 별 위치에서 방사형 물결 굴절 → 그 뒤 세부 화면 이동**.
  - 굴절은 `Canvas` + `nativeCanvas.drawBitmapMesh`(28×28 메시) 로 구현. ⚠️ AGSL `RuntimeShader`/`RenderEffect` 는
    **에뮬레이터(SwiftShader 소프트웨어 GPU)에서 무시돼 안 보임** → mesh 방식으로 교체(에뮬·실기기 공통 동작). `DiaryOpenWarp` 참고.
  - 연출은 **지도 마커를 100m 이내에서 탭할 때만** 트리거(스냅샷 대상이 지도).
- **지도만 보기(몰입) 모드**: `core/util/MapUiState`(전역 mutableState) — 좌하단 필터 다이얼 맨 아래 "지도만 보기" →
  탑바(MainScreen)·필터(MainListScreen)·FAB/줌(DiaryMap) 전부 숨김. `feature/home/screen/MapOnlyOverlay` 가 하단 중앙 원형 X
  (3초 후 자동 숨김, 그 자리 탭/뒤로가기로 다시 표시, X 탭 시 복귀, BackHandler 로 이탈 방지). 다이어리 열람 시 자동 해제.
- **업적 해금 팝업**: `feature/profile/AchievementUnlockWatcher` (MainScreen 최상위, 로그인 시). prefs `stary_prefs/ach_announced_<uid>`
  로 기준선 저장 후 새로 달성한 업적만 팝업(트로피+이름+보상). **코치마크(showOnboarding) 동안은 suppressed 로 큐에만 쌓고 닫힌 뒤 표시**.
- **지도 좌상단 +/- 줌 버튼**(`animateCamera(zoomBy ±1, 220ms)`), **별자리 페이드 인/아웃 + 후광 3겹**(halo/glow/line, `Animatable` 로 opacity 0↔target).
- **첫 실행 코치마크**: 7단계(마지막 중앙 "지금부터 우주를…" 메시지) + 텍스트 가운데 정렬. ⚠️ 마지막 단계 스포트라이트 r=0 → radialGradient 크래시 가드(`if r>0`).
- **내 다이어리 다이얼**: 컨테이너 박스 260→360dp(터치 감지·하단 텍스트 아래로 확장), 별자리 상단 260 고정(TopCenter),
  `DIAL_BOTTOM_DP` 150→100 보정으로 다이얼 절대 위치 유지.
- **빌드/서명**: 디버그도 릴리즈 keystore 로 서명(`build.gradle.kts` debug signingConfig) → Studio Run(debug) ↔ CLI 릴리즈 설치 시
  "서명이 다른 앱" 충돌 제거. (keystore.properties 없으면 기본 디버그 키 폴백)

## 8.5 기능 배치 1 (이번 라운드 추가 — 테스트는 콘솔 규칙 해제 후)
- **친구**: `shared` `FriendRepository`/`Friend`/`FriendRequest`/`UserProfile` + `FirebaseFriendRepository`
  (users/{uid}/friends 양방향, friendRequests 컬렉션, userName prefix 검색) + `feature/friend/` FriendScreen/ViewModel
  + NavRoute.Friends(드로어 "친구"). 로그인 시 `upsertProfile` 로 users/{uid} 공개 프로필 기록(검색용, fire-and-forget).
- **별 선택 업로드**: Diary += `starType`(0~4)/`starColor`(0~11). UploadScreen 피커(StarShapeIcon=마커와 동일 Path).
- **필터**: MainListScreen 칩 "미조회만"(users/{uid}/viewedDiaries — DetailScreen 진입 시 기록) / "친구만"(friends 기준).
- **FRIEND_POST 인앱 알림**: NotificationType.FRIEND_POST. saveDiary 성공 시 친구들에게 알림 문서 생성(fire-and-forget).
  푸시(FCM)는 Cloud Functions 필요 — 미구현(체크리스트 7/8).
- **안정화**: 스냅샷 리스너 `close(error)` 금지(권한 에러 크래시 방지), 로그인/저장 경로의 Firestore 부수 작업은
  전부 fire-and-forget, GIF 인트로 속도 상향.
- **위치 보기 버튼 삭제**(DetailScreen) — 100m 밖은 지도에서 거리 토스트만.
- **(라운드 2)** 로그인 = MainScreen **오버레이**(NavHost start=Main, 지도 선로딩 → 로그인 직후 즉시 표시),
  마커 위상 그룹 4개(따로 부유), iconSize 줌 보간(8→0.3x~15→1x), 팔레트 흰색 30% 혼합(밝게),
  팬/줌 중 애니메이션 일시정지 + GeoJSON 변화시에만 재생성(끊김 해소).
- **(라운드 3)** 별가루 파티클을 Compose Canvas(`StarParticleOverlay`) → **MapLibre GeoJSON+SymbolLayer 전환**(6절 참고).
- **FCM 클라이언트**: `push/StaryMessagingService`(data {diaryId,title,body} → 알림), 알림 탭 →
  `MainActivity` extra → Detail 딥링크, 토큰은 `users/{uid}.fcmToken`. **발송은 Cloud Functions 배포 필요**(체크리스트 8).
- **FCM 서버(코드 완료, 배포 대기)**: 루트 `firebase.json`/`.firebaserc`(default=momentdiary-f26c8) +
  `functions/`(node 20, firebase-admin 12 / firebase-functions 6 v2 API).
  `notifyFriendsOnDiaryCreate` = diaries onCreate(**database: stary-db** 명시) → 친구 fcmToken 수집(`db.getAll`) →
  `sendEachForMulticast`(500개 청크, android priority high) → 만료 토큰(`registration-token-not-registered`) 은
  users/{uid}.fcmToken 필드 삭제로 정리. ⚠️ `REGION`(현재 asia-northeast3)은 stary-db 리전과 일치 필수.
  배포: Blaze + `cd functions && npm install` + `firebase deploy --only functions`.

## 8.7 기능 배치 4 (BUILD SUCCESSFUL + 테스트 완료)
- **이미지 업로드 안정화/원인 추적**: `ImageUploadHelper` 가 업로드 직전 `ensureAuthenticated()`(세션 없으면
  `signInAnonymously().await()`)로 Auth 세션 보장 → Storage 규칙(`request.auth != null`) 통과. 실패 시
  실제 에러 메시지를 `Result(url,error)` 로 반환(기존엔 null 만 → 원인 묻힘). `UserRepository.uploadProfileImage` 도
  동일하게 세션 보장. `ProfileViewModel` 에 `uploadError` StateFlow 추가 → `ProfileScreen` 에서 토스트로 노출.
  - ⚠️ 경로의 userId 는 Google sub(JWT)라 Firebase uid 와 다름 → Storage 규칙에서 `auth.uid == userId` 쓰면 안 됨.
- **Storage 보안 규칙 파일화**: 루트 `storage.rules`(diary_images/profile_images = 읽기공개 + 로그인+이미지<10MB 쓰기,
  그 외 거부) + `firebase.json` 에 `"storage": {"rules":"storage.rules"}`. 배포: `firebase deploy --only storage`.
  - ⚠️ 원본 앱(momentdiary-52b78)은 Firebase Auth 세션을 안 만들어(익명/credential 로그인 없음) `request.auth` 항상 null.
    이 규칙을 원본에 적용하면 업로드 전부 거부됨 → 원본은 콘솔 버전기록 롤백 또는 오픈 규칙 필요(원본 코드 수정 금지).
- **미열람 알림 빨간 점**: `MainScreen` 하트 BadgedBox 배지를 민트 숫자 → 빨간 동그라미 점(0xFFFF3B30, 어두운 테두리).
- **커스텀 토스트**: `core/ui/StaryToast.kt` — 시스템 Toast(Android 12+ setView 무시) 대신 Compose 전역 오버레이
  `StaryToastHost`(MainScreen 최상단, 로그인 오버레이 포함 위). 남색 그라데이션+PoorStory 폰트. 호출은 `StaryToast.show(msg)`.
  기존 `Toast.makeText` 10곳 전부 교체(Profile/Login/MainList/Friend/DiaryMap/Upload).
- **앱 아이콘**: `AndroidManifest` icon/roundIcon → `@drawable/app_image`. (런처에 따라 사각 PNG 그대로 보일 수 있음;
  어댑티브 마스킹 원하면 별도 작업 필요.)
- **토스트 확장**: 댓글 작성/삭제·좋아요(하트)·칭호 장착/해제에도 `StaryToast` 적용(DetailScreen/AchievementsScreen).
- **미열람 알림 빨간 점 버그픽스**: ⚠️ Kotlin `Boolean isRead` 는 Firestore 에 **`read`** 필드로 저장됨(getter "is" 접두 제거).
  쿼리/업데이트가 `"isRead"` 였어서 항상 0건 → `read` 로 수정(`FirebaseNotificationRepository`). 빨간 점 위로 살짝(-2dp)+테두리 제거.
- **스플래시 완전 검정**: `values/themes.xml` windowBackground=검정, `values-v31/themes.xml` 시스템 스플래시 배경 검정 +
  아이콘 숨김(`drawable/splash_icon_none` 투명). 콜드스타트 흰 번쩍임 제거.
- **내 다이어리 배경**: `MyDiaryScreen` 을 Box 로 감싸 `drawable/mydiary_bg`(업로드와 동일 밝기) 깔음.
- **정렬 효과음**: `MusicManager.playWind()`(`res/raw/wind.mp3`, 배경음악과 별개 SFX 플레이어, enabled 시만). 정렬 변경 시 호출.
- **바나나 다이얼 수정**: 드래그 중엔 회전만, 선택 이벤트는 놓을 때/클릭 시·이전과 다를 때만(onDragCancel 추가).
- **거리순 수정**: `DiaryStarBox.here` 가 위치 캐시 null 이면 `getCurrentLocation` 비동기 측정해 채움(이전엔 거리순 무반응).
- **별자리 실제 배치**: `MyDiaryScreen.CONSTELLATIONS` = 최신순 사수자리(Teapot)/인기순 처녀자리/거리순 전갈자리(Scorpius).

## 8.6 기능 배치 3 (BUILD SUCCESSFUL 확인됨)
- **UploadScreen 무한 캐러셀**: 별 모양/색상 피커를 `HorizontalPager`(pageCount=10_000, initialPage=5000-based)로 교체.
  - 중앙 외 페이지: `graphicsLayer(scale/alpha)` 로 페이드+축소 효과. `contentPadding` 으로 양쪽 미리보기.
  - 별 모양 아이콘: `StarShapeIcon`(56px 박스+RoundedCorner18) 선택 시 mint 테두리/배경. 색상: CircleShape 원형 슬롯.
- **MainListScreen 필터 스피드 다이얼**: 기존 수평 칩 Row 제거 → 좌측 하단 원형 FAB + `AnimatedVisibility`(expandVertically).
  - 5가지 옵션 pill(전체보기/미조회만/친구만/나만보기/친구선택). 선택된 필터는 mint 색상 강조.
  - FAB 자체도 활성 필터 있으면 mint 테두리로 표시.
  - `private FilterOpt` data class로 옵션 정의(ImageVector 사용).
- **MapLibre 워터마크 제거**: `map.uiSettings { isLogoEnabled=false; isAttributionEnabled=false }`.

## 8.7 기능 배치 4 — 다이얼/별자리/업적 해금 (BUILD SUCCESSFUL)
- **내 다이어리 별자리**: `MyDiaryScreen.CONSTELLATIONS` 를 `drawable/reference{1,2,3}.png`(미사용 참고 이미지, 미커밋) 픽셀
  분석으로 옮긴 `CStar(x,y,mag)`+edges 로 교체. 정렬별 색 = 최신순 파랑/인기순 분홍/거리순 보라(`sortColor`).
  별마다 후광 pulse(무한 twinkle) + `onSelect` 시 전체 번쩍(flash 1.7→0.78) `sortNonce` 연동.
- **바나나 다이얼**: 원호→포물선(`DIAL_H_SPACING/CURVE/BOTTOM`). 드래그 방향 반전, 세 버튼 모양 구분(`dialStarType`).
  - **터치 영역 = 별자리 박스 전체**(`matchParentSize`), `DIAL_BOTTOM_DP=100` 으로 별이 박스 안에 들어와 아래쪽 탭도 인식.
  - 선택은 `!=selected` 가드 제거(기본 최신순 재선택도 동작) + `sortNonce` 로 같은 정렬 재선택도 재정렬.
- **wind SFX**: `MusicManager` 효과음을 `MediaPlayer`→`SoundPool`(미리 로드, USAGE_MEDIA) 로 교체(지연/묵음 해결).
- **친구 화면**: `FriendScreen` 카드형 리팩토링(아바타 링, pill 버튼, 배경).
- **별 모양/색 업적 해금**: `Achievement.reward` = `Reward.Title|Shape|StarColor` 로 칭호 업적과 별·색 업적 **분리**.
  `StarUnlocks` 는 보상 정의에서 자동 도출. 업로드 피커의 잠긴 항목은 흐릿+자물쇠, 탭/저장 시 해금 토스트.
  - 새 통계: `UserStats.maxSpanMeters/maxLikesOnOne/distinctDays/nightPosts`(`rememberUserStats` 가 좌표·시각으로 계산).
  - 창의적 업적: 친구 N명/기록 거리(50km·1000km)/심야 기록/서로 다른 N일 등.
- **창의적 별 모양**(`StarStyle` TYPE_COUNT=8): 5=꽃 / 6=보석 / 7=초승달(반시계 22° 회전). 0~4 별/스파클 유지.
- **그라데이션 색**(COLOR_COUNT=20): 16~19 2색 그라데이션(`fillShader` LinearGradient) — 지도·내다이어리·카드·피커 일관 적용.
  가장 어려운 업적(좋아요 300/친구 20/100개 작성/조회 1000)에 배치.
- **업적 화면**: `AchievementsScreen` 「칭호」/「별 모양·색」 2섹션, 보상 미리보기. 배경 = `mydiary_bg`(0.7 darken).

## 8.39 출시 준비 배치 — 용량 경량화 / 인스타 스토리 / 개척 카운트다운 / 크리스탈 아이콘 (BUILD SUCCESSFUL)
- **용량 경량화**(앱 리소스 ~90MB → ~28MB):
  - `res/drawable` PNG/JPG 6장 → **WebP**(`app_image`/`image_frame`/`logo`/`mydiary_bg`/`mypage_bg`/`upload_bg`, 총 12.6MB → 1.1MB).
    파일명(리소스 id)은 그대로라 코드 수정 불필요.
  - BGM 6곡 재인코딩(33MB → 22MB), `login_video.mp4` 25MB → 3.1MB, `assets/earth_*.jpg`(지구본 텍스처) 5.1MB → 1.6MB.
  - `res/raw/keep.xml` 신설 — ⚠️ `MusicManager` 가 `Resources.getIdentifier()` 로 BGM/SFX 를 **동적 참조**해서
    `isShrinkResources=true` 릴리즈에서 R8 이 미사용으로 오판·제거한다. `tools:keep` 로 bgm 6곡 + open_diary/wind/turning_dial 보호.
  - 디버그 APK 100MB 는 정상(전 ABI 네이티브 41.9MB + 미난독화 dex). 릴리즈 AAB 는 ABI 분할 + minify/shrink 적용.
- **인스타 스토리 직접 공유**(`ShareCardHelper.shareToInstagramStory`, ShareCardEditor 의 인스타 버튼):
  - `AndroidManifest` 에 `<queries><package android:name="com.instagram.android"/></queries>` — Android 11+ 패키지 가시성 없으면 인텐트 해석 실패.
  - `BuildConfig.INSTAGRAM_APP_ID` ← `secrets.properties` 의 `INSTAGRAM_APP_ID`(Facebook 앱 ID, 없으면 빈 값).
    ⚠️ 링크스티커(`content_url`)는 **Meta 앱 ID 등록 + 인스타 인정**이 있어야 붙는다 → 미등록이면 조용히 무시되므로
    항상 링크를 클립보드에 복사하고 시스템 Toast 로 "링크 스티커에 붙여넣기" 안내(폴백).
- **개척 퀘스트 카운트다운**: `pioneer_quest_toast` 를 「%1$s에서 처음으로 별을 만들어 특별한 칭호를 얻으세요.\n(%2$d일 %3$d시간 후 나라 변경)」로 교체
  (ko/en/ja). 남은 시간은 `shared` 의 `PioneerQuest.daysHoursUntilCountryChange(nowMs)` — iOS `PioneerQuest.swift` 에 동일 구현.
- **부유 통계 아이콘 = 크리스탈**(`FloatingStatBox`): 벡터 아이콘 틴트(`ColorFilter.tint`) → **별과 같은 크리스탈 파편** 채움.
  - `StarStyle.drawCrystalFacets(canvas, silhouette=null, ...)` 신설(=`drawCrystalFill` 의 임의 실루엣/무클립 버전).
  - 파편 무늬는 정적 → `bakeCrystalIcon()` 이 아이콘을 알파 마스크로 깔고 **SRC_IN 레이어**에 파편을 그려 `ImageBitmap` 으로 **1회만 굽고**,
    매 프레임엔 `drawImage(dstOffset/dstSize)` 로 스케일·회전만(파티클 버스트까지 매 프레임 파편을 그리면 비싸다).
- **iOS 패리티**: 개척 카운트다운(`LocalizedNames.pioneerQuestMessage`), 겹친별 카드 = 사진 대신 **항상 별**(`StarClusterView`) 반영.
- **웹 랜딩**(`web/index.html`): 미출시 상태라 스토어 버튼을 '준비 중'(비활성)으로. 출시 후 `STORE_URL_ANDROID/IOS` 상수만 채우면 활성화.
- 버전: `versionCode 5` / `versionName 1.3.0`.

## 9. 남은 작업 / TODO (다음에 할 것)
- [ ] **iOS: 공유 카드 편집 화면(`ShareCardEditor`) + 인스타 스토리 직접 공유 미구현** — Android 는 편집 화면 안의 인스타 버튼이 진입점인데
      iOS 는 `ShareCard.share()`(시스템 시트)만 있다. 이식 시 `project.yml` 에 `LSApplicationQueriesSchemes: [instagram-stories]` +
      `INSTAGRAM_APP_ID` 주입, `UIPasteboard`(com.instagram.sharedSticker.backgroundImage) + `instagram-stories://share` 필요.
- [ ] iOS 앱(Xcode 프로젝트) 추가 + iOS용 Repository 구현(Firebase iOS SDK) — 현재 `shared` 스캐폴딩만(iosX64/Arm64/Sim 타깃만, iosApp/.xcodeproj 없음). **iOS 빌드·실행은 macOS+Xcode 필요(Windows 불가).**
- [x] 실제 `secrets.properties` / `google-services.json`(f26c8) 채워 런타임 확인 — 지도·Google 로그인 동작 확인됨.
- [x] 지도 엔진 Google Maps → **MapLibre + MapTiler** 전환 + 커스텀 스타일(검정/물/큰길, 줌 색보간) — 동작 확인.
- [x] 다이어리 별 마커(종류0~4×색0~11) 커스텀 렌더 + 클릭 100m 게이팅 — 완료(길찾기는 사용자 결정으로 미구현/삭제).
- [x] 별가루 파티클 Canvas → MapLibre GeoJSON+SymbolLayer 전환(줌 6 이하 숨김) — 완료.
- [ ] **FCM 푸시 발송 Function 배포(사용자)**: Blaze + `cd functions && npm install` + `firebase deploy --only functions`
      (코드는 `functions/index.js` 완료, REGION=stary-db 리전 확인).
- [ ] ViewModel 들이 Firebase* 구현 대신 공용 인터페이스 타입을 주입받도록 DI 정리(현재는 직접 생성).
- [x] GitHub remote(`origin` = Chaminwoo/Stary) 연결 + 푸시 완료(main).

## 10. 빠른 네비게이션 (기능 → 파일)
| 하고 싶은 일 | 파일 |
|---|---|
| 지도/스타일/마커 수정 | `feature/map/screen/DiaryMap.kt`, `res/raw/maplibre_style.json`, `feature/home/screen/MainListScreen.kt` |
| 다이어리 CRUD/쿼리 | `data/repository/FirebaseDiaryRepository.kt` (+ 인터페이스 `shared/.../Repositories.kt`) |
| 좋아요/댓글/알림 | `FirebaseLikeRepository`, `FirebaseCommentRepository`, `FirebaseNotificationRepository`, `InteractionViewModel` |
| 로그인/인증 | `feature/auth/GoogleAuthHelper.kt`, `LoginScreen.kt` |
| 좌표/거리 공용 로직 | `shared/.../core/geo/LatLng.kt`, `GeoUtils.kt`, `core/util/LocationHelper.kt` |
| 상수/설정/민감값 계약 | `shared/.../shared/config/StaryConfig.kt`, `Secrets.kt` |
| 키/시크릿 주입 | `androidApp/build.gradle.kts`, `secrets.properties`(MAPTILER_KEY / GOOGLE_WEB_CLIENT_ID) |
