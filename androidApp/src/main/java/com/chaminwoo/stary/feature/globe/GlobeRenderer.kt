package com.chaminwoo.stary.feature.globe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.chaminwoo.stary.core.designsystem.StarStyle
import com.chaminwoo.stary.core.model.Diary
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * 3D 행성(지구) 렌더러 — 지도 줌 최소 진입 시 보이는 "밤의 지구" 뷰.
 *
 * 씬 구성(레퍼런스 `references/min_zoom.png` 재현):
 *  1. 배경 별밭(모델과 함께 회전 → "내 시점이 움직이는" 느낌, 미세 트윙클)
 *  2. 지구 구체: 원본 1/2 밝기 — 다이어리 라이트맵으로 별 근처만 1/4 추가 밝힘(부드러운 경계)
 *  3. 궤적 트레일: 완전한 링이 아닌 자유 원호 '선' — 양 끝이 자연스럽게 투명해지고
 *     지구 좌표계에 붙어 있어 구를 돌리면 같이 회전
 *  4. 노란 작은 불빛: 좋아요 [FLARE_MIN_LIKES] 미만 다이어리 1:1 — 도시 야경 점광
 *  5. 별 플레어: 좋아요 [FLARE_MIN_LIKES] 이상 다이어리만, 구 표면 바깥(FLARE_RADIUS),
 *     레퍼런스풍 팔레트로 별마다 색 다르게 + 트윙클
 *
 * 스레딩: UI 스레드가 카메라 상태(@Volatile)를 쓰고 GL 스레드가 읽는다.
 * 별 데이터는 [setDiaries] (백그라운드) → dirty 플래그 → GL 스레드에서 VBO 업로드.
 * 성능: 이 렌더러는 글로브 진입 시에만 생성되고 이탈 시 뷰와 함께 파괴된다(진입 전 비용 0).
 */
class GlobeRenderer(private val context: Context) : GLSurfaceView.Renderer {

    // ── 카메라/인터랙션 상태 (UI 스레드 쓰기, GL 스레드 읽기) ──
    @Volatile var yawDeg = 0f            // 모델 Y 회전(= -경도)
    @Volatile var pitchDeg = 15f         // 모델 X 기울임(= 위도), ±75 클램프
    @Volatile var camDist = ENTER_DIST   // 카메라 거리(지구 반지름=1)
    @Volatile var yawVelDeg = 0f         // 드래그 관성(도/초)
    @Volatile var pitchVelDeg = 0f
    @Volatile var lastInteractionMs = 0L // 마지막 터치 시각(자동 회전 재개 판단)
    @Volatile private var dollyTarget = IDLE_DIST // 진입 돌리-인 목표

    /** 지금 화면 정면에 보이는 지점 (위도, 경도). 지도 복귀 좌표로 사용. */
    fun facingLatLng(): Pair<Double, Double> {
        val lat = pitchDeg.toDouble().coerceIn(-85.0, 85.0)
        var lng = (-yawDeg).toDouble() % 360.0
        if (lng > 180) lng -= 360.0
        if (lng < -180) lng += 360.0
        return lat to lng
    }

    fun setInitialFacing(lat: Double, lng: Double) {
        pitchDeg = lat.toFloat().coerceIn(-75f, 75f)
        yawDeg = (-lng).toFloat()
    }

    // ── 별 데이터 (setDiaries 가 백그라운드에서 빌드 → GL 스레드 업로드) ──
    private var flareData: FloatBuffer? = null
    private var flareVertexCount = 0
    private var glowData: FloatBuffer? = null
    private var glowVertexCount = 0
    @Volatile private var starsDirty = false

    /** 별 근처 지형만 밝히는 등장방형 라이트맵(setDiaries 백그라운드 생성 → GL 업로드, recycle 금지). */
    @Volatile private var lightMapBmp: Bitmap? = null
    private var lightMapTex = 0

    // ── GL 핸들 ──
    private var earthProgram = 0
    private var spriteProgram = 0
    private var ringProgram = 0
    private var earthTex = 0
    private var flareTex = 0
    private var glowTex = 0
    private var earthVbo = 0
    private var earthIbo = 0
    private var earthIndexCount = 0
    private var flareVbo = 0
    private var glowVbo = 0
    private var starfieldVbo = 0
    private var starfieldVertexCount = 0

