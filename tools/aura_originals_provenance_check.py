#!/usr/bin/env python3
"""Validate Aura Originals manifest provenance and release readiness.

Checks every entry in the bundled manifest for:
  - Required fields: id, category, name, durationSec, url, sha256.
  - Provenance fields: license, sourceUrl, creator, curationDate, reviewResult.
  - CC0/Public Domain license requirement for bundled content.
  - Valid category (ringtone, notification, alarm).
  - No duplicate IDs.
  - HTTPS URL scheme.
  - sha256 format (hex, 64 chars).
  - Duration bounds per category.
"""
import json
import os
import re
import sys

VALID_CATEGORIES = {"ringtone", "notification", "alarm"}
CC0_LICENSES = {"CC0", "CC0 1.0", "PUBLIC DOMAIN", "CC0 1.0 UNIVERSAL"}
SHA256_PATTERN = re.compile(r"^[a-fA-F0-9]{64}$")
DURATION_RANGES = {
    "ringtone": (5.0, 40.0),
    "notification": (0.5, 8.0),
    "alarm": (5.0, 60.0),
}


def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    manifest_path = os.path.join(
        repo_root, "app", "src", "main", "assets", "aura_originals_manifest.json"
    )

    errors = []
    warnings = []

    if not os.path.isfile(manifest_path):
        errors.append(f"Missing manifest: {manifest_path}")
        _report(errors, warnings, 0)
        return

    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    sounds = manifest.get("sounds", [])
    if not sounds:
        print("OK: manifest is empty (curation pass pending)")
        sys.exit(0)

    seen_ids = set()

    for i, entry in enumerate(sounds):
        prefix = f"sounds[{i}] ({entry.get('id', '?')})"

        eid = entry.get("id", "").strip()
        if not eid:
            errors.append(f"{prefix}: missing id")
        elif eid in seen_ids:
            errors.append(f"{prefix}: duplicate id '{eid}'")
        seen_ids.add(eid)

        category = entry.get("category", "").strip()
        if category not in VALID_CATEGORIES:
            errors.append(f"{prefix}: invalid category '{category}'")

        if not entry.get("name", "").strip():
            errors.append(f"{prefix}: missing name")

        duration = entry.get("durationSec", 0.0)
        if category in DURATION_RANGES:
            lo, hi = DURATION_RANGES[category]
            if not (lo <= duration <= hi):
                warnings.append(f"{prefix}: duration {duration}s outside {category} range [{lo}, {hi}]")

        url = entry.get("url", "").strip()
        if not url:
            errors.append(f"{prefix}: missing url")
        elif not url.startswith("https://"):
            errors.append(f"{prefix}: url must use HTTPS: {url}")

        sha256 = entry.get("sha256", "").strip()
        if not sha256:
            errors.append(f"{prefix}: missing sha256")
        elif not SHA256_PATTERN.match(sha256):
            errors.append(f"{prefix}: sha256 must be 64 hex chars")

        license_val = entry.get("license", "").strip()
        if not license_val:
            errors.append(f"{prefix}: missing license")
        elif license_val.upper() not in CC0_LICENSES:
            errors.append(f"{prefix}: bundled content requires CC0 license, got '{license_val}'")

        source_url = entry.get("sourceUrl", "").strip()
        if not source_url:
            errors.append(f"{prefix}: missing sourceUrl (upstream attribution)")
        elif not source_url.startswith("https://"):
            errors.append(f"{prefix}: sourceUrl must use HTTPS: {source_url}")

        if not entry.get("creator", "").strip():
            errors.append(f"{prefix}: missing creator (upstream attribution)")

        if not entry.get("curationDate", "").strip():
            errors.append(f"{prefix}: missing curationDate")

        review = entry.get("reviewResult", "").strip()
        if not review:
            errors.append(f"{prefix}: missing reviewResult")
        elif review != "approved":
            warnings.append(f"{prefix}: reviewResult is '{review}', not 'approved'")

    _report(errors, warnings, len(sounds))


def _report(errors, warnings=None, count=0):
    if warnings:
        for w in warnings:
            print(f"  WARN: {w}")
    if errors:
        print(f"FAIL: {len(errors)} issue(s) in {count} entries")
        for e in errors:
            print(f"  - {e}")
        sys.exit(1)
    else:
        print(f"OK: {count} manifest entries pass provenance checks")
        sys.exit(0)


if __name__ == "__main__":
    main()
