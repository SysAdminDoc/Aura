package com.freevibe.data.model

import java.util.Locale

enum class WallpaperAction {
    APPLY,
    DOWNLOAD,
    SHARE,
    EDIT,
}

enum class WallpaperActionDecision {
    ALLOWED,
    CONFIRMATION_REQUIRED,
    DISABLED,
}

data class WallpaperActionCapability(
    val decision: WallpaperActionDecision,
    val reason: String = "",
)

data class WallpaperLicenseCapabilities(
    val normalizedLicense: String,
    val attributionRequired: Boolean,
    val sourceLinkRequired: Boolean,
    val uploaderRequired: Boolean,
    val actions: Map<WallpaperAction, WallpaperActionCapability>,
) {
    fun capability(action: WallpaperAction): WallpaperActionCapability =
        actions.getValue(action)

    fun canUse(action: WallpaperAction): Boolean =
        capability(action).decision != WallpaperActionDecision.DISABLED

    fun requiresConfirmation(action: WallpaperAction): Boolean =
        capability(action).decision == WallpaperActionDecision.CONFIRMATION_REQUIRED
}

fun Wallpaper.wallpaperLicenseCapabilities(): WallpaperLicenseCapabilities {
    if (isSourceUnavailable()) {
        return WallpaperLicenseCapabilities(
            normalizedLicense = normalizeWallpaperLicense(source, license),
            attributionRequired = false,
            sourceLinkRequired = false,
            uploaderRequired = false,
            actions = disabledWallpaperActions("Source is unavailable; live-source wallpaper actions are disabled."),
        )
    }

    val normalizedLicense = normalizeWallpaperLicense(source, license)
    val licenseKey = normalizedLicense.uppercase(Locale.ROOT)
    val isCreativeCommons = licenseKey.startsWith("CC ")
    val isCc0Compatible = licenseKey == "CC0" || licenseKey == "CC0 1.0" || licenseKey == "PUBLIC DOMAIN"
    val isNoDerivatives = licenseKey.contains("-ND")
    val isNonCommercial = licenseKey.contains("-NC")
    val attributionRequired = isCreativeCommons && !isCc0Compatible
    val sourceLinkRequired = source in REMOTE_WALLPAPER_SOURCES || attributionRequired
    val uploaderRequired = source in REMOTE_WALLPAPER_SOURCES || attributionRequired
    val missingLicense = normalizedLicense == WALLPAPER_UNKNOWN_LICENSE
    val hasSourceLink = sourcePageUrl.isNotBlank() || (source == ContentSource.COMMUNITY && fullUrl.isNotBlank())
    val missingSourceLink = sourceLinkRequired && !hasSourceLink
    val missingUploader = uploaderRequired && uploaderName.isBlank()

    val actions = mutableAllowedWallpaperActions()

    if (missingLicense && source in REMOTE_WALLPAPER_SOURCES) {
        requireWallpaperConfirmation(actions, WallpaperAction.APPLY, "Confirm source terms before applying this wallpaper.")
        requireWallpaperConfirmation(actions, WallpaperAction.DOWNLOAD, "Confirm source terms before downloading this wallpaper.")
        requireWallpaperConfirmation(actions, WallpaperAction.SHARE, "Confirm source terms before sharing this wallpaper.")
        requireWallpaperConfirmation(actions, WallpaperAction.EDIT, "Confirm source terms before editing this wallpaper.")
    }

    if (missingSourceLink || missingUploader) {
        val missing = buildList {
            if (missingSourceLink) add("source link")
            if (missingUploader) add("uploader")
        }.joinToString(" and ")
        disableWallpaperAction(actions, WallpaperAction.SHARE, "Share is disabled until the wallpaper has $missing metadata.")
    }

    when (source) {
        ContentSource.BING -> {
            requireWallpaperConfirmation(actions, WallpaperAction.DOWNLOAD, "Confirm Bing copyright terms before downloading this wallpaper.")
            requireWallpaperConfirmation(actions, WallpaperAction.EDIT, "Confirm Bing copyright terms before editing this wallpaper.")
            disableWallpaperAction(actions, WallpaperAction.SHARE, "Bing daily images are view-and-apply only; sharing the image is not permitted.")
        }
        ContentSource.REDDIT -> {
            requireWallpaperConfirmation(actions, WallpaperAction.APPLY, "Confirm Reddit source availability before applying this wallpaper.")
            requireWallpaperConfirmation(actions, WallpaperAction.DOWNLOAD, "Confirm Reddit source terms before downloading this wallpaper.")
            disableWallpaperAction(actions, WallpaperAction.EDIT, "Reddit wallpapers cannot be edited in Aura.")
        }
        ContentSource.COMMUNITY -> {
            if (normalizedLicense == "User Upload") {
                requireWallpaperConfirmation(actions, WallpaperAction.APPLY, "Confirm community upload rights before applying this wallpaper.")
                requireWallpaperConfirmation(actions, WallpaperAction.DOWNLOAD, "Confirm community upload rights before downloading this wallpaper.")
                requireWallpaperConfirmation(actions, WallpaperAction.EDIT, "Confirm community upload rights before editing this wallpaper.")
            }
        }
        ContentSource.AI_GENERATED -> {
            requireWallpaperConfirmation(actions, WallpaperAction.SHARE, "Confirm generator terms before sharing this AI-generated wallpaper.")
        }
        else -> Unit
    }

    if (isNonCommercial) {
        requireWallpaperConfirmation(actions, WallpaperAction.APPLY, "Confirm non-commercial license terms before applying this wallpaper.")
        requireWallpaperConfirmation(actions, WallpaperAction.DOWNLOAD, "Confirm non-commercial license terms before downloading this wallpaper.")
        requireWallpaperConfirmation(actions, WallpaperAction.EDIT, "Confirm non-commercial license terms before editing this wallpaper.")
    } else if (isNoDerivatives) {
        disableWallpaperAction(actions, WallpaperAction.EDIT, "No-derivatives wallpapers cannot be edited.")
    }

    return WallpaperLicenseCapabilities(
        normalizedLicense = normalizedLicense,
        attributionRequired = attributionRequired,
        sourceLinkRequired = sourceLinkRequired,
        uploaderRequired = uploaderRequired,
        actions = actions,
    )
}

