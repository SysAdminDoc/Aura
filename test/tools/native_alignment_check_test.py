import io
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from tools import native_alignment_check


def minimal_elf64(load_alignments):
    header = bytearray(64)
    header[:4] = b"\x7fELF"
    header[4] = 2
    header[5] = 1
    phoff = 64
    phentsize = 56
    phnum = len(load_alignments)
    struct.pack_into("<Q", header, 32, phoff)
    struct.pack_into("<H", header, 54, phentsize)
    struct.pack_into("<H", header, 56, phnum)
    program_headers = bytearray(phentsize * phnum)
    for index, alignment in enumerate(load_alignments):
        base = index * phentsize
        struct.pack_into("<I", program_headers, base, native_alignment_check.PT_LOAD)
        struct.pack_into("<Q", program_headers, base + 8, index * 4096)
        struct.pack_into("<Q", program_headers, base + 16, index * 4096)
        struct.pack_into("<Q", program_headers, base + 48, alignment)
    return bytes(header + program_headers)


def minimal_elf32(load_alignments):
    header = bytearray(52)
    header[:4] = b"\x7fELF"
    header[4] = 1
    header[5] = 1
    phoff = 52
    phentsize = 32
    phnum = len(load_alignments)
    struct.pack_into("<I", header, 28, phoff)
    struct.pack_into("<H", header, 42, phentsize)
    struct.pack_into("<H", header, 44, phnum)
    program_headers = bytearray(phentsize * phnum)
    for index, alignment in enumerate(load_alignments):
        base = index * phentsize
        struct.pack_into("<I", program_headers, base, native_alignment_check.PT_LOAD)
        struct.pack_into("<I", program_headers, base + 4, index * 4096)
        struct.pack_into("<I", program_headers, base + 8, index * 4096)
        struct.pack_into("<I", program_headers, base + 28, alignment)
    return bytes(header + program_headers)


