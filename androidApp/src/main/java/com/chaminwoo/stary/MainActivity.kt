package com.chaminwoo.stary

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chaminwoo.stary.core.designsystem.StaryTheme
import com.chaminwoo.stary.core.util.LocaleManager
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.home.screen.MainScreen

class MainActivity : ComponentActivity() {
    // 저장된 인앱 언어를 모든 리소스 해석 전에 적용(시스템 기본이면 원본 그대로).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 영속된 로그인 세션 복원 — 있으면 로그인 화면을 건너뛰고 바로 지도로 진입한다.
        GoogleAuthHelper.restoreSession()

        // 앱 시작 즉시 필요한 권한 요청 — 위치(지도 초기 위치)·푸시 알림(API 33+).
        // 위치를 늦게 받으면 초기 지도가 기본 좌표로 떠서 주변 다이어리가 안 보이므로 켜자마자 요청한다.
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
        }

        // 푸시 알림 탭 → 해당 다이어리 상세로 딥링크
        val initialDiaryId = intent?.getStringExtra(EXTRA_DIARY_ID)

        enableEdgeToEdge()
        setContent {
            StaryTheme {
                MainScreen(initialDiaryId = initialDiaryId)
            }
        }
    }

    companion object {
        const val EXTRA_DIARY_ID = "diaryId"
    }
}
