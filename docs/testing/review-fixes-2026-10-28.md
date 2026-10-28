# Review fixes — 2026-10-28

Implementation owner: GPT-6.1 Sol; no children, commits, pushes or worktrees.
Starting branch `feat/emulator-validation`, HEAD `399d68229fc61f3fdac33145a95ee9c34f4941f2`.
Existing untracked source and modified `.gitignore` preserved, as are both recovery
backups including `/tmp/battery-review-fixes-20261028/source-before.tar.gz`.
Prior evidence: [recovery](recovery-2026-10-28.md).

Plan: executable regressions and fixes for C1/H1/H2; bounded medium fixes
C2–C4/H3–H6; serial formatter/JVM/coverage/lint/APK checks; API34 full runtime
and lifecycle; API37 direct suite only. Parent owns independent final review.
One Gradle invocation at a time, eight-minute timeout, no parallel tasks,
max two workers. One emulator at a time with four-minute boot deadline.

## Checkpoints

- Initial reads confirmed the three HIGH paths and the single AppContainer repository
  shared by UI, manual snapshots and BackgroundCheck. No Gradle/emulator was running.
  HIGH regression tests added before production changes.

### HIGH regressions executed (RED)

- Core: 94 tests, two new C1 wakelock failures, before production changes;
  `/tmp/battery-review-fixes-20261028/high-red-build.log`.
- API34 actual SQLite/repository suite: 21 tests, five failures, including all
  H1/H2 regressions and H3/H5 regressions. Reopen-and-continue and real SQLite
  trigger-abort rollback tests already passed. Evidence:
  `/tmp/battery-review-fixes-20261028/high-red-instrumentation.txt`.
- HIGH fixes now implemented: missing UID/tag wakelock baselines excluded and
  reported; shared repository mutex covers collection through recording with a
  timestamp captured immediately after the actual dump; stale timestamps leave
  the newer baseline untouched; archive requires successful battery-time parsing.
  GREEN execution follows below.

### Bounded medium fixes, first group

- RED core run: 103 tests, 10 failures (C1 plus C2/C3/C4); number parsing exposed
  raw NumberFormatException and regression counters returned Ok. Log:
  `/tmp/battery-review-fixes-20261028/medium-red-core.log`.
- C2: radio energy and component optional fields require both endpoints; new
  global components are excluded without a baseline, explicit zero is preserved.
- C3: matched cumulative UID/job/alarm/partial/kernel/wakeup and global/radio
  activity counters now invalidate regressed intervals. Maxima and current-held
  durations are excluded from continuity checks. New kernel entries are omitted.
- C4: checked finite nonnegative energy/count conversions produce the domain
  parse exception on malformed/overflowing values; duration/byte overflow is checked.
- H3: archive requests unchanged UID energy rows to preserve nullable-state
  coverage; default diagnostic deltas continue to omit unchanged UID energy.
- H5: selected the explicitly permitted capture-triggered retention policy;
  removed inconsistent query-only daily cutoff. Reopening without capture keeps
  retained rows/baseline; both totals use the same range. This is not an automatic
  cleanup-on-open guarantee. Documentation now states this limitation plainly.
- H6: real SQLite reopen-and-continue and trigger-injected mid-transaction rollback
  tests passed in the initial 21-test RED run, before production modifications.

### Build checkpoint

- Initial combined formatter/build stopped on one new test line >140 characters.
  Corrected the line; standalone `spotlessApply` passed (32s), no rules suppressed.
- H4 data shape and regression tests added before metadata behavior changes.
  AppLabel adds a defaulted nullable-metadata list; resolver/archive still ignore
  it at this RED stage. Tests cover update, same-package reinstall, unknown-to-known
  boundary and current-profile-only metadata resolution. APK build is in progress.

### HIGH fixes and first medium group executed GREEN; H4 executed RED

- API34 ran 26 archive/package tests: **all 21 ArchiveStore tests passed**, including
  C1-adjacent capture behavior, H1/H2, H3, H5, H6; the four H4 behavior regressions
  failed as expected before metadata implementation. Legacy fixture test passed.
  `/tmp/battery-review-fixes-20261028/high-green-metadata-red-instrumentation.txt`.
