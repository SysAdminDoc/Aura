from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.gradle_wrapper_check import GradleWrapperPolicyError, validate_gradle_wrapper


REPO_ROOT = Path(__file__).resolve().parents[2]
LIVE_PROPERTIES = REPO_ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"


def live_properties_text() -> str:
    return LIVE_PROPERTIES.read_text(encoding="utf-8")


def write_properties(text: str) -> tuple[tempfile.TemporaryDirectory[str], Path]:
    tmpdir = tempfile.TemporaryDirectory()
    path = Path(tmpdir.name) / "gradle-wrapper.properties"
    path.write_text(text, encoding="utf-8")
    return tmpdir, path


class GradleWrapperCheckTest(unittest.TestCase):
    def test_live_gradle_wrapper_matches_policy(self) -> None:
        result = validate_gradle_wrapper(LIVE_PROPERTIES)

        self.assertEqual("ok", result["status"])
        self.assertEqual(
            "8d97a97984f6cbd2b85fe4c60a743440a347544bf18818048e611f5288d46c94",
            result["distributionSha256Sum"],
        )

    def test_rejects_missing_distribution_sha256(self) -> None:
        text = "\n".join(
            line
            for line in live_properties_text().splitlines()
            if not line.startswith("distributionSha256Sum=")
        )
        tmpdir, path = write_properties(text)
        self.addCleanup(tmpdir.cleanup)

        with self.assertRaises(GradleWrapperPolicyError):
            validate_gradle_wrapper(path)

    def test_rejects_distribution_sha256_drift(self) -> None:
        text = live_properties_text().replace(
            "distributionSha256Sum=8d97a97984f6cbd2b85fe4c60a743440a347544bf18818048e611f5288d46c94",
            "distributionSha256Sum=0000000000000000000000000000000000000000000000000000000000000000",
        )
        tmpdir, path = write_properties(text)
        self.addCleanup(tmpdir.cleanup)

        with self.assertRaises(GradleWrapperPolicyError):
            validate_gradle_wrapper(path)

    def test_rejects_all_distribution(self) -> None:
        text = live_properties_text().replace("gradle-8.12.1-bin.zip", "gradle-8.12.1-all.zip")
        tmpdir, path = write_properties(text)
        self.addCleanup(tmpdir.cleanup)

        with self.assertRaises(GradleWrapperPolicyError):
            validate_gradle_wrapper(path)

    def test_rejects_disabled_url_validation(self) -> None:
        text = live_properties_text().replace("validateDistributionUrl=true", "validateDistributionUrl=false")
        tmpdir, path = write_properties(text)
        self.addCleanup(tmpdir.cleanup)

        with self.assertRaises(GradleWrapperPolicyError):
            validate_gradle_wrapper(path)

    def test_rejects_low_network_timeout(self) -> None:
        text = live_properties_text().replace("networkTimeout=10000", "networkTimeout=5000")
        tmpdir, path = write_properties(text)
        self.addCleanup(tmpdir.cleanup)

        with self.assertRaises(GradleWrapperPolicyError):
            validate_gradle_wrapper(path)


if __name__ == "__main__":
    unittest.main()
