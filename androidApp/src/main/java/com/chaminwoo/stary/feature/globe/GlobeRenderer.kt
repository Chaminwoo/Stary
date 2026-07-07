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
 *  1. 배경 별밭: 반지름이 다른 3겹 구면 셸 + 원경 성운 글로우 +
 *     뚜렷한 은하수(잔별 밀집 띠 + 끊김 없는 헤이즈 리본 + 은하핵 벌지) +
 *     황도 12궁 별자리(레퍼런스 references/zodiac.avif, 궁별 고유색 + 희미한 연결선) + 4방 광선 반짝별 —
 *     카메라가 중심에서 떨어져 있어 회전/줌 시 셸마다 시차가 생겨 "진짜 3D 공간" 깊이감
 *  1.5. 유성: 입장 후 30초마다 25% 확률 판정 → 성공 시 낙하, 끝나면 대기 없이 즉시 재판정
 *     (운 좋으면 연속으로 여러 개, 실패하면 다시 30초 대기) — 실제 3D 우주공간을 가로지르는
 *     별똥별. 머리(밝게)+꼬리(스트릭 순번별 색상) 스프라이트 체인, 깊이 성분 포함 랜덤 3D
 *     경로(원근 이동), 점화→소멸 봉투
 *  2. 지구 구체: 원본의 3/4 밝기 기준 + 낮/밤 반구 — UTC 하루 기준 360도 도는 태양 방향의
 *     반구는 기준 밝기 그대로, 반대 반구는 30% 감광, 터미네이터는 smoothstep 으로 부드럽게
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
    private var cloudProgram = 0
    private var cloudTex = 0            // NASA 구름맵(흑백 — 밝기를 알파로 사용, 구름 외 투명)
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
    private var sunTex = 0              // 태양 전용 텍스처(원반+코로나 합성 — 인위적 십자 광선 없음)
    private var sunVbo = 0
    private var sunBuiltFrac = -1f      // 태양 스프라이트를 빌드한 시각(dayFrac) — 1분 넘게 지나면 재배치

    /** 자유 원호 트레일(지구 좌표계 — 구와 함께 회전).
     *  phase: 트레일별 파동 위상(불규칙성), intensity: 트레일별 투명도 차등(1=기준). */
    private class Trail(
        val vbo: Int, val count: Int,
        val colorA: FloatArray, val colorB: FloatArray,
        val speed: Float, val phase: Float, val intensity: Float,
    )
    private val trails = ArrayList<Trail>()

    // ── 유성(별똥별) — 랜덤 간격으로 하늘을 곡선으로 가로지르는 빛줄기 + 잔류 스파클 ──
    private var meteorVbo = 0
    private var meteorStartT = -1f          // 진행 중 유성의 시작 시각(초). 음수 = 대기 중
    private var meteorDur = 1.1f            // 이번 유성 수명(초)
    private var nextRollT = 0f              // 다음 확률 판정 시각(초) — 실패 시 30초 뒤, 성공 시 낙하 종료 즉시
    private var meteorStreak = 0            // 연속 성공 횟수(색 선택에 사용, 실패하면 0으로 리셋)
    private var meteorTintIdx = 0           // 지금 낙하 중인 유성에 적용된 색상(스폰 시점에 고정)
    private val meteorP0 = FloatArray(3)    // 시작점(뷰 공간 — 모델 회전과 무관, 화면 기준 배치)
    private val meteorDir = FloatArray(3)   // 진행 방향(단위벡터)
    private val meteorPerp = FloatArray(3)  // 곡선 휨 방향(경로 수직, 화면면 성분)
    private var meteorBend = 0f             // 총 휨 거리(월드 단위) — p(s)=p0+dir·len·s+perp·bend·s²
    private var meteorLen = 8f              // 총 이동 거리(월드 단위)
    private val meteorRnd = java.util.Random()
    private var screenAspect = 0.55f        // onSurfaceChanged 에서 갱신(가로/세로)
    private val identityM = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    /** 잔류 스파클 — 유성이 지나간 자리에 남아 반짝이다 사그라드는 파티클.
     *  [x, y, z, r, g, b, size, birthT, life, phase] × N. 유성이 죽은 뒤에도 수명이 다할 때까지 그려진다. */
    private val meteorSparks = ArrayList<FloatArray>()
    private var sparkEmitCarry = 0f         // 프레임 간 방출량 이월(초당 방출률 적분)
    private var lastMeteorU = 0f            // 직전 프레임의 진행도(방출 구간 보간용)

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
        cloudProgram = buildProgram(CLOUD_VS, CLOUD_FS)

        buildEarthMesh()
        earthTex = loadEarthTexture()
        cloudTex = loadCloudTexture()
        flareTex = uploadTexture(makeFlareBitmap())
        glowTex = uploadTexture(makeGlowBitmap())
        buildStarfield()
        buildTrails()
        flareVbo = genBuffer()
        glowVbo = genBuffer()
        sunTex = uploadTexture(makeSunBitmap())
        sunVbo = genBuffer()
        sunBuiltFrac = -1f
        meteorVbo = genBuffer()
        meteorStartT = -1f
        meteorStreak = 0
        meteorSparks.clear()
        sparkEmitCarry = 0f
        // 글로브 입장 이후 30초마다 확률 판정 시작(첫 판정도 입장 30초 뒤).
        nextRollT = (SystemClock.uptimeMillis() - startMs) / 1000f + METEOR_ROLL_INTERVAL
        starsDirty = true // 서피스 재생성 시(백그라운드 복귀) 재업로드
        fade = 0f
        lastFrameNs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        screenAspect = aspect
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
        // 1.7) 유성 — 랜덤 간격으로 하늘을 곡선으로 가로지르는 별똥별 + 잔류 스파클(배경층)
        drawMeteor(camPos, t, dt)
        // 1.8) 태양 — 광원 방향 하늘의 해(원반+코로나, 십자 광선 없음). 하루 주기로 광원과 함께 돈다
        drawSun(camPos, t)

        // 2) 지구 본체 (불투명, 깊이 기록)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        drawEarth()

        // 2.5) 구름 레이어 — 지표 살짝 위에서 천천히 흘러가는 대기(확대해서 다가가면 페이드아웃)
        drawClouds(t)

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
        // 태양 방향 — 지구 좌표계 벡터라 구를 드래그로 돌려도 "지금 실제로 낮인 지역"이 항상 밝다.
        val sun = sunDirection()
        GLES20.glUniform3f(GLES20.glGetUniformLocation(earthProgram, "uSunDir"), sun[0], sun[1], sun[2])
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

    /** 구름 레이어 — 지구 메쉬 재사용(셰이더에서 반경 1.012 로 확대), 경도 방향으로 천천히 드리프트.
     *  NASA 구름맵은 흑백(밝기=구름 밀도)이라 셰이더에서 밝기를 알파로 써서 구름 외엔 투명.
     *  카메라가 가까이 오면(확대) 부드럽게 사라져 지표를 가리지 않는다. */
    private fun drawClouds(t: Float) {
        if (cloudTex == 0) return
        // 줌 페이드: camDist 2.4 이상 = 완전 표시, 1.7 이하 = 완전 소멸 (smoothstep)
        val f = ((camDist - 1.7f) / (2.4f - 1.7f)).coerceIn(0f, 1f)
        val zoomAlpha = f * f * (3f - 2f * f)
        if (zoomAlpha < 0.01f) return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(cloudProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(cloudProgram, "uMVP"), 1, false, mvp, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(cloudProgram, "uFade"), fade)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(cloudProgram, "uAlpha"), zoomAlpha)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(cloudProgram, "uShift"), t * CLOUD_DRIFT)
        val sun = sunDirection()
        GLES20.glUniform3f(GLES20.glGetUniformLocation(cloudProgram, "uSunDir"), sun[0], sun[1], sun[2])
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cloudTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(cloudProgram, "uTex"), 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, earthVbo)
        val aPos = GLES20.glGetAttribLocation(cloudProgram, "aPos")
        val aUV = GLES20.glGetAttribLocation(cloudProgram, "aUV")
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
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    /** 태양 방향(지구 좌표계 단위벡터) — UTC 기준 하루에 360도 회전(적도 상공, UTC 정오에 경도 0 상공). */
    private fun sunDirection(): FloatArray {
        val dayFrac = (System.currentTimeMillis() % 86_400_000L) / 86_400_000f
        return latLngToXyz(0.0, 180.0 - dayFrac * 360.0, 1f)
    }

    /** 태양 — 광원 방향 하늘(SUN_DIST)에 떠 있는 해: 전용 텍스처(원반+코로나 합성, 색은
     *  텍스처에 베이크)를 단일 스프라이트로 그린다 — 인위적인 십자 광선 없이 부드러운 구체감.
     *  배경층(지구 앞에서 가려짐)에 additive 로 그린다. 하루 주기로 도는 위치는 1분 단위로만
     *  재빌드(그 사이 이동량은 시각적으로 0에 수렴 — 매 프레임 버퍼 재업로드 낭비 방지). */
    private fun drawSun(camPos: FloatArray, t: Float) {
        val dayFrac = (System.currentTimeMillis() % 86_400_000L) / 86_400_000f
        if (sunBuiltFrac < 0f || kotlin.math.abs(dayFrac - sunBuiltFrac) > 1f / 1440f) {
            sunBuiltFrac = dayFrac
            val dir = sunDirection()
            val p = floatArrayOf(dir[0] * SUN_DIST, dir[1] * SUN_DIST, dir[2] * SUN_DIST)
            val list = ArrayList<Float>(6 * SPRITE_FLOATS)
            addSprite(list, p, r = 1f, g = 1f, b = 1f, a = 1f, size = 1.5f, phase = 0.13f, mode = 2f)
            uploadBuffer(sunVbo, toFloatBuffer(list))
        }
        drawSprites(sunVbo, 6, sunTex, camPos, t, depthTest = false)
    }

    /** 태양 텍스처 — 원경 산광 → 금빛 코로나 → 백열 원반을 부드러운 다단 그라데이션으로 겹쳐
     *  실제 우주에서 보이는 태양처럼(둥근 구체감, 인위적 십자 플레어 없이) 합성한다. */
    private fun makeSunBitmap(): Bitmap {
        val s = 256
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = s / 2f
        fun glow(radiusFrac: Float, colors: IntArray, stops: FloatArray) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cx, cx * radiusFrac, colors, stops, Shader.TileMode.CLAMP)
            }
            c.drawCircle(cx, cx, cx * radiusFrac, p)
        }
        // 원경 산광 — 아주 넓고 옅게 퍼져 우주 공간 속 광원임을 알려준다
        glow(
            1.00f,
            intArrayOf(0x2A281808, 0x140A0604, 0x00000000),
            floatArrayOf(0f, 0.5f, 1f),
        )
        // 금빛 코로나 — 중간 반경, 따뜻한 주황빛
        glow(
            0.46f,
            intArrayOf(0xB08C6E2E.toInt(), 0x50755530, 0x00000000),
            floatArrayOf(0f, 0.45f, 1f),
        )
        // 백열 원반 — 다단 그라데이션으로 가장자리를 부드럽게(림 다크닝풍) 마감
        glow(
            0.20f,
            intArrayOf(
                0xFFFFF8E8.toInt(), 0xFFFFEFC8.toInt(), 0xE8FFD9A0.toInt(), 0x60E8B060.toInt(), 0x00000000,
            ),
            floatArrayOf(0f, 0.55f, 0.80f, 0.94f, 1f),
        )
        return bmp
    }

    /** 유성 경로 — 약간의 곡선(2차 베지어풍): p(s) = p0 + dir·len·s + perp·bend·s². */
    private fun meteorPathAt(s: Float, out: FloatArray) {
        val s2 = s * s
        out[0] = meteorP0[0] + meteorDir[0] * meteorLen * s + meteorPerp[0] * meteorBend * s2
        out[1] = meteorP0[1] + meteorDir[1] * meteorLen * s + meteorPerp[1] * meteorBend * s2
        out[2] = meteorP0[2] + meteorDir[2] * meteorLen * s + meteorPerp[2] * meteorBend * s2
    }

    /** 유성 — 글로브 입장 후 [METEOR_ROLL_INTERVAL]초마다 [METEOR_SPAWN_CHANCE] 확률 판정.
     *  성공하면 유성이 떨어지고, 그 낙하가 끝나자마자(대기 없이) 곧바로 다시 확률을 돌린다 —
     *  운이 좋으면 연속으로 계속 떨어질 수 있다. 실패하면 다시 [METEOR_ROLL_INTERVAL]초를 기다린다.
     *  연속(스트릭)으로 떨어질 때는 매번 다른 색([METEOR_TINTS])으로 바뀐다.
     *
     *  연출(3D 디자인 개편):
     *  - 직선이 아니라 **약간의 곡선**을 그리며 떨어진다(경로 수직 방향 2차 휨).
     *  - 꼬리는 경로를 따라 휘고, 머리→꼬리로 **2색 그라데이션 + 트윙클**로 화려하게 반짝인다.
     *  - 지나간 자리에 **잔류 스파클 파티클**을 흩뿌리고, 유성이 사라진 뒤에도 1~2초 반짝이다
     *    사그라든다([meteorSparks] — 유성 없는 프레임에도 계속 그려진다). */
    private fun drawMeteor(camPos: FloatArray, t: Float, dt: Float) {
        if (meteorStartT < 0f) {
            if (t >= nextRollT) {
                if (meteorRnd.nextFloat() < METEOR_SPAWN_CHANCE) {
                    meteorTintIdx = meteorStreak % METEOR_TINTS.size
                    spawnMeteor(t)
                    meteorStreak++ // 다음 판정이 성공하면 그 다음 색으로
                } else {
                    meteorStreak = 0 // 스트릭 종료 — 다음 성공은 다시 기본색부터
                    nextRollT = t + METEOR_ROLL_INTERVAL
                }
            }
        }

        val list = ArrayList<Float>((meteorSparks.size + METEOR_SPRITES) * 6 * SPRITE_FLOATS)

        // ── 잔류 스파클 — 수명이 다한 것은 제거, 살아있는 것은 나이에 따라 사그라들며 반짝인다 ──
        if (meteorSparks.isNotEmpty()) {
            val it = meteorSparks.iterator()
            while (it.hasNext()) {
                val s = it.next()
                val age = (t - s[7]) / s[8]
                if (age >= 1f) { it.remove(); continue }
                val fadeS = (1f - age) * (1f - age) // 잔광 easeOut
                addSprite(
                    list, floatArrayOf(s[0], s[1], s[2]),
                    r = s[3] * fadeS, g = s[4] * fadeS, b = s[5] * fadeS, a = 1f,
                    size = s[6] * (0.55f + 0.45f * (1f - age)),
                    phase = s[9], mode = 1f, // 트윙클 — 잔해가 자글자글 반짝인다
                )
            }
        }

        // ── 본체(머리+휘어지는 꼬리) ──
        if (meteorStartT >= 0f) {
            val u = (t - meteorStartT) / meteorDur
            if (u >= 1f) {
                meteorStartT = -1f
                nextRollT = t // 낙하가 끝나면 대기 없이 즉시 재도전(연속 확률)
            } else {
                val ignite = min(1f, u / 0.12f)
                val dieRaw = ((1f - u) / 0.30f).coerceIn(0f, 1f)
                val env = ignite * dieRaw * (2f - dieRaw) // 소멸 구간은 easeOut
                val tint = METEOR_TINTS[meteorTintIdx]
                val tint2 = METEOR_TINTS[(meteorTintIdx + 1) % METEOR_TINTS.size] // 꼬리 쪽 보조색
                val tailFrac = 0.14f + 0.12f * ignite // 점화되며 꼬리가 자란다(경로 비율)
                val pos = FloatArray(3)
                for (i in 0 until METEOR_SPRITES) {
                    val back = i / (METEOR_SPRITES - 1f) // 0=머리 → 1=꼬리 끝
                    val fall = 1f - back
                    val bright = env * (0.06f + 0.94f * fall * fall)
                    meteorPathAt((u - tailFrac * back).coerceAtLeast(-0.05f), pos)
                    // 머리는 백열(정광), 꼬리는 본색→보조색 그라데이션 + 트윙클로 화려하게
                    val mixT = back * 0.60f
                    val cr = tint[0] + (tint2[0] - tint[0]) * mixT
                    val cg = tint[1] + (tint2[1] - tint[1]) * mixT
                    val cb = tint[2] + (tint2[2] - tint[2]) * mixT
                    addSprite(
                        list, floatArrayOf(pos[0], pos[1], pos[2]),
                        r = bright * (0.72f + 0.28f * fall) * cr,
                        g = bright * (0.80f + 0.20f * fall) * cg,
                        b = bright * cb,
                        a = 1f,
                        size = 0.05f + 0.10f * fall,
                        phase = i * 0.37f,
                        mode = if (i == 0) 2f else 1f, // 머리=정광, 꼬리=트윙클
                    )
                }
                // 잔류 스파클 방출 — 머리가 지나는 구간을 따라 초당 일정량 흩뿌린다
                emitSparks(t, dt, u, tint, tint2)
                lastMeteorU = u
            }
        }

        if (list.isEmpty()) return
        uploadBuffer(meteorVbo, toFloatBuffer(list))
        drawSprites(
            meteorVbo, list.size / SPRITE_FLOATS, glowTex, camPos, t,
            depthTest = false, modelM = identityM,
        )
    }

    /** 잔류 스파클 방출 — 직전 프레임 진행도와 현재 진행도 사이 경로에 고르게 뿌린다. */
    private fun emitSparks(t: Float, dt: Float, u: Float, tint: FloatArray, tint2: FloatArray) {
        sparkEmitCarry += dt * SPARK_RATE
        val n = sparkEmitCarry.toInt()
        if (n <= 0) return
        sparkEmitCarry -= n
        val pos = FloatArray(3)
        val jitter = meteorLen * 0.012f
        repeat(n) {
            if (meteorSparks.size >= SPARK_MAX) meteorSparks.removeAt(0)
            val s = lastMeteorU + (u - lastMeteorU) * meteorRnd.nextFloat()
            meteorPathAt(s.coerceIn(0f, 1f), pos)
            // 색 — 본색↔보조색↔백색 사이를 랜덤하게 오가는 화려한 불꽃 파편
            val m1 = meteorRnd.nextFloat() * 0.8f
            val m2 = meteorRnd.nextFloat() * 0.45f
            var cr = tint[0] + (tint2[0] - tint[0]) * m1; cr += (1f - cr) * m2
            var cg = tint[1] + (tint2[1] - tint[1]) * m1; cg += (1f - cg) * m2
            var cb = tint[2] + (tint2[2] - tint[2]) * m1; cb += (1f - cb) * m2
            val br = 0.35f + meteorRnd.nextFloat() * 0.45f
            meteorSparks.add(
                floatArrayOf(
                    pos[0] + (meteorRnd.nextFloat() * 2f - 1f) * jitter,
                    pos[1] + (meteorRnd.nextFloat() * 2f - 1f) * jitter,
                    pos[2] + (meteorRnd.nextFloat() * 2f - 1f) * jitter,
                    cr * br, cg * br, cb * br,
                    0.020f + meteorRnd.nextFloat() * 0.034f, // size
                    t,                                        // birthT
                    SPARK_LIFE_MIN + meteorRnd.nextFloat() * SPARK_LIFE_VAR, // life
                    meteorRnd.nextFloat(),                    // phase
                )
            )
        }
    }

    /** 유성 생성 — 카메라 앞 임의 깊이의 통과 지점을 잡고, 깊이 성분을 포함한 랜덤 3D 방향에
     *  경로 수직 방향의 **곡선 휨**(perp·bend)을 더해 완만한 포물선 궤적을 뽑는다. */
    private fun spawnMeteor(t: Float) {
        meteorStartT = t
        meteorDur = 0.9f + meteorRnd.nextFloat() * 0.7f
        lastMeteorU = 0f
        sparkEmitCarry = 0f
        val depth = 20f + meteorRnd.nextFloat() * 16f      // 카메라~통과지점 거리(별밭 셸 사이)
        val kY = depth * 0.384f                            // 그 깊이에서 화면 세로 반높이(tan 21°)
        val kX = kY * screenAspect
        // 화면 어디서든 나타나게 — 통과 지점을 화면 전역에 랜덤 배치
        val sx = (meteorRnd.nextFloat() * 1.7f - 0.85f) * kX
        val sy = (meteorRnd.nextFloat() * 1.7f - 0.85f) * kY
        val midZ = camDist - depth                         // 카메라(+z) 앞쪽(-z 방향)
        // 화면면 방향 — 항상 아래쪽(수직 하강)~사선으로만, 위로 올라가는 방향은 배제.
        // theta ∈ [π+margin, 2π-margin] → sin(theta) ≤ 0(수평 좌/우 ~ 수직 아래) + 깊이 성분(다가옴/멀어짐)
        val margin = 0.20f
        val theta = Math.PI.toFloat() + margin + meteorRnd.nextFloat() * (Math.PI.toFloat() - 2f * margin)
        val zc = (meteorRnd.nextFloat() * 2f - 1f) * 0.55f
        val tc = kotlin.math.sqrt(1f - zc * zc)
        meteorDir[0] = cos(theta) * tc
        meteorDir[1] = sin(theta) * tc
        meteorDir[2] = zc
        meteorLen = (1.0f + meteorRnd.nextFloat() * 0.7f) * kY
        // 곡선 휨 — 진행 방향과 시선(z축)에 수직인 화면면 방향으로 완만하게 휜다.
        // perp = normalize(dir × ẑ) (dir 이 z 에 가까우면 화면 가로로 폴백), 부호는 랜덤.
        var px = meteorDir[1] * 1f - 0f
        var py = 0f - meteorDir[0] * 1f
        val pl = kotlin.math.sqrt(px * px + py * py)
        if (pl < 0.15f) { px = 1f; py = 0f } else { px /= pl; py /= pl }
        val sign = if (meteorRnd.nextBoolean()) 1f else -1f
        meteorPerp[0] = px * sign
        meteorPerp[1] = py * sign
        meteorPerp[2] = 0f
        meteorBend = meteorLen * (0.10f + meteorRnd.nextFloat() * 0.14f)
        meteorP0[0] = sx - meteorDir[0] * meteorLen * 0.5f - meteorPerp[0] * meteorBend * 0.25f
        meteorP0[1] = sy - meteorDir[1] * meteorLen * 0.5f - meteorPerp[1] * meteorBend * 0.25f
        meteorP0[2] = midZ - meteorDir[2] * meteorLen * 0.5f
    }

    private fun drawSprites(
        vbo: Int, count: Int, tex: Int, camPos: FloatArray, t: Float, depthTest: Boolean,
        modelM: FloatArray = model, // 유성 등 "화면 기준" 스프라이트는 identityM(모델 회전 무시)
    ) {
        if (vbo == 0 || count == 0) return
        if (depthTest) GLES20.glEnable(GLES20.GL_DEPTH_TEST) else GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(spriteProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(spriteProgram, "uVP"), 1, false, vp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(spriteProgram, "uModel"), 1, false, modelM, 0)
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

        // ── 은하수 — 실제 은하수 사진의 구조를 재현(자료 조사: 은하핵 골든 벌지 + Great Rift
        //    암흑 균열 + 얼룩덜룩한 스타 클라우드 + H-II 핑크 성운 + 핵→외곽 색 온도 구배) ──
        //  additive 렌더라 "어두운 먼지"는 직접 못 그리므로, 균열/먼지 자리의 별·유광 밝기를
        //  감쇠(riftAtten·patch)시켜 주변이 빛나는 만큼 상대적으로 어둡게 보이게 한다.
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
            val coreAng = 1.2f // 은하핵(벌지) 방향 — 이 근처가 가장 밝고 두껍다
            fun angDist(ang: Float): Float = // 핵까지의 대원 위 각거리(0..π)
                kotlin.math.abs((ang - coreAng + Math.PI.toFloat()).mod(6.2832f) - Math.PI.toFloat())
            fun coreness(ang: Float): Float { // 핵에 가까울수록 1(가우시안)
                val d = angDist(ang)
                return exp(-d * d / 1.5f)
            }
            // Great Rift — 핵 쪽 절반에서 띠를 세로로 가르는 암흑 성운 균열.
            // 균열 중심선은 사인 2개로 구불구불 밴드 중심에서 살짝 벗어나 흐르고, 폭도 변한다.
            fun riftAtten(ang: Float, spread: Float): Float {
                val d = angDist(ang)
                val strength = exp(-d * d / 1.9f) * 0.90f // 핵 근처에서 가장 짙고 반대편은 없음
                if (strength < 0.04f) return 1f
                val center = 0.022f + 0.026f * sin(ang * 2.3f + 0.8f) + 0.012f * sin(ang * 5.1f)
                val halfW = 0.030f + 0.013f * sin(ang * 3.7f + 2.0f)
                val x = (spread - center) / halfW
                return 1f - strength * exp(-x * x)
            }
            // 얼룩(패치) — 밝은 스타 클라우드와 어두운 먼지 조각이 띠를 따라 교차(사진의 mottled 질감)
            fun patch(ang: Float): Float {
                val p = 0.5f + 0.5f * sin(ang * 7.3f + 1.7f) * sin(ang * 3.1f + 4.2f)
                return 0.62f + 0.55f * p
            }
            // 색 온도 — 핵(골든·오렌지) → 외곽(차가운 청백). warm ∈ 0..1
            fun bandTint(warm: Float): FloatArray = floatArrayOf(
                0.84f + 0.19f * warm,          // r
                0.88f - 0.05f * warm,          // g
                1.00f - 0.38f * warm,          // b
            )

            // ① 잔별 밀집 띠 — 2600개, 핵 근처 밀도·두께 증가(채택-기각 샘플링), 균열·얼룩 감쇠
            var placed = 0
            while (placed < 2600) {
                val ang = rnd.nextFloat() * 6.2832f
                val cn = coreness(ang)
                if (rnd.nextFloat() > 0.38f + 0.62f * cn) continue // 핵 쪽 밀도↑
                placed++
                val thick = 1f + 0.8f * cn // 핵 근처는 띠가 두껍다(벌지)
                val sigma = (if (rnd.nextFloat() < 0.66f) 0.045f else 0.115f) * thick
                val spread = (rnd.nextGaussian() * sigma).toFloat()
                val atten = riftAtten(ang, spread)
                val br = (0.07f + rnd.nextFloat() * 0.26f) * (0.70f + 0.60f * cn) *
                    patch(ang) * (0.22f + 0.78f * atten)
                val warm = (rnd.nextFloat() * 0.45f + 0.55f * cn * rnd.nextFloat()).coerceIn(0f, 1f)
                val tint = bandTint(warm)
                addSprite(
                    list, p = bandPoint(39f, spread, ang),
                    r = br * tint[0], g = br * tint[1], b = br * tint[2], a = 1f,
                    size = 0.028f + rnd.nextFloat() * rnd.nextFloat() * 0.10f,
                    phase = rnd.nextFloat(), mode = 1f,
                )
            }
            // ①-b 전경 밝은 별 — 띠 위에 도드라지는 소수의 밝은 별(사진의 빛나는 점들)
            repeat(26) {
                val ang = rnd.nextFloat() * 6.2832f
                val cn = coreness(ang)
                val spread = (rnd.nextGaussian() * 0.09f * (1f + 0.6f * cn)).toFloat()
                val br = 0.30f + rnd.nextFloat() * 0.34f
                val warm = rnd.nextFloat() * 0.5f
                val tint = bandTint(warm)
                addSprite(
                    list, p = bandPoint(39f, spread, ang),
                    r = br * tint[0], g = br * tint[1], b = br * tint[2], a = 1f,
                    size = 0.11f + rnd.nextFloat() * 0.09f,
                    phase = rnd.nextFloat(), mode = 1f,
                )
            }
            // ② 연속 유광 리본(희미한 바탕) — 띠가 끊겨 보이지 않게 잇는 은은한 강바닥
            repeat(72) { i ->
                val ang = i / 72f * 6.2832f + (rnd.nextFloat() - 0.5f) * 0.05f
                val cn = coreness(ang)
                val spread = (rnd.nextGaussian() * 0.022).toFloat()
                val base = (0.018f + 0.016f * cn) * (0.30f + 0.70f * riftAtten(ang, spread))
                addSprite(
                    list, p = bandPoint(40f, spread, ang),
                    r = base * (0.85f + 0.35f * cn), g = base * 0.92f, b = base * (1.10f - 0.25f * cn), a = 1f,
                    size = 2.2f + rnd.nextFloat() * 1.5f + 1.1f * cn,
                    phase = rnd.nextFloat(), mode = 1f,
                )
            }
            // ②-b 스타 클라우드 — 뭉게뭉게 밝은 유광 덩어리(얼룩·균열을 따라 응집/단절)
            repeat(150) {
                val ang = rnd.nextFloat() * 6.2832f
                val cn = coreness(ang)
                if (rnd.nextFloat() > 0.30f + 0.70f * cn) return@repeat // 핵 쪽에 더 많이
                val spread = (rnd.nextGaussian() * 0.055f * (1f + 0.7f * cn)).toFloat()
                val atten = riftAtten(ang, spread)
                val base = (0.014f + 0.030f * cn) * patch(ang) * (0.15f + 0.85f * atten)
                val warm = (0.25f + 0.75f * cn) * rnd.nextFloat()
                val tint = bandTint(warm)
                addSprite(
                    list, p = bandPoint(40f, spread, ang),
                    r = base * tint[0] * 1.15f, g = base * tint[1], b = base * tint[2], a = 1f,
                    size = 0.9f + rnd.nextFloat() * 1.8f + 0.8f * cn,
                    phase = rnd.nextFloat(), mode = 1f,
                )
            }
            // ③ 은하핵 벌지 — 골든·오렌지 대형 글로우 응집(사진의 가장 밝은 심장부)
            repeat(22) {
                val ang = coreAng + (rnd.nextGaussian() * 0.20).toFloat()
                val spread = (rnd.nextGaussian() * 0.065).toFloat()
                val atten = 0.35f + 0.65f * riftAtten(ang, spread) // 벌지도 균열에 살짝 갈라진다
                addSprite(
                    list, p = bandPoint(40f, spread, ang),
                    r = 0.085f * atten, g = 0.058f * atten, b = 0.034f * atten, a = 1f,
                    size = 1.5f + rnd.nextFloat() * 1.9f,
                    phase = rnd.nextFloat(), mode = 1f,
                )
            }
            // ③-b 벌지 심장 — 핵 정중앙의 크고 은은한 골든 후광 2장
            repeat(2) { i ->
                addSprite(
                    list, p = bandPoint(40f, 0.012f * i, coreAng + 0.05f * i),
                    r = 0.052f, g = 0.036f, b = 0.020f, a = 1f,
                    size = 3.4f + i * 0.9f,
                    phase = rnd.nextFloat(), mode = 1f,
                )
            }
            // ④ H-II 발광 성운 — 핵 주변 띠 위에 흩어진 작은 핑크/마젠타 반점(라군 성운풍)
            repeat(9) {
                val ang = coreAng + (rnd.nextGaussian() * 0.85).toFloat()
                val spread = (rnd.nextGaussian() * 0.06).toFloat()
                addSprite(
                    list, p = bandPoint(39.5f, spread, ang),
                    r = 0.050f, g = 0.016f, b = 0.030f, a = 1f,
                    size = 0.5f + rnd.nextFloat() * 0.7f,
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

    /** NASA 구름맵 로드(assets/earth_clouds.jpg, 퍼블릭 도메인). 없어도 크래시 없이 레이어만 생략. */
    private fun loadCloudTexture(): Int = try {
        val bmp = context.assets.open("earth_clouds.jpg").use { BitmapFactory.decodeStream(it) }
        uploadTexture(bmp, wrapS = GLES20.GL_REPEAT) // 경도 드리프트(u+shift)를 위해 REPEAT
    } catch (e: Exception) {
        Log.w("GlobeRenderer", "cloud texture missing: $e")
        0
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
        const val ENTER_DIST = 10.4f  // 진입 시작 거리(돌리-인 출발점 — 최대 거리 살짝 바깥)
        const val IDLE_DIST = 9.5f    // 진입 정착 거리 = 최소 줌(MAX_DIST) — 지구가 가장 작게 보이는 상태
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
        private const val SUN_DIST = 45f // 태양 위치 반지름(원경 별밭 너머, far plane 안)
        private const val CLOUD_DRIFT = 0.0035f // 구름 경도 드리프트 속도(rev/s — 한 바퀴 ≈ 4.8분)

        /** 유성 — 꼬리 스프라이트 수 / 확률 판정 주기(초) / 판정 성공 확률. */
        private const val METEOR_SPRITES = 22
        private const val METEOR_ROLL_INTERVAL = 30f
        private const val METEOR_SPAWN_CHANCE = 0.25f

        /** 잔류 스파클 — 초당 방출 수 / 최대 동시 수 / 수명(초, 최소+가변). */
        private const val SPARK_RATE = 85f
        private const val SPARK_MAX = 240
        private const val SPARK_LIFE_MIN = 1.0f
        private const val SPARK_LIFE_VAR = 0.9f

        /** 유성 스트릭 색상 팔레트 — 연속으로 떨어질 때마다 순서대로 바뀐다(0=기본 청백). */
        private val METEOR_TINTS = arrayOf(
            floatArrayOf(1.00f, 1.00f, 1.00f), // 기본 청백
            floatArrayOf(1.00f, 0.60f, 0.32f), // 주황
            floatArrayOf(0.55f, 1.00f, 0.62f), // 초록
            floatArrayOf(1.00f, 0.45f, 0.85f), // 핑크
            floatArrayOf(1.00f, 0.86f, 0.32f), // 골드
            floatArrayOf(0.62f, 0.58f, 1.00f), // 보라
        )

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
            varying vec2 vUV; varying vec3 vN;
            void main() { vUV = aUV; vN = aPos; gl_Position = uMVP * vec4(aPos, 1.0); }
        """

        private const val EARTH_FS = """
            precision mediump float;
            uniform sampler2D uTex; uniform float uFade;
            uniform vec3 uSunDir; // 태양 방향(지구 좌표계 단위벡터) — UTC 하루 기준 360도 회전
            varying vec2 vUV; varying vec3 vN;
            void main() {
                // 낮/밤 반구 — 태양 쪽은 기준보다 살짝 밝게(1.15), 반대쪽은 30% 감광(0.7).
                // 터미네이터(명암 경계)는 smoothstep 으로 자연스럽게 이어진다.
                float ndl = dot(normalize(vN), uSunDir);
                float light = 0.70 + 0.45 * smoothstep(-0.18, 0.22, ndl);
                gl_FragColor = vec4(texture2D(uTex, vUV).rgb * $EARTH_BRIGHTNESS * light * uFade, 1.0);
            }
        """

        /** 구름 레이어 셰이더 — 지구 메쉬를 1.012배로 부풀려 대기 셸을 만든다.
         *  흑백 구름맵의 밝기 = 구름 밀도 = 알파(구름 외 완전 투명). u 를 시간에 따라 밀어
         *  대기가 지표 위를 천천히 흐르는 느낌. 낮/밤 광원(uSunDir)도 지구와 동일하게 적용. */
        private const val CLOUD_VS = """
            uniform mat4 uMVP;
            attribute vec3 aPos; attribute vec2 aUV;
            varying vec2 vUV; varying vec3 vN;
            void main() { vUV = aUV; vN = aPos; gl_Position = uMVP * vec4(aPos * 1.012, 1.0); }
        """

        private const val CLOUD_FS = """
            precision mediump float;
            uniform sampler2D uTex; uniform float uFade; uniform float uAlpha; uniform float uShift;
            uniform vec3 uSunDir;
            varying vec2 vUV; varying vec3 vN;
            void main() {
                float cloud = texture2D(uTex, vec2(vUV.x + uShift, vUV.y)).r; // 밝기 = 구름 밀도
                float ndl = dot(normalize(vN), uSunDir);
                float light = 0.70 + 0.45 * smoothstep(-0.18, 0.22, ndl);
                vec3 col = vec3(0.62, 0.70, 0.82) * light; // 밤 지구 톤에 맞춘 은은한 청백 구름
                gl_FragColor = vec4(col * uFade, cloud * 0.45 * uAlpha * uFade);
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
                if (aMode > 1.5) tw = 1.0; // mode=2: 트윙클 없는 정광(태양)
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

    }
}
