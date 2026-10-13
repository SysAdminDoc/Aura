@file:Suppress("DEPRECATION")

package com.freevibe.service

import android.app.WallpaperColors
import android.app.wallpaper.WallpaperDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Movie
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import com.freevibe.BuildConfig
import com.freevibe.R
import java.io.File
import kotlin.math.max

/**
 * Colour quantization only needs a rough thumbnail, and a GIF frame decoded at full
 * size on a wallpaper process is exactly the allocation this app has been bitten by.
 */
private const val COLOR_FRAME_SAMPLE_SIZE = 4

private data class BatterySnapshot(
    val percent: Int?,
    val isCharging: Boolean,
)

private data class VideoPlaybackProfile(
    val requestedFps: Int,
    val effectiveFps: Int,
    val batteryPercent: Int?,
    val isCharging: Boolean,
    val lowBatterySaverActive: Boolean,
    val systemPowerSaveMode: Boolean,
    val motionPausedForPowerSave: Boolean,
)

/**
 * Live wallpaper service that plays a video or animated GIF on the home/lock screen.
 * Uses center-crop rendering: motion fills the screen, overflow is clipped, and
 * aspect ratio is always preserved.
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    @RequiresApi(36)
    override fun onCreateEngine(description: WallpaperDescription): Engine =
        VideoEngine(readAuraWallpaperDescriptionContent(description)?.source)

    inner class VideoEngine(
        private val describedVideoPath: String? = null,
    ) : Engine(), LiveWallpaperResourceReporter {
        private val receiptStore by lazy { LiveWallpaperReceiptStore.create(this@VideoWallpaperService) }
        private var mediaPlayer: MediaPlayer? = null
        private var gifMovie: Movie? = null
        private var gifCanvasBackground: Bitmap? = null
        private var gifStartedAtMs = 0L
        private var gifFrameRunnable: Runnable? = null
        private val gifHandler = Handler(Looper.getMainLooper())
        private var currentHolder: SurfaceHolder? = null
        private var lastModified: Long = 0
        private var lastPath: String? = null
        private var screenWidth = 0
        private var screenHeight = 0
        private var visible = false
        private var powerSaveReceiverRegistered = false
        private var systemPowerSaveMode = false
        private var motionPausedForPowerSave = false
        private var activeMediaType = "none"
        private var activeProfile = VideoPlaybackProfile(
            requestedFps = 30,
            effectiveFps = 30,
            batteryPercent = null,
            isCharging = false,
            lowBatterySaverActive = false,
            systemPowerSaveMode = false,
            motionPausedForPowerSave = false,
        )
        private val powerSaveReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (
                    intent?.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ||
                    intent?.action == VIDEO_AUTO_BATTERY_SAVER_CHANGED_ACTION
                ) {
                    reconcilePowerSavePause()
                }
            }
        }
        private var telemetryRunnable: Runnable? = null
        private val telemetryHandler = Handler(Looper.getMainLooper())
        private val overlayBackgroundPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(168, 0, 0, 0)
            style = android.graphics.Paint.Style.FILL
        }
        private val overlayTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        private var gifSampleStartedAtMs = 0L
        private var gifFramesInSample = 0
        private var gifSampledFps = 0
        private var recoveryState = VideoWallpaperRecovery.reset()
        private var pendingRebuild: Runnable? = null
        private var watchdogRunnable: Runnable? = null
        private val recoveryHandler = Handler(Looper.getMainLooper())
        private var pendingResumePositionMs = 0
        private var gifValidationGeneration = 0L
        @Volatile private var destroyed = false
        private val colorPublisher = LiveWallpaperColorPublisher()
        // A video has no source bitmap lying around, so one representative frame has
        // to be decoded to describe it. That runs here rather than inline in
        // initializePlayer, which is on the main thread.
        private val colorLoader = LiveWallpaperMediaLoader("aura-video-colors")
        private var dimEnabled = false
        private val dimming = LiveWallpaperDimming(
            onRevealChanged = { if (visible) currentHolder?.let { drawGifFrame(it) } },
        )

        private val renderContext: android.content.Context
            get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                displayContext ?: this@VideoWallpaperService
            } else {
                this@VideoWallpaperService
            }

        private fun getPrefs() = getSharedPreferences(VIDEO_WALLPAPER_PREFS_NAME, MODE_PRIVATE)
        private fun getRuntimePrefs() = getSharedPreferences(VIDEO_PREFS_NAME, MODE_PRIVATE)
        private fun getVideoPath(): String? =
            describedVideoPath ?: getPrefs().getString("video_path", null)

        @RequiresApi(36)
        override fun onApplyWallpaper(which: Int): WallpaperDescription {
            val content = auraWallpaperDescriptionContent(source = getVideoPath())
            return buildAuraWallpaperDescription(
                id = auraWallpaperDescriptionId("video", AuraWallpaperDescriptionContent(source = getVideoPath())),
                title = getString(R.string.video_wallpaper_label),
                description = getString(R.string.video_wallpaper_label),
                content = content,
            )
        }
        private fun getScaleMode(): String =
            normalizeVideoWallpaperScaleMode(getPrefs().getString("scale_mode", VIDEO_WALLPAPER_SCALE_MODE_ZOOM))
        private fun getCanvasColor(): Int = getPrefs().getInt(
            VIDEO_WALLPAPER_CANVAS_COLOR_PREF,
            Color.BLACK,
        ) or 0xFF000000.toInt()
        private fun getCanvasMode(): String = getPrefs().getString(
            VIDEO_WALLPAPER_CANVAS_MODE_PREF,
            com.freevibe.data.model.FitCanvasMode.AMOLED_BLACK.preferenceValue,
        ).orEmpty()
        private fun isCanvasBaked(): Boolean = getPrefs().getBoolean(VIDEO_WALLPAPER_CANVAS_BAKED_PREF, false)
        private fun getPlaybackSpeed(): Float =
            getRuntimePrefs().getFloat(VIDEO_PLAYBACK_SPEED_PREF, 1.0f).takeIf { it > 0 } ?: 1.0f
        private fun getRequestedFpsLimit(): Int =
            sanitizeVideoFpsLimit(getRuntimePrefs().getInt(VIDEO_FPS_LIMIT_PREF, 30))
        private fun isFpsOverlayEnabled(): Boolean =
            getRuntimePrefs().getBoolean(VIDEO_FPS_OVERLAY_PREF, false)
        private fun isAutoBatterySaverEnabled(): Boolean =
            getRuntimePrefs().getBoolean(VIDEO_AUTO_BATTERY_SAVER_PREF, true)

        private fun loadDimmingFromPrefs() {
            dimEnabled = getPrefs().getBoolean(LIVE_WALLPAPER_DIM_ENABLED_PREF, false)
        }

        private fun loadColorPublicationFromPrefs() {
            val enabled = getPrefs().getBoolean(
                LIVE_WALLPAPER_COLORS_ENABLED_PREF,
                LIVE_WALLPAPER_COLORS_ENABLED_DEFAULT,
            )
            if (colorPublisher.setEnabled(enabled)) notifyWallpaperColorsChanged()
        }

        @RequiresApi(android.os.Build.VERSION_CODES.O_MR1)
        override fun onComputeColors(): WallpaperColors? = colorPublisher.current

        /**
         * Describes the clip to the system by quantizing one frame, keyed on path plus
         * modification time so a re-applied identical file costs nothing and a replaced
         * file at the same path still recomputes.
         */
        private fun refreshWallpaperColors(path: String, modifiedAt: Long) {
            val token = "$path@$modifiedAt"
            colorLoader.request {
                val frame = decodeColorFrame(path) ?: return@request
                val changed = try {
                    colorPublisher.update(token, frame)
                } finally {
                    frame.recycle()
                }
                if (changed) recoveryHandler.post { if (!destroyed) notifyWallpaperColorsChanged() }
            }
        }

        private fun decodeColorFrame(path: String): Bitmap? {
            if (path.endsWith(".gif", ignoreCase = true)) {
                // MediaMetadataRetriever cannot read GIF; BitmapFactory hands back its
                // first frame, which is as representative as any single frame of a loop.
                return try {
                    BitmapFactory.decodeFile(
                        path,
                        BitmapFactory.Options().apply { inSampleSize = COLOR_FRAME_SAMPLE_SIZE },
                    )
                } catch (_: Throwable) {
                    null
                }
            }
            return decodeBoundedVideoFrame(path)
        }

        private fun resolveScreenSize() {
            try {
                val wm = renderContext.getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val bounds = wm?.currentWindowMetrics?.bounds
                    screenWidth = bounds?.width() ?: 0
                    screenHeight = bounds?.height() ?: 0
                } else {
                    val metrics = android.util.DisplayMetrics()
                    @Suppress("DEPRECATION")
                    wm?.defaultDisplay?.getRealMetrics(metrics)
                    screenWidth = metrics.widthPixels
                    screenHeight = metrics.heightPixels
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoWallpaper", "Failed to resolve screen size", e)
                // Zero, not the last display's size: the scaling math then uses the surface frame.
                screenWidth = 0
                screenHeight = 0
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
        }

        override fun onTouchEvent(event: android.view.MotionEvent) {
            super.onTouchEvent(event)
            if (dimEnabled) dimming.onTouchEvent(event)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            currentHolder = holder
            resolveScreenSize()
            registerPowerSaveReceiver()
            reconcilePowerSavePause()
            receiptStore.recordSurfaceCreated(LiveWallpaperReceiptStore.ENGINE_VIDEO, getVideoPath())
            loadColorPublicationFromPrefs()
            loadDimmingFromPrefs()
            initializePlayer(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            currentHolder = null
            visible = false
            unregisterPowerSaveReceiver()
            stopTelemetryHeartbeat()
            stopPlaybackWatchdog()
            cancelPendingRebuild()
            releasePlayback()
            receiptStore.recordSurfaceDestroyed(LiveWallpaperReceiptStore.ENGINE_VIDEO)
            publishVideoTelemetry()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            receiptStore.recordVisibilityChanged(LiveWallpaperReceiptStore.ENGINE_VIDEO, visible)
            if (visible) {
                loadColorPublicationFromPrefs()
                loadDimmingFromPrefs()
                reconcilePowerSavePause()
                startTelemetryHeartbeat()
                val path = getVideoPath()
                if (path != null) {
                    val file = File(path)
                    // Re-init if the user picked a different video OR the same path's
                    // contents changed. Path comparison guards against rare cases where
                    // two different files happen to share the same lastModified timestamp.
                    if (file.exists() && (path != lastPath || file.lastModified() != lastModified)) {
                        currentHolder?.let { initializePlayer(it) }
                        return
                    }
                }
                gifMovie?.let {
                    currentHolder?.let { resumeGifPlayback(it) }
                    return
                }
                try {
                    mediaPlayer?.let {
                        if (!it.isPlaying) {
                            it.seekTo(0)
                            if (!motionPausedForPowerSave) it.start()
                        }
                    }
                } catch (_: Exception) {}
            } else {
                stopTelemetryHeartbeat()
                pauseGifPlayback()
                try { mediaPlayer?.pause() } catch (_: Exception) {}
                publishVideoTelemetry()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            destroyed = true
            visible = false
            unregisterPowerSaveReceiver()
            stopTelemetryHeartbeat()
            stopPlaybackWatchdog()
            cancelPendingRebuild()
            releasePlayback()
            colorLoader.shutdown()
            colorPublisher.clear()
            publishVideoTelemetry()
        }

        private fun initializePlayer(holder: SurfaceHolder) {
            cancelPendingRebuild()
            stopPlaybackWatchdog()
            releasePlayback()
            val path = getVideoPath() ?: return
            val file = File(path)
            if (!file.exists()) return
            try {
                if (path != lastPath || file.lastModified() != lastModified) {
                    // A different medium is a fresh start, not a continuation of the
                    // previous file's failures, so it gets a full recovery budget.
                    recoveryState = VideoWallpaperRecovery.reset()
                    pendingResumePositionMs = 0
                }
                lastModified = file.lastModified()
                lastPath = path
                if (file.extension.equals("gif", ignoreCase = true)) {
                    activeMediaType = "gif"
                    validateAndInitializeGifPlayback(holder, file)
                    return
                }
                refreshWallpaperColors(path, lastModified)
                activeMediaType = "video"
                val speed = getPlaybackSpeed()
                val scaleMode = getScaleMode()

                // Detect video dimensions before playback for accurate surface sizing
                var videoW = 0
                var videoH = 0
                try {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)
                        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        if (rotation == 90 || rotation == 270) {
                            videoW = h; videoH = w
                        } else {
                            videoW = w; videoH = h
                        }
                    } finally {
                        retriever.release()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("VideoWallpaper", "Failed to read video metadata: $path", e)
                }

                // Set surface to screen size — this is the canvas the user sees
                val (sw, sh) = configureSurface(holder)
                configureFrameRate(holder)
                publishVideoTelemetry()

                val safeHolder = object : SurfaceHolder by holder {
                    override fun setKeepScreenOn(screenOn: Boolean) {}
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    setDisplay(safeHolder)
                    isLooping = true
                    setVolume(0f, 0f)
                    // Android 17 background audio hardening: WallpaperService is not a
                    // foreground service, so MediaPlayer.start() can fail silently if
                    // the platform detects an active audio session. Set non-media
                    // attributes and deselect audio tracks after prepare to avoid
                    // creating an AudioTrack entirely.
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_UNKNOWN)
                            .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                            .build()
                    )
                    try {
                        setVideoScalingMode(
                            if (scaleMode == VIDEO_WALLPAPER_SCALE_MODE_FIT) {
                                MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                            } else {
                                MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                            },
                        )
                    } catch (_: Exception) {}
                    setOnPreparedListener { mp ->
                        // If MediaMetadataRetriever didn't get dimensions, read from MediaPlayer
                        if (videoW <= 0 || videoH <= 0) {
                            videoW = mp.videoWidth
                            videoH = mp.videoHeight
                        }
                        mp.isLooping = true
                        try {
                            for (i in mp.trackInfo.indices) {
                                if (mp.trackInfo[i].trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                                    mp.deselectTrack(i)
                                }
                            }
                        } catch (_: Exception) {}
                        try { mp.playbackParams = mp.playbackParams.setSpeed(speed) } catch (_: Exception) {}
                        // A rebuild resumes where the dead player stopped, so recovery
                        // is not visible to the user as a restart from the first frame.
                        val resumeMs = pendingResumePositionMs
                        pendingResumePositionMs = 0
                        if (resumeMs > 0) {
                            try { mp.seekTo(resumeMs) } catch (_: Exception) {}
                        }
                        if (visible && !motionPausedForPowerSave) {
                            mp.start()
                        } else if (resumeMs <= 0) {
                            try { mp.seekTo(0) } catch (_: Exception) {}
                        }
                        startPlaybackWatchdog()
                    }
                    setOnErrorListener { _, what, extra ->
                        // Returning true claims the error so MediaPlayer does not also
                        // fire onCompletion for a player we are about to discard.
                        handlePlaybackFailure(
                            VideoPlaybackFailure.RUNTIME_ERROR,
                            "MediaPlayer error what=" + what + " extra=" + extra,
                        )
                        true
                    }
                    prepareAsync()
                }

                if (BuildConfig.DEBUG) android.util.Log.d("VideoWPService",
                    "Playing ${videoW}x${videoH} on ${sw}x${sh} screen, mode=$scaleMode, path=$path")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("VideoWPService", "Init failed: ${e.message}")
                handlePlaybackFailure(
                    VideoPlaybackFailure.PREPARE_ERROR,
                    "${e.javaClass.simpleName}: ${e.message}",
                )
            }
        }

        /**
         * Applies the bounded recovery policy to a playback failure.
         *
         * Prepare errors, runtime errors, and a frozen-but-"playing" player all land
         * here. Rebuilds back off exponentially and stop after
         * [VideoWallpaperRecovery.MAX_ATTEMPTS], at which point the surface keeps its
         * last rendered frame instead of entering a restart loop.
         */
        private fun handlePlaybackFailure(failure: VideoPlaybackFailure, detail: String) {
            stopPlaybackWatchdog()
            val positionMs = runCatching { mediaPlayer?.currentPosition ?: 0 }.getOrDefault(0)
            releasePlayback()
            receiptStore.recordError(
                LiveWallpaperReceiptStore.ENGINE_VIDEO,
                "${failure.name}: $detail",
            )
            val (next, decision) = VideoWallpaperRecovery.onFailure(
                state = recoveryState,
                failure = failure,
                positionMs = positionMs,
                nowMs = System.currentTimeMillis(),
            )
            recoveryState = next
            when (decision) {
                is VideoRecoveryDecision.Rebuild -> {
                    receiptStore.recordRecovery(
                        LiveWallpaperReceiptStore.ENGINE_VIDEO,
                        "rebuild attempt ${decision.attempt} in ${decision.delayMs}ms",
                    )
                    scheduleRebuild(decision)
                }
                VideoRecoveryDecision.Fallback -> {
                    // Leave the last frame on the surface: a still image beats a loop
                    // that keeps waking the decoder and never settles.
                    receiptStore.recordRecovery(
                        LiveWallpaperReceiptStore.ENGINE_VIDEO,
                        "exhausted after ${VideoWallpaperRecovery.MAX_ATTEMPTS} attempts; holding last frame",
                    )
                }
            }
        }

        private fun scheduleRebuild(decision: VideoRecoveryDecision.Rebuild) {
            cancelPendingRebuild()
            pendingResumePositionMs = decision.resumePositionMs
            val runnable = Runnable {
                pendingRebuild = null
                val holder = currentHolder ?: return@Runnable
                initializePlayer(holder)
            }
            pendingRebuild = runnable
            recoveryHandler.postDelayed(runnable, decision.delayMs)
        }

        private fun cancelPendingRebuild() {
            pendingRebuild?.let { recoveryHandler.removeCallbacks(it) }
            pendingRebuild = null
        }

        /**
         * Watches playback position while visible. Decoder death after an OEM
         * sleep/wake cycle produces no error callback at all - the position simply
         * stops advancing - so polling is the only way to notice.
         */
        private fun startPlaybackWatchdog() {
            stopPlaybackWatchdog()
            val runnable = object : Runnable {
                override fun run() {
                    val player = mediaPlayer
                    if (player == null) {
                        watchdogRunnable = null
                        return
                    }
                    val positionMs = runCatching { player.currentPosition }.getOrDefault(0)
                    val isPlaying = runCatching { player.isPlaying }.getOrDefault(false)
                    val (next, stalled) = VideoWallpaperRecovery.onWatchdogSample(
                        state = recoveryState,
                        positionMs = positionMs,
                        isPlaying = isPlaying && visible && !motionPausedForPowerSave,
                        nowMs = System.currentTimeMillis(),
                    )
                    recoveryState = next
                    if (stalled) {
                        watchdogRunnable = null
                        handlePlaybackFailure(
                            VideoPlaybackFailure.PROGRESS_STALLED,
                            "position frozen at ${positionMs}ms",
                        )
                        return
                    }
                    recoveryHandler.postDelayed(this, VideoWallpaperRecovery.WATCHDOG_INTERVAL_MS)
                }
            }
            watchdogRunnable = runnable
            recoveryHandler.postDelayed(runnable, VideoWallpaperRecovery.WATCHDOG_INTERVAL_MS)
        }

        private fun stopPlaybackWatchdog() {
            watchdogRunnable?.let { recoveryHandler.removeCallbacks(it) }
            watchdogRunnable = null
        }

        private fun configureSurface(holder: SurfaceHolder): Pair<Int, Int> {
            val sw = screenWidth.takeIf { it > 0 } ?: holder.surfaceFrame.width()
            val sh = screenHeight.takeIf { it > 0 } ?: holder.surfaceFrame.height()
            if (sw > 0 && sh > 0) {
                try { holder.setFixedSize(sw, sh) } catch (_: Exception) {}
            }
            return sw to sh
        }

        private fun configureFrameRate(holder: SurfaceHolder) {
            val profile = refreshPlaybackProfile()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    holder.surface.setFrameRate(
                        profile.effectiveFps.toFloat(),
                        android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    )
                } catch (_: Exception) {
                }
            }
            publishVideoTelemetry(profile)
        }

        private fun initializeGifPlayback(holder: SurfaceHolder, file: File) {
            val movie = Movie.decodeFile(file.absolutePath)
                ?: throw IllegalStateException("Selected GIF could not be decoded")
            if (movie.width() <= 0 || movie.height() <= 0) {
                throw IllegalStateException("Selected GIF has invalid dimensions")
            }

            val (sw, sh) = configureSurface(holder)
            configureFrameRate(holder)
            gifMovie = movie
            loadGifCanvasBackground()
            gifStartedAtMs = SystemClock.uptimeMillis()
            gifSampleStartedAtMs = 0L
            gifFramesInSample = 0
            gifSampledFps = 0
            if (motionPausedForPowerSave) {
                drawGifFrame(holder)
            } else {
                resumeGifPlayback(holder)
            }

            if (BuildConfig.DEBUG) {
                android.util.Log.d(
                    "VideoWPService",
                    "Playing GIF ${movie.width()}x${movie.height()} on ${sw}x${sh} screen, mode=${getScaleMode()}, path=${file.absolutePath}",
                )
            }
        }

        /** Keeps malformed GIF data away from native decoders, which may abort the process. */
        private fun validateAndInitializeGifPlayback(holder: SurfaceHolder, file: File) {
            val path = file.absolutePath
            val modifiedAt = file.lastModified()
            val length = file.length()
            val generation = gifValidationGeneration
            colorLoader.request {
                val valid = GifStructureValidator.isValid(file)
                recoveryHandler.post {
                    if (
                        destroyed || generation != gifValidationGeneration ||
                        currentHolder !== holder || getVideoPath() != path ||
                        !file.exists() || file.lastModified() != modifiedAt || file.length() != length
                    ) {
                        return@post
                    }
                    if (!valid) {
                        handlePlaybackFailure(
                            VideoPlaybackFailure.PREPARE_ERROR,
                            "Selected GIF failed structural validation",
                        )
                        return@post
                    }
                    refreshWallpaperColors(path, modifiedAt)
                    try {
                        initializeGifPlayback(holder, file)
                    } catch (e: Exception) {
                        handlePlaybackFailure(
                            VideoPlaybackFailure.PREPARE_ERROR,
                            "${e.javaClass.simpleName}: ${e.message}",
                        )
                    }
                }
            }
        }

        private fun resumeGifPlayback(holder: SurfaceHolder) {
            pauseGifPlayback()
            if (gifMovie == null || !visible || motionPausedForPowerSave) return
            val frameRunnable = object : Runnable {
                override fun run() {
                    if (gifMovie == null || currentHolder != holder) return
                    drawGifFrame(holder)
                    gifHandler.postDelayed(this, gifFrameDelayMs())
                }
            }
            gifFrameRunnable = frameRunnable
            gifHandler.post(frameRunnable)
        }

        private fun pauseGifPlayback() {
            gifFrameRunnable?.let { gifHandler.removeCallbacks(it) }
            gifFrameRunnable = null
        }

        private fun drawGifFrame(holder: SurfaceHolder) {
            val movie = gifMovie ?: return
            val canvas = try {
                holder.lockCanvas()
            } catch (_: Exception) {
                null
            } ?: return

            try {
                drawGifCanvasBackground(canvas)
                val now = SystemClock.uptimeMillis()
                val duration = movie.duration().takeIf { it > 0 } ?: 1000
                val time = ((now - gifStartedAtMs) % duration).toInt()
                movie.setTime(time)

                val movieWidth = movie.width().coerceAtLeast(1)
                val movieHeight = movie.height().coerceAtLeast(1)
                val scaleX = canvas.width / movieWidth.toFloat()
                val scaleY = canvas.height / movieHeight.toFloat()
                val scale = if (getScaleMode() == VIDEO_WALLPAPER_SCALE_MODE_FIT) {
                    minOf(scaleX, scaleY)
                } else {
                    max(scaleX, scaleY)
                }
                val dx = (canvas.width - movieWidth * scale) / 2f
                val dy = (canvas.height - movieHeight * scale) / 2f

                canvas.save()
                canvas.translate(dx, dy)
                canvas.scale(scale, scale)
                movie.draw(canvas, 0f, 0f)
                canvas.restore()
                updateGifFpsSample(now)
                if (isFpsOverlayEnabled()) drawFpsOverlay(canvas)
                if (dimEnabled) {
                    dimming.tick()
                    dimming.drawDimOverlay(canvas, canvas.width, canvas.height)
                }
                drawWallpaperClockOverlay(renderContext, canvas)
            } finally {
                try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
        }

        private fun loadGifCanvasBackground() {
            gifCanvasBackground?.takeUnless { it.isRecycled }?.recycle()
            gifCanvasBackground = null
            if (getScaleMode() != VIDEO_WALLPAPER_SCALE_MODE_FIT) return
            val backgroundPath = getPrefs().getString(VIDEO_WALLPAPER_CANVAS_BACKGROUND_PREF, null)
                ?.takeIf { it.isNotBlank() }
                ?: return
            gifCanvasBackground = runCatching { BitmapFactory.decodeFile(backgroundPath) }.getOrNull()
        }

        private fun drawGifCanvasBackground(canvas: android.graphics.Canvas) {
            if (getScaleMode() != VIDEO_WALLPAPER_SCALE_MODE_FIT) {
                canvas.drawColor(Color.BLACK)
                return
            }
            canvas.drawColor(getCanvasColor())
            gifCanvasBackground?.takeUnless { it.isRecycled }?.let { background ->
                canvas.drawBitmap(
                    background,
                    null,
                    android.graphics.Rect(0, 0, canvas.width, canvas.height),
                    android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
                )
            }
        }

        /**
         * Video and GIF hold a decoder, up to four posted Runnables, and a power
         * save receiver. Every one of them is engine-scoped, so all of them must
         * be gone once the surface is.
         */
        override fun resourceSnapshot(): LiveWallpaperResourceSnapshot =
            LiveWallpaperResourceSnapshot(
                engine = LiveWallpaperReceiptStore.ENGINE_VIDEO,
                players = (if (mediaPlayer != null) 1 else 0) + (if (gifMovie != null) 1 else 0),
                frameCallbacks = listOf(
                    gifFrameRunnable,
                    telemetryRunnable,
                    watchdogRunnable,
                    pendingRebuild,
                ).count { it != null },
                broadcastReceivers = if (powerSaveReceiverRegistered) 1 else 0,
                loaderThreads = colorLoader.outstanding,
            )

        private fun releasePlayback() {
            gifValidationGeneration += 1
            pauseGifPlayback()
            gifMovie = null
            gifCanvasBackground?.takeUnless { it.isRecycled }?.recycle()
            gifCanvasBackground = null
            activeMediaType = "none"
            mediaPlayer?.apply {
                try { setOnPreparedListener(null) } catch (_: Exception) {}
                try { setOnErrorListener(null) } catch (_: Exception) {}
                try { if (isPlaying) stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
            mediaPlayer = null
        }

        private fun registerPowerSaveReceiver() {
            if (powerSaveReceiverRegistered) return
            val filter = IntentFilter().apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(VIDEO_AUTO_BATTERY_SAVER_CHANGED_ACTION)
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(powerSaveReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    registerReceiver(powerSaveReceiver, filter)
                }
                powerSaveReceiverRegistered = true
            } catch (_: Exception) {
            }
        }

        private fun unregisterPowerSaveReceiver() {
            if (!powerSaveReceiverRegistered) return
            try {
                unregisterReceiver(powerSaveReceiver)
            } catch (_: Exception) {
            } finally {
                powerSaveReceiverRegistered = false
            }
        }

        private fun readSystemPowerSaveMode(): Boolean =
            try {
                getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
            } catch (_: Exception) {
                false
            }

        private fun reconcilePowerSavePause() {
            val modeActive = readSystemPowerSaveMode()
            val action = videoMotionPowerSaveAction(
                wasPausedForPowerSave = motionPausedForPowerSave,
                systemPowerSaveMode = modeActive,
                autoSaverEnabled = isAutoBatterySaverEnabled(),
            )
            systemPowerSaveMode = modeActive
            motionPausedForPowerSave = shouldPauseVideoMotionForPowerSave(
                systemPowerSaveMode = modeActive,
                autoSaverEnabled = isAutoBatterySaverEnabled(),
            )

            when (action) {
                VideoMotionPowerSaveAction.PAUSE -> {
                    pauseGifPlayback()
                    try { mediaPlayer?.pause() } catch (_: Exception) {}
                }
                VideoMotionPowerSaveAction.RESUME -> {
                    if (visible) {
                        if (gifMovie != null) {
                            currentHolder?.let { resumeGifPlayback(it) }
                        } else try {
                            mediaPlayer?.let { if (!it.isPlaying) it.start() }
                        } catch (_: Exception) {
                        }
                    }
                }
                VideoMotionPowerSaveAction.NONE -> Unit
            }
            if (action != VideoMotionPowerSaveAction.NONE && BuildConfig.DEBUG) {
                android.util.Log.d(
                    "VideoWPService",
                    "System Battery Saver transition=$action; decoder retained",
                )
            }
            publishVideoTelemetry(refreshPlaybackProfile())
        }

        private fun refreshPlaybackProfile(): VideoPlaybackProfile {
            val battery = readBatterySnapshot()
            val requestedFps = getRequestedFpsLimit()
            val lowBatterySaverActive = shouldUseVideoBatterySaver(
                batteryPercent = battery.percent,
                isCharging = battery.isCharging,
                autoSaverEnabled = isAutoBatterySaverEnabled(),
            )
            activeProfile = VideoPlaybackProfile(
                requestedFps = requestedFps,
                effectiveFps = effectiveVideoFpsLimit(requestedFps, lowBatterySaverActive),
                batteryPercent = battery.percent,
                isCharging = battery.isCharging,
                lowBatterySaverActive = lowBatterySaverActive,
                systemPowerSaveMode = systemPowerSaveMode,
                motionPausedForPowerSave = motionPausedForPowerSave,
            )
            return activeProfile
        }

        private fun readBatterySnapshot(): BatterySnapshot {
            val intent = try {
                registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } catch (_: Exception) {
                null
            }
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) {
                ((level * 100f) / scale).toInt().coerceIn(0, 100)
            } else {
                null
            }
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            return BatterySnapshot(
                percent = percent,
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL ||
                    plugged != 0,
            )
        }

        private fun publishVideoTelemetry(profile: VideoPlaybackProfile = activeProfile) {
            getSharedPreferences(VIDEO_STATS_PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putLong("last_seen_ms", System.currentTimeMillis())
                .putBoolean("visible", visible)
                .putString("media_type", activeMediaType)
                .putInt("requested_fps", profile.requestedFps)
                .putInt("effective_fps", profile.effectiveFps)
                .putBoolean("low_battery_saver_active", profile.lowBatterySaverActive)
                .putBoolean("system_power_save_mode", profile.systemPowerSaveMode)
                .putBoolean("motion_paused_for_power_save", profile.motionPausedForPowerSave)
                .putBoolean("charging", profile.isCharging)
                .putBoolean("fps_overlay_enabled", isFpsOverlayEnabled())
                .putString("scale_mode", getScaleMode())
                .putString("canvas_mode", getCanvasMode())
                .putBoolean("canvas_baked", isCanvasBaked())
                .apply {
                    if (profile.batteryPercent == null) remove("battery_percent")
                    else putInt("battery_percent", profile.batteryPercent)
                }
                .apply()
        }

        private fun startTelemetryHeartbeat() {
            stopTelemetryHeartbeat()
            val runnable = object : Runnable {
                override fun run() {
                    reconcilePowerSavePause()
                    currentHolder?.let { configureFrameRate(it) } ?: publishVideoTelemetry(refreshPlaybackProfile())
                    receiptStore.recordDraw(LiveWallpaperReceiptStore.ENGINE_VIDEO)
                    telemetryHandler.postDelayed(this, 30_000L)
                }
            }
            telemetryRunnable = runnable
            telemetryHandler.post(runnable)
        }

        private fun stopTelemetryHeartbeat() {
            telemetryRunnable?.let { telemetryHandler.removeCallbacks(it) }
            telemetryRunnable = null
        }

        private fun gifFrameDelayMs(): Long =
            (1000L / activeProfile.effectiveFps.coerceAtLeast(1)).coerceAtLeast(16L)

        private fun updateGifFpsSample(nowMs: Long) {
            if (gifSampleStartedAtMs == 0L) {
                gifSampleStartedAtMs = nowMs
                gifFramesInSample = 0
            }
            gifFramesInSample += 1
            val elapsed = nowMs - gifSampleStartedAtMs
            if (elapsed >= 1_000L) {
                gifSampledFps = ((gifFramesInSample * 1_000f) / elapsed).toInt().coerceAtLeast(0)
                gifFramesInSample = 0
                gifSampleStartedAtMs = nowMs
            }
        }

        private fun drawFpsOverlay(canvas: android.graphics.Canvas) {
            val fps = if (gifSampledFps > 0) gifSampledFps else activeProfile.effectiveFps
            val label = if (activeProfile.lowBatterySaverActive) {
                "$fps FPS saver"
            } else {
                "$fps FPS"
            }
            val paddingX = 12f
            val paddingY = 8f
            val textWidth = overlayTextPaint.measureText(label)
            val height = overlayTextPaint.textSize + paddingY * 2f
            val rect = android.graphics.RectF(
                16f,
                16f,
                16f + textWidth + paddingX * 2f,
                16f + height,
            )
            canvas.drawRoundRect(rect, 8f, 8f, overlayBackgroundPaint)
            canvas.drawText(label, rect.left + paddingX, rect.bottom - paddingY - 4f, overlayTextPaint)
        }
    }
}
