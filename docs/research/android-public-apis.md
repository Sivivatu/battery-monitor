# Android public battery APIs: measurement and attribution limits

Research date: **2026-10-25**. Scope: an ordinary, unrooted, third-party Android app, with optional user-granted Usage Access; no shell identity, ADB grants, Shizuku, OEM privileges, or physical-device test.

**Target clarified: Pixel 9 Pro running Android 17.** ADB/Shizuku willingness is unknown, so neither is a baseline recommendation. “Documented support” below describes Android API contracts; actual Pixel sensor availability, enabled feature flags and background behavior remain untested.

## Conclusion

**Persistent whole-device battery history is feasible. Accurate measurement of other apps' energy consumption split into foreground/background is not exposed by the public APIs examined.** BatteryManager supplies device-level battery state; UsageStats supplies app activity durations/events; NetworkStats supplies UID traffic. Combining these produces useful correlations, not measured per-app mAh or energy. Android's internal per-UID battery attribution exists, but its service enforces `BATTERY_STATS`. [B] [U] [N] [BS]

Recommended product: retain local battery samples and app-activity history, compare discharge trends over time, and label app timelines as activity. Offer subsystem energy only as an optional device-dependent diagnostic. Do not promise a complete “background battery drain by app” ranking from ordinary permissions. This is an engineering conclusion from the access boundaries below, not a device-tested result.

For this Pixel on Android 17/API 37, the API 21–36 entry points described below meet their documented OS-version gates, including API 35 filtered UsageEvents and power-monitor queries. That **does not guarantee hardware support**: probe each battery property and enumerate power monitors, retaining unsupported results. Pixel branding does not grant `BATTERY_STATS` or bypass Usage Access. Android 17 also makes the modern foreground-service/boot restrictions relevant; where a rule depends on target SDK, check the chosen app's target rather than equating installed OS with app target. [REL] [B] [U] [H] [PERM] [F] [FT]

The [BatStats source check](batstats-source-check.md), pinned to `47b7ca46a1ad5fe2969a50c28142464860c584c0`, reports persistent hourly per-UID mAh deltas through privileged collection, with **no foreground/background energy fields or parser split**. It is a useful partial-match lead, not evidence of ordinary-permission attribution or verified Pixel 9 Pro/Android 17 compatibility. This report references that inspection without duplicating it; whether the released APK matches the inspected revision remains unverified.

## Evidence and version scope

- Started with `gh search repos 'android battery monitor'` and `gh search code 'getSupportedPowerMonitors' --repo aosp-mirror/platform_frameworks_base`, followed by a BatteryManager code search. Repository search returned examples; both scoped code searches returned no matches. Empty search results are not evidence that an API is absent. Official references and AOSP supplied the findings.
- The official AOSP build table identifies **Android 17 / API 37 / 26Q2**, and lists `android-17.0.0_r1` with security patch level 2026-06-05. That tag resolves successfully; framework source citations below pin commit **`94b4c163b7dfe5ce3607f7bb8456f9573f7de57d`**. This supports an Android 17 release baseline; it does not prove any particular phone has received it. [REL] [REV]
- The live API reference also advertises **37.1/37.2**; Android 17 navigation labels QPR1/QPR2 as beta. Conversely, the SDK Platform release page still tops out at API 36. Treat these as unevenly updated catalogs, not independent proof of rollout or production availability. [17] [SDK]
- API introduction levels below come from individual official reference entries. Source confirms implementation behavior at the pinned revision, not an OEM-wide compatibility guarantee. No emulator, SDK compilation, battery calibration, or physical-device test was performed.

## 1. Battery state and counters

`Intent.ACTION_BATTERY_CHANGED` is a **sticky broadcast, API 1**. It carries charging state, level, and other battery information. Receive it through `Context.registerReceiver()`; a manifest receiver cannot receive it. Calling `registerReceiver(null, filter)` obtains the current sticky state without keeping a receiver alive. The basic query needs neither Usage Access nor `BATTERY_STATS`. It is a latest snapshot, not historical replay or a guarantee of periodic delivery. [I] [BG]

