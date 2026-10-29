# F-Droid beta distribution and GitHub feedback feasibility

Research checked: **2026-10-29 (UTC)**. Scope: documentation and feasibility only. No deployment, workflow/app changes, credentials, publishing, or issue submission performed. Official vendor documentation and first-party F-Droid source were consulted; source inspection is not a device compatibility test.

## Confirmed beta distribution decision

On 2026-10-29 the user revised the earlier approved-testers requirement: **public
beta downloads without authentication or separate tester logins**. Keep a separate
beta stream when stable releases are available. This supersedes the earlier
restricted-beta decision. Authentication research below is optional reference,
not a requirement of the chosen design.

## Recommended path

Start with a **public custom F-Droid repository** containing release APKs signed
with a persistent developer-controlled app key. Publish its HTTPS URL and signing
fingerprint; users explicitly add the beta feed. Use separate public beta/stable
paths, such as `/fdroid/beta/repo/` and `/fdroid/stable/repo/`, when stable releases
exist. Keep official f-droid.org inclusion as a later, separately reviewed milestone.

Use CI to build/test/sign the release APK, generate the repository with
**fdroidserver**, and publish its output to public hosting. Persist both the APK
signing identity and repository signing identity. GitHub Releases may be an
intermediate artifact source, and GitHub Pages is a possible public feed host;
no tester account management or download-authentication backend is required.

For initial feedback, provide “Report bug” and “Request feature” buttons that open **prefilled GitHub issue templates in the browser** after the user reviews the content. This needs no Google Auth, no GitHub SDK, and no embedded token. It does require a GitHub account to submit. If users must submit entirely inside the app without a GitHub account, choose and secure a backend that files issues on their behalf.

