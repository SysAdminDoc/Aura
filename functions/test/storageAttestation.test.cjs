const assert = require("node:assert/strict");
const test = require("node:test");

const {
  attestStorageObject,
  readJpegDimensions,
  storageObjectMetadataFrom,
} = require("../lib/storageAttestation.js");

// Production host unless a test opts into the emulator.
delete process.env.FIREBASE_STORAGE_EMULATOR_HOST;

const BUCKET = "aura-test.appspot.com";
// The space checks that the object name is compared decoded and re-encoded the same way.
const STORAGE_PATH = "sounds/owner1/1700000000000_bell tone.mp3";
const OBJECT = "sounds%2Fowner1%2F1700000000000_bell%20tone.mp3";
const URL_PREFIX = `https://firebasestorage.googleapis.com/v0/b/${BUCKET}/o/`;
const DECLARED_URL = `${URL_PREFIX}${OBJECT}?alt=media&token=tok-1`;
const RULES = {
  allowedContentTypes: new Set(["audio/mpeg", "audio/ogg"]),
  maxBytes: 1_000,
  declaredContentType: "audio/mpeg",
  declaredBytes: 500,
};

function stored(overrides = {}) {
  return {
    exists: true,
    bucket: BUCKET,
    contentType: "audio/mpeg",
    size: 500,
    downloadTokens: ["tok-0", "tok-1"],
    ...overrides,
  };
}

function attest({ url = DECLARED_URL, metadata = stored(), rules = RULES } = {}) {
  return attestStorageObject(STORAGE_PATH, url, metadata, rules, { operationId: "op-1" });
}

function assertRefused(fn, reason) {
  assert.throws(fn, (error) => {
    assert.equal(error.code, "failed-precondition");
    assert.equal(error.details.reason, reason);
    assert.equal(error.details.operationId, "op-1");
    assert.equal(error.details.storagePath, STORAGE_PATH);
    return true;
  });
}

test("a matching object attests to server-derived values", () => {
  const attested = attest();
  assert.deepEqual(attested, {
    storagePath: STORAGE_PATH,
    bucket: BUCKET,
    contentType: "audio/mpeg",
    size: 500,
    downloadUrl: `${URL_PREFIX}${OBJECT}?alt=media&token=tok-1`,
  });

  // Lowercase escapes and a reordered query name the same object, and come back canonical.
  const respelled = attest({ url: `${URL_PREFIX}sounds%2fowner1%2f1700000000000_bell%20tone.mp3?token=tok-1&alt=media` });
  assert.equal(respelled.downloadUrl, attested.downloadUrl);

  // Case and parameters don't change the stored type.
  assert.equal(attest({ metadata: stored({ contentType: "AUDIO/MPEG; foo=bar" }) }).contentType, "audio/mpeg");
});

test("a missing object or unavailable metadata fails closed", () => {
  assertRefused(() => attest({ metadata: { exists: false } }), "object-missing");

  for (const field of ["bucket", "contentType", "size", "downloadTokens"]) {
    assertRefused(() => attest({ metadata: stored({ [field]: undefined }) }), "metadata-unavailable");
  }
  for (const overrides of [{ bucket: "" }, { contentType: "" }, { size: Number.NaN }, { downloadTokens: [] }]) {
    assertRefused(() => attest({ metadata: stored(overrides) }), "metadata-unavailable");
  }

  assert.throws(() => attest({ metadata: { exists: true } }), (error) => {
    assert.deepEqual(error.details.missing, ["bucket", "contentType", "size", "downloadTokens"]);
    return true;
  });
});

test("the stored MIME type must be allowed and exactly the declared one", () => {
  assert.throws(() => attest({ metadata: stored({ contentType: "audio/ogg" }) }), (error) => {
    assert.equal(error.details.reason, "content-type-mismatch");
    assert.equal(error.details.declared, "audio/mpeg");
    assert.equal(error.details.actual, "audio/ogg");
    return true;
  });

  // Agreeing with the client isn't enough when the type isn't allowed.
  assertRefused(
    () => attest({ metadata: stored({ contentType: "video/mp4" }), rules: { ...RULES, declaredContentType: "video/mp4" } }),
    "content-type-mismatch",
  );
});

test("the stored size must be positive, within the limit, and equal to the declared size", () => {
  assertRefused(() => attest({ metadata: stored({ size: 0 }) }), "size-out-of-range");
  assertRefused(() => attest({ metadata: stored({ size: -1 }) }), "size-out-of-range");
  assertRefused(() => attest({ metadata: stored({ size: 500.5 }) }), "size-out-of-range");
  assertRefused(
    () => attest({ metadata: stored({ size: 1_001 }), rules: { ...RULES, declaredBytes: 1_001 } }),
    "size-out-of-range",
  );
  assertRefused(() => attest({ metadata: stored({ size: 501 }) }), "size-mismatch");

  assert.equal(attest({ metadata: stored({ size: 1_000 }), rules: { ...RULES, declaredBytes: 1_000 } }).size, 1_000);
});

test("the declared URL must name this bucket and object on the Storage host", () => {
  for (const url of [
    "not a url",
    DECLARED_URL.replace("https://", "http://"),
    DECLARED_URL.replace("firebasestorage.googleapis.com", "firebasestorage.googleapis.com.evil.example"),
    DECLARED_URL.replace("firebasestorage.googleapis.com", "firebasestorage.googleapis.com:8443"),
    DECLARED_URL.replace("/v0/b/", "/v1/b/"),
    DECLARED_URL.replace(BUCKET, "other.appspot.com"),
    DECLARED_URL.replace("bell%20tone", "bell%20tune"),
    DECLARED_URL.replace(OBJECT, "sounds/owner1/1700000000000_bell%20tone.mp3"),
    DECLARED_URL.replace(OBJECT, `${OBJECT}%2Fextra`),
    DECLARED_URL.replace(OBJECT, "sounds%2Fowner1%2F%E0%A4%A"),
  ]) {
    assertRefused(() => attest({ url }), "download-url-mismatch");
  }
});

