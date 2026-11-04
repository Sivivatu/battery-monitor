# LibADB Android source

The required Java client classes are copied from
[MuntashirAkon/libadb-android](https://github.com/MuntashirAkon/libadb-android),
version **3.1.1**, commit **c849886ebc6d48e7b46d967e78a6bb65c90c3b74**.
The unused connection manager, mDNS discovery, legacy PRNG helper, examples, and
other Android platform helpers are omitted. Original SPDX identifiers and
copyright notices are retained. Applicable upstream license texts are in
`LICENSES/`; the combined app is distributed as GPL-3.0-or-later.

This local source module deliberately does not fetch and execute new protocol
code during the app's setup. Its pinned dependencies are AndroidX annotations
1.9.1, Bouncy Castle bcprov-jdk15to18 1.81, Conscrypt Android 2.8.0, and
SPAKE2 Android 2.2.1. SPAKE2 is LGPL-3.0, and its corresponding source is available
at <https://github.com/MuntashirAkon/spake2-java/tree/2.2.1>. Conscrypt is Apache-2.0,
with its bundled native component notices supplied by that dependency. The APK
source distribution must preserve these sources, notices, and dependency versions.

## Battery Monitor modifications

- `AdbConnection`: socket connection and read timeouts of ten seconds; close waits
  at most one second for its worker; failed socket initialization closes the
  socket; clamp the negotiated packet size to the supported local limit; remove
  handshake debug output and exception stack printing.
- `PairingConnectionCtx`: retain ownership of the raw socket before connect/TLS,
  bound connection and read waits to ten seconds, and close safely even before
  streams or authentication exist; clear the pairing password on close and wipe temporary password/key-material arrays; remove
  peer-identifier and handshake debug logging; call the public bundled Conscrypt
  key-export API instead of reflective hidden platform APIs.
- `SslUtils`: create a fresh SSL context for each credential pair and use the
  bundled Conscrypt provider directly. Upstream's process-wide cached context
  retained a previous attempt's private key across retries. No provider debug
  output is emitted.
- `AdbStream` / `AdbInputStream`: drain queued response data and terminate at peer
  EOF rather than waiting indefinitely; retain unsigned single-byte values.

ADB intentionally uses a pairing-derived trust model rather than Web PKI: its
TLS certificate trust manager accepts the locally paired daemon's certificate.
The application restricts this library to IPv4 loopback and does not expose a
remote host selector, discovery, a terminal, or arbitrary shell commands. ADB
credentials and pairing codes remain in memory for one setup attempt only.
