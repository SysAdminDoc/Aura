package com.freevibe.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import com.freevibe.util.rethrowIfCancelled
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.freevibe.R
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.decideSoundOptimization
import com.freevibe.data.model.mediaOptimizationKey
import com.freevibe.data.model.originalTechnicalMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9._-]")

@Singleton
class SoundApplier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val prefs: PreferencesManager,
    private val mediaCopyStore: MediaCopyStore,
    private val audioTrimmer: AudioTrimmer,
    private val systemSounds: SystemSoundWriter,
) {
    /** Check if app has WRITE_SETTINGS permission */
    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    /** Launch system settings to grant WRITE_SETTINGS */
    fun requestWriteSettings(): Intent {
        return Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun canOpenWriteSettings(): Boolean =
        requestWriteSettings().resolveActivity(context.packageManager) != null

    /** Download audio from URL, save to MediaStore, and set as system sound */
    suspend fun downloadAndApply(
        url: String,
        fileName: String,
        type: ContentType,
        savedOriginalId: String? = null,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            if (!canWriteSettings()) {
                throw SecurityException("WRITE_SETTINGS permission not granted")
            }

            val (savedOriginal, effectiveLocator) = mediaCopyStore.findOriginal(savedOriginalId, url)
            val savedUri = savedOriginal?.localPath
                ?.let(Uri::parse)
                ?.takeIf { it.scheme.equals("content", ignoreCase = true) }
            val savedMetadata = savedOriginal?.originalTechnicalMetadata()
            val savedDecision = savedMetadata
                ?.takeIf { it.mimeType.isNotBlank() }
                ?.let { decideSoundOptimization(it, edited = false) }

            val uri = if (savedUri != null && savedDecision?.required == false) {
                // A compatible saved original is already a MediaStore item. Point Android
                // at those exact bytes instead of creating a redundant apply copy.
                savedUri
            } else {
                val staged = stageSoundLocator(effectiveLocator)
                var temporaryApplyFile: File? = null
                try {
                    val metadata = readMediaTechnicalMetadata(staged)
                    val decision = decideSoundOptimization(metadata, edited = false)
                    val applyFile = if (decision.required) {
                        prepareCompatibleSoundCopy(
                            source = staged,
                            sourceRecordId = savedOriginal?.id,
                            sourceHash = savedOriginal?.originalSha256.orEmpty().ifBlank { sha256File(staged) },
                            durationMs = metadata.durationMs,
                            reason = decision.reason,
                        )
                    } else {
                        staged
                    }
                    if (decision.required && savedOriginal == null) temporaryApplyFile = applyFile
                    saveLocalFileToMediaStore(
                        fileName.replace(SANITIZE_REGEX, "_"),
                        type,
                        applyFile,
                    ) ?: throw IllegalStateException("Failed to save audio to MediaStore")
                } finally {
                    temporaryApplyFile?.delete()
                    staged.delete()
                }
            }

            // Set as system sound
            val target = soundTargetFor(type) ?: throw IllegalArgumentException("Invalid sound type: $type")
            try {
                systemSounds.write(target, uri)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException(
                    context.getString(R.string.sound_apply_oem_failure), e
                )
            } catch (e: SecurityException) {
                throw SecurityException(
                    context.getString(R.string.sound_apply_write_settings_required), e
                )
            }
            persistAppliedUri(type, uri)

            uri
        }.onFailure { it.rethrowIfCancelled() }
    }

    /**
     * Save audio without applying - just download to storage. [maxBytes] lowers the size ceiling
     * for a caller with less than that to spend, and never raises it.
     */
    suspend fun downloadOnly(
        url: String,
        fileName: String,
        type: ContentType,
        maxBytes: Long = MAX_APPLY_BYTES,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            saveUrlToMediaStore(fileName.replace(SANITIZE_REGEX, "_"), type, url, maxBytes)
                ?: throw IllegalStateException("Failed to save audio to MediaStore")
        }.onFailure { it.rethrowIfCancelled() }
    }

    /** Silence the default ringtone so only explicitly assigned contact tones ring. */
    suspend fun setDefaultRingtoneSilent(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!canWriteSettings()) {
                throw SecurityException("WRITE_SETTINGS permission not granted")
            }
            systemSounds.write(SoundShuffleTarget.RINGTONE, null)
            prefs.setLastAppliedRingtoneUri("")
        }.onFailure { it.rethrowIfCancelled() }
    }

    /** Reuse a MediaStore item Aura published during an earlier shuffle. */
    suspend fun applyExistingUri(uri: Uri?, type: ContentType): Result<Uri?> = withContext(Dispatchers.IO) {
        runCatching {
            if (!canWriteSettings()) {
                throw SecurityException("WRITE_SETTINGS permission not granted")
            }
            if (uri != null) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    if (input.read() < 0) throw IllegalStateException("Saved sound is empty")
                } ?: throw IllegalStateException("Saved sound is no longer available")
            }
            val target = soundTargetFor(type) ?: throw IllegalArgumentException("Invalid sound type: $type")
            try {
                systemSounds.write(target, uri)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException(
                    context.getString(R.string.sound_apply_oem_failure), e
                )
            } catch (e: SecurityException) {
                throw SecurityException(
                    context.getString(R.string.sound_apply_write_settings_required), e
                )
            }
            persistAppliedUri(type, uri)
            uri
        }.onFailure { it.rethrowIfCancelled() }
    }

    /** Apply a local audio file (e.g. trimmed output) as system sound */
    suspend fun applyFromLocalFile(
        filePath: String,
        fileName: String,
        type: ContentType,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            if (!canWriteSettings()) {
                throw SecurityException("WRITE_SETTINGS permission not granted")
            }

            val uri = saveLocalFileToMediaStore(fileName.replace(SANITIZE_REGEX, "_"), type, File(filePath))
                ?: throw IllegalStateException("Failed to save audio to MediaStore")

            val target = soundTargetFor(type) ?: throw IllegalArgumentException("Invalid sound type: $type")
            systemSounds.write(target, uri)
            persistAppliedUri(type, uri)

            uri
        }.onFailure { it.rethrowIfCancelled() }
    }

    /** Save an edited local audio file to Music/Aura without changing a system sound. */
    suspend fun exportFromLocalFile(
        filePath: String,
        fileName: String,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            saveLocalFileToMediaStore(
                fileName = fileName.replace(SANITIZE_REGEX, "_"),
                type = null,
                file = File(filePath),
            ) ?: throw IllegalStateException("Failed to export audio to MediaStore")
        }.onFailure { it.rethrowIfCancelled() }
    }

    private suspend fun persistAppliedUri(type: ContentType, uri: Uri?) {
        when (type) {
            ContentType.RINGTONE -> prefs.setLastAppliedRingtoneUri(uri?.toString().orEmpty())
            ContentType.NOTIFICATION -> prefs.setLastAppliedNotificationUri(uri?.toString().orEmpty())
            ContentType.ALARM -> prefs.setLastAppliedAlarmUri(uri?.toString().orEmpty())
            else -> {}
        }
    }

    private fun saveToMediaStore(
        fileName: String,
        mimeType: String,
        type: ContentType?,
        writeContent: (OutputStream) -> Unit,
    ): Uri? {
        val relativePath = when (type) {
            ContentType.RINGTONE -> Environment.DIRECTORY_RINGTONES
            ContentType.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
            ContentType.ALARM -> Environment.DIRECTORY_ALARMS
            else -> Environment.DIRECTORY_MUSIC + "/Aura"
        }

        val title = fileName.substringBeforeLast('.')

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_RINGTONE, type == ContentType.RINGTONE)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, type == ContentType.NOTIFICATION)
            put(MediaStore.Audio.Media.IS_ALARM, type == ContentType.ALARM)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        val written = try {
            resolver.openOutputStream(uri)?.use {
                writeContent(it)
                true
            } ?: false
        } catch (_: Exception) {
            false
        }

        if (!written) {
            resolver.delete(uri, null, null)
            return null
        }

        // Mark as complete
        if (Build.VERSION.SDK_INT >= 29) {
            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            val published = runCatching {
                resolver.update(uri, contentValues, null, null) > 0
            }.getOrDefault(false)
            if (!published) {
                resolver.delete(uri, null, null)
                return null
            }
        }

        return uri
    }

    private fun saveUrlToMediaStore(
        fileName: String,
        type: ContentType,
        url: String,
        maxBytes: Long,
    ): Uri? {
        val limit = maxBytes.coerceIn(1L, MAX_APPLY_BYTES)
        if (isLocalMediaLocator(url)) {
            val tempFile = stageLocalMediaLocator(
                context = context,
                locator = url,
                tempDirectoryName = "audio_apply",
                prefix = "aura_sound_",
                maxBytes = limit,
            )
            return try {
                saveLocalFileToMediaStore(fileName, type, tempFile)
            } finally {
                tempFile.delete()
            }
        }
        val request = Request.Builder().url(url).build()
        return okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${resp.code}")
            }
            val body = resp.body ?: throw IllegalStateException("Empty response body")
            // Bound the download — ringtones/notifications/alarms are short clips. A hostile
            // or misresolved URL returning an endless stream would otherwise write into
            // MediaStore until the user's storage fills. Matches DownloadManager's ceiling.
            val advertised = body.contentLength()
            if (advertised in 1..Long.MAX_VALUE && advertised > limit) {
                throw IllegalStateException("Sound file too large (${advertised / (1024 * 1024)} MB)")
            }
            val tempDir = File(context.cacheDir, "audio_apply").apply { mkdirs() }
            val tempFile = File.createTempFile("aura_sound_", ".tmp", tempDir)
            try {
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        copyStreamCapped(input, output, limit)
                    }
                }
                saveLocalFileToMediaStore(fileName, type, tempFile)
            } finally {
                tempFile.delete()
            }
        }
    }

    private fun stageSoundLocator(locator: String): File {
        if (isLocalMediaLocator(locator)) {
            return stageLocalMediaLocator(
                context = context,
                locator = locator,
                tempDirectoryName = "audio_apply",
                prefix = "aura_sound_original_",
                maxBytes = MAX_APPLY_BYTES,
            )
        }
        val request = Request.Builder().url(locator).build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty response body")
            if (advertisedLengthExceeds(body.contentLength(), MAX_APPLY_BYTES)) {
                throw IllegalStateException("Sound file too large")
            }
            val directory = File(context.cacheDir, "audio_apply").apply { mkdirs() }
            File.createTempFile("aura_sound_original_", ".tmp", directory).also { staged ->
                try {
                    body.byteStream().use { input ->
                        staged.outputStream().use { output ->
                            copyStreamCapped(input, output, MAX_APPLY_BYTES)
                        }
                    }
                    if (staged.length() <= 0L) throw IllegalStateException("Empty response body")
                } catch (error: Exception) {
                    staged.delete()
                    throw error
                }
            }
        }
    }

    private suspend fun prepareCompatibleSoundCopy(
        source: File,
        sourceRecordId: String?,
        sourceHash: String,
        durationMs: Long,
        reason: String,
    ): File {
        if (durationMs <= 0L) throw IllegalStateException("Sound duration could not be read")
        val key = mediaOptimizationKey("sound", sourceHash, "m4a", durationMs)
        mediaCopyStore.reusableCopy(sourceRecordId, key)?.let { return it.file }
        val rendered = audioTrimmer.trim(
            inputPath = source.absolutePath,
            startMs = 0L,
            endMs = durationMs,
            outputFileName = "Aura_compatible_sound",
            exportFormat = AudioExportFormat.M4A,
            bitrateKbps = AudioExportFormat.M4A.defaultBitrateKbps,
        ).getOrThrow()
        val renderedFile = File(rendered)
        return try {
            if (sourceRecordId == null) {
                renderedFile
            } else {
                mediaCopyStore.prepareCopy(
                    downloadId = sourceRecordId,
                    sourceIdentity = sourceHash,
                    optimizationKey = key,
                    reason = reason,
                    extension = "m4a",
                    expectedBytes = renderedFile.length(),
                    maxBytes = MAX_APPLY_BYTES,
                ) { pending ->
                    renderedFile.inputStream().use { input ->
                        pending.outputStream().use { output ->
                            copyStreamCapped(input, output, MAX_APPLY_BYTES)
                        }
                    }
                }.file
            }
        } finally {
            if (sourceRecordId != null) renderedFile.delete()
        }
    }

    private companion object {
        private const val MAX_APPLY_BYTES = 64L * 1024 * 1024
    }

    private fun saveLocalFileToMediaStore(
        fileName: String,
        type: ContentType?,
        file: File,
    ): Uri? {
        if (file.length() > MAX_APPLY_BYTES) {
            throw java.io.IOException("Sound file too large: ${file.length()} > $MAX_APPLY_BYTES bytes")
        }
        val sniffed = requireSniffedMediaFile(file, MediaFamily.AUDIO, "Sound")
        return saveToMediaStore(normalizeMediaFileName(fileName, sniffed), sniffed.mimeType, type) { output ->
            FileInputStream(file).use { input -> copyStreamCapped(input, output, MAX_APPLY_BYTES) }
        }
    }
}
