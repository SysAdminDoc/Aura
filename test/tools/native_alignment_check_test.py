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
        )

        self.assertEqual(result["checked64BitLoadSegments"], 2)
        self.assertEqual(result["seen64BitAbis"], ["arm64-v8a"])

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
            )

    def test_skips_zip_payloads_named_so(self):
        temp_dir, apk_path = write_apk(
            {
                "lib/arm64-v8a/libarchive.zip.so": b"PK\x03\x04archive",
                "lib/arm64-v8a/libok.so": minimal_elf64([16384]),
            }
        )
        self.addCleanup(temp_dir.cleanup)

        skipped = []
        libraries = native_alignment_check.inspect_apk(apk_path, skipped_archive_entries=skipped)

        self.assertEqual(skipped, ["lib/arm64-v8a/libarchive.zip.so"])
        self.assertEqual([library.apk_entry for library in libraries], ["lib/arm64-v8a/libok.so"])


if __name__ == "__main__":
    unittest.main()
