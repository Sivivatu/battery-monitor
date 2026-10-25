# Existing Android battery apps: evidence and gaps

Research date: **2026-10-25**. Target: **Pixel 9 Pro, Android 17**.
ADB/Shizuku willingness is unknown; the default recommendations require neither.
No APK, emulator, physical phone, purchase, or advertising behavior was tested.

## Conclusion

Start with **Pixel Settings → Battery → Battery usage**, plus **Battery Monitor by Tomasz Świstak** if persistent, free battery-event history and CSV export are useful. This combination provides a practical starting point, but does not establish retained per-app foreground/background **energy** comparisons. [P1] [M1] [M2]

**BatStats is the closest released open-source lead for retained per-app energy history** among the sources examined: its inspected collector stores hourly per-UID mAh deltas, but its schema/parser do not preserve a foreground/background energy split. Privileged collection is optional setup, not an assumed user preference. See the [source follow-up](batstats-source-check.md), including its release/source-version mismatch and the **`--checkin` collection
issue**: the inspected collector can consume saved completed Android statistics
and mix accounting windows. Preserve existing diagnostics and verify a corrected
current-stat collector before enabling or trusting privileged history. [B1] [B2]

**Wattdog is the closest source-level lead for the actual foreground/background energy split**: its parser, UI, and snapshot-difference code implement separate mAh fields. However, there is no published GitHub release, automatic snapshots last only two days and are removed across statistics resets, and Android/device coverage remains unverified here. It is an experimental evaluation candidate, not an immediate consumer recommendation. [W1] [W2] [W3] [W4] [W5]

None of these investigations verifies a ready-to-install, free, ad-free app satisfying **all three** requirements on this Pixel. That is an evidence limit, not a claim that no such app exists.

## What counts as meeting the requirement

- **History:** data retained across process death, charging cycles, reboots, and multiple days; a live graph or “since unplugged” counter alone is insufficient.
- **Per-app energy:** attributed charge/energy estimates, such as mAh; foreground minutes, wakeup counts, and wakelock duration are different quantities.
- **Foreground/background split:** separate attributed energy by app state. Whole-phone screen-on/off drain is not this split; a foreground service also deserves its own label.
- **Change over time:** comparable retained intervals or daily/session history, with counter resets handled. Raw cumulative totals cannot simply be subtracted across resets. Wattdog explicitly rejects such comparisons. [W4] [W5]
- **Measurement caveat:** Android's attribution can be modeled; an app showing mAh does not establish independently measured per-app electrical consumption. AccuBattery explicitly describes assigning the entire phone's simultaneous drain to the foreground app. [A2]

## Candidate comparison

“Not verified” below means the inspected primary evidence did not establish the feature or distribution; it is not proof of absence. Details and qualifications follow the table.

