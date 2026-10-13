package com.freevibe.ui.screens.sounds

import com.freevibe.data.legal.isProviderAvailableInCurrentArtifact
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SoundQualityTest {

    @Test
    fun `retired providers still get a readable source label for saved sounds`() {
        assertEquals("Freesound", soundSourceLabel(ContentSource.FREESOUND))
        assertEquals("Audius", soundSourceLabel(ContentSource.AUDIUS))
        assertEquals("ccMixter", soundSourceLabel(ContentSource.CCMIXTER))
        assertEquals("SoundCloud", soundSourceLabel(ContentSource.SOUNDCLOUD))
    }

    @Test
    fun `core sound tabs stay limited to everyday sound types`() {
        assertEquals(
            listOf(SoundTab.RINGTONES, SoundTab.NOTIFICATIONS, SoundTab.ALARMS),
            coreSoundTabs,
        )
    }

    @Test
    fun `secondary sound tabs only expose search when search is active`() {
        assertEquals(
            listOf(SoundTab.YOUTUBE, SoundTab.COMMUNITY),
            secondarySoundTabs(SoundTab.RINGTONES),
        )
        assertEquals(
            listOf(SoundTab.YOUTUBE, SoundTab.COMMUNITY, SoundTab.SEARCH),
            secondarySoundTabs(SoundTab.SEARCH),
        )
    }

    @Test
    fun `secondary sound tabs hide youtube when provider is disabled`() {
        assertEquals(
            listOf(SoundTab.COMMUNITY),
            secondarySoundTabs(SoundTab.RINGTONES, youtubeProviderEnabled = false),
        )
        assertEquals(
            listOf(SoundTab.COMMUNITY, SoundTab.SEARCH),
            secondarySoundTabs(SoundTab.SEARCH, youtubeProviderEnabled = false),
        )
    }

    @Test
    fun `secondary sound tabs hide community when provider is disabled`() {
        assertEquals(
            listOf(SoundTab.YOUTUBE),
            secondarySoundTabs(SoundTab.RINGTONES, communityProviderEnabled = false),
        )
        assertEquals(
            emptyList<SoundTab>(),
            secondarySoundTabs(
                SoundTab.RINGTONES,
                youtubeProviderEnabled = false,
                communityProviderEnabled = false,
            ),
        )
    }

    @Test
    fun `sound collections are only shown for everyday sound tabs`() {
        assertTrue(soundCollectionsFor(SoundTab.RINGTONES).isNotEmpty())
        assertTrue(soundCollectionsFor(SoundTab.NOTIFICATIONS).isNotEmpty())
        assertTrue(soundCollectionsFor(SoundTab.ALARMS).isNotEmpty())
        assertTrue(soundCollectionsFor(SoundTab.YOUTUBE).isEmpty())
        assertTrue(soundCollectionsFor(SoundTab.COMMUNITY).isEmpty())
        assertTrue(soundCollectionsFor(SoundTab.SEARCH).isEmpty())
    }

    @Test
    fun `sound collections expose distinct nonblank discovery queries`() {
        SoundTab.entries.forEach { tab ->
            val collections = soundCollectionsFor(tab)
            val titleKeys = collections.map { it.title.ifBlank { it.titleRes.toString() } }
            assertEquals(titleKeys.distinct(), titleKeys)
            assertTrue(collections.all { it.query.isNotBlank() })
            assertTrue(collections.all { it.subtitle.isNotBlank() || it.subtitleRes != 0 })
        }
    }

    @Test
    fun `rankSounds keeps best duplicate instead of first duplicate`() {
        val weak = testSound(
            id = "yt_one",
            source = ContentSource.YOUTUBE,
            name = "Crystal Chime Melody",
            duration = 14.0,
        )
        val stronger = testSound(
            id = "bundle_one",
            source = ContentSource.BUNDLED,
            name = "Crystal Chime Melody",
            duration = 14.0,
            tags = listOf("soft", "clean", "chime"),
            license = "CC0",
        )

        val ranked = rankSounds(
            sounds = listOf(weak, stronger),
            tab = SoundTab.RINGTONES,
            filter = SoundQualityFilter.BEST,
        )

        assertEquals(1, ranked.size)
        assertEquals("bundle_one", ranked.first().id)
    }

    @Test
    fun `clean filter returns only clean sounding entries`() {
        val clean = testSound(
            id = "clean",
            source = ContentSource.BUNDLED,
            name = "Soft Bell Tone",
            duration = 2.0,
            tags = listOf("soft", "chime"),
        )
        val noisy = testSound(
            id = "noisy",
            source = ContentSource.YOUTUBE,
            name = "Podcast Mix Review",
            duration = 2.0,
            tags = listOf("mix"),
        )

        val ranked = rankSounds(
            sounds = listOf(clean, noisy),
            tab = SoundTab.NOTIFICATIONS,
            filter = SoundQualityFilter.CLEAN,
        )

        assertEquals(listOf("clean"), ranked.map { it.id })
    }

    @Test
    fun `sound badges describe intent and mood`() {
        val sound = testSound(
            id = "alarm_one",
            source = ContentSource.CCMIXTER,
            name = "Bright Alarm Bell",
            duration = 9.0,
            tags = listOf("alarm", "bright"),
            license = "CC BY",
        )

        val badges = soundBadges(sound, SoundTab.ALARMS)

        assertTrue(badges.contains("Alarm-ready"))
        assertTrue(badges.contains("Punchy"))
    }

    @Test
    fun `quality floor drops weak long form sound when stronger set exists`() {
        val strongCandidates = listOf(
            testSound(
                id = "bundle_best",
                source = ContentSource.BUNDLED,
                name = "Soft Orbit Chime",
                duration = 14.0,
                tags = listOf("soft", "clean", "chime"),
                license = "CC0",
            ),
            testSound(
                id = "bundled_pulse",
                source = ContentSource.BUNDLED,
                name = "Night Pulse Ringtone",
                duration = 16.0,
                tags = listOf("ringtone", "bright", "electro"),
                license = "CC BY",
            ),
            testSound(
                id = "local_best",
                source = ContentSource.LOCAL,
                name = "Minimal Echo Tone",
                duration = 12.0,
                tags = listOf("tone", "clean", "soft"),
                license = "CC BY",
            ),
            testSound(
                id = "youtube_best",
                source = ContentSource.YOUTUBE,
                name = "Glass Ping Alert",
                duration = 11.0,
                tags = listOf("ping", "alert", "clean"),
                license = "CC0",
            ),
        )
        val weakCandidate = testSound(
            id = "weak_mix",
            source = ContentSource.YOUTUBE,
            name = "Viral Remix Review Edit",
            duration = 245.0,
            tags = listOf("review", "remix", "podcast"),
        )

        val ranked = rankSounds(
            sounds = listOf(weakCandidate) + strongCandidates,
            tab = SoundTab.RINGTONES,
            filter = SoundQualityFilter.BEST,
        )

        val expectedSize = if (isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE)) 4 else 3
        assertEquals(expectedSize, ranked.size)
        assertTrue(ranked.none { it.id == "weak_mix" })
    }

    @Test
    fun `live sound ranking omits legacy providers`() {
        val ranked = rankSounds(
            sounds = listOf(
                testSound("legacy", ContentSource.FREESOUND, "Legacy Chime", 12.0, license = "CC0"),
                testSound("current", ContentSource.BUNDLED, "Current Chime", 12.0, license = "CC0"),
            ),
            tab = SoundTab.RINGTONES,
            filter = SoundQualityFilter.BEST,
        )

        assertEquals(listOf("current"), ranked.map { it.id })
    }

    @Test
    fun `sound feed follows current provider priority while preserving quality within source`() {
        val sounds = listOf(
            testSound("local_1", ContentSource.LOCAL, "Bright Local Alert", 12.0, listOf("alert", "clean"), "CC0"),
            testSound("bundled_1", ContentSource.BUNDLED, "Soft Original Chime", 12.0, listOf("chime", "clean"), "CC0"),
            testSound("youtube_weak", ContentSource.YOUTUBE, "Plain Tone", 12.0),
            testSound("youtube_best", ContentSource.YOUTUBE, "Crystal YouTube Chime", 12.0, listOf("chime", "clean")),
        )

        val ranked = rankSounds(sounds, SoundTab.RINGTONES, SoundQualityFilter.BEST)

        val expectedSources = if (isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE)) {
            listOf(ContentSource.YOUTUBE, ContentSource.BUNDLED, ContentSource.LOCAL)
        } else {
            listOf(ContentSource.BUNDLED, ContentSource.LOCAL)
        }
        assertEquals(expectedSources, ranked.take(expectedSources.size).map { it.source })
        assertEquals(
            if (isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE)) "youtube_best" else "bundled_1",
            ranked.first().id,
        )
    }

    @Test
    fun `tiktok creator clips lead ringtones in the order they arrived`() {
        assumeTrue(isProviderAvailableInCurrentArtifact(ContentSource.TIKTOK))
        val sounds = listOf(
            testSound("bundled_1", ContentSource.BUNDLED, "Soft Original Chime", 12.0, listOf("chime", "clean"), "CC0"),
            // Remix and viral terms sink a clip in the keyword score; lead rows must not care.
            testSound("tt_1", ContentSource.TIKTOK, "Reflections Ultra Remix", 17.0, listOf("viral"), "TikTok"),
            testSound("tt_2", ContentSource.TIKTOK, "Nokia Banger", 24.0, license = "TikTok"),
            testSound("youtube_best", ContentSource.YOUTUBE, "Crystal YouTube Chime", 12.0, listOf("chime", "clean")),
        )

        val ranked = rankSounds(sounds, SoundTab.RINGTONES, SoundQualityFilter.BEST)

        assertEquals(listOf("tt_1", "tt_2"), ranked.take(2).map { it.id })
        assertEquals(sounds.size, ranked.size)
    }

    @Test
    fun `tiktok clips are not pinned outside the ringtones tab`() {
        assertEquals(setOf(ContentSource.TIKTOK), leadSoundSources(SoundTab.RINGTONES))
        SoundTab.entries.filterNot { it == SoundTab.RINGTONES }.forEach { tab ->
            assertTrue("$tab pins a source", leadSoundSources(tab).isEmpty())
        }
    }

    private fun testSound(
        id: String,
        source: ContentSource,
        name: String,
        duration: Double,
        tags: List<String> = emptyList(),
        license: String = "",
    ) = Sound(
        id = id,
        source = source,
        name = name,
        description = "",
        previewUrl = "https://example.com/$id.mp3",
        downloadUrl = "https://example.com/$id.mp3",
        duration = duration,
        tags = tags,
        license = license,
        uploaderName = "Tester",
    )
}

