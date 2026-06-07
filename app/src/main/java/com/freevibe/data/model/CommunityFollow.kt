package com.freevibe.data.model

private const val MAX_COMMUNITY_FOLLOW_CREATOR_ID = 240
private const val MAX_COMMUNITY_FOLLOW_LABEL = 120
private val COMMUNITY_FOLLOW_WHITESPACE_REGEX = Regex("\\s+")
private val COMMUNITY_FOLLOW_CONTROL_REGEX = Regex("[\\u0000-\\u001F\\u007F]")

data class CommunityFollowInput(
    val creatorId: String,
    val label: String,
    val following: Boolean,
)

fun buildCommunityFollowCallablePayload(input: CommunityFollowInput): Map<String, Any> {
    val normalizedCreatorId = normalizeCommunityFollowText(
        value = input.creatorId,
        maxLength = MAX_COMMUNITY_FOLLOW_CREATOR_ID,
    )
    require(normalizedCreatorId.isNotBlank()) { "Creator ID is required" }
    val normalizedLabel = normalizeCommunityFollowText(
        value = input.label,
        maxLength = MAX_COMMUNITY_FOLLOW_LABEL,
    ).ifBlank { normalizedCreatorId.take(8) }
    return mapOf(
        "creatorId" to normalizedCreatorId,
        "label" to normalizedLabel,
        "following" to input.following,
    )
}

private fun normalizeCommunityFollowText(value: String, maxLength: Int): String =
    value
        .replace(COMMUNITY_FOLLOW_CONTROL_REGEX, " ")
        .replace(COMMUNITY_FOLLOW_WHITESPACE_REGEX, " ")
        .trim()
        .take(maxLength)
