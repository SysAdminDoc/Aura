package com.freevibe.data.legal

import com.freevibe.data.model.ContentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSearchReachTest {

    private val full = ProviderBuild.FULL
    private val github = ProviderChannel.GITHUB

    private fun setup(
        on: Set<ContentSource> = emptySet(),
        keyed: Set<ContentSource> = emptySet(),
    ) = ProviderSetup(
        enabled = SWITCHED.associateWith { it in on },
        keyed = keyed,
    )

    @Test
    fun `search sources follow the registry plus each feed's local filter`() {
        assertEquals(
            listOf(ContentSource.WALLHAVEN, ContentSource.PEXELS, ContentSource.PIXABAY).toSet(),
            searchSourcesFor(ProviderMediaType.WALLPAPER, full, github).toSet(),
        )
        assertEquals(ContentSource.REDDIT, searchSourcesFor(ProviderMediaType.VIDEO, full, github).first())
        assertTrue(ContentSource.YOUTUBE in searchSourcesFor(ProviderMediaType.VIDEO, full, github))
        assertEquals(listOf(ContentSource.BUNDLED, ContentSource.YOUTUBE), searchSourcesFor(ProviderMediaType.SOUND, full, github))
        assertTrue(
            "YouTube isn't in Play builds",
            ContentSource.YOUTUBE !in searchSourcesFor(ProviderMediaType.SOUND, full, ProviderChannel.PLAY),
        )
    }

    @Test
    fun `a required key blocks a switched-on provider until it's saved`() {
        assertEquals(ProviderBlock.NEEDS_KEY, setup(on = setOf(ContentSource.PIXABAY)).blockFor(ContentSource.PIXABAY, full, github))
        assertNull(setup(on = setOf(ContentSource.PIXABAY), keyed = setOf(ContentSource.PIXABAY)).blockFor(ContentSource.PIXABAY, full, github))
        assertNull("Wallhaven's key is optional", setup(on = setOf(ContentSource.WALLHAVEN)).blockFor(ContentSource.WALLHAVEN, full, github))
        assertEquals(ProviderBlock.TURNED_OFF, setup().blockFor(ContentSource.REDDIT, full, github))
        assertEquals(ProviderBlock.NOT_IN_BUILD, setup(on = SWITCHED).blockFor(ContentSource.COMMUNITY, ProviderBuild.FOSS, github))
    }

    @Test
    fun `wallpaper search reaches only usable providers and names the missing key`() {
        val keyless = setup(on = setOf(ContentSource.PIXABAY)).searchReach(ProviderMediaType.WALLPAPER, true, full, github)
        assertEquals(ProviderBlock.NEEDS_KEY, keyless.block)
        assertEquals(emptyList<ContentSource>(), keyless.sources)

        val mixed = setup(on = setOf(ContentSource.WALLHAVEN, ContentSource.PIXABAY)).searchReach(ProviderMediaType.WALLPAPER, true, full, github)
        assertEquals(listOf(ContentSource.WALLHAVEN), mixed.sources)
        assertNull(mixed.block)
    }

    @Test
    fun `Pexels alone can't carry the video feed`() {
        val pexelsOnly = setup(on = setOf(ContentSource.PEXELS), keyed = setOf(ContentSource.PEXELS))
            .searchReach(ProviderMediaType.VIDEO, true, full, github)
        assertEquals(ProviderBlock.TURNED_OFF, pexelsOnly.block)

        val withReddit = setup(on = setOf(ContentSource.PEXELS, ContentSource.REDDIT), keyed = setOf(ContentSource.PEXELS))
            .searchReach(ProviderMediaType.VIDEO, true, full, github)
        assertEquals(setOf(ContentSource.REDDIT, ContentSource.PEXELS), withReddit.sources.toSet())
    }

    @Test
    fun `sound search keeps working offline on Aura Originals`() {
        val offline = setup(on = setOf(ContentSource.YOUTUBE)).searchReach(ProviderMediaType.SOUND, false, full, github)
        assertEquals(listOf(ContentSource.BUNDLED), offline.sources)
        assertTrue(offline.worksOffline)

        val online = setup(on = setOf(ContentSource.YOUTUBE)).searchReach(ProviderMediaType.SOUND, true, full, github)
        assertEquals(listOf(ContentSource.BUNDLED, ContentSource.YOUTUBE), online.sources)
    }

    @Test
    fun `display names come from the disclosures`() {
        assertEquals("Aura Originals", ContentSource.BUNDLED.providerDisplayName())
        assertEquals("Pixabay", ContentSource.PIXABAY.providerDisplayName())
    }

    private companion object {
        val SWITCHED = setOf(
            ContentSource.WALLHAVEN,
            ContentSource.BING,
            ContentSource.REDDIT,
            ContentSource.YOUTUBE,
            ContentSource.COMMUNITY,
            ContentSource.PEXELS,
            ContentSource.PIXABAY,
        )
    }
}
