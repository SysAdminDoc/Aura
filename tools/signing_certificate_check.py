#!/usr/bin/env python3
"""Keep the published signing-certificate digest matching the real keystore.

A sideloading user has no way to tell an official Aura build from a re-signed
one except the signing certificate, and AppVerifier and IzzyOnDroid's
`AllowedAPKSigningKeys` both key off its SHA-256. The digest had only ever
appeared in release notes, which neither a gate nor a store can read, so it
could drift from the keystore or from the documents quoting it and nothing
would notice.

Two halves:

  * every document the policy says publishes the digest actually contains it;
  * the digest matches the certificate in the release keystore.

The keystore half is tri-state, like `release_publication_check`. The keystore
password lives in `local.properties`, which is untracked, so a fresh clone
cannot read the certificate at all. That is reported as unknown and skipped
rather than failed, because a contributor should not be blocked by not holding
the release key. The document half always runs.

Exit 0 if clean, 1 if violations found.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any

POLICY_PATH = "docs/distribution/signing-certificate.json"
LOCAL_PROPERTIES = "local.properties"
SHA256_LINE = re.compile(r"SHA256:\s*((?:[0-9A-F]{2}:){31}[0-9A-F]{2})", re.IGNORECASE)


class SigningCertificateError(ValueError):
    """Raised when the published digest is missing or does not match the keystore."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate the published release signing certificate digest.",
    )
    parser.add_argument("--repo-root", default=".")
    parser.add_argument("--policy", default=POLICY_PATH)
    return parser.parse_args()


def read_local_properties(repo_root: Path) -> dict[str, str]:
    path = repo_root / LOCAL_PROPERTIES
    if not path.is_file():
        return {}
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.split("#", 1)[0].strip()
    return values


def find_keytool() -> str | None:
    found = shutil.which("keytool")
    if found:
        return found
    for env_name in ("JAVA_HOME", "JDK_HOME"):
        root = os.environ.get(env_name)
        if not root:
            continue
        candidate = Path(root) / "bin" / ("keytool.exe" if os.name == "nt" else "keytool")
        if candidate.is_file():
            return str(candidate)
    return None


def keystore_certificate_sha256(repo_root: Path, policy: dict[str, Any]) -> str | None:
    """The digest from the real keystore, or None when it cannot be read here."""
    props = read_local_properties(repo_root)
    keystore_property = str(policy["keystoreProperty"])
    relative = props.get(keystore_property)
    password = props.get("signing.keystore.password")
    if not relative or not password:
        return None

    keystore = Path(relative)
    if not keystore.is_absolute():
        # local.properties paths are relative to the app module, which is where
        # the Gradle signing config resolves them from.
        keystore = (repo_root / "app" / relative).resolve()
    if not keystore.is_file():
        return None

    keytool = find_keytool()
    if not keytool:
        return None

    try:
        result = subprocess.run(
            [
                keytool,
                "-list",
                "-v",
                "-keystore",
                str(keystore),
                "-alias",
                str(policy["keyAlias"]),
                "-storepass",
                password,
            ],
            capture_output=True,
            text=True,
            timeout=120,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    if result.returncode != 0:
        return None

    match = SHA256_LINE.search(result.stdout)
    if not match:
        return None
    return match.group(1).replace(":", "").upper()


def validate_signing_certificate(repo_root: Path, policy: dict[str, Any]) -> dict[str, object]:
    if policy.get("schemaVersion") != 1:
        raise SigningCertificateError("schemaVersion must be 1")
    if policy.get("policyKind") != "releaseSigningCertificate":
        raise SigningCertificateError("policyKind must be releaseSigningCertificate")

    declared = str(policy.get("certificateSha256", "")).replace(":", "").upper()
    if not re.fullmatch(r"[0-9A-F]{64}", declared):
        raise SigningCertificateError(
            "certificateSha256 must be 64 hex characters, colons optional"
        )

    published_on = policy.get("publishedOn")
    if not isinstance(published_on, list) or not published_on:
        raise SigningCertificateError("publishedOn must be a non-empty list of paths")

    # Keystore first. The keystore is the source of truth, so if the policy has
    # drifted from it every document quoting the policy is wrong too, and
    # reporting those as "digest absent" would name the symptom and hide the
    # cause.
    actual = keystore_certificate_sha256(repo_root, policy)
    if actual is None:
        keystore_state = "unknown"
    elif actual != declared:
        raise SigningCertificateError(
            f"{POLICY_PATH} publishes certificate {declared} but the release keystore "
            f"holds {actual}; a mismatch means every published verification "
            "instruction is wrong"
        )
    else:
        keystore_state = "matches"

    missing: list[str] = []
    for relative in published_on:
        path = repo_root / str(relative)
        if not path.is_file():
            raise SigningCertificateError(f"publishedOn names a missing file: {relative}")
        haystack = path.read_text(encoding="utf-8").replace(":", "").upper()
        if declared not in haystack:
            missing.append(str(relative))
    if missing:
        raise SigningCertificateError(
            "the signing certificate digest is absent from: " + ", ".join(sorted(missing))
        )

    return {
        "status": "ok",
        "policyKind": "releaseSigningCertificate",
        "schemaVersion": 1,
        "certificateSha256": declared,
        "keystore": keystore_state,
        "publishedOn": [str(item) for item in published_on],
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = json.loads((repo_root / args.policy).read_text(encoding="utf-8"))
        result = validate_signing_certificate(repo_root, policy)
    except (SigningCertificateError, OSError, ValueError, json.JSONDecodeError) as exc:
        print(json.dumps({"status": "fail", "error": str(exc)}, indent=2, sort_keys=True))
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
