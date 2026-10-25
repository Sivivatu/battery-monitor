# Wattdog: functional correctness and reuse evaluation

Reviewed 2026-10-25. Upstream: [loganrosen/wattdog][repo], pinned revision **06f3979a19f95c4b91f3a3234ccb7547b404e45e**.
Source was downloaded separately to `/tmp/battery-monitor-wattdog-mLRIU2` and inspected read-only. This is published source, not decompilation or APK source extraction. No implementation was integrated or copied into our repository.

## Verdict and evidence boundary

**Open source: yes, GPLv3-or-later per README.** We may fork, modify, and reuse it.
For a distributed derivative, preserve copyright/license notices, mark modifications,
license the covered combined work under the GPL, and provide matching corresponding
source including build scripts. Taking only the Kotlin core does not automatically
remove these obligations. Private evaluation does not require publishing a modified
copy. The README supplies the “or later” election; the license file contains the
GPLv3 terms, particularly sections 2, 4, 5, and 6. [license] [license-terms]

**Suitability: a useful diagnostic prototype and selective reuse candidate, not yet a dependable long-term per-app foreground/background drain recorder.** Strongest assets are its isolated Kotlin parsing/analysis module, raw snapshots, UID-aware labels, and constrained privileged collector. Missing-value handling, delta validity, and durable trend storage need work before trusting reports. [parser] [delta] [snapshots]

**Review evidence:** local source and test bodies inspected; Android 17 AOSP
command implementation inspected at a pinned revision. Build verification is recorded
separately below. No test was performed on the user's phone.

**GitHub metadata checked:** upstream main matches the pin, repository is not archived, latest commit is 2026-10-14, and no GitHub releases exist. A successful [upstream CI run][ci] at this SHA has a nonexpired `wattdog-debug` artifact (~11.6 MB). That is a CI artifact, not a release. We did not download that artifact; the local build below is independent. No device installation or target-device operation was verified.

**Fixture evidence, not device validation:** `RealPixelApi37Test` identifies its capture as **Pixel 9 Pro XL, API 37**. It asserts summary, UID power, wakelock, radio and analysis behavior against stored anonymized dumps. API 34 emulator fixtures exercise another format. This supports investigating Android 17 parsing; it does not establish compatibility with the user's **Pixel 9 Pro / Android 17**, permission grants, Shizuku, scheduling, or physical energy accuracy. [pixel-test] [emulator-test] [fixtures]

## Capture commands and permissions

- **Source finding:** normal capture runs `dumpsys batterystats --charged` plus `dumpsys power`; history is fetched separately with `--history` on demand. The five-command allowlist also contains `deviceidle` and `battery`. It contains no `--usage`, `--checkin`, reset, arbitrary command, or shell-string execution. [targets] [repository] [exec]
- **Correct safety distinction:** AOSP Android 17's real `--checkin` path reads a saved completed-statistics file and deletes it after dumping. It can consume historical evidence; it does not mean all current counters are reset. Wattdog avoids that path. Keep reset commands out of routine collection. [aosp-checkin]
- **Recommended investigation:** Android 17 AOSP's `--usage` path requests fresh stats, process-state, model, screen-state and power-state data. Probe the actual phone's help/output before adding a separate usage collector/parser. Wattdog's parser requires the `Statistics since last charge:` header, so substituting `--usage` into its existing collector is not a complete fix. [aosp-usage] [parser]
- **Routes:** Shizuku user service supplies shell access; direct collection instead executes as the app after ADB grants. README requires both `DUMP` and `PACKAGE_USAGE_STATS`. `DirectDumpSource.isAvailable()` checks only `DUMP`, so "available" is weaker than a successful authorized dump; denial detection occurs during reading. [access] [direct] [targets] [readme-access]
- **Fallback limitation:** Shizuku is preferred when granted; a failing Shizuku read is not retried through direct access in that call. Binding has a 15-second timeout, but dump reads/process waits have no explicit execution deadline. Add bounded reads, cancellation cleanup and validated fallback behavior. [access] [client] [shell]

## Attribution and parser correctness

