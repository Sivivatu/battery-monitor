# Android telemetry options

Research checked **29 October 2026** against official documentation and upstream source. Documentation and default-branch source can change; no SDK version is selected, dependency graph resolved, integration installed, or device behavior tested here.

## Recommendation

**Release decision (2026-10-29): telemetry is deferred until after the initial
beta. No SDK or telemetry-service configuration is needed to publish that beta.**

**Yes, either SDK or both can be integrated technically. Start with optional Sentry crash diagnostics; consider PostHog behavior analytics later under separate consent.** The user has not selected a vendor combination. Both introduce data disclosure and observer overhead, and neither combination has been approved for this app by official F-Droid. Keep the battery monitor fully useful without telemetry; a telemetry-free build is the simplest fallback for distribution review.

| Need | PostHog Android | Sentry Android |
| --- | --- | --- |
| Behavior analytics | Manual events, properties, funnels downstream; lifecycle/screens/deep links captured by default unless disabled. | Breadcrumbs and diagnostic context; not a replacement for dedicated product analytics. |
| Errors | Current Android-specific docs support manual `captureException` and uncaught JVM exceptions through `Thread.UncaughtExceptionHandler`; exception autocapture must be enabled. This is SDK-specific support, not an inference from the broader PostHog platform. | Handled/unhandled errors, crash diagnostics, release context. |
| ANRs/native crashes | The Android installation page does **not establish equivalent ANR or native-signal crash coverage**. Do not promise it. | Documented ANRs: watchdog below Android 11, `ApplicationExitInfo` on Android 11+. NDK native crash capture; optional tombstones on Android 12+, disabled by default and not a full NDK replacement. |
| Performance/replay | Android session replay exists with masking and screenshot-related configuration. Exclude it here. | Tracing and profiling exist; optional replay also exists. Leave these off initially. |

