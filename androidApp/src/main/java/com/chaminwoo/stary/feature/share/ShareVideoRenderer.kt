package com.chaminwoo.stary.feature.share

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import android.view.Surface
import com.chaminwoo.stary.core.model.Diary
import com.chaminwoo.stary.feature.globe.GlobeRenderer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sin

/**
 * 공유 영상 만들기 — 앱의 3D 글로브([GlobeRenderer], 스크립트 모드)를 **화면 없이** 한 프레임씩 그려
 * H.264 인코더(MediaCodec)의 입력 Surface 로 바로 보내고 mp4 로 묶는다. 실시간 녹화가 아니라
 * 정해진 대본([ShareVideoSpec])대로 그리므로 기기 성능과 상관없이 같은 영상이 나온다.
 *
 * 한 프레임 = ① 글로브(카메라·시각을 대본대로) → ② 고리가 지나가는 자리의 일그러짐([Ripple]) → ③ 빛(번짐·고리·빛기둥, 더하기 합성)
 * → ④ 주인공 별빛·글 블록(덮어 그리기). ③④는 [ShareVideoOverlay] 가 미리 만든 그림을 사각형에 붙여 위치·크기·투명도만 바꾼다.
 *
 * ⚠️ [render] 는 **한 스레드에서 끝까지** 돌아야 한다(EGL 컨텍스트가 스레드에 묶인다) — 코루틴에서 부를 땐 중간에 suspend 하지 말 것.
 * iOS 대응: `ShareVideoRenderer.swift`(Metal 오프스크린 + AVAssetWriter).
 */
