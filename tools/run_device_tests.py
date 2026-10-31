#!/usr/bin/env python3
"""Run instrumented tests on one named device and leave its installed Aura alone.

Gradle's connected*AndroidTest tasks install on every attached device and uninstall the app under
test afterward, which takes a real install and its data with it. This installs a suffixed debug
build beside whatever is on the phone, runs am instrument on that one serial and removes only what
it installed. Build the pair with:

    gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest -Paura.debugApplicationIdSuffix=.qa
"""

from __future__ import annotations

import argparse
import re
import shlex
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

RUNNER = "androidx.test.runner.AndroidJUnitRunner"
UNSUFFIXED_PACKAGE = "com.freevibe"
STATUS_NAMES = {0: "passed", -1: "error", -2: "failed", -3: "ignored", -4: "skipped"}
STATUS_FIELD = re.compile(r"INSTRUMENTATION_STATUS: (class|test)=(.+)")
STATUS_CODE = re.compile(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)")
# Windows only: keeps each adb call from opening a console window.
NO_WINDOW = {"creationflags": 0x08000000} if sys.platform == "win32" else {}


class DeviceTestError(ValueError):
    """Raised when the run can't be trusted or would touch a real install."""


@dataclass(frozen=True)
class DeviceTestResult:
    class_name: str
    test_name: str
    status: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Aura's instrumented tests on one device.")
    parser.add_argument("--serial", required=True, help="adb serial of the device to use.")
    parser.add_argument("--apk", required=True, type=Path, help="Suffixed debug APK.")
    parser.add_argument("--test-apk", required=True, type=Path, help="Its androidTest APK.")
    parser.add_argument("--package", default="com.freevibe.qa", help="Application id of --apk.")
    parser.add_argument("--class", dest="classes", action="append", default=[], help="Test class or class#method; repeatable.")
    parser.add_argument("--arg", dest="args", action="append", default=[], help="Instrumentation argument as key=value; repeatable.")
    parser.add_argument("--adb", default="adb", help="adb executable.")
    parser.add_argument("--log", type=Path, help="Write am instrument's raw output here.")
    return parser.parse_args()


def require_suffixed_package(package: str) -> None:
    if package == UNSUFFIXED_PACKAGE or not package.startswith(f"{UNSUFFIXED_PACKAGE}."):
        raise DeviceTestError(f"{package} isn't a suffixed Aura build, so uninstalling it could take a real install's data")


def instrument_command(package: str, classes: list[str], arguments: list[str]) -> list[str]:
    command = ["am", "instrument", "-w", "-r"]
    for argument in arguments:
        key, separator, value = argument.partition("=")
        if not separator or not key:
            raise DeviceTestError(f"--arg needs key=value, got {argument}")
        command += ["-e", key, value]
    if classes:
        command += ["-e", "class", ",".join(classes)]
    command.append(f"{package}.test/{RUNNER}")
    return command


def device_shell_line(command: list[str]) -> str:
    """adb shell joins its arguments with spaces and the device's sh splits them again, so a URL's ? and & need quoting here."""
    return " ".join(shlex.quote(part) for part in command)


def parse_results(output: str) -> list[DeviceTestResult]:
    """Read am instrument's raw status blocks. A code of 1 only announces that a test started."""
    results: list[DeviceTestResult] = []
    fields: dict[str, str] = {}
    for line in output.splitlines():
        field = STATUS_FIELD.match(line.strip())
        if field:
            fields[field.group(1)] = field.group(2)
            continue
        code = STATUS_CODE.match(line.strip())
        if code and int(code.group(1)) != 1:
            status = STATUS_NAMES.get(int(code.group(1)), f"code {code.group(1)}")
            results.append(DeviceTestResult(fields.get("class", "?"), fields.get("test", "?"), status))
    return results


def check_run(output: str, results: list[DeviceTestResult]) -> None:
    """A run counts only when it reached the end and something actually ran."""
    if "INSTRUMENTATION_FAILED" in output or "Process crashed" in output or "INSTRUMENTATION_CODE: -1" not in output:
        raise DeviceTestError("the instrumentation run didn't finish cleanly")
    if not results:
        raise DeviceTestError("no test ran")
    bad = [f"{result.class_name}#{result.test_name}: {result.status}" for result in results if result.status not in {"passed", "skipped", "ignored"}]
    if bad:
        raise DeviceTestError("\n".join(bad))


def main() -> int:
    args = parse_args()
    try:
        require_suffixed_package(args.package)
        command = instrument_command(args.package, args.classes, args.args)

        def adb(*parts: str) -> subprocess.CompletedProcess[str]:
            return subprocess.run(
                [args.adb, "-s", args.serial, *parts],
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                **NO_WINDOW,
            )

        try:
            for apk in (args.apk, args.test_apk):
                installed = adb("install", "-r", "-t", str(apk))
                if installed.returncode != 0:
                    raise DeviceTestError(f"couldn't install {apk}: {installed.stdout}{installed.stderr}")
            output = adb("shell", device_shell_line(command)).stdout
        finally:
            adb("uninstall", f"{args.package}.test")
            adb("uninstall", args.package)

        if args.log:
            args.log.write_text(output, encoding="utf-8", newline="\n")
        results = parse_results(output)
        for result in results:
            print(f"{result.status:8} {result.class_name}#{result.test_name}")
        check_run(output, results)
    except DeviceTestError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
