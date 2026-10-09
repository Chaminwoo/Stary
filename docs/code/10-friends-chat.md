# 10. 친구 · 채팅

Android: `feature/friend/screen/FriendScreen.kt`, `feature/friend/FriendViewModel.kt`,
`feature/chat/screen/ChatScreen.kt`, `feature/chat/ChatViewModel.kt`, `core/util/ChatReadStore.kt`
iOS: `Features/Friends/FriendsScreen.swift`, `FriendsViewModel.swift`,
`Features/Chat/ChatScreen.swift`, `ChatViewModel.swift`, `Data/ChatReadStore.swift`, `Data/InviteStore.swift`

---

## FriendViewModel.kt

- `friends` : 내 친구 목록 실시간(StateFlow, `FriendRepository.observeFriends`).
- `incomingRequests` : 받은 친구 요청 목록. `outgoingRequests` : **내가 보낸 pending 요청** —
  검색 결과의 "요청됨" 상태 칩 표시용.
- `searchResults` / `isSearching` : 닉네임 검색 결과/진행. 결과 2명 이상이면 나와 **공통 친구 많은 순**
  정렬(8.28).
- `event : SharedFlow<String>` : 토스트 문구 방출(요청 전송/수락/거절/삭제 결과) —
  화면이 collect 해 `StaryToast.show`.
- `search(query)` / `clearSearch()` / `sendRequest(to)` / `accept(request)` / `decline(request)` /
  `remove(friendId, friendName)`.
- `factory(me: UserProfile)` 로 생성(내 프로필 정보를 요청 문서에 박제).

## FriendScreen.kt — 친구 화면(메신저형)

- 상단: 검색창(닉네임) + 검색 결과(PersonCard — 아바타 탭=프로필, 상태 칩: 친구/요청됨/추가).
- 받은 요청 섹션: 수락/거절.
- 친구 행(메신저 스타일): 아바타(탭=프로필) + 이름/최근 메시지 + 미읽음 파란 점(`ChatReadStore`) +
  **행 최우측 = 그 친구의 최근 공개 별**(비공개/익명 제외) — 탭하면
  `onOpenDiaryOnMap(diaryId)` → NavGraph 가 `MapFocusState.request(id)` 로
  지도 카메라 이동 + 파장 연출(도보 길찾기는 2026-09-21 삭제). 행 탭 = 채팅.
- **행 순서 = 최신 대화순**(`sortedFriends` = 방 `updatedAt` 내림차순, 2026-09-21).
  대화가 없는 친구는 0 이라 뒤로 밀리고 그들끼리는 원래 순서 유지(안정 정렬).
  iOS `FriendsScreen.sortedFriends` 가 같은 규칙(enumerated + offset tiebreak).
- 친구 초대 링크 공유(체크리스트 31): `stary://invite/{내uid}` 링크 생성·공유 —
  받은 쪽은 로그인 후 자동 리딤(FirebaseInviteRepository, 12 문서).
- `FirstVisitInfo("info_friends")` 1회 안내.

## ChatViewModel.kt / ChatScreen.kt / ChatReadStore.kt

- `chatId = StaryConfig.chatId(myId, friendId)` : 두 uid 를 정렬해 만드는 **결정적 방 id**(공용 규칙).
- `messages` : `FirebaseChatRepository.observeMessages(chatId)` 실시간.
- `send(text)` : **방 메타(chats/{chatId}) 먼저 → 메시지 문서** 순서로 쓴다(양 플랫폼 동일).

### ⚠️ chats 보안 규칙 — 판정 방식을 용도별로 **섞어** 써야 한다(8.45, 두 번 데임)

| 대상 | 판정 | 이유 |
|---|---|---|
| `chats` **목록 쿼리**(`whereArrayContains("participants", 나)`) · 방 문서 읽기/수정 | `myAppUserId() in resource.data.participants` | 쿼리 제약과 규칙 조건이 **같은 형태**여야 list 가 통과한다. chatId(문서 id) 기반 조건은 규칙 엔진이 쿼리 결과를 증명할 수 없어 **쿼리 전체가 거부** → 친구 화면이 "아직 채팅이 없어요"로 뜬다 |
| 방 문서 **생성** · `messages` 하위 전부 | `myAppUserId() in chatId.split('_')` (`isChatMember`) | 방 문서가 **아직 없는 첫 메시지**에서도 판정돼야 한다. `resource`/`get(chats/{chatId})` 는 null 이라 항상 거부됐고, 그게 "새 상대(iOS↔Android)와 채팅이 아예 안 가던" 원인 |

