from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


class MediaIngestionCheckError(RuntimeError):
    pass


@dataclass(frozen=True)
class Finding:
    path: Path
    line: int
    reason: str
    text: str


PROHIBITED_PATTERNS: tuple[tuple[re.Pattern[str], str], ...] = (
    (
        re.compile(r"\b(?:body|responseBody|errorBody)\s*\.\s*bytes\s*\("),
        "ResponseBody.bytes() eagerly buffers untrusted media without a cap",
    ),
    (
        re.compile(r"\.\s*byteStream\s*\(\s*\)\s*\.\s*use\s*\{[^}\n]*\.\s*copyTo\s*\("),
        "ResponseBody.byteStream().copyTo() bypasses MediaIngestion capped-copy helpers",
    ),
    (
        re.compile(r"\.\s*openInputStream\s*\([^)]*\)\s*\?\s*\.\s*use\s*\{[^}\n]*\.\s*copyTo\s*\("),
        "ContentResolver.openInputStream().copyTo() bypasses MediaIngestion capped-copy helpers",
    ),
    (
        re.compile(r"\.\s*inputStream\s*\(\s*\)\s*\.\s*use\s*\{[^}\n]*\.\s*copyTo\s*\("),
        "File.inputStream().copyTo() on media paths bypasses MediaIngestion capped-copy helpers",
    ),
)

DEFAULT_SCAN_DIRS = (
    Path("app/src/main/java"),
)

SKIP_DIR_NAMES = {
    "build",
    ".gradle",
    ".git",
}


def iter_kotlin_files(repo_root: Path, scan_dirs: Iterable[Path]) -> Iterable[Path]:
    for scan_dir in scan_dirs:
        root = (repo_root / scan_dir).resolve()
        if not root.exists():
            continue
        for path in root.rglob("*.kt"):
            if any(part in SKIP_DIR_NAMES for part in path.parts):
                continue
            yield path


def find_media_ingestion_findings(
    repo_root: Path,
    scan_dirs: Iterable[Path] = DEFAULT_SCAN_DIRS,
) -> list[Finding]:
    findings: list[Finding] = []
    for path in iter_kotlin_files(repo_root, scan_dirs):
        text = path.read_text(encoding="utf-8")
        for line_number, line in enumerate(text.splitlines(), start=1):
            if line.lstrip().startswith("//"):
                continue
            for pattern, reason in PROHIBITED_PATTERNS:
                if pattern.search(line):
                    findings.append(
                        Finding(
                            path=path.relative_to(repo_root),
                            line=line_number,
                            reason=reason,
                            text=line.strip(),
                        )
                    )
    return findings


def validate_media_ingestion(repo_root: Path) -> dict[str, int | str]:
    findings = find_media_ingestion_findings(repo_root)
    if findings:
        details = "\n".join(
            f"{finding.path}:{finding.line}: {finding.reason}: {finding.text}"
            for finding in findings
        )
        raise MediaIngestionCheckError(details)
    return {"status": "ok", "findings": 0}


def main() -> None:
    parser = argparse.ArgumentParser(description="Reject unbounded media-ingestion copy paths.")
    parser.add_argument("--repo-root", default=".", type=Path)
    args = parser.parse_args()
    result = validate_media_ingestion(args.repo_root.resolve())
    print(result)


if __name__ == "__main__":
    main()
