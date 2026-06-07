from __future__ import annotations

import tempfile
import unittest
import zipfile
from pathlib import Path

from tools.provider_credential_apk_scan import ProviderCredentialApkScanError, scan_release_apks


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_apk(path: Path, entries: dict[str, bytes]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as apk:
        for name, data in entries.items():
            apk.writestr(name, data)


class ProviderCredentialApkScanTest(unittest.TestCase):
    def test_passes_when_release_properties_are_blank(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            local_properties = root / "local.properties"
            apk = root / "release.apk"
            write(local_properties, "pexels.api.key=\npixabay.api.key=\n")
            write_apk(apk, {"classes.dex": b"no provider credentials"})

            result = scan_release_apks(local_properties, [apk])

            self.assertEqual("ok", result["status"])
            self.assertEqual(1, result["apkCount"])
            self.assertEqual(0, result["credentialValueCount"])

    def test_passes_when_nonblank_provider_value_is_absent_from_apk(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            local_properties = root / "local.properties"
            apk = root / "release.apk"
            write(local_properties, "stability.ai.key=sentinel-provider-key\n")
            write_apk(apk, {"classes.dex": b"redacted build output"})

            result = scan_release_apks(local_properties, [apk])

            self.assertEqual("ok", result["status"])
            self.assertEqual(["stability.ai.key"], result["checkedProviderKeys"])

    def test_rejects_provider_value_embedded_in_apk_without_printing_value(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            local_properties = root / "local.properties"
            apk = root / "release.apk"
            write(local_properties, "pexels.api.key=sentinel-provider-key\n")
            write_apk(apk, {"classes.dex": b"BuildConfig sentinel-provider-key"})

            with self.assertRaises(ProviderCredentialApkScanError) as context:
                scan_release_apks(local_properties, [apk])

            message = str(context.exception)
            self.assertIn("pexels.api.key", message)
            self.assertIn("classes.dex", message)
            self.assertNotIn("sentinel-provider-key", message)

    def test_scans_multiple_release_apks(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            local_properties = root / "local.properties"
            universal = root / "universal.apk"
            arm64 = root / "arm64.apk"
            write(local_properties, "freesound.api.key=sentinel-provider-key\n")
            write_apk(universal, {"classes.dex": b"clean"})
            write_apk(arm64, {"classes2.dex": b"sentinel-provider-key"})

            with self.assertRaises(ProviderCredentialApkScanError) as context:
                scan_release_apks(local_properties, [universal, arm64])

            self.assertIn("freesound.api.key", str(context.exception))
            self.assertIn("arm64.apk!classes2.dex", str(context.exception))

    def test_rejects_missing_apk(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            local_properties = root / "local.properties"
            write(local_properties, "soundcloud.client.id=sentinel-provider-key\n")

            with self.assertRaises(ProviderCredentialApkScanError):
                scan_release_apks(local_properties, [root / "missing.apk"])

    def test_rejects_missing_local_properties(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            apk = root / "release.apk"
            write_apk(apk, {"classes.dex": b"clean"})

            with self.assertRaises(ProviderCredentialApkScanError):
                scan_release_apks(root / "missing.properties", [apk])


if __name__ == "__main__":
    unittest.main()