| Candidate | Retained history / comparisons | Per-app energy and foreground/background | Cost and ads, separately | Access / distribution |
|---|---|---|---|---|
| Pixel Settings | Recent built-in view; long-term export/archive not established | App usage supported; exact Android 17 UI/energy split untested | Included; no separate monitoring purchase | No added privileges; built in. [P1] [P2] |
| Battery Monitor, Świstak | Local event logs, filtering, CSV | Inspected log schema is phone-level; no per-app split | Free APK/F-Droid; no ad/billing dependency in inspected build configuration | Ordinary logging; root/Shizuku only for additional hardware data. [M1] [M2] [M3] [M4] |
| BatteryBot Pro | Battery-state logs, CSV | Phone-level history, not app attribution | Free on F-Droid; Play Pro observed **US$2.99**; publisher says no ads | Ordinary permissions; F-Droid/GitHub/Play. [T1] [T2] [T3] |
| BatStats | Persisted hourly per-UID mAh and sessions | Per-UID total deltas; no stored foreground/background energy split | Free GitHub release; limited manifest/dependency inspection found no ad SDK | Advanced data via root, Shizuku, or ADB grants; current F-Droid availability unverified. [B1] [B2] |
| Wattdog | Manual marks; automatic hourly snapshots, two-day retention | Separate foreground/background/foreground-service/cached mAh | Free GPL source; no network/ad/billing dependency found in inspected app files | Android 14+; Shizuku or ADB grants; no GitHub release. [W1] [W2] [W3] [W4] [W5] [W6] |
| DrainScope | SQLite hourly history, 24-hour chart, CSV | Weighted per-app mAh model; app-state **time**, not separate state-energy values | Free Apache-licensed release; ad behavior not independently audited | Shizuku for detailed stats; usage-access estimates otherwise; GitHub APK. [D1] [D2] [D3] |
| Battery Guru | Free history 30 days; app usage three days; longer history premium | Foreground app drain; publisher explicitly says screen-off total is **not split by app** | Free download **with ads**; premium subscription removes ads/unlocks retention | Usage access for app view; Play. [G1] |
| AccuBattery | Free historical details 24 hours; premium up to 150 session cards | Foreground attribution; background work can be charged to the wrong foreground app | Free download **with ads**; paid Pro removes ads/unlocks older details | Usage access; no root/ADB in documented core setup; Play. [A1] [A2] [A3] [A4] |
| GSam | Historical averages, charts, custom reference; durable daily per-app archive unverified | App drain plus “background CPU only” view, not full state-energy partition | Free edition labeled **contains ads**; Pro observed **US$2.49**, publisher says ad-free | Enhanced statistics require manual privileged setup; Play. [S1] [S2] [S3] |
| BetterBatteryStats | Stored reference snapshots and interval diagnosis | Wakelock/process diagnostics; requested persistent energy partition not established | Source free; current original-app price/ads/install channel unverified | Archived; historical ADB/root setup; old Play URL returned 404. [C1] [C2] [C3] [C4] |
| Wattz | Live battery indicator; retained comparisons not established | Whole-phone watts/current; not app attribution | Free, explicitly no ads; MIT | No root/ADB listed; F-Droid/GitHub; publisher withdrew Play distribution. [Z1] [Z2] |
| Battery Historian | Saved bugreports, timelines, A/B comparison of two reports | App diagnostics; timeline activity bars are not energy measurements | Free Apache-licensed source; no app purchase/ad tier | Desktop/server tool using ADB bugreports; archived and no longer maintained. [H1] [H2] |

## Evidence and compatibility details

### Pixel Settings: inspect first

Google documents **Battery → Battery usage → View by apps**, with app background-usage controls. AOSP Settings models contain foreground, foreground-service, background, and cached power fields. This verifies platform-side concepts, **not** which numeric breakdown the Pixel 9 Pro Android 17 screen actually displays. Check whether “background” in the UI means duration, a control, or attributed energy. No inspected official source establishes a user-exportable months-long archive. [P1] [P2]

### Battery Monitor and BatteryBot Pro: useful local logs

Battery Monitor is a continued/rebranded BatteryBot Pro fork. F-Droid lists **1.7.3**, added **2026-10-06**, Android 8+, GPL-3.0-or-later; GitHub release 1.7.3 is dated **2026-10-04**. Source HEAD examined was `ca4ec1084f42a737a82e98c89002704cd08d239a` (commit dated 2026-10-16). Its build targets API 37 and its listing mentions Android 16+ Live Updates, neither of which proves Android 17 runtime reliability. [M1] [M2] [M4]

Its stored records are status, charge, time, temperature, and voltage; the database supports age-based pruning. Check retention settings and export regularly. Root/Shizuku exposes extra battery hardware/service information, rather than establishing app-energy attribution. The inspected manifest has no Internet permission and app dependencies show no advertising/billing SDK; this is source evidence, not a complete binary audit. [M3] [M4]

BatteryBot Pro's F-Droid build is **12.0.0**, added **2021-12-07**, while its live Play listing says updated **2026-09-14**, and source HEAD is dated **2026-09-20**. Do not mistake the old F-Droid build for the current Play build, or a stale description claiming testing only through Android 8 for proof of Android 17 support. Source says GPL v3 **or later**, while F-Droid labels its packaged build GPL-3.0-only; preserve that distinction when considering reuse. [T1] [T2] [T3]

