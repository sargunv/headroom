#!/usr/bin/env python3
"""Check the packaged app, then render its isolated, demo-only native smoke scene."""

import argparse
from pathlib import Path
import plistlib
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "app",
        nargs="?",
        type=Path,
        default=Path(__file__).resolve().parents[1]
        / "build/compose/binaries/main/app/Headroom.app",
    )
    app = parser.parse_args().app.resolve()
    plist = app / "Contents/Info.plist"
    if not plist.is_file():
        raise ValueError("Build the app with :desktopApp:createDistributable first.")
    with plist.open("rb") as source:
        info = plistlib.load(source)
    if info.get("CFBundleIdentifier") != "dev.sebastiano.headroom.desktop":
        raise ValueError("The app has an unexpected bundle identifier.")
    minimum = tuple(int(part) for part in info.get("LSMinimumSystemVersion", "0").split("."))
    if minimum < (13,):
        raise ValueError("The app must declare macOS 13 or newer.")
    protocols = {
        scheme
        for entry in info.get("CFBundleURLTypes", [])
        for scheme in entry.get("CFBundleURLSchemes", [])
    }
    if "headroom" not in protocols:
        raise ValueError("The Headroom OAuth return protocol is not registered in the bundle.")
    configuration = app / "Contents/app/Headroom.cfg"
    if "-XstartOnFirstThread" not in configuration.read_text():
        raise ValueError("The Tao launcher must start on the first thread.")
    if not (app / "Contents/runtime").is_dir():
        raise ValueError("The app must include its Java runtime.")
    launcher = app / "Contents/MacOS/Headroom"
    result = subprocess.run(
        [str(launcher), "--smoke-test"],
        check=True,
        capture_output=True,
        text=True,
        timeout=45,
    )
    if "HEADROOM_SMOKE_TEST_OK:" not in result.stdout:
        raise ValueError("The native smoke scene did not report render and status-item readiness.")
    if result.stdout:
        print(result.stdout, end="")
    if result.stderr:
        print(result.stderr, file=sys.stderr, end="")
    print(f"Packaged macOS smoke test passed: {app}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print(f"Desktop smoke test failed: {error}", file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError):
            print(error.stdout or "", file=sys.stderr)
            print(error.stderr or "", file=sys.stderr)
        sys.exit(1)
