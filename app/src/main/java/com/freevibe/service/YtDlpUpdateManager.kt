package com.freevibe.service

import android.content.Context
import com.freevibe.di.IoDispatcher
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class YtDlpUpdateStatus {
    NEVER_RUN,
    CHECKING,
    ALREADY_UP_TO_DATE,
    UPDATED_PENDING_VALIDATION,
    VALIDATED,
    ROLLED_BACK,
    FAILED,
}

data class YtDlpUpdateSnapshot(
    val activeVersion: String? = null,
    val activeVersionName: String? = null,
    val lastStatus: YtDlpUpdateStatus = YtDlpUpdateStatus.NEVER_RUN,
    val lastAttemptAtMs: Long = 0L,
    val lastSuccessAtMs: Long = 0L,
    val lastError: String? = null,
    val pendingValidation: Boolean = false,
    val rollbackAvailable: Boolean = false,
)

data class YtDlpUpdateResult(
    val status: YtDlpUpdateStatus,
    val snapshot: YtDlpUpdateSnapshot,
)

internal interface YtDlpRuntime {
    fun init(context: Context)
    fun updateStable(context: Context): YoutubeDL.UpdateStatus?
    fun version(context: Context): String?
    fun versionName(context: Context): String?
    fun initYtDlp(context: Context, runtimeDir: File)
}

private object RealYtDlpRuntime : YtDlpRuntime {
    override fun init(context: Context) {
        YoutubeDL.getInstance().init(context)
    }

    override fun updateStable(context: Context): YoutubeDL.UpdateStatus? =
        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)

    override fun version(context: Context): String? =
        runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()

    override fun versionName(context: Context): String? =
        runCatching { YoutubeDL.getInstance().versionName(context) }.getOrNull()

    override fun initYtDlp(context: Context, runtimeDir: File) {
        YoutubeDL.getInstance().init_ytdlp(context, runtimeDir)
    }
}

