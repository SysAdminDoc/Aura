import json
import tempfile
import unittest
from pathlib import Path

from tools import ytdlp_cve_policy_check


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_policy_v1(repo_root: Path) -> Path:
    policy_path = repo_root / "docs/security/ytdlp-cve-policy.json"
    write_text(
        policy_path,
        json.dumps(
            {
                "schemaVersion": 1,
                "policyKind": "ytdlpNetrcCommandCveReachability",
                "cve": "CVE-2026-26331",
                "advisory": "GHSA-g3gw-q23r-pgqm",
                "affectedVersionRange": {
                    "introduced": "2023.06.21",
                    "fixed": "2026.02.21",
                },
                "minimumSafeYtDlpVersion": "2026.02.21",
                "nativeComplianceLockPath": "docs/legal/native-compliance.lock.json",
                "allowedAffectedReachability": "affectedBundledVersionAllowedOnlyWhenForbiddenOptionsAreAbsent",
                "forbiddenOptions": ["--netrc-cmd", "netrc_cmd"],
                "scanSourceRoots": ["app/src/main/java"],
                "requiredYtDlpCallSites": [
                    {
                        "id": "youtube-audio-stream-resolution",
                        "path": "app/src/main/java/com/freevibe/data/repository/YouTubeRepository.kt",
                        "requiredTerms": ["YoutubeDLRequest", "YoutubeDL.getInstance().execute"],
                    }
                ],
            }
        ),
    )
    return policy_path


def write_policy_v2(repo_root: Path) -> Path:
    policy_path = repo_root / "docs/security/ytdlp-cve-policy.json"
    write_text(
        policy_path,
        json.dumps(
            {
                "schemaVersion": 2,
                "policyKind": "ytdlpCveReachability",
                "trackedCves": [
                    {"cve": "CVE-2026-26331", "advisory": "GHSA-g3gw-q23r-pgqm", "summary": "netrc-cmd injection", "forbiddenOptions": ["--netrc-cmd", "netrc_cmd"]},
                    {"cve": "CVE-2026-50019", "advisory": "", "summary": "Cookie leak", "forbiddenOptions": ["--cookies"]},
                    {"cve": "CVE-2026-50023", "advisory": "", "summary": "Filename sanitization", "forbiddenOptions": []},
                    {"cve": "CVE-2026-50574", "advisory": "", "summary": "aria2c code exec", "forbiddenOptions": ["aria2c", "--downloader"]},
                    {"cve": "CVE-2025-54072", "advisory": "", "summary": "--exec injection", "forbiddenOptions": ["--exec"]},
                ],
                "affectedVersionRange": {"introduced": "2023.06.21", "fixed": "2026.02.21"},
                "minimumSafeYtDlpVersion": "2026.02.21",
                "nativeComplianceLockPath": "docs/legal/native-compliance.lock.json",
                "allowedAffectedReachability": "affectedBundledVersionAllowedOnlyWhenForbiddenOptionsAreAbsent",
                "forbiddenOptions": ["--netrc-cmd", "netrc_cmd", "--cookies", "aria2c", "--downloader", "--exec"],
                "scanSourceRoots": ["app/src/main/java"],
                "requiredYtDlpCallSites": [
                    {
                        "id": "youtube-audio-stream-resolution",
                        "path": "app/src/main/java/com/freevibe/data/repository/YouTubeRepository.kt",
                        "requiredTerms": ["YoutubeDLRequest", "YoutubeDL.getInstance().execute"],
                    }
                ],
            }
        ),
    )
    return policy_path


def write_lock(repo_root: Path, version: str) -> None:
    write_text(
        repo_root / "docs/legal/native-compliance.lock.json",
        json.dumps({"records": [{"payloads": [{"facts": {"yt-dlp version": version}}]}]}),
    )


