#!/usr/bin/env python3
"""Inventory native/copyleft-sensitive artifacts from the local Gradle cache."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import re
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path


DEFAULT_LOCKFILE = "docs/legal/native-compliance.lock.json"

DEFAULT_COORDINATES = (
    "io.github.junkfood02.youtubedl-android:common:0.18.1",
    "io.github.junkfood02.youtubedl-android:library:0.18.1",
    "io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1",
    "com.github.teamnewpipe:NewPipeExtractor:v0.24.8",
    "com.github.teamnewpipe.NewPipeExtractor:NewPipeExtractor:v0.24.8",
    "com.github.teamnewpipe.NewPipeExtractor:extractor:v0.24.8",
    "com.github.teamnewpipe.NewPipeExtractor:timeago-generator:v0.24.8",
    "com.github.teamnewpipe.NewPipeExtractor:timeago-parser:v0.24.8",
)


@dataclass(frozen=True)
class Coordinate:
    group: str
    name: str
    version: str

    @property
    def label(self) -> str:
        return f"{self.group}:{self.name}:{self.version}"


@dataclass(frozen=True)
class SourceReference:
    license_id: str
    source_url: str
    license_url: str
    review_note: str


@dataclass(frozen=True)
class PayloadReference:
    name: str
    license_id: str
    evidence_url: str
    review_note: str


SOURCE_REFERENCES = {
    "io.github.junkfood02.youtubedl-android:common:0.18.1": SourceReference(
        license_id="GPL-3.0",
        source_url="https://github.com/yausername/youtubedl-android/tree/master/common",
        license_url="https://raw.githubusercontent.com/yausername/youtubedl-android/master/LICENSE",
        review_note="Wrapper component; no native payload entries matched this inventory.",
    ),
    "io.github.junkfood02.youtubedl-android:library:0.18.1": SourceReference(
        license_id="GPL-3.0",
        source_url="https://github.com/yausername/youtubedl-android/tree/master/library",
        license_url="https://raw.githubusercontent.com/yausername/youtubedl-android/master/LICENSE",
        review_note="Bundles yt-dlp plus Python/native runtime payloads; exact nested payload source evidence is required for release review.",
    ),
    "io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1": SourceReference(
        license_id="LGPL-2.1-or-later or GPL-2.0-or-later depending on build flags",
        source_url="https://github.com/yausername/youtubedl-android/tree/master/ffmpeg",
        license_url="https://www.ffmpeg.org/legal.html",
        review_note="FFmpeg build mode cannot be inferred from the AAR name; exact configure/source correspondence must be published.",
    ),
    "com.github.teamnewpipe:NewPipeExtractor:v0.24.8": SourceReference(
        license_id="GPL-3.0-or-later",
        source_url="https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.24.8",
        license_url="https://raw.githubusercontent.com/TeamNewPipe/NewPipeExtractor/v0.24.8/LICENSE",
        review_note="Marker coordinate; inspect transitive NewPipeExtractor modules for real classes.",
    ),
    "com.github.teamnewpipe.NewPipeExtractor:NewPipeExtractor:v0.24.8": SourceReference(
        license_id="GPL-3.0-or-later",
        source_url="https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.24.8",
        license_url="https://raw.githubusercontent.com/TeamNewPipe/NewPipeExtractor/v0.24.8/LICENSE",
        review_note="Aggregator module; no native payload entries matched this inventory.",
    ),
    "com.github.teamnewpipe.NewPipeExtractor:extractor:v0.24.8": SourceReference(
        license_id="GPL-3.0-or-later",
        source_url="https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.24.8",
        license_url="https://raw.githubusercontent.com/TeamNewPipe/NewPipeExtractor/v0.24.8/LICENSE",
        review_note="Main extractor module used by Aura's YouTube metadata path.",
    ),
    "com.github.teamnewpipe.NewPipeExtractor:timeago-generator:v0.24.8": SourceReference(
        license_id="GPL-3.0-or-later",
        source_url="https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.24.8",
        license_url="https://raw.githubusercontent.com/TeamNewPipe/NewPipeExtractor/v0.24.8/LICENSE",
        review_note="Transitive NewPipeExtractor time parsing helper.",
    ),
    "com.github.teamnewpipe.NewPipeExtractor:timeago-parser:v0.24.8": SourceReference(
        license_id="GPL-3.0-or-later",
        source_url="https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.24.8",
        license_url="https://raw.githubusercontent.com/TeamNewPipe/NewPipeExtractor/v0.24.8/LICENSE",
        review_note="Transitive NewPipeExtractor time parsing helper.",
    ),
}


PAYLOAD_REFERENCES = (
    PayloadReference(
        name="yt-dlp payload",
        license_id="Unlicense for source; zipimport release file also includes ISC/MIT components",
        evidence_url="https://github.com/yt-dlp/yt-dlp/tree/2025.11.12",
        review_note="The report extracts the bundled version, origin, and git head from res/raw/ytdlp.",
    ),
    PayloadReference(
        name="Python runtime payload",
        license_id="PSF-2.0 plus packaged dependency licenses",
        evidence_url="https://docs.python.org/3.12/license.html",
        review_note="The report extracts python3.12 directories from libpython.zip.so; exact Termux package source set still needs release-owner review.",
    ),
    PayloadReference(
        name="QuickJS runtime payload",
        license_id="MIT",
        evidence_url="https://bellard.org/quickjs/",
        review_note="libqjs.so is present in youtubedl-android library AARs; exact packaged QuickJS revision is not encoded in the AAR.",
    ),
    PayloadReference(
        name="FFmpeg payload",
        license_id="LGPL-2.1-or-later or GPL-2.0-or-later depending on build flags",
        evidence_url="https://www.ffmpeg.org/legal.html",
        review_note="FFmpeg legal guidance requires matching source and configure/build evidence for the shipped binaries.",
    ),
    PayloadReference(
        name="youtubedl-android FFmpeg build notes",
        license_id="Build evidence, not a separate license",
        evidence_url="https://raw.githubusercontent.com/yausername/youtubedl-android/master/BUILD_FFMPEG.md",
        review_note="Upstream describes a Termux package build path; Aura still needs exact source/build correspondence for the resolved 0.18.1 AAR.",
    ),
    PayloadReference(
        name="youtubedl-android Python build notes",
        license_id="Build evidence, not a separate license",
        evidence_url="https://raw.githubusercontent.com/yausername/youtubedl-android/master/BUILD_PYTHON.md",
        review_note="Upstream describes a Termux package build path; the resolved AAR contains python3.12 despite older README/build-note examples.",
    ),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create a native/copyleft payload inventory from Gradle cache artifacts."
    )
    parser.add_argument(
        "--mode",
        choices=("report", "write-lock", "check-lock"),
        default="report",
        help="report writes markdown; write-lock updates the JSON lock; check-lock verifies the JSON lock.",
    )
    parser.add_argument(
        "--gradle-cache",
        default=str(Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1"),
        help="Gradle modules cache root. Defaults to ~/.gradle/caches/modules-2/files-2.1.",
    )
    parser.add_argument(
        "--coordinate",
        action="append",
        dest="coordinates",
        help="Coordinate to inspect as group:name:version. Can be repeated.",
    )
    parser.add_argument(
        "--output",
        default="build/reports/native-compliance-inventory.md",
        help="Markdown output path. Defaults to build/reports/native-compliance-inventory.md.",
    )
    parser.add_argument(
        "--apk",
        default=None,
        help="Optional final APK path to inspect in addition to resolved Gradle cache artifacts.",
    )
    parser.add_argument(
        "--lockfile",
        default=DEFAULT_LOCKFILE,
        help=f"Native compliance lockfile path. Defaults to {DEFAULT_LOCKFILE}.",
    )
    return parser.parse_args()


def parse_coordinate(value: str) -> Coordinate:
    parts = value.split(":")
    if len(parts) != 3 or not all(parts):
        raise ValueError(f"Invalid coordinate {value!r}; expected group:name:version")
    return Coordinate(group=parts[0], name=parts[1], version=parts[2])


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def require_file(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"Required native compliance lockfile not found: {path}")


def markdown_cell(value: object) -> str:
    text = "" if value is None else str(value)
    return text.replace("\\", "\\\\").replace("|", "\\|").replace("\n", " ")


def display_path(path: Path) -> str:
    cwd = Path.cwd().resolve()
    try:
        return path.resolve().relative_to(cwd).as_posix()
    except ValueError:
        pass
    home = Path.home().resolve()
    try:
        return "~/" + path.resolve().relative_to(home).as_posix()
    except ValueError:
        return path.as_posix()


def find_artifacts(cache_root: Path, coordinate: Coordinate) -> list[Path]:
    base = cache_root / coordinate.group / coordinate.name / coordinate.version
    if not base.is_dir():
        return []
    return sorted(
        path
        for path in base.rglob("*")
        if path.is_file() and path.suffix.lower() in {".aar", ".jar", ".pom", ".module"}
    )


def is_interesting_entry(name: str) -> bool:
    lower = name.lower()
    return (
        lower.startswith("jni/")
        or lower.startswith("lib/")
        or lower.startswith("assets/")
        or lower.startswith("res/raw/")
        or "license" in lower
        or "copying" in lower
        or "notice" in lower
        or "ffmpeg" in lower
        or "ffprobe" in lower
        or "python" in lower
        or "ytdlp" in lower
        or "yt-dlp" in lower
        or "libqjs" in lower
    )


def nested_zip_summary(data: bytes) -> tuple[int | None, list[str], dict[str, str]]:
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as nested:
            names = nested.namelist()
            selected = [
                name
                for name in names
                if is_interesting_entry(name)
                or name.startswith("usr/lib/")
                or name in {"yt_dlp/version.py", "yt_dlp_ejs/_version.py"}
            ][:30]
            facts: dict[str, str] = {}
            if "yt_dlp/version.py" in names:
                text = nested.read("yt_dlp/version.py").decode("utf-8", "replace")
                version = re.search(r"__version__\s*=\s*['\"]([^'\"]+)['\"]", text)
                git_head = re.search(r"RELEASE_GIT_HEAD\s*=\s*['\"]([^'\"]+)['\"]", text)
                origin = re.search(r"ORIGIN\s*=\s*['\"]([^'\"]+)['\"]", text)
                if version:
                    facts["yt-dlp version"] = version.group(1)
                if git_head:
                    facts["yt-dlp git head"] = git_head.group(1)
                if origin:
                    facts["yt-dlp origin"] = origin.group(1)
            python_dirs = sorted(
                {
                    match.group(1)
                    for name in names
                    for match in [re.match(r"usr/lib/(python\d+\.\d+)/", name)]
                    if match
                }
            )
            if python_dirs:
                facts["python payload"] = ", ".join(python_dirs)
            return len(names), selected, facts
    except zipfile.BadZipFile:
        return None, [], {}


def inspect_zip_artifact(path: Path) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    try:
        with zipfile.ZipFile(path) as archive:
            for info in archive.infolist():
                if not is_interesting_entry(info.filename):
                    continue
                nested_count = None
                nested_selected: list[str] = []
                facts: dict[str, str] = {}
                if info.file_size and (
                    info.filename.endswith(".zip.so") or info.filename == "res/raw/ytdlp"
                ):
                    nested_count, nested_selected, facts = nested_zip_summary(
                        archive.read(info.filename)
                    )
                rows.append(
                    {
                        "entry": info.filename,
                        "size": info.file_size,
                        "nested_count": nested_count,
                        "nested_selected": nested_selected,
                        "facts": facts,
                    }
                )
    except zipfile.BadZipFile:
        return rows
    return rows


def normalize_payload_rows(rows: list[dict[str, object]]) -> list[dict[str, object]]:
    normalized: list[dict[str, object]] = []
    for row in sorted(rows, key=lambda item: str(item["entry"])):
        normalized.append(
            {
                "entry": str(row["entry"]),
                "facts": dict(sorted(row["facts"].items())),
                "nestedCount": row["nested_count"],
                "nestedSample": list(row["nested_selected"]),
                "size": row["size"],
            }
        )
    return normalized


def build_lock(cache_root: Path, coordinates: list[Coordinate]) -> dict[str, object]:
    coordinate_records: list[dict[str, object]] = []
    artifact_count = 0
    payload_entry_count = 0
    for coordinate in coordinates:
        artifacts = find_artifacts(cache_root, coordinate)
        artifact_records: list[dict[str, object]] = []
        for artifact in artifacts:
            rows: list[dict[str, object]] = []
            if artifact.suffix.lower() in {".aar", ".jar"}:
                rows = inspect_zip_artifact(artifact)
            payload_entries = normalize_payload_rows(rows)
            payload_entry_count += len(payload_entries)
            artifact_count += 1
            artifact_records.append(
                {
                    "fileName": artifact.name,
                    "kind": artifact.suffix.lower().lstrip("."),
                    "payloadEntries": payload_entries,
                    "sha256": sha256(artifact),
                    "size": artifact.stat().st_size,
                }
            )
        coordinate_records.append(
            {
                "artifacts": sorted(
                    artifact_records,
                    key=lambda item: (str(item["kind"]), str(item["fileName"])),
                ),
                "coordinate": coordinate.label,
                "missing": not bool(artifact_records),
            }
        )

    return {
        "schemaVersion": 1,
        "gradleCache": display_path(cache_root),
        "counts": {
            "artifactRecords": artifact_count,
            "coordinates": len(coordinate_records),
            "payloadEntries": payload_entry_count,
        },
        "coordinates": coordinate_records,
    }


def write_lock(lockfile: Path, lock: dict[str, object]) -> None:
    lockfile.parent.mkdir(parents=True, exist_ok=True)
    lockfile.write_text(json.dumps(lock, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(
        json.dumps(
            {
                "artifactRecords": lock["counts"]["artifactRecords"],
                "coordinates": lock["counts"]["coordinates"],
                "lockfile": str(lockfile),
                "payloadEntries": lock["counts"]["payloadEntries"],
            },
            sort_keys=True,
        )
    )


def artifact_keys(lock: dict[str, object]) -> set[str]:
    keys: set[str] = set()
    for coordinate in lock.get("coordinates", []):
        label = str(coordinate.get("coordinate", ""))
        for artifact in coordinate.get("artifacts", []):
            keys.add(f"{label}:{artifact.get('fileName', '')}")
    return keys


def check_lock(lockfile: Path, current: dict[str, object]) -> int:
    require_file(lockfile)
    expected = json.loads(lockfile.read_text(encoding="utf-8"))
    if expected == current:
        print(
            json.dumps(
                {
                    "artifactRecords": current["counts"]["artifactRecords"],
                    "coordinates": current["counts"]["coordinates"],
                    "lockfile": str(lockfile),
                    "payloadEntries": current["counts"]["payloadEntries"],
                    "status": "ok",
                },
                sort_keys=True,
            )
        )
        return 0

    expected_keys = artifact_keys(expected)
    current_keys = artifact_keys(current)
    added = sorted(current_keys - expected_keys)
    removed = sorted(expected_keys - current_keys)
    lines = ["Native compliance lockfile is stale."]
    if added:
        lines.append("Added artifacts:")
        lines.extend(f"  + {key}" for key in added[:40])
        if len(added) > 40:
            lines.append(f"  ... {len(added) - 40} more")
    if removed:
        lines.append("Removed artifacts:")
        lines.extend(f"  - {key}" for key in removed[:40])
        if len(removed) > 40:
            lines.append(f"  ... {len(removed) - 40} more")
    if not added and not removed:
        lines.append("Artifact hashes, payload facts, or payload entries changed.")
    lines.append(
        f"Regenerate intentionally with: python tools/native_compliance_inventory.py --mode write-lock --lockfile {lockfile.as_posix()}"
    )
    print("\n".join(lines), file=sys.stderr)
    return 1


def build_markdown(
    cache_root: Path, coordinates: list[Coordinate], apk_path: Path | None = None
) -> str:
    lines: list[str] = [
        "# Native Compliance Inventory",
        "",
        "Generated from local Gradle cache artifacts. This is a factual payload inventory for release review; it does not replace legal review.",
        "",
        f"- Gradle cache: `{display_path(cache_root)}`",
        "",
        "## Coordinate Summary",
        "",
        "| Coordinate | Artifact | Bytes | SHA-256 |",
        "| --- | --- | ---: | --- |",
    ]

    details: list[tuple[Coordinate, Path, list[dict[str, object]]]] = []
    for coordinate in coordinates:
        artifacts = find_artifacts(cache_root, coordinate)
        if not artifacts:
            lines.append(f"| `{markdown_cell(coordinate.label)}` | Not found |  |  |")
            continue
        for artifact in artifacts:
            lines.append(
                "| `{coord}` | `{artifact}` | {size} | `{digest}` |".format(
                    coord=markdown_cell(coordinate.label),
                    artifact=markdown_cell(artifact.name),
                    size=artifact.stat().st_size,
                    digest=sha256(artifact),
                )
            )
            if artifact.suffix.lower() in {".aar", ".jar"}:
                details.append((coordinate, artifact, inspect_zip_artifact(artifact)))

    lines.extend(
        [
            "",
            "## Upstream Source And License References",
            "",
            "| Coordinate | License | Source | License text | Review note |",
            "| --- | --- | --- | --- | --- |",
        ]
    )

    for coordinate in coordinates:
        reference = SOURCE_REFERENCES.get(coordinate.label)
        if reference is None:
            lines.append(
                f"| `{markdown_cell(coordinate.label)}` | Unknown |  |  | No source reference configured. |"
            )
            continue
        lines.append(
            "| `{coord}` | {license_id} | {source} | {license_url} | {note} |".format(
                coord=markdown_cell(coordinate.label),
                license_id=markdown_cell(reference.license_id),
                source=markdown_cell(reference.source_url),
                license_url=markdown_cell(reference.license_url),
                note=markdown_cell(reference.review_note),
            )
        )

    lines.extend(
        [
            "",
            "## Payload Source And Build References",
            "",
            "| Payload | License / status | Evidence | Review note |",
            "| --- | --- | --- | --- |",
        ]
    )
    for reference in PAYLOAD_REFERENCES:
        lines.append(
            "| {name} | {license_id} | {evidence} | {note} |".format(
                name=markdown_cell(reference.name),
                license_id=markdown_cell(reference.license_id),
                evidence=markdown_cell(reference.evidence_url),
                note=markdown_cell(reference.review_note),
            )
        )

    if apk_path is not None:
        lines.extend(
            [
                "",
                "## APK Payload Entries",
                "",
            ]
        )
        if apk_path.is_file():
            lines.extend(
                [
                    f"- APK: `{display_path(apk_path)}`",
                    f"- Bytes: `{apk_path.stat().st_size}`",
                    f"- SHA-256: `{sha256(apk_path)}`",
                    "",
                ]
            )
            apk_rows = inspect_zip_artifact(apk_path)
            if apk_rows:
                lines.extend(
                    [
                        "| Entry | Bytes | Nested entries | Facts |",
                        "| --- | ---: | ---: | --- |",
                    ]
                )
                for row in apk_rows:
                    facts = row["facts"]
                    fact_text = "; ".join(f"{key}: {value}" for key, value in facts.items())
                    if row["nested_selected"]:
                        selected = ", ".join(row["nested_selected"][:8])
                        if len(row["nested_selected"]) > 8:
                            selected += ", ..."
                        fact_text = (
                            f"{fact_text}; nested sample: {selected}"
                            if fact_text
                            else f"nested sample: {selected}"
                        )
                    lines.append(
                        "| `{entry}` | {size} | {nested} | {facts} |".format(
                            entry=markdown_cell(row["entry"]),
                            size=row["size"],
                            nested="" if row["nested_count"] is None else row["nested_count"],
                            facts=markdown_cell(fact_text),
                        )
                    )
            else:
                lines.append("No native/payload/license entries matched the inventory filters.")
        else:
            lines.append(f"APK path was requested but not found: `{markdown_cell(apk_path)}`")

    lines.extend(["", "## Payload Entries"])

    for coordinate, artifact, rows in details:
        lines.extend(["", f"### `{coordinate.label}` - `{artifact.name}`", ""])
        if not rows:
            lines.append("No native/payload/license entries matched the inventory filters.")
            continue
        lines.extend(
            [
                "| Entry | Bytes | Nested entries | Facts |",
                "| --- | ---: | ---: | --- |",
            ]
        )
        for row in rows:
            facts = row["facts"]
            fact_text = "; ".join(f"{key}: {value}" for key, value in facts.items())
            if row["nested_selected"]:
                selected = ", ".join(row["nested_selected"][:8])
                if len(row["nested_selected"]) > 8:
                    selected += ", ..."
                fact_text = f"{fact_text}; nested sample: {selected}" if fact_text else f"nested sample: {selected}"
            lines.append(
                "| `{entry}` | {size} | {nested} | {facts} |".format(
                    entry=markdown_cell(row["entry"]),
                    size=row["size"],
                    nested="" if row["nested_count"] is None else row["nested_count"],
                    facts=markdown_cell(fact_text),
                )
            )

    lines.extend(
        [
            "",
            "## Release Review Notes",
            "",
            "- Confirm exact upstream source and license text for every artifact listed above before public release expansion.",
            "- FFmpeg payloads require exact binary/source correspondence and build configuration evidence.",
            "- youtubedl-android library payloads include yt-dlp and Python assets that need version/source disclosure.",
            "- Generated Google OSS notices cover Maven coordinates but do not inspect these nested native payloads.",
        ]
    )
    return "\n".join(lines).rstrip() + "\n"


def main() -> int:
    args = parse_args()
    cache_root = Path(os.path.expanduser(args.gradle_cache)).resolve()
    coordinates = [parse_coordinate(value) for value in (args.coordinates or DEFAULT_COORDINATES)]
    if args.mode in {"write-lock", "check-lock"}:
        lock = build_lock(cache_root, coordinates)
        lockfile = Path(args.lockfile)
        if args.mode == "write-lock":
            write_lock(lockfile, lock)
            return 0
        return check_lock(lockfile, lock)

    apk_path = Path(args.apk).resolve() if args.apk else None
    markdown = build_markdown(cache_root, coordinates, apk_path=apk_path)
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(markdown, encoding="utf-8", newline="\n")
    print(f"Wrote {output_path} ({output_path.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
