package io.github.loganrosen.wattdog.setup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbSetupTest {
    @Test fun setupGrantsOnlyOwnPermissionsAndDisconnectsBeforeVerification() = runBlocking {
        val session = FakeSession()
        val controller = AdbSetupController("io.github.sivivatu.batterymonitor", { session }) {
            assertTrue("ADB must be closed before direct verification", session.closed)
            session.events += "verify"
        }
        controller.enable(PairingRequest.parse("37123", "012345", "40123")) {}
        assertEquals(
            listOf(
                "pair:37123:012345",
                "connect:40123",
                "pm grant io.github.sivivatu.batterymonitor android.permission.DUMP",
                "pm grant io.github.sivivatu.batterymonitor android.permission.PACKAGE_USAGE_STATS",
                "pm grant io.github.sivivatu.batterymonitor android.permission.BATTERY_STATS",
                "close",
                "verify",
            ),
            session.events,
        )
    }

    @Test fun malformedPortsAndCodesAreRejectedBeforeConnecting() {
        for (port in listOf("", "0", "65536", "-1", "123;id", "1.5")) {
            assertThrows(IllegalArgumentException::class.java) { PairingRequest.parse(port, "012345", "40123") }
            assertThrows(IllegalArgumentException::class.java) { PairingRequest.parse("37123", "012345", port) }
        }
        for (code in listOf("", "12345", "1234567", "12 345", "１２３４５６", "123;id")) {
            assertThrows(IllegalArgumentException::class.java) { PairingRequest.parse("37123", code, "40123") }
        }
    }

    @Test fun rejectedPairingConnectionOrGrantClosesSessionAndNeverVerifies() = runBlocking {
        for (failureAt in listOf("pair", "connect", "DUMP", "PACKAGE_USAGE_STATS")) {
            val session = FakeSession(failureAt)
            var verified = false
            val controller = AdbSetupController("example.battery", { session }) { verified = true }
            try {
                controller.enable(PairingRequest.parse("37123", "012345", "40123")) {}
                throw AssertionError("The failed stage must not be reported as successful")
            } catch (_: java.io.IOException) {
                assertTrue(session.closed)
                assertFalse(verified)
                assertEquals("close", session.events.last())
            }
        }
    }

    @Test fun pairAndConnectionFailuresIdentifySafeStageAndCloseSession() = runBlocking {
        val cases = listOf(
            "pair" to AdbSetupStage.PAIRING,
            "connect" to AdbSetupStage.CONNECTING,
        )
        for ((failureAt, expectedStage) in cases) {
            val pairingCode = "807261"
            val rejectedSession = FakeSession(failureAt)
            val controller = AdbSetupController("example.battery", { rejectedSession }) {}
            val failure = assertThrows(AdbSetupException::class.java) {
                runBlocking {
                    controller.enable(PairingRequest.parse("41953", pairingCode, "37743")) {}
                }
            }

            assertEquals(expectedStage, failure.stage)
            assertEquals("Rejected", failure.cause?.message)
            assertTrue(rejectedSession.closed)
            assertFalse(failure.userMessage.contains(pairingCode))
            assertFalse(failure.userMessage.contains("Rejected"))
        }
    }

    @Test fun grantAndVerificationFailuresIdentifyStageAndCleanup() = runBlocking {
        val failedGrantSession = FakeSession("DUMP")
        val grantFailure = assertThrows(AdbSetupException::class.java) {
            runBlocking {
                AdbSetupController("example.battery", { failedGrantSession }) {}.enable(
                    PairingRequest.parse("41953", "807261", "37743"),
                ) {}
            }
        }
        assertEquals(AdbSetupStage.GRANTING, grantFailure.stage)
        assertTrue(failedGrantSession.closed)
        assertFalse(grantFailure.userMessage.contains("807261"))

        val verifiedAndClosed = FakeSession()
        val verifyFailure = assertThrows(AdbSetupException::class.java) {
            runBlocking {
                AdbSetupController("example.battery", { verifiedAndClosed }) {
                    assertTrue("ADB must close before direct reads", verifiedAndClosed.closed)
                    throw SecurityException("private diagnostic detail")
                }.enable(PairingRequest.parse("41953", "807261", "37743")) {}
            }
        }
        assertEquals(AdbSetupStage.VERIFYING, verifyFailure.stage)
        assertTrue(verifiedAndClosed.closed)
        assertFalse(verifyFailure.userMessage.contains("private diagnostic detail"))
    }

    @Test fun sessionCreationFailureHasSafePreparingStage() = runBlocking {
        val failure = assertThrows(AdbSetupException::class.java) {
            runBlocking {
                AdbSetupController("example.battery", { throw java.io.IOException("secret setup detail") }) {}.enable(
                    PairingRequest.parse("41953", "807261", "37743"),
                ) {}
            }
        }
        assertEquals(AdbSetupStage.PREPARING, failure.stage)
        assertFalse(failure.userMessage.contains("secret setup detail"))
        assertFalse(failure.userMessage.contains("807261"))
    }

    @Test fun optionalCompatibilityGrantCanBeDeniedWhenDirectCollectionWorks() = runBlocking {
        val session = FakeSession("BATTERY_STATS")
        var verified = false
        val controller = AdbSetupController("example.battery", { session }) {
            assertTrue(session.closed)
            verified = true
        }
        controller.enable(PairingRequest.parse("37123", "012345", "40123")) {}
        assertTrue(verified)
    }

    @Test fun cleanupFailureDoesNotHideTheOriginalPairingFailure() = runBlocking {
        val session = FakeSession("pair", failClose = true)
        val failure = assertThrows(AdbSetupException::class.java) {
            runBlocking {
                AdbSetupController("example.battery", { session }) {}.enable(
                    PairingRequest.parse("41953", "807261", "37743"),
                ) {}
            }
        }
        assertEquals(AdbSetupStage.PAIRING, failure.stage)
        assertEquals("Rejected", failure.cause?.message)
        assertEquals("Cleanup failed", failure.suppressed.single().message)
        assertTrue(session.closed)
    }

    @Test fun cleanupOnlyFailureReportsDisconnectingAndNeverVerifies() = runBlocking {
        val session = FakeSession(failClose = true)
        var verified = false
        val failure = assertThrows(AdbSetupException::class.java) {
            runBlocking {
                AdbSetupController("example.battery", { session }) { verified = true }.enable(
                    PairingRequest.parse("41953", "807261", "37743"),
                ) {}
            }
        }
        assertEquals(AdbSetupStage.DISCONNECTING, failure.stage)
        assertTrue(session.closed)
        assertFalse(verified)
        assertFalse(failure.userMessage.contains("Cleanup failed"))
    }

    @Test fun directVerificationFailureIsNotReportedAsSuccessfulSetup() = runBlocking {
        val session = FakeSession()
        val controller = AdbSetupController("example.battery", { session }) {
            throw SecurityException("Direct permission denied")
        }
        val failure = try {
            controller.enable(PairingRequest.parse("37123", "012345", "40123")) {}
            throw AssertionError("A grant is insufficient without actual collection")
        } catch (failure: AdbSetupException) {
            failure
        }
        assertEquals(AdbSetupStage.VERIFYING, failure.stage)
        assertTrue(session.closed)
        assertTrue(failure.cause is SecurityException)
        assertEquals("Direct permission denied", failure.cause?.message)
        assertFalse(failure.userMessage.contains("Direct permission denied"))
    }

    @Test fun verifyAfterWirelessDebuggingIsOffReportsIndependentVerifyFailure() = runBlocking {
        val controller = AdbSetupController("example.battery", { throw AssertionError("Verify does not use ADB") }) {
            throw SecurityException("private diagnostic output")
        }
        val failure = try {
            controller.verify()
            throw AssertionError("A failed direct read must report a failure")
        } catch (failure: AdbSetupException) {
            failure
        }

        assertEquals(AdbSetupStage.VERIFYING, failure.stage)
        assertTrue(failure.cause is SecurityException)
        assertEquals("private diagnostic output", failure.cause?.message)
        assertFalse(failure.userMessage.contains("private diagnostic output"))
    }

    @Test fun timeoutClosesSessionAndDoesNotVerify() = runBlocking {
        val session = FakeSession(stall = true)
        var verified = false
        val controller = AdbSetupController("example.battery", { session }, timeoutMillis = 100) { verified = true }
        try {
            controller.enable(PairingRequest.parse("37123", "012345", "40123")) {}
            throw AssertionError("Stalled pairing must time out")
        } catch (_: TimeoutCancellationException) {
            assertTrue(session.closed)
            assertFalse(verified)
        }
    }

    @Test fun cancellingSetupClosesSessionAndDoesNotVerify() = runBlocking {
        val session = FakeSession(stall = true)
        var verified = false
        val controller = AdbSetupController("example.battery", { session }) { verified = true }
        val job = launch { controller.enable(PairingRequest.parse("37123", "012345", "40123")) {} }
        session.pairingStarted.await()
        job.cancelAndJoin()
        assertTrue(session.closed)
        assertFalse(verified)
    }

    @Test fun verifyingAfterDebuggingIsOffNeverCreatesAnAdbSession() = runBlocking {
        var verified = false
        val controller = AdbSetupController("example.battery", { throw AssertionError("ADB is unnecessary") }) { verified = true }
        controller.verify()
        assertTrue(verified)
    }

    @Test fun packageNamesCannotInjectAdditionalCommands() {
        for (applicationId in listOf("bad;id", "foo.bar && id", "", "foo\nbar")) {
            assertThrows(IllegalArgumentException::class.java) { AdbSetupController(applicationId, { FakeSession() }) {} }
        }
    }

    private class FakeSession(
        private val failAt: String? = null,
        private val stall: Boolean = false,
        private val failClose: Boolean = false,
    ) : LocalAdbSession {
        val events = mutableListOf<String>()
        var closed = false
        val pairingStarted = CompletableDeferred<Unit>()
        override suspend fun pair(port: Int, code: String) {
            events += "pair:$port:$code"
            pairingStarted.complete(Unit)
            if (failAt == "pair") throw java.io.IOException("Rejected")
            if (stall) delay(Long.MAX_VALUE)
        }
        override suspend fun connect(port: Int) {
            events += "connect:$port"
            if (failAt == "connect") throw java.io.IOException("Rejected")
        }
        override suspend fun execute(command: String) {
            events += command
            if (failAt != null && command.endsWith(".$failAt")) throw java.io.IOException("Rejected")
        }
        override fun close() {
            closed = true
            events += "close"
            if (failClose) throw java.io.IOException("Cleanup failed")
        }
    }
}
