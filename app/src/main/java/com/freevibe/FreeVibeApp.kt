package com.freevibe

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.service.NotificationChannels
import com.freevibe.service.CrashDiagnosticsCollector
import com.freevibe.service.CrashDiagnosticsText
import com.freevibe.service.AppCheckInstaller
import com.freevibe.service.OfflineFavoritesManager
import com.freevibe.service.PathBackedRecordReconciler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class FreeVibeApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var wallpaperCacheManager: WallpaperCacheManager

    @Inject
    lateinit var offlineFavoritesManager: OfflineFavoritesManager

    @Inject
    lateinit var systemThemeListener: com.freevibe.service.SystemThemeListener

    @Inject
    lateinit var pathBackedRecordReconciler: PathBackedRecordReconciler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(okHttpClient)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25) // 25% of available app memory
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(cacheDir, "coil_cache"))
                .maxSizeBytes(256L * 1024 * 1024) // 256 MB — wallpaper app needs generous image cache
                .build()
        }
        .crossfade(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        setupCrashLogging()
        installAppCheck()
        NotificationChannels.createAll(this)
        evictStaleCaches()
        startSystemThemeListener()
        initYtDlp()
        enqueueAuraOriginalsDownload()
        reconcileRotationTriggers()
    }

    private fun installAppCheck() {
        try {
            AppCheckInstaller.install(this)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("FreeVibeApp", "App Check init failed: ${e.message}")
        }
    }

    /**
     * NX-6: on cold start, read the rotate-on-unlock + rotate-on-screen-off prefs
     * and reconcile the foreground [RotationTriggerService]. The service stays
     * idle (no notification, no work) until at least one trigger is opted in.
     * Settings UI calls [RotationTriggerService.reconcile] directly on toggle.
     */
    private fun reconcileRotationTriggers() {
        appScope.launch {
            try {
                val prefs = com.freevibe.data.local.PreferencesManager(this@FreeVibeApp)
                val unlock = prefs.rotateOnUnlock.first()
                val screenOff = prefs.rotateOnScreenOff.first()
                if (unlock || screenOff) {
                    com.freevibe.service.RotationTriggerService.reconcile(
                        this@FreeVibeApp, unlock = unlock, screenOff = screenOff,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (BuildConfig.DEBUG) Log.w("FreeVibeApp", "Rotation trigger reconcile failed: ${e.message}")
            }
        }
    }

    /**
     * Roadmap N-5: schedule the Aura Originals CC0 sound pack download on Wi-Fi.
     * Worker is enqueued every cold start with KEEP policy, so existing successful
     * downloads aren't redone and the pack converges idempotently.
     */
    private fun enqueueAuraOriginalsDownload() {
        try {
            com.freevibe.service.AuraOriginalsDownloader.enqueue(this)
        } catch (e: Exception) {
            // Must never crash app startup on a WorkManager queue failure.
            if (BuildConfig.DEBUG) Log.w("FreeVibeApp", "AuraOriginals enqueue failed: ${e.message}")
        }
    }

    private fun initYtDlp() {
        appScope.launch {
            try {
                com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this@FreeVibeApp)
                com.yausername.ffmpeg.FFmpeg.getInstance().init(this@FreeVibeApp)
                if (BuildConfig.DEBUG) Log.d("AuraApp", "yt-dlp + FFmpeg initialized")
            } catch (e: Throwable) {
                // Must catch CancellationException-as-Throwable here? Throwable catches it, but
                // we never cancel appScope before process death, so swallowing is OK. We do NOT
                // rethrow: init-failure of yt-dlp should degrade the YouTube tab, not kill the app.
                if (BuildConfig.DEBUG) Log.e("AuraApp", "yt-dlp init failed: ${e.message}")
            }
        }
    }


    private fun setupCrashLogging() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logFile = File(filesDir, CrashDiagnosticsCollector.CRASH_LOG_FILE_NAME)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                logFile.appendText(
                    CrashDiagnosticsText.formatCrashEntry(timestamp, thread.name, throwable),
                    Charsets.UTF_8,
                )
                // Keep log file reasonable (max 500KB) — trim tail without full read
                if (logFile.length() > 512_000) {
                    try {
                        java.io.RandomAccessFile(logFile, "r").use { raf ->
                            val keepBytes = 256_000
                            val skipTo = raf.length() - keepBytes
                            raf.seek(skipTo.coerceAtLeast(0))
                            val tail = ByteArray(keepBytes.coerceAtMost(raf.length().toInt()))
                            raf.readFully(tail)
                            logFile.writeBytes(tail)
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                // Don't let crash logging itself crash
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun evictStaleCaches() {
        appScope.launch {
            try {
                wallpaperCacheManager.evictExpired()
                offlineFavoritesManager.pruneOrphans()
                pathBackedRecordReconciler.reconcile()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (BuildConfig.DEBUG) Log.w("FreeVibeApp", "Cache eviction failed", e)
            }
        }
    }

    private fun startSystemThemeListener() {
        appScope.launch {
            try {
                systemThemeListener.startListening()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Dark/light mode auto-switch is optional; never crash on startup
                if (BuildConfig.DEBUG) Log.w("FreeVibeApp", "System theme listener failed", e)
            }
        }
    }
}
