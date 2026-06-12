package com.freevibe.service

import android.content.Context
import android.net.Uri
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.local.FavoriteDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class PathBackedRecordReconciliationResult(
    val favoritesCleared: Int,
    val downloadsCleared: Int,
)

@Singleton
class PathBackedRecordReconciler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoriteDao: FavoriteDao,
    private val downloadDao: DownloadDao,
) {
    suspend fun reconcile(): PathBackedRecordReconciliationResult = withContext(Dispatchers.IO) {
        var favoritesCleared = 0
        favoriteDao.getAll().first()
            .filter { shouldClearPathBackedRecord(it.offlinePath, ::pathBackedRecordExists) }
            .forEach { favorite ->
                favoriteDao.updateOfflinePath(favorite.id, favorite.source, favorite.type, "")
                favoritesCleared += 1
            }

        var downloadsCleared = 0
        downloadDao.getAll().first()
            .filter { shouldClearPathBackedRecord(it.localPath, ::pathBackedRecordExists) }
            .forEach { download ->
                downloadDao.updateLocalPath(download.id, "")
                downloadsCleared += 1
            }

        PathBackedRecordReconciliationResult(
            favoritesCleared = favoritesCleared,
            downloadsCleared = downloadsCleared,
        )
    }

    private fun pathBackedRecordExists(rawPath: String): Boolean =
        pathBackedRecordExists(
            rawPath = rawPath,
            fileExists = { File(it).exists() },
            contentUriExists = ::contentUriExists,
        )

    private fun contentUriExists(rawUri: String): Boolean =
        runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(rawUri), "r")?.use { true } ?: false
        }.getOrDefault(false)
}

internal fun shouldClearPathBackedRecord(
    rawPath: String,
    exists: (String) -> Boolean,
): Boolean = rawPath.isNotBlank() && !exists(rawPath)

internal fun pathBackedRecordExists(
    rawPath: String,
    fileExists: (String) -> Boolean,
    contentUriExists: (String) -> Boolean,
): Boolean {
    val path = rawPath.trim()
    if (path.isBlank()) return false

    return when (extractUriScheme(path)?.lowercase(Locale.ROOT)) {
        null -> fileExists(path)
        "file" -> fileExists(fileUriPath(path) ?: path)
        "content" -> contentUriExists(path)
        else -> fileExists(path)
    }
}

private fun extractUriScheme(value: String): String? {
    val colonIndex = value.indexOf(':')
    if (colonIndex <= 0) return null
    val firstSeparator = value.indexOfAny(charArrayOf('/', '\\', '?', '#')).let { index ->
        if (index == -1) value.length else index
    }
    if (colonIndex > firstSeparator) return null
    val candidate = value.substring(0, colonIndex)
    return candidate.takeIf { scheme ->
        scheme.first().isLetter() &&
            scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
    }
}

private fun fileUriPath(rawPath: String): String? =
    runCatching { URI(rawPath).path?.takeIf { it.isNotBlank() } }.getOrNull()
