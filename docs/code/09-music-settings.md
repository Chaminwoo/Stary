# 09. 배경음악 · 설정

Android: `feature/profile/screen/MusicScreen.kt`, `SettingsScreen.kt`,
`core/util/MusicManager.kt`, `MusicCatalog.kt`, `AppSettings.kt`, `LocaleManager.kt`
iOS: `Features/Music/MusicScreen.swift`, `Features/Profile/SettingsScreen.swift`,
`Core/MusicManager.swift`, `MusicCatalog.swift`, `AppSettings.swift`, `LocaleManager.swift`

---

## MusicManager.kt — 배경음악/효과음 싱글턴

앱 전역 BGM 1개 + 효과음(SoundPool) 재생기. `MainScreen` 이 생명주기에 맞춰 `init/resume/pause/release`.

### 상태(Compose 관찰 가능)
- `enabled` : BGM on/off(설정 탭 토글). 끄면 효과음도 출력하지 않는다.
- `selectedTrackId` : 영구 선택된 BGM 트랙 id(음악 탭에서 확정 시 갱신, 기본 `MusicCatalog.DEFAULT_ID`).
- `musicVolume` / `sfxVolume` : 배경음악/효과음 볼륨(0..1, 설정 탭 슬라이더).

### 함수
- `init(context)` : prefs 로드 + SoundPool 준비. `release()` : 위치 보존 후 해제
  (⚠️ 언어 변경 recreate 사이클 대비 `initialized` 리셋 — 안 풀면 효과음이 깨진다).
- `resume()` / `pause()` : 전면 복귀 시 마지막 위치부터 재생 / 위치 보존 정지.
- `playTrack(id, positionMs0)` : 트랙 교체 재생(음악 탭 미리듣기 겸용 — 화면 이탈 시 확정 안 했으면
  원래 트랙 복원). `commitSelectedTrack(id)` : 선택 확정 저장.
- `setActive(value)` : on/off 저장+즉시 반영. `updateMusicVolume/updateSfxVolume(value)` :
  저장 + (BGM 은) 재생 중 플레이어에 즉시 반영.
- 효과음: `playWind()`(다이얼/휠 바람) · `playOpenDiary()`(별 열람 파장) · `setDialTurning(turning)`
  (다이얼 회전 루프음). 전부 `enabled=false` 면 무음.

## MusicCatalog.kt — 트랙 데이터(순수 데이터)
- `Track(id, displayName, rawName, colorArgb, starType, unlockAchievementId)` :
  음원 리소스명(`res/raw/bgm_*`), 테마색, 다이얼 별 모양, 해금 업적(null=기본 해금).
- `tracks` : 6곡 — star_whisper(기본)/tiny_explorer/celestial_drift/cosmic_funk/forgotten_galaxy/nebula_garden.
- `DEFAULT_ID = "star_whisper"`, `byId/rawName/default/indexOf`.
- **새 곡 추가 절차**: `res/raw/bgm_xxx.mp3` 추가 → 여기 `Track` 추가 → `MusicScreen` 의
  `MUSIC_CONSTELLATIONS` 에 별자리 문양 추가 → iOS `MusicCatalog.swift`/번들 mp3/별자리도 동일하게.

## MusicScreen.kt — 음악 선택 화면
- `MusicScreen()` : 원형 로터리 다이얼로 트랙 선택. 들어올 때 재생 중 트랙에서 시작,
  다이얼로 돌리면 **미리듣기**(`playTrack`), 저장하면 `commitSelectedTrack`,
  저장 없이 이탈하면 원래 트랙·위치 복원. 잠긴 곡(업적 미달성)은 흐림+자물쇠.
- `DIAL_RING_RADIUS_DP = 124` : 별 고리 반지름(다이얼 크기 조절 포인트).
- `MusicDial(...)` : 별이 원 둘레에 놓이고 드래그로 고리를 돌려 위쪽(topAngle)에 온 트랙 선택.
- `MStar/MConstel` + `MC_*` + `MUSIC_CONSTELLATIONS` : 트랙별 별자리 문양(좌표 0..1, mag 가중).
  트랙 id → 문양 매핑. `MusicConstellationBackground(...)` 이 중앙에 렌더(선택 시 번쩍임).

## SettingsScreen.kt — 설정 화면

### 색 상수(2026-07-18 어둡게 조정 — "탁함" 피드백)
- `Accent = 0xFF9FB3E8`(남색 라이트 강조) / `Blue`/`Navy`(테두리 그라데이션) / `SoftRed`(위험).
- `CardBg = 0xE6080D1A` : 글래스 카드 배경(검정에 가까운 어두운 남색).
- `DialogBg = 0xFF0A0F1D` : 언어/탈퇴 다이얼로그 배경. `TrackBg = 0xFF111726` /
  `TrackBgDisabled = 0xFF0D1220` : 스위치·슬라이더 트랙.