@Singleton
class YtDlpUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    @Volatile
    internal var runtime: YtDlpRuntime = RealYtDlpRuntime

    private val mutex = Mutex()
    private val metadataPrefs by lazy {
        context.getSharedPreferences(METADATA_PREFS, Context.MODE_PRIVATE)
    }
    private val libraryPrefs by lazy {
        context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE)
    }

    fun snapshot(): YtDlpUpdateSnapshot = readSnapshot()

    suspend fun updateStable(): YtDlpUpdateResult = withContext(ioDispatcher) {
        mutex.withLock {
            val existing = readSnapshot()
            if (existing.pendingValidation && rollbackDir().exists()) {
                return@withLock YtDlpUpdateResult(
                    YtDlpUpdateStatus.UPDATED_PENDING_VALIDATION,
                    existing,
                )
            }

            val attemptedAt = System.currentTimeMillis()
            writeMetadata {
                putString(KEY_LAST_STATUS, YtDlpUpdateStatus.CHECKING.name)
                putLong(KEY_LAST_ATTEMPT_AT_MS, attemptedAt)
                remove(KEY_LAST_ERROR)
            }

            try {
                runtime.init(context)
                val runtimeDir = runtimeDir()
                prepareRollback(runtimeDir)
                rememberPreviousLibraryVersion()

                val updateStatus = runtime.updateStable(context)
                    ?: throw IllegalStateException("yt-dlp update returned no status")
                val mappedStatus = when (updateStatus) {
                    YoutubeDL.UpdateStatus.DONE -> YtDlpUpdateStatus.UPDATED_PENDING_VALIDATION
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> YtDlpUpdateStatus.ALREADY_UP_TO_DATE
                }
                val pendingValidation = updateStatus == YoutubeDL.UpdateStatus.DONE
                if (!pendingValidation) {
                    cleanupRollback()
                    forgetPreviousLibraryVersion()
                }
                writeMetadata {
                    putString(KEY_LAST_STATUS, mappedStatus.name)
                    putLong(KEY_LAST_ATTEMPT_AT_MS, attemptedAt)
                    putLong(KEY_LAST_SUCCESS_AT_MS, System.currentTimeMillis())
                    putBoolean(KEY_PENDING_VALIDATION, pendingValidation)
                    remove(KEY_LAST_ERROR)
                }
                YtDlpUpdateResult(mappedStatus, readSnapshot())
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                restoreRollbackIfAvailable()
                writeMetadata {
                    putString(KEY_LAST_STATUS, YtDlpUpdateStatus.FAILED.name)
                    putLong(KEY_LAST_ATTEMPT_AT_MS, attemptedAt)
                    putBoolean(KEY_PENDING_VALIDATION, false)
                    putString(KEY_LAST_ERROR, RequestRedactor.redact(error.message ?: error.javaClass.simpleName))
                }
                YtDlpUpdateResult(YtDlpUpdateStatus.FAILED, readSnapshot())
            }
        }
    }

    suspend fun recordExtractionSuccess() = withContext(ioDispatcher) {
        mutex.withLock {
            if (!metadataPrefs.getBoolean(KEY_PENDING_VALIDATION, false)) return@withLock
            cleanupRollback()
            forgetPreviousLibraryVersion()
            writeMetadata {
                putString(KEY_LAST_STATUS, YtDlpUpdateStatus.VALIDATED.name)
                putBoolean(KEY_PENDING_VALIDATION, false)
                remove(KEY_LAST_ERROR)
            }
        }
    }

    suspend fun recordExtractionFailure(error: Throwable): Boolean = withContext(ioDispatcher) {
        mutex.withLock {
            if (!metadataPrefs.getBoolean(KEY_PENDING_VALIDATION, false)) return@withLock false
            val restored = restoreRollbackIfAvailable()
            val status = if (restored) YtDlpUpdateStatus.ROLLED_BACK else YtDlpUpdateStatus.FAILED
            writeMetadata {
                putString(KEY_LAST_STATUS, status.name)
                putBoolean(KEY_PENDING_VALIDATION, false)
                putString(KEY_LAST_ERROR, RequestRedactor.redact(error.message ?: error.javaClass.simpleName))
            }
            restored
        }
    }

    private fun readSnapshot(): YtDlpUpdateSnapshot =
        YtDlpUpdateSnapshot(
            activeVersion = runtime.version(context).orNullIfBlank(),
            activeVersionName = runtime.versionName(context).orNullIfBlank(),
            lastStatus = metadataPrefs.getString(KEY_LAST_STATUS, null)
                ?.let { runCatching { YtDlpUpdateStatus.valueOf(it) }.getOrNull() }
                ?: YtDlpUpdateStatus.NEVER_RUN,
            lastAttemptAtMs = metadataPrefs.getLong(KEY_LAST_ATTEMPT_AT_MS, 0L),
            lastSuccessAtMs = metadataPrefs.getLong(KEY_LAST_SUCCESS_AT_MS, 0L),
            lastError = metadataPrefs.getString(KEY_LAST_ERROR, null).orNullIfBlank(),
            pendingValidation = metadataPrefs.getBoolean(KEY_PENDING_VALIDATION, false),
            rollbackAvailable = rollbackDir().exists(),
        )

    private fun prepareRollback(runtimeDir: File) {
        val rollbackDir = rollbackDir()
        val stagingDir = File(rollbackDir.parentFile, ROLLBACK_STAGING_NAME)
        stagingDir.deleteRecursively()
        if (runtimeDir.exists()) {
            runtimeDir.copyRecursively(target = stagingDir, overwrite = true)
        }
        rollbackDir.deleteRecursively()
        if (stagingDir.exists()) {
            stagingDir.renameTo(rollbackDir)
        }
    }

    private fun restoreRollbackIfAvailable(): Boolean {
        val rollbackDir = rollbackDir()
        if (!rollbackDir.exists()) return false
        val runtimeDir = runtimeDir()
        val staleDir = File(runtimeDir.parentFile, STALE_RUNTIME_NAME)
        staleDir.deleteRecursively()
        if (runtimeDir.exists()) {
            if (!runtimeDir.renameTo(staleDir)) {
                runtimeDir.deleteRecursively()
            }
        }
        val restored = rollbackDir.copyRecursively(target = runtimeDir, overwrite = true)
        if (!restored) {
            runtimeDir.deleteRecursively()
            if (staleDir.exists()) staleDir.renameTo(runtimeDir)
            return false
        }
        staleDir.deleteRecursively()
        restorePreviousLibraryVersion()
        return runCatching {
            runtime.initYtDlp(context, runtimeDir)
            true
        }.getOrDefault(false)
    }

    private fun cleanupRollback() {
        rollbackDir().deleteRecursively()
    }

    private fun rememberPreviousLibraryVersion() {
        val previousVersion = libraryPrefs.getString(LIBRARY_VERSION_KEY, null)
        val previousVersionName = libraryPrefs.getString(LIBRARY_VERSION_NAME_KEY, null)
        writeMetadata {
            if (previousVersion == null) {
                remove(KEY_PREVIOUS_VERSION)
            } else {
                putString(KEY_PREVIOUS_VERSION, previousVersion)
            }
            if (previousVersionName == null) {
                remove(KEY_PREVIOUS_VERSION_NAME)
            } else {
                putString(KEY_PREVIOUS_VERSION_NAME, previousVersionName)
            }
        }
    }

    private fun restorePreviousLibraryVersion() {
        val previousVersion = metadataPrefs.getString(KEY_PREVIOUS_VERSION, null)
        val previousVersionName = metadataPrefs.getString(KEY_PREVIOUS_VERSION_NAME, null)
        libraryPrefs.edit()
            .apply {
                if (previousVersion == null) {
                    remove(LIBRARY_VERSION_KEY)
                } else {
                    putString(LIBRARY_VERSION_KEY, previousVersion)
                }
                if (previousVersionName == null) {
                    remove(LIBRARY_VERSION_NAME_KEY)
                } else {
                    putString(LIBRARY_VERSION_NAME_KEY, previousVersionName)
                }
            }
            .apply()
    }

    private fun forgetPreviousLibraryVersion() {
        writeMetadata {
            remove(KEY_PREVIOUS_VERSION)
            remove(KEY_PREVIOUS_VERSION_NAME)
        }
    }

    private fun runtimeDir(): File =
        File(File(context.noBackupFilesDir, YTDLP_ANDROID_DIR), YTDLP_DIR_NAME)

    private fun rollbackDir(): File =
        File(File(context.noBackupFilesDir, YTDLP_ANDROID_DIR), ROLLBACK_DIR_NAME)

    private inline fun writeMetadata(block: android.content.SharedPreferences.Editor.() -> Unit) {
        metadataPrefs.edit().apply(block).apply()
    }

    private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    companion object {
        private const val METADATA_PREFS = "freevibe_ytdlp_update"
        private const val LIBRARY_PREFS = "youtubedl-android"
        private const val YTDLP_ANDROID_DIR = "youtubedl-android"
        private const val YTDLP_DIR_NAME = "yt-dlp"
        private const val ROLLBACK_DIR_NAME = "yt-dlp.rollback"
        private const val ROLLBACK_STAGING_NAME = "yt-dlp.rollback.tmp"
        private const val STALE_RUNTIME_NAME = "yt-dlp.stale"
        private const val KEY_LAST_STATUS = "lastStatus"
        private const val KEY_LAST_ATTEMPT_AT_MS = "lastAttemptAtMs"
        private const val KEY_LAST_SUCCESS_AT_MS = "lastSuccessAtMs"
        private const val KEY_LAST_ERROR = "lastError"
        private const val KEY_PENDING_VALIDATION = "pendingValidation"
        private const val KEY_PREVIOUS_VERSION = "previousVersion"
        private const val KEY_PREVIOUS_VERSION_NAME = "previousVersionName"
        private const val LIBRARY_VERSION_KEY = "dlpVersion"
        private const val LIBRARY_VERSION_NAME_KEY = "dlpVersionName"
    }
}
