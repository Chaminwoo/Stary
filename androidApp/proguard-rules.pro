# ============================================================================
# Stary 릴리즈(R8) 룰
# ============================================================================
# ⚠️ 패키지 전체를 통째로 보존하는 규칙(`-keep class com.example.** { *; }`)을 쓰지 말 것.
#    R8 이 그 패키지를 축소·최적화·난독화하지 못해 메모리·성능이 나빠지고, Play Console 이
#    "R8 구성으로 인해 메모리 사용량이 증가하고 성능이 저하될 수 있습니다" 경고를 띄운다(2026-10-14 정리).
#    리플렉션으로 접근하는 **멤버만** 좁게 보존한다. 라이브러리(MapLibre·Firestore·GIF·kotlinx.serialization 등)는
#    AAR/JAR 에 자체 consumer 규칙을 싣고 있으니 여기서 다시 통째로 keep 하지 않는다.
#    점검 방법: docs/code 또는 https://developer.android.com/r8-analyzer (R8 Configuration Analyzer).

# 크래시 스택트레이스 가독성을 위해 줄번호/소스파일 보존
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# 제네릭/어노테이션/시그니처 보존 (Firestore·Gson TypeToken 리플렉션에 필요)
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses

# --- Firestore POJO 매핑 (doc.toObject(Diary::class.java) 등 리플렉션) -------
# Firestore CustomClassMapper 는 무인자 생성자로 만들고, getter(get*/is*) 이름으로 속성을 찾은 뒤
# 같은 이름의 필드에 값을 넣는다 → 모델의 무인자 생성자·필드·getter/setter 만 이름째 보존하면 된다.
# (클래스 자체는 Diary::class.java 참조로 살아 있다. 예전엔 패키지 전체 { *; } 였다.)
-keepclassmembers class com.chaminwoo.stary.core.model.** {
    <init>();
    <fields>;
    public *** get*();
    public boolean is*();
    public void set*(***);
}
# @PropertyName 으로 이름을 바꾼 멤버
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# --- auth0 jwtdecode + Gson ---------------------------------------------------
# 로그인 시 JWT(idToken).subject 로 uid 를 읽는다. jwtdecode 는 헤더를 Gson TypeToken<Map<…>> 으로,
# 페이로드를 자체 JsonDeserializer(JWTDeserializer)로 푼다. R8 이 TypeToken 익명 하위 클래스의 제네릭 시그니처를
# 지우면 파싱이 실패해 currentUserId 가 null 이 되고 **릴리즈에서만** "로그인 안 됨"이 난다(예전 사고).
# 이 Gson 버전은 자체 규칙을 싣지 않으므로 Gson 공식 R8 규칙 중 필요한 것만 둔다(예전엔 Gson·jwtdecode 패키지 전체 keep).
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn com.google.gson.**

# --- 경고만 끄기 ----------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-dontwarn org.maplibre.android.**
-dontwarn pl.droidsonroids.gif.**
