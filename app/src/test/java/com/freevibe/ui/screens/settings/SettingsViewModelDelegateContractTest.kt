package com.freevibe.ui.screens.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelDelegateContractTest {
    private val settingsDir = File("src/main/java/com/freevibe/ui/screens/settings")
    private val viewModel = settingsDir.resolve("SettingsViewModel.kt")

    @Test
    fun `facade stays small and delegates own feature slices`() {
        val source = viewModel.readText()
        assertTrue("SettingsViewModel.kt should stay below 500 lines", viewModel.readLines().size < 500)

        listOf(
            "SettingsRotationDelegate.kt" to "SettingsRotationDelegate",
            "SettingsMediaDelegate.kt" to "SettingsMediaDelegate",
            "SettingsCommunityDelegate.kt" to "SettingsCommunityDelegate",
            "SettingsDiagnosticsDelegate.kt" to "SettingsDiagnosticsDelegate",
        ).forEach { (fileName, className) ->
            val delegate = settingsDir.resolve(fileName)
            assertTrue("$fileName should exist", delegate.isFile)
            val delegateSource = delegate.readText()
            assertTrue("$className should receive an explicit CoroutineScope", delegateSource.contains("scope: CoroutineScope"))
            assertTrue(
                "$className should own state or job execution",
                delegateSource.contains("stateIn(scope") || delegateSource.contains("scope.launch"),
            )
            assertTrue("$className should be constructed by the facade", source.contains(className))
        }
    }

    @Test
    fun `delegates use the ViewModel lifecycle scope and facade owns no jobs`() {
        val source = viewModel.readText()

        val rotationDelegate = source.substringAfter("SettingsRotationDelegate(").substringBefore("\n    )")
        assertTrue(rotationDelegate.contains("rotationPrefetch = rotationPrefetch,"))
        assertTrue(rotationDelegate.contains("scope = viewModelScope,"))
        assertTrue(source.contains("SettingsMediaDelegate(context, prefs, viewModelScope, pickedFileGrants)"))
        assertTrue(source.contains("scope = viewModelScope"))
        assertFalse("the facade must not create independent jobs", source.contains("viewModelScope.launch"))
        assertFalse("the facade must not own preference stateIn calls", source.contains("stateIn(viewModelScope"))
    }
}