Sources: [F-Droid repository setup](https://f-droid.org/en/docs/Setup_an_F-Droid_App_Repo/), [signing process](https://f-droid.org/en/docs/Signing_Process/), [GitHub issue creation](https://docs.github.com/en/issues/tracking-your-work-with-issues/creating-an-issue).

## Official listing versus a custom beta repository

| Route | Verified behavior | Implication |
| --- | --- | --- |
| Official f-droid.org repository | Apps undergo inclusion/build review; published source, FLOSS licensing, allowed dependencies/toolchain, and useful maintained functionality are required. | Public distribution, not an invitation-only beta service. Acceptance and timing are not guaranteed. |
| Developer-operated repository | fdroidserver creates a repository from APKs and metadata; users add its URL to their client. | Appropriate for controlled beta cadence without waiting for official inclusion. Hosting and access policy are the developer's responsibility. |
| Unlisted custom repository | No access restriction follows merely from keeping the URL out of a directory. | Anyone obtaining the URL may retrieve public content. |
| Authenticated custom repository | F-Droid's first-party client source supports repository username/password and HTTP Basic authentication. | Feasible access control, conditional on testing the chosen client/version and host. |

The current inclusion policy also says apps should generally have at least three months of active development, with exceptions including proven users or an author maintaining other F-Droid apps. For forks, it requires a distinct application ID and corresponding name/icon/string changes. This project derives from WattDog; official submission needs a separate compliance audit of attribution, license, branding, dependencies, and buildability. The current local Gradle file uses application ID `io.github.sivivatu.batterymonitor`, `versionCode = 1`, `versionName = "0.1.0"`, and does not show a release signing configuration. This is a limited file inspection, not a complete compliance or signing audit.

Sources: [inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/), [repository setup](https://f-droid.org/en/docs/Setup_an_F-Droid_App_Repo/), local `android/app/build.gradle.kts`.

## Optional authenticated-repository reference (not selected)

F-Droid's repository-addition implementation accepts credentials, stores them separately from the normalized repository address, and supplies them to downloads. Its first-party test explicitly covers a URL of the form `https://username:password@host/repo/`; downloader documentation explicitly names HTTP Basic Auth support, including the v2 downloader.

Primary implementation evidence:

- [Repository addition](https://github.com/f-droid/fdroidclient/blob/master/libs/database/src/main/java/org/fdroid/repo/RepoAdder.kt).
- [Basic-auth repository-addition test](https://github.com/f-droid/fdroidclient/blob/master/libs/database/src/test/java/org/fdroid/repo/RepoAdderTest.kt) (`testAddingMinRepoWithBasicAuth`).
- [HTTP v2 downloader](https://github.com/f-droid/fdroidclient/blob/master/libs/download/src/androidMain/kotlin/org/fdroid/download/HttpDownloaderV2.kt).
- [Repository credential fields](https://github.com/f-droid/fdroidclient/blob/master/libs/database/src/main/java/org/fdroid/database/Repository.kt).

These links track the current development branch. They establish implementation support, **not universal support across F-Droid releases or alternative clients**. Pin the client version during beta acceptance testing.

Recommended hosting constraints and tests (engineering recommendations):

- Require HTTPS and protect **all** indexes, APKs, icons, archive paths, and any alternate download/mirror URLs. A login page protecting only the landing page is insufficient.
- Use HTTP authentication compatible with the client, rather than an interactive OAuth/SSO page. Do not assume browser cookies, GitHub private-repository authentication, custom bearer headers, or CDN redirects will work.
- Prefer per-tester credentials for revocation. Credential-bearing onboarding URLs/QR codes are secrets: avoid public README files, screenshots, analytics, and logs. Encode reserved characters if credentials appear in URL userinfo.
- Verify an unauthenticated request cannot fetch indexes or APKs; an authorized client can add/update/install; wrong/revoked credentials fail; a second APK updates successfully. Check redirects and caches against access leakage.
- Repository authentication restricts future downloads; it does not prevent an invited tester from redistributing an APK already downloaded. Repository signatures authenticate content; they do not encrypt it.
- Keep signing keys, keystores, and `config.yml` out of the served directory. F-Droid's production setup explicitly separates signing from the public web server.

No live authenticated repository or Android client was tested in this research.

## CI delivery and persistent state

The official fdroidserver pattern is enough; a particular marketplace action is not required. F-Droid documents `fdroid init`, putting APKs under `repo/`, `fdroid update` (initially `--create-metadata`), and publishing the generated `repo/` contents, optionally with `fdroid deploy`. This is a documented CLI workflow that can be orchestrated by GitHub Actions, rather than a verified end-to-end official GitHub Action for this project.

Proposed release pipeline:

1. Trigger from an approved beta tag or explicit release request; run the project's checks and build a release APK from that exact source revision.
2. Sign with the persistent **app** key, verify the signature/certificate and expected application ID/version code, and preserve the signed APK as an immutable artifact.
3. Restore the repository's configuration, metadata, retained APKs, and persistent **repository** signing key. Initialize once; do not generate a new repository key on each CI run.
4. Add the APK, update release metadata, then run `fdroid update`. Publish the complete consistent repository output; serialize concurrent releases and avoid exposing a partially updated index/artifact set.
5. Smoke-test repository refresh and upgrade on the selected F-Droid client before announcing availability. Retain key backups and enough repository state to recover.

Use protected CI secret storage for keystores/passwords and narrowly scoped host deployment credentials. Restrict signing/publishing jobs to trusted release code; untrusted pull-request code must not receive release secrets. Keep signing material out of artifacts and logs. Repository-host credentials and feedback-backend credentials are separate concerns.

GitHub Releases can retain the same signed APK for later repository ingestion. They do not create an F-Droid index automatically. Public beta APKs and public repository hosting match the revised decision; release and hosting deployment credentials still belong only to trusted CI jobs.

F-Droid also documents `fdroid nightly`, but that guide deliberately uses the Android **debug keystore for both APK and index signing** and explicitly warns that it provides less security than a standard repository. Its illustrated automation is GitLab CI. Do not adopt it unchanged for a beta expected to transition to durable release signing.

Sources: [repository setup and deployment](https://f-droid.org/en/docs/Setup_an_F-Droid_App_Repo/), [signing process](https://f-droid.org/en/docs/Signing_Process/), [nightly publishing](https://f-droid.org/en/docs/Publishing_Nightly_Builds/), [GitHub Actions secret handling](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).

## Signing, versions, and transition to official F-Droid

**Two independent identities:** the APK certificate is Android's app-update identity; the repository certificate authenticates the catalog trusted by F-Droid. Keep both persistent and backed up, preferably as separate keys. Changing hosts does not itself require changing either key. Signing a new repository does not make an APK signed with another app key an acceptable update.

**Versions:** allocate a higher `versionCode` for each delivered update, across every delivery channel using the same application ID/key. `versionName` is a user-facing label and is not an update-order mechanism. Avoid independent counters that collide between beta and stable. F-Droid documents `CurrentVersionCode` as the recommended stable version; newer APKs can be marked beta and require client opt-in. A separate beta repository makes the initial opt-in explicit, but does not bypass Android version ordering.

**Separate streams:** keep one monotonically increasing version-code sequence and the same application ID/APK key across public beta/stable feeds for in-place channel switching. Enabling both feeds may select the highest eligible version. A stable APK with a lower version code cannot replace an installed newer beta without a downgrade/reinstall; release a newer stable build or wait for stable to catch up. Separate feed paths do not isolate installations. Use a separate application ID only if parallel beta/stable installations are desired.

**Existing debug installs:** a release APK signed with a different key normally cannot update a same-ID debug installation. For the beta, document export/backup if available, uninstall/reinstall, possible loss of local app data, and reconfiguration of special permissions such as Shizuku/ADB grants. Alternatively use a distinct beta application ID for parallel installation, accepting separate data and package identity. Do not promise automatic debug-to-release migration or use the debug key as the permanent release identity. Verify the certificate on the actual installed APK before deciding whether a mismatch exists.

**Official inclusion later:** ordinary F-Droid-built APKs use F-Droid-managed app signing keys. An upstream-signed beta therefore does not automatically update to an ordinary F-Droid-signed build. F-Droid's reproducible-build workflow can retain upstream APK signatures: F-Droid independently rebuilds, copies the upstream signature, and verifies the resulting APK. This enables matching identity across channels when configured and successfully reproduced; it is not an approval to redistribute arbitrary upstream binaries.

Prepare pinned source tags and toolchains/dependencies, deterministic outputs, and metadata pointing to upstream signed APKs (`Binaries`/`binary` as applicable). Prove reproducibility with F-Droid's documented tools and infrastructure before promising seamless official-channel upgrades. Preserve the developer app key regardless. No reproducible rebuild or official inclusion assessment was performed here.

Sources: [Android app signing and update identity](https://developer.android.com/studio/publish/app-signing), [Android versioning](https://developer.android.com/studio/publish/versioning), [F-Droid signing](https://f-droid.org/en/docs/Signing_Process/), [reproducible builds](https://f-droid.org/en/docs/Reproducible_Builds/), [beta version metadata](https://f-droid.org/en/docs/Setup_an_F-Droid_App_Repo/).

## Android developer verification outside Play

This is distinct from both **Google Auth inside the app** and **publishing on Google Play**. Battery Monitor can use F-Droid and GitHub feedback without adding Google sign-in or Google Play Services. Separately, Google's device-side distribution requirements can affect installation of apps distributed outside Play.

Current official documents need careful scoping:

- The [overview](https://developer.android.com/developer-verification) says enforcement began **September 30, 2026**, for participating stores in **Brazil, Indonesia, Singapore, and Thailand**, on certified Android devices running Android 7+, with broader global expansion in 2027.
- The [detailed guide](https://developer.android.com/developer-verification/guides) lists initial participating stores: Google Play, HONOR App Market, OPPO App Market, Galaxy Store, Palm Store, V-Appstore, and GetApps. **F-Droid is not listed there.** This does not prove a permanent F-Droid exemption; the guide says capability will expand to third-party stores and all apps on certified devices.
- The official [FAQ](https://developer.android.com/developer-verification/guides/faq) says ADB installation remains available without verification. The guides also describe an advanced flow for sideloading unregistered apps; this is additional user interaction, not equivalent to ordinary verified installation.
- Developers distributing exclusively outside Play can use [Android Developer Console](https://developer.android.com/developer-verification/guides/android-developer-console). Console signup uses a **Google Account**; that is a developer administrative account, not a requirement to implement Google login for users of this app.
- The dedicated [limited-distribution guide](https://developer.android.com/developer-verification/guides/limited-distribution), updated **2026-10-28**, says the free account supports **up to 50 explicitly authorized devices** via an invitation/QR/link handshake. It requires a Google Account with 2-Step Verification, a Google payments profile for legal name/address, and contact email; it says no government ID is required. **The overview and general guide still say 20 devices.** Prefer the newer specific guide for planning, but verify the effective console limit and onboarding on real tester devices before committing to a beta size. The dedicated guide also uses broader enforcement wording than the store-scoped overview; this documentation discrepancy remains unresolved.
- The [limited-distribution help page](https://support.google.com/android-developer-console/answer/17131204?hl=en) also restricts registration to new package names not previously seen on Android. Existing distributed preview package names may therefore need a different path; do not assume the current application ID can be registered in that account type.
- The [open-source registration guide](https://developer.android.com/developer-verification/guides/open-source-app-registration) explicitly covers a repository that re-signs apps: register the distributed signing certificate too. A repository integrating with the console API may handle package/key registration on the developer's behalf, but the guide still requires a verified developer account. Do not assume F-Droid currently performs this for the project.

Recommendation: collect testers' regions, certified-device status, Android versions, and intended install paths. Decide whether limited-distribution registration, full verification, or an advanced/ADB test path fits them. A private HTTP repository is not an Android verification exemption or a replacement for its device-authorization flow. Recheck the linked documents and actual console immediately before distribution; no registration or installation behavior was verified here.

## GitHub feedback without Google Auth

### Minimal beta: browser issue drafts

Use an app form or direct buttons to open a URL such as:

`https://github.com/OWNER/REPO/issues/new?template=bug_report.md&title=ENCODED_TITLE&body=ENCODED_BODY`

Use the actual template filenames; provide a feature-request equivalent. URL-encode each value. GitHub also supports prepopulating issue-form fields by field ID, so match the template type actually chosen. GitHub documents permission checks on query parameters: do not rely on ordinary reporters being able to set labels/assignees. Configure defaults in templates instead. A URL opens a draft; the user reviews and submits it on GitHub under their account. Long URLs can fail with HTTP 414, so keep the draft short and use a separately exported attachment for logs.

This provides an in-app entry point, but the final submission occurs in the browser. It meets “no Google Auth”; it does not meet “no GitHub account” or “entirely native submission.”

Source: [GitHub issue creation, URL queries, templates and limits](https://docs.github.com/en/issues/tracking-your-work-with-issues/creating-an-issue#creating-an-issue-from-a-url-query).

### Entirely in-app or anonymous submission

The [create-issue REST endpoint](https://docs.github.com/en/rest/issues/issues#create-an-issue) is authenticated; fine-grained tokens need repository **Issues: write** permission. A shared PAT, GitHub App private key, client secret, or installation token embedded in an APK can be extracted and abused. Do not ship one.

Two possible designs:

- User-authorized GitHub access: each reporter signs in to GitHub through a supported authorization flow. Google Auth is unnecessary, but account and token handling remain.
- Accountless app feedback: the app posts a reviewed report to a developer-operated HTTPS backend. The backend validates size/content, rate-limits and moderates spam, and uses narrowly scoped server-side GitHub App credentials to create the issue. The issue is filed as the backend identity, with attribution only when explicitly provided. The app contains the public endpoint, not a reusable privileged secret. Anonymous access creates an abuse-control and hosting obligation; it does not eliminate backend authentication to GitHub.

Source: [GitHub REST authentication](https://docs.github.com/en/rest/authentication/authenticating-to-the-rest-api), [create issue permissions and request schema](https://docs.github.com/en/rest/issues/issues#create-an-issue).

### Privacy and attachments

Default to a short user-editable description plus app version, Android version, and relevant feature/permission state. Battery reports may expose installed packages, usage patterns, timestamps, device identifiers, or log secrets; collect diagnostics only by explicit opt-in and allow preview/redaction. Do not put sensitive diagnostics in URL query strings, which can persist in browser history and intermediary logs. Explain whether the destination repository is public before submission; a private APK repository does not make GitHub reports private.

GitHub supports attaching files in the browser and uploads them **immediately when selected**, before the issue is submitted. Its current docs say attachments in public repositories are accessible without authentication; private/internal repository attachments require repository access. Image limits are 10 MB and other-file limits 25 MB (video rules depend on plan). The create-issue JSON endpoint has no general local-file attachment field: do not assume a prefilled URL or POST body uploads a device log. For beta, export a redacted text/log file for the user to attach manually, or separately design a documented upload mechanism and retention policy for the backend. Do not treat possession of an attachment URL as confidentiality.

Source: [GitHub file attachments and visibility](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/attaching-files), [issue API request schema](https://docs.github.com/en/rest/issues/issues#create-an-issue).

## Decisions and verification still required

1. Public beta with no tester login and separate beta/stable streams is confirmed. Which public host and exact F-Droid client/version will be supported?
2. Prefer the same application ID/key for channel switching unless side-by-side installs are desired. Verify certificates on existing tester installs and decide what data needs preserving.
3. Who owns and backs up the app and repository keys, and which trusted CI release path may access them?
4. Is browser handoff with a GitHub account acceptable, or is accountless/native submission required? Which repository receives reports, with what visibility and attachment policy?
5. How many tester devices, in which regions, and what verification/onboarding path applies? Resolve the official 20/50-device documentation mismatch in the actual console.
6. Is seamless migration to official F-Droid a requirement? If yes, prove reproducible upstream-signed builds before promising it.

This note establishes documented feasibility. It does not establish working CI, client/channel-switching compatibility, reproducibility, official F-Droid acceptance, Android verification registration, or a tested installation/upgrade/feedback journey.
