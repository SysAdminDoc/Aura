from __future__ import annotations

import tempfile
import unittest
import zipfile
from pathlib import Path

from tools.debug_tool_apk_scan import DebugToolApkScanError, scan_release_apks


def write_apk(path: Path, entries: dict[str, bytes]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as apk:
        for name, data in entries.items():
            apk.writestr(name, data)


class DebugToolApkScanTest(unittest.TestCase):
    def test_passes_a_clean_release(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            apk = Path(tmpdir) / "release.apk"
            write_apk(apk, {"classes.dex": b"Lcom/freevibe/FreeVibeApp;", "AndroidManifest.xml": b"manifest"})

            result = scan_release_apks([apk])

            self.assertEqual("ok", result["status"])
            self.assertEqual(["LeakCanary"], result["checkedTools"])

    def test_rejects_a_class_descriptor_in_any_dex(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            apk = Path(tmpdir) / "release.apk"
            write_apk(apk, {"classes.dex": b"clean", "classes3.dex": b"Lleakcanary/internal/InternalLeakCanary;"})

            with self.assertRaises(DebugToolApkScanError) as context:
                scan_release_apks([apk])

            self.assertIn("LeakCanary", str(context.exception))
            self.assertIn("classes3.dex", str(context.exception))

    def test_rejects_a_component_in_the_binary_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            apk = Path(tmpdir) / "release.apk"
            manifest = "leakcanary.internal.activity.LeakActivity".encode("utf-16-le")
            write_apk(apk, {"classes.dex": b"clean", "AndroidManifest.xml": manifest})

            with self.assertRaises(DebugToolApkScanError) as context:
                scan_release_apks([apk])

            self.assertIn("AndroidManifest.xml", str(context.exception))

    def test_rejects_a_packaged_version_file(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            apk = Path(tmpdir) / "release.apk"
            write_apk(apk, {"classes.dex": b"clean", "META-INF/leakcanary-android.version": b"2.14"})

            with self.assertRaises(DebugToolApkScanError) as context:
                scan_release_apks([apk])

            self.assertIn("META-INF/leakcanary-android.version", str(context.exception))

    def test_ignores_licence_text_that_names_the_tool(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            apk = Path(tmpdir) / "release.apk"
            write_apk(apk, {"classes.dex": b"clean", "res/raw/notices.txt": b"see leakcanary docs"})

            self.assertEqual("ok", scan_release_apks([apk])["status"])

    def test_scans_every_apk(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            universal = root / "universal.apk"
            arm64 = root / "arm64.apk"
            write_apk(universal, {"classes.dex": b"clean"})
            write_apk(arm64, {"classes.dex": b"leakcanary"})

            with self.assertRaises(DebugToolApkScanError) as context:
                scan_release_apks([universal, arm64])

            self.assertIn("arm64.apk!classes.dex", str(context.exception))

    def test_rejects_an_apk_with_nothing_to_scan(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            apk = Path(tmpdir) / "release.apk"
            write_apk(apk, {"res/raw/notices.txt": b"clean"})

            with self.assertRaises(DebugToolApkScanError):
                scan_release_apks([apk])

    def test_rejects_missing_apk(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            with self.assertRaises(DebugToolApkScanError):
                scan_release_apks([Path(tmpdir) / "missing.apk"])


if __name__ == "__main__":
    unittest.main()
