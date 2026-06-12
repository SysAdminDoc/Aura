from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


class RoomSchemaHistoryError(ValueError):
    pass


DATABASE_VERSION_RE = re.compile(r"version\s*=\s*(\d+)")
MIGRATION_RE = re.compile(r"\bval\s+MIGRATION_(\d+)_(\d+)\b")


def read_text(path: Path) -> str:
    if not path.is_file():
        raise RoomSchemaHistoryError(f"missing file: {path}")
    return path.read_text(encoding="utf-8")


def current_database_version(database_source: str) -> int:
    match = DATABASE_VERSION_RE.search(database_source)
    if not match:
        raise RoomSchemaHistoryError("FreeVibeDatabase version declaration was not found")
    return int(match.group(1))


def exported_schema_versions(schema_dir: Path) -> list[int]:
    if not schema_dir.is_dir():
        raise RoomSchemaHistoryError(f"schema directory is missing: {schema_dir}")
    versions: list[int] = []
    for path in schema_dir.glob("*.json"):
        try:
            file_version = int(path.stem)
        except ValueError as exc:
            raise RoomSchemaHistoryError(f"schema file is not version-named: {path.name}") from exc
        payload = json.loads(path.read_text(encoding="utf-8"))
        declared_version = payload.get("database", {}).get("version")
        if declared_version != file_version:
            raise RoomSchemaHistoryError(f"{path.name} declares database.version {declared_version}")
        versions.append(file_version)
    if not versions:
        raise RoomSchemaHistoryError("no Room schema exports found")
    return sorted(versions)


def require_contiguous(values: list[int], label: str) -> None:
    expected = list(range(values[0], values[-1] + 1))
    if values != expected:
        raise RoomSchemaHistoryError(f"{label} must be contiguous: expected {expected}, got {values}")


def validate_room_schema_history(
    repo_root: Path,
    supported_export_start: int = 9,
) -> dict[str, int | str | list[int]]:
    database_source = read_text(repo_root / "app/src/main/java/com/freevibe/data/local/Database.kt")
    migrations_source = read_text(repo_root / "app/src/main/java/com/freevibe/data/local/DatabaseMigrations.kt")
    gradle_source = read_text(repo_root / "app/build.gradle.kts")
    migration_test_source = read_text(repo_root / "app/src/androidTest/java/com/freevibe/data/local/DatabaseMigrationTest.kt")

    current_version = current_database_version(database_source)
    schema_versions = exported_schema_versions(
        repo_root / "app/schemas/com.freevibe.data.local.FreeVibeDatabase"
    )
    require_contiguous(schema_versions, "exported schema versions")
    if schema_versions[0] != supported_export_start:
        raise RoomSchemaHistoryError(
            f"exported schema history must start at supported version {supported_export_start}, got {schema_versions[0]}"
        )
    if schema_versions[-1] != current_version:
        raise RoomSchemaHistoryError(
            f"latest schema export {schema_versions[-1]} does not match database version {current_version}"
        )

    declared_migrations = {
        (int(start), int(end))
        for start, end in MIGRATION_RE.findall(migrations_source)
    }
    required_migrations = {(version, version + 1) for version in range(1, current_version)}
    missing = sorted(required_migrations - declared_migrations)
    if missing:
        raise RoomSchemaHistoryError(f"missing adjacent migrations: {missing}")
    for start, end in required_migrations:
        token = f"MIGRATION_{start}_{end}"
        if token not in migrations_source.split("val ALL_MIGRATIONS", 1)[-1]:
            raise RoomSchemaHistoryError(f"{token} is not listed in ALL_MIGRATIONS")

    for required_term in (
        'arg("room.schemaLocation"',
        'getByName("androidTest").assets.srcDir("$projectDir/schemas")',
    ):
        if required_term not in gradle_source:
            raise RoomSchemaHistoryError(f"app/build.gradle.kts missing Room schema wiring: {required_term}")

    for required_term in (
        "migrate8To9_preservesCachedWallpaperAndBackfillsMetadataDefaults",
        "migrateEveryExportedSchemaVersionToCurrent",
        "migrate14To16_preservesRepresentativeRowsAndBackfillsAvailabilityDefaults",
        f"EXPORTED_SCHEMA_START_VERSION = {supported_export_start}",
        f"CURRENT_SCHEMA_VERSION = {current_version}",
    ):
        if required_term not in migration_test_source:
            raise RoomSchemaHistoryError(f"DatabaseMigrationTest missing coverage term: {required_term}")

    return {
        "status": "ok",
        "databaseVersion": current_version,
        "schemaVersions": schema_versions,
        "migrationCount": len(required_migrations),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate Aura Room schema history and migration gates.")
    parser.add_argument("--repo-root", default=".", type=Path)
    parser.add_argument("--supported-export-start", default=9, type=int)
    args = parser.parse_args()
    result = validate_room_schema_history(args.repo_root.resolve(), args.supported_export_start)
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
