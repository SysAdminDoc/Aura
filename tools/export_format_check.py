#!/usr/bin/env python3
"""Verify export-format.json against FavoritesExporter.kt and CollectionExporter.kt.

Checks:
  - Every Moshi @JsonClass field in FavoriteExportItem has a row in the spec.
  - Every Moshi @JsonClass field in CollectionExportItem has a row in the spec.
  - No orphan spec rows (field listed but not in source).
  - Version policy fields are present.
  - Privacy exclusions are non-empty.
"""
import argparse
import json
import re
import sys
from pathlib import Path


def extract_data_class_fields(source_path: Path, class_name: str) -> set[str]:
    text = source_path.read_text(encoding="utf-8")
    pattern = rf"data class {class_name}\s*\((.*?)\)"
    match = re.search(pattern, text, re.DOTALL)
    if not match:
        return set()
    body = match.group(1)
    fields = set()
    for line in body.split("\n"):
        line = line.strip().rstrip(",")
        m = re.match(r"val\s+(\w+)\s*:", line)
        if m:
            fields.add(m.group(1))
    return fields


def check_format(spec: dict, source_path: Path, item_class: str, label: str) -> list[str]:
    errors = []
    source_fields = extract_data_class_fields(source_path, item_class)
    if not source_fields:
        errors.append(f"[{label}] Could not extract fields from {item_class} in {source_path}")
        return errors

    spec_fields = set(spec.get("itemFields", {}).keys())

    missing_in_spec = source_fields - spec_fields
    orphan_in_spec = spec_fields - source_fields

    for f in sorted(missing_in_spec):
        errors.append(f"[{label}] Field '{f}' exists in {item_class} but missing from export-format.json")
    for f in sorted(orphan_in_spec):
        errors.append(f"[{label}] Field '{f}' in export-format.json but not in {item_class}")

    if "versionPolicy" not in spec:
        errors.append(f"[{label}] Missing 'versionPolicy' section")
    else:
        policy = spec["versionPolicy"]
        for key in ("bumpRule", "forwardCompat", "backwardCompat", "unsupportedVersionCopy"):
            if key not in policy or not policy[key]:
                errors.append(f"[{label}] versionPolicy.{key} is missing or empty")

    if not spec.get("privacyExclusions"):
        errors.append(f"[{label}] privacyExclusions is missing or empty")

    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--spec", default="docs/data/export-format.json")
    parser.add_argument("--repo-root", default=".")
    args = parser.parse_args()

    root = Path(args.repo_root)
    spec_path = root / args.spec
    if not spec_path.exists():
        print(f"FAIL: {spec_path} not found", file=sys.stderr)
        sys.exit(1)

    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    formats = spec.get("formats", {})
    errors = []

    fav = formats.get("favorites")
    if fav:
        src = root / fav["sourceFile"]
        errors.extend(check_format(fav, src, fav["itemClass"], "favorites"))
    else:
        errors.append("Missing 'favorites' format in spec")

    col = formats.get("collections")
    if col:
        src = root / col["sourceFile"]
        errors.extend(check_format(col, src, col["itemClass"], "collections"))
    else:
        errors.append("Missing 'collections' format in spec")

    if errors:
        for e in errors:
            print(f"  FAIL: {e}", file=sys.stderr)
        sys.exit(1)

    fav_count = len(fav.get("itemFields", {})) if fav else 0
    col_count = len(col.get("itemFields", {})) if col else 0
    print(f"OK: export format spec covers {fav_count} favorite fields and {col_count} collection fields")


if __name__ == "__main__":
    main()
