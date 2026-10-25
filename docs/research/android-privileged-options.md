# Android privileged battery attribution options

Research date: **2026-10-25**. Scope: AOSP BatteryStats/BatteryUsageStats, ADB, root, Shizuku, Historian, and distribution constraints. No physical-device testing, tooling installation, or implementation was performed.

## Conclusion

**Long-term per-app foreground/background trends are plausible as an optional, device-validated privileged feature. They are not a capability unlocked by an ordinary runtime permission.** AOSP exposes historical per-UID battery attribution, including process-state breakdowns where supported, through restricted interfaces. An application must preserve its own observations to provide dependable long-term history; OS storage, attribution coverage, and collection continuity vary. [permission15] [query15] [provider15] [store15]

Start with **read-only ADB exports on the actual phone**, especially `dumpsys batterystats --usage` where advertised by its help. If useful state-specific values are present, evaluate a Shizuku shell collector for ongoing local snapshots. Retain an ordinary-app mode for battery history and activity context, with privileged attribution explicitly unavailable when unsupported. Root is an optional advanced tier; requiring it is a poor default for a consumer utility. These are recommendations, not tested product capabilities. [usage15] [shizuku]

Battery Historian is an Apache-2.0-licensed diagnostic option for importing and comparing bugreports, rather than an established continuously collecting phone app. Its Google repository is **archived** in the API response checked for this report. Modern Android/OEM compatibility remains unverified. Nothing in this research establishes that an existing free, ad-free Android app already satisfies the complete requirement. [historian] [historian-license] [historian-api]

## Pixel 9 Pro on Android 17: targeted follow-up

The user identified this as the intended handset after the initial Android 15/16
source study. Willingness to use ADB/Shizuku has not been established. Begin with
ordinary-access history; privileged routes below are optional feasibility checks.
No command was run on the user's phone and no Pixel-specific capability is certified.

A follow-up inspected Android 17 framework revision
[`94b4c163b7dfe5ce3607f7bb8456f9573f7de57d`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d):

