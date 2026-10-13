package com.freevibe.ui.screens.sounds

import com.freevibe.service.OriginalRestore
import com.freevibe.service.SystemSoundOriginal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSoundsNoticeTest {

    private val stock = "content://media/internal/audio/media/42"
    private val other = "content://media/external/audio/media/900"

    @Test
    fun `a restore that will be undone by automation says so`() {
        assertEquals(DeviceSoundsNotice.RestoredButAutomated, restoreNotice(OriginalRestore.Restored(stock), automated = true))
        assertEquals(DeviceSoundsNotice.RestoredButAutomated, restoreNotice(OriginalRestore.Restored(null), automated = true))
    }

    @Test
    fun `a clean restore names what came back`() {
        assertEquals(DeviceSoundsNotice.Restored, restoreNotice(OriginalRestore.Restored(stock), automated = false))
        assertEquals(DeviceSoundsNotice.RestoredSilent, restoreNotice(OriginalRestore.Restored(null), automated = false))
    }

    @Test
    fun `a restore that changed nothing never claims it did`() {
        assertEquals(DeviceSoundsNotice.OriginalMissing, restoreNotice(OriginalRestore.Missing, automated = true))
        assertEquals(DeviceSoundsNotice.OriginalUnknown, restoreNotice(OriginalRestore.Unknown, automated = true))
        assertEquals(DeviceSoundsNotice.NeverChanged, restoreNotice(OriginalRestore.NeverChanged, automated = false))
    }

    @Test
    fun `restore is offered only for a known original that is not already set`() {
        assertTrue(canRestoreOriginal(SystemSoundOriginal.Sound(stock), other))
        assertTrue(canRestoreOriginal(SystemSoundOriginal.Sound(stock), null))
        assertTrue(canRestoreOriginal(SystemSoundOriginal.Silent, other))

        assertFalse(canRestoreOriginal(SystemSoundOriginal.Sound(stock), "$stock?canonical=1"))
        assertFalse(canRestoreOriginal(SystemSoundOriginal.Silent, null))
        assertFalse(canRestoreOriginal(SystemSoundOriginal.Unknown, other))
        assertFalse(canRestoreOriginal(null, other))
    }
}
