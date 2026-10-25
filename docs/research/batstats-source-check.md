# BatStats: source-level follow-up

Research date: 25 October 2026. This supplements the existing-app survey with a
closer examination of a particularly relevant candidate. No APK or device was tested.

## Finding

[BatStats](https://github.com/mlm-games/BatStats) is worth evaluating before building
another app. It already contains device battery logging and persistent hourly
per-app/UID energy estimates with privileged collection. However, its inspected
privileged collector uses a potentially destructive check-in query, so historical
accuracy and non-destructive operation must be resolved before recommending that
mode on the Pixel. The inspected data model
and collector do **not** establish a persistent foreground/background energy split.
This is a concrete partial match, not evidence that the whole requirement is solved.

## Evidence pinned to a source revision

The inspected revision is
[`47b7ca46a1ad5fe2969a50c28142464860c584c0`](https://github.com/mlm-games/BatStats/tree/47b7ca46a1ad5fe2969a50c28142464860c584c0).

- [README](https://github.com/mlm-games/BatStats/blob/47b7ca46a1ad5fe2969a50c28142464860c584c0/README.md):
  offers root, Shizuku, and ADB-granted advanced statistics; explicitly warns that
  statistics depend on the device and that edge cases are not fully handled.
- [Room entities](https://github.com/mlm-games/BatStats/blob/47b7ca46a1ad5fe2969a50c28142464860c584c0/app/src/main/java/app/batstats/battery/data/db/Entities.kt):
  battery samples include timestamp, charge counter, current, voltage, temperature,
  and screen state; charge/discharge sessions are persisted. `AppEnergyStat` stores
  hourly bucket, package name, collection mode, mAh, and sample count. It has no
  foreground/background energy fields. Some comments still describe privileged
  modes as future work, so the collector is better evidence of actual wiring.
- [Privileged collector](https://github.com/mlm-games/BatStats/blob/47b7ca46a1ad5fe2969a50c28142464860c584c0/app/src/main/java/app/batstats/battery/shizuku/BStatsCollector.kt):
  differences cumulative per-UID energy snapshots and stores positive deltas in hour
  buckets, with the collection mode recorded. Shared or ambiguous UIDs remain grouped.
- [Parser](https://github.com/mlm-games/BatStats/blob/47b7ca46a1ad5fe2969a50c28142464860c584c0/app/src/main/java/app/batstats/battery/shizuku/CheckinParser.kt):
  reads `dumpsys batterystats --checkin` UID power totals and UID/package mappings;
  this path does not parse foreground/background power components.
- [DAO](https://github.com/mlm-games/BatStats/blob/47b7ca46a1ad5fe2969a50c28142464860c584c0/app/src/main/java/app/batstats/battery/data/db/Dao.kt):
  aggregates energy into hour buckets and queries top drainers for a date range and
  collection mode. This supports retained attribution data, though usability and
  accuracy of historical comparisons still need a device test.
- [Manifest](https://github.com/mlm-games/BatStats/blob/47b7ca46a1ad5fe2969a50c28142464860c584c0/app/src/main/AndroidManifest.xml)
  and [dependencies](https://github.com/mlm-games/BatStats/blob/47b7ca46a1ad5fe2969a50c28142464860c584c0/app/build.gradle.kts):
  no explicit Internet permission or obvious advertising SDK in these files. This
  limited source inspection is not a merged-manifest or binary audit and does not
  independently certify every distributed build as ad-free.
- [License](https://github.com/mlm-games/BatStats/blob/47b7ca46a1ad5fe2969a50c28142464860c584c0/LICENSE):
  GPL-3.0. Evaluate reuse obligations before copying implementation into another project.

GitHub's API reported a non-archived repository with a push on 23 October 2026;
[latest release returned by the API](https://github.com/mlm-games/BatStats/releases/tag/6.2.6)
was 6.2.6, published 14 September 2026. Source at the inspected revision declares
version 6.3.6, so these findings must not be assumed to describe the released APK.
Metadata and a recent push do not demonstrate maintenance quality or device compatibility.

## Material collection issue found during review

The [pinned collector, line 38](https://github.com/mlm-games/BatStats/blob/47b7ca46a1ad5fe2969a50c28142464860c584c0/app/src/main/java/app/batstats/battery/shizuku/BStatsCollector.kt#L38)
repeatedly runs `dumpsys batterystats --checkin`. In
[Android 17's real check-in path](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/am/BatteryStatsService.java#3419),
the OS prefers a saved completed-statistics file when one exists, dumps it, and
**deletes that saved file**. Later calls can instead return current statistics.
This does not reset all current OS accounting, but it can consume historical
evidence and mix different accounting windows in the collector's hourly deltas.

Before enabling this privileged mode, preserve any existing diagnostics privately
and verify the exact released collector. A corrected collector should use a
current-stat query such as `-c` and validate accounting-window/reset boundaries;
changing the switch alone does not establish correct deltas. Do not trust its
hourly comparisons until these checks pass. This issue was confirmed by source
inspection, not by running BatStats on a device. The source/release mismatch remains.

## Try-first acceptance checks

1. Use an official release and verify which source revision it represents.
2. On the intended phone, test ordinary mode first, then explicitly opt into
   Shizuku/ADB only if needed. Before enabling collection, preserve existing diagnostics
   and verify the non-destructive collector/current-window checks above. Confirm
   the displayed source of energy data.
3. Run a foreground workload, then background audio/download and overnight idle.
   Check whether per-app history exists across days and survives app/process restart.
4. Ask whether it displays foreground/background **energy**, foreground time, or
   device screen-on/off drain: these are different requirements.
5. Check reset/charging/reboot behavior. The inspected collector clamps negative
   energy deltas; this alone does not prove that every reset or UID change is handled.

## Other recent leads

- [Battery Monitor by tswistak](https://github.com/tswistak/Battery-Monitor): its
  publisher documents local battery event logging, CSV export, root/Shizuku hardware
  information, and a BatteryBot Pro lineage. These claims do not establish per-app
  foreground/background attribution. [Release 1.7.3](https://github.com/tswistak/Battery-Monitor/releases/tag/1.7.3)
  was published 4 October 2026 according to GitHub's API.
- [PULSE // BATTERY](https://github.com/privshell/pulse-battery): publisher explicitly
  describes free/no-ads/no-trackers distribution and an overnight per-app alarm-wakeup
  report via Shizuku. Its README explains why it uses wakeup counts rather than mAh;
  wakeup rankings must not be relabeled as energy rankings. Repository creation was
  15 October 2026 according to GitHub's API, so its longevity is unproven.

These are additional search leads, not endorsements. No claim is made that an
unsearched application cannot meet the requirement.
