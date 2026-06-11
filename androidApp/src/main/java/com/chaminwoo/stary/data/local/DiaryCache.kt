package com.chaminwoo.stary.data.local

import com.chaminwoo.stary.core.model.Diary

object DiaryCache {
    private val cache = mutableMapOf<String, Diary>()

    fun put(diary: Diary) { cache[diary.id] = diary }
    fun get(id: String): Diary? = cache[id]
    fun putAll(diaries: List<Diary>) { diaries.forEach { cache[it.id] = it } }
}
