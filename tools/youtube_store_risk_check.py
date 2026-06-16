#!/usr/bin/env python3
"""Verify YouTube store-risk containment profile.

Checks:
  - Profile JSON exists and has required channel/runtime/risk-term sections.
  - Fastlane full_description.txt does not contain banned risk terms.
  - Cautious terms in metadata are flagged as warnings (not failures).
  - Runtime toggle source file exists.
"""
import argparse
import json
import re
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--profile",
        default="docs/distribution/youtube-store-risk-profile.json",
    )
    parser.add_argument("--repo-root", default=".")
    args = parser.parse_args()

    root = Path(args.repo_root)
    profile_path = root / args.profile
    if not profile_path.exists():
        print(f"FAIL: {profile_path} not found", file=sys.stderr)
        sys.exit(1)

    profile = json.loads(profile_path.read_text(encoding="utf-8"))
    errors = []
    warnings = []

    for section in ("channels", "runtimeBehavior", "metadataRiskTerms"):
        if section not in profile:
            errors.append(f"Missing required section: {section}")

    channels = profile.get("channels", {})
    for name in ("github", "izzyOnDroid", "play"):
        if name not in channels:
            errors.append(f"Missing channel profile: {name}")
        else:
            ch = channels[name]
            if "youtubeEnabled" not in ch:
                errors.append(f"Channel '{name}' missing youtubeEnabled field")
            if "metadataPolicy" not in ch:
                errors.append(f"Channel '{name}' missing metadataPolicy field")

    runtime = profile.get("runtimeBehavior", {})
    source_file = runtime.get("sourceFile", "")
    if source_file:
        base_name = source_file.split("(")[0].strip()
        matches = list(root.rglob(f"**/{base_name}"))
        if not matches:
            errors.append(f"Runtime toggle source file not found: {base_name}")

    risk_terms = profile.get("metadataRiskTerms", {})
    banned = risk_terms.get("banned", [])
    cautious = risk_terms.get("cautious", [])

    metadata_rel = profile.get("fastlaneMetadataFile", "")
    if metadata_rel:
        metadata_path = root / metadata_rel
        if metadata_path.exists():
            text = metadata_path.read_text(encoding="utf-8").lower()
            for term in banned:
                if term.lower() in text:
                    errors.append(
                        f"Banned term '{term}' found in {metadata_rel}"
                    )
            for term in cautious:
                if term.lower() in text:
                    warnings.append(
                        f"Cautious term '{term}' found in {metadata_rel} — review for Play submission"
                    )
        else:
            warnings.append(f"Fastlane metadata not found: {metadata_rel}")

    if warnings:
        for w in warnings:
            print(f"  WARN: {w}")
    if errors:
        for e in errors:
            print(f"  FAIL: {e}", file=sys.stderr)
        sys.exit(1)

    channel_count = len(channels)
    banned_count = len(banned)
    print(
        f"OK: YouTube store-risk profile covers {channel_count} channels, "
        f"{banned_count} banned terms checked against metadata"
    )


if __name__ == "__main__":
    main()
