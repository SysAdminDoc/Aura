from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.target37_toolchain_gate import Target37ToolchainError, validate_toolchain


REPO_ROOT = Path(__file__).resolve().parents[2]


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_repo(
    repo_root: Path,
    *,
    agp: str = "8.7.3",
    gradle: str = "8.12.1",
    compile_sdk: int = 35,
    target_sdk: int = 35,
) -> None:
    write_text(repo_root / "gradle/libs.versions.toml", f'[versions]\nagp = "{agp}"\n')
    write_text(
        repo_root / "gradle/wrapper/gradle-wrapper.properties",
        f"distributionUrl=https\\://services.gradle.org/distributions/gradle-{gradle}-bin.zip\n",
    )
    module = f"""
android {{
    compileSdk = {compile_sdk}

    defaultConfig {{
        targetSdk = {target_sdk}
    }}
}}
"""
    write_text(repo_root / "app/build.gradle.kts", module)
    write_text(repo_root / "baselineprofile/build.gradle.kts", module)


def write_sdk(root: Path) -> Path:
    (root / "platforms/android-37.0").mkdir(parents=True)
    (root / "build-tools/37.0.0").mkdir(parents=True)
    return root


class Target37ToolchainGateTest(unittest.TestCase):
    def test_live_repo_keeps_gate_armed_but_pending_until_sdk_bump(self) -> None:
        result = validate_toolchain(REPO_ROOT)

        self.assertEqual("pending", result["status"])
        self.assertEqual(35, result["modules"]["app"]["compileSdk"])

    def test_rejects_target37_with_old_agp(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_repo(repo_root, compile_sdk=37, target_sdk=37)

            with self.assertRaisesRegex(Target37ToolchainError, "Android Gradle plugin 9.1.1"):
                validate_toolchain(repo_root, str(write_sdk(repo_root / "sdk")))

    def test_rejects_target37_with_old_gradle(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_repo(repo_root, agp="9.1.1", compile_sdk=37, target_sdk=37)

            with self.assertRaisesRegex(Target37ToolchainError, "Gradle 9.3.1"):
                validate_toolchain(repo_root, str(write_sdk(repo_root / "sdk")))

    def test_rejects_partial_module_sdk_bump(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_repo(repo_root, agp="9.1.1", gradle="9.3.1", compile_sdk=37, target_sdk=35)

            with self.assertRaisesRegex(Target37ToolchainError, "both compileSdk and targetSdk"):
                validate_toolchain(repo_root, str(write_sdk(repo_root / "sdk")))

    def test_accepts_target37_with_required_toolchain_and_sdk(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_repo(repo_root, agp="9.2.0", gradle="9.4.1", compile_sdk=37, target_sdk=37)

            result = validate_toolchain(repo_root, str(write_sdk(repo_root / "sdk")))

            self.assertEqual("ok", result["status"])
            self.assertEqual("android-37.0", result["sdk"]["platform"])

    def test_rejects_target37_without_platform(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            sdk_root = repo_root / "sdk"
            (sdk_root / "build-tools/37.0.0").mkdir(parents=True)
            write_repo(repo_root, agp="9.2.0", gradle="9.4.1", compile_sdk=37, target_sdk=37)

            with self.assertRaisesRegex(Target37ToolchainError, "SDK platform"):
                validate_toolchain(repo_root, str(sdk_root))


if __name__ == "__main__":
    unittest.main()