fun Wallpaper.canUseWallpaperAction(action: WallpaperAction): Boolean =
    wallpaperLicenseCapabilities().canUse(action)

fun Wallpaper.requiresWallpaperActionConfirmation(action: WallpaperAction): Boolean =
    wallpaperLicenseCapabilities().requiresConfirmation(action)

fun Wallpaper.wallpaperActionMessage(action: WallpaperAction): String =
    wallpaperLicenseCapabilities().capability(action).reason

fun normalizeWallpaperLicense(source: ContentSource, license: String): String {
    val raw = license.trim()
    if (source == ContentSource.BING && raw.isBlank()) return "Bing Daily"
    if (source == ContentSource.REDDIT && raw.isBlank()) return "Reddit"
    if (source == ContentSource.COMMUNITY && raw.isBlank()) return "User Upload"
    if (source == ContentSource.AI_GENERATED && raw.isBlank()) return "AI Generated"
    if (source == ContentSource.LOCAL && raw.isBlank()) return "Local User Content"
    if (raw.isBlank()) return WALLPAPER_UNKNOWN_LICENSE

    val key = raw.uppercase(Locale.ROOT)
    return when {
        key.contains("CC0") || key.contains("CREATIVE COMMONS 0") -> "CC0"
        key.contains("PUBLIC DOMAIN") || key == "PDM" -> "Public Domain"
        key.contains("BY-NC-ND") || key.contains("ATTRIBUTION-NONCOMMERCIAL-NODERIVS") -> "CC BY-NC-ND"
        key.contains("BY-NC-SA") || key.contains("ATTRIBUTION-NONCOMMERCIAL-SHAREALIKE") -> "CC BY-NC-SA"
        key.contains("BY-NC") || key.contains("ATTRIBUTION-NONCOMMERCIAL") -> "CC BY-NC"
        key.contains("BY-ND") || key.contains("ATTRIBUTION-NODERIVS") -> "CC BY-ND"
        key.contains("BY-SA") || key.contains("ATTRIBUTION-SHAREALIKE") -> "CC BY-SA"
        key == "BY" || key.contains("CC BY") || key.contains("ATTRIBUTION") -> "CC BY"
        key.contains("PEXELS") -> "Pexels License"
        key.contains("PIXABAY") -> "Pixabay License"
        key.contains("USER UPLOAD") -> "User Upload"
        else -> raw.take(80)
    }
}

private const val WALLPAPER_UNKNOWN_LICENSE = "Unknown"

private val REMOTE_WALLPAPER_SOURCES = setOf(
    ContentSource.WALLHAVEN,
    ContentSource.PEXELS,
    ContentSource.PIXABAY,
    ContentSource.BING,
    ContentSource.REDDIT,
    ContentSource.NASA,
    ContentSource.WIKIMEDIA,
    ContentSource.PICSUM,
    ContentSource.COMMUNITY,
)

private fun mutableAllowedWallpaperActions(): MutableMap<WallpaperAction, WallpaperActionCapability> =
    WallpaperAction.entries.associateWith {
        WallpaperActionCapability(WallpaperActionDecision.ALLOWED)
    }.toMutableMap()

private fun disabledWallpaperActions(reason: String): Map<WallpaperAction, WallpaperActionCapability> =
    WallpaperAction.entries.associateWith {
        WallpaperActionCapability(WallpaperActionDecision.DISABLED, reason)
    }

private fun requireWallpaperConfirmation(
    actions: MutableMap<WallpaperAction, WallpaperActionCapability>,
    action: WallpaperAction,
    reason: String,
) {
    if (actions[action]?.decision == WallpaperActionDecision.DISABLED) return
    actions[action] = WallpaperActionCapability(WallpaperActionDecision.CONFIRMATION_REQUIRED, reason)
}

private fun disableWallpaperAction(
    actions: MutableMap<WallpaperAction, WallpaperActionCapability>,
    action: WallpaperAction,
    reason: String,
) {
    actions[action] = WallpaperActionCapability(WallpaperActionDecision.DISABLED, reason)
}
