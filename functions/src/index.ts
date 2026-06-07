import { getApps, initializeApp } from "firebase-admin/app";

import { createFailClosedCommunityCallable } from "./callableScaffold";
import { surfaceByFunctionName } from "./communityContract";
import { createSubmitCommunityReportCallable } from "./reportHandler";

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
export const recordCommunityVote = createFailClosedCommunityCallable(
  surfaceByFunctionName("recordCommunityVote"),
);
export const setCreatorFollow = createFailClosedCommunityCallable(
  surfaceByFunctionName("setCreatorFollow"),
);
export const setCommunityUserBlock = createFailClosedCommunityCallable(
  surfaceByFunctionName("setCommunityUserBlock"),
);
export const updateCreatorProfile = createFailClosedCommunityCallable(
  surfaceByFunctionName("updateCreatorProfile"),
);
