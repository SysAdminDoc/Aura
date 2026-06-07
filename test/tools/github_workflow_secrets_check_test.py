from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.github_workflow_secrets_check import (
    WorkflowSecretPolicyError,
    validate_workflow_secret_policy,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


def read_json(relative_path: str) -> dict[str, object]:
    return json.loads((REPO_ROOT / relative_path).read_text(encoding="utf-8"))


def live_policy() -> dict[str, object]:
    return read_json("docs/distribution/github-workflow-secrets.json")


def fixture_policy() -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "policyKind": "githubWorkflowSecretPolicy",
        "workflowDirectory": ".github/workflows",
        "requiredWorkflowPaths": [".github/workflows/example.yml"],
        "forbiddenTokenPatterns": ["GITHUB_TOKEN", "github.token", "secrets.GITHUB_TOKEN"],
        "allowedSecrets": [
            {
                "secret": "AURA_EXAMPLE_SECRET",
                "workflow": ".github/workflows/example.yml",
                "allowedEnvNames": ["AURA_EXAMPLE_SECRET"],
                "purpose": "Fixture release signing secret.",
            }
        ],
    }


def write_workflow(repo_root: Path, path: str, text: str) -> None:
    workflow_path = repo_root / path
    workflow_path.parent.mkdir(parents=True, exist_ok=True)
    workflow_path.write_text(text, encoding="utf-8")


def example_workflow(secret_name: str = "AURA_EXAMPLE_SECRET", env_name: str = "AURA_EXAMPLE_SECRET") -> str:
    return (
        "name: Example\n\n"
        "on:\n"
        "  workflow_dispatch:\n\n"
        "jobs:\n"
        "  example:\n"
        "    runs-on: ubuntu-latest\n"
        "    steps:\n"
        "      - name: Use secret\n"
        "        env:\n"
        f"          {env_name}: ${{{{ secrets.{secret_name} }}}}\n"
        "        run: echo ok\n"
    )


class GitHubWorkflowSecretsCheckTest(unittest.TestCase):
    def test_live_workflow_secret_policy_matches_workflows(self) -> None:
        result = validate_workflow_secret_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual("githubWorkflowSecretPolicy", result["policyKind"])
        self.assertEqual(5, result["workflowCount"])
        self.assertEqual(4, result["allowedSecretCount"])
        self.assertEqual(8, result["secretReferenceCount"])

    def test_rejects_unreviewed_secret(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(repo_root, ".github/workflows/example.yml", example_workflow(secret_name="AURA_OTHER_SECRET"))

            with self.assertRaises(WorkflowSecretPolicyError):
                validate_workflow_secret_policy(repo_root, fixture_policy())

    def test_rejects_forbidden_github_token_reference(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(
                repo_root,
                ".github/workflows/example.yml",
                example_workflow() + "      - run: echo $GITHUB_TOKEN\n",
            )

            with self.assertRaises(WorkflowSecretPolicyError):
                validate_workflow_secret_policy(repo_root, fixture_policy())

    def test_rejects_unreviewed_env_alias(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(
                repo_root,
                ".github/workflows/example.yml",
                example_workflow(env_name="UNREVIEWED_ALIAS"),
            )

            with self.assertRaises(WorkflowSecretPolicyError):
                validate_workflow_secret_policy(repo_root, fixture_policy())

    def test_rejects_missing_required_workflow(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            (repo_root / ".github" / "workflows").mkdir(parents=True)

            with self.assertRaises(WorkflowSecretPolicyError):
                validate_workflow_secret_policy(repo_root, fixture_policy())

    def test_rejects_unused_allowed_secret(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            write_workflow(repo_root, ".github/workflows/example.yml", example_workflow())
            policy = copy.deepcopy(fixture_policy())
            policy["allowedSecrets"].append(  # type: ignore[index, union-attr]
                {
                    "secret": "AURA_UNUSED_SECRET",
                    "workflow": ".github/workflows/example.yml",
                    "allowedEnvNames": ["AURA_UNUSED_SECRET"],
                    "purpose": "Unused fixture secret.",
                }
            )

            with self.assertRaises(WorkflowSecretPolicyError):
                validate_workflow_secret_policy(repo_root, policy)


if __name__ == "__main__":
    unittest.main()
