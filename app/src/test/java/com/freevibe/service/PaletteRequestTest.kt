package com.freevibe.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.size.Size
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PaletteRequestTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val url = "https://i.redd.it/abc123.jpg"

    @Test
    fun `the detail palette never downloads and decodes small`() {
        val request = paletteRequest(context, url, allowNetwork = false)

        assertEquals(CachePolicy.DISABLED, request.networkCachePolicy)
        assertEquals(CachePolicy.ENABLED, request.diskCachePolicy)
        assertEquals(CachePolicy.DISABLED, request.memoryCachePolicy)
        assertFalse("Palette needs readable pixels", request.allowHardware)
        assertEquals(Size(PALETTE_DECODE_EDGE, PALETTE_DECODE_EDGE), runBlocking { request.sizeResolver.size() })
    }

    @Test
    fun `history thumbnails may still fetch`() {
        assertEquals(CachePolicy.ENABLED, paletteRequest(context, url, allowNetwork = true).networkCachePolicy)
    }

    @Test
    fun `detail keeps to the pager's lookahead and the palette has no download of its own`() {
        val detail = File("src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperDetailScreen.kt").readText()
        val extractor = File("src/main/java/com/freevibe/service/ColorExtractor.kt").readText()

        assertFalse("no original-size prefetches beside the pager", detail.contains("imageLoader.enqueue("))
        assertTrue(detail.contains("beyondViewportPageCount = 1"))
        assertTrue(detail.contains("onLoaded = { viewModel.onWallpaperImageLoaded(pageUrl) }"))
        assertFalse("palette shares Coil's cache instead of its own client", extractor.contains("okhttp3"))
    }
}
