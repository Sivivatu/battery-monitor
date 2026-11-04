# Local ADB validation and pairing diagnostics — 2026-10-31

## Report and scope

On a Pixel 9 Pro, connection port `98377` silently prevented setup. After the
user corrected it to `37743`, matching Android Wireless debugging, setup still
failed with the generic beta.2 error. The screenshots alone do not establish
which setup stage failed. The user subsequently confirmed switching between
Settings and Wattdog rather than keeping the pairing dialog visible in split
screen. This is consistent with Android stopping the pairing service when the
dialog is no longer visible; a successful fresh split-screen retry on the phone
has not yet been observed.

Beta.3 (version code 4) adds inline validation to both port fields and safe,
stage-specific failures for preparation, pairing, connection, grants,
disconnection, and verification. Ports must contain 1–5 ASCII digits representing
1–65535. Invalid pasted input remains visible instead of being silently shortened.
Pairing codes and raw exception/device output are never displayed in errors.
Primary failures and cancellation survive a concurrent cleanup failure.

## Automated evidence

- App JVM suite: 26 tests, zero failures/errors/skips.
- Core batterystats JVM suite: 110 tests, zero failures/errors/skips.
- Scoped setup/process coverage: 78/79 lines (98.7%). This is not whole-app coverage.
- Android emulator API 37: four native/transport tests passed (loopback access,
  matching/mismatching SPAKE2 codes, stalled TLS timeout and socket cleanup).
- Production client SSL context completed a real TLS 1.3 mutual-auth handshake
  with an ephemeral loopback test server requiring the exact client certificate.
  The unmodified provider/key manager passed. The suspected missing engine-key
  callback defect was not reproduced, and no speculative TLS change was made.
- Debug instrumentation APK compilation and formatting checks passed. Android
  lint completed with zero errors and 18 warnings.
- Android UI: entering `98377` preserved the input and displayed
  “Enter a port from 1 to 65535.” Evidence is under
  `artifacts/adb-pairing-fix/port-validation-ui.*` (locally ignored artifacts).
  Correcting the same field to `37743` cleared the inline error.

The validation and diagnostic changes passed code/security review. A review of
the new instrumentation fixtures identified a pre-existing-permission false
positive in the opt-in live test. The test now requires DUMP and
PACKAGE_USAGE_STATS to be absent before pairing and checks both grants afterwards.

## Limits

The user's physical-phone pairing failure has not been reproduced. The emulator
initially showed repeated System UI ANRs while opening Android Settings. After
correcting the emulator display density and suppressing its repeated system-error
dialog, app validation could be checked. Further Settings navigation lost the
emulator connection (QEMU CPU/main-loop hang recorded in its log); live Android
pairing was not executed. Native
pairing primitives and controlled TLS success do not prove end-to-end pairing
against Android adbd or explain the Pixel failure. The opt-in live test must not
be counted as passed unless run with a fresh, visible Android pairing dialog and
clean permission preconditions.

No CI, commit, push, F-Droid publication, or repository release was performed.

## Deliverable

- Signed APK: `artifacts/adb-pairing-fix/battery-monitor-0.1.0-beta.3.apk`.
- Package `io.github.sivivatu.batterymonitor`, version code 4, beta.3.
- Same beta signing certificate as beta.2:
  `d524168decc8c4c1fa779dc1a0ad5dd5a3882ccd4500e8cee0504328d481bc57`.
- SHA-256: `a9d482d7f627db5967cab6051ef05d39e78ad0de4cc0df104bcc5fdc693662b1`.
- Minified release build and release vital lint passed locally. Final signed APK
  passed signature verification and 16 KB ZIP alignment validation.
- Public temporary HTTPS download returned HTTP 200 with APK content type; the
  downloaded bytes matched the local signed APK's SHA-256.
- No beta.3 signed-device startup/update smoke was completed after the emulator
  exited. Beta.2-to-beta.3 update compatibility is supported by matching package,
  certificate and increasing version code; the actual update is not device-tested.
