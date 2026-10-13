package com.freevibe.ui.screens.editor

import com.freevibe.data.legal.isProviderAvailableInCurrentArtifact
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.service.AudioExportFormat
import com.freevibe.service.isLosslessCutAllowed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundEditorViewModelTest {

    @Test
    fun `remote audio cache file name is scoped by identity`() {
        val first = buildRemoteAudioCacheFileName(
            name = "Focus Loop",
            cacheIdentity = "SOUND::YOUTUBE::yt_focus12345",
            url = "https://example.com/audio.mp3",
        )
        val second = buildRemoteAudioCacheFileName(
            name = "Focus Loop",
            cacheIdentity = "SOUND::YOUTUBE::yt_relax12345",
            url = "https://example.com/audio.mp3",
        )

        assertNotEquals(first, second)
    }

    @Test
    fun `remote audio cache file name preserves detected extension`() {
        val fileName = buildRemoteAudioCacheFileName(
            name = "Ocean Wave",
            cacheIdentity = "SOUND::BUNDLED::ocean_wave",
            url = "https://example.com/ocean.wav?download=1",
        )

        assertTrue(fileName.endsWith(".wav"))
    }

    @Test
    fun `shouldReuseLoadedSound ignores local editor state`() {
        val shouldReuse = shouldReuseLoadedSound(
            loadedSoundKey = "SOUND::YOUTUBE::yt_focus12345",
            requestedSoundKey = "SOUND::YOUTUBE::yt_focus12345",
            state = SoundEditorState(
                localFilePath = "C:/cache/local.mp3",
                isLocalFile = true,
            ),
        )

        assertFalse(shouldReuse)
    }

    @Test
    fun `shouldReuseLoadedLocalUri reuses active local editor state`() {
        val key = buildLocalAudioEditorIdentity("content://audio/1")
        val shouldReuse = shouldReuseLoadedLocalUri(
            loadedSoundKey = key,
            requestedLocalKey = key,
            state = SoundEditorState(
                localFilePath = "C:/cache/audio.mp3",
                isLocalFile = true,
            ),
        )

        assertTrue(shouldReuse)
    }

    @Test
    fun `shouldReuseLoadedLocalUri ignores remote editor state`() {
        val key = buildLocalAudioEditorIdentity("content://audio/1")
        val shouldReuse = shouldReuseLoadedLocalUri(
            loadedSoundKey = key,
            requestedLocalKey = key,
            state = SoundEditorState(
                localFilePath = "C:/cache/audio.mp3",
                isLocalFile = false,
            ),
        )

        assertFalse(shouldReuse)
    }

    @Test
    fun `default ringtone trim keeps short clips selected`() {
        assertEquals(1f, defaultRingtoneTrimEndFraction(20_000L), 0.0001f)
    }

    @Test
    fun `default ringtone trim caps long clips at thirty seconds`() {
        assertEquals(0.5f, defaultRingtoneTrimEndFraction(60_000L), 0.0001f)
    }

    @Test
    fun `default ringtone trim keeps unknown durations selected`() {
        assertEquals(1f, defaultRingtoneTrimEndFraction(0L), 0.0001f)
    }

    @Test
    fun `default ringtone trim milliseconds preserve exact short duration`() {
        assertEquals(20_123L, defaultRingtoneTrimEndMs(20_123L))
        assertEquals(30_000L, defaultRingtoneTrimEndMs(60_000L))
    }

    @Test
    fun `lossless cut requires processing effects to stay disabled`() {
        assertTrue(isLosslessCutAllowed(0L, 0L, playbackSpeed = 1f))
        assertFalse(isLosslessCutAllowed(1L, 0L, playbackSpeed = 1f))
        assertFalse(isLosslessCutAllowed(0L, 1L, playbackSpeed = 1f))
        assertFalse(isLosslessCutAllowed(0L, 0L, playbackSpeed = 1.25f))
    }

    @Test
    fun `editor offers lossless cut only for supported unprocessed sources`() {
        assertTrue(
            SoundEditorState(localFilePath = "C:/cache/source.ogg").canUseLosslessCut,
        )
        assertFalse(
            SoundEditorState(
                localFilePath = "C:/cache/source.ogg",
                playbackSpeed = 1.25f,
            ).canUseLosslessCut,
        )
        assertFalse(
            SoundEditorState(localFilePath = "C:/cache/source.webm").canUseLosslessCut,
        )
    }

    @Test
    fun `editor defaults to platform AAC export and offers stable speed steps`() {
        val state = SoundEditorState()

        assertEquals(AudioExportFormat.M4A, state.exportFormat)
        assertEquals(AudioExportFormat.M4A.defaultBitrateKbps, state.exportBitrateKbps)
        assertEquals(1f, state.playbackSpeed, 0f)
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f), SOUND_EDITOR_PLAYBACK_SPEEDS)
        assertEquals("0.75×", formatPlaybackSpeed(0.75f))
        assertEquals("1×", formatPlaybackSpeed(1f))
    }

    @Test
    fun `playback speed marks the editor as changed`() {
        val durationMs = 10_000L
        val unchanged = SoundEditorState(
            durationMs = durationMs,
            trimEndMs = defaultRingtoneTrimEndMs(durationMs),
        )

        assertFalse(hasUnsavedSoundEdits(unchanged))
        assertTrue(hasUnsavedSoundEdits(unchanged.copy(playbackSpeed = 1.25f)))
    }

    @Test
    fun `speed-adjusted duration limits fades on the exported timeline`() {
        val state = SoundEditorState(
            trimStartMs = 1_000L,
            trimEndMs = 5_000L,
            playbackSpeed = 2f,
        )

        assertEquals(2_000L, state.processedDurationMs)
        assertEquals(1_000L, state.maximumFadeMs)
    }

    @Test
    fun `loop preview restarts only at the selected end`() {
        assertFalse(shouldLoopTrimPreview(positionMs = 4_999, startMs = 1_000, endMs = 5_000))
        assertTrue(shouldLoopTrimPreview(positionMs = 5_000, startMs = 1_000, endMs = 5_000))
        assertFalse(shouldLoopTrimPreview(positionMs = 5_000, startMs = 5_000, endMs = 5_000))
    }

    @Test
    fun `numeric trim bounds clamp to one encoded frame`() {
        assertEquals(
            9_977L,
            clampTrimStartMs(
                requestedMs = 10_000L,
                trimEndMs = 10_000L,
                durationMs = 20_000L,
                frameDurationMs = 23L,
            ),
        )
        assertEquals(
            1_023L,
            clampTrimEndMs(
                requestedMs = 1_001L,
                trimStartMs = 1_000L,
                durationMs = 20_000L,
                frameDurationMs = 23L,
            ),
        )
    }

    @Test
    fun `sound editor state preserves exact millisecond bounds`() {
        val state = SoundEditorState(
            durationMs = 123_456L,
            trimStartMs = 12_345L,
            trimEndMs = 98_765L,
        )

        assertEquals(12_345L, state.trimStartMs)
        assertEquals(98_765L, state.trimEndMs)
        assertEquals(86_420L, state.trimDurationMs)
    }

    @Test
    fun `waveform zoom keeps focus anchored and clamps viewport`() {
        val zoomed = updateWaveformViewport(
            zoom = 1f,
            startFraction = 0f,
            zoomChange = 2f,
            panFraction = 0f,
            focusFraction = 0.75f,
        )
        assertEquals(2f, zoomed.zoom, 0.0001f)
        assertEquals(0.375f, zoomed.startFraction, 0.0001f)

        val clamped = updateWaveformViewport(
            zoom = zoomed.zoom,
            startFraction = zoomed.startFraction,
            zoomChange = 100f,
            panFraction = -10f,
            focusFraction = 0.5f,
        )
        assertEquals(MAX_WAVEFORM_ZOOM, clamped.zoom, 0.0001f)
        assertTrue(clamped.startFraction in 0f..(1f - 1f / MAX_WAVEFORM_ZOOM))
    }

    @Test
    fun `an unreadable waveform is a flat line rather than a fake signal`() {
        val waveform = unavailableWaveform()

        assertEquals(WAVEFORM_SAMPLES, waveform.size)
        assertEquals(1, waveform.distinct().size)
        assertTrue("the line stays visible above the baseline", waveform.first() > 0f)
    }

    @Test
    fun `editor state tells a real waveform from the unavailable stand-in`() {
        val readable = SoundEditorState(waveform = unavailableWaveform())
        val unavailable = readable.copy(waveformUnavailable = true)

        assertNotEquals(readable, unavailable)
        assertNotEquals(readable.hashCode(), unavailable.hashCode())
    }

    @Test
    fun `local audio editor identity is scoped to uri`() {
        val first = buildLocalAudioEditorIdentity("content://audio/1")
        val second = buildLocalAudioEditorIdentity("content://audio/2")

        assertNotEquals(first, second)
    }

    @Test
    fun `editor blocks sounds whose edit action is disabled`() {
        val message = soundEditorEditGateMessage(
            sound = sound(source = ContentSource.YOUTUBE, license = "YouTube"),
            editConfirmed = true,
        )

        assertNotNull(message)
        assertTrue(message!!.contains("cannot be edited", ignoreCase = true))
    }

    @Test
    fun `editor requires confirmation for non-commercial licensed sounds`() {
        val sound = sound(
            source = ContentSource.COMMUNITY,
            license = "CC BY-NC",
            sourcePageUrl = "https://example.com/community/sound/123",
            uploaderName = "creator",
        )

        assertNotNull(soundEditorEditGateMessage(sound, editConfirmed = false))
        if (isProviderAvailableInCurrentArtifact(ContentSource.COMMUNITY)) {
            assertNull(soundEditorEditGateMessage(sound, editConfirmed = true))
        } else {
            assertNotNull(soundEditorEditGateMessage(sound, editConfirmed = true))
        }
    }

    @Test
    fun `editor allows local user files without confirmation`() {
        val message = soundEditorEditGateMessage(
            sound = sound(source = ContentSource.LOCAL, license = ""),
            editConfirmed = false,
        )

        assertNull(message)
    }

    private fun sound(
        source: ContentSource,
        license: String,
        sourcePageUrl: String = "https://example.com/source",
        uploaderName: String = "uploader",
    ) = Sound(
        id = "sound_1",
        source = source,
        name = "Sound",
        previewUrl = "https://example.com/preview.mp3",
        downloadUrl = "https://example.com/download.mp3",
        license = license,
        sourcePageUrl = sourcePageUrl,
        uploaderName = uploaderName,
    )
}
