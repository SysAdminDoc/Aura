from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.design_token_check import DesignTokenError, validate


REPO_ROOT = Path(__file__).resolve().parents[2]
THEME = "app/src/main/java/com/freevibe/ui/theme"
SCREEN = "app/src/main/java/com/freevibe/ui/screens/Sample.kt"
WIDGET = "app/src/main/java/com/freevibe/widget/Widget.kt"

SHAPES = """package com.freevibe.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Never a CircleShape or RoundedCornerShape(50). */
const val MAX_CORNER_RADIUS_DP = 12

object AuraShapes {
    val Small = RoundedCornerShape(4.dp)
    val Medium = RoundedCornerShape(8.dp)
    val SheetTop = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
}
"""

SOURCE_TONES = """package com.freevibe.ui.theme

internal const val MIN_TEXT_CONTRAST = 4.5
internal const val GLASS_CARD_ALPHA = 0.72f
internal val SourceBrandTones = mapOf("BING" to Color(0xFF00809D))

internal fun sourceBadgeTone(source: String, scheme: ColorScheme): Color {
    val tone = SourceBrandTones[source] ?: scheme.onSurfaceVariant
    return readableOn(tone, glassBackdrops(scheme.surfaceContainer))
}
"""

SHARED = """package com.freevibe.ui.components

@Composable
fun GlassCard(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = GLASS_CARD_ALPHA)) { content() }
}

@Composable
fun SourceBadge(source: String, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val color = remember(source, colorScheme) { sourceBadgeTone(source, colorScheme) }
    Text(text = source, modifier = modifier, color = color)
}
"""

SAMPLE = """package com.freevibe.ui.screens

@Composable
fun Sample(onPick: () -> Unit) {
    Card(shape = AuraShapes.Medium) {
        Button(
            onClick = { launcher.launch(arrayOf("image/*")) },
            shape = AuraShapes.Medium,
        ) { Text("Pick (one)") }
        OutlinedButton(onClick = onPick, shape = AuraShapes.Small) { Text("Cancel") }
        TextButton(onClick = onPick) { Text("Later") }
        NavigationBarItem(
            selected = true,
            onClick = onPick,
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
        )
    }
}
"""

GLANCE = """package com.freevibe.widget

import androidx.glance.Button

@Composable
fun Widget() {
    Column(modifier = GlanceModifier.cornerRadius(12.dp)) {
        Button(text = "Open", onClick = actionStartActivity<MainActivity>())
    }
}
"""