### 상태/변수
- `showLanguageDialog` / `showDeleteDialog` / `deleting` : 언어 선택/계정 삭제 다이얼로그·진행 상태.
- `blockedIds` : 내가 차단한 uid 집합(`FirebaseModerationRepository.observeBlockedIds`) —
  안전 섹션 행 우측의 "N명" 카운트에만 쓴다(비어 있으면 숫자 숨김).
- 파라미터 `onOpenBlockedUsers` : 안전 > 차단한 사용자 → `NavRoute.BlockedUsers` 이동(NavGraph 가 주입).

### 구성(섹션 순서)
1. 사운드 — BGM 토글(`MusicManager.setActive`) + BGM/효과음 볼륨(`VolumeRow`)
   + **햅틱(진동) 토글**(`AppSettings.updateHapticsEnabled` — 끄면 `Haptics` 호출이 전부 무음, 02 문서).
2. 알림 — 인앱 팝업 토글(`AppSettings.updateNotificationsEnabled`).
3. 언어 — `NavRow` 탭 → `LanguageDialog`(시스템/ko/en/ja) → `LocaleManager.setLanguageTag` +
   `activity.recreate()`.
4. **안전 — 차단한 사용자(`NavRow`, 우측 = 차단 수) → `BlockedUsersScreen`.**
5. 계정 — 탈퇴(7일 유예 예약, `GoogleAuthHelper.requestDeletion`) → 성공 시 로그아웃 콜백.

## BlockedUsersScreen.kt — 차단 목록(설정 > 안전)

내가 차단한 사용자 확인/해제. 라우트 `NavRoute.BlockedUsers`(탑바 제목 `nav_blocked_users`).

- 데이터: `FirebaseModerationRepository.observeBlockedUsers(myId)` →
  `List<BlockedUser>`(문서 id=상대 uid, `userName`/`photoUrl`/`createdAt`, **최근 차단 순**).
  이름·사진은 **차단 시점 스냅샷**이라 상대 프로필 문서를 다시 읽지 않는다.
- 행 = `BlockedRow` : [사진 44dp] [이름 / `yyyy.MM.dd` 차단일] [차단 해제 pill(SoftRed)].
  사진·이름 탭 → 그 사람 프로필(`NavRoute.UserProfile`), 해제 pill → **확인 다이얼로그**를 거쳐 `unblock`.
- 비어 있으면 사람✕ 아이콘 + `blocked_empty`/`blocked_empty_desc` 안내, 목록 위에는 `blocked_hint`
  (숨겨지는 범위 + "상대는 모른다" 설명).

### 차단이 실제로 걸리는 지점(한 곳에서 안 막는다 — 화면마다 필터)
| 화면 | Android | iOS |
|---|---|---|
| 지도/글로브 별 | `MainListScreen.filteredDiaries` (`diary.userId !in blockedIds`) | `MapScreen.shownDiaries` |
| 목록 탭 | (지도와 같은 필터) | `ListScreen.rows` |
| 상세 댓글 | `DetailScreen.comments` | `DetailScreen.visibleComments` |
| 알림 | `NotificationScreen.visibleNotifs` (`actorId`) | `NotificationsScreen.items` |
| 친구 검색/받은 요청 | `FriendScreen` results/visibleRequests | `FriendsScreen` results/visibleRequests |
- **차단 시 친구 관계도 해제**된다(UserProfileScreen: Android `vm.remove`, iOS 양방향 friends 문서 삭제).
- 차단은 `users/{내uid}/blocked/{상대uid}` **한 방향 기록** — 상대에겐 아무 표시도 가지 않는다.

### 보조 컴포저블
- `GlassCard` : CardBg + 파랑→남색 그라데이션 테두리 카드. `SectionLabel` : 섹션 제목.
- `IconBadge(icon, active)` : 원형 아이콘 뱃지(활성=Accent 틴트).
- `ToggleRow` / `NavRow` / `VolumeRow` : 토글 행 / 이동 행(현재값+›) / 볼륨 행(% 칩+슬라이더).
- `StarThumb` : 슬라이더 핸들 = 별 모양 + 후광(누르면 1.3배 확대·발광 — interactionSource 공유 필수).
- `Context.findActivity()` : recreate 용 액티비티 탐색.

## AppSettings.kt
- `hapticsEnabled` : 햅틱(진동) on/off(기본 켜짐). `init(context)` 이 `Haptics.init` 도 호출한다.
  켠 순간 `Haptics.light()` 로 어떤 느낌인지 바로 보여준다.
- `notificationsEnabled` : 인앱 알림 배너 on/off(기본 켜짐, prefs `stary_prefs` 영속).
  끄면 배너만 안 뜨고 미읽음 카운트/알림 목록은 유지. `init(context)` / `updateNotificationsEnabled(v)`.

## LocaleManager.kt
- `SUPPORTED = ["", "ko", "en", "ja"]` ("" = 시스템 기본).
- `getLanguageTag/setLanguageTag` : prefs 저장. `wrap(context)` : 저장된 로케일로 context 래핑 —
  `MainActivity.attachBaseContext` 에서 호출된다. 언어 변경 = 저장 + `recreate()`.
