#!/usr/bin/env python3
"""Compare dependency/runtime claims in ROADMAP.md and RESEARCH.md against actual manifests.

Only checks "current state" sections (State of the Repo, Executive Summary,
Product Map, Architecture Assessment) where version claims describe what IS
shipped, not aspirational/target versions in roadmap items.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

VERSION_CATALOG = Path("gradle/libs.versions.toml")
BUILD_GRADLE = Path("app/build.gradle.kts")
PACKAGE_JSON = Path("functions/package.json")
ROADMAP = Path("ROADMAP.md")
RESEARCH = Path("RESEARCH.md")

VERSION_PATTERN = re.compile(
    r"(?:^|[\s(,;])(?P<name>"
    r"Kotlin|AGP|KSP|Compose BOM|Hilt|Room|Retrofit|OkHttp|Coil|Navigation|"
    r"Lifecycle|Coroutines|DataStore|Media3|WorkManager|Paging|Glance|"
    r"NewPipe(?:Extractor| Extractor)|youtubedl-android|"
    r"Firebase BoM|firebase-bom|"
    r"play-services-base|play-services-mlkit-subject-segmentation|"
    r"Moshi|Serialization|"
    r"Node|node|firebase-admin|firebase-functions|typescript|"
    r"compileSdk|targetSdk|minSdk|versionCode|versionName"
    r")\s+"
    r"(?:v(?=\d))?(?P<version>\d[\d.]+(?:-\w[\w.]*)?)",
    re.IGNORECASE,
)

CURRENT_STATE_HEADERS_ROADMAP = re.compile(
    r"^#{1,4}\s+(?:State of the Repo)", re.IGNORECASE,
)

CURRENT_STATE_HEADERS_RESEARCH = re.compile(
    r"^#{1,4}\s+(?:"
    r"Executive\s+Summary|"
    r"Product\s+Map|"
    r"Architecture\s+Assessment|"
    r"Security,?\s+Privacy"
    r")",
    re.IGNORECASE,
)

SECTION_HEADER = re.compile(r"^(#{1,4})\s+")

ASPIRATIONAL_SKIP = re.compile(
    r"(?:planned|targets?|upgrade\s+to|migrate\s+to|requires|should\s+become|"
    r"evaluate|skip\s+\S+\s+and\s+go|needs|blocked\s+until|goal\s+is|"
    r"vs\.?\s+the\s+planned)",
    re.IGNORECASE,
)


def parse_version_catalog(path: Path) -> dict[str, str]:
    versions: dict[str, str] = {}
    in_versions = False
    text = path.read_text(encoding="utf-8")
    for line in text.splitlines():
        stripped = line.strip()
        if stripped == "[versions]":
            in_versions = True
            continue
        if stripped.startswith("[") and in_versions:
            break
        if in_versions and "=" in stripped:
            key, val = stripped.split("=", 1)
            versions[key.strip()] = val.strip().strip('"')
    return versions


def parse_build_gradle(path: Path) -> dict[str, str]:
    facts: dict[str, str] = {}
    text = path.read_text(encoding="utf-8")
    for pat, key in [
        (r'compileSdk\s*=\s*(\d+)', "compileSdk"),
        (r'minSdk\s*=\s*(\d+)', "minSdk"),
        (r'targetSdk\s*=\s*(\d+)', "targetSdk"),
        (r'versionCode\s*=\s*(\d+)', "versionCode"),
        (r'versionName\s*=\s*"([^"]+)"', "versionName"),
    ]:
        m = re.search(pat, text)
        if m:
            facts[key] = m.group(1)

    for m in re.finditer(r'implementation\(\s*"([^"]+):([^"]+)"\s*\)', text):
        coord = m.group(1)
        ver = m.group(2)
        artifact = coord.rsplit(":", 1)[-1] if ":" in coord else coord
        facts[f"gradle:{artifact}"] = ver

    for m in re.finditer(r'platform\(\s*"([^"]+):([^"]+)"\s*\)', text):
        coord = m.group(1)
        ver = m.group(2)
        artifact = coord.rsplit(":", 1)[-1] if ":" in coord else coord
        facts[f"gradle:{artifact}"] = ver

    return facts


def parse_package_json(path: Path) -> dict[str, str]:
    data = json.loads(path.read_text(encoding="utf-8"))
    facts: dict[str, str] = {}
    engines = data.get("engines", {})
    if "node" in engines:
        facts["node"] = str(engines["node"])
    for section in ("dependencies", "devDependencies"):
        for pkg, ver in data.get(section, {}).items():
            facts[f"npm:{pkg}"] = str(ver)
    return facts


CANONICAL_MAP: dict[str, list[str]] = {
    "kotlin": ["Kotlin"],
    "agp": ["AGP"],
    "ksp": ["KSP"],
    "compose-bom": ["Compose BOM"],
    "hilt": ["Hilt"],
    "room": ["Room"],
    "retrofit": ["Retrofit"],
    "okhttp": ["OkHttp"],
    "coil": ["Coil"],
    "navigation": ["Navigation"],
    "lifecycle": ["Lifecycle"],
    "coroutines": ["Coroutines"],
    "datastore": ["DataStore"],
    "media3": ["Media3"],
    "work": ["WorkManager"],
    "paging": ["Paging"],
    "glance": ["Glance"],
    "moshi": ["Moshi"],
    "serialization": ["Serialization"],
}


def _norm_ver(v: str) -> str:
    return v.lstrip("v").rstrip(".")


def build_truth(
    catalog: dict[str, str],
    gradle: dict[str, str],
    npm: dict[str, str],
) -> dict[str, str]:
    truth: dict[str, str] = {}
    for key, aliases in CANONICAL_MAP.items():
        if key in catalog:
            for alias in aliases:
                truth[alias.lower()] = catalog[key]

    for key in ("compileSdk", "targetSdk", "minSdk", "versionCode", "versionName"):
        if key in gradle:
            truth[key.lower()] = gradle[key]

    newpipe = gradle.get("gradle:NewPipeExtractor")
    if newpipe:
        truth["newpipeextractor"] = _norm_ver(newpipe)
        truth["newpipe extractor"] = _norm_ver(newpipe)

    ytdl = gradle.get("gradle:library")
    if ytdl and "youtubedl-android" not in truth:
        truth["youtubedl-android"] = ytdl

    firebase_bom = gradle.get("gradle:firebase-bom")
    if firebase_bom:
        truth["firebase bom"] = firebase_bom
        truth["firebase-bom"] = firebase_bom

    for gkey in gradle:
        if gkey.startswith("gradle:"):
            artifact = gkey[7:]
            truth[artifact.lower()] = _norm_ver(gradle[gkey])

    if "node" in npm:
        truth["node"] = npm["node"]
    for nkey in npm:
        if nkey.startswith("npm:"):
            truth[nkey[4:].lower()] = npm[nkey]

    return truth


def extract_current_state_lines(path: Path, header_re: re.Pattern) -> list[tuple[int, str]]:
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8").splitlines()
    results: list[tuple[int, str]] = []
    in_current = False
    current_depth = 0

    for i, line in enumerate(lines, start=1):
        hm = SECTION_HEADER.match(line)
        if hm:
            depth = len(hm.group(1))
            if header_re.match(line):
                in_current = True
                current_depth = depth
            elif in_current and depth <= current_depth:
                in_current = False

        if in_current:
            results.append((i, line))

    return results


def find_stale_claims(
    active_lines: list[tuple[int, str]],
    truth: dict[str, str],
    source: str,
) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    for lineno, line in active_lines:
        if ASPIRATIONAL_SKIP.search(line):
            continue
        for m in VERSION_PATTERN.finditer(line):
            name = m.group("name")
            claimed = _norm_ver(m.group("version"))
            lookup = name.lower().replace(" ", "").replace("-", "")
            actual = None

            for tkey, tval in truth.items():
                tkey_normalized = tkey.lower().replace(" ", "").replace("-", "")
                if tkey_normalized == lookup:
                    actual = _norm_ver(tval)
                    break

            if actual is None:
                for tkey, tval in truth.items():
                    if lookup in tkey.lower().replace("-", "").replace(" ", ""):
                        actual = _norm_ver(tval)
                        break

            if actual is not None and claimed != actual:
                findings.append({
                    "file": source,
                    "line": lineno,
                    "dependency": name,
                    "claimed": claimed,
                    "actual": actual,
                    "text": line.strip()[:120],
                })
    return findings


def find_duplicate_titles(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8").splitlines()
    title_re = re.compile(r"^-\s+\[.\]\s+P\d\s*[-–—]\s*\*\*(.+?)\*\*")
    seen: dict[str, list[int]] = {}

    historical_re = re.compile(
        r"^#{1,4}\s+(?:Implementation\s+Log|What is shipped|Version History|"
        r"Continuation Brief|Rejected|Appendix|Sources)",
        re.IGNORECASE,
    )
    in_historical = False
    historical_depth = 0

    for i, line in enumerate(lines, start=1):
        hm = SECTION_HEADER.match(line)
        if hm:
            depth = len(hm.group(1))
            if historical_re.match(line):
                in_historical = True
                historical_depth = depth
            elif in_historical and depth <= historical_depth:
                in_historical = False

        if in_historical:
            continue

        m = title_re.match(line)
        if m:
            title = m.group(1).strip()
            seen.setdefault(title, []).append(i)

    dupes: list[dict[str, Any]] = []
    for title, line_nums in seen.items():
        if len(line_nums) > 1:
            dupes.append({"title": title, "lines": line_nums, "file": str(path)})
    return dupes


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="Manifest consistency check for Aura planning docs.")
    parser.add_argument("--json", action="store_true", help="Output machine-readable JSON")
    args = parser.parse_args()

    catalog = parse_version_catalog(VERSION_CATALOG)
    gradle = parse_build_gradle(BUILD_GRADLE)
    npm = parse_package_json(PACKAGE_JSON)
    truth = build_truth(catalog, gradle, npm)

    stale: list[dict[str, Any]] = []
    stale.extend(find_stale_claims(
        extract_current_state_lines(ROADMAP, CURRENT_STATE_HEADERS_ROADMAP),
        truth, str(ROADMAP),
    ))
    stale.extend(find_stale_claims(
        extract_current_state_lines(RESEARCH, CURRENT_STATE_HEADERS_RESEARCH),
        truth, str(RESEARCH),
    ))

    dupes = find_duplicate_titles(ROADMAP)

    result = {
        "stale_claims": stale,
        "duplicate_titles": dupes,
        "manifest_versions": {k: v for k, v in sorted(truth.items())},
        "status": "fail" if stale or dupes else "ok",
    }

    if args.json:
        print(json.dumps(result, indent=2, sort_keys=True))
    else:
        if stale:
            print(f"STALE CLAIMS ({len(stale)}):")
            for f in stale:
                print(f"  {f['file']}:{f['line']}  {f['dependency']}: claimed {f['claimed']}, actual {f['actual']}")
                print(f"    {f['text']}")
            print()
        if dupes:
            print(f"DUPLICATE TITLES ({len(dupes)}):")
            for d in dupes:
                print(f"  \"{d['title']}\" appears on lines {d['lines']}")
            print()
        if not stale and not dupes:
            print("OK - no stale claims or duplicate titles found.")

    return 1 if stale or dupes else 0


if __name__ == "__main__":
    raise SystemExit(main())
