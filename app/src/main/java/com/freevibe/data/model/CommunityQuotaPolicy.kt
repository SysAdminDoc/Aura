package com.freevibe.data.model

private const val SECOND_MILLIS = 1_000L
private const val MINUTE_MILLIS = 60 * SECOND_MILLIS

enum class CommunityQuotaEnforcement {
    APP_CHECKED_CALLABLE,
    RTDB_TRANSACTION,
    STORAGE_RULES,
}

data class CommunityQuotaPolicy(
    val surfaceKey: String,
    val dailyLimit: Int,
    val minIntervalMillis: Long,
    val dedupeKey: String,
    val enforcement: Set<CommunityQuotaEnforcement>,
) {
    val quotaLedgerPath: String
        get() = "/community_write_quotas/{uid}/{yyyyMMdd}/$surfaceKey"

    val dedupeLedgerPath: String
        get() = "/community_write_dedupe/{uid}/$surfaceKey/{dedupeKey}"

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
    )

    val follows = CommunityQuotaPolicy(
        surfaceKey = "follows",
        dailyLimit = 50,
        minIntervalMillis = 5 * SECOND_MILLIS,
        dedupeKey = "creatorId",
        enforcement = setOf(CommunityQuotaEnforcement.APP_CHECKED_CALLABLE),
    )

    val profileEdits = CommunityQuotaPolicy(
        surfaceKey = "profile_edits",
        dailyLimit = 12,
        minIntervalMillis = 5 * MINUTE_MILLIS,
        dedupeKey = "profileUid",
        enforcement = setOf(CommunityQuotaEnforcement.APP_CHECKED_CALLABLE),
    )

    val all: List<CommunityQuotaPolicy> = listOf(
        reports,
        soundUploads,
        wallpaperUploads,
        votes,
        follows,
        profileEdits,
    )
}
