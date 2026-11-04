package io.github.loganrosen.wattdog.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PortValidationTest {
    @Test fun acceptsInclusiveAndroidPortBoundaries() {
        assertNull(PairingRequest.portError("1"))
        assertNull(PairingRequest.portError("65535"))
        assertNull(PairingRequest.portError("37743"))
    }

    @Test fun rejectsInvalidPortsWithAnActionableFixedMessage() {
        val error = PairingRequest.portError("")
        assertNotNull(error)
        assertEquals("Enter a port from 1 to 65535.", error)

        for (value in listOf("0", "65536", "98377", "100000", "99999999999999999999999999999", "-1", "1.5", "80/tcp", "id")) {
            assertEquals("Port feedback should be consistent", error, PairingRequest.portError(value))
        }
    }

    @Test fun requestParserUsesSamePortValidationWithoutTruncatingInput() {
        val error = PairingRequest.portError("98377")
        assertNotNull(error)
        val failure = assertThrows(IllegalArgumentException::class.java) {
            PairingRequest.parse("41953", "012345", "98377")
        }
        assertEquals(error, failure.message)
    }
}