한쪽으로 통일하면 반드시 다른 쪽이 깨진다(첫 채팅 불통 ↔ 목록 미표시). 규칙을 고치면
**반드시 배포**: `firebase deploy --only firestore:rules`.
- `canDelete(message)` / `deleteMessage(message)` : **내가 보낸 메시지 + 1분 이내**
  (`StaryConfig.CHAT_DELETE_WINDOW_MS`)만 완전 삭제(상대 쪽에서도 사라짐). 1분이 지나 누르면 `ChatEvent.DELETE_EXPIRED` 토스트.

### 나에게서만 삭제(2026-10-07) — 메시지 하나 / 대화 내용 전체

공용 문서(`chats/{chatId}`, `messages`)는 **건드리지 않고**, 내 전용 문서 `users/{나}/chatHidden/{chatId}`
(shared `core/model/ChatHidden.kt` ↔ iOS `Models.swift` `ChatHidden`)로 내 화면에서만 가린다. 상대 대화방엔 그대로.
규칙: `firestore.rules` 의 `users/{uid}/chatHidden/{chatId}` = 본인만 읽기/쓰기(상대가 내가 뭘 지웠는지 못 보게 공용 방 문서에 두지 않았다).
**규칙을 배포해야 동작한다**(`firebase deploy --only firestore:rules`) — 미배포면 구독은 권한 오류 → 숨김 없이 표시, 삭제 시 "삭제하지 못했어요".

| 필드 | 의미 |
|---|---|
| `messageIds` | 메시지 하나씩 나만 삭제한 id(arrayUnion) |
| `clearedAt` | 대화 내용 나만 삭제 — createdAt ≤ 이 값인 메시지를 가린다. ⚠️ 내 기기 시각이 아니라 **그때 방의 마지막 메시지 createdAt**(+방 메타 updatedAt 중 큰 값). createdAt 은 보낸 사람 기기 시각이라 내 시계로 자르면 시계가 늦은 상대의 새 메시지가 숨을 수 있다 |
| `previewFor`/`previewText`/`previewAt`/`previewSenderId` | 친구 목록 미리보기 대체값. 방 메타 `lastMessage` 는 공용이라 내가 숨긴 마지막 메시지가 그대로 보이므로, 숨길 때 "그 시점 방의 마지막 메시지 시각"과 "내게 남은 마지막 메시지"를 적어 두고 방 `updatedAt ≤ previewFor`(그 뒤 새 메시지 없음)인 동안만 쓴다 |

- **메시지 롱프레스(모든 메시지)** → 선택 팝업: "나에게서만 삭제"(언제나) + "모두에게서 삭제"(내 메시지 1분 이내일 때만).
  Android `AlertDialog`(본문에 TextButton 목록) / iOS `staryChoiceDialog`. 예전엔 롱프레스가 내 메시지 1분 이내에만 반응했다.
  1분 링(`DeleteWindowRing`)은 "모두에게서 삭제"가 남은 시간 표시로 의미가 바뀌었다(Android `showDeleteRing` 파라미터).
- **대화 내용 삭제(나에게서만)** — 진입점 2곳:
  - 채팅 화면 탑바 ⋮ → "대화 내용 삭제" → 확인 팝업. Android 는 `core/util/ChatActionState`(ProfilePinState 패턴 전역 브리지 —
    ChatScreen 이 등록, MainScreen 탑바가 `ownerKey == 현재 friendId` 일 때 ⋮ 노출. 채팅→배너→다른 채팅 전환에서 옛 화면 해제가
    새 등록을 지우지 않게 key 비교 후 해제). iOS 는 `ChatScreen` 툴바 `Menu`.
  - 친구 목록 행 길게 누르기(보이는 대화가 있을 때만) → 같은 확인 팝업. Android `combinedClickable(onLongClick)`, iOS `.contextMenu`.
  - 지운 뒤 새 메시지는 정상으로 보인다(clearedAt 이후). 지울 게 없으면 "삭제할 대화가 없어요".
- VM: Android `ChatViewModel.messages` = `combine(allMessages, hidden)` 필터 결과, `hideForMe`/`clearForMe`/`event: SharedFlow<ChatEvent>`(리소스 id).
  저장소: `FirebaseChatRepository.observeHidden`/`observeAllHidden`/`hideMessageForMe`/`clearChatForMe`(shared `ChatRepository` 계약에 추가).
  iOS: `ChatViewModel.hideForMe`/`clearForMe`/`static clearChatForMe`(친구 목록도 사용), `FriendsViewModel.hiddenByChat` + `preview(of:)`.
