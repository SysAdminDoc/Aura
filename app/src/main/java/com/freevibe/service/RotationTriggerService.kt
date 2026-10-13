package com.freevibe.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.freevibe.util.appLaunchIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that dynamically registers broadcast receivers for
 * [Intent.ACTION_USER_PRESENT] (unlock) and [Intent.ACTION_SCREEN_OFF].
 *
 * Required as a foreground service because both broadcasts are on the
 * background-restriction allow-list (Android 8+) only when the receiver is
 * runtime-registered from a long-lived process. A manifest-declared receiver
 * would not fire on phone unlock.
 *
 * ROADMAP NX-6 — first slice: per-unlock + screen-off pre-stage rotation
 * triggers. Off-by-default; the user opts in via two new Settings toggles
 * backed by `PreferencesManager.rotateOnUnlock` and `rotateOnScreenOff`.
 *
 * Each trigger enqueues a one-shot expedited `AutoWallpaperWorker` (same
 * worker the periodic rotation already uses) with the same constraints the
 * periodic schedule respects. WorkManager handles retries and battery
 * coalescing for us.
 *
 * Not in scope for this slice:
 * - Per-app rotation exclusion (needs `PACKAGE_USAGE_STATS`)
 * - Sub-15-minute periodic rotation (AlarmManager-backed)
 * - One-tap-shuffle Glance widget (bundled with NX-2 widget polish)
 */
class RotationTriggerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var receiver: BroadcastReceiver? = null
    @Volatile private var screenOffEnabled = false
    @Volatile private var unlockEnabled = false
    // Main-thread only: bumped by every onStartCommand.
    private var commandGeneration = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        RotationTriggerRecovery.clear(this)
        registerTriggers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val generation = ++commandGeneration
        if (intent == null) {
            // START_STICKY restart after process death: the volatile flags are gone
            // and defaulting them to false would stopSelf() — silently killing
            // rotate-on-unlock until the app is next opened. Re-read the toggles off
            // the main thread; triggers stay idle until the read lands.
            val prefs = com.freevibe.data.local.PreferencesManager(applicationContext)
            scope.launch {
                val (unlock, screenOff) = withContext(Dispatchers.IO) {
                    prefs.rotateOnUnlock.first() to prefs.rotateOnScreenOff.first()
                }
                // An explicit start that arrived during the read carries newer toggles.
                if (generation != commandGeneration) return@launch
                unlockEnabled = unlock
                screenOffEnabled = screenOff
                stopIfIdle(startId)
            }
        } else {
            // Allow Intent extras to update which triggers are active without
            // restarting the service. EXTRA_UNLOCK / EXTRA_SCREEN_OFF default to the
            // current state so the caller can selectively flip one.
            screenOffEnabled = intent.getBooleanExtra(EXTRA_SCREEN_OFF, screenOffEnabled)
            unlockEnabled = intent.getBooleanExtra(EXTRA_UNLOCK, unlockEnabled)
            stopIfIdle(startId)
        }
        return START_STICKY
    }

    private fun stopIfIdle(startId: Int) {
        if (!screenOffEnabled && !unlockEnabled) {
            stopSelf(startId)
        }
    }

    private fun startInForeground() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            // Never null: handing PendingIntent.getActivity a null intent would crash the service
            // before it reaches startForeground.
            appLaunchIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif: Notification = NotificationCompat.Builder(this, NotificationChannels.ROTATION_TRIGGERS)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("Aura — wallpaper triggers")
            .setContentText("Rotating on unlock / screen off")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    private fun registerTriggers() {
        val rx = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    // Unlock and screen-off rotations are automatic, so they leave the
                    // periodic countdown alone; tile and automation taps restart it.
                    Intent.ACTION_USER_PRESENT -> if (unlockEnabled) enqueueRotation(context, restartCountdown = false)
                    Intent.ACTION_SCREEN_OFF -> if (screenOffEnabled) enqueueRotation(context, restartCountdown = false)
                }
            }
        }
        receiver = rx
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // ContextCompat is available but the receiver does not export any callbacks
        // back to other apps — RECEIVER_NOT_EXPORTED is the correct flag on API 33+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(rx, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(rx, filter)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 9241
        const val EXTRA_UNLOCK = "extra_unlock"
        const val EXTRA_SCREEN_OFF = "extra_screen_off"
        const val WORK_NAME = "rotation_trigger_oneshot"

        /**
         * Idempotent start: if any trigger is enabled, start (or update) the service;
         * if both are disabled, stop it. A background-start denial is persisted and
         * retried when the user next resumes Aura.
         */
        fun reconcile(context: Context, unlock: Boolean, screenOff: Boolean) {
            if (unlock || screenOff) {
                val intent = Intent(context, RotationTriggerService::class.java).apply {
                    putExtra(EXTRA_UNLOCK, unlock)
                    putExtra(EXTRA_SCREEN_OFF, screenOff)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (_: IllegalStateException) {
                    // Android 8-11 use IllegalStateException for background FGS
                    // denials; Android 12+ throws its ForegroundServiceStartNotAllowedException
                    // subclass. Persist the exact request and make the degraded state visible.
                    RotationTriggerRecovery.markPending(context, unlock, screenOff)
                }
            } else {
                RotationTriggerRecovery.clear(context)
                context.stopService(Intent(context, RotationTriggerService::class.java))
            }
        }

        /**
         * Enqueue a one-shot rotation. Reuses [AutoWallpaperWorker] (the periodic
         * worker already does the right thing — it reads source / target / shuffle
         * from prefs and applies). A tap or automation action is expedited so the
         * change shows right away.
         */
        internal fun enqueueRotation(context: Context, restartCountdown: Boolean = true) {
            val request = OneTimeWorkRequestBuilder<AutoWallpaperWorker>()
                .setConstraints(triggeredRotationConstraints(restartCountdown))
                .setInputData(
                    workDataOf(
                        AutoWallpaperWorker.RECEIPT_WORK_NAME_KEY to WORK_NAME,
                        AutoWallpaperWorker.TRIGGERED_ROTATION_KEY to true,
                        AutoWallpaperWorker.RESTART_COUNTDOWN_KEY to restartCountdown,
                    ),
                )
                .apply { if (restartCountdown) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, triggeredRotationPolicy(restartCountdown), request)
        }
    }
}

/**
 * Passive triggers coalesce so a chatty unlock can't queue 10 rotations. An explicit tap or
 * automation action replaces any trigger work instead of being dropped, since only it restarts the
 * countdown. Replacing never cuts an apply in half: [AutoWallpaperWorker] applies NonCancellable
 * under its run lock, so a run that is already applying finishes and the tap applies after it.
 * Chaining the tap behind the running work instead would fail it whenever that run failed.
 */
internal fun triggeredRotationPolicy(restartCountdown: Boolean): ExistingWorkPolicy =
    if (restartCountdown) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

/**
 * There is no network constraint: the work has to start offline to apply a wallpaper saved ahead
 * of time, and [AutoWallpaperWorker] checks the connection itself before it fetches anything.
 * WorkManager refuses to build expedited work with a battery constraint, so only a tap or
 * automation action is expedited; unlock and screen-off rotations run as ordinary work and keep
 * the battery floor.
 */
internal fun triggeredRotationConstraints(restartCountdown: Boolean): Constraints =
    Constraints.Builder()
        .apply { if (!restartCountdown) setRequiresBatteryNotLow(true) }
        .build()
