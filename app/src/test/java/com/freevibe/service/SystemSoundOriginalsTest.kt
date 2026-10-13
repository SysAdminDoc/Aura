package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSoundOriginalsTest {

    private val stock = "content://media/internal/audio/media/42"
    private val auraSound = "content://media/external/audio/media/900"

    @Test
    fun `the first apply records the sound that was set`() {
        assertEquals(SystemSoundOriginal.Sound(stock), originalToCapture(stored = "", current = stock, lastApplied = ""))
    }

    @Test
    fun `a repeat apply never records over the original`() {
        val recorded = SystemSoundOriginal.Sound(stock).encode()

        assertNull(originalToCapture(stored = recorded, current = auraSound, lastApplied = auraSound))
        assertNull(originalToCapture(stored = recorded, current = null, lastApplied = ""))
        assertNull(originalToCapture(stored = SystemSoundOriginal.Silent.encode(), current = stock, lastApplied = ""))
        assertNull(originalToCapture(stored = SystemSoundOriginal.Unknown.encode(), current = stock, lastApplied = ""))
    }

    @Test
    fun `no sound is recorded as silent rather than left out`() {
        assertEquals(SystemSoundOriginal.Silent, originalToCapture(stored = "", current = null, lastApplied = ""))
        assertEquals(SystemSoundOriginal.Silent, originalToCapture(stored = "", current = "", lastApplied = auraSound))
    }

    @Test
    fun `a sound an older build already replaced is recorded as unknown`() {
        assertEquals(SystemSoundOriginal.Unknown, originalToCapture(stored = "", current = auraSound, lastApplied = auraSound))
        assertEquals(
            SystemSoundOriginal.Unknown,
            originalToCapture(
                stored = "",
                current = "content://media/external_primary/audio/media/900?title=Chime&canonical=1",
                lastApplied = auraSound,
            ),
        )
    }

    @Test
    fun `a sound the user picked after Aura's is a real original`() {
        assertEquals(SystemSoundOriginal.Sound(stock), originalToCapture(stored = "", current = stock, lastApplied = auraSound))
    }

    @Test
    fun `an unreadable stored value counts as nothing recorded`() {
        assertEquals(SystemSoundOriginal.Sound(stock), originalToCapture(stored = "garbage", current = stock, lastApplied = ""))
        assertNull(SystemSoundOriginal.decode("uri:"))
    }

    @Test
    fun `originals survive the trip through preferences`() {
        listOf(SystemSoundOriginal.Unknown, SystemSoundOriginal.Silent, SystemSoundOriginal.Sound(stock)).forEach {
            assertEquals(it, SystemSoundOriginal.decode(it.encode()))
        }
    }

    @Test
    fun `restore goes back to the recorded sound when it is still there`() {
        val plan = planOriginalRestore(SystemSoundOriginal.Sound(stock).encode()) { it == stock }

        assertEquals(OriginalRestore.Restored(stock), plan)
    }

    @Test
    fun `restore reports a missing original and asks for no write`() {
        val plan = planOriginalRestore(SystemSoundOriginal.Sound(stock).encode()) { false }

        assertEquals(OriginalRestore.Missing, plan)
    }

    @Test
    fun `restore goes back to silence without checking a file`() {
        val plan = planOriginalRestore(SystemSoundOriginal.Silent.encode()) { error("silence has no file to check") }

        assertEquals(OriginalRestore.Restored(null), plan)
    }

    @Test
    fun `restore says so when Aura never changed the sound or does not know the original`() {
        assertEquals(OriginalRestore.NeverChanged, planOriginalRestore("") { true })
        assertEquals(OriginalRestore.Unknown, planOriginalRestore(SystemSoundOriginal.Unknown.encode()) { true })
    }

    @Test
    fun `the same sound is recognised through query and volume differences`() {
        assertTrue(sameSystemSound(stock, "$stock?title=Over%20the%20Horizon&canonical=1"))
        assertTrue(sameSystemSound(auraSound, "content://media/external_primary/audio/media/900"))
        assertFalse(sameSystemSound(stock, auraSound))
        assertFalse(sameSystemSound(stock, "content://media/internal/audio/media/421"))
        assertFalse(sameSystemSound(null, null))
        assertFalse(sameSystemSound("", ""))
    }

    @Test
    fun `device sounds list the user's own first and drop repeats`() {
        val ordered = orderDeviceSounds(
            listOf(
                DeviceSound("Zen", stock, builtIn = true),
                DeviceSound("my song", auraSound, builtIn = false),
                DeviceSound("Argon", "content://media/internal/audio/media/7", builtIn = true),
                DeviceSound("Zen", stock, builtIn = true),
                DeviceSound("Birdsong", "content://media/external/audio/media/3", builtIn = false),
            ),
        )

        assertEquals(listOf("Birdsong", "my song", "Argon", "Zen"), ordered.map { it.title })
        assertTrue(isBuiltInSound(stock))
        assertFalse(isBuiltInSound(auraSound))
    }
}
