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
import com.google.android.gms.tasks.CancellationTokenSource
import com.chaminwoo.stary.core.geo.GeoUtils
import com.chaminwoo.stary.core.geo.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
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

    private fun hasFine(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * 위치 권한 — 정확한 위치(FINE) **또는 대략적인 위치(COARSE)**. Android 12+ 는 사용자가 "대략적인 위치"만 줄 수 있는데,
     * 예전엔 FINE 만 확인해 그런 사용자는 위치 추적이 아예 시작되지 않았다(2026-10-23 — 해외 신규 사용자 업로드 실패 조사).
     */
    fun hasPermission(context: Context): Boolean =
        hasFine(context) ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 기기 위치(GPS·네트워크 위치)가 켜져 있는지. */
    fun isLocationOn(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return androidx.core.location.LocationManagerCompat.isLocationEnabled(lm)
    }

    /** 대략적인 위치만 있으면 GPS 대신 와이파이·기지국 위치(BALANCED)로 — HIGH 를 요청해도 정확도가 오르지 않는다. */
    private fun priorityFor(context: Context): Int =
        if (hasFine(context)) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

    @SuppressLint("MissingPermission")
    fun startContinuousUpdates(context: Context) {
        if (continuousCallback != null) return
        if (!hasPermission(context)) return

        val appContext = context.applicationContext
        val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
        val request = LocationRequest.Builder(priorityFor(appContext), 2000)
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

    private val oneShotLock = Mutex()
    private var lastOneShot: Location? = null

    /**
     * 1회 위치(거리순 정렬 등). 권한이 없으면 바로 null — 예전엔 대체 경로(getLastKnownLocation)가 권한 예외를 다시 던져
     * 내 다이어리·별 상자를 열면 앱이 꺼졌다. 위치가 꺼져 있으면 콜백이 오지 않아 끝없이 기다렸다 → 10초 제한.
     * 여러 화면이 동시에 불러도 측정은 한 번(잠금 + 30초 안의 결과 재사용).
     */
    suspend fun getCurrentLocation(context: Context): Location? {
        val appContext = context.applicationContext
        if (!hasPermission(appContext)) return null
        return oneShotLock.withLock {
            lastOneShot?.takeIf { ageMs(it) < 30_000 }
                ?: (withTimeoutOrNull(10_000) { requestOneFix(appContext) } ?: lastKnownWithin(appContext, FALLBACK_MS))
                    ?.also { lastOneShot = it }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestOneFix(appContext: Context): Location? = try {
        val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
        suspendCancellableCoroutine { cont ->
            val request = LocationRequest.Builder(priorityFor(appContext), 1000)
                .setMaxUpdates(1)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedClient.removeLocationUpdates(this)
                    // 일회성 fix 도 실시간 flow 에 반영(진입 직후 빠르게 내 위치 잡히게).
                    // 단 모의 위치는 거부하고 null 반환(위치 조작 차단).
                    val loc = result.lastLocation
                    if (cont.isActive) cont.resume(if (loc != null && acceptLocation(appContext, loc)) loc else null)
                }
            }
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            cont.invokeOnCancellation { fusedClient.removeLocationUpdates(callback) }
        }
    } catch (e: SecurityException) {
        null // 확인 직후 권한이 회수된 경우
    }

    /** 업로드 위치 결과 — 실패면 이유별로 다른 안내(권한 요청 / 위치 켜기 / 다시 시도 / 위치 조작)를 띄운다. */
    sealed interface UploadFix {
        data class Ok(val latitude: Double, val longitude: Double) : UploadFix
        data object NoPermission : UploadFix
        data object LocationOff : UploadFix
        data object Mock : UploadFix
        data object NotFound : UploadFix
    }

    private const val FRESH_MS = 30 * 60_000L    // 실시간 fix 가 없을 때 믿는 최근 위치(30분)
    private const val FALLBACK_MS = 2 * 3600_000L // 새 fix 를 끝내 못 잡았을 때 마지막 수단(2시간)
    private const val FIX_TIMEOUT_MS = 15_000L

    private fun ageMs(location: Location): Long =
        (android.os.SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    @SuppressLint("MissingPermission")
    private fun lastKnownWithin(context: Context, maxAgeMs: Long): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { !isMock(it) && ageMs(it) <= maxAgeMs }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }

    /**
     * 업로드용 위치(2026-10-23 완화) — 예전엔 실시간 fix 가 없으면 고정밀 1회 fix 를 10초 기다리고 실패 = 업로드 실패였다
     * (실내·와이파이 없는 환경·대략적인 위치 사용자는 자주 실패). 이제 순서대로:
     * 실시간 위치 → 30분 내 최근 위치 → 새 fix(대략 정확도, 15초) → 2시간 내 마지막 위치. 모의 위치는 모든 단계에서 거부.
     */
    @SuppressLint("MissingPermission")
    suspend fun locateForUpload(context: Context): UploadFix {
        val appContext = context.applicationContext
        if (!hasPermission(appContext)) return UploadFix.NoPermission
        _location.value?.let { return UploadFix.Ok(it.latitude, it.longitude) }

        val fused = LocationServices.getFusedLocationProviderClient(appContext)
        fun ok(loc: Location): UploadFix =
            if (acceptLocation(appContext, loc)) UploadFix.Ok(loc.latitude, loc.longitude) else UploadFix.Mock

        val last = runCatching { fused.lastLocation.await() }.getOrNull()
        if (last != null && !isMock(last) && ageMs(last) <= FRESH_MS) return ok(last)

        if (!isLocationOn(appContext)) {
            return lastKnownWithin(appContext, FALLBACK_MS)?.let { ok(it) } ?: UploadFix.LocationOff
        }

        val cts = CancellationTokenSource()
        val fresh = try {
            withTimeoutOrNull(FIX_TIMEOUT_MS) {
                runCatching {
                    fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
                }.getOrNull()
            }
        } finally {
            cts.cancel()
        }
        if (fresh != null) return ok(fresh)

        (last?.takeIf { !isMock(it) && ageMs(it) <= FALLBACK_MS } ?: lastKnownWithin(appContext, FALLBACK_MS))
            ?.let { return ok(it) }
        return if (_mockDetected.value || last?.let { isMock(it) } == true) UploadFix.Mock else UploadFix.NotFound
    }

    // 거리 계산은 플랫폼 공용 GeoUtils(Haversine)로 위임 — iOS 공유 가능
    fun distanceBetween(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Float = GeoUtils.distanceBetween(lat1, lng1, lat2, lng2)
}