    /** 자유 원호 트레일(지구 좌표계 — 구와 함께 회전). */
    private class Trail(val vbo: Int, val count: Int, val colorA: FloatArray, val colorB: FloatArray, val speed: Float)
    private val trails = ArrayList<Trail>()

    // ── 행렬 ──
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private val tmp = FloatArray(16)

    private var fade = 0f                 // 진입 페이드(장면 밝기 0→1)
    private var lastFrameNs = 0L
    private val startMs = SystemClock.uptimeMillis()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        earthProgram = buildProgram(EARTH_VS, EARTH_FS)
        spriteProgram = buildProgram(SPRITE_VS, SPRITE_FS)
        ringProgram = buildProgram(RING_VS, RING_FS)

        buildEarthMesh()
        earthTex = loadEarthTexture()
        flareTex = uploadTexture(makeFlareBitmap())
        glowTex = uploadTexture(makeGlowBitmap())
        // 라이트맵 플레이스홀더(전부 어두움) — setDiaries 업로드 전까지 사용
        lightMapTex = uploadTexture(
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF000000.toInt()) }
        )
        buildStarfield()
        buildTrails()
        flareVbo = genBuffer()
        glowVbo = genBuffer()
        starsDirty = true // 서피스 재생성 시(백그라운드 복귀) 재업로드
        fade = 0f
        lastFrameNs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(proj, 0, 42f, aspect, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 0.016f else ((now - lastFrameNs) / 1e9f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now
        val t = (SystemClock.uptimeMillis() - startMs) / 1000f

        stepSimulation(dt)

        if (starsDirty) uploadStars()

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // 카메라 고정(+Z), 지구(모델)만 회전
        val camPos = floatArrayOf(0f, 0f, camDist)
        Matrix.setLookAtM(view, 0, 0f, 0f, camDist, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, pitchDeg, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, yawDeg, 0f, 1f, 0f)
        Matrix.multiplyMM(tmp, 0, vp, 0, model, 0)
        System.arraycopy(tmp, 0, mvp, 0, 16)

        // 1) 배경 별밭 — 깊이 무시하고 먼저(지구가 위에 그려져 가려짐)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
        drawSprites(starfieldVbo, starfieldVertexCount, glowTex, camPos, t, depthTest = false)

        // 2) 지구 본체 (불투명, 깊이 기록)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        drawEarth(camPos)

        // 이하 전부 additive, 깊이 테스트만(기록 X) — 행성 뒤로 가려짐
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
        GLES20.glDepthMask(false)

        // 3) 궤적 트레일 — 지구 좌표계(uMVP)라 구를 돌리면 같이 회전
        for (tr in trails) drawTrail(tr, t)

        // 4) 다이어리 노란 불빛(도시 야경) → 5) 별 플레어
        if (glowVertexCount > 0) drawSprites(glowVbo, glowVertexCount, glowTex, camPos, t, depthTest = true)
        if (flareVertexCount > 0) drawSprites(flareVbo, flareVertexCount, flareTex, camPos, t, depthTest = true)

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    /** 관성/자동회전/돌리인/페이드 진행. */
    private fun stepSimulation(dt: Float) {
        fade = min(1f, fade + dt / 1.1f)
        // 진입 돌리-인 (사용자 핀치가 없을 때만 부드럽게 목표 거리로)
        val sinceTouch = SystemClock.uptimeMillis() - lastInteractionMs
        if (sinceTouch > 250) {
            camDist += (dollyTarget - camDist) * min(1f, dt * 2.0f)
        } else {
            dollyTarget = camDist // 사용자가 만지면 그 거리 유지
        }
        // 드래그 관성
        if (sinceTouch > 60) {
            yawDeg += yawVelDeg * dt
            pitchDeg = (pitchDeg + pitchVelDeg * dt).coerceIn(-75f, 75f)
            val decay = exp(-2.6f * dt)
            yawVelDeg *= decay
            pitchVelDeg *= decay
        }
        // 자동 느린 회전(3초 이상 무입력)
        if (sinceTouch > 3000) yawDeg += dt * 1.7f
    }

    // ────────────────────────── 그리기 ──────────────────────────

    private fun drawEarth(camPos: FloatArray) {
        GLES20.glUseProgram(earthProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(earthProgram, "uMVP"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(earthProgram, "uModel"), 1, false, model, 0)
        GLES20.glUniform3fv(GLES20.glGetUniformLocation(earthProgram, "uCamPos"), 1, camPos, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(earthProgram, "uFade"), fade)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, earthTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(earthProgram, "uTex"), 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lightMapTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(earthProgram, "uLight"), 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, earthVbo)
        val aPos = GLES20.glGetAttribLocation(earthProgram, "aPos")
        val aUV = GLES20.glGetAttribLocation(earthProgram, "aUV")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 20, 0)
        GLES20.glEnableVertexAttribArray(aUV)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 20, 12)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, earthIbo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, earthIndexCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUV)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    /** 트레일 = 지구 좌표계 원호 리본 — uMVP(vp·model)로 그려 구와 함께 회전. */
    private fun drawTrail(tr: Trail, t: Float) {
        if (tr.vbo == 0 || tr.count == 0) return
        GLES20.glUseProgram(ringProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(ringProgram, "uMVP"), 1, false, mvp, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(ringProgram, "uTime"), t)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(ringProgram, "uSpeed"), tr.speed)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(ringProgram, "uFade"), fade)
        GLES20.glUniform3fv(GLES20.glGetUniformLocation(ringProgram, "uColorA"), 1, tr.colorA, 0)
        GLES20.glUniform3fv(GLES20.glGetUniformLocation(ringProgram, "uColorB"), 1, tr.colorB, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tr.vbo)
        val aPos = GLES20.glGetAttribLocation(ringProgram, "aPos")
        val aUV = GLES20.glGetAttribLocation(ringProgram, "aUV")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 20, 0)
        GLES20.glEnableVertexAttribArray(aUV)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 20, 12)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, tr.count)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUV)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawSprites(vbo: Int, count: Int, tex: Int, camPos: FloatArray, t: Float, depthTest: Boolean) {
        if (vbo == 0 || count == 0) return
        if (depthTest) GLES20.glEnable(GLES20.GL_DEPTH_TEST) else GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(spriteProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(spriteProgram, "uVP"), 1, false, vp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(spriteProgram, "uModel"), 1, false, model, 0)
        GLES20.glUniform3fv(GLES20.glGetUniformLocation(spriteProgram, "uCamPos"), 1, camPos, 0)
        // 카메라 고정이므로 right/up 도 고정
        GLES20.glUniform3f(GLES20.glGetUniformLocation(spriteProgram, "uCamRight"), 1f, 0f, 0f)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(spriteProgram, "uCamUp"), 0f, 1f, 0f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(spriteProgram, "uTime"), t)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(spriteProgram, "uFade"), fade)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(spriteProgram, "uTex"), 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        val stride = SPRITE_FLOATS * 4
        val aCenter = GLES20.glGetAttribLocation(spriteProgram, "aCenter")
        val aCorner = GLES20.glGetAttribLocation(spriteProgram, "aCorner")
        val aColor = GLES20.glGetAttribLocation(spriteProgram, "aColor")
        val aSize = GLES20.glGetAttribLocation(spriteProgram, "aSize")
        val aPhase = GLES20.glGetAttribLocation(spriteProgram, "aPhase")
        val aMode = GLES20.glGetAttribLocation(spriteProgram, "aMode")
        GLES20.glEnableVertexAttribArray(aCenter)
        GLES20.glVertexAttribPointer(aCenter, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(aCorner)
        GLES20.glVertexAttribPointer(aCorner, 2, GLES20.GL_FLOAT, false, stride, 12)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, stride, 20)
        GLES20.glEnableVertexAttribArray(aSize)
        GLES20.glVertexAttribPointer(aSize, 1, GLES20.GL_FLOAT, false, stride, 36)
        GLES20.glEnableVertexAttribArray(aPhase)
        GLES20.glVertexAttribPointer(aPhase, 1, GLES20.GL_FLOAT, false, stride, 40)
        GLES20.glEnableVertexAttribArray(aMode)
        GLES20.glVertexAttribPointer(aMode, 1, GLES20.GL_FLOAT, false, stride, 44)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        GLES20.glDisableVertexAttribArray(aCenter)
        GLES20.glDisableVertexAttribArray(aCorner)
        GLES20.glDisableVertexAttribArray(aColor)
        GLES20.glDisableVertexAttribArray(aSize)
        GLES20.glDisableVertexAttribArray(aPhase)
        GLES20.glDisableVertexAttribArray(aMode)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        if (!depthTest) GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    // ────────────────────────── 별(다이어리) 데이터 ──────────────────────────

    /**
     * 다이어리 → 구면 스프라이트 빌드(백그라운드 호출 OK — GL 미접근).
     * 좋아요 [FLARE_MIN_LIKES] 이상 → 별 플레어(구 표면 살짝 바깥), 미만 → 노란 작은 점광.
     * 동시에 별 근처 지형만 밝히는 라이트맵도 빌드.
     */
    fun setDiaries(diaries: List<Diary>) {
        val valid = diaries.filter { it.latitude != 0.0 && it.longitude != 0.0 }

        // 별 플레어 — 좋아요 100+ 다이어리만(레퍼런스의 큰 컬러 별)
        val popular = valid.filter { it.likeCount >= FLARE_MIN_LIKES }
            .sortedByDescending { it.likeCount }
            .take(FLARE_MAX)
        val flares = ArrayList<Float>(popular.size * 6 * SPRITE_FLOATS)
        for (d in popular) {
            val p = latLngToXyz(d.latitude, d.longitude, FLARE_RADIUS)
            val argb = StarStyle.colorOf(d.starColor).toArgb()
            val boost = min(d.likeCount, 1000).toFloat() / 1000f
            val size = 0.055f + 0.045f * boost
            val bright = 0.60f + 0.15f * boost // 이전(0.75~1.0)보다 감광
            addSprite(
                flares, p,
                r = ((argb shr 16) and 0xFF) / 255f * bright,
                g = ((argb shr 8) and 0xFF) / 255f * bright,
                b = (argb and 0xFF) / 255f * bright,
                a = 1f, size = size,
                phase = ((d.latitude * 7 + d.longitude * 13).mod(1.0)).toFloat(),
                mode = 0f,
            )
        }

        // 노란 작은 불빛 — 나머지 다이어리 1:1(도시 야경 점광, 상한 GLOW_MAX)
        val rest = valid.filter { it.likeCount < FLARE_MIN_LIKES }
        val glows = ArrayList<Float>(min(rest.size, GLOW_MAX) * 6 * SPRITE_FLOATS)
        for ((i, d) in rest.withIndex()) {
            if (i >= GLOW_MAX) break
            val p = latLngToXyz(d.latitude, d.longitude, GLOW_RADIUS)
            addSprite(
                glows, p,
                r = 1.0f * GLOW_ALPHA, g = 0.76f * GLOW_ALPHA, b = 0.36f * GLOW_ALPHA, a = 1f,
                size = 0.042f,
                phase = ((d.latitude * 3 + d.longitude * 5).mod(1.0)).toFloat(),
                mode = 0f,
            )
        }

        lightMapBmp = buildLightMap(valid)

        flareData = toFloatBuffer(flares)
        flareVertexCount = flares.size / SPRITE_FLOATS
        glowData = toFloatBuffer(glows)
        glowVertexCount = glows.size / SPRITE_FLOATS
        starsDirty = true
    }

    /**
     * 등장방형 라이트맵: 다이어리 위치마다 소프트 스플랫(좋아요 100+ 는 더 크고 밝게).
     * 지구 셰이더가 샘플해 "별 근처 지형만" 밝힌다. 날짜변경선(±180°) 이음매는 양쪽 중복 드로우.
     */
    private fun buildLightMap(diaries: List<Diary>): Bitmap {
        val w = LIGHT_W
        val h = LIGHT_H
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF000000.toInt())
        val c = Canvas(bmp)
        val splat = makeGlowBitmap()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for ((i, d) in diaries.withIndex()) {
            if (i >= LIGHT_MAX) break
            val big = d.likeCount >= FLARE_MIN_LIKES
            val r = if (big) 30f else 15f
            paint.alpha = if (big) 210 else 120
            val cx = ((d.longitude + 180.0) / 360.0 * w).toFloat()
            val cy = ((90.0 - d.latitude) / 180.0 * h).toFloat()
            fun drawAt(x: Float) = c.drawBitmap(splat, null, RectF(x - r, cy - r, x + r, cy + r), paint)
            drawAt(cx)
            if (cx < r) drawAt(cx + w)
            if (cx > w - r) drawAt(cx - w)
        }
        splat.recycle()
        return bmp
    }

    /** GL 스레드에서 setDiaries 결과 업로드. */
    private fun uploadStars() {
        starsDirty = false
        flareData?.let { uploadBuffer(flareVbo, it) }
        glowData?.let { uploadBuffer(glowVbo, it) }
        lightMapBmp?.let {
            if (lightMapTex != 0) GLES20.glDeleteTextures(1, intArrayOf(lightMapTex), 0)
            // 서피스 재생성 시 재업로드해야 하므로 비트맵은 recycle 하지 않고 유지
            lightMapTex = uploadTexture(it, wrapS = GLES20.GL_REPEAT, recycle = false)
        }
    }

    /** 배경 별밭 — 반지름 28 구면 랜덤(모델과 함께 회전 → 지구를 돌리면 우주도 함께 돈다). */
    private fun buildStarfield() {
        val rnd = java.util.Random(7L)
        val list = ArrayList<Float>(BG_STAR_COUNT * 6 * SPRITE_FLOATS)
        repeat(BG_STAR_COUNT) {
            // 균일 구면 분포
            val z = rnd.nextFloat() * 2f - 1f
            val ang = rnd.nextFloat() * 6.2832f
            val r = kotlin.math.sqrt(1f - z * z)
            val p = floatArrayOf(r * cos(ang) * 28f, z * 28f, r * sin(ang) * 28f)
            val warm = rnd.nextFloat()
            val bright = 0.18f + rnd.nextFloat() * 0.82f
            val big = rnd.nextFloat() // 제곱 분포 — 대부분 잔별, 소수만 크게
            addSprite(
                list, p,
                r = bright * (0.85f + 0.15f * warm),
                g = bright * (0.85f + 0.10f * warm),
                b = bright * (0.95f - 0.15f * warm),
                a = 1f,
                size = 0.035f + big * big * 0.16f,
                phase = rnd.nextFloat(),
                mode = 1f,
            )
        }
        val buf = toFloatBuffer(list)
        starfieldVbo = genBuffer()
        uploadBuffer(starfieldVbo, buf)
        starfieldVertexCount = list.size / SPRITE_FLOATS
    }

    /** 빌보드 사각형(삼각형 2개 = 6 정점)을 스프라이트 배열에 추가. */
    private fun addSprite(
        out: MutableList<Float>, p: FloatArray,
        r: Float, g: Float, b: Float, a: Float,
        size: Float, phase: Float, mode: Float,
    ) {
        val corners = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f)
        for (c in 0 until 6) {
            out.add(p[0]); out.add(p[1]); out.add(p[2])
            out.add(corners[c * 2]); out.add(corners[c * 2 + 1])
            out.add(r); out.add(g); out.add(b); out.add(a)
            out.add(size); out.add(phase); out.add(mode)
        }
    }

    // ────────────────────────── 메쉬/텍스처 빌드 ──────────────────────────

    /** 위경도 → 단위구 좌표(반지름 radius). 텍스처 UV 와 동일 규약(λ=0 이 +Z). */
    private fun latLngToXyz(lat: Double, lng: Double, radius: Float): FloatArray {
        val phi = Math.toRadians(lat)
        val lam = Math.toRadians(lng)
        return floatArrayOf(
            (cos(phi) * sin(lam) * radius).toFloat(),
            (sin(phi) * radius).toFloat(),
            (cos(phi) * cos(lam) * radius).toFloat(),
        )
    }

    private fun buildEarthMesh() {
        val stacks = 96
        val slices = 192
        val verts = FloatArray((stacks + 1) * (slices + 1) * 5)
        var vi = 0
        for (i in 0..stacks) {
            val v = i.toFloat() / stacks
            val phi = Math.toRadians(90.0 - 180.0 * v) // 북극 → 남극
            for (j in 0..slices) {
                val u = j.toFloat() / slices
                val lam = Math.toRadians(-180.0 + 360.0 * u)
                verts[vi++] = (cos(phi) * sin(lam)).toFloat()
                verts[vi++] = sin(phi).toFloat()
                verts[vi++] = (cos(phi) * cos(lam)).toFloat()
                verts[vi++] = u
                verts[vi++] = v
            }
        }
        val idx = ShortArray(stacks * slices * 6)
        var ii = 0
        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                val a = (i * (slices + 1) + j)
                val b = a + slices + 1
                idx[ii++] = a.toShort(); idx[ii++] = b.toShort(); idx[ii++] = (a + 1).toShort()
                idx[ii++] = (a + 1).toShort(); idx[ii++] = b.toShort(); idx[ii++] = (b + 1).toShort()
            }
        }
        earthIndexCount = idx.size

        earthVbo = genBuffer()
        val vb = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(verts).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, earthVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * 4, vb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        earthIbo = genBuffer()
        val ib: ShortBuffer = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(idx).position(0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, earthIbo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, idx.size * 2, ib, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    /** 트레일 세트 — 반지름/기울기/호 길이/색을 랜덤하게 섞은 자유 원호 여러 개. */
    private fun buildTrails() {
        trails.clear()
        val rnd = java.util.Random(11L)
        repeat(TRAIL_COUNT) { i ->
            val radius = 1.28f + rnd.nextFloat() * 0.50f
            val halfW = 0.016f + rnd.nextFloat() * 0.014f
            val tiltX = -38f + rnd.nextFloat() * 76f
            val tiltZ = -45f + rnd.nextFloat() * 90f
            val start = rnd.nextFloat() * 360f
            val sweep = 130f + rnd.nextFloat() * 150f
            val (vbo, count) = buildArc(radius, halfW, tiltX, tiltZ, start, sweep)
            val dir = if (rnd.nextBoolean()) 1f else -1f
            trails.add(
                Trail(
                    vbo, count,
                    colorA = TRAIL_COLORS[i % TRAIL_COLORS.size],
                    colorB = TRAIL_COLORS[(i + 2) % TRAIL_COLORS.size],
                    speed = dir * (0.05f + rnd.nextFloat() * 0.08f),
                )
            )
        }
    }

    /** 부분 원호 리본(TRIANGLE_STRIP), 지구 좌표계. 반환: (vbo, 정점수). */
    private fun buildArc(
        radius: Float, halfWidth: Float,
        tiltXDeg: Float, tiltZDeg: Float,
        startDeg: Float, sweepDeg: Float,
    ): Pair<Int, Int> {
        val segs = 192
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.rotateM(m, 0, tiltZDeg, 0f, 0f, 1f)
        Matrix.rotateM(m, 0, tiltXDeg, 1f, 0f, 0f)
        val list = ArrayList<Float>((segs + 1) * 2 * 5)
        val pin = FloatArray(4)
        val pout = FloatArray(4)
        for (s in 0..segs) {
            val u = s.toFloat() / segs
            val ang = Math.toRadians((startDeg + u * sweepDeg).toDouble()).toFloat()
            for (k in 0..1) {
                val r = radius + (if (k == 0) -halfWidth else halfWidth)
                pin[0] = cos(ang) * r; pin[1] = 0f; pin[2] = sin(ang) * r; pin[3] = 1f
                Matrix.multiplyMV(pout, 0, m, 0, pin, 0)
                list.add(pout[0]); list.add(pout[1]); list.add(pout[2])
                list.add(u); list.add(k.toFloat())
            }
        }
        val vbo = genBuffer()
        uploadBuffer(vbo, toFloatBuffer(list))
        return vbo to list.size / 5
    }

    /** NASA Blue Marble 텍스처 로드(assets). GL_MAX_TEXTURE_SIZE 미만이면 다운스케일. */
    private fun loadEarthTexture(): Int {
        val maxSize = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxSize, 0)
        var bmp = context.assets.open("earth_blue_marble.jpg").use { BitmapFactory.decodeStream(it) }
        if (bmp.width > maxSize[0]) {
            val w = maxSize[0].coerceAtLeast(1024)
            val scaled = Bitmap.createScaledBitmap(bmp, w, w / 2, true)
            bmp.recycle()
            bmp = scaled
        }
        val tex = uploadTexture(bmp, wrapS = GLES20.GL_REPEAT)
        return tex
    }

    private fun uploadTexture(bmp: Bitmap, wrapS: Int = GLES20.GL_CLAMP_TO_EDGE, recycle: Boolean = true): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrapS)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        if (recycle) bmp.recycle()
        return ids[0]
    }

    /** 4-포인트 별 플레어 텍스처(흰색 — 정점색으로 tint). */
    private fun makeFlareBitmap(): Bitmap {
        val s = 128
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = s / 2f
        // 중심 코어(글로우 감산 — 별이 너무 밝지 않게)
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cx, s * 0.26f,
                intArrayOf(0xFFFFFFFF.toInt(), 0x5CFFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.22f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(cx, cx, s * 0.26f, core)
        // 4방향 광선(수직/수평 길게 + 대각 짧게)
        fun ray(lenFrac: Float, thickFrac: Float, angleDeg: Float) {
            c.save()
            c.rotate(angleDeg, cx, cx)
            val half = s * lenFrac / 2f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    cx - half, cx, cx + half, cx,
                    intArrayOf(0x00FFFFFF, 0xB4FFFFFF.toInt(), 0x00FFFFFF),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
            }
            val t = s * thickFrac / 2f
            val path = android.graphics.Path().apply {
                moveTo(cx - half, cx)
                quadTo(cx, cx - t, cx + half, cx)
                quadTo(cx, cx + t, cx - half, cx)
                close()
            }
            c.drawPath(path, p)
            c.restore()
        }
        ray(0.98f, 0.09f, 0f)
        ray(0.98f, 0.09f, 90f)
        ray(0.52f, 0.055f, 45f)
        ray(0.52f, 0.055f, 135f)
        return bmp
    }

    /** 부드러운 원형 글로우(흰색 — tint 로 노란 불빛/배경 별에 공용). */
    private fun makeGlowBitmap(): Bitmap {
        val s = 64
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = s / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cx, cx,
                intArrayOf(0xFFFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(cx, cx, cx, p)
        return bmp
    }

    // ────────────────────────── GL 유틸 ──────────────────────────

    private fun genBuffer(): Int {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        return ids[0]
    }

    private fun uploadBuffer(vbo: Int, buf: FloatBuffer) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buf.capacity() * 4, buf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun toFloatBuffer(list: List<Float>): FloatBuffer {
        val buf = ByteBuffer.allocateDirect(list.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (f in list) buf.put(f)
        buf.position(0)
        return buf
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src)
            GLES20.glCompileShader(id)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) Log.w("GlobeRenderer", "shader compile: ${GLES20.glGetShaderInfoLog(id)}")
            return id
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) Log.w("GlobeRenderer", "program link: ${GLES20.glGetProgramInfoLog(p)}")
        return p
    }

    companion object {
        const val ENTER_DIST = 4.6f   // 진입 시작 거리(돌리-인 출발점)
        const val IDLE_DIST = 3.25f   // 기본 관람 거리
        const val MIN_DIST = 2.10f    // 이 밑으로 핀치-인 → 지도 복귀
        const val MAX_DIST = 6.0f

        private const val SPRITE_FLOATS = 12
        private const val FLARE_MIN_LIKES = 100 // 이 이상 좋아요 → 별 플레어, 미만 → 노란 점광
        private const val FLARE_MAX = 500
        private const val FLARE_RADIUS = 1.045f // 구 표면에서 살짝 띄워 렌더(박힘 방지)
        private const val GLOW_RADIUS = 1.008f
        private const val GLOW_ALPHA = 0.42f
        private const val GLOW_MAX = 5000
        private const val BG_STAR_COUNT = 1600
        private const val TRAIL_COUNT = 5
        /** 트레일 팔레트(레퍼런스풍) — 원호마다 A→B 두 색을 섞어 흐르게 한다. */
        private val TRAIL_COLORS = arrayOf(
            floatArrayOf(0.55f, 0.75f, 1.00f), // 청백
            floatArrayOf(1.00f, 0.62f, 0.42f), // 주황
            floatArrayOf(0.72f, 0.55f, 1.00f), // 보라
            floatArrayOf(0.45f, 1.00f, 0.80f), // 민트
            floatArrayOf(1.00f, 0.80f, 0.45f), // 금색
        )
        private const val LIGHT_W = 1024        // 라이트맵 해상도(등장방형, POT 필수 — REPEAT+밉맵)
        private const val LIGHT_H = 512
        private const val LIGHT_MAX = 4000      // 라이트맵 스플랫 상한

        // ── 셰이더 (ES 2.0) ──
        private const val EARTH_VS = """
            uniform mat4 uMVP; uniform mat4 uModel;
            attribute vec3 aPos; attribute vec2 aUV;
            varying vec2 vUV; varying vec3 vN; varying vec3 vW;
            void main() {
                vUV = aUV;
                vN = normalize((uModel * vec4(aPos, 0.0)).xyz);
                vW = (uModel * vec4(aPos, 1.0)).xyz;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val EARTH_FS = """
            precision mediump float;
            uniform sampler2D uTex; uniform sampler2D uLight; uniform vec3 uCamPos; uniform float uFade;
            varying vec2 vUV; varying vec3 vN; varying vec3 vW;
            void main() {
                vec3 V = normalize(uCamPos - vW);
                float ndv = clamp(dot(vN, V), 0.0, 1.0);
                vec3 tex = texture2D(uTex, vUV).rgb;
                float lm = texture2D(uLight, vUV).r;
                // 짙은 밤: 땅/바다는 훨씬 어둡게, 다이어리 별 근처만 라이트맵으로 밝힘(따뜻한 톤)
                vec3 night = tex * (0.030 + 0.075 * pow(ndv, 1.2)) * vec3(0.70, 0.80, 1.05);
                vec3 lit = tex * lm * 0.95 * vec3(1.10, 1.00, 0.82);
                gl_FragColor = vec4((night + lit) * uFade, 1.0);
            }
        """

        private const val SPRITE_VS = """
            uniform mat4 uVP; uniform mat4 uModel;
            uniform vec3 uCamPos; uniform vec3 uCamRight; uniform vec3 uCamUp;
            uniform float uTime;
            attribute vec3 aCenter; attribute vec2 aCorner; attribute vec4 aColor;
            attribute float aSize; attribute float aPhase; attribute float aMode;
            varying vec4 vColor; varying vec2 vUV;
            void main() {
                // 배경 별(mode=1)도 모델 회전 적용 — 지구를 돌리면 우주가 같이 돌아 시점 이동감
                vec3 wc = (uModel * vec4(aCenter, 1.0)).xyz;
                float tw = 0.82 + 0.28 * sin(uTime * (1.1 + aPhase * 2.3) + aPhase * 6.2831);
                float vis = 1.0;
                if (aMode < 0.5) {
                    vec3 n = normalize(wc);
                    vec3 toCam = normalize(uCamPos - wc);
                    vis = smoothstep(-0.02, 0.22, dot(n, toCam));
                }
                vColor = aColor * (tw * vis);
                vUV = aCorner * 0.5 + 0.5;
                vec3 pos = wc + (uCamRight * aCorner.x + uCamUp * aCorner.y) * aSize * (0.88 + 0.22 * tw);
                gl_Position = uVP * vec4(pos, 1.0);
            }
        """

        private const val SPRITE_FS = """
            precision mediump float;
            uniform sampler2D uTex; uniform float uFade;
            varying vec4 vColor; varying vec2 vUV;
            void main() {
                gl_FragColor = texture2D(uTex, vUV) * vColor * uFade;
            }
        """

        private const val RING_VS = """
            uniform mat4 uMVP;
            attribute vec3 aPos; attribute vec2 aUV;
            varying vec2 vUV;
            void main() { vUV = aUV; gl_Position = uMVP * vec4(aPos, 1.0); }
        """

        private const val RING_FS = """
            precision mediump float;
            uniform float uTime; uniform float uSpeed; uniform float uFade;
            uniform vec3 uColorA; uniform vec3 uColorB;
            varying vec2 vUV;
            void main() {
                float across = sin(vUV.y * 3.14159);          // 리본 폭 방향(0..1..0)
                float glow = pow(across, 1.8) * 0.14;         // 넓은 소프트 글로우(빛번짐)
                float core = pow(across, 9.0);                // 얇은 코어 라인
                float head = pow(0.5 + 0.5 * sin((vUV.x - uTime * uSpeed) * 6.2831), 4.0);
                // 링을 따라 빠르게 흐르는 좁은 백색 하이라이트 — 광택(글린트) 느낌
                float shine = pow(0.5 + 0.5 * sin((vUV.x * 3.0 + uTime * uSpeed * 5.0) * 6.2831), 24.0);
                vec3 col = mix(uColorA, uColorB, 0.5 + 0.5 * sin(vUV.x * 6.2831 + uTime * 0.3));
                vec3 c = col * (glow + core * (0.30 + 0.80 * head))
                       + vec3(1.0) * core * (shine * 0.85 + head * 0.30);
                gl_FragColor = vec4(c * uFade, 1.0);
            }
        """
    }
}
