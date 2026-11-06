# Android 17 history screen timeout — 2026-11-06

## Root cause

The focused History screen test initially failed at the unchanged `Refresh`
accessibility assertion after its 30-second wait. During a reproduced failure,
`dumpsys activity` showed the app's MainActivity top-resumed, but WindowManager's
focused window was `Application Not Responding: com.android.systemui`. A previous
API 37 run had the same title, `System UI isn't responding`, and no app crash.
The same Android 17 System UI/keyguard blocker had appeared in the repository's
October 28 and October 31 evidence. This is an emulator System UI ANR, not a
Battery Monitor process crash.

The app start is visible under the modal ANR window. The test cannot query its
`Refresh` control until the user dismisses the System UI warning. The test now
recognizes only the exact `System UI isn't responding` dialog, clicks its Android
`Wait` action once, waits up to three seconds for that dialog to disappear, and
then retains the original 30-second `Refresh` assertion and the complete History
navigation checks. It separately asserts that the warning is gone after Refresh
appears. If startup still fails, the test saves the accessibility hierarchy and
logs whether that exact dialog remains.

## Red/green evidence

The initial red repro on API 37 was:

```sh
adb -s emulator-5554 shell am instrument -w -r \
  -e class io.github.loganrosen.wattdog.validation.EmulatorValidationTest#liveCaptureAndSavedHistoryScreenOpen \
  io.github.sivivatu.batterymonitor.test/androidx.test.runner.AndroidJUnitRunner
```

It failed at `EmulatorValidationTest.kt:71` after 31.887 seconds. The contemporaneous
WindowManager focus was the System UI ANR dialog while MainActivity remained top
resumed. Logcat showed no application crash.

After adding the exact-dialog handling and bounded dismissal check, that same
focused test passed in 20.93 seconds, including `Refresh`, History, 7/30 day
ranges, Timeline, return to History and Dashboard. The full API 37 instrumentation
suite completed in 40.379 seconds: **36 passed, 1 skipped by assumption, 0 failed**.
The runner prints `OK (37 tests)` while reporting the live pairing assumption
with instrumentation status `-4`; that optional test is not a passing live session. It included archive/database,
package history, local ADB/certificate, collection and the History UI checks.
The simulated battery was unplugged for the suite and restored in a `finally`
block; the emulator reports AC powered afterward.

The full-suite invocation used the installed AndroidJUnitRunner on `emulator-5554`:

```sh
adb -s emulator-5554 shell am instrument -w -r \
  io.github.sivivatu.batterymonitor.test/androidx.test.runner.AndroidJUnitRunner
```

The 37 discovered tests are in `ArchiveStoreTest` (22), `PackageHistoryTest` (5),
`LocalAdbInstrumentedTest` (4), `ClientCertificateInstrumentedTest` (1),
`EmulatorValidationTest` (4), and `LiveAdbPairingInstrumentedTest` (1). The last
is an opt-in real-device smoke test: it requires a visible Android Wireless
debugging pairing dialog plus the two current Settings ports passed as runner
arguments. Without those prerequisites it exits via JUnit assumption; the
Android 17 run did not exercise a real Settings pairing session. Its live ports
and pairing code are intentionally not part of the repeatable full-suite
command.

The emulator is `BatteryMonitor_API37`, SDK 37 / Android 17, emulator version
37.2.12.0. The Device panel screenshot succeeded after the ANR overlay cleared.

Final checks from `android/` also passed:

```sh
./gradlew spotlessCheck :app:testDebugUnitTest :core:batterystats:test \
  :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  --no-parallel --max-workers=1 --console=plain
```

Result: **BUILD SUCCESSFUL**; app JVM tests **33/33**, core JVM tests **110/110**,
lint **0 errors** (19 warnings), and both debug APK builds succeeded.

## Limits

This recovery handles the specific Android System UI warning observed in this
API 37 emulator. It does not repair an emulator image whose keyguard service
repeatedly hangs. The focused run and complete suite passed on the current
Android 17 AVD, which remains running in the Device panel.
