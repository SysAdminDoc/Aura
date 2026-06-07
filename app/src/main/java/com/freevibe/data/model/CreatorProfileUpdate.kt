package com.freevibe.data.model

import java.net.URI
import java.util.Locale

private const val MAX_CREATOR_PROFILE_DISPLAY_NAME = 80
private const val MAX_CREATOR_PROFILE_BIO = 280
private const val MAX_CREATOR_PROFILE_URL = 2048
private val CREATOR_PROFILE_CONTROL_REGEX = Regex("[\\u0000-\\u001F\\u007F]")
private val CREATOR_PROFILE_WHITESPACE_REGEX = Regex("\\s+")

data class CreatorProfileUpdateInput(
    val displayName: String,
    val bio: String = "",
    val websiteUrl: String = "",
    val avatarUrl: String = "",
)

fun buildCreatorProfileUpdateCallablePayload(input: CreatorProfileUpdateInput): Map<String, Any> {
    val displayName = normalizeCreatorProfileText(input.displayName, MAX_CREATOR_PROFILE_DISPLAY_NAME)
    require(displayName.length >= 2) { "Display name must contain at least two characters" }

    return mapOf(
        "displayName" to displayName,
        "bio" to normalizeCreatorProfileText(input.bio, MAX_CREATOR_PROFILE_BIO),
        "websiteUrl" to normalizeCreatorProfileHttpsUrl(input.websiteUrl, "websiteUrl"),
        "avatarUrl" to normalizeCreatorProfileHttpsUrl(input.avatarUrl, "avatarUrl"),
    )
}

fun normalizeCreatorProfileInput(input: CreatorProfileUpdateInput): CreatorProfileUpdateInput =
    buildCreatorProfileUpdateCallablePayload(input).let { payload ->
        CreatorProfileUpdateInput(
            displayName = payload.getValue("displayName").toString(),
            bio = payload.getValue("bio").toString(),
            websiteUrl = payload.getValue("websiteUrl").toString(),
            avatarUrl = payload.getValue("avatarUrl").toString(),
        )
    }

private fun normalizeCreatorProfileText(value: String, maxLength: Int): String =
    value
        .replace(CREATOR_PROFILE_CONTROL_REGEX, " ")
        .replace(CREATOR_PROFILE_WHITESPACE_REGEX, " ")
        .trim()
        .take(maxLength)

private fun normalizeCreatorProfileHttpsUrl(value: String, field: String): String {
    val normalized = normalizeCreatorProfileText(value, MAX_CREATOR_PROFILE_URL)
    if (normalized.isBlank()) return ""
    val uri = runCatching { URI(normalized) }.getOrNull()
    require(uri?.scheme?.lowercase(Locale.ROOT) == "https") { "$field must use HTTPS" }
    require(!uri.host.isNullOrBlank()) { "$field must include a host" }
    return normalized
}
