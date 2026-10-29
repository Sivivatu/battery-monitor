Public early beta of Battery Monitor for Android 14 and later.

Install through our [F-Droid beta repository](https://sivivatu.github.io/battery-monitor/),
or download the signed APK below. This is a developer-operated feed, separate from
the default f-droid.org catalogue. No tester account is needed.

- Per-app battery estimates, available foreground/background breakdowns, and local history.
- Collection requires [ADB grants or Shizuku](https://github.com/Sivivatu/battery-monitor#install-and-enable-collection).
- No advertisements, telemetry, remote crash reporting, or internet permission.
- Feedback and bugs: https://github.com/Sivivatu/battery-monitor/issues

Debug-preview users must uninstall the preview before installing this release
because the signing identity changes. Uninstalling removes saved history;
there is no archive export yet. Later beta updates retain the release signing key.

Battery values are Android estimates. Real-device accuracy, collection overhead,
Doze behavior, and Shizuku operation still need validation. Android 17 emulator
validation has known System UI limitations. This is not a stable release.

The matching GPLv3-or-later source archive is attached; build instructions,
dependency versions, license, and upstream attribution are included. APK and
repository certificate fingerprints are pinned in `fdroid/signing-certificates.json`.
