package com.chaminwoo.stary.data

import com.chaminwoo.stary.shared.config.StaryConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * 앱 전역 Firestore 인스턴스.
 *
 * momentdiary-f26c8 프로젝트의 DB 가 (default) 가 아니라 named DB(`stary-db`)로
 * 생성되어 있어, 기본 `Firebase.firestore` 대신 반드시 이 인스턴스를 사용해야 한다.
 * (기본 인스턴스를 쓰면 NOT_FOUND: database (default) does not exist 로 서버 쓰기가 영원히 대기)
 */
val staryFirestore: FirebaseFirestore
    get() = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), StaryConfig.FIRESTORE_DB_ID)
