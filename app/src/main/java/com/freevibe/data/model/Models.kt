package com.freevibe.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.util.Locale

// -- Unified content types --

enum class ContentSource { WALLHAVEN, PICSUM, BING, WIKIMEDIA, INTERNET_ARCHIVE, REDDIT, NASA, FREESOUND, JAMENDO, AUDIUS, CCMIXTER, LOCAL, YOUTUBE, PEXELS, PIXABAY, KLIPY, SOUNDCLOUD, COMMUNITY, BUNDLED, AI_GENERATED, OPEN_METEO, LEMMY, TIKTOK }
enum class ContentType { WALLPAPER, LIVE_WALLPAPER, RINGTONE, NOTIFICATION, ALARM }
enum class WallpaperTarget { HOME, LOCK, BOTH }

const val WALLPAPER_SOURCE_LOCAL_FOLDER = "local_folder"

const val SOURCE_AVAILABILITY_AVAILABLE = "AVAILABLE"
const val SOURCE_AVAILABILITY_UNAVAILABLE = "SOURCE_UNAVAILABLE"

fun normalizeSourceAvailability(value: String?): String =
    if (value.equals(SOURCE_AVAILABILITY_UNAVAILABLE, ignoreCase = true)) {
        SOURCE_AVAILABILITY_UNAVAILABLE
    } else {
        SOURCE_AVAILABILITY_AVAILABLE
    }

fun isSourceUnavailable(value: String?): Boolean =
    normalizeSourceAvailability(value) == SOURCE_AVAILABILITY_UNAVAILABLE

// -- Wallpaper --

@Immutable
@JsonClass(generateAdapter = true)
data class Wallpaper(
    val id: String,
    val source: ContentSource,
    val thumbnailUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
    val category: String = "",
    val tags: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val fileSize: Long = 0,
    val fileType: String = "",
    val sourcePageUrl: String = "",
    val license: String = "",
    val uploaderName: String = "",
    val views: Int = 0,
    val favorites: Int = 0,
    val sourceAvailability: String = SOURCE_AVAILABILITY_AVAILABLE,
    val sourceAvailabilityReason: String = "",
    val communityUploaderId: String = "",
    val isAiGenerated: Boolean? = null,
    val contentHash: String = "",
    /** The web address behind a favorite whose [fullUrl] points at its offline copy. Empty otherwise. */
    val remoteFullUrl: String = "",
)

/**
 * The address to save or hand on. [Wallpaper.fullUrl] can be a favorite's offline copy, which is
 * gone once the favorite is removed.
 */
val Wallpaper.webUrl: String get() = remoteFullUrl.ifBlank { fullUrl }

// -- Sound --

@Immutable
@JsonClass(generateAdapter = true)
data class Sound(
    val id: String,
    val source: ContentSource,
    val name: String,
    val description: String = "",
    val previewUrl: String,
    val downloadUrl: String,
    val duration: Double = 0.0,
    val sampleRate: Int = 0,
    val fileType: String = "",
    val fileSize: Long = 0,
    val tags: List<String> = emptyList(),
    val license: String = "",
    val uploaderName: String = "",
    val sourcePageUrl: String = "",
    val sourceAvailability: String = SOURCE_AVAILABILITY_AVAILABLE,
    val sourceAvailabilityReason: String = "",
    val communityUploaderId: String = "",
    val isAiGenerated: Boolean? = null,
)

@Immutable
data class FavoriteIdentity(
    val id: String,
    val source: String,
    val type: String,
) {
    fun stableKey(): String = "$type::$source::$id"
}

// -- Favorites (Room entity) --

@Immutable
@Entity(
    tableName = "favorites",
    primaryKeys = ["id", "source", "type"],
    indices = [Index("type"), Index("addedAt"), Index("type", "addedAt")],
)
data class FavoriteEntity(
    val id: String,
    val source: String,
    val type: String,           // WALLPAPER, SOUND or VIDEO
    val thumbnailUrl: String,
    val fullUrl: String,
    val name: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val duration: Double = 0.0,
    val addedAt: Long = System.currentTimeMillis(),
    val offlinePath: String = "",   // #3: local file path for offline access
    val tags: String? = null,               // comma-separated
    val colors: String? = null,             // comma-separated
    val category: String? = null,
    val uploaderName: String? = null,
    val sourcePageUrl: String? = null,
    val license: String? = null,
    val fileSize: Long? = null,
    val fileType: String? = null,
    val views: Long? = null,
    val favoritesCount: Long? = null,
    val sourceAvailability: String = SOURCE_AVAILABILITY_AVAILABLE,
    val sourceAvailabilityReason: String? = null,
    /** Health of [offlinePath], kept separate from the remote provider state. */
    val localMediaStatus: String = LocalMediaStatus.AVAILABLE,
    val localMediaReason: String? = null,
    val localMediaSha256: String = "",
)

fun Wallpaper.favoriteIdentity() = FavoriteIdentity(
    id = id,
    source = source.name,
    type = "WALLPAPER",
)

fun Sound.favoriteIdentity() = FavoriteIdentity(
    id = id,
    source = source.name,
    type = "SOUND",
)

