#!/usr/bin/env python3
"""Generate or verify the Aura community Firebase backend manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path


DEFAULT_MANIFEST = "docs/community-backend-manifest.json"

STATIC_BACKEND_FILES = [
    "firebase.json",
    "database.rules.json",
    "storage.rules",
    "package.json",
    "package-lock.json",
    "functions/package.json",
    "functions/package-lock.json",
    "functions/tsconfig.json",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate or verify the Aura community backend manifest."
    )
    parser.add_argument(
        "--mode",
        choices=("write", "check", "print"),
        default="check",
        help="write updates the manifest, check compares it, print writes JSON to stdout.",
    )
    parser.add_argument(
        "--manifest",
        default=DEFAULT_MANIFEST,
        help=f"Manifest path. Defaults to {DEFAULT_MANIFEST}.",
    )
    return parser.parse_args()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_json(path: Path) -> dict[str, object]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected JSON object in {path}")
    return value


def require_file(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"Required backend file not found: {path}")


def file_entry(root: Path, path: Path) -> dict[str, object]:
    require_file(path)
    return {
        "path": path.relative_to(root).as_posix(),
        "bytes": path.stat().st_size,
        "sha256": sha256_file(path),
    }


def selected_scripts(package_json: dict[str, object]) -> dict[str, str]:
    scripts = package_json.get("scripts", {})
    if not isinstance(scripts, dict):
        raise ValueError("package.json scripts must be an object")
    selected: dict[str, str] = {}
    for name, command in scripts.items():
        if (
            isinstance(name, str)
            and name.startswith("test:")
            and ("rules" in name or name == "test:functions")
        ):
            selected[name] = str(command)
    return dict(sorted(selected.items()))


def backend_files(root: Path) -> list[Path]:
    files = [root / path for path in STATIC_BACKEND_FILES]
    functions_src = sorted((root / "functions" / "src").glob("*.ts"))
    return files + functions_src


def build_manifest(root: Path) -> dict[str, object]:
    firebase_json = read_json(root / "firebase.json")
    package_json = read_json(root / "package.json")
    dev_dependencies = package_json.get("devDependencies", {})
    if not isinstance(dev_dependencies, dict):
        raise ValueError("package.json devDependencies must be an object")

    return {
        "schemaVersion": 1,
        "name": "aura-community-firebase-backend",
        "deployTargets": ["database", "storage", "functions"],
        "firebaseCli": {
            "package": "firebase-tools",
            "version": str(dev_dependencies.get("firebase-tools", "")),
        },
        "commands": {
            "verify": "npm run test:firebase-rules && npm run test:functions",
            "dryRun": "npx firebase deploy --only database,storage,functions --project <project-id> --dry-run",
            "deploy": "npx firebase deploy --only database,storage,functions --project <project-id> --message <message>",
            "rollback": "git checkout <known-good-commit> -- firebase.json database.rules.json storage.rules package.json package-lock.json functions/package.json functions/package-lock.json functions/tsconfig.json functions/src docs/community-backend-manifest.json",
        },
        "firebaseConfig": {
            "databaseRules": firebase_json.get("database", {}).get("rules"),
            "storageRules": firebase_json.get("storage", {}).get("rules"),
            "functions": firebase_json.get("functions", {}),
            "emulators": firebase_json.get("emulators", {}),
        },
        "npmScripts": selected_scripts(package_json),
        "files": [file_entry(root, path) for path in backend_files(root)],
    }


def dump_manifest(manifest: dict[str, object]) -> str:
    return json.dumps(manifest, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    manifest_path = Path(args.manifest)
    manifest_text = dump_manifest(build_manifest(Path.cwd()))

    if args.mode == "print":
        sys.stdout.write(manifest_text)
        return 0

    if args.mode == "write":
        manifest_path.parent.mkdir(parents=True, exist_ok=True)
        manifest_path.write_text(manifest_text, encoding="utf-8")
        print(f"wrote {manifest_path}")
        return 0

    require_file(manifest_path)
    expected = manifest_path.read_text(encoding="utf-8")
    if expected != manifest_text:
        print(
            f"{manifest_path} is stale. Run: "
            f"python tools/community_backend_manifest.py --mode write",
            file=sys.stderr,
        )
        return 1
    print(f"{manifest_path} ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
