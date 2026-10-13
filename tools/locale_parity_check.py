#!/usr/bin/env python3
"""Keep Aura's translations in step with the English strings.

For every source set that ships default strings, each values-<locale> folder has to carry the
same keys, the same resource kinds, the same placeholders and the same array lengths. A folder
is read as Android reads it: every XML file in it together, and a key defined twice is an error.
Every values file under app/src is also checked for unescaped quotes, which aapt rejects or
silently eats. A gap that is on purpose goes in docs/localization/locale-fallbacks.json with a reason.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path


class LocaleParityError(ValueError):
    """Raised when the strings files can't be read."""


FALLBACKS = "docs/localization/locale-fallbacks.json"
SOURCE_ROOT = "app/src"
REQUIRED_LOCALES = ("zh",)
LOCALE_DIR = re.compile(r"^values-([a-z]{2,3}(?:-r[A-Z]{2})?)$")
KINDS = ("string", "plurals", "string-array")

PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")
ESCAPE = re.compile(r"\\.", re.DOTALL)
BODY = re.compile(r"<(string|item)(?=[\s>])[^>]*>(.*?)</\1>", re.DOTALL)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Check locale parity of strings.xml files.")
    parser.add_argument("--repo-root", default=Path(__file__).resolve().parents[1])
    return parser.parse_args()


Key = tuple[str, str]


def load(folder: Path, repo_root: Path, errors: list[str]) -> dict[Key, ET.Element]:
    """Every string resource in a values folder, whichever XML file it sits in.

    Android merges all the files in a folder, so a key counts as translated wherever it lives,
    and the same key in two files is a build error, not a second translation.
    """
    found: dict[Key, ET.Element] = {}
    origin: dict[Key, str] = {}
    for path in sorted(folder.glob("*.xml")):
        try:
            root = ET.parse(path).getroot()
        except (OSError, ET.ParseError) as exc:
            raise LocaleParityError(f"cannot read {path}: {exc}") from exc
        rel = path.relative_to(repo_root).as_posix()
        for element in root:
            name = element.get("name")
            if element.tag not in KINDS or not name:
                continue
            key = (element.tag, name)
            if key in origin:
                errors.append(f"{rel}: {element.tag} '{name}' is already defined in {origin[key]}")
                continue
            origin[key] = rel
            if element.get("translatable") == "false":
                continue
            found[key] = element
    return found


def placeholders(text: str) -> Counter:
    # %% is a literal percent sign, so a translation may need it where the English spells out "percent".
    return Counter(p for p in PLACEHOLDER.findall(text) if p != "%%")


def text_of(element: ET.Element) -> str:
    return "".join(element.itertext())


def default_variants(element: ET.Element) -> list[str]:
    """The texts a translation of this resource is compared against."""
    if element.tag == "plurals":
        other = [text_of(i) for i in element.findall("item") if i.get("quantity") == "other"]
        return other
    if element.tag == "string-array":
        return []
    return [text_of(element)]


def texts_to_check(element: ET.Element) -> list[str]:
    if element.tag == "plurals":
        return [text_of(i) for i in element.findall("item")]
    if element.tag == "string-array":
        return []
    return [text_of(element)]


def describe(counter: Counter) -> str:
    return ", ".join(sorted(counter.elements())) or "none"


def load_fallbacks(repo_root: Path, errors: list[str]) -> dict[str, str]:
    path = repo_root / FALLBACKS
    if not path.is_file():
        errors.append(f"missing {FALLBACKS}")
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise LocaleParityError(f"cannot read {FALLBACKS}: {exc}") from exc
    entries = data.get("fallbacks") if isinstance(data, dict) else None
    if not isinstance(entries, dict):
        errors.append(f"{FALLBACKS} needs a top-level \"fallbacks\" object")
        return {}
    usable: dict[str, str] = {}
    for key, reason in entries.items():
        if not isinstance(reason, str) or not reason.strip():
            errors.append(f"{FALLBACKS}: '{key}' has no reason")
            continue
        usable[key] = reason
    return usable


