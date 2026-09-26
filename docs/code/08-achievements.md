# 08. 업적 · 히든 업적 · 칭호 · 개척 퀘스트

Android: `feature/profile/Achievements.kt`, `HiddenAchievements.kt`, `HiddenClaimStore.kt`,
`HiddenParticles.kt`, `AchievementUnlockWatcher.kt`, `HiddenAchievementWatcher.kt`,
`PioneerClaimHelper.kt`, `screen/AchievementsScreen.kt`
iOS: `Core/Achievements.swift`, `Core/HiddenAchievements.swift`, `Data/HiddenAchievementStore.swift`,
`Data/PioneerStore.swift`, `Features/Profile/AchievementsScreen.swift`

---

## Achievements.kt — 일반 업적(로컬 판정)

- `UserStats` : 업적 판정용 누적 통계(작성 수/좋아요 받은 수/친구 수/조회 등).
- `rememberUserStats(userId, diaryVm?)` : 통계 실시간 수집 컴포저블 — 프로필/업로드/업적 화면 공용.
- `Achievement(id, 이름, 설명, 조건, reward, hidden)` : 업적 정의.
  `Reward` = 칭호(Title) / 별 모양(StarType) / 별 색(StarColor). `hidden=true` 면 조건을 ??? 로 가림.
- `Achievements.all` / `unlockedIds(stats)` / `byId(id)` : 정의 목록/달성 판정/조회.
  **일반 업적은 서버 기록 없이 stats 로 매번 판정**(로컬 파생) — 통계만 맞으면 어디서든 동일.
- `StarUnlocks` : 별 모양/색 인덱스 → 해금 업적 id 매핑(**업적 정의의 보상에서 자동 도출** —
  업로드 피커 잠금이 이걸 쓴다. 보상만 고치면 동기화).
- `StigmaStore` : 장착 칭호 id 를 기기(prefs)에 저장(uid별). 프로필이 Firestore
  `users.equippedTitle` 로 백필해 타인에게도 보인다.
- `equippedTitleName(id)` : 일반+히든 통합 칭호 표시명 조회.

### 2026-09-26 확장 — 업적 38개 · 별 모양 12종(9~20) · 묶음 팝업
- 일반 업적 31 → **69**(칭호 26 + 별 모양 12 추가). 계절 업적은 나라(반구)마다 달라 넣지 않았다.
  이름은 칭호 그대로 쓰이므로 짧고 재밌게(사용자 요청 "뻔하지 않게"). 문구는 `title_*`/`ach_name_*`/`ach_cond_*` 3벌 +
  iOS `LocalizedNames` 표 — 새 업적을 넣을 땐 이 3곳(+ Android `LocalizedNames` 매핑)을 **동시에** 채운다(하나라도 빠지면 그 언어만 한국어).
- **새 통계**(`UserStats` — iOS 동일 필드): 사진/영상/장문(500자)/단문(20자)/나만보기 수, 최장 연속 일수, 새벽(5~6시) 기록,
  하루 최다, 기록한 달 수(1~12월), 11:11, 보름달 밤(월령 14.765±1일 · 18~6시), 1/1, 12/24·25, 1km 떨어진 장소 수(작성 순 탐욕),
  반경 1km 최대 군집, 같은 자리(50m) 30일 뒤 재방문, **첫 발자국**(1km 안에 *먼저* 남겨진 남의 별 없음 — 나중에 누가 와도 유지),
  **이웃 별**(남의 별 100m 안), **별 도감 수**(연 글 ∩ 지금 보이는 남의 글 — 남의 프로필에선 0), 한 글 최다 조회, 댓글 합(commentCount),
  서로 다른 모양/색 수, `achievementsDone`(왕관용 — 왕관 자신 제외 달성 수, `Achievements.withAchievementCount`).
  첫 발자국/이웃 별은 **전체 목록이 비어 있으면 판정 안 함**(내 글만 먼저 온 순간 오판 → 잘못된 해금 팝업 방지).
- **시간 판정은 전부 기기 현지 시간**(java.time / Calendar.current). 예전 `nightPosts`·`distinctDays` 는 UTC 라 한국에선
  오전 9시~오후 1시가 '심야'로 잡혔다 → 같이 고침(사용자 승인). 오전 기록으로 심야 색을 받았던 사람은 다시 잠길 수 있다.
- **숨김 업적**(`hidden = true`, 5개: 소원 접수 완료·달빛에 홀린 고양이·카운트다운 끝·산타보다 먼저·우리 전에 여기 왔었지):
  업적 화면에서 달성 전 조건이 `???`(이름은 힌트로 노출). 이번에 처음 쓰여 화면 처리를 추가했다(Android Title/RewardAchievementRow, iOS achievementRow).
- **묶음 팝업**: `AchievementUnlockWatcher` 가 새 업적을 `pending` 에 모으고 마지막 추가 뒤 `BUNDLE_WINDOW_MS`(1200ms) 조용하면
  한 묶음으로 큐에 넣는다 → 2개 이상이면 한 장("업적 N개 달성!" + 목록, 리빌은 새 별 모양 우선). iOS RootView 동일(`pendingAchievements` + `bundleTask`).
  iOS 는 `achievementSignature` 에 댓글 합·해금 수·전체 글 수를 추가하고 `DiaryUnlockStore` 를 관찰한다.
