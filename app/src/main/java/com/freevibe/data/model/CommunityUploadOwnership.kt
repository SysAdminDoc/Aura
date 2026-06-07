package com.freevibe.data.model

private val COMMUNITY_UPLOAD_KEY_REGEX = Regex("[.#$\\[\\]/]")
private val COMMUNITY_UPLOAD_WHITESPACE_REGEX = Regex("\\s+")
private const val MAX_COMMUNITY_UPLOAD_KEY_LENGTH = 240
private const val MAX_COMMUNITY_UPLOAD_TITLE_LENGTH = 80

enum class CommunityUploadKind(
    val contentType: String,
    val metadataRoot: String,
    val ownerRoot: String,
    val publicIdPrefix: String,
) {
    SOUND("SOUND", "community_sounds", "sounds", "cu_"),
    WALLPAPER("WALLPAPER", "community_wallpapers", "wallpapers", "cw_"),
}

enum class CommunityUploadDeleteReason(val storageValue: String) {
    OWNER_DELETE("OWNER_DELETE"),
    ADMIN_TAKEDOWN("ADMIN_TAKEDOWN"),
}

fun sanitizeCommunityUploadKey(value: String): String =
    value
        .removePrefix("cu_")
        .removePrefix("cw_")
        .trim()
        .replace(COMMUNITY_UPLOAD_KEY_REGEX, "_")
        .take(MAX_COMMUNITY_UPLOAD_KEY_LENGTH)

fun sanitizeCommunityOwnerKey(value: String): String =
    value
        .trim()
        .replace(COMMUNITY_UPLOAD_KEY_REGEX, "_")
        .take(MAX_COMMUNITY_UPLOAD_KEY_LENGTH)

fun communityUploadMetadataPath(kind: CommunityUploadKind, uploadId: String): String =
    "/${kind.metadataRoot}/${sanitizeCommunityUploadKey(uploadId)}"

fun communityOwnerUploadIndexPath(kind: CommunityUploadKind, ownerUid: String, uploadId: String): String =
    "/owner_uploads/${sanitizeCommunityOwnerKey(ownerUid)}/${kind.ownerRoot}/${sanitizeCommunityUploadKey(uploadId)}"

fun communityUploadDeletionPath(kind: CommunityUploadKind, uploadId: String): String =
    "/community_upload_deletions/${kind.publicIdPrefix}${sanitizeCommunityUploadKey(uploadId)}"

fun buildCommunityOwnerUploadIndexPayload(
    kind: CommunityUploadKind,
    uploadId: String,
    storagePath: String,
    title: String,
    createdAt: Long,
): Map<String, Any> {
    val safeUploadId = sanitizeCommunityUploadKey(uploadId)
    require(safeUploadId.isNotBlank()) { "Upload ID is required" }
    val normalizedStoragePath = storagePath.trim()
    require(normalizedStoragePath.startsWith("${kind.ownerRoot}/")) { "Storage path does not match upload type" }
    require(createdAt > 0L) { "Upload timestamp is required" }

    return mapOf(
        "uploadId" to safeUploadId,
        "publicId" to "${kind.publicIdPrefix}$safeUploadId",
        "contentType" to kind.contentType,
        "metadataPath" to communityUploadMetadataPath(kind, safeUploadId),
        "storagePath" to normalizedStoragePath,
        "title" to normalizeCommunityUploadTitle(title),
        "createdAt" to createdAt,
    )
}

fun buildCommunityUploadDeleteUpdates(
    kind: CommunityUploadKind,
    ownerUid: String,
    uploadId: String,
    storagePath: String,
    deletedByUid: String,
    deletedAt: Long,
    reason: CommunityUploadDeleteReason,
): Map<String, Any?> {
    val safeOwner = sanitizeCommunityOwnerKey(ownerUid)
    require(safeOwner.isNotBlank()) { "Owner UID is required" }
    val safeDeletedBy = sanitizeCommunityOwnerKey(deletedByUid)
    require(safeDeletedBy.isNotBlank()) { "Deleted-by UID is required" }
    val safeUploadId = sanitizeCommunityUploadKey(uploadId)
    require(safeUploadId.isNotBlank()) { "Upload ID is required" }
    val normalizedStoragePath = storagePath.trim()
    require(normalizedStoragePath.startsWith("${kind.ownerRoot}/$safeOwner/")) {
        "Storage path does not match upload owner"
    }
    require(deletedAt > 0L) { "Deletion timestamp is required" }

    return mapOf(
        communityUploadMetadataPath(kind, safeUploadId) to null,
        communityOwnerUploadIndexPath(kind, safeOwner, safeUploadId) to null,
        communityUploadDeletionPath(kind, safeUploadId) to mapOf(
            "publicId" to "${kind.publicIdPrefix}$safeUploadId",
            "uploadId" to safeUploadId,
            "contentType" to kind.contentType,
            "metadataPath" to communityUploadMetadataPath(kind, safeUploadId),
            "storagePath" to normalizedStoragePath,
            "uploaderUid" to safeOwner,
            "deletedByUid" to safeDeletedBy,
            "deletedAt" to deletedAt,
            "reason" to reason.storageValue,
        ),
    )
}

private fun normalizeCommunityUploadTitle(value: String): String =
    value
        .replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
        .replace(COMMUNITY_UPLOAD_WHITESPACE_REGEX, " ")
        .trim()
        .take(MAX_COMMUNITY_UPLOAD_TITLE_LENGTH)
        .ifBlank { "Community upload" }