- **Estimates, not independent per-app measurement:** `UidPower` reads Android's estimated UID mAh and optional `fg`, `bg`, `fgs`, `cached` totals. Preserve foreground service and cached categories separately and describe these as OS process-state attribution; screen-off drain and foreground app activity are different quantities. The history summary produces device battery-level rates and top-app durations, not per-app energy measurements. [model] [history-summary]
- **Priority correctness defect:** absent state fields become `0.0`; the Apps UI unconditionally renders all four state totals. A missing/unreported state is therefore indistinguishable from measured zero. Header-only or partially changed text can also produce a default-heavy report because unknown lines are skipped. Add nullable values, field-presence/schema diagnostics and explicit unsupported/partial status before using these values in trends. [parser] [states-parser] [apps-ui]
- The parser keeps unconditional component totals and deliberately ignores component substates. It therefore cannot independently audit detailed per-component state attribution. Do not force total UID mAh to equal a complete four-state split when fields/components are unavailable. [states-parser] [model]
- **UID mapping strengths:** numeric and formatted multi-user/isolated UIDs are modeled. Labels use PackageManager for the current user, dump package hints for other users, shared labels and system UID names; settings navigation is withheld for ambiguous ownership. A shared UID remains one attribution bucket, not separate package-level measurements. [uid] [resolver]
- **Identity gap:** the label cache is keyed only by UID; `clear()` exists but no call site was found in the inspected app. Snapshot metadata does not persist package ownership/version history. Uninstall/reinstall, UID reuse, profile visibility and isolated-process ownership require explicit handling before cross-session per-app trends. [resolver] [snapshots]

## Delta, persistence and trend limitations

- `ReportDelta` rejects different non-null start-clock strings and decreasing on-battery realtime. It subtracts UID/component counters, assumes a missing earlier UID means zero, drops entries without positive total growth, and clamps negative differences to zero. Existing tests cover subtraction, identical snapshots and two reset cases. [delta] [delta-test]
- **Source-inferred risk:** missing session identity, per-UID resets, estimator revisions, disappearing fields/UIDs or reused UIDs can still create plausible but wrong deltas. Missing earlier data can become all-time energy in a short window; clamps hide discontinuities. Preserve unknown intervals and reset reasons, require compatible provenance/identity, and treat regressions as invalid segments. [delta] [snapshots]
- **Actual implementation:** snapshots store gzipped raw batterystats plus JSON metadata in app-private files and reload/reparse after restart. They do not persist the full power dump or history timeline. Writes are mutex-protected but are not an atomic dump/metadata transaction; unreadable metadata is silently skipped. Manual snapshots have no automatic retention limit. [snapshots] [container] [repository]
- **Automatic collection:** off by default, coupled to background anomaly checks, nominally hourly WorkManager work; successful on-battery passes keep automatic snapshots for 48 hours and prune those outside the current stats session. Minimum spacing is 20 minutes. Disabling checks deletes automatic snapshots. Charging passes skip persistence/pruning. This is short-term baseline storage, not a daily/weekly trend database. [checks] [background]
- **Gaps/reboots:** WorkManager timing is inexact; presets select nearby snapshots within tolerance or omit the window. Lost Shizuku after reboot, permission failure, charging skips, delays and OS stats resets leave gaps without catch-up reconstruction. Do not assume every reboot resets batterystats: the implemented detector observes only start-clock/realtime changes. Access loss gets a notice after three recognized failures; generic worker failures are recorded and return success for the next periodic attempt. [windows] [background] [worker] [readme-access]
- History is fetched lazily and held in ViewModel memory; raw snapshots retain only batterystats captures. A long-term recorder needs durable, session-segmented interval records and explicit coverage/gap metadata, independently configurable from alerting. [repository] [viewmodel] [snapshots]

## Reuse candidates and priorities

1. **P0 — validate the target first:** privately preserve the actual Pixel 9 Pro build/help and read-only sample outputs. Verify both access routes, state availability and session boundaries across natural charging, reboot and process restart. No compatibility claim until those observations exist.
2. **P0 — attribution validity:** preserve unknown versus zero; record capture command, parser/schema version, source interval, model/measurement basis and package/user identity. Reject incompatible deltas instead of clamping away regressions. Add regression cases for omitted states, partial dumps, counter rollback, UID reuse and missing baselines.
3. **P1 — reusable core:** evaluate `core/batterystats` models/parsers, `Units`, `ReportDelta`, `WindowPresets` and selected metrics under GPL review. Existing fixtures are a starting corpus; test bodies are not proof of passing results or target support. Anomaly thresholds and proportional wakelock costs remain heuristics. [costs]
4. **P1 — persistent trends:** adapt the raw snapshot concept, adding atomic writes/recovery, bounded raw retention, durable per-interval aggregates, boot/session/provenance keys and package history. Keep useful completed sessions instead of deleting the only automatic historical evidence at reset.
5. **P1 — privileged/security review:** reuse the strict enum allowlist and argument-list `ProcessBuilder` pattern only after review. Audit Binder caller boundaries, exported components, Shizuku dependency behavior, time/output limits and cancellation. Manifest's Shizuku provider is permission-protected; no `INTERNET` permission is declared. App-private storage and disabled backup reduce exposure, but gzip is not encryption. Dumps contain sensitive app/device/account tags; the fixture anonymizer explicitly offers only best-effort scrubbing. [shell] [exec] [manifest] [backup] [scrubber]

## Local verification

The unchanged pinned source was tested using OpenJDK 21.0.12.1, its Gradle 9.7.1
wrapper, and Android SDK Platform 37.0 revision 2. The SDK location was supplied
explicitly; this required no upstream source changes.

