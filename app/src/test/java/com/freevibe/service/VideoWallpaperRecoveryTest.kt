package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every transition of the video wallpaper's recovery policy. The failure this
 * guards — a decoder that dies across an OEM sleep/wake cycle and reports nothing
 * — cannot be provoked on demand, so the policy is pure and fully covered here.
 */
class VideoWallpaperRecoveryTest {

    private val start = VideoWallpaperRecovery.reset()

    @Test
    fun `backoff grows and is clamped`() {
        assertEquals(1_000L, VideoWallpaperRecovery.backoffMs(1))
        assertEquals(2_000L, VideoWallpaperRecovery.backoffMs(2))
        assertEquals(4_000L, VideoWallpaperRecovery.backoffMs(3))
        assertEquals(8_000L, VideoWallpaperRecovery.backoffMs(4))
        assertEquals(VideoWallpaperRecovery.MAX_BACKOFF_MS, VideoWallpaperRecovery.backoffMs(20))
        assertEquals(VideoWallpaperRecovery.MAX_BACKOFF_MS, VideoWallpaperRecovery.backoffMs(1_000))
        // A zero/negative attempt must never produce a zero delay - that is the
        // restart loop this whole policy exists to prevent.
        assertEquals(1_000L, VideoWallpaperRecovery.backoffMs(0))
        assertEquals(1_000L, VideoWallpaperRecovery.backoffMs(-5))
    }

    @Test
    fun `a prepare error schedules a delayed rebuild that preserves position`() {
        val (state, decision) = VideoWallpaperRecovery.onFailure(
            state = start,
            failure = VideoPlaybackFailure.PREPARE_ERROR,
            positionMs = 4_200,
            nowMs = 1_000L,
        )

        assertEquals(1, state.attempt)
        assertEquals(VideoPlaybackFailure.PREPARE_ERROR, state.lastFailure)
        assertFalse(state.exhausted)
        val rebuild = decision as VideoRecoveryDecision.Rebuild
        assertEquals(1_000L, rebuild.delayMs)
        assertEquals(4_200, rebuild.resumePositionMs)
    }

    @Test
    fun `a negative position never becomes a negative seek`() {
        val (_, decision) = VideoWallpaperRecovery.onFailure(
            start,
            VideoPlaybackFailure.RUNTIME_ERROR,
            positionMs = -1,
            nowMs = 0L,
        )

        assertEquals(0, (decision as VideoRecoveryDecision.Rebuild).resumePositionMs)
    }

    @Test
    fun `attempts are bounded and then fall back instead of looping`() {
        var state = start
        var now = 0L
        repeat(VideoWallpaperRecovery.MAX_ATTEMPTS) { index ->
            now += 1_000L
            val (next, decision) = VideoWallpaperRecovery.onFailure(
                state,
                VideoPlaybackFailure.RUNTIME_ERROR,
                positionMs = 100,
                nowMs = now,
            )
            state = next
            assertTrue("attempt ${index + 1} must rebuild", decision is VideoRecoveryDecision.Rebuild)
            assertFalse(state.exhausted)
        }

        val (exhausted, decision) = VideoWallpaperRecovery.onFailure(
            state,
            VideoPlaybackFailure.RUNTIME_ERROR,
            positionMs = 100,
            nowMs = now + 1_000L,
        )

        assertEquals(VideoRecoveryDecision.Fallback, decision)
        assertTrue(exhausted.exhausted)
    }

    @Test
    fun `an exhausted engine never rebuilds again`() {
        var state = start
        repeat(VideoWallpaperRecovery.MAX_ATTEMPTS + 1) {
            state = VideoWallpaperRecovery.onFailure(
                state,
                VideoPlaybackFailure.RUNTIME_ERROR,
                positionMs = 0,
                nowMs = it * 1_000L,
            ).first
        }
        assertTrue(state.exhausted)

        val (_, decision) = VideoWallpaperRecovery.onFailure(
            state,
            VideoPlaybackFailure.PROGRESS_STALLED,
            positionMs = 0,
            nowMs = 999_999L,
        )

        assertEquals(VideoRecoveryDecision.Fallback, decision)
    }

    // -- watchdog --

