#!/usr/bin/env python3
"""Validate Aura's Gradle wrapper distribution policy."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


EXPECTED_DISTRIBUTION_URL = r"https\://services.gradle.org/distributions/gradle-8.12-bin.zip"
EXPECTED_DISTRIBUTION_SHA256 = "7a00d51fb93147819aab76024feece20b6b84e420694101f276be952e08bef03"
EXPECTED_WRAPPER_PATH = Path("gradle/wrapper/gradle-wrapper.properties")
SHA256_HEX = re.compile(r"^[a-f0-9]{64}$")


class GradleWrapperPolicyError(ValueError):
    """Raised when the Gradle wrapper policy check fails."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura Gradle wrapper policy.")
    parser.add_argument("--properties", default=str(EXPECTED_WRAPPER_PATH))
    return parser.parse_args()


def parse_properties(text: str) -> dict[str, str]:
    properties: dict[str, str] = {}
    for line_number, raw_line in enumerate(text.splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise GradleWrapperPolicyError(f"Line {line_number} must be a key/value entry")
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key or not value:
            raise GradleWrapperPolicyError(f"Line {line_number} key and value must be non-empty")
        if key in properties:
            raise GradleWrapperPolicyError(f"Duplicate Gradle wrapper property: {key}")
        properties[key] = value
    return properties


def require_property(properties: dict[str, str], key: str) -> str:
    value = properties.get(key)
    if not value:
        raise GradleWrapperPolicyError(f"Missing Gradle wrapper property: {key}")
    return value


def validate_gradle_wrapper(path: Path) -> dict[str, Any]:
    properties = parse_properties(path.read_text(encoding="utf-8"))

    distribution_url = require_property(properties, "distributionUrl")
    if distribution_url != EXPECTED_DISTRIBUTION_URL:
        raise GradleWrapperPolicyError("Gradle wrapper distributionUrl must remain the reviewed Gradle 8.12 bin ZIP")
    if distribution_url.endswith("-all.zip"):
        raise GradleWrapperPolicyError("Gradle wrapper must use the bin distribution, not the all distribution")

    distribution_sha256 = require_property(properties, "distributionSha256Sum").lower()
    if not SHA256_HEX.fullmatch(distribution_sha256):
        raise GradleWrapperPolicyError("Gradle wrapper distributionSha256Sum must be a SHA-256 hex digest")
    if distribution_sha256 != EXPECTED_DISTRIBUTION_SHA256:
        raise GradleWrapperPolicyError("Gradle wrapper distributionSha256Sum does not match the reviewed Gradle 8.12 checksum")

    if require_property(properties, "validateDistributionUrl") != "true":
        raise GradleWrapperPolicyError("Gradle wrapper validateDistributionUrl must be true")
    if require_property(properties, "distributionBase") != "GRADLE_USER_HOME":
        raise GradleWrapperPolicyError("Gradle wrapper distributionBase must be GRADLE_USER_HOME")
    if require_property(properties, "zipStoreBase") != "GRADLE_USER_HOME":
        raise GradleWrapperPolicyError("Gradle wrapper zipStoreBase must be GRADLE_USER_HOME")
    if require_property(properties, "distributionPath") != "wrapper/dists":
        raise GradleWrapperPolicyError("Gradle wrapper distributionPath must be wrapper/dists")
    if require_property(properties, "zipStorePath") != "wrapper/dists":
        raise GradleWrapperPolicyError("Gradle wrapper zipStorePath must be wrapper/dists")

    try:
        network_timeout = int(require_property(properties, "networkTimeout"))
    except ValueError as exc:
        raise GradleWrapperPolicyError("Gradle wrapper networkTimeout must be an integer") from exc
    if network_timeout < 10000:
        raise GradleWrapperPolicyError("Gradle wrapper networkTimeout must be at least 10000")

    return {
        "distributionSha256Sum": distribution_sha256,
        "distributionUrl": distribution_url,
        "networkTimeout": network_timeout,
        "properties": str(path),
        "status": "ok",
    }


def main() -> int:
    args = parse_args()
    try:
        result = validate_gradle_wrapper(Path(args.properties))
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
