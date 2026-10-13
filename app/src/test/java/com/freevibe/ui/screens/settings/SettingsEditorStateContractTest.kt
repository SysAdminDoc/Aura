package com.freevibe.ui.screens.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The profile and 24H pack editors send the user to a system picker. The activity can be rebuilt
 * while that picker is in front, and what comes back has to find the editor still open.
 */
class SettingsEditorStateContractTest {
    private val settingsDir = File("src/main/java/com/freevibe/ui/screens/settings")

    @Test
    fun `both editors stay open across a rotation or a rebuilt activity`() {
        val sounds = settingsDir.resolve("SettingsSoundSection.kt").readText()
        val wallpapers = settingsDir.resolve("SettingsWallpaperSection.kt").readText()

        assertTrue(sounds.contains("var showSoundProfiles by rememberSaveable { mutableStateOf(false) }"))
        assertTrue(wallpapers.contains("var showPackEditor by rememberSaveable { mutableStateOf(false) }"))
    }

    @Test
    fun `the pack editor hands a picked wallpaper to the view model instead of its own scope`() {
        val editor = settingsDir.resolve("WallpaperPackEditor.kt").readText()

        assertTrue(editor.contains("viewModel.setWallpaperPackSlot(daypart, uri, unnamedWallpaper)"))
        assertFalse(
            "work started in the dialog's scope is cancelled when the dialog closes",
            editor.contains("rememberCoroutineScope"),
        )
    }
}
