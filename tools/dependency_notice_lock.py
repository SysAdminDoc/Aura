#!/usr/bin/env python3
"""Generate or verify the release dependency notice lockfile."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


DEFAULT_LOCKFILE = "docs/legal/dependency-notices.lock.json"


@dataclass(frozen=True)
class NoticeEntry:
    offset: int
    length: int
    name: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate or verify the Google OSS dependency notice lockfile."
    )
    parser.add_argument(
        "--mode",
        choices=("write", "check"),
        default="check",
        help="write updates the lockfile; check compares generated outputs to the lockfile.",
    )
    parser.add_argument(
        "--generated-root",
        default="app/build/generated",
        help="Root Gradle generated directory. Defaults to app/build/generated.",
    )
    parser.add_argument(
        "--variant",
        default="release",
        help="Android variant name used by releaseOssLicensesTask. Defaults to release.",
    )
    parser.add_argument(
        "--lockfile",
        default=DEFAULT_LOCKFILE,
        help=f"Lockfile path. Defaults to {DEFAULT_LOCKFILE}.",
    )
    return parser.parse_args()


def require_file(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"Required Google OSS notices output not found: {path}")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def read_dependencies(path: Path) -> list[dict[str, str]]:
    raw_dependencies = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw_dependencies, list):
        raise ValueError(f"Expected dependency JSON array in {path}")
    dependencies: list[dict[str, str]] = []
    for index, item in enumerate(raw_dependencies):
        if not isinstance(item, dict):
            raise ValueError(f"Dependency record {index} in {path} is not an object")
        group = str(item.get("group", "")).strip()
        name = str(item.get("name", "")).strip()
        version = str(item.get("version", "")).strip()
        if not group or not name or not version:
            raise ValueError(f"Dependency record {index} in {path} is incomplete: {item!r}")
        dependencies.append(
            {
                "coordinate": f"{group}:{name}:{version}",
                "group": group,
                "name": name,
                "version": version,
            }
        )
    return sorted(
        dependencies,
        key=lambda item: (item["group"], item["name"], item["version"]),
    )


def parse_metadata(path: Path) -> list[NoticeEntry]:
    entries: list[NoticeEntry] = []
    pattern = re.compile(r"^(\d+):(\d+)\s+(.+)$")
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        match = pattern.match(line)
        if not match:
            raise ValueError(f"Invalid metadata line {line_number} in {path}: {line!r}")
        entries.append(
            NoticeEntry(
                offset=int(match.group(1)),
                length=int(match.group(2)),
                name=match.group(3).strip(),
            )
        )
    return entries


def path_for_json(path: Path) -> str:
    try:
        return path.resolve().relative_to(Path.cwd().resolve()).as_posix()
    except ValueError:
        return path.as_posix()


def build_lock(*, generated_root: Path, variant: str) -> dict[str, object]:
    dependency_path = generated_root / "third_party_licenses" / variant / "dependencies.json"
    metadata_path = (
        generated_root
        / "res"
        / f"{variant}OssLicensesTask"
        / "raw"
        / "third_party_license_metadata"
    )
    licenses_path = (
        generated_root / "res" / f"{variant}OssLicensesTask" / "raw" / "third_party_licenses"
    )

    for path in (dependency_path, metadata_path, licenses_path):
        require_file(path)

    dependencies = read_dependencies(dependency_path)
    metadata = parse_metadata(metadata_path)
    license_bytes = licenses_path.read_bytes()
    notice_sections: list[dict[str, object]] = []
    for entry in metadata:
        end = entry.offset + entry.length
        if entry.offset < 0 or entry.length < 0 or end > len(license_bytes):
            raise ValueError(
                f"Invalid metadata range for {entry.name}: "
                f"offset {entry.offset}, length {entry.length}, bytes {len(license_bytes)}"
            )
        notice_text = license_bytes[entry.offset:end]
        notice_sections.append(
            {
                "length": entry.length,
                "name": entry.name,
                "noticeSha256": sha256_bytes(notice_text),
                "offset": entry.offset,
            }
        )

    return {
        "schemaVersion": 1,
        "variant": variant,
        "generatedFrom": {
            "dependencies": path_for_json(dependency_path),
            "licenseMetadata": path_for_json(metadata_path),
            "licenseText": path_for_json(licenses_path),
        },
        "inputSha256": {
            "dependencies": sha256_file(dependency_path),
            "licenseMetadata": sha256_file(metadata_path),
            "licenseText": sha256_bytes(license_bytes),
        },
        "counts": {
            "dependencyRecords": len(dependencies),
            "noticeSections": len(notice_sections),
        },
        "dependencies": dependencies,
        "noticeSections": notice_sections,
    }


def summarize_dependency_diff(
    expected: list[dict[str, str]], current: list[dict[str, str]]
) -> list[str]:
    expected_by_coordinate = {item["coordinate"]: item for item in expected}
    current_by_coordinate = {item["coordinate"]: item for item in current}
    removed = sorted(set(expected_by_coordinate) - set(current_by_coordinate))
    added = sorted(set(current_by_coordinate) - set(expected_by_coordinate))
    lines: list[str] = []
    if added:
        lines.append("Added dependencies:")
        lines.extend(f"  + {coordinate}" for coordinate in added[:40])
        if len(added) > 40:
            lines.append(f"  ... {len(added) - 40} more")
    if removed:
        lines.append("Removed dependencies:")
        lines.extend(f"  - {coordinate}" for coordinate in removed[:40])
        if len(removed) > 40:
            lines.append(f"  ... {len(removed) - 40} more")
    return lines


def summarize_notice_diff(
    expected: list[dict[str, object]], current: list[dict[str, object]]
) -> list[str]:
    expected_names = [str(item["name"]) for item in expected]
    current_names = [str(item["name"]) for item in current]
    removed = sorted(set(expected_names) - set(current_names))
    added = sorted(set(current_names) - set(expected_names))
    changed = [
        str(current_item["name"])
        for expected_item, current_item in zip(expected, current)
        if expected_item != current_item and expected_item.get("name") == current_item.get("name")
    ]
    lines: list[str] = []
    if added:
        lines.append("Added notice sections:")
        lines.extend(f"  + {name}" for name in added[:40])
        if len(added) > 40:
            lines.append(f"  ... {len(added) - 40} more")
    if removed:
        lines.append("Removed notice sections:")
        lines.extend(f"  - {name}" for name in removed[:40])
        if len(removed) > 40:
            lines.append(f"  ... {len(removed) - 40} more")
    if changed:
        lines.append("Changed notice sections:")
        lines.extend(f"  * {name}" for name in changed[:40])
        if len(changed) > 40:
            lines.append(f"  ... {len(changed) - 40} more")
    return lines


def write_lock(lockfile: Path, lock: dict[str, object]) -> None:
    lockfile.parent.mkdir(parents=True, exist_ok=True)
    lockfile.write_text(json.dumps(lock, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(
        json.dumps(
            {
                "lockfile": str(lockfile),
                "dependencyRecords": lock["counts"]["dependencyRecords"],
                "noticeSections": lock["counts"]["noticeSections"],
            },
            sort_keys=True,
        )
    )


def check_lock(lockfile: Path, current: dict[str, object]) -> int:
    require_file(lockfile)
    expected = json.loads(lockfile.read_text(encoding="utf-8"))
    if expected == current:
        print(
            json.dumps(
                {
                    "lockfile": str(lockfile),
                    "status": "ok",
                    "dependencyRecords": current["counts"]["dependencyRecords"],
                    "noticeSections": current["counts"]["noticeSections"],
                },
                sort_keys=True,
            )
        )
        return 0

    lines = ["Dependency notice lockfile is stale."]
    lines.extend(
        summarize_dependency_diff(
            expected.get("dependencies", []),
            current.get("dependencies", []),
        )
    )
    lines.extend(
        summarize_notice_diff(
            expected.get("noticeSections", []),
            current.get("noticeSections", []),
        )
    )
    if len(lines) == 1:
        lines.append("Input hashes, counts, or metadata ordering changed.")
    lines.append(
        f"Regenerate intentionally with: python tools/dependency_notice_lock.py --mode write --lockfile {lockfile.as_posix()}"
    )
    print("\n".join(lines), file=sys.stderr)
    return 1


def main() -> int:
    args = parse_args()
    current = build_lock(generated_root=Path(args.generated_root), variant=args.variant)
    lockfile = Path(args.lockfile)
    if args.mode == "write":
        write_lock(lockfile, current)
        return 0
    return check_lock(lockfile, current)


if __name__ == "__main__":
    raise SystemExit(main())