- 친구 목록 행 미리보기·미읽음·**정렬 기준이 방 메타가 아니라 `ChatHidden.preview(...)` 결과**로 바뀌었다(나만 삭제한 대화는 "아직 대화가 없어요" + 뒤로).
- 알려진 한계: "모두에게서 삭제"(1분)는 방 메타 미리보기를 갱신하지 않는다(기존과 동일). 대화 나만 삭제는 방 메타 updatedAt 까지 덮어 이 경우도 비워진다.
- `ChatScreen(friendId, friendName)` : 말풍선 목록 + 입력창.
  - **내 말풍선 = 파랑→남색 그라데이션**(`MineBubble` 0xFF2F4C9E→0xFF1B2A5E) + 남색 테두리.
    예전 초록 단색(0xFF6EE7B7)은 남색으로 개편된 앱 톤에서 혼자 튀었다. 입력창 포커스/커서/전송
    버튼도 같은 남색(`Accent` 0xFF9FB3E8)으로 통일.
  - **삭제 가능 링 타이머**(`DeleteWindowRing`) : 내 메시지 왼쪽에 1분 잔여 시간이 줄어드는 원호.
    0 이 되면 사라진다(그때부터 롱프레스 삭제도 막힌다). 1초 주기 갱신.
  - **방금 보낸 메시지 등장 연출** : 화면 진입 시각(`sessionStartedAt`) 이후 내가 보낸 것만
    아래에서 떠오르며 별가루가 흩어진다(과거 메시지는 조용히 — 스크롤 시 재생 방지).
  - 빈 대화는 `StaryEmptyState`(02 문서). 전송/롱프레스에 `Haptics.light()`.
  - 진입/메시지 수신 시 `ChatReadStore.markRead` — 친구 목록 미읽음 점이 즉시 꺼진다.
  - **입력 바 하단 여백은 한 번만**: `windowInsetsPadding(WindowInsets.safeDrawing.only(Bottom))`
    (= 키보드가 있으면 키보드 높이, 없으면 내비바 높이). `navigationBarsPadding()+imePadding()` 을
    이어 붙이면 키보드 위로 내비바 높이만큼 더 떠오른다(8.45 수정).
    Manifest 의 `windowSoftInputMode="adjustResize"` 와 세트 — 창이 통째로 밀려 올라가는 것 방지.
- `ChatReadStore` : chatId → 마지막으로 본 시각(ms) 로컬 저장(Compose 상태 맵 겸용).
  미읽음 판정 = 방 updatedAt > lastReadAt && 마지막 발신자가 내가 아님. **기기 로컬 기준**(허용 범위).
- 채팅 FCM 알림/딥링크는 11 문서(StaryMessagingService → DeepLinkState → MainScreen).

---

## iOS 대응

- `FriendsViewModel.swift` : friends/incoming/`outgoingIds`(요청됨 칩)/검색(공통 친구 정렬) —
  Android 와 같은 구성. 요청 전송 토스트(`friendRequestSent/Fail`).
- `FriendsScreen.swift` : 메신저형 행(행 탭=채팅, 아바타 위 투명 버튼=프로필 push),
  행 최우측 최근 별 버튼 → `MapFocusStore.request(diaryId:)`. 행 순서도 최신 대화순(Android 동일).
- `ChatViewModel.swift` / `ChatScreen.swift` : 같은 chatId 규칙/1분 삭제/읽음 처리.
  채팅 타이틀(principal 툴바)에 `HiddenStarBadges`. 말풍선/삭제 링/등장 연출은 Android 와 동일
  (`SentAppear` ViewModifier + `DeleteWindowRing`). ⚠️ iOS 는 **빈 대화 안내가 아직 없다**(Android `chat_empty`) — TODO.
  - `send` 는 **Bool 반환** — 실패 시 입력 내용을 되돌리고 토스트(`chatSendFailed`). 조용한 실패 금지.
  - ⚠️ **배경은 ZStack 형제가 아니라 `.background { ScreenBackground(...) }`** — `ignoresSafeArea` 배경을
    ZStack 에 형제로 두면 스택이 키보드 영역까지 커져 입력 바가 키보드 뒤에 깔린다(8.45 수정).
    입력 바 배경은 `.background(Theme.background)`(ShapeStyle 오버로드 — 하단 안전영역까지 자연히 이어짐).
    여기에 `ignoresSafeArea(.all)` 짜리 뷰를 넣으면 키보드 영역까지 무시해 같은 증상이 재발한다.
