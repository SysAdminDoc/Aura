package com.freevibe.data.model

import java.net.URI
import java.util.Locale

enum class CommunityReportReason(val storageValue: String, val label: String) {
    RIGHTS("RIGHTS", "Rights or license"),
    SOURCE_REMOVED("SOURCE_REMOVED", "Source removed"),
    SAFETY("SAFETY", "Safety issue"),
    SPAM("SPAM", "Spam"),
    OTHER("OTHER", "Other"),
}

enum class CommunityReportResolutionStatus(val storageValue: String) {
    OPEN("OPEN"),
    HIDDEN("HIDDEN"),
    DISMISSED("DISMISSED"),
    RESTORED("RESTORED"),
}

enum class CommunityTakedownAction(val storageValue: String) {
    HIDE("HIDE"),
    DELETE("DELETE"),
}

data class CommunityReportRecord(
    val id: String,
    val contentId: String,
    val contentKey: String,
    val contentType: String,
    val contentSource: String,
    val reason: CommunityReportReason,
    val note: String,
    val sourceUrl: String,
    val license: String,
    val uploaderName: String,
    val uploaderUid: String = "",
    val reporterUid: String,
    val reportedAt: Long,
    val status: CommunityReportResolutionStatus,
    val resolverUid: String = "",
    val resolvedAt: Long = 0L,
)

data class CommunityReportInput(
    val contentId: String,
    val contentType: String,
    val contentSource: ContentSource,
    val reason: CommunityReportReason,
    val note: String = "",
    val sourceUrl: String = "",
    val license: String = "",
    val uploaderName: String = "",
    val uploaderUid: String = "",
)

private val REPORT_KEY_REGEX = Regex("[.#$\\[\\]/]")
private val REPORT_WHITESPACE_REGEX = Regex("\\s+")
private val REPORT_CONTENT_TYPE_REGEX = Regex("^[A-Z_]{3,40}$")
private const val MAX_REPORT_CONTENT_ID = 240
private const val MAX_REPORT_NOTE = 500
private const val MAX_REPORT_SOURCE_URL = 2048
private const val MAX_REPORT_SHORT_TEXT = 120

fun sanitizeCommunityReportKey(value: String): String =
    value.trim().replace(REPORT_KEY_REGEX, "_").take(MAX_REPORT_CONTENT_ID)

fun communityReportReasonFromStorage(value: String?): CommunityReportReason =
    CommunityReportReason.entries.firstOrNull { it.storageValue == value } ?: CommunityReportReason.OTHER

fun communityReportStatusFromStorage(value: String?): CommunityReportResolutionStatus =
    CommunityReportResolutionStatus.entries.firstOrNull { it.storageValue == value }
        ?: CommunityReportResolutionStatus.OPEN

fun normalizeCommunityReportText(value: String, maxLength: Int = MAX_REPORT_NOTE): String =
    value
        .replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
        .replace(REPORT_WHITESPACE_REGEX, " ")
        .trim()
        .take(maxLength)

fun normalizeCommunityReportSourceUrl(value: String): String {
    val normalized = normalizeCommunityReportText(value, MAX_REPORT_SOURCE_URL)
    if (normalized.isBlank()) return ""
    val uri = runCatching { URI(normalized) }.getOrNull()
    require(uri?.scheme?.lowercase(Locale.ROOT) == "https") { "Report source URL must use HTTPS" }
    require(!uri.host.isNullOrBlank()) { "Report source URL must include a host" }
    return normalized
}

fun buildCommunityReportPayload(
    input: CommunityReportInput,
    reporterUid: String,
    reportedAt: Long,
): Map<String, Any> {
    val contentId = normalizeCommunityReportText(input.contentId, MAX_REPORT_CONTENT_ID)
    require(contentId.isNotBlank()) { "Report content ID is required" }
    val contentType = input.contentType.trim().uppercase(Locale.ROOT)
    require(REPORT_CONTENT_TYPE_REGEX.matches(contentType)) { "Report content type is invalid" }
    val reporter = reporterUid.trim()
    require(reporter.isNotBlank()) { "Reporter UID is required" }
    require(reportedAt > 0L) { "Report timestamp is required" }

    val uploaderUid = normalizeCommunityReportText(input.uploaderUid, MAX_REPORT_CONTENT_ID)
    return mutableMapOf<String, Any>(
        "contentId" to contentId,
        "contentKey" to sanitizeCommunityReportKey(contentId),
        "contentType" to contentType,
        "contentSource" to input.contentSource.name,
        "reason" to input.reason.storageValue,
        "note" to normalizeCommunityReportText(input.note),
        "sourceUrl" to normalizeCommunityReportSourceUrl(input.sourceUrl),
        "license" to normalizeCommunityReportText(input.license, MAX_REPORT_SHORT_TEXT),
        "uploaderName" to normalizeCommunityReportText(input.uploaderName, MAX_REPORT_SHORT_TEXT),
        "reporterUid" to reporter,
        "reportedAt" to reportedAt,
        "status" to CommunityReportResolutionStatus.OPEN.storageValue,
    ).also { payload ->
        if (uploaderUid.isNotBlank()) {
            payload["uploaderUid"] = uploaderUid
        }
    }
}

