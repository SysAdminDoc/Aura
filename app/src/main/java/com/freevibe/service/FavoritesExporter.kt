package com.freevibe.service

import android.content.Context
import android.net.Uri
import com.freevibe.data.local.FavoriteDao
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.FavoriteIdentity
import com.freevibe.data.model.LocalMediaStatus
import com.freevibe.data.model.MEDIA_TYPE_VIDEO
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.model.normalizeSourceAvailability
import com.freevibe.util.rethrowIfCancelled
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

private const val CURRENT_EXPORT_VERSION = 2
private val FAVORITE_LOCAL_MEDIA_HASH = Regex("^[0-9a-f]{64}$")

@Singleton
class FavoritesExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoriteDao: FavoriteDao,
    private val moshi: Moshi,
) {
    private val fileAdapter = moshi.adapter(FavoritesExportFile::class.java)
    private val listType = Types.newParameterizedType(List::class.java, FavoriteExportItem::class.java)
    private val listAdapter = moshi.adapter<List<FavoriteExportItem>>(listType)

    /** Export all favorites to a JSON file, returns URI */
    suspend fun export(outputUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val favorites = favoriteDao.getAll().first()
            requireWithinTransferLimit(
                "Favorites export",
                favorites.size,
                LibraryTransferContract.MAX_FAVORITES,
            )
            val items = favorites.map { it.toExportItem() }
            val exportFile = FavoritesExportFile(
                version = CURRENT_EXPORT_VERSION,
                exportedAt = System.currentTimeMillis(),
                items = items,
            )
            val json = fileAdapter.indent("  ").toJson(exportFile)
            requireWithinTransferLimit(
                "Favorites export document characters",
                json.length,
                LibraryTransferContract.MAX_FAVORITES_DOCUMENT_CHARS,
            )
            publishStagedDocument(context, outputUri, json.toByteArray(Charsets.UTF_8))
            items.size
        }.onFailure { it.rethrowIfCancelled() }
    }

    /** Import favorites from a JSON file */
    suspend fun import(inputUri: Uri): Result<FavoriteImportOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val json = readJson(inputUri)
            val items = parseItems(json)
            requireWithinTransferLimit(
                "Favorites import",
                items.size,
                LibraryTransferContract.MAX_FAVORITES,
            )

            val seen = favoriteDao.getAll().first()
                .mapTo(mutableSetOf<FavoriteIdentity>()) { it.favoriteIdentity() }
            var skipped = 0
            var failed = 0
            val entities = buildList {
                items.forEach { item ->
                    val entity = item.toValidatedEntity()
                    when {
                        entity == null -> failed++
                        !seen.add(entity.favoriteIdentity()) -> skipped++
                        else -> add(entity)
                    }
                }
            }
            if (entities.isNotEmpty()) {
                favoriteDao.insertAll(entities)
            }
            FavoriteImportOutcome(
                imported = entities.size,
                skipped = skipped,
                failed = failed,
            )
        }.onFailure { it.rethrowIfCancelled() }
    }

    /** Generate export as string (for sharing) */
    suspend fun exportToString(): String = withContext(Dispatchers.IO) {
        val favorites = favoriteDao.getAll().first()
        requireWithinTransferLimit(
            "Favorites export",
            favorites.size,
            LibraryTransferContract.MAX_FAVORITES,
        )
        val items = favorites.map { it.toExportItem() }
        val json = fileAdapter.indent("  ").toJson(
            FavoritesExportFile(
                version = CURRENT_EXPORT_VERSION,
                exportedAt = System.currentTimeMillis(),
                items = items,
            )
        )
        requireWithinTransferLimit(
            "Favorites export document characters",
            json.length,
            LibraryTransferContract.MAX_FAVORITES_DOCUMENT_CHARS,
        )
        json
    }

    private fun readJson(inputUri: Uri): String {
        val builder = StringBuilder()
        context.contentResolver.openInputStream(inputUri)?.use { input ->
            BufferedReader(InputStreamReader(input)).use { reader ->
                val buffer = CharArray(4096)
                while (true) {
                    val read = reader.read(buffer)
                    if (read == -1) break
                    builder.append(buffer, 0, read)
                    if (builder.length > LibraryTransferContract.MAX_FAVORITES_DOCUMENT_CHARS) {
                        throw IllegalStateException("Favorites file is too large to import")
                    }
                }
            }
        } ?: throw IllegalStateException("Failed to read file")
        return builder.toString()
    }

    private fun parseItems(json: String): List<FavoriteExportItem> {
        try {
            fileAdapter.fromJson(json)?.let { file ->
                if (file.version > CURRENT_EXPORT_VERSION) {
                    throw IllegalStateException("Favorites file version ${file.version} is not supported yet")
                }
                return file.items
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (_: Exception) {
            // Fall through to legacy list parsing below.
        }

        return try {
            listAdapter.fromJson(json) ?: throw IllegalStateException("Invalid JSON format")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw IllegalStateException("Invalid favorites file: ${e.message}")
        }
    }

}

// ── Export data model (clean JSON without Room annotations) ───────

@JsonClass(generateAdapter = true)
data class FavoritesExportFile(
    val version: Int,
    val exportedAt: Long,
    val items: List<FavoriteExportItem>,
)

data class FavoriteImportOutcome(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
)

@JsonClass(generateAdapter = true)
data class FavoriteExportItem(
    val id: String,
    val source: String,
    val type: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val name: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val duration: Double = 0.0,
    val tags: String? = null,
    val colors: String? = null,
    val category: String? = null,
    val uploaderName: String? = null,
    val sourcePageUrl: String? = null,
    val license: String? = null,
    val fileSize: Long? = null,
    val fileType: String? = null,
    val views: Long? = null,
    val favoritesCount: Long? = null,
    val addedAt: Long? = null,
    val sourceAvailability: String? = null,
    val sourceAvailabilityReason: String? = null,
    val localMedia: Boolean = false,
    val localMediaSha256: String = "",
)

private fun FavoriteEntity.toExportItem(): FavoriteExportItem {
    val localMedia = source.equals(ContentSource.LOCAL.name, true) ||
        localMediaStatus != LocalMediaStatus.AVAILABLE ||
        isNonPortableLocator(fullUrl.takeIf(String::isNotBlank) ?: offlinePath)
    val portableHash = localMediaSha256.trim().lowercase(java.util.Locale.ROOT)
        .takeIf(FAVORITE_LOCAL_MEDIA_HASH::matches)
        .orEmpty()
    return FavoriteExportItem(
        id = if (localMedia) portableLocalMediaId(source, id) else id,
        source = source,
        type = type,
        thumbnailUrl = thumbnailUrl.takeUnless { localMedia }.orEmpty(),
        fullUrl = fullUrl.takeUnless { localMedia }.orEmpty(),
        name = name,
        width = width,
        height = height,
        duration = duration,
        tags = tags,
        colors = colors,
        category = category,
        uploaderName = uploaderName,
        sourcePageUrl = sourcePageUrl?.takeIf { !isNonPortableLocator(it) },
        license = license,
        fileSize = fileSize,
        fileType = fileType,
        views = views,
        favoritesCount = favoritesCount,
        addedAt = addedAt,
        sourceAvailability = sourceAvailability,
        sourceAvailabilityReason = sourceAvailabilityReason,
        localMedia = localMedia,
        localMediaSha256 = portableHash,
    )
}

internal fun isAllowedImportedFavoriteUrl(
    url: String,
    allowBlank: Boolean = false,
): Boolean = isAllowedImportedHttpsUrl(url, allowBlank)

internal fun FavoriteExportItem.toValidatedEntity(): FavoriteEntity? {
    val normalizedId = normalizeImportedText(id)
    val normalizedSource = normalizeImportedContentSource(source)
    val normalizedType = type.trim().uppercase(java.util.Locale.ROOT)

    if (normalizedId.isBlank()) return null
    if (localMedia && !isSafePortableLocalMediaId(normalizedId)) return null
    if (normalizedSource == null) return null
    if (normalizedType !in IMPORTED_FAVORITE_TYPES) return null
    val isVideo = normalizedType == MEDIA_TYPE_VIDEO
    // A saved video is always a provider's, so it never travels as a file to pick again.
    if (isVideo && localMedia) return null

    val normalizedName = normalizeImportedText(name)
    val normalizedThumbnailUrl = normalizeImportedHttpsUrl(
        thumbnailUrl,
        allowBlank = localMedia || normalizedType == "SOUND" || isVideo,
    ) ?: return null
    val normalizedFullUrl = when {
        isVideo -> normalizeImportedVideoFavoriteUrl(normalizedSource, normalizedId, fullUrl)
        else -> normalizeImportedHttpsUrl(fullUrl, allowBlank = localMedia)
    } ?: return null
    val normalizedSourcePageUrl =
        normalizeImportedHttpsUrl(sourcePageUrl, allowBlank = true)?.takeIf { it.isNotBlank() }
    val normalizedLicense = normalizeImportedOptionalText(license)
    val normalizedSourceAvailability = normalizeSourceAvailability(sourceAvailability)
    val normalizedSourceAvailabilityReason =
        normalizeImportedOptionalText(sourceAvailabilityReason)

    if (!localMedia && normalizedType == "WALLPAPER" && (normalizedThumbnailUrl.isBlank() || normalizedFullUrl.isBlank())) {
        return null
    }
    if (!localMedia && normalizedType == "SOUND" && normalizedFullUrl.isBlank()) {
        return null
    }
    val normalizedHash = localMediaSha256.trim().lowercase(java.util.Locale.ROOT)
    if (normalizedHash.isNotBlank() && !FAVORITE_LOCAL_MEDIA_HASH.matches(normalizedHash)) return null
    return FavoriteEntity(
        id = normalizedId,
        source = normalizedSource,
        type = normalizedType,
        thumbnailUrl = normalizedThumbnailUrl.takeUnless { localMedia }.orEmpty(),
        fullUrl = normalizedFullUrl.takeUnless { localMedia }.orEmpty(),
        name = normalizedName,
        width = width.coerceAtLeast(0),
        height = height.coerceAtLeast(0),
        duration = duration.coerceAtLeast(0.0),
        tags = normalizeImportedOptionalText(tags),
        colors = normalizeImportedOptionalText(colors),
        category = normalizeImportedOptionalText(category),
        uploaderName = normalizeImportedOptionalText(uploaderName),
        sourcePageUrl = normalizedSourcePageUrl,
        license = normalizedLicense,
        fileSize = fileSize?.coerceAtLeast(0L),
        fileType = normalizeImportedOptionalText(fileType),
        views = views?.coerceAtLeast(0L),
        favoritesCount = favoritesCount?.coerceAtLeast(0L),
        addedAt = (addedAt ?: System.currentTimeMillis()).coerceAtLeast(0L),
        sourceAvailability = normalizedSourceAvailability,
        sourceAvailabilityReason = normalizedSourceAvailabilityReason,
        localMediaStatus = if (localMedia) LocalMediaStatus.MISSING else LocalMediaStatus.AVAILABLE,
        localMediaReason = if (localMedia) "Choose the original file or a replacement" else null,
        localMediaSha256 = normalizedHash,
    )
}
