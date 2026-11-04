package io.github.loganrosen.wattdog.setup

import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** No host field: setup can only connect back to this device. */
class PairingRequest private constructor(val pairingPort: Int, val code: String, val connectionPort: Int) {
    companion object {
        fun parse(pairingPort: String, code: String, connectionPort: String): PairingRequest {
            require(code.matches(Regex("[0-9]{6}"))) { "Enter the six-digit pairing code shown by Android." }
            return PairingRequest(port(pairingPort), code, port(connectionPort))
        }

        private fun port(value: String): Int {
            require(portError(value) == null) { requireNotNull(portError(value)) }
            return value.toInt()
        }

        fun portError(value: String): String? {
            val valid = value.matches(Regex("[0-9]{1,5}")) && value.toIntOrNull()?.let { it in 1..65535 } == true
            return if (valid) null else "Enter a port from 1 to 65535."
        }
    }
}

data class AdbSetupState(val running: Boolean = false, val message: String? = null, val verified: Boolean = false)

interface LocalAdbSession : Closeable {
    suspend fun pair(port: Int, code: String)
    suspend fun connect(port: Int)
    suspend fun execute(command: String)
}

/** Pair/grant is temporary; only a successful read by the ordinary app proves collection works. */
class AdbSetupController(
    applicationId: String,
    private val sessionFactory: () -> LocalAdbSession,
    private val timeoutMillis: Long = 60_000,
    private val verifyDirect: suspend () -> Unit,
) {
    private val commands: List<String>

    init {
        require(applicationId.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")))
        commands = listOf("DUMP", "PACKAGE_USAGE_STATS", "BATTERY_STATS").map {
            "pm grant $applicationId android.permission.$it"
        }
    }

    suspend fun enable(request: PairingRequest, onProgress: (String) -> Unit) = withTimeout(timeoutMillis) {
        onProgress("Preparing temporary ADB credentials…")
        val session = atStage(AdbSetupStage.PREPARING) { withContext(Dispatchers.IO) { sessionFactory() } }
        var primaryFailure: Throwable? = null
        try {
            onProgress("Pairing with this phone… Keep Android's pairing dialog open.")
            atStage(AdbSetupStage.PAIRING) { session.pair(request.pairingPort, request.code) }
            onProgress("Connecting to this phone's debugging port…")
            atStage(AdbSetupStage.CONNECTING) { session.connect(request.connectionPort) }
            onProgress("Granting battery diagnostic permissions…")
            atStage(AdbSetupStage.GRANTING) { commands.take(2).forEach { session.execute(it) } }
            try {
                // Some Android builds need this extra development grant; actual direct reads decide success.
                session.execute(commands.last())
            } catch (_: IOException) {
                onProgress("Additional battery permission was refused. Checking whether direct collection works…")
            }
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                withContext(NonCancellable + Dispatchers.IO) { session.close() }
            } catch (cleanupFailure: Exception) {
                // Preserve the original stage or cancellation if closing also fails.
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure)
                } else {
                    throw AdbSetupException(AdbSetupStage.DISCONNECTING, cleanupFailure)
                }
            }
        }
        onProgress("ADB disconnected. Checking direct battery collection…")
        atStage(AdbSetupStage.VERIFYING) { verifyDirect() }
    }

    suspend fun verify() = withTimeout(timeoutMillis) { atStage(AdbSetupStage.VERIFYING) { verifyDirect() } }

    private suspend fun <T> atStage(stage: AdbSetupStage, action: suspend () -> T): T = try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        throw AdbSetupException(stage, failure)
    }
}
