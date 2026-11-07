package com.chaminwoo.stary.core.designsystem

import android.graphics.Matrix
import android.graphics.Path
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.PathParser
import com.chaminwoo.stary.core.model.Diary
import java.util.concurrent.ConcurrentHashMap

/**
 * 앱에 내장되지 않은 별 모양·색 — starType ≥ [StarStyle.TYPE_COUNT], starColor ≥ [StarStyle.COLOR_COUNT] (2026-10-22).
 *
 * 새 모양·색(성운 상자 보상 등)은 앱 업데이트 없이 DB 로만 늘어난다. 그래서 그런 별은 문서에 **그릴 정보를 함께** 싣는다:
 * [Diary.starPath](SVG 경로) · [Diary.starColors](hex 정지점). 앱은 처음 보는 번호를 여기에 배워 두고, [StarStyle] 이
 * 이 정의로 그린다 → 새 버전에서 올린 별도 이 버전 이후의 앱이면 모양·색 그대로 보인다.
 * 정보가 없는 번호(아직 못 배운 것)는 [StarStyle.safeType]/[StarStyle.safeColor] 가 기본 별·흰색으로 바꿔 그린다.
 *
 * ⚠️ 경로 규칙 — iOS `StarCatalog.swift` 의 파서와 같아야 한다:
 *  - 0~1 단위 정사각형(좌상단 원점, y 는 아래로) 안의 SVG path data. 명령은 **M L H V C S Q T Z**(대·소문자).
 *  - **A(호)는 쓰지 않는다**(iOS 파서 미지원) — 원·호는 C(3차 베지어)로 근사한다. 채움 규칙 non-zero.
 * 색 규칙 — "#RRGGBB" 1~3개(1개 = 단색, 2~3개 = 좌상→우하 그라데이션). 내장 단색처럼 흰색을 섞지 않고 그대로 쓴다.
 */
object StarCatalog {
    private const val TAG = "StarCatalog"
    private const val MAX_STOPS = 3
    /** 배울 수 있는 DB 정의 번호 상한 — 문서가 임의 번호를 싣더라도 표가 끝없이 커지지 않게. */
    private const val MAX_CUSTOM_INDEX = 255
    private const val MAX_PATH_LEN = 2000
    private const val MAX_ENTRIES = 128

    private val shapeSvgs = ConcurrentHashMap<Int, String>()
    /** 단위(0~1) 경로 — [unitPath] 가 배율만 줘서 복사해 준다. */
    private val shapePaths = ConcurrentHashMap<Int, Path>()
    private val colorHex = ConcurrentHashMap<Int, List<String>>()
    private val colorValues = ConcurrentHashMap<Int, List<Color>>()

    /** 새로 배울 때마다 +1 — 이미 그린 화면이 다시 그려지게(Compose 상태, 어느 스레드에서 바뀌어도 된다). */
    var revision by mutableIntStateOf(0)
        private set

    /** 별 문서에 실린 그림 정보를 배운다(목록·상세를 읽을 때마다 부른다 — 같은 값이면 아무것도 안 한다). */
    fun learn(d: Diary) {
        learnShape(d.starType, d.starPath)
        learnColor(d.starColor, d.starColors)
    }

    fun learnShape(type: Int, svg: String?) {
        if (type < StarStyle.TYPE_COUNT || type > MAX_CUSTOM_INDEX || svg.isNullOrBlank() || svg.length > MAX_PATH_LEN ||
            shapeSvgs[type] == svg || (shapeSvgs.size >= MAX_ENTRIES && !shapeSvgs.containsKey(type))
        ) return
        val path = try {
            PathParser.createPathFromPathData(svg)
        } catch (e: Exception) {
            Log.w(TAG, "별 모양 $type 경로를 읽지 못함: ${e.javaClass.simpleName}")
            return
        }
        // 값이 없는/비정상 경로(NaN 좌표 등)는 쓰지 않는다.
        val bounds = android.graphics.RectF()
        try {
            @Suppress("DEPRECATION")
            path.computeBounds(bounds, true)
        } catch (e: Exception) {
            return
        }
        if (bounds.isEmpty || !bounds.left.isFinite() || !bounds.right.isFinite() || !bounds.top.isFinite() || !bounds.bottom.isFinite()) return
        shapePaths[type] = path
        shapeSvgs[type] = svg
        revision++
    }

    fun learnColor(index: Int, hexes: List<String>?) {
        if (index < StarStyle.COLOR_COUNT || index > MAX_CUSTOM_INDEX || hexes.isNullOrEmpty() || colorHex[index] == hexes ||
            (colorHex.size >= MAX_ENTRIES && !colorHex.containsKey(index))
        ) return
        val parsed = hexes.take(MAX_STOPS).mapNotNull(::parseHex)
        if (parsed.isEmpty()) return
        colorValues[index] = parsed
        colorHex[index] = hexes.take(MAX_STOPS)
        revision++
    }

    fun hasShape(type: Int): Boolean = shapePaths.containsKey(type)
    fun hasColor(index: Int): Boolean = colorValues.containsKey(index)

    /** 업로드할 때 문서에 실을 값 — 내장 번호면 null(싣지 않음). */
    fun shapeSvg(type: Int): String? = shapeSvgs[type]
    fun colorHexes(index: Int): List<String>? = colorHex[index]

    /** [sizePx] 정사각형에 맞춘 새 경로(배운 적 없으면 null). */
    fun path(type: Int, sizePx: Float): Path? {
        val unit = shapePaths[type] ?: return null
        return Path(unit).apply { transform(Matrix().apply { setScale(sizePx, sizePx) }) }
    }

    fun colors(index: Int): List<Color>? = colorValues[index]

    private fun parseHex(s: String): Color? {
        val h = s.trim().removePrefix("#")
        if (h.length != 6) return null
        val v = h.toLongOrNull(16) ?: return null
        return Color(0xFF000000 or v)
    }
}
