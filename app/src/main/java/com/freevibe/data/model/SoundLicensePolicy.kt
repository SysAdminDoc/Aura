package com.freevibe.data.model

import java.util.Locale

enum class SoundAction {
    APPLY,
    DOWNLOAD,
    SHARE,
    EDIT,
    BUNDLE,
}

enum class SoundActionDecision {
    ALLOWED,
    CONFIRMATION_REQUIRED,
    DISABLED,
}

data class SoundActionCapability(
    val decision: SoundActionDecision,
    val reason: String = "",
)

data class SoundLicenseCapabilities(
    val normalizedLicense: String,
    val attributionRequired: Boolean,
    val sourceLinkRequired: Boolean,
    val uploaderRequired: Boolean,
    val actions: Map<SoundAction, SoundActionCapability>,
) {
    fun capability(action: SoundAction): SoundActionCapability =
        actions.getValue(action)

    fun canUse(action: SoundAction): Boolean =
        capability(action).decision != SoundActionDecision.DISABLED

    fun requiresConfirmation(action: SoundAction): Boolean =
        capability(action).decision == SoundActionDecision.CONFIRMATION_REQUIRED
}

fun Sound.soundLicenseCapabilities(): SoundLicenseCapabilities {
    if (isSourceUnavailable()) {
        return SoundLicenseCapabilities(
            normalizedLicense = normalizeSoundLicense(source, license),
            attributionRequired = false,
            sourceLinkRequired = false,
            uploaderRequired = false,
            actions = disabledActions("Source is unavailable; live-source sound actions are disabled."),
        )
    }

    val normalizedLicense = normalizeSoundLicense(source, license)
    val licenseKey = normalizedLicense.uppercase(Locale.ROOT)
    val isCreativeCommons = licenseKey.startsWith("CC ")
    val isCc0Compatible = licenseKey == "CC0" || licenseKey == "CC0 1.0" || licenseKey == "PUBLIC DOMAIN"
    val isNoDerivatives = licenseKey.contains("-ND")
    val isNonCommercial = licenseKey.contains("-NC")
    val attributionRequired = isCreativeCommons && !isCc0Compatible
    val sourceLinkRequired = source in REMOTE_SOUND_SOURCES || attributionRequired
    val uploaderRequired = source in REMOTE_SOUND_SOURCES || attributionRequired
    val missingLicense = normalizedLicense == UNKNOWN_LICENSE
    val hasSourceLink = sourcePageUrl.isNotBlank() || (source == ContentSource.COMMUNITY && downloadUrl.isNotBlank())
    val missingSourceLink = sourceLinkRequired && !hasSourceLink
    val missingUploader = uploaderRequired && uploaderName.isBlank()

    val actions = mutableAllowedActions()

    if (missingLicense) {
        return SoundLicenseCapabilities(
            normalizedLicense = normalizedLicense,
            attributionRequired = true,
            sourceLinkRequired = sourceLinkRequired,
            uploaderRequired = uploaderRequired,
            actions = disabledActions("Sound license metadata is missing; apply, download, edit, and share are disabled."),
        )
    }

    if (missingSourceLink || missingUploader) {
        val missing = buildList {
            if (missingSourceLink) add("source link")
            if (missingUploader) add("uploader")
        }.joinToString(" and ")
        disable(actions, SoundAction.SHARE, "Share is disabled until the sound has $missing metadata.")
        requireConfirmation(actions, SoundAction.APPLY, "Confirm license metadata before applying this sound.")
        requireConfirmation(actions, SoundAction.DOWNLOAD, "Confirm license metadata before downloading this sound.")
        requireConfirmation(actions, SoundAction.EDIT, "Confirm license metadata before editing this sound.")
    }

    when (source) {
        ContentSource.YOUTUBE -> {
            requireConfirmation(actions, SoundAction.APPLY, "Confirm YouTube source terms before applying this sound.")
            requireConfirmation(actions, SoundAction.DOWNLOAD, "Confirm YouTube source terms before downloading this sound.")
            disable(actions, SoundAction.EDIT, "YouTube sounds cannot be trimmed or normalized in Aura.")
            disable(actions, SoundAction.BUNDLE, "YouTube sounds cannot be included in Aura Originals.")
        }
        ContentSource.SOUNDCLOUD -> {
            disable(actions, SoundAction.APPLY, "SoundCloud sounds are link-only until source permissions are reviewed.")
            disable(actions, SoundAction.DOWNLOAD, "SoundCloud downloads are disabled until source permissions are reviewed.")
            disable(actions, SoundAction.EDIT, "SoundCloud sounds cannot be edited in Aura.")
            disable(actions, SoundAction.BUNDLE, "SoundCloud sounds cannot be included in Aura Originals.")
        }
        ContentSource.COMMUNITY -> {
            if (normalizedLicense == "User Upload") {
                requireConfirmation(actions, SoundAction.APPLY, "Confirm community upload rights before applying this sound.")
                requireConfirmation(actions, SoundAction.DOWNLOAD, "Confirm community upload rights before downloading this sound.")
                requireConfirmation(actions, SoundAction.EDIT, "Confirm community upload rights before editing this sound.")
            }
            disable(actions, SoundAction.BUNDLE, "Community uploads need rights review before Aura Originals use.")
        }
        ContentSource.LOCAL -> {
            disable(actions, SoundAction.BUNDLE, "Local user files need curation review before Aura Originals use.")
        }
        ContentSource.BUNDLED -> {
            if (!isCc0Compatible) {
                requireConfirmation(actions, SoundAction.APPLY, "Confirm bundled sound curation metadata before applying this sound.")
                requireConfirmation(actions, SoundAction.DOWNLOAD, "Confirm bundled sound curation metadata before downloading this sound.")
                requireConfirmation(actions, SoundAction.EDIT, "Confirm bundled sound curation metadata before editing this sound.")
                disable(actions, SoundAction.BUNDLE, "Bundled sound is not marked CC0-compatible for Aura Originals.")
            }
        }
        else -> Unit
    }

    if (isNonCommercial) {
        requireConfirmation(actions, SoundAction.APPLY, "Confirm non-commercial license terms before applying this sound.")
        requireConfirmation(actions, SoundAction.DOWNLOAD, "Confirm non-commercial license terms before downloading this sound.")
        requireConfirmation(actions, SoundAction.EDIT, "Confirm non-commercial license terms before editing this sound.")
        disable(actions, SoundAction.BUNDLE, "Non-commercial sounds cannot be included in Aura Originals.")
    } else if (isNoDerivatives) {
        disable(actions, SoundAction.EDIT, "No-derivatives sounds cannot be trimmed or normalized.")
        disable(actions, SoundAction.BUNDLE, "No-derivatives sounds cannot be included in Aura Originals.")
    } else if (!isCc0Compatible && source != ContentSource.BUNDLED) {
        disable(actions, SoundAction.BUNDLE, "Only reviewed CC0-compatible sounds can be included in Aura Originals.")
    }

    return SoundLicenseCapabilities(
        normalizedLicense = normalizedLicense,
        attributionRequired = attributionRequired,
        sourceLinkRequired = sourceLinkRequired,
        uploaderRequired = uploaderRequired,
        actions = actions,
    )
}