- ⚠️ 하드코딩 한국어는 번역이 안 된다 — 반드시 `strings.xml` 리소스로.

---

## iOS 대응

### MusicManager.swift / MusicCatalog.swift
- Android 와 같은 공개 API(`enabled`/`selectedTrackId`/`musicVolume`/`sfxVolume`/`resume`/`pause`/
  `playTrack`/`commitSelectedTrack`/`playWind`/`playOpenDiary`). `@Published` 로 관찰.
- 트랙 6곡 정의 **값 동일 유지**(id/색/별모양/해금 업적). 음원은 앱 번들 mp3.
- 생명주기: `MainTabView.onChange(scenePhase)` 가 resume/pause(01 문서).

### MusicScreen.swift
- `MusicConstellationView` = 내 다이어리 보드와 동일 렌더(그라데이션 후광, mag 가중 트윙클 3.4s,
  선택 플래시 0.78, `flashKey`=selectedIndex). ⚠️ Canvas 수식은 Double 로 계산 후 마지막에 CGFloat
  (CGFloat·Double 혼합 '+' 는 컴파일 에러).

### SettingsScreen.swift
- 카드 배경 `cardBg = Color(hex: 0x080D1A).opacity(0.9)` — **Android CardBg 0xE6080D1A 패리티**.
- 구성 동일(사운드/알림/언어/**안전**/계정). 언어는 confirmationDialog → `locale.setLanguage(tag)`
  (RootView 가 `.id(locale.language)` 로 전체 재렌더). 탈퇴는 `auth.requestDeletion()`.
- 안전 섹션은 `NavigationLink { BlockedUsersScreen() }`, 우측 숫자는 `blocks.blockedIds.count`
  (`BlockStore` 는 RootView 가 environmentObject 로 주입).

### BlockedUsersScreen.swift
- `BlockStore.blockedUsers`(스냅샷 리스너에서 `BlockedUser` 로 매핑, 최근 차단 순)를 그대로 그린다.
- 행/빈 화면/문구는 Android 와 동일. 해제는 `.alert(presenting:)` 확인 후 `ModerationRepository.unblock`.
- 문구는 `L10n` 의 `navBlockedUsers`/`settingsSafety`/`settingsBlockedUsers(Desc)`/`blockedEmpty(Desc)`/
  `blockedHint`/`blockedAtFormat`/`blockConfirmTitle`/`blockConfirmMsg`/`unblockConfirmMsg`.
- 볼륨 슬라이더는 시스템 Slider + navyAccent 틴트(별 thumb 는 Android 전용 — iOS TODO).

### AppSettings.swift / LocaleManager.swift
- `AppSettings.shared.notificationsEnabled` : Android 와 같은 의미(UserDefaults 영속).
- `LocaleManager.shared` : `language`("",ko,en,ja) + `t(.키)` 문자열 테이블(`L10n` enum).
  **Android 는 strings.xml, iOS 는 L10n enum** — 문구 추가/수정 시 양쪽 모두 넣는다(ko/en/ja 3언어).

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 설정 카드/다이얼로그/트랙 색 | `SettingsScreen.kt` CardBg·DialogBg·TrackBg* | `SettingsScreen.swift` cardBg |
| 트랙 목록/해금 업적 | `MusicCatalog.kt` | `MusicCatalog.swift` (**값 동일**) |
| 다이얼 크기 | `MusicScreen.kt` DIAL_RING_RADIUS_DP | `MusicScreen.swift` 대응 상수 |
| 음악 별자리 문양 | `MusicScreen.kt` MC_* | `MusicScreen.swift` (**값 동일**) |
| 볼륨/토글 저장 키 | prefs `stary_prefs` | UserDefaults |
| 언어 지원 목록 | `LocaleManager.SUPPORTED` | `LocaleManager.supported` |
| 차단 목록 화면 | `BlockedUsersScreen.kt` | `BlockedUsersScreen.swift` |
| 차단 저장 스키마 | `FirebaseModerationRepository` (`userName`/`photoUrl`/`createdAt`) | `ModerationRepository` (**필드 동일**) |
| 맷돌(다이얼) 그라인딩음 — 회전 중 | `MusicManager.dialTick()`(각도 눈금 지날 때마다 호출 = 속도 비례, 최소 간격 `DIAL_TICK_MIN_GAP_MS`=40ms) | `MusicManager.dialTick()`(`dialTickMinGap`=0.04s) — **같은 값 유지** |
| 맷돌(다이얼) 눈금 촘촘함(트랙 1칸당 등분 수) | `MusicScreen.kt` `FINE_DIVISIONS`=5 | `MusicScreen.swift` `fineDivisions`=5 |
| 놓았을 때(드르륵 잔향) | `MusicManager.dialRelease()` — 간격 벌어짐+볼륨감쇠 5회(`DIAL_RELEASE_GAPS_MS`=45/70/105/150/210ms) | `MusicManager.dialRelease()`(`dialReleaseGaps`) — **같은 값 유지** |
