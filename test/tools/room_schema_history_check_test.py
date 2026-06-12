from __future__ import annotations

import shutil
import tempfile
import unittest
from pathlib import Path

from tools.room_schema_history_check import RoomSchemaHistoryError, validate_room_schema_history


REPO_ROOT = Path(__file__).resolve().parents[2]


class RoomSchemaHistoryCheckTest(unittest.TestCase):
    def test_live_room_schema_history_passes(self) -> None:
        result = validate_room_schema_history(REPO_ROOT)

        self.assertEqual("ok", result["status"])
        self.assertEqual(16, result["databaseVersion"])
        self.assertEqual(list(range(9, 17)), result["schemaVersions"])

    def test_rejects_missing_latest_schema(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            (repo / "app/schemas/com.freevibe.data.local.FreeVibeDatabase/16.json").unlink()

            with self.assertRaises(RoomSchemaHistoryError):
                validate_room_schema_history(repo)

    def test_rejects_missing_migration_declaration(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            migrations = repo / "app/src/main/java/com/freevibe/data/local/DatabaseMigrations.kt"
            migrations.write_text(
                migrations.read_text(encoding="utf-8").replace("val MIGRATION_15_16", "val REMOVED_15_16", 1),
                encoding="utf-8",
            )

            with self.assertRaises(RoomSchemaHistoryError):
                validate_room_schema_history(repo)

    def test_rejects_missing_android_test_coverage(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            test_file = repo / "app/src/androidTest/java/com/freevibe/data/local/DatabaseMigrationTest.kt"
            test_file.write_text(
                test_file.read_text(encoding="utf-8").replace("migrateEveryExportedSchemaVersionToCurrent", ""),
                encoding="utf-8",
            )

            with self.assertRaises(RoomSchemaHistoryError):
                validate_room_schema_history(repo)


def copy_required_tree(destination: Path) -> Path:
    paths = [
        "app/build.gradle.kts",
        "app/src/main/java/com/freevibe/data/local/Database.kt",
        "app/src/main/java/com/freevibe/data/local/DatabaseMigrations.kt",
        "app/src/androidTest/java/com/freevibe/data/local/DatabaseMigrationTest.kt",
    ]
    for relative_path in paths:
        source = REPO_ROOT / relative_path
        target = destination / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")

    source_schema_dir = REPO_ROOT / "app/schemas/com.freevibe.data.local.FreeVibeDatabase"
    target_schema_dir = destination / "app/schemas/com.freevibe.data.local.FreeVibeDatabase"
    target_schema_dir.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source_schema_dir, target_schema_dir)
    return destination


if __name__ == "__main__":
    unittest.main()
