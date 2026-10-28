# Durable battery history

Date: 2026-10-25. Source plan: the emulator validation work and the Wattdog evaluation in [wattdog-evaluation.md](../research/wattdog-evaluation.md). This document describes this fork; upstream Wattdog only retains automatic raw snapshots for 48 hours within the current accounting session.

## Storage and collection

The archive uses Android's bundled SQLite implementation (`SQLiteOpenHelper`), in the app-private `databases/battery-history.db`. The existing backup rules exclude databases from cloud backup and device transfer. No server account or network upload is involved. Uninstalling or clearing app storage removes the archive. There is no archive export interface yet.

Each successful privileged collection records its since-charge accounting session, capture time and validation status. It keeps one complete raw dump as the next comparison baseline and records the difference against the previous complete capture. Immutable interval rows and per-UID estimated charge values are saved together in a transaction. Display names, package lists and nullable per-package version code, version name and first-install timestamp are saved at capture time. Metadata is obtained only for the actual current-profile UID; another profile's package name never borrows the current profile's installed version or installation timestamp. Shared UIDs remain shared identities. Ownership changes, version changes and known installation changes exclude the affected interval and retain separate version/install histories with distinct boundary reasons. Unknown-to-known metadata changes also create a boundary. When installation metadata is unavailable, same-package reinstall cannot be distinguished; unknown-to-unknown metadata retains package-based continuity and is not a claim of complete reinstall detection. The additive SQLite v1-to-v2 migration preserves existing rows and the raw baseline; old version metadata remains unknown. Unresolved UIDs are kept separate across accounting sessions.

The archive retains its compact interval records across charging, accounting resets, process death and reboot. Retention is **capture-triggered**: each capture prunes compact records whose interval end is older than 90 days relative to the newest capture. Pruning deletes app rows, intervals and capture metadata together in that transaction, preserving the newest trustworthy raw baseline. Opening or querying without another capture does not run maintenance: compact rows and the single raw baseline can therefore remain older than 90 days while collection is stopped. App totals and daily totals use the same requested whole-interval range over those retained rows; daily totals do not silently apply a second cutoff. The separate raw-snapshot cache continues to prune automatic snapshots after 48 hours or a reset; pruning that cache does not delete the SQLite archive. Manual marks remain separate raw snapshots.

Background collection uses the existing opt-in hourly WorkManager checks, including reads while charging. Android can delay periodic work, particularly during Doze. Live refreshes and manual marks also collect archive baselines. A gap over three hours, a backwards clock, an accounting reset or counter regression creates a boundary rather than an invented usage value. Collection through archive recording is serialized by the shared application repository; the timestamp is taken immediately after the actual battery dump. A stale or equal-timestamp capture becomes a gap boundary and cannot replace a newer baseline. Successful parsing of both required on-battery time fields is necessary for baseline acceptance; a literal field name alone is insufficient. A recorded incomplete capture invalidates the next baseline comparison. A newly appearing or vanished UID creates a partial boundary; unknown app totals are excluded. An archive write failure is logged and exposed through `ArchiveStore.error`, while live diagnostics continue.

The Saved history screen queries complete intervals wholly inside the selected 1, 7 or 30 day range. It reports the actual captured on-battery time and boundaries. It does not spread a multi-hour interval into synthetic hourly values. Daily app trends group whole intervals by their UTC capture-end date and are explicitly labelled that way; they are not exact calendar-day measurements. Only the most recent 20 boundaries are loaded for display, with the full count retained. Foreground, background, foreground-service and cached estimates remain nullable. If one included interval lacks a state estimate, that state's range total is unknown, including intervals whose total-energy delta is zero. Zero-total coverage rows are persisted. An explicitly reported zero remains zero. Values are Android estimates in mAh, not direct per-app energy measurements.

## Test guarantees

`ArchiveStoreTest` exercises actual SQLite and app-private files on Android:

