package com.chaminwoo.stary.core.util

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val VmCrashGuard = CoroutineExceptionHandler { _, e -> Log.e("StaryVM", "uncaught in viewModelScope", e) }

/**
 * 뷰모델 코루틴 안전망 — 저장소가 미처 못 잡은 예외로 앱이 꺼지지 않게 기록만 남긴다.
 * 실패 안내는 각 저장소의 성공/실패 반환값이 맡는다(이건 마지막 그물이다).
 */
fun ViewModel.launchSafely(block: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch(VmCrashGuard, block = block)
