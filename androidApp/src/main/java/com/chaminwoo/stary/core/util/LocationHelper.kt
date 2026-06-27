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

    private var continuousCallback: LocationCallback? = null

    fun setCurrentLocation(newLatLng: LatLng) {
        _location.value = newLatLng
        manualOverride = true
    }

    fun getCurrentLatLng(): LatLng? = _location.value

    @SuppressLint("MissingPermission")
    fun startContinuousUpdates(context: Context) {
        if (continuousCallback != null) return
        val permission = android.Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) return

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .build()

        continuousCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!manualOverride) {
                    result.lastLocation?.let { _location.value = LatLng(it.latitude, it.longitude) }
                }
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
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            suspendCancellableCoroutine { cont ->
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                    .setMaxUpdates(1)
                    .build()
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        fusedClient.removeLocationUpdates(this)
                        // 일회성 fix 도 실시간 flow 에 반영(진입 직후 빠르게 내 위치 잡히게).
                        if (!manualOverride) {
                            result.lastLocation?.let { _location.value = LatLng(it.latitude, it.longitude) }
                        }
                        cont.resume(result.lastLocation)
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
        return manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }

    // 거리 계산은 플랫폼 공용 GeoUtils(Haversine)로 위임 — iOS 공유 가능
    fun distanceBetween(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Float = GeoUtils.distanceBetween(lat1, lng1, lat2, lng2)
}
