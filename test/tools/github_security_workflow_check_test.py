from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.github_security_workflow_check import WorkflowPolicyError, validate_workflows


REPO_ROOT = Path(__file__).resolve().parents[2]


def read_json(relative_path: str) -> dict[str, object]:
    return json.loads((REPO_ROOT / relative_path).read_text(encoding="utf-8"))


def live_policy() -> dict[str, object]:
    return read_json("docs/distribution/github-security-workflows.json")


def write_workflow(repo_root: Path, path: str, text: str) -> None:
    workflow_path = repo_root / path
    workflow_path.parent.mkdir(parents=True, exist_ok=True)
    workflow_path.write_text(text, encoding="utf-8")


class GitHubSecurityWorkflowCheckTest(unittest.TestCase):
    def test_live_policy_matches_workflows(self) -> None:
        result = validate_workflows(REPO_ROOT, live_policy())

        self.assertEqual("githubSecurityWorkflowPolicy", result["policyKind"])
        self.assertEqual(1, result["schemaVersion"])
        self.assertEqual(3, result["workflowCount"])
        self.assertEqual(
            ["Dependency Review", "OpenSSF Scorecard", "Release"],
            [workflow["name"] for workflow in result["workflows"]],
        )

    def test_rejects_missing_required_snippet(self) -> None:
        policy = {
            "schemaVersion": 1,
            "policyKind": "githubSecurityWorkflowPolicy",
            "workflows": [
                {
                    "name": "Example",
                    "path": ".github/workflows/example.yml",
                    "requiredSnippets": ["contents: read", "pull_request:"],
                    "forbiddenSnippets": [],
                }
            ],
        }

        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(repo_root, ".github/workflows/example.yml", "contents: read\n")

            with self.assertRaises(WorkflowPolicyError):
                validate_workflows(repo_root, policy)

    def test_rejects_forbidden_snippet(self) -> None:
        policy = {
            "schemaVersion": 1,
            "policyKind": "githubSecurityWorkflowPolicy",
            "workflows": [
                {
                    "name": "Example",
                    "path": ".github/workflows/example.yml",
                    "requiredSnippets": ["contents: read"],
                    "forbiddenSnippets": ["pull_request_target:"],
                }
            ],
        }

        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(
                repo_root,
                ".github/workflows/example.yml",
                "contents: read\npull_request_target:\n",
            )

            with self.assertRaises(WorkflowPolicyError):
                validate_workflows(repo_root, policy)

    def test_rejects_duplicate_workflow_name(self) -> None:
        policy = copy.deepcopy(live_policy())
        policy["workflows"][1]["name"] = policy["workflows"][0]["name"]  # type: ignore[index]

        with self.assertRaises(WorkflowPolicyError):
            validate_workflows(REPO_ROOT, policy)

    def test_rejects_duplicate_workflow_path(self) -> None:
        policy = copy.deepcopy(live_policy())
        policy["workflows"][1]["path"] = policy["workflows"][0]["path"]  # type: ignore[index]

        with self.assertRaises(WorkflowPolicyError):
            validate_workflows(REPO_ROOT, policy)

    def test_rejects_missing_workflow_file(self) -> None:
        policy = copy.deepcopy(live_policy())
        policy["workflows"][0]["path"] = ".github/workflows/missing-policy.yml"  # type: ignore[index]

        with self.assertRaises(WorkflowPolicyError):
            validate_workflows(REPO_ROOT, policy)


if __name__ == "__main__":
    unittest.main()
