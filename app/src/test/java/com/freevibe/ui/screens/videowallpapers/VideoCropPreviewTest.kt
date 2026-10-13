package com.freevibe.ui.screens.videowallpapers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.freevibe.ui.theme.FreeVibeTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoCropPreviewTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a slow player stays in progress until its first frame`() {
        val tracker = VideoCropPreviewTracker()
        tracker.onTracksChanged(videoTracks(C.FORMAT_HANDLED))

        assertEquals(VideoCropPreview.Preparing, tracker.state)

        tracker.onRenderedFirstFrame()

        assertEquals(VideoCropPreview.Ready, tracker.state)
    }

    @Test
    fun `an unsupported codec fails instead of leaving a black surface`() {
        val skipped = VideoCropPreviewTracker()
        // ExoPlayer drops a video track it can't decode and plays on with no error.
        skipped.onTracksChanged(videoTracks(C.FORMAT_UNSUPPORTED_SUBTYPE))
        assertEquals(VideoCropPreview.Failed(VideoCropFailure.UNSUPPORTED), skipped.state)

        val audioOnly = VideoCropPreviewTracker()
        audioOnly.onTracksChanged(tracksOf(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build(), C.FORMAT_HANDLED))
        assertEquals(VideoCropPreview.Failed(VideoCropFailure.UNSUPPORTED), audioOnly.state)

        val decoder = VideoCropPreviewTracker()
        decoder.onPlayerError(PlaybackException("hevc", null, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertEquals(VideoCropPreview.Failed(VideoCropFailure.UNSUPPORTED), decoder.state)
        decoder.onRenderedFirstFrame()
        assertEquals("a late frame doesn't hide the error", VideoCropPreview.Failed(VideoCropFailure.UNSUPPORTED), decoder.state)
    }

    @Test
    fun `a load error can be retried`() {
        val tracker = VideoCropPreviewTracker()
        tracker.onPlayerError(PlaybackException("offline", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals(VideoCropPreview.Failed(VideoCropFailure.LOAD), tracker.state)

        tracker.retry()
        assertEquals(VideoCropPreview.Preparing, tracker.state)
        tracker.onRenderedFirstFrame()
        assertEquals(VideoCropPreview.Ready, tracker.state)

        assertEquals(VideoCropFailure.UNSUPPORTED, videoCropFailureFor(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertEquals(VideoCropFailure.LOAD, videoCropFailureFor(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertEquals(VideoCropFailure.LOAD, videoCropFailureFor(PlaybackException.ERROR_CODE_TIMEOUT))
    }

    @Test
    fun `progress shows until the first frame, then offers a way out when slow`() {
        var preview by mutableStateOf<VideoCropPreview>(VideoCropPreview.Preparing)
        var choseAnother = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                VideoCropPreviewOverlay(preview = preview, onRetry = {}, onChooseAnother = { choseAnother++ })
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("Loading video\u2026").assertIsDisplayed()
        composeRule.onNodeWithText("Choose another video").assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(VIDEO_CROP_SLOW_LOAD_MS + 100)
        composeRule.onNodeWithText("Still loading. A slow connection can take a while.").assertIsDisplayed()
        composeRule.onNodeWithText("Choose another video").performClick()
        assertEquals(1, choseAnother)

        preview = VideoCropPreview.Ready
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("Still loading. A slow connection can take a while.").assertDoesNotExist()
    }

    @Test
    fun `failures explain themselves and only a load error offers a retry`() {
        var preview by mutableStateOf<VideoCropPreview>(VideoCropPreview.Failed(VideoCropFailure.UNSUPPORTED))
        var retries = 0
        var choseAnother = 0
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                VideoCropPreviewOverlay(preview = preview, onRetry = { retries++ }, onChooseAnother = { choseAnother++ })
            }
        }
        composeRule.onNodeWithText("This video can't play here").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
        composeRule.onNodeWithText("Choose another video").performClick()
        assertEquals(1, choseAnother)

        preview = VideoCropPreview.Failed(VideoCropFailure.LOAD)
        composeRule.onNodeWithText("The video didn't load").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `the crop screen waits for a frame before cropping`() {
        val screen = File("src/main/java/com/freevibe/ui/screens/videowallpapers/VideoCropScreen.kt").readText()

        assertTrue(screen.contains("exoPlayer.addListener(previewTracker)"))
        assertTrue(screen.contains("exoPlayer.removeListener(previewTracker)"))
        assertTrue(screen.contains("enabled = !isCropping && dimensionsReady && previewReady"))
        assertTrue(screen.contains("enabled = !smartCropInFlight && dimensionsReady && previewReady"))
        assertTrue(screen.contains("VideoCropPreviewOverlay("))
    }

    private fun videoTracks(support: Int) =
        tracksOf(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).setWidth(1080).setHeight(1920).build(), support)

    private fun tracksOf(format: Format, support: Int) = Tracks(
        listOf(Tracks.Group(TrackGroup(format), false, intArrayOf(support), booleanArrayOf(support == C.FORMAT_HANDLED))),
    )
}
