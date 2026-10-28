# Wattdog provenance

This directory is derived from https://github.com/loganrosen/wattdog at commit
`06f3979a19f95c4b91f3a3234ccb7547b404e45e` (retrieved 2026-10-25).
Upstream author: Logan Rosen and contributors. The source was imported from
Git, not decompiled from an APK. Upstream elects GPL version 3 or later; this
fork retains that license (see LICENSE). Original source headers and README remain.

Fork modifications dated 2026-10-25 through 2026-10-28: nullable state estimates, interval continuity
validation, durable local SQLite interval archive and daily summaries, archive UI,
instrumentation tests, reproducible emulator validation, build/report automation.
Follow-up review fixes serialize captures, validate required timing and cumulative
activity, preserve zero-delta coverage and current-profile package/version metadata,
and add an additive SQLite schema migration with runtime regression tests.
The application ID is `io.github.sivivatu.batterymonitor` to allow side-by-side
installation with upstream. Kotlin packages retain upstream names for provenance.
This is an independent fork and is not an upstream Wattdog release.

To build the corresponding source: install JDK21 and Android SDK platform37, then
run `./gradlew :app:assembleDebug` here. Root documentation describes verification.