fun FavoriteEntity.favoriteIdentity() = FavoriteIdentity(
    id = id,
    source = source.uppercase(Locale.ROOT),
    type = type.uppercase(Locale.ROOT),
)

fun Wallpaper.stableKey(): String = favoriteIdentity().stableKey()

fun Sound.stableKey(): String = favoriteIdentity().stableKey()

fun FavoriteEntity.stableKey(): String = favoriteIdentity().stableKey()

// -- Download history (Room entity) --

@Immutable
@Entity(tableName = "downloads", indices = [Index("type"), Index("downloadedAt")])
data class DownloadEntity(
    @PrimaryKey val id: String,
    val source: String,
    val type: String,
    val localPath: String,
    val name: String = "",
    val downloadedAt: Long = System.currentTimeMillis(),
    val sourceAvailability: String = SOURCE_AVAILABILITY_AVAILABLE,
    val sourceAvailabilityReason: String? = null,
    /** Health of [localPath], kept separate from the remote provider state. */
    val localMediaStatus: String = LocalMediaStatus.AVAILABLE,
    val localMediaReason: String? = null,
    /** Source page or download URL retained as provenance, never used as the local locator. */
    val provenanceUrl: String = "",
    /** Digest and technical details for the untouched bytes at [localPath]. */
    val originalSha256: String = "",
    val originalMimeType: String = "",
    val originalCodec: String = "",
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val originalDurationMs: Long = 0,
    val originalSizeBytes: Long = 0,
    val originalHdr: Boolean = false,
    /** Optional device-compatible working copy. The original fields never point here. */
    val optimizedPath: String = "",
    val optimizedSha256: String = "",
    val optimizedMimeType: String = "",
    val optimizedCodec: String = "",
    val optimizedWidth: Int = 0,
    val optimizedHeight: Int = 0,
    val optimizedDurationMs: Long = 0,
    val optimizedSizeBytes: Long = 0,
    val optimizedHdr: Boolean = false,
    val optimizationKey: String = "",
    val optimizationReason: String = "",
    val optimizedAt: Long = 0,
)

fun Wallpaper.isSourceUnavailable(): Boolean = isSourceUnavailable(sourceAvailability)

fun Sound.isSourceUnavailable(): Boolean = isSourceUnavailable(sourceAvailability)

fun FavoriteEntity.isSourceUnavailable(): Boolean = isSourceUnavailable(sourceAvailability)

fun DownloadEntity.isSourceUnavailable(): Boolean = isSourceUnavailable(sourceAvailability)

// -- Search results wrapper --

data class SearchResult<T>(
    val items: List<T>,
    val totalCount: Int,
    val currentPage: Int,
    val hasMore: Boolean,
)

// -- Search history (Room entity) --

@Immutable
@Entity(
    tableName = "search_history",
    primaryKeys = ["query", "type"],
)
data class SearchHistoryEntity(
    val query: String,
    val type: String,           // WALLPAPER or SOUND
    val timestamp: Long = System.currentTimeMillis(),
)

// -- Cached content (Room entity) --

@Entity(
    tableName = "wallpaper_cache",
    primaryKeys = ["id", "source", "cacheKey"],
    indices = [Index("cacheKey")],
)
data class WallpaperCacheEntity(
    val id: String,
    val source: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
    val category: String = "",
    val tags: String = "",
    val fileSize: Long = 0,
    val fileType: String = "",
    val uploaderName: String = "",
    val colors: String = "",
    val sourcePageUrl: String = "",
    val views: Int = 0,
    val favorites: Int = 0,
    val cacheKey: String = "",       // e.g. "wallhaven_1", "search_nature_1"
    val cachedAt: Long = System.currentTimeMillis(),
)

// -- Wallpaper history (#11) --

@Immutable
@Entity(tableName = "wallpaper_history", indices = [Index("appliedAt")])
data class WallpaperHistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0,
    val wallpaperId: String,
    val source: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val width: Int = 0,
    val height: Int = 0,
    val target: String = "BOTH",       // HOME, LOCK, BOTH
    val appliedAt: Long = System.currentTimeMillis(),
)

// -- Wallpaper collections --

@Immutable
@Entity(tableName = "wallpaper_collections")
data class WallpaperCollectionEntity(
    @PrimaryKey(autoGenerate = true) val collectionId: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "wallpaper_collection_items",
    primaryKeys = ["collectionId", "wallpaperId", "source"],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = WallpaperCollectionEntity::class,
            parentColumns = ["collectionId"],
            childColumns = ["collectionId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
    indices = [androidx.room.Index("collectionId")],
)
@Immutable
data class WallpaperCollectionItemEntity(
    val collectionId: Long,
    val wallpaperId: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val source: String,
    val width: Int = 0,
    val height: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
)

fun WallpaperCollectionItemEntity.stableKey(): String =
    "$collectionId::${source.uppercase(Locale.ROOT)}::$wallpaperId"

// -- Dual wallpaper pair --

@Immutable
data class WallpaperPair(
    val home: Wallpaper,
    val lock: Wallpaper,
    val name: String = "",
)
