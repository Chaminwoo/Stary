package com.chaminwoo.stary.feature.share

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaminwoo.stary.core.model.Diary
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 공유 영상이 실제 기기(EGL + H.264 인코더)에서 끝까지 만들어지는지 확인하는 스모크 테스트.
 * 결과물은 앱 캐시의 `share_video_test.mp4` — 눈으로 보려면 테스트 뒤에
 * `adb exec-out run-as com.chaminwoo.stary_ios cat cache/share_video_test.mp4 > out.mp4` 로 꺼낸다
 * (`connectedAndroidTest` 는 끝나면 앱을 지우므로, 파일을 보려면 `am instrument` 로 직접 돌릴 것).
 */
@RunWith(AndroidJUnit4::class)
class ShareVideoRendererTest {
    @Test
    fun rendersPlayableVideo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val diary = Diary(
            id = "test", title = "비 오는 날, 연남동 골목", latitude = 37.5627, longitude = 126.9255,
            createdAt = 1_791_676_800_000L, starType = 0, starColor = 1,
        )
        val sky = listOf(35.18 to 129.08, 33.5 to 126.53, 35.68 to 139.69, 34.69 to 135.5, 25.03 to 121.56)
            .mapIndexed { i, (lat, lng) -> Diary(id = "sky$i", latitude = lat, longitude = lng, starColor = 7 + i) }
        val out = File(context.cacheDir, "share_video_test.mp4")
        out.delete()

        val ok = ShareVideoRenderer(context, diary, sky).render(out, onProgress = {}, isCancelled = { false })

        assertTrue("render reported failure", ok)
        assertTrue("video too small: ${out.length()} bytes", out.length() > 200_000)
    }
}
