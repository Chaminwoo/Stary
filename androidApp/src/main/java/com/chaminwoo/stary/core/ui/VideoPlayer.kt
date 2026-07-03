package com.chaminwoo.stary.core.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 짧은 영상(3초 이내)을 루프 자동재생하는 플레이어. 업로드 미리보기 + 상세 조회 공용.
 *
 * - VideoView(플랫폼 내장, 추가 의존성 없음)로 로컬 Uri / 원격 URL 을 모두 재생(Uri.parse).
 * - [muted] true 면 음소거(미리보기·자동재생용). 화면을 벗어나면 정지/해제.
 */
@Composable
fun LoopingVideoPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(uri)
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    if (muted) mp.setVolume(0f, 0f)
                    start()
                }
            }
        },
        update = { view ->
            if (!view.isPlaying) view.start()
        },
        onRelease = { view -> view.stopPlayback() }, // 컴포지션 이탈 시 재생 정지·리소스 해제
        modifier = modifier,
    )
}
