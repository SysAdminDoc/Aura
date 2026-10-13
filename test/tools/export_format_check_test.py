from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from pathlib import Path

from tools.export_format_check import (
    ExportFormatError,
    is_device_only_field,
    validate_export_format,
)

REPO_ROOT = Path(__file__).resolve().parents[2]
SPEC = "docs/data/export-format.json"
CONTACT_RINGTONE_SOURCE = "app/src/main/java/com/freevibe/service/ContactRingtoneBackup.kt"
LIBRARY_SOURCE = "app/src/main/java/com/freevibe/service/LibraryExporter.kt"
CONTACT_RINGTONE_CLASS_HEAD = "data class ContactRingtoneExportEntry(\n"


def fixture(destination: Path) -> dict[str, object]:
    shutil.copytree(
        REPO_ROOT / "app/src/main/java/com/freevibe/service",
        destination / "app/src/main/java/com/freevibe/service",
    )
    spec_path = destination / SPEC
    spec_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(REPO_ROOT / SPEC, spec_path)
    return json.loads(spec_path.read_text(encoding="utf-8"))


class ExportFormatCheckTest(unittest.TestCase):
    def test_live_export_contract_passes(self) -> None:
        result = validate_export_format(REPO_ROOT, SPEC)

        self.assertEqual("ok", result["status"])
        self.assertEqual(3, result["formatCount"])
        self.assertGreaterEqual(result["limitCount"], 15)
        self.assertGreaterEqual(result["contactRingtoneFieldCount"], 5)

    def test_unknown_limit_key_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            spec = fixture(root)
            spec["formats"]["favorites"]["limitKeys"].append("MADE_UP_LIMIT")  # type: ignore[index]
            (root / SPEC).write_text(json.dumps(spec), encoding="utf-8")

            with self.assertRaisesRegex(ExportFormatError, "unknown limit key"):
                validate_export_format(root, SPEC)

    def test_undocumented_contract_constant_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            spec = fixture(root)
            spec["documentedLimitKeys"].remove("MAX_FAVORITES")  # type: ignore[union-attr]
            (root / SPEC).write_text(json.dumps(spec), encoding="utf-8")

            with self.assertRaisesRegex(ExportFormatError, "undocumented transfer limits"):
                validate_export_format(root, SPEC)

    def test_silent_truncation_policy_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            spec = fixture(root)
            spec["formats"]["favorites"]["importValidation"].append(  # type: ignore[index]
                "Extra rows are silently dropped"
            )
            (root / SPEC).write_text(json.dumps(spec), encoding="utf-8")

            with self.assertRaisesRegex(ExportFormatError, "silent truncation"):
                validate_export_format(root, SPEC)

    def test_runtime_item_truncation_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            fixture(root)
            source = root / "app/src/main/java/com/freevibe/service/CollectionExporter.kt"
            source.write_text(
                source.read_text(encoding="utf-8")
                + "\nval truncated = items.take(LibraryTransferContract.MAX_COLLECTION_ITEMS)\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ExportFormatError, "truncates a transfer"):
                validate_export_format(root, SPEC)

    def test_missing_contact_ringtone_section_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            spec = fixture(root)
            del spec["formats"]["library"]["contactRingtones"]  # type: ignore[index]
            (root / SPEC).write_text(json.dumps(spec), encoding="utf-8")

            with self.assertRaisesRegex(ExportFormatError, r"library\.contactRingtones\] section is missing"):
                validate_export_format(root, SPEC)

    def test_contact_ringtone_field_missing_from_spec_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            spec = fixture(root)
            del spec["formats"]["library"]["contactRingtones"]["itemFields"]["soundLicense"]  # type: ignore[index]
            (root / SPEC).write_text(json.dumps(spec), encoding="utf-8")

            with self.assertRaisesRegex(
                ExportFormatError, "Field 'soundLicense' exists in ContactRingtoneExportEntry"
            ):
                validate_export_format(root, SPEC)

    def test_contact_ringtone_identity_field_is_required(self) -> None:
        for field in ("contactLookupKey", "contactDisplayName", "soundSource", "soundId", "soundTitle"):
            with self.subTest(field=field), tempfile.TemporaryDirectory() as tmpdir:
                root = Path(tmpdir)
                spec = fixture(root)
                section = spec["formats"]["library"]["contactRingtones"]  # type: ignore[index]
                del section["itemFields"][field]
                (root / SPEC).write_text(json.dumps(spec), encoding="utf-8")

                with self.assertRaisesRegex(ExportFormatError, f"required field '{field}' is missing"):
                    validate_export_format(root, SPEC)

    def test_contact_row_id_in_the_kotlin_class_fails(self) -> None:
        for declaration, field in (
            ("    val _id: Long = 0,\n", "_id"),
            ("    val contactId: Long = 0,\n", "contactId"),
            ("    val rawContactId: Long = 0,\n", "rawContactId"),
        ):
            with self.subTest(field=field), tempfile.TemporaryDirectory() as tmpdir:
                root = Path(tmpdir)
                fixture(root)
                source = root / CONTACT_RINGTONE_SOURCE
                text = source.read_text(encoding="utf-8")
                self.assertIn(CONTACT_RINGTONE_CLASS_HEAD, text)
                source.write_text(
                    text.replace(CONTACT_RINGTONE_CLASS_HEAD, CONTACT_RINGTONE_CLASS_HEAD + declaration),
                    encoding="utf-8",
                )

                with self.assertRaisesRegex(ExportFormatError, f"Field '{field}' looks like a contact row id"):
                    validate_export_format(root, SPEC)

    def test_media_row_id_or_content_address_in_the_section_fails(self) -> None:
        for field in ("mediaStoreId", "soundRowId", "ringtoneUri", "soundPath"):
            with self.subTest(field=field), tempfile.TemporaryDirectory() as tmpdir:
                root = Path(tmpdir)
                spec = fixture(root)
                section = spec["formats"]["library"]["contactRingtones"]  # type: ignore[index]
                section["itemFields"][field] = {"type": "String", "default": "", "since": 4}
                (root / SPEC).write_text(json.dumps(spec), encoding="utf-8")
                source = root / CONTACT_RINGTONE_SOURCE
                source.write_text(
                    source.read_text(encoding="utf-8").replace(
                        CONTACT_RINGTONE_CLASS_HEAD,
                        CONTACT_RINGTONE_CLASS_HEAD + f'    val {field}: String = "",\n',
                    ),
                    encoding="utf-8",
                )

                # Documenting the field does not make it allowed.
                with self.assertRaisesRegex(ExportFormatError, f"Field '{field}' looks like"):
                    validate_export_format(root, SPEC)

    def test_contact_ringtone_limit_must_be_listed(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            spec = fixture(root)
            spec["formats"]["library"]["limitKeys"].remove("MAX_CONTACT_RINGTONES")  # type: ignore[index]
            (root / SPEC).write_text(json.dumps(spec), encoding="utf-8")

            with self.assertRaisesRegex(ExportFormatError, "limitKeys must list MAX_CONTACT_RINGTONES"):
                validate_export_format(root, SPEC)

    def test_library_file_must_carry_the_contact_ringtone_section(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            fixture(root)
            source = root / LIBRARY_SOURCE
            text = source.read_text(encoding="utf-8")
            self.assertIn("val contactRingtones: List<ContactRingtoneExportEntry>", text)
            source.write_text(
                text.replace("val contactRingtones: List<ContactRingtoneExportEntry>", "val other: List<String>"),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ExportFormatError, "LibraryExportFile does not carry contactRingtones"):
                validate_export_format(root, SPEC)

    def test_device_only_field_names(self) -> None:
        for field in ("_id", "id", "contact_id", "contactId", "RAW_CONTACT_ID", "mediaStoreId", "ringtoneUri", "filePath"):
            self.assertTrue(is_device_only_field(field), field)
        live = json.loads((REPO_ROOT / SPEC).read_text(encoding="utf-8"))
        for field in live["formats"]["library"]["contactRingtones"]["itemFields"]:
            self.assertFalse(is_device_only_field(field), field)


if __name__ == "__main__":
    unittest.main()
