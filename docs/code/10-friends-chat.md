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
  `onOpenDiaryOnMap(diaryId)` → NavGraph 가 `MapFocusState.request(id, withRoute=true)` 로
  지도 파동+도보 길찾기. 행 탭 = 채팅.
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
  (`StaryConfig.CHAT_DELETE_WINDOW_MS`)만 완전 삭제(상대 쪽에서도 사라짐).
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
  행 최우측 최근 별 버튼 → `MapFocusStore.request(diaryId, withRoute: true)`.
- `ChatViewModel.swift` / `ChatScreen.swift` : 같은 chatId 규칙/1분 삭제/읽음 처리.
  채팅 타이틀(principal 툴바)에 `HiddenStarBadges`. 말풍선/삭제 링/등장 연출은 Android 와 동일
  (`SentAppear` ViewModifier + `DeleteWindowRing`). ⚠️ iOS 는 **빈 대화 안내가 아직 없다**(Android `chat_empty`) — TODO.
  - `send` 는 **Bool 반환** — 실패 시 입력 내용을 되돌리고 토스트(`chatSendFailed`). 조용한 실패 금지.
  - ⚠️ **배경은 ZStack 형제가 아니라 `.background { ScreenBackground(...) }`** — `ignoresSafeArea` 배경을
    ZStack 에 형제로 두면 스택이 키보드 영역까지 커져 입력 바가 키보드 뒤에 깔린다(8.45 수정).
    입력 바 배경은 `.background(Theme.background)`(ShapeStyle 오버로드 — 하단 안전영역까지 자연히 이어짐).
    여기에 `ignoresSafeArea(.all)` 짜리 뷰를 넣으면 키보드 영역까지 무시해 같은 증상이 재발한다.
- `InviteStore.swift` : 초대 딥링크 보관/리딤(비로그인 시 보관 → 로그인 후 처리).
- 친구 요청 전송 시 상대에게 알림 문서 생성(`notifyFriendRequest`) → 인앱 배너 + 푸시(11 문서).
- iOS 채팅/알림 푸시(APNs)는 **8.45 에서 구현**(`Data/PushManager.swift`) — 11 문서 참고.

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 채팅방 id 규칙 | shared `StaryConfig.chatId(a,b)` | `AppConfig.chatId` (**규칙 동일 필수** — 다르면 방이 갈라짐) |
| 메시지 삭제 허용 시간(1분) | shared `StaryConfig.CHAT_DELETE_WINDOW_MS` | `AppConfig`(동일 값) |
| 미읽음 판정 | `ChatReadStore`(로컬) | `ChatReadStore.swift`(로컬) |
| 초대 링크 | `stary://invite/{uid}` (StaryConfig) | `AppConfig.deepLinkHostInvite` |
| 내 말풍선 색 | `ChatScreen.kt` MineBubble(0xFF2F4C9E→0xFF1B2A5E) | `ChatScreen.swift` 같은 hex LinearGradient |
| 삭제 링/등장 연출 | `DeleteWindowRing`·appear 애니 | `DeleteWindowRing`·`SentAppear` (**값 동일**) |
