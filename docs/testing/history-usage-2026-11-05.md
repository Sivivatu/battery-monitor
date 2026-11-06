# History navigation and daily usage — 2026-11-05

Source plan: [Battery usage over time](../plans/history-usage.md).

## User journeys

- Open History from the bottom navigation to see the private archive across
  charging sessions. Choose a rolling 1-, 7-, or 30-day range.
- Swipe the daily chart and tap a dated bar to read its app charge estimate.
  Missing dates remain unknown; an actual zero remains zero.
- Enable Automatic history or use Capture now without leaving History.
  Notification permission affects alerts, not local collection.
- Open Timeline for Android's rotating detailed event buffer.

## Test guarantees

`ArchiveTrendTest` tests the pure projection used by the actual chart:

| Guarantee | Test |
| --- | --- |
| Missing dates are null; recorded zero is preserved | `dailyTrendFillsMissingUtcDaysWithoutTurningThemIntoZero` |
| Both rolling range edges use UTC dates | `dailyTrendUsesUtcDatesAtBothRollingRangeEdges` |
| Input order and dates outside the range cannot alter plotted dates | `dailyTrendSortsRecordedDaysAndIgnoresDataOutsideTheSelectedDates` |
| A same-instant range still has one date | `dailyTrendIncludesOneDateForAZeroLengthRange` |
| Rolling 30-day ranges can intersect 31 UTC dates | `dailyTrendCanCoverThirtyOneUtcDatesInAThirtyDayRollingRange` |

RED was confirmed before production changes: `:app:testDebugUnitTest --tests
'*ArchiveTrendTest'` failed compiling the missing `dailyTrend()` interface.
Five tests are retained. Existing archive SQLite tests cover durable intervals,
resets and retention; this change does not alter storage or collection frequency.

`EmulatorValidationTest.liveCaptureAndSavedHistoryScreenOpen` now enters History
from navigation, changes the range, opens Timeline and returns to the archive.

## Device limitation

The device tools could not boot BatteryMonitor_API34. The first attempt reported
missing ffmpeg/libx264. After supplying that dependency, the retry reported:
"The emulator process for BatteryMonitor_API34 exited with code 1 before coming
online." No device tests or visual checks were completed in that initial run.

The subsequent Android 17 diagnosis identified the startup blocker as insufficient
host disk space. Cleanup freed space on both the root filesystem and temporary
storage, and Android 17/API 37 then booted successfully. Direct collection and
all 22 archive SQLite tests passed. The UI test still timed out finding Refresh,
and the device screenshot tool failed; visual validation remains incomplete. See
the [Android 17 emulator repair report](android17-emulator-2026-11-05.md) for
the repair and validation evidence.

## Review

Security review found no critical or high issues. General review found that the
30-day chart needed larger dated bars and exact readable values, and that a 1 mAh
minimum scale compressed small estimates. Both findings were fixed: days have
48 dp touch targets in a scrolling row, selected values are shown as text, and
the scale uses the largest positive reading. Zero/missing-only charts use a safe
fallback denominator.

## Executed validation

From `android/`, with `ANDROID_HOME=/home/ubuntu/Android/Sdk` and JDK 21:

```sh
./gradlew spotlessCheck :app:testDebugUnitTest :core:batterystats:test \
  :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  --no-parallel --max-workers=1 --console=plain
```

Result: **BUILD SUCCESSFUL**. App JVM tests: **33 passed**, including all five new
trend tests. Core JVM tests: **110 passed**. Debug application and instrumentation
APKs both compile and package successfully. Lint completes successfully.

Earlier overlapping Gradle checks hit an internal lint
`AsyncExecutionService.getService must not return null` failure. Running format
application separately, then a single serial check run resolved it without
suppressing lint checks. The final log is
`/tmp/battery-monitor-history-final.log` in this environment.

UI instrumentation was compiled, not executed. Whole-application UI coverage is
not established by the JVM test counts.

Targeted JaCoCo coverage for `ArchiveTrendKt` and `ArchiveTrendDay`: **100% lines
(8/8) and branches (4/4)**. The report is scoped to the pure daily projection,
not Compose UI. It was generated without changing project build configuration:

```sh
./gradlew -I /tmp/history-trend-coverage.init.gradle :app:historyTrendCoverage \
  --no-configuration-cache --no-parallel --max-workers=1 --console=plain
```

The temporary init script selects those two classes and the app's existing JVM
execution data. Reports: `/tmp/battery-monitor-history-coverage.xml` and
`/tmp/battery-monitor-history-coverage/index.html`. Lint reported 0 errors and
19 warnings, in existing dependencies/configuration and unchanged code.


## Android 17 follow-up — 2026-11-06

The navigation timeout was reproduced while a stale Android System UI ANR dialog
covered the resumed app. Handling only that exact dialog's Wait action once,
waiting up to three seconds for its dismissal, and asserting that it stays absent
allowed the original Refresh and navigation assertions to pass within their
existing timeouts. The final focused test passed in 20.93 seconds; the complete
instrumentation suite completed in 40.379 seconds with 36 passed, one optional
live-pairing test skipped by assumption, and zero failures. A subsequent
device screenshot also succeeded. These results supersede the earlier
compile-only/device-blocked limitations for that navigation journey; see the
[follow-up report](android17-ui-tests-2026-11-06.md) for scope and commands.
