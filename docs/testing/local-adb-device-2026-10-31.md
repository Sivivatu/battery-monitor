# Local ADB Android runtime validation

Build validation is recorded in [local-adb-2026-10-31.md](local-adb-2026-10-31.md).
This document records the device-level verification attempt for the Android
local wireless ADB preview.

## Intended flow

The setup pairs to the device's Wireless debugging pairing endpoint, connects to
the separate debugging endpoint, grants only this app's battery diagnostic
permissions, disconnects ADB, and verifies direct diagnostics as the app. The
Android instrumentation checks loopback networking, SPAKE2 pairing-code
agreement/rejection, and closure after a stalled pairing handshake. Existing
emulator checks also cover direct diagnostics, screen navigation, snapshot
persistence and background checks.

## Environment and runtime results

The host had Android Emulator 37.2.12.0 with usable KVM. Its Pixel 9 Pro shaped
API 37 AVD reports `sdk_gphone64_x86_64`, Android 17, fingerprint
`google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.051.A2/16418371:user/release-keys`.
This is an emulator, not a physical Pixel 9 Pro.

After regenerable Gradle caches were cleared, the existing AVD booted without
wiping its data or using a snapshot. Wireless debugging was explicitly disabled
(`settings get global adb_wifi_enabled` returned `0`); the host emulator ADB
transport remained connected.

The native pairing and transport instrumentation suite passed **4/4**:

- Loopback socket communication worked without an Android local-network
  permission.
- Matching pairing codes derived interoperable keys.
- Different pairing codes failed peer identity verification.
- A stalled TLS pairing peer timed out and its socket closed.

The existing emulator validation suite passed **4/4**. Direct batterystats and
power reads as the app passed with Wireless debugging off and DUMP,
PACKAGE_USAGE_STATS and BATTERY_STATS granted. Snapshot recreation/pruning,
automatic background-check snapshot persistence, and dashboard/history UI
navigation all passed. The first full-suite run's UI test hit a startup
`System UI isn't responding` dialog before showing the Refresh control. Its UI
hierarchy was captured, Wait was selected, and the single failed UI test was
rerun successfully (`OK (1 test)`).

The signed v2 beta APK (`0.1.0-beta.1`, code 2) updated in place to the signed v3
preview (`0.1.0-beta.2`, code 3). `adb install -r` succeeded, the original first
install time remained unchanged, and DUMP, PACKAGE_USAGE_STATS and BATTERY_STATS
were still granted. This validates package/signature continuity and permission
survival; no pre-existing user snapshot was seeded for a history-retention check.

Runtime logs and the earlier capacity-bound boot attempts are retained under
[API 37 artifacts](../../artifacts/adb-preview/api37/). Full Settings pairing
with a live Android pairing dialog was not verified. The physical Pixel 9 Pro
remains the final target for confirming that UI flow.
