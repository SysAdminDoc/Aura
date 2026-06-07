#!/usr/bin/env python3
"""Validate GitHub workflow events and permissions against a reviewed policy."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


TOP_LEVEL_KEY_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_-]*:")
KEY_VALUE_PATTERN = re.compile(r"^\s*([A-Za-z0-9_-]+):\s*([A-Za-z0-9_-]+)\s*(?:#.*)?$")
KEY_HEADER_PATTERN = re.compile(r"^(\s*)([A-Za-z0-9_-]+):\s*(?:#.*)?$")
ALLOWED_PERMISSION_VALUES = {"none", "read", "write"}


class WorkflowPermissionsError(ValueError):
    """Raised when GitHub workflow permissions validation fails."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura GitHub workflow permissions policy.")
    parser.add_argument("--policy", default="docs/distribution/github-workflow-permissions.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise WorkflowPermissionsError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise WorkflowPermissionsError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise WorkflowPermissionsError(f"{label} must be a non-empty list")
    values: list[str] = []
    for index, item in enumerate(value):
        values.append(require_string(item, f"{label}[{index}]"))
    if len(values) != len(set(values)):
        raise WorkflowPermissionsError(f"{label} contains duplicate values")
    return values


def normalize_repo_relative(path: Path, repo_root: Path) -> str:
    return path.relative_to(repo_root).as_posix()


def workflow_paths(repo_root: Path, workflow_directory: str) -> list[Path]:
    directory = repo_root / workflow_directory
    if not directory.is_dir():
        raise WorkflowPermissionsError(f"Workflow directory is missing: {workflow_directory}")
    return sorted(
        path
        for path in directory.iterdir()
        if path.is_file() and path.suffix.lower() in {".yml", ".yaml"}
    )


def count_indent(line: str) -> int:
    if "\t" in line:
        raise WorkflowPermissionsError("Workflow YAML must use spaces for indentation")
    return len(line) - len(line.lstrip(" "))


def extract_top_level_block(lines: list[str], key: str, path: str) -> tuple[str, list[str]]:
    prefix = f"{key}:"
    for index, line in enumerate(lines):
        if line.startswith(prefix):
            rest = line.split(":", 1)[1].strip()
            block: list[str] = []
            for candidate in lines[index + 1 :]:
                if candidate and not candidate.startswith(" ") and TOP_LEVEL_KEY_PATTERN.match(candidate):
                    break
                block.append(candidate)
            return rest, block
    raise WorkflowPermissionsError(f"{path} is missing top-level {key}: block")


def parse_inline_event_value(value: str, path: str) -> list[str]:
    if value.startswith("[") and value.endswith("]"):
        events = [item.strip().strip("'\"") for item in value[1:-1].split(",") if item.strip()]
    else:
        events = [value.strip().strip("'\"")]
    if not events or any(not event for event in events):
        raise WorkflowPermissionsError(f"{path} has an invalid on: event declaration")
    return events


def parse_events(lines: list[str], path: str) -> list[str]:
    rest, block = extract_top_level_block(lines, "on", path)
    if rest:
        return sorted(set(parse_inline_event_value(rest, path)))

    events: list[str] = []
    for line in block:
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if count_indent(line) != 2:
            continue
        match = KEY_HEADER_PATTERN.match(line)
        if match:
            events.append(match.group(2))
    if not events:
        raise WorkflowPermissionsError(f"{path} has no parseable workflow events")
    if len(events) != len(set(events)):
        raise WorkflowPermissionsError(f"{path} declares duplicate workflow events")
    return sorted(events)


def parse_permission_map_from_lines(
    lines: list[str],
    header_index: int,
    header_indent: int,
    label: str,
) -> dict[str, str]:
    header = lines[header_index]
    rest = header.split(":", 1)[1].strip()
    if rest:
        raise WorkflowPermissionsError(f"{label} permissions must be an explicit map, not {rest}")

    permissions: dict[str, str] = {}
    for line in lines[header_index + 1 :]:
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        indent = count_indent(line)
        if indent <= header_indent:
            break
        if indent != header_indent + 2:
            raise WorkflowPermissionsError(f"{label} permissions contain unsupported nested YAML")
        match = KEY_VALUE_PATTERN.match(line)
        if not match:
            raise WorkflowPermissionsError(f"{label} permissions contain an invalid row: {line.strip()}")
        name, value = match.groups()
        if value not in ALLOWED_PERMISSION_VALUES:
            raise WorkflowPermissionsError(f"{label} permission {name} uses invalid value: {value}")
        if name in permissions:
            raise WorkflowPermissionsError(f"{label} permissions contain duplicate key: {name}")
        permissions[name] = value
    if not permissions:
        raise WorkflowPermissionsError(f"{label} permissions map must not be empty")
    return dict(sorted(permissions.items()))


def parse_top_level_permissions(lines: list[str], path: str) -> dict[str, str] | None:
    for index, line in enumerate(lines):
        if line.startswith("permissions:"):
            return parse_permission_map_from_lines(lines, index, 0, f"{path} top-level")
    return None


def parse_jobs(lines: list[str], path: str) -> dict[str, dict[str, str] | None]:
    _rest, block = extract_top_level_block(lines, "jobs", path)
    job_blocks: dict[str, list[str]] = {}
    current_job: str | None = None

    for line in block:
        match = KEY_HEADER_PATTERN.match(line)
        if match and len(match.group(1)) == 2:
            current_job = match.group(2)
            if current_job in job_blocks:
                raise WorkflowPermissionsError(f"{path} declares duplicate job: {current_job}")
            job_blocks[current_job] = []
            continue
        if current_job is not None:
            job_blocks[current_job].append(line)

    if not job_blocks:
        raise WorkflowPermissionsError(f"{path} has no parseable jobs")

    job_permissions: dict[str, dict[str, str] | None] = {}
    for job_name, job_lines in job_blocks.items():
        permission_indexes = [
            index
            for index, line in enumerate(job_lines)
            if line.startswith("    permissions:")
        ]
        if len(permission_indexes) > 1:
            raise WorkflowPermissionsError(f"{path} job {job_name} declares permissions more than once")
        if not permission_indexes:
            job_permissions[job_name] = None
            continue
        job_permissions[job_name] = parse_permission_map_from_lines(
            job_lines,
            permission_indexes[0],
            4,
            f"{path} job {job_name}",
        )
    return job_permissions


def normalize_permissions(value: Any, label: str) -> dict[str, str] | None:
    if value is None:
        return None
    permissions = require_object(value, label)
    normalized: dict[str, str] = {}
    for name, raw_value in permissions.items():
        key = require_string(name, f"{label} permission key")
        permission_value = require_string(raw_value, f"{label}.{key}")
        if permission_value not in ALLOWED_PERMISSION_VALUES:
            raise WorkflowPermissionsError(f"{label}.{key} uses invalid permission value: {permission_value}")
        normalized[key] = permission_value
    if not normalized:
        raise WorkflowPermissionsError(f"{label} must not be an empty permissions map")
    return dict(sorted(normalized.items()))


def validate_policy(policy: dict[str, Any]) -> dict[str, Any]:
    if policy.get("schemaVersion") != 1:
        raise WorkflowPermissionsError("Workflow permissions policy schemaVersion must be 1")
    if policy.get("policyKind") != "githubWorkflowPermissionsPolicy":
        raise WorkflowPermissionsError("Workflow permissions policyKind is invalid")
    workflow_directory = require_string(policy.get("workflowDirectory"), "workflowDirectory")
    if Path(workflow_directory).is_absolute() or ".." in Path(workflow_directory).parts:
        raise WorkflowPermissionsError("workflowDirectory must stay inside the repository")
    required_workflows = require_string_list(policy.get("requiredWorkflowPaths"), "requiredWorkflowPaths")

    raw_workflows = policy.get("workflows")
    if not isinstance(raw_workflows, list) or not raw_workflows:
        raise WorkflowPermissionsError("workflows must be a non-empty list")

    workflows: list[dict[str, Any]] = []
    seen_names: set[str] = set()
    seen_paths: set[str] = set()
    for index, raw_workflow in enumerate(raw_workflows):
        workflow = require_object(raw_workflow, f"workflows[{index}]")
        name = require_string(workflow.get("name"), f"workflows[{index}].name")
        path = require_string(workflow.get("path"), f"{name}.path")
        if Path(path).is_absolute() or ".." in Path(path).parts:
            raise WorkflowPermissionsError(f"{name} path must stay inside the repository")
        if name in seen_names:
            raise WorkflowPermissionsError(f"Duplicate workflow name: {name}")
        if path in seen_paths:
            raise WorkflowPermissionsError(f"Duplicate workflow path: {path}")
        allowed_events = require_string_list(workflow.get("allowedEvents"), f"{name}.allowedEvents")
        if "topLevelPermissions" not in workflow:
            raise WorkflowPermissionsError(f"{name}.topLevelPermissions must be explicit")
        jobs = require_object(workflow.get("jobs"), f"{name}.jobs")
        if not jobs:
            raise WorkflowPermissionsError(f"{name}.jobs must not be empty")
        normalized_jobs = {
            require_string(job_name, f"{name}.jobs key"): normalize_permissions(job_permissions, f"{name}.{job_name}")
            for job_name, job_permissions in jobs.items()
        }
        workflows.append(
            {
                "name": name,
                "path": path,
                "allowedEvents": sorted(allowed_events),
                "topLevelPermissions": normalize_permissions(workflow.get("topLevelPermissions"), f"{name}.topLevelPermissions"),
                "jobs": normalized_jobs,
            }
        )
        seen_names.add(name)
        seen_paths.add(path)

    if set(required_workflows) != seen_paths:
        raise WorkflowPermissionsError("requiredWorkflowPaths must match policy workflow paths")

    return {
        "workflowDirectory": workflow_directory,
        "requiredWorkflowPaths": required_workflows,
        "workflows": workflows,
    }


def compare_permissions(
    actual: dict[str, str] | None,
    expected: dict[str, str] | None,
    label: str,
) -> None:
    if actual != expected:
        raise WorkflowPermissionsError(f"{label} permissions drifted: expected {expected}, got {actual}")


def validate_workflow_permissions_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, Any]:
    parsed = validate_policy(policy)
    required_paths = set(parsed["requiredWorkflowPaths"])
    actual_paths = {normalize_repo_relative(path, repo_root) for path in workflow_paths(repo_root, parsed["workflowDirectory"])}
    missing_workflows = sorted(required_paths - actual_paths)
    if missing_workflows:
        raise WorkflowPermissionsError(f"Required workflow files are missing: {', '.join(missing_workflows)}")
    unexpected_workflows = sorted(actual_paths - required_paths)
    if unexpected_workflows:
        raise WorkflowPermissionsError(f"Unexpected workflow files need permissions review: {', '.join(unexpected_workflows)}")

    results: list[dict[str, Any]] = []
    job_count = 0
    for workflow in parsed["workflows"]:
        workflow_path = repo_root / workflow["path"]
        lines = workflow_path.read_text(encoding="utf-8").splitlines()
        actual_events = parse_events(lines, workflow["path"])
        if actual_events != workflow["allowedEvents"]:
            raise WorkflowPermissionsError(
                f"{workflow['name']} events drifted: expected {workflow['allowedEvents']}, got {actual_events}"
            )

        compare_permissions(
            parse_top_level_permissions(lines, workflow["path"]),
            workflow["topLevelPermissions"],
            f"{workflow['name']} top-level",
        )

        actual_jobs = parse_jobs(lines, workflow["path"])
        expected_jobs = workflow["jobs"]
        missing_jobs = sorted(set(expected_jobs) - set(actual_jobs))
        if missing_jobs:
            raise WorkflowPermissionsError(f"{workflow['name']} jobs are missing: {', '.join(missing_jobs)}")
        unexpected_jobs = sorted(set(actual_jobs) - set(expected_jobs))
        if unexpected_jobs:
            raise WorkflowPermissionsError(f"{workflow['name']} has unexpected jobs: {', '.join(unexpected_jobs)}")

        for job_name, expected_permissions in expected_jobs.items():
            compare_permissions(actual_jobs[job_name], expected_permissions, f"{workflow['name']} job {job_name}")
        job_count += len(actual_jobs)
        results.append(
            {
                "allowedEvents": actual_events,
                "jobCount": len(actual_jobs),
                "name": workflow["name"],
                "path": workflow["path"],
            }
        )

    return {
        "jobCount": job_count,
        "policyKind": policy["policyKind"],
        "schemaVersion": policy["schemaVersion"],
        "status": "ok",
        "workflowCount": len(results),
        "workflows": results,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root)
    try:
        result = validate_workflow_permissions_policy(
            repo_root,
            require_object(read_json(repo_root / args.policy), "Workflow permissions policy"),
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
