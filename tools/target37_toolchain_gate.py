#!/usr/bin/env python3
"""Validate Aura's target-37 Android toolchain gate."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import Any


TARGET_SDK = 37
MIN_TARGET37_AGP = (9, 1, 1)
MIN_TARGET37_GRADLE = (9, 3, 1)
MIN_TARGET37_BUILD_TOOLS = (36, 0, 0)
GRADLE_VERSION_RE = re.compile(r"gradle-([0-9]+(?:\.[0-9]+){1,2})-")
AGP_VERSION_RE = re.compile(r'^agp\s*=\s*"([^"]+)"\s*$')
SDK_ASSIGNMENT_RE = re.compile(r"\b(compileSdk|targetSdk)\s*=\s*(\d+)\b")


class Target37ToolchainError(ValueError):
    """Raised when the target-37 toolchain gate fails."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate target-37 toolchain readiness.")
    parser.add_argument("--repo-root", default=".")
    parser.add_argument("--sdk-root", default=None)
    return parser.parse_args()


def parse_semver(value: str, label: str) -> tuple[int, int, int]:
    parts = value.split(".")
    if len(parts) not in {2, 3} or not all(part.isdigit() for part in parts):
        raise Target37ToolchainError(f"{label} version must be numeric major.minor[.patch]: {value}")
    numeric = [int(part) for part in parts]
    while len(numeric) < 3:
        numeric.append(0)
    return tuple(numeric)  # type: ignore[return-value]


def require_file(path: Path, label: str) -> Path:
    if not path.is_file():
        raise Target37ToolchainError(f"{label} file is missing: {path}")
    return path


def read_text(path: Path, label: str) -> str:
    return require_file(path, label).read_text(encoding="utf-8")


def parse_gradle_wrapper_version(repo_root: Path) -> tuple[int, int, int]:
    text = read_text(repo_root / "gradle/wrapper/gradle-wrapper.properties", "Gradle wrapper")
    match = GRADLE_VERSION_RE.search(text)
    if not match:
        raise Target37ToolchainError("Gradle wrapper distributionUrl is missing a Gradle version")
    return parse_semver(match.group(1), "Gradle wrapper")


def parse_agp_version(repo_root: Path) -> tuple[int, int, int]:
    for line in read_text(repo_root / "gradle/libs.versions.toml", "version catalog").splitlines():
        match = AGP_VERSION_RE.match(line.strip())
        if match:
            return parse_semver(match.group(1), "AGP")
    raise Target37ToolchainError("version catalog is missing agp version")


def parse_module_sdks(repo_root: Path, relative_path: str) -> dict[str, int]:
    values: dict[str, int] = {}
    for match in SDK_ASSIGNMENT_RE.finditer(read_text(repo_root / relative_path, relative_path)):
        values[match.group(1)] = int(match.group(2))
    missing = {"compileSdk", "targetSdk"} - values.keys()
    if missing:
        raise Target37ToolchainError(f"{relative_path} missing SDK assignments: {', '.join(sorted(missing))}")
    return values


def sdk_root_from_local_properties(repo_root: Path) -> Path | None:
    path = repo_root / "local.properties"
    if not path.is_file():
        return None
    for raw_line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = raw_line.strip()
        if line.startswith("sdk.dir="):
            return Path(line.split("=", 1)[1].strip())
    return None


def sdk_root(repo_root: Path, explicit: str | None) -> Path | None:
    if explicit:
        return Path(explicit)
    env_sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if env_sdk:
        return Path(env_sdk)
    return sdk_root_from_local_properties(repo_root)


def installed_build_tools_versions(sdk: Path) -> list[tuple[int, int, int]]:
    build_tools = sdk / "build-tools"
    if not build_tools.is_dir():
        return []
    versions: list[tuple[int, int, int]] = []
    for child in build_tools.iterdir():
        if child.is_dir():
            try:
                versions.append(parse_semver(child.name, "SDK Build Tools"))
            except Target37ToolchainError:
                continue
    return sorted(versions)


def validate_target37_sdk(sdk: Path) -> dict[str, Any]:
    platform_candidates = [
        sdk / "platforms" / "android-37",
        sdk / "platforms" / "android-37.0",
    ]
    platform = next((path for path in platform_candidates if path.is_dir()), None)
    if platform is None:
        raise Target37ToolchainError("target-37 builds require SDK platform android-37.0 or android-37")
    build_tools = installed_build_tools_versions(sdk)
    if not build_tools or build_tools[-1] < MIN_TARGET37_BUILD_TOOLS:
        raise Target37ToolchainError("target-37 builds require SDK Build Tools 36.0.0 or newer")
    return {
        "buildTools": ".".join(str(part) for part in build_tools[-1]),
        "platform": platform.name,
        "sdkRoot": str(sdk),
    }


def validate_toolchain(repo_root: Path, explicit_sdk_root: str | None = None) -> dict[str, Any]:
    app_sdks = parse_module_sdks(repo_root, "app/build.gradle.kts")
    baseline_sdks = parse_module_sdks(repo_root, "baselineprofile/build.gradle.kts")
    modules = {"app": app_sdks, "baselineprofile": baseline_sdks}
    max_sdk = max(value for module in modules.values() for value in module.values())
    agp = parse_agp_version(repo_root)
    gradle = parse_gradle_wrapper_version(repo_root)

    result: dict[str, Any] = {
        "agp": ".".join(str(part) for part in agp),
        "gradle": ".".join(str(part) for part in gradle),
        "modules": modules,
        "requiredTargetSdk": TARGET_SDK,
    }
    if max_sdk < TARGET_SDK:
        result["status"] = "pending"
        result["reason"] = "compileSdk/targetSdk are below 37; target-37 release gate is armed but not active"
        return result

    for module, sdks in modules.items():
        if sdks["compileSdk"] != TARGET_SDK or sdks["targetSdk"] != TARGET_SDK:
            raise Target37ToolchainError(
                f"{module} must set both compileSdk and targetSdk to 37 when target-37 is active"
            )
    if agp < MIN_TARGET37_AGP:
        raise Target37ToolchainError("target-37 builds require Android Gradle plugin 9.1.1 or newer")
    if gradle < MIN_TARGET37_GRADLE:
        raise Target37ToolchainError("target-37 builds require Gradle 9.3.1 or newer")

    sdk = sdk_root(repo_root, explicit_sdk_root)
    if sdk is None:
        raise Target37ToolchainError("target-37 builds require ANDROID_HOME or local.properties sdk.dir")
    result["sdk"] = validate_target37_sdk(sdk)
    result["status"] = "ok"
    return result


def main() -> int:
    args = parse_args()
    try:
        result = validate_toolchain(Path(args.repo_root), args.sdk_root)
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
