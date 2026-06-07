from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.github_workflow_permissions_check import (
    WorkflowPermissionsError,
    validate_workflow_permissions_policy,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


def read_json(relative_path: str) -> dict[str, object]:
    return json.loads((REPO_ROOT / relative_path).read_text(encoding="utf-8"))


def live_policy() -> dict[str, object]:
    return read_json("docs/distribution/github-workflow-permissions.json")


def fixture_policy() -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "policyKind": "githubWorkflowPermissionsPolicy",
        "workflowDirectory": ".github/workflows",
        "requiredWorkflowPaths": [".github/workflows/example.yml"],
        "workflows": [
            {
                "name": "Example",
                "path": ".github/workflows/example.yml",
                "allowedEvents": ["pull_request"],
                "topLevelPermissions": None,
                "jobs": {"example": {"contents": "read"}},
            }
        ],
    }


def write_workflow(repo_root: Path, path: str, text: str) -> None:
    workflow_path = repo_root / path
    workflow_path.parent.mkdir(parents=True, exist_ok=True)
    workflow_path.write_text(text, encoding="utf-8")


def example_workflow(
    event: str = "pull_request",
    job_permissions: str = "      contents: read\n",
    extra_job: bool = False,
) -> str:
    extra_job_text = ""
    if extra_job:
        extra_job_text = "  extra:\n    runs-on: ubuntu-latest\n    steps:\n      - run: echo ok\n"
    return (
        "name: Example\n\n"
        "on:\n"
        f"  {event}:\n\n"
        "jobs:\n"
        "  example:\n"
        "    runs-on: ubuntu-latest\n"
        "    permissions:\n"
        f"{job_permissions}"
        "    steps:\n"
        "      - run: echo ok\n"
        f"{extra_job_text}"
    )


class GitHubWorkflowPermissionsCheckTest(unittest.TestCase):
    def test_live_workflow_permissions_policy_matches_workflows(self) -> None:
        result = validate_workflow_permissions_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual("githubWorkflowPermissionsPolicy", result["policyKind"])
        self.assertEqual(5, result["workflowCount"])
        self.assertEqual(6, result["jobCount"])

    def test_rejects_unreviewed_event(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(repo_root, ".github/workflows/example.yml", example_workflow(event="pull_request_target"))

            with self.assertRaises(WorkflowPermissionsError):
                validate_workflow_permissions_policy(repo_root, fixture_policy())

    def test_rejects_job_permission_escalation(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(
                repo_root,
                ".github/workflows/example.yml",
                example_workflow(job_permissions="      contents: write\n"),
            )

            with self.assertRaises(WorkflowPermissionsError):
                validate_workflow_permissions_policy(repo_root, fixture_policy())

    def test_rejects_missing_expected_job_permissions(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(
                repo_root,
                ".github/workflows/example.yml",
                (
                    "name: Example\n\n"
                    "on:\n"
                    "  pull_request:\n\n"
                    "jobs:\n"
                    "  example:\n"
                    "    runs-on: ubuntu-latest\n"
                    "    steps:\n"
                    "      - run: echo ok\n"
                ),
            )

            with self.assertRaises(WorkflowPermissionsError):
                validate_workflow_permissions_policy(repo_root, fixture_policy())

    def test_rejects_unexpected_job(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(
                repo_root,
                ".github/workflows/example.yml",
                example_workflow(extra_job=True),
            )

            with self.assertRaises(WorkflowPermissionsError):
                validate_workflow_permissions_policy(repo_root, fixture_policy())

    def test_rejects_scalar_permissions(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(
                repo_root,
                ".github/workflows/example.yml",
                (
                    "name: Example\n\n"
                    "on:\n"
                    "  pull_request:\n\n"
                    "permissions: write-all\n\n"
                    "jobs:\n"
                    "  example:\n"
                    "    runs-on: ubuntu-latest\n"
                    "    permissions:\n"
                    "      contents: read\n"
                    "    steps:\n"
                    "      - run: echo ok\n"
                ),
            )
            policy = copy.deepcopy(fixture_policy())
            policy["workflows"][0]["topLevelPermissions"] = {"contents": "read"}  # type: ignore[index]

            with self.assertRaises(WorkflowPermissionsError):
                validate_workflow_permissions_policy(repo_root, policy)


if __name__ == "__main__":
    unittest.main()
