import hashlib
import json
import tempfile
import unittest
import zipfile
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
                "downloadBoundsHelper": {
                    "path": BOUNDS_HELPER,
                    "requiredTerms": ["--max-filesize", "--no-playlist"],
                },
                "downloadCallSites": [
                    {
                        "id": "video-wallpaper-download",
                        "path": DOWNLOAD_SITE,
                        "requiredTerms": ["applyYtDlpDownloadBounds"],
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
                    {"cve": "CVE-2026-55404", "advisory": "GHSA-6v4j-43gg-vj32", "summary": "write-link injection", "forbiddenOptions": ["--write-link", "--write-url-link", "--write-webloc-link", "--write-desktop-link"]},
                ],
                "affectedVersionRange": {"introduced": "2023.06.21", "fixed": "2026.02.21"},
                "minimumSafeYtDlpVersion": "2026.02.21",
                "nativeComplianceLockPath": "docs/legal/native-compliance.lock.json",
                "allowedAffectedReachability": "affectedBundledVersionAllowedOnlyWhenForbiddenOptionsAreAbsent",
                "forbiddenOptions": ["--netrc-cmd", "netrc_cmd", "--cookies", "aria2c", "--downloader", "--exec", "--write-link", "--write-url-link", "--write-webloc-link", "--write-desktop-link"],
                "scanSourceRoots": ["app/src/main/java"],
                "requiredYtDlpCallSites": [
                    {
                        "id": "youtube-audio-stream-resolution",
                        "path": "app/src/main/java/com/freevibe/data/repository/YouTubeRepository.kt",
                        "requiredTerms": ["YoutubeDLRequest", "YoutubeDL.getInstance().execute"],
                    }
                ],
                "downloadBoundsHelper": {
                    "path": BOUNDS_HELPER,
                    "requiredTerms": ["--max-filesize", "--no-playlist"],
                },
                "downloadCallSites": [
                    {
                        "id": "video-wallpaper-download",
                        "path": DOWNLOAD_SITE,
                        "requiredTerms": ["applyYtDlpDownloadBounds"],
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


DOWNLOAD_SITE = "app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoDownloadActions.kt"
BOUNDS_HELPER = "app/src/main/java/com/freevibe/service/YtDlpDownloadSafety.kt"


def write_download_bounds(repo_root: Path, *, bounded_downloads: int = 1) -> None:
    """A bounds helper plus a download site with `bounded_downloads` bounded executions."""
    write_text(
        repo_root / BOUNDS_HELPER,
        "\n".join(
            [
                "internal fun applyYtDlpDownloadBounds(request: YoutubeDLRequest) {",
                "    request.addOption(\"--max-filesize\", maxBytes.toString())",
                "    request.addOption(\"--no-playlist\")",
                "}",
            ]
        ),
    )
    body = ["fun download() {"]
    for index in range(bounded_downloads):
        body.append(f"    applyYtDlpDownloadBounds(request{index})")
        body.append(f"    YoutubeDL.getInstance().execute(request{index})")
    body.append("}")
    write_text(repo_root / DOWNLOAD_SITE, "\n".join(body))


class YtDlpCvePolicyCheckV1Test(unittest.TestCase):
    def test_allows_affected_bundled_version_when_netrc_cmd_is_absent(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v1(repo_root)
            write_lock(repo_root, "2025.11.12")
            write_call_site(repo_root)
            write_download_bounds(repo_root)

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
            write_download_bounds(repo_root)

            result = ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

            self.assertEqual(result["status"], "fixed_or_unaffected")

    def test_rejects_missing_bundled_version(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v1(repo_root)
            write_text(repo_root / "docs/legal/native-compliance.lock.json", json.dumps({"records": []}))
            write_call_site(repo_root)
            write_download_bounds(repo_root)

            with self.assertRaisesRegex(
                ytdlp_cve_policy_check.YtDlpCvePolicyError,
                "does not record a yt-dlp version",
            ):
                ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)


class YtDlpCvePolicyCheckV2Test(unittest.TestCase):
    def test_v2_tracks_all_six_cves(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v2(repo_root)
            write_lock(repo_root, "2025.11.12")
            write_call_site(repo_root)
            write_download_bounds(repo_root)

            result = ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

            self.assertEqual(result["status"], "affected_not_reachable")
            self.assertEqual(len(result["trackedCves"]), 6)
            self.assertIn("CVE-2026-26331", result["trackedCves"])
            self.assertIn("CVE-2026-50019", result["trackedCves"])
            self.assertIn("CVE-2026-50023", result["trackedCves"])
            self.assertIn("CVE-2026-50574", result["trackedCves"])
            self.assertIn("CVE-2025-54072", result["trackedCves"])
            self.assertIn("CVE-2026-55404", result["trackedCves"])

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
            write_download_bounds(repo_root)

            result = ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

            self.assertEqual(result["status"], "affected_not_reachable")
            self.assertEqual(len(result["forbiddenOptions"]), 10)

    def test_v2_validates_effective_app_payload_override(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v2(repo_root)
            policy = json.loads(policy_path.read_text(encoding="utf-8"))
            payload = repo_root / "app/src/main/res/raw/ytdlp"
            payload.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(payload, "w") as archive:
                archive.writestr("yt_dlp/version.py", "__version__ = '2026.07.04'\n")
            digest = hashlib.sha256(payload.read_bytes()).hexdigest()
            policy.update(
                {
                    "affectedVersionRange": {"introduced": "2023.06.21", "fixed": "2026.07.04"},
                    "minimumSafeYtDlpVersion": "2026.07.04",
                    "bundledPayloadPath": "app/src/main/res/raw/ytdlp",
                    "bundledPayloadSha256": digest,
                    "bundledPayloadSourceUrl": "https://github.com/yt-dlp/yt-dlp/releases/download/2026.07.04/yt-dlp",
                }
            )
            write_text(policy_path, json.dumps(policy))
            write_lock(repo_root, "2025.11.12")
            write_call_site(repo_root)
            write_download_bounds(repo_root)

            result = ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

            self.assertEqual(result["status"], "fixed_or_unaffected")
            self.assertEqual(result["bundledYtDlpVersion"], "2026.07.04")
            self.assertEqual(result["bundledPayloadSha256"], digest)

    def test_v2_rejects_payload_hash_mismatch(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = write_policy_v2(repo_root)
            policy = json.loads(policy_path.read_text(encoding="utf-8"))
            payload = repo_root / "app/src/main/res/raw/ytdlp"
            payload.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(payload, "w") as archive:
                archive.writestr("yt_dlp/version.py", "__version__ = '2026.07.04'\n")
            policy.update(
                {
                    "affectedVersionRange": {"introduced": "2023.06.21", "fixed": "2026.07.04"},
                    "minimumSafeYtDlpVersion": "2026.07.04",
                    "bundledPayloadPath": "app/src/main/res/raw/ytdlp",
                    "bundledPayloadSha256": "0" * 64,
                    "bundledPayloadSourceUrl": "https://github.com/yt-dlp/yt-dlp/releases/download/2026.07.04/yt-dlp",
                }
            )
            write_text(policy_path, json.dumps(policy))
            write_lock(repo_root, "2025.11.12")
            write_call_site(repo_root)
            write_download_bounds(repo_root)

            with self.assertRaisesRegex(
                ytdlp_cve_policy_check.YtDlpCvePolicyError,
                "SHA-256 mismatch",
            ):
                ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)


class YtDlpDownloadBoundsTest(unittest.TestCase):
    """A forbidden-option scan cannot see an option that was never passed."""

    REPO_ROOT = Path(__file__).resolve().parents[2]

    def _staged(self, repo_root: Path) -> Path:
        policy_path = write_policy_v2(repo_root)
        write_lock(repo_root, "2026.07.04")
        write_call_site(repo_root)
        return policy_path

    def test_live_downloads_are_bounded(self):
        result = ytdlp_cve_policy_check.validate_policy(
            self.REPO_ROOT, self.REPO_ROOT / "docs/security/ytdlp-cve-policy.json"
        )

        self.assertIn("video-wallpaper-download", result["boundedDownloadSites"])

    def test_rejects_a_download_site_that_never_bounds(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = self._staged(repo_root)
            write_download_bounds(repo_root)
            # A download that executes without ever calling the bounds helper.
            write_text(
                repo_root / DOWNLOAD_SITE,
                "fun download() {\n    YoutubeDL.getInstance().execute(request)\n}",
            )

            with self.assertRaisesRegex(
                ytdlp_cve_policy_check.YtDlpCvePolicyError,
                "without bounding it first",
            ):
                ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

    def test_rejects_a_second_branch_that_forgets_the_cap(self):
        """Two executions, one bounded: the new branch must fail the gate."""
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = self._staged(repo_root)
            write_download_bounds(repo_root, bounded_downloads=1)
            existing = (repo_root / DOWNLOAD_SITE).read_text(encoding="utf-8")
            write_text(
                repo_root / DOWNLOAD_SITE,
                existing + "\nfun second() {\n    YoutubeDL.getInstance().execute(other)\n}",
            )

            with self.assertRaisesRegex(
                ytdlp_cve_policy_check.YtDlpCvePolicyError,
                "2 yt-dlp executions but only 1 bounded",
            ):
                ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

    def test_rejects_a_helper_that_drops_the_size_cap(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = self._staged(repo_root)
            write_download_bounds(repo_root)
            write_text(
                repo_root / BOUNDS_HELPER,
                "internal fun applyYtDlpDownloadBounds() {\n"
                "    request.addOption(\"--no-playlist\")\n}",
            )

            with self.assertRaisesRegex(
                ytdlp_cve_policy_check.YtDlpCvePolicyError,
                "no longer passes the required download option: --max-filesize",
            ):
                ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)

    def test_rejects_a_missing_bounds_helper(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            policy_path = self._staged(repo_root)

            with self.assertRaisesRegex(
                ytdlp_cve_policy_check.YtDlpCvePolicyError,
                "download bounds helper is missing",
            ):
                ytdlp_cve_policy_check.validate_policy(repo_root, policy_path)


if __name__ == "__main__":
    unittest.main()