- `InviteStore.swift` : 초대 딥링크 보관/리딤(비로그인 시 보관 → 로그인 후 처리).

### iOS 초대 링크 활성화 (2026-10-08)
사용자: "친구 초대 링크 및 로직 iOS 도 제대로 활성화 (iOS 앱 링크 `https://apps.apple.com/us/app/stary/id6799375537`)".
진단: 앱 안의 리딤 로직(`InviteStore`)은 이미 Android 와 같았다. 막혀 있던 건 **링크가 앱으로 이어지는 길**이었다.
- **웹 랜딩(`web/index.html`)**: iOS 기기에서 `STORE_URL_IOS` 가 비어 "iOS 앱은 준비 중이에요"만 나왔다 → App Store 링크를 채워 "App Store 에서 설치" 버튼이 뜬다.
  ⚠️ **iOS 는 "버튼 누르고 1.4초 뒤 스토어로 보내는 미설치 폴백"을 끈다**(`isIOS` 제외) — 앱이 설치돼 있으면 Safari 가 "Stary 에서 열까요?" 확인창을 띄우는데
  그동안 페이지는 그대로 보여, 타이머가 확인창 위로 App Store 를 덮어 버린다. iPadOS Safari(UA 가 Macintosh)도 `maxTouchPoints` 로 iOS 취급.
- **상수**: `AppConfig.appStoreUrl` ↔ `StaryConfig.APP_STORE_URL` ↔ `web/index.html STORE_URL_IOS` (3곳 동기화).
- **유니버설 링크(앱이 https 링크를 직접 처리)**: `StaryApp.appLink(_:)` 가 `stary://diary|invite/{id}` 와 `https://{shareHost}/s|i/{id}` 를 같은 분기로 처리
  (Android App Links 와 같은 동작 — 카톡/문자에서 링크를 누르면 웹을 거치지 않고 앱이 열림). `project.yml` 에 `com.apple.developer.associated-domains: applinks:momentdiary-f26c8.web.app` 추가,
  `web/apple-app-site-association`(+ 표준 위치 `web/.well-known/apple-app-site-association`, `firebase.json` 에 Content-Type 헤더).
- ⚠️ **사용자가 해야 할 일**
  1. ~~AASA 의 `TEAMID` 교체~~ — **완료(2026-10-08)**: 두 파일 모두 `3G3447GK74.com.chaminwoo.stary.ios`. Team ID 는 비밀이 아니라 커밋해도 된다
     (developer.apple.com → Membership = CI 시크릿 `IOS_DEVELOPMENT_TEAM` 값). 이게 틀려도 앱은 정상 — 커스텀 스킴 폴백으로 동작할 뿐 유니버설 링크만 안 먹는다.
  2. **웹 배포**: `firebase deploy --only hosting` (랜딩 + AASA). 배포 전에는 iOS 기기 랜딩이 예전 그대로다. 유니버설 링크는 앱을 **새로 설치/업데이트한 뒤**부터 적용(iOS 가 설치 때 AASA 를 읽는다).
- 흐름(미설치 친구): 링크 → 랜딩 "App Store 에서 설치" → 설치·가입 → **링크를 다시 열기**(유니버설 링크면 앱이 바로, 아니면 랜딩의 "앱에서 초대 수락") → `InviteStore` 리딤 → 양쪽 칭호.
  iOS 는 설치 직후 초대 정보를 이어받는 "지연 딥링크"가 없어 "다시 열기" 안내가 필요하다(랜딩 문구에 이미 있음).
- 친구 요청 전송 시 상대에게 알림 문서 생성(`notifyFriendRequest`) → 인앱 배너 + 푸시(11 문서).
- iOS 채팅/알림 푸시(APNs)는 **8.45 에서 구현**(`Data/PushManager.swift`) — 11 문서 참고.

