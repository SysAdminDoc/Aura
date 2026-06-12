package com.freevibe.data.local

import androidx.room.withTransaction
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.PROVIDER_CACHE_TTL_DEFAULT_MS
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperCacheEntity
import com.freevibe.data.model.longestProviderRequestCacheTtlMs
import com.freevibe.data.model.providerNetworkPoliciesBySource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperCacheManager @Inject constructor(
    private val cacheDao: WallpaperCacheDao,
    private val db: FreeVibeDatabase,
) {
    companion object {
        private const val MAX_ENTRIES_PER_KEY = 180
        private const val MAX_TOTAL_ENTRIES = 1800
    }

    /** Get cached wallpapers if fresh enough */
    suspend fun getCached(cacheKey: String, source: ContentSource): List<Wallpaper>? {
        val timestamp = cacheDao.getCacheTimestamp(cacheKey) ?: return null
        val ttl = getTtl(source)
        if (System.currentTimeMillis() - timestamp > ttl) {
            cacheDao.evictByKey(cacheKey)
            return null
        }
        return cacheDao.getByCacheKey(cacheKey).map { it.toWallpaper() }
    }

    /** Get cached wallpapers even if expired (offline fallback) */
    suspend fun getStaleCached(cacheKey: String): List<Wallpaper>? {
        val entries = cacheDao.getByCacheKey(cacheKey)
        return if (entries.isNotEmpty()) entries.map { it.toWallpaper() } else null
    }

    /** Get the latest cached wallpaper for each raw-id/source pair (across all cache keys). */
    suspend fun getByIds(ids: List<String>): List<Wallpaper> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(500).flatMap { chunk ->
            cacheDao.getByIds(chunk).map { it.toWallpaper() }
        }
    }

    /** Cache wallpapers with key */
    suspend fun cache(cacheKey: String, wallpapers: List<Wallpaper>) {
        db.withTransaction {
            cacheDao.evictByKey(cacheKey)
            val now = System.currentTimeMillis()
            cacheDao.insertAll(wallpapers.map { it.toCacheEntity(cacheKey, now) })
            cacheDao.pruneCacheKey(cacheKey, MAX_ENTRIES_PER_KEY)
            cacheDao.pruneToMaxEntries(MAX_TOTAL_ENTRIES)
        }
    }

    /** Remove all expired entries (uses longest TTL — per-source freshness is checked in getCached) */
    suspend fun evictExpired() {
        val oldest = System.currentTimeMillis() - longestProviderRequestCacheTtlMs()
        cacheDao.evictOlderThan(oldest)
        cacheDao.pruneToMaxEntries(MAX_TOTAL_ENTRIES)
    }

    /** Full cache clear */
    suspend fun clearAll() = cacheDao.clearAll()

    suspend fun countEntries(): Int = cacheDao.countEntries()

    private fun getTtl(source: ContentSource): Long =
        providerNetworkPoliciesBySource[source]?.requestCacheTtlMs ?: PROVIDER_CACHE_TTL_DEFAULT_MS

    private fun WallpaperCacheEntity.toWallpaper() = Wallpaper(
        id = id,
        source = try { ContentSource.valueOf(source) } catch (_: Exception) { ContentSource.WALLHAVEN },
        thumbnailUrl = thumbnailUrl,
        fullUrl = fullUrl,
        width = width,
        height = height,
        category = category,
        tags = if (tags.isBlank()) emptyList() else tags.split(" ||| "),
        colors = if (colors.isBlank()) emptyList() else colors.split(" ||| "),
        fileSize = fileSize,
        fileType = fileType,
        uploaderName = uploaderName,
        sourcePageUrl = sourcePageUrl,
        views = views,
        favorites = favorites,
    )

    private fun Wallpaper.toCacheEntity(cacheKey: String, timestamp: Long) = WallpaperCacheEntity(
        id = id,
        source = source.name,
        thumbnailUrl = thumbnailUrl,
        fullUrl = fullUrl,
        width = width,
        height = height,
        category = category,
        tags = tags.joinToString(" ||| "),
        colors = colors.joinToString(" ||| "),
        fileSize = fileSize,
        fileType = fileType,
        uploaderName = uploaderName,
        sourcePageUrl = sourcePageUrl,
        views = views,
        favorites = favorites,
        cacheKey = cacheKey,
        cachedAt = timestamp,
    )
}
