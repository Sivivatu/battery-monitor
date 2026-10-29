# F-Droid beta preparation — 2026-10-29

This records executed evidence separately from pending publication.

## Completed

- Two permanent RSA4096 signing identities generated outside Git. Eight signing
  values stored in Infisical EU, project `battery-monitor`, environment `prod`,
  folder `/android-signing`; immediate export matched all values and both JKS files.
- GitHub `fdroid-release` environment permits only branch `main`; eight encrypted
  environment-secret replicas configured. GitHub Pages configured for workflow
  deployment, public HTTPS. No Infisical login token is given to CI.
- Fourteen publication regression tests passed, including immutable source/version
  records, rollback rejection, APK retention, symlink rejection and public-file guards.
- Throwaway-key initial/repeat publication integration passed. Local permanent-key
  signing/index integration also passed with a test-only state directory. Its source
  label is not a published release claim; the eventual release uses the merged tag.
- Independent Sol code and security reviews approved without remaining HIGH or
  CRITICAL findings. Review findings led to a full Python dependency hash lock.
  A subsequent automated review corrected the publication job's OIDC permission:
  `deploy-pages` requires `id-token: write` independently of Infisical authentication.
  Both workflow YAML files pass actionlint
  1.7.12, downloaded from its official release and checksum-verified.
- API34 focused SQLite regression passed: `OK (1 test)`, 25.95 seconds. A >3MiB
  Unicode raw baseline remains readable across reopening/replacement, with three
  valid intervals and 10mAh recorded. Existing TEXT schemas need no migration.
  Evidence: `artifacts/beta-release/baseline-regression/`.

- Final minified release build, Spotless, JVM checks and release lint passed.
  Release lint reports 0 errors and 10 warnings; final build completed in 4m6s.

- Actual permanent-key, minified release smoke passed all eight checks on API34:
  setup, dashboard collection, force-stop/relaunch, signed in-place reinstall,
  retained install identity/collection grant, dashboard recovery, non-debuggable.
  Evidence: `artifacts/beta-release/release-smoke/results.json`.
  Tested APK SHA-256: `a8184b7fd570cbea73a1ceb9ee64530e4a57e8063b85da6423aacde1317f1bed`.
  CI rebuild bytes may differ; the final release records its own hash.

## Pending

- Hosted CI run, tagged prerelease, live Pages publication and public signature checks.
- A real F-Droid client install and a two-version client upgrade. The release smoke
  script validates signed in-place reinstall, not an incremental client upgrade.

The first release is `0.1.0-beta.1`, versionCode2. The beta feed is developer-operated,
not an assertion of inclusion in the default f-droid.org catalogue. No telemetry,
remote crash reporting, app account or internet permission is added.

Owner-controlled offline key backup, physical-device accuracy/overhead/Doze, and
Shizuku runtime validation remain outside the executed evidence. See the
[release runbook](../releasing/README.md) and [key custody](../releasing/signing-keys.md).
