package com.freevibe.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFitCanvasComposerTest {
    @Test
    fun `poster sampling bounds landscape portrait and ultrawide frames`() {
        assertEquals(512 to 288, boundedVideoFrameSize(7680, 4320))
        assertEquals(288 to 512, boundedVideoFrameSize(2160, 3840))
        assertEquals(512 to 128, boundedVideoFrameSize(7680, 1920))
        assertEquals(64, boundedBitmapSampleSize(32_768, 16_384))
    }

    @Test
    fun `even video dimensions stay inside the pixel budget`() {
        val normal = boundedVideoCanvasSize(1_440, 3_088, 8_000_000L)
        val extreme = boundedVideoCanvasSize(Int.MAX_VALUE, 1, 8_000_000L)

        assertEquals(0, normal.width % 2)
        assertEquals(0, normal.height % 2)
        assertTrue(normal.pixels <= 8_000_000L)
        assertEquals(0, extreme.width % 2)
        assertEquals(0, extreme.height % 2)
        assertTrue(extreme.pixels <= 8_000_000L)
    }

    @Test
    fun `a video fitted into a taller canvas sits centered with bars above and below`() {
        assertEquals(
            VideoRect(left = 0, top = 900, right = 1080, bottom = 1500),
            fittedVideoRect(1920, 1067, 1080, 2400),
        )
    }

    @Test
    fun `a video fitted into a wider canvas sits centered with bars at the sides`() {
        assertEquals(
            VideoRect(left = 300, top = 0, right = 780, bottom = 1920),
            fittedVideoRect(90, 360, 1080, 1920),
        )
    }

    @Test
    fun `a video with the canvas shape leaves no background showing`() {
        assertEquals(
            VideoRect(left = 0, top = 0, right = 1080, bottom = 2340),
            fittedVideoRect(540, 1170, 1080, 2340),
        )
    }

    @Test
    fun `the opening rounds inward so the black bars never show`() {
        val opening = fittedVideoRect(1000, 333, 1080, 2340)

        assertEquals(0, opening.left)
        assertEquals(1080, opening.right)
        // 359.64 tall, so 990.18 to 1349.82 on a 2340 canvas.
        assertEquals(991, opening.top)
        assertEquals(1349, opening.bottom)
    }

    @Test
    fun `a composed video may land on a nearby or smaller size of the same shape`() {
        assertTrue(composedVideoMatchesCanvas(1080, 2340, 1080, 2340))
        assertTrue(composedVideoMatchesCanvas(1080, 2336, 1080, 2340))
        assertTrue(composedVideoMatchesCanvas(886, 1920, 1080, 2340))
    }

    @Test
    fun `a composed video of another shape is refused`() {
        assertFalse(composedVideoMatchesCanvas(1080, 1920, 1080, 2340))
        assertFalse(composedVideoMatchesCanvas(2340, 1080, 1080, 2340))
        assertFalse(composedVideoMatchesCanvas(0, 2340, 1080, 2340))
    }

    @Test
    fun `a quarter turn swaps the upright size`() {
        assertEquals(180 to 320, uprightVideoSize(320, 180, 90))
        assertEquals(180 to 320, uprightVideoSize(320, 180, 270))
        assertEquals(320 to 180, uprightVideoSize(320, 180, 180))
        assertEquals(320 to 180, uprightVideoSize(320, 180, 0))
    }

    @Test
    fun `a crop region maps to Media3 coordinates with y running up`() {
        val top = normalizedCrop(VideoCropRegion(0, 0, 180, 160), 180, 320)
        assertEquals(-1f, top.left, 0.0001f)
        assertEquals(1f, top.right, 0.0001f)
        assertEquals(0f, top.bottom, 0.0001f)
        assertEquals(1f, top.top, 0.0001f)

        val inner = normalizedCrop(VideoCropRegion(100, 200, 200, 400), 800, 800)
        assertEquals(-0.75f, inner.left, 0.0001f)
        assertEquals(-0.25f, inner.right, 0.0001f)
        assertEquals(-0.5f, inner.bottom, 0.0001f)
        assertEquals(0.5f, inner.top, 0.0001f)
    }
}