Public extras include level/scale, status, plugged source, present, health category, voltage, temperature, and technology. The common BatteryManager extra constants are documented from API 5. Compute percent as `100 × level / scale`, with validation for missing values and a positive scale. AOSP maps voltage to **millivolts** and temperature to **tenths of a degree Celsius**. `EXTRA_HEALTH` is a categorical status such as GOOD/OVERHEAT, not capacity health percent. [B] [BROADCAST]

`BatteryManager.getIntProperty()` and `getLongProperty()` were added in **API 21**. Their core public properties describe the **entire battery**, without package or UID attribution: [B] [BM]

- `BATTERY_PROPERTY_CAPACITY`, API 21: remaining capacity as an **integer percent**, with no fractional part.
- `BATTERY_PROPERTY_CHARGE_COUNTER`, API 21: battery charge in **µAh**. Treat it as a remaining-charge gauge reading, not a lifetime discharge accumulator; the HAL identifies it as instantaneous battery capacity. Divide by 1,000 for mAh. [HAL]
- `BATTERY_PROPERTY_CURRENT_NOW`, API 21: instantaneous **µA**. The contract is **positive into the battery while net charging, negative out while net discharging**. Divide by 1,000 for mA.
- `BATTERY_PROPERTY_CURRENT_AVERAGE`, API 21: average **µA**, same sign convention. The averaging window depends on fuel-gauge hardware/configuration; it is not a standardized “last minute” value.
- `BATTERY_PROPERTY_ENERGY_COUNTER`, API 21: **remaining energy in nWh**, returned as a long. Divide by 1,000,000 for mWh. It is not cumulative energy consumed since boot.
- `BATTERY_PROPERTY_STATUS`, API 26: a `BATTERY_STATUS_*` charging-state value.

Unsupported/error results must remain missing data: `getLongProperty()` returns `Long.MIN_VALUE`; `getIntProperty()` returns `Integer.MIN_VALUE` for callers targeting API 28+, but **0** for older targets. Do not convert these sentinels into chart values. The HAL separately permits average current to be zero when unsupported, so zero alone cannot establish a working sensor. Detect availability per field and test sign/freshness against observed charge state on the actual device. [B] [BM] [HAL]

`isCharging()`, API 23, indicates sufficient external supply to charge or maintain full charge; it is not equivalent to merely being plugged in. `computeChargeTimeRemaining()`, API 28, returns an **approximate millisecond** time to full, or **−1** when unavailable/discharging. It is not a per-app consumption API or a guaranteed discharge-time prediction. [B]

### Newer battery fields: useful, but not an attribution escape hatch

- **API 34:** `EXTRA_CYCLE_COUNT` and `EXTRA_CHARGING_STATUS`; report only available/credible device values. A cycle count does not identify which apps consumed the battery. [B]
- **API 35:** `getStringProperty()` exists, returning null if unsupported. The method's existence does not make every AOSP string-property identifier public or grant access to battery serial/manufacturing information. [B] [BM] [REG]
- **API 36:** `EXTRA_CAPACITY_LEVEL` and `BATTERY_CAPACITY_LEVEL_*` describe system power/charging capability categories. They are neither a finer battery percentage nor per-app energy. CPU/GPU headroom methods also arrive at API 36; see section 4. [B] [H]
- **Android 17 source:** `BATTERY_PROPERTY_STATE_OF_HEALTH` appears in the public signature file behind `android.os.state_of_health_public`; the live BatteryManager reference retrieved here does **not** list it. AOSP defines estimated full-charge/rated capacity percent, and enforces `BATTERY_STATS` if the public flag is disabled. Do not assign it a verified universally available SDK introduction level or rely on raw property ID 10. This source/catalog discrepancy needs SDK and device confirmation. [API] [BM] [REG]
- Manufacturing date, first-use date, serial, manufacturer/model and design-voltage identifiers found in source are hidden/system or privileged paths at this revision. Source presence does not establish an ordinary public API. The service's permission checks are decisive. [BM] [REG]

