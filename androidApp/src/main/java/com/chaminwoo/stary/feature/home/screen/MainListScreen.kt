package com.chaminwoo.stary.feature.home.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.chaminwoo.stary.data.repository.FirebaseFriendRepository
import com.chaminwoo.stary.data.repository.FirebaseViewedRepository
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.feature.diary.DiaryViewModel
import com.chaminwoo.stary.feature.map.screen.DiaryMap
import com.chaminwoo.stary.shared.config.StaryConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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

    // --- 필터 (미조회만 / 친구만) ---
    val userId = GoogleAuthHelper.currentUserId
    var unviewedOnly by remember { mutableStateOf(false) }
    var friendsOnly by remember { mutableStateOf(false) }
    val viewedIds by remember(userId) {
        if (userId != null) FirebaseViewedRepository().observeViewedIds(userId)
        else flowOf(emptySet())
    }.collectAsState(initial = emptySet())
    val friendIds by remember(userId) {
        if (userId != null) FirebaseFriendRepository().observeFriends(userId)
            .map { friends -> friends.map { it.userId }.toSet() }
        else flowOf(emptySet<String>())
    }.collectAsState(initial = emptySet())

    val filteredDiaries = remember(diaries, unviewedOnly, friendsOnly, viewedIds, friendIds) {
        diaries.filter { diary ->
            (!unviewedOnly || diary.id !in viewedIds) &&
                (!friendsOnly || diary.userId in friendIds)
        }
    }

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
            diaries = filteredDiaries,
            currentLatLng = currentLatLng,
            onDiaryClick = onItemClick,
            onCreateClick = onCreateClick,
        )

        // 필터 칩 (로그인한 경우에만 — 기록/친구는 계정 기반)
        if (userId != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0xCC1A1A1A),
                    labelColor = Color(0xFFF0F0F0),
                    selectedContainerColor = Color(0xFF6EE7B7),
                    selectedLabelColor = Color(0xFF0D0D0D),
                )
                FilterChip(
                    selected = unviewedOnly,
                    onClick = { unviewedOnly = !unviewedOnly },
                    label = { Text("미조회만", fontSize = 13.sp) },
                    colors = chipColors,
                )
                FilterChip(
                    selected = friendsOnly,
                    onClick = { friendsOnly = !friendsOnly },
                    label = { Text("친구만", fontSize = 13.sp) },
                    colors = chipColors,
                )
            }
        }
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
