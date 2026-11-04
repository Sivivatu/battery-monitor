Battery Monitor 0.1.0-beta.3 for Android 14 and later.

Install through our [F-Droid beta repository](https://sivivatu.github.io/battery-monitor/),
or download the signed APK below. This is a developer-operated feed, separate from
the default f-droid.org catalogue. No tester account is needed.

- Guided on-device ADB setup grants diagnostic permissions without a computer or Shizuku installation.
- Keep Android's pairing-code dialog and Battery Monitor visible in split screen. Switching away can stop pairing; use a fresh port/code when retrying.
- Port fields now explain invalid values. Setup failures identify pairing, connection, permission grants, or collection verification.
- Prevent a fast ADB response from leaving setup waiting after a successful permission grant.
- After successful setup, turn Wireless debugging off and verify direct collection. No persistent ADB connection is needed.
- Fix parsing of fractional battery-discharge counters on newer Android versions.
- No advertisements, telemetry, remote crash reporting, or uploads. Network permission is used only for temporary loopback ADB setup on this device.

Signed beta users can update without uninstalling and keep local history. Debug
previews signed with a different key require uninstalling first, which removes
saved history. This release retains the existing beta signing identity.

Battery values are Android estimates. Real-device accuracy, collection overhead,
Doze behavior, and complete physical-phone pairing still need validation. Android
17 local emulator validation encountered System UI limitations. This is an early
beta, not a stable release.

The matching GPLv3-or-later source archive is attached. Build instructions,
pinned dependency versions, third-party license texts, and upstream attribution
are included. APK and repository certificate fingerprints are pinned in
`fdroid/signing-certificates.json`.

Feedback and bugs: https://github.com/Sivivatu/battery-monitor/issues
