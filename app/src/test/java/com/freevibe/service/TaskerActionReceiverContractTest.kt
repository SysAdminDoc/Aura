package com.freevibe.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskerActionReceiverContractTest {

    private fun settingsSource(): String =
        File("src/main/java/com/freevibe/ui/screens/settings")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.name }
            .joinToString("\n") { it.readText() }

    @Test
    fun `exported automation receiver gates actions before enqueueing rotation`() {
        val source = File("src/main/java/com/freevibe/service/TaskerActionReceiver.kt").readText()

        assertTrue(
            "receiver must route through the shared dispatcher",
            source.contains("ExternalAutomationDispatcher.dispatch("),
        )
        assertTrue(
            "receiver must tag its entry point",
            source.contains("ExternalAutomationDispatcher.ENTRY_POINT_RECEIVER"),
        )
        assertFalse(
            "receiver must not enqueue rotation work outside the dispatcher",
            source.contains("RotationTriggerService.enqueueRotation"),
        )
    }

    @Test
    fun `exported activity shares the automation gate instead of bypassing it`() {
        val source = File("src/main/java/com/freevibe/MainActivity.kt").readText()

        assertTrue(
            "activity must route rotate or shuffle launches through the shared dispatcher",
            source.contains("ExternalAutomationDispatcher.dispatch("),
        )
        assertTrue(
            "activity must tag its entry point",
            source.contains("ExternalAutomationDispatcher.ENTRY_POINT_ACTIVITY"),
        )
        assertFalse(
            "activity must not enqueue rotation work outside the dispatcher",
            source.contains("RotationTriggerService.enqueueRotation"),
        )
        assertTrue(
            "the activity must gate both the cold-start and onNewIntent paths",
            Regex("handleShortcutSideEffects\\(intent\\)").findAll(source).count() >= 2,
        )
    }

    @Test
    fun `only the dispatcher may enqueue rotation for automation intents`() {
        val dispatcher = File("src/main/java/com/freevibe/service/ExternalAutomationDispatcher.kt").readText()
        val gateIndex = dispatcher.indexOf("ExternalAutomationGate.evaluate(")
        val enqueueIndex = dispatcher.indexOf("enqueueRotation(context)")

        assertTrue("dispatcher must evaluate the opt-in gate", gateIndex >= 0)
        assertTrue("dispatcher must enqueue accepted rotation work", enqueueIndex >= 0)
        assertTrue("gate must run before rotation enqueue", gateIndex < enqueueIndex)
        assertTrue(dispatcher.contains("if (decision.accepted)"))
        assertTrue(
            "dispatcher must reject non-automation intents before writing diagnostics",
            dispatcher.contains("!ExternalAutomationGate.isSupportedAction(intent.action)"),
        )
    }

    @Test
    fun `manifest exposes only the documented automation actions`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:name=\".service.TaskerActionReceiver\""))
        assertTrue(manifest.contains("android:exported=\"true\""))
        assertTrue(manifest.contains("com.freevibe.action.ROTATE_NOW"))
        assertTrue(manifest.contains("com.freevibe.action.SHUFFLE_NOW"))
    }

    @Test
    fun `settings exposes automation consent and diagnostics`() {
        val screen = settingsSource()
        val viewModel = File("src/main/java/com/freevibe/ui/screens/settings/SettingsViewModel.kt").readText()

        assertTrue(screen.contains("settings_external_automation_title"))
        assertTrue(screen.contains("externalAutomationSubtitle(externalAutomationDiagnostics)"))
        assertTrue(screen.contains("ExternalAutomationDiagnosticsSummary(snapshot)"))
        assertTrue(viewModel.contains("setExternalAutomationEnabled"))
        assertTrue(viewModel.contains("refreshExternalAutomationDiagnostics"))
    }

    @Test
    fun `readme documents public intent contract and risks`() {
        val readme = File("../README.md").readText()

        assertTrue(readme.contains("## Automation"))
        assertTrue(readme.contains("com.freevibe.action.ROTATE_NOW"))
        assertTrue(readme.contains("com.freevibe.action.SHUFFLE_NOW"))
        assertTrue(readme.contains("one broadcast every 30 seconds"))
        assertTrue(readme.contains("battery rules can still hold it back"))
    }
}