### BatStats: retained per-app totals, but no energy-state partition

The source check inspected revision `47b7ca46a1ad5fe2969a50c28142464860c584c0`: Room entities store hourly package/UID mAh, source mode, and sample count; the collector differences cumulative per-UID snapshots. Its parser handles UID totals rather than foreground/background power components. Shared/ambiguous UIDs remain grouped. The linked follow-up has exact entity, collector, parser, DAO, and manifest permalinks; this survey adopts that inspection without duplicating it. [B1] [B2]

The reported GitHub release is **6.2.6**, published **2026-09-14**; inspected source declares **6.3.6**. Thus source findings do not establish the released APK's exact behavior. Recent pushes, available code, and lack of an obvious ad SDK do not establish Pixel 9 Pro Android 17 compatibility or every distributed build's advertising behavior. License: GPL-3.0. [B1] [B2]

**Collection caveat:** the inspected collector repeatedly uses `--checkin`. Android 17 can return and delete saved completed statistics on that path, before later calls return current statistics. This can consume historical evidence and corrupt cross-window delta comparisons. Preserve diagnostics before any privileged trial, inspect the exact release, and require a non-destructive current-stat path (such as `-c`) plus window/reset checks before trusting hourly trends. The [source follow-up](batstats-source-check.md) links both implementations.

### Wattdog: closest split, substantial maturity and retention limits

Pinned revision `06f3979a19f95c4b91f3a3234ccb7547b404e45e` (2026-10-14) parses `fg`, `bg`, `fgs`, and `cached` mAh; the app renders those as separate rows and differences them between snapshots. **Missing state fields default to zero**, so a zero background value must not be treated as confirmed zero consumption without inspecting the phone's raw dump. [W2] [W3] [W5]

Automatic checks are off by default. Publisher documentation specifies hourly captures retained for two days and removed after statistics reset; manual marks from previous sessions cannot serve as current baselines. Source distinguishes automatic from manual snapshots when pruning, so the two-day policy should not be applied to every manually saved mark. This still falls short of demonstrated multi-week comparisons across charging cycles. [W1] [W4]

The repository was created **2026-10-12** and had no published GitHub releases when queried. README reports real fixtures from a Pixel on Android 17, and the source fixture name is `pixel9-api37-history.txt.gz`. This is **publisher-reported Android 17 evidence**, not this researcher's device test and not a confirmed Pixel 9 **Pro** end-to-end test. Most fixtures are described as synthetic. Source minSdk is 34; compile/target SDK is 37. [W1] [W6]

The documented access choices are a Shizuku user service or ADB grants of `DUMP` and `PACKAGE_USAGE_STATS`. The README says grants survive reboot but not reinstall; Shizuku can stop after reboot. These are app-author instructions, not verified behavior on the target phone. Shizuku's own documentation warns that shell privileges vary by Android version. [W1] [Q1]

### DrainScope: released, very new, model rather than app-state metering

GitHub **v1.0.0** was published **2026-10-21**, the repository's creation date; license Apache-2.0. README documents Android 7+ and Shizuku/usage-access alternatives, but no inspected claim establishes tested Android 17 support. No F-Droid listing was verified. [D1]

With Shizuku, its UI allocates measured whole-phone discharge using a weighted app score. Without Shizuku, the documented fallback assumes roughly **8 mAh per foreground minute**. This is not hardware-measured app energy. The UI includes foreground-service/background **durations**, and history stores phone snapshots and offender statistics, but not separate foreground/background mAh. The visible chart queries 24 hours; CSV exports all stored phone snapshots. [D1] [D2] [D3]

### Battery Guru and AccuBattery: easy installs, concrete missing pieces

Battery Guru's live publisher listing explicitly documents charge/discharge history, weekly summaries, PDF/CSV export, and optional encrypted backup. It labels its core readings as estimates where applicable. Free history is limited to 30 days and app usage to three days; subscription premium removes ads and extends these windows. The listing says updated **2026-10-24** and works on Android 7+, but does not establish tested Android 17 compatibility. Crucially: **“Drain with the screen off is shown as one total, not split by app.”** No publisher-authorized open-source repository, open-source license, or F-Droid build was verified. [G1]

