package com.freevibe.service

import com.freevibe.data.model.ContentSource
import java.net.URI
import java.util.Locale

internal const val IMPORT_MAX_TEXT_LENGTH = 512
internal const val IMPORT_MAX_URL_LENGTH = 2048

private val IMPORT_CONTENT_SOURCES: Set<String> = ContentSource.entries.map { it.name }.toSet()

internal fun normalizeImportedText(
    value: String?,
    maxLength: Int = IMPORT_MAX_TEXT_LENGTH,
): String = value.orEmpty().trim().take(maxLength)

internal fun normalizeImportedOptionalText(
    value: String?,
    maxLength: Int = IMPORT_MAX_TEXT_LENGTH,
): String? = normalizeImportedText(value, maxLength).takeIf { it.isNotBlank() }

internal fun isAllowedImportedHttpsUrl(
    url: String?,
    allowBlank: Boolean = false,
): Boolean {
    val trimmed = url.orEmpty().trim()
    if (trimmed.isBlank()) return allowBlank
    if (trimmed.length > IMPORT_MAX_URL_LENGTH) return false
    val scheme = runCatching { URI(trimmed).scheme }
        .getOrNull()
        ?.lowercase(Locale.ROOT)
        ?: return false
    return scheme == "https"
}

internal fun normalizeImportedHttpsUrl(
    url: String?,
    allowBlank: Boolean = false,
): String? {
    val trimmed = url.orEmpty().trim()
    if (trimmed.isBlank()) return if (allowBlank) "" else null
    return trimmed.takeIf { isAllowedImportedHttpsUrl(it) }
}

internal fun normalizeImportedContentSource(
    source: String?,
    blankDefault: String? = null,
): String? {
    val normalized = source.orEmpty()
        .trim()
        .ifBlank { blankDefault.orEmpty() }
        .uppercase(Locale.ROOT)
    return normalized.takeIf { it in IMPORT_CONTENT_SOURCES }
}
