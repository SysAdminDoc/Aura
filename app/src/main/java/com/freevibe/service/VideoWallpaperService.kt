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
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.freevibe.BuildConfig
import com.freevibe.R
import java.io.File
import kotlin.math.max
import kotlin.random.Random

/**
 * Colour quantization only needs a rough thumbnail, and a GIF frame decoded at full
 * size on a wallpaper process is exactly the allocation this app has been bitten by.
 */
private const val COLOR_FRAME_SAMPLE_SIZE = 4

@OptIn(UnstableApi::class)
internal fun videoScalingModeFor(scaleMode: String): Int =
    if (scaleMode == VIDEO_WALLPAPER_SCALE_MODE_FIT) {
        C.VIDEO_SCALING_MODE_SCALE_TO_FIT
    } else {
        C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
    }

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
@OptIn(UnstableApi::class)
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    @RequiresApi(36)
    override fun onCreateEngine(description: WallpaperDescription): Engine =
        VideoEngine(readAuraWallpaperDescriptionContent(description)?.source)

    inner class VideoEngine(
        private val describedVideoPath: String? = null,
    ) : Engine(), LiveWallpaperResourceReporter {
        private val receiptStore by lazy { LiveWallpaperReceiptStore.create(this@VideoWallpaperService) }
        private var player: ExoPlayer? = null
        private val playerListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackFailure(
                    VideoPlaybackFailure.RUNTIME_ERROR,
                    error.errorCodeName + ": " + error.message,
                )
            }
        }
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
        private var activePlaylist: VideoPlaylist? = null
        private var playlistIndex = 0
        private var playlistAdvanceRunnable: Runnable? = null
        private var playlistAdvanceDueMs = 0L
        private var playlistAdvanceLeftMs = 0L
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

        /** A surface the system described by its own file keeps that file, whatever's picked later. */
        private fun readPlaylist(): VideoPlaylist? =
            if (describedVideoPath != null) null else readActiveVideoPlaylist(getPrefs())

        @RequiresApi(36)
        override fun onApplyWallpaper(which: Int): WallpaperDescription {
            val playlist = readPlaylist()
            val source = if (playlist != null) null else getVideoPath()
            val content = auraWallpaperDescriptionContent(source = source)
            return buildAuraWallpaperDescription(
                id = auraWallpaperDescriptionId(
                    "video",
                    AuraWallpaperDescriptionContent(source = source ?: playlist?.let(::videoPlaylistKey)),
                ),
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
            receiptStore.recordSurfaceCreated(
                LiveWallpaperReceiptStore.ENGINE_VIDEO,
                readPlaylist()?.clips?.firstOrNull()?.path ?: getVideoPath(),
            )
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
                val playlist = readPlaylist()
                if (playlist != null) {
                    if (videoPlaylistKey(playlist) != lastPath) {
                        currentHolder?.let { initializePlayer(it) }
                        return
                    }
                } else {
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
                }
                gifMovie?.let {
                    currentHolder?.let { resumeGifPlayback(it) }
                    return
                }
                try {
                    player?.let {
                        if (!it.playWhenReady) {
                            // A list picks up where it paused. Starting over at every glance
                            // would keep a long clip from ever ending and the list from moving on.
                            if (activePlaylist == null) it.seekTo(0)
                            if (!motionPausedForPowerSave) it.play()
                        }
                    }
                } catch (_: Exception) {}
                startPlaylistAdvance()
            } else {
                stopTelemetryHeartbeat()
                stopPlaylistAdvance()
                pauseGifPlayback()
                try { player?.pause() } catch (_: Exception) {}
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
            readPlaylist()?.let { playlist ->
                initializePlaylistPlayer(holder, playlist)
                return
            }
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
                val scaleMode = getScaleMode()

                // Set surface to screen size — this is the canvas the user sees
                val (sw, sh) = configureSurface(holder)
                configureFrameRate(holder)
                publishVideoTelemetry()

                // A rebuild resumes where the dead player stopped, so recovery is not
                // visible to the user as a restart from the first frame.
                val resumeMs = pendingResumePositionMs
                pendingResumePositionMs = 0
                // Held before it's set up, so a failure below still releases it.
                val built = videoWallpaperPlayerFactory(this@VideoWallpaperService)
                player = built
                built.addListener(playerListener)
                built.setVideoSurface(holder.surface)
                built.videoScalingMode = videoScalingModeFor(scaleMode)
                built.repeatMode = Player.REPEAT_MODE_ONE
                built.setPlaybackSpeed(getPlaybackSpeed())
                built.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)), resumeMs.toLong())
                built.playWhenReady = visible && !motionPausedForPowerSave
                built.prepare()
                startPlaybackWatchdog()

                if (BuildConfig.DEBUG) android.util.Log.d("VideoWPService",
                    "Playing on ${sw}x${sh} screen, mode=$scaleMode, path=$path")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("VideoWPService", "Init failed: ${e.message}")
                handlePlaybackFailure(
                    VideoPlaybackFailure.PREPARE_ERROR,
                    "${e.javaClass.simpleName}: ${e.message}",
                )
            }
        }

        /**
         * One player runs the whole list, so moving between clips never swaps a surface, and the
         * frame rate cap, the low battery cap and the visibility pause all stay in charge of it.
         * The scaling stays on crop for every clip. A Fit clip was already drawn at the screen's size
         * when it was prepared, so crop shows it whole, and no scaling change can land on a seam.
         * The cost is that a Fit clip gets cropped when the screen turns or unfolds.
         */
        private fun initializePlaylistPlayer(holder: SurfaceHolder, playlist: VideoPlaylist) {
            try {
                val key = videoPlaylistKey(playlist)
                if (key != lastPath) {
                    recoveryState = VideoWallpaperRecovery.reset()
                    playlistAdvanceLeftMs = 0L
                    playlistIndex = if (playlist.shuffle) Random.nextInt(playlist.clips.size) else 0
                    // Once per list, not per clip: new colors at every seam could re-theme the whole system.
                    playlist.clips.getOrNull(playlistIndex)?.let { first ->
                        refreshWallpaperColors(first.path, File(first.path).lastModified())
                    }
                }
                lastPath = key
                lastModified = 0L
                pendingResumePositionMs = 0
                activeMediaType = "video"
                activePlaylist = playlist
                configureSurface(holder)
                configureFrameRate(holder)
                publishVideoTelemetry()
                val built = videoWallpaperPlayerFactory(this@VideoWallpaperService)
                player = built
                built.addListener(playerListener)
                built.setVideoSurface(holder.surface)
                built.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                built.repeatMode = videoPlaylistRepeatMode(playlist)
                built.shuffleModeEnabled = playlist.shuffle
                built.preloadConfiguration = ExoPlayer.PreloadConfiguration(VIDEO_PLAYLIST_PRELOAD_US)
                built.setPlaybackSpeed(getPlaybackSpeed())
                built.setMediaItems(
                    playlist.clips.mapIndexed { index, clip ->
                        MediaItem.Builder()
                            .setUri(Uri.fromFile(File(clip.path)))
                            .setMediaId(index.toString())
                            .build()
                    },
                    playlistIndex.coerceIn(playlist.clips.indices),
                    0L,
                )
                built.playWhenReady = visible && !motionPausedForPowerSave
                built.prepare()
                startPlaybackWatchdog()
                startPlaylistAdvance()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("VideoWPService", "Playlist init failed: ${e.message}")
                handlePlaybackFailure(
                    VideoPlaybackFailure.PREPARE_ERROR,
                    "${e.javaClass.simpleName}: ${e.message}",
                )
            }
        }

        /** The timer only counts time on screen, so a clip gets its whole turn however often the phone sleeps. */
        private fun startPlaylistAdvance() {
            stopPlaylistAdvance()
            val playlist = activePlaylist ?: return
            val interval = playlist.advanceSeconds * 1_000L
            if (interval <= 0L || playlist.clips.size < 2 || !visible || motionPausedForPowerSave) return
            val delay = playlistAdvanceLeftMs.takeIf { it > 0L } ?: interval
            val runnable = Runnable {
                playlistAdvanceRunnable = null
                playlistAdvanceLeftMs = 0L
                advancePlaylist()
                startPlaylistAdvance()
            }
            playlistAdvanceDueMs = SystemClock.uptimeMillis() + delay
            playlistAdvanceRunnable = runnable
            recoveryHandler.postDelayed(runnable, delay)
        }

        private fun stopPlaylistAdvance() {
            playlistAdvanceRunnable?.let {
                recoveryHandler.removeCallbacks(it)
                playlistAdvanceLeftMs = (playlistAdvanceDueMs - SystemClock.uptimeMillis()).coerceAtLeast(1L)
            }
            playlistAdvanceRunnable = null
        }

        private fun advancePlaylist() {
            try {
                player?.moveToNextPlaylistClip()
            } catch (_: Exception) {}
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
            val positionMs = runCatching { player?.currentPosition?.toInt() ?: 0 }.getOrDefault(0)
            val failedPlaylist = activePlaylist
            releasePlayback()
            receiptStore.recordError(
                LiveWallpaperReceiptStore.ENGINE_VIDEO,
                "${failure.name}: $detail",
            )
            val (next, decision) = VideoWallpaperRecovery.onFailure(
                state = recoveryState,
                failure = failure,
                positionMs = positionMs,
                nowMs = SystemClock.uptimeMillis(),
            )
            recoveryState = next
            when (decision) {
                is VideoRecoveryDecision.Rebuild -> {
                    receiptStore.recordRecovery(
                        LiveWallpaperReceiptStore.ENGINE_VIDEO,
                        "rebuild attempt ${decision.attempt} in ${decision.delayMs}ms",
                    )
                    scheduleRebuild(decision)
                    if (failedPlaylist != null) {
                        // A broken clip doesn't hold up the rest of the list.
                        pendingResumePositionMs = 0
                        playlistIndex = nextVideoPlaylistIndex(playlistIndex, failedPlaylist.clips.size)
                    }
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
         * Watches decoded frames while visible. Decoder death after an OEM sleep/wake
         * cycle can produce no error callback at all, so polling is the only way to
         * notice. The position can't show it: with no audio renderer it comes from a
         * plain clock that keeps running after the decoder has stopped.
         */
        private fun startPlaybackWatchdog() {
            stopPlaybackWatchdog()
            // A new player counts frames from zero, and the last sample may be old, so
            // a match with it says nothing about this player.
            recoveryState = recoveryState.copy(hasSample = false)
            val runnable = object : Runnable {
                override fun run() {
                    val player = player
                    if (player == null) {
                        watchdogRunnable = null
                        return
                    }
                    val frames = runCatching { player.decodedFrameCount() }.getOrDefault(0)
                    // playWhenReady, because a decoder that hangs before its first frame
                    // leaves the player buffering, and isPlaying is false while it does.
                    val wantsFrames = runCatching { player.playWhenReady }.getOrDefault(false)
                    val (next, stalled) = VideoWallpaperRecovery.onWatchdogSample(
                        state = recoveryState,
                        progress = frames,
                        isPlaying = wantsFrames && visible && !motionPausedForPowerSave,
                        nowMs = SystemClock.uptimeMillis(),
                        stallTimeoutMs = VideoWallpaperRecovery.stallTimeoutMs(
                            runCatching { player.knownFrameRate() }.getOrNull(),
                        ),
                    )
                    recoveryState = next
                    if (stalled) {
                        watchdogRunnable = null
                        handlePlaybackFailure(
                            VideoPlaybackFailure.PROGRESS_STALLED,
                            "no new frame after $frames",
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
         * Video and GIF hold a decoder, up to four posted Runnables at once (a list's
         * timer stands in for the GIF's frame loop), and a power save receiver. Every
         * one of them is engine-scoped, so all of them must be gone once the surface is.
         */
        override fun resourceSnapshot(): LiveWallpaperResourceSnapshot =
            LiveWallpaperResourceSnapshot(
                engine = LiveWallpaperReceiptStore.ENGINE_VIDEO,
                players = (if (player != null) 1 else 0) + (if (gifMovie != null) 1 else 0),
                frameCallbacks = listOf(
                    gifFrameRunnable,
                    telemetryRunnable,
                    watchdogRunnable,
                    pendingRebuild,
                    playlistAdvanceRunnable,
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
            if (activePlaylist != null) {
                playlistIndex = runCatching { player?.currentMediaItemIndex }.getOrNull() ?: playlistIndex
            }
            stopPlaylistAdvance()
            activePlaylist = null
            player?.apply {
                try { removeListener(playerListener) } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
            player = null
        }

        private fun registerPowerSaveReceiver() {
            if (powerSaveReceiverRegistered) return
            val filter = IntentFilter().apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(VIDEO_AUTO_BATTERY_SAVER_CHANGED_ACTION)
            }
            try {
                ContextCompat.registerReceiver(
                    this@VideoWallpaperService,
                    powerSaveReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
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
                    try { player?.pause() } catch (_: Exception) {}
                    stopPlaylistAdvance()
                }
                VideoMotionPowerSaveAction.RESUME -> {
                    if (visible) {
                        if (gifMovie != null) {
                            currentHolder?.let { resumeGifPlayback(it) }
                        } else try {
                            player?.let { if (!it.playWhenReady) it.play() }
                        } catch (_: Exception) {
                        }
                        startPlaylistAdvance()
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

        private fun activePlaylistScaleMode(): String? {
            val clips = activePlaylist?.clips ?: return null
            val index = runCatching { player?.currentMediaItemIndex }.getOrNull() ?: playlistIndex
            return clips.getOrNull(index)?.scaleMode
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
                .putString("scale_mode", activePlaylistScaleMode() ?: getScaleMode())
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
