#!/usr/bin/env python3
"""Validate GitHub Actions workflow action references against an allowlist."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


USES_PATTERN = re.compile(r"^\s*uses:\s*['\"]?([^'\"\s#]+)['\"]?\s*(?:#.*)?$")


class GitHubActionsAllowlistError(ValueError):
    """Raised when GitHub Actions allowlist validation fails."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura GitHub Actions allowlist.")
    parser.add_argument("--policy", default="docs/distribution/github-actions-allowlist.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise GitHubActionsAllowlistError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise GitHubActionsAllowlistError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise GitHubActionsAllowlistError(f"{label} must be a non-empty list")
    values: list[str] = []
    for index, item in enumerate(value):
        values.append(require_string(item, f"{label}[{index}]"))
    if len(values) != len(set(values)):
        raise GitHubActionsAllowlistError(f"{label} contains duplicate values")
    return values


def validate_policy(policy: dict[str, Any]) -> dict[str, Any]:
    if policy.get("schemaVersion") != 1:
        raise GitHubActionsAllowlistError("GitHub Actions allowlist schemaVersion must be 1")
    if policy.get("policyKind") != "githubActionsAllowlist":
        raise GitHubActionsAllowlistError("GitHub Actions allowlist policyKind is invalid")
    workflow_directory = require_string(policy.get("workflowDirectory"), "workflowDirectory")
    if Path(workflow_directory).is_absolute() or ".." in Path(workflow_directory).parts:
        raise GitHubActionsAllowlistError("workflowDirectory must stay inside the repository")
    required_workflows = require_string_list(policy.get("requiredWorkflowPaths"), "requiredWorkflowPaths")
    allowed_actions = require_string_list(policy.get("allowedActions"), "allowedActions")
    forbidden_refs = require_string_list(policy.get("forbiddenRefs"), "forbiddenRefs")
    for action in allowed_actions:
        validate_action_reference(action, forbidden_refs, f"allowed action {action}")
    return {
        "workflowDirectory": workflow_directory,
        "requiredWorkflowPaths": required_workflows,
        "allowedActions": allowed_actions,
        "forbiddenRefs": forbidden_refs,
    }


def validate_action_reference(reference: str, forbidden_refs: list[str], label: str) -> None:
    if reference.startswith("./") or reference.startswith("../"):
        raise GitHubActionsAllowlistError(f"{label} must not use a local action")
    if "@" not in reference:
        raise GitHubActionsAllowlistError(f"{label} must pin an explicit ref")
    name, ref = reference.rsplit("@", 1)
    if not name or not ref:
        raise GitHubActionsAllowlistError(f"{label} must include action name and ref")
    if ref in forbidden_refs:
        raise GitHubActionsAllowlistError(f"{label} uses forbidden floating ref: {ref}")


def workflow_paths(repo_root: Path, workflow_directory: str) -> list[Path]:
    directory = repo_root / workflow_directory
    if not directory.is_dir():
        raise GitHubActionsAllowlistError(f"Workflow directory is missing: {workflow_directory}")
    return sorted(
        path
        for path in directory.iterdir()
        if path.is_file() and path.suffix.lower() in {".yml", ".yaml"}
    )


def normalize_repo_relative(path: Path, repo_root: Path) -> str:
    return path.relative_to(repo_root).as_posix()


def scan_workflow_uses(path: Path) -> list[tuple[int, str]]:
    uses: list[tuple[int, str]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        match = USES_PATTERN.match(line)
        if match:
            uses.append((line_number, match.group(1)))
    return uses


def validate_github_actions_allowlist(repo_root: Path, policy: dict[str, Any]) -> dict[str, Any]:
    parsed = validate_policy(policy)
    required_paths = set(parsed["requiredWorkflowPaths"])
    actual_paths = {normalize_repo_relative(path, repo_root) for path in workflow_paths(repo_root, parsed["workflowDirectory"])}
    missing_workflows = sorted(required_paths - actual_paths)
    if missing_workflows:
        raise GitHubActionsAllowlistError(f"Required workflow files are missing: {', '.join(missing_workflows)}")
    unexpected_workflows = sorted(actual_paths - required_paths)
    if unexpected_workflows:
        raise GitHubActionsAllowlistError(f"Unexpected workflow files need allowlist review: {', '.join(unexpected_workflows)}")

    allowed_actions = set(parsed["allowedActions"])
    forbidden_refs = parsed["forbiddenRefs"]
    seen_actions: set[str] = set()
    action_rows: list[dict[str, Any]] = []
    for workflow_path in workflow_paths(repo_root, parsed["workflowDirectory"]):
        relative_path = normalize_repo_relative(workflow_path, repo_root)
        for line_number, action in scan_workflow_uses(workflow_path):
            validate_action_reference(action, forbidden_refs, f"{relative_path}:{line_number}")
            if action not in allowed_actions:
                raise GitHubActionsAllowlistError(f"{relative_path}:{line_number} uses unreviewed action: {action}")
            seen_actions.add(action)
            action_rows.append({"action": action, "line": line_number, "workflow": relative_path})

    unused_actions = sorted(allowed_actions - seen_actions)
    if unused_actions:
        raise GitHubActionsAllowlistError(f"Allowed actions are not used by workflows: {', '.join(unused_actions)}")

    return {
        "actionReferenceCount": len(action_rows),
        "allowedActionCount": len(allowed_actions),
        "policyKind": policy["policyKind"],
        "schemaVersion": policy["schemaVersion"],
        "status": "ok",
        "workflowCount": len(actual_paths),
        "workflows": sorted(actual_paths),
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root)
    try:
        result = validate_github_actions_allowlist(
            repo_root,
            require_object(read_json(repo_root / args.policy), "GitHub Actions allowlist"),
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
