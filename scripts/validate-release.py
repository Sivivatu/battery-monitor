#!/usr/bin/env python3
"""Smoke-test a signed, minified APK on a disposable emulator only."""
import argparse
import json
import os
import subprocess
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if not args.serial.startswith('emulator-'):
        parser.error('Only disposable emulator-* serials are allowed')
    adb = str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb')
    package = 'io.github.sivivatu.batterymonitor'
    checks = []

    def run(*words):
        return subprocess.run([adb, '-s', args.serial, *words], check=True,
                              capture_output=True, text=True, timeout=60).stdout

    def shell(*words):
        return run('shell', *words)

    def require(name, condition):
        if not condition:
            raise AssertionError(name)
        checks.append(name)
        print('PASS', name, flush=True)

    def screen_contains(text):
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            shell('uiautomator', 'dump', '/data/local/tmp/battery-release-ui.xml')
            if text in shell('cat', '/data/local/tmp/battery-release-ui.xml'):
                return True
            time.sleep(1)
        return False

    def launch():
        shell('am', 'start', '-W', '-n', package + '/io.github.loganrosen.wattdog.ui.MainActivity')

    require('Device is an emulator', shell('getprop', 'ro.kernel.qemu').strip() == '1')
    args.output.mkdir(parents=True, exist_ok=True)
    status = 'failed'
    try:
        run('install', '-r', str(args.apk))
        shell('pm', 'revoke', package, 'android.permission.DUMP')
        launch()
        require('Minified release displays setup', screen_contains('Not granted'))
        for permission in ['DUMP', 'PACKAGE_USAGE_STATS', 'BATTERY_STATS', 'POST_NOTIFICATIONS']:
            shell('pm', 'grant', package, 'android.permission.' + permission)
        launch()
        require('Minified release collects and displays dashboard', screen_contains('content-desc="Refresh"'))
        shell('am', 'force-stop', package)
        launch()
        require('Release relaunches after process stop', screen_contains('content-desc="Refresh"'))
        before = shell('dumpsys', 'package', package)
        installed = next(line.strip() for line in before.splitlines() if 'firstInstallTime=' in line)
        run('install', '-r', str(args.apk))
        after = shell('dumpsys', 'package', package)
        require('Signed in-place reinstall retains installation', installed in after)
        require('Signed in-place reinstall retains collection grant', 'android.permission.DUMP: granted=true' in after)
        launch()
        require('Dashboard survives signed in-place reinstall', screen_contains('content-desc="Refresh"'))
        require('Release is not debuggable', 'DEBUGGABLE' not in after)
        status = 'passed'
    finally:
        (args.output / 'results.json').write_text(json.dumps({'status': status, 'checks': checks}, indent=2) + '\n')


if __name__ == '__main__':
    main()
