package com.freevibe.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.freevibe.MainActivity
import com.freevibe.R
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.repository.WallpaperRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Daily wallpaper notification. Uses Bing Daily first, then Wallhaven toplist
 * as a fallback so the worker is not coupled to retired Reddit JSON endpoints.
 */
@HiltWorker
class DailyWallpaperWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val wallpaperRepository: WallpaperRepository,
    private val okHttpClient: OkHttpClient,
    private val receiptStore: BackgroundWorkReceiptStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val wallpaper = wallpaperRepository.getWallpaperOfTheDay()
                ?: return retryReceipt("no eligible Bing or Wallhaven daily wallpaper was available")

            // Download thumbnail for notification
            val thumbUrl = wallpaper.thumbnailUrl.takeIf { it.startsWith("http") }
                ?: wallpaper.fullUrl
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    okHttpClient.newCall(Request.Builder().url(thumbUrl).build()).execute().use { resp ->
                        val body = resp.body ?: return@use null
                        // Notification thumbs should be tiny; cap at 4 MB so a compromised feed
                        // can't force a giant byte[] + bitmap into a background worker.
                        val advertised = body.contentLength()
                        if (advertisedLengthExceeds(advertised, DAILY_THUMB_MAX_BYTES)) return@use null
                        val bytes = readStreamCapped(body.byteStream(), DAILY_THUMB_MAX_BYTES)
                        if (bytes.isEmpty()) return@use null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    null
                }
            }

            try {
                createNotificationChannel()

                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return successReceipt()
                }

                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("daily_wallpaper_id", wallpaper.id)
                    putExtra("daily_wallpaper_url", wallpaper.fullUrl)
                    putExtra("daily_wallpaper_thumb", wallpaper.thumbnailUrl)
                    putExtra("daily_wallpaper_source", wallpaper.source.name)
                    putExtra("daily_wallpaper_width", wallpaper.width)
                    putExtra("daily_wallpaper_height", wallpaper.height)
                }
                val pendingIntent = PendingIntent.getActivity(
                    applicationContext, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                val sourceName = wallpaper.dailySourceName()
                val sizeText = if (wallpaper.width > 0 && wallpaper.height > 0) {
                    "${wallpaper.width}x${wallpaper.height} "
                } else {
                    ""
                }

                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Wallpaper of the Day")
                    .setContentText("$sourceName daily pick ${sizeText}- tap to preview")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .apply {
                        bitmap?.let {
                            setLargeIcon(it)
                            setStyle(NotificationCompat.BigPictureStyle()
                                .bigPicture(it)
                                .setSummaryText("$sourceName daily pick"))
                        }
                    }
                    .build()

                NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            } finally {
                bitmap?.recycle()
            }
            successReceipt()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            receiptStore.recordRetry(
                uniqueWorkName = WORK_NAME,
                errorClass = e.javaClass.simpleName,
                deferralReason = "daily wallpaper worker failed; check Bing/Wallhaven provider availability and network state",
            )
            Result.retry()
        }
    }

    private fun successReceipt(): Result {
        receiptStore.recordSuccess(WORK_NAME)
        return Result.success()
    }

    private fun retryReceipt(reason: String): Result {
        receiptStore.recordRetry(
            uniqueWorkName = WORK_NAME,
            deferralReason = reason,
        )
        return Result.retry()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Daily Wallpaper",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Daily wallpaper picks from active wallpaper sources" }
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun Wallpaper.dailySourceName(): String = when (source) {
        ContentSource.BING -> "Bing"
        ContentSource.WALLHAVEN -> "Wallhaven"
        else -> source.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    companion object {
        const val WORK_NAME = "daily_wallpaper"
        const val CHANNEL_ID = "daily_wallpaper"
        const val NOTIFICATION_ID = 42
        private const val DAILY_THUMB_MAX_BYTES = 4L * 1024L * 1024L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyWallpaperWorker>(
                24, TimeUnit.HOURS,
            )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(8, TimeUnit.HOURS)
                // Match AutoWallpaperWorker / WeatherUpdateWorker: exponential backoff on retry.
                // Default is 30s linear, which burns battery retrying transient network errors
                // (Reddit 5xx, temporary DNS) at short intervals.
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
