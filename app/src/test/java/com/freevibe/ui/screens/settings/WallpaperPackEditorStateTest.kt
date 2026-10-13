package com.freevibe.ui.screens.settings

import com.freevibe.service.Daypart
import com.freevibe.service.parsePack
import com.freevibe.service.shouldSchedulePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPackEditorStateTest {

    @Test
    fun `blank json gives an empty editable pack`() {
        val pack = editablePack("")
        assertEquals(WALLPAPER_PACK_ID, pack.id)
        assertTrue(pack.slots.isEmpty())
    }

    @Test
    fun `editor output round trips through parsePack`() {
        val json = editablePack("")
            .withSlot(Daypart.NIGHT, "content://n", "Stars")
            .withSlot(Daypart.MORNING, "content://m", "Sunrise")
            .withSlotTarget(Daypart.NIGHT, "LOCK")
            .copy(name = "Mine")
            .toEditorJson()
        val parsed = parsePack(json)
        assertNotNull(parsed)
        assertEquals("Mine", parsed!!.name)
        assertEquals(listOf(Daypart.MORNING, Daypart.NIGHT), parsed.slots.map { it.daypart })
        assertEquals("LOCK", parsed.slotFor(Daypart.NIGHT)?.target)
        assertEquals("content://m", parsed.slotFor(Daypart.MORNING)?.wallpaperUri)
        assertEquals("Sunrise", parsed.slotFor(Daypart.MORNING)?.label)
    }

    @Test
    fun `replacing a wallpaper keeps its target and one slot per daypart`() {
        val pack = editablePack("")
            .withSlot(Daypart.DAY, "content://a", "A")
            .withSlotTarget(Daypart.DAY, "HOME")
            .withSlot(Daypart.DAY, "content://b", "B")
        assertEquals(1, pack.slots.size)
        assertEquals("content://b", pack.slots.single().wallpaperUri)
        assertEquals("HOME", pack.slots.single().target)
    }

    @Test
    fun `removing the last slot stops the schedule`() {
        val withSlot = editablePack("").withSlot(Daypart.DAY, "content://a", "A")
        assertTrue(shouldSchedulePack(true, withSlot.toEditorJson()))
        val emptied = withSlot.withoutSlot(Daypart.DAY)
        assertNull(emptied.slotFor(Daypart.DAY))
        assertFalse(shouldSchedulePack(true, emptied.toEditorJson()))
    }

    @Test
    fun `effective target falls back to the pack and then both`() {
        val pack = editablePack("").withSlot(Daypart.DAY, "content://a", "A")
        val slot = pack.slots.single()
        assertEquals("BOTH", pack.effectiveTarget(slot))
        assertEquals("LOCK", pack.copy(target = "LOCK").effectiveTarget(slot))
        assertEquals("HOME", pack.withSlotTarget(Daypart.DAY, "HOME").let { it.effectiveTarget(it.slots.single()) })
    }
}
