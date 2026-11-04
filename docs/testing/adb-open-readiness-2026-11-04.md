# ADB stream readiness race — 2026-11-04

PR #2 review identified a lost-notification race in the vendored
`AdbConnection.open()`: the receive thread could handle OKAY before the caller
entered its unconditional wait. A fast permission command could therefore finish
while setup waited until its overall timeout.

The regression uses the actual loopback ADB connection and receive thread, with a
fake daemon. A test-only output wrapper holds OPEN's flush until the receive
thread has set its real readiness state. This forces the original missed-wakeup
ordering without statistical stress or arbitrary sleeps.

The first focused run against the original code failed as expected:
`1 test completed, 1 failed`, with
`open() lost the peer's early OKAY notification`. Evidence is retained locally in
`/tmp/battery-adb-open-red.log`.

The fix checks ready/closed state in a loop under the same stream monitor that
the receiver holds when it changes readiness and notifies. An early notification
is harmless because the state already satisfies the predicate; a spurious wakeup
does not imply readiness. Sending OPEN can remain outside that monitor: checking
the persistent predicate and waiting are atomic relative to the receive thread.
A second case verifies that an early CLSE produces ConnectException.

The public beta retains the name 0.1.0-beta.3 and existing signing identity, with
version code 5. This ensures F-Droid offers the correction to phones that already
installed the unpublished version-code-4 beta.3 preview.

Reference: [Java 17 Object.wait contract](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#wait()).

Focused GREEN command:

```sh
ANDROID_HOME=/home/ubuntu/Android/Sdk android/gradlew -p android \
  :app:testDebugUnitTest --tests 'io.github.muntashirakon.adb.AdbOpenReadinessTest' \
  --no-daemon --no-parallel --max-workers=1
```

Result: both early-OKAY and early-CLSE cases passed (2 tests, zero failures,
errors, or skips). Code and targeted security reviews approved the predicate
change. A fixture compile issue was corrected by retaining the stream reference
before writing OPEN; a rejected stream can be removed from the connection's map
before flush returns. The fixture retains no real pairing codes or credentials.

Full regression result: `BUILD SUCCESSFUL in 3m 41s`, including Spotless, all 28
app JVM tests, all 110 parser JVM tests, coverage reports, debug app/test APKs,
debug lint and release lint. Publication guards (14 tests) and actionlint also
passed. Physical-phone Settings pairing remains outside this automated proof.
