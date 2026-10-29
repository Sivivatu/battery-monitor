# Signing keys and Infisical

Created and restore-verified on 2026-10-29 for the public custom F-Droid beta.
The APK identity is also intended for the future custom stable stream.

## Permanent identities

Two independent RSA4096 private keys, in separate password-protected JKS files,
with 10,000-day certificate validity:

| Identity | Alias | Certificate SHA-256 |
| --- | --- | --- |
| Android APK | `battery-monitor` | `d524168decc8c4c1fa779dc1a0ad5dd5a3882ccd4500e8cee0504328d481bc57` |
| F-Droid repository | `fdroid-repo` | `133465e78e767c9691c06786f8c286144ef43cbf5a2565d0285ab4cbfb072a1b` |

The public pins also live in `fdroid/signing-certificates.json`. Certificate
subjects use project names, with no personal address. The APK key password matches
its store password for Android tooling compatibility. The repository key and
store passwords differ. Passwords were generated with the operating system's
cryptographic random source and were never printed or passed in command arguments.

Do not regenerate these identities for each release. Android recognizes updates
by APK signing identity; F-Droid trusts the repository signing identity. Neither
requires Google authentication or a purchased certificate.

## Authoritative Infisical copy

- Instance: EU Cloud, `https://eu.infisical.com`
- Organization: Databard Analytics
- Project: `battery-monitor` (`7ec1693b-18a8-4da7-bdc3-ca535cff5f75`)
- Environment: `prod`
- Folder: `/android-signing`
- Local CLI profile: `battery-monitor-eu`

| APK secret | Repository secret |
| --- | --- |
| `APK_KEYSTORE_BASE64` | `FDROID_KEYSTORE_BASE64` |
| `APK_KEYSTORE_PASSWORD` | `FDROID_KEYSTORE_PASSWORD` |
| `APK_KEY_ALIAS` | `FDROID_KEY_ALIAS` |
| `APK_KEY_PASSWORD` | `FDROID_KEY_PASSWORD` |

The entire JKS files are stored, not just certificates. Base64 is a representation,
not encryption; encoded files require the same protection as private keys.
All eight values were exported immediately after storage, compared with originals,
and decoded keystores matched byte for byte. Signed publication verifies both
certificate pins and private-key usability before deploying.

The official `@infisical/cli@0.44.10` package is installed through pnpm. Explicit
EU/profile flags avoid changing other project defaults. To export for recovery,
run on a trusted machine with `umask 077`, and direct output to a protected file:

```sh
umask 077
INFISICAL_DISABLE_TELEMETRY=1 infisical export --format json \
  --projectId 7ec1693b-18a8-4da7-bdc3-ca535cff5f75 --env prod \
  --path /android-signing --domain https://eu.infisical.com \
  --profile battery-monitor-eu --silent --output-file signing-recovery.json
```

Never paste the result into chat, commit it, upload it as a workflow artifact, or
put it in the Pages directory. Restore on a trusted machine and check both pinned
fingerprints with `keytool -exportcert` before use.

## CI replica and local custody

Eight values are mirrored into encrypted GitHub environment secrets in
`fdroid-release`. Deployments in that environment are restricted to branch `main`.
Only the trusted signing step receives them. The unsigned build and PR jobs do not.
No Infisical account credential is stored in GitHub. This initial delivery uses
GitHub secrets rather than an Infisical OIDC identity; changing Infisical does not
synchronize the replica automatically. Future OIDC can replace the replica with
read-only access to this folder after its exact repository/workflow claims are
verified.

Original files and recovery information are retained outside Git under
`/home/ubuntu/.local/share/battery-monitor-signing/`, directory mode 0700, files
0600. JKS files are password-encrypted; local password/recovery files additionally
rely on filesystem access controls. Infisical is an independently hosted copy.
**An owner-controlled offline backup has not been established.** Copy the keys
and recovery information into your own encrypted offline backup/password manager
before relying on this build host for long-term custody. GitHub secrets cannot be
read back and are not a user recovery backup.

The initial debug preview used a different certificate. Moving to this release
normally requires uninstall/reinstall, which removes history; there is no export
feature yet. Future releases must preserve the permanent APK identity above.

## References

- [Android app signing](https://developer.android.com/studio/publish/app-signing)
- [F-Droid signing](https://f-droid.org/en/docs/Signing_Process/)
- [Infisical CLI installation](https://infisical.com/docs/cli/install)
- [Infisical GitHub OIDC](https://infisical.com/docs/documentation/platform/identities/oidc-auth/github)
