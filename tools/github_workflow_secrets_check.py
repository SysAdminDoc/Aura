#!/usr/bin/env python3
"""Validate GitHub workflow secret references against a reviewed allowlist."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


SECRET_PATTERN = re.compile(r"\$\{\{\s*secrets\.([A-Za-z0-9_]+)\s*}}")
ENV_SECRET_PATTERN = re.compile(
    r"^\s*([A-Za-z_][A-Za-z0-9_]*)\s*:\s*\$\{\{\s*secrets\.([A-Za-z0-9_]+)\s*}}\s*(?:#.*)?$"
)


class WorkflowSecretPolicyError(ValueError):
    """Raised when GitHub workflow secret policy validation fails."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura GitHub workflow secret policy.")
    parser.add_argument("--policy", default="docs/distribution/github-workflow-secrets.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise WorkflowSecretPolicyError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise WorkflowSecretPolicyError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise WorkflowSecretPolicyError(f"{label} must be a non-empty list")
    values: list[str] = []
    for index, item in enumerate(value):
        values.append(require_string(item, f"{label}[{index}]"))
    if len(values) != len(set(values)):
        raise WorkflowSecretPolicyError(f"{label} contains duplicate values")
    return values


def normalize_repo_relative(path: Path, repo_root: Path) -> str:
    return path.relative_to(repo_root).as_posix()


def workflow_paths(repo_root: Path, workflow_directory: str) -> list[Path]:
    directory = repo_root / workflow_directory
    if not directory.is_dir():
        raise WorkflowSecretPolicyError(f"Workflow directory is missing: {workflow_directory}")
    return sorted(
        path
        for path in directory.iterdir()
        if path.is_file() and path.suffix.lower() in {".yml", ".yaml"}
    )


def validate_policy(policy: dict[str, Any]) -> dict[str, Any]:
    if policy.get("schemaVersion") != 1:
        raise WorkflowSecretPolicyError("Workflow secret policy schemaVersion must be 1")
    if policy.get("policyKind") != "githubWorkflowSecretPolicy":
        raise WorkflowSecretPolicyError("Workflow secret policyKind is invalid")
    workflow_directory = require_string(policy.get("workflowDirectory"), "workflowDirectory")
    if Path(workflow_directory).is_absolute() or ".." in Path(workflow_directory).parts:
        raise WorkflowSecretPolicyError("workflowDirectory must stay inside the repository")
    required_workflows = require_string_list(policy.get("requiredWorkflowPaths"), "requiredWorkflowPaths")
    forbidden_patterns = require_string_list(policy.get("forbiddenTokenPatterns"), "forbiddenTokenPatterns")

    raw_secrets = policy.get("allowedSecrets")
    if not isinstance(raw_secrets, list) or not raw_secrets:
        raise WorkflowSecretPolicyError("allowedSecrets must be a non-empty list")
    allowed: dict[tuple[str, str], dict[str, Any]] = {}
    for index, raw_secret in enumerate(raw_secrets):
        secret = require_object(raw_secret, f"allowedSecrets[{index}]")
        secret_name = require_string(secret.get("secret"), f"allowedSecrets[{index}].secret")
        workflow = require_string(secret.get("workflow"), f"{secret_name}.workflow")
        if Path(workflow).is_absolute() or ".." in Path(workflow).parts:
            raise WorkflowSecretPolicyError(f"{secret_name} workflow must stay inside the repository")
        allowed_env_names = require_string_list(secret.get("allowedEnvNames"), f"{secret_name}.allowedEnvNames")
        purpose = require_string(secret.get("purpose"), f"{secret_name}.purpose")
        key = (workflow, secret_name)
        if key in allowed:
            raise WorkflowSecretPolicyError(f"Duplicate allowed secret entry: {workflow}:{secret_name}")
        allowed[key] = {
            "allowedEnvNames": set(allowed_env_names),
            "purpose": purpose,
            "secret": secret_name,
            "workflow": workflow,
        }

    policy_workflows = {workflow for workflow, _secret in allowed}
    missing_policy_workflows = sorted(policy_workflows - set(required_workflows))
    if missing_policy_workflows:
        raise WorkflowSecretPolicyError(
            f"Allowed secret workflows are not required workflows: {', '.join(missing_policy_workflows)}"
        )

    return {
        "allowedSecrets": allowed,
        "forbiddenTokenPatterns": forbidden_patterns,
        "requiredWorkflowPaths": required_workflows,
        "workflowDirectory": workflow_directory,
    }


def find_secret_references(path: Path, relative_path: str, forbidden_patterns: list[str]) -> list[dict[str, Any]]:
    references: list[dict[str, Any]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        for forbidden in forbidden_patterns:
            if forbidden in line:
                raise WorkflowSecretPolicyError(f"{relative_path}:{line_number} uses forbidden token pattern: {forbidden}")
        if "${{" in line and "secrets" in line and not SECRET_PATTERN.search(line):
            raise WorkflowSecretPolicyError(f"{relative_path}:{line_number} has an unparseable secret reference")
        matches = SECRET_PATTERN.findall(line)
        if not matches:
            continue
        env_match = ENV_SECRET_PATTERN.match(line)
        if len(matches) != 1 or not env_match:
            raise WorkflowSecretPolicyError(
                f"{relative_path}:{line_number} secret references must be direct env assignments"
            )
        env_name, secret_name = env_match.groups()
        references.append(
            {
                "envName": env_name,
                "line": line_number,
                "secret": secret_name,
                "workflow": relative_path,
            }
        )
    return references


def validate_workflow_secret_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, Any]:
    parsed = validate_policy(policy)
    required_paths = set(parsed["requiredWorkflowPaths"])
    actual_paths = {normalize_repo_relative(path, repo_root) for path in workflow_paths(repo_root, parsed["workflowDirectory"])}
    missing_workflows = sorted(required_paths - actual_paths)
    if missing_workflows:
        raise WorkflowSecretPolicyError(f"Required workflow files are missing: {', '.join(missing_workflows)}")
    unexpected_workflows = sorted(actual_paths - required_paths)
    if unexpected_workflows:
        raise WorkflowSecretPolicyError(f"Unexpected workflow files need secret review: {', '.join(unexpected_workflows)}")

    allowed = parsed["allowedSecrets"]
    seen_allowed: set[tuple[str, str]] = set()
    references: list[dict[str, Any]] = []
    for workflow_path in workflow_paths(repo_root, parsed["workflowDirectory"]):
        relative_path = normalize_repo_relative(workflow_path, repo_root)
        for reference in find_secret_references(workflow_path, relative_path, parsed["forbiddenTokenPatterns"]):
            key = (reference["workflow"], reference["secret"])
            if key not in allowed:
                raise WorkflowSecretPolicyError(
                    f"{relative_path}:{reference['line']} uses unreviewed secret: {reference['secret']}"
                )
            if reference["envName"] not in allowed[key]["allowedEnvNames"]:
                raise WorkflowSecretPolicyError(
                    f"{relative_path}:{reference['line']} uses secret {reference['secret']} through unreviewed env name: {reference['envName']}"
                )
            seen_allowed.add(key)
            references.append(reference)

    unused_allowed = sorted(set(allowed) - seen_allowed)
    if unused_allowed:
        unused = [f"{workflow}:{secret}" for workflow, secret in unused_allowed]
        raise WorkflowSecretPolicyError(f"Allowed secrets are not used by workflows: {', '.join(unused)}")

    return {
        "allowedSecretCount": len(allowed),
        "policyKind": policy["policyKind"],
        "schemaVersion": policy["schemaVersion"],
        "secretReferenceCount": len(references),
        "status": "ok",
        "workflowCount": len(actual_paths),
        "workflows": sorted(actual_paths),
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root)
    try:
        result = validate_workflow_secret_policy(
            repo_root,
            require_object(read_json(repo_root / args.policy), "Workflow secret policy"),
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
