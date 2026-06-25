# iosApp — iOS 앱(스캐폴드)

> 맥 없이 CI(macOS)로 빌드/제출하는 구조. 전체 방법은 [`../docs/IOS_RELEASE.md`](../docs/IOS_RELEASE.md) 참고.

## 구조
- `project.yml` — **XcodeGen** 프로젝트 정의. `.xcodeproj` 는 커밋하지 않고 CI에서 생성한다.
- `Sources/` — SwiftUI 소스. **1차 코어 슬라이스 구현**: `Core/`(설정·테마·별 렌더·위치), `Data/`(Firestore·Auth·Repo), `Features/`(로그인·지도·목록·업로드·상세·프로필).
- `fastlane/` — TestFlight/App Store 업로드 자동화(App Store Connect API Key).
- `Gemfile` — fastlane/cocoapods.

## 로컬(맥)에서 열기
```bash
brew install xcodegen
cd iosApp && xcodegen generate   # Stary.xcodeproj 생성
open Stary.xcodeproj
```
Xcode 빌드 시 prebuild 스크립트가 `./gradlew :shared:embedAndSignAppleFrameworkForXcode` 로 공유 프레임워크를 만들어 링크한다.

## CI(맥 없이)
`.github/workflows/ios.yml` 가 push 시 macOS 러너에서: 공유 프레임워크 빌드 → XcodeGen → 아카이브 → (시크릿 있으면) TestFlight 업로드.

## 다음 작업(마일스톤 1+)
`../docs/IOS_RELEASE.md` 6절의 체크리스트 — Firebase iOS, MapLibre, 로그인, 화면(SwiftUI) 순으로 CI 그린 유지하며 구현.
