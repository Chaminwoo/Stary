package com.chaminwoo.stary.core.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.chaminwoo.stary.core.geo.GeoUtils
import com.chaminwoo.stary.core.geo.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LocationHelper {
    // 실시간 위치 — 연속 업데이트마다 갱신된다. Compose 에서 collectAsState 로 관찰하면
    // 내 위치 마커/카메라가 실시간으로 따라온다. null = 아직 위치 fix 없음(기본 좌표 사용).
    private val _location = MutableStateFlow<LatLng?>(null)
    val location: StateFlow<LatLng?> = _location.asStateFlow()
    private var manualOverride = false
    var cameraTarget: LatLng? = null

    // 모의 위치(GPS 스푸핑 앱) 감지 — 감지되면 위치 갱신을 거부하고 UI 에서 경고를 띄운다.
    private val _mockDetected = MutableStateFlow(false)
    val mockDetected: StateFlow<Boolean> = _mockDetected.asStateFlow()

    private const val PREFS = "stary_location"
    private const val KEY_LAST_LAT = "last_lat_bits"
    private const val KEY_LAST_LNG = "last_lng_bits"
    private const val KEY_CAM_LAT = "cam_lat_bits"
    private const val KEY_CAM_LNG = "cam_lng_bits"
    private const val KEY_CAM_ZOOM = "cam_zoom_bits"
    private var lastPersistMs = 0L
    private var lastCamPersistMs = 0L

    private var continuousCallback: LocationCallback? = null

    fun setCurrentLocation(newLatLng: LatLng) {
        _location.value = newLatLng
        manualOverride = true
    }

    fun getCurrentLatLng(): LatLng? = _location.value

    /**
     * 지난 세션에서 저장한 마지막 실제 위치 — 앱 시작 시 초기 카메라 좌표용(없으면 null → 기본좌표).
     * 100m 열람 게이팅에는 절대 쓰지 않는다(게이팅은 실제 fix 인 [location] 만).
     */
    fun lastSavedLatLng(context: Context): LatLng? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(KEY_LAST_LAT) || !p.contains(KEY_LAST_LNG)) return null
        return LatLng(
            Double.fromBits(p.getLong(KEY_LAST_LAT, 0L)),
            Double.fromBits(p.getLong(KEY_LAST_LNG, 0L))
        )
    }

    /** 마지막으로 보던 지도 카메라(중심+줌) — 앱 재시작 시 초기 카메라 복원용. */
    data class CameraState(val latitude: Double, val longitude: Double, val zoom: Double)

    /**
     * 지난 세션에서 마지막으로 보던 카메라(중심+줌) — 앱 시작 시 초기 카메라가 "마지막으로
     * 앱을 종료한 위치"에서 시작하도록 복원한다(없으면 null → 마지막 위치/기본좌표 폴백).
     */
    fun lastCameraState(context: Context): CameraState? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(KEY_CAM_LAT) || !p.contains(KEY_CAM_LNG)) return null
        return CameraState(
            Double.fromBits(p.getLong(KEY_CAM_LAT, 0L)),
            Double.fromBits(p.getLong(KEY_CAM_LNG, 0L)),
            Double.fromBits(p.getLong(KEY_CAM_ZOOM, 15.0.toRawBits()))
        )
    }

    /** 카메라 idle 마다 호출 — 2초 스로틀로 디스크 쓰기를 아낀다(프로세스 킬에도 최신값 유지). */
    fun persistCameraState(context: Context, latitude: Double, longitude: Double, zoom: Double) {
        val now = System.currentTimeMillis()
        if (now - lastCamPersistMs < 2_000) return
        lastCamPersistMs = now
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_CAM_LAT, latitude.toRawBits())
            .putLong(KEY_CAM_LNG, longitude.toRawBits())
            .putLong(KEY_CAM_ZOOM, zoom.toRawBits())
            .apply()
    }

    private fun persistLastLocation(context: Context, latLng: LatLng) {
        val now = System.currentTimeMillis()
        if (now - lastPersistMs < 10_000) return // 1초 간격 업데이트마다 디스크에 쓰지 않게 스로틀
        lastPersistMs = now
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_LAT, latLng.latitude.toRawBits())
            .putLong(KEY_LAST_LNG, latLng.longitude.toRawBits())
            .apply()
    }

    /** 모의 위치 제공자(위치 조작 앱)가 만든 Location 인지. */
    private fun isMock(location: Location): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 31) location.isMock
        else @Suppress("DEPRECATION") location.isFromMockProvider

    /** 모의 위치면 거부(true 반환). 정상 위치면 flow 반영 + 마지막 위치 저장. */
    private fun acceptLocation(context: Context, location: Location): Boolean {
        if (isMock(location)) {
            _mockDetected.value = true
            return false
        }
        _mockDetected.value = false
        if (!manualOverride) {
            val ll = LatLng(location.latitude, location.longitude)
            _location.value = ll
            persistLastLocation(context, ll)
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun startContinuousUpdates(context: Context) {
        if (continuousCallback != null) return
        val permission = android.Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) return

        val appContext = context.applicationContext
        val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .build()

        continuousCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { acceptLocation(appContext, it) }
            }
        }
        fusedClient.requestLocationUpdates(request, continuousCallback!!, Looper.getMainLooper())
    }

    fun stopContinuousUpdates(context: Context) {
        continuousCallback?.let {
            LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(it)
            continuousCallback = null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? {
        return try {
            val appContext = context.applicationContext
            val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
            suspendCancellableCoroutine { cont ->
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                    .setMaxUpdates(1)
                    .build()
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        fusedClient.removeLocationUpdates(this)
                        // 일회성 fix 도 실시간 flow 에 반영(진입 직후 빠르게 내 위치 잡히게).
                        // 단 모의 위치는 거부하고 null 반환(위치 조작 차단).
                        val loc = result.lastLocation
                        cont.resume(if (loc != null && acceptLocation(appContext, loc)) loc else null)
                    }
                }
                fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                cont.invokeOnCancellation { fusedClient.removeLocationUpdates(callback) }
            }
        } catch (e: Exception) {
            getLocationFromManager(context)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocationFromManager(context: Context): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        // 폴백 경로도 모의 위치는 거부
        return loc?.takeUnless { isMock(it) }
    }

    // 거리 계산은 플랫폼 공용 GeoUtils(Haversine)로 위임 — iOS 공유 가능
    fun distanceBetween(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Float = GeoUtils.distanceBetween(lat1, lng1, lat2, lng2)
}
