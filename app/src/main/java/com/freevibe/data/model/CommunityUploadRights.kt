package com.freevibe.data.model

import java.net.URI
import java.util.Locale

data class CommunityUploadRights(
    val license: String,
    val rightsAttested: Boolean,
    val sourceUrl: String = "",
)

val COMMUNITY_UPLOAD_LICENSES = listOf("CC0", "CC BY", "CC BY-NC")

fun validateCommunityUploadRights(
    license: String,
    rightsAttested: Boolean,
    sourceUrl: String = "",
): CommunityUploadRights {
    val normalizedLicense = normalizeCommunityUploadLicense(license)
    require(rightsAttested) { "Confirm you own or have rights to share this upload" }
    val normalizedSourceUrl = normalizeCommunitySourceUrl(sourceUrl)
    return CommunityUploadRights(
        license = normalizedLicense,
        rightsAttested = true,
        sourceUrl = normalizedSourceUrl,
    )
}

fun normalizeCommunityUploadLicense(license: String): String {
    val normalized = license.trim().uppercase(Locale.ROOT)
    return when (normalized) {
        "CC0", "CC0 1.0" -> "CC0"
        "CC BY", "CC-BY", "ATTRIBUTION" -> "CC BY"
        "CC BY-NC", "CC-BY-NC", "ATTRIBUTION-NONCOMMERCIAL" -> "CC BY-NC"
        else -> throw IllegalArgumentException("Choose a supported community license")
    }
}

fun normalizeCommunitySourceUrl(sourceUrl: String): String {
    val trimmed = sourceUrl.trim().take(2048)
    if (trimmed.isBlank()) return ""
    val uri = runCatching { URI(trimmed) }.getOrNull()
    require(uri?.scheme?.lowercase(Locale.ROOT) == "https") { "Source URL must use HTTPS" }
    require(!uri.host.isNullOrBlank()) { "Source URL must include a host" }
    return trimmed
}