AccuBattery's publisher explains that it assigns **all realized whole-phone drain while an app is foreground** to that app, including unrelated background processes. Nothing is considered foreground when the screen is off. Screen-on/off totals therefore do not give per-app background consumption. History details are clickable for 24 hours free; premium unlocks up to 150 historical cards and removes ads. Its Play listing was updated **2025-11-06**; no explicit Android 17 validation was found in the inspected publisher material. No publisher-authorized public source, open-source license, or F-Droid build was verified. [A1] [A2] [A3] [A4]

### GSam: closer established diagnostic alternative, with narrower split

The official guide documents **% Power (Background CPU Only)** as removing foreground CPU usage, plus “since screen off,” “since now,” charts, and lifetime averages. This is useful background diagnosis, but is not a documented complete foreground/background allocation of CPU, radio, screen, sensors, and other energy across retained days. Its custom reference resets on reboot or unplug. The guide is old (version 3.35), so current behavior needs checking. [S3]

Both Play listings say updated **2026-01-03**, with **3.47: bug fixes for Android 16**. That establishes an Android 16 maintenance claim, not Android 17 testing. Free is currently labeled “Contains ads”; the old guide describes a telemetry-versus-ads choice and says paid has no ads. Do not assume the old option yields an acceptable free/ad-free current build. Enhanced statistics use manual ADB setup without requiring root according to the guide; current Android 17 compatibility of that path is unknown. Public app source, an open-source license, and F-Droid distribution were not verified; the linked translation repository is not app source. [S1] [S2] [S3]

### BetterBatteryStats and Battery Historian: historical/reference tools

Original BetterBatteryStats is **archived**, with GitHub showing archival on **2026-03-15**. Default-branch commit `ccb0904…` is dated **2023-05-08** and titled **“Updated to android 14, does not work yet.”** Its README grants Apache-2.0 for code with name/artwork exclusions, and its reference database establishes stored interval baselines, not the requested historical app-state energy series. Its wiki documents old ADB grants and changing `hidden_api_policy`; this report does not recommend applying that global setting on the Pixel. [C1] [C2] [C3] [C4]

The original Play URL returned 404 in the queried US/English context. That does not prove global withdrawal, nor establish current prices, ads, or a safe free APK source. Current F-Droid availability was not verified. Do not treat successors/forks carrying similar names as automatically equivalent to the archived original. [C1] [C2]

Google's Battery Historian repository is archived, and current Android documentation explicitly warns that it is **no longer actively maintained**, suggesting system tracing, Macrobenchmark power metrics, or Power Profiler instead. README supports A/B comparison of two bugreports, app-specific metrics, and Android 5+ input; those old minimum-version claims are not Android 17 compatibility guarantees. [H1] [H2]

Its normal workflow uses USB debugging and `adb bugreport`, without rooting; optional kernel tracing is a separate root-dependent path. Saved reports provide manual retention, not unattended phone logging. Google cautions that timeline bars show activity, not how much energy a component used. Prefer local analysis for bugreports, which can contain sensitive diagnostics; no public upload service was tested or endorsed. [H1] [H2]

### Wattz and other open-source leads

Wattz is a live status-bar power/current indicator, explicitly free of ads and data collection, MIT-licensed. GitHub release **1.20** is dated **2025-08-06**; F-Droid added it **2025-08-10**, Android 9+. A **2026-10-08** publisher commit explicitly removes the Play link and states it is no longer available there; use F-Droid/GitHub. It supplies no documented app-attribution/history solution and no verified Android 17 device test. [Z1] [Z2]

- **Battery Archeologist:** GPL-3.0 project created 2026-10-23; inspected README calls it a “first working vertical slice,” with Shizuku ADB-mode capture, saved reports and app power predictions. It lists comparison as a next wave; no GitHub release was returned. This is an experimental Historian port, not a proven solution. [L1]
- **BattInsight:** GPL-3.0-only, no published release; README explicitly says not ready for daily use, although later sections describe partial session/wakelock history. That internal status inconsistency prevents treating roadmap features as implemented. It reports Android 16 emulator testing, not this Pixel on Android 17. [L2]
- **BatteryHub:** Apache-2.0, battery charts and running-app/memory information, with anonymous data uploads. Latest returned release **1.2.0 (2019-10-04)** and default-branch commit are old; no verified foreground/background app-energy feature or current Android 17 support. It is less suitable than the shortlist. [L3]

