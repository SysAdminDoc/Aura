package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundProfileManagerTest {

    @Test
    fun `coversHour handles simple range`() {
        val profile = SoundProfile(
            id = "work",
            name = "Work",
            startHour = 9,
            endHour = 17,
        )
        assertTrue(profile.coversHour(9))
        assertTrue(profile.coversHour(12))
        assertTrue(profile.coversHour(16))
        assertTrue(!profile.coversHour(17))
        assertTrue(!profile.coversHour(8))
        assertTrue(!profile.coversHour(0))
        assertTrue(!profile.coversHour(23))
    }

    @Test
    fun `coversHour handles overnight range`() {
        val profile = SoundProfile(
            id = "night",
            name = "Night",
            startHour = 22,
            endHour = 6,
        )
        assertTrue(profile.coversHour(22))
        assertTrue(profile.coversHour(23))
        assertTrue(profile.coversHour(0))
        assertTrue(profile.coversHour(3))
        assertTrue(profile.coversHour(5))
        assertTrue(!profile.coversHour(6))
        assertTrue(!profile.coversHour(12))
        assertTrue(!profile.coversHour(21))
    }

    @Test
    fun `coversHour handles full day range`() {
        val profile = SoundProfile(
            id = "all",
            name = "All Day",
            startHour = 0,
            endHour = 24,
        )
        assertTrue(profile.coversHour(0))
        assertTrue(profile.coversHour(12))
        assertTrue(profile.coversHour(23))
    }

    @Test
    fun `serializeProfiles roundtrips correctly`() {
        val profiles = listOf(
            SoundProfile(
                id = "work",
                name = "Work",
                ringtoneUri = "content://media/1",
                notificationUri = "content://media/2",
                alarmUri = "",
                startHour = 9,
                endHour = 17,
            ),
            SoundProfile(
                id = "quiet",
                name = "Quiet",
                ringtoneUri = "",
                notificationUri = "",
                alarmUri = "content://media/3",
                startHour = 22,
                endHour = 6,
                enabled = false,
            ),
        )

        val json = serializeProfiles(profiles)
        val parsed = parseProfiles(json)

        assertEquals(2, parsed.size)
        assertEquals("work", parsed[0].id)
        assertEquals("Work", parsed[0].name)
        assertEquals("content://media/1", parsed[0].ringtoneUri)
        assertEquals(9, parsed[0].startHour)
        assertEquals(17, parsed[0].endHour)
        assertTrue(parsed[0].enabled)
        assertEquals("quiet", parsed[1].id)
        assertTrue(!parsed[1].enabled)
    }

    @Test
    fun `parseProfiles returns empty for blank input`() {
        assertEquals(emptyList<SoundProfile>(), parseProfiles(""))
        assertEquals(emptyList<SoundProfile>(), parseProfiles("   "))
    }

    @Test
    fun `parseProfiles returns empty for malformed JSON`() {
        assertEquals(emptyList<SoundProfile>(), parseProfiles("{bad json"))
    }

    @Test
    fun `activeProfileForHour returns matching enabled profile`() {
        val profiles = listOf(
            SoundProfile(id = "morning", name = "Morning", startHour = 6, endHour = 12),
            SoundProfile(id = "afternoon", name = "Afternoon", startHour = 12, endHour = 18),
            SoundProfile(id = "evening", name = "Evening", startHour = 18, endHour = 22),
        )

        assertEquals("morning", activeProfileForHour(profiles, 8)?.id)
        assertEquals("afternoon", activeProfileForHour(profiles, 14)?.id)
        assertEquals("evening", activeProfileForHour(profiles, 20)?.id)
        assertNull(activeProfileForHour(profiles, 23))
        assertNull(activeProfileForHour(profiles, 3))
    }

    @Test
    fun `activeProfileForHour skips disabled profiles`() {
        val profiles = listOf(
            SoundProfile(id = "work", name = "Work", startHour = 9, endHour = 17, enabled = false),
            SoundProfile(id = "fallback", name = "Fallback", startHour = 9, endHour = 17, enabled = true),
        )

        val active = activeProfileForHour(profiles, 12)
        assertNotNull(active)
        assertEquals("fallback", active?.id)
    }

    @Test
    fun `activeProfileForHour returns first match when ranges overlap`() {
        val profiles = listOf(
            SoundProfile(id = "primary", name = "Primary", startHour = 8, endHour = 20),
            SoundProfile(id = "secondary", name = "Secondary", startHour = 10, endHour = 16),
        )

        assertEquals("primary", activeProfileForHour(profiles, 12)?.id)
    }
}
