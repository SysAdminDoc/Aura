package com.freevibe.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.freevibe.data.model.ContentType
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
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            if (!canWriteSettings()) {
                throw SecurityException("WRITE_SETTINGS permission not granted")
            }

            // Save to MediaStore
            val uri = saveUrlToMediaStore(fileName.replace(SANITIZE_REGEX, "_"), type, url)
                ?: throw IllegalStateException("Failed to save audio to MediaStore")

            // Set as system sound
            val ringtoneType = when (type) {
                ContentType.RINGTONE -> RingtoneManager.TYPE_RINGTONE
                ContentType.NOTIFICATION -> RingtoneManager.TYPE_NOTIFICATION
                ContentType.ALARM -> RingtoneManager.TYPE_ALARM
                else -> throw IllegalArgumentException("Invalid sound type: $type")
            }
            RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType, uri)

            uri
        }
    }

    /** Save audio without applying - just download to storage */
    suspend fun downloadOnly(
        url: String,
        fileName: String,
        type: ContentType,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            saveUrlToMediaStore(fileName.replace(SANITIZE_REGEX, "_"), type, url)
                ?: throw IllegalStateException("Failed to save audio to MediaStore")
        }
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

            val ringtoneType = when (type) {
                ContentType.RINGTONE -> RingtoneManager.TYPE_RINGTONE
                ContentType.NOTIFICATION -> RingtoneManager.TYPE_NOTIFICATION
                ContentType.ALARM -> RingtoneManager.TYPE_ALARM
                else -> throw IllegalArgumentException("Invalid sound type: $type")
            }
            RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType, uri)

            uri
        }
    }

    private fun saveToMediaStore(
        fileName: String,
        mimeType: String,
        type: ContentType,
        writeContent: (OutputStream) -> Unit,
    ): Uri? {
        val relativePath = when (type) {
            ContentType.RINGTONE -> Environment.DIRECTORY_RINGTONES
            ContentType.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
            ContentType.ALARM -> Environment.DIRECTORY_ALARMS
            else -> Environment.DIRECTORY_MUSIC
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
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
            resolver.update(uri, contentValues, null, null)
        }

        return uri
    }

    private fun saveUrlToMediaStore(
        fileName: String,
        type: ContentType,
        url: String,
    ): Uri? {
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
            if (advertised in 1..Long.MAX_VALUE && advertised > MAX_APPLY_BYTES) {
                throw IllegalStateException("Sound file too large (${advertised / (1024 * 1024)} MB)")
            }
            val tempDir = File(context.cacheDir, "audio_apply").apply { mkdirs() }
            val tempFile = File.createTempFile("aura_sound_", ".tmp", tempDir)
            try {
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        copyStreamCapped(input, output, MAX_APPLY_BYTES)
                    }
                }
                saveLocalFileToMediaStore(fileName, type, tempFile)
            } finally {
                tempFile.delete()
            }
        }
    }

    private companion object {
        private const val MAX_APPLY_BYTES = 64L * 1024 * 1024
    }

    private fun saveLocalFileToMediaStore(
        fileName: String,
        type: ContentType,
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
