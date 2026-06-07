export type QuotaDayBoundary = "UTC";

export interface CommunityCallableDefinition {
  readonly functionName: string;
  readonly payloadSchema: string;
  readonly finalWritePaths: readonly string[];
  readonly consumeLimitedUseAppCheckToken: boolean;
  readonly requiresAuth: boolean;
  readonly requiresAppCheck: boolean;
}

export interface CommunityCallableSurface {
  readonly surfaceKey: string;
  readonly dailyLimit: number;
  readonly minIntervalMillis: number;
  readonly dedupeKey: string;
  readonly enforcement: readonly string[];
  readonly quotaLedgerPath: string;
  readonly dedupeLedgerPath: string;
  readonly callable: CommunityCallableDefinition;
}

export interface CommunityCallableRuntimeOptions {
  readonly enforceAppCheck: boolean;
  readonly consumeAppCheckToken: boolean;
}

const SECOND_MILLIS = 1_000;
const MINUTE_MILLIS = 60 * SECOND_MILLIS;

export const QUOTA_DAY_BOUNDARY: QuotaDayBoundary = "UTC";

export const COMMUNITY_CALLABLE_SURFACES: readonly CommunityCallableSurface[] = [
  {
    surfaceKey: "reports",
    dailyLimit: 10,
    minIntervalMillis: 2 * MINUTE_MILLIS,
    dedupeKey: "content key + reason",
    enforcement: ["APP_CHECKED_CALLABLE"],
    quotaLedgerPath: "/community_write_quotas/{uid}/{yyyyMMdd}/reports",
    dedupeLedgerPath: "/community_write_dedupe/{uid}/reports/{dedupeKey}",
    callable: {
      functionName: "submitCommunityReport",
      payloadSchema: "CommunityReportInput",
      finalWritePaths: ["/community_reports/{reportId}"],
      consumeLimitedUseAppCheckToken: true,
      requiresAuth: true,
      requiresAppCheck: true,
    },
  },
  {
    surfaceKey: "sound_uploads",
    dailyLimit: 3,
    minIntervalMillis: 15 * MINUTE_MILLIS,
    dedupeKey: "storagePath",
    enforcement: ["APP_CHECKED_CALLABLE", "STORAGE_RULES"],
    quotaLedgerPath: "/community_write_quotas/{uid}/{yyyyMMdd}/sound_uploads",
    dedupeLedgerPath: "/community_write_dedupe/{uid}/sound_uploads/{dedupeKey}",
    callable: {
      functionName: "finalizeCommunitySoundUpload",
      payloadSchema: "CommunitySoundUploadMetadata",
      finalWritePaths: [
        "/community_sounds/{uploadId}",
        "/owner_uploads/{uid}/sounds/{uploadId}",
      ],
      consumeLimitedUseAppCheckToken: true,
      requiresAuth: true,
      requiresAppCheck: true,
    },
  },
  {
    surfaceKey: "wallpaper_uploads",
    dailyLimit: 5,
    minIntervalMillis: 10 * MINUTE_MILLIS,
    dedupeKey: "storagePath",
    enforcement: ["APP_CHECKED_CALLABLE", "STORAGE_RULES"],
    quotaLedgerPath: "/community_write_quotas/{uid}/{yyyyMMdd}/wallpaper_uploads",
    dedupeLedgerPath: "/community_write_dedupe/{uid}/wallpaper_uploads/{dedupeKey}",
    callable: {
      functionName: "finalizeCommunityWallpaperUpload",
      payloadSchema: "CommunityWallpaperUploadMetadata",
      finalWritePaths: [
        "/community_wallpapers/{uploadId}",
        "/owner_uploads/{uid}/wallpapers/{uploadId}",
      ],
      consumeLimitedUseAppCheckToken: true,
      requiresAuth: true,
      requiresAppCheck: true,
    },
  },
  {
    surfaceKey: "votes",
    dailyLimit: 100,
    minIntervalMillis: 3 * SECOND_MILLIS,
    dedupeKey: "contentId",
    enforcement: ["APP_CHECKED_CALLABLE", "RTDB_TRANSACTION"],
    quotaLedgerPath: "/community_write_quotas/{uid}/{yyyyMMdd}/votes",
    dedupeLedgerPath: "/community_write_dedupe/{uid}/votes/{dedupeKey}",
    callable: {
      functionName: "recordCommunityVote",
      payloadSchema: "CommunityVoteInput",
      finalWritePaths: [
        "/votes/{contentId}",
        "/voters/{contentId}/{uid}",
      ],
      consumeLimitedUseAppCheckToken: false,
      requiresAuth: true,
      requiresAppCheck: true,
    },
  },
  {
    surfaceKey: "follows",
    dailyLimit: 50,
    minIntervalMillis: 5 * SECOND_MILLIS,
    dedupeKey: "creatorId + desired state",
    enforcement: ["APP_CHECKED_CALLABLE"],
    quotaLedgerPath: "/community_write_quotas/{uid}/{yyyyMMdd}/follows",
    dedupeLedgerPath: "/community_write_dedupe/{uid}/follows/{dedupeKey}",
    callable: {
      functionName: "setCreatorFollow",
      payloadSchema: "CommunityFollowInput",
      finalWritePaths: ["/creator_follows/{uid}/{creatorId}"],
      consumeLimitedUseAppCheckToken: false,
      requiresAuth: true,
      requiresAppCheck: true,
    },
  },
  {
    surfaceKey: "user_blocks",
    dailyLimit: 100,
    minIntervalMillis: SECOND_MILLIS,
    dedupeKey: "blockedUid + desired state",
    enforcement: ["APP_CHECKED_CALLABLE"],
    quotaLedgerPath: "/community_write_quotas/{uid}/{yyyyMMdd}/user_blocks",
    dedupeLedgerPath: "/community_write_dedupe/{uid}/user_blocks/{dedupeKey}",
    callable: {
      functionName: "setCommunityUserBlock",
      payloadSchema: "CommunityUserBlockInput",
      finalWritePaths: [
        "/community_user_blocks/{uid}/{blockedUid}",
        "/community_blocked_by/{blockedUid}/{uid}",
      ],
      consumeLimitedUseAppCheckToken: false,
      requiresAuth: true,
      requiresAppCheck: true,
    },
  },
  {
    surfaceKey: "profile_edits",
    dailyLimit: 12,
    minIntervalMillis: 5 * MINUTE_MILLIS,
    dedupeKey: "profileUid",
    enforcement: ["APP_CHECKED_CALLABLE"],
    quotaLedgerPath: "/community_write_quotas/{uid}/{yyyyMMdd}/profile_edits",
    dedupeLedgerPath: "/community_write_dedupe/{uid}/profile_edits/{dedupeKey}",
    callable: {
      functionName: "updateCreatorProfile",
      payloadSchema: "CreatorProfileUpdateInput",
      finalWritePaths: ["/creator_profiles/{uid}"],
      consumeLimitedUseAppCheckToken: false,
      requiresAuth: true,
      requiresAppCheck: true,
    },
  },
] as const;

export function surfaceByKey(surfaceKey: string): CommunityCallableSurface {
  const surface = COMMUNITY_CALLABLE_SURFACES.find((candidate) => candidate.surfaceKey === surfaceKey);
  if (surface === undefined) {
    throw new Error(`Unknown community callable surface: ${surfaceKey}`);
  }
  return surface;
}

export function surfaceByFunctionName(functionName: string): CommunityCallableSurface {
  const surface = COMMUNITY_CALLABLE_SURFACES.find(
    (candidate) => candidate.callable.functionName === functionName,
  );
  if (surface === undefined) {
    throw new Error(`Unknown community callable function: ${functionName}`);
  }
  return surface;
}

export function callableRuntimeOptionsFor(
  surface: CommunityCallableSurface,
): CommunityCallableRuntimeOptions {
  return {
    enforceAppCheck: surface.callable.requiresAppCheck,
    consumeAppCheckToken: surface.callable.consumeLimitedUseAppCheckToken,
  };
}

export function callableExportNames(): readonly string[] {
  return COMMUNITY_CALLABLE_SURFACES.map((surface) => surface.callable.functionName);
}
