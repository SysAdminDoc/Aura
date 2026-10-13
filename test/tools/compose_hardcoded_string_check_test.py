import json
import tempfile
import unittest
from pathlib import Path

from tools import compose_hardcoded_string_check

BASELINE = "docs/localization/hardcoded-string-baseline.json"


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_strings(repo_root: Path) -> None:
    write_text(
        repo_root / "app/src/main/res/values/strings.xml",
        "<resources><string name=\"app_name\">Aura</string></resources>",
    )


def write_screen(repo_root: Path, text: str = "Existing title") -> None:
    (repo_root / "app/src/full/java/com/freevibe/ui").mkdir(parents=True, exist_ok=True)
    write_text(
        repo_root / "app/src/main/java/com/freevibe/ui/ExampleScreen.kt",
        "\n".join(
            [
                "package com.freevibe.ui",
                "",
                "import androidx.compose.material3.Text",
                "",
                "@Composable",
                "fun ExampleScreen() {",
                f"    Text(\"{text}\")",
                "}",
            ]
        ),
    )


def write_state_source(repo_root: Path, text: str = "Existing error") -> None:
    write_text(
        repo_root / "app/src/main/java/com/freevibe/ui/ExampleViewModel.kt",
        "\n".join(
            [
                "package com.freevibe.ui",
                "data class ExampleState(val error: String? = null)",
                "fun control() = FilterControl(\"Existing filter\")",
                f"val state = ExampleState(error = \"{text}\")",
            ]
        ),
    )


def write_reviewed_baseline(repo_root: Path, reason: str = "Reviewed fixture entry.") -> Path:
    """What a person does by hand: list today's findings, each with a reason."""
    baseline_path = repo_root / BASELINE
    entries = compose_hardcoded_string_check.summarize_findings(
        compose_hardcoded_string_check.find_hardcoded_strings(
            repo_root,
            compose_hardcoded_string_check.DEFAULT_SCAN_ROOTS,
            compose_hardcoded_string_check.DEFAULT_IGNORED_PATH_FRAGMENTS,
        )
    )
    write_text(
        baseline_path,
        json.dumps(
            {
                "schemaVersion": 1,
                "policyKind": "composeHardcodedStringBaseline",
                "scanSourceRoots": compose_hardcoded_string_check.DEFAULT_SCAN_ROOTS,
                "ignoredPathFragments": compose_hardcoded_string_check.DEFAULT_IGNORED_PATH_FRAGMENTS,
                "allowedExistingStatus": "baselineGateActiveExtractionPending",
                "migrationPlan": compose_hardcoded_string_check.DEFAULT_MIGRATION_PLAN,
                "baseline": [{**entry, "reason": reason} for entry in entries],
            }
        ),
    )
    return baseline_path


def scan(repo_root: Path) -> set[tuple[str, str]]:
    findings = compose_hardcoded_string_check.find_hardcoded_strings(
        repo_root,
        compose_hardcoded_string_check.DEFAULT_SCAN_ROOTS,
        compose_hardcoded_string_check.DEFAULT_IGNORED_PATH_FRAGMENTS,
    )
    return {(finding.sink, finding.text) for finding in findings}


# One Kotlin line per sink the gate understands, and the finding it has to produce.
SINK_FIXTURES = [
    ('    Text("Fixture text")', ("Text", "Fixture text")),
    ('    Icon(icon, contentDescription = "Fixture description")', ("contentDescription", "Fixture description")),
    ('    Icon(Icons.Default.Close, "Fixture icon")', ("Icon", "Fixture icon")),
    ('    Image(painterResource(R.drawable.logo), "Fixture image")', ("Image", "Fixture image")),
    ('    Toast.makeText(context, "Fixture toast", Toast.LENGTH_SHORT).show()', ("Toast", "Fixture toast")),
    ('    snackbar.showSnackbar("Fixture snackbar")', ("Snackbar", "Fixture snackbar")),
    ('    snackbar.showSnackbar(message = "Fixture named snackbar")', ("message", "Fixture named snackbar")),
    ('    snackbar.showSnackbar(message = text, actionLabel = "Fixture action")', ("actionLabel", "Fixture action")),
    ('    OutlinedTextField(value, onChange, label = "Fixture label")', ("label", "Fixture label")),
    ('    AuraStateCard(title = "Fixture title")', ("title", "Fixture title")),
    ('    Modifier.semantics { stateDescription = "Fixture state" }', ("stateDescription", "Fixture state")),
    ('    Modifier.clickable(onClickLabel = "Fixture click") {}', ("onClickLabel", "Fixture click")),
    ('    Modifier.semantics { paneTitle = "Fixture pane" }', ("paneTitle", "Fixture pane")),
    ('    _state.update { it.copy(error = "Fixture error") }', ("error", "Fixture error")),
    ('    _state.update { it.copy(uploadError = "Fixture upload error") }', ("uploadError", "Fixture upload error")),
    ('    _state.update { it.copy(statusMessage = "Fixture status") }', ("statusMessage", "Fixture status")),
    ('    throw IllegalStateException("Fixture exception")', ("exception", "Fixture exception")),
    ('    error("Fixture error call")', ("exception", "Fixture error call")),
    ('    FilterControl("Fixture filter")', ("FilterControl", "Fixture filter")),
    ('    EditorPreset("Fixture preset")', ("EditorPreset", "Fixture preset")),
    ('    val name = raw.ifBlank { "Fixture fallback" }', ("ifBlank", "Fixture fallback")),
]


