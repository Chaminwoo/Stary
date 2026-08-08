package com.chaminwoo.stary.feature.profile

import android.content.Context
import android.location.Geocoder
import com.chaminwoo.stary.R
import com.chaminwoo.stary.core.util.LocalizedNames
import com.chaminwoo.stary.data.repository.FirebasePioneerRepository
import com.chaminwoo.stary.data.staryFirestore
import com.chaminwoo.stary.feature.auth.GoogleAuthHelper
import com.chaminwoo.stary.shared.config.PioneerQuest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 업로드 성공 직후 개척 퀘스트 선점 시도(체크리스트 32).
 *
 * 화면 네비게이션으로 컴포저블 스코프가 죽어도 진행되도록 자체 스코프에서 돈다.
 * 흐름: 역지오코딩(국가 코드) → 활성 대상국(미개척) 확인 → 트랜잭션 선점 → 성공 토스트.
 * 모든 실패는 조용히 무시(업로드 UX 에 영향 없음).
 */
object PioneerClaimHelper {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun attemptClaim(context: Context, lat: Double, lng: Double) {
        if (lat == 0.0 && lng == 0.0) return
        val appContext = context.applicationContext
        val uid = GoogleAuthHelper.currentUserId ?: return
        val name = GoogleAuthHelper.currentUserName ?: ""
        scope.launch {
            try {
                @Suppress("DEPRECATION")
                val code = Geocoder(appContext, Locale.getDefault())
                    .getFromLocation(lat, lng, 1)?.firstOrNull()?.countryCode?.uppercase()
                    ?: return@launch
                // 이번 주 활성 대상국(미개척 나라 중 이번 주 1개)과 일치하는지 확인 — 비콘과 동일 기준.
                val claimedDocs = staryFirestore.collection(PioneerQuest.COLLECTION).get().await()
                val claimed = claimedDocs.documents.map { it.id }.toSet()
                val active = PioneerQuest.activeCountry(System.currentTimeMillis(), claimed)
                if (active?.code != code) return@launch
                val won = FirebasePioneerRepository().claim(code, uid, name)
                if (won) {
                    val title = LocalizedNames.pioneerTitle(appContext, PioneerQuest.titleId(code)) ?: return@launch
                    withContext(Dispatchers.Main) {
                        com.chaminwoo.stary.core.ui.StaryToast.show(
                            appContext.getString(R.string.pioneer_claimed_toast, title)
                        )
                    }
                }
            } catch (_: Exception) {
                // 역지오코딩/네트워크 실패 등 — 조용히 무시
            }
        }
    }
}
