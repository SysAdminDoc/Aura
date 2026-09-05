#!/usr/bin/env python3
"""Reject dependency calls that link an API the minSdk floor does not have.

Aura's minSdk is 26. A dependency compiled against a newer platform can call a
method that simply does not exist on an older device, and neither javac nor
Kotlin will say a word: the class resolves at compile time against the newer
`android.jar` and only fails when ART tries to link it at runtime.

Issue #2 is the worked example. NewPipeExtractor's `Utils.encodeUrlUtf8()`
calls `URLEncoder.encode(String, Charset)`, added in API 33, so every YouTube
search through `StreamingService.getSearchExtractor(String)` crashed on Android
8 through 12 with:

    java.lang.NoSuchMethodError: No static method
    encode(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;

Core library desugaring does not help, because it rewrites Aura's own bytecode
and not the extractor's. The fix is to build the `SearchQueryHandler` ourselves
with the API 1 `URLEncoder.encode(String, String)` overload, which is what
`createLegacyCompatibleYouTubeSearchHandler` does.

That fix originally landed on the Sounds search path only, while the Video
Wallpapers feed kept calling `getSearchExtractor(query)` directly and stayed
exposed. This gate exists so the next call site cannot be added silently.

Exit 0 if clean, 1 if violations found.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


# Each hazard names a call that reaches a too-new platform method through a
# dependency, and the in-repo helper that must be used instead. `window` is how
# far past the call site the required symbol may appear, so a multi-line call
# still satisfies the rule.
HAZARDS = (
    {
        "pattern": "getSearchExtractor(",
        "required": "createLegacyCompatibleYouTubeSearchHandler(",
        "window": 200,
        "reason": (
            "NewPipe's default search handler links "
            "URLEncoder.encode(String, Charset), which is API 33 and absent on "
            "Aura's minSdk 26 floor (issue #2)"
        ),
        "replacement": (
            "service.getSearchExtractor(createLegacyCompatibleYouTubeSearchHandler(query))"
        ),
    },
)

SCAN_ROOTS = (
    "app/src/main",
    "app/src/full",
    "app/src/foss",
)


class LegacyAndroidLinkageError(ValueError):
    """Raised when production source links a method the minSdk floor lacks."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Reject dependency calls that link an API newer than minSdk.",
    )
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def iter_kotlin_files(repo_root: Path) -> list[Path]:
    files: list[Path] = []
    for root in SCAN_ROOTS:
        base = repo_root / root
        if base.is_dir():
            files.extend(sorted(base.rglob("*.kt")))
    return files


def _line_of(text: str, index: int) -> int:
    return text.count("\n", 0, index) + 1


def validate_legacy_linkage(repo_root: Path) -> dict[str, object]:
    files = iter_kotlin_files(repo_root)
    if not files:
        raise LegacyAndroidLinkageError(
            "no Kotlin sources found; the scanner is not reading anything"
        )

    violations: list[str] = []
    guarded = 0
    for path in files:
        text = path.read_text(encoding="utf-8")
        for hazard in HAZARDS:
            pattern = hazard["pattern"]
            start = text.find(pattern)
            while start != -1:
                call_end = start + len(pattern)
                window = text[call_end:call_end + hazard["window"]]
                if hazard["required"] in window:
                    guarded += 1
                else:
                    relative = path.relative_to(repo_root).as_posix()
                    violations.append(
                        f"{relative}:{_line_of(text, start)} calls {pattern} without "
                        f"{hazard['required']} — {hazard['reason']}. "
                        f"Use {hazard['replacement']}"
                    )
                start = text.find(pattern, call_end)

    if violations:
        raise LegacyAndroidLinkageError("; ".join(violations))

    return {
        "status": "ok",
        "policyKind": "legacyAndroidLinkage",
        "schemaVersion": 1,
        "hazardCount": len(HAZARDS),
        "guardedCallCount": guarded,
        "scannedFileCount": len(files),
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        result = validate_legacy_linkage(repo_root)
    except LegacyAndroidLinkageError as exc:
        print(json.dumps({"status": "fail", "error": str(exc)}, indent=2, sort_keys=True))
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
