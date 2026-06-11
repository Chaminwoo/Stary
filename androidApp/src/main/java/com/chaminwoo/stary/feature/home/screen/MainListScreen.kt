package com.chaminwoo.stary.feature.home.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaminwoo.stary.core.geo.LatLng
import com.chaminwoo.stary.core.util.LocationHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import com.chaminwoo.stary.feature.map.screen.DiaryMap
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.delay

@Composable
fun MainListScreen(
    onItemClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
    diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory())
) {
    val context = LocalContext.current
    val diaries by diaryViewModel.diaries.collectAsState()
    val step = 0.0001
    val focusRequester = remember { FocusRequester() }

    var currentLatLng by remember {
        mutableStateOf(
            LocationHelper.getCurrentLatLng()
                ?: LatLng(StaryConfig.DEFAULT_LAT, StaryConfig.DEFAULT_LNG)
        )
    }
    var isFollowing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "위치 권한이 필요해요", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(currentLatLng) {
        delay(600)
        diaryViewModel.prefetchNearby(diaries, currentLatLng.latitude, currentLatLng.longitude)
    }

    LaunchedEffect(Unit) {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION

        if (
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(permission)
            return@LaunchedEffect
        }

        LocationHelper.startContinuousUpdates(context)

        val latLng = LocationHelper.getCurrentLatLng()
            ?: LocationHelper.getCurrentLocation(context)?.let {
                LatLng(it.latitude, it.longitude)
            }

        latLng?.let { currentLatLng = it }

        focusRequester.requestFocus()
        // 초기 카메라(현재 위치 중심 또는 LocationHelper.cameraTarget 경계)는 DiaryMap 이 처리.
    }

    fun moveLocation(latDelta: Double, lngDelta: Double) {
        val newLatLng = LatLng(currentLatLng.latitude + latDelta, currentLatLng.longitude + lngDelta)
        currentLatLng = newLatLng
        LocationHelper.setCurrentLocation(newLatLng)
        // follow 카메라 추적은 DiaryMap 이 currentLatLng/isFollowing 변화에 반응해 처리.
    }


    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.W -> { moveLocation(step, 0.0); true }
                        Key.S -> { moveLocation(-step, 0.0); true }
                        Key.A -> { moveLocation(0.0, -step); true }
                        Key.D -> { moveLocation(0.0, step); true }
                        else -> false
                    }
                } else false
            }
    ) {
        DiaryMap(
            diaries = diaries,
            currentLatLng = currentLatLng,
            isFollowing = isFollowing,
            onGestureDetected = { isFollowing = false },
            onRefollowClick = { isFollowing = true },
            onItemClick = onItemClick,
            onCreateClick = onCreateClick,
        )
    }
}

@Composable
fun DpadButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC000000)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 18.sp, color = Color.White)
    }
}