Counter deltas can estimate **net whole-device discharge** over a window. Gauge recalibration, quantization, charging, reboot/reset, and missing samples require segment boundaries and quality flags. Integrating sparse current samples is only an estimate; a live current reading cannot reconstruct unobserved spikes. While plugged in, battery current represents net battery flow, not the phone's total power from its charger. These are measurement implications of the documented quantities, not a vendor accuracy guarantee. [B] [HAL]

## 2. App activity: UsageStatsManager and UsageEvents

`UsageStatsManager`, `queryUsageStats()` and `queryEvents(begin,end)` are **API 21**. Reading other apps' activity requires declaring `PACKAGE_USAGE_STATS` **and the user enabling Usage Access in Settings**; declaring the permission alone is insufficient. Methods limited to the caller, such as `queryEventsForSelf()` (**API 28**), do not require that grant. Usage Access does not grant battery-statistics access. [U] [H] [BS]

Public measurements and their limits: [E] [US]

- `getTotalTimeInForeground()`, API 21: package foreground time in **milliseconds**.
- `getTotalTimeVisible()`, API 29: visible activity time, including foreground time; visible does not necessarily mean foreground.
- `getTotalTimeForegroundServiceUsed()`, API 29: time with foreground services started, in milliseconds. This can overlap activity time; do not add the two as independent totals.
- `MOVE_TO_FOREGROUND/BACKGROUND`, API 21, deprecated in API 29 in favor of `ACTIVITY_RESUMED/PAUSED` (same event values). The newer events correspond to activity `onResume()/onPause()`; `ACTIVITY_STOPPED`, API 29, corresponds to becoming invisible.
- `FOREGROUND_SERVICE_START/STOP`, API 29: service lifecycle events, including package/class. Multiple services and activities require per-component state handling rather than one alternating package flag.
- `SCREEN_INTERACTIVE/NON_INTERACTIVE` and keyguard events, API 28, provide device context. They do not prove a particular app caused screen-off drain.

**Foreground service is a distinct state from a foreground UI.** A music player may have no visible activity while its foreground service runs. “Not foreground” also includes apps doing no work at all. There is no public UsageStats measurement of each other app's CPU-active background time, wake-lock energy, or total background energy. Do not compute “background running time” as window duration minus foreground time. [E] [US] [API]

`queryUsageStats()` returns interval aggregates and may expand the requested range to whole intervals. It is unsuitable for pretending to have exact per-minute historical transitions. `queryEvents()` supplies transitions but the contract retains them only for **a few days**, not a guaranteed 7/30-day archive. Collect incrementally into the app's own storage, with overlap/deduplication and explicit coverage gaps. AOSP's daily-file pruning at ten days is an implementation detail, not a public retention promise. [U] [RET]

From **Android 11 / API 30**, relevant usage queries return **null when the user is not unlocked** (`UserManager.isUserUnlocked()`). This refers to the credential-encrypted user storage state, especially before the first unlock after boot; it does not mean every ordinary screen lock makes the user “locked” in this API sense. Retry after unlock, and distinguish unavailable data from an empty activity history. [U] [DB]

`DEVICE_SHUTDOWN/STARTUP`, API 29, explicitly warn that shutdown timestamps represent the last database persistence; unmatched activity/service intervals around a restart have unknown ends and should be ignored rather than extended across the reboot. Multi-window activities and overlapping services also mean package durations are not a partition of wall-clock time. [E] [US]

**API 35** adds `queryEvents(UsageEventsQuery)`, supporting filtered queries; prefer it where available. Android 17 source marks the older time-range overload deprecated behind a flag, although the fetched live reference does not show that deprecation. Neither overload returns energy. [U] [API] [U17]

The live reference additionally lists `queryAppUsageDuration(AppUsageDurationQuery)` as **37.2**, requiring `QUERY_APP_USAGE`, calendar-aligned aggregation, and queries limited to the past **30 days**. It is absent from the pinned API 37 signature file and the Android 17 navigation labels QPR2 beta. Treat it as a newer catalog/preview item, not a stable baseline or an energy API; its availability and permission UX were not validated. [U][17] [API]

## 3. NetworkStatsManager: foreground/background bytes, not battery

