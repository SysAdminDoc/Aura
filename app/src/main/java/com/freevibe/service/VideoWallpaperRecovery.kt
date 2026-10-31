package com.freevibe.service

/** How a video wallpaper stopped working. */
enum class VideoPlaybackFailure {
    /** The player could not be built or set up for the file. */
    PREPARE_ERROR,

    /** The player reported an error, while preparing or while playing. */
    RUNTIME_ERROR,

    /**
     * The player claims to be playing but the decoder has stopped handing back
     * frames. This is the silent OEM sleep/wake failure: no error callback ever
     * fires, the surface simply freezes.
     */
    PROGRESS_STALLED,
}

/** Recovery bookkeeping for one engine instance. */
data class VideoRecoveryState(
    /** Rebuild attempts since the last healthy run. */
    val attempt: Int = 0,
    val lastFailure: VideoPlaybackFailure? = null,
    /** True once the attempt budget is spent; no further rebuilds are allowed. */
    val exhausted: Boolean = false,
    /** Position to seek back to after a rebuild, so recovery is not a visible restart. */
    val resumePositionMs: Int = 0,
    /** Wall-clock of the last observed forward progress. */
    val lastProgressAtMs: Long = 0L,
    /** Last observed progress value, used to detect a frozen player. */
    val lastProgress: Int = 0,
    /**
     * Whether any watchdog sample has been taken. Kept explicit rather than
     * inferred from a zero timestamp, so a clock reading of 0 cannot masquerade
     * as "never sampled" and suppress stall detection.
     */
    val hasSample: Boolean = false,
    /**
     * When the current uninterrupted healthy run began, or 0 while paused. A long
     * enough healthy run clears the attempt budget so a device that hiccups once a
     * day never accumulates its way to exhaustion.
     */
    val healthySinceMs: Long = 0L,
)

/** What the engine should do after a failure. */
sealed interface VideoRecoveryDecision {
    /** Rebuild the player after [delayMs], seeking back to [resumePositionMs]. */
    data class Rebuild(
        val delayMs: Long,
        val attempt: Int,
        val resumePositionMs: Int,
    ) : VideoRecoveryDecision

    /**
     * Stop retrying. The engine leaves the last rendered frame on the surface —
     * a frozen frame is a better outcome than a restart loop that never settles
     * and keeps the GPU and decoder busy.
     */
    data object Fallback : VideoRecoveryDecision
}

/**
 * Bounded, pure recovery policy for [VideoWallpaperService].
 *
 * The engine had no error listener, no progress watchdog, and no rebuild budget,
 * so an OEM sleep/wake decoder death left a frozen wallpaper until the user
 * re-picked the video. This adds all three, and keeps them pure so every
 * transition — including the ones that are hard to provoke on a device — is
 * covered by JVM tests.
 */
object VideoWallpaperRecovery {

    /** Rebuild attempts before giving up. */
    const val MAX_ATTEMPTS = 4

    /** First backoff step; doubles per attempt. */
    const val BASE_BACKOFF_MS = 1_000L

    /** Ceiling on the backoff so a persistent failure cannot spin. */
    const val MAX_BACKOFF_MS = 30_000L

    /** How long a playing player may go without a new frame before it is stalled. */
    const val STALL_TIMEOUT_MS = 6_000L

    /** How often the watchdog samples playback progress. */
    const val WATCHDOG_INTERVAL_MS = 2_000L

    /** Frames this many intervals apart still count as a healthy, very slow clip. */
    private const val SLOW_CLIP_FRAME_ALLOWANCE = 3

    /**
     * How long to wait for a frame before calling the player stalled. A slideshow
     * made as a video can hold each picture for several seconds, so a clip whose own
     * frame rate is that low gets more than the usual wait.
     */
    fun stallTimeoutMs(frameRate: Float?): Long {
        if (frameRate == null || frameRate <= 0f) return STALL_TIMEOUT_MS
        val frameIntervalMs = (1_000f / frameRate).toLong()
        return maxOf(STALL_TIMEOUT_MS, frameIntervalMs * SLOW_CLIP_FRAME_ALLOWANCE)
    }

