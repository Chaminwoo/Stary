# iOS 보상형 광고 설정 — 사용자(개발자)가 직접 해야 할 일

> 코드는 2026-10-04 에 연결 완료(`Core/AdsManager.swift`, `LevelPlayRewarded.swift`, `AdMobRewarded.swift`).
> **키를 안 넣으면 광고만 꺼진다** — 100m 밖 글의 아이콘을 탭하면 "지금은 광고를 불러올 수 없어요"가 나오고 앱은 정상 동작한다.
> 아래를 채우면 켜진다. 광고원은 **LevelPlay(1순위) → AdMob(폴백)** 순서(Android 와 동일).

---

## 0. 한눈에 보는 체크리스트

- [ ] A. LevelPlay 에 **iOS 앱** 추가 → App Key + 보상형 Ad unit ID 받기
- [ ] B. (권장) AdMob 에 **iOS 앱** 추가 → App ID + 보상형 광고 단위 ID 받기
- [ ] C. `iosApp/Config/Local.secrets.xcconfig` 에 4개 값 넣기(로컬 빌드)
- [ ] D. GitHub Secrets 4개 등록(TestFlight/CI 빌드)
- [ ] E. `app-ads.txt` 를 개발자 웹사이트 루트에 게시 + App Store Connect 의 개발자 웹사이트에 같은 도메인 등록
- [ ] F. App Store Connect **앱 개인정보(App Privacy)** 에 광고/추적 항목 선언
- [ ] G. 실기기에서 ATT 팝업 → 광고 재생 → 해금까지 확인
- [ ] H. CI 가 빨개지면 7번 "문제 해결" 확인

---

## 1. LevelPlay (Unity) — iOS 앱 추가