## Practical try-first order for the Pixel 9 Pro

1. **No privileged setup:** inspect Settings and install Battery Monitor from F-Droid if whole-phone logs/CSV meet enough of the need. Confirm retention and logging after an overnight idle and reboot. [P1] [M1]
2. **If paying is acceptable:** GSam Pro is a stronger background-diagnosis lead than foreground-only attribution apps, but test the free edition first for Android 17 behavior. AccuBattery Pro/Battery Guru premium remain appropriate for battery health/session history, not the missing app-background energy partition. [S1] [S2] [S3] [A2] [G1]
3. **Only if ADB/Shizuku is acceptable:** evaluate an official BatStats release for durable per-app total history. First preserve existing diagnostics and verify the release's source revision and collection mode. Resolve the inspected `--checkin` issue with a current-stat path and accounting-window/reset checks before trusting hourly history; do not assume current source equals its APK. [B1] [B2]
4. **Developer evaluation:** Wattdog is the most relevant source to test for actual app-state mAh. It currently needs building and privileged setup, and its short retention/reset limits must be accepted or changed. [W1] [W2] [W3] [W4] [W5] [W6]

Remaining acceptance checks: collect a foreground workload, background audio/download, and overnight idle; distinguish times from energy; verify nonzero/absent app-state fields against raw data; compare at least two days and charging cycles; test reboot/process death, UID grouping, missing snapshots, retention/export, and logger overhead. Android 17 compatibility is **not verified by this research** for any third-party candidate. No tooling was installed and no privileges were changed.

## Sources and reproducibility

Started with `gh search repos` and `gh search code`, then checked publisher Play listings, F-Droid pages, GitHub API metadata/releases, pinned source files, and official Android documentation. Code search returned no matches for some narrowly phrased queries; those misses were not used as absence evidence. Live catalog dates/claims may be stale, revised, or future-dated relative to cached material. Metadata confirms what was returned, not release quality or historical availability. Readme/test-fixture claims remain publisher claims; screenshots were not used as measurement evidence. Observed prices are US-store snapshots, not guaranteed regional prices.