    /** Healthy playback for this long clears the attempt budget. */
    const val HEALTHY_RUN_MS = 60_000L

    /** Exponential backoff for [attempt] (1-based), clamped to [MAX_BACKOFF_MS]. */
    fun backoffMs(attempt: Int): Long {
        if (attempt <= 1) return BASE_BACKOFF_MS
        val shift = (attempt - 1).coerceAtMost(20)
        val scaled = BASE_BACKOFF_MS shl shift
        return if (scaled <= 0L || scaled > MAX_BACKOFF_MS) MAX_BACKOFF_MS else scaled
    }

    /**
     * Records a failure and decides what happens next.
     *
     * @param positionMs playback position at the moment of failure, preserved so a
     *   rebuild resumes rather than restarting from zero.
     */
    fun onFailure(
        state: VideoRecoveryState,
        failure: VideoPlaybackFailure,
        positionMs: Int,
        nowMs: Long,
    ): Pair<VideoRecoveryState, VideoRecoveryDecision> {
        if (state.exhausted) {
            return state.copy(lastFailure = failure) to VideoRecoveryDecision.Fallback
        }
        val attempt = state.attempt + 1
        val resumePositionMs = positionMs.coerceAtLeast(0)
        if (attempt > MAX_ATTEMPTS) {
            val exhausted = state.copy(
                attempt = attempt,
                lastFailure = failure,
                exhausted = true,
                resumePositionMs = resumePositionMs,
            )
            return exhausted to VideoRecoveryDecision.Fallback
        }
        val next = state.copy(
            attempt = attempt,
            lastFailure = failure,
            resumePositionMs = resumePositionMs,
            lastProgressAtMs = nowMs,
            lastProgress = 0,
            healthySinceMs = 0L,
            hasSample = false,
        )
        return next to VideoRecoveryDecision.Rebuild(
            delayMs = backoffMs(attempt),
            attempt = attempt,
            resumePositionMs = resumePositionMs,
        )
    }

    /**
     * Feeds a watchdog sample in.
     *
     * @param progress anything that keeps changing while frames are being produced.
     *   The engine passes the decoded frame count, because a player with no audio
     *   renderer runs its position off a plain clock, and that clock keeps going when
     *   the decoder has died.
     * @return the updated state, and true when the player is considered stalled.
     */
    fun onWatchdogSample(
        state: VideoRecoveryState,
        progress: Int,
        isPlaying: Boolean,
        nowMs: Long,
        stallTimeoutMs: Long = STALL_TIMEOUT_MS,
    ): Pair<VideoRecoveryState, Boolean> {
        // A paused or invisible wallpaper is supposed to stand still; only a player
        // that claims to be playing can be stalled.
        if (!isPlaying) {
            return state.copy(
                lastProgress = progress,
                lastProgressAtMs = nowMs,
                healthySinceMs = 0L,
                hasSample = true,
            ) to false
        }
        val advanced = !state.hasSample || progress != state.lastProgress
        if (!advanced) {
            val stalled = nowMs - state.lastProgressAtMs >= stallTimeoutMs
            return state to stalled
        }
        val healthySinceMs = state.healthySinceMs.takeIf { it != 0L } ?: nowMs
        val healthyLongEnough = state.attempt > 0 && nowMs - healthySinceMs >= HEALTHY_RUN_MS
        val next = state.copy(
            lastProgress = progress,
            lastProgressAtMs = nowMs,
            hasSample = true,
            healthySinceMs = if (healthyLongEnough) nowMs else healthySinceMs,
            attempt = if (healthyLongEnough) 0 else state.attempt,
            lastFailure = if (healthyLongEnough) null else state.lastFailure,
        )
        return next to false
    }

    /** Clears recovery bookkeeping after a fresh, deliberate media change. */
    fun reset(): VideoRecoveryState = VideoRecoveryState()
}
