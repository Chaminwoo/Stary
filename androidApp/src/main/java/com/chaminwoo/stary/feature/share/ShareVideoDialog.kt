package com.chaminwoo.stary.feature.share

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.core.ui.StarLoadingIndicator
import kotlinx.coroutines.launch
import java.io.File
import com.chaminwoo.stary.core.ui.StaryButton
import com.chaminwoo.stary.core.ui.StaryButtonKind

/**
 * 공유 화면 — 공유 버튼을 누르면 뜨는 전체 화면 다이얼로그.
 * 뜨자마자 스토리용 영상(8초)을 만들고(진행률 표시), 다 되면 반복 재생으로 미리 보여 준다.
 * 아래 두 버튼: **SNS에 공유**(인스타 스토리, 없으면 공유 시트) · **저장**(갤러리).
 * 예전 공유 카드 편집 화면(ShareCardEditor)을 대체한다 — 편집 기능은 없다(2026-10-11 사용자 결정).
 *
 * ⚠️ DetailScreen 본체에 인라인하지 말 것 — dex 메서드 레지스터 한계(VerifyError) 방지를 위해 별도 파일로 둔다.
 */
@Composable
fun ShareVideoDialog(diary: Diary, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = StarStyle.colorOf(diary.starColor)

    var progress by remember { mutableFloatStateOf(0f) }
    var video by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // 다이얼로그가 닫히면 이 effect 가 취소되고, 렌더러는 다음 프레임에서 멈춘다.
    LaunchedEffect(diary.id) {
        val file = ShareVideoHelper.generate(context, diary) { progress = it }
        if (file != null) video = file else failed = true
    }

    fun save(file: File) {
        scope.launch {
            saving = true
            val ok = ShareVideoHelper.saveToGallery(context, file)
            saving = false
            // 다이얼로그(별도 창) 위에서도 보이도록 시스템 토스트를 쓴다(StaryToast 는 다이얼로그 뒤에 그려진다).
            Toast.makeText(
                context, context.getString(if (ok) R.string.share_video_saved else R.string.share_video_save_failed), Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val file = video
        if (granted && file != null) save(file)
        else if (!granted) Toast.makeText(context, context.getString(R.string.share_video_save_failed), Toast.LENGTH_SHORT).show()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF7090D16))
                .systemBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.share_video_title),
                    color = Color(0xFFF0F0F0), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close), tint = Color.White)
                }
            }

            // 미리보기 — 남는 높이에 9:16 으로 맞춘다.
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF04050C)),
                    contentAlignment = Alignment.Center,
                ) {
                    val file = video
                    when {
                        file != null -> LoopingVideo(file)
                        failed -> Text(
                            stringResource(R.string.share_failed),
                            color = Color(0xFFB9C0D0), fontSize = 13.sp, modifier = Modifier.padding(24.dp),
                        )
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StarLoadingIndicator(size = 40.dp, color = accent)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                stringResource(R.string.share_video_making, (progress * 100).toInt()),
                                color = Color(0xFFB9C0D0), fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val ready = video != null
                StaryButton(
                    stringResource(R.string.share_to_story),
                    { video?.let { ShareVideoHelper.shareToSns(context, diary, it) } },
                    Modifier.weight(1f),
                    enabled = ready,
                )
                StaryButton(
                    stringResource(R.string.share_video_save),
                    onClick = {
                        val file = video ?: return@StaryButton
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                            PackageManager.PERMISSION_GRANTED
                        if (ShareVideoHelper.needsStoragePermission && !granted) {
                            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else save(file)
                    },
                    modifier = Modifier.weight(1f),
                    kind = StaryButtonKind.Ghost,
                    enabled = ready,
                    loading = saving,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** 만든 영상을 소리 없이 반복 재생(영상에는 소리가 없다). 화면을 떠나면 플레이어를 놓는다. */
@Composable
private fun LoopingVideo(file: File) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        // surface_type=texture_view(둥근 모서리로 잘리게) 등은 XML(view_login_player)에서 설정.
        factory = { ctx -> LayoutInflater.from(ctx).inflate(R.layout.view_login_player, null) as PlayerView },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}