    @Test
    fun `a frozen playing player is reported stalled only after the timeout`() {
        // First sample seeds the baseline.
        var (state, stalled) = VideoWallpaperRecovery.onWatchdogSample(start, 1_000, isPlaying = true, nowMs = 0L)
        assertFalse(stalled)

        // Same position, still inside the tolerance window.
        val beforeTimeout = VideoWallpaperRecovery.onWatchdogSample(
            state,
            1_000,
            isPlaying = true,
            nowMs = VideoWallpaperRecovery.STALL_TIMEOUT_MS - 1,
        )
        assertFalse(beforeTimeout.second)

        val atTimeout = VideoWallpaperRecovery.onWatchdogSample(
            state,
            1_000,
            isPlaying = true,
            nowMs = VideoWallpaperRecovery.STALL_TIMEOUT_MS,
        )
        assertTrue(atTimeout.second)
    }

    @Test
    fun `a paused or invisible player is never stalled`() {
        val (state, _) = VideoWallpaperRecovery.onWatchdogSample(start, 1_000, isPlaying = true, nowMs = 0L)

        val (next, stalled) = VideoWallpaperRecovery.onWatchdogSample(
            state,
            1_000,
            isPlaying = false,
            nowMs = 600_000L,
        )

        assertFalse(stalled)
        assertEquals(0L, next.healthySinceMs)
    }

    @Test
    fun `advancing playback keeps the watchdog quiet`() {
        var state = start
        var now = 0L
        var position = 0
        repeat(10) {
            now += VideoWallpaperRecovery.WATCHDOG_INTERVAL_MS
            position += 2_000
            val (next, stalled) = VideoWallpaperRecovery.onWatchdogSample(state, position, true, now)
            state = next
            assertFalse(stalled)
        }
    }

    @Test
    fun `a long healthy run clears the attempt budget`() {
        var state = VideoWallpaperRecovery.onFailure(
            start,
            VideoPlaybackFailure.RUNTIME_ERROR,
            positionMs = 0,
            nowMs = 0L,
        ).first
        assertEquals(1, state.attempt)

        // Playback resumes and keeps advancing well past the healthy-run window.
        state = VideoWallpaperRecovery.onWatchdogSample(state, 1_000, true, 2_000L).first
        state = VideoWallpaperRecovery.onWatchdogSample(
            state,
            2_000,
            true,
            2_000L + VideoWallpaperRecovery.HEALTHY_RUN_MS,
        ).first

        assertEquals(0, state.attempt)
        assertNull(state.lastFailure)
    }

    @Test
    fun `a short healthy run does not clear the budget`() {
        var state = VideoWallpaperRecovery.onFailure(
            start,
            VideoPlaybackFailure.RUNTIME_ERROR,
            positionMs = 0,
            nowMs = 0L,
        ).first

        state = VideoWallpaperRecovery.onWatchdogSample(state, 1_000, true, 2_000L).first
        state = VideoWallpaperRecovery.onWatchdogSample(state, 2_000, true, 5_000L).first

        assertEquals(1, state.attempt)
    }

    @Test
    fun `reset clears everything`() {
        assertEquals(VideoRecoveryState(), VideoWallpaperRecovery.reset())
    }

    // -- stall window --

    @Test
    fun `an ordinary clip gets the usual wait`() {
        assertEquals(VideoWallpaperRecovery.STALL_TIMEOUT_MS, VideoWallpaperRecovery.stallTimeoutMs(null))
        assertEquals(VideoWallpaperRecovery.STALL_TIMEOUT_MS, VideoWallpaperRecovery.stallTimeoutMs(0f))
        assertEquals(VideoWallpaperRecovery.STALL_TIMEOUT_MS, VideoWallpaperRecovery.stallTimeoutMs(-1f))
        assertEquals(VideoWallpaperRecovery.STALL_TIMEOUT_MS, VideoWallpaperRecovery.stallTimeoutMs(30f))
        assertEquals(VideoWallpaperRecovery.STALL_TIMEOUT_MS, VideoWallpaperRecovery.stallTimeoutMs(1f))
    }

    @Test
    fun `a clip slower than the usual wait gets three of its own frame intervals`() {
        assertEquals(30_000L, VideoWallpaperRecovery.stallTimeoutMs(0.1f))
    }

    @Test
    fun `a longer stall window is honoured`() {
        val (state, _) = VideoWallpaperRecovery.onWatchdogSample(start, 5, isPlaying = true, nowMs = 0L)

        val inside = VideoWallpaperRecovery.onWatchdogSample(state, 5, true, 29_999L, stallTimeoutMs = 30_000L)
        val past = VideoWallpaperRecovery.onWatchdogSample(state, 5, true, 30_000L, stallTimeoutMs = 30_000L)

        assertFalse(inside.second)
        assertTrue(past.second)
    }
}
