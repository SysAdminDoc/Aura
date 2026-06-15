from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.manifest_consistency_check import validate_manifest_consistency


REPO_ROOT = Path(__file__).resolve().parents[2]


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_minimal_repo(repo: Path) -> None:
    write(
        repo / "gradle/libs.versions.toml",
        """
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
compose-bom = "2024.12.01"
hilt = "2.53.1"
room = "2.7.2"
retrofit = "3.0.0"
okhttp = "5.3.2"
coil = "2.7.0"
navigation = "2.8.5"
lifecycle = "2.8.7"
coroutines = "1.9.0"
datastore = "1.1.1"
media3 = "1.5.1"
work = "2.11.2"
paging = "3.3.5"
glance = "1.1.1"
moshi = "1.15.1"
serialization = "1.7.3"
""".strip()
        + "\n",
    )
    write(
        repo / "app/build.gradle.kts",
        """
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        targetSdk = 35
        versionCode = 112
        versionName = "6.31.1"
    }
}
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.3")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("com.google.android.gms:play-services-base:18.5.0")
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
}
""".strip()
        + "\n",
    )
    write(
        repo / "functions/package.json",
        json.dumps(
            {
                "engines": {"node": "22"},
                "dependencies": {
                    "firebase-admin": "13.10.0",
                    "firebase-functions": "7.2.5",
                },
                "devDependencies": {
                    "@types/node": "22.19.21",
                    "typescript": "5.9.3",
                },
            },
        ),
    )
    write(repo / "ROADMAP.md", "# Roadmap\n\n## State of the Repo\n- Room 2.7.2\n")
    write(repo / "RESEARCH.md", "# Research\n\n## Executive Summary\nKotlin 2.1.0 and WorkManager 2.11.2 are current.\n")
    write(repo / "README.md", "# Aura\n\n## Tech Stack\n| Database | Room 2.7.2 |\n")


class ManifestConsistencyCheckTest(unittest.TestCase):
    def test_live_manifest_consistency_passes(self) -> None:
        result = validate_manifest_consistency(REPO_ROOT)

        self.assertEqual("ok", result["status"])
        self.assertEqual([], result["stale_claims"])
        self.assertEqual("6.31.1", result["manifest_versions"]["versionname"])
        self.assertEqual("22", result["manifest_versions"]["node"])

    def test_rejects_stale_readme_current_state_claim(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            write_minimal_repo(repo)
            write(repo / "README.md", "# Aura\n\n## Tech Stack\n| Database | Room 2.6.1 |\n")

            result = validate_manifest_consistency(repo)

            self.assertEqual("fail", result["status"])
            self.assertTrue(any(f["file"] == "README.md" and f["dependency"] == "Room" for f in result["stale_claims"]))

    def test_scans_local_context_doc_when_present(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            write_minimal_repo(repo)
            write(repo / "CLAUDE.md", "# Notes\n\n## Tech Stack\n- WorkManager 2.10.0\n")

            result = validate_manifest_consistency(repo)

            self.assertEqual("fail", result["status"])
            self.assertTrue(any(f["file"] == "CLAUDE.md" and f["dependency"] == "WorkManager" for f in result["stale_claims"]))

    def test_rejects_stale_callable_runtime_doc_when_present(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            write_minimal_repo(repo)
            write(
                repo / "docs/community-callable-quota-enforcement.md",
                "# Community Callable Quota Enforcement\n\nFunctions use Node 20.\n",
            )

            result = validate_manifest_consistency(repo)

            self.assertEqual("fail", result["status"])
            self.assertTrue(any(f["file"].endswith("community-callable-quota-enforcement.md") for f in result["stale_claims"]))


if __name__ == "__main__":
    unittest.main()
