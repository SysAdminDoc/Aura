package com.freevibe.data.model

import com.freevibe.ui.screens.videowallpapers.VideoWallpaperItem
import java.util.Locale

enum class VideoWallpaperAction {
    APPLY,
    DOWNLOAD,
    SHARE,
}

enum class VideoWallpaperActionDecision {
    ALLOWED,
    CONFIRMATION_REQUIRED,
    DISABLED,
}

data class VideoWallpaperActionCapability(
    val decision: VideoWallpaperActionDecision,
    val reason: String = "",
)

data class VideoWallpaperLicenseCapabilities(
    val normalizedLicense: String,
    val attributionRequired: Boolean,
    val sourceLinkRequired: Boolean,
    val uploaderRequired: Boolean,
    val actions: Map<VideoWallpaperAction, VideoWallpaperActionCapability>,
) {
    fun capability(action: VideoWallpaperAction): VideoWallpaperActionCapability =
        actions.getValue(action)

    fun canUse(action: VideoWallpaperAction): Boolean =
        capability(action).decision != VideoWallpaperActionDecision.DISABLED

    fun requiresConfirmation(action: VideoWallpaperAction): Boolean =
        capability(action).decision == VideoWallpaperActionDecision.CONFIRMATION_REQUIRED
}

fun VideoWallpaperItem.videoWallpaperLicenseCapabilities(): VideoWallpaperLicenseCapabilities {
    val normalizedLicense = normalizeVideoWallpaperLicense(contentSource, license)
    val attributionRequired = contentSource in REMOTE_VIDEO_SOURCES
    val sourceLinkRequired = contentSource in REMOTE_VIDEO_SOURCES
    val uploaderRequired = contentSource in REMOTE_VIDEO_SOURCES
    val missingSourceLink = sourceLinkRequired && sourcePageUrl.isBlank()
    val missingUploader = uploaderRequired && uploaderName.isBlank()

    val actions = mutableAllowedVideoActions()

    if (missingSourceLink || missingUploader) {
        val missing = buildList {
            if (missingSourceLink) add("source link")
            if (missingUploader) add("uploader")
        }.joinToString(" and ")
        disableVideoAction(actions, VideoWallpaperAction.SHARE, "Share is disabled until the video has $missing metadata.")
    }

    when (contentSource) {
        ContentSource.PEXELS -> {
            requireVideoConfirmation(actions, VideoWallpaperAction.DOWNLOAD, "Confirm Pexels license terms before downloading this video.")
        }
        ContentSource.PIXABAY -> {
            requireVideoConfirmation(actions, VideoWallpaperAction.DOWNLOAD, "Confirm Pixabay license terms before downloading this video.")
        }
        ContentSource.YOUTUBE -> {
            requireVideoConfirmation(actions, VideoWallpaperAction.APPLY, "Confirm YouTube source terms before applying this video wallpaper.")
            requireVideoConfirmation(actions, VideoWallpaperAction.DOWNLOAD, "Confirm YouTube source terms before downloading this video.")
            disableVideoAction(actions, VideoWallpaperAction.SHARE, "YouTube videos cannot be shared outside Aura.")
        }
        ContentSource.REDDIT -> {
            requireVideoConfirmation(actions, VideoWallpaperAction.APPLY, "Confirm Reddit source availability before applying this video wallpaper.")
            requireVideoConfirmation(actions, VideoWallpaperAction.DOWNLOAD, "Confirm Reddit source terms before downloading this video.")
        }
        ContentSource.LOCAL -> Unit
        else -> Unit
    }

    return VideoWallpaperLicenseCapabilities(
        normalizedLicense = normalizedLicense,
        attributionRequired = attributionRequired,
        sourceLinkRequired = sourceLinkRequired,
        uploaderRequired = uploaderRequired,
        actions = actions,
    )
}

fun VideoWallpaperItem.canUseVideoAction(action: VideoWallpaperAction): Boolean =
    videoWallpaperLicenseCapabilities().canUse(action)

fun VideoWallpaperItem.requiresVideoActionConfirmation(action: VideoWallpaperAction): Boolean =
    videoWallpaperLicenseCapabilities().requiresConfirmation(action)

fun VideoWallpaperItem.videoActionMessage(action: VideoWallpaperAction): String =
    videoWallpaperLicenseCapabilities().capability(action).reason

fun normalizeVideoWallpaperLicense(source: ContentSource, license: String): String {
    val raw = license.trim()
    if (source == ContentSource.YOUTUBE) return "YouTube"
    if (source == ContentSource.REDDIT && raw.isBlank()) return "Reddit"
    if (source == ContentSource.LOCAL && raw.isBlank()) return "Local User Content"
    if (raw.isBlank()) return VIDEO_UNKNOWN_LICENSE

    val key = raw.uppercase(Locale.ROOT)
    return when {
        key.contains("PEXELS") -> "Pexels License"
        key.contains("PIXABAY") -> "Pixabay License"
        else -> raw.take(80)
    }
}

private const val VIDEO_UNKNOWN_LICENSE = "Unknown"

private val REMOTE_VIDEO_SOURCES = setOf(
    ContentSource.PEXELS,
    ContentSource.PIXABAY,
    ContentSource.YOUTUBE,
    ContentSource.REDDIT,
)

private fun mutableAllowedVideoActions(): MutableMap<VideoWallpaperAction, VideoWallpaperActionCapability> =
    VideoWallpaperAction.entries.associateWith {
        VideoWallpaperActionCapability(VideoWallpaperActionDecision.ALLOWED)
    }.toMutableMap()

private fun requireVideoConfirmation(
    actions: MutableMap<VideoWallpaperAction, VideoWallpaperActionCapability>,
    action: VideoWallpaperAction,
    reason: String,
) {
    if (actions[action]?.decision == VideoWallpaperActionDecision.DISABLED) return
    actions[action] = VideoWallpaperActionCapability(VideoWallpaperActionDecision.CONFIRMATION_REQUIRED, reason)
}

private fun disableVideoAction(
    actions: MutableMap<VideoWallpaperAction, VideoWallpaperActionCapability>,
    action: VideoWallpaperAction,
    reason: String,
) {
    actions[action] = VideoWallpaperActionCapability(VideoWallpaperActionDecision.DISABLED, reason)
}