class DesignTokenCheckTest(unittest.TestCase):
    def test_repository_design_tokens_hold(self) -> None:
        result = validate(REPO_ROOT)

        self.assertEqual("ok", result["status"])
        self.assertEqual(12, result["cornerRadiusCeilingDp"])
        self.assertGreater(result["shapeTokenCount"], 0)

    def test_fixture_with_tokens_only_passes(self) -> None:
        with self.fixture() as root:
            result = validate(root, registry={})

        self.assertEqual("ok", result["status"])
        self.assertEqual(3, result["shapeTokenCount"])

    def test_literal_shape_outside_the_token_file_fails(self) -> None:
        self.assert_fails(
            {SCREEN: SAMPLE.replace("Card(shape = AuraShapes.Medium)", "Card(shape = RoundedCornerShape(8.dp))")},
            "Sample.kt:5 builds RoundedCornerShape directly",
        )

    def test_pill_radius_in_the_token_file_fails(self) -> None:
        self.assert_fails(
            {f"{THEME}/Shapes.kt": SHAPES.replace("RoundedCornerShape(8.dp)", "RoundedCornerShape(50)")},
            "uses '50'. Token radii are written in dp",
        )

    def test_radius_above_the_ceiling_fails(self) -> None:
        self.assert_fails(
            {f"{THEME}/Shapes.kt": SHAPES.replace("topStart = 12.dp", "topStart = 24.dp")},
            "uses topStart = 24.dp, above the 12 dp ceiling",
        )

    def test_raising_the_ceiling_without_the_docs_fails(self) -> None:
        self.assert_fails(
            {f"{THEME}/Shapes.kt": SHAPES.replace("MAX_CORNER_RADIUS_DP = 12", "MAX_CORNER_RADIUS_DP = 28")},
            "but ARCHITECTURE.md documents 12 dp",
        )

    def test_circle_shape_fails_but_a_comment_naming_it_does_not(self) -> None:
        self.assert_fails(
            {SCREEN: SAMPLE.replace("Card(shape = AuraShapes.Medium)", "Card(shape = CircleShape)")},
            "Sample.kt:5 uses CircleShape",
        )

    def test_container_button_on_the_material_pill_fails(self) -> None:
        self.assert_fails(
            {SCREEN: SAMPLE.replace("OutlinedButton(onClick = onPick, shape = AuraShapes.Small)", "OutlinedButton(onClick = onPick)")},
            "Sample.kt:10 OutlinedButton keeps Material's pill shape",
        )

    def test_navigation_item_with_the_pill_indicator_fails(self) -> None:
        self.assert_fails(
            {SCREEN: SAMPLE.replace("indicatorColor = Color.Transparent", "selectedIconColor = Color.Transparent")},
            "NavigationBarItem keeps Material's pill indicator",
        )

    def test_widget_radius_above_the_ceiling_fails(self) -> None:
        self.assert_fails(
            {WIDGET: GLANCE.replace("cornerRadius(12.dp)", "cornerRadius(16.dp)")},
            "Widget.kt:7 gives a widget corner '16.dp'",
        )

    def test_unregistered_colour_literal_fails_and_theme_literals_pass(self) -> None:
        self.assert_fails(
            {SCREEN: SAMPLE.replace("Text(\"Later\")", "Text(\"Later\", color = Color(0xFFFF6A5B))")},
            "Sample.kt has 1 Color(0x...) literal(s). Take the colour from the theme",
        )

    def test_registered_colour_count_must_match(self) -> None:
        with self.fixture() as root:
            with self.assertRaisesRegex(DesignTokenError, "Sample.kt has 0 Color\\(0x...\\) literal\\(s\\), but the registry records 2"):
                validate(root, registry={SCREEN: (2, "Swatches")})
            with self.assertRaisesRegex(DesignTokenError, "lists app/src/main/java/com/freevibe/Gone.kt, which no longer exists"):
                validate(root, registry={"app/src/main/java/com/freevibe/Gone.kt": (1, "Old")})

    def test_badge_that_bypasses_the_contrast_tone_fails(self) -> None:
        self.assert_fails(
            {"app/src/main/java/com/freevibe/ui/components/SharedComponents.kt": SHARED.replace(
                "remember(source, colorScheme) { sourceBadgeTone(source, colorScheme) }",
                "MaterialTheme.colorScheme.outline",
            )},
            "SourceBadge must take its colour from sourceBadgeTone",
        )

    def test_lowered_contrast_floor_fails(self) -> None:
        self.assert_fails(
            {f"{THEME}/SourceTones.kt": SOURCE_TONES.replace("MIN_TEXT_CONTRAST = 4.5", "MIN_TEXT_CONTRAST = 3.0")},
            "must keep MIN_TEXT_CONTRAST at WCAG's 4.5:1",
        )

    def test_tone_function_that_skips_readable_on_fails(self) -> None:
        self.assert_fails(
            {f"{THEME}/SourceTones.kt": SOURCE_TONES.replace(
                "return readableOn(tone, glassBackdrops(scheme.surfaceContainer))", "return tone",
            )},
            "sourceBadgeTone must pass its tone through readableOn",
        )

    def test_glass_card_alpha_drift_fails(self) -> None:
        self.assert_fails(
            {"app/src/main/java/com/freevibe/ui/components/SharedComponents.kt": SHARED.replace(
                "copy(alpha = GLASS_CARD_ALPHA)", "copy(alpha = 0.4f)",
            )},
            "GlassCard must paint with GLASS_CARD_ALPHA",
        )

    def test_material_pill_defaults_fail_even_as_an_explicit_shape(self) -> None:
        for pill in ("ShapeDefaults.Full", "ButtonDefaults.shape", "IconButtonDefaults.filledShape"):
            with self.subTest(pill=pill):
                self.assert_fails(
                    {SCREEN: SAMPLE.replace("shape = AuraShapes.Small", f"shape = {pill}")},
                    f"Sample.kt:10 uses {pill}, a Material default that draws a pill or circle",
                )

    def test_shape_named_inside_a_lambda_does_not_count(self) -> None:
        self.assert_fails(
            {SCREEN: SAMPLE.replace(
                "OutlinedButton(onClick = onPick, shape = AuraShapes.Small)",
                "OutlinedButton(onClick = { resize(shape = AuraShapes.Small) })",
            )},
            "Sample.kt:10 OutlinedButton keeps Material's pill shape",
        )

    def test_aliased_shape_or_button_import_fails(self) -> None:
        for aliased in (
            "import androidx.compose.foundation.shape.RoundedCornerShape as Rounded",
            "import androidx.compose.material3.Button as Pill",
        ):
            with self.subTest(aliased=aliased):
                self.assert_fails(
                    {SCREEN: SAMPLE.replace("@Composable", f"{aliased}\n\n@Composable", 1)},
                    "Sample.kt:3 aliases a guarded import",
                )

    def test_every_spelling_of_a_colour_literal_is_counted(self) -> None:
        for literal in ("Color (0xFF112233)", "Color(4278190080)", "Color(color = 0xFF112233)", "Color(0.2f, 0.4f, 0.6f)"):
            with self.subTest(literal=literal):
                self.assert_fails(
                    {SCREEN: SAMPLE.replace("Text(\"Later\")", f"Text(\"Later\", color = {literal})")},
                    "Sample.kt has 1 Color(0x...) literal(s)",
                )

    def test_badge_that_computes_the_tone_but_draws_another_colour_fails(self) -> None:
        self.assert_fails(
            {"app/src/main/java/com/freevibe/ui/components/SharedComponents.kt": SHARED.replace(
                "color = color)", "color = MaterialTheme.colorScheme.outline)",
            )},
            "SourceBadge must take its colour from sourceBadgeTone",
        )

    def assert_fails(self, overrides: dict[str, str], message: str) -> None:
        with self.fixture(overrides) as root:
            with self.assertRaises(DesignTokenError) as raised:
                validate(root, registry={})
        self.assertIn(message, str(raised.exception))

    def fixture(self, overrides: dict[str, str] | None = None):
        files = {
            f"{THEME}/Shapes.kt": SHAPES,
            f"{THEME}/SourceTones.kt": SOURCE_TONES,
            "app/src/main/java/com/freevibe/ui/components/SharedComponents.kt": SHARED,
            SCREEN: SAMPLE,
            WIDGET: GLANCE,
        }
        files.update(overrides or {})
        return _Fixture(files)


class _Fixture:
    def __init__(self, files: dict[str, str]) -> None:
        self.files = files
        self.temp = tempfile.TemporaryDirectory()

    def __enter__(self) -> Path:
        root = Path(self.temp.name)
        for relative, text in self.files.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        return root

    def __exit__(self, *exc: object) -> None:
        self.temp.cleanup()


if __name__ == "__main__":
    unittest.main()
