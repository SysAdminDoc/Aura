from __future__ import annotations

import shutil
import tempfile
import unittest
from pathlib import Path

from tools.community_identity_laziness_check import (
    CommunityIdentityLazinessError,
    validate_community_identity_laziness,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


class CommunityIdentityLazinessCheckTest(unittest.TestCase):
    def test_live_community_identity_laziness_passes(self) -> None:
        result = validate_community_identity_laziness(REPO_ROOT)

        self.assertEqual("ok", result["status"])
        self.assertGreater(result["lazyEnsureSignedInCalls"], 0)

    def test_rejects_startup_sign_in(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            app = repo / "app/src/main/java/com/freevibe/FreeVibeApp.kt"
            app.write_text(app.read_text(encoding="utf-8") + "\nfun eager() { ensureSignedIn() }\n", encoding="utf-8")

            with self.assertRaises(CommunityIdentityLazinessError):
                validate_community_identity_laziness(repo)

    def test_rejects_missing_lazy_write_path(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            upload_repo = repo / "app/src/main/java/com/freevibe/data/repository/UploadRepository.kt"
            upload_repo.write_text(upload_repo.read_text(encoding="utf-8").replace("ensureSignedIn(", "currentUserId("), encoding="utf-8")

            with self.assertRaises(CommunityIdentityLazinessError):
                validate_community_identity_laziness(repo)


def copy_required_tree(destination: Path) -> Path:
    paths = [
        "app/src/main/java/com/freevibe/FreeVibeApp.kt",
        "app/src/main/java/com/freevibe/service/CommunityIdentityProvider.kt",
        "app/src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt",
        *[
            "app/src/main/java/com/freevibe/data/repository/" + name
            for name in (
                "UploadRepository.kt",
                "WallpaperUploadRepository.kt",
                "VoteRepository.kt",
                "CommunityReportRepository.kt",
                "CreatorProfileRepository.kt",
                "CommunityBlockRepository.kt",
            )
        ],
    ]
    for relative_path in paths:
        source = REPO_ROOT / relative_path
        target = destination / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    return destination


if __name__ == "__main__":
    unittest.main()
