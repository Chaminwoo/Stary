import java.util.Properties

plugins {
    // 주의: AGP 9 는 com.android.application 적용 시 Kotlin 을 내장 지원하므로
    // org.jetbrains.kotlin.android 를 명시적으로 적용하면 'kotlin' extension 중복 오류가 난다.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.secrets.gradle.plugin)
}

// --- 민감값 로딩 (절대 하드코딩/커밋 금지) -------------------------------------
// secrets.properties(gitignore 대상)에서 값을 읽고, 없으면 secrets.defaults.properties
// 의 플레이스홀더로 폴백한다. 두 파일 모두 프로젝트 루트에 둔다.
//  - MAPS_API_KEY        : AndroidManifest 의 ${MAPS_API_KEY} 로 주입 (manifestPlaceholders)
//  - GOOGLE_WEB_CLIENT_ID: BuildConfig.GOOGLE_WEB_CLIENT_ID 로 주입
val secretsProps = Properties().apply {
    rootProject.file("secrets.defaults.properties").takeIf { it.exists() }
        ?.inputStream()?.use { load(it) }
    rootProject.file("secrets.properties").takeIf { it.exists() }
        ?.inputStream()?.use { load(it) }
}
val googleWebClientId: String = secretsProps.getProperty("GOOGLE_WEB_CLIENT_ID")
    ?: "TODO_ADD_GOOGLE_WEB_CLIENT_ID"
val maptilerKey: String = secretsProps.getProperty("MAPTILER_KEY")
    ?: "TODO_ADD_MAPTILER_KEY"
// 광고: Unity LevelPlay(미디에이션, 보상형) — LevelPlay 대시보드의 **Android 앱** 값 2개.
// (Unity Ads SDK 직접 연동은 2026-01-31 로 수익화 지원 종료 → LevelPlay 경유가 유일한 길. 2026-09-22 전환)
// 없으면 placeholder → 빌드는 되지만 광고는 자동 비활성(AdsManager.isConfigured=false).
val levelPlayAppKeyAndroid: String = secretsProps.getProperty("LEVELPLAY_APP_KEY_ANDROID")
    ?: "TODO_ADD_LEVELPLAY_APP_KEY_ANDROID"
val levelPlayRewardedAdUnitAndroid: String = secretsProps.getProperty("LEVELPLAY_REWARDED_AD_UNIT_ANDROID")
    ?: "TODO_ADD_LEVELPLAY_REWARDED_AD_UNIT_ANDROID"
// 광고 폴백: Google AdMob(보상형) — LevelPlay 가 `509 Mediation No fill` 일 때 대신 채운다(2026-09-23).
//  - 값이 없으면: 디버그는 구글 공식 **테스트** 광고 단위로 동작(계정/대시보드 없이 항상 노출), 릴리즈는 폴백 자체가 꺼짐.
//  - 앱 ID 는 매니페스트 필수값(없으면 SDK 가 시작 시 앱을 죽인다) → 미설정이면 구글 공식 테스트 App ID 를 주입.
//    테스트 ID 2종은 구글이 공개한 개발용 상수라 비밀이 아니다(하드코딩 금지 규칙 대상 아님).
val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"
val admobAppIdAndroid: String = secretsProps.getProperty("ADMOB_APP_ID_ANDROID")
    ?.takeIf { it.isNotBlank() } ?: admobTestAppId
val admobRewardedAdUnitAndroid: String = secretsProps.getProperty("ADMOB_REWARDED_AD_UNIT_ANDROID")
    ?.takeIf { it.isNotBlank() } ?: "TODO_ADD_ADMOB_REWARDED_AD_UNIT_ANDROID"
// 인스타그램 스토리 공유 링크스티커(content_url) 귀속용 Facebook App ID.
// 없으면 빈 값 → 스토리에 카드 이미지는 올라가나 자동 링크스티커는 붙지 않는다(수동 추가 필요).
// 발급: developers.facebook.com → 앱 생성 → 앱 ID. iOS 도 동일 값 사용.
val instagramAppId: String = secretsProps.getProperty("INSTAGRAM_APP_ID") ?: ""

// --- 릴리즈 서명 키 로딩 (절대 하드코딩/커밋 금지) ---------------------------
// keystore.properties(gitignore 대상, 프로젝트 루트)에서 서명 정보를 읽는다.
// 파일이 없으면 release 빌드는 서명 없이 생성된다(로컬 디버그용). 템플릿: keystore.properties.example
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.inputStream()?.use { load(it) }
}
val hasReleaseKeystore: Boolean = rootProject.file("keystore.properties").exists()

