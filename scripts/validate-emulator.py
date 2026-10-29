#!/usr/bin/env python3
"""Validate an already booted disposable emulator; never accepts a physical device.
Build APKs first. Changes emulator battery, app permissions, stats and reboot state.
Does not clear app data. Reports contain test output/metadata, never raw battery dumps.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time

APP = "io.github.sivivatu.batterymonitor"
ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument("--serial", required=True)
p.add_argument("--output", type=Path, default=ROOT / "artifacts/emulator")
a = p.parse_args()
if not a.serial.startswith("emulator-"):
    p.error("Only emulator-* serials are allowed")
adb = str(Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk")) / "platform-tools/adb")

def run(*args, timeout=120):
    return subprocess.run([adb, "-s", a.serial, *args], check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout).stdout

def shell(*args):
    return run("shell", *args)

def wait_boot():
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        try:
            if run("shell", "getprop", "sys.boot_completed",
                   timeout=min(10, max(0.1, deadline - time.monotonic()))).strip() == "1":
                shell("input", "keyevent", "82")
                return
        except (subprocess.SubprocessError, OSError):
            pass
        time.sleep(2)
    raise RuntimeError("Emulator did not boot within four minutes")

wait_boot()
if shell("getprop", "ro.kernel.qemu").strip() != "1":
    raise RuntimeError("Device is not an emulator")
a.output.mkdir(parents=True, exist_ok=True)
results = {"serial": a.serial, "api": shell("getprop", "ro.build.version.sdk").strip(), "checks": []}

def check(name, condition=True):
    if not condition:
        raise AssertionError(name)
    results["checks"].append(name)
    print("PASS", name, flush=True)

def launch():
    shell("am", "start", "-W", "-n", APP + "/io.github.loganrosen.wattdog.ui.MainActivity")

def wait_ui(text):
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        shell("uiautomator", "dump", "/data/local/tmp/battery-monitor-ui.xml")
        if text in shell("cat", "/data/local/tmp/battery-monitor-ui.xml"):
            return True
        time.sleep(1)
    return False

try:
    run("install", "-r", str(ROOT / "android/app/build/outputs/apk/debug/app-debug.apk"))
    run("install", "-r", str(ROOT / "android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"))
    # Remove grants even on repeated runs; revocation can kill the process.
    shell("pm", "revoke", APP, "android.permission.DUMP")
    launch()
    check("No-access setup displayed", wait_ui("Not granted"))
    for permission in ("DUMP", "PACKAGE_USAGE_STATS", "BATTERY_STATS", "POST_NOTIFICATIONS"):
        shell("pm", "grant", APP, "android.permission." + permission)
    shell("dumpsys", "battery", "unplug")
    shell("dumpsys", "battery", "set", "level", "80")
    check("Simulated unplugged battery", "powered: false" in shell("dumpsys", "battery"))
    launch()
    check("Granted app shows dashboard", wait_ui('content-desc="Refresh"'))
    output = shell("am", "instrument", "-w", "-r", APP + ".test/androidx.test.runner.AndroidJUnitRunner")
    (a.output / "instrumentation.txt").write_text(output)
    check("Instrumentation suite", "OK (" in output and "FAILURES" not in output)
    files_before = shell("run-as", APP, "ls", "databases")
    check("History database created", "battery-history.db" in files_before)
    shell("am", "force-stop", APP)
    launch()
    check("Force-stop/relaunch", wait_ui('content-desc="Refresh"'))
    check("Database survives process death", "battery-history.db" in shell("run-as", APP, "ls", "databases"))
    shell("dumpsys", "battery", "set", "ac", "1")
    check("Charging simulation", "AC powered: true" in shell("dumpsys", "battery"))
    shell("dumpsys", "batterystats", "--reset")
    shell("am", "force-stop", APP)
    launch()
    check("Stats reset handled without crash", wait_ui('content-desc="Refresh"'))
    shell("pm", "revoke", APP, "android.permission.DUMP")
    launch()
    check("Revoked permission returns to setup", wait_ui("Not granted"))
    shell("pm", "grant", APP, "android.permission.DUMP")
    run("reboot")
    time.sleep(3)
    wait_boot()
    launch()
    check("Grant survives reboot", wait_ui('content-desc="Refresh"'))
    check("History survives reboot", "battery-history.db" in shell("run-as", APP, "ls", "databases"))
    results["status"] = "passed"
finally:
    try:
        shell("dumpsys", "battery", "reset")
    finally:
        (a.output / "results.json").write_text(json.dumps(results, indent=2) + "\n")
