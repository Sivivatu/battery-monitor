# Beta publication, feedback and observability audit

Audit date: 2026-10-29. Inspected commit: `d56041445a1d53bd6152fd0e0e2a08efdb6f4997`.
This is a source/artifact audit and implementation proposal. No release pipeline,
feedback feature, telemetry collection, hosting or credentials were configured at that audit point.
The later [release runbook](../releasing/README.md) describes the implemented beta pipeline.
See [distribution research](fdroid-beta-distribution.md) for dated external requirements.

## Current packaging and automation

- Application ID: `io.github.sivivatu.batterymonitor`; Android 14/API 34 minimum.
- `versionCode = 1`, `versionName = "0.1.0"`. Preview Git tags alone do not change
  either embedded value. Every distributable update needs a higher version code.
- `android/app/build.gradle.kts` defines a minified release build, but no release
  signing configuration. The current published development APK is a debug build.
- `apksigner verify --print-certs` confirms the inspected APK uses `CN=Android Debug`;
  its certificate SHA-256 is
  `74620f7f2691a41bf4a6e4d7f0a97afbfe10fd2c3cf31c15120b00c5cb733f6b`.
- `aapt2 dump badging` confirms version code 1 and the package/minimum SDK above.
  The merged manifest marks the inspected APK debuggable.
- `.github/workflows/android.yml` runs formatting, unit tests, coverage, lint,
  debug app/test APK builds and API 34 emulator validation. It uploads Actions
  artifacts. It has read-only repository permissions and no tag/release trigger,
  signing, F-Droid index update or hosting deployment step.
- The app retains upstream GPLv3-or-later licensing and pinned source provenance
  in `android/UPSTREAM.md`. Distribution must retain the license and provide the
  corresponding source and build information.

## Proposed delivery design

Initial beta scope confirmed on 2026-10-29: **publish without telemetry**.
PostHog/Sentry integration is deferred until after this release.

Use a **public custom F-Droid beta repository**, without download authentication
or tester accounts. The user revised the earlier restricted-beta decision on
2026-10-29. Keep separate public beta and stable repository streams once stable
releases exist, for example `/fdroid/beta/repo/` and `/fdroid/stable/repo/`.
The official public F-Droid catalogue remains a separate submission/build process.

Use the same application ID and persistent APK signing identity across these
custom streams if switching channels should preserve installation and data.
Allocate version codes from one increasing sequence across both streams. Users
who enable both feeds can receive the highest eligible version; a stable build
with a lower version code cannot downgrade an installed beta in place. Separate
repository paths are distribution channels, not Android app isolation. A separate
beta application ID is needed only if side-by-side installation becomes a goal.

A release workflow should:

1. Accept a deliberate beta release tag/manual release input from trusted source.
2. Run existing verification; build and smoke-test the actual release variant,
   including minification and privileged collection, not just the debug variant.
3. Check that its package, version code and tag match the intended release, and
   reject non-increasing version codes or a changed signing identity.
4. Sign the APK using a persistent beta/release APK key, verify it and record
   its certificate fingerprint and checksum. Back up the key separately.
5. Stage the APK, metadata/changelog and matching GPL source reference in a
   persistent F-Droid repository; retain earlier versions as configured.
6. Generate and sign the F-Droid index using a persistent repository signing key.
   Its role is distinct from Android APK signing. Pin the repository fingerprint
   when adding the feed to tester devices.
7. Deploy the index/APKs together over public HTTPS without a login, verify
   the served signatures/metadata, and test an actual client update.
   Use a concurrency lock so overlapping publications cannot lose repository state.

Use protected GitHub Actions environments and least-privilege deployment credentials;
never expose signing/deployment secrets to untrusted pull-request execution. Public
GitHub Releases and GitHub Pages are now eligible artifact/hosting options. No
per-tester credentials or download access-control service is required.

The current debug build cannot normally be updated in place by a release APK
signed with a different certificate. With the same application ID, expect an
uninstall/reinstall that deletes local history; this app has no archive export.
A separate beta application ID permits side-by-side installation but has its own
history and permissions. Choose the identity/signing strategy before onboarding
more testers; the final release needs an installation/update continuity test.

## Current telemetry and logging

| Category | Finding | Evidence |
| --- | --- | --- |
| Analytics, remote telemetry, automatic crash upload | None found in inspected source/declarations | No analytics/crash SDK in app dependencies or version catalogue; no network client/upload implementation found |
| Internet permission | Absent in the inspected debug APK/merged manifest | `aapt2 dump badging`; `app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml` |
| Local runtime errors | Present in Android Logcat, including exception stack traces | `AnomalyCheckWorker.kt:19`, `WattdogShellService.kt:33`, `ArchiveStore.kt:81` |
| Background-check status | Local latest timestamp/summary, access-failure counts and notification state | `alerts/AlertStore.kt`, `alerts/BackgroundCheck.kt`; app-private JSON |
| Battery diagnostics/history | App-private SQLite archive, raw baseline and compressed snapshot files with metadata | `data/ArchiveStore.kt`, `data/SnapshotStore.kt` |
| Retention | Archive pruning is capture-triggered at 90 days; automatic snapshots use a separate 48-hour/session policy | `ArchiveStore.RETENTION_MILLIS`, `BackgroundCheck.AUTO_KEEP`, README |
| Cloud/device-transfer backup | Disabled/excluded | Main manifest `allowBackup=false`; `res/xml/data_extraction_rules.xml` |
| User-submitted diagnostic bundle or GitHub feedback | Not implemented | UI/source search; no issue templates or feedback flow in `.github/`/app |