class FormatSoundCodecBadgeTest {

    private fun stubSound(
        duration: Double = 0.0,
        fileType: String = "",
        fileSize: Long = 0L,
    ) = Sound(
        id = "codec_test",
        source = ContentSource.YOUTUBE,
        name = "Test",
        previewUrl = "",
        downloadUrl = "",
        duration = duration,
        fileType = fileType,
        fileSize = fileSize,
    )

    @Test
    fun `returns null when fileType is blank`() {
        assertNull(formatSoundCodecBadge(stubSound(duration = 10.0)))
    }

    @Test
    fun `returns format only when no duration or fileSize`() {
        assertEquals("MP3", formatSoundCodecBadge(stubSound(fileType = "audio/mpeg")))
    }

    @Test
    fun `returns format with bitrate when both fileSize and duration available`() {
        assertEquals(
            "MP3 128k",
            formatSoundCodecBadge(stubSound(duration = 10.0, fileType = "audio/mpeg", fileSize = 160_000L)),
        )
    }

    @Test
    fun `strips audio prefix from fileType`() {
        val badge = formatSoundCodecBadge(stubSound(duration = 5.0, fileType = "audio/ogg", fileSize = 40_000L))
        assertNotNull(badge)
        assertTrue(badge!!.startsWith("OGG"))
    }

    @Test
    fun `normalizes mp4a-latm to AAC`() {
        val badge = formatSoundCodecBadge(stubSound(duration = 10.0, fileType = "mp4a-latm", fileSize = 160_000L))
        assertNotNull(badge)
        assertTrue(badge!!.startsWith("AAC"))
    }
}
