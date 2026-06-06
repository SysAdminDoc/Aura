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

data class CommunityReportInput(
    val contentId: String,
    val contentType: String,
    val contentSource: ContentSource,
    val reason: CommunityReportReason,
    val note: String = "",
    val sourceUrl: String = "",
    val license: String = "",
    val uploaderName: String = "",
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

    return mapOf(
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
    )
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
