package com.chaminwoo.stary

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chaminwoo.stary.core.designsystem.StaryTheme
import com.chaminwoo.stary.feature.home.screen.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 푸시 알림 표시용 권한 (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
            )
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
