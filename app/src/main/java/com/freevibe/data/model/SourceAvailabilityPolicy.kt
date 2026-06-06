package com.freevibe.data.model

import java.util.Locale

private val REMOTE_REMOVED_REGEX = Regex(
    pattern = """\b(?:HTTP\s*)?(?:404|410)\b|content not found|not found|gone|removed|deleted""",
    option = RegexOption.IGNORE_CASE,
)

fun sourceUnavailableReasonForFailure(
    source: ContentSource,
    failure: Throwable?,
): String? = sourceUnavailableReasonForMessage(source.name, failure?.message)

fun sourceUnavailableReasonForFailure(
    source: String,
    failure: Throwable?,
): String? = sourceUnavailableReasonForMessage(source, failure?.message)

fun sourceUnavailableReasonForMessage(
    source: String,
    message: String?,
): String? {
    val raw = message?.takeIf { REMOTE_REMOVED_REGEX.containsMatchIn(it) } ?: return null
    val sourceName = source.uppercase(Locale.ROOT)
    return when (sourceName) {
        ContentSource.REDDIT.name -> "Source post is unavailable or removed"
        ContentSource.PEXELS.name -> "Pexels media is unavailable or removed"
        ContentSource.PIXABAY.name -> "Pixabay media is unavailable or removed"
        ContentSource.YOUTUBE.name -> "YouTube media is unavailable or removed"
        ContentSource.COMMUNITY.name -> "Community upload is unavailable or removed"
        ContentSource.FREESOUND.name -> "Freesound media is unavailable or removed"
        ContentSource.SOUNDCLOUD.name -> "SoundCloud media is unavailable or removed"
        else -> "Source content is unavailable or removed"
    }.let { reason ->
        if (raw.contains("410")) "$reason (gone)" else reason
    }
}
