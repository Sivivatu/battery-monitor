<img src="assets/icon.svg" width="64" height="64" alt="">

# Battery Monitor

Free, ad-free Android 14+ battery monitor with local app-usage history. This is
an independent GPLv3-or-later fork of [Wattdog](https://github.com/loganrosen/wattdog),
with durable history and stricter checks on battery-usage comparisons.

## What it does

- Shows Android's per-app charge estimates, available foreground/background
  estimates, wakelocks, radio activity and the system battery timeline.
- Saves validated usage intervals in an app-private SQLite database, across
  statistics resets, app restarts and charging sessions.
- Shows saved 1-, 7- and 30-day totals and daily app trends. Package versions and
  installation metadata are retained where the current profile permits access.
- Labels missing estimates as unavailable and excludes invalid comparisons,
  counter regressions, missing baselines and collection gaps.
- Uses no advertisements, server account or internet permission. Backup is disabled.

## Install and enable collection

Install the signed public beta through our [F-Droid repository](https://sivivatu.github.io/battery-monitor/),
or download its APK from [Releases](https://github.com/Sivivatu/battery-monitor/releases).
No tester account is needed. This is our own beta feed, separate from the default
f-droid.org catalogue. The package is `io.github.sivivatu.batterymonitor`, so it can
coexist with upstream Wattdog. It is an early beta, not a stable release.

Moving from a debug preview to the signed beta requires uninstalling the preview,
which deletes its local history. Later beta updates preserve the signing identity.

Collection needs elevated access. The ADB route was exercised on emulators:

```sh
adb install -r io.github.sivivatu.batterymonitor_2.apk
adb shell pm grant io.github.sivivatu.batterymonitor android.permission.DUMP
adb shell pm grant io.github.sivivatu.batterymonitor android.permission.PACKAGE_USAGE_STATS
adb shell pm grant io.github.sivivatu.batterymonitor android.permission.BATTERY_STATS
```

Alternatively, start [Shizuku](https://shizuku.rikka.app/) and authorize the app.
Shizuku is inherited from upstream; this fork's Shizuku runtime and reboot behavior
have not been validated. Enable background checks in Settings for periodic
collection; Android can delay the nominal hourly schedule. The first complete
capture establishes a baseline; later captures populate Saved history.

The compact archive prunes records older than 90 days when another capture runs.
Stopping collection can leave older data stored. Raw automatic snapshots have a
separate 48-hour policy. Clearing app storage or uninstalling removes all history;
there is no archive export yet. See [storage details](docs/testing/durable-history.md).

## Build and verification

Requires JDK 21 and Android SDK platform 37. Minimum Android version is 14 (API 34).

```sh
cd android
./gradlew spotlessCheck :core:batterystats:test :core:batterystats:jacocoTestReport \
  :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  --no-parallel --max-workers=2
```

The APK is `android/app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions
workflow builds the APKs and runs the API 34 emulator validation script.

Verified locally on October 28, 2026:

| Check | Result |
| --- | --- |
| JVM unit tests | 107 passed |
| Android 14 / API 34 instrumentation | Earlier preview: 30 passed; latest local rerun blocked before instrumentation by System UI ANR |
| Android 14 host lifecycle checks | Earlier preview: 12 passed; latest local rerun blocked at setup |
| Android 17 / API 37 instrumentation | Earlier preview: 29 passed; one UI test blocked by System UI ANR |
| Formatting, lint and debug APK builds | Passed; lint has 0 errors and 10 warnings |
| Core coverage | 98.32% lines; 77.63% branches; Android app coverage unmeasured |

The initial-beta [release evidence](docs/testing/fdroid-beta-2026-10-29.md) records
the minified release checks and oversized SQLite baseline regression.

See the [executed review-fix evidence](docs/testing/review-fixes-2026-10-28.md)
and [history clock-anchor regression evidence](docs/testing/pr-1-history-anchor.md).
The [initial recovery report](docs/testing/recovery-2026-10-28.md) records earlier checks.
API 37 host lifecycle validation remains blocked by emulator System UI failures.
Host restart/reboot smoke checks verify file presence and UI recovery; row-level
durability, baseline continuation, rollback and migration are covered separately
by actual SQLite instrumentation tests.

Per-app values are Android estimates in mAh, not direct energy measurements.
Daily trends assign whole intervals to their UTC capture-end date. Real Pixel
accuracy, collection overhead, long-term Doze behavior and Shizuku operation still
need physical-device validation. Unknown installation metadata cannot identify a
same-package reinstall reliably.

Release operators: see the [publication and recovery runbook](docs/releasing/README.md).

## Research

Start with the [research findings and recommendation](docs/research/README.md).
The reports cover existing applications, public Android APIs, privileged attribution,
and source evaluations of BatStats and Wattdog, tailored to a Pixel 9 Pro on Android 17.

The key feasibility question is access: device-wide drain and app activity history
are available through public APIs, but those observations alone cannot measure
other apps' foreground/background energy consumption. Deeper estimates need an
appropriate elevated-access route and verification on the intended phone.

## License and provenance

GNU GPL v3 or later; see [LICENSE](LICENSE) and [pinned upstream provenance](android/UPSTREAM.md).
Original Kotlin package names are retained. This fork is not an upstream Wattdog release.

Created in [T3 Code](https://t3.codes).
