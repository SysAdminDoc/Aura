package com.freevibe.data.repository

import android.app.UiAutomation
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freevibe.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class NewPipeLegacySearchInstrumentedTest {
    private class OfflineDownloader : Downloader() {
        var requestCount = 0

        override fun execute(request: Request): Response {
            requestCount += 1
            throw IOException("Offline regression test")
        }
    }

    @Test
    fun searchEntryPointDoesNotLinkToTheApi33UrlEncoder() {
        val downloader = OfflineDownloader()
        try {
            NewPipe.init(downloader)
            val service = NewPipe.getService(ServiceList.YouTube.serviceId)
            val extractor = service.getSearchExtractor(
                createLegacyCompatibleYouTubeSearchHandler("legacy android alarm"),
            )

            try {
                extractor.fetchPage()
            } catch (error: LinkageError) {
                fail("NewPipe search linked an unsupported Android API: $error")
            } catch (_: Exception) {
                // The device may be offline. Reaching the request proves query creation linked.
            }
        } catch (error: LinkageError) {
            fail("NewPipe search linked an unsupported Android API: $error")
        }
        assertTrue("NewPipe search did not reach the offline downloader", downloader.requestCount > 0)
    }

    @Test
    fun soundsDestinationOpensOnLegacyAndroid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("freevibe_app", 0)
            .edit()
            .putBoolean("onboarding_complete", true)
            .commit()

        ActivityScenario.launch(MainActivity::class.java).use {
            val soundsNode = waitForNode(instrumentation.uiAutomation, "Sounds")
            assertTrue("Sounds destination was not clickable", clickNodeOrParent(soundsNode))
            waitForNode(instrumentation.uiAutomation, "Ringtones")
        }
    }

    private fun waitForNode(uiAutomation: UiAutomation, label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 15_000
        do {
            uiAutomation.rootInActiveWindow?.let { root ->
                findNode(root, label)?.let { return it }
            }
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)

        fail("Timed out waiting for '$label'")
        throw AssertionError("unreachable")
    }

    private fun findNode(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                return node
            }
            repeat(node.childCount) { index ->
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var candidate: AccessibilityNodeInfo? = node
        repeat(6) {
            val current = candidate ?: return false
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            candidate = current.parent
        }
        return false
    }
}
