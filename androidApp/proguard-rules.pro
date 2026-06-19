# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============================================================================
# Stary 릴리즈(R8) 룰
# ============================================================================

# 크래시 스택트레이스 가독성을 위해 줄번호/소스파일 보존
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# 제네릭/어노테이션/시그니처 보존 (Firestore·직렬화 리플렉션에 필요)
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses

# --- Firestore POJO 매핑 (doc.toObject(Diary::class.java) 등 리플렉션) -------
# 모든 도메인 모델은 필드/생성자 유지해야 toObject 가 동작한다.
-keep class com.chaminwoo.stary.core.model.** { *; }
-keep class com.chaminwoo.stary.core.geo.** { *; }
-keepclassmembers class com.chaminwoo.stary.core.model.** {
    <init>();
    <fields>;
}
# Firestore 어노테이션 사용 클래스 보호
-keepnames class com.google.firebase.firestore.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# --- kotlinx.serialization ---------------------------------------------------
-keepclassmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.chaminwoo.stary.**$$serializer { *; }

# --- Kotlin 메타데이터 / 코루틴 ----------------------------------------------
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# --- MapLibre GL Native ------------------------------------------------------
-keep class org.maplibre.android.** { *; }
-keep interface org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# --- Coil / GIF drawable -----------------------------------------------------
-dontwarn pl.droidsonroids.gif.**
-keep class pl.droidsonroids.gif.** { *; }

# --- auth0 jwtdecode + Gson --------------------------------------------------
# JWT(idToken) 는 Gson 리플렉션으로 페이로드(sub 등)를 파싱한다. R8 가 모델/필드를
# 지우면 getUserIdFromToken() 이 예외→null 을 반환해 currentUserId 가 null 이 되고
# 릴리즈에서만 "로그인 안 됨 + 다이어리 필터 깨짐" 증상이 난다. (디버그는 R8 미적용이라 정상)
-keep class com.auth0.android.jwt.** { *; }
-keepclassmembers class com.auth0.android.jwt.** { *; }
# Gson (jwtdecode 내부 사용)
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn com.google.gson.**