fun buildCommunityReportResolutionPayload(
    reportId: String,
    status: CommunityReportResolutionStatus,
    resolverUid: String,
    resolvedAt: Long,
    note: String = "",
): Map<String, Any> {
    val normalizedReportId = sanitizeCommunityReportKey(reportId)
    require(normalizedReportId.isNotBlank()) { "Report ID is required" }
    val resolver = resolverUid.trim()
    require(resolver.isNotBlank()) { "Resolver UID is required" }
    require(resolvedAt > 0L) { "Resolution timestamp is required" }
    require(status != CommunityReportResolutionStatus.OPEN) { "Resolution status must close or restore the report" }

    return mapOf(
        "reportId" to normalizedReportId,
        "status" to status.storageValue,
        "resolverUid" to resolver,
        "resolvedAt" to resolvedAt,
        "note" to normalizeCommunityReportText(note),
    )
}

fun communityTakedownUploadKind(
    contentType: String,
    contentSource: String,
): CommunityUploadKind? {
    if (!contentSource.trim().equals(ContentSource.COMMUNITY.name, ignoreCase = true)) return null
    return when (contentType.trim().uppercase(Locale.ROOT)) {
        CommunityUploadKind.SOUND.contentType -> CommunityUploadKind.SOUND
        CommunityUploadKind.WALLPAPER.contentType -> CommunityUploadKind.WALLPAPER
        else -> null
    }
}

fun communityTakedownUploadIdFromContentId(
    contentId: String,
    kind: CommunityUploadKind,
): String {
    val candidate = contentId.trim().substringAfterLast("::")
    val hasCommunityPrefix = candidate.startsWith(CommunityUploadKind.SOUND.publicIdPrefix) ||
        candidate.startsWith(CommunityUploadKind.WALLPAPER.publicIdPrefix)
    if (hasCommunityPrefix && !candidate.startsWith(kind.publicIdPrefix)) return ""
    return sanitizeCommunityUploadKey(candidate)
}

fun buildCommunityTakedownReceiptPayload(
    reportId: String,
    contentId: String,
    contentType: String,
    contentSource: String,
    reason: CommunityReportReason,
    action: CommunityTakedownAction,
    status: CommunityReportResolutionStatus,
    uploadId: String,
    metadataPath: String,
    storagePath: String,
    uploaderUid: String,
    resolverUid: String,
    resolvedAt: Long,
    note: String = "",
): Map<String, Any> {
    val normalizedReportId = sanitizeCommunityReportKey(reportId)
    require(normalizedReportId.isNotBlank()) { "Report ID is required" }
    val kind = communityTakedownUploadKind(contentType, contentSource)
        ?: throw IllegalArgumentException("Takedown receipt requires a community upload")
    require(reason == CommunityReportReason.RIGHTS) { "Takedown receipt requires a rights report" }
    require(status == CommunityReportResolutionStatus.HIDDEN) { "Takedown receipt requires a hidden report" }

    val normalizedContentId = normalizeCommunityReportText(contentId, MAX_REPORT_CONTENT_ID)
    require(normalizedContentId.isNotBlank()) { "Receipt content ID is required" }
    val safeUploadId = sanitizeCommunityUploadKey(uploadId)
    require(safeUploadId.isNotBlank()) { "Receipt upload ID is required" }
    val expectedMetadataPath = communityUploadMetadataPath(kind, safeUploadId)
    require(metadataPath.trim() == expectedMetadataPath) { "Receipt metadata path does not match upload" }
    val normalizedStoragePath = storagePath.trim()
    require(normalizedStoragePath.startsWith("${kind.ownerRoot}/")) { "Receipt storage path does not match upload type" }
    val uploader = uploaderUid.trim()
    require(uploader.isNotBlank()) { "Receipt uploader UID is required" }
    val resolver = resolverUid.trim()
    require(resolver.isNotBlank()) { "Resolver UID is required" }
    require(resolvedAt > 0L) { "Receipt timestamp is required" }

    return mapOf(
        "reportId" to normalizedReportId,
        "contentId" to normalizedContentId,
        "contentType" to kind.contentType,
        "contentSource" to ContentSource.COMMUNITY.name,
        "reason" to reason.storageValue,
        "action" to action.storageValue,
        "status" to status.storageValue,
        "uploadId" to safeUploadId,
        "metadataPath" to expectedMetadataPath,
        "storagePath" to normalizedStoragePath,
        "uploaderUid" to uploader,
        "resolverUid" to resolver,
        "resolvedAt" to resolvedAt,
        "note" to normalizeCommunityReportText(note),
    )
}
