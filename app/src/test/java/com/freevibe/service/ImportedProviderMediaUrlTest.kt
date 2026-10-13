package com.freevibe.service

import com.freevibe.data.model.ContentSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which web addresses in imported data a background job may fetch with nobody looking: the ones on
 * a provider's own host, and nothing a backup's author could aim somewhere else.
 */
class ImportedProviderMediaUrlTest {

    private val everyProvider: (ContentSource) -> Boolean = { true }

    @Test
    fun `an address on a provider's host or one of its subdomains is accepted`() {
        listOf(
            "https://wallhaven.cc/w/abc123",
            "https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg",
            "https://images.pexels.com/photos/1/a.jpeg",
            "  https://www.reddit.com/media/a.jpg  ",
        ).forEach { assertTrue(it, isImportedProviderMediaUrl(it, everyProvider)) }
    }

    @Test
    fun `any other host is refused however it is dressed up`() {
        listOf(
            "https://tracker.example/pixel.jpg",
            "https://wallhaven.cc.tracker.example/x.jpg",
            "https://notwallhaven.cc/x.jpg",
            "https://wallhaven.cc@tracker.example/x.jpg",
            "https://tracker.example/x.jpg?from=wallhaven.cc",
            "https://tracker.example/x.jpg#@wallhaven.cc",
            "https://203.0.113.7/x.jpg",
        ).forEach { assertFalse(it, isImportedProviderMediaUrl(it, everyProvider)) }
    }

    @Test
    fun `only https counts, and a blank or broken address never does`() {
        listOf(
            "http://w.wallhaven.cc/x.jpg",
            "ftp://wallhaven.cc/x.jpg",
            "file:///sdcard/Pictures/x.jpg",
            "content://media/external/images/media/5",
            "/sdcard/Pictures/x.jpg",
            "wallhaven.cc/x.jpg",
            "https://",
            "",
            null,
        ).forEach { assertFalse("$it", isImportedProviderMediaUrl(it, everyProvider)) }
    }

    @Test
    fun `a provider this build can't use is refused`() {
        val withoutWallhaven: (ContentSource) -> Boolean = { it != ContentSource.WALLHAVEN }

        assertFalse(isImportedProviderMediaUrl("https://w.wallhaven.cc/x.jpg", withoutWallhaven))
        assertTrue(isImportedProviderMediaUrl("https://images.pexels.com/a.jpeg", withoutWallhaven))
    }
}
