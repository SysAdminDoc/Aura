package com.freevibe.service

import android.content.Context
import android.net.Uri
import com.freevibe.data.local.FavoriteDao
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.util.rethrowIfCancelled
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.model.normalizeSourceAvailability
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

private const val CURRENT_EXPORT_VERSION = 1
private const val MAX_IMPORT_ITEMS = 5000
private const val MAX_IMPORT_CHARS = 2_000_000

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
            val items = favorites.map { it.toExportItem() }
            val exportFile = FavoritesExportFile(
                version = CURRENT_EXPORT_VERSION,
                exportedAt = System.currentTimeMillis(),
                items = items,
            )
            val json = fileAdapter.indent("  ").toJson(exportFile)
            context.contentResolver.openOutputStream(outputUri)?.use { out ->
                out.write(json.toByteArray())
            } ?: throw IllegalStateException("Failed to open output stream")
            items.size
        }.onFailure { it.rethrowIfCancelled() }
    }

    /** Import favorites from a JSON file */
    suspend fun import(inputUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val json = readJson(inputUri)
            val items = parseItems(json)

            if (items.size > MAX_IMPORT_ITEMS) {
                throw IllegalStateException("Too many favorites (${items.size}). Maximum is $MAX_IMPORT_ITEMS")
            }

            val entities = items
                .mapNotNull { it.toValidatedEntity() }
                .distinctBy { it.favoriteIdentity() }
            if (entities.isEmpty()) {
                throw IllegalStateException("No valid favorites found in file")
            }

            favoriteDao.insertAll(entities)
            entities.size
        }.onFailure { it.rethrowIfCancelled() }
    }

    /** Generate export as string (for sharing) */
    suspend fun exportToString(): String = withContext(Dispatchers.IO) {
        val favorites = favoriteDao.getAll().first()
        val items = favorites.map { it.toExportItem() }
        fileAdapter.indent("  ").toJson(
            FavoritesExportFile(
                version = CURRENT_EXPORT_VERSION,
                exportedAt = System.currentTimeMillis(),
                items = items,
            )
        )
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
                    if (builder.length > MAX_IMPORT_CHARS) {
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
)

private fun FavoriteEntity.toExportItem() = FavoriteExportItem(
    id = id, source = source, type = type, thumbnailUrl = thumbnailUrl,
    fullUrl = fullUrl, name = name, width = width, height = height, duration = duration,
    tags = tags, colors = colors, category = category, uploaderName = uploaderName,
    sourcePageUrl = sourcePageUrl, license = license, fileSize = fileSize, fileType = fileType,
    views = views, favoritesCount = favoritesCount, addedAt = addedAt,
    sourceAvailability = sourceAvailability,
    sourceAvailabilityReason = sourceAvailabilityReason,
)

internal fun isAllowedImportedFavoriteUrl(
    url: String,
    allowBlank: Boolean = false,
): Boolean = isAllowedImportedHttpsUrl(url, allowBlank)

internal fun FavoriteExportItem.toValidatedEntity(): FavoriteEntity? {
    val normalizedId = normalizeImportedText(id)
    val normalizedSource = normalizeImportedContentSource(source)
    val normalizedType = type.trim().uppercase(java.util.Locale.ROOT)

    if (normalizedId.isBlank()) return null
    if (normalizedSource == null) return null
    if (normalizedType !in setOf("WALLPAPER", "SOUND")) return null

    val normalizedName = normalizeImportedText(name)
    val normalizedThumbnailUrl = normalizeImportedHttpsUrl(
        thumbnailUrl,
        allowBlank = normalizedType == "SOUND",
    ) ?: return null
    val normalizedFullUrl = normalizeImportedHttpsUrl(fullUrl) ?: return null
    val normalizedSourcePageUrl =
        normalizeImportedHttpsUrl(sourcePageUrl, allowBlank = true)?.takeIf { it.isNotBlank() }
    val normalizedLicense = normalizeImportedOptionalText(license)
    val normalizedSourceAvailability = normalizeSourceAvailability(sourceAvailability)
    val normalizedSourceAvailabilityReason =
        normalizeImportedOptionalText(sourceAvailabilityReason)

    if (normalizedType == "WALLPAPER" && (normalizedThumbnailUrl.isBlank() || normalizedFullUrl.isBlank())) {
        return null
    }
    if (normalizedType == "SOUND" && normalizedFullUrl.isBlank()) {
        return null
    }
    return FavoriteEntity(
        id = normalizedId,
        source = normalizedSource,
        type = normalizedType,
        thumbnailUrl = normalizedThumbnailUrl,
        fullUrl = normalizedFullUrl,
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
    )
}