| Guarantee | Test |
| --- | --- |
| Saved interval survives closing and recreating the store | `validIntervalsSurviveStoreRecreation` |
| Accounting reset preserves old intervals and avoids a cross-session delta | `osResetPreservesPriorIntervalsWithoutCrossSessionDelta` |
| Missing state stays unknown; explicit zero stays zero | `absentStateRemainsUnknownWhileExplicitZeroRemainsZero` |
| Counter regression creates a boundary | `counterRegressionCreatesBoundaryInsteadOfInventingUsage` |
| Long collection gap contributes no false interval | `incompleteOrLongGapDoesNotBecomeACompleteInterval` |
| Raw 48-hour retention does not delete the archive | `pruningRawSnapshotsDoesNotRemoveArchive` |
| Changed UID package ownership cannot be merged into an old identity | `changedPackageOwnershipCannotBeMergedIntoOldIdentity` |
| Range edges do not interpolate partial intervals | `rangeDoesNotInterpolateAnIntervalAcrossItsBoundary` |
| A missing state in any interval makes its range state unknown | `oneMissingStateMakesTheWholeRangeStateUnknown` |
| Incomplete capture becomes a boundary and resets the comparison baseline | `missingCaptureDataCreatesABoundaryAndStartsANewBaseline` |
| Failed archive write exposes an error and does not throw from safe capture | `archiveWriteFailureIsVisibleAndDoesNotThrowFromSafeRecording` |
| Archive prunes only records older than 90 days and keeps the comparison baseline | `archivePrunesOnlyDataOlderThanNinetyDaysAndPreservesNewBaseline` |
| Boundary results are capped while the full count remains available | `boundaryResultsAreBoundedWhileTotalCountRemainsAvailable` |
| Daily trends explicitly use the UTC capture-end date | `dailyTrendExplicitlyAssignsWholeIntervalToItsUtcCaptureEndDate` |

Additional review regressions exercise concurrent repository captures with coroutine gates,
stale capture ordering, malformed required time, missing states with unchanged energy,
reopen-and-continue baselines and an actual SQLite abort after capture/interval insertion.
The rollback test uses a SQLite trigger, with no production failure-injection hook.
See [review-fix evidence](review-fixes-2026-10-28.md) for the final executed results.

## Execution evidence

Historical initial implementation RED: `:app:compileDebugAndroidTestKotlin` failed before implementation, with unresolved `ArchiveStore`, `ArchiveCaptureStatus` and instrumentation dependencies. The initial log is `/tmp/archive-red.log` in the development environment. Tests were authored first; the compilation failure was the initial RED gate, rather than an executed failing assertion.

Historical compile GREEN: `ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew :app:compileDebugAndroidTestKotlin --console=plain` passed in 37 seconds. Compilation alone does not prove persistence behavior. On 2026-10-28, API34 and API37 each executed all 14 archive tests and four collection/UI tests successfully; API34 also passed 12 host lifecycle checks, while API37 host automation remained blocked; see [dated recovery evidence](recovery-2026-10-28.md) for commands, full outcomes, and limitations.

Coverage is not measured for the imported Android UI; do not infer 80% coverage from these test counts. Tests use synthetic accounting data and prove local persistence and validation behavior. They cannot establish Pixel 9 Pro power accuracy, real foreground/background accounting availability, long-term Doze scheduling, battery overhead, or Shizuku survival across reboot.


The 2026-10-28 review fixes have separate executable RED/GREEN evidence in
[review-fixes-2026-10-28.md](review-fixes-2026-10-28.md). That report supersedes the
older counts for the updated implementation. The final core suite passes 104 JVM
tests; API34 passes 30 instrumentation tests (21 archive/repository, five package
metadata/migration, four collection/UI). Real SQLite instrumentation verifies
rows, persisted baseline continuation, transaction rollback, and schema migration.
Host force-stop/reboot checks establish database-file presence and UI recovery;
they do not independently verify unchanged rows across a real reboot.

Device scope is Pixel-configured emulators only: API34 uses the Pixel 7 profile;
API37 uses Pixel 9 Pro. Other OEM devices and physical Pixel battery accuracy are
unvalidated. API37 host lifecycle automation remains blocked by the system ANRs
recorded in the recovery report; a direct instrumentation pass does not remove
that limitation.

For the final revised APK, API34 passed all 12 host lifecycle checks after one
System UI restart. API37 direct execution ran all 30 tests: 29 passed; the UI test
was blocked by the observed System UI ANR overlay. All 26 SQLite/repository/package
tests and the three collection/snapshot tests passed there. The full API37 suite
and API37 host lifecycle are not reported as successful. Both AVDs were stopped
and simulated battery state was restored; see the dated review-fixes report.
