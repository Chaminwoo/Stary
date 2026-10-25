package com.chaminwoo.stary.core.ui

import android.media.AudioManager
import android.media.MediaPlayer
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
 *   음소거 영상은 오디오 포커스를 요청하지 않는다 — 예전엔 상세 화면만 열어도 듣던 다른 앱 음악이 멈췄다.
 *   소리 있는 전체화면은 잠깐 줄여 달라고만(덕킹) 한다(3초짜리 반복 영상이라 남의 음악을 아예 멈추면 다시 안 켜진다).
 * - [onFirstFrameRendered] 는 첫 프레임이 실제로 화면에 그려진 시점(prepared 보다 늦음 — 검은
 *   화면 없이 정확히 이 시점부터 [MediaLoadingFrame] 페이드인을 트리거하기 위함)에 1회 호출된다.
 */
@Composable
fun LoopingVideoPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
    onFirstFrameRendered: () -> Unit = {},
) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setAudioFocusRequest(
                    if (muted) AudioManager.AUDIOFOCUS_NONE else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                setVideoURI(uri)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onFirstFrameRendered()
                    false
                }
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
