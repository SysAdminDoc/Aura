package com.freevibe.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val AURA_PACKAGE = "com.freevibe"

private const val SHORT_WAIT_MS = 1_000L
private const val CONTENT_WAIT_MS = 8_000L
private const val UI_SETTLE_MS = 500L

internal fun MacrobenchmarkScope.startAuraHome() {
    startActivityAndWait()
    device.dismissOnboardingIfVisible()
    device.waitForAuraShell()
}

internal fun MacrobenchmarkScope.prepareAuraHome() {
    startAuraHome()
    pressHome()
}

internal fun MacrobenchmarkScope.exerciseAuraCriticalJourneys() {
    startAuraHome()
    device.navigateAndScroll("Wallpapers", swipes = 2)
    device.openWallpaperDetailIfAvailable()
    device.pressBack()
    device.waitForAuraShell()

    device.navigateAndScroll("Videos", swipes = 2)
    device.navigateAndScroll("Sounds", swipes = 2)
    device.navigateAndScroll("Favorites", swipes = 1)
}

internal fun UiDevice.navigateAndScroll(tabLabel: String, swipes: Int) {
    tapBottomNav(tabLabel)
    waitForContent()
    swipeMainContent(swipes)
}

internal fun UiDevice.openWallpaperDetailIfAvailable() {
    val target = waitForObjectByDescription("View wallpaper", SHORT_WAIT_MS)
        ?: waitForObjectByDescription("Wallpaper", CONTENT_WAIT_MS)
        ?: return

    target.click()
    waitForIdle()
    SystemClock.sleep(UI_SETTLE_MS)
    waitForObjectByDescription("Back", CONTENT_WAIT_MS)
}

internal fun UiDevice.tapBottomNav(label: String) {
    val target = waitForObjectByDescription(label, CONTENT_WAIT_MS)
        ?: waitForObjectByText(label, CONTENT_WAIT_MS)
    target?.click()
    waitForIdle()
    SystemClock.sleep(UI_SETTLE_MS)
}

internal fun UiDevice.dismissOnboardingIfVisible() {
    waitForObjectByText("Skip setup", SHORT_WAIT_MS)?.click()
    waitForIdle()
}

internal fun UiDevice.waitForAuraShell() {
    wait(Until.hasObject(By.desc("Wallpapers")), CONTENT_WAIT_MS)
    waitForIdle()
}

internal fun UiDevice.waitForContent() {
    waitForIdle()
    SystemClock.sleep(UI_SETTLE_MS)
}

internal fun UiDevice.swipeMainContent(swipes: Int) {
    val startX = displayWidth / 2
    val startY = (displayHeight * 0.78f).toInt()
    val endY = (displayHeight * 0.28f).toInt()
    repeat(swipes) {
        swipe(startX, startY, startX, endY, 18)
        waitForIdle()
        SystemClock.sleep(250L)
    }
}

private fun UiDevice.waitForObjectByDescription(description: String, timeoutMs: Long): UiObject2? =
    wait(Until.findObject(By.desc(description)), timeoutMs)

private fun UiDevice.waitForObjectByText(text: String, timeoutMs: Long): UiObject2? =
    wait(Until.findObject(By.text(text)), timeoutMs)
