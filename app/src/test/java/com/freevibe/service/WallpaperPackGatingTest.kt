package com.freevibe.service

import com.freevibe.data.model.WallpaperTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPackGatingTest {

    private fun packJson(vararg slots: DaypartSlot, enabled: Boolean = true, target: String = "BOTH") =
        serializePack(WallpaperPack(id = "main", name = "Mine", target = target, slots = slots.toList(), enabled = enabled))

    @Test
    fun `no pack means nothing is scheduled even with the toggle on`() {
        assertFalse(shouldSchedulePack(true, ""))
        assertFalse(shouldSchedulePack(true, "{bad json"))
    }

    @Test
    fun `a pack with no slots is not scheduled`() {
        assertFalse(shouldSchedulePack(true, packJson()))
    }

    @Test
    fun `a slot with a blank locator does not count`() {
        assertFalse(shouldSchedulePack(true, packJson(DaypartSlot(Daypart.DAY, ""))))
    }

    @Test
    fun `toggle off never schedules`() {
        assertFalse(shouldSchedulePack(false, packJson(DaypartSlot(Daypart.DAY, "content://a"))))
    }

    @Test
    fun `an inactive pack is not scheduled`() {
        assertFalse(shouldSchedulePack(true, packJson(DaypartSlot(Daypart.DAY, "content://a"), enabled = false)))
    }

    @Test
    fun `toggle on with a real slot schedules`() {
        assertTrue(shouldSchedulePack(true, packJson(DaypartSlot(Daypart.NIGHT, "content://a"))))
    }

    @Test
    fun `night slot resolves across midnight`() {
        val pack = WallpaperPack(
            id = "main",
            name = "",
            slots = listOf(
                DaypartSlot(Daypart.MORNING, "content://m"),
                DaypartSlot(Daypart.NIGHT, "content://n"),
            ),
        )
        listOf(21, 22, 23, 0, 1, 5).forEach { hour ->
            assertEquals("hour $hour", "content://n", activeSlotForHour(pack, hour)?.wallpaperUri)
        }
        assertEquals("content://m", activeSlotForHour(pack, 6)?.wallpaperUri)
        assertNull(activeSlotForHour(pack, 20))
    }

    @Test
    fun `slot target overrides the pack target and falls back to it`() {
        val pack = WallpaperPack(id = "main", name = "", target = "LOCK")
        assertEquals(WallpaperTarget.HOME, slotTarget(pack, DaypartSlot(Daypart.DAY, "x", target = "HOME")))
        assertEquals(WallpaperTarget.LOCK, slotTarget(pack, DaypartSlot(Daypart.DAY, "x")))
        assertEquals(WallpaperTarget.BOTH, slotTarget(pack.copy(target = "nonsense"), DaypartSlot(Daypart.DAY, "x")))
    }

    @Test
    fun `json from before the editor still parses`() {
        val old = """{"id":"p","name":"Old","target":"HOME","slots":[{"daypart":"MORNING","wallpaperUri":"content://a","label":"A"}]}"""
        val parsed = parsePack(old)
        assertNotNull(parsed)
        assertTrue(parsed!!.enabled)
        assertEquals("", parsed.slots.single().target)
        assertTrue(shouldSchedulePack(true, old))
    }
}
