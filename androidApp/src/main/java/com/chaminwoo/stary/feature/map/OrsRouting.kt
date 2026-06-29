package com.chaminwoo.stary.feature.map

import android.util.Log
import com.chaminwoo.stary.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenRouteService 도보 길찾기 클라이언트.
 *
 * - 무료 티어(키 발급 무료, ~2,000건/일). 키는 [BuildConfig.ORS_API_KEY] 로 주입(secrets.properties).
 * - 외부 HTTP 라이브러리 없이 [HttpURLConnection] 한 번의 GET 으로 GeoJSON 경로를 받는다.
 * - 응답 좌표는 ORS 규약대로 [lng, lat] 순서. 그대로 [Route.coordinates] 로 돌려준다(MapLibre Point 와 동일 순서).
 *
 * 규모가 커지면 이 object 만 다른 provider(Mapbox 등)로 교체하면 된다(호출부는 좌표 list 만 사용).
 */
object OrsRouting {

    /** 도보 경로 1건. [coordinates] = [[lng,lat], ...], [distanceM] 미터, [durationS] 초. */
    data class Route(
        val coordinates: List<List<Double>>,
        val distanceM: Double,
        val durationS: Double,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private const val TAG = "OrsRouting"

    /** ORS 키가 실제 값으로 주입됐는지(placeholder 가 아닌지). */
    val isConfigured: Boolean get() = !BuildConfig.ORS_API_KEY.startsWith("TODO_")

    /**
     * 출발(현위치) → 도착(별) 도보 경로. 실패/미설정 시 null.
     * 네트워크 호출이므로 IO 디스패처에서 수행한다.
     */
    suspend fun walkingRoute(
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double,
    ): Route? = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            Log.w(TAG, "ORS_API_KEY 미설정 — secrets.properties 에 ORS_API_KEY 를 넣어주세요.")
            return@withContext null
        }
        val url = "https://api.openrouteservice.org/v2/directions/foot-walking" +
            "?api_key=${BuildConfig.ORS_API_KEY}" +
            "&start=$startLng,$startLat&end=$endLng,$endLat"
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            conn.disconnect()
            if (code !in 200..299 || body == null) {
                Log.w(TAG, "ORS 응답 실패: HTTP $code")
                return@withContext null
            }
            val parsed = json.decodeFromString<OrsResponse>(body)
            val feature = parsed.features.firstOrNull() ?: return@withContext null
            val coords = feature.geometry.coordinates
            if (coords.size < 2) return@withContext null
            Route(
                coordinates = coords,
                distanceM = feature.properties?.summary?.distance ?: 0.0,
                durationS = feature.properties?.summary?.duration ?: 0.0,
            )
        } catch (e: Exception) {
            Log.w(TAG, "ORS 호출 오류: ${e.localizedMessage}")
            null
        }
    }

    // --- GeoJSON 응답 모델(필요한 필드만) ---
    @Serializable private data class OrsResponse(val features: List<OrsFeature> = emptyList())
    @Serializable private data class OrsFeature(val geometry: OrsGeometry, val properties: OrsProps? = null)
    @Serializable private data class OrsGeometry(val coordinates: List<List<Double>> = emptyList())
    @Serializable private data class OrsProps(val summary: OrsSummary? = null)
    @Serializable private data class OrsSummary(val distance: Double = 0.0, val duration: Double = 0.0)
}
