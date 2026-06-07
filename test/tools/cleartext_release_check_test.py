from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.cleartext_release_check import CleartextReleaseError, validate_cleartext_release_policy


REPO_ROOT = Path(__file__).resolve().parents[2]


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


class CleartextReleaseCheckTest(unittest.TestCase):
    def test_live_release_cleartext_policy_passes(self) -> None:
        result = validate_cleartext_release_policy(
            REPO_ROOT,
            "app/src/main/res/xml/network_security_config.xml",
            "app/src/main/AndroidManifest.xml",
        )

        self.assertEqual("ok", result["status"])
        self.assertEqual(0, result["cleartextReferences"])
        self.assertGreater(result["checkedProviderSourceFiles"], 0)

    def test_rejects_network_security_config_cleartext_exception(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            write(
                repo / "app/src/main/res/xml/network_security_config.xml",
                '<network-security-config><domain-config cleartextTrafficPermitted="true" /></network-security-config>',
            )

            with self.assertRaises(CleartextReleaseError):
                validate_cleartext_release_policy(
                    repo,
                    "app/src/main/res/xml/network_security_config.xml",
                    "app/src/main/AndroidManifest.xml",
                    ["app/src/main/java"],
                )

    def test_rejects_manifest_cleartext_true(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            write(
                repo / "app/src/main/AndroidManifest.xml",
                '<manifest><application android:usesCleartextTraffic="true" /></manifest>',
            )

            with self.assertRaises(CleartextReleaseError):
                validate_cleartext_release_policy(
                    repo,
                    "app/src/main/res/xml/network_security_config.xml",
                    "app/src/main/AndroidManifest.xml",
                    ["app/src/main/java"],
                )

    def test_rejects_provider_http_url_literal(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            write(repo / "app/src/main/java/Example.kt", 'const val BASE_URL = "http://ccmixter.org/api/"\n')

            with self.assertRaises(CleartextReleaseError):
                validate_cleartext_release_policy(
                    repo,
                    "app/src/main/res/xml/network_security_config.xml",
                    "app/src/main/AndroidManifest.xml",
                    ["app/src/main/java"],
                )

    def test_rejects_provider_http_scheme_builder(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            write(repo / "app/src/main/java/Example.kt", 'val url = HttpUrl.Builder().scheme("http").build()\n')

            with self.assertRaises(CleartextReleaseError):
                validate_cleartext_release_policy(
                    repo,
                    "app/src/main/res/xml/network_security_config.xml",
                    "app/src/main/AndroidManifest.xml",
                    ["app/src/main/java"],
                )


def seed_repo(repo: Path) -> Path:
    write(repo / "app/src/main/res/xml/network_security_config.xml", "<network-security-config />\n")
    write(repo / "app/src/main/AndroidManifest.xml", "<manifest><application /></manifest>\n")
    write(repo / "app/src/main/java/Example.kt", 'const val BASE_URL = "https://ccmixter.org/"\n')
    return repo


if __name__ == "__main__":
    unittest.main()