### 웹 초대 랜딩(`/i/{uid}`) — 3D 글로브 + "초대 수락" / "친구 초대하기" (2026-10-09)
사용자: "친구 초대 웹페이지에서 지금 쓰는 글로브를 띄워주고, 친구 초대 버튼, 수락 버튼 띄우고 싶어".
- **글로브**: `web/globe/globe.js` = 앱 글로브(Android `GlobeRenderer.kt`, 04 문서)를 **WebGL1 로 포팅**. 유리 지구(`EARTH_FS`)·성운 하늘·대기광·궤도 링·별빛 셰이더는 앱과 **같은 식·같은 값**(GLSL ES 1.00 이라 그대로 동작).
  텍스처는 앱 에셋의 **복사본** `web/globe/globe_land.jpg`(4096×2048 육지 마스크)·`globe_nebula.jpg` — 앱에서 `tools/globe` 로 다시 구우면 **이쪽도 다시 복사**할 것.
  웹 전용 단순화: 별자리 선·은하수 띠·유성·태양 원반 생략, 다이어리 별은 실제 데이터(로그인 필요) 대신 **장식용 도시 별**(`CITIES`). 실제 UTC 태양 방향으로 낮/밤이 맞고, 드래그 회전·관성·휠 줌·느린 자동 회전(`prefers-reduced-motion` 이면 정지).
  작은 화면(<700px)은 육지 마스크를 2048 로 줄여 올려 메모리를 아낀다. WebGL 불가/컨텍스트 소실/셰이더 실패 → `body.globe-on` 이 빠져 **예전 별 아이콘 화면으로 폴백**(`startSky` 로 잔별 캔버스 시작).
  탭이 백그라운드(`document.hidden`)면 프레임을 그리지 않는다 — 자동화 브라우저에서 "검게 보이면" 먼저 이걸 의심.
- **버튼**: "초대 수락"(`#openApp`, `stary://invite/{uid}` — 기존 동작: 안드로이드는 1.4초 뒤 미설치 스토어 폴백, iOS 는 폴백 없음) + "친구 초대하기"(`#share` — **이 초대 링크**를 Web Share API 로 공유, 안 되면 클립보드 복사 + 토스트) + 스토어 설치 링크.
  ⚠️ "친구 초대하기"는 **랜딩에 온 사람이 이 링크를 다른 친구에게도 전달**하는 버튼이다(웹은 로그인 정보가 없어 방문자 본인의 초대 링크를 만들 수 없다). 방문자 본인의 링크는 앱 안 친구 화면의 초대 버튼에서.
- **초대자 이름 표시는 안 한다** — `users/{uid}` 읽기는 로그인 세션이 필요해(규칙) 비로그인 웹에서 못 읽는다. 필요하면 Cloud Function 으로 공개 필드만 내려주는 엔드포인트가 필요.
- 레이아웃: 세로 = 위 글로브/아래 패널, 가로 넓은 화면(≥820px·가로형) = 왼쪽 글로브/오른쪽 패널(`index.html` CSS 미디어쿼리와 `globeBox()` 조건 동일). 공유 페이지(`/s/{id}`)는 변경 없음(글로브 미사용).
- 로컬 확인: `web/` 을 정적 서버로 띄우되 `/i/x` → `index.html` 로 되돌려 줘야 한다(firebase.json rewrites 와 같게). 배포는 `firebase deploy --only hosting`.

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 채팅방 id 규칙 | shared `StaryConfig.chatId(a,b)` | `AppConfig.chatId` (**규칙 동일 필수** — 다르면 방이 갈라짐) |
| 메시지 삭제 허용 시간(1분) | shared `StaryConfig.CHAT_DELETE_WINDOW_MS` | `AppConfig`(동일 값) |
| 나만 삭제 문서 경로 | `StaryConfig.Collections.CHAT_HIDDEN`(users/{uid}/chatHidden/{chatId}) | `AppConfig.Collections.chatHidden` + `FirestoreService.chatHidden(of:)` |
| 나만 삭제 판정/미리보기 | shared `ChatHidden.hides` / `preview` | `Models.swift` `ChatHidden.hides` / `preview` (**판정 동일 필수**) |
| 미읽음 판정 | `ChatReadStore`(로컬) | `ChatReadStore.swift`(로컬) |
| 초대 링크 | `stary://invite/{uid}` (StaryConfig) | `AppConfig.deepLinkHostInvite` |
| 초대 https 링크(`/i/{uid}`) | 매니페스트 App Links(autoVerify) → `MainActivity` | `StaryApp.appLink` + Associated Domains(`project.yml`) + AASA (**호스트/경로 동일: `SHARE_HOST`/`shareHost`**) |
| 스토어 링크 | `StaryConfig.PLAY_STORE_URL` / `APP_STORE_URL` | `AppConfig.playStoreUrl` / `appStoreUrl` (+ `web/index.html`) |
| 내 말풍선 색 | `ChatScreen.kt` MineBubble(0xFF2F4C9E→0xFF1B2A5E) | `ChatScreen.swift` 같은 hex LinearGradient |
| 삭제 링/등장 연출 | `DeleteWindowRing`·appear 애니 | `DeleteWindowRing`·`SentAppear` (**값 동일**) |
