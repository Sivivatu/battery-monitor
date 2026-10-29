# PR 1: absolute history anchor year

Date: 2026-10-28. Finding:
[Codex review comment](https://github.com/Sivivatu/battery-monitor/pull/1#discussion_r4405599946).
The two accompanying review-summary comments contain no additional requested change.

## Reproduction and fix

Absolute Android 15+ history lines omit the year in their timestamp prefix.
Previously the parser used its default year before processing a `TIME:` or
`RESET:TIME:` clock anchor in the same line. Reading December 2026 history in
January 2027 therefore started the timeline in December 2027; monotonic clamping
collapsed subsequent real timestamps. A February 29 anchor could also throw
when the default year was not a leap year.

The absolute-line parser now resolves an explicit clock anchor before constructing
the timestamp. Month-based year rollover is applied only when that line has no
anchor. Timestamp milliseconds continue to come from the absolute line prefix.
Relative-format parsing and the monotonic clamp are unchanged.

## Regression evidence

Tests were added before production edits. The narrow command, from `android/`, is:

```sh
timeout --kill-after=15s 8m env ANDROID_HOME=/home/ubuntu/Android/Sdk \
  ./gradlew :core:batterystats:test --tests '*HistoryParserTest' \
  --no-daemon --no-parallel --max-workers=2 --console=plain
```

RED: **9 tests executed, 2 failed**, in 40 seconds. The New Year assertion
expected `1798758000125` but received `1830294000125`; the leap-day assertion
threw `DateTimeException` for February 29, 2027. The initial XML is retained
locally at `/tmp/battery-history-anchor-red.xml`.

GREEN: the same **9 tests passed** after the fix, in 33 seconds. Regressions cover:

- Both `TIME:` and `RESET:TIME:` anchors selecting December 2026 while the default
  year is 2027; exact first/last timestamps, milliseconds, level points, reset
  markers, screen spans and two-hour summary/drop-rate values are asserted.
- An explicit leap-day anchor selecting 2024 despite a non-leap default year,
  including the exact timestamp of a subsequent event.
- History without an anchor retaining the supplied default year and rolling
  December into January normally.

## Full verification and review

`spotlessApply` passed in 26 seconds, followed by:

```sh
timeout --kill-after=15s 8m env ANDROID_HOME=/home/ubuntu/Android/Sdk \
  ./gradlew spotlessCheck :core:batterystats:test \
  :core:batterystats:jacocoTestReport :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  --no-daemon --no-parallel --max-workers=2 --console=plain
```

**PASS**, 1 minute 40 seconds. All **107 JVM tests passed**, with no failures,
errors or skips. Core coverage: **98.32% lines** (1,634/1,662), **77.63% branches**
(1,048/1,350); Android app coverage remains unmeasured. Lint: **0 errors, 10 warnings**.
Both app and instrumentation APK builds passed. Separate Sol code and security
reviews approved the focused change with no HIGH or CRITICAL findings.

Rebuilt APK SHA-256:
`033658dc1ac01000d19c89d4582a738089c192e88f0656b53e703fc654ef289f`.
The instrumentation APK is unchanged:
`0bcb7c8dcd25dc73ebe2a8160cf6b5dd52e2bd45a3c165d9024c91682dc8d48b`.

## Runtime limitations

Two bounded API 34 attempts booted in approximately 62 and 57 seconds, respectively.
Both failed at the initial `No-access setup displayed` host assertion, before any
instrumentation tests ran. Saved UI hierarchies from both failures show
`System UI isn't responding`. The retry started without a visible ANR, but the
same dialog returned during validation. No further retries were attempted and no
runtime pass is claimed for this rebuilt APK.

Local diagnostics are under `artifacts/pr-1-history-anchor/api34/` and
`artifacts/pr-1-history-anchor/api34-retry/`; they are excluded from Git. Both
attempts stopped their owned emulator and reset battery simulation. After cleanup,
`adb devices` reported no attached devices.

The earlier preview's successful API 34 run (30 instrumentation tests and 12 host
checks) remains historical evidence in the review-fix report. Hosted build and
emulator checks also passed on preceding commit `5d0c4e3`; those results do not
verify this new commit. Hosted CI will run after this fix is pushed.

API 37 was not rerun. Its documented System UI blocker remains; no additional
API 37 success is inferred from this fix. Physical-device accuracy, overhead,
long-term Doze and Shizuku runtime behavior remain unvalidated.
