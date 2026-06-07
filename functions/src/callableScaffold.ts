import { HttpsError, onCall } from "firebase-functions/v2/https";

import {
  callableRuntimeOptionsFor,
  type CommunityCallableSurface,
} from "./communityContract";

interface CallableIdentityRequest {
  readonly auth?: {
    readonly uid?: string;
  };
  readonly app?: unknown;
}

export function createFailClosedCommunityCallable(surface: CommunityCallableSurface) {
  return onCall(callableRuntimeOptionsFor(surface), async (request) => {
    requireCallableIdentity(request, surface);
    throw new HttpsError(
      "failed-precondition",
      `${surface.callable.functionName} is not enabled until its write handler and emulator tests land.`,
      {
        surfaceKey: surface.surfaceKey,
        status: "handler_pending",
      },
    );
  });
}

export function requireCallableIdentity(
  request: CallableIdentityRequest,
  surface: CommunityCallableSurface,
): string {
  const uid = request.auth?.uid?.trim();
  if (!uid) {
    throw new HttpsError("unauthenticated", `${surface.callable.functionName} requires Firebase Auth.`);
  }
  if (surface.callable.requiresAppCheck && request.app === undefined) {
    throw new HttpsError("failed-precondition", `${surface.callable.functionName} requires App Check.`);
  }
  return uid;
}