- 익명 게시는 기능이 없어(iOS 모델 주석: 제거됨) 익명 업적 대신 단문 업적(세 줄 요약도 길다).

## HiddenAchievements.kt — 히든 업적(전 앱 선착순 1명)

- 개념: **앱 전체에서 단 한 사람만** 달성 가능한 특별 업적. 달성 전 조건은 `???`,
  달성되면 조건+달성자 공개.
- `HiddenIcon`(전용 아이콘+대표색) / `ParticleEffect`(STARDUST/SNOW/AURORA/EMBER/SHADOW/HEART/
  MUSIC/ORBIT/BUBBLE — 프로필 부유 아이콘의 오라·버스트 종류).
- `HiddenContext(stats, allNormalDone)` : 자동 판정 입력.
- `HiddenAchievement(id, title(=칭호), 설명, icon, effect, auto, badgeType/badgeColor)` :
  `auto` 가 null 이면 이벤트형(화면에서 직접 claim), 아니면 조건 함수.
  `badgeType/badgeColor` : 이름 옆 크리스탈 배지 모양/색 — **iOS 와 값 동일 유지**.
- `RemoteLandmark(region, name, lat, lng)` : "도달하기 어려운 곳" 후보(같은 region = 업적 1개).
- `HiddenClaim(achieverId, achieverName, ...)` : 선점 기록 문서(`hiddenAchievements/{id}`).

## HiddenClaimStore.kt / 감시자들
- `HiddenClaimStore` : 선점 현황 전역 구독(캐시) — 배지/프로필/업적 화면이 공유.
- `HiddenAchievementWatcher(userId, suppressed)` : 자동 조건 충족 시 **Firestore 트랜잭션으로 선점**
  (이미 주인 있으면 실패 — 선착순 보장) + 첫 달성 팝업. 어드민 계정은 선점 제외(8.31)
  + 어드민이 선점한 기록 자가치유 해제(8.32).
- `AchievementUnlockWatcher(userId, suppressed)` : 일반 업적 새 달성 팝업(코치마크 중엔 큐잉).
- `AchievementUnlockDialog` : **해금된 보상을 실제로 보여준다** — 파편 14개가 사방에서 모여
  그 별이 완성되는 리빌(900ms) + 뒤쪽 광선 12갈래 회전(18초 1바퀴) + `Haptics.celebrate()`.
  보상별 표시 별: 칭호=앰버골드(15) 5꼭지 / 모양=해금 모양을 앰버골드로 / 색=해금 색 5꼭지.
  ⚠️ 문구는 전부 `strings.xml`(`ach_unlocked`/`ach_reward_*`/`common_confirm`) —
  예전엔 한국어 하드코딩이라 en/ja 에서 번역되지 않았다. 업적 이름은 `LocalizedNames.title`.
- `HiddenParticles.kt` : `HiddenIconWithEffect` 등 — 히든 아이콘 전용 파티클 렌더.

## AchievementsScreen.kt
- 일반/히든 2탭. 일반: 달성/미달성 + 진행도, 보상(칭호/별) 표시, 칭호 장착/해제(StigmaStore).
  히든: 미달성=??? / 달성=조건+달성자 이름(`UserDirectory` 현재값). 심연의 별 등 전용 아이콘.

## PioneerClaimHelper.kt — 개척 퀘스트(체크리스트 32)
- 업로드 성공 좌표로 `attemptClaim(context, lat, lng)` — 그 좌표가 **이번 주 활성 대상국 1개**(shared
  `PioneerQuest.activeCountry` — 미개척 나라 중 주차로 결정) 이면 국가 선점 시도(트랜잭션).
  지도에는 금색 비콘(03 문서)이 이 나라 1개에만 뜬다(과거 주 나라 누적 없음 — 비콘·선점 판정 동일 기준).
  구 `featuredCountries`(등장한 모든 미개척국 누적)는 이 변경으로 미사용.

---

## iOS 대응
- `Achievements.swift` / `HiddenAchievements.swift` : 정의·판정 **값 동일 유지**(칭호 fallback 포함 —
  과거 iOS 만 다른 칭호명이던 drift 사고 있음, 8.40 에서 정정).
- `HiddenAchievementStore.shared` : 전역 리스너 1개(`start()`) + `myIds(uid:)`/`achievements(of:)` —
  Android HiddenClaimStore 패턴.
- `ProfileScreen.runHiddenClaims()` : 자동 조건 선점 시도(Android 감시자 대응).
- `PioneerStore.swift` : 개척 현황 구독(지도 비콘용).

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 업적 정의(조건/보상) | `Achievements.kt` | `Achievements.swift` (**값 동일**) |
| 히든 정의/배지 모양·색 | `HiddenAchievements.kt` | `HiddenAchievements.swift` (**값 동일**) |
| 별 해금 매핑 | `StarUnlocks`(보상에서 자동) | Achievements.swift 대응 |
| 개척 대상국 주기/목록 | shared `PioneerQuest` | `Core/PioneerQuest.swift` (**값 동일**) |
