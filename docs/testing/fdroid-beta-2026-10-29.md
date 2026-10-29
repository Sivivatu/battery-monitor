# F-Droid beta publication — 2026-10-29

Initial public beta published and verified on 2026-10-29. Earlier preparation
evidence is retained below; published artifacts and outstanding validation are
identified separately.

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

## Published and verified

- [PR1](https://github.com/Sivivatu/battery-monitor/pull/1) merged after all checks
  passed. Both hosted validation runs passed 31 instrumentation tests and all
  lifecycle checks. One run needed a single retry for the post-reboot dashboard
  assertion; no application changes or weakened assertions were used to pass it.
- Tag `v0.1.0-beta.1` points to merged source
  `cb487a40d66848a17a354838be59b54b851b9698`.
- [Publication workflow 40964616497](https://github.com/Sivivatu/battery-monitor/actions/runs/40964616497)
  passed both build and publish jobs, including all eight signed release smoke
  checks, retained-state push, prerelease assets, Pages deployment and live verification.
- [Public landing page](https://sivivatu.github.io/battery-monitor/) and
  [beta feed](https://sivivatu.github.io/battery-monitor/fdroid/beta/repo) are live.
  Independent verification passed both signed index formats, APK hash/certificate
  and the expected source commit. Public state is retained on branch `fdroid-repo`.
- [GitHub prerelease](https://github.com/Sivivatu/battery-monitor/releases/tag/v0.1.0-beta.1)
  contains the signed APK and corresponding GPL source archive. The APK matches
  the locally smoke-tested bytes, SHA-256
  `a8184b7fd570cbea73a1ceb9ee64530e4a57e8063b85da6423aacde1317f1bed`.
- Source archive SHA-256
  `336fe8a90b26212d533ed63d728c2d0fe61b00639ec7d6dbf90a090dbb3986db`
  matches a fresh `git archive` of the release tag exactly.
- Official F-Droid client 2.0.2 on a disposable API34 emulator accepted the
  fingerprinted feed, displayed Battery Monitor, and installed versionCode 2 /
  `0.1.0-beta.1` through Android's normal installer confirmation. Android reports
  installer `org.fdroid.fdroid`; pulling the installed APK produced the exact
  published hash, and it is not debuggable. Local, Git-ignored evidence is under
  `artifacts/beta-release/fdroid-client/`; downloaded hosted smoke evidence is under
  `artifacts/beta-release/published/ci-smoke/`.

## Remaining validation

A real two-version F-Droid-client upgrade has not been exercised: this feed has
one release. Signed in-place reinstall and repository version/retention guards
passed, but they do not establish a two-version client upgrade.

The first release is `0.1.0-beta.1`, versionCode 2. The beta feed is developer-operated,
not an assertion of inclusion in the default f-droid.org catalogue. No telemetry,
remote crash reporting, app account or internet permission is added.

Owner-controlled offline key backup, physical-device accuracy/overhead/Doze, and
Shizuku runtime validation remain outside the executed evidence. See the
[release runbook](../releasing/README.md) and [key custody](../releasing/signing-keys.md).