`NetworkStatsManager` and bucket state constants are **API 23**. By default an app sees its own UID; access to other apps/device totals requires Usage Access (or special owner/carrier roles). Before API 24, even own-app queries required the usage permission. Ordinary user-granted access has user/profile boundaries; do not assume visibility into every work profile or Android user. [N]

`querySummary()` preserves UID and state while aggregating over the requested time window. `queryDetailsForUid()` aggregates states together; do not use it to claim a foreground/background split. `queryDetailsForUidTagState()` is **API 28** and can select a state. It includes complete time buckets rather than interpolating partial buckets; documented bucket lengths are on the order of **hours**, unsuitable for fine-grained time alignment. Run queries off the UI thread and handle missing/denied results. [N]

Buckets expose RX/TX **bytes and packets**, with `STATE_FOREGROUND`, `STATE_DEFAULT` (“not accounted for in any other state”), and `STATE_ALL`. Prefer “foreground traffic” / “other-state traffic” labels over implying these precisely match resumed-activity time. AOSP networking foreground accounting can include foreground-service process states, so its classification differs from a visible-app timeline. No bucket contains energy or mAh. [NB] [NSTATE] [NP]

Subscriber IDs have additional restrictions from API 29. Passing null for mobile queries can request all mobile networks without retrieving an IMSI; Wi-Fi null queries cover all Wi-Fi. No telephony-identifier permission is needed merely to build an aggregate traffic view this way. [N]

UID totals are not necessarily one package; shared UIDs and removed-app buckets need careful presentation. Keep UID/profile identity and known package associations, not invented per-package splits. Traffic volume cannot determine radio energy: neither these fields nor the public API supply a universal joules-per-byte conversion. [NB] [N]

The reviewed NetworkStats contract does not promise indefinite retention or a fixed portable history duration. Retain imported buckets locally if long-term comparisons matter; do not advertise a guaranteed historical backfill depth. [N]

## 4. SystemHealthManager and power monitors

`SystemHealthManager` health snapshots are **API 24**. `takeMyUidSnapshot()` works for the calling UID. `takeUidSnapshot(otherUid)` / plural snapshots require `BATTERY_STATS`; AOSP checks this at the service boundary. The permission is **signature|privileged|development**, not a normal user runtime grant. ADB-granted development permission, root or a shell bridge is a separate installation/privilege workflow even when the physical device is unrooted. [H] [PERM] [BS]

Android's `BatteryStatsManager`/`BatteryUsageStats` infrastructure contains per-UID/subsystem attribution, but the manager is `@SystemApi`/hidden and `getBatteryUsageStats()` enforces `BATTERY_STATS`. Settings' ability to display per-app battery use does not imply a supported third-party SDK export. Reflection does not remove server-side checks; Usage Access is not a substitute. [BSM] [BS] [API]

**API 35** adds public `PowerMonitor`, `PowerMonitorReadings`, `getSupportedPowerMonitors()` and `getPowerMonitorReadings()`: [H] [P] [PR]

- Enumeration includes raw ODPM rails and modeled energy consumers; an unsupported device can return an **empty list**. Availability must be probed on each phone.
- Readings are accumulated **µW·s (µJ) since boot**, including both plugged-in and battery operation; they do not persist across reboot. Divide by **3,600,000** for mWh.
- `getTimestampMillis()` uses **elapsed realtime milliseconds**, not Unix time. Compute deltas with the reading timestamps, not just callback arrival times.
- `ENERGY_UNAVAILABLE` is **−1**. Do not plot it as negative energy or silently replace it with zero.
- Raw rail names are device-specific. Modeled consumers can combine or divide rails; overlapping rails/consumers must not be indiscriminately summed into a “total.”
- These APIs take monitors, not target UIDs/packages. They provide subsystem/device measurements, **not other apps' foreground/background energy**.

No special permission is required for the ordinary coarse power-monitor methods at this revision. However, AOSP caches ordinary readings for up to a **20-second freshness threshold** and adds randomized noise. A separate `ACCESS_FINE_POWER_MONITORS` permission is signature/privileged/development, and the implementation uses a 250 ms freshness threshold for that path; this is not an ordinary app capability. These are pinned implementation details, not universal precision guarantees. [H17] [PS] [PERM]

