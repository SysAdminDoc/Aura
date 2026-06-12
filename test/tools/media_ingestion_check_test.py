from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.media_ingestion_check import MediaIngestionCheckError, validate_media_ingestion


REPO_ROOT = Path(__file__).resolve().parents[2]


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


class MediaIngestionCheckTest(unittest.TestCase):
    def test_live_media_ingestion_policy_passes(self) -> None:
        result = validate_media_ingestion(REPO_ROOT)

        self.assertEqual("ok", result["status"])
        self.assertEqual(0, result["findings"])

    def test_rejects_response_body_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            write(repo / "app/src/main/java/Example.kt", "fun bad(body: okhttp3.ResponseBody) = body.bytes()\n")

            with self.assertRaises(MediaIngestionCheckError):
                validate_media_ingestion(repo)

    def test_rejects_unbounded_response_stream_copy(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            write(
                repo / "app/src/main/java/Example.kt",
                "fun bad(body: okhttp3.ResponseBody, out: java.io.OutputStream) { body.byteStream().use { input -> input.copyTo(out) } }\n",
            )

            with self.assertRaises(MediaIngestionCheckError):
                validate_media_ingestion(repo)

    def test_rejects_unbounded_local_media_stream_copy(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            write(
                repo / "app/src/main/java/Example.kt",
                "fun bad(file: java.io.File, out: java.io.OutputStream) { file.inputStream().use { input -> input.copyTo(out) } }\n",
            )

            with self.assertRaises(MediaIngestionCheckError):
                validate_media_ingestion(repo)

    def test_allows_capped_helper_copy(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            write(
                repo / "app/src/main/java/Example.kt",
                "fun ok(input: java.io.InputStream, out: java.io.OutputStream) { copyStreamCapped(input, out, 1024L) }\n",
            )

            result = validate_media_ingestion(repo)

            self.assertEqual("ok", result["status"])


if __name__ == "__main__":
    unittest.main()