1. [LevelPlay 대시보드](https://platform.ironsrc.com/) → **Apps → Add App → iOS**.
   - ⚠️ Android 앱과 **다른 앱**으로 따로 추가해야 한다(키가 다르다).
   - 번들 ID: `com.chaminwoo.stary.ios`
   - 앱이 아직 App Store 에 없으면 "Not live yet" 로 추가 가능(출시 후 스토어 URL 연결).
2. 앱 생성 후 **App Key** 복사(예: `1a2b3c4d5`).
3. **Ad Units → Rewarded** 광고 단위 생성 → **Ad unit ID** 복사.
4. **Networks(Setup)** 에서 광고 네트워크를 켠다 — 최소 **Unity Ads**(코드에 어댑터가 들어 있음). 다른 네트워크를 쓰려면 해당 어댑터 SPM 패키지를 `iosApp/project.yml` 에 추가해야 한다
   (`https://github.com/ironsource-mobile/LevelPlay-<Network>-Adapter-Swift-Package`, 의존 규칙 Exact 권장).
   - 네트워크가 하나도 안 켜져 있으면 로드가 항상 `509 Mediation No fill` — 이 경우 코드가 자동으로 AdMob 폴백으로 넘어간다.
5. 키: `LEVELPLAY_APP_KEY`, `LEVELPLAY_REWARDED_AD_UNIT`.

## 2. AdMob — iOS 앱 추가 (폴백, 권장)

1. [AdMob](https://admob.google.com/) → **앱 → 앱 추가 → iOS** (번들 ID `com.chaminwoo.stary.ios`).
2. **앱 ID** 복사 — 형식 `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY` (물결표 `~`).
3. **광고 단위 → 보상형** 생성 → **광고 단위 ID** 복사 — 형식 `ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ` (슬래시 `/`).
4. 키: `ADMOB_APP_ID`, `ADMOB_REWARDED_AD_UNIT`.
5. ⚠️ **앱 ID 가 비어 있으면 AdMob SDK 를 아예 시작하지 않는다**(Info.plist 에 앱 ID 가 없으면 SDK 가 시작 시 크래시하므로 코드가 막아 둔다).
6. DEBUG 빌드는 `Config/Local.xcconfig` 가 구글 **테스트 앱 ID**(`ca-app-pub-3940256099942544~1458002511`)를 넣고, 코드는 **구글 테스트 보상형 단위**
   (`ca-app-pub-3940256099942544/1712485313`)만 쓴다 — 키 없이도 디버그에선 AdMob 테스트 광고가 항상 나오고 수익은 0(Android 와 같은 정책).
   실제 광고 단위를 넣어도 DEBUG 는 테스트 광고다(개발 중 실광고 클릭은 정책 위반).

## 3. 로컬 빌드: `iosApp/Config/Local.secrets.xcconfig`

이 파일은 **gitignore** 대상(커밋 금지)이고 `Local.xcconfig` 가 `#include?` 로 자동 읽는다. 없으면 만들어서:

```
LEVELPLAY_APP_KEY = 여기에_LevelPlay_App_Key
LEVELPLAY_REWARDED_AD_UNIT = 여기에_LevelPlay_Rewarded_AdUnitId
ADMOB_APP_ID = ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
ADMOB_REWARDED_AD_UNIT = ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
```

- ⚠️ **xcconfig 의 `//` 는 주석**이다. 값에 `//`(예: URL `https://…`)가 들어가면 그 뒤가 잘린다. 위 4개 값에는 `//` 가 없어서 그대로 써도 되지만
  (AdMob ID 의 `/` 는 한 개라 괜찮음), 다른 값을 넣을 땐 `https:/$()/` 처럼 이스케이프해야 한다.
- 값에 따옴표는 붙이지 않는다.
- project.yml 에는 이 키들의 값을 **넣지 않는다**(target 설정이 xcconfig 보다 우선해서 항상 빈 값으로 덮어쓰기 때문 — 2026-10-04 제거함).
- 확인: Xcode 에서 빌드 후 `Stary.app/Info.plist` 에 `LEVELPLAY_APP_KEY` / `GADApplicationIdentifier` 등이 값으로 들어 있는지 본다.

## 4. CI / TestFlight 빌드: GitHub Secrets

GitHub 레포 → Settings → Secrets and variables → Actions → New repository secret 로 4개 등록:

| Secret 이름 | 값 |
|---|---|
| `LEVELPLAY_APP_KEY` | LevelPlay App Key |
| `LEVELPLAY_REWARDED_AD_UNIT` | LevelPlay 보상형 Ad unit ID |
| `ADMOB_APP_ID` | AdMob iOS 앱 ID(`~` 포함) |
| `ADMOB_REWARDED_AD_UNIT` | AdMob 보상형 단위 ID(`/` 포함) |

`.github/workflows/ios.yml` 의 TestFlight 업로드 잡에 이 값을 `Local.secrets.xcconfig` 로 쓰는 스텝이 이미 들어 있다(시크릿이 없으면 빈 값 = 광고 꺼짐).
컴파일 검증(`build` 잡)은 키 없이 돈다.

## 5. 정책/심사 관련

### 5-1. app-ads.txt
- AdMob/LevelPlay 가 광고주에게 "이 앱의 정식 판매자"를 증명하는 파일. App Store Connect 의 **개발자 웹사이트(마케팅 URL) 도메인 루트**에
  `https://내도메인/app-ads.txt` 로 게시해야 한다. 안 하면 광고 채움률/수익이 크게 떨어질 수 있다(Android 와 같은 이유).
- 내용은 각 대시보드가 알려 준다: AdMob(앱 → app-ads.txt 설정 안내의 `google.com, pub-…, DIRECT, f08c47fec0942fa0` 줄),
  LevelPlay(대시보드의 app-ads.txt 안내 — ironSource/Unity 줄).

### 5-2. ATT (앱 추적 투명성)
- Info.plist `NSUserTrackingUsageDescription`(한국어 문구)는 이미 추가돼 있다. 문구를 바꾸려면 `iosApp/project.yml` 의 같은 키 수정.
- 동작: **광고 키가 하나라도 있으면** 첫 실행 때 앱이 활성화된 뒤 1.5초쯤 지나 ATT 팝업이 **한 번** 뜬다(비차단). 팝업의 답이 난 뒤에 광고 SDK 를 시작한다.
  거부해도 광고는 나온다(개인화만 빠짐). 키가 없으면 팝업도 없다.
- 팝업을 다시 보려면: 앱 삭제 후 재설치(또는 설정 → 개인정보 보호 → 추적 에서 초기화).

### 5-3. App Store Connect 앱 개인정보(App Privacy)
광고 SDK 가 수집하는 항목을 선언해야 한다(심사 거절 방지). 앱 → **앱 개인정보 → 개인정보 보호 관행 편집**:
- **광고 데이터 / 식별자(기기 ID, IDFA)**: "수집함" → 용도 "제3자 광고", **"사용자를 추적하는 데 사용됨"** 체크(ATT 와 일치).
- **사용 데이터(제품 상호작용), 진단(크래시/성능)**: 광고 SDK 가 수집 → 선언(각 네트워크 문서의 데이터 공개 가이드 참고).
- 정확한 항목은 [Google AdMob 개인정보 공개 가이드](https://developers.google.com/admob/ios/privacy/strategies) 와
  LevelPlay 의 "Apple privacy / app privacy details" 문서를 따른다.
- 아동 대상 앱이 아니므로 "Made for Kids" 는 해당 없음. 연령 등급 설문에서 "광고 포함"은 체크.

### 5-4. SKAdNetwork
- `iosApp/project.yml` 의 `SKAdNetworkItems` 에 Google(`cstr6suwn9`) + ironSource(`su67r6k2v3`) + Unity(`4dzt52r2t5`) + 주요 네트워크 ID 를 넣어 두었다.
- 구글이 권장하는 **전체 최신 목록**은 더 길고 수시로 늘어난다 →
  [Google SKAdNetwork 가이드](https://developers.google.com/admob/ios/choose-networks#skadnetwork) 에서 목록을 받아 보강하는 것을 권장.
  LevelPlay 도 네트워크를 추가할 때마다 해당 네트워크의 SKAdNetwork ID 를 요구한다.

### 5-5. (선택) ATS
- LevelPlay 문서는 `NSAllowsArbitraryLoads = YES` 를 권하지만 심사에 불리할 수 있어 **넣지 않았다**. 일부 네트워크 광고가 http 소재를 써서 안 뜨면 그때 검토.

## 6. 확인 방법 (G)

1. 키를 넣고 **실기기**(시뮬레이터는 광고가 불안정)에 설치.
2. 첫 실행: 잠시 뒤 ATT 팝업 → "허용" 또는 "앱이 추적하지 않도록 요청".
3. 100m 밖 다른 사람의 글 상세 → 재생 아이콘 탭.
   - 로드 전이면 토스트 "광고를 불러오는 중이에요…" → 최대 12초 안에 재생.
   - 끝까지 보면 "이야기가 열렸어요" + 영구 해금. 중간에 닫으면 "광고를 끝까지 봐야 열려요".
   - 12초 안에 못 받으면 "지금은 광고를 불러올 수 없어요".
4. Xcode 콘솔(DEBUG)에서 `[Ads]` 로그로 LevelPlay 실패 사유(`509 No fill` 등)·폴백(AdMob) 실패 사유를 본다.
5. LevelPlay 가 No fill 이면 → 대시보드에서 네트워크(Unity Ads) 활성/앱 연결을 확인. 그 사이 AdMob 폴백이 광고를 대신 보여 준다.
6. **테스트 기기 등록(선택)**: AdMob 실광고를 개발기에서 보려면 콘솔 로그에 찍히는 기기 ID 를 AdMob → 설정 → 테스트 기기에 등록. DEBUG 는 어차피 테스트 단위라 필수는 아님.
7. 잠금 자체를 확인하려면(100m 밖인데 열려 있는 경우) DEBUG 콘솔의 `🔒 [Lock] … 이유=` 로그 확인:
   `owner`(내 글) / `near`(100m 이내) / `reviewAccount`(이메일 로그인 계정) / `unlockStore`(이전에 해금·잠금 이전에 열어 본 글).

## 7. 문제 해결

- **CI 에서 패키지 해석 실패 / 링크 오류**: 이 변경에서 `iosApp/project.yml` 에 SPM 패키지 3개를 추가했다
  (`GoogleMobileAds`, `LevelPlay`(제품 `UnityMediationSDK`), `LevelPlayUnityAdsAdapter`(제품 `UnityAdsAdapter`)).
  LevelPlay 쪽이 문제면 `packages:` 의 LevelPlay 두 항목 + `dependencies:` 의 해당 두 줄 + `OTHER_LDFLAGS` 의 `-ObjC` 를 지워도 된다 —
  코드는 `#if canImport(IronSource)` 로 감싸져 있어 컴파일은 그대로 통과하고, 그 경우 AdMob 만 동작한다.
  (LevelPlay SPM 공식 안내는 `main` 브랜치 사용이지만 태그 `9.6.x` 로 고정해 뒀다. 해석이 안 되면 `branch: main` 으로 바꿔 본다.)
- **LevelPlay Swift 컴파일 오류**: API 이름은 공식 문서(SDK 8.5+/9.x: `LPMInitRequestBuilder`, `LevelPlay.initWith`, `LPMRewardedAd`, `LPMRewardedAdDelegate`) 기준이다.
  SDK 메이저 버전이 올라 이름이 바뀌면 `Core/LevelPlayRewarded.swift` 한 파일만 고치면 된다.
- **AdMob 컴파일 오류**: `from: "12.0.0"`(12.x 라인)의 Swift API(`MobileAds.shared.start`, `RewardedAd.load(with:request:)`, `present(from:)`,
  `FullScreenContentDelegate`) 기준. 13.x 로 올릴 땐 `Core/AdMobRewarded.swift` 확인.
- **앱 시작 시 크래시 `GADApplicationIdentifier`**: 앱 ID 가 비어 있을 때 AdMob 을 시작하면 발생 — 코드가 막아 두었으니, 나오면 `ADMOB_APP_ID` 가 Info.plist 에 비어 있는지 확인.
- **광고가 계속 안 나옴(실기기, 키 있음)**: ① 새로 만든 광고 단위는 활성화까지 수 시간 걸릴 수 있음 ② `app-ads.txt` 미게시 ③ 스토어 미게시 앱은 실광고 no fill 이 흔함(테스트 광고로 흐름만 확인).
