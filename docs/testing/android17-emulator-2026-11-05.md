# Android 17 emulator startup and history validation — 2026-11-05


Follow-up on 2026-11-06: the UI timeout was reproduced with a stale System UI
ANR dialog above the resumed app. A bounded, exact-dialog recovery allowed the
unchanged navigation assertions to pass. The Android 17 suite completed with
36 tests passed and one optional live-pairing test skipped; device screenshots
succeeded. See the
[follow-up diagnosis and results](android17-ui-tests-2026-11-06.md). The failures
below describe the earlier run.

## Finding and recovery

`BatteryMonitor_API37` is the Pixel 9 Pro profile using Android 17 (API 37). The
installed emulator is 37.2.12.0. Initially, the root filesystem had 893 MB free
(99% used); `/tmp` had about 5.1 GB free. Following the device tool's diagnostic
command produced:

```text
Checking: hasSufficientDiskSpace
Error: Your device does not have enough disk space to run avd: `BatteryMonitor_API37`
FATAL | Your device does not have enough disk space to run avd
```

The official [Android Emulator troubleshooting guide](https://developer.android.com/studio/run/emulator-troubleshooting#disk-space)
says the emulator checks free space at startup and will not start unless at least
5 GB is free. During recovery, unused, re-downloadable caches were cleared:

- `uv cache clean`: 53,992 package cache files, 615.9 MB.
- pnpm's `.cache/pnpm/v11` cache: 282 MB.
- pip's download cache: 217.1 MB.
- Playwright's unused Chromium 1248 cache: 403 MB. No Chromium process or open
  cache files were present.
- Duplicate Battery Monitor release downloads and a temporary beta verifier
  environment in `/tmp`: about 255 MB.

The latest app APK and both API 37 AVD backing/userdata files were preserved. The
emulator then booted and remained running in the Device panel. At that point, the
root filesystem showed 2.4 GB free and `/tmp` showed 5.4 GB free. The actual
filesystem used by the emulator's 5 GB guard is not reported by the startup check;
the threshold is documented, while the exact filesystem relationship is inferred
from this environment's before/after observations. Later APK installation and
instrumentation writes brought root free space to about 2.0 GB. Current `/tmp`
free space is 5.4 GB.

The Android 17 device remains running as `emulator-5554`. ADB confirmed:

```text
ro.build.version.sdk = 37
ro.build.version.release = 17
```

The package service started, the current debug APK installed, and `/data` had 4.6
GB free. `DUMP`, `PACKAGE_USAGE_STATS`, and `BATTERY_STATS` were granted to the
app for validation.

## Validation results

- `EmulatorValidationTest.directCollectionRunsWithAppPermissionRatherThanShellIdentity`: **PASS**.
- `ArchiveStoreTest`: **22 passed**.
- Initial `EmulatorValidationTest.liveCaptureAndSavedHistoryScreenOpen`: **FAIL**.
  At line 71 it timed out after 30 seconds waiting for `Refresh`. This was later
  reproduced with contemporaneous WindowManager evidence of a focused System UI
  ANR dialog covering the top-resumed MainActivity. See the
  [follow-up diagnosis and fix](android17-ui-tests-2026-11-06.md).
- After the test learned to recognize and dismiss that exact Android System UI
  warning, the focused History/Timeline flow passed; the full API 37 instrumentation
  suite completed with **36 passed, 1 optional live-pairing test skipped, 0 failed**. The device screenshot succeeded after the dialog cleared.
- Device screenshot failed twice with:

  ```text
  Device screenshot failed: The device hub could not complete the request.
  Call device_list to check the device state, and device_open if it needs to be reopened.
  ```

  `device_list` reported the Android 17 device booted and open. The screenshot
  failure remained, so on-device visual confirmation was not possible.

## Limits

The exact disk mount checked by the emulator cannot be established from its
emitted error. No AVD user data was wiped. The API 37 emulator remains running.
The repeated Android 17 System UI/keyguard ANR and test recovery are documented in
the [follow-up report](android17-ui-tests-2026-11-06.md).
