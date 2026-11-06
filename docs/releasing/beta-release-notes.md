Battery Monitor 0.1.0-beta.4 for Android 14 and later.

Install through our [F-Droid beta repository](https://sivivatu.github.io/battery-monitor/),
or download the signed APK below. This is a developer-operated feed, separate from
the default f-droid.org catalogue. No tester account is needed.

- Open **History** from the main navigation for saved 1-, 7- and 30-day usage across charging sessions.
- Swipe the daily app-usage chart and tap a dated bar to see its charge estimate. Missing readings remain unknown rather than becoming zero usage.
- Turn on **Automatic history** or use **Capture now** directly in History. Hourly collection can run while the app is closed; Android may delay checks.
- Local history collection works without notification permission. Permission is needed only for battery-problem alerts.
- Per-app trends show every recorded date in the selected range. **Timeline** opens Android's separate detailed battery-event history.

The first complete capture establishes a baseline; later comparable captures
populate history. Usage from before collection started cannot be recovered.
Daily totals use UTC capture-end dates and include complete recorded intervals,
so days with partial coverage are not directly comparable.

Signed beta users can update without uninstalling and keep local history. Debug
previews signed with a different key require uninstalling first, which removes
saved history. This release retains the existing beta signing identity.

Android 17 / API 37 emulator validation completed with 36 device tests passed,
one optional live-pairing test skipped, and zero failures. The history-navigation
journey passed after recovering a stale emulator System UI warning. All 143 JVM
tests, lint and debug APK builds passed. The publisher separately validates the
minified signed release before updating the feed.

Battery values are Android estimates. Physical-phone accuracy, collection
overhead, long-term Doze behavior and complete physical-phone pairing still need
validation. No advertisements, telemetry, remote crash reporting or uploads are
included. This is an early beta, not a stable release.

The matching GPLv3-or-later source archive is attached. Build instructions,
pinned dependency versions, third-party license texts and upstream attribution
are included. APK and repository certificate fingerprints remain pinned in
`fdroid/signing-certificates.json`.

Feedback and bugs: https://github.com/Sivivatu/battery-monitor/issues
