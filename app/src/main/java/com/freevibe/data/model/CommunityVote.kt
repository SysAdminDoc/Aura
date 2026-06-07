package com.freevibe.data.model

private const val MAX_COMMUNITY_VOTE_CONTENT_ID = 240
private val COMMUNITY_VOTE_FIREBASE_KEY_REGEX = Regex("[.#$\\[\\]/]")
private val COMMUNITY_VOTE_WHITESPACE_REGEX = Regex("\\s+")
private val COMMUNITY_VOTE_CONTROL_REGEX = Regex("[\\u0000-\\u001F\\u007F]")

data class CommunityVoteInput(
    val contentId: String,
)

fun buildCommunityVoteCallablePayload(contentId: String): Map<String, Any> =
    mapOf("contentId" to normalizeCommunityVoteContentId(contentId))

internal fun normalizeCommunityVoteContentId(value: String): String {
    val normalized = value
        .replace(COMMUNITY_VOTE_CONTROL_REGEX, " ")
        .replace(COMMUNITY_VOTE_WHITESPACE_REGEX, " ")
        .trim()
        .take(MAX_COMMUNITY_VOTE_CONTENT_ID)
        .replace(COMMUNITY_VOTE_FIREBASE_KEY_REGEX, "_")
    require(normalized.isNotBlank()) { "Vote content ID is required" }
    return normalized
}
