package com.freevibe.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.freevibe.ACTION_SHORTCUT_DOWNLOADS
import com.freevibe.BuildConfig
import com.freevibe.MainActivity
import com.freevibe.R
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.SOURCE_AVAILABILITY_UNAVAILABLE
import com.freevibe.data.model.sourceUnavailableReasonForFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.math.roundToInt

data class DownloadProgress(
    val id: String,
    val fileName: String,
    val progress: Float,       // 0.0 - 1.0
    val totalBytes: Long,
    val downloadedBytes: Long,
    val isComplete: Boolean = false,
    val error: String? = null,
)

private data class DownloadNotificationSnapshot(
    val postedAtMs: Long,
    val percent: Int,
    val totalBytes: Long,
)

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val downloadDao: DownloadDao,
    private val downloadTrash: DownloadTrash,
    private val mediaCopyStore: MediaCopyStore,
) {
    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads.asStateFlow()

    /**
     * How to run each download again, by history ID, until it succeeds or its card is dismissed.
     * In memory only, like the cards themselves, which don't outlive the process.
     */
    private val retryRequests = ConcurrentHashMap<String, suspend () -> Result<Uri>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** How long a finished download stays under Active before history alone shows it. */
    internal var completedProgressVisibleMs = COMPLETED_PROGRESS_VISIBLE_MS
    private val notificationLock = Any()
    private val notificationSnapshots = mutableMapOf<String, DownloadNotificationSnapshot>()

    /** Download a wallpaper image to the Pictures directory */
    suspend fun downloadWallpaper(
        id: String,
        url: String,
        fileName: String,
        source: String = "WALLPAPER",
        provenanceUrl: String = url,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val contentType = "WALLPAPER"
        val historyId = downloadHistoryId(contentType, id)
        retryRequests[historyId] = { downloadWallpaper(id, url, fileName, source, provenanceUrl) }
        downloadFile(
            contentId = id,
            historyId = historyId,
            url = url,
            fileName = sanitize(fileName),
            relativePath = Environment.DIRECTORY_PICTURES + "/Aura",
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentType = contentType,
            contentSource = source,
            provenanceUrl = provenanceUrl,
            maxBytes = MAX_IMAGE_DOWNLOAD_BYTES,
            expectedMediaFamily = MediaFamily.IMAGE,
        )
    }

    /** Download a sound to the appropriate audio directory */
    suspend fun downloadSound(
        id: String,
        url: String,
        fileName: String,
        type: ContentType,
        source: String = "SOUND",
        provenanceUrl: String = url,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val contentType = "SOUND"
        val relativePath = when (type) {
            ContentType.RINGTONE -> Environment.DIRECTORY_RINGTONES
            ContentType.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
            ContentType.ALARM -> Environment.DIRECTORY_ALARMS
            else -> Environment.DIRECTORY_MUSIC
        } + "/Aura"

        val historyId = downloadHistoryId(contentType, id)
        retryRequests[historyId] = { downloadSound(id, url, fileName, type, source, provenanceUrl) }
        downloadFile(
            contentId = id,
            historyId = historyId,
            url = url,
            fileName = sanitize(fileName),
            relativePath = relativePath,
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            contentType = contentType,
            contentSource = source,
            provenanceUrl = provenanceUrl,
            maxBytes = MAX_AUDIO_DOWNLOAD_BYTES,
            expectedMediaFamily = MediaFamily.AUDIO,
        )
    }

    private suspend fun downloadFile(
        contentId: String,
        historyId: String,
        url: String,
        fileName: String,
        relativePath: String,
        collection: Uri,
        contentType: String,
        contentSource: String,
        provenanceUrl: String,
        maxBytes: Long,
        expectedMediaFamily: MediaFamily,
    ): Result<Uri> = try {
        updateProgress(historyId, DownloadProgress(historyId, fileName, 0f, 0, 0))

        if (isLocalMediaLocator(url)) {
            val tempFile = stageLocalMediaLocator(
                context = context,
                locator = url,
                tempDirectoryName = "downloads",
                prefix = "aura_download_",
                maxBytes = maxBytes,
            )
            try {
                val downloadedBytes = tempFile.length()
                val sniffed = requireSniffedMediaFile(
                    tempFile,
                    expectedMediaFamily,
                    if (expectedMediaFamily == MediaFamily.IMAGE) "Wallpaper" else "Sound",
                )
                val storedFileName = normalizeMediaFileName(fileName, sniffed)
                Result.success(
                    writeValidatedDownloadToMediaStore(
                        tempFile = tempFile,
                        contentId = contentId,
                        historyId = historyId,
                        fileName = storedFileName,
                        mimeType = sniffed.mimeType,
                        relativePath = relativePath,
                        collection = collection,
                        contentType = contentType,
                        contentSource = contentSource,
                        provenanceUrl = provenanceUrl,
                        totalBytes = downloadedBytes,
                        downloadedBytes = downloadedBytes,
                        maxBytes = maxBytes,
                    ),
                )
            } finally {
                tempFile.delete()
            }
        } else {
            // Start HTTP download
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            Result.success(response.use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${resp.code}")
            }
            val body = resp.body
                ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength().let { if (it <= 0) 0L else it }
            // Reject oversized downloads up front when the server advertises a size, so we
            // don't allocate a MediaStore entry we're immediately going to delete.
            if (totalBytes in 1..Long.MAX_VALUE && totalBytes > maxBytes) {
                throw IllegalStateException(
                    "Download exceeds size limit (${totalBytes} > ${maxBytes})"
                )
            }

            val tempDir = File(context.cacheDir, "downloads").apply { mkdirs() }
            val tempFile = File.createTempFile("aura_download_", ".tmp", tempDir)
            var downloadedBytes = 0L
            try {
                tempFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var lastEmitNanos = System.nanoTime()
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (downloadedBytes + bytesRead > maxBytes) {
                                throw IllegalStateException(
                                    "Download exceeds size limit ($maxBytes bytes)"
                                )
                            }
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val now = System.nanoTime()
                            if (now - lastEmitNanos >= 250_000_000L) {
                                val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                                updateProgress(
                                    historyId,
                                    DownloadProgress(historyId, fileName, progress, totalBytes, downloadedBytes),
                                )
                                lastEmitNanos = now
                            }
                        }
                    }
                }
                updateProgress(
                    historyId,
                    DownloadProgress(historyId, fileName, 1f, totalBytes, downloadedBytes),
                )
                if (downloadedBytes <= 0L) {
                    throw IllegalStateException("Empty response body")
                }
                val sniffed = requireSniffedMediaFile(
                    tempFile,
                    expectedMediaFamily,
                    if (expectedMediaFamily == MediaFamily.IMAGE) "Wallpaper" else "Sound",
                )
                val storedFileName = normalizeMediaFileName(fileName, sniffed)

                // Create MediaStore entry only after content has been bounded and verified.
                writeValidatedDownloadToMediaStore(
                    tempFile = tempFile,
                    contentId = contentId,
                    historyId = historyId,
                    fileName = storedFileName,
                    mimeType = sniffed.mimeType,
                    relativePath = relativePath,
                    collection = collection,
                    contentType = contentType,
                    contentSource = contentSource,
                    provenanceUrl = provenanceUrl,
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    maxBytes = maxBytes,
                )
            } finally {
                tempFile.delete()
            }
            })
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        sourceUnavailableReasonForFailure(contentSource, e)?.let { reason ->
            downloadDao.updateSourceAvailability(historyId, SOURCE_AVAILABILITY_UNAVAILABLE, reason)
        }
        val reason = downloadFailureReason(context, e)
        updateProgress(historyId, DownloadProgress(historyId, fileName, 0f, 0, 0, error = reason))
        // Callers show the message in a snackbar, so it carries the same safe reason as the card.
        Result.failure(DownloadFailedException(reason, e))
    }

    /**
     * Runs a failed download again with the request that started it. The retry replaces the
     * card in place, and a success replaces any earlier copy the same way a first download does.
     */
    suspend fun retryDownload(id: String): Result<Uri> {
        val request = retryRequests[id] ?: return Result.failure(IllegalStateException("This download can't be retried"))
        return request()
    }

    /** Starts [retryDownload] in the manager's own scope, so leaving the screen doesn't cancel it. */
    fun startRetry(id: String) {
        scope.launch { retryDownload(id) }
    }

    private suspend fun writeValidatedDownloadToMediaStore(
        tempFile: File,
        contentId: String,
        historyId: String,
        fileName: String,
        mimeType: String,
        relativePath: String,
        collection: Uri,
        contentType: String,
        contentSource: String,
        provenanceUrl: String,
        totalBytes: Long,
        downloadedBytes: Long,
        maxBytes: Long,
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        var success = false
        try {
            val outputStream = resolver.openOutputStream(uri)
            if (outputStream == null) {
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                throw IllegalStateException("Failed to open output stream")
            }
            outputStream.use { output ->
                tempFile.inputStream().use { input -> copyStreamCapped(input, output, maxBytes) }
            }

            // Save Original is byte-for-byte. Verify the published MediaStore item before
            // exposing it or replacing an older copy.
            val originalSha256 = sha256File(tempFile)
            val publishedSha256 = resolver.openInputStream(uri)?.use(::sha256InputStream)
                ?: throw IllegalStateException("Failed to verify saved original")
            if (publishedSha256 != originalSha256) {
                throw IllegalStateException("Saved original did not match its source bytes")
            }
            val originalMetadata = readMediaTechnicalMetadata(tempFile, mimeType)

            // Mark as complete in MediaStore
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                if (resolver.update(uri, values, null, null) <= 0) {
                    throw IllegalStateException("Failed to publish saved original")
                }
            }

            val existingEntries = downloadDao.findMatching(
                type = contentType,
                legacyId = contentId,
                scopedId = historyId,
            )

            val currentEntry = existingEntries.firstOrNull { it.id == historyId }
            val replacement = if (
                currentEntry != null &&
                currentEntry.originalSha256.equals(originalSha256, ignoreCase = true)
            ) {
                currentEntry.copy(
                    source = contentSource,
                    type = contentType,
                    localPath = uri.toString(),
                    name = fileName,
                    downloadedAt = System.currentTimeMillis(),
                    localMediaStatus = com.freevibe.data.model.LocalMediaStatus.AVAILABLE,
                    localMediaReason = null,
                    provenanceUrl = provenanceUrl,
                    originalSha256 = originalSha256,
                    originalMimeType = originalMetadata.mimeType,
                    originalCodec = originalMetadata.codec,
                    originalWidth = originalMetadata.width,
                    originalHeight = originalMetadata.height,
                    originalDurationMs = originalMetadata.durationMs,
                    originalSizeBytes = originalMetadata.sizeBytes,
                    originalHdr = originalMetadata.isHdr,
                )
            } else {
                DownloadEntity(
                    id = historyId,
                    source = contentSource,
                    type = contentType,
                    localPath = uri.toString(),
                    name = fileName,
                    provenanceUrl = provenanceUrl,
                    originalSha256 = originalSha256,
                    originalMimeType = originalMetadata.mimeType,
                    originalCodec = originalMetadata.codec,
                    originalWidth = originalMetadata.width,
                    originalHeight = originalMetadata.height,
                    originalDurationMs = originalMetadata.durationMs,
                    originalSizeBytes = originalMetadata.sizeBytes,
                    originalHdr = originalMetadata.isHdr,
                )
            }

            // Record metadata only after byte verification. A failed insert leaves the
            // previous row and both its original and optimized copy intact.
            downloadDao.insert(replacement)

            existingEntries
                .filter { prior ->
                    prior.optimizedPath.isNotBlank() &&
                        (prior.id != historyId || prior.originalSha256 != originalSha256)
                }
                .map { it.optimizedPath }
                .distinct()
                .forEach(mediaCopyStore::deleteManagedApplyCopy)

            existingEntries
                .map { it.localPath }
                .filter { it.isNotBlank() && it != uri.toString() }
                .distinct()
                .forEach(::deleteStoredContent)

            existingEntries
                .map { it.id }
                .filter { it != historyId }
                .distinct()
                .forEach { existingId ->
                    downloadDao.deleteById(existingId)
                }

            // Mark download complete, then let the card go once history has it.
            val completed = DownloadProgress(historyId, fileName, 1f, totalBytes, downloadedBytes, isComplete = true)
            updateProgress(historyId, completed)
            retryRequests.remove(historyId)
            scope.launch {
                delay(completedProgressVisibleMs)
                _activeDownloads.update { current -> withoutFinishedProgress(current, historyId, completed) }
                synchronized(notificationLock) { notificationSnapshots.remove(historyId) }
            }
            success = true
        } finally {
            if (!success) {
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            }
        }

        return uri
    }

    fun clearCompleted(id: String) {
        _activeDownloads.update { it - id }
        retryRequests.remove(id)
        synchronized(notificationLock) { notificationSnapshots.remove(id) }
        NotificationManagerCompat.from(context).cancel(downloadNotificationId(id))
    }

    /**
     * Moves a download into the staged trash instead of destroying it.
     *
     * A managed local file is moved into the trash staging directory and a
     * `content://` locator is simply retained, so [restoreDownload] can put the
     * row and its bytes back. Nothing is actually destroyed until
     * [purgeExpiredTrash] runs past [DELETION_RETENTION_MS].
     */
    suspend fun deleteDownload(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val existing = downloadDao.getById(id)
            if (existing == null) {
                clearCompleted(id)
                return@withContext Result.success(Unit)
            }
            val stagedPath = stageForTrash(existing)
            downloadTrash.add(
                TrashedDownload(
                    id = existing.id,
                    source = existing.source,
                    type = existing.type,
                    name = existing.name,
                    localPath = existing.localPath,
                    downloadedAt = existing.downloadedAt,
                    sourceAvailability = existing.sourceAvailability,
                    sourceAvailabilityReason = existing.sourceAvailabilityReason,
                    localMediaStatus = existing.localMediaStatus,
                    localMediaReason = existing.localMediaReason,
                    provenanceUrl = existing.provenanceUrl,
                    originalSha256 = existing.originalSha256,
                    originalMimeType = existing.originalMimeType,
                    originalCodec = existing.originalCodec,
                    originalWidth = existing.originalWidth,
                    originalHeight = existing.originalHeight,
                    originalDurationMs = existing.originalDurationMs,
                    originalSizeBytes = existing.originalSizeBytes,
                    originalHdr = existing.originalHdr,
                    optimizedPath = existing.optimizedPath,
                    optimizedSha256 = existing.optimizedSha256,
                    optimizedMimeType = existing.optimizedMimeType,
                    optimizedCodec = existing.optimizedCodec,
                    optimizedWidth = existing.optimizedWidth,
                    optimizedHeight = existing.optimizedHeight,
                    optimizedDurationMs = existing.optimizedDurationMs,
                    optimizedSizeBytes = existing.optimizedSizeBytes,
                    optimizedHdr = existing.optimizedHdr,
                    optimizationKey = existing.optimizationKey,
                    optimizationReason = existing.optimizationReason,
                    optimizedAt = existing.optimizedAt,
                    stagedPath = stagedPath,
                    deletedAtMs = System.currentTimeMillis(),
                ),
            )
            downloadDao.deleteById(id)
            clearCompleted(id)
            purgeExpiredTrashInternal()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    /**
     * Puts a trashed download back, file included.
     *
     * @return true when the row was restored. A file that vanished from staging
     *   (external cleanup, storage pressure) still restores its metadata so the
     *   entry is visible and reports its own missing source rather than silently
     *   disappearing.
     */
    suspend fun restoreDownload(id: String): Boolean = withContext(Dispatchers.IO) {
        val entry = downloadTrash.take(id) ?: return@withContext false
        val staged = entry.stagedPath.takeIf { it.isNotBlank() }?.let(::File)
        if (staged != null && staged.exists()) {
            val target = resolveManagedLocalDeletionTarget(entry.localPath.removePrefix("file://"))
            if (target != null) {
                runCatching {
                    target.parentFile?.mkdirs()
                    if (!staged.renameTo(target)) {
                        staged.copyTo(target, overwrite = true)
                        staged.delete()
                    }
                }
            }
        }
        downloadDao.insert(entry.toEntity())
        true
    }

    /** Destroys trash entries past the retention window. */
    suspend fun purgeExpiredTrash(): Int = withContext(Dispatchers.IO) {
        purgeExpiredTrashInternal()
    }

    private fun purgeExpiredTrashInternal(): Int {
        val expired = downloadTrash.purgeExpired(System.currentTimeMillis())
        expired.forEach { entry ->
            // Staged files are already gone; a content:// locator is only released
            // now, which is what makes the retention window restorable.
            if (entry.stagedPath.isBlank() && entry.localPath.isNotBlank()) {
                runCatching { deleteStoredContent(entry.localPath) }
            }
            if (entry.optimizedPath.isNotBlank()) {
                mediaCopyStore.deleteManagedApplyCopy(entry.optimizedPath)
            }
        }
        return expired.size
    }

    /**
     * Moves a managed local file into the trash staging directory.
     *
     * @return the staged path, or blank when the locator is not a managed local
     *   file (a `content://` MediaStore row is retained in place instead).
     */
    private fun stageForTrash(entity: DownloadEntity): String {
        val raw = entity.localPath.takeIf { it.isNotBlank() } ?: return ""
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        if (uri != null && uri.scheme.equals("content", ignoreCase = true)) return ""
        val source = resolveManagedLocalDeletionTarget(uri?.path ?: raw) ?: return ""
        if (!source.exists()) return ""
        val target = File(downloadTrash.stagingDir, "${sanitizeTrashName(entity.id)}_${source.name}")
        return runCatching {
            target.parentFile?.mkdirs()
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = true)
                source.delete()
            }
            target.absolutePath
        }.getOrDefault("")
    }

    private fun sanitizeTrashName(raw: String): String =
        raw.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
            .joinToString("")
            .take(64)
            .ifBlank { "download" }

    private fun updateProgress(id: String, progress: DownloadProgress) {
        _activeDownloads.update { it + (id to progress) }
        publishDownloadNotification(progress)
    }

    private fun publishDownloadNotification(progress: DownloadProgress) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled() || !shouldPostNotification(progress)) return

        val notification = try {
            if (Build.VERSION.SDK_INT >= 36) {
                buildApi36DownloadNotification(progress)
            } else {
                buildLegacyDownloadNotification(progress)
            }
        } catch (e: Exception) {
            Log.w("DownloadManager", "Failed to build download notification: ${e.javaClass.simpleName}")
            return
        }
        try {
            manager.notify(downloadNotificationId(progress.id), notification)
        } catch (e: SecurityException) {
            Log.w("DownloadManager", "Download notification permission was revoked")
        }
    }

    private fun shouldPostNotification(progress: DownloadProgress): Boolean {
        val now = SystemClock.elapsedRealtime()
        val percent = downloadNotificationPercent(progress)
        val terminal = progress.isComplete || progress.error != null
        synchronized(notificationLock) {
            val previous = notificationSnapshots[progress.id]
            val changed = previous == null ||
                previous.percent != percent ||
                previous.totalBytes != progress.totalBytes
            val shouldPost = previous == null || terminal ||
                (changed && now - previous.postedAtMs >= DOWNLOAD_NOTIFICATION_MIN_INTERVAL_MS)
            if (shouldPost) {
                notificationSnapshots[progress.id] = DownloadNotificationSnapshot(
                    postedAtMs = now,
                    percent = percent,
                    totalBytes = progress.totalBytes,
                )
            }
            return shouldPost
        }
    }

    private fun downloadNotificationId(id: String): Int =
        DOWNLOAD_NOTIFICATION_ID_BASE + (id.hashCode() and 0x0FFFFFFF)

    private fun downloadNotificationPercent(progress: DownloadProgress): Int =
        if (progress.isComplete) {
            100
        } else {
            (progress.progress.coerceIn(0f, 1f) * 100f).roundToInt()
        }

    private fun buildLegacyDownloadNotification(progress: DownloadProgress): Notification =
        NotificationCompat.Builder(context, NotificationChannels.DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(downloadNotificationTitle(progress))
            .setContentText(downloadNotificationText(progress))
            .setContentIntent(downloadContentIntent(progress.id))
            .setOngoing(progress.error == null && !progress.isComplete)
            .setAutoCancel(progress.error != null || progress.isComplete)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(
                100,
                downloadNotificationPercent(progress),
                progress.totalBytes <= 0L && !progress.isComplete && progress.error == null,
            )
            .build()

    @RequiresApi(36)
    private fun buildApi36DownloadNotification(progress: DownloadProgress): Notification {
        val builder = Notification.Builder(context, NotificationChannels.DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(downloadNotificationTitle(progress))
            .setContentText(downloadNotificationText(progress))
            .setContentIntent(downloadContentIntent(progress.id))
            .setOngoing(progress.error == null && !progress.isComplete)
            .setAutoCancel(progress.error != null || progress.isComplete)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (progress.error == null) {
            val style = Notification.ProgressStyle().setStyledByProgress(true)
            if (progress.totalBytes > 0L || progress.isComplete) {
                style.setProgress(downloadNotificationPercent(progress))
            } else {
                style.setProgressIndeterminate(true)
            }
            builder.setStyle(style)
        }
        return builder.build()
    }

    private fun downloadContentIntent(id: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_SHORTCUT_DOWNLOADS
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            DOWNLOAD_NOTIFICATION_REQUEST_CODE_BASE + (id.hashCode() and 0x0FFFFFFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun downloadNotificationTitle(progress: DownloadProgress): String =
        if (progress.isComplete) {
            context.getString(R.string.download_notification_complete, progress.fileName)
        } else if (progress.error != null) {
            context.getString(R.string.download_notification_failed, progress.fileName)
        } else {
            context.getString(R.string.download_notification_title, progress.fileName)
        }

    private fun downloadNotificationText(progress: DownloadProgress): String = when {
        progress.error != null -> progress.error
            .takeIf { it.isNotBlank() }
            ?: context.getString(R.string.download_notification_failed, progress.fileName)
        progress.isComplete -> context.getString(R.string.a11y_download_complete)
        progress.totalBytes > 0L -> context.getString(
            R.string.a11y_download_percent,
            downloadNotificationPercent(progress),
        )
        else -> context.getString(R.string.download_notification_preparing)
    }

    private fun deleteStoredContent(rawPath: String) {
        val uri = runCatching { Uri.parse(rawPath) }.getOrNull()
        when {
            uri == null -> resolveManagedLocalDeletionTarget(rawPath)?.takeIf { it.exists() }?.delete()
            uri.scheme == null || uri.scheme.equals("file", ignoreCase = true) -> {
                resolveManagedLocalDeletionTarget(uri.path ?: rawPath)?.takeIf { it.exists() }?.delete()
            }
            uri.scheme.equals("content", ignoreCase = true) -> {
                if (isAuraManagedContentUri(uri, context)) {
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (_: Exception) {
                    }
                } else if (com.freevibe.BuildConfig.DEBUG) {
                    Log.w("DownloadManager", "Skipping deletion for unmanaged content URI: $uri")
                }
            }
            else -> resolveManagedLocalDeletionTarget(rawPath)?.takeIf { it.exists() }?.delete()
        }
    }

    private fun resolveManagedLocalDeletionTarget(rawPath: String): File? {
        val canonicalTarget = runCatching { File(rawPath).canonicalFile }.getOrNull() ?: return null
        val managedRoots = listOfNotNull(
            context.filesDir,
            context.cacheDir,
            context.externalCacheDir,
            context.getExternalFilesDir(null),
        ).mapNotNull { root ->
            runCatching { root.canonicalFile }.getOrNull()
        }
        val isAppManaged = managedRoots.any { root ->
            canonicalTarget == root || canonicalTarget.path.startsWith(root.path + File.separator)
        }
        if (isAppManaged || isAuraManagedAbsolutePath(canonicalTarget.path)) {
            return canonicalTarget
        }
        if (com.freevibe.BuildConfig.DEBUG) {
            Log.w("DownloadManager", "Skipping deletion for unmanaged local path: $rawPath")
        }
        return null
    }

    private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9._-]")

    private fun sanitize(name: String) = name.replace(SANITIZE_REGEX, "_")

}

internal fun downloadHistoryId(type: String, id: String): String =
    "${type.lowercase(java.util.Locale.ROOT)}:$id"

/** Drops a finished card only if it is still the one that finished, not a newer attempt. */
internal fun withoutFinishedProgress(
    current: Map<String, DownloadProgress>,
    id: String,
    finished: DownloadProgress,
): Map<String, DownloadProgress> = if (current[id] == finished) current - id else current

/**
 * A failure always carries a reason, or its card would look like a download still running. The
 * reason is a short sentence in the app's language: exception text can carry a URL or host name,
 * so it's never shown as is.
 */
internal fun downloadFailureReason(context: Context, error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    val message = error.message.orEmpty()
    val httpStatus = HTTP_STATUS_REGEX.find(message)?.groupValues?.get(1)?.toInt()
    return when {
        causes.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException } ->
            context.getString(R.string.download_failed_offline)
        causes.any { it is SocketTimeoutException } -> context.getString(R.string.download_failed_timeout)
        causes.any { it is SSLException } -> context.getString(R.string.download_failed_secure)
        httpStatus != null -> context.getString(R.string.download_failed_http, httpStatus)
        message.contains("size limit", ignoreCase = true) -> context.getString(R.string.download_failed_too_large)
        message.contains("content type", ignoreCase = true) -> context.getString(R.string.download_failed_not_media)
        error is SecurityException || STORAGE_FAILURE_MARKERS.any { message.contains(it, ignoreCase = true) } ->
            context.getString(R.string.download_failed_storage)
        else -> context.getString(R.string.download_failed_unknown)
    }
}

/** A failed download whose message is already worded for people. [cause] keeps the original for logs. */
class DownloadFailedException(message: String, cause: Throwable) : IOException(message, cause)

private val HTTP_STATUS_REGEX = Regex("""\bHTTP (\d{3})\b""")
private val STORAGE_FAILURE_MARKERS = listOf("MediaStore", "output stream", "saved original")

private const val COMPLETED_PROGRESS_VISIBLE_MS = 4_000L

/** Hard cap on wallpaper downloads — ~64 MB covers any realistic 8K JPG/PNG/WEBP. */
private const val MAX_IMAGE_DOWNLOAD_BYTES = 64L * 1024 * 1024

/** Hard cap on audio downloads — matches the 20 MB community upload ceiling + headroom. */
private const val MAX_AUDIO_DOWNLOAD_BYTES = 64L * 1024 * 1024

private const val DOWNLOAD_NOTIFICATION_MIN_INTERVAL_MS = 250L
private const val DOWNLOAD_NOTIFICATION_ID_BASE = 16_000
private const val DOWNLOAD_NOTIFICATION_REQUEST_CODE_BASE = 8_000

private val AURA_MEDIA_DIRECTORIES = listOfNotNull(
    Environment.DIRECTORY_PICTURES,
    Environment.DIRECTORY_RINGTONES,
    Environment.DIRECTORY_NOTIFICATIONS,
    Environment.DIRECTORY_ALARMS,
    Environment.DIRECTORY_MUSIC,
    Environment.DIRECTORY_MOVIES,
).ifEmpty {
    listOf("Pictures", "Ringtones", "Notifications", "Alarms", "Music", "Movies")
}

internal fun isAuraManagedRelativePath(relativePath: String?): Boolean {
    val normalized = relativePath
        ?.replace('\\', '/')
        ?.trim('/')
        ?.lowercase(Locale.ROOT)
        ?: return false
    return AURA_MEDIA_DIRECTORIES.any { directory ->
        val managedRoot = "${directory.lowercase(Locale.ROOT)}/aura"
        normalized == managedRoot || normalized.startsWith("$managedRoot/")
    }
}

internal fun isAuraManagedAbsolutePath(path: String?): Boolean {
    val normalized = path
        ?.replace('\\', '/')
        ?.lowercase(Locale.ROOT)
        ?: return false
    return AURA_MEDIA_DIRECTORIES.any { directory ->
        normalized.contains("/${directory.lowercase(Locale.ROOT)}/aura/")
    }
}

internal fun isAuraManagedContentUri(uri: Uri, context: Context): Boolean {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst() || cursor.isNull(0)) {
                    false
                } else {
                    isAuraManagedRelativePath(cursor.getString(0))
                }
            }
            ?: false
    }.getOrDefault(false)
}
