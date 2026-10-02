package com.example.data.cache

import java.util.concurrent.ConcurrentHashMap

object SmartTranslationCache {
    private val cache = ConcurrentHashMap<String, CachedTranslation>()
    private const val MAX_ENTRIES = 500
    private const val EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24 hours

    data class CachedTranslation(
        val translation: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun get(key: String): String? {
        val entry = cache[key.trim().lowercase()] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > EXPIRATION_MS) {
            cache.remove(key.trim().lowercase())
            return null
        }
        return entry.translation
    }

    fun put(key: String, translation: String) {
        if (cache.size >= MAX_ENTRIES) {
            autoPrune()
        }
        cache[key.trim().lowercase()] = CachedTranslation(translation)
    }

    fun autoPrune() {
        val now = System.currentTimeMillis()
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.timestamp > EXPIRATION_MS) {
                iterator.remove()
            }
        }
        if (cache.size > MAX_ENTRIES) {
            val sorted = cache.entries.sortedBy { it.value.timestamp }
            val toRemove = sorted.take(cache.size - MAX_ENTRIES / 2)
            toRemove.forEach { cache.remove(it.key) }
        }
    }

    fun onLowMemory() {
        cache.clear()
    }

    fun clear() {
        cache.clear()
    }
}
