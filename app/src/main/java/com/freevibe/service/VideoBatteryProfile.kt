package com.freevibe.service

internal const val VIDEO_PREFS_NAME = "freevibe_prefs"
internal const val VIDEO_STATS_PREFS_NAME = "freevibe_video_stats"
internal const val VIDEO_FPS_LIMIT_PREF = "video_fps_limit"
internal const val VIDEO_PLAYBACK_SPEED_PREF = "video_playback_speed"
internal const val VIDEO_FPS_OVERLAY_PREF = "video_fps_overlay_enabled"
internal const val VIDEO_AUTO_BATTERY_SAVER_PREF = "video_auto_battery_saver"
internal const val VIDEO_AUTO_BATTERY_SAVER_CHANGED_ACTION =
    "com.freevibe.action.VIDEO_AUTO_BATTERY_SAVER_CHANGED"

internal enum class VideoMotionPowerSaveAction {
    NONE,
    PAUSE,
    RESUME,
}

internal fun sanitizeVideoFpsLimit(fps: Int): Int = when {
    fps <= 15 -> 15
    fps >= 60 -> 60
    else -> 30
}

/** The speeds Settings offers. MediaPlayer handles this range without dropping audio-less video frames. */
internal val VIDEO_PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

internal fun sanitizeVideoPlaybackSpeed(speed: Float): Float =
    if (!speed.isFinite() || speed <= 0f) 1f else speed.coerceIn(VIDEO_PLAYBACK_SPEEDS.first(), VIDEO_PLAYBACK_SPEEDS.last())

internal fun shouldUseVideoBatterySaver(
    batteryPercent: Int?,
    isCharging: Boolean,
    autoSaverEnabled: Boolean,
): Boolean =
    autoSaverEnabled && !isCharging && batteryPercent != null && batteryPercent in 0..14

internal fun shouldPauseVideoMotionForPowerSave(
    systemPowerSaveMode: Boolean,
    autoSaverEnabled: Boolean,
): Boolean = systemPowerSaveMode && autoSaverEnabled

internal fun videoMotionPowerSaveAction(
    wasPausedForPowerSave: Boolean,
    systemPowerSaveMode: Boolean,
    autoSaverEnabled: Boolean,
): VideoMotionPowerSaveAction {
    val shouldPause = shouldPauseVideoMotionForPowerSave(systemPowerSaveMode, autoSaverEnabled)
    return when {
        shouldPause && !wasPausedForPowerSave -> VideoMotionPowerSaveAction.PAUSE
        !shouldPause && wasPausedForPowerSave -> VideoMotionPowerSaveAction.RESUME
        else -> VideoMotionPowerSaveAction.NONE
    }
}

internal fun effectiveVideoFpsLimit(
    requestedFps: Int,
    lowBatterySaverActive: Boolean,
): Int {
    val sanitized = sanitizeVideoFpsLimit(requestedFps)
    return if (lowBatterySaverActive) minOf(sanitized, 15) else sanitized
}

internal fun videoBatteryImpactLabel(
    requestedFps: Int,
    fpsOverlayEnabled: Boolean,
    lowBatterySaverActive: Boolean,
    motionPausedForPowerSave: Boolean = false,
): String {
    if (motionPausedForPowerSave) return "System Battery Saver"
    if (lowBatterySaverActive) return "Low battery saver"
    val sanitized = sanitizeVideoFpsLimit(requestedFps)
    return when {
        sanitized <= 15 -> "Light"
        sanitized >= 60 || fpsOverlayEnabled -> "High"
        else -> "Balanced"
    }
}

internal fun videoBatteryImpactSummary(
    requestedFps: Int,
    effectiveFps: Int,
    fpsOverlayEnabled: Boolean,
    lowBatterySaverActive: Boolean,
    motionPausedForPowerSave: Boolean = false,
): String {
    val impact = videoBatteryImpactLabel(
        requestedFps = requestedFps,
        fpsOverlayEnabled = fpsOverlayEnabled,
        lowBatterySaverActive = lowBatterySaverActive,
        motionPausedForPowerSave = motionPausedForPowerSave,
    )
    return when {
        motionPausedForPowerSave -> "$impact - static frame until saver exits"
        lowBatterySaverActive -> "$impact - capped at ${effectiveFps} FPS until battery recovers"
        fpsOverlayEnabled -> "$impact - ${effectiveFps} FPS target with debug overlay enabled"
        else -> "$impact - ${effectiveFps} FPS target"
    }
}
