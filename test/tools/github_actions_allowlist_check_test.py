from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.github_actions_allowlist_check import (
    GitHubActionsAllowlistError,
    validate_github_actions_allowlist,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


def read_json(relative_path: str) -> dict[str, object]:
    return json.loads((REPO_ROOT / relative_path).read_text(encoding="utf-8"))


def live_policy() -> dict[str, object]:
    return read_json("docs/distribution/github-actions-allowlist.json")


def fixture_policy() -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "policyKind": "githubActionsAllowlist",
        "workflowDirectory": ".github/workflows",
        "requiredWorkflowPaths": [".github/workflows/example.yml"],
        "allowedActions": ["actions/checkout@v4"],
        "forbiddenRefs": ["latest", "main", "master"],
    }


def write_workflow(repo_root: Path, path: str, text: str) -> None:
    workflow_path = repo_root / path
    workflow_path.parent.mkdir(parents=True, exist_ok=True)
    workflow_path.write_text(text, encoding="utf-8")


class GitHubActionsAllowlistCheckTest(unittest.TestCase):
    def test_live_github_actions_allowlist_matches_workflows(self) -> None:
        result = validate_github_actions_allowlist(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual(5, result["workflowCount"])
        self.assertEqual(10, result["allowedActionCount"])
        self.assertGreaterEqual(result["actionReferenceCount"], 20)

    def test_rejects_unreviewed_action(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(
                repo_root,
                ".github/workflows/example.yml",
                "jobs:\n  test:\n    steps:\n      - uses: evil/action@v1\n",
            )

            with self.assertRaises(GitHubActionsAllowlistError):
                validate_github_actions_allowlist(repo_root, fixture_policy())

    def test_rejects_floating_ref(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(
                repo_root,
                ".github/workflows/example.yml",
                "jobs:\n  test:\n    steps:\n      - uses: actions/checkout@main\n",
            )
            policy = fixture_policy()
            policy["allowedActions"] = ["actions/checkout@main"]

            with self.assertRaises(GitHubActionsAllowlistError):
                validate_github_actions_allowlist(repo_root, policy)

    def test_rejects_unpinned_action_reference(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(
                repo_root,
                ".github/workflows/example.yml",
                "jobs:\n  test:\n    steps:\n      - uses: actions/checkout\n",
            )

            with self.assertRaises(GitHubActionsAllowlistError):
                validate_github_actions_allowlist(repo_root, fixture_policy())

    def test_rejects_missing_required_workflow(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            (repo_root / ".github" / "workflows").mkdir(parents=True)

            with self.assertRaises(GitHubActionsAllowlistError):
                validate_github_actions_allowlist(repo_root, fixture_policy())

    def test_rejects_unused_allowlist_entry(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(
                repo_root,
                ".github/workflows/example.yml",
                "jobs:\n  test:\n    steps:\n      - uses: actions/checkout@v4\n",
            )
            policy = copy.deepcopy(fixture_policy())
            policy["allowedActions"] = ["actions/checkout@v4", "actions/cache@v4"]

            with self.assertRaises(GitHubActionsAllowlistError):
                validate_github_actions_allowlist(repo_root, policy)


if __name__ == "__main__":
    unittest.main()
