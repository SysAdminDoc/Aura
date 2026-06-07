#!/usr/bin/env python3
"""Validate GitHub security workflow policy snippets."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


class WorkflowPolicyError(ValueError):
    """Raised when GitHub workflow security policy validation fails."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura GitHub security workflow policy.")
    parser.add_argument("--policy", default="docs/distribution/github-security-workflows.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise WorkflowPolicyError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise WorkflowPolicyError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list):
        raise WorkflowPolicyError(f"{label} must be a list")
    values: list[str] = []
    for index, item in enumerate(value):
        values.append(require_string(item, f"{label}[{index}]"))
    if len(values) != len(set(values)):
        raise WorkflowPolicyError(f"{label} contains duplicate values")
    return values


def validate_policy_metadata(policy: dict[str, Any]) -> list[dict[str, Any]]:
    if policy.get("schemaVersion") != 1:
        raise WorkflowPolicyError("Workflow policy schemaVersion must be 1")
    if policy.get("policyKind") != "githubSecurityWorkflowPolicy":
        raise WorkflowPolicyError("Workflow policy policyKind is invalid")
    workflows = policy.get("workflows")
    if not isinstance(workflows, list) or not workflows:
        raise WorkflowPolicyError("Workflow policy workflows must be a non-empty list")

    seen_names: set[str] = set()
    seen_paths: set[str] = set()
    validated: list[dict[str, Any]] = []
    for index, raw_workflow in enumerate(workflows):
        workflow = require_object(raw_workflow, f"workflows[{index}]")
        name = require_string(workflow.get("name"), f"workflows[{index}].name")
        path = require_string(workflow.get("path"), f"{name}.path")
        if name in seen_names:
            raise WorkflowPolicyError(f"Duplicate workflow name: {name}")
        if path in seen_paths:
            raise WorkflowPolicyError(f"Duplicate workflow path: {path}")
        if Path(path).is_absolute() or ".." in Path(path).parts:
            raise WorkflowPolicyError(f"{name} path must stay inside the repository")
        seen_names.add(name)
        seen_paths.add(path)
        required = require_string_list(workflow.get("requiredSnippets"), f"{name}.requiredSnippets")
        forbidden = require_string_list(workflow.get("forbiddenSnippets", []), f"{name}.forbiddenSnippets")
        validated.append(
            {
                "name": name,
                "path": path,
                "requiredSnippets": required,
                "forbiddenSnippets": forbidden,
            }
        )
    return validated


def validate_workflows(repo_root: Path, policy: dict[str, Any]) -> dict[str, Any]:
    workflows = validate_policy_metadata(policy)
    results: list[dict[str, Any]] = []
    for workflow in workflows:
        workflow_path = repo_root / workflow["path"]
        if not workflow_path.is_file():
            raise WorkflowPolicyError(f"{workflow['name']} workflow file is missing: {workflow['path']}")
        text = workflow_path.read_text(encoding="utf-8")
        missing = [snippet for snippet in workflow["requiredSnippets"] if snippet not in text]
        if missing:
            raise WorkflowPolicyError(
                f"{workflow['name']} workflow missing required snippets: {', '.join(missing)}"
            )
        present_forbidden = [snippet for snippet in workflow["forbiddenSnippets"] if snippet in text]
        if present_forbidden:
            raise WorkflowPolicyError(
                f"{workflow['name']} workflow contains forbidden snippets: {', '.join(present_forbidden)}"
            )
        results.append(
            {
                "name": workflow["name"],
                "path": workflow["path"],
                "requiredSnippetCount": len(workflow["requiredSnippets"]),
                "forbiddenSnippetCount": len(workflow["forbiddenSnippets"]),
            }
        )
    return {
        "policyKind": policy["policyKind"],
        "schemaVersion": policy["schemaVersion"],
        "workflowCount": len(results),
        "workflows": results,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root)
    try:
        policy = require_object(read_json(repo_root / args.policy), "Workflow policy")
        result = validate_workflows(repo_root, policy)
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