def write_call_site(repo_root: Path, extra: str = "") -> None:
    write_text(
        repo_root / "app/src/main/java/com/freevibe/data/repository/YouTubeRepository.kt",
        "\n".join(
            [
                "fun resolve(url: String) {",
                "    val request = YoutubeDLRequest(url)",
                "    request.addOption(\"--get-url\")",
                "    YoutubeDL.getInstance().execute(request)",
                extra,
                "}",
            ]
        ),
    )


class YtDlpCvePolicyCheckV1Test(unittest.TestCase):
    def test_allows_affected_bundled_version_when_netrc_cmd_is_absent(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v1(repo_root)
            write_lock(repo_root, "2025.11.12")
            write_call_site(repo_root)

            result = ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

            self.assertEqual(result["status"], "affected_not_reachable")
            self.assertEqual(result["bundledYtDlpVersion"], "2025.11.12")

    def test_rejects_forbidden_netrc_cmd_option(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v1(repo_root)
            write_lock(repo_root, "2025.11.12")
            write_call_site(repo_root, "    request.addOption(\"--netrc-cmd\", \"helper\")")

            with self.assertRaisesRegex(
                ytdlp_cve_policy_check.YtDlpCvePolicyError,
                "forbidden yt-dlp option",
            ):
                ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

    def test_accepts_fixed_bundled_version(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v1(repo_root)
            write_lock(repo_root, "2026.02.21")
            write_call_site(repo_root)

            result = ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

            self.assertEqual(result["status"], "fixed_or_unaffected")

    def test_rejects_missing_bundled_version(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v1(repo_root)
            write_text(repo_root / "docs/legal/native-compliance.lock.json", json.dumps({"records": []}))
            write_call_site(repo_root)

            with self.assertRaisesRegex(
                ytdlp_cve_policy_check.YtDlpCvePolicyError,
                "does not record a yt-dlp version",
            ):
                ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)


class YtDlpCvePolicyCheckV2Test(unittest.TestCase):
    def test_v2_tracks_all_five_cves(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v2(repo_root)
            write_lock(repo_root, "2025.11.12")
            write_call_site(repo_root)

            result = ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

            self.assertEqual(result["status"], "affected_not_reachable")
            self.assertEqual(len(result["trackedCves"]), 5)
            self.assertIn("CVE-2026-26331", result["trackedCves"])
            self.assertIn("CVE-2026-50019", result["trackedCves"])
            self.assertIn("CVE-2026-50023", result["trackedCves"])
            self.assertIn("CVE-2026-50574", result["trackedCves"])
            self.assertIn("CVE-2025-54072", result["trackedCves"])

    def test_v2_rejects_exec_flag(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v2(repo_root)
            write_lock(repo_root, "2025.11.12")
            write_call_site(repo_root, "    request.addOption(\"--exec\", \"cmd\")")

            with self.assertRaisesRegex(
                ytdlp_cve_policy_check.YtDlpCvePolicyError,
                "forbidden yt-dlp option",
            ):
                ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

    def test_v2_rejects_cookies_flag(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v2(repo_root)
            write_lock(repo_root, "2025.11.12")
            write_call_site(repo_root, "    request.addOption(\"--cookies\", \"file.txt\")")

            with self.assertRaisesRegex(
                ytdlp_cve_policy_check.YtDlpCvePolicyError,
                "forbidden yt-dlp option",
            ):
                ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

    def test_v2_rejects_aria2c_downloader(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v2(repo_root)
            write_lock(repo_root, "2025.11.12")
            write_call_site(repo_root, "    request.addOption(\"--downloader\", \"aria2c\")")

            with self.assertRaisesRegex(
                ytdlp_cve_policy_check.YtDlpCvePolicyError,
                "forbidden yt-dlp option",
            ):
                ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

    def test_v2_allows_safe_code_paths(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v2(repo_root)
            write_lock(repo_root, "2025.11.12")
            write_call_site(repo_root)

            result = ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

            self.assertEqual(result["status"], "affected_not_reachable")
            self.assertEqual(len(result["forbiddenOptions"]), 6)


if __name__ == "__main__":
    unittest.main()
