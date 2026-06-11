package com.chaminwoo.stary.feature.auth.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.StarDiaryButton
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView

@Composable
fun LoginScreen(onLoginClick: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showUI by remember { mutableStateOf(false) }
    val gifRef = remember { mutableStateOf<GifDrawable?>(null) }

    LaunchedEffect(Unit) {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            LocationHelper.startContinuousUpdates(context)
        }
        delay(1_500)
        showUI = true
    }

    // GIF 재생 속도: 전체적으로 빠르게(2.5x 시작), 종반에만 살짝 감속(하한 0.5x)
    LaunchedEffect(gifRef.value) {
        val drawable = gifRef.value ?: return@LaunchedEffect
        val totalFrames = drawable.numberOfFrames
        if (totalFrames <= 0) return@LaunchedEffect
        drawable.setSpeed(2.5f)

        while (drawable.isRunning) {
            val progress = drawable.currentFrameIndex.toFloat() / totalFrames
            if (progress <= 0.5f) {
                // 0% → 50%: 2.5x → 1.8x
                val fastProgress = progress / 0.5f
                drawable.setSpeed(2.5f - fastProgress * 0.7f)
            } else if (progress >= 0.75f) {
                // 75% → 100%: 1.8x → 0.5x 로 감속 (과도하게 느려지지 않게 하한 0.5x)
                val slowProgress = (progress - 0.75f) / 0.25f
                drawable.setSpeed((1.8f - slowProgress * 1.3f).coerceAtLeast(0.5f))
            }
            delay(50)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                GifImageView(ctx).apply {
                    val drawable = GifDrawable(ctx.resources, R.drawable.login)
                    drawable.loopCount = 1
                    setImageDrawable(drawable)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    gifRef.value = drawable
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = 1.12f, scaleY = 1.12f, clip = true)
        )

        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn(animationSpec = tween(800))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Stary",
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-1).sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-80).dp)
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StarDiaryButton(
                        text = "Google 계정으로 로그인",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            coroutineScope.launch {
                                val idToken = GoogleAuthHelper.signInWithGoogle(context)
                                if (idToken != null) onLoginClick()
                                else Toast.makeText(context, "로그인에 실패했습니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onLoginClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "로그인 없이 둘러보기",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
