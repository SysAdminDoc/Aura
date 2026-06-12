#!/usr/bin/env python3
"""Validate 16KB page-size alignment for native libraries in a release APK."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any


PACKAGE_RE = re.compile(r'applicationId\s*=\s*"([^"]+)"')
PT_LOAD = 1
ELF_MAGIC = b"\x7fELF"
ABI_64_BIT = {"arm64-v8a", "x86_64", "riscv64"}


class NativeAlignmentError(ValueError):
    """Raised when native-library alignment validation fails."""


@dataclass(frozen=True)
class LoadSegment:
    offset: int
    virtual_address: int
    alignment: int


@dataclass(frozen=True)
class NativeLibrary:
    apk_entry: str
    abi: str
    elf_class: int
    load_segments: tuple[LoadSegment, ...]

    @property
    def is_64_bit(self) -> bool:
        return self.elf_class == 2 or self.abi in ABI_64_BIT


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate 64-bit ELF PT_LOAD segment alignment in an APK."
    )
    parser.add_argument("--apk", required=True, help="Release APK to inspect.")
    parser.add_argument(
        "--policy",
        default="docs/distribution/native-alignment.json",
        help="Native alignment policy JSON.",
    )
    parser.add_argument("--repo-root", default=".", help="Repository root.")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise NativeAlignmentError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise NativeAlignmentError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise NativeAlignmentError(f"{label} must be a non-empty string")
    return value.strip()


def require_int(value: Any, label: str) -> int:
    if not isinstance(value, int):
        raise NativeAlignmentError(f"{label} must be an integer")
    return value


def require_bool(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise NativeAlignmentError(f"{label} must be a boolean")
    return value


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise NativeAlignmentError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise NativeAlignmentError(f"{label} contains duplicate values")
    return values


def parse_package_name(repo_root: Path) -> str:
    text = (repo_root / "app/build.gradle.kts").read_text(encoding="utf-8")
    match = PACKAGE_RE.search(text)
    if match:
        return match.group(1)
    raise NativeAlignmentError("app/build.gradle.kts is missing applicationId")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def unpack(fmt: str, data: bytes, offset: int) -> tuple[int, ...]:
    size = struct.calcsize(fmt)
    if offset + size > len(data):
        raise NativeAlignmentError("ELF header is truncated")
    return struct.unpack_from(fmt, data, offset)


def parse_elf(entry_name: str, data: bytes) -> NativeLibrary:
    if len(data) < 16 or data[:4] != ELF_MAGIC:
        raise NativeAlignmentError(f"{entry_name} is not an ELF shared object")
    elf_class = data[4]
    endian_marker = data[5]
    if elf_class not in {1, 2}:
        raise NativeAlignmentError(f"{entry_name} has unsupported ELF class {elf_class}")
    if endian_marker == 1:
        endian = "<"
    elif endian_marker == 2:
        endian = ">"
    else:
        raise NativeAlignmentError(f"{entry_name} has unsupported ELF endian marker {endian_marker}")

    if elf_class == 2:
        phoff = unpack(endian + "Q", data, 32)[0]
        phentsize = unpack(endian + "H", data, 54)[0]
        phnum = unpack(endian + "H", data, 56)[0]
        min_entry_size = 56
    else:
        phoff = unpack(endian + "I", data, 28)[0]
        phentsize = unpack(endian + "H", data, 42)[0]
        phnum = unpack(endian + "H", data, 44)[0]
        min_entry_size = 32

    if phentsize < min_entry_size:
        raise NativeAlignmentError(f"{entry_name} has invalid program header size {phentsize}")
    if phoff + (phentsize * phnum) > len(data):
        raise NativeAlignmentError(f"{entry_name} program headers are truncated")

    segments: list[LoadSegment] = []
    for index in range(phnum):
        base = phoff + (index * phentsize)
        p_type = unpack(endian + "I", data, base)[0]
        if p_type != PT_LOAD:
            continue
        if elf_class == 2:
            p_offset = unpack(endian + "Q", data, base + 8)[0]
            p_vaddr = unpack(endian + "Q", data, base + 16)[0]
            p_align = unpack(endian + "Q", data, base + 48)[0]
        else:
            p_offset = unpack(endian + "I", data, base + 4)[0]
            p_vaddr = unpack(endian + "I", data, base + 8)[0]
            p_align = unpack(endian + "I", data, base + 28)[0]
        segments.append(
            LoadSegment(
                offset=p_offset,
                virtual_address=p_vaddr,
                alignment=p_align,
            )
        )

    if not segments:
        raise NativeAlignmentError(f"{entry_name} has no PT_LOAD segments")

    parts = entry_name.split("/")
    abi = parts[1] if len(parts) >= 3 and parts[0] == "lib" else "unknown"
    return NativeLibrary(
        apk_entry=entry_name,
        abi=abi,
        elf_class=elf_class,
        load_segments=tuple(segments),
    )


def inspect_apk(apk_path: Path, skipped_archive_entries: list[str] | None = None) -> list[NativeLibrary]:
    if not apk_path.is_file():
        raise NativeAlignmentError(f"APK not found: {apk_path}")
    libraries: list[NativeLibrary] = []
    with zipfile.ZipFile(apk_path) as archive:
        for name in sorted(archive.namelist()):
            if not name.startswith("lib/") or not name.endswith(".so"):
                continue
            data = archive.read(name)
            if data.startswith(b"PK\x03\x04") and name.endswith(".zip.so"):
                if skipped_archive_entries is not None:
                    skipped_archive_entries.append(name)
                continue
            libraries.append(parse_elf(name, data))
    if not libraries:
        raise NativeAlignmentError(f"APK has no native libraries under lib/: {apk_path}")
    return libraries


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, Any]:
    if policy.get("schemaVersion") != 1:
        raise NativeAlignmentError("schemaVersion must be 1")
    if policy.get("policyKind") != "nativePageAlignment":
        raise NativeAlignmentError("policyKind must be nativePageAlignment")
    package_name = require_string(policy.get("packageName"), "packageName")
    if parse_package_name(repo_root) != package_name:
        raise NativeAlignmentError("packageName does not match app/build.gradle.kts")
    required_alignment = require_int(
        policy.get("requiredLoadSegmentAlignmentBytes"),
        "requiredLoadSegmentAlignmentBytes",
    )
    if required_alignment < 16384 or required_alignment & (required_alignment - 1) != 0:
        raise NativeAlignmentError("requiredLoadSegmentAlignmentBytes must be a power of two at least 16384")
    if not require_bool(policy.get("require64BitOnly"), "require64BitOnly"):
        raise NativeAlignmentError("require64BitOnly must remain true")
    required_abis = set(require_string_list(policy.get("required64BitAbis"), "required64BitAbis"))
    if not required_abis <= ABI_64_BIT:
        raise NativeAlignmentError("required64BitAbis contains an unknown 64-bit ABI")
    return {
        "packageName": package_name,
        "requiredAlignment": required_alignment,
        "requiredAbis": required_abis,
    }


def validate_libraries(
    libraries: list[NativeLibrary],
    *,
    required_alignment: int,
    required_abis: set[str],
) -> dict[str, object]:
    errors: list[str] = []
    seen_64_bit_abis = {library.abi for library in libraries if library.is_64_bit}
    missing_abis = sorted(required_abis - seen_64_bit_abis)
    if missing_abis:
        errors.append("APK missing required 64-bit ABIs: " + ", ".join(missing_abis))

    checked_segments = 0
    for library in libraries:
        if not library.is_64_bit:
            continue
        for segment in library.load_segments:
            checked_segments += 1
            if segment.alignment < required_alignment or segment.alignment % required_alignment != 0:
                errors.append(
                    f"{library.apk_entry} PT_LOAD offset 0x{segment.offset:x} "
                    f"has p_align {segment.alignment}, expected a multiple of {required_alignment}"
                )
    if errors:
        raise NativeAlignmentError("; ".join(errors))
    return {
        "checked64BitLoadSegments": checked_segments,
        "nativeLibraryCount": len(libraries),
        "seen64BitAbis": sorted(seen_64_bit_abis),
    }


def validate_release_apk(repo_root: Path, policy: dict[str, Any], apk_path: Path) -> dict[str, object]:
    policy_info = validate_policy(repo_root, policy)
    skipped_archive_entries: list[str] = []
    libraries = inspect_apk(apk_path, skipped_archive_entries=skipped_archive_entries)
    library_result = validate_libraries(
        libraries,
        required_alignment=policy_info["requiredAlignment"],
        required_abis=policy_info["requiredAbis"],
    )
    return {
        "status": "ok",
        "policyKind": "nativePageAlignment",
        "packageName": policy_info["packageName"],
        "apk": str(apk_path),
        "apkSha256": sha256_file(apk_path),
        "requiredLoadSegmentAlignmentBytes": policy_info["requiredAlignment"],
        "skippedArchivePayloads": skipped_archive_entries,
        **library_result,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    apk_path = Path(args.apk)
    if not apk_path.is_absolute():
        apk_path = repo_root / apk_path
    try:
        policy = require_object(read_json(repo_root / args.policy), "native alignment policy")
        result = validate_release_apk(repo_root, policy, apk_path.resolve())
    except (OSError, ValueError, json.JSONDecodeError, zipfile.BadZipFile) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