- [`BATTERY_STATS` and `DUMP` retain `signature|privileged|development`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/res/AndroidManifest.xml#7860).
  This is not a normal runtime permission flow.
- [`getBatteryUsageStats()` still enforces `BATTERY_STATS`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/am/BatteryStatsService.java#955).
- [`--usage` remains in dump help, with `--accumulated`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/am/BatteryStatsService.java#2996).
  Its [query builder requests process, screen, and power-state data](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/am/BatteryStatsService.java#3062).
  These are separate dimensions; screen state is not an app foreground/background classification.
- [`BatteryUsageStatsQuery` remains hidden, and `includePowerModels()` remains a deprecated no-op](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/java/android/os/BatteryUsageStatsQuery.java#370).

This confirms that the examined access boundary and promising dump route still
exist in Android 17 source. Actual granted permissions, returned state fields,
retention, startup reliability, and Shizuku support require validation on the
Pixel's exact build. Android 15/16-specific implementation details below are
historical evidence, not automatically asserted as unchanged on Android 17.

## Evidence boundaries and reproducibility

- GitHub repository search found Google's Battery Historian and RikkaApps' Shizuku repositories. Required `gh search code` attempts for `BATTERY_STATS` and `getBatteryUsageStats` in the AOSP mirror returned no matches; direct official-mirror source inspection supplied the evidence instead.
- Source references below pin **Android 15 `android-15.0.0_r1`** to `4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3` and **Android 16 `android-16.0.0_r1`** to `99b01a65cc4c104933788b3143285ab6bae65827`. These are inspected baselines, not a claim that they are the newest release on 2026-10-25. [aosp15] [aosp16]
- Shizuku API and Historian README links also use the retrieved revision. Live vendor documentation and GitHub metadata can change; catalog timestamps, apparent recency, and README claims do not prove installed-device support or app-store availability.
- **Verified** below means documentation/source/metadata was inspected. **Inference/recommendation** means engineering judgment. **Unknown** means no runtime evidence; in particular there is no tested handset, build fingerprint, permission grant, Binder invocation, or successful report parse.

## What the operating system actually records

1. **BatteryStats is OS accounting, not a per-package electrical meter.** AOSP describes tracking component states and durations, associating resource use with applications, and combining that accounting with device power-profile values. Shared-resource consumption may be allocated among applications. Its persisted accounting can survive reboots. Modern source also uses energy-consumer data, so the older timing/profile explanation is not exhaustive for every component/build. [power-doc] [cpu15] [cpu16]
2. **BatteryUsageStats produces attributed per-subsystem/per-UID consumption.** The service enforces `BATTERY_STATS`; query and consumer classes are hidden (`@hide`) in the inspected source. These are not ordinary stable public SDK APIs just because their Java declarations say `public`. [service15] [query15] [consumer15]
3. Values called consumed “power” are generally **mAh, a quantity of charge**, not instantaneous watts or directly measured app energy. Android 15 distinguishes power-profile estimates from PowerStats-HAL energy-consumption-based attribution. Hardware-supported aggregate readings still do not establish a dedicated meter for each app: Android 16's CPU processor scales modeled brackets to measured consumption and allocates usage. Label results **OS-attributed battery use/estimates**, retaining provenance when available. [consumer15] [cpu16]
4. Device-wide charge counter, current, and remaining-energy readings are a different signal. `BatteryManager` defines their units and unsupported-value sentinels; support is hardware dependent. They cannot by themselves identify the app responsible for a discharge interval. Do not apportion device-wide drain by foreground duration and present that as measured per-app drain. The latter warning is an inference from the scope of these readings. [battery-api]
5. **Process-state energy and process-state duration are separate outputs.** A duration in background does not imply a corresponding measured charge, and missing energy attribution must not be replaced by that duration. Querying process-state data explicitly may still return a result without it; inspect `isProcessStateDataIncluded()`. Android 15's provider conditions inclusion on actual availability. [query15] [provider15] [uid15]
6. AOSP distinguishes **foreground, background, foreground service, cached, and unspecified** states. Its mapping puts both top and other foreground UID states into “foreground,” and top-sleeping into “background.” This differs from a universal “screen visibly showing this package” definition. A foreground service may run with no visible activity. Keep the states separate in storage; any two-way UI grouping must state exactly where foreground services and cached use go. [states15]
7. Component coverage is incomplete. Android 15 lists CPU, mobile radio, Wi-Fi, Bluetooth, audio, video, flashlight, camera, and GNSS as supporting process-state keys; this is a schema capability, not a promise of populated values. CPU source has different modeled and energy-consumer calculations. Display and other unsplit components mean a foreground/background subtotal need not equal total UID attribution. Preserve an **unattributed-to-state residual**, subject to rounding, rather than silently forcing equality. [consumer15] [cpu15]
8. **UID is not package identity.** `getPackagesForUid()` can return multiple packages or none. Legacy shared UIDs remain relevant despite `sharedUserId` being deprecated since API 29. A “package with highest drain” label is not an independently measured split of that UID's consumption. Report shared-UID groups rather than duplicating the same charge against every package. [packages16] [shared-doc] [cpu15]
9. Capture full UID/user/profile, package set and versions, and mapping validity at collection time; keep unknown/system entries. Do not assign old history using only today's installed-package list. AOSP also maps short-lived isolated UIDs back to owner UIDs, so reimplementing attribution from process lists can misidentify work. Handling reinstall/UID reuse and multi-user identity correctly remains a POC requirement. [packages16] [resolver16]

## Access routes and their limits

### Ordinary application and ADB development grant

- `BATTERY_STATS` is **`signature|privileged|development`** in both inspected Android 15 and 16 manifests. It is not a dangerous permission offered through `requestPermissions()`. Usage Access (`PACKAGE_USAGE_STATS`) is a distinct permission/app-op path; enabling Usage Access does not itself grant battery attribution. [permission15] [permission16]
- The **development** flag matters: Android 15's permission-manager implementation accepts runtime **or development** permissions for administrative grants and requires the package to have requested the permission. Thus `adb shell pm grant <package> android.permission.BATTERY_STATS` is a justified **device test**, not categorically impossible merely because the permission also has `signature|privileged`. It is not a general facility to grant arbitrary signature-only permissions. User restrictions, policy-fixed state, manifest declaration, and OEM changes still matter. [grant15]
- Even a successful grant proves only a permission check. Hidden-API access, interface compatibility, endpoint permissions, required app-ops, and field availability remain separate checks. Android restricts non-SDK interfaces beginning with Android 9, and applicability changes with release/target SDK. Do not promise that a one-line grant enables every dashboard field. [non-sdk] [service15] [dump-checks]
- A system/OEM-integrated build is a separate route: privileged apps require the appropriate system-image placement and permission allowlisting under the documented rules. Sideloading an ordinary APK does not make it a privileged system app. [allowlist]

### ADB shell, dumpsys, and bugreports

- The AOSP Shell manifest requests `BATTERY_STATS`. `BatteryStatsService.dump()` separately calls `checkDumpAndUsageStatsPermission()`: the utility checks `DUMP`, then usage access, with a usage-check exemption for shell/root/system identities. **Running `dumpsys` inside a normal app process does not confer shell identity.** `BATTERY_STATS` alone is not proof that this dump route is authorized. [shell15] [service-dump15] [dump-checks]
- `adb shell dumpsys batterystats` exposes history, device statistics, and **approximate** UID/component consumption. `-c` selects current check-in-format data; `--proto` and `--history` are available in the inspected implementation. These debugging formats are versioned implementation details: archive raw output and test parsers against each supported build. [dumpsys-doc] [dump-help15]
- `adb shell dumpsys batterystats --usage` is particularly promising: Android 15 source requests fresh BatteryUsageStats with process-state data and model metadata. It can output text or proto. Android 16 still requests state data, with changes described below. Check the device's `-h` first; existence in AOSP is not evidence that an OEM build emits all fields. [usage15] [usage16]
- `adb bugreport <destination>` captures a broader diagnostic report; developer options can also produce reports. This supports manual import and validation, not an automatically available third-party-app API. Treat full reports as sensitive: official docs say they contain system-service output, error logs, and stack traces, and package/usage data itself is sensitive under Play policy. Prefer local processing and minimal retained fields. [bugreport-doc] [privacy]
- Do not use `--reset` or `--reset-all` in routine collection. They clear current statistics/history, and `--reset-all` also resets the PowerStats store in inspected Android 15 source. **`--checkin` is not equivalent to `-c`:** its help explicitly says it writes and clears the old completed statistics when present. Preserve source windows and avoid consuming historical evidence accidentally. [dump-help15] [service-reset15]

### Shizuku and root

- Shizuku's developer API runs authorized Java/JNI work with **shell UID 2000 or root UID 0**, depending on how the service started. Binder forwarding and a privileged UserService are supported mechanisms. This delegates the backend identity's capabilities; it does not grant the ordinary app every platform permission. Request Shizuku authorization and verify backend identity before probing battery access. [shizuku]
- With an ADB backend, abilities are bounded by Shell's permissions, Linux capabilities, and SELinux. The project's example explicitly says shell cannot read other apps' `/data/user/0/<package>` files. Shizuku is not root and does not manufacture missing OS energy data. Root is a broader identity, but still needs compatible interfaces/data and an applicable security context. [shizuku]
- Shizuku documents that its UserService process has no non-SDK restrictions, while ordinary-process remote Binder access may encounter them. A UserService is not a normal application process; some `Context` APIs do not work there. This suggests isolating version-specific collection behind a narrow interface, not assuming SDK-style application code runs unchanged. This architecture is a recommendation, not an implemented/tested design. [shizuku]
- On unrooted devices, Shizuku must be restarted after **each reboot**. Android 11+ wireless debugging allows on-device startup; older devices require a computer for the documented ADB method. Permission authorization surviving is different from the backend actually running. The setup guide documents OEM restrictions and unexpected stops; its troubleshooting content is old and does not certify modern OEM behavior. [shizuku] [shizuku-setup]
- Root/Sui can improve startup integration but is not a requirement for a first POC. Sui is documented as a Magisk module. Rooting availability, boot startup, SELinux behavior, and compatibility with the particular phone remain unknown. Do not claim unattended post-reboot collection until it has been observed. [shizuku]

## History, resets, and change over time

- **OS history is finite.** Android 15 configures history buffers/files and a bounded PowerStats store that removes older spans; incompatible stored formats can also be removed. Its BatteryStats reader erases incompatible parcel versions or resets on read corruption. Surviving a normal reboot is not a promise of indefinite retention or surviving an OS update intact. [retention15] [store15] [read15]
- Reset behavior is not simply “every reboot” or “only at exactly 100%.” Android 15 source supports configurable resets on unplug after a high level/significant charge, cumulative high discharge, and extended plugged-in time. Energy-consumer configuration changes can reset accounting. Record the returned statistics start/end/session boundaries and detect discontinuities instead of hard-coding a single charge threshold. [reset15] [energy-reset15]
- `aggregateSnapshots(from, to)` is not proof of arbitrary hourly history. Android 15 selects stored sessions by their **end timestamps** and sums eligible whole snapshots. Source explicitly notes ambiguous wall-clock changes; snapshots with incompatible custom components or missing requested process-state data may be skipped. Show the **actual covered window**, not merely the requested dates. [aggregate15]
- **Important Android 16 additions:** the hidden query supports monotonic time ranges, and `dumpsys batterystats` advertises `--accumulated` when supported, described as continuously accumulated since setup or `reset-all`. Support is conditional on four feature flags. The provider has a separate accumulated-data path; time-range requests are incompatible with legacy PowerCalculators and can exclude that path. Neither a version number nor successful parsing proves complete component/time-range coverage. [query16] [accumulation16] [provider16]
- **Important Android 16 removal:** `includePowerModels()` is deprecated and does nothing; `getPowerModel()` is deprecated and returns `POWER_MODEL_UNDEFINED`. Therefore retain model metadata when actually supplied on older builds, but use **unknown/mixed OS attribution** when no reliable provenance is exposed. Never interpret “undefined” as measured, or infer physical accuracy from decimal precision. [query16] [consumer16]
- Recommended persistence: save timestamped raw snapshots plus normalized UID/component/state totals, units, schema/build, backend identity, package/version mapping, statistics boundaries, inclusion/coverage flags, and collection gaps in a local store. This is the proposed way to provide month-scale trends rather than rely on finite OS retention. [store15] [provider15]
- Difference cumulative values only when source window/identity/schema are compatible. A reset, missing UID, model/schema change, or negative revision starts a new segment or produces an explicit unknown interval; do not silently clamp to zero. A missed snapshot spanning an OS reset cannot be reconstructed exactly unless matching stored sessions are recovered. These are analysis recommendations based on the reset and aggregation behavior above. [reset15] [aggregate15]
- Preserve charge sessions and charging/screen/network context; compare normalized mAh/hour or charge per foreground/background time with clear denominators and coverage. Displaying a UID's share of attributed app charge as if it were its share of the phone's battery-level drop is misleading. This is a reporting recommendation, not a verified statistical estimator.

## Scheduling and distribution concerns

- Continuous local collection must handle Doze, force-stop, reboot, debugging loss, backend death, and OEM process management. WorkManager periodic work has a minimum interval and may be delayed by constraints/system optimization; it is not an exact sampling clock. Begin with a modest scheduled interval and record actual timestamps/gaps rather than promise minute-accurate coverage. [workmanager] [doze] [shizuku-setup]
- A persistent foreground service is not automatically a valid workaround. Android 15+ applies a six-hours-per-24-hours background limit to **dataSync and mediaProcessing** foreground services for apps targeting Android 15+, with separate budgets. That is a type-specific rule, not a claim that every FGS has a six-hour cap. The appropriate service type and permitted use must be established before design/distribution. [fgs]
- Android 11+ package visibility filtering can affect ordinary-app package discovery. Play treats app inventory as sensitive; `QUERY_ALL_PACKAGES` requires a justified core purpose, a declaration, and compliance with its restricted-use policy. Battery-monitor eligibility is **not established** by this report. A shell collector does not by itself settle the distribution/privacy question. [package-doc] [play-packages]
- Play's User Data policy covers app inventory and sensitive usage data; provide accurate disclosure, consent when required, secure handling, and a privacy policy. A local, ad-free approach fits the user's preference but is not itself evidence of policy approval. [privacy]
- Play prohibits unauthorized device/API access, security exploits, and facilitating circumvention of security protections. The inspected policy does **not establish a categorical ban or approval for every Shizuku/root battery app**. Review the concrete APK, hidden-API strategy, setup instructions, collected data, and current policy before Play submission; user ADB/root consent alone does not decide acceptance. Independent distribution avoids Play review, not Android's technical constraints or privacy duties. [play-abuse] [non-sdk]

## Capability tiers recommended for the product

- **Tier 0 — ordinary app:** persist available device battery signals and separate activity context; advertise no cross-app energy attribution without another verified route. Device property support remains conditional. [battery-api]
- **Tier 1 — user-imported ADB exports:** retain read-only dumps/bugreports and compare actual source windows; easiest first validation route, with gaps between exports. [dumpsys-doc] [bugreport-doc]
- **Tier 2 — administrative grant:** a declared development `BATTERY_STATS` grant plus a proven compatible endpoint may support direct collection. Treat grant, invocation, and populated fields as distinct milestones; do not advertise universal one-command enablement. [grant15] [service15]
- **Tier 3 — Shizuku shell:** optional automated privileged snapshots once actual UID/component/state coverage is proven. Expose backend stopped/restart-needed status and unsupported-build states. [shizuku]
- **Tier 4 — root/OEM integration:** potentially broader access/startup control, with the same attribution limits and version tests. OEM privileged integration is distinct from installing a root-capable consumer APK. [shizuku] [allowlist]

## Concrete device POC acceptance checks

These are proposed tests, **not executed**. Do not change/reset battery statistics until existing evidence is exported and the device owner explicitly chooses a controlled reset experiment.

1. Record Android/API level, target SDK, OEM/build fingerprint, user/profile, debugging mode, and collector/backend version. Save `adb shell id` and `adb shell dumpsys batterystats -h`; do not infer phone behavior from AOSP alone. **Pass:** artifact identifies the exact environment and supported options.
2. Export baseline `dumpsys batterystats`, `-c`, `--history`, and `--usage` where supported, plus one private bugreport. Use `--usage --proto` only if its option ordering is accepted on that device. **Pass:** permissions succeed, raw artifacts are retained, and parser records source timestamps/units/UIDs without silently dropping unknown fields. [dump-help15] [usage15]
3. If considering a direct app route, verify declared `BATTERY_STATS`, capture the administrative grant result, then separately exercise the intended data endpoint. **Pass:** actual data invocation succeeds with useful fields. A successful `pm grant`, package permission listing, or Shizuku permission dialog alone fails this gate. [grant15] [service15]
4. Inspect process-state inclusion and component keys. Run repeated controlled foreground CPU/activity, screen-off permitted background work, foreground-service activity, and idle/cached phases using known workloads. **Pass:** supported state attribution changes in the expected direction; durations and charge remain separate; unsupported/zero/unknown distinctions and foreground-service grouping are visible. This tests usefulness, not laboratory accuracy. [query15] [states15]
5. Resolve full UIDs against package sets, including a system/shared-UID group and another profile where available. Test uninstall/reinstall or an equivalent identity-change fixture. **Pass:** no double-counted shared-UID totals, no retroactive relabeling, and unknown attribution is preserved. [packages16] [resolver16]
6. Compare consecutive snapshots within one source session, then across charge/unplug, reboot, clock adjustment, and an OS-controlled reset observed naturally. **Pass:** compatible intervals can be differenced; new sessions/gaps are segmented; retained history survives collector restart. Never force a reset just to make a graph appear continuous. [reset15] [aggregate15]
7. On Android 16, test `--accumulated` **only if advertised**, then verify real support, baseline identity, retention, process-state/component coverage, and behavior across reboot/reset. Probe monotonic-range collection separately. **Pass:** unsupported paths degrade explicitly, and legacy model “undefined” is handled correctly. [accumulation16] [provider16] [consumer16]
8. Run 24–72 hours including Doze, lost debugging connection, Shizuku death/restart, device reboot, and ordinary app process termination. **Pass:** actual scheduling coverage and missing intervals are recorded, previously persisted history remains, and resumption status matches reality. Measure collector overhead against a comparable no-collector period. [doze] [shizuku-setup]
9. Compare exported OS totals/settings over the **same covered window**, while allowing system components/residuals and rounding. **Pass:** product values reconcile with their actual source; no claim of equality with external measured energy. If Historian is considered, separately require successful parsing of a current private report and meaningful A/B comparison. [dumpsys-doc] [historian]
10. Define support only for combinations passing the above: device/build, route, fields, coverage, and recovery. Validate at least one non-Pixel OEM and every intended Android major version before making broad support claims. **Unknown today:** all actual grant/invocation, retention, accuracy, overhead, OEM, Play-acceptance, and existing-app suitability outcomes.

## Primary sources

Reference names ending `15`/`16` distinguish the inspected source baseline. Source links are revision permalinks; documentation and repository metadata are live observations as of the research date.

[aosp15]: https://github.com/aosp-mirror/platform_frameworks_base/commit/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3
[aosp16]: https://github.com/aosp-mirror/platform_frameworks_base/commit/99b01a65cc4c104933788b3143285ab6bae65827
[permission15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/core/res/AndroidManifest.xml#L6540-L6611
[permission16]: https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/core/res/AndroidManifest.xml#L6990-L7057
[shell15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/packages/Shell/AndroidManifest.xml#L168-L172
[grant15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/pm/permission/PermissionManagerServiceImpl.java#L1343-L1470
[service15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/am/BatteryStatsService.java#L1053-L1075
[service-dump15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/am/BatteryStatsService.java#L2963-L2980
[dump-checks]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/core/java/com/android/internal/util/DumpUtils.java#L95-L178
[dump-help15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/am/BatteryStatsService.java#L2809-L2843
[service-reset15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/am/BatteryStatsService.java#L3028-L3043
[usage15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/am/BatteryStatsService.java#L2896-L2925
[query15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/core/java/android/os/BatteryUsageStatsQuery.java
[provider15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/power/stats/BatteryUsageStatsProvider.java#L181-L259
[consumer15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/core/java/android/os/BatteryConsumer.java
[uid15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/core/java/android/os/UidBatteryConsumer.java#L35-L125
[states15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/core/java/android/os/BatteryStats.java#L653-L702
[cpu15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/power/stats/CpuPowerCalculator.java#L142-L245
[cpu16]: https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/services/core/java/com/android/server/power/stats/processor/CpuPowerStatsProcessor.java#L394-L565
[battery-api]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/core/java/android/os/BatteryManager.java
[packages16]: https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/core/java/android/content/pm/PackageManager.java#L7325-L7341
[resolver16]: https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/services/core/java/com/android/server/power/stats/PowerStatsUidResolver.java#L28-L61
[retention15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/power/stats/BatteryStatsImpl.java#L15934-L15975
[store15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/power/stats/PowerStatsStore.java
[read15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/power/stats/BatteryStatsImpl.java#L16400-L16455
[reset15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/power/stats/BatteryStatsImpl.java#L14820-L14925
[energy-reset15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/power/stats/BatteryStatsImpl.java#L15820-L15852
[aggregate15]: https://github.com/aosp-mirror/platform_frameworks_base/blob/4e43ad12e1b211f15152e9c5b16b0fe88e6b93f3/services/core/java/com/android/server/power/stats/BatteryUsageStatsProvider.java#L337-L413
[query16]: https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/core/java/android/os/BatteryUsageStatsQuery.java#L297-L371
[accumulation16]: https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/services/core/java/com/android/server/am/BatteryStatsService.java#L619-L624
[usage16]: https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/services/core/java/com/android/server/am/BatteryStatsService.java#L3019-L3164
[provider16]: https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/services/core/java/com/android/server/power/stats/BatteryUsageStatsProvider.java#L274-L415
[consumer16]: https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/core/java/android/os/BatteryConsumer.java#L582-L604
[power-doc]: https://source.android.com/docs/core/power
[dumpsys-doc]: https://developer.android.com/tools/dumpsys#battery
[bugreport-doc]: https://developer.android.com/studio/debug/bug-report
[shared-doc]: https://developer.android.com/guide/topics/manifest/manifest-element#uid
[non-sdk]: https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces
[allowlist]: https://source.android.com/docs/core/permissions/perms-allowlist
[shizuku]: https://github.com/RikkaApps/Shizuku-API/blob/a27f6e4151ba7b39965ca47edb2bf0aeed7102e5/README.md
[shizuku-setup]: https://shizuku.rikka.app/guide/setup/
[historian]: https://github.com/google/battery-historian/blob/d2356ba4fd5f69a631fdf766b2f23494b50f6744/README.md
[historian-license]: https://github.com/google/battery-historian/blob/d2356ba4fd5f69a631fdf766b2f23494b50f6744/LICENSE
[historian-api]: https://api.github.com/repos/google/battery-historian
[workmanager]: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#schedule_periodic_work
[doze]: https://developer.android.com/training/monitoring-device-state/doze-standby
[fgs]: https://developer.android.com/develop/background-work/services/fgs/timeout
[package-doc]: https://developer.android.com/training/package-visibility
[play-packages]: https://support.google.com/googleplay/android-developer/answer/10158779?hl=en
[privacy]: https://support.google.com/googleplay/android-developer/answer/10144311?hl=en
[play-abuse]: https://support.google.com/googleplay/android-developer/answer/9888379?hl=en