- The controlled repository test additionally verifies dump timestamps and the
  recorded 6 mAh interval; the stale 10→16→delayed12→18 test yields exactly 8 mAh.
- H4 test APK needed one bounded correction: explicit `arrayOf<Any>` for mixed
  SQLite bind arguments under Kotlin 2.4.20. Rebuild passed (42s).
- Metadata implementation applied after these failures: nullable per-package
  versionCode/versionName/firstInstallTime, only for the matching current-profile
  UID; immutable JSON per interval and baseline; version/install boundaries with
  separate reasons and grouping; additive SQLite v1→v2 migration. Unknown metadata
  remains unknown and cannot bridge a known metadata boundary. No new dependency.

### Numeric parsing checkpoint

- Core execution after C1–C4 fixes: **104 tests, one failure**. All earlier
  numerical/continuity regressions passed; a new malformed required-time test
  exposed partial duration token acceptance (`1h junk`). Evidence:
  `/tmp/battery-review-fixes-20261028/core-green-timing-red.log`.
- Required durations now require a complete duration string; parsed zero remains
  valid, malformed/negative required timing is marked absent, overflow throws the
  domain parse exception. Final validation is next.
- H4 limitation: when first-install metadata is unavailable (including other
  profiles), same-package reinstalls cannot be proven. Unknown-to-unknown history
  uses package identity; known versions/installations are separately preserved.
  This is now explicit in durable-history.md.

### Final JVM verification completed

- `spotlessApply`: PASS, 32s, all rules retained.
- Full core JVM suite: **104 tests, 0 failures/errors/skips**, 15 suites.
- JaCoCo core: lines **1,630/1,658 (98.31%)**; branches
  **1,045/1,348 (77.52%)**; instructions **14,655/15,547 (94.26%)**;
  methods **461/549 (83.97%)**. Branch coverage remains below 80%; Android
  app/UI/archive coverage is unmeasured. No coverage threshold was weakened.
- Full serial build is still completing lint and APK tasks; do not infer a build
  or instrumentation pass from the JVM results alone.

### Final build completed

From `android/`, after standalone `spotlessApply`:

```sh
timeout --kill-after=15s 8m env ANDROID_HOME=/home/ubuntu/Android/Sdk \
  ./gradlew spotlessCheck :core:batterystats:test \
  :core:batterystats:jacocoTestReport :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  --no-daemon --no-parallel --max-workers=2 --console=plain
```

**PASS**, 2m16s; 91 tasks, 26 executed / 65 up-to-date. Core tests executed;
formatter check and both APK builds passed. Log:
`/tmp/battery-review-fixes-20261028/final-build.log`.
Lint: **0 errors, 10 warnings** (seven dependency/version notices, three existing
version-catalog recommendations). No test/lint/security settings weakened.

Final APKs relative to repository:

- `android/app/build/outputs/apk/debug/app-debug.apk`, SHA-256
  `895cf8efd67df0d4986afe5a453f2fe9299cd7fad7a2b140e22c611213984654`.
