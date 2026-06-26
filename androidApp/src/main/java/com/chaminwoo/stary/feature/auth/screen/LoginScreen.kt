package com.chaminwoo.stary.feature.auth.screen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.ui.StarDiaryButton
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun LoginScreen(onLoginClick: () -> Unit, onVideoEnded: () -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showUI by remember { mutableStateOf(false) }

    // 로그인 인트로 영상(무음 mp4). res/raw/login_video.mp4 를 1회 재생.
    // prepare()/재생 시작은 PlayerView attach 이후(update 블록)에서 1회 — 첫 프레임 누락 방지.
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.login_video}")
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f                                   // 소리 미사용(영상만)
            playbackParameters = PlaybackParameters(2.5f)
            playWhenReady = false                          // 첫 프레임 렌더 후에 재생 시작(아래 리스너)
        }
    }
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            // surface 준비+첫 프레임이 그려진 뒤 처음부터 재생 시작(빈 화면/조기 종료 방지).
            override fun onRenderedFirstFrame() {
                exoPlayer.playWhenReady = true
            }
            // 영상이 다 끝난 뒤에 지도를 로드한다(영상 우선 — 재생 중 무거운 GL 초기화로 프리즈 방지).
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onVideoEnded()
            }
        }
        exoPlayer.addListener(listener)
        exoPlayer.prepare()   // 리스너 등록 후 prepare → 첫 프레임 콜백 누락 방지
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(Unit) {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            LocationHelper.startContinuousUpdates(context)
        }
        delay(1_500)
        showUI = true
    }

    // 재생 속도: 전체적으로 빠르게(2.5x 시작), 종반에만 살짝 감속(하한 0.5x) — 기존 GIF 속도 곡선과 동일.
    LaunchedEffect(exoPlayer) {
        // 영상 길이가 확정될 때까지 대기(최대 ~2초).
        var waited = 0
        while (exoPlayer.duration <= 0 && waited < 2_000) {
            delay(16); waited += 16
        }
        val total = exoPlayer.duration.toFloat()
        if (total <= 0) return@LaunchedEffect
        exoPlayer.playbackParameters = PlaybackParameters(2.5f)

        while (exoPlayer.playbackState != Player.STATE_ENDED) {
            val progress = exoPlayer.currentPosition.toFloat() / total
            if (progress <= 0.5f) {
                // 0% → 50%: 2.5x → 1.8x
                val fastProgress = progress / 0.5f
                exoPlayer.playbackParameters = PlaybackParameters(2.5f - fastProgress * 0.7f)
            } else if (progress >= 0.75f) {
                // 75% → 100%: 1.8x → 0.5x 로 감속 (과도하게 느려지지 않게 하한 0.5x)
                val slowProgress = (progress - 0.75f) / 0.25f
                exoPlayer.playbackParameters =
                    PlaybackParameters((1.8f - slowProgress * 1.3f).coerceAtLeast(0.5f))
            }
            delay(50)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                // surface_type=texture_view 등은 XML(view_login_player)에서 설정.
                LayoutInflater.from(ctx).inflate(R.layout.view_login_player, null) as PlayerView
            },
            // 첫 표시 때 첫 프레임이 안 보이는 문제 방지: 바인딩/재생 시작을 attach 이후인 update 에서.
            update = { view -> view.player = exoPlayer },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = 1.12f, scaleY = 1.12f, clip = true)
        )

        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn(animationSpec = tween(800))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-80).dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 밝게 보정용 컬러필터(RGB 1.7배) — 후광을 더 환하게.
                    val brighten = remember {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(1.7f, 1.7f, 1.7f, 1f) })
                    }
                    // 뒤에 깔리는 빛나는 후광: 로고 복제 + 블러 + 밝게 + 살짝 확대.
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = brighten,
                        alpha = 0.9f,
                        modifier = Modifier
                            .width(225.dp)
                            .blur(12.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    )
                    // 선명한 로고(앞).
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = "Stary",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.width(220.dp)
                    )
                }

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
                                else com.chaminwoo.stary.core.ui.StaryToast.show("잠시만 기다려주세요")
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
