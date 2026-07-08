# PROJECT_NOTES.md — Stary-Project 코드 분석 / 작업 핸드오프

> 목적: **다음 작업 시 코드를 처음부터 다시 읽지 않고** 바로 시작할 수 있도록 구조·연동·결정사항을 정리.
> 업데이트 규칙: 빌드+테스트 성공 때마다 갱신(자세한 건 `CLAUDE.md` 참고).
> 최종 갱신: **8.36 Seedance 2.0 광고 마스터 기획 + 광고 자산 정리**(기획안 4종 + 씬별 i2v 프롬프트, 코드 변경 없음 — 커밋 `db12725`) — 아래 8.36 참고.
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
- **다음 단계**: ① S4~S8 클립 생성 → ② 30s/15s 편집 → ③ 세로 티저(신규 이미지 §6-②~④ 먼저) → ④ 범퍼 D.
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

## 9. 남은 작업 / TODO (다음에 할 것)
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
