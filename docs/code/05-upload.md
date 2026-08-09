# 05. 업로드 (다이어리 작성)

Android: `feature/diary/screen/UploadScreen.kt`, `core/util/ImageCropHelper.kt`,
`core/util/ImageUploadHelper.kt`, `core/util/BoomerangHelper.kt`, `core/util/GifEncoder.kt`
iOS: `Features/Upload/UploadScreen.swift`, `BoomerangCamera.swift`, `BoomerangCaptureView.swift`,
`Data/ImageUploader.swift`

---

## UploadScreen.kt

### 상태/변수
- `title` / `content` : 제목(최대 `StaryConfig.DIARY_TITLE_MAX_LEN`)·본문 입력.
- `visibilityType` : 공개 범위 key("public"/"friends"/"private") — `VisibilityOptions` 목록에서 선택.
- `isAnonymous` : 익명 업로드 토글(작성자명 "익명").
- `selectedImageUri` / `cameraUri` : 갤러리/카메라 사진(부메랑과 배타 — 하나만 첨부).
- `boomerangFile` / `showBoomerangCapture` : 부메랑(3초 움짤) GIF 파일 / 촬영 화면 표시.
- `isUploading` : 저장 진행 중(버튼 잠금 + 로딩 별).
- `showImageSourceDialog` : 사진 추가 다이얼로그(사진 촬영/갤러리/부메랑 3택).
- `myDiaries` : 내 다이어리 실시간 구독 — **하루 업로드 제한**(오늘 자정 이후 개수 ≥
  `StaryConfig.DAILY_UPLOAD_LIMIT`(10) 이면 저장 선차단) 판정용.
- `cropController : CropController` : 사진 크롭 상태(핀치 scale + 드래그 offset, cover 배율 기반,
  프레임 밖 빈 공간이 안 생기게 클램프). `selectedImageUri` 변경 시
  `ImageCropHelper.loadDownsampled` 로 비트맵 로드.
- `starType` / `starColor` : 선택한 별 모양/색 인덱스(`mutableIntStateOf`) — `StarWheelPicker` 2개가 조작.
- `unlockedIds` : 달성 업적 id — 잠긴 별 모양/색 판정(잠긴 항목은 흐림+자물쇠+토스트, 저장 차단).
- `pioneerClaimTarget` : 저장 성공 시 개척 퀘스트 선점 시도할 좌표.
- `starTypeRef/starColorRef` : `rememberUpdatedState` — 저장 직전 선택값을 이벤트 수집기에서 참조
  (별 탄생 연출이 마지막 선택 별로 재생되게).

### 저장 흐름
1. 제목 검증 → 하루 제한 검증 → `isUploading = true`.
2. 첨부 업로드: 사진이면 `cropController` 크롭 결과 → `ImageUploadHelper.uploadImage`(diary_images/),
   부메랑이면 `uploadGif`(contentType=image/gif, URL 은 `videoUrl` 필드에 저장 — 스키마 재사용).
3. 좌표 = `LocationHelper` 현재 위치(없으면 기본좌표). `diaryViewModel.saveDiary(Diary(...))`.
4. `diaryViewModel.event` 로 "저장 완료!" 수신 →
   `PioneerClaimHelper.attemptClaim`(개척 선점) → `StarBirthState.trigger(모양, 색)`(별 탄생 연출)
   → `onSaveClick()`(화면 pop — 연출은 MainScreen 의 StarBirthHost 가 지도 위에서 이어 재생).

### 구성 컴포저블
- `ImageCropFrame(controller, modifier)` : 고정 비율(`ImageCropHelper.ASPECT`) 프레임 안 cover-fit +
  드래그/핀치. 프레임이 곧 크롭 영역.
- `StarWheelPicker(...)` : iOS WheelPicker 이식 — 선택 항목 항상 정중앙(민트 링), 좌우 5개 노출,
  modulo 순환. 놓은 지점 항목을 중앙으로 스냅 → **스냅이 끝난 뒤 selection 일괄 갱신**
  (좌표 불연속 방지). 잠긴 항목은 alpha 0.25+자물쇠.
  ⚠️ `commit(steps)` 는 **steps=0(반 슬롯 미만 드래그)일 때도 drag 를 0 으로 되돌려야 한다** —
  그냥 return 하면 항목과 항목 **사이**에 멈춘 채 고정된다(8.45 수정, iOS 도 동일).
- `VisibilityOptions` : (key, 라벨 리소스, 아이콘) — 공개 범위 선택지 정의.

