# Battery usage over time

## Product requirement

Make saved usage across charging sessions easy to find, show daily changes over
1, 7, and 30 days, and let users start collection from that view. Existing live
diagnostics and the detailed Android battery timeline remain available.

## Architecture and system design

Reuse the existing private SQLite archive and opt-in hourly WorkManager job.
No schema, permissions, retention, or collection frequency changes are needed.
The History navigation destination opens saved trends. A separate Timeline action
opens the rotating Android event history. Archive revisions reload the selected
range after captures.

## Technical design

A pure daily-trend projection emits each UTC date in the requested rolling range,
oldest first. Recorded zero stays zero; dates without comparable readings stay
unknown. Compose renders this projection as daily bars with accessible values.
The chart sums Android app estimates, not measured total device discharge.
Collection controls expose opt-in background checks, last check status and manual
refresh. Saving fails visibly; collection requires diagnostic access. Unknown
coverage, reset boundaries and UTC capture-end grouping remain explicit.

## Tasks and validation

1. Add failing unit tests for daily projection and unknown/zero behavior.
2. Add trend projection, daily chart, navigation and collection controls.
3. Extend Android UI instrumentation for the History and Timeline routes.
4. Run formatting, JVM tests, lint and APK/test APK builds; execute emulator
   checks if available. Review changes and fix material findings.

## Limits

There is no backfill before the first baseline. A later comparable capture is
required. Android can delay hourly collection, and whole-interval range selection
may omit intervals crossing a boundary. Daily totals use UTC capture-end dates.
