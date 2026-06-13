#!/usr/bin/env python3
"""Validate Aura's target-37 privacy/security static compatibility gates."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


class Target37CompatibilityError(ValueError):
    """Raised when the target-37 compatibility policy is stale or violated."""


PERMISSION_RE = re.compile(r'android:name="([^"]+)"')
STRING_LITERAL_RE = re.compile(r"""["']([^"']+)["']""")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate target-37 privacy/security compatibility policy."
    )
    parser.add_argument("--policy", default="docs/security/target37-compatibility.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise Target37CompatibilityError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise Target37CompatibilityError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise Target37CompatibilityError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list):
        raise Target37CompatibilityError(f"{label} must be a list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise Target37CompatibilityError(f"{label} contains duplicate values")
    return values


def kotlin_files(repo_root: Path, source_roots: list[str]) -> list[Path]:
    files: list[Path] = []
    for root in source_roots:
        root_path = repo_root / root
        if not root_path.exists():
            raise Target37CompatibilityError(f"scan source root is missing: {root}")
        candidates = [root_path] if root_path.is_file() else root_path.rglob("*")
        for path in candidates:
            if path.suffix in {".kt", ".java"}:
                files.append(path)
    return sorted(files)


def source_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def collect_term_findings(
    *,
    repo_root: Path,
    files: list[Path],
    terms: list[str],
) -> list[tuple[str, str, int]]:
    findings: list[tuple[str, str, int]] = []
    for path in files:
        text = source_text(path)
        for index, line in enumerate(text.splitlines(), start=1):
            for term in terms:
                if term in line:
                    findings.append((path.relative_to(repo_root).as_posix(), term, index))
    return findings


def declared_permissions(manifest_path: Path) -> set[str]:
    if not manifest_path.is_file():
        raise Target37CompatibilityError(f"manifest file is missing: {manifest_path}")
    permissions: set[str] = set()
    for match in PERMISSION_RE.finditer(manifest_path.read_text(encoding="utf-8")):
        value = match.group(1)
        if value.startswith("android.permission."):
            permissions.add(value)
    return permissions


def ensure_forbidden_permissions_absent(
    *,
    permissions: set[str],
    forbidden: list[str],
    label: str,
) -> None:
    present = sorted(set(forbidden) & permissions)
    if present:
        raise Target37CompatibilityError(
            f"{label} forbidden manifest permissions present: {', '.join(present)}"
        )


def validate_local_network(
    *,
    repo_root: Path,
    files: list[Path],
    policy: dict[str, Any],
) -> int:
    require_string(policy.get("decision"), "localNetwork.decision")
    forbidden_terms = require_string_list(policy.get("forbiddenTerms"), "localNetwork.forbiddenTerms")
    findings = collect_term_findings(repo_root=repo_root, files=files, terms=forbidden_terms)
    pattern = require_string(policy.get("stringLiteralHostPattern"), "localNetwork.stringLiteralHostPattern")
    local_host_re = re.compile(pattern)
    for path in files:
        for index, line in enumerate(source_text(path).splitlines(), start=1):
            for literal in STRING_LITERAL_RE.findall(line):
                if local_host_re.search(literal):
                    findings.append((path.relative_to(repo_root).as_posix(), pattern, index))
    if findings:
        detail = "; ".join(f"{path}:{line} uses {term}" for path, term, line in findings[:20])
        raise Target37CompatibilityError(f"unreviewed local-network API or .local literal: {detail}")
    return len(forbidden_terms)


def validate_contacts(
    *,
    repo_root: Path,
    files: list[Path],
    policy: dict[str, Any],
) -> int:
    require_string(policy.get("decision"), "contacts.decision")
    reviewed = set(require_string_list(policy.get("reviewedContactFiles"), "contacts.reviewedContactFiles"))
    for relative_path in reviewed:
        if not (repo_root / relative_path).is_file():
            raise Target37CompatibilityError(f"reviewed contact file is missing: {relative_path}")
    findings = [
        (path.relative_to(repo_root).as_posix(), index)
        for path in files
        for index, line in enumerate(source_text(path).splitlines(), start=1)
        if "ContactsContract" in line
    ]
    unreviewed = [(path, line) for path, line in findings if path not in reviewed]
    if unreviewed:
        detail = "; ".join(f"{path}:{line}" for path, line in unreviewed[:20])
        raise Target37CompatibilityError(f"unreviewed ContactsContract usage: {detail}")
    return len(findings)


def reviewed_reflection_terms(policy: dict[str, Any], repo_root: Path) -> dict[tuple[str, str], str]:
    reviewed_raw = policy.get("reviewedOccurrences")
    if not isinstance(reviewed_raw, list):
        raise Target37CompatibilityError("reflection.reviewedOccurrences must be a list")
    reviewed: dict[tuple[str, str], str] = {}
    for index, raw_entry in enumerate(reviewed_raw):
        entry = require_object(raw_entry, f"reflection.reviewedOccurrences[{index}]")
        path = require_string(entry.get("path"), f"reflection.reviewedOccurrences[{index}].path")
        terms = require_string_list(entry.get("terms"), f"reflection.reviewedOccurrences[{index}].terms")
        reason = require_string(entry.get("reason"), f"reflection.reviewedOccurrences[{index}].reason")
        file_path = repo_root / path
        if not file_path.is_file():
            raise Target37CompatibilityError(f"reviewed reflection file is missing: {path}")
        text = source_text(file_path)
        for term in terms:
            if term not in text:
                raise Target37CompatibilityError(
                    f"reviewed reflection term {term!r} is not present in {path}"
                )
            reviewed[(path, term)] = reason
    return reviewed


def validate_reflection(
    *,
    repo_root: Path,
    files: list[Path],
    policy: dict[str, Any],
) -> int:
    require_string(policy.get("decision"), "reflection.decision")
    terms = require_string_list(policy.get("terms"), "reflection.terms")
    reviewed = reviewed_reflection_terms(policy, repo_root)
    findings = collect_term_findings(repo_root=repo_root, files=files, terms=terms)
    unreviewed = [
        (path, term, line)
        for path, term, line in findings
        if (path, term) not in reviewed
    ]
    if unreviewed:
        detail = "; ".join(f"{path}:{line} uses {term}" for path, term, line in unreviewed[:20])
        raise Target37CompatibilityError(f"unreviewed reflection usage: {detail}")
    return len(findings)


def validate_network_security(repo_root: Path, scan: dict[str, Any], policy: dict[str, Any]) -> None:
    require_string(policy.get("decision"), "networkSecurity.decision")
    require_string(policy.get("ccmixterCleartextException"), "networkSecurity.ccmixterCleartextException")
    config_path = repo_root / require_string(
        scan.get("networkSecurityConfigPath"),
        "scan.networkSecurityConfigPath",
    )
    if not config_path.is_file():
        raise Target37CompatibilityError(f"network security config is missing: {config_path}")
    text = config_path.read_text(encoding="utf-8")
    cleartext_allowed = policy.get("cleartextPermitted")
    if cleartext_allowed is not False:
        raise Target37CompatibilityError("networkSecurity.cleartextPermitted must be false")
    if 'cleartextTrafficPermitted="true"' in text:
        raise Target37CompatibilityError("network security config allows cleartext traffic")


def validate_dynamic_code_loading(
    *,
    repo_root: Path,
    files: list[Path],
    policy: dict[str, Any],
) -> int:
    require_string(policy.get("decision"), "dynamicCodeLoading.decision")
    forbidden_terms = require_string_list(
        policy.get("forbiddenTerms"),
        "dynamicCodeLoading.forbiddenTerms",
    )
    findings = collect_term_findings(repo_root=repo_root, files=files, terms=forbidden_terms)
    if findings:
        detail = "; ".join(f"{path}:{line} uses {term}" for path, term, line in findings[:20])
        raise Target37CompatibilityError(f"unreviewed dynamic code loading: {detail}")
    return len(forbidden_terms)


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, Any]:
    if policy.get("schemaVersion") != 1:
        raise Target37CompatibilityError("target37 policy schemaVersion must be 1")
    if policy.get("policyKind") != "target37PrivacySecurityCompatibilityPreflight":
        raise Target37CompatibilityError("target37 policyKind is invalid")
    require_string(policy.get("androidVersion"), "androidVersion")
    scan = require_object(policy.get("scan"), "scan")
    source_roots = require_string_list(scan.get("sourceRoots"), "scan.sourceRoots")
    files = kotlin_files(repo_root, source_roots)
    permissions = declared_permissions(repo_root / require_string(scan.get("manifestPath"), "scan.manifestPath"))

    local_network = require_object(policy.get("localNetwork"), "localNetwork")
    ensure_forbidden_permissions_absent(
        permissions=permissions,
        forbidden=require_string_list(local_network.get("forbiddenPermissions"), "localNetwork.forbiddenPermissions"),
        label="localNetwork",
    )
    local_terms = validate_local_network(repo_root=repo_root, files=files, policy=local_network)

    contacts = require_object(policy.get("contacts"), "contacts")
    ensure_forbidden_permissions_absent(
        permissions=permissions,
        forbidden=require_string_list(
            contacts.get("forbiddenManifestPermissions"),
            "contacts.forbiddenManifestPermissions",
        ),
        label="contacts",
    )
    contact_findings = validate_contacts(repo_root=repo_root, files=files, policy=contacts)

    reflection_findings = validate_reflection(
        repo_root=repo_root,
        files=files,
        policy=require_object(policy.get("reflection"), "reflection"),
    )
    validate_network_security(
        repo_root=repo_root,
        scan=scan,
        policy=require_object(policy.get("networkSecurity"), "networkSecurity"),
    )
    dcl_terms = validate_dynamic_code_loading(
        repo_root=repo_root,
        files=files,
        policy=require_object(policy.get("dynamicCodeLoading"), "dynamicCodeLoading"),
    )
    tls = require_object(policy.get("tlsPlatformReview"), "tlsPlatformReview")
    require_string(tls.get("certificateTransparency"), "tlsPlatformReview.certificateTransparency")
    require_string(tls.get("encryptedClientHello"), "tlsPlatformReview.encryptedClientHello")

    return {
        "policyKind": policy["policyKind"],
        "schemaVersion": policy["schemaVersion"],
        "scannedFiles": len(files),
        "declaredPermissions": sorted(permissions),
        "localNetworkTerms": local_terms,
        "contactsContractFindings": contact_findings,
        "reviewedReflectionFindings": reflection_findings,
        "dynamicCodeLoadingTerms": dcl_terms,
        "status": "ok",
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root)
    try:
        policy = require_object(read_json(repo_root / args.policy), "target37 policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
