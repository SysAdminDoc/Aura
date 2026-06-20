package com.freevibe.service

import android.media.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Av1CodecSupportTest {

    @Test
    fun `preferredVideoMimeTypes always contains AVC`() {
        val support = Av1CodecSupport()
        assertTrue(support.preferredVideoMimeTypes().contains(MediaFormat.MIMETYPE_VIDEO_AVC))
    }

    @Test
    fun `preferredVideoMimeTypes always contains HEVC`() {
        val support = Av1CodecSupport()
        assertTrue(support.preferredVideoMimeTypes().contains(MediaFormat.MIMETYPE_VIDEO_HEVC))
    }

    @Test
    fun `preferredVideoMimeTypes has at least two entries`() {
        val support = Av1CodecSupport()
        assertTrue(support.preferredVideoMimeTypes().size >= 2)
    }

    @Test
    fun `hasHardwareAv1Decode does not throw in unit test environment`() {
        val support = Av1CodecSupport()
        // MediaCodecList is not available in Robolectric, so this returns false
        assertEquals(false, support.hasHardwareAv1Decode)
    }
}
