from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.provider_credential_release_check import (
    ProviderCredentialReleaseError,
    validate_provider_credentials,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


class ProviderCredentialReleaseCheckTest(unittest.TestCase):
    def test_live_release_provider_credential_policy_passes_without_local_properties(self) -> None:
        result = validate_provider_credentials(
            REPO_ROOT / "app" / "build.gradle.kts",
            REPO_ROOT / ".github" / "workflows" / "release.yml",
            None,
        )

        self.assertEqual("ok", result["status"])
        self.assertEqual(5, len(result["buildConfigProviderKeys"]))
        self.assertEqual(5, len(result["releaseWorkflowBlankProviderKeys"]))

    def test_rejects_nonblank_local_provider_keys(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            local_properties = Path(tmpdir) / "local.properties"
            write(local_properties, "pexels.api.key=sentinel-provider-key\npixabay.api.key=\n")

            with self.assertRaises(ProviderCredentialReleaseError):
                validate_provider_credentials(
                    REPO_ROOT / "app" / "build.gradle.kts",
                    REPO_ROOT / ".github" / "workflows" / "release.yml",
                    local_properties,
                )

    def test_allows_nonblank_local_provider_keys_with_explicit_override_warning(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            local_properties = Path(tmpdir) / "local.properties"
            write(local_properties, "stability.ai.key=sentinel-provider-key\n")

            result = validate_provider_credentials(
                REPO_ROOT / "app" / "build.gradle.kts",
                REPO_ROOT / ".github" / "workflows" / "release.yml",
                local_properties,
                allow_nonblank=True,
            )

            self.assertEqual("warning", result["status"])
            self.assertEqual(["stability.ai.key"], result["localProperties"]["nonblankProviderKeys"])

    def test_rejects_missing_blank_release_workflow_assignment(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            app_gradle = repo / "app" / "build.gradle.kts"
            release_workflow = repo / ".github" / "workflows" / "release.yml"
            write(app_gradle, (REPO_ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8"))
            workflow_text = (REPO_ROOT / ".github" / "workflows" / "release.yml").read_text(encoding="utf-8")
            write(release_workflow, workflow_text.replace("            printf 'pexels.api.key=\\n'\n", ""))

            with self.assertRaises(ProviderCredentialReleaseError):
                validate_provider_credentials(app_gradle, release_workflow, None)

    def test_rejects_nonblank_build_config_default(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            app_gradle = repo / "app" / "build.gradle.kts"
            release_workflow = repo / ".github" / "workflows" / "release.yml"
            gradle_text = (REPO_ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
            write(
                app_gradle,
                gradle_text.replace(
                    'localProps.getProperty("pexels.api.key", "")',
                    'localProps.getProperty("pexels.api.key", "sentinel")',
                ),
            )
            write(release_workflow, (REPO_ROOT / ".github" / "workflows" / "release.yml").read_text(encoding="utf-8"))

            with self.assertRaises(ProviderCredentialReleaseError):
                validate_provider_credentials(app_gradle, release_workflow, None)


if __name__ == "__main__":
    unittest.main()
