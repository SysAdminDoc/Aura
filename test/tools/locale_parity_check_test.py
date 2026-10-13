from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.locale_parity_check import FALLBACKS, LocaleParityError, validate

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT = "app/src/main/res/values/strings.xml"
ZH = "app/src/main/res/values-zh/strings.xml"

BASE_DEFAULT = """<resources>
    <string name="title">Title</string>
    <string name="count">%1$d items, %2$s</string>
    <string name="skip_me" translatable="false">Aura</string>
    <plurals name="files">
        <item quantity="one">%1$d file</item>
        <item quantity="other">%1$d files</item>
    </plurals>
    <string-array name="tabs">
        <item>One</item>
        <item>Two</item>
    </string-array>
</resources>
"""

BASE_ZH = """<resources>
    <string name="title">标题</string>
    <string name="count">%2$s，共 %1$d 项</string>
    <plurals name="files">
        <item quantity="other">%1$d 个文件</item>
    </plurals>
    <string-array name="tabs">
        <item>一</item>
        <item>二</item>
    </string-array>
</resources>
"""


def write(root: Path, relative: str, text: str) -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def make_repo(root: Path, default: str = BASE_DEFAULT, zh: str = BASE_ZH,
              fallbacks: dict | None = None) -> Path:
    write(root, DEFAULT, default)
    write(root, ZH, zh)
    write(root, FALLBACKS, json.dumps({"fallbacks": fallbacks or {}}))
    return root


class LocaleParityLiveTest(unittest.TestCase):
    def test_repository_translations_are_in_step(self) -> None:
        result = validate(REPO_ROOT)
        self.assertGreaterEqual(result["localesChecked"], 2)

    def test_fallback_list_ships_empty_or_with_reasons(self) -> None:
        data = json.loads((REPO_ROOT / FALLBACKS).read_text(encoding="utf-8"))
        for key, reason in data["fallbacks"].items():
            self.assertTrue(reason.strip(), key)


