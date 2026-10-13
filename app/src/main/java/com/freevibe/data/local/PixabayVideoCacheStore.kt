package com.freevibe.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persistence boundary for the video feed cache; UI code must not own SharedPreferences.
 *
 * Every query, page and feed gets its own key, and SharedPreferences rewrites the whole file on
 * each write and holds it all in memory, so writes prune the oldest entries to stay inside
 * [MAX_CACHE_ENTRIES] and [MAX_CACHE_CHARS]. All access runs on the IO dispatcher.
 */
class PixabayVideoCacheStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    suspend fun readString(key: String): String? = withContext(Dispatchers.IO) {
        preferences.getString(key, null)
    }

    suspend fun writeString(key: String, value: String) = withContext(Dispatchers.IO) {
        val all = preferences.all
        val entries = all.mapNotNull { (entryKey, entryValue) ->
            if (entryKey == key || entryKey.startsWith(WRITTEN_AT_PREFIX) || entryValue !is String) {
                null
            } else {
                CachedEntry(entryKey, entryValue.length, all[WRITTEN_AT_PREFIX + entryKey] as? Long ?: 0L)
            }
        } + CachedEntry(key, value.length, Long.MAX_VALUE)
        val editor = preferences.edit()
            .putString(key, value)
            .putLong(WRITTEN_AT_PREFIX + key, System.currentTimeMillis())
        selectCacheEvictions(entries, keep = key, MAX_CACHE_ENTRIES, MAX_CACHE_CHARS).forEach { evicted ->
            editor.remove(evicted).remove(WRITTEN_AT_PREFIX + evicted)
        }
        editor.apply()
    }

    suspend fun readLong(key: String, defaultValue: Long = 0L): Long = withContext(Dispatchers.IO) {
        preferences.getLong(key, defaultValue)
    }

    suspend fun writeLong(key: String, value: Long) = withContext(Dispatchers.IO) {
        preferences.edit().putLong(key, value).apply()
    }

    internal data class CachedEntry(val key: String, val chars: Int, val writtenAtMs: Long)

    internal companion object {
        const val PREFS_NAME = "freevibe_pixabay_video_cache"
        const val MAX_CACHE_ENTRIES = 32
        const val MAX_CACHE_CHARS = 512 * 1024
        private const val WRITTEN_AT_PREFIX = "written_at:"

        /**
         * Oldest first (entries cached before write times were recorded count as oldest) until
         * both limits hold. [keep], the entry being written, is never evicted.
         */
        fun selectCacheEvictions(
            entries: List<CachedEntry>,
            keep: String,
            maxEntries: Int,
            maxChars: Int,
        ): List<String> {
            var count = entries.size
            var chars = entries.sumOf { it.chars.toLong() }
            val evicted = mutableListOf<String>()
            for (entry in entries.sortedBy { it.writtenAtMs }) {
                if (count <= maxEntries && chars <= maxChars) break
                if (entry.key == keep) continue
                evicted += entry.key
                count--
                chars -= entry.chars
            }
            return evicted
        }
    }
}