internal class ShareVideoRenderer(
    private val context: Context,
    private val diary: Diary,
    /** 지구 위에 같이 띄울 작은 불빛들(내 다른 별). 주인공 별은 따로 넣지 않아도 된다. */
    private val skyDiaries: List<Diary>,
) {
    /** @return 끝까지 만들었으면 true. 취소([isCancelled])·실패면 false(만들던 파일은 지운다). */
    fun render(out: File, onProgress: (Float) -> Unit, isCancelled: () -> Boolean): Boolean {
        // 1080p 세로 인코딩을 못 하는 기기는 720p 로 한 번 더 시도한다(글·빛은 디자인 px 비율로 그려 크기와 무관).
        for ((w, h) in listOf(1080 to 1920, 720 to 1280)) {
            val ok = runCatching { renderAt(w, h, out, onProgress, isCancelled) }
                .onFailure { Log.w(TAG, "render ${w}x$h failed: $it") }
                .getOrDefault(false)
            if (ok) return true
            out.delete()
            if (isCancelled()) return false
        }
        return false
    }

    private fun renderAt(width: Int, height: Int, out: File, onProgress: (Float) -> Unit, isCancelled: () -> Boolean): Boolean {
        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, if (width >= 1080) 8_000_000 else 5_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, ShareVideoSpec.FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        var codec: MediaCodec? = null
        var input: Surface? = null
        var muxer: MediaMuxer? = null
        var egl: Egl? = null
        var overlay: ShareVideoOverlay? = null
        var finished = false
        try {
            codec = MediaCodec.createEncoderByType(MIME)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            input = codec.createInputSurface()
            codec.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val sink = Sink(codec, muxer)
            egl = Egl(input)

            val globe = GlobeRenderer(context)
            val script = GlobeRenderer.Script(
                ndcShiftY = ShareVideoSpec.NDC_SHIFT_Y,
                sunDir = ShareVideoSpec.sunDir(diary.latitude, diary.longitude),
            )
            globe.script = script
            globe.setDiaries((skyDiaries.filter { it.id != diary.id } + diary).distinctBy { it.id })
            globe.onSurfaceCreated(null, null)
            globe.onSurfaceChanged(null, width, height)

            val art = ShareVideoOverlay(context, diary).also { overlay = it }
            val quads = Quads()
            val ripple = Ripple(width, height)
            val topTex = texture(art.topBlock.bitmap)
            val bottomTex = texture(art.bottomBlock.bitmap)
            val beamTex = texture(art.beam)
            val glowTex = texture(art.glow)
            val ringTex = texture(art.ring)
            val heroTex = texture(art.hero)

            for (i in 0 until ShareVideoSpec.FRAME_COUNT) {
                if (isCancelled()) return false
                val t = i.toFloat() / ShareVideoSpec.FPS
                val pose = ShareVideoSpec.pose(t, diary.latitude, diary.longitude)
                globe.yawDeg = pose.yawDeg
                globe.pitchDeg = pose.pitchDeg
                globe.camDist = pose.dist
                script.timeSec = t
                script.fade = (t / ShareVideoSpec.FADE_IN).coerceIn(0f, 1f)
                GLES20.glViewport(0, 0, width, height)
                globe.onDrawFrame(null)

                val star = ShareVideoSpec.project(diary.latitude, diary.longitude, STAR_RADIUS, pose)
                val ring = if (star[2] > 0f) ShareVideoSpec.ringPhase(t) else -1f
                if (ring >= 0f) ripple.apply(star[0], star[1], ShareVideoSpec.ringRadius(ring), ShareVideoSpec.rippleAmp(ring))
                drawOverlay(quads, art, t, star, ring, topTex, bottomTex, beamTex, glowTex, ringTex, heroTex)

                egl.present(i * 1_000_000_000L / ShareVideoSpec.FPS)
                sink.drain(end = false)
                onProgress((i + 1f) / ShareVideoSpec.FRAME_COUNT)
            }
            codec.signalEndOfInputStream()
            sink.drain(end = true)
            finished = sink.wroteFrames
            return finished
        } finally {
            overlay?.recycle()
            egl?.release()
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { input?.release() }
            // 프레임을 하나도 못 쓴 muxer 는 stop() 이 예외를 던진다 — 삼키고 닫기만 한다.
            runCatching { if (finished) muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    /** 글로브 위에 얹는 빛·글 — 순서가 곧 겹치는 순서다. */
    private fun drawOverlay(
        q: Quads, art: ShareVideoOverlay, t: Float, star: FloatArray, ring: Float,
        topTex: Int, bottomTex: Int, beamTex: Int, glowTex: Int, ringTex: Int, heroTex: Int,
    ) {
        val sx = star[0]; val sy = star[1]
        q.begin()

        // 별이 켜진다 — 살짝 넘쳤다 자리 잡고, 그 뒤로는 천천히 숨 쉰다.
        val on = ShareVideoSpec.progress(t, ShareVideoSpec.STAR_AT, ShareVideoSpec.STAR_DUR)
        if (on > 0f && star[2] > 0f) {
            val lit = ShareVideoSpec.progress(t, ShareVideoSpec.STAR_AT, 0.3f)
            val breathe = 1f + 0.06f * sin(t * 5.0f)
            val glowR = ShareVideoSpec.GLOW_HALF * ShareVideoSpec.easeOutCubic(on) * breathe
            q.draw(glowTex, sx - glowR, sy - glowR, glowR * 2f, glowR * 2f, lit, additive = true)

            // 고리 — 3초마다 하나씩 퍼진다(그 자리의 일그러짐은 호출부가 먼저 그려 두었다).
            if (ring >= 0f) {
                val r = ShareVideoSpec.ringRadius(ring)
                val ry = r * ShareVideoSpec.RING_SQUASH
                q.draw(ringTex, sx - r, sy - ry, r * 2f, ry * 2f, ShareVideoSpec.ringAlpha(ring), additive = true)
            }

            // 빛기둥 — 별에서 위로 자라난다(텍스처를 길이에 맞춰 늘리므로 끝은 항상 부드럽다).
            val rise = ShareVideoSpec.easeOutCubic(ShareVideoSpec.progress(t, ShareVideoSpec.BEAM_AT, ShareVideoSpec.BEAM_DUR))
            if (rise > 0f) {
                val len = (sy - ShareVideoSpec.BEAM_TOP).coerceAtLeast(0f) * rise
                val shimmer = 0.92f + 0.08f * sin(t * 7.0f)
                q.draw(beamTex, sx - ShareVideoSpec.BEAM_HALF_W, sy - len, ShareVideoSpec.BEAM_HALF_W * 2f, len, shimmer, additive = true)
            }

            val half = ShareVideoSpec.STAR_HALF * ShareVideoSpec.easeOutBack(on) * breathe
            q.draw(heroTex, sx - half, sy - half, half * 2f, half * 2f, lit, additive = false)
        }

        // 글 — 아래에서 살짝 떠오르며 나타난다(위쪽 먼저, 아래쪽이 뒤따른다).
        val topIn = ShareVideoSpec.easeOutCubic(ShareVideoSpec.progress(t, ShareVideoSpec.TITLE_AT, ShareVideoSpec.TEXT_DUR))
        if (topIn > 0f) drawBlock(q, topTex, art.topBlock, topIn)
        val bottomIn = ShareVideoSpec.easeOutCubic(ShareVideoSpec.progress(t, ShareVideoSpec.CTA_AT, ShareVideoSpec.TEXT_DUR))
        if (bottomIn > 0f) drawBlock(q, bottomTex, art.bottomBlock, bottomIn)

        q.end()
    }

    private fun drawBlock(q: Quads, tex: Int, block: ShareVideoOverlay.Block, appear: Float) {
        val y = block.top + (1f - appear) * ShareVideoSpec.TEXT_RISE
        q.draw(tex, 0f, y, block.bitmap.width.toFloat(), block.bitmap.height.toFloat(), appear, additive = false)
    }

    private fun texture(bmp: Bitmap): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return ids[0]
    }

    /**
     * 그림 한 장을 화면 사각형에 붙여 그리는 최소 셰이더. 텍스처는 프리멀티플라이드라
     * 더하기(ONE, ONE)는 "색 × 밝기"를 더하고, 덮어 그리기(ONE, 1−a)는 그대로 얹는다. [draw] 의 alpha 는 전체 배율.
     */
    private class Quads {
        private val program: Int
        private val aPos: Int
        private val aUV: Int
        private val uTex: Int
        private val uMul: Int
        private val buf: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        init {
            fun compile(type: Int, src: String): Int {
                val id = GLES20.glCreateShader(type)
                GLES20.glShaderSource(id, src)
                GLES20.glCompileShader(id)
                val ok = IntArray(1)
                GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0)
                check(ok[0] != 0) { "overlay shader: ${GLES20.glGetShaderInfoLog(id)}" }
                return id
            }
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, VS))
            GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, FS))
            GLES20.glLinkProgram(program)
            aPos = GLES20.glGetAttribLocation(program, "aPos")
            aUV = GLES20.glGetAttribLocation(program, "aUV")
            uTex = GLES20.glGetUniformLocation(program, "uTex")
            uMul = GLES20.glGetUniformLocation(program, "uMul")
        }

        fun begin() {
            GLES20.glUseProgram(program)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glUniform1i(uTex, 0)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glEnableVertexAttribArray(aUV)
        }

        /** [left]/[top]/[w]/[h] = 디자인 px(왼쪽 위 원점). */
        fun draw(tex: Int, left: Float, top: Float, w: Float, h: Float, alpha: Float, additive: Boolean) {
            if (alpha <= 0f || w <= 0f || h <= 0f) return
            val x0 = left / ShareVideoSpec.W * 2f - 1f
            val x1 = (left + w) / ShareVideoSpec.W * 2f - 1f
            val y0 = 1f - top / ShareVideoSpec.H * 2f
            val y1 = 1f - (top + h) / ShareVideoSpec.H * 2f
            buf.clear()
            buf.put(floatArrayOf(x0, y0, 0f, 0f, x1, y0, 1f, 0f, x0, y1, 0f, 1f, x1, y1, 1f, 1f))
            buf.position(0)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, buf)
            buf.position(2)
            GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 16, buf)
            GLES20.glBlendFunc(GLES20.GL_ONE, if (additive) GLES20.GL_ONE else GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glUniform4f(uMul, alpha, alpha, alpha, alpha)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        /** 글로브 렌더러가 기대하는 상태로 되돌린다(깊이 테스트 켬, 블렌드 끔). */
        fun end() {
            GLES20.glDisableVertexAttribArray(aPos)
            GLES20.glDisableVertexAttribArray(aUV)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        }

        private companion object {
            const val VS = "attribute vec2 aPos; attribute vec2 aUV; varying vec2 vUV;\n" +
                "void main() { vUV = aUV; gl_Position = vec4(aPos, 0.0, 1.0); }"
            const val FS = "precision mediump float; uniform sampler2D uTex; uniform vec4 uMul; varying vec2 vUV;\n" +
                "void main() { gl_FragColor = texture2D(uTex, vUV) * uMul; }"
        }
    }

    /**
     * 고리가 지나가는 자리를 살짝 일그러뜨린다 — 방금 그린 글로브에서 별 주위 정사각 영역을 텍스처로 복사한 뒤,
     * 같은 자리에 "고리 근처만 별 쪽/바깥쪽으로 밀어서 읽는" 셰이더로 다시 그린다([ShareVideoSpec.rippleShift] 와 같은 식).
     * 띠 밖은 그대로 읽으므로 영역 가장자리에 이음매가 생기지 않는다. iOS 는 같은 식을 CPU 로 계산한다(`FrameSink.applyRipple`).
     */
    private class Ripple(private val frameW: Int, private val frameH: Int) {
        private val program: Int
        private val aPos: Int
        private val aUV: Int
        private val tex: Int
        private val buf: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        init {
            fun compile(type: Int, src: String): Int {
                val id = GLES20.glCreateShader(type)
                GLES20.glShaderSource(id, src)
                GLES20.glCompileShader(id)
                val ok = IntArray(1)
                GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0)
                check(ok[0] != 0) { "ripple shader: ${GLES20.glGetShaderInfoLog(id)}" }
                return id
            }
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, VS))
            GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, FS))
            GLES20.glLinkProgram(program)
            aPos = GLES20.glGetAttribLocation(program, "aPos")
            aUV = GLES20.glGetAttribLocation(program, "aUV")
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            tex = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }

        /** [sx]/[sy] = 별 위치, [radius] = 고리 반지름, [amp] = 가장 크게 밀리는 거리(전부 디자인 px). */
        fun apply(sx: Float, sy: Float, radius: Float, amp: Float) {
            if (amp < 0.05f) return
            // 영역이 화면 밖으로 나가지 않게 줄인다(프레임 버퍼 밖을 복사하면 값이 정해져 있지 않다).
            val half = minOf(ShareVideoSpec.RIPPLE_HALF, sx, ShareVideoSpec.W - sx, minOf(sy, ShareVideoSpec.H - sy))
            if (half < 16f) return
            val k = frameW / ShareVideoSpec.W                      // 디자인 px → 실제 픽셀
            val side = (half * 2f * k).toInt()
            val x0 = ((sx - half) * k).toInt()
            val y0 = ((ShareVideoSpec.H - (sy + half)) * k).toInt() // GL 프레임 버퍼는 아래가 0
            if (side < 8 || x0 < 0 || y0 < 0 || x0 + side > frameW || y0 + side > frameH) return

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glCopyTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, x0, y0, side, side, 0)

            // 복사한 픽셀 사각형과 정확히 같은 자리에 다시 그린다(텍스처는 아래 행이 v=0).
            val nx0 = x0.toFloat() / frameW * 2f - 1f
            val nx1 = (x0 + side).toFloat() / frameW * 2f - 1f
            val ny0 = y0.toFloat() / frameH * 2f - 1f
            val ny1 = (y0 + side).toFloat() / frameH * 2f - 1f
            buf.clear()
            buf.put(floatArrayOf(nx0, ny0, 0f, 0f, nx1, ny0, 1f, 0f, nx0, ny1, 0f, 1f, nx1, ny1, 1f, 1f))
            GLES20.glUseProgram(program)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glEnableVertexAttribArray(aUV)
            buf.position(0)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, buf)
            buf.position(2)
            GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 16, buf)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0)
            // 별이 영역 한가운데가 아닐 수 있다(정수 픽셀로 자르면서 생기는 차이) — 영역 안에서의 별 위치를 넘긴다.
            val side1 = side / k
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uStar"), sx - x0 / k, (ShareVideoSpec.H - sy) - y0 / k)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSide"), side1)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uRadius"), radius)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uAmp"), amp * ShareVideoSpec.RIPPLE_NORM)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uWidth"), ShareVideoSpec.RIPPLE_WIDTH)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSquash"), ShareVideoSpec.RING_SQUASH)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPos)
            GLES20.glDisableVertexAttribArray(aUV)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        }

        private companion object {
            const val VS = "attribute vec2 aPos; attribute vec2 aUV; varying vec2 vUV;\n" +
                "void main() { vUV = aUV; gl_Position = vec4(aPos, 0.0, 1.0); }"
            // p = 이 픽셀의 위치(별 기준, 디자인 px). 고리는 세로로 납작한 타원이라 y 를 uSquash 로 펴서 거리를 잰다.
            const val FS = "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n" +
                "uniform sampler2D uTex; uniform vec2 uStar; uniform float uSide; uniform float uRadius;\n" +
                "uniform float uAmp; uniform float uWidth; uniform float uSquash; varying vec2 vUV;\n" +
                "void main() {\n" +
                "  vec2 p = vUV * uSide - uStar;\n" +
                "  vec2 q = vec2(p.x, p.y / uSquash);\n" +
                "  float d = length(q);\n" +
                "  float x = (d - uRadius) / uWidth;\n" +
                "  float shift = uAmp * x * exp(-x * x);\n" +
                "  vec2 dir = d > 0.001 ? q / d : vec2(0.0);\n" +
                "  vec2 src = p - vec2(dir.x, dir.y * uSquash) * shift;\n" +
                "  gl_FragColor = vec4(texture2D(uTex, (src + uStar) / uSide).rgb, 1.0);\n" +
                "}"
        }
    }

    /** 인코더 입력 Surface 에 그리기 위한 EGL(GLES2, 녹화 가능 설정). 깊이 24비트 우선(글로브의 깊이 정밀도 — 04 문서). */
    private class Egl(surface: Surface) {
        private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val eglContext: EGLContext
        private val eglSurface: EGLSurface

        init {
            check(display != EGL14.EGL_NO_DISPLAY) { "no EGL display" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
            val config = chooseConfig(24) ?: chooseConfig(16) ?: error("no recordable EGL config")
            eglContext = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext" }
            eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface" }
            check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) { "eglMakeCurrent" }
        }

        private fun chooseConfig(depthBits: Int): EGLConfig? {
            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, depthBits,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] < 1) return null
            return configs[0]
        }

        /** 이 프레임의 표시 시각을 찍고 인코더로 넘긴다. */
        fun present(timeNs: Long) {
            EGLExt.eglPresentationTimeANDROID(display, eglSurface, timeNs)
            EGL14.eglSwapBuffers(display, eglSurface)
        }

        fun release() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, eglSurface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }

        private companion object {
            const val EGL_RECORDABLE_ANDROID = 0x3142
        }
    }

    /** 인코더 출력 → mp4. 프레임마다 쌓인 만큼 빼고([drain] end=false), 마지막에 끝 표시가 나올 때까지 뺀다. */
    private class Sink(private val codec: MediaCodec, private val muxer: MediaMuxer) {
        private val info = MediaCodec.BufferInfo()
        private var track = -1
        var wroteFrames = false
            private set

        fun drain(end: Boolean) {
            var idle = 0
            while (true) {
                val index = codec.dequeueOutputBuffer(info, if (end) 10_000L else 0L)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!end) return
                        if (++idle > END_WAIT_TRIES) error("encoder did not finish")
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                    }
                    index >= 0 -> {
                        idle = 0
                        val data = codec.getOutputBuffer(index)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (data != null && info.size > 0 && track >= 0) {
                            data.position(info.offset)
                            data.limit(info.offset + info.size)
                            muxer.writeSampleData(track, data, info)
                            wroteFrames = true
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        private companion object {
            const val END_WAIT_TRIES = 500 // × 10ms = 최대 5초
        }
    }

    private companion object {
        const val TAG = "ShareVideo"
        const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        /** 주인공 별의 높이(지구 = 1) — 글로브가 그리는 작은 불빛(GLOW_RADIUS 1.008)과 같은 자리에 얹는다. */
        const val STAR_RADIUS = 1.008f
    }
}