test("the URL must carry exactly one of the object's download tokens", () => {
  for (const url of [
    DECLARED_URL.replace("&token=tok-1", ""),
    DECLARED_URL.replace("tok-1", "tok-forged"),
    DECLARED_URL.replace("tok-1", "TOK-1"),
    `${DECLARED_URL}&token=tok-0`,
  ]) {
    assertRefused(() => attest({ url }), "download-token-unknown");
  }

  // Any of the object's tokens works, and the published URL carries the one presented.
  assert.equal(
    attest({ url: DECLARED_URL.replace("tok-1", "tok-0") }).downloadUrl,
    `${URL_PREFIX}${OBJECT}?alt=media&token=tok-0`,
  );
});

test("under the Storage emulator only the emulator host is accepted", () => {
  process.env.FIREBASE_STORAGE_EMULATOR_HOST = "127.0.0.1:9199";
  try {
    const emulatorUrl = DECLARED_URL.replace("https://firebasestorage.googleapis.com", "http://127.0.0.1:9199");
    assert.equal(attest({ url: emulatorUrl }).downloadUrl, emulatorUrl);
    assert.equal(attest({ url: emulatorUrl.replace("http://", "https://") }).downloadUrl, emulatorUrl);
    assertRefused(() => attest(), "download-url-mismatch");
  } finally {
    delete process.env.FIREBASE_STORAGE_EMULATOR_HOST;
  }
});

test("GCS metadata maps onto attestation metadata, download tokens included", () => {
  assert.deepEqual(
    storageObjectMetadataFrom({
      bucket: BUCKET,
      contentType: "audio/mpeg",
      size: "500",
      metadata: { firebaseStorageDownloadTokens: "tok-0, tok-1,," },
    }),
    { exists: true, bucket: BUCKET, contentType: "audio/mpeg", size: 500, downloadTokens: ["tok-0", "tok-1"] },
  );
  assert.deepEqual(storageObjectMetadataFrom({}), {
    exists: true,
    bucket: undefined,
    contentType: undefined,
    size: undefined,
    downloadTokens: undefined,
  });
  assert.equal(storageObjectMetadataFrom({ size: "not-a-number" }).size, undefined);
  assert.equal(storageObjectMetadataFrom({ metadata: { firebaseStorageDownloadTokens: 12 } }).downloadTokens, undefined);
});

const APP0 = [0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00];
const DHT = [0xff, 0xc4, 0x00, 0x03, 0x00];
const DAC = [0xff, 0xcc, 0x00, 0x04, 0x00, 0x00];
const SOS = [0xff, 0xda, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3f, 0x00];

function sof(marker, width, height) {
  return [
    0xff, marker, 0x00, 0x11, 0x08, height >> 8, height & 0xff, width >> 8, width & 0xff,
    0x03, 0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
  ];
}

function jpeg(...segments) {
  return Uint8Array.from([0xff, 0xd8, ...segments.flat()]);
}

test("readJpegDimensions reads the first frame header", () => {
  assert.deepEqual(readJpegDimensions(jpeg(APP0, sof(0xc0, 1080, 1920))), { width: 1080, height: 1920 });
  assert.deepEqual(readJpegDimensions(Buffer.from(jpeg(APP0, sof(0xc0, 1080, 1920)))), { width: 1080, height: 1920 });
  // Progressive frames count, fill bytes and TEM are skipped, and DHT/DAC aren't frames.
  assert.deepEqual(readJpegDimensions(jpeg(DHT, [0xff, 0xff], sof(0xc2, 2560, 1440))), { width: 2560, height: 1440 });
  assert.deepEqual(readJpegDimensions(jpeg([0xff, 0x01], DAC, sof(0xc1, 100, 200))), { width: 100, height: 200 });
  assert.deepEqual(readJpegDimensions(jpeg(sof(0xc0, 10, 20), sof(0xc0, 30, 40))), { width: 10, height: 20 });
});

test("readJpegDimensions returns null for anything malformed or truncated", () => {
  for (const head of [
    new Uint8Array(0),
    Uint8Array.from([0xff, 0xd8]),
    Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    jpeg(APP0),
    jpeg(APP0, sof(0xc0, 1080, 1920)).subarray(0, 25),
    jpeg(APP0).subarray(0, 5),
    jpeg([0xff, 0xe0, 0x00, 0x40, 0x00]),
    jpeg([0xff, 0xe0, 0x00, 0x01]),
    jpeg(SOS, sof(0xc0, 1080, 1920)),
    jpeg([0xff, 0xd9]),
    jpeg([0xff, 0xd8], sof(0xc0, 1080, 1920)),
    jpeg([0x00, 0xff, 0xc0]),
    jpeg([0xff, 0x02, 0x00, 0x02], sof(0xc0, 1080, 1920)),
    jpeg(sof(0xc0, 1080, 0)),
    jpeg(sof(0xc0, 0, 1920)),
    jpeg([0xff, 0xc0, 0x00, 0x07, 0x08, 0x07, 0x80, 0x04, 0x38]),
  ]) {
    assert.equal(readJpegDimensions(head), null);
  }
});
