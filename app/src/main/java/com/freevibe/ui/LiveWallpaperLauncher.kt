package com.freevibe.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import androidx.annotation.StringRes
import com.freevibe.R

internal enum class LiveWallpaperLaunchMode {
    DIRECT,
    CHOOSER,
}

/** Why the system live wallpaper picker can't be opened for one of Aura's wallpaper services. */
internal enum class LiveWallpaperBlock {
    /** The device ships without live wallpaper support. Nothing Aura does can change that. */
    UNSUPPORTED,

    /** The wallpaper service is missing or disabled, or no activity answers either picker action. */
    UNAVAILABLE,

    /** A picker exists and the system refused to let Aura open it. The manual route still works. */
    DENIED,
}

internal sealed interface LiveWallpaperLaunchResult {
    data class Opened(val mode: LiveWallpaperLaunchMode) : LiveWallpaperLaunchResult
    data class Blocked(val reason: LiveWallpaperBlock) : LiveWallpaperLaunchResult
}

/** What a live wallpaper is built from, which decides what Aura can offer when one can't be set. */
internal enum class LiveWallpaperSource {
    IMAGE,
    VIDEO,
}

internal data class LiveWallpaperFallback(
    @StringRes val messageRes: Int,
    /** True when the source is a picture, so it can still be set as an ordinary wallpaper. */
    val offersStaticApply: Boolean,
)

/** The platform answers the preflight needs, split out so the decision is testable off a device. */
internal interface LiveWallpaperPlatform {
    val hasLiveWallpaperFeature: Boolean
    val hasWallpaperService: Boolean
    fun resolves(mode: LiveWallpaperLaunchMode): Boolean

    /** Throws whatever `startActivity` throws. */
    fun start(mode: LiveWallpaperLaunchMode)
}

/**
 * Checks that the picker can be opened without opening it. Callers run this before the costly
 * part (a download, a depth map, a gallery pick) so nobody does that work for a wallpaper the
 * device can't show. A null result means the launch is worth trying.
 */
internal fun liveWallpaperBlock(context: Context, serviceComponent: ComponentName): LiveWallpaperBlock? =
    liveWallpaperBlock(AndroidLiveWallpaperPlatform(context, serviceComponent))

internal fun liveWallpaperBlock(platform: LiveWallpaperPlatform): LiveWallpaperBlock? = when {
    !platform.hasLiveWallpaperFeature -> LiveWallpaperBlock.UNSUPPORTED
    !platform.hasWallpaperService -> LiveWallpaperBlock.UNAVAILABLE
    LiveWallpaperLaunchMode.entries.none(platform::resolves) -> LiveWallpaperBlock.UNAVAILABLE
    else -> null
}

internal fun launchLiveWallpaperPicker(
    context: Context,
    serviceComponent: ComponentName,
    tag: String,
): LiveWallpaperLaunchResult = launchLiveWallpaperPicker(AndroidLiveWallpaperPlatform(context, serviceComponent), tag)

internal fun launchLiveWallpaperPicker(platform: LiveWallpaperPlatform, tag: String): LiveWallpaperLaunchResult {
    liveWallpaperBlock(platform)?.let { return LiveWallpaperLaunchResult.Blocked(it) }

    var denied = false
    for (mode in LiveWallpaperLaunchMode.entries) {
        // An action nothing answers is never started, so a failed launch can't be read as success.
        if (!platform.resolves(mode)) continue
        try {
            platform.start(mode)
            return LiveWallpaperLaunchResult.Opened(mode)
        } catch (e: ActivityNotFoundException) {
            logLaunchFailure("Activity missing for $tag:$mode", e)
        } catch (e: SecurityException) {
            denied = true
            logLaunchFailure("Security error for $tag:$mode", e)
        } catch (e: Exception) {
            logLaunchFailure("Unexpected failure for $tag:$mode", e)
        }
    }
    return LiveWallpaperLaunchResult.Blocked(
        if (denied) LiveWallpaperBlock.DENIED else LiveWallpaperBlock.UNAVAILABLE,
    )
}

