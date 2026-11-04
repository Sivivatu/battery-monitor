# Fractional discharge parsing regression

Date: 2026-10-31. Source plan: user-reported Shizuku collection failure while
preparing the local-ADB setup build.

## Symptom and confirmed reproduction

The phone screenshot reports `Invalid or overflowing activity counter`. Its raw
diagnostic output was not provided, so the exact token from that phone is unknown.
A controlled full-parser test reproduces that exact exception with valid decimal
discharge mAh. The prior parser sent all six discharge buckets through an integer
activity-counter parser, which rejected fractional mAh.

The pinned Android 17 framework source prints these buckets using
`formatCharge(dischargeCount / 1000.0)`, with decimal places for values below
100 mAh. Cellular packet counters, by contrast, are printed as raw long integers;
a packet-number formatting change is not supported by this source inspection.

Primary source:

- [AOSP BatteryStats.java discharge output, pinned revision](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/java/android/os/BatteryStats.java#5119)
- [AOSP formatCharge and formatValue](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/java/android/os/BatteryStats.java#3574)

## Fix and limits

Discharge fields now accept finite, nonnegative decimal mAh within the existing
Int range, then round to the model's existing whole-mAh precision. This preserves
snapshot metadata compatibility; it does not add sub-mAh storage precision.
Malformed, negative, nonfinite, and overflowing values still fail. Discharge
failures name only the fixed field label, without echoing raw values or app tags.
Activity-counter validation remains unchanged.

This confirms and fixes a real valid-format trigger for the screenshot's error,
but is not physical-phone confirmation. Other genuinely invalid activity counters
can still produce the original error.

## RED and GREEN evidence

RED command:

```sh
ANDROID_HOME=/home/ubuntu/Android/Sdk android/gradlew -p android \
  :core:batterystats:test --tests '*DischargeFormatTest' \
  --no-parallel --max-workers=2
```

Actual result: `3 tests completed, 2 failed`. The fractional-discharge test failed
with `BatteryStatsParseException`; the safe field-context assertion also failed.

GREEN and full regression command:

```sh
ANDROID_HOME=/home/ubuntu/Android/Sdk android/gradlew -p android \
  :core:batterystats:test :core:batterystats:jacocoTestReport \
  --no-parallel --max-workers=2
```

Actual result: `BUILD SUCCESSFUL in 25s`.

`DischargeFormatTest` guarantees all six fractional discharge buckets are accepted
and rounded consistently, malformed/negative/nonfinite/overflowing discharge is
rejected with safe field context, and Int.MAX_VALUE remains accepted. Existing
parser, delta-continuity, analysis, and real-fixture tests also ran in the full
suite. No new credentials, logs, or temporary diagnostics were added to the repo.

Full-suite result: **110 tests passed**, zero failures/errors. JaCoCo core line
coverage is **98.32%** (1,637 covered / 28 missed); branch coverage is **77.79%**
(1,058 covered / 302 missed). Overall Android app coverage is outside this narrow
core-parser run.
