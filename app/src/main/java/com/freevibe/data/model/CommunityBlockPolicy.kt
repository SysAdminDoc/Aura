package com.freevibe.data.model

enum class CommunityBlockReason(val storageValue: String) {
    SPAM("SPAM"),
    HARASSMENT("HARASSMENT"),
    SAFETY("SAFETY"),
    RIGHTS("RIGHTS"),
    OTHER("OTHER"),
}

fun communityUserBlockPath(blockerUid: String, blockedUid: String): String =
    "/community_user_blocks/${sanitizeCommunityOwnerKey(blockerUid)}/${sanitizeCommunityOwnerKey(blockedUid)}"

fun communityBlockedByPath(blockerUid: String, blockedUid: String): String =
    "/community_blocked_by/${sanitizeCommunityOwnerKey(blockedUid)}/${sanitizeCommunityOwnerKey(blockerUid)}"

fun buildCommunityUserBlockUpdates(
    blockerUid: String,
    blockedUid: String,
    createdAt: Long,
    reason: CommunityBlockReason,
): Map<String, Any> {
    val safeBlocker = sanitizeCommunityOwnerKey(blockerUid)
    require(safeBlocker.isNotBlank()) { "Blocker UID is required" }
    val safeBlocked = sanitizeCommunityOwnerKey(blockedUid)
    require(safeBlocked.isNotBlank()) { "Blocked UID is required" }
    require(safeBlocker != safeBlocked) { "A user cannot block themselves" }
    require(createdAt > 0L) { "Block timestamp is required" }

    val payload = mapOf(
        "blockerUid" to safeBlocker,
        "blockedUid" to safeBlocked,
        "createdAt" to createdAt,
        "reason" to reason.storageValue,
    )

    return mapOf(
        communityUserBlockPath(safeBlocker, safeBlocked) to payload,
        communityBlockedByPath(safeBlocker, safeBlocked) to payload,
    )
}

fun buildCommunityUserUnblockUpdates(
    blockerUid: String,
    blockedUid: String,
): Map<String, Any?> {
    val safeBlocker = sanitizeCommunityOwnerKey(blockerUid)
    require(safeBlocker.isNotBlank()) { "Blocker UID is required" }
    val safeBlocked = sanitizeCommunityOwnerKey(blockedUid)
    require(safeBlocked.isNotBlank()) { "Blocked UID is required" }
    require(safeBlocker != safeBlocked) { "A user cannot unblock themselves" }

    return mapOf(
        communityUserBlockPath(safeBlocker, safeBlocked) to null,
        communityBlockedByPath(safeBlocker, safeBlocked) to null,
    )
}

fun normalizeCommunityBlockedUserIds(blockedUserIds: Iterable<String>): Set<String> =
    blockedUserIds
        .map(::sanitizeCommunityOwnerKey)
        .filter(String::isNotBlank)
        .toSet()

fun isCommunityUserBlocked(
    uploaderUid: String?,
    uploaderId: String?,
    blockedUserIds: Set<String>,
): Boolean {
    if (blockedUserIds.isEmpty()) return false
    val normalizedBlocked = normalizeCommunityBlockedUserIds(blockedUserIds)
    return listOf(uploaderUid, uploaderId)
        .mapNotNull { it?.let(::sanitizeCommunityOwnerKey) }
        .any { it in normalizedBlocked }
}