internal fun liveWallpaperFallback(block: LiveWallpaperBlock, source: LiveWallpaperSource): LiveWallpaperFallback =
    LiveWallpaperFallback(
        messageRes = when (block) {
            LiveWallpaperBlock.UNSUPPORTED -> R.string.live_wallpaper_blocked_unsupported
            LiveWallpaperBlock.UNAVAILABLE -> R.string.live_wallpaper_blocked_unavailable
            LiveWallpaperBlock.DENIED -> R.string.live_wallpaper_blocked_denied
        },
        offersStaticApply = source == LiveWallpaperSource.IMAGE,
    )

/** The reason on its own for a picture, and with the "video only works live" line for a video. */
internal fun liveWallpaperBlockedMessage(
    context: Context,
    block: LiveWallpaperBlock,
    source: LiveWallpaperSource,
): String {
    val reason = context.getString(liveWallpaperFallback(block, source).messageRes)
    // After a refusal the picker is still there, so the reason already says how to finish by hand.
    return if (source == LiveWallpaperSource.VIDEO && block != LiveWallpaperBlock.DENIED) {
        context.getString(R.string.live_wallpaper_video_limit, reason)
    } else {
        reason
    }
}

/** Opens the picker for [serviceClass] and returns the line to show: how to finish, or why it can't start. */
internal fun launchLiveWallpaperWithFeedback(
    context: Context,
    serviceClass: Class<out WallpaperService>,
    tag: String,
    source: LiveWallpaperSource,
    @StringRes directRes: Int,
    @StringRes chooserRes: Int,
): String = when (val launch = launchLiveWallpaperPicker(context, ComponentName(context, serviceClass), tag)) {
    is LiveWallpaperLaunchResult.Opened ->
        context.getString(if (launch.mode == LiveWallpaperLaunchMode.DIRECT) directRes else chooserRes)
    is LiveWallpaperLaunchResult.Blocked -> liveWallpaperBlockedMessage(context, launch.reason, source)
}

/** Null when the picker can be opened for [serviceClass], else the reason to show instead of starting. */
internal fun liveWallpaperBlockMessage(
    context: Context,
    serviceClass: Class<out WallpaperService>,
    source: LiveWallpaperSource,
): String? = liveWallpaperBlock(context, ComponentName(context, serviceClass))
    ?.let { liveWallpaperBlockedMessage(context, it, source) }

private fun logLaunchFailure(message: String, error: Exception) {
    if (com.freevibe.BuildConfig.DEBUG) {
        Log.w("LiveWallpaper", message, error)
    }
}

private class AndroidLiveWallpaperPlatform(
    private val context: Context,
    private val serviceComponent: ComponentName,
) : LiveWallpaperPlatform {
    private val packageManager: PackageManager get() = context.packageManager

    override val hasLiveWallpaperFeature: Boolean
        get() = packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER)

    // The same lookup the system picker runs, so a disabled or unexported service counts as missing.
    override val hasWallpaperService: Boolean
        get() = packageManager
            .queryIntentServices(
                Intent(WallpaperService.SERVICE_INTERFACE).setPackage(serviceComponent.packageName),
                0,
            )
            .any { it.serviceInfo?.name == serviceComponent.className }

    override fun resolves(mode: LiveWallpaperLaunchMode): Boolean =
        packageManager.resolveActivity(intentFor(mode), PackageManager.MATCH_DEFAULT_ONLY) != null

    override fun start(mode: LiveWallpaperLaunchMode) = context.startActivity(intentFor(mode))

    private fun intentFor(mode: LiveWallpaperLaunchMode): Intent = when (mode) {
        LiveWallpaperLaunchMode.DIRECT -> Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, serviceComponent)
        LiveWallpaperLaunchMode.CHOOSER -> Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
