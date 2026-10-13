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
            None,
            None,
        )

        self.assertEqual("ok", result["status"])
        self.assertEqual(3, len(result["buildConfigProviderKeys"]))
        self.assertEqual([], result["releaseWorkflowBlankProviderKeys"])
        self.assertEqual("local-only release; no workflow", result["releaseWorkflow"])

    def test_allows_nonblank_local_provider_keys_for_debug_builds(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            local_properties = Path(tmpdir) / "local.properties"
            write(local_properties, "pexels.api.key=sentinel-provider-key\npixabay.api.key=\n")

            result = validate_provider_credentials(
                REPO_ROOT / "app" / "build.gradle.kts",
                None,
                local_properties,
            )

            self.assertEqual("ok", result["status"])
            self.assertEqual("debugOnly", result["localProperties"]["status"])
            self.assertEqual(["pexels.api.key"], result["localProperties"]["nonblankProviderKeys"])

    def test_allows_nonblank_local_provider_keys_with_explicit_override_warning(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            local_properties = Path(tmpdir) / "local.properties"
            write(local_properties, "stability.ai.key=sentinel-provider-key\n")

            result = validate_provider_credentials(
                REPO_ROOT / "app" / "build.gradle.kts",
                None,
                local_properties,
                allow_nonblank=True,
            )

            self.assertEqual("ok", result["status"])
            self.assertEqual("debugOnly", result["localProperties"]["status"])
            self.assertEqual(["stability.ai.key"], result["localProperties"]["nonblankProviderKeys"])

    def test_rejects_missing_blank_release_workflow_assignment(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            app_gradle = repo / "app" / "build.gradle.kts"
            release_workflow = repo / ".github" / "workflows" / "release.yml"
            write(app_gradle, (REPO_ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8"))
            workflow_text = "\n".join(
                f"            printf '{key}=\\n'"
                for key in (
                    "pixabay.api.key",
                    "stability.ai.key",
                )
            )
            write(release_workflow, workflow_text)

            with self.assertRaises(ProviderCredentialReleaseError):
                validate_provider_credentials(app_gradle, release_workflow, None)

    def test_rejects_release_build_that_inherits_local_provider_key(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            app_gradle = repo / "app" / "build.gradle.kts"
            release_workflow = repo / ".github" / "workflows" / "release.yml"
            gradle_text = (REPO_ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
            write(
                app_gradle,
                gradle_text.replace(
                    'buildConfigField("String", "PEXELS_API_KEY", "\\"\\"")',
                    'buildConfigField("String", "PEXELS_API_KEY", "\\"${localProps.getProperty(\\"pexels.api.key\\", \\"\\")}\\"")',
                    1,
                ),
            )
            with self.assertRaises(ProviderCredentialReleaseError):
                validate_provider_credentials(app_gradle, None, None)

    def test_rejects_missing_full_release_stability_override(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            app_gradle = Path(tmpdir) / "app" / "build.gradle.kts"
            gradle_text = (REPO_ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
            write(
                app_gradle,
                gradle_text.replace(
                    '.withFlavor("distribution" to "full")',
                    '.withFlavor("distribution" to "foss")',
                ),
            )

            with self.assertRaises(ProviderCredentialReleaseError):
                validate_provider_credentials(app_gradle, None, None)


if __name__ == "__main__":
    unittest.main()