**API 36** CPU/GPU headroom APIs (`getCpuHeadroom`, `getGpuHeadroom` and support/window/minimum-interval queries) estimate available compute capacity. Device support is optional; unsupported implementations can throw `UnsupportedOperationException`, and rapid calls may return cached data. They do not report energy or expose an energy ledger for other apps. The reviewed API 37 signature still has the same health/power-monitor boundary. [H] [API]

## 5. Reliable history under background restrictions

WorkManager is the appropriate baseline for **persistent, deferrable** capture/import: scheduled work survives ordinary app restarts and device reboot. Periodic work has a **15-minute minimum repeat interval**, not a guarantee of one measurement every 15 minutes; constraints/system optimization can delay or skip runs. It cannot backfill battery samples it never observed. [W] [WM]

In Doze, Android defers JobScheduler work (including WorkManager), standard alarms and network access, and ignores ordinary wake locks. Maintenance windows become less frequent during extended idle. A foreground service is not a blanket Doze exemption, and the official guidance rejects starting one merely to keep an app out of standby. Battery-optimization exemptions are partial, not a universal delivery guarantee. [D]

For optional user-started live monitoring, a foreground service requires a visible notification and a valid service type/permissions; **target API 31+** generally forbids starting it from the background except documented exemptions. **Target API 34+** requires the applicable declared FGS type and type permission. Battery diagnostics is not the fitness/body-sensor `health` type. `specialUse` may be relevant but requires an explained subtype and Play review; acceptance cannot be promised. [F] [F14] [FT]

Do not disguise an unlimited logger as `dataSync`: **target API 35+** data-sync FGS cannot launch from `BOOT_COMPLETED`, and background data-sync/media-processing FGS have a **six-hour budget per type in a 24-hour period**, reset by the documented user-foreground interaction. Boot is not a universal exemption to service-type restrictions. [FT] [TIME]

For reboot recovery, use WorkManager's rescheduling and resume imports after user unlock. A custom boot receiver needs `RECEIVE_BOOT_COMPLETED`; pre-unlock work needs Direct Boot awareness/device-protected storage, while UsageStats remains unavailable until unlock. Background-restricted apps, force-stop, user stopping the app, and OEM behavior can create gaps; do not promise uninterrupted capture or silent automatic recovery in all cases. [WM] [DB] [I] [FORCE] [STOP]

Distinguish **Force Stop** from Android 13+'s foreground-service Task Manager **Stop**: Force Stop keeps the package stopped until user interaction, and Android 15 also cancels pending intents. Task Manager Stop removes the running app without a callback, but scheduled jobs and alarms remain scheduled. Both interrupt a live session; their recovery semantics differ. [FORCE] [STOP]

Exact alarms are intended for precise user-facing alarms/notifications, not recurring telemetry. Idle-allowed alarms are rate-limited (official Doze guidance: no more often than once per nine minutes per app). Replacing WorkManager with a chain of exact alarms is not an honest reliability solution for this monitor. [WM] [D]

## 6. Minimal viable measurement and honest presentation

The following are recommendations derived from the contracts above, rather than additional platform guarantees:

1. Persist timestamped whole-device samples locally: UTC time, elapsed realtime/boot segment, level/scale, charge/plug state, available charge/current/energy fields, temperature/voltage, and acquisition source. Preserve unavailable values and sampling gaps.
2. Capture the current sticky state on app/worker start; subscribe while running. Use periodic WorkManager capture with a user-visible, **best-effort** cadence; optionally offer a deliberate live-monitoring session with measured monitor overhead.
3. With explicit Usage Access, import activity/visible/foreground-service events frequently enough to outrun short system retention. Retain deduplicated local intervals and coverage status. Without that grant, the battery-history product still works.
4. Label whole-device discharge as “battery percentage points/hour” or “net battery mAh lost,” with window length and coverage. Keep charging windows separate. Label app metrics “foreground activity time,” “visible time,” “foreground-service time,” and optional “network traffic by Android state.”
5. For association views, use wording such as “device lost 4 percentage points while App X was visible.” If allocating drain proportionally to activity duration, label it **a heuristic estimate**, expose the allocation model/unattributed share, and never call it measured app battery use.
6. Compare like conditions over time: discharge-only windows, duration, screen state, temperature, charging, network context and coverage. An app update followed by higher drain is correlation; simultaneous apps/system work, radio conditions and battery aging remain confounders.

