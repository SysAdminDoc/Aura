package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPackManagerTest {

    @Test
    fun `Daypart forHour returns correct daypart`() {
        assertEquals(Daypart.NIGHT, Daypart.forHour(0))
        assertEquals(Daypart.NIGHT, Daypart.forHour(3))
        assertEquals(Daypart.NIGHT, Daypart.forHour(5))
        assertEquals(Daypart.MORNING, Daypart.forHour(6))
        assertEquals(Daypart.MORNING, Daypart.forHour(9))
        assertEquals(Daypart.MORNING, Daypart.forHour(11))
        assertEquals(Daypart.DAY, Daypart.forHour(12))
        assertEquals(Daypart.DAY, Daypart.forHour(14))
        assertEquals(Daypart.DAY, Daypart.forHour(16))
        assertEquals(Daypart.EVENING, Daypart.forHour(17))
        assertEquals(Daypart.EVENING, Daypart.forHour(19))
        assertEquals(Daypart.EVENING, Daypart.forHour(20))
        assertEquals(Daypart.NIGHT, Daypart.forHour(21))
        assertEquals(Daypart.NIGHT, Daypart.forHour(23))
    }

    @Test
    fun `Daypart coversHour works for overnight range`() {
        assertTrue(Daypart.NIGHT.coversHour(21))
        assertTrue(Daypart.NIGHT.coversHour(23))
        assertTrue(Daypart.NIGHT.coversHour(0))
        assertTrue(Daypart.NIGHT.coversHour(3))
        assertTrue(Daypart.NIGHT.coversHour(5))
        assertTrue(!Daypart.NIGHT.coversHour(6))
        assertTrue(!Daypart.NIGHT.coversHour(12))
        assertTrue(!Daypart.NIGHT.coversHour(20))
    }

    @Test
    fun `serializePack roundtrips correctly`() {
        val pack = WallpaperPack(
            id = "test",
            name = "Test Pack",
            target = "HOME",
            slots = listOf(
                DaypartSlot(Daypart.MORNING, "content://media/1", "Sunrise"),
                DaypartSlot(Daypart.NIGHT, "content://media/2", "Stars"),
            ),
        )
        val json = serializePack(pack)
        val parsed = parsePack(json)
        assertNotNull(parsed)
        assertEquals("test", parsed!!.id)
        assertEquals("Test Pack", parsed.name)
        assertEquals("HOME", parsed.target)
        assertEquals(2, parsed.slots.size)
        assertEquals(Daypart.MORNING, parsed.slots[0].daypart)
        assertEquals("content://media/1", parsed.slots[0].wallpaperUri)
        assertEquals(Daypart.NIGHT, parsed.slots[1].daypart)
    }

    @Test
    fun `parsePack returns null for blank input`() {
        assertNull(parsePack(""))
        assertNull(parsePack("   "))
    }

    @Test
    fun `parsePack returns null for malformed JSON`() {
        assertNull(parsePack("{bad json"))
    }

    @Test
    fun `activeSlotForHour returns matching slot`() {
        val pack = WallpaperPack(
            id = "test",
            name = "All Day",
            slots = listOf(
                DaypartSlot(Daypart.MORNING, "content://morning"),
                DaypartSlot(Daypart.DAY, "content://day"),
                DaypartSlot(Daypart.EVENING, "content://evening"),
                DaypartSlot(Daypart.NIGHT, "content://night"),
            ),
        )
        assertEquals("content://morning", activeSlotForHour(pack, 8)?.wallpaperUri)
        assertEquals("content://day", activeSlotForHour(pack, 14)?.wallpaperUri)
        assertEquals("content://evening", activeSlotForHour(pack, 19)?.wallpaperUri)
        assertEquals("content://night", activeSlotForHour(pack, 23)?.wallpaperUri)
        assertEquals("content://night", activeSlotForHour(pack, 2)?.wallpaperUri)
    }

    @Test
    fun `activeSlotForHour returns null when daypart has no slot`() {
        val pack = WallpaperPack(
            id = "partial",
            name = "Partial",
            slots = listOf(
                DaypartSlot(Daypart.MORNING, "content://morning"),
            ),
        )
        assertNotNull(activeSlotForHour(pack, 8))
        assertNull(activeSlotForHour(pack, 14))
        assertNull(activeSlotForHour(pack, 22))
    }
}
