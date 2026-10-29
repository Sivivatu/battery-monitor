# Public F-Droid beta releases

The beta feed is hosted at
`https://sivivatu.github.io/battery-monitor/fdroid/beta/repo`.
The [landing page](https://sivivatu.github.io/battery-monitor/) provides the
fingerprinted add-repository link. Downloads are public, with no tester accounts.
This is our own F-Droid repository; the app has not been accepted into the default
f-droid.org catalogue. Telemetry is deferred.

## Publish the next beta

1. Increment `versionCode` and `versionName` in `android/app/build.gradle.kts`.
   Use one increasing code sequence across future beta/stable feeds. Use numbered
   beta names such as `0.1.0-beta.2`, and update the F-Droid metadata/changelog.
2. Review, test, and merge the source to `main`. Do not put signing material in Git.
3. Tag the intended main commit and dispatch the workflow from `main`:

   ```sh
   git tag v0.1.0-beta.2
   git push origin v0.1.0-beta.2
   gh workflow run fdroid-beta.yml --ref main -f tag=v0.1.0-beta.2
   ```

The deliberate dispatch is the release gate. The rest is automated: source
ancestry validation, formatting/unit tests/lint, minified release build, permanent
APK signing, signed v1/v2 indexes, release install/reinstall smoke on Android 14,
retained repository state, GitHub prerelease with corresponding source, GitHub
Pages deployment, and verification of publicly served signatures/hashes.

`fdroid-repo` stores public feed state and historical APKs. Never delete or rebuild
that branch from scratch. Publications are serialized; pushes never force-update
it. The helper rejects older version codes, unexpected certificates, modified
retained APKs, and private files in the payload. Rerunning the same source/tag
reuses its existing APK, rather than replacing an immutable version with a rebuild.
Keep this branch within GitHub's repository-size limits; large future archives
may need separate object storage without changing signing identities.

## Signing and access

See [signing custody](signing-keys.md). Infisical EU is the authoritative key store;
GitHub environment secrets are a CI replica. The workflow uses no Infisical login
token, service token, or machine identity. Changes to Infisical are not automatically
synced to GitHub. Do not rotate permanent keys as routine password maintenance.

The GitHub `fdroid-release` environment allows deployments only from branch `main`.
The workflow rejects other refs and tags whose commit is outside `main`. Its
signing scripts are checked out from `main`, not the caller's tag. Actions in the
publisher are pinned to commit SHAs. Only the signing step receives key variables;
temporary files are deleted before artifacts/Pages upload. The build job and
pull-request validation receive no signing secrets. Repository administrators can
change these settings and are trusted release operators; no independent human
reviewer gate is configured.

## Validate and recover

```sh
python -m unittest discover -s scripts -p test_fdroid_release.py -v
# In the pinned fdroidserver environment, with JDK/Android build tools available:
python scripts/verify-fdroid-live.py --expected-source FULL_RELEASE_COMMIT_SHA
```

`fdroidserver==2.4.5`, Python 3.12, JDK 21, and Android build-tools 36.1.0 are used
for publication. Exact app build dependencies are pinned in the Gradle catalog.
The publisher's full Python dependency tree is hash-locked in
`fdroid/requirements.txt` and installed with `pip --require-hashes`. Review lock
updates; regenerate with `uv pip compile --python 3.12 --generate-hashes
fdroid/requirements.in -o fdroid/requirements.txt` when upgrading deliberately.
The signed APK is verified independently from the repository index signature.
The smoke test proves setup/dashboard/relaunch and signed in-place reinstall;
it does not claim a real F-Droid client has performed a two-version upgrade.

If publication fails, inspect the failed stage before retrying the same tag.
The persistent branch can advance before Pages does; rerun the same tag to deploy
that complete state. Release uploads can also precede Pages deployment. Never
restore an older feed over newer versions. If keys are unavailable, restore the
originals and verify fingerprints; generating replacements breaks client trust.

Future stable releases should use a separate `/fdroid/stable/repo/` feed with the
same APK signing identity. Stable with a lower versionCode cannot downgrade a
newer installed beta. Official F-Droid inclusion and its signing/reproducibility
policy require a separate submission.
