package com.chaminwoo.stary

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.chaminwoo.stary.core.util.AppForeground
import com.google.firebase.auth.FirebaseAuth

/**
 * Firebase 는 google-services.json 기반 자동 초기화 사용.
 *
 * Firestore/Storage 보안 규칙(request.auth != null)을 통과하려면 항상 Firebase Auth
 * 세션이 필요하므로, 로그인 전(둘러보기 포함)에는 **익명 로그인**으로 세션을 만든다.
 * (Firebase Console > Authentication > Sign-in method 에서 '익명' 활성화 필요)
 * Google 로그인 시 GoogleAuthHelper 가 signInWithCredential 로 교체한다.
 */
class StaryApplication : Application(), ImageLoaderFactory {

    /**
     * 앱 전역 Coil 로더 — 이미지 "불러오는 텀"을 줄이는 핵심 튜닝.
     *  - respectCacheHeaders(false): Firebase Storage 응답의 보수적 Cache-Control 을 무시하고
     *    항상 디스크에 캐시 → 재방문 시 네트워크 없이 즉시 표시.
     *  - 메모리 25% + 디스크 256MB 캐시로 목록 스크롤 재사용을 넉넉하게.
     *  - GIF 디코더 포함(부메랑 움짤) → GifImage 도 이 싱글턴을 그대로 쓴다.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(120)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
        }
        .build()

    override fun onCreate() {
        super.onCreate()

        // heads-up 알림 채널 사전 생성 — 종료/백그라운드에서 시스템이 표시하는 알림도 상단 배너로 뜨게.
        com.chaminwoo.stary.push.ensureStaryNotificationChannel(this)

        // 앱 전면/후면 추적 — FCM 시스템 알림 vs 인앱 배너 이중 표시 방지에 사용.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = AppForeground.onResumed()
            override fun onActivityPaused(activity: Activity) = AppForeground.onPaused()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnFailureListener { e ->
                    Log.w("StaryApplication", "익명 로그인 실패(콘솔에서 익명 인증 활성화 필요): ${e.localizedMessage}")
                }
        }
    }
}
