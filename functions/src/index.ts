import { getApps, initializeApp } from "firebase-admin/app";

import { createSetCommunityUserBlockCallable } from "./blockHandler";
import { createFailClosedCommunityCallable } from "./callableScaffold";
import { surfaceByFunctionName } from "./communityContract";
import { createSetCreatorFollowCallable } from "./followHandler";
import { createSubmitCommunityReportCallable } from "./reportHandler";
import { createFinalizeCommunitySoundUploadCallable } from "./soundUploadHandler";
import { createRecordCommunityVoteCallable } from "./voteHandler";
import { createFinalizeCommunityWallpaperUploadCallable } from "./wallpaperUploadHandler";

if (getApps().length === 0) {
  initializeApp();
}

export const submitCommunityReport = createSubmitCommunityReportCallable();
export const finalizeCommunitySoundUpload = createFinalizeCommunitySoundUploadCallable();
export const finalizeCommunityWallpaperUpload = createFinalizeCommunityWallpaperUploadCallable();
export const recordCommunityVote = createRecordCommunityVoteCallable();
export const setCreatorFollow = createSetCreatorFollowCallable();
export const setCommunityUserBlock = createSetCommunityUserBlockCallable();
export const updateCreatorProfile = createFailClosedCommunityCallable(
  surfaceByFunctionName("updateCreatorProfile"),
);