## Remaining uncertainties and decision

- **Needs device testing:** actual supported properties/rails, current sign compliance and freshness, gauge noise, monitor overhead, OEM background survival, denied/revoked Usage Access, multi-window/service overlap, reboot and first-unlock recovery.
- **Needs SDK confirmation:** flagged state-of-health exposure and API 37.1/37.2 release/permission behavior; current catalogs contain inconsistent freshness signals. No conclusion here depends on those newer entries.
- **Outside this API research:** whether a particular free/ad-free app implements these measurements accurately, its real retention/export behavior, or what extra privileges it requests. A listing claim alone cannot establish attribution quality.
- **Decision:** proceed only with a battery-history/activity-correlation promise under ordinary permissions. If measured/system-estimated per-app foreground/background energy is mandatory, use a separately disclosed privileged/system or developer-assisted diagnostic workflow and validate it; the public API baseline does not meet that requirement. [BS] [BSM]

## Sources

[B]: https://developer.android.com/reference/android/os/BatteryManager
[I]: https://developer.android.com/reference/android/content/Intent#ACTION_BATTERY_CHANGED
[BG]: https://developer.android.com/training/monitoring-device-state/battery-monitoring
[REL]: https://source.android.com/docs/setup/reference/build-numbers
[REV]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d
[17]: https://developer.android.com/about/versions/17
[SDK]: https://developer.android.com/tools/releases/platforms
[API]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/api/current.txt#34833
[BM]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/java/android/os/BatteryManager.java#421
[BROADCAST]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/BatteryService.java#1020
[REG]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/BatteryService.java#1827
[HAL]: https://android.googlesource.com/platform/hardware/interfaces/+/refs/tags/android-16.0.0_r1/health/aidl/android/hardware/health/HealthInfo.aidl#80
[U]: https://developer.android.com/reference/android/app/usage/UsageStatsManager
[E]: https://developer.android.com/reference/android/app/usage/UsageEvents.Event
[US]: https://developer.android.com/reference/android/app/usage/UsageStats
[U17]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/java/android/app/usage/UsageStatsManager.java#588
[RET]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/usage/java/com/android/server/usage/UsageStatsDatabase.java#928
[DB]: https://developer.android.com/privacy-and-security/direct-boot
[N]: https://developer.android.com/reference/android/app/usage/NetworkStatsManager
[NB]: https://developer.android.com/reference/android/app/usage/NetworkStats.Bucket
[NSTATE]: https://android.googlesource.com/platform/packages/modules/Connectivity/+/refs/tags/android-17.0.0_r1/service-t/src/com/android/server/net/NetworkStatsService.java#2075
[NP]: https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/core/java/android/net/NetworkPolicyManager.java#875
[H]: https://developer.android.com/reference/android/os/health/SystemHealthManager
[P]: https://developer.android.com/reference/android/os/PowerMonitor
[PR]: https://developer.android.com/reference/android/os/PowerMonitorReadings
[H17]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/java/android/os/health/SystemHealthManager.java#486
[PS]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/powerstats/PowerStatsService.java#711
[PERM]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/res/AndroidManifest.xml#7858
[BS]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/am/BatteryStatsService.java#954
[BSM]: https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/java/android/os/BatteryStatsManager.java#169
[W]: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#schedule_periodic_work
[WM]: https://developer.android.com/topic/libraries/architecture/workmanager
[D]: https://developer.android.com/training/monitoring-device-state/doze-standby
[F]: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
[F14]: https://developer.android.com/about/versions/14/changes/fgs-types-required
[FT]: https://developer.android.com/develop/background-work/services/fgs/service-types
[TIME]: https://developer.android.com/develop/background-work/services/fgs/timeout
[FORCE]: https://developer.android.com/about/versions/15/behavior-changes-all#enhanced-stop-states
[STOP]: https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping
