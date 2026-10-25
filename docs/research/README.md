# Battery Monitor research

Research date: **25 October 2026**. Scope: existing Android apps and feasibility of
persistent battery history, per-app battery use, foreground/background distinctions,
and comparisons over time. Findings come from publisher listings, official Android
documentation, and source inspection. No physical-device or APK testing was performed.

## Target device

The intended phone is a **Pixel 9 Pro running Android 17**, as reported by the user.
The public-API report inspects an Android 17 source baseline; the privileged report
includes an Android 17 follow-up. None of these sources proves actual field support
or continuous collection on this phone. ADB/Shizuku remains an optional setup choice.

## Recommendation

**Evaluate existing apps before implementing a new one.** For the Pixel, begin
with **Settings → Battery → Battery usage**, plus
[Battery Monitor from F-Droid](https://f-droid.org/en/packages/codes.swistak.batterymonitor/)
for free local device logs and CSV export. This does not supply the full requested
per-app energy history; see the [app survey](existing-apps.md) for source evidence.

If elevated access is acceptable, [BatStats](https://github.com/mlm-games/BatStats)
is a released open-source lead with persistent hourly per-app/UID energy estimates
in its source. The inspected collector and schema do not establish a retained
foreground/background energy split; see the [pinned source check](batstats-source-check.md).
Repository source and the latest released APK differ, so capabilities need release verification.
**Review found a collection issue:** the inspected collector uses `--checkin`,
which can consume saved completed Android statistics and mix accounting windows.
Preserve existing diagnostics before any privileged trial; require a verified
current-stat collector and reset/window checks before trusting its historical totals.

[Wattdog](https://github.com/loganrosen/wattdog) is the closest inspected source-level
lead for the foreground/background energy split. It has no published GitHub release,
requires elevated setup, and its automatic history is retained for only two days
within a statistics session. It is an experimental reuse/contribution candidate,
not an established ready-to-install solution; the [survey](existing-apps.md) documents
its parser, storage behavior, and publisher-reported Android 17 fixtures.

A complete match for the requested combination has **not been verified**. That is
not a claim that no such application exists. Free download, no advertising, no
paid feature gate, open-source licensing, and modern Android compatibility are
separate checks. See the [existing-app survey](existing-apps.md) for alternatives.

## The technical distinction that determines the product

An ordinary Android app can collect **device-wide battery readings** and, with
Usage Access, **app activity history**. Joining those timelines can show what was
active during drain; it does not establish how much energy each app consumed.

[BatteryManager](https://developer.android.com/reference/android/os/BatteryManager)
exposes device battery properties, while
[UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
exposes usage statistics and events. Neither is a general public API returning
other apps' foreground/background energy consumption. Newer power monitoring
facilities must also be checked for scope and device support rather than assumed
to be a public replacement for system battery attribution.

Deeper estimates require a capability such as ADB-granted development permissions,
shell-level collection through Shizuku, root, or system integration. AOSP marks
[`BATTERY_STATS` and `DUMP`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/AndroidManifest.xml)
as `signature|privileged|development`; they are not ordinary runtime permission
prompts. A grant alone does not make hidden interfaces stable, bypass all service
checks, or guarantee compatibility on every Android/OEM version.

## If building is still worthwhile

Proceed through a small device feasibility check before committing to an app architecture:

1. Record the Pixel 9 Pro's exact Android 17 build and the choice about ADB/Shizuku.
2. Test a suitable history logger against the real requirements. Evaluate BatStats
   privileged collection only after checking the release, preserving diagnostics,
   and resolving its `--checkin` and accounting-window issues.
3. Verify device-wide battery properties, Usage Access events, and collection gaps
   during screen-off, Doze, charging, process death, and reboot.
4. If per-app energy is essential, test read-only battery-stat snapshots with the
   chosen elevated access. Confirm whether **energy by process state** is actually
   available; foreground time or screen-on drain is not a substitute.
5. Verify resets, shared UIDs, missing readings, estimator changes, and observer
   overhead before calculating differences between snapshots.
6. Only then choose reuse/contribution, a fork with the appropriate license, or a
   new application.

A useful ordinary-access app could store battery readings and app activity locally,
show hourly/daily trends and screen-on/off device drain, and export observations.
It must label these as observations or correlations. A deeper optional mode could
retain Android's per-UID estimates where supported and label their collection
method and limitations. Unsupported attribution should appear as unavailable.

For trends, keep device drain, app activity duration, and estimated per-UID energy
as separate series. Compare consistent windows with charging state, sample coverage,
collector version, and reset boundaries recorded. Never allocate all drain to the
visible app or convert background time directly into mAh.

## Detailed reports

- [Existing applications, costs, advertising, and gaps](existing-apps.md)
- [Public Android APIs and history collection limits](android-public-apis.md)
- [ADB, Shizuku, root, and system attribution options](android-privileged-options.md)
- [BatStats source-level follow-up and recent candidates](batstats-source-check.md)
- [Wattdog license, source reuse, and correctness evaluation](wattdog-evaluation.md)

This repository currently contains research and project setup, not a runnable Android app.
