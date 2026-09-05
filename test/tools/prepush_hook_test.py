from __future__ import annotations

import re
import subprocess
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
HOOK = REPO_ROOT / "tools" / "hooks" / "pre-push"
GATE_LINE = re.compile(r"^tools/[a-z0-9_]+\.py$")


def hook_text() -> str:
    return HOOK.read_text(encoding="utf-8")


def declared_gates() -> list[str]:
    """The gate list the hook actually iterates, read out of the script."""
    body = hook_text()
    block = body.split('gates="', 1)[1].split('"', 1)[0]
    return [line.strip() for line in block.splitlines() if GATE_LINE.match(line.strip())]


class PrePushHookTest(unittest.TestCase):
    def test_the_hook_exists_and_is_a_posix_shell_script(self) -> None:
        self.assertTrue(HOOK.is_file(), "tools/hooks/pre-push is missing")
        self.assertTrue(hook_text().startswith("#!/bin/sh"))

    def test_the_hook_uses_lf_line_endings(self) -> None:
        # Git for Windows runs hooks through sh. A CRLF script fails with
        # "cannot execute: required file not found", which reads like a missing
        # hook rather than a line-ending problem.
        self.assertNotIn(b"\r\n", HOOK.read_bytes())

    def test_every_gate_the_hook_names_exists(self) -> None:
        gates = declared_gates()
        self.assertGreaterEqual(len(gates), 4)
        for gate in gates:
            self.assertTrue((REPO_ROOT / gate).is_file(), f"{gate} does not exist")

    def test_the_hook_runs_the_publication_gate(self) -> None:
        # This is the check the hook exists for. 6.39.0 through 6.41.0 and then
        # 6.45.1 and 6.45.2 were all declared and never released.
        self.assertIn("tools/release_publication_check.py", declared_gates())

    def test_the_hook_is_marked_executable_in_the_index(self) -> None:
        result = subprocess.run(
            ["git", "ls-files", "-s", "tools/hooks/pre-push"],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode != 0 or not result.stdout.strip():
            self.skipTest("hook is not tracked yet, so it has no index mode")
        mode = result.stdout.split()[0]
        self.assertEqual(
            "100755",
            mode,
            "the hook needs the exec bit in the index or git will not run it on a "
            "fresh clone; set it with: git update-index --chmod=+x tools/hooks/pre-push",
        )

    def test_the_hook_probes_absolute_python_paths_as_well_as_names(self) -> None:
        # Git for Windows runs hooks under its own sh, whose PATH often has no
        # Python even when the calling shell does. Probing only `py`/`python3`/
        # `python` made the hook exit "no usable Python found" on this machine,
        # which would have read as the gates passing if the exit code were 0.
        body = hook_text()
        self.assertIn("Programs/Python/Python313/python.exe", body)
        self.assertIn("$LOCALAPPDATA", body)
        self.assertIn("$HOME/AppData/Local", body)

    def test_the_hook_reports_a_missing_python_rather_than_passing(self) -> None:
        body = hook_text()
        marker = body.index("no usable Python found")
        self.assertIn("exit 1", body[marker : marker + 200])

    def test_the_hook_honours_an_explicit_escape_hatch_only_via_env(self) -> None:
        # A documented, named escape hatch beats people reaching for
        # --no-verify, which silences every hook at once.
        self.assertIn("AURA_SKIP_PREPUSH", hook_text())
        self.assertNotIn("--no-verify", hook_text())


if __name__ == "__main__":
    unittest.main()
