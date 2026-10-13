package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundProfileEditingTest {

    private val night = SoundProfile(
        id = "night",
        name = "Night",
        ringtoneUri = "content://media/internal/audio/media/11",
        startHour = 22,
        endHour = 7,
    )
    private val work = SoundProfile(
        id = "work",
        name = "Work",
        notificationUri = "content://media/internal/audio/media/12",
        startHour = 9,
        endHour = 17,
    )

    @Test
    fun `the worker isn't needed while the switch is off`() {
        assertFalse(soundProfilesNeedWorker(enabled = false, profiles = listOf(night)))
    }

    @Test
    fun `the worker isn't needed with no profiles`() {
        assertFalse(soundProfilesNeedWorker(enabled = true, profiles = emptyList()))
    }

    @Test
    fun `a profile with no sound or one that's switched off doesn't count`() {
        val silent = SoundProfile(id = "empty", name = "Empty")
        assertFalse(silent.changesASound)
        assertFalse(soundProfilesNeedWorker(enabled = true, profiles = listOf(silent)))
        assertFalse(soundProfilesNeedWorker(enabled = true, profiles = listOf(night.copy(enabled = false))))
    }

    @Test
    fun `one enabled profile with a sound is enough to need the worker`() {
        assertTrue(night.changesASound)
        assertTrue(
            soundProfilesNeedWorker(
                enabled = true,
                profiles = listOf(SoundProfile(id = "empty", name = "Empty"), night),
            ),
        )
    }

    @Test
    fun `a night window covers both sides of midnight and nothing else`() {
        assertTrue(night.isOvernight)
        assertFalse(night.isAllDay)
        listOf(22, 23, 0, 3, 6).forEach { assertTrue("hour $it", night.coversHour(it)) }
        listOf(7, 12, 21).forEach { assertFalse("hour $it", night.coversHour(it)) }
        assertFalse(work.isOvernight)
    }

    @Test
    fun `stepping an hour wraps around midnight`() {
        assertEquals(0, night.copy(startHour = 23).withStartStepped(1).startHour)
        assertEquals(23, night.copy(startHour = 0).withStartStepped(-1).startHour)
        assertEquals("24 is midnight at the end of the day", 1, work.copy(endHour = 24).withEndStepped(1).endHour)
        assertEquals(24, work.copy(endHour = 1).withEndStepped(-1).endHour)
    }

    @Test
    fun `stepping never leaves a window with the same start and end`() {
        val narrow = work.copy(startHour = 6, endHour = 7)

        assertEquals(8, narrow.withStartStepped(1).startHour)
        assertEquals(5, narrow.withEndStepped(-1).endHour)
        assertEquals(6, narrow.copy(startHour = 8).withStartStepped(-1).startHour)
    }

    @Test
    fun `hours from an import are put in range before editing`() {
        val allDay = SoundProfile(id = "a", name = "A")
        assertEquals(allDay, allDay.withEditableHours())
        assertTrue(allDay.withEditableHours().isAllDay)

        val never = SoundProfile(id = "b", name = "B", startHour = 5, endHour = 5)
        assertTrue("a window that matched no hour opens as all day", never.withEditableHours().isAllDay)

        val odd = SoundProfile(id = "c", name = "C", startHour = 25, endHour = 0).withEditableHours()
        assertEquals(1, odd.startHour)
        assertEquals(24, odd.endHour)
        assertEquals(6, SoundProfile(id = "d", name = "D", startHour = 22, endHour = 30).withEditableHours().endHour)
    }

    @Test
    fun `overlapping profiles are found and the higher one is applied`() {
        val evening = SoundProfile(
            id = "evening",
            name = "Evening",
            alarmUri = "content://media/internal/audio/media/13",
            startHour = 18,
            endHour = 23,
        )

        assertEquals(listOf(night), overlappingSoundProfiles(listOf(night, work, evening), evening))
        assertEquals(listOf(evening), overlappingSoundProfiles(listOf(night, work, evening), night))
        assertTrue(overlappingSoundProfiles(listOf(night, work, evening), work).isEmpty())
        assertEquals("night", activeProfileForHour(listOf(night, evening), 22)?.id)
        assertEquals("evening", activeProfileForHour(listOf(night, evening).moving("evening", -1), 22)?.id)
    }

    @Test
    fun `a switched off profile isn't reported as in the way`() {
        val evening = work.copy(id = "evening", startHour = 18, endHour = 23)

        assertTrue(overlappingSoundProfiles(listOf(night.copy(enabled = false), evening), evening).isEmpty())
    }

    @Test
    fun `saving replaces a profile in place and adds a new one at the end`() {
        val renamed = night.copy(name = "Sleep")

        assertEquals(listOf(renamed, work), listOf(night, work).saving(renamed))
        assertEquals(listOf(work, night), listOf(work).saving(night))
    }

    @Test
    fun `moving past either end leaves the list alone`() {
        val list = listOf(night, work)

        assertEquals(listOf(work, night), list.moving("night", 1))
        assertSame(list, list.moving("night", -1))
        assertSame(list, list.moving("work", 1))
        assertSame(list, list.moving("missing", 1))
    }

    @Test
    fun `each sound is set and cleared on its own`() {
        val uri = "content://media/internal/audio/media/20"
        val edited = night.withSound(SoundShuffleTarget.ALARM, uri)

        assertEquals(uri, edited.soundFor(SoundShuffleTarget.ALARM))
        assertEquals(night.ringtoneUri, edited.soundFor(SoundShuffleTarget.RINGTONE))
        assertEquals("", edited.soundFor(SoundShuffleTarget.NOTIFICATION))
        assertFalse(night.withSound(SoundShuffleTarget.RINGTONE, "").changesASound)
    }

    @Test
    fun `what the editor saves is what the worker reads back`() {
        val saved = listOf(night).saving(work.withSound(SoundShuffleTarget.ALARM, "content://media/internal/audio/media/21"))

        val read = parseProfiles(serializeProfiles(saved))

        assertEquals(saved, read)
        assertEquals("work", activeProfileForHour(read, 10)?.id)
        assertEquals("night", activeProfileForHour(read, 2)?.id)
        assertEquals(null, activeProfileForHour(read, 19))
    }
}
