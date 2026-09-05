#!/usr/bin/env python3
"""Validate 16KB page-size alignment for native libraries in a release APK."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import struct
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any


if __package__ in (None, ""):
    # Executed as `python tools/native_alignment_check.py`, where only tools/ is
    # on sys.path. Tests import this as `tools.native_alignment_check`.
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.published_state import PublishedStateError, assert_enforcement_mechanism

PACKAGE_RE = re.compile(r'applicationId\s*=\s*"([^"]+)"')
PT_LOAD = 1
ELF_MAGIC = b"\x7fELF"
ABI_64_BIT = {"arm64-v8a", "x86_64", "riscv64"}


class NativeAlignmentError(ValueError):
    """Raised when native-library alignment validation fails."""


@dataclass(frozen=True)
class AlignmentException:
    """One acknowledged 64-bit ELF inside a nested archive that is under-aligned.

    These are prebuilt objects inside a published third-party AAR, so they
    cannot be rebuilt here. Recording them is the honest alternative to the old
    behaviour, which was to skip the entire archive and report nothing.

    An exception is only tolerated while it is still observed. A listed entry
    that the APK no longer contains fails the gate, so the list cannot decay
    into a blanket suppression that hides the next regression.
    """

    archive_entry: str
    inner_path: str
    abis: frozenset[str]
    observed_alignment: int
    reason: str
    upstream: str

    def matches(self, library: NativeLibrary) -> bool:
        return (
            library.archive_entry is not None
            and library.inner_path == self.inner_path
            and library.archive_entry.endswith(self.archive_entry)
            and library.abi in self.abis
        )


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
    archive_entry: str | None = None
    inner_path: str | None = None

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


def require_object_list(value: Any, label: str) -> list[dict[str, Any]]:
    if not isinstance(value, list) or not value:
        raise NativeAlignmentError(f"{label} must be a non-empty list")
    return [require_object(item, f"{label}[{index}]") for index, item in enumerate(value)]


def parse_package_name(repo_root: Path) -> str:
    text = (repo_root / "app/build.gradle.kts").read_text(encoding="utf-8")
    match = PACKAGE_RE.search(text)
    if match:
        return match.group(1)
    raise NativeAlignmentError("app/build.gradle.kts is missing applicationId")


def validate_media_stack_migration_evidence(repo_root: Path, value: Any) -> dict[str, Any]:
    evidence = require_object(value, "mediaStackMigrationEvidence")
    require_string(evidence.get("date"), "mediaStackMigrationEvidence.date")
    if evidence.get("status") != "verified":
        raise NativeAlignmentError("mediaStackMigrationEvidence.status must be verified")

    artifact_sizes = require_object(
        evidence.get("artifactBytes"),
        "mediaStackMigrationEvidence.artifactBytes",
    )
    before = require_object(artifact_sizes.get("before"), "artifactBytes.before")
    after = require_object(artifact_sizes.get("after"), "artifactBytes.after")
    if not before or set(before) != set(after):
        raise NativeAlignmentError("artifactBytes.before and artifactBytes.after must name the same artifacts")
    for phase, sizes in (("before", before), ("after", after)):
        for artifact, byte_count in sizes.items():
            require_string(artifact, f"artifactBytes.{phase} artifact name")
            if require_int(byte_count, f"artifactBytes.{phase}.{artifact}") <= 0:
                raise NativeAlignmentError(f"artifactBytes.{phase}.{artifact} must be positive")

    packaging = require_object(
        evidence.get("legacyPackagingDecision"),
        "mediaStackMigrationEvidence.legacyPackagingDecision",
    )
    legacy_enabled = require_bool(
        packaging.get("useLegacyPackaging"),
        "legacyPackagingDecision.useLegacyPackaging",
    )
    can_disable = require_bool(
        packaging.get("canDisable"),
        "legacyPackagingDecision.canDisable",
    )
    require_string(packaging.get("reason"), "legacyPackagingDecision.reason")
    gradle_text = (repo_root / "app/build.gradle.kts").read_text(encoding="utf-8")
    declared_legacy = bool(re.search(r"useLegacyPackaging\s*=\s*true", gradle_text))
    if legacy_enabled != declared_legacy:
        raise NativeAlignmentError(
            "legacyPackagingDecision.useLegacyPackaging does not match app/build.gradle.kts"
        )
    if legacy_enabled and can_disable:
        raise NativeAlignmentError("legacy packaging cannot be enabled while canDisable is true")

    consumers = require_object_list(
        evidence.get("retainedFfmpegConsumers"),
        "mediaStackMigrationEvidence.retainedFfmpegConsumers",
    )
    consumer_ids: set[str] = set()
    for index, consumer in enumerate(consumers):
        label = f"retainedFfmpegConsumers[{index}]"
        consumer_id = require_string(consumer.get("id"), f"{label}.id")
        if consumer_id in consumer_ids:
            raise NativeAlignmentError(f"retainedFfmpegConsumers contains duplicate id {consumer_id}")
        consumer_ids.add(consumer_id)
        source_path = require_string(consumer.get("sourcePath"), f"{label}.sourcePath")
        require_string(consumer.get("mode"), f"{label}.mode")
        require_string_list(consumer.get("operations"), f"{label}.operations")
        if not (repo_root / source_path).is_file():
            raise NativeAlignmentError(f"retained FFmpeg consumer source is missing: {source_path}")

    required_consumers = {
        "sound-editor-codec-fallbacks",
        "video-crop-export",
        "yt-dlp-extractor-runtime",
    }
    if consumer_ids != required_consumers:
        missing = sorted(required_consumers - consumer_ids)
        extra = sorted(consumer_ids - required_consumers)
        details = []
        if missing:
            details.append("missing " + ", ".join(missing))
        if extra:
            details.append("unexpected " + ", ".join(extra))
        raise NativeAlignmentError("retainedFfmpegConsumers mismatch: " + "; ".join(details))

    if require_bool(
        evidence.get("videoCropIsSoleRemainingConsumer"),
        "mediaStackMigrationEvidence.videoCropIsSoleRemainingConsumer",
    ):
        raise NativeAlignmentError(
            "videoCropIsSoleRemainingConsumer must be false while codec fallbacks and yt-dlp remain"
        )
    require_string(evidence.get("videoCropStatus"), "mediaStackMigrationEvidence.videoCropStatus")
    return {
        "artifactCount": len(after),
        "retainedFfmpegConsumerIds": sorted(consumer_ids),
        "useLegacyPackaging": legacy_enabled,
        "canDisableLegacyPackaging": can_disable,
    }


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


def inspect_nested_archive(outer_entry: str, payload: bytes) -> tuple[list[NativeLibrary], int]:
    """Read the ELFs inside a `lib/<abi>/*.zip.so` payload.

    youtubedl-android ships FFmpeg and CPython as ZIP archives renamed to `.so`
    so the APK packager will carry them, then extracts them at runtime. The
    loader never maps them from the APK, so nothing about the outer entry says
    anything about the ELFs inside, and this gate used to record the whole
    archive as "skipped". That left roughly 250 shipped 64-bit ELFs per ABI
    unmeasured, including five libwebp objects that are only 4 KB aligned.

    Entries that are not ELFs are counted, not skipped silently: the archives
    are full of Python sources and of tiny text files standing in for symlinks.
    """
    libraries: list[NativeLibrary] = []
    non_elf_entries = 0
    with zipfile.ZipFile(io.BytesIO(payload)) as inner:
        for info in sorted(inner.infolist(), key=lambda item: item.filename):
            if info.is_dir():
                continue
            blob = inner.read(info)
            if not blob.startswith(ELF_MAGIC):
                non_elf_entries += 1
                continue
            library = parse_elf(f"{outer_entry}!{info.filename}", blob)
            libraries.append(
                NativeLibrary(
                    apk_entry=library.apk_entry,
                    abi=library.abi,
                    elf_class=library.elf_class,
                    load_segments=library.load_segments,
                    archive_entry=outer_entry,
                    inner_path=info.filename,
                )
            )
    return libraries, non_elf_entries


def inspect_apk(
    apk_path: Path,
    skipped_archive_entries: list[str] | None = None,
    nested_archive_report: dict[str, dict[str, int]] | None = None,
) -> list[NativeLibrary]:
    if not apk_path.is_file():
        raise NativeAlignmentError(f"APK not found: {apk_path}")
    libraries: list[NativeLibrary] = []
    with zipfile.ZipFile(apk_path) as archive:
        for name in sorted(archive.namelist()):
            if not name.startswith("lib/") or not name.endswith(".so"):
                continue
            data = archive.read(name)
            if data.startswith(b"PK\x03\x04"):
                try:
                    nested, non_elf_entries = inspect_nested_archive(name, data)
                except zipfile.BadZipFile as exc:
                    raise NativeAlignmentError(
                        f"{name} starts with a ZIP signature but could not be read: {exc}"
                    ) from exc
                if not nested:
                    # An archive payload carrying no ELF at all is either not
                    # what we think it is or has changed shape upstream. Either
                    # way, reporting it as inspected would be a false negative.
                    if skipped_archive_entries is not None:
                        skipped_archive_entries.append(name)
                if nested_archive_report is not None:
                    nested_archive_report[name] = {
                        "elfCount": len(nested),
                        "nonElfEntryCount": non_elf_entries,
                    }
                libraries.extend(nested)
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
    # This used to insist require64BitOnly stay true while the shipped APK carried
    # armeabi-v7a and x86, and the library loop skipped every 32-bit library, so
    # the contradiction was unobservable. The flag now means what it says, and the
    # declared set below is what records reality.
    require_64_bit_only = require_bool(policy.get("require64BitOnly"), "require64BitOnly")
    required_abis = set(require_string_list(policy.get("required64BitAbis"), "required64BitAbis"))
    if not required_abis <= ABI_64_BIT:
        raise NativeAlignmentError("required64BitAbis contains an unknown 64-bit ABI")
    declared_abis = set(require_string_list(policy.get("declaredAbis"), "declaredAbis"))
    if not required_abis <= declared_abis:
        raise NativeAlignmentError(
            "required64BitAbis names ABIs missing from declaredAbis: "
            + ", ".join(sorted(required_abis - declared_abis))
        )
    if require_64_bit_only and not declared_abis <= ABI_64_BIT:
        raise NativeAlignmentError(
            "require64BitOnly is true but declaredAbis includes 32-bit ABIs: "
            + ", ".join(sorted(declared_abis - ABI_64_BIT))
        )
    if not require_64_bit_only:
        require_string(
            policy.get("thirtyTwoBitSupportRationale"),
            "thirtyTwoBitSupportRationale",
        )
    status = require_string(policy.get("status"), "status")
    enforced_by = validate_enforcement(repo_root, status, policy.get("enforcedBy"))
    media_stack_migration = validate_media_stack_migration_evidence(
        repo_root,
        policy.get("mediaStackMigrationEvidence"),
    )
    alignment_exceptions = parse_alignment_exceptions(
        policy.get("nestedArchiveAlignmentExceptions"),
        required_alignment,
        declared_abis,
    )
    return {
        "packageName": package_name,
        "requiredAlignment": required_alignment,
        "requiredAbis": required_abis,
        "declaredAbis": declared_abis,
        "require64BitOnly": require_64_bit_only,
        "status": status,
        "enforcedBy": enforced_by,
        "mediaStackMigrationEvidence": media_stack_migration,
        "alignmentExceptions": alignment_exceptions,
    }


def validate_enforcement(repo_root: Path, status: str, enforced_by: Any) -> list[str]:
    """Hold an `...Enforced` status to naming a mechanism that actually exists.

    This file said `releaseWorkflowEnforced` long after the workflows it meant
    were deleted, and nothing noticed because no gate read the field. A status
    string is a claim; the file it names is the mechanism.
    """
    if not status.endswith("Enforced"):
        return []
    paths = [] if enforced_by is None else require_string_list(enforced_by, "enforcedBy")
    try:
        assert_enforcement_mechanism(repo_root, status, paths, "native alignment policy")
    except PublishedStateError as exc:
        raise NativeAlignmentError(str(exc)) from exc
    return paths


def parse_alignment_exceptions(
    value: Any,
    required_alignment: int,
    declared_abis: set[str],
) -> tuple[AlignmentException, ...]:
    if value is None:
        return ()
    exceptions: list[AlignmentException] = []
    for entry in require_object_list(value, "nestedArchiveAlignmentExceptions"):
        abis = set(require_string_list(entry.get("abis"), "nestedArchiveAlignmentExceptions[].abis"))
        unknown = sorted(abis - declared_abis)
        if unknown:
            raise NativeAlignmentError(
                "nestedArchiveAlignmentExceptions names ABIs the app does not declare: "
                + ", ".join(unknown)
            )
        observed = require_int(
            entry.get("observedAlignmentBytes"),
            "nestedArchiveAlignmentExceptions[].observedAlignmentBytes",
        )
        if observed >= required_alignment:
            raise NativeAlignmentError(
                "nestedArchiveAlignmentExceptions entry records "
                f"observedAlignmentBytes {observed}, which already meets the required "
                f"{required_alignment}; it is not an exception and must be removed"
            )
        exceptions.append(
            AlignmentException(
                archive_entry=require_string(
                    entry.get("archiveEntry"), "nestedArchiveAlignmentExceptions[].archiveEntry"
                ),
                inner_path=require_string(
                    entry.get("innerPath"), "nestedArchiveAlignmentExceptions[].innerPath"
                ),
                abis=frozenset(abis),
                observed_alignment=observed,
                reason=require_string(
                    entry.get("reason"), "nestedArchiveAlignmentExceptions[].reason"
                ),
                upstream=require_string(
                    entry.get("upstream"), "nestedArchiveAlignmentExceptions[].upstream"
                ),
            )
        )
    return tuple(exceptions)


def expected_abis_for_apk(apk_name: str, declared_abis: set[str]) -> tuple[str, set[str]]:
    """What this particular artifact should contain, decided by its file name.

    Which ABIs an APK *should* have cannot be inferred from the ones it has: a
    universal build that lost three of its four ABIs looks exactly like a split.
    Gradle names the artifacts, so the name is the declaration — `...-arm64-v8a-`
    must hold that ABI and nothing else, and anything else must hold all of them.
    """
    for abi in sorted(declared_abis, key=len, reverse=True):
        if f"-{abi}-" in apk_name or apk_name.endswith(f"-{abi}.apk"):
            return f"split:{abi}", {abi}
    return "universal", set(declared_abis)


def validate_libraries(
    libraries: list[NativeLibrary],
    *,
    required_alignment: int,
    required_abis: set[str],
    expected_abis: set[str],
    require_64_bit_only: bool,
    variant: str = "universal",
    alignment_exceptions: tuple[AlignmentException, ...] = (),
) -> dict[str, object]:
    errors: list[str] = []
    seen_abis = {library.abi for library in libraries}
    seen_64_bit_abis = {library.abi for library in libraries if library.is_64_bit}

    # Checked in both directions. An ABI the artifact should not carry is payload
    # nobody signed up to ship; one it should carry but does not is a device
    # silently losing support. The old gate could see neither, because it skipped
    # every non-64-bit library before it looked at anything.
    unexpected = sorted(seen_abis - expected_abis)
    if unexpected:
        errors.append(f"{variant} APK ships unexpected ABIs: " + ", ".join(unexpected))
    absent = sorted(expected_abis - seen_abis)
    if absent:
        errors.append(f"{variant} APK is missing expected ABIs: " + ", ".join(absent))

    if require_64_bit_only:
        thirty_two_bit = sorted({library.abi for library in libraries if not library.is_64_bit})
        if thirty_two_bit:
            errors.append(
                "policy requires 64-bit only but the APK ships 32-bit ABIs: "
                + ", ".join(thirty_two_bit)
            )

    # A per-ABI split holds one ABI by definition, so demanding the full 64-bit
    # set of it would fail every split including the correct ones.
    if variant == "universal":
        missing_abis = sorted(required_abis - seen_64_bit_abis)
        if missing_abis:
            errors.append("APK missing required 64-bit ABIs: " + ", ".join(missing_abis))

    checked_segments = 0
    nested_libraries = 0
    excused: list[str] = []
    matched_exceptions: set[AlignmentException] = set()
    for library in libraries:
        if library.archive_entry is not None:
            nested_libraries += 1
        # 16 KB page alignment is a 64-bit requirement; 32-bit libraries have no
        # such contract, so they are counted above but not measured here.
        if not library.is_64_bit:
            continue
        exception = next((item for item in alignment_exceptions if item.matches(library)), None)
        for segment in library.load_segments:
            checked_segments += 1
            if segment.alignment >= required_alignment and segment.alignment % required_alignment == 0:
                continue
            if exception is not None and segment.alignment == exception.observed_alignment:
                matched_exceptions.add(exception)
                excused.append(f"{library.apk_entry} p_align {segment.alignment}")
                continue
            errors.append(
                f"{library.apk_entry} PT_LOAD offset 0x{segment.offset:x} "
                f"has p_align {segment.alignment}, expected a multiple of {required_alignment}"
            )

    # A recorded exception the artifact no longer contains is a stale suppression.
    # Failing on it is what stops the list growing into a permanent blind spot.
    if variant == "universal" or nested_libraries:
        for item in alignment_exceptions:
            if item in matched_exceptions:
                continue
            if not item.abis & expected_abis:
                continue
            errors.append(
                f"nestedArchiveAlignmentExceptions lists {item.archive_entry}!{item.inner_path} "
                f"for {', '.join(sorted(item.abis & expected_abis))}, but this APK has no such "
                "under-aligned entry; remove the exception"
            )

    if errors:
        raise NativeAlignmentError("; ".join(errors))
    return {
        "checked64BitLoadSegments": checked_segments,
        "nativeLibraryCount": len(libraries),
        "nestedArchiveLibraryCount": nested_libraries,
        "acknowledgedUnderAlignedSegments": sorted(excused),
        "seen64BitAbis": sorted(seen_64_bit_abis),
        "seenAbis": sorted(seen_abis),
        "expectedAbis": sorted(expected_abis),
        "apkVariant": variant,
    }


def validate_release_apk(repo_root: Path, policy: dict[str, Any], apk_path: Path) -> dict[str, object]:
    policy_info = validate_policy(repo_root, policy)
    skipped_archive_entries: list[str] = []
    nested_archive_report: dict[str, dict[str, int]] = {}
    libraries = inspect_apk(
        apk_path,
        skipped_archive_entries=skipped_archive_entries,
        nested_archive_report=nested_archive_report,
    )
    variant, expected_abis = expected_abis_for_apk(apk_path.name, policy_info["declaredAbis"])
    library_result = validate_libraries(
        libraries,
        required_alignment=policy_info["requiredAlignment"],
        required_abis=policy_info["requiredAbis"],
        expected_abis=expected_abis,
        require_64_bit_only=policy_info["require64BitOnly"],
        variant=variant,
        alignment_exceptions=policy_info["alignmentExceptions"],
    )
    return {
        "status": "ok",
        "policyKind": "nativePageAlignment",
        "enforcementStatus": policy_info["status"],
        "enforcedBy": policy_info["enforcedBy"],
        "packageName": policy_info["packageName"],
        "apk": str(apk_path),
        "apkSha256": sha256_file(apk_path),
        "requiredLoadSegmentAlignmentBytes": policy_info["requiredAlignment"],
        "skippedArchivePayloads": skipped_archive_entries,
        "nestedArchives": nested_archive_report,
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