- `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, SHA-256
  `0bcb7c8dcd25dc73ebe2a8160cf6b5dd52e2bd45a3c165d9024c91682dc8d48b`.

Reports:
- `android/core/batterystats/build/reports/tests/test/index.html`
- `android/core/batterystats/build/reports/jacoco/test/html/index.html`
- `android/app/build/reports/lint-results-debug.html`

API34 runtime validation is running; final outcomes follow.

### API34 host runtime interruption

The first final host script failed its initial no-access setup assertion.
Inspected the single saved UI hierarchy once: the foreground overlay was
**“System UI isn't responding”**, with Close app / Wait actions; target activity
was resumed. Filtered AndroidRuntime/ActivityManager error output did not show an
app crash. One targeted correction selected the observed **Close app** action to
restart the unresponsive system UI, then one bounded full-script retry began.
No assertions or security checks were changed. Initial failed results remain in
`artifacts/review-fixes-20261028/api34/results.json`; local diagnostics:
`/tmp/battery-review-fixes-20261028/api34-failure-{ui.xml,logcat.txt}` and
`final-api34-host.log` in that same temporary directory. Retry evidence is kept
separately under `artifacts/review-fixes-20261028/api34-retry/`.

### Final API34 instrumentation completed

One targeted System UI restart restored the host path. Its full instrumentation
suite passed: **OK (30 tests)**, **21.635s** — 21 archive/repository tests, five
package/version/migration tests, four collection/UI tests. This executes H4
GREEN as well as the final H1/H2/H3/H5/H6 implementation on real Android SQLite.
Evidence: `artifacts/review-fixes-20261028/api34-retry/instrumentation.txt`.
Host lifecycle checks are completing separately; instrumentation alone does not
establish a full host lifecycle pass.

### Final API34 host lifecycle completed

Bounded full host retry **PASS**, exit 0, **12 checks**, including no-access,
granted dashboard, 30-test instrumentation suite, charging, force-stop/relaunch,
accounting reset, permission revocation/restoration and reboot. Evidence:
`artifacts/review-fixes-20261028/api34-retry/results.json`; local log
`/tmp/battery-review-fixes-20261028/final-api34-host-retry.log`.
The host script restored simulated battery state in its finally block. API34 was
then stopped and its emulator process exited before API37 startup.

Host database checks assert file presence and UI recovery, not exact saved rows
across real reboot. Row-level durability/rollback/baseline/migration evidence is
from actual SQLite instrumentation. Pixel-only profiles verified from AVD config:
API34 Pixel 7 / default x86_64; API37 Pixel 9 Pro / Google Play x86_64.
API37 direct instrumentation is next; no API37 host UI retry is planned.

## Findings addressed

| Finding | Implemented behavior and regression evidence |
| --- | --- |
| C1 HIGH | New UID or tag wakelocks require an earlier matching baseline; affected UIDs enter missingBaselineUids. Two short-window/large-history regressions executed RED then GREEN. |
| H1 HIGH | Shared repository serializes actual dumps through archive recording; dump-end timestamps retained; stale/equal timestamps cannot move the baseline backward. Gated concurrency and 10→16→delayed12→18=8 mAh regressions pass. |
| H2 HIGH | Required on-battery parsing is explicit; malformed text with valid UID power never establishes a complete archive baseline. Parsed zero is distinct from absence. |
| C2 | Nullable radio/component fields require both endpoints; new global components are excluded; explicit zero preserved. |
| C3 | Matched cumulative activity regressions invalidate intervals, including job count 100→2 while battery time grows; maxima/running-held durations remain non-cumulative. |
| C4 | Malformed/non-finite/overflowing matched energy and counts use the domain parse exception; required durations reject partial tokens and expose missing timing. |
| H3 | Zero-total UID coverage rows preserve unknown states in range and daily sums. |
| H4 | Nullable package versions and first-install times stored per interval and baseline; safe profile/UID resolution; update/install/ownership reasons separated; v1→v2 additive migration and history continuation pass. Unknown installation metadata remains a documented limit. |
| H5 | Explicit capture-triggered retention policy retained; app and daily range rules made identical; reopening >90 days without capture tested. No cleanup-on-open claim. |
| H6 | Actual SQLite close/reopen/continue and mid-transaction trigger-abort rollback verified. No production injection seam. Host file/UI evidence distinguished from row-level guarantees. |

No finding was rejected. H5 uses the explicitly permitted documented-policy
option; H4 only claims installation identity where metadata is actually available.

### Final API37 direct instrumentation: 29 passed, 1 UI blocked

API37 boot passed in **62.4s** within its four-minute bound. Installed the final
APKs, granted the same test permissions, simulated unplugging, and invoked the
full runner directly with a **180s subprocess deadline**; no host lifecycle script
was run. **30 tests executed, 29 passed, one failure**: the UI test timed out
waiting for `Refresh` at EmulatorValidationTest.kt:71. All **26 archive/package
metadata tests** and the three direct collection/background/snapshot tests passed.

Inspected once after the failure: the UI hierarchy showed **“System UI isn't
responding”**, matching the known API37 runtime blocker. Filtered AndroidRuntime
and ActivityManager error output showed no app crash. No API37 UI retry or assertion
change was made; **the full API37 suite is not claimed as passed**. Local diagnostics:
`/tmp/battery-review-fixes-20261028/api37-direct-failure-{ui.xml,logcat.txt}`.
Runner evidence: `artifacts/review-fixes-20261028/api37/instrumentation-direct.txt`;
individual outcomes: `api37/test-results.json` in that same artifact directory.

The direct helper restored simulated battery state in finally; it was also reset
explicitly before stopping API37. Both emulator processes have exited. The final
API37 host lifecycle route remains unavailable and was not retried.

## Runtime commands and handoff

Final API34 command (repository root):

```sh
timeout --kill-after=15s 8m env ANDROID_HOME=/home/ubuntu/Android/Sdk \
  python3 scripts/validate-emulator.py --serial emulator-5554 \
  --output artifacts/review-fixes-20261028/api34-retry