## ImageCropHelper.kt
- `ASPECT` : 업로드/상세 공용 고정 비율(4:3). `loadDownsampled(context, uri)` : 다운샘플 디코드.
- 크롭 결과 비트맵 생성(cover 배율 × 사용자 scale/offset).

## ImageUploadHelper.kt
- `UploadResult(url, error)` : 실패 원인을 사람이 읽는 문구로 반환(토스트 노출용).
- `uploadImage(...)` : `diary_images/` 업로드. `uploadVideo(...)` : 3초 이내 영상(diary_videos/,
  contentType 명시로 Storage 규칙 통과). `uploadGif(...)` : 부메랑 GIF(diary_images 규칙 그대로 통과,
  URL 은 `diary.videoUrl` 에 저장).

## BoomerangHelper.kt / GifEncoder.kt
- 부메랑: 카메라 프레임 3초 캡처 → 정방향+역방향 왕복 프레임 → `GifEncoder` 로 GIF 인코딩.
- `minScaleFor(...)` : 조정 단계 진입 시 "찍힌 화면 전체가 들어오는 최소 배율"(잘림 없음, 여백 검정).
- 크롭은 캔버스에 그대로 그림(전체 화각 유지).

---

## iOS 대응

### UploadScreen.swift
- 같은 입력 구성(제목/본문/공개범위/익명/사진·부메랑/별 휠 2개). `WheelPicker` 가 원본
  (Android `StarWheelPicker` 가 이걸 이식한 것). 저장 성공 →
  `StarBirthStore.shared.trigger` + `TabRouter.go(map)`(지도 복귀 후 연출).
- **첨부 3지선다(8.45)** : "사진 추가" 버튼 → `confirmationDialog` (카메라 촬영 / 갤러리 / 3초 영상) —
  Android `showImageSourceDialog` 패리티. 첨부가 있으면 "다시 선택"(upload_reselect) 버튼이 붙는다.
  - 카메라 = `CameraPicker.swift`(UIImagePickerController, 긴 변 1600px JPEG 0.85 로 축소).
    시뮬레이터는 카메라 불가 → `toastCameraUnavailable`, 권한 거부 → `toastCameraPermission`.
  - 촬영 화면(사진/움짤)은 **fullScreenCover 하나**(`captureSheet` item)로 처리 — 같은 뷰에 커버 2개를
    달면 한쪽이 안 열릴 수 있다.
- **키보드(8.45)** : `@FocusState` + 키보드 툴바 "완료" + `scrollDismissesKeyboard(.interactively)` +
  제목 필드 `submitLabel(.done)`. 본문은 여러 줄이라 리턴키가 줄바꿈이므로 "완료" 버튼이 유일한 닫기 수단.
  배경은 ZStack 형제가 아니라 `.background { ScreenBackground(...) }` — 그래야 키보드가 올라올 때
  스크롤 영역이 줄어들어 입력칸이 가려지지 않는다.
- 하루 제한/제목 길이 등 수치는 `AppConfig`(= shared StaryConfig 복제값) 사용.
- 시뮬레이터 위치: `LocationManager` 가 시뮬레이터 빌드에선 위치 업데이트를 무시하고 서울(건국대)
  고정 — 업로드 좌표(`coordinateOrDefault`)도 서울이 된다(8.44 #3).

### BoomerangCamera.swift / BoomerangCaptureView.swift
- AVFoundation 캡처 + `BoomerangConfig.minScale`/`cropFrames`(Android BoomerangHelper 패리티),
  프리뷰 `resizeAspect`(전체 화각). 문구는 `L10n`(boomerRetake/boomerUse).

### ImageUploader.swift
- Storage 업로드(이미지/GIF/영상) — Android ImageUploadHelper 대응. 인증 세션 확보 후 업로드.

### 값 조절(패리티 매핑)
| 항목 | Android | iOS |
|---|---|---|
| 하루 업로드 제한(10) | shared `StaryConfig.DAILY_UPLOAD_LIMIT` | `AppConfig`(동일 값 유지) |
| 제목 최대 길이 | shared `StaryConfig.DIARY_TITLE_MAX_LEN` | `AppConfig` |
| 크롭 비율(4:3) | `ImageCropHelper.ASPECT` | iOS 크롭 상수 |
| 휠 피커 노출 개수/스냅 | `UploadScreen.kt` StarWheelPicker | `UploadScreen.swift` WheelPicker (원본) |
| 부메랑 길이(3초)/배율 | `BoomerangHelper` | `BoomerangConfig` |
| 별 해금(업적) 조건 | `Achievements`(08 문서) + 피커 잠금 | 동일 데이터( Achievements.swift ) |