This establishes what the inspected app implements; it is not a runtime packet
capture or a claim about device-wide Android/OEM diagnostics. Dependencies and
Android itself can also write local system logs. There is no app-owned persistent
crash-report queue or remote observability dashboard. Existing Logcat output is
subject to Android buffer retention and access restrictions.

Raw battery dumps and package/UID history can identify installed apps and usage
patterns. Do not include them automatically in public bug reports. Retention is
not guaranteed to erase data exactly when its nominal age expires if collection
has stopped. Uninstalling or clearing app storage deletes this local history.

## Suggested first feedback feature

Add Settings actions for **Report a bug** and **Suggest a feature**. Collect a
short editable title/body and open a prefilled GitHub issue in the user's browser.
This requires no embedded GitHub token, backend or Google login. Users still need
to sign in to GitHub and submit the issue themselves; it is not a silent API upload.
Repository issue templates should prompt for app version, Android version,
collection method, expected/actual behavior and reproduction steps.

For bugs, offer a separately reviewed and redacted diagnostic summary. Default
metadata can include app version/build and Android API level; exclude raw dumps,
package lists, device identifiers and local history by default. Show exactly what
will be shared and state that issues in this public repository are public. URL
prefill is suitable for short text; attachments need an explicit export/share or
browser upload step, not a large URL query.

If reports must be submitted wholly inside the app, use a reviewed GitHub
OAuth/device-flow design or a small authenticated/rate-limited relay with a GitHub
App and server-held credentials. Never put a shared personal access token or app
private key in the APK. That is a larger feature and changes the networking/privacy
scope. The first beta can keep all monitoring local and use browser feedback.

## Telemetry integration considerations

The current telemetry audit above describes the unchanged app. The user asked
whether Sentry and/or PostHog can be added; see the separately sourced
[Android telemetry options](android-telemetry-options.md). This question is a
feasibility assessment, not evidence that SDKs or remote projects are configured.

An integration should:

- Treat crash/error diagnostics and product-usage analytics as distinct data
  purposes, with separate consent controls. Test first launch, disabled state,
  process restart and consent withdrawal, including queued data and initialization.
- Limit product events to how this app is used, such as screen/feature actions and
  setup outcomes. Do not treat monitoring of other installed apps as analytics.
- Exclude raw dumps, snapshots, app/package inventories, UID history and device
  identifiers. Sanitize exception messages and automatic breadcrumbs; stack traces
  alone do not guarantee an event is free of private battery data.
- Keep screen recording/replay, screenshots, broad interaction autocapture and
  continuous tracing outside the initial beta scope. Verify SDK defaults against
  the pinned versions and test the actual resulting network payloads.
- Add INTERNET permission deliberately and update README/setup privacy wording
  to describe optional diagnostic/analytics traffic accurately. Preserve the
  promise that raw battery diagnostics remain local unless an explicit future
  export flow is added.
- Report relevant handled failures deliberately: the current background worker
  and archive paths catch exceptions and log them, so uncaught-crash reporting
  alone will not cover those failures. Avoid collecting repeatedly identical
  background failures every run without deduplication/rate limits.
- Account for the Shizuku shell service's separate process. Do not assume that
  initializing an SDK in the main application covers shell-process failures or
  should initialize a second telemetry client there.
- Identify release/build and beta/stable environment in diagnostics. Keep R8
  mappings/native symbols needed for readable release stacks tied to the exact
  build, with CI upload credentials limited to trusted release jobs.
- Select cloud region or self-hosted endpoint, retention and project configuration.
  Keep administrative tokens out of the APK; ingestion configuration is distinct
  from administrative access. Reassess F-Droid metadata/build requirements before
  proposing official inclusion with a telemetry-enabled binary.
- Measure observer overhead on the target phone. Check network/CPU wakeups,
  queueing while offline, storage, and idle/Doze behavior with telemetry enabled
  versus disabled; this matters especially for a battery-monitoring application.

No telemetry was enabled and no service account/project was created in this work.

## Google-account and device-verification distinction

F-Droid distribution and browser-based GitHub feedback do not require adding
Google login to this app. Android's separate developer-verification rollout can
still affect outside-Play installation on certified devices. The detailed official
[distribution guide](https://developer.android.com/developer-verification/guides)
scopes initial enforcement to participating stores in selected regions, describes
later expansion, and preserves ADB installation. Check actual tester regions,
devices and installation paths rather than treating private hosting as an exemption.

The official [limited-distribution help page](https://support.google.com/android-developer-console/answer/17131204?hl=en)
requires a Google account, two-step verification and a payments profile, even though
it does not require government ID. It also restricts registration to new package
names not previously seen on Android. Because previews of this app have already
been distributed, do not assume its existing package ID is eligible. The dedicated
help/guide currently says 50 devices while the general overview still says 20;
confirm the effective console rules before choosing this path. See the accompanying
research for sources and remaining documentation inconsistencies.

## Decisions before implementation

- Decided: public beta downloads, no tester login, separate beta/stable streams.
  Validate unauthenticated feed refresh, installation, update and channel switching.
- Public hosting/domain and CI deployment credentials.
- Persistent APK and repository signing keys, backup/recovery custody, beta/stable
  application identity and eventual official F-Droid signing strategy.
- Browser-based GitHub submission or fully in-app authenticated submission.
- Decided: defer Sentry/PostHog until after the initial beta. Telemetry projects
  and endpoints are not prerequisites for beta publication. Future integration
  should follow the separately documented [telemetry options](android-telemetry-options.md).
