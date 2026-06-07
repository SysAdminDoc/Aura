from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.dependabot_config_check import DependabotConfigError, validate_dependabot_config


REPO_ROOT = Path(__file__).resolve().parents[2]
LIVE_CONFIG = REPO_ROOT / ".github" / "dependabot.yml"


def live_config_text() -> str:
    return LIVE_CONFIG.read_text(encoding="utf-8")


def write_config(text: str) -> tuple[tempfile.TemporaryDirectory[str], Path]:
    tmpdir = tempfile.TemporaryDirectory()
    config = Path(tmpdir.name) / "dependabot.yml"
    config.write_text(text, encoding="utf-8")
    return tmpdir, config


class DependabotConfigCheckTest(unittest.TestCase):
    def test_live_dependabot_config_matches_expected_surfaces(self) -> None:
        result = validate_dependabot_config(LIVE_CONFIG)

        self.assertEqual("ok", result["status"])
        self.assertEqual(4, result["updateCount"])
        self.assertEqual(
            [
                ("github-actions", "/"),
                ("gradle", "/"),
                ("npm", "/"),
                ("npm", "/functions"),
            ],
            [
                (entry["packageEcosystem"], entry["directory"])
                for entry in result["updates"]
            ],
        )

    def test_rejects_unexpected_update_surface(self) -> None:
        text = live_config_text().replace('directory: "/functions"', 'directory: "/unknown"', 1)
        tmpdir, config = write_config(text)
        self.addCleanup(tmpdir.cleanup)

        with self.assertRaises(DependabotConfigError):
            validate_dependabot_config(config)

    def test_rejects_duplicate_update_surface(self) -> None:
        text = live_config_text().replace('directory: "/functions"', 'directory: "/"', 1)
        tmpdir, config = write_config(text)
        self.addCleanup(tmpdir.cleanup)

        with self.assertRaises(DependabotConfigError):
            validate_dependabot_config(config)

    def test_rejects_schedule_drift(self) -> None:
        text = live_config_text().replace('interval: "weekly"', 'interval: "daily"', 1)
        tmpdir, config = write_config(text)
        self.addCleanup(tmpdir.cleanup)

        with self.assertRaises(DependabotConfigError):
            validate_dependabot_config(config)

    def test_rejects_missing_required_label(self) -> None:
        text = live_config_text().replace('      - "security"\n', "", 1)
        tmpdir, config = write_config(text)
        self.addCleanup(tmpdir.cleanup)

        with self.assertRaises(DependabotConfigError):
            validate_dependabot_config(config)

    def test_rejects_excessive_open_pull_request_limit(self) -> None:
        text = live_config_text().replace("open-pull-requests-limit: 5", "open-pull-requests-limit: 8", 1)
        tmpdir, config = write_config(text)
        self.addCleanup(tmpdir.cleanup)

        with self.assertRaises(DependabotConfigError):
            validate_dependabot_config(config)


if __name__ == "__main__":
    unittest.main()
