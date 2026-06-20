package com.freevibe.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

data class OemBatteryGuide(
    val manufacturer: String,
    val summary: String,
    val settingsIntent: Intent?,
)

object OemBatteryGuidance {

    fun detect(context: Context): OemBatteryGuide? {
        val mfr = Build.MANUFACTURER.lowercase(java.util.Locale.ROOT)
        val pkg = context.packageName

        return when {
            mfr.contains("samsung") -> OemBatteryGuide(
                manufacturer = "Samsung",
                summary = "Open Settings > Battery > Background usage limits and remove Aura from the Sleeping/Deep sleeping list.",
                settingsIntent = resolveFirst(
                    context,
                    componentIntent("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                    componentIntent("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                    appDetailsIntent(pkg),
                ),
            )

            mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco") -> OemBatteryGuide(
                manufacturer = "Xiaomi",
                summary = "Open Settings > Apps > Aura > Battery saver and set to No restrictions. Also enable Auto-start.",
                settingsIntent = resolveFirst(
                    context,
                    componentIntent("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"),
                    appDetailsIntent(pkg),
                ),
            )

            mfr.contains("oneplus") || mfr.contains("oppo") || mfr.contains("realme") -> OemBatteryGuide(
                manufacturer = "OnePlus/OPPO",
                summary = "Open Settings > Battery > Battery optimization > Aura and select Don't optimize.",
                settingsIntent = resolveFirst(
                    context,
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    appDetailsIntent(pkg),
                ),
            )

            mfr.contains("huawei") || mfr.contains("honor") -> OemBatteryGuide(
                manufacturer = "Huawei",
                summary = "Open Settings > Battery > App launch > Aura and disable automatic management so all three toggles (Auto-launch, Secondary launch, Run in background) are on.",
                settingsIntent = resolveFirst(
                    context,
                    componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                    appDetailsIntent(pkg),
                ),
            )

            mfr.contains("vivo") || mfr.contains("iqoo") -> OemBatteryGuide(
                manufacturer = "vivo",
                summary = "Open Settings > Battery > Background power consumption management and allow Aura. Also check i Manager > App manager > Auto-start manager.",
                settingsIntent = resolveFirst(
                    context,
                    appDetailsIntent(pkg),
                ),
            )

            mfr.contains("asus") -> OemBatteryGuide(
                manufacturer = "ASUS",
                summary = "Open Settings > Battery > PowerMaster > Auto-start manager and enable Aura.",
                settingsIntent = resolveFirst(
                    context,
                    componentIntent("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
                    appDetailsIntent(pkg),
                ),
            )

            else -> null
        }
    }

    private fun componentIntent(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))

    private fun appDetailsIntent(pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$pkg"))

    private fun resolveFirst(context: Context, vararg intents: Intent): Intent? =
        intents.firstOrNull { context.packageManager.resolveActivity(it, 0) != null }
}
