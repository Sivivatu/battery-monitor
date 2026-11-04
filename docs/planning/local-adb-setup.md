# On-device ADB setup

## Product requirement

An Android 14+ user can enable detailed battery collection without a computer or
Shizuku installation. Android Developer options, wireless debugging, and explicit
pairing remain required. Existing Shizuku and computer grants remain available.

## Architecture and technical design

A temporary, local-only ADB session pairs using the phone's pairing port/code,
connects using its separate debugging port, and executes only the three fixed
permission grants for this application's own package. `DUMP` and usage access
are required; a refused compatibility `BATTERY_STATS` grant is followed by real
direct verification rather than blocking an otherwise working installation. Pairing codes and ephemeral
ADB keys are not persisted. The client closes before direct dumpsys verification.
The setup screen supports a second direct verification after debugging is off.
No background ADB service, discovery, or remote-host input is added.

Reuse LibADB Android 3.1.1, pinned upstream commit
c849886ebc6d48e7b46d967e78a6bb65c90c3b74. Carry the source locally so socket
bounds, cleanup before a completed TLS handshake, and fresh TLS credentials on
retry can be corrected without implementing the ADB/SPAKE2 protocols ourselves.
Use published SPAKE2 and Conscrypt dependencies and preserve upstream licensing.
Network sockets require INTERNET; any Android 17 local-network permission need
must be evaluated against the actual local-only connection.

## Tasks and acceptance

1. Add failing tests at the setup controller boundary: input validation, fixed
   grants, stage failures, timeout/cancellation cleanup, and post-close verification.
2. Implement the bounded transport and guided pairing UI; correct incomplete
   computer commands while preserving existing direct-grant availability.
3. Test real direct collection and the setup UI on an emulator; attempt actual
   wireless pairing when supported. Record physical-phone gaps explicitly.
4. Run formatting, unit tests, lint, debug and minified release builds; review
   security/code and sign a version-code-3 APK with the existing beta certificate.

## Risks and delivery

Pairing and debugging ports differ. The pairing dialog must remain open (use split
screen). OEM policy may refuse grants. Socket timeouts and failures must leave no
active session, and a successful grant is insufficient without actual direct
collection. The new INTERNET permission is for local ADB, with no telemetry or
remote endpoints. Public beta history must survive the signed in-place update.
This task prepares a local APK; it does not publish a release or F-Droid update.
