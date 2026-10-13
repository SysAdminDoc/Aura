#!/usr/bin/env python3
"""Hold Aura's UI to its design tokens.

Corner shapes come from ui/theme/Shapes.kt and stay at or under the documented 12 dp ceiling, nothing draws a
pill or circle backdrop, colour literals live in the theme or in a registered table, and the provider badge
keeps routing its colour through the contrast-checked tone function.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


SOURCE_ROOTS = (
    "app/src/main/java",
    "app/src/full/java",
    "app/src/foss/java",
    "app/src/debug/java",
    "app/src/release/java",
)
THEME_DIR = "app/src/main/java/com/freevibe/ui/theme/"
SHAPES_FILE = THEME_DIR + "Shapes.kt"
SOURCE_TONES_FILE = THEME_DIR + "SourceTones.kt"
SHARED_COMPONENTS_FILE = "app/src/main/java/com/freevibe/ui/components/SharedComponents.kt"

# ARCHITECTURE.md: "Rectangular 4-12dp radii. No pill / oval / fully-rounded backdrops."
DOCUMENTED_CEILING_DP = 12
DOCUMENTED_MIN_TEXT_CONTRAST = "4.5"

SHAPE_CALL = re.compile(r"\b(RoundedCornerShape|AbsoluteRoundedCornerShape|CutCornerShape|AbsoluteCutCornerShape)\s*\(")
CIRCLE_SHAPE = re.compile(r"\bCircleShape\b")
CEILING = re.compile(r"const val MAX_CORNER_RADIUS_DP = (\d+)\b")
DP_ARGUMENT = re.compile(r"^(?:\w+\s*=\s*)?(\d+(?:\.\d+)?)\.dp$")
GLANCE_RADIUS = re.compile(r"\.cornerRadius\(\s*([^)]*)\)")
CONTAINER_BUTTON = re.compile(r"(?<![\w.])(Button|OutlinedButton|FilledTonalButton|ElevatedButton)\s*\(")
NAVIGATION_ITEM = re.compile(r"(?<![\w.])(NavigationBarItem|NavigationRailItem)\s*\(")
COLOR_LITERAL = re.compile(r"\bColor\(\s*0[xX][0-9A-Fa-f]")

# Files outside ui/theme that may spell colours out, with the exact count and why.
COLOR_LITERAL_REGISTRY: dict[str, tuple[int, str]] = {
    "app/src/main/java/com/freevibe/ui/screens/categories/CategoriesScreen.kt": (
        32,
        "Category tiles carry fixed gradient artwork under white text, the same in either theme.",
    ),
    "app/src/main/java/com/freevibe/ui/screens/sounds/SoundUiTokens.kt": (
        22,
        "The sound source-tone table. Each provider has a light and a dark tone, checked at 4.5:1 by SoundDetailLayoutTest.",
    ),
    "app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt": (
        29,
        "Colour-search swatches. Each colour is the search value itself.",
    ),
    "app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperPreviewScreen.kt": (
        1,
        "Ink drawn over a bright wallpaper's dominant colour. It follows the image, not the theme.",
    ),
    "app/src/main/java/com/freevibe/widget/FreeVibeWidget.kt": (
        9,
        "Glance draws the home-screen widget outside MaterialTheme, so its day and night colours are spelled out.",
    ),
}


class DesignTokenError(ValueError):
    """Raised when the UI drifts off Aura's design tokens."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura's shape, colour and badge-contrast tokens.")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def kotlin_sources(repo_root: Path) -> dict[str, str]:
    sources: dict[str, str] = {}
    for root in SOURCE_ROOTS:
        base = repo_root / root
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*.kt")):
            sources[path.relative_to(repo_root).as_posix()] = path.read_text(encoding="utf-8")
    return sources


