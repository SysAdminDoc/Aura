#!/usr/bin/env python3
"""Validate Aura's export schemas and their single runtime limit contract."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

CONSTANT_PATTERN = re.compile(r"const\s+val\s+(\w+)\s*=\s*([0-9][0-9_]*)L?")

CONTACT_RINGTONE_SECTION = "contactRingtones"
CONTACT_RINGTONE_LIMIT_KEY = "MAX_CONTACT_RINGTONES"
# A restore has to find the contact and the sound on a different phone from these alone.
CONTACT_RINGTONE_IDENTITY_FIELDS = (
    "contactLookupKey",
    "contactDisplayName",
    "soundSource",
    "soundId",
    "soundTitle",
)
# Field names that would carry a contact row, a media library row, or a place on one phone.
# They are compared in lower case with underscores removed, so `_id` and `contact_id` are caught.
DEVICE_ONLY_FIELD_NAMES = frozenset(
    {"id", "contactid", "rawcontactid", "rowid", "mediaid", "mediastoreid", "audioid", "ringtoneid"}
)
DEVICE_ONLY_FIELD_FRAGMENTS = ("contactid", "rowid", "mediastore", "phonenumber")
DEVICE_ONLY_FIELD_ENDINGS = ("uri", "path")


class ExportFormatError(ValueError):
    """Raised when the public transfer format and runtime contract disagree."""


def extract_data_class_fields(source_path: Path, class_name: str) -> set[str]:
    text = source_path.read_text(encoding="utf-8")
    pattern = rf"data class {class_name}\s*\((.*?)\)"
    match = re.search(pattern, text, re.DOTALL)
    if not match:
        return set()
    fields = set()
    for line in match.group(1).split("\n"):
        field = re.match(r"val\s+(\w+)\s*:", line.strip().rstrip(","))
        if field:
            fields.add(field.group(1))
    return fields


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise ExportFormatError(f"{label} must be a non-empty list")
    values: list[str] = []
    for index, item in enumerate(value):
        if not isinstance(item, str) or not item.strip():
            raise ExportFormatError(f"{label}[{index}] must be a non-empty string")
        values.append(item.strip())
    if len(values) != len(set(values)):
        raise ExportFormatError(f"{label} contains duplicates")
    return values


def check_format(
    spec: dict[str, Any], source_path: Path, item_class: str, label: str
) -> list[str]:
    errors: list[str] = []
    source_fields = extract_data_class_fields(source_path, item_class)
    if not source_fields:
        return [f"[{label}] Could not extract fields from {item_class} in {source_path}"]

    spec_fields = set(spec.get("itemFields", {}).keys())
    for field in sorted(source_fields - spec_fields):
        errors.append(
            f"[{label}] Field '{field}' exists in {item_class} but is missing from the spec"
        )
    for field in sorted(spec_fields - source_fields):
        errors.append(
            f"[{label}] Field '{field}' is in the spec but not in {item_class}"
        )

    version_policy = spec.get("versionPolicy")
    if not isinstance(version_policy, dict):
        errors.append(f"[{label}] Missing versionPolicy section")
    else:
        for key in ("bumpRule", "forwardCompat", "backwardCompat", "unsupportedVersionCopy"):
            if not version_policy.get(key):
                errors.append(f"[{label}] versionPolicy.{key} is missing or empty")
    if not spec.get("privacyExclusions"):
        errors.append(f"[{label}] privacyExclusions is missing or empty")
    return errors


def is_device_only_field(name: str) -> bool:
    """True when a field name says it holds a row id, a content address, or a file path."""
    normalized = name.replace("_", "").lower()
    return (
        normalized in DEVICE_ONLY_FIELD_NAMES
        or any(fragment in normalized for fragment in DEVICE_ONLY_FIELD_FRAGMENTS)
        or normalized.endswith(DEVICE_ONLY_FIELD_ENDINGS)
    )


def check_contact_ringtones(
    repo_root: Path, library_spec: dict[str, Any], library_source: str
) -> tuple[list[str], int]:
    """Check the contact ringtone section of the library format against its Kotlin class."""
    label = f"library.{CONTACT_RINGTONE_SECTION}"
    section = library_spec.get(CONTACT_RINGTONE_SECTION)
    if not isinstance(section, dict):
        return [f"[{label}] section is missing"], 0

    errors: list[str] = []
    item_class = section.get("itemClass")
    source_relative = section.get("sourceFile")
    spec_fields = section.get("itemFields")
    if not isinstance(spec_fields, dict) or not spec_fields:
        return [f"[{label}] itemFields must be a non-empty object"], 0
    if not isinstance(item_class, str) or not item_class.strip():
        return [f"[{label}] itemClass is missing"], 0
    if not isinstance(source_relative, str) or not (repo_root / source_relative).is_file():
        return [f"[{label}] sourceFile is missing: {source_relative}"], 0

    source_fields = extract_data_class_fields(repo_root / source_relative, item_class)
    if not source_fields:
        errors.append(f"[{label}] Could not extract fields from {item_class} in {source_relative}")
    else:
        for field in sorted(source_fields - spec_fields.keys()):
            errors.append(f"[{label}] Field '{field}' exists in {item_class} but is missing from the spec")
        for field in sorted(spec_fields.keys() - source_fields):
            errors.append(f"[{label}] Field '{field}' is in the spec but not in {item_class}")

    for field in sorted(source_fields | spec_fields.keys()):
        if is_device_only_field(field):
            errors.append(
                f"[{label}] Field '{field}' looks like a contact row id, a media row id, "
                "a content address, or a file path, and none of those may be exported"
            )

    for field in CONTACT_RINGTONE_IDENTITY_FIELDS:
        if field not in spec_fields:
            errors.append(f"[{label}] required field '{field}' is missing from the spec")
        if source_fields and field not in source_fields:
            errors.append(f"[{label}] required field '{field}' is missing from {item_class}")
    try:
        identity = require_string_list(section.get("identityFields"), f"{label}.identityFields")
    except ExportFormatError as exc:
        errors.append(str(exc))
    else:
        for field in CONTACT_RINGTONE_IDENTITY_FIELDS:
            if field not in identity:
                errors.append(f"[{label}] identityFields must list '{field}'")
        for field in identity:
            if field not in spec_fields:
                errors.append(f"[{label}] identityFields names an unknown field '{field}'")

    for key in ("importMatching", "privacyNotes"):
        try:
            require_string_list(section.get(key), f"{label}.{key}")
        except ExportFormatError as exc:
            errors.append(str(exc))
    if not isinstance(section.get("source"), str) or not section["source"].strip():
        errors.append(f"[{label}] source must say where the rows come from")

    if section.get("limitKey") != CONTACT_RINGTONE_LIMIT_KEY:
        errors.append(f"[{label}] limitKey must be {CONTACT_RINGTONE_LIMIT_KEY}")
    if CONTACT_RINGTONE_LIMIT_KEY not in (library_spec.get("limitKeys") or []):
        errors.append(f"[library] limitKeys must list {CONTACT_RINGTONE_LIMIT_KEY}")
    data_sections = library_spec.get("portableDataSections")
    if not isinstance(data_sections, dict) or CONTACT_RINGTONE_SECTION not in data_sections:
        errors.append(f"[library] portableDataSections must describe {CONTACT_RINGTONE_SECTION}")
    if not re.search(
        rf"val\s+{CONTACT_RINGTONE_SECTION}\s*:\s*List<{re.escape(item_class)}>", library_source
    ):
        errors.append(
            f"[{label}] LibraryExportFile does not carry {CONTACT_RINGTONE_SECTION} as a list of {item_class}"
        )
    return errors, len(spec_fields)


def validate_export_format(repo_root: Path, spec_relative: str) -> dict[str, Any]:
    spec_path = repo_root / spec_relative
    if not spec_path.is_file():
        raise ExportFormatError(f"{spec_relative} not found")
    raw_spec = spec_path.read_text(encoding="utf-8")
    spec = json.loads(raw_spec)
    if not isinstance(spec, dict):
        raise ExportFormatError("export format spec must be an object")

    contract_relative = spec.get("limitsContract")
    if not isinstance(contract_relative, str) or not contract_relative.strip():
        raise ExportFormatError("limitsContract must name the runtime contract")
    contract_path = repo_root / contract_relative
    if not contract_path.is_file():
        raise ExportFormatError(f"limitsContract not found: {contract_relative}")
    constants = {
        name: int(value.replace("_", ""))
        for name, value in CONSTANT_PATTERN.findall(
            contract_path.read_text(encoding="utf-8")
        )
    }
    if not constants:
        raise ExportFormatError("limitsContract declares no numeric const val entries")

    documented = set(
        require_string_list(spec.get("documentedLimitKeys"), "documentedLimitKeys")
    )
    missing_documentation = sorted(constants.keys() - documented)
    unknown_documentation = sorted(documented - constants.keys())
    errors: list[str] = []
    if missing_documentation:
        errors.append("undocumented transfer limits: " + ", ".join(missing_documentation))
    if unknown_documentation:
        errors.append("unknown documented transfer limits: " + ", ".join(unknown_documentation))

    formats = spec.get("formats")
    if not isinstance(formats, dict):
        raise ExportFormatError("formats must be an object")
    required_formats = {"favorites", "collections", "library"}
    if missing_formats := sorted(required_formats - formats.keys()):
        errors.append("missing formats: " + ", ".join(missing_formats))

    for label, format_spec in formats.items():
        if not isinstance(format_spec, dict):
            errors.append(f"[{label}] format must be an object")
            continue
        source_relative = format_spec.get("sourceFile")
        if not isinstance(source_relative, str) or not (repo_root / source_relative).is_file():
            errors.append(f"[{label}] sourceFile is missing: {source_relative}")
        for key in require_string_list(format_spec.get("limitKeys"), f"{label}.limitKeys"):
            if key not in constants:
                errors.append(f"[{label}] unknown limit key: {key}")

    favorite_spec = formats.get("favorites")
    collection_spec = formats.get("collections")
    if isinstance(favorite_spec, dict):
        errors.extend(
            check_format(
                favorite_spec,
                repo_root / str(favorite_spec.get("sourceFile")),
                str(favorite_spec.get("itemClass")),
                "favorites",
            )
        )
    if isinstance(collection_spec, dict):
        errors.extend(
            check_format(
                collection_spec,
                repo_root / str(collection_spec.get("sourceFile")),
                str(collection_spec.get("itemClass")),
                "collections",
            )
        )

    service_root = repo_root / "app/src/main/java/com/freevibe/service"
    consumers = "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted(service_root.glob("*.kt"))
        if path.resolve() != contract_path.resolve()
    )
    for key in sorted(constants):
        if f"LibraryTransferContract.{key}" not in consumers:
            errors.append(f"transfer limit is declared but unused by runtime code: {key}")

    if "silently dropped" in raw_spec.lower():
        errors.append("the public import policy still permits silent truncation")
    for relative in (
        "app/src/main/java/com/freevibe/service/FavoritesExporter.kt",
        "app/src/main/java/com/freevibe/service/CollectionExporter.kt",
        "app/src/main/java/com/freevibe/service/LibraryExporter.kt",
    ):
        text = (repo_root / relative).read_text(encoding="utf-8")
        if re.search(
            r"\.(?:take|drop)\(LibraryTransferContract\.MAX_"
            r"(?:FAVORITES|COLLECTIONS|COLLECTION_ITEMS|SEARCH_HISTORY_ITEMS)\b",
            text,
        ):
            errors.append(f"{relative} truncates a transfer at its contract limit")

    favorites_source = (
        repo_root / "app/src/main/java/com/freevibe/service/FavoritesExporter.kt"
    ).read_text(encoding="utf-8")
    library_source = (
        repo_root / "app/src/main/java/com/freevibe/service/LibraryExporter.kt"
    ).read_text(encoding="utf-8")
    collection_source = (
        repo_root / "app/src/main/java/com/freevibe/service/CollectionExporter.kt"
    ).read_text(encoding="utf-8")
    if "publishStagedDocument" not in favorites_source:
        errors.append("FavoritesExporter does not publish from a completed stage")
    if "publishStagedDocument" not in library_source:
        errors.append("LibraryExporter does not publish from a completed stage")
    if "publishAtomicLocalFile" not in collection_source:
        errors.append("CollectionExporter does not atomically publish its share file")

    contact_ringtone_field_count = 0
    library_spec = formats.get("library")
    if isinstance(library_spec, dict):
        contact_errors, contact_ringtone_field_count = check_contact_ringtones(
            repo_root, library_spec, library_source
        )
        errors.extend(contact_errors)

    if errors:
        raise ExportFormatError("; ".join(errors))
    return {
        "status": "ok",
        "formatCount": len(formats),
        "limitCount": len(constants),
        "favoriteFieldCount": len(favorite_spec.get("itemFields", {})),
        "collectionFieldCount": len(collection_spec.get("itemFields", {})),
        "contactRingtoneFieldCount": contact_ringtone_field_count,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--spec", default="docs/data/export-format.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        result = validate_export_format(Path(args.repo_root), args.spec)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
