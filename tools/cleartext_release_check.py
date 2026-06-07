#!/usr/bin/env python3
"""Check that release builds do not allow provider cleartext traffic."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


DEFAULT_SOURCE_ROOTS = [
    "app/src/main/java/com/freevibe/di",
    "app/src/main/java/com/freevibe/data/remote",
    "app/src/main/java/com/freevibe/data/repository",
]

CLEARTEXT_CONFIG_RE = re.compile(r"cleartextTrafficPermitted\s*=\s*[\"']true[\"']", re.IGNORECASE)
MANIFEST_CLEARTEXT_TRUE_RE = re.compile(r"usesCleartextTraffic\s*=\s*[\"']true[\"']", re.IGNORECASE)
HTTP_URL_LITERAL_RE = re.compile(r'"([^"\n]*http://[^"\n]*)"')
HTTP_SCHEME_CALL_RE = re.compile(r"\.scheme\(\s*\"http\"\s*\)")
SOURCE_SUFFIXES = {".java", ".kt"}


class CleartextReleaseError(ValueError):
    """Raised when release cleartext policy validation fails."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura release cleartext traffic policy.")
    parser.add_argument("--repo-root", default=".")
    parser.add_argument("--network-security-config", default="app/src/main/res/xml/network_security_config.xml")
    parser.add_argument("--manifest", default="app/src/main/AndroidManifest.xml")
    parser.add_argument(
        "--source-root",
        action="append",
        dest="source_roots",
        help="Provider source root to scan. Defaults to Aura's provider-network roots.",
    )
    return parser.parse_args()


def resolve_path(repo_root: Path, path: str | Path) -> Path:
    candidate = Path(path)
    if candidate.is_absolute():
        return candidate
    return repo_root / candidate


def matching_lines(path: Path, pattern: re.Pattern[str], label: str, repo_root: Path) -> list[str]:
    if not path.is_file():
        raise CleartextReleaseError(f"required file is missing: {path}")
    matches: list[str] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if pattern.search(line):
            relative = path.relative_to(repo_root) if path.is_relative_to(repo_root) else path
            matches.append(f"{relative}:{line_number}: {label}")
    return matches


def provider_source_files(repo_root: Path, source_roots: list[str]) -> list[Path]:
    files: list[Path] = []
    for root in source_roots:
        root_path = resolve_path(repo_root, root)
        if not root_path.exists():
            raise CleartextReleaseError(f"scan source root is missing: {root}")
        candidates = [root_path] if root_path.is_file() else root_path.rglob("*")
        files.extend(path for path in candidates if path.is_file() and path.suffix in SOURCE_SUFFIXES)
    return sorted(files)


def collect_provider_cleartext_references(repo_root: Path, source_roots: list[str]) -> tuple[list[str], int]:
    refs: list[str] = []
    checked_files = 0
    for path in provider_source_files(repo_root, source_roots):
        checked_files += 1
        for line_number, line in enumerate(path.read_text(encoding="utf-8", errors="ignore").splitlines(), start=1):
            if HTTP_URL_LITERAL_RE.search(line) or HTTP_SCHEME_CALL_RE.search(line):
                relative = path.relative_to(repo_root) if path.is_relative_to(repo_root) else path
                refs.append(f"{relative}:{line_number}: {line.strip()}")
    return refs, checked_files


def validate_cleartext_release_policy(
    repo_root: Path,
    network_security_config: str | Path,
    manifest: str | Path,
    source_roots: list[str] | None = None,
) -> dict[str, Any]:
    repo_root = repo_root.resolve()
    roots = source_roots or DEFAULT_SOURCE_ROOTS
    config_path = resolve_path(repo_root, network_security_config)
    manifest_path = resolve_path(repo_root, manifest)

    cleartext_config_refs = matching_lines(
        config_path,
        CLEARTEXT_CONFIG_RE,
        'cleartextTrafficPermitted="true"',
        repo_root,
    )
    manifest_cleartext_refs = matching_lines(
        manifest_path,
        MANIFEST_CLEARTEXT_TRUE_RE,
        'usesCleartextTraffic="true"',
        repo_root,
    )
    provider_refs, checked_files = collect_provider_cleartext_references(repo_root, roots)

    violations = cleartext_config_refs + manifest_cleartext_refs + provider_refs
    if violations:
        raise CleartextReleaseError("release cleartext policy violations: " + "; ".join(violations))

    return {
        "policyKind": "cleartextReleasePolicy",
        "status": "ok",
        "networkSecurityConfig": str(config_path.relative_to(repo_root)),
        "manifest": str(manifest_path.relative_to(repo_root)),
        "sourceRoots": roots,
        "checkedProviderSourceFiles": checked_files,
        "cleartextReferences": 0,
    }


def main() -> int:
    args = parse_args()
    try:
        result = validate_cleartext_release_policy(
            Path(args.repo_root),
            args.network_security_config,
            args.manifest,
            args.source_roots,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
