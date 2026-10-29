# Emulator implementation plan — 2026-10-25

## Product requirements

Provide a free, ad-free local Android monitor based on pinned GPLv3+ Wattdog.
Retain per-app charge estimates and available foreground/background states across
process death and battery-accounting resets. Show trends with honest missing-data
and partial-coverage labels. Never infer energy from foreground duration.

## Architecture and system design

Keep upstream's Compose UI, allowlisted privileged collectors, parser and WorkManager
checks. A JVM core validates intervals before aggregation. App-private SQLite stores
one recoverable raw baseline plus normalized intervals and UID/package identities.
Raw gzip manual/automatic marks remain separate. No network permission or backend.
ADB development grants are the primary emulator path; Shizuku remains an alternative.

## Technical decisions

Use NULL for absent process-state mAh, retain explicit zero. Reject missing/changed
session identity and regressed counters. New UIDs require a baseline. Persist valid
intervals and gap/reset/partial boundaries in a single transaction; keep 90 days.
Count on-battery accounting time separately from wall time and app coverage.
Calendar buckets must state their interval assignment rule.

## Tasks and acceptance

1. Import pinned source/license and preserve provenance.
2. Reproduce regression failures then fix parser/delta defects.
3. Add transactional archive, retention and restart/reset tests.
4. Wire saved-history ranges and daily per-app comparisons.
5. Run API37 and minimum API34 instrumentation and guarded host lifecycle checks.
6. Run formatting, build, lint, coverage and independent code/SQL/security reviews.
7. Publish source, installable debug APK and dated evidence, with physical-device
   energy accuracy explicitly left for a later Pixel acceptance check.
