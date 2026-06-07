const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const {
  COMMUNITY_CALLABLE_SURFACES,
  QUOTA_DAY_BOUNDARY,
  callableExportNames,
  callableRuntimeOptionsFor,
  surfaceByFunctionName,
} = require("../lib/communityContract.js");

function readManifest() {
  const manifestPath = path.resolve(__dirname, "..", "..", "docs", "community-callable-contract.json");
  return JSON.parse(fs.readFileSync(manifestPath, "utf8"));
}

test("functions contract mirrors the backend manifest", () => {
  const manifest = readManifest();

  assert.equal(QUOTA_DAY_BOUNDARY, manifest.quotaDayBoundary);
  assert.deepEqual(
    COMMUNITY_CALLABLE_SURFACES.map((surface) => JSON.parse(JSON.stringify(surface))),
    manifest.surfaces,
  );
});

test("callable export names cover every contracted function", () => {
  assert.deepEqual(
    callableExportNames(),
    readManifest().surfaces.map((surface) => surface.callable.functionName),
  );
});

test("runtime options enforce App Check and limited-use token choices", () => {
  const limitedUseFunctions = new Set([
    "submitCommunityReport",
    "finalizeCommunitySoundUpload",
    "finalizeCommunityWallpaperUpload",
  ]);

  for (const functionName of callableExportNames()) {
    const options = callableRuntimeOptionsFor(surfaceByFunctionName(functionName));
    assert.equal(options.enforceAppCheck, true, functionName);
    assert.equal(options.consumeAppCheckToken, limitedUseFunctions.has(functionName), functionName);
  }
});
