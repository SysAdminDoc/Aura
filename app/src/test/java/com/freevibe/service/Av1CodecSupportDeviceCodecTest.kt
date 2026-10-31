package com.freevibe.service

import android.media.MediaCodecInfo
import android.media.MediaFormat
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.MediaCodecInfoBuilder
import org.robolectric.shadows.ShadowMediaCodecList

@RunWith(RobolectricTestRunner::class)
class Av1CodecSupportDeviceCodecTest {

    @After
    fun forgetCodecs() {
        ShadowMediaCodecList.reset()
    }

    private fun installAv1Decoder(name: String, softwareOnly: Boolean = false) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AV1, 1_920, 1_080)
        ShadowMediaCodecList.addCodec(
            MediaCodecInfoBuilder.newBuilder()
                .setName(name)
                .setIsSoftwareOnly(softwareOnly)
                .setIsHardwareAccelerated(!softwareOnly)
                .setCapabilities(
                    MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
                        .setMediaFormat(format)
                        .setColorFormats(intArrayOf(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible))
                        .build(),
                )
                .build(),
        )
    }

    // Android 8 and 9 have no MediaCodecInfo.isHardwareAccelerated. Calling it there is a
    // NoSuchMethodError, which isn't an Exception, so it crashes the video download path.
    @Test
    @Config(sdk = [27])
    fun `below Android 10 a vendor decoder counts as hardware by its name`() {
        installAv1Decoder("OMX.vendor.video.decoder.av1")

        assertTrue(Av1CodecSupport().hasHardwareAv1Decode)
    }

    @Test
    @Config(sdk = [27])
    fun `below Android 10 Android's own software decoder doesn't count`() {
        installAv1Decoder("c2.android.av1.decoder")

        assertFalse(Av1CodecSupport().hasHardwareAv1Decode)
    }

    @Test
    @Config(sdk = [35])
    fun `from Android 10 the platform says which decoders are hardware`() {
        installAv1Decoder("c2.vendor.av1.decoder")

        assertTrue(Av1CodecSupport().hasHardwareAv1Decode)
    }

    @Test
    @Config(sdk = [35])
    fun `from Android 10 a software only decoder doesn't count`() {
        installAv1Decoder("c2.android.av1.decoder", softwareOnly = true)

        assertFalse(Av1CodecSupport().hasHardwareAv1Decode)
    }
}