def nested_zip(entries):
    """Real ZIP bytes, the way youtubedl-android ships FFmpeg and CPython."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for name, data in entries.items():
            archive.writestr(name, data)
    return buffer.getvalue()


def write_apk(entries):
    temp_dir = tempfile.TemporaryDirectory()
    apk_path = Path(temp_dir.name) / "release.apk"
    with zipfile.ZipFile(apk_path, "w") as archive:
        for name, data in entries.items():
            archive.writestr(name, data)
    return temp_dir, apk_path


class NativeAlignmentCheckTest(unittest.TestCase):
    def test_accepts_16kb_aligned_64_bit_load_segments(self):
        temp_dir, apk_path = write_apk(
            {
                "lib/arm64-v8a/libok.so": minimal_elf64([16384, 65536]),
                "lib/armeabi-v7a/liblegacy.so": minimal_elf32([4096]),
            }
        )
        self.addCleanup(temp_dir.cleanup)

        libraries = native_alignment_check.inspect_apk(apk_path)
        result = native_alignment_check.validate_libraries(
            libraries,
            required_alignment=16384,
            required_abis={"arm64-v8a"},
            expected_abis={"arm64-v8a", "armeabi-v7a"},
            require_64_bit_only=False,
        )

        self.assertEqual(result["checked64BitLoadSegments"], 2)
        self.assertEqual(result["seen64BitAbis"], ["arm64-v8a"])
        self.assertEqual(result["seenAbis"], ["arm64-v8a", "armeabi-v7a"])
        self.assertEqual(result["apkVariant"], "universal")

    def test_rejects_4kb_aligned_64_bit_load_segment(self):
        temp_dir, apk_path = write_apk(
            {
                "lib/arm64-v8a/libbad.so": minimal_elf64([4096]),
            }
        )
        self.addCleanup(temp_dir.cleanup)

        libraries = native_alignment_check.inspect_apk(apk_path)
        with self.assertRaisesRegex(native_alignment_check.NativeAlignmentError, "p_align 4096"):
            native_alignment_check.validate_libraries(
                libraries,
                required_alignment=16384,
                required_abis={"arm64-v8a"},
                expected_abis={"arm64-v8a"},
                require_64_bit_only=True,
            )

    def test_rejects_missing_required_64_bit_abi(self):
        temp_dir, apk_path = write_apk(
            {
                "lib/armeabi-v7a/liblegacy.so": minimal_elf32([4096]),
            }
        )
        self.addCleanup(temp_dir.cleanup)

        libraries = native_alignment_check.inspect_apk(apk_path)
        with self.assertRaisesRegex(native_alignment_check.NativeAlignmentError, "missing required 64-bit ABIs"):
            native_alignment_check.validate_libraries(
                libraries,
                required_alignment=16384,
                required_abis={"arm64-v8a"},
                expected_abis={"arm64-v8a", "armeabi-v7a"},
                require_64_bit_only=False,
            )

    def test_rejects_an_abi_the_artifact_should_not_carry(self):
        temp_dir, apk_path = write_apk(
            {
                "lib/arm64-v8a/libok.so": minimal_elf64([16384]),
                "lib/riscv64/libsurprise.so": minimal_elf64([16384]),
            }
        )
        self.addCleanup(temp_dir.cleanup)

        libraries = native_alignment_check.inspect_apk(apk_path)
        with self.assertRaisesRegex(native_alignment_check.NativeAlignmentError, "unexpected ABIs"):
            native_alignment_check.validate_libraries(
                libraries,
                required_alignment=16384,
                required_abis={"arm64-v8a"},
                expected_abis={"arm64-v8a"},
                require_64_bit_only=True,
            )

    def test_rejects_a_universal_apk_that_lost_abis(self):
        """Packaging dropped two ABIs. Content alone cannot tell this from a split."""
        temp_dir, apk_path = write_apk(
            {
                "lib/arm64-v8a/libok.so": minimal_elf64([16384]),
                "lib/x86_64/libok.so": minimal_elf64([16384]),
            }
        )
        self.addCleanup(temp_dir.cleanup)

        libraries = native_alignment_check.inspect_apk(apk_path)
        with self.assertRaisesRegex(native_alignment_check.NativeAlignmentError, "missing expected ABIs"):
            native_alignment_check.validate_libraries(
                libraries,
                required_alignment=16384,
                required_abis={"arm64-v8a", "x86_64"},
                expected_abis={"arm64-v8a", "armeabi-v7a", "x86", "x86_64"},
                require_64_bit_only=False,
            )

    def test_a_split_is_accepted_without_the_full_64_bit_set(self):
        temp_dir, apk_path = write_apk(
            {
                "lib/arm64-v8a/libok.so": minimal_elf64([16384]),
            }
        )
        self.addCleanup(temp_dir.cleanup)

        libraries = native_alignment_check.inspect_apk(apk_path)
        result = native_alignment_check.validate_libraries(
            libraries,
            required_alignment=16384,
            required_abis={"arm64-v8a", "x86_64"},
            expected_abis={"arm64-v8a"},
            require_64_bit_only=False,
            variant="split:arm64-v8a",
        )

        self.assertEqual(result["apkVariant"], "split:arm64-v8a")

    def test_a_32_bit_split_carries_no_16kb_obligation(self):
        temp_dir, apk_path = write_apk(
            {
                "lib/armeabi-v7a/liblegacy.so": minimal_elf32([4096]),
            }
        )
        self.addCleanup(temp_dir.cleanup)

        libraries = native_alignment_check.inspect_apk(apk_path)
        result = native_alignment_check.validate_libraries(
            libraries,
            required_alignment=16384,
            required_abis={"arm64-v8a", "x86_64"},
            expected_abis={"armeabi-v7a"},
            require_64_bit_only=False,
            variant="split:armeabi-v7a",
        )

        self.assertEqual(result["apkVariant"], "split:armeabi-v7a")
        self.assertEqual(result["checked64BitLoadSegments"], 0)

    def test_a_split_carrying_the_wrong_abi_fails(self):
        """The name says arm64; the payload says otherwise."""
        temp_dir, apk_path = write_apk(
            {
                "lib/x86_64/libok.so": minimal_elf64([16384]),
            }
        )
        self.addCleanup(temp_dir.cleanup)

        libraries = native_alignment_check.inspect_apk(apk_path)
        with self.assertRaisesRegex(native_alignment_check.NativeAlignmentError, "unexpected ABIs"):
            native_alignment_check.validate_libraries(
                libraries,
                required_alignment=16384,
                required_abis={"arm64-v8a", "x86_64"},
                expected_abis={"arm64-v8a"},
                require_64_bit_only=False,
                variant="split:arm64-v8a",
            )


class ExpectedAbisForApkTest(unittest.TestCase):
    """The artifact's name is the declaration; its contents are the claim under test."""

    DECLARED = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}

    def test_a_split_name_expects_exactly_that_abi(self):
        variant, expected = native_alignment_check.expected_abis_for_apk(
            "app-full-arm64-v8a-release.apk", self.DECLARED
        )

        self.assertEqual("split:arm64-v8a", variant)
        self.assertEqual({"arm64-v8a"}, expected)

    def test_x86_does_not_swallow_x86_64(self):
        variant, expected = native_alignment_check.expected_abis_for_apk(
            "app-full-x86_64-release.apk", self.DECLARED
        )

        self.assertEqual("split:x86_64", variant)
        self.assertEqual({"x86_64"}, expected)

    def test_a_universal_name_expects_every_declared_abi(self):
        variant, expected = native_alignment_check.expected_abis_for_apk(
            "app-full-universal-release.apk", self.DECLARED
        )

        self.assertEqual("universal", variant)
        self.assertEqual(self.DECLARED, expected)

    def test_an_unsplit_build_is_treated_as_universal(self):
        variant, expected = native_alignment_check.expected_abis_for_apk(
            "app-full-release.apk", self.DECLARED
        )

        self.assertEqual("universal", variant)
        self.assertEqual(self.DECLARED, expected)

    def test_a_64_bit_only_policy_rejects_a_32_bit_library(self):
        """The old gate skipped these outright, so the claim could never be false."""
        temp_dir, apk_path = write_apk(
            {
                "lib/arm64-v8a/libok.so": minimal_elf64([16384]),
                "lib/armeabi-v7a/liblegacy.so": minimal_elf32([4096]),
            }
        )
        self.addCleanup(temp_dir.cleanup)

        libraries = native_alignment_check.inspect_apk(apk_path)
        with self.assertRaisesRegex(native_alignment_check.NativeAlignmentError, "64-bit only"):
            native_alignment_check.validate_libraries(
                libraries,
                required_alignment=16384,
                required_abis={"arm64-v8a"},
                expected_abis={"arm64-v8a", "armeabi-v7a"},
                require_64_bit_only=True,
            )

    def test_inspects_the_elfs_inside_a_zip_payload(self):
        # Replaces test_skips_zip_payloads_named_so, which asserted that a
        # .zip.so was recorded as skipped and contributed no libraries. That
        # was the real behaviour, and it is exactly the blind spot this gate
        # had to lose: the shipped arm64 FFmpeg payload hides 112 ELFs behind
        # one such entry.
        temp_dir, apk_path = write_apk(
            {
                "lib/arm64-v8a/libarchive.zip.so": nested_zip(
                    {
                        "usr/lib/libinner.so": minimal_elf64([16384]),
                        "usr/lib/libinner.so.1": b"libinner.so",
                        "usr/lib/python/module.py": b"print('hi')\n",
                    }
                ),
                "lib/arm64-v8a/libok.so": minimal_elf64([16384]),
            }
        )
        self.addCleanup(temp_dir.cleanup)

        skipped = []
        report = {}
        libraries = native_alignment_check.inspect_apk(
            apk_path, skipped_archive_entries=skipped, nested_archive_report=report
        )

        self.assertEqual(skipped, [])
        self.assertEqual(
            sorted(library.apk_entry for library in libraries),
            [
                "lib/arm64-v8a/libarchive.zip.so!usr/lib/libinner.so",
                "lib/arm64-v8a/libok.so",
            ],
        )
        nested = next(item for item in libraries if item.archive_entry is not None)
        self.assertEqual("lib/arm64-v8a/libarchive.zip.so", nested.archive_entry)
        self.assertEqual("usr/lib/libinner.so", nested.inner_path)
        self.assertEqual("arm64-v8a", nested.abi)
        self.assertEqual(
            {"elfCount": 1, "nonElfEntryCount": 2},
            report["lib/arm64-v8a/libarchive.zip.so"],
        )

    def test_rejects_a_4kb_aligned_64_bit_elf_inside_a_zip_payload(self):
        temp_dir, apk_path = write_apk(
            {
                "lib/arm64-v8a/libarchive.zip.so": nested_zip(
                    {"usr/lib/libwebp.so": minimal_elf64([4096])}
                ),
            }
        )
        self.addCleanup(temp_dir.cleanup)
        libraries = native_alignment_check.inspect_apk(apk_path)

        with self.assertRaises(native_alignment_check.NativeAlignmentError) as ctx:
            native_alignment_check.validate_libraries(
                libraries,
                required_alignment=16384,
                required_abis={"arm64-v8a"},
                expected_abis={"arm64-v8a"},
                require_64_bit_only=False,
                variant="split:arm64-v8a",
            )

        message = str(ctx.exception)
        self.assertIn("libarchive.zip.so!usr/lib/libwebp.so", message)
        self.assertIn("p_align 4096", message)

    def test_an_exception_excuses_only_the_entry_it_names(self):
        temp_dir, apk_path = write_apk(
            {
                "lib/arm64-v8a/libarchive.zip.so": nested_zip(
                    {
                        "usr/lib/libwebp.so": minimal_elf64([4096]),
                        "usr/lib/libother.so": minimal_elf64([4096]),
                    }
                ),
            }
        )
        self.addCleanup(temp_dir.cleanup)
        libraries = native_alignment_check.inspect_apk(apk_path)
        exception = native_alignment_check.AlignmentException(
            archive_entry="libarchive.zip.so",
            inner_path="usr/lib/libwebp.so",
            abis=frozenset({"arm64-v8a"}),
            observed_alignment=4096,
            reason="prebuilt upstream object",
            upstream="https://example.invalid/issue",
        )

        with self.assertRaises(native_alignment_check.NativeAlignmentError) as ctx:
            native_alignment_check.validate_libraries(
                libraries,
                required_alignment=16384,
                required_abis={"arm64-v8a"},
                expected_abis={"arm64-v8a"},
                require_64_bit_only=False,
                variant="split:arm64-v8a",
                alignment_exceptions=(exception,),
            )

        message = str(ctx.exception)
        self.assertIn("libother.so", message)
        self.assertNotIn("libwebp.so PT_LOAD", message)

    def test_an_exception_that_is_no_longer_observed_fails(self):
        temp_dir, apk_path = write_apk(
            {
                "lib/arm64-v8a/libarchive.zip.so": nested_zip(
                    {"usr/lib/libwebp.so": minimal_elf64([16384])}
                ),
            }
        )
        self.addCleanup(temp_dir.cleanup)
        libraries = native_alignment_check.inspect_apk(apk_path)
        exception = native_alignment_check.AlignmentException(
            archive_entry="libarchive.zip.so",
            inner_path="usr/lib/libwebp.so",
            abis=frozenset({"arm64-v8a"}),
            observed_alignment=4096,
            reason="prebuilt upstream object",
            upstream="https://example.invalid/issue",
        )

        with self.assertRaises(native_alignment_check.NativeAlignmentError) as ctx:
            native_alignment_check.validate_libraries(
                libraries,
                required_alignment=16384,
                required_abis={"arm64-v8a"},
                expected_abis={"arm64-v8a"},
                require_64_bit_only=False,
                variant="split:arm64-v8a",
                alignment_exceptions=(exception,),
            )

        self.assertIn("remove the exception", str(ctx.exception))

    def test_a_zip_payload_carrying_no_elf_is_still_reported_as_skipped(self):
        temp_dir, apk_path = write_apk(
            {
                "lib/arm64-v8a/libdata.zip.so": nested_zip({"usr/share/data.txt": b"nothing"}),
                "lib/arm64-v8a/libok.so": minimal_elf64([16384]),
            }
        )
        self.addCleanup(temp_dir.cleanup)

        skipped = []
        native_alignment_check.inspect_apk(apk_path, skipped_archive_entries=skipped)

        self.assertEqual(skipped, ["lib/arm64-v8a/libdata.zip.so"])

    def test_a_corrupt_zip_payload_is_an_error_not_a_silent_skip(self):
        temp_dir, apk_path = write_apk(
            {"lib/arm64-v8a/libarchive.zip.so": b"PK\x03\x04truncated"}
        )
        self.addCleanup(temp_dir.cleanup)

        with self.assertRaises(native_alignment_check.NativeAlignmentError) as ctx:
            native_alignment_check.inspect_apk(apk_path)

        self.assertIn("could not be read", str(ctx.exception))

    def test_an_exception_that_meets_the_requirement_is_rejected_as_policy(self):
        with self.assertRaises(native_alignment_check.NativeAlignmentError) as ctx:
            native_alignment_check.parse_alignment_exceptions(
                [
                    {
                        "archiveEntry": "libarchive.zip.so",
                        "innerPath": "usr/lib/libwebp.so",
                        "abis": ["arm64-v8a"],
                        "observedAlignmentBytes": 16384,
                        "reason": "not actually under-aligned",
                        "upstream": "https://example.invalid/issue",
                    }
                ],
                16384,
                {"arm64-v8a"},
            )

        self.assertIn("must be removed", str(ctx.exception))

    def test_an_exception_naming_an_undeclared_abi_is_rejected(self):
        with self.assertRaises(native_alignment_check.NativeAlignmentError) as ctx:
            native_alignment_check.parse_alignment_exceptions(
                [
                    {
                        "archiveEntry": "libarchive.zip.so",
                        "innerPath": "usr/lib/libwebp.so",
                        "abis": ["riscv64"],
                        "observedAlignmentBytes": 4096,
                        "reason": "prebuilt upstream object",
                        "upstream": "https://example.invalid/issue",
                    }
                ],
                16384,
                {"arm64-v8a"},
            )

        self.assertIn("does not declare", str(ctx.exception))


class MediaStackMigrationEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.repo_root = Path(self.temp_dir.name)
        build_file = self.repo_root / "app/build.gradle.kts"
        build_file.parent.mkdir(parents=True)
        build_file.write_text("useLegacyPackaging = true\n", encoding="utf-8")
        for source_path in (
            "app/src/main/java/com/freevibe/service/AudioTrimmer.kt",
            "app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoCropScreen.kt",
        ):
            source = self.repo_root / source_path
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text("FFmpeg\n", encoding="utf-8")

    def evidence(self):
        return {
            "date": "2026-08-21",
            "status": "verified",
            "artifactBytes": {
                "before": {"app-full-arm64-v8a-release.apk": 100},
                "after": {"app-full-arm64-v8a-release.apk": 90},
            },
            "legacyPackagingDecision": {
                "useLegacyPackaging": True,
                "canDisable": False,
                "reason": "The extractor still expands bundled native archives.",
            },
            "retainedFfmpegConsumers": [
                {
                    "id": "sound-editor-codec-fallbacks",
                    "sourcePath": "app/src/main/java/com/freevibe/service/AudioTrimmer.kt",
                    "mode": "fallback",
                    "operations": ["MP3 encode"],
                },
                {
                    "id": "video-crop-export",
                    "sourcePath": "app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoCropScreen.kt",
                    "mode": "direct",
                    "operations": ["video crop"],
                },
                {
                    "id": "yt-dlp-extractor-runtime",
                    "sourcePath": "app/build.gradle.kts",
                    "mode": "runtime",
                    "operations": ["stream extraction"],
                },
            ],
            "videoCropIsSoleRemainingConsumer": False,
            "videoCropStatus": "Last direct video editing consumer, but not the sole consumer.",
        }

    def test_accepts_measured_artifacts_and_named_ffmpeg_consumers(self):
        result = native_alignment_check.validate_media_stack_migration_evidence(
            self.repo_root,
            self.evidence(),
        )

        self.assertEqual(result["artifactCount"], 1)
        self.assertTrue(result["useLegacyPackaging"])
        self.assertFalse(result["canDisableLegacyPackaging"])

    def test_rejects_an_unnamed_ffmpeg_consumer(self):
        evidence = self.evidence()
        evidence["retainedFfmpegConsumers"].pop()

        with self.assertRaisesRegex(native_alignment_check.NativeAlignmentError, "yt-dlp-extractor-runtime"):
            native_alignment_check.validate_media_stack_migration_evidence(self.repo_root, evidence)

    def test_rejects_artifact_measurements_with_different_keys(self):
        evidence = self.evidence()
        evidence["artifactBytes"]["after"] = {"different.apk": 90}

        with self.assertRaisesRegex(native_alignment_check.NativeAlignmentError, "same artifacts"):
            native_alignment_check.validate_media_stack_migration_evidence(self.repo_root, evidence)


if __name__ == "__main__":
    unittest.main()
