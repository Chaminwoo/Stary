package com.chaminwoo.stary.feature.globe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
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
 *  1. 배경 별밭: 반지름이 다른 3겹 구면 셸 + 원경 성운 글로우 + 은하수 띠 +
 *     황도 12궁 별자리(레퍼런스 references/zodiac.avif, 궁별 고유색 + 희미한 연결선) + 4방 광선 반짝별 +
 *     우주 공간에 드리운 오로라 커튼(초록→보라 수직 그라데이션, 흐르는 주름) —
 *     카메라가 중심에서 떨어져 있어 회전/줌 시 셸마다 시차가 생겨 "진짜 3D 공간" 깊이감
 *  2. 지구 구체: 원본의 3/4 밝기 균일(라이트맵/지형 밝힘 없음)
 *  3. 궤적 트레일: 자유 원호 — 얇은 코어 라인 + 감싸는 아주 옅은 글로우, 훨씬 반투명.
 *     양 끝은 점점 투명해지며 소멸, 백색 빛무리(가우시안 펄스)가 궤적을 따라
 *     자연스럽게 흘러감. 지구 좌표계라 구와 함께 회전
 *  4. 노란 작은 불빛: 좋아요 [FLARE_MIN_LIKES] 미만 다이어리 1:1 — 인류의 도시 야경 점광
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
    private var lineProgram = 0
    private var constLineVbo = 0        // 별자리 연결선(GL_LINES)
    private var constLineVertexCount = 0
    private var bgFlareVbo = 0          // 배경 반짝별(4방 광선 텍스처)
    private var bgFlareVertexCount = 0

    /** 자유 원호 트레일(지구 좌표계 — 구와 함께 회전).
     *  phase: 트레일별 파동 위상(불규칙성), intensity: 트레일별 투명도 차등(1=기준). */
    private class Trail(
        val vbo: Int, val count: Int,
        val colorA: FloatArray, val colorB: FloatArray,
        val speed: Float, val phase: Float, val intensity: Float,
    )
    private val trails = ArrayList<Trail>()

    /** 오로라 커튼(우주 공간에 드리운 빛의 장막 — 배경 별밭처럼 모델과 함께 회전).
     *  botColor→topColor 수직 그라데이션, 주름(fold)은 셰이더가 uTime 으로 흘린다. */
    private class Aurora(
        val vbo: Int, val count: Int,
        val botColor: FloatArray, val topColor: FloatArray,
        val speed: Float, val phase: Float, val intensity: Float,
    )
    private val auroras = ArrayList<Aurora>()
    private var auroraProgram = 0

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
        lineProgram = buildProgram(LINE_VS, LINE_FS)
        auroraProgram = buildProgram(RING_VS, AURORA_FS)

        buildEarthMesh()
        earthTex = loadEarthTexture()
        flareTex = uploadTexture(makeFlareBitmap())
        glowTex = uploadTexture(makeGlowBitmap())
        buildStarfield()
        buildTrails()
        buildAuroras()
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
        // 1.5) 별자리 연결선 + 배경 반짝별(4방 광선) — 별밭과 같은 additive 층
        drawConstellationLines()
        if (bgFlareVertexCount > 0) {
            drawSprites(bgFlareVbo, bgFlareVertexCount, flareTex, camPos, t, depthTest = false)
        }
        // 1.7) 오로라 커튼 — 우주 공간에 드리운 빛의 장막(배경층 — 지구가 위에 그려져 가려짐)
        for (au in auroras) drawAurora(au, t)

        // 2) 지구 본체 (불투명, 깊이 기록)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        drawEarth()

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

    private fun drawEarth() {
        GLES20.glUseProgram(earthProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(earthProgram, "uMVP"), 1, false, mvp, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(earthProgram, "uFade"), fade)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, earthTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(earthProgram, "uTex"), 0)

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
        GLES20.glUniform1f(GLES20.glGetUniformLocation(ringProgram, "uPhase"), tr.phase)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(ringProgram, "uIntensity"), tr.intensity)
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

    /** 오로라 커튼 — 트레일과 같은 리본 정점 포맷(pos3+uv2), 전용 셰이더로 주름을 흘린다. */
    private fun drawAurora(au: Aurora, t: Float) {
        if (au.vbo == 0 || au.count == 0) return
        GLES20.glUseProgram(auroraProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(auroraProgram, "uMVP"), 1, false, mvp, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(auroraProgram, "uTime"), t)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(auroraProgram, "uSpeed"), au.speed)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(auroraProgram, "uPhase"), au.phase)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(auroraProgram, "uIntensity"), au.intensity)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(auroraProgram, "uFade"), fade)
        GLES20.glUniform3fv(GLES20.glGetUniformLocation(auroraProgram, "uBotColor"), 1, au.botColor, 0)
        GLES20.glUniform3fv(GLES20.glGetUniformLocation(auroraProgram, "uTopColor"), 1, au.topColor, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, au.vbo)
        val aPos = GLES20.glGetAttribLocation(auroraProgram, "aPos")
        val aUV = GLES20.glGetAttribLocation(auroraProgram, "aUV")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 20, 0)
        GLES20.glEnableVertexAttribArray(aUV)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 20, 12)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, au.count)
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
            // 레퍼런스풍 팔레트(빨강/파랑/분홍/노랑…) — 좌표 기반 결정적 선택으로 별마다 색이 갈린다
            val argb = FLARE_COLORS[flareColorIndex(d)]
            val boost = min(d.likeCount, 1000).toFloat() / 1000f
            val size = 0.034f + 0.026f * boost // 한 단계 더 축소(이전 0.040+0.032)
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

        // 노란 작은 불빛 — 나머지 다이어리 1:1(인류의 도시 야경 점광, 상한 GLOW_MAX)
        val rest = valid.filter { it.likeCount < FLARE_MIN_LIKES }
        val glows = ArrayList<Float>(min(rest.size, GLOW_MAX) * 6 * SPRITE_FLOATS)
        for ((i, d) in rest.withIndex()) {
            if (i >= GLOW_MAX) break
            val p = latLngToXyz(d.latitude, d.longitude, GLOW_RADIUS)
            addSprite(
                glows, p,
                r = 1.0f * GLOW_ALPHA, g = 0.76f * GLOW_ALPHA, b = 0.36f * GLOW_ALPHA, a = 1f,
                size = 0.030f, // 밝아진 지구 위 은은한 소형 점광
                phase = ((d.latitude * 3 + d.longitude * 5).mod(1.0)).toFloat(),
                mode = 0f,
            )
        }

        flareData = toFloatBuffer(flares)
        flareVertexCount = flares.size / SPRITE_FLOATS
        glowData = toFloatBuffer(glows)
        glowVertexCount = glows.size / SPRITE_FLOATS
        starsDirty = true
    }

    /** 좌표 기반 결정적 팔레트 인덱스 — 같은 다이어리는 항상 같은 색. */
    private fun flareColorIndex(d: Diary): Int =
        (d.latitude * 7919.0 + d.longitude * 104729.0).mod(FLARE_COLORS.size.toDouble()).toInt()

    /** GL 스레드에서 setDiaries 결과 업로드. */
    private fun uploadStars() {
        starsDirty = false
        flareData?.let { uploadBuffer(flareVbo, it) }
        glowData?.let { uploadBuffer(glowVbo, it) }
    }

    /**
     * 배경 별밭 — 반지름이 다른 3겹 구면 셸 + 원경 성운 글로우.
     * 모델과 함께 회전하지만 카메라가 중심에서 떨어져 있어(줌 거리) 회전/줌 시
     * 셸마다 화면 이동량(시차)이 달라 겹겹이 쌓인 "진짜 3D 공간"처럼 느껴진다.
     * 가까운 셸일수록 밝고 또렷, 먼 셸일수록 잘고 어둡고 촘촘하게.
     */
    private fun buildStarfield() {
        val rnd = java.util.Random(7L)
        val list = ArrayList<Float>()
        fun randomOnSphere(radius: Float): FloatArray {
            val z = rnd.nextFloat() * 2f - 1f
            val ang = rnd.nextFloat() * 6.2832f
            val r = kotlin.math.sqrt(1f - z * z)
            return floatArrayOf(r * cos(ang) * radius, z * radius, r * sin(ang) * radius)
        }
        fun addShell(radius: Float, count: Int, sizeBase: Float, sizeVar: Float, brightMul: Float) {
            repeat(count) {
                val p = randomOnSphere(radius)
                val warm = rnd.nextFloat()
                val bright = (0.15f + rnd.nextFloat() * 0.68f) * brightMul
                val big = rnd.nextFloat() // 제곱 분포 — 대부분 잔별, 소수만 크게
                addSprite(
                    list, p,
                    r = bright * (0.85f + 0.15f * warm),
                    g = bright * (0.85f + 0.10f * warm),
                    b = bright * (0.95f - 0.15f * warm),
                    a = 1f,
                    size = sizeBase + big * big * sizeVar,
                    phase = rnd.nextFloat(),
                    mode = 1f,
                )
            }
        }
        addShell(radius = 12f, count = 320, sizeBase = 0.018f, sizeVar = 0.062f, brightMul = 1.00f) // 근경
        addShell(radius = 22f, count = 620, sizeBase = 0.028f, sizeVar = 0.100f, brightMul = 0.72f) // 중경
        addShell(radius = 38f, count = 900, sizeBase = 0.042f, sizeVar = 0.140f, brightMul = 0.52f) // 원경
        // 원경 너머 아주 어두운 성운 글로우 — 배경에 색 온도와 깊이(도형이 아니라 '공간'으로 읽히게)
        val nebulaColors = arrayOf(
            floatArrayOf(0.055f, 0.030f, 0.100f), // 보라
            floatArrayOf(0.040f, 0.050f, 0.110f), // 청보라
            floatArrayOf(0.070f, 0.030f, 0.080f), // 자주
            floatArrayOf(0.030f, 0.050f, 0.100f), // 청록빛
            floatArrayOf(0.060f, 0.040f, 0.110f), // 연보라
            floatArrayOf(0.050f, 0.020f, 0.090f), // 짙은 보라
        )
        for (c in nebulaColors) {
            val p = randomOnSphere(41f)
            addSprite(
                list, p,
                r = c[0], g = c[1], b = c[2], a = 1f,
                size = 6.5f + rnd.nextFloat() * 4.0f,
                phase = rnd.nextFloat(),
                mode = 1f,
            )
        }

        // ── 은하수 띠 — 기울어진 대원을 따라 잔별을 뿌리고 어두운 헤이즈로 감싼다 ──
        run {
            val m = FloatArray(16)
            Matrix.setIdentityM(m, 0)
            Matrix.rotateM(m, 0, 28f, 0f, 0f, 1f)
            Matrix.rotateM(m, 0, 62f, 1f, 0f, 0f)
            val pin = FloatArray(4)
            val pout = FloatArray(4)
            fun bandPoint(radius: Float, spreadRad: Float, ang: Float): FloatArray {
                pin[0] = cos(ang) * cos(spreadRad); pin[1] = sin(spreadRad)
                pin[2] = sin(ang) * cos(spreadRad); pin[3] = 0f
                Matrix.multiplyMV(pout, 0, m, 0, pin, 0)
                return floatArrayOf(pout[0] * radius, pout[1] * radius, pout[2] * radius)
            }
            repeat(560) {
                val ang = rnd.nextFloat() * 6.2832f
                val spread = (rnd.nextGaussian() * 0.055).toFloat() // 띠 두께 ±3° 남짓
                val p = bandPoint(39f, spread, ang)
                val br = 0.06f + rnd.nextFloat() * 0.22f
                val warm = rnd.nextFloat()
                addSprite(
                    list, p,
                    r = br * (0.88f + 0.12f * warm), g = br * 0.90f, b = br * (1.00f - 0.10f * warm),
                    a = 1f,
                    size = 0.030f + rnd.nextFloat() * 0.075f,
                    phase = rnd.nextFloat(), mode = 1f,
                )
            }
            repeat(10) { // 띠를 감싸는 아주 어두운 대형 헤이즈
                val ang = rnd.nextFloat() * 6.2832f
                val p = bandPoint(40f, (rnd.nextGaussian() * 0.03).toFloat(), ang)
                addSprite(
                    list, p,
                    r = 0.020f, g = 0.024f, b = 0.034f, a = 1f,
                    size = 2.6f + rnd.nextFloat() * 2.2f,
                    phase = rnd.nextFloat(), mode = 1f,
                )
            }
        }

        // ── 별자리 — 황도 12궁(레퍼런스 references/zodiac.avif), 궁마다 고유색 ──
        // 디자인(밝은 별 + 희미한 연결선)은 기존 그대로, 색만 궁별로 다르게. 라인 VBO = pos3+rgb3.
        val constLines = ArrayList<Float>()
        fun addConstellation(
            centerLat: Double, centerLng: Double, scaleDeg: Float, rollDeg: Float,
            tint: FloatArray, points: Array<FloatArray>, segments: Array<IntArray>,
        ) {
            val radius = 36f
            val c = latLngToXyz(centerLat, centerLng, 1f)
            val east = norm3(cross3(floatArrayOf(0f, 1f, 0f), c)) // 접평면 기저
            val north = norm3(cross3(c, east))
            val s = Math.toRadians(scaleDeg.toDouble()).toFloat()
            val roll = Math.toRadians(rollDeg.toDouble()).toFloat()
            val cosR = cos(roll)
            val sinR = sin(roll)
            val pts = points.map { p ->
                val x = (p[0] * cosR - p[1] * sinR) * s
                val y = (p[0] * sinR + p[1] * cosR) * s
                val d = norm3(floatArrayOf(
                    c[0] + east[0] * x + north[0] * y,
                    c[1] + east[1] * x + north[1] * y,
                    c[2] + east[2] * x + north[2] * y,
                ))
                floatArrayOf(d[0] * radius, d[1] * radius, d[2] * radius)
            }
            for (p in pts) {
                val br = 0.55f + rnd.nextFloat() * 0.25f // 배경보다 또렷한 밝기 + 궁별 틴트
                addSprite(
                    list, p,
                    r = br * (0.45f + 0.55f * tint[0]),
                    g = br * (0.45f + 0.55f * tint[1]),
                    b = br * (0.45f + 0.55f * tint[2]),
                    a = 1f,
                    size = 0.20f + rnd.nextFloat() * 0.08f,
                    phase = rnd.nextFloat(), mode = 1f,
                )
            }
            for (seg in segments) {
                for (i in intArrayOf(seg[0], seg[1])) {
                    constLines.addAll(pts[i].toList())
                    constLines.add(tint[0] * 0.30f); constLines.add(tint[1] * 0.30f); constLines.add(tint[2] * 0.30f)
                }
            }
        }
        fun pts(vararg p: Float): Array<FloatArray> =
            Array(p.size / 2) { floatArrayOf(p[it * 2], p[it * 2 + 1]) }
        fun segs(vararg s: Int): Array<IntArray> =
            Array(s.size / 2) { intArrayOf(s[it * 2], s[it * 2 + 1]) }
        // 12궁 — 경도 30°씩 + 위도 4단 사이클로 하늘 전체에 골고루 분산. 궁마다 고유색.
        // 양자리 — 코랄 레드
        addConstellation(52.0, -165.0, 4.5f, -8f, floatArrayOf(1.00f, 0.52f, 0.42f),
            pts(0f, 0f, 0.9f, 0.3f, 1.6f, 0.35f, 1.9f, 0.05f),
            segs(0, 1, 1, 2, 2, 3))
        // 황소자리 — 연두 (V 자 히아데스 + 두 뿔)
        addConstellation(18.0, -135.0, 4.8f, 10f, floatArrayOf(0.62f, 0.95f, 0.55f),
            pts(0f, 0f, 0.6f, 0.5f, 1.6f, 0.9f, 0.55f, -0.35f, 1.5f, -0.75f),
            segs(0, 1, 1, 2, 0, 3, 3, 4))
        // 쌍둥이자리 — 옐로 (나란한 두 줄기 + 어깨 연결)
        addConstellation(-18.0, -105.0, 4.6f, -14f, floatArrayOf(1.00f, 0.88f, 0.45f),
            pts(0f, 1.0f, 0.55f, 0.95f, 0.05f, 0.4f, 0.6f, 0.35f, 0f, -0.35f, 0.65f, -0.4f),
            segs(0, 2, 2, 4, 1, 3, 3, 5, 2, 3))
        // 게자리 — 은청 (희미한 Y)
        addConstellation(-52.0, -75.0, 4.2f, 6f, floatArrayOf(0.75f, 0.85f, 1.00f),
            pts(0f, 0.65f, 0.35f, 0.15f, 0.05f, -0.55f, 0.85f, 0.3f),
            segs(0, 1, 1, 2, 1, 3))
        // 사자자리 — 골드 (낫 + 몸통)
        addConstellation(52.0, -45.0, 4.6f, 0f, floatArrayOf(1.00f, 0.72f, 0.30f),
            pts(0f, 0f, 0.15f, 0.55f, 0.5f, 0.9f, 1.0f, 0.9f, 1.25f, 0.55f,
                -1.2f, 0.35f, -0.7f, 0.62f, -0.55f, 0.05f),
            segs(0, 1, 1, 2, 2, 3, 3, 4, 0, 7, 7, 5, 5, 6, 6, 1))
        // 처녀자리 — 민트 (스피카에서 뻗는 가지)
        addConstellation(18.0, -15.0, 4.8f, 12f, floatArrayOf(0.55f, 1.00f, 0.80f),
            pts(0f, -0.95f, 0.15f, -0.2f, -0.4f, 0.3f, 0.5f, 0.35f, -0.9f, 0.55f, 0.95f, 0.8f, 0.15f, 0.9f),
            segs(0, 1, 1, 2, 1, 3, 2, 4, 3, 5, 2, 6))
        // 천칭자리 — 핑크 (삼각 접시 + 두 다리)
        addConstellation(-18.0, 15.0, 4.4f, -6f, floatArrayOf(1.00f, 0.62f, 0.82f),
            pts(0f, 0.7f, -0.65f, 0.2f, 0.6f, 0.25f, -0.5f, -0.6f, 0.55f, -0.65f),
            segs(0, 1, 0, 2, 1, 2, 1, 3, 2, 4))
        // 전갈자리 — 크림슨 (집게 + 갈고리 꼬리)
        addConstellation(-52.0, 45.0, 4.8f, 8f, floatArrayOf(1.00f, 0.42f, 0.48f),
            pts(1.35f, 0.85f, 1.2f, 0.55f, 1.35f, 0.3f, 0.95f, 0.5f, 0.6f, 0.3f, 0.3f, 0f,
                0.15f, -0.45f, 0.25f, -0.85f, 0.55f, -1.1f, 0.9f, -1.05f, 1.05f, -0.85f),
            segs(0, 3, 1, 3, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10))
        // 사수자리 — 퍼플 (주전자 Teapot)
        addConstellation(52.0, 75.0, 4.4f, -10f, floatArrayOf(0.72f, 0.55f, 1.00f),
            pts(0f, 0.05f, 0.3f, 0.3f, 0.65f, 0.55f, 1.0f, 0.3f, 1.3f, 0f, 1.0f, -0.35f, 0.3f, -0.35f),
            segs(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 0, 1, 6, 3, 5))
        // 염소자리 — 틸 (아래로 처진 보울)
        addConstellation(18.0, 105.0, 4.6f, 4f, floatArrayOf(0.45f, 0.88f, 0.92f),
            pts(-1.0f, 0.5f, -0.45f, 0.1f, 0.15f, -0.2f, 0.75f, -0.05f, 1.05f, 0.45f),
            segs(0, 1, 1, 2, 2, 3, 3, 4, 4, 0))
        // 물병자리 — 블루 (물항아리 Y + 흘러내리는 물줄기)
        addConstellation(-18.0, 135.0, 4.6f, -4f, floatArrayOf(0.50f, 0.72f, 1.00f),
            pts(0f, 0.8f, 0.35f, 0.95f, 0.65f, 0.75f, 0.95f, 0.92f, 0.3f, 0.3f,
                -0.25f, 0.1f, 0.5f, -0.3f, 0.15f, -0.75f),
            segs(0, 1, 1, 2, 2, 3, 1, 4, 4, 5, 4, 6, 6, 7))
        // 물고기자리 — 라벤더 (두 끈이 만나는 V)
        addConstellation(-52.0, 165.0, 4.8f, 14f, floatArrayOf(0.82f, 0.70f, 1.00f),
            pts(1.35f, 0.95f, 0.95f, 0.6f, 0.5f, 0.3f, 0f, 0f, 0.5f, -0.18f, 1.05f, -0.28f, 1.55f, -0.2f),
            segs(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6))
        constLineVbo = genBuffer()
        uploadBuffer(constLineVbo, toFloatBuffer(constLines))
        constLineVertexCount = constLines.size / 6

        // ── 배경 반짝별 — 4방 광선 텍스처의 특별한 별 몇 개(은은한 포인트) ──
        val bgFlares = ArrayList<Float>()
        repeat(9) {
            val p = randomOnSphere(36f)
            val br = 0.16f + rnd.nextFloat() * 0.16f
            val warm = rnd.nextFloat()
            addSprite(
                bgFlares, p,
                r = br * (0.90f + 0.10f * warm), g = br * 0.93f, b = br * (1.05f - 0.15f * warm),
                a = 1f,
                size = 0.30f + rnd.nextFloat() * 0.28f,
                phase = rnd.nextFloat(), mode = 1f,
            )
        }
        bgFlareVbo = genBuffer()
        uploadBuffer(bgFlareVbo, toFloatBuffer(bgFlares))
        bgFlareVertexCount = bgFlares.size / SPRITE_FLOATS

        val buf = toFloatBuffer(list)
        starfieldVbo = genBuffer()
        uploadBuffer(starfieldVbo, buf)
        starfieldVertexCount = list.size / SPRITE_FLOATS
    }

    /** 별자리 연결선 — 아주 희미한 궁별 색 라인(additive, 별밭 층에서 호출). 정점 = pos3+rgb3. */
    private fun drawConstellationLines() {
        if (constLineVbo == 0 || constLineVertexCount == 0) return
        GLES20.glUseProgram(lineProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(lineProgram, "uMVP"), 1, false, mvp, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(lineProgram, "uFade"), fade)
        GLES20.glLineWidth(2f)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, constLineVbo)
        val aPos = GLES20.glGetAttribLocation(lineProgram, "aPos")
        val aColor = GLES20.glGetAttribLocation(lineProgram, "aColor")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, 0)
        GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, 24, 12)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, constLineVertexCount)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aColor)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glLineWidth(1f)
    }

    private fun cross3(a: FloatArray, b: FloatArray) = floatArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    private fun norm3(v: FloatArray): FloatArray {
        val len = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f)
        return floatArrayOf(v[0] / len, v[1] / len, v[2] / len)
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

    /** 트레일 세트 — 반지름/기울기/호 길이/색/위상을 랜덤하게 섞은 자유 원호 여러 개.
     *  전부 행성에서 여유 있게 떨어진 궤도(근접 궤도는 시각적으로 난잡해 롤백). */
    private fun buildTrails() {
        trails.clear()
        val rnd = java.util.Random(11L)
        repeat(TRAIL_COUNT) { i ->
            val radius = 1.28f + rnd.nextFloat() * 0.50f
            val halfW = 0.030f + rnd.nextFloat() * 0.020f // 얇은 선 + 감싸는 글로우 폭
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
                    speed = dir * (0.030f + rnd.nextFloat() * 0.040f), // 느긋하지만 흐름이 느껴지는 속도
                    phase = rnd.nextFloat() * 6.2832f,
                    // 앞 2개만 기준 세기, 나머지는 훨씬 옅게 — 궤적이 많아 보이지 않게 위계를 준다
                    intensity = if (i < 2) 1f else 0.35f + rnd.nextFloat() * 0.25f,
                )
            )
        }
    }

    /** 오로라 커튼 세트 — 우주 공간(배경 별밭 셸 사이)에 드리운 빛의 장막 4폭.
     *  극지 오벌이 아니라 하늘에 떠 있는 자유 커튼: 중심 방향의 접평면(별자리 배치와 같은
     *  east/north 기저)을 따라 밑단이 완만한 S 라인으로 물결치고 위로 솟아 소멸한다.
     *  v=0(아래 가장자리, 초록 또렷) → v=1(위, 보라/핑크로 소멸). 주름 애니메이션은 셰이더 담당.
     *  반지름을 폭마다 달리해 회전/줌 시 별밭 셸처럼 시차(깊이감)가 난다. */
    private fun buildAuroras() {
        auroras.clear()
        val rnd = java.util.Random(23L)
        fun addCurtain(
            centerLat: Double, centerLng: Double, radius: Float,
            lengthRad: Float, heightRad: Float, rollDeg: Float,
            botColor: FloatArray, topColor: FloatArray,
            speed: Float, intensity: Float,
        ) {
            val segs = 160
            val p1 = rnd.nextFloat() * 6.2832f
            val p2 = rnd.nextFloat() * 6.2832f
            val p3 = rnd.nextFloat() * 6.2832f
            val c = latLngToXyz(centerLat, centerLng, 1f)
            val east = norm3(cross3(floatArrayOf(0f, 1f, 0f), c))
            val north = norm3(cross3(c, east))
            val roll = Math.toRadians(rollDeg.toDouble()).toFloat()
            val cosR = cos(roll)
            val sinR = sin(roll)
            fun dirAt(x0: Float, y0: Float): FloatArray {
                val x = x0 * cosR - y0 * sinR
                val y = x0 * sinR + y0 * cosR
                return norm3(floatArrayOf(
                    c[0] + east[0] * x + north[0] * y,
                    c[1] + east[1] * x + north[1] * y,
                    c[2] + east[2] * x + north[2] * y,
                ))
            }
            val list = ArrayList<Float>((segs + 1) * 2 * 5)
            for (s in 0..segs) {
                val u = s.toFloat() / segs
                val x = (u - 0.5f) * lengthRad
                // 커튼 밑단의 완만한 물결(S 라인) — 자로 잰 듯한 직선이 되지 않게
                val yWave = 0.10f * sin(u * 6.2832f * 1.5f + p1) + 0.045f * sin(u * 6.2832f * 3.7f + p2)
                val h = heightRad * (0.82f + 0.18f * sin(u * 6.2832f * 2f + p3))
                val bot = dirAt(x, yWave)
                val top = dirAt(x, yWave + h)
                list.add(bot[0] * radius); list.add(bot[1] * radius); list.add(bot[2] * radius)
                list.add(u); list.add(0f)
                list.add(top[0] * radius); list.add(top[1] * radius); list.add(top[2] * radius)
                list.add(u); list.add(1f)
            }
            val vbo = genBuffer()
            uploadBuffer(vbo, toFloatBuffer(list))
            auroras.add(
                Aurora(
                    vbo, list.size / 5, botColor, topColor,
                    speed = speed, phase = rnd.nextFloat() * 6.2832f, intensity = intensity,
                )
            )
        }
        // 하늘 곳곳에 4폭 — 반지름(시차)/기울기/길이/색이 모두 달라 서로 다른 장막으로 읽힌다
        addCurtain(38.0, -60.0, 26f, 1.9f, 0.42f, -16f, AURORA_GREEN, AURORA_VIOLET, 0.9f, 0.95f)
        addCurtain(-24.0, 30.0, 34f, 2.4f, 0.55f, 12f, AURORA_TEAL, AURORA_INDIGO, -0.7f, 0.60f)
        addCurtain(8.0, 150.0, 30f, 2.1f, 0.48f, -7f, AURORA_GREEN, AURORA_PINK, 0.75f, 0.80f)
        addCurtain(-48.0, -150.0, 38f, 2.6f, 0.60f, 18f, AURORA_TEAL, AURORA_VIOLET, 0.5f, 0.45f)
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
        const val MIN_DIST = 1.45f    // 카메라 최소 거리(더 바짝 당겨보기 — 화면 전환 없음)
        const val MAX_DIST = 9.5f     // 카메라 최대 거리(지구가 작아 보일 만큼 멀리)

        private const val SPRITE_FLOATS = 12
        private const val FLARE_MIN_LIKES = 100 // 이 이상 좋아요 → 별 플레어, 미만 → 노란 점광
        private const val FLARE_MAX = 500
        private const val FLARE_RADIUS = 1.045f // 구 표면에서 살짝 띄워 렌더(박힘 방지)
        private const val GLOW_RADIUS = 1.008f
        private const val GLOW_ALPHA = 0.42f
        private const val GLOW_MAX = 5000
        private const val EARTH_BRIGHTNESS = 0.45f // 원본 대비 지구 밝기(균일)
        private const val TRAIL_COUNT = 5

        /** 오로라 팔레트 — 아래(초록/청록)에서 위(보라/핑크/남보라)로 녹아드는 실제 오로라 색. */
        private val AURORA_GREEN = floatArrayOf(0.18f, 0.95f, 0.52f)
        private val AURORA_TEAL = floatArrayOf(0.20f, 0.80f, 0.78f)
        private val AURORA_VIOLET = floatArrayOf(0.58f, 0.32f, 0.95f)
        private val AURORA_PINK = floatArrayOf(0.90f, 0.38f, 0.78f)
        private val AURORA_INDIGO = floatArrayOf(0.35f, 0.40f, 0.98f)

        /** 별 플레어 팔레트(레퍼런스풍) — 빨강/파랑/분홍/노랑/민트/보라/백색. */
        private val FLARE_COLORS = intArrayOf(
            0xFFFF6257.toInt(), // 빨강
            0xFF6D9EFF.toInt(), // 파랑
            0xFFFF8BD8.toInt(), // 분홍
            0xFFFFD966.toInt(), // 노랑
            0xFF8FF7E2.toInt(), // 민트
            0xFFC49BFF.toInt(), // 보라
            0xFFFFFFFF.toInt(), // 백색
        )

        /** 트레일 팔레트(레퍼런스풍) — 원호마다 A→B 두 색을 섞어 흐르게 한다. */
        private val TRAIL_COLORS = arrayOf(
            floatArrayOf(0.55f, 0.75f, 1.00f), // 청백
            floatArrayOf(1.00f, 0.62f, 0.42f), // 주황
            floatArrayOf(0.72f, 0.55f, 1.00f), // 보라
            floatArrayOf(0.45f, 1.00f, 0.80f), // 민트
            floatArrayOf(1.00f, 0.80f, 0.45f), // 금색
        )
        // ── 셰이더 (ES 2.0) ──
        private const val EARTH_VS = """
            uniform mat4 uMVP;
            attribute vec3 aPos; attribute vec2 aUV;
            varying vec2 vUV;
            void main() { vUV = aUV; gl_Position = uMVP * vec4(aPos, 1.0); }
        """

        private const val EARTH_FS = """
            precision mediump float;
            uniform sampler2D uTex; uniform float uFade;
            varying vec2 vUV;
            void main() {
                // 원본의 3/4 밝기 균일(별 근처 지형 밝힘 없음)
                gl_FragColor = vec4(texture2D(uTex, vUV).rgb * $EARTH_BRIGHTNESS * uFade, 1.0);
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

        private const val LINE_VS = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            attribute vec3 aColor;
            varying vec3 vColor;
            void main() { vColor = aColor; gl_Position = uMVP * vec4(aPos, 1.0); }
        """

        private const val LINE_FS = """
            precision mediump float;
            uniform float uFade;
            varying vec3 vColor;
            void main() { gl_FragColor = vec4(vColor * uFade, 1.0); }
        """

        private const val RING_VS = """
            uniform mat4 uMVP;
            attribute vec3 aPos; attribute vec2 aUV;
            varying vec2 vUV;
            void main() { vUV = aUV; gl_Position = uMVP * vec4(aPos, 1.0); }
        """

        private const val RING_FS = """
            precision mediump float;
            uniform float uTime; uniform float uSpeed; uniform float uFade; uniform float uPhase;
            uniform float uIntensity;
            uniform vec3 uColorA; uniform vec3 uColorB;
            varying vec2 vUV;
            void main() {
                float across = sin(vUV.y * 3.14159);           // 리본 폭 방향(0..1..0)
                float glow = pow(across, 2.0) * 0.07;          // 선을 감싸는 아주 옅은 글로우
                float core = pow(across, 14.0);                // 레퍼런스풍 얇은 코어 라인
                // 양 끝은 점점 투명해지며 소멸(확 끊기지 않게 긴 램프)
                float ends = smoothstep(0.0, 0.20, vUV.x) * smoothstep(1.0, 0.80, vUV.x);
                // 바탕 밝기 — 저주파 파동 2개로 은근히 숨쉬는 정도만
                float t = uTime * uSpeed;
                float w1 = 0.5 + 0.5 * sin((vUV.x - t) * 6.2831 + uPhase);
                float w2 = 0.5 + 0.5 * sin((vUV.x * 2.7 + t * 0.7) * 6.2831 + uPhase * 2.3);
                float flow = 0.45 + 0.55 * (0.6 * w1 + 0.4 * w2);
                // 백색 빛무리(가우시안 펄스)가 궤적을 따라 자연스럽게 흘러간다 — 주/부 2개
                float head = fract(t * 2.2 + uPhase * 0.159);
                float d1 = vUV.x - head;
                float d2 = vUV.x - fract(head + 0.47);
                float pulse = exp(-d1 * d1 * 220.0) + 0.45 * exp(-d2 * d2 * 300.0);
                vec3 col = mix(uColorA, uColorB, 0.5 + 0.5 * sin(vUV.x * 6.2831 + uTime * 0.15 + uPhase));
                // 훨씬 반투명 — 트레일은 배경에 스치는 빛줄기 정도로만
                vec3 c = col * (glow * flow + core * (0.10 + 0.10 * flow))
                       + vec3(1.0) * core * pulse * 0.30;
                gl_FragColor = vec4(c * ends * uFade * uIntensity, 1.0);
            }
        """

        /** 오로라 커튼 셰이더 — u: 커튼 길이(0..1), v: 높이(0=아래 가장자리, 1=꼭대기).
         *  아래 가장자리는 또렷하고 위로 갈수록 소멸, 양 끝은 점점 투명해지며 소멸.
         *  주름(fold) 파동 3개가 서로 다른 속도로 흐르며 간섭 + 커튼 전체를 훑는
         *  저주파 파동으로 밝기가 숨쉬듯 살아있는 느낌. */
        private const val AURORA_FS = """
            precision mediump float;
            uniform float uTime; uniform float uSpeed; uniform float uFade; uniform float uPhase;
            uniform float uIntensity;
            uniform vec3 uBotColor; uniform vec3 uTopColor;
            varying vec2 vUV;
            void main() {
                float u = vUV.x;
                float v = vUV.y;
                float t = uTime * uSpeed;
                // 커튼 주름 — 주파수 다른 파동 3개가 흐르며 간섭
                float f1 = 0.5 + 0.5 * sin(u * 6.2831 * 5.0 + t * 1.00 + uPhase);
                float f2 = 0.5 + 0.5 * sin(u * 6.2831 * 12.0 - t * 1.70 + uPhase * 2.7);
                float f3 = 0.5 + 0.5 * sin(u * 6.2831 * 2.5 + t * 0.45 + uPhase * 1.3);
                float folds = 0.22 + 0.78 * (0.45 * f1 + 0.35 * f2 + 0.20 * f3);
                // 커튼 전체를 훑는 저주파 밝기 파동 — 장막이 숨쉬듯
                float sweep = 0.45 + 0.55 * (0.5 + 0.5 * sin(u * 6.2831 * 0.8 + t * 0.12 + uPhase * 0.7));
                // 수직 프로파일 — 아래 가장자리 또렷, 위로 갈수록 부드럽게 소멸
                float base = smoothstep(0.0, 0.10, v);
                float fadeUp = pow(1.0 - v, 1.6);
                // 양 끝은 점점 투명해지며 소멸(열린 커튼 — 확 끊기지 않게 긴 램프)
                float ends = smoothstep(0.0, 0.14, u) * smoothstep(1.0, 0.86, u);
                vec3 col = mix(uBotColor, uTopColor, smoothstep(0.05, 0.90, v));
                float a = base * fadeUp * ends * folds * sweep * uIntensity;
                gl_FragColor = vec4(col * a * uFade, 1.0);
            }
        """
    }
}
