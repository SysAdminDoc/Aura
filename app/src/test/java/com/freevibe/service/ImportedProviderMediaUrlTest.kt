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

    @Test
    fun `Reddit's picture hosts count as Reddit's, and a lookalike doesn't`() {
        listOf(
            "https://i.redd.it/abc123.jpg",
            "https://preview.redd.it/abc123.jpg?width=1080",
        ).forEach {
            assertTrue(it, isImportedProviderMediaUrl(it, everyProvider))
            // The call a library restore makes: the provider list as this build ships it.
            assertTrue(it, isImportedProviderMediaUrl(it))
        }
        listOf(
            "https://evilredd.it/abc123.jpg",
            "https://redd.it.tracker.example/abc123.jpg",
            "https://i.redd.it.tracker.example/abc123.jpg",
        ).forEach { assertFalse(it, isImportedProviderMediaUrl(it, everyProvider)) }
    }

    @Test
    fun `a redirect may move between one provider's hosts`() {
        val start = "https://www.reddit.com/media?url=abc"

        assertTrue("the first address itself", isSameImportedProviderMediaUrl(start, start, everyProvider))
        assertTrue(isSameImportedProviderMediaUrl(start, "https://i.redd.it/abc.jpg", everyProvider))
        assertTrue(
            isSameImportedProviderMediaUrl(
                "https://wallhaven.cc/w/abc",
                "https://w.wallhaven.cc/full/ab/abc.jpg",
                everyProvider,
            ),
        )
    }

    @Test
    fun `a redirect may not leave the provider it started with`() {
        val start = "https://www.reddit.com/media?url=abc"

        listOf(
            "https://tracker.example/abc.jpg",
            "https://images.pexels.com/photos/1/a.jpeg",
            "http://i.redd.it/abc.jpg",
            "https://evilredd.it/abc.jpg",
            "",
            null,
        ).forEach { assertFalse("$it", isSameImportedProviderMediaUrl(start, it, everyProvider)) }
        assertFalse(
            "a provider this build can't use",
            isSameImportedProviderMediaUrl(start, "https://i.redd.it/abc.jpg") { it != ContentSource.REDDIT },
        )
    }

    @Test
    fun `a download that starts off a provider's host may go nowhere`() {
        val tracker = "https://tracker.example/a.jpg"

        assertFalse(isSameImportedProviderMediaUrl(tracker, tracker, everyProvider))
        assertFalse(isSameImportedProviderMediaUrl(tracker, "https://i.redd.it/a.jpg", everyProvider))
    }
}
