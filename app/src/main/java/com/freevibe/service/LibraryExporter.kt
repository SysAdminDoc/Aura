package com.freevibe.service

import android.content.Context
import android.net.Uri
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.local.FavoriteDao
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.SearchHistoryDao
import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.SearchHistoryEntity
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.util.rethrowIfCancelled
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val LIBRARY_EXPORT_VERSION = 1
private const val MAX_IMPORT_SIZE_CHARS = 10_000_000

@JsonClass(generateAdapter = true)
data class LibraryExportFile(
    val version: Int = LIBRARY_EXPORT_VERSION,
    val exportedAt: Long = 0,
    val favorites: List<FavoriteExportEntry> = emptyList(),
    val downloads: List<DownloadExportEntry> = emptyList(),
    val collections: List<CollectionExportEntry> = emptyList(),
    val searchHistory: List<SearchHistoryExportEntry> = emptyList(),
    val wallpaperPackJson: String = "",
    val soundProfilesJson: String = "",
)

@JsonClass(generateAdapter = true)
data class FavoriteExportEntry(
    val id: String,
    val source: String,
    val type: String,
    val thumbnailUrl: String = "",
    val fullUrl: String = "",
    val name: String = "",
    val addedAt: Long = 0,
)

@JsonClass(generateAdapter = true)
data class DownloadExportEntry(
    val id: String,
    val source: String,
    val type: String,
    val localPath: String = "",
    val name: String = "",
    val downloadedAt: Long = 0,
)

@JsonClass(generateAdapter = true)
data class CollectionExportEntry(
    val id: Long,
    val name: String,
    val createdAt: Long = 0,
    val items: List<CollectionItemExportEntry> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CollectionItemExportEntry(
    val wallpaperId: String,
    val source: String = "",
    val thumbnailUrl: String = "",
    val fullUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class SearchHistoryExportEntry(
    val query: String,
    val type: String = "",
    val searchedAt: Long = 0,
)

@Singleton
class LibraryExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoriteDao: FavoriteDao,
    private val downloadDao: DownloadDao,
    private val collectionRepo: CollectionRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val prefs: PreferencesManager,
    private val moshi: Moshi,
) {
    private val adapter = moshi.adapter(LibraryExportFile::class.java)

    suspend fun exportLibrary(outputUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val favorites = favoriteDao.getAll().first().map { it.toExportEntry() }
            val downloads = downloadDao.getAll().first().map { it.toExportEntry() }
            val collections = exportCollections()
            val wallpaperSearches = searchHistoryDao.getRecent("WALLPAPER", 100).first()
            val soundSearches = searchHistoryDao.getRecent("SOUND", 100).first()
            val searchHistory = (wallpaperSearches + soundSearches).map { it.toExportEntry() }
            val wallpaperPack = prefs.wallpaperPackJson.first()
            val soundProfiles = prefs.soundProfilesJson.first()

            val exportFile = LibraryExportFile(
                version = LIBRARY_EXPORT_VERSION,
                exportedAt = System.currentTimeMillis(),
                favorites = favorites,
                downloads = downloads,
                collections = collections,
                searchHistory = searchHistory,
                wallpaperPackJson = wallpaperPack,
                soundProfilesJson = soundProfiles,
            )

            val json = adapter.indent("  ").toJson(exportFile)
            context.contentResolver.openOutputStream(outputUri)?.use { out ->
                out.write(json.toByteArray())
            } ?: throw IllegalStateException("Failed to open output stream")

            favorites.size + downloads.size + collections.size + searchHistory.size
        }.onFailure { it.rethrowIfCancelled() }
    }

    suspend fun importLibrary(inputUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.contentResolver.openInputStream(inputUri)?.use { input ->
                val reader = java.io.BufferedReader(java.io.InputStreamReader(input))
                val sb = StringBuilder()
                val buffer = CharArray(8192)
                var read: Int
                while (reader.read(buffer).also { read = it } != -1) {
                    sb.append(buffer, 0, read)
                    if (sb.length > MAX_IMPORT_SIZE_CHARS) {
                        throw IllegalStateException("Import file too large (>${MAX_IMPORT_SIZE_CHARS / 1_000_000}MB)")
                    }
                }
                sb.toString()
            } ?: throw IllegalStateException("Failed to open input stream")

            val exportFile = adapter.fromJson(json)
                ?: throw IllegalStateException("Invalid library backup format")

            var imported = 0

            if (exportFile.favorites.isNotEmpty()) {
                val entities = exportFile.favorites.mapNotNull { it.toEntity() }
                favoriteDao.insertAll(entities)
                imported += entities.size
            }

            if (exportFile.searchHistory.isNotEmpty()) {
                exportFile.searchHistory.forEach { entry ->
                    searchHistoryDao.insert(
                        SearchHistoryEntity(
                            query = entry.query,
                            type = entry.type,
                            timestamp = entry.searchedAt,
                        )
                    )
                }
                imported += exportFile.searchHistory.size
            }

            if (exportFile.wallpaperPackJson.isNotBlank()) {
                prefs.setWallpaperPackJson(exportFile.wallpaperPackJson)
                imported++
            }

            if (exportFile.soundProfilesJson.isNotBlank()) {
                prefs.setSoundProfilesJson(exportFile.soundProfilesJson)
                imported++
            }

            imported
        }.onFailure { it.rethrowIfCancelled() }
    }

    private suspend fun exportCollections(): List<CollectionExportEntry> {
        val collections = collectionRepo.getAll().first()
        return collections.map { collection ->
            val items = collectionRepo.getItems(collection.collectionId).first()
            CollectionExportEntry(
                id = collection.collectionId,
                name = collection.name,
                createdAt = collection.createdAt,
                items = items.map { item ->
                    CollectionItemExportEntry(
                        wallpaperId = item.wallpaperId,
                        source = item.source,
                        thumbnailUrl = item.thumbnailUrl,
                        fullUrl = item.fullUrl,
                    )
                },
            )
        }
    }
}

private fun FavoriteEntity.toExportEntry() = FavoriteExportEntry(
    id = id,
    source = source,
    type = type,
    thumbnailUrl = thumbnailUrl,
    fullUrl = fullUrl,
    name = name,
    addedAt = addedAt,
)

private fun DownloadEntity.toExportEntry() = DownloadExportEntry(
    id = id,
    source = source,
    type = type,
    localPath = localPath,
    name = name,
    downloadedAt = downloadedAt,
)

private fun SearchHistoryEntity.toExportEntry() = SearchHistoryExportEntry(
    query = query,
    type = type,
    searchedAt = timestamp,
)

private fun FavoriteExportEntry.toEntity(): FavoriteEntity? {
    if (id.isBlank() || source.isBlank() || type.isBlank()) return null
    return FavoriteEntity(
        id = id,
        source = source,
        type = type,
        thumbnailUrl = thumbnailUrl,
        fullUrl = fullUrl,
        name = name,
        addedAt = if (addedAt > 0) addedAt else System.currentTimeMillis(),
    )
}
