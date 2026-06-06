#!/usr/bin/env python3
"""Archive raw Google OSS Licenses outputs for release review."""

from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from pathlib import Path


FIXED_ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create a deterministic archive of Google OSS raw notice inputs."
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
        default="release/GOOGLE-OSS-RAW-INPUTS.zip",
        help="Output archive path. Defaults to release/GOOGLE-OSS-RAW-INPUTS.zip.",
    )
    return parser.parse_args()


def require_file(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"Required Google OSS raw input not found: {path}")
    if path.stat().st_size <= 0:
        raise ValueError(f"Required Google OSS raw input is empty: {path}")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def path_for_json(path: Path) -> str:
    try:
        return path.resolve().relative_to(Path.cwd().resolve()).as_posix()
    except ValueError:
        return path.as_posix()


def raw_input_paths(*, generated_root: Path, variant: str) -> dict[str, Path]:
    return {
        "dependencies.json": (
            generated_root / "third_party_licenses" / variant / "dependencies.json"
        ),
        "third_party_license_metadata": (
            generated_root
            / "res"
            / f"{variant}OssLicensesTask"
            / "raw"
            / "third_party_license_metadata"
        ),
        "third_party_licenses": (
            generated_root
            / "res"
            / f"{variant}OssLicensesTask"
            / "raw"
            / "third_party_licenses"
        ),
    }


def manifest_for(paths: dict[str, Path], *, variant: str) -> dict[str, object]:
    files: list[dict[str, object]] = []
    for archive_name, path in sorted(paths.items()):
        data = path.read_bytes()
        files.append(
            {
                "archiveName": archive_name,
                "sha256": sha256_bytes(data),
                "size": len(data),
                "sourcePath": path_for_json(path),
            }
        )
    return {
        "schemaVersion": 1,
        "variant": variant,
        "files": files,
    }


def write_zip_entry(archive: zipfile.ZipFile, name: str, data: bytes) -> None:
    info = zipfile.ZipInfo(name, FIXED_ZIP_TIMESTAMP)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o644 << 16
    archive.writestr(info, data)


def create_archive(*, generated_root: Path, variant: str, output: Path) -> dict[str, object]:
    paths = raw_input_paths(generated_root=generated_root, variant=variant)
    for path in paths.values():
        require_file(path)

    manifest = manifest_for(paths, variant=variant)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w") as archive:
        write_zip_entry(
            archive,
            "MANIFEST.json",
            json.dumps(manifest, indent=2, sort_keys=True).encode("utf-8") + b"\n",
        )
        for archive_name, path in sorted(paths.items()):
            write_zip_entry(archive, archive_name, path.read_bytes())

    archive_bytes = output.read_bytes()
    return {
        "archiveSha256": sha256_bytes(archive_bytes),
        "archiveSize": len(archive_bytes),
        "files": manifest["files"],
        "output": str(output),
        "status": "ok",
        "variant": variant,
    }


def main() -> None:
    args = parse_args()
    result = create_archive(
        generated_root=Path(args.generated_root),
        variant=args.variant,
        output=Path(args.output),
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