Sources: [PostHog Android SDK](https://posthog.com/docs/libraries/android), [Android error installation](https://posthog.com/docs/error-tracking/installation/android), [Sentry Android](https://docs.sentry.io/platforms/android/), [ANRs](https://docs.sentry.io/platforms/android/configuration/app-not-respond/), [NDK](https://docs.sentry.io/platforms/android/configuration/using-ndk/), [tombstones](https://docs.sentry.io/platforms/android/configuration/tombstones/), [tracing](https://docs.sentry.io/platforms/android/tracing/).

If both are eventually used, let Sentry own automatic exception handling and keep PostHog exception autocapture off. Multiple SDKs wrapping the uncaught-exception handler require explicit crash/restart tests; do not assume ordering, delivery, or deduplication. PostHog-only may suffice for simple JVM exception reporting, but its documented scope does not justify promising Sentry-equivalent diagnosis.

## F-Droid: eligibility and labels are separate

The official [Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/) requires freely licensed app code/dependencies and a FLOSS build process. Proprietary tracking/advertising libraries, including Firebase/Crashlytics and Google Play Services, are prohibited in distributed apps; use a FLOSS alternative or a build flavor excluding them. Trusted Maven hosting alone does not establish a dependency's license. F-Droid does not register API keys and says supplied keys are included in binary and source releases. Maintainers still review the complete build and exercise judgment.

The exact [Anti-Features](https://f-droid.org/en/docs/Anti-Features/) conditions matter:

- **Tracking:** detailed definition applies when activity is tracked/reported without permission or by default. It explicitly exempts the examples when functionality is **opt-in, disabled by default, enabled only with informed consent**, with a privacy policy similar to GDPR and PII avoided as far as possible. The page's short list also says tracking can be marked even when it can be turned off: an off switch is insufficient. Crash reports are an explicit example.
- **NonFreeNet:** applies to apps that **“promote or depend entirely on a proprietary network service.”** Optional telemetry is not automatically an entire-app dependency, but promotion and the actual service/license still need assessment. Opt-in does not waive this separate condition.
- **TetheredNet:** depends entirely on an instance that is impossible or difficult to replace. The documented exception is a simple configuration option pointing to an alternative publicly available self-hostable server. Do not promise that developer-controlled endpoint settings alone satisfy that exception.

Thus informed opt-in can satisfy the documented Tracking exception; it does **not** automatically remove other labels or guarantee inclusion. Disclose the SDKs, endpoints, data schema and switches to packagers. A separate repository is also distinct from acceptance into the official main repository.

## SDK licenses and dependencies

- PostHog Android is [MIT](https://github.com/PostHog/posthog-android/blob/main/LICENSE.md); Sentry's Java/Android SDK is [MIT](https://github.com/getsentry/sentry-java/blob/main/LICENSE). These are SDK licenses, not assurances about all dependencies or hosted services.
- Current [PostHog Android build source](https://github.com/PostHog/posthog-android/blob/main/posthog-android/build.gradle.kts) has Kotlin, AndroidX, Curtains and the PostHog core as runtime dependencies. It also declares **Firebase Messaging as `compileOnly`**: that declaration does not itself make Firebase a transitive runtime dependency. Avoid push integration and any added Firebase dependency; inspect the selected published artifact, bytecode references and F-Droid scanner results before claiming compatibility.
- Current [Sentry aggregate artifact](https://github.com/getsentry/sentry-java/blob/main/sentry-android/build.gradle.kts) includes core, NDK and replay modules. A replay module present in the dependency graph is distinct from replay being enabled. Select the smallest documented artifact set that meets the requirement; audit native libraries and every transitive license too. No complete release dependency audit was performed in this bounded research.

Pin reviewed release versions during implementation rather than copying dynamic `3.+` examples from vendor docs. Generate the resolved release dependency/license inventory, check for non-free libraries and unreviewed prebuilts, and run the actual F-Droid build/scanning process. SDK MIT licensing alone is insufficient evidence of inclusion readiness.

## Consent, offline storage and withdrawal

**Recommended policy:** two independent, default-off settings: “Send crash diagnostics” and “Send anonymous usage events.” Explain recipients, purposes, fields, local queue retention, server retention/deletion, regions, and withdrawal before enabling. Declining must preserve functionality. “Anonymous” SDK events can still contain persistent IDs/device context; never equate the SDK term with unlinkability.

Prefer **no SDK initialization before consent**, including no startup configuration/feature-flag requests. Sentry auto-initializes through a provider unless disabled: follow [manual initialization](https://docs.sentry.io/platforms/android/configuration/manual-init/) (`io.sentry.auto-init=false`, and review merged providers). Its docs also describe removing both initialization and performance providers. After stored consent, initialize once in `WattdogApplication.onCreate`, before `AppContainer` if startup failures are to be covered. First-run failures before consent/initialization cannot be captured.

PostHog documents `optOut`, `optIn`, persistent offline queues, `reset`, and asynchronous best-effort `flush`. Its defaults include lifecycle/screens/deep links and preloaded feature flags. Disable those; opt-out is not evidence of zero network traffic or deletion. The [current source](https://github.com/PostHog/posthog-android/blob/main/posthog/src/main/java/com/posthog/PostHog.kt) shows **`close()` calls `flush()` before teardown**; `optOut()` is not a documented disk-purge API, and `reset()` is primarily identity/property reset. The [queue implementation](https://github.com/PostHog/posthog-android/blob/main/posthog/src/main/java/com/posthog/internal/PostHogQueue.kt) distinguishes stopping its timer from clearing records. These are inspected default-branch semantics, not a guarantee about an unselected released version.

Sentry [stores events on disk before sending and retries on next startup](https://docs.sentry.io/platforms/android/configuration/draining/). `flush()` explicitly sends; [closing the SDK](https://github.com/getsentry/sentry-java/blob/main/sentry/src/main/java/io/sentry/Sentry.java) is not a documented cache-erasure guarantee. Do not advertise either SDK's “disable” or “close” operation as forgetting previously queued or uploaded data.

Before shipping, verify a withdrawal path against the pinned SDK: block new captures and transmissions immediately, stop SDK activity without a final upload, purge its pending memory/disk queues and identifiers safely, and ensure re-enabling cannot resend old records. Account for events already in flight and native/replay caches. Do not invent a purge API; if no reliable supported path exists, resolve it before promising withdrawal semantics. Test offline capture → withdraw → force-stop/restart → reconnect → re-enable. Server deletion is separate from local purge and requires its own documented mechanism/retention policy.

## Battery-data privacy boundary

Use an explicit **manual event and property allowlist**, applied before persistence where supported. Suggested behavior events: `screen_opened` with a fixed screen enum, `setup_step_completed`, and `collector_action` with a fixed success/error category. Allow release/build, coarse Android version and collector mode only when needed. PostHog supports a pre-queue `addBeforeSend` filter; Sentry provides [event/breadcrumb filtering](https://docs.sentry.io/platforms/android/configuration/filtering/). Inspect each envelope type, not just custom fields.

Never upload raw `dumpsys` output, installed package/app lists, UIDs, usage histories, precise charging/battery timelines, database/export contents, arbitrary exception messages, shell command output, paths, URLs or user-entered text. Keep stack structure with scrubbed messages and fixed error codes. Disable screenshots, view hierarchies, session replay, Logcat/log forwarding, network body/header collection, automatic screen/deep-link/lifecycle events and unnecessary breadcrumbs. Do not call `identify` with an email/device identifier; avoid cross-vendor identity correlation. Default masking and `sendDefaultPii=false` are not complete privacy guarantees: review the selected SDK's current [data options](https://docs.sentry.io/platforms/android/configuration/options/), including newer data-collection controls and separate replay settings.

## Main app versus Shizuku process

Local inspection: `WattdogApplication` constructs `AppContainer`; `ShizukuShellClient` binds a Shizuku user service using a `dump` process suffix and documents launching a separate `app_process`. SDK setup in the ordinary app cannot be assumed to instrument that shell service, its child commands, or the Shizuku server. Sentry explicitly documents [separate initialization for non-main app processes](https://docs.sentry.io/platforms/android/configuration/multi-process-apps/), but that is not proof its Android initialization works unchanged in Shizuku's shell context.

Keep telemetry out of the privileged shell process initially. Return allowlisted failure codes to the consenting main app for manual reporting. A shell-process death may appear only as Binder death/timeout; main-app ANR/native reporting does not establish capture of the shell's root cause. Test each process independently. Never send privileged dumps as diagnostic attachments.

## Configuration and hosting decisions still needed

| Service | Required configuration | Privacy/operations decision |
| --- | --- | --- |
| PostHog | Public project token, host (`https://us.i.posthog.com` or `https://eu.i.posthog.com`, or chosen self-host endpoint), release/environment, manual allowlist and explicit disabled features. | Choose project region, retention, access and deletion policy. The [project token is public](https://posthog.com/docs/privacy); personal/admin API keys must stay out of the APK. |
| Sentry | Project's **public DSN**, release/environment, crash/ANR/native scope, scrubbers and disabled replay/performance defaults; matching mapping/debug symbols for readable release crashes. | Choose organization region or self-host URL. Keep mapping-upload authentication in build secrets, separate from the DSN. |

Sentry's [storage-location policy](https://docs.sentry.io/organization/data-storage-location/) lists US/Iowa and EU/Frankfurt event storage, while some account/control metadata remains in the US; region is not a blanket guarantee of all processing location. PostHog's SDK documents distinct US/EU ingestion hosts. Confirm each vendor's current contractual processing/retention details before publishing a privacy policy.

Self-hosting changes the recipient and operational burden, not automatically the license classification. [PostHog server licensing](https://github.com/PostHog/posthog/blob/master/LICENSE) separates MIT portions from [restricted enterprise code](https://github.com/PostHog/posthog/blob/master/ee/LICENSE). [Current Sentry server](https://github.com/getsentry/sentry/blob/master/LICENSE.md) and [self-hosted distribution](https://github.com/getsentry/self-hosted/blob/master/LICENSE.md) use FSL with an Apache-2.0 future license; current restricted versions are not equivalent to an MIT SDK or an unrestricted FLOSS backend. Review the exact deployed server versions/features before making NonFreeNet claims. No cloud account or backend was created.

## Energy acceptance gate

Measure on the target device with telemetry disabled, crash-only enabled, and analytics enabled: idle/screen-off drain, wakeups, CPU, memory, bytes, queue growth and startup latency. Include offline/retry, Doze and crash/restart cases over comparable windows. PostHog explicitly warns that `flushAt=1` uses more battery; keep batching and bounded queues. Disable tracing/profiling/replay initially; do not flush on every battery sample or turn battery collection into an analytics stream. Vendor [Sentry overhead guidance](https://docs.sentry.io/platforms/android/overhead/) and batching claims cannot establish overhead in this battery-measurement app. No numerical overhead claim is justified without measurement.
