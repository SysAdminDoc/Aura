package com.freevibe.service

import com.freevibe.data.legal.isProviderAvailableInCurrentArtifact
import com.freevibe.data.model.ContentSource
import com.freevibe.data.remote.providerSourceForHost
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI
import java.util.Locale

internal const val IMPORT_MAX_TEXT_LENGTH = LibraryTransferContract.MAX_TEXT_CHARS
internal const val IMPORT_MAX_URL_LENGTH = LibraryTransferContract.MAX_URL_CHARS

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

/**
 * True for an https address on a host that belongs to one of Aura's providers, and only while this
 * build can use that provider. The hosts come from the provider registry, the same list the network
 * layer checks requests against.
 *
 * Imported data that a background job fetches with nobody looking is held to this, so a backup
 * can't point the phone at a host of its author's choosing. The host is read the way the HTTP
 * client reads it, which is what decides where a request really goes.
 */
internal fun isImportedProviderMediaUrl(
    url: String?,
    isAvailable: (ContentSource) -> Boolean = ::isProviderAvailableInCurrentArtifact,
): Boolean {
    if (!isAllowedImportedHttpsUrl(url)) return false
    val host = url.orEmpty().trim().toHttpUrlOrNull()?.takeIf { it.isHttps }?.host ?: return false
    return providerSourceForHost(host)?.let(isAvailable) ?: false
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

private const val IMPORT_MAX_FILE_NAME_LENGTH = 255

/**
 * Sources whose stored address either expires within hours or names something on one phone. Their
 * sounds are fetched again from the source id, so the address is left out of a backup.
 */
private val CONTACT_RINGTONE_SOURCES_WITHOUT_URL = setOf(
    ContentSource.LOCAL.name,
    ContentSource.BUNDLED.name,
    ContentSource.YOUTUBE.name,
    ContentSource.TIKTOK.name,
)

/** A bare file name, or blank when the value carries a folder, a scheme, or a control character. */
internal fun normalizeImportedFileName(value: String?): String {
    val trimmed = value.orEmpty().trim()
    if (trimmed.isEmpty() || trimmed.length > IMPORT_MAX_FILE_NAME_LENGTH) return ""
    if (trimmed == "." || trimmed == "..") return ""
    if (trimmed.any { it == '/' || it == '\\' || it == ':' || it.isISOControl() }) return ""
    return trimmed
}

/**
 * The id a sound has at its source, or blank when the value is really an address on one phone.
 *
 * A sound picked from the phone's own storage is known to Aura only by a media row, which another
 * phone can't use, so it never gets an id. It is matched by title and file name instead.
 */
internal fun portableContactRingtoneSoundId(source: String, id: String?): String {
    val trimmed = id.orEmpty().trim()
    if (source.isBlank() || source == ContentSource.LOCAL.name) return ""
    if (trimmed.isEmpty() || trimmed.length > IMPORT_MAX_TEXT_LENGTH) return ""
    if (trimmed.any { it.isWhitespace() || it.isISOControl() }) return ""
    if (trimmed.startsWith('/') || trimmed.startsWith('\\')) return ""
    val hasScheme = trimmed.contains("://") || trimmed.startsWith("content:", ignoreCase = true) ||
        trimmed.startsWith("file:", ignoreCase = true)
    if (hasScheme && !isAllowedImportedHttpsUrl(trimmed)) return ""
    if (trimmed.length > 2 && trimmed[0].isLetter() && trimmed[1] == ':' && (trimmed[2] == '/' || trimmed[2] == '\\')) {
        return ""
    }
    return trimmed
}

/**
 * One contact ringtone row with every field checked, or null when it names no contact or no sound.
 *
 * The same check runs when Aura writes a row and when it reads one back, so a backup can't carry a
 * content address or a row id in, and can't carry one out.
 */
internal fun normalizeImportedContactRingtone(entry: ContactRingtoneExportEntry): ContactRingtoneExportEntry? {
    val lookupKey = entry.contactLookupKey.trim()
        .takeIf { it.length <= LibraryTransferContract.MAX_URL_CHARS && it.none(Char::isISOControl) }
        .orEmpty()
    val displayName = normalizeImportedText(entry.contactDisplayName)
    if (lookupKey.isBlank() && displayName.isBlank()) return null

    val source = normalizeImportedContentSource(entry.soundSource).orEmpty()
    val soundId = portableContactRingtoneSoundId(source, entry.soundId)
    val title = normalizeImportedText(entry.soundTitle)
    val fileName = normalizeImportedFileName(entry.soundFileName)
    if (soundId.isBlank() && title.isBlank() && fileName.isBlank()) return null

    val keepsUrl = soundId.isNotBlank() && source !in CONTACT_RINGTONE_SOURCES_WITHOUT_URL
    return ContactRingtoneExportEntry(
        contactLookupKey = lookupKey,
        contactDisplayName = displayName,
        soundSource = source,
        soundId = soundId,
        soundTitle = title,
        soundFileName = fileName,
        soundUrl = if (keepsUrl) normalizeImportedHttpsUrl(entry.soundUrl, allowBlank = true).orEmpty() else "",
        soundPageUrl = normalizeImportedHttpsUrl(entry.soundPageUrl, allowBlank = true).orEmpty(),
        soundLicense = normalizeImportedText(entry.soundLicense, 128),
        soundUploader = normalizeImportedText(entry.soundUploader, 256),
        assignedAt = entry.assignedAt.coerceAtLeast(0),
    )
}
