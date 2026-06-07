from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.community_guidelines_consent_check import (
    CommunityGuidelinesConsentError,
    REQUIRED_CODE_MARKERS,
    validate,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


class CommunityGuidelinesConsentCheckTest(unittest.TestCase):
    def test_live_community_guidelines_consent_passes(self) -> None:
        result = validate(
            REPO_ROOT,
            "docs/legal/community-guidelines.md",
            "docs/distribution/play-app-content.json",
        )

        self.assertEqual("ok", result["status"])
        self.assertEqual("communityGuidelinesConsent", result["policyKind"])
        self.assertGreaterEqual(result["checkedCodeSurfaceCount"], 8)

    def test_rejects_missing_settings_prompt_marker(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            temp_repo = Path(tmpdir) / "Aura"
            for relative_path in [
                "docs/legal/community-guidelines.md",
                "docs/distribution/play-app-content.json",
                *REQUIRED_CODE_MARKERS,
            ]:
                source = REPO_ROOT / relative_path
                target = temp_repo / relative_path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
            settings_path = temp_repo / "app/src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt"
            settings_path.write_text(
                settings_path.read_text(encoding="utf-8").replace("Community guidelines", "Community rules"),
                encoding="utf-8",
            )

            with self.assertRaises(CommunityGuidelinesConsentError):
                validate(
                    temp_repo,
                    "docs/legal/community-guidelines.md",
                    "docs/distribution/play-app-content.json",
                )


if __name__ == "__main__":
    unittest.main()