```sh
ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew \
  :core:batterystats:test :app:assembleDebug :app:lintDebug --console=plain
```

- **Core tests:** 80 tests in 13 suites; zero failures, errors, or skips, counted
  from the generated JUnit XML. This includes stored Pixel 9 Pro XL API 37 fixtures;
  it does not validate live collection, battery accuracy, or overnight operation.
- **Debug build:** `:app:assembleDebug` completed and produced
  `app/build/outputs/apk/debug/app-debug.apk` in the separate source checkout.
  APK SHA-256: `051b6f2e86027cd928f966808122fb683a7cf926937949f8a5e7853166e12433`.
  This is a debug-signed evaluation build, not a published release.
- **Lint:** `:app:lintDebug` passed with zero errors and six warnings, all about
  newer available build-tool/plugin/library versions. The combined command finished
  successfully in 5 minutes 20 seconds. This is static checking, not a security audit.
- **Merged debug manifest:** no `INTERNET` permission; source app dependencies
  contain no obvious advertising/billing SDK. This supports the privacy/no-ads
  preference but is not a full dependency or binary behavior audit.
- **Limits:** no instrumentation/E2E test, device installation, calibration,
  collector overhead measurement, or Pixel 9 Pro permission/reboot test was performed.
  No numerical test-coverage percentage was measured.

## Pinned primary sources

All Wattdog links below resolve to the inspected commit, not moving `main`.

[repo]: https://github.com/loganrosen/wattdog/tree/06f3979a19f95c4b91f3a3234ccb7547b404e45e
[license]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/README.md#license
[license-terms]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/LICENSE
[ci]: https://github.com/loganrosen/wattdog/actions/runs/38505974158
[parser]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/main/kotlin/io/github/loganrosen/wattdog/batterystats/parse/BatteryStatsParser.kt#L25-L55
[states-parser]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/main/kotlin/io/github/loganrosen/wattdog/batterystats/parse/BatteryStatsParser.kt#L330-L405
[model]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/main/kotlin/io/github/loganrosen/wattdog/batterystats/model/BatteryStatsReport.kt#L108-L118
[delta]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/main/kotlin/io/github/loganrosen/wattdog/batterystats/analysis/ReportDelta.kt#L25-L161
[delta-test]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/test/kotlin/io/github/loganrosen/wattdog/batterystats/analysis/ReportDeltaTest.kt#L32-L65
[snapshots]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/data/SnapshotStore.kt#L19-L109
[targets]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/privileged/DumpTarget.kt#L5-L58
[repository]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/data/BatteryRepository.kt#L57-L96
[exec]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/privileged/Dumpsys.kt#L8-L23
[access]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/privileged/PrivilegedAccess.kt#L14-L59
[direct]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/privileged/DirectDumpSource.kt#L11-L31
[readme-access]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/README.md#access
[client]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/privileged/ShizukuShellClient.kt#L19-L77
[shell]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/privileged/WattdogShellService.kt#L9-L37
[pixel-test]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/test/kotlin/io/github/loganrosen/wattdog/batterystats/parse/RealPixelApi37Test.kt#L21-L144
[emulator-test]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/test/kotlin/io/github/loganrosen/wattdog/batterystats/parse/EmulatorApi34Test.kt#L19-L94
[fixtures]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/test/kotlin/io/github/loganrosen/wattdog/batterystats/Fixtures.kt#L18-L23
[history-summary]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/main/kotlin/io/github/loganrosen/wattdog/batterystats/analysis/HistorySummary.kt#L13-L67
[apps-ui]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/ui/screens/AppsScreen.kt#L102-L109
[uid]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/main/kotlin/io/github/loganrosen/wattdog/batterystats/model/AppUid.kt#L3-L49
[resolver]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/data/UidResolver.kt#L10-L59
[container]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/AppContainer.kt#L32-L39
[checks]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/alerts/BackgroundChecks.kt#L16-L55
[background]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/alerts/BackgroundCheck.kt#L33-L130
[windows]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/main/kotlin/io/github/loganrosen/wattdog/batterystats/analysis/WindowPresets.kt#L20-L58
[worker]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/alerts/AnomalyCheckWorker.kt#L10-L24
[viewmodel]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/ui/MainViewModel.kt#L142-L157
[costs]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/main/kotlin/io/github/loganrosen/wattdog/batterystats/analysis/DrainCost.kt#L55-L64
[manifest]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/AndroidManifest.xml#L5-L47
[backup]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/res/xml/data_extraction_rules.xml#L2-L18
[scrubber]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/scripts/anonymize_dump.py#L2-L16
[aosp-checkin]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/am/BatteryStatsService.java#3419
[aosp-usage]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/am/BatteryStatsService.java#3056
