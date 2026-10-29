# Public beta feed inputs

`metadata/` contains public F-Droid metadata. `signing-certificates.json` pins the
permanent public APK and repository certificate SHA-256 fingerprints; it contains
no private signing material. The release helper rejects other identities.

The helper needs Python 3.12 with `fdroidserver==2.4.5`, JDK 21, and Android SDK
build-tools 36.1.0. `scripts/test_fdroid_release.py` exercises release invariants;
it does not substitute for APK installation or an F-Droid client test.

The persistent publication branch stores only public repository output and its
release manifest. Signing config, keystores and passwords are temporary and are
never copied to that branch or a Pages artifact. Retain all historical APKs;
upgrades use increasing version codes and the same app signing certificate.

Invoke the helper with the eight documented signing environment variables:

```sh
python scripts/fdroid-release.py \
  --apk android/app/build/outputs/apk/release/app-release-unsigned.apk \
  --state /path/to/publication-branch \
  --output /path/to/empty-pages-payload \
  --tag v0.1.0-beta.1 --source-sha <full-tag-commit-sha>
```

`--output` must be empty and separate from `--state`. The public manifest records
APK hashes, source commits, versions and original publication dates. A retry of
the same source/version reuses its verified published APK, so rebuild timestamps
cannot replace a released binary. A changed source commit requires a new version.
The signed v1 index must cover exactly the retained manifest; the signed v2 entry
must authenticate the generated v2 index. New source files are provided separately
with the GitHub release and matching source tag.
