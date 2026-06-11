plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // AGP 9 부터 KMP 라이브러리는 com.android.library 대신 이 플러그인을 사용한다.
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    // Android 타깃 (새 AGP KMP DSL — 별도 최상위 android {} 블록 없이 kotlin{} 안에서 설정)
    android {
        namespace = "com.chaminwoo.stary.shared"
        compileSdk = 36
        minSdk = 26
    }

    // iOS 타깃 — 실제 네이티브 컴파일/프레임워크 링크는 macOS + Xcode 환경에서만 수행된다.
    // (Windows 호스트에서는 commonMain 메타데이터까지만 처리되고 iOS 링크 태스크는 비활성화됨)
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