class ComposeHardcodedStringCheckTest(unittest.TestCase):
    def test_accepts_current_baseline(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_strings(repo_root)
            write_screen(repo_root)
            baseline_path = write_reviewed_baseline(repo_root)

            result = compose_hardcoded_string_check.validate_baseline(repo_root, baseline_path)

            self.assertEqual(result["status"], "ok")
            self.assertEqual(result["baselineEntries"], 1)
            self.assertEqual(result["currentFindings"], 1)

    def test_rejects_new_hardcoded_text_literal(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_strings(repo_root)
            write_screen(repo_root)
            baseline_path = write_reviewed_baseline(repo_root)
            write_screen(repo_root, "New visible copy")

            with self.assertRaisesRegex(
                compose_hardcoded_string_check.ComposeHardcodedStringError,
                "baseline drifted",
            ):
                compose_hardcoded_string_check.validate_baseline(repo_root, baseline_path)

    def test_rejects_count_increase_for_existing_literal(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_strings(repo_root)
            write_screen(repo_root)
            baseline_path = write_reviewed_baseline(repo_root)
            write_text(
                repo_root / "app/src/main/java/com/freevibe/ui/ExampleScreen.kt",
                "\n".join(
                    [
                        "package com.freevibe.ui",
                        "import androidx.compose.material3.Text",
                        "@Composable",
                        "fun ExampleScreen() {",
                        "    Text(\"Existing title\")",
                        "    Text(\"Existing title\")",
                        "}",
                    ]
                ),
            )

            with self.assertRaisesRegex(
                compose_hardcoded_string_check.ComposeHardcodedStringError,
                "countChanges",
            ):
                compose_hardcoded_string_check.validate_baseline(repo_root, baseline_path)

    def test_rejects_missing_migration_plan(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_strings(repo_root)
            write_screen(repo_root)
            baseline_path = write_reviewed_baseline(repo_root)
            baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
            baseline.pop("migrationPlan")
            baseline_path.write_text(json.dumps(baseline), encoding="utf-8")

            with self.assertRaisesRegex(
                compose_hardcoded_string_check.ComposeHardcodedStringError,
                "migrationPlan",
            ):
                compose_hardcoded_string_check.validate_baseline(repo_root, baseline_path)

    def test_rejects_an_entry_without_a_reason(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_strings(repo_root)
            write_screen(repo_root)
            baseline_path = write_reviewed_baseline(repo_root, reason="  ")

            with self.assertRaisesRegex(
                compose_hardcoded_string_check.ComposeHardcodedStringError,
                r"baseline\[0\]\.reason",
            ):
                compose_hardcoded_string_check.validate_baseline(repo_root, baseline_path)

    def test_write_mode_refuses_a_new_entry(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_strings(repo_root)
            write_screen(repo_root)
            baseline_path = write_reviewed_baseline(repo_root)
            before = baseline_path.read_text(encoding="utf-8")
            write_state_source(repo_root)

            with self.assertRaisesRegex(
                compose_hardcoded_string_check.ComposeHardcodedStringError,
                "only shrinks",
            ):
                compose_hardcoded_string_check.write_baseline(repo_root, baseline_path)
            self.assertEqual(before, baseline_path.read_text(encoding="utf-8"))

    def test_write_mode_refuses_a_count_increase(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_strings(repo_root)
            write_screen(repo_root)
            baseline_path = write_reviewed_baseline(repo_root)
            write_text(
                repo_root / "app/src/main/java/com/freevibe/ui/ExampleScreen.kt",
                'fun ExampleScreen() {\n    Text("Existing title")\n    Text("Existing title")\n}',
            )

            with self.assertRaisesRegex(
                compose_hardcoded_string_check.ComposeHardcodedStringError,
                "only shrinks",
            ):
                compose_hardcoded_string_check.write_baseline(repo_root, baseline_path)

    def test_write_mode_prunes_extracted_strings_and_keeps_reasons(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_strings(repo_root)
            write_screen(repo_root)
            write_state_source(repo_root)
            baseline_path = write_reviewed_baseline(repo_root, reason="Waiting on the state message follow-up.")
            write_state_source(repo_root, text="")

            baseline = compose_hardcoded_string_check.write_baseline(repo_root, baseline_path)

            remaining = {entry["text"]: entry["reason"] for entry in baseline["baseline"]}
            self.assertNotIn("Existing error", remaining)
            self.assertEqual("Waiting on the state message follow-up.", remaining["Existing title"])
            self.assertEqual(
                compose_hardcoded_string_check.validate_baseline(repo_root, baseline_path)["status"],
                "ok",
            )

    def test_write_preserves_policy_metadata(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_strings(repo_root)
            write_screen(repo_root)
            baseline_path = write_reviewed_baseline(repo_root)
            baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
            baseline["pseudolocaleReleaseGate"] = {"status": "active"}
            baseline["migrationPlan"] = {"nextSteps": ["Keep this policy"]}
            baseline_path.write_text(json.dumps(baseline), encoding="utf-8")

            written = compose_hardcoded_string_check.write_baseline(repo_root, baseline_path)

            self.assertEqual(written["pseudolocaleReleaseGate"]["status"], "active")
            self.assertEqual(written["migrationPlan"]["nextSteps"], ["Keep this policy"])

    def test_scans_viewmodel_state_and_editor_control_literals(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_strings(repo_root)
            write_screen(repo_root)
            write_state_source(repo_root)

            findings = scan(repo_root)

            self.assertIn(("error", "Existing error"), findings)
            self.assertIn(("FilterControl", "Existing filter"), findings)

    def test_each_sink_flags_its_fixture(self):
        for line, expected in SINK_FIXTURES:
            with self.subTest(sink=expected[0]), tempfile.TemporaryDirectory() as temp_dir:
                repo_root = Path(temp_dir)
                (repo_root / "app/src/full/java/com/freevibe/ui").mkdir(parents=True)
                write_text(
                    repo_root / "app/src/main/java/com/freevibe/ui/Fixture.kt",
                    f"package com.freevibe.ui\n\nfun fixture() {{\n{line}\n}}\n",
                )

                self.assertIn(expected, scan(repo_root))

    def test_text_builders_flag_prose_but_not_units_or_composables(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            (repo_root / "app/src/full/java/com/freevibe/ui").mkdir(parents=True)
            write_text(
                repo_root / "app/src/main/java/com/freevibe/ui/Builders.kt",
                "\n".join(
                    [
                        "package com.freevibe.ui",
                        "",
                        "internal fun downloadStatusLabel(done: Boolean, bytes: Long): String = when {",
                        '    done -> "Download complete"',
                        '    bytes > 0 -> "%.1f MB".format(bytes / 1e6)',
                        '    else -> "QHD"',
                        "}",
                        "",
                        "internal fun cardSummary(name: String): String =",
                        '    listOf(name, "saved to favorites").joinToString(", ")',
                        "",
                        "@Composable",
                        'fun screenTitle(): String = "Composable builders use stringResource"',
                        "",
                        "class ExampleViewModel {",
                        "    private fun disabledMessage(): String = \"Source is disabled in Settings\"",
                        "",
                        "    fun load() {",
                        '        log("Not a builder, not flagged")',
                        "    }",
                        "}",
                        "",
                        'fun mimeType(): String = "Video/MP4 Not A Builder"',
                    ]
                ),
            )

            builder = {text for sink, text in scan(repo_root) if sink == "textBuilder"}

            self.assertEqual(
                {"Download complete", "saved to favorites", "Source is disabled in Settings"},
                builder,
            )

    def test_live_baseline_is_valid(self):
        repo_root = Path(__file__).resolve().parents[2]
        baseline_path = repo_root / BASELINE
        if not baseline_path.exists():
            self.skipTest("live baseline is generated by the implementation batch")

        result = compose_hardcoded_string_check.validate_baseline(repo_root, baseline_path)

        self.assertEqual(result["status"], "ok")


if __name__ == "__main__":
    unittest.main()