android {
    namespace = "com.chaminwoo.stary"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.chaminwoo.stary_ios"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        versionName = "1.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // TODO: secrets.properties 에 실제 값 채우기 (커밋 금지)
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        // MapTiler 벡터 타일 키 -> MapLibre 스타일 JSON 의 __MAPTILER_KEY__ 치환에 사용
        buildConfigField("String", "MAPTILER_KEY", "\"$maptilerKey\"")
        // 인스타 스토리 링크스티커 귀속용 Facebook App ID (없으면 빈 값)
        buildConfigField("String", "INSTAGRAM_APP_ID", "\"$instagramAppId\"")
        // Unity Ads(보상형) — 게임 ID / 보상형 배치 ID / 테스트 모드
        buildConfigField("String", "LEVELPLAY_APP_KEY", "\"$levelPlayAppKeyAndroid\"")
        buildConfigField("String", "LEVELPLAY_REWARDED_AD_UNIT", "\"$levelPlayRewardedAdUnitAndroid\"")
        // AdMob 폴백 — 보상형 단위는 BuildConfig, 앱 ID 는 매니페스트 메타데이터로.
        buildConfigField("String", "ADMOB_REWARDED_AD_UNIT", "\"$admobRewardedAdUnitAndroid\"")
        manifestPlaceholders["ADMOB_APP_ID"] = admobAppIdAndroid
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = rootProject.file(keystoreProps.getProperty("STORE_FILE"))
                storePassword = keystoreProps.getProperty("STORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("KEY_ALIAS")
                keyPassword = keystoreProps.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // ProGuard/R8 코드 축소·난독화 활성 (Firestore 리플렉션 대상은 proguard-rules.pro 에서 keep)
            isMinifyEnabled = true
            // 미사용 리소스 제거(코드 축소와 함께 동작) — 용량 최소화.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // keystore.properties 가 있을 때만 릴리즈 서명 적용
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // 디버그(Android Studio Run)도 릴리즈 키로 서명 → CLI 릴리즈 설치본과 서명이 같아
            // "서명이 다른 앱이 이미 있습니다(제거하시겠습니까?)" 충돌이 안 난다.
            // (keystore.properties 없으면 기본 디버그 키로 폴백)
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    // 공용 KMP 모듈
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    // 사진 크롭 시 EXIF 회전 보정(InputStream 지원).
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // AppCompat: themes.xml 의 Theme.AppCompat.* 상속용.
    // (기존엔 네이버맵 의존성이 transitive 로 제공했으나 Google Maps 전환으로 명시 추가)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // 구글 로그인
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.foundation.layout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.ktx)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.messaging)
    implementation(libs.jwt.decode)

    implementation("io.coil-kt:coil-compose:2.6.0")
    // 부메랑(3초 움짤) GIF 미리보기/상세 표시 — Coil GIF 디코더
    implementation("io.coil-kt:coil-gif:2.6.0")
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.29")

    // 부메랑 촬영 — CameraX 커스텀 카메라(프리뷰 + 프레임 버스트 분석)
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.compose.material:material-icons-extended")

    // 로그인 인트로 영상(무음 mp4 + 동적 속도 제어) 재생용
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // 지도: MapLibre GL Native (Google Maps 대체) + 위치
    implementation(libs.maplibre.android)
    implementation(libs.play.services.location)

    // 광고: Unity LevelPlay(미디에이션) + Unity Ads 네트워크 — 100m 밖 게시물 열람 해제용 보상형.
    // 앱 키/광고 단위 ID 는 secrets.properties 주입(BuildConfig) — 코드에 하드코딩 금지.
    // Unity Ads 어댑터 POM 에 SDK 의존성이 없어 unity-ads 를 **직접** 명시해야 한다(어댑터 5.13.0 ↔ SDK 4.20.1 짝).
    // play-services-ads-identifier / appset 은 LevelPlay 가이드 필수 항목(광고 ID·앱 세트 ID → 단가/빈도 제한).
    implementation("com.unity3d.ads-mediation:mediation-sdk:9.6.0")
    implementation("com.unity3d.ads-mediation:unityads-adapter:5.13.0")
    implementation("com.unity3d.ads:unity-ads:4.20.1")
    implementation("com.google.android.gms:play-services-ads-identifier:18.3.0")
    implementation("com.google.android.gms:play-services-appset:16.1.0")
    // 광고 폴백: Google AdMob 보상형(LevelPlay no fill 대비). 광고 단위는 secrets 주입(미설정 시 디버그 한정 테스트 단위).
    implementation("com.google.android.gms:play-services-ads:25.5.0")
}