- [P1] [Google Pixel: fix battery drain / inspect app usage](https://support.google.com/pixelphone/answer/6090599?hl=en).
- [P2] [AOSP Settings power/time fields, pinned revision](https://android.googlesource.com/platform/packages/apps/Settings/+/7c598253ff60f06f8e6fe046f18fd88e9daa72d3/src/com/android/settings/fuelgauge/batteryusage/BatteryDiffEntry.java#94).
- [M1] [Battery Monitor F-Droid install/listing](https://f-droid.org/en/packages/codes.swistak.batterymonitor/).
- [M2] [Battery Monitor README](https://github.com/tswistak/Battery-Monitor/blob/ca4ec1084f42a737a82e98c89002704cd08d239a/README.md); [release 1.7.3/APK](https://github.com/tswistak/Battery-Monitor/releases/tag/1.7.3).
- [M3] [Battery Monitor log schema/pruning](https://github.com/tswistak/Battery-Monitor/blob/ca4ec1084f42a737a82e98c89002704cd08d239a/app/src/main/kotlin/codes/swistak/batterymonitor/logs/LogDatabase.kt#L28); [advanced hardware snapshot](https://github.com/tswistak/Battery-Monitor/blob/ca4ec1084f42a737a82e98c89002704cd08d239a/app/src/main/kotlin/codes/swistak/batterymonitor/advancedstats/AdvancedBatterySnapshot.kt#L25).
- [M4] [Battery Monitor manifest](https://github.com/tswistak/Battery-Monitor/blob/ca4ec1084f42a737a82e98c89002704cd08d239a/app/src/main/AndroidManifest.xml); [SDK/dependencies](https://github.com/tswistak/Battery-Monitor/blob/ca4ec1084f42a737a82e98c89002704cd08d239a/app/build.gradle#L50).
- [T1] [BatteryBot Pro F-Droid install/listing](https://f-droid.org/en/packages/com.darshancomputing.BatteryIndicatorPro/).
- [T2] [BatteryBot Pro Play/install, price, ads, maintenance](https://play.google.com/store/apps/details?id=com.darshancomputing.BatteryIndicatorPro&hl=en&gl=US).
- [T3] [BatteryBot source/license](https://github.com/darshan-/Battery-Indicator-Pro/blob/d2a76123d16ab4f2fcc6a0a557ba8788bc576fc9/LICENSE#L25); [source commit](https://github.com/darshan-/Battery-Indicator-Pro/commit/d2a76123d16ab4f2fcc6a0a557ba8788bc576fc9).
- [B1] [BatStats pinned source](https://github.com/mlm-games/BatStats/tree/47b7ca46a1ad5fe2969a50c28142464860c584c0); [release 6.2.6/APK](https://github.com/mlm-games/BatStats/releases/tag/6.2.6).
- [B2] [Source inspection with entity/parser/collector/DAO evidence](batstats-source-check.md).
- [W1] [Wattdog README, access/retention/Android 17 fixture claims](https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/README.md); [release inventory](https://github.com/loganrosen/wattdog/releases).
- [W2] [Wattdog state-energy parser and missing-field defaults](https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/main/kotlin/io/github/loganrosen/wattdog/batterystats/parse/BatteryStatsParser.kt#L382).
- [W3] [Wattdog foreground/background mAh UI](https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/ui/screens/AppsScreen.kt#L103).
- [W4] [Wattdog stored snapshots, comparability, automatic pruning](https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/data/SnapshotStore.kt#L19).
- [W5] [Wattdog reset rejection and state-energy deltas](https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/main/kotlin/io/github/loganrosen/wattdog/batterystats/analysis/ReportDelta.kt#L25).
- [W6] [Wattdog SDK/dependency versions](https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/gradle/libs.versions.toml); [manifest](https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/AndroidManifest.xml); [app dependencies](https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/build.gradle.kts).
- [D1] [DrainScope README/model/access/license](https://github.com/santo1993/drainscope/blob/6f7628cdceaf1f0a6e5f891880ed32ffdeeff6a8/README.md); [v1.0.0/APK](https://github.com/santo1993/drainscope/releases/tag/v1.0.0).
- [D2] [DrainScope attribution UI, history and CSV](https://github.com/santo1993/drainscope/blob/6f7628cdceaf1f0a6e5f891880ed32ffdeeff6a8/app/src/main/java/app/drainscope/battery/MainActivity.java#L465).
- [D3] [DrainScope persistent schema](https://github.com/santo1993/drainscope/blob/6f7628cdceaf1f0a6e5f891880ed32ffdeeff6a8/app/src/main/java/app/drainscope/battery/HistoryStore.java#L17).
- [G1] [Battery Guru publisher listing/install](https://play.google.com/store/apps/details?id=com.paget96.batteryguru&hl=en&gl=US).
- [A1] [AccuBattery publisher listing/install](https://play.google.com/store/apps/details?id=com.digibites.accubattery&hl=en&gl=US).
- [A2] [AccuBattery publisher: discharge methodology/foreground attribution](https://accubattery.zendesk.com/hc/en-us/articles/210226125-Tab-2-discharging-screen).
- [A3] [AccuBattery publisher: history and 150-session limit](https://accubattery.zendesk.com/hc/en-us/articles/210226445-Tab-4-charge-history-screen).
- [A4] [AccuBattery publisher: premium/ad removal](https://accubattery.zendesk.com/hc/en-us/articles/209507129-What-do-I-get-with-AccuBattery-premium); [permissions](https://accubattery.zendesk.com/hc/en-us/articles/209748869-About-permissions).
- [S1] [GSam free Play/install](https://play.google.com/store/apps/details?id=com.gsamlabs.bbm&hl=en&gl=US).
- [S2] [GSam Pro Play/install](https://play.google.com/store/apps/details?id=com.gsamlabs.bbm.pro&hl=en&gl=US).
- [S3] [GSam publisher user guide: background CPU, references, enhanced access, ads](https://badassbatterymonitor.blogspot.com/2011/11/badass-battery-monitor-users-guide.html).
- [C1] [Original BetterBatteryStats archive/README/license](https://github.com/asksven/BetterBatteryStats); [pinned Android 14 failure commit](https://github.com/asksven/BetterBatteryStats/commit/ccb0904791ab20e35a187dbd2a4cf53643dcba19).
- [C2] [Original BetterBatteryStats Play URL, returned 404](https://play.google.com/store/apps/details?id=com.asksven.betterbatterystats&hl=en&gl=US).
- [C3] [Original BetterBatteryStats historical permission instructions](https://github.com/asksven/BetterBatteryStats/wiki).
- [C4] [Original BetterBatteryStats reference database](https://github.com/asksven/BetterBatteryStats/blob/ccb0904791ab20e35a187dbd2a4cf53643dcba19/app/src/main/java/com/asksven/betterbatterystats/data/ReferenceDBHelper.java#L46).
- [Z1] [Wattz F-Droid install/listing](https://f-droid.org/en/packages/dubrowgn.wattz/); [GitHub release/APK](https://github.com/dubrowgn/wattz/releases/tag/v1.20).
- [Z2] [Wattz README at Play-withdrawal revision](https://github.com/dubrowgn/wattz/blob/78681ea350f08a511b563ae6a636d456d8df8f0e/readme.md#L6).
- [H1] [Android Developers: Historian maintenance warning, setup, timeline limits](https://developer.android.com/topic/performance/power/setup-battery-historian) (page returned “Last updated 2026-05-19”).
- [H2] [Archived Battery Historian repository](https://github.com/google/battery-historian); [README/A-B comparison/license/ADB and optional root tracing](https://github.com/google/battery-historian/blob/d2356ba4fd5f69a631fdf766b2f23494b50f6744/README.md).
- [Q1] [Shizuku official source README: root/ADB distinction and varying permissions](https://github.com/RikkaApps/Shizuku#how-does-shizuku-work). Official setup website returned 403 in this environment; no setup was attempted.
- [L1] [Battery Archeologist README/status, pinned](https://github.com/leekleak/battery-archeologist/blob/2ce3a12131d1b0a6b1352b82dd7672118b331e96/README.md).
- [L2] [BattInsight README/status, pinned](https://github.com/rmpsdroid/BattInsight/blob/efbec352501bb6a714d8a7961e0fd05955a7510b/README.md).
- [L3] [BatteryHub README/history/uploads/license](https://github.com/greenhub-project/batteryhub/blob/ed2fdf83b5d795956302b4f261a0e29ac2240c67/README.md); [release 1.2.0](https://github.com/greenhub-project/batteryhub/releases/tag/v1.2.0).

[P1]: https://support.google.com/pixelphone/answer/6090599?hl=en
[P2]: https://android.googlesource.com/platform/packages/apps/Settings/+/7c598253ff60f06f8e6fe046f18fd88e9daa72d3/src/com/android/settings/fuelgauge/batteryusage/BatteryDiffEntry.java#94
[M1]: https://f-droid.org/en/packages/codes.swistak.batterymonitor/
[M2]: https://github.com/tswistak/Battery-Monitor/blob/ca4ec1084f42a737a82e98c89002704cd08d239a/README.md
[M3]: https://github.com/tswistak/Battery-Monitor/blob/ca4ec1084f42a737a82e98c89002704cd08d239a/app/src/main/kotlin/codes/swistak/batterymonitor/logs/LogDatabase.kt#L28
[M4]: https://github.com/tswistak/Battery-Monitor/blob/ca4ec1084f42a737a82e98c89002704cd08d239a/app/src/main/AndroidManifest.xml
[T1]: https://f-droid.org/en/packages/com.darshancomputing.BatteryIndicatorPro/
[T2]: https://play.google.com/store/apps/details?id=com.darshancomputing.BatteryIndicatorPro&hl=en&gl=US
[T3]: https://github.com/darshan-/Battery-Indicator-Pro/blob/d2a76123d16ab4f2fcc6a0a557ba8788bc576fc9/LICENSE#L25
[B1]: https://github.com/mlm-games/BatStats/tree/47b7ca46a1ad5fe2969a50c28142464860c584c0
[B2]: batstats-source-check.md
[W1]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/README.md
[W2]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/main/kotlin/io/github/loganrosen/wattdog/batterystats/parse/BatteryStatsParser.kt#L382
[W3]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/ui/screens/AppsScreen.kt#L103
[W4]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/app/src/main/kotlin/io/github/loganrosen/wattdog/data/SnapshotStore.kt#L19
[W5]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/core/batterystats/src/main/kotlin/io/github/loganrosen/wattdog/batterystats/analysis/ReportDelta.kt#L25
[W6]: https://github.com/loganrosen/wattdog/blob/06f3979a19f95c4b91f3a3234ccb7547b404e45e/gradle/libs.versions.toml
[D1]: https://github.com/santo1993/drainscope/blob/6f7628cdceaf1f0a6e5f891880ed32ffdeeff6a8/README.md
[D2]: https://github.com/santo1993/drainscope/blob/6f7628cdceaf1f0a6e5f891880ed32ffdeeff6a8/app/src/main/java/app/drainscope/battery/MainActivity.java#L465
[D3]: https://github.com/santo1993/drainscope/blob/6f7628cdceaf1f0a6e5f891880ed32ffdeeff6a8/app/src/main/java/app/drainscope/battery/HistoryStore.java#L17
[G1]: https://play.google.com/store/apps/details?id=com.paget96.batteryguru&hl=en&gl=US
[A1]: https://play.google.com/store/apps/details?id=com.digibites.accubattery&hl=en&gl=US
[A2]: https://accubattery.zendesk.com/hc/en-us/articles/210226125-Tab-2-discharging-screen
[A3]: https://accubattery.zendesk.com/hc/en-us/articles/210226445-Tab-4-charge-history-screen
[A4]: https://accubattery.zendesk.com/hc/en-us/articles/209507129-What-do-I-get-with-AccuBattery-premium
[S1]: https://play.google.com/store/apps/details?id=com.gsamlabs.bbm&hl=en&gl=US
[S2]: https://play.google.com/store/apps/details?id=com.gsamlabs.bbm.pro&hl=en&gl=US
[S3]: https://badassbatterymonitor.blogspot.com/2011/11/badass-battery-monitor-users-guide.html
[C1]: https://github.com/asksven/BetterBatteryStats
[C2]: https://play.google.com/store/apps/details?id=com.asksven.betterbatterystats&hl=en&gl=US
[C3]: https://github.com/asksven/BetterBatteryStats/wiki
[C4]: https://github.com/asksven/BetterBatteryStats/blob/ccb0904791ab20e35a187dbd2a4cf53643dcba19/app/src/main/java/com/asksven/betterbatterystats/data/ReferenceDBHelper.java#L46
[Z1]: https://f-droid.org/en/packages/dubrowgn.wattz/
[Z2]: https://github.com/dubrowgn/wattz/blob/78681ea350f08a511b563ae6a636d456d8df8f0e/readme.md#L6
[H1]: https://developer.android.com/topic/performance/power/setup-battery-historian
[H2]: https://github.com/google/battery-historian
[Q1]: https://github.com/RikkaApps/Shizuku#how-does-shizuku-work
[L1]: https://github.com/leekleak/battery-archeologist/blob/2ce3a12131d1b0a6b1352b82dd7672118b331e96/README.md
[L2]: https://github.com/rmpsdroid/BattInsight/blob/efbec352501bb6a714d8a7961e0fd05955a7510b/README.md
[L3]: https://github.com/greenhub-project/batteryhub/blob/ed2fdf83b5d795956302b4f261a0e29ac2240c67/README.md
