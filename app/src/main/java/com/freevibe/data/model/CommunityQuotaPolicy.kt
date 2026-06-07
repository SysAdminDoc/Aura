package com.freevibe.data.model

private const val SECOND_MILLIS = 1_000L
private const val MINUTE_MILLIS = 60 * SECOND_MILLIS

enum class CommunityQuotaEnforcement {
    APP_CHECKED_CALLABLE,
    RTDB_TRANSACTION,
    STORAGE_RULES,
}

data class CommunityQuotaCallableContract(
    val functionName: String,
    val payloadSchema: String,
    val finalWritePaths: List<String>,
    val consumeLimitedUseAppCheckToken: Boolean,
    val requiresAuth: Boolean = true,
    val requiresAppCheck: Boolean = true,
)

data class CommunityQuotaPolicy(
    val surfaceKey: String,
    val dailyLimit: Int,
    val minIntervalMillis: Long,
    val dedupeKey: String,
    val enforcement: Set<CommunityQuotaEnforcement>,
    val callable: CommunityQuotaCallableContract,
) {
    val quotaLedgerPath: String
        get() = "/community_write_quotas/{uid}/{yyyyMMdd}/$surfaceKey"

    val dedupeLedgerPath: String
        get() = "/community_write_dedupe/{uid}/$surfaceKey/{dedupeKey}"

    val callableWritePaths: List<String>
        get() = callable.finalWritePaths + quotaLedgerPath + dedupeLedgerPath

    val storageBacked: Boolean
        get() = CommunityQuotaEnforcement.STORAGE_RULES in enforcement
}

object CommunityQuotaPolicies {
    val reports = CommunityQuotaPolicy(
        surfaceKey = "reports",
        dailyLimit = 10,
        minIntervalMillis = 2 * MINUTE_MILLIS,
        dedupeKey = "contentKey + reason",
        enforcement = setOf(CommunityQuotaEnforcement.APP_CHECKED_CALLABLE),
        callable = CommunityQuotaCallableContract(
            functionName = "submitCommunityReport",
            payloadSchema = "CommunityReportInput",
            finalWritePaths = listOf("/community_reports/{reportId}"),
            consumeLimitedUseAppCheckToken = true,
        ),
    )

    val soundUploads = CommunityQuotaPolicy(
        surfaceKey = "sound_uploads",
        dailyLimit = 3,
        minIntervalMillis = 15 * MINUTE_MILLIS,
        dedupeKey = "storagePath",
        enforcement = setOf(
            CommunityQuotaEnforcement.APP_CHECKED_CALLABLE,
            CommunityQuotaEnforcement.STORAGE_RULES,
        ),
        callable = CommunityQuotaCallableContract(
            functionName = "finalizeCommunitySoundUpload",
            payloadSchema = "CommunitySoundUploadMetadata",
            finalWritePaths = listOf(
                "/community_sounds/{uploadId}",
                "/owner_uploads/{uid}/sounds/{uploadId}",
            ),
            consumeLimitedUseAppCheckToken = true,
        ),
    )

    val wallpaperUploads = CommunityQuotaPolicy(
        surfaceKey = "wallpaper_uploads",
        dailyLimit = 5,
        minIntervalMillis = 10 * MINUTE_MILLIS,
        dedupeKey = "storagePath",
        enforcement = setOf(
            CommunityQuotaEnforcement.APP_CHECKED_CALLABLE,
            CommunityQuotaEnforcement.STORAGE_RULES,
        ),
        callable = CommunityQuotaCallableContract(
            functionName = "finalizeCommunityWallpaperUpload",
            payloadSchema = "CommunityWallpaperUploadMetadata",
            finalWritePaths = listOf(
                "/community_wallpapers/{uploadId}",
                "/owner_uploads/{uid}/wallpapers/{uploadId}",
            ),
            consumeLimitedUseAppCheckToken = true,
        ),
    )

    val votes = CommunityQuotaPolicy(
        surfaceKey = "votes",
        dailyLimit = 100,
        minIntervalMillis = 3 * SECOND_MILLIS,
        dedupeKey = "contentId",
        enforcement = setOf(
            CommunityQuotaEnforcement.APP_CHECKED_CALLABLE,
            CommunityQuotaEnforcement.RTDB_TRANSACTION,
        ),
        callable = CommunityQuotaCallableContract(
            functionName = "recordCommunityVote",
            payloadSchema = "CommunityVoteInput",
            finalWritePaths = listOf(
                "/votes/{contentId}",
                "/voters/{contentId}/{uid}",
            ),
            consumeLimitedUseAppCheckToken = false,
        ),
    )

    val follows = CommunityQuotaPolicy(
        surfaceKey = "follows",
        dailyLimit = 50,
        minIntervalMillis = 5 * SECOND_MILLIS,
        dedupeKey = "creatorId + desired state",
        enforcement = setOf(CommunityQuotaEnforcement.APP_CHECKED_CALLABLE),
        callable = CommunityQuotaCallableContract(
            functionName = "setCreatorFollow",
            payloadSchema = "CommunityFollowInput",
            finalWritePaths = listOf("/creator_follows/{uid}/{creatorId}"),
            consumeLimitedUseAppCheckToken = false,
        ),
    )

    val userBlocks = CommunityQuotaPolicy(
        surfaceKey = "user_blocks",
        dailyLimit = 100,
        minIntervalMillis = SECOND_MILLIS,
        dedupeKey = "blockedUid",
        enforcement = setOf(CommunityQuotaEnforcement.APP_CHECKED_CALLABLE),
        callable = CommunityQuotaCallableContract(
            functionName = "setCommunityUserBlock",
            payloadSchema = "CommunityUserBlockInput",
            finalWritePaths = listOf(
                "/community_user_blocks/{uid}/{blockedUid}",
                "/community_blocked_by/{blockedUid}/{uid}",
            ),
            consumeLimitedUseAppCheckToken = false,
        ),
    )

    val profileEdits = CommunityQuotaPolicy(
        surfaceKey = "profile_edits",
        dailyLimit = 12,
        minIntervalMillis = 5 * MINUTE_MILLIS,
        dedupeKey = "profileUid",
        enforcement = setOf(CommunityQuotaEnforcement.APP_CHECKED_CALLABLE),
        callable = CommunityQuotaCallableContract(
            functionName = "updateCreatorProfile",
            payloadSchema = "CreatorProfileUpdateInput",
            finalWritePaths = listOf("/creator_profiles/{uid}"),
            consumeLimitedUseAppCheckToken = false,
        ),
    )

    val all: List<CommunityQuotaPolicy> = listOf(
        reports,
        soundUploads,
        wallpaperUploads,
        votes,
        follows,
        userBlocks,
        profileEdits,
    )
}
