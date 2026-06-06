#!/usr/bin/env python3
"""Convert Google OSS Licenses Gradle outputs into a release markdown artifact."""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class NoticeEntry:
    offset: int
    length: int
    name: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate THIRD-PARTY-NOTICES.md from Google OSS Licenses outputs."
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
        "--output",
        default="release/THIRD-PARTY-NOTICES.md",
        help="Markdown output path. Defaults to release/THIRD-PARTY-NOTICES.md.",
    )
    return parser.parse_args()


def require_file(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"Required Google OSS notices output not found: {path}")


def markdown_cell(value: object) -> str:
    text = "" if value is None else str(value)
    return text.replace("\\", "\\\\").replace("|", "\\|").replace("\n", " ")


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
                name=match.group(3),
            )
        )
    return entries


def read_dependencies(path: Path) -> list[dict[str, object]]:
    dependencies = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(dependencies, list):
        raise ValueError(f"Expected dependency JSON array in {path}")
    return sorted(
        dependencies,
        key=lambda item: (
            str(item.get("group", "")),
            str(item.get("name", "")),
            str(item.get("version", "")),
        ),
    )


def build_markdown(
    *,
    variant: str,
    dependency_path: Path,
    metadata_path: Path,
    licenses_path: Path,
    dependencies: list[dict[str, object]],
    metadata: list[NoticeEntry],
    license_bytes: bytes,
) -> str:
    lines: list[str] = [
        "# Third-Party Notices",
        "",
        "Generated from Google Play services OSS Licenses Gradle task outputs.",
        "",
        f"- Variant: `{variant}`",
        f"- Dependency records: {len(dependencies)}",
        f"- Notice sections: {len(metadata)}",
        f"- Dependency input: `{dependency_path.as_posix()}`",
        f"- Metadata input: `{metadata_path.as_posix()}`",
        f"- License text input: `{licenses_path.as_posix()}`",
        "",
        "## Dependency Coordinates",
        "",
        "| Group | Name | Version |",
        "| --- | --- | --- |",
    ]

    for dependency in dependencies:
        lines.append(
            "| {group} | {name} | {version} |".format(
                group=markdown_cell(dependency.get("group", "")),
                name=markdown_cell(dependency.get("name", "")),
                version=markdown_cell(dependency.get("version", "")),
            )
        )

    lines.extend(["", "## License Texts"])

    for entry in metadata:
        end = entry.offset + entry.length
        if entry.offset < 0 or entry.length < 0 or end > len(license_bytes):
            raise ValueError(
                f"Invalid metadata range for {entry.name}: "
                f"offset {entry.offset}, length {entry.length}, bytes {len(license_bytes)}"
            )
        text = license_bytes[entry.offset:end].decode("utf-8").strip()
        lines.extend(["", f"### {entry.name}", "", text])

    return "\n".join(lines).rstrip() + "\n"


def main() -> None:
    args = parse_args()
    generated_root = Path(args.generated_root)
    variant = args.variant
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
    markdown = build_markdown(
        variant=variant,
        dependency_path=dependency_path,
        metadata_path=metadata_path,
        licenses_path=licenses_path,
        dependencies=dependencies,
        metadata=metadata,
        license_bytes=license_bytes,
    )

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(markdown, encoding="utf-8", newline="\n")
    print(
        json.dumps(
            {
                "output": str(output_path),
                "dependencyRecords": len(dependencies),
                "noticeSections": len(metadata),
                "outputBytes": output_path.stat().st_size,
            },
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