def quote_problems(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    problems = []
    for match in BODY.finditer(text):
        body = match.group(2)
        if body.lstrip().startswith("<![CDATA["):
            continue
        stripped = ESCAPE.sub("", body.strip())
        if len(stripped) >= 2 and stripped.startswith('"') and stripped.endswith('"'):
            inner = stripped[1:-1]
            if '"' in inner:
                problems.append(body)
            continue
        if "'" in stripped or '"' in stripped:
            problems.append(body)
    return problems


def check_quotes(repo_root: Path, errors: list[str]) -> None:
    for path in sorted((repo_root / SOURCE_ROOT).glob("*/res/values*/*.xml")):
        rel = path.relative_to(repo_root).as_posix()
        for body in quote_problems(path):
            snippet = " ".join(body.split())[:60]
            errors.append(
                f"{rel}: unescaped quote in \"{snippet}\" (write \\' or \\\", "
                "or wrap the whole text in double quotes)"
            )


def check_doubled_percent(repo_root: Path, errors: list[str]) -> None:
    """%% only becomes one percent sign when the text goes through a formatter.

    A string with no placeholder is read with a plain getString, which hands the two characters
    back as they are, so the screen shows "15%%".
    """
    for path in sorted((repo_root / SOURCE_ROOT).glob("*/res/values*/*.xml")):
        rel = path.relative_to(repo_root).as_posix()
        for match in BODY.finditer(path.read_text(encoding="utf-8")):
            body = match.group(2)
            if "%%" in body and not placeholders(body):
                snippet = " ".join(body.split())[:60]
                errors.append(
                    f"{rel}: doubled percent sign in \"{snippet}\", which takes no arguments "
                    "(write a single % and set formatted=\"false\")"
                )


def check_pair(
    rel: str, default: dict[Key, ET.Element], translated: dict[Key, ET.Element],
    fallbacks: dict[str, str], used: set[str], errors: list[str],
) -> None:
    for (kind, name), source in default.items():
        target = translated.get((kind, name))
        if target is None:
            if name in fallbacks:
                used.add(name)
            else:
                errors.append(f"{rel}: missing translation for {source.tag} '{name}'")
            continue
        if source.tag == "string-array":
            have, want = len(target.findall("item")), len(source.findall("item"))
            if have != want:
                errors.append(f"{rel}: array '{name}' has {have} items, the default has {want}")
            continue
        variants = default_variants(source)
        if not variants:
            continue
        want = placeholders(variants[0])
        for text in texts_to_check(target):
            got = placeholders(text)
            if got != want:
                errors.append(
                    f"{rel}: placeholders differ in '{name}' "
                    f"(default: {describe(want)}; translation: {describe(got)})"
                )
                break
    for kind, name in translated:
        if (kind, name) not in default:
            errors.append(f"{rel}: {kind} '{name}' has no default of the same kind")


def locale_dirs(res: Path) -> list[Path]:
    found = {res / f"values-{code}" for code in REQUIRED_LOCALES}
    for child in res.iterdir():
        if child.is_dir() and LOCALE_DIR.match(child.name):
            found.add(child)
    return sorted(found)


def validate(repo_root: Path) -> dict[str, object]:
    errors: list[str] = []
    fallbacks = load_fallbacks(repo_root, errors)
    used: set[str] = set()
    checked = 0
    defaults = 0
    for default_path in sorted((repo_root / SOURCE_ROOT).glob("*/res/values/strings.xml")):
        res = default_path.parent.parent
        defaults += 1
        default = load(default_path.parent, repo_root, errors)
        for folder in locale_dirs(res):
            path = folder / "strings.xml"
            rel = path.relative_to(repo_root).as_posix()
            if not path.is_file():
                errors.append(f"{rel}: missing file")
                continue
            check_pair(rel, default, load(folder, repo_root, errors), fallbacks, used, errors)
            checked += 1
    if defaults == 0:
        raise LocaleParityError(f"no strings files found under {SOURCE_ROOT}")
    for key in sorted(set(fallbacks) - used):
        errors.append(f"{FALLBACKS}: '{key}' is no longer missing, remove it from the list")
    check_quotes(repo_root, errors)
    check_doubled_percent(repo_root, errors)
    if errors:
        raise LocaleParityError("\n".join(errors))
    return {"localesChecked": checked, "fallbacks": sorted(used)}


def main() -> int:
    args = parse_args()
    try:
        result = validate(Path(args.repo_root).resolve())
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
