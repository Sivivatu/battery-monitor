package io.github.loganrosen.wattdog.setup

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.security.GeneralSecurityException
import javax.net.ssl.SSLException

enum class AdbSetupStage { PREPARING, PAIRING, CONNECTING, GRANTING, DISCONNECTING, VERIFYING }

/** Keep the cause for diagnosis, but expose only fixed messages without codes or device output. */
class AdbSetupException(val stage: AdbSetupStage, cause: Exception) : IOException("ADB setup failed during $stage.", cause) {
    val userMessage: String
        get() = when (stage) {
            AdbSetupStage.PREPARING -> "Could not prepare ADB setup. Restart the app and try again."

            AdbSetupStage.PAIRING -> pairingMessage()

            AdbSetupStage.CONNECTING ->
                "Pairing succeeded, but the debugging connection failed. Recheck the Connection port on Android's main Wireless debugging page."

            AdbSetupStage.GRANTING ->
                "Pairing and connection succeeded, but Android refused diagnostic permissions. Try Verify direct collection to check existing grants."

            AdbSetupStage.DISCONNECTING ->
                "Setup could not confirm ADB disconnected. Turn Wireless debugging off, then try Verify direct collection."

            AdbSetupStage.VERIFYING ->
                "Direct battery collection could not be verified. Check the diagnostic permissions and try Verify direct collection again."
        }

    private fun pairingMessage(): String = when (cause) {
        is ConnectException -> "Could not reach the pairing port. $FRESH_PAIRING"
        is SocketTimeoutException -> "Pairing timed out. $FRESH_PAIRING"
        is SSLException -> "The secure pairing connection failed. $FRESH_PAIRING"
        is GeneralSecurityException -> "Secure pairing could not be initialized on this device. Restart the app and try again."
        else -> "Pairing failed before Android accepted this app. $FRESH_PAIRING"
    }

    private companion object {
        const val FRESH_PAIRING = "Open Pair device with pairing code again and use its new port and six-digit code. " +
            "Keep that dialog open beside this app in split screen until pairing finishes."
    }
}