class LocaleParityFixtureTest(unittest.TestCase):
    def run_gate(self, **kwargs) -> str:
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(Path(tmp), **kwargs)
            try:
                validate(root)
            except LocaleParityError as exc:
                return str(exc)
        return ""

    def test_clean_fixture_passes(self) -> None:
        self.assertEqual(self.run_gate(), "")

    def test_missing_key_fails(self) -> None:
        zh = BASE_ZH.replace('    <string name="title">标题</string>\n', "")
        self.assertIn("missing translation for string 'title'", self.run_gate(zh=zh))

    def test_missing_plural_and_array_fail(self) -> None:
        zh = "<resources>\n" + '    <string name="title">标题</string>\n' + \
            '    <string name="count">%2$s %1$d</string>\n</resources>\n'
        message = self.run_gate(zh=zh)
        self.assertIn("plurals 'files'", message)
        self.assertIn("string-array 'tabs'", message)

    def test_wrong_kind_fails(self) -> None:
        zh = BASE_ZH.replace(
            '<plurals name="files">\n        <item quantity="other">%1$d 个文件</item>\n    </plurals>',
            '<string name="files">%1$d 个文件</string>',
        )
        message = self.run_gate(zh=zh)
        self.assertIn("missing translation for plurals 'files'", message)
        self.assertIn("string 'files' has no default of the same kind", message)

    def test_placeholder_mismatch_fails(self) -> None:
        zh = BASE_ZH.replace("%2$s，共 %1$d 项", "共 %1$d 项")
        self.assertIn("placeholders differ in 'count'", self.run_gate(zh=zh))

    def test_plural_placeholder_mismatch_fails(self) -> None:
        zh = BASE_ZH.replace("%1$d 个文件", "多个文件")
        self.assertIn("placeholders differ in 'files'", self.run_gate(zh=zh))

    def test_reordered_placeholders_pass(self) -> None:
        self.assertEqual(self.run_gate(), "")

    def test_literal_percent_may_differ(self) -> None:
        default = BASE_DEFAULT.replace("<string name=\"title\">Title</string>",
                                       "<string name=\"title\">%1$d percent</string>")
        zh = BASE_ZH.replace("<string name=\"title\">标题</string>",
                             "<string name=\"title\">%1$d%%</string>")
        self.assertEqual(self.run_gate(default=default, zh=zh), "")

    def test_extra_key_fails(self) -> None:
        zh = BASE_ZH.replace("</resources>", '    <string name="ghost">幽灵</string>\n</resources>')
        self.assertIn("string 'ghost' has no default", self.run_gate(zh=zh))

    def test_array_length_mismatch_fails(self) -> None:
        zh = BASE_ZH.replace("        <item>二</item>\n", "")
        self.assertIn("array 'tabs' has 1 items, the default has 2", self.run_gate(zh=zh))

    def test_non_translatable_key_is_not_required(self) -> None:
        self.assertEqual(self.run_gate(), "")

    def test_doubled_percent_without_arguments_fails(self) -> None:
        default = BASE_DEFAULT.replace("Title", "Below 15%% battery")
        zh = BASE_ZH.replace("标题", "低于 15%%")
        message = self.run_gate(default=default, zh=zh)
        self.assertIn("doubled percent sign", message)
        self.assertIn("values/strings.xml", message)
        self.assertIn("values-zh/strings.xml", message)

    def test_doubled_percent_beside_an_argument_passes(self) -> None:
        default = BASE_DEFAULT.replace("<string name=\"title\">Title</string>",
                                       "<string name=\"title\">%1$d%% done</string>")
        zh = BASE_ZH.replace("<string name=\"title\">标题</string>",
                             "<string name=\"title\">已完成 %1$d%%</string>")
        self.assertEqual(self.run_gate(default=default, zh=zh), "")

    def test_single_percent_without_arguments_passes(self) -> None:
        default = BASE_DEFAULT.replace("<string name=\"title\">Title</string>",
                                       "<string name=\"title\" formatted=\"false\">Under 15%</string>")
        zh = BASE_ZH.replace("<string name=\"title\">标题</string>",
                             "<string name=\"title\" formatted=\"false\">低于 15%</string>")
        self.assertEqual(self.run_gate(default=default, zh=zh), "")

    def test_bare_apostrophe_fails(self) -> None:
        default = BASE_DEFAULT.replace("Title", "Couldn't open")
        self.assertIn("unescaped quote", self.run_gate(default=default))

    def test_escaped_apostrophe_and_quote_pass(self) -> None:
        default = BASE_DEFAULT.replace("Title", "Couldn\\'t open \\\"x\\\"")
        self.assertEqual(self.run_gate(default=default), "")

    def test_bare_double_quote_fails(self) -> None:
        zh = BASE_ZH.replace("标题", '带"引号"')
        self.assertIn("unescaped quote", self.run_gate(zh=zh))

    def test_body_wrapped_in_double_quotes_may_hold_apostrophes(self) -> None:
        default = BASE_DEFAULT.replace("Title", "\"Couldn't open\"")
        self.assertEqual(self.run_gate(default=default), "")

    def test_apostrophe_in_array_item_fails(self) -> None:
        default = BASE_DEFAULT.replace("<item>One</item>", "<item>Won't</item>")
        self.assertIn("unescaped quote", self.run_gate(default=default))

    def test_listed_fallback_allows_missing_key(self) -> None:
        zh = BASE_ZH.replace('    <string name="title">标题</string>\n', "")
        reason = {"title": "Brand name stays in English for now"}
        self.assertEqual(self.run_gate(zh=zh, fallbacks=reason), "")

    def test_stale_fallback_fails(self) -> None:
        message = self.run_gate(fallbacks={"title": "was missing once"})
        self.assertIn("'title' is no longer missing", message)

    def test_fallback_without_reason_fails(self) -> None:
        zh = BASE_ZH.replace('    <string name="title">标题</string>\n', "")
        message = self.run_gate(zh=zh, fallbacks={"title": "  "})
        self.assertIn("'title' has no reason", message)
        self.assertIn("missing translation for string 'title'", message)

    def test_missing_locale_file_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(Path(tmp))
            (root / ZH).unlink()
            with self.assertRaises(LocaleParityError) as caught:
                validate(root)
            self.assertIn("missing file", str(caught.exception))

    def test_extra_locale_folder_is_checked_too(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(Path(tmp))
            write(root, "app/src/main/res/values-de/strings.xml",
                  '<resources><string name="title">Titel</string></resources>')
            with self.assertRaises(LocaleParityError) as caught:
                validate(root)
            self.assertIn("values-de", str(caught.exception))


class LocaleParityFolderTest(unittest.TestCase):
    """A values folder is read the way Android merges it: every XML file together."""

    SECOND_ZH = "app/src/main/res/values-zh/strings_recent_features.xml"
    TITLE_LINE = '    <string name="title">标题</string>\n'

    def gate(self, zh: str, second: str) -> str:
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(Path(tmp), zh=zh)
            write(root, self.SECOND_ZH, second)
            try:
                validate(root)
            except LocaleParityError as exc:
                return str(exc)
            return ""

    def test_a_key_translated_in_a_second_file_counts(self) -> None:
        second = "<resources>\n" + self.TITLE_LINE + "</resources>\n"

        self.assertEqual("", self.gate(BASE_ZH.replace(self.TITLE_LINE, ""), second))

    def test_a_key_defined_in_two_files_is_reported(self) -> None:
        second = "<resources>\n" + self.TITLE_LINE + "</resources>\n"

        message = self.gate(BASE_ZH, second)

        self.assertIn("string 'title' is already defined in app/src/main/res/values-zh/strings.xml", message)

    def test_a_bare_quote_in_a_second_file_is_reported(self) -> None:
        second = '<resources>\n    <string name="title">It\'s here</string>\n</resources>\n'

        self.assertIn("unescaped quote", self.gate(BASE_ZH.replace(self.TITLE_LINE, ""), second))

if __name__ == "__main__":
    unittest.main()
