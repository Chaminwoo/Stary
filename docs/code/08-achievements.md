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
- `HiddenParticles.kt` : `HiddenIconWithEffect` 등 — 히든 아이콘 전용 파티클 렌더.

## AchievementsScreen.kt
- 일반/히든 2탭. 일반: 달성/미달성 + 진행도, 보상(칭호/별) 표시, 칭호 장착/해제(StigmaStore).
  히든: 미달성=??? / 달성=조건+달성자 이름(`UserDirectory` 현재값). 심연의 별 등 전용 아이콘.

## PioneerClaimHelper.kt — 개척 퀘스트(체크리스트 32)
- 업로드 성공 좌표로 `attemptClaim(context, lat, lng)` — 그 좌표가 이번 주 대상국(shared
  `PioneerQuest.featuredCountries`) 안이면 국가 선점 시도(트랜잭션). 지도에는 금색 비콘(03 문서).

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