fun Sound.canUseSoundAction(action: SoundAction): Boolean =
    soundLicenseCapabilities().canUse(action)

fun Sound.requiresSoundActionConfirmation(action: SoundAction): Boolean =
    soundLicenseCapabilities().requiresConfirmation(action)

fun Sound.soundActionMessage(action: SoundAction): String =
    soundLicenseCapabilities().capability(action).reason

fun normalizeSoundLicense(source: ContentSource, license: String): String {
    val raw = license.trim()
    if (source == ContentSource.YOUTUBE) return "YouTube"
    if (source == ContentSource.SOUNDCLOUD && raw.isBlank()) return "SoundCloud"
    if (source == ContentSource.COMMUNITY && raw.isBlank()) return "User Upload"
    if (source == ContentSource.LOCAL && raw.isBlank()) return "Local User Content"
    if (raw.isBlank()) return UNKNOWN_LICENSE

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
        key.contains("YOUTUBE") -> "YouTube"
        key.contains("SOUNDCLOUD") -> "SoundCloud"
        key.contains("USER UPLOAD") -> "User Upload"
        key.contains("AUDIUS") -> "Audius"
        else -> raw.take(80)
    }
}

private const val UNKNOWN_LICENSE = "Unknown"

private val REMOTE_SOUND_SOURCES = setOf(
    ContentSource.FREESOUND,
    ContentSource.JAMENDO,
    ContentSource.AUDIUS,
    ContentSource.CCMIXTER,
    ContentSource.YOUTUBE,
    ContentSource.SOUNDCLOUD,
    ContentSource.COMMUNITY,
    ContentSource.BUNDLED,
)

private fun mutableAllowedActions(): MutableMap<SoundAction, SoundActionCapability> =
    SoundAction.entries.associateWith {
        SoundActionCapability(SoundActionDecision.ALLOWED)
    }.toMutableMap()

private fun disabledActions(reason: String): Map<SoundAction, SoundActionCapability> =
    SoundAction.entries.associateWith {
        SoundActionCapability(SoundActionDecision.DISABLED, reason)
    }

private fun requireConfirmation(
    actions: MutableMap<SoundAction, SoundActionCapability>,
    action: SoundAction,
    reason: String,
) {
    if (actions[action]?.decision == SoundActionDecision.DISABLED) return
    actions[action] = SoundActionCapability(SoundActionDecision.CONFIRMATION_REQUIRED, reason)
}

private fun disable(
    actions: MutableMap<SoundAction, SoundActionCapability>,
    action: SoundAction,
    reason: String,
) {
    actions[action] = SoundActionCapability(SoundActionDecision.DISABLED, reason)
}
