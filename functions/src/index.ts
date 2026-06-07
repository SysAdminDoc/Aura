import { getApps, initializeApp } from "firebase-admin/app";

import { createFailClosedCommunityCallable } from "./callableScaffold";
import { surfaceByFunctionName } from "./communityContract";
import { createSetCreatorFollowCallable } from "./followHandler";
import { createSubmitCommunityReportCallable } from "./reportHandler";
import { createRecordCommunityVoteCallable } from "./voteHandler";

if (getApps().length === 0) {
  initializeApp();
}

export const submitCommunityReport = createSubmitCommunityReportCallable();
export const finalizeCommunitySoundUpload = createFailClosedCommunityCallable(
  surfaceByFunctionName("finalizeCommunitySoundUpload"),
);
export const finalizeCommunityWallpaperUpload = createFailClosedCommunityCallable(
  surfaceByFunctionName("finalizeCommunityWallpaperUpload"),
);
export const recordCommunityVote = createRecordCommunityVoteCallable();
export const setCreatorFollow = createSetCreatorFollowCallable();
export const setCommunityUserBlock = createFailClosedCommunityCallable(
  surfaceByFunctionName("setCommunityUserBlock"),
);
export const updateCreatorProfile = createFailClosedCommunityCallable(
  surfaceByFunctionName("updateCreatorProfile"),
);
