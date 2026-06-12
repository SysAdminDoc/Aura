package com.freevibe.service

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskerActionReceiverContractTest {

    @Test
    fun `exported automation receiver gates actions before enqueueing rotation`() {
        val source = File("src/main/java/com/freevibe/service/TaskerActionReceiver.kt").readText()
        val gateIndex = source.indexOf("ExternalAutomationGate.evaluate(context, intent)")
        val enqueueIndex = source.indexOf("RotationTriggerService.enqueueRotation(context)")

        assertTrue("receiver must evaluate the opt-in gate", gateIndex >= 0)
        assertTrue("receiver must still enqueue accepted rotation work", enqueueIndex >= 0)
        assertTrue("gate must run before rotation enqueue", gateIndex < enqueueIndex)
        assertTrue(source.contains("if (decision.accepted)"))
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
        val screen = File("src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt").readText()
        val viewModel = File("src/main/java/com/freevibe/ui/screens/settings/SettingsViewModel.kt").readText()

        assertTrue(screen.contains("title = \"External automation\""))
        assertTrue(screen.contains("externalAutomationSubtitle(externalAutomationDiagnostics)"))
        assertTrue(screen.contains("ExternalAutomationDiagnosticsSummary(snapshot)"))
        assertTrue(viewModel.contains("setExternalAutomationEnabled"))
        assertTrue(viewModel.contains("refreshExternalAutomationDiagnostics"))
    }

    @Test
    fun `readme documents public intent contract and risks`() {
        val readme = File("../README.md").readText()

        assertTrue(readme.contains("## External Automation"))
        assertTrue(readme.contains("com.freevibe.action.ROTATE_NOW"))
        assertTrue(readme.contains("com.freevibe.action.SHUFFLE_NOW"))
        assertTrue(readme.contains("one every 30 seconds"))
        assertTrue(readme.contains("Doze"))
    }
}