def line_of(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def call_arguments(text: str, open_paren_end: int) -> str:
    depth, index = 1, open_paren_end
    while index < len(text) and depth:
        depth += {"(": 1, ")": -1}.get(text[index], 0)
        index += 1
    return text[open_paren_end:index - 1]


def split_arguments(arguments: str) -> list[str]:
    parts, depth, current = [], 0, []
    for char in arguments:
        if char in "([":
            depth += 1
        elif char in ")]":
            depth -= 1
        if char == "," and depth == 0:
            parts.append("".join(current).strip())
            current = []
        else:
            current.append(char)
    tail = "".join(current).strip()
    if tail:
        parts.append(tail)
    return parts


def strip_comments(text: str) -> str:
    """Blank out comments and string contents, keeping offsets and newlines.

    KDoc that names a shape doesn't count, a "/*" inside a MIME type string doesn't open a comment, and a
    parenthesis inside a string doesn't throw off argument matching. Template expressions stay as code.
    """
    out = list(text)
    stack: list[list] = [["code", 0]]
    index, length = 0, len(text)

    def blank(start: int, stop: int) -> None:
        for position in range(start, stop):
            if out[position] != "\n":
                out[position] = " "

    while index < length:
        state = stack[-1]
        char, ahead = text[index], text[index:index + 3]
        if state[0] in ("code", "template"):
            if ahead.startswith("//"):
                stop = text.find("\n", index)
                stop = length if stop < 0 else stop
                blank(index, stop)
                index = stop
            elif ahead.startswith("/*"):
                stop = text.find("*/", index + 2)
                stop = length if stop < 0 else stop + 2
                blank(index, stop)
                index = stop
            elif ahead == '"""':
                stack.append(["raw", 0])
                index += 3
            elif char == '"':
                stack.append(["string", 0])
                index += 1
            elif char == "'":
                stop = index + 1
                while stop < length and text[stop] != "'":
                    stop += 2 if text[stop] == "\\" else 1
                blank(index + 1, min(stop, length))
                index = stop + 1
            elif state[0] == "template" and char == "{":
                state[1] += 1
                index += 1
            elif state[0] == "template" and char == "}":
                if state[1] == 0:
                    stack.pop()
                else:
                    state[1] -= 1
                index += 1
            else:
                index += 1
        else:
            closing = '"""' if state[0] == "raw" else '"'
            if text.startswith(closing, index):
                stack.pop()
                index += len(closing)
            elif text.startswith("${", index):
                stack.append(["template", 0])
                index += 2
            elif char == "\\" and state[0] == "string":
                blank(index, min(index + 2, length))
                index += 2
            else:
                blank(index, index + 1)
                index += 1
    return "".join(out)


def check_shapes(sources: dict[str, str], errors: list[str]) -> int:
    shapes_text = sources.get(SHAPES_FILE)
    if shapes_text is None:
        errors.append(f"{SHAPES_FILE} is missing, so corner shapes have no token source")
        return 0
    ceiling_match = CEILING.search(shapes_text)
    if ceiling_match is None:
        errors.append(f"{SHAPES_FILE} must declare MAX_CORNER_RADIUS_DP")
        return 0
    ceiling = int(ceiling_match.group(1))
    if ceiling != DOCUMENTED_CEILING_DP:
        errors.append(
            f"{SHAPES_FILE} sets MAX_CORNER_RADIUS_DP = {ceiling}, but ARCHITECTURE.md documents {DOCUMENTED_CEILING_DP} dp"
        )

    token_count = 0
    for path, raw in sources.items():
        text = strip_comments(raw)
        for match in SHAPE_CALL.finditer(text):
            where = f"{path}:{line_of(text, match.start())}"
            if path != SHAPES_FILE:
                errors.append(f"{where} builds {match.group(1)} directly. Use an AuraShapes token from ui/theme/Shapes.kt")
                continue
            token_count += 1
            arguments = split_arguments(call_arguments(text, match.end()))
            if not arguments:
                errors.append(f"{where} declares a shape without a radius")
            for argument in arguments:
                radius = DP_ARGUMENT.match(argument)
                if radius is None:
                    errors.append(f"{where} uses '{argument}'. Token radii are written in dp, never percent or px")
                elif float(radius.group(1)) > ceiling:
                    errors.append(f"{where} uses {argument}, above the {ceiling} dp ceiling")
        for match in CIRCLE_SHAPE.finditer(text):
            errors.append(f"{path}:{line_of(text, match.start())} uses CircleShape. Aura draws no circle or pill backdrops")
        for match in GLANCE_RADIUS.finditer(text):
            radius = DP_ARGUMENT.match(match.group(1).strip())
            if radius is None or float(radius.group(1)) > ceiling:
                errors.append(
                    f"{path}:{line_of(text, match.start())} gives a widget corner '{match.group(1).strip()}'. "
                    f"Widget radii are dp values at or under {ceiling}"
                )
    return token_count


def check_component_defaults(sources: dict[str, str], errors: list[str]) -> int:
    """Material 3 container buttons default to a full pill and nav items to a pill indicator."""
    checked = 0
    for path, raw in sources.items():
        text = strip_comments(raw)
        if "import androidx.glance" in text:
            continue
        for match in CONTAINER_BUTTON.finditer(text):
            checked += 1
            if not re.search(r"\bshape\s*=", call_arguments(text, match.end())):
                errors.append(
                    f"{path}:{line_of(text, match.start())} {match.group(1)} keeps Material's pill shape. Pass shape = AuraShapes.Medium"
                )
        for match in NAVIGATION_ITEM.finditer(text):
            checked += 1
            if "indicatorColor = Color.Transparent" not in call_arguments(text, match.end()):
                errors.append(
                    f"{path}:{line_of(text, match.start())} {match.group(1)} keeps Material's pill indicator. "
                    "Set indicatorColor = Color.Transparent"
                )
    return checked


def check_color_literals(
    sources: dict[str, str],
    errors: list[str],
    registry: dict[str, tuple[int, str]],
) -> int:
    registered = 0
    seen: set[str] = set()
    for path, raw in sources.items():
        if path.startswith(THEME_DIR):
            continue
        count = len(COLOR_LITERAL.findall(strip_comments(raw)))
        entry = registry.get(path)
        if entry is None:
            if count:
                errors.append(
                    f"{path} has {count} Color(0x...) literal(s). Take the colour from the theme, or register the file "
                    "in COLOR_LITERAL_REGISTRY with a reason"
                )
            continue
        seen.add(path)
        expected, _reason = entry
        if count != expected:
            errors.append(f"{path} has {count} Color(0x...) literal(s), but the registry records {expected}")
        registered += count
    for path in sorted(set(registry) - seen):
        errors.append(f"COLOR_LITERAL_REGISTRY lists {path}, which no longer exists")
    return registered


def check_badge_contrast(sources: dict[str, str], errors: list[str]) -> None:
    tones = sources.get(SOURCE_TONES_FILE)
    shared = sources.get(SHARED_COMPONENTS_FILE)
    if tones is None or shared is None:
        errors.append(f"{SOURCE_TONES_FILE} and {SHARED_COMPONENTS_FILE} must both exist for the badge contrast check")
        return
    if f"const val MIN_TEXT_CONTRAST = {DOCUMENTED_MIN_TEXT_CONTRAST}" not in tones:
        errors.append(f"{SOURCE_TONES_FILE} must keep MIN_TEXT_CONTRAST at WCAG's {DOCUMENTED_MIN_TEXT_CONTRAST}:1")
    tone_function = tones.partition("internal fun sourceBadgeTone(")[2].partition("\n}\n")[0]
    if "readableOn(" not in tone_function or "glassBackdrops(" not in tone_function:
        errors.append("sourceBadgeTone must pass its tone through readableOn against the glass card backdrops")
    badge = shared.partition("fun SourceBadge(")[2].partition("\n}\n")[0]
    if not badge:
        errors.append(f"{SHARED_COMPONENTS_FILE} no longer defines SourceBadge")
    elif "sourceBadgeTone(" not in badge:
        errors.append("SourceBadge must take its colour from sourceBadgeTone so it keeps 4.5:1 contrast")
    if "surfaceContainer.copy(alpha = GLASS_CARD_ALPHA)" not in shared:
        errors.append("GlassCard must paint with GLASS_CARD_ALPHA, the alpha the badge contrast check assumes")


def validate(
    repo_root: Path,
    registry: dict[str, tuple[int, str]] = COLOR_LITERAL_REGISTRY,
) -> dict[str, object]:
    sources = kotlin_sources(repo_root)
    if not sources:
        raise DesignTokenError(f"no Kotlin sources under {repo_root}")
    errors: list[str] = []
    token_count = check_shapes(sources, errors)
    component_count = check_component_defaults(sources, errors)
    registered_colors = check_color_literals(sources, errors, registry)
    check_badge_contrast(sources, errors)
    if errors:
        raise DesignTokenError("\n".join(errors))
    return {
        "status": "ok",
        "policyKind": "designTokens",
        "cornerRadiusCeilingDp": DOCUMENTED_CEILING_DP,
        "shapeTokenCount": token_count,
        "checkedComponentCalls": component_count,
        "registeredColorLiteralFiles": len(registry),
        "registeredColorLiterals": registered_colors,
        "checkedSourceFiles": len(sources),
    }


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
