package com.freevibe.data.model

import java.util.Locale

private const val MAX_COMMUNITY_SOUND_UPLOAD_NAME = 80
private const val MAX_COMMUNITY_SOUND_UPLOAD_FILE_NAME = 240
private const val MAX_COMMUNITY_SOUND_UPLOAD_SHORT_TEXT = 120
private const val MAX_COMMUNITY_SOUND_UPLOAD_URL = 2048
private const val MAX_COMMUNITY_SOUND_UPLOAD_STORAGE_PATH = 512
private const val MAX_COMMUNITY_SOUND_UPLOAD_TAGS = 8
private const val MAX_COMMUNITY_SOUND_UPLOAD_TAG_LENGTH = 24
private val COMMUNITY_SOUND_UPLOAD_CONTROL_REGEX = Regex("[\\u0000-\\u001F\\u007F]")
private val COMMUNITY_SOUND_UPLOAD_WHITESPACE_REGEX = Regex("\\s+")
private val COMMUNITY_SOUND_UPLOAD_TAG_REGEX = Regex("[^a-z0-9_\\- ]")
private val COMMUNITY_SOUND_UPLOAD_CATEGORIES = setOf("ringtone", "notification", "alarm")
private val COMMUNITY_SOUND_UPLOAD_LICENSES = mapOf(
    "CC0" to "CC0",
    "CC BY" to "CC BY",
    "CC-BY" to "CC BY",
    "CC BY-NC" to "CC BY-NC",
    "CC-BY-NC" to "CC BY-NC",
)

data class CommunitySoundUploadMetadataInput(
    val name: String,
    val category: String,
    val tags: List<String>,
    val downloadUrl: String,
    val storagePath: String,
    val fileType: String,
    val originalFileName: String,
    val uploaderLabel: String,
    val license: String,
    val rightsAttested: Boolean,
    val sourceUrl: String = "",
)

fun buildCommunitySoundUploadCallablePayload(input: CommunitySoundUploadMetadataInput): Map<String, Any> {
    val normalizedName = normalizeCommunitySoundUploadText(input.name, MAX_COMMUNITY_SOUND_UPLOAD_NAME)
    require(normalizedName.isNotBlank()) { "Sound name is required" }
    val normalizedCategory = normalizeCommunitySoundUploadText(input.category, 40).lowercase(Locale.ROOT)
    require(normalizedCategory in COMMUNITY_SOUND_UPLOAD_CATEGORIES) { "Invalid sound category" }
    val normalizedStoragePath = input.storagePath.trim().take(MAX_COMMUNITY_SOUND_UPLOAD_STORAGE_PATH)
    require(normalizedStoragePath.isNotBlank()) { "Storage path is required" }
    val normalizedDownloadUrl = input.downloadUrl.trim().take(MAX_COMMUNITY_SOUND_UPLOAD_URL)
    require(normalizedDownloadUrl.startsWith("https://", ignoreCase = true)) { "Download URL must use HTTPS" }
    val normalizedFileType = normalizeCommunitySoundUploadText(input.fileType, MAX_COMMUNITY_SOUND_UPLOAD_SHORT_TEXT)
        .lowercase(Locale.ROOT)
    require(normalizedFileType.isNotBlank()) { "File type is required" }
    val normalizedLicense = normalizeCommunitySoundUploadLicense(input.license)
    require(input.rightsAttested) { "Sound upload rights must be confirmed" }

    return mapOf(
        "name" to normalizedName,
        "category" to normalizedCategory,
        "tags" to normalizeCommunitySoundUploadTags(input.tags),
        "downloadUrl" to normalizedDownloadUrl,
        "storagePath" to normalizedStoragePath,
        "fileType" to normalizedFileType,
        "originalFileName" to normalizeCommunitySoundUploadText(
            input.originalFileName,
            MAX_COMMUNITY_SOUND_UPLOAD_FILE_NAME,
        ).ifBlank { "community-sound" },
        "uploaderLabel" to normalizeCommunitySoundUploadText(
            input.uploaderLabel,
            MAX_COMMUNITY_SOUND_UPLOAD_SHORT_TEXT,
        ),
        "license" to normalizedLicense,
        "rightsAttested" to true,
        "sourceUrl" to normalizeCommunitySourceUrl(input.sourceUrl),
    )
}

private fun normalizeCommunitySoundUploadText(value: String, maxLength: Int): String =
    value
        .replace(COMMUNITY_SOUND_UPLOAD_CONTROL_REGEX, " ")
        .replace(COMMUNITY_SOUND_UPLOAD_WHITESPACE_REGEX, " ")
        .trim()
        .take(maxLength)

private fun normalizeCommunitySoundUploadTags(tags: List<String>): List<String> =
    tags.asSequence()
        .map { tag ->
            normalizeCommunitySoundUploadText(tag, MAX_COMMUNITY_SOUND_UPLOAD_TAG_LENGTH)
                .lowercase(Locale.ROOT)
                .replace(COMMUNITY_SOUND_UPLOAD_TAG_REGEX, "")
                .trim()
        }
        .filter { it.length in 2..MAX_COMMUNITY_SOUND_UPLOAD_TAG_LENGTH }
        .distinct()
        .take(MAX_COMMUNITY_SOUND_UPLOAD_TAGS)
        .toList()

private fun normalizeCommunitySoundUploadLicense(license: String): String {
    val normalized = normalizeCommunitySoundUploadText(license, MAX_COMMUNITY_SOUND_UPLOAD_SHORT_TEXT)
        .uppercase(Locale.ROOT)
    return COMMUNITY_SOUND_UPLOAD_LICENSES[normalized]
        ?: throw IllegalArgumentException("Unsupported sound upload license")
}
