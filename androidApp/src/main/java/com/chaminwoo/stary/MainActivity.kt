package com.chaminwoo.stary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chaminwoo.stary.core.designsystem.StaryTheme
import com.chaminwoo.stary.feature.home.screen.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            StaryTheme {
                MainScreen()
            }
        }
    }
}