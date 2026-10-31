from __future__ import annotations

import shlex
import unittest

from tools.run_device_tests import (
    DeviceTestError,
    DeviceTestResult,
    check_run,
    device_shell_line,
    instrument_command,
    parse_results,
    require_suffixed_package,
)

FINISHED = """\
INSTRUMENTATION_STATUS: class=com.freevibe.service.AudioTrimmerInstrumentedTest
INSTRUMENTATION_STATUS: current=1
INSTRUMENTATION_STATUS: test=flacExportKeepsEverySample
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.freevibe.service.AudioTrimmerInstrumentedTest
INSTRUMENTATION_STATUS: test=flacExportKeepsEverySample
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.freevibe.service.AudioTrimmerInstrumentedTest
INSTRUMENTATION_STATUS: test=oggExportCarriesTheLoopTag
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.freevibe.service.AudioTrimmerInstrumentedTest
INSTRUMENTATION_STATUS: test=oggExportCarriesTheLoopTag
INSTRUMENTATION_STATUS_CODE: -4
INSTRUMENTATION_RESULT: stream=OK (1 test)
INSTRUMENTATION_CODE: -1
"""


class RunDeviceTestsTest(unittest.TestCase):
    def test_refuses_the_package_a_real_install_uses(self) -> None:
        for package in ("com.freevibe", "com.freevibeother", "org.example.qa"):
            with self.assertRaises(DeviceTestError):
                require_suffixed_package(package)
        require_suffixed_package("com.freevibe.qa")

    def test_builds_the_instrument_command(self) -> None:
        self.assertEqual(
            instrument_command("com.freevibe.qa", ["a.B", "a.C#d"], ["ytUrl=https://example.com/x?y=1"]),
            [
                "am", "instrument", "-w", "-r",
                "-e", "ytUrl", "https://example.com/x?y=1",
                "-e", "class", "a.B,a.C#d",
                "com.freevibe.qa.test/androidx.test.runner.AndroidJUnitRunner",
            ],
        )

    def test_a_url_with_a_query_stays_one_argument_on_the_device(self) -> None:
        command = instrument_command("com.freevibe.qa", [], ["ytUrl=https://example.com/watch?v=abc&t=1"])

        line = device_shell_line(command)

        self.assertEqual(shlex.split(line), command)
        self.assertIn("'https://example.com/watch?v=abc&t=1'", line)

    def test_rejects_an_argument_without_a_value(self) -> None:
        with self.assertRaises(DeviceTestError):
            instrument_command("com.freevibe.qa", [], ["ytUrl"])

    def test_reads_each_finished_test_once(self) -> None:
        results = parse_results(FINISHED)

        self.assertEqual(
            results,
            [
                DeviceTestResult("com.freevibe.service.AudioTrimmerInstrumentedTest", "flacExportKeepsEverySample", "passed"),
                DeviceTestResult("com.freevibe.service.AudioTrimmerInstrumentedTest", "oggExportCarriesTheLoopTag", "skipped"),
            ],
        )
        check_run(FINISHED, results)

    def test_a_failed_test_fails_the_run(self) -> None:
        output = FINISHED.replace("INSTRUMENTATION_STATUS_CODE: 0", "INSTRUMENTATION_STATUS_CODE: -2")

        with self.assertRaisesRegex(DeviceTestError, "flacExportKeepsEverySample: failed"):
            check_run(output, parse_results(output))

    def test_a_crashed_process_fails_even_with_passes_before_it(self) -> None:
        output = FINISHED.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE: 0")

        with self.assertRaisesRegex(DeviceTestError, "didn't finish cleanly"):
            check_run(output, parse_results(output))

    def test_a_run_where_nothing_ran_fails(self) -> None:
        output = "INSTRUMENTATION_RESULT: stream=OK (0 tests)\nINSTRUMENTATION_CODE: -1\n"

        with self.assertRaisesRegex(DeviceTestError, "no test ran"):
            check_run(output, parse_results(output))


if __name__ == "__main__":
    unittest.main()