```

API37 direct helper (installs both APKs, grants test permissions, runs the full
runner with 180s timeout, resets battery in finally):

```sh
python3 /tmp/battery-review-fixes-20261028/run-instrument.py \
  artifacts/review-fixes-20261028/api37/instrumentation-direct.txt
```

The actual runner call is `adb -s emulator-5554 shell am instrument -w -r
io.github.sivivatu.batterymonitor.test/androidx.test.runner.AndroidJUnitRunner`.
AVDs were booted one at a time with `-no-window -gpu swiftshader -noaudio
-no-boot-anim -no-snapshot -cores 2 -memory 2048`; boot probes had four-minute
deadlines. Detailed emulator logs remain local under the same temporary directory.

Final source changes: **14 Kotlin files and two testing documents**; exact paths
are in `artifacts/review-fixes-20261028/changed-files.json`. No script, dependency,
CI, manifest, security policy or lint configuration change was needed. A concrete
unified source diff against the preserved pre-task source backup is available at
`artifacts/review-fixes-20261028/source.patch`, useful because the Android sources
remain untracked. Consolidated results are in that directory's `summary.json`.

No Gradle invocation stalled or timed out; expected RED tests, one formatter line
correction and one Kotlin test bind-array correction are documented above.
No active Gradle/emulator is left running. Branch and HEAD are unchanged. Existing
untracked work, `.gitignore`, source backups and local-only raw diagnostics are
preserved. No commit, push, publication or final code approval occurred.

Parent next action: independent review of the concrete patch (including the
additive SQLite migration) before publication. Remaining limits: API37 UI/system
ANR and host lifecycle blocker; Pixel emulator profiles only; physical Pixel
energy accuracy, overhead, long-term Doze and Shizuku reboot behavior unvalidated;
unknown installation metadata cannot prove same-package reinstall; retention is
capture-triggered; core branch coverage below 80%, app coverage unmeasured.

## Independent parent review

The parent collected separate Sol code, SQLite and security reviews after the
final checks above. All three reported **no HIGH or CRITICAL findings**. Code
review traced interval/continuity/parser changes and repository serialization;
database review checked transactions, migration, stale captures, coverage rows
and metadata identity; security review checked fixed privileged arguments,
bound queries, app-private storage, backup exclusions, anonymized fixtures and
the exact proposed source/CI publication set. No reviewer changed code or ran a
second competing build.

Two low-severity observations are deferred: revision notifications currently
advance immediately before SQLite transaction completion, so a commit failure
could cause a harmless extra refresh; local archive-error UI can include SQLite
exception details. Neither review identified a data-loss or remote exposure path
from these observations. They are not counted as fixed or as test guarantees.

After review, the parent updated the root/fork installation and build documentation
and upstream modification dates. These documentation-only edits do not alter the
tested APK. GitHub-hosted CI results, once available, are separate from the local
verification recorded here.

Publication preparation also removes trailing spaces from the synthetic
`batterystats-charged.txt` fixture and declares CRLF checkout for the Windows
Gradle wrapper, with its staged content normalized by Git. The parent reran
`spotlessCheck :core:batterystats:test :core:batterystats:jacocoTestReport` with
the same serial/timeout options: **PASS**, 32 seconds, all **104 tests passed**.
The staged whitespace check and secret/generated-file scan passed. No Android
production source changed after the executed device validation or final review;
the APK SHA-256 above is unchanged.
