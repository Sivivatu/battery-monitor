package io.github.loganrosen.wattdog.setup

import android.os.Build
import io.github.loganrosen.wattdog.BuildConfig
import io.github.muntashirakon.adb.AdbConnection
import io.github.muntashirakon.adb.PairingConnectionCtx
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.security.auth.DestroyFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/** A short-lived, loopback-only ADB client. Neither its key nor the pairing code is saved. */
class AndroidLocalAdbSession : LocalAdbSession {
    private var identity: Identity? = null
    private var pairing: PairingConnectionCtx? = null
    private var connection: AdbConnection? = null
    private var isClosed = false

    override suspend fun pair(port: Int, code: String) = runInterruptible(Dispatchers.IO) {
        check(!isClosed)
        require(port in PORT_RANGE && code.matches(Regex("[0-9]{6}")))
        val credentials = credentials()
        val client = PairingConnectionCtx(
            LOOPBACK,
            port,
            code.toByteArray(Charsets.US_ASCII),
            credentials.keyPair.private,
            credentials.certificate,
            DEVICE_NAME,
        )
        pairing = client
        try {
            client.start()
        } finally {
            client.close()
            pairing = null
        }
    }

    override suspend fun connect(port: Int) = runInterruptible(Dispatchers.IO) {
        check(!isClosed)
        require(port in PORT_RANGE)
        val credentials = checkNotNull(identity) { "Pair this setup session first." }
        val client = AdbConnection.create(
            LOOPBACK,
            port,
            credentials.keyPair.private,
            credentials.certificate,
            Build.VERSION.SDK_INT,
        )
        connection = client
        client.setDeviceName(DEVICE_NAME)
        if (!client.connect(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS, true)) {
            throw IOException("Local ADB connection timed out.")
        }
    }

    override suspend fun execute(command: String) = runInterruptible(Dispatchers.IO) {
        check(!isClosed)
        require(command in GRANT_COMMANDS) { "Only this app's battery permission grants are allowed." }
        val client = checkNotNull(connection) { "Connect this setup session first." }
        // The success marker is printed only when pm grant returns exit status zero.
        client.open("shell:$command && echo $SUCCESS_MARKER").use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(OUTPUT_CHUNK_BYTES)
            val input = stream.openInputStream()
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                if (output.size() + count > MAX_OUTPUT_BYTES) throw IOException("Unexpected permission response.")
                output.write(buffer, 0, count)
            }
            if (output.toString(Charsets.UTF_8.name()).trim() != SUCCESS_MARKER) {
                throw IOException("Android did not grant a required battery permission.")
            }
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        try {
            pairing?.close()
        } finally {
            try {
                connection?.close()
            } finally {
                try {
                    identity?.keyPair?.private?.destroy()
                } catch (_: DestroyFailedException) {
                    // Software key providers can refuse explicit destruction; release all references.
                }
                pairing = null
                connection = null
                identity = null
            }
        }
    }

    private fun credentials(): Identity = identity ?: newIdentity().also { identity = it }

    private fun newIdentity(): Identity {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(RSA_KEY_BITS) }.generateKeyPair()
        val name = X500Name("CN=Battery Monitor setup")
        val now = System.currentTimeMillis()
        val certificate = JcaX509v3CertificateBuilder(
            name,
            BigInteger(SERIAL_BITS, SecureRandom()).add(BigInteger.ONE),
            Date(now - CERTIFICATE_CLOCK_SKEW_MILLIS),
            Date(now + CERTIFICATE_VALIDITY_MILLIS),
            name,
            keyPair.public,
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
        return Identity(keyPair, JcaX509CertificateConverter().getCertificate(certificate))
    }

    private data class Identity(val keyPair: KeyPair, val certificate: X509Certificate)

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val DEVICE_NAME = "Battery Monitor setup"
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val RSA_KEY_BITS = 2048
        const val SERIAL_BITS = 160
        const val CERTIFICATE_CLOCK_SKEW_MILLIS = 3_600_000L
        const val CERTIFICATE_VALIDITY_MILLIS = 86_400_000L
        const val OUTPUT_CHUNK_BYTES = 1024
        const val MAX_OUTPUT_BYTES = 4096
        const val SUCCESS_MARKER = "BATTERY_MONITOR_GRANT_OK"
        val PORT_RANGE = 1..65535
        val GRANT_COMMANDS = setOf("DUMP", "PACKAGE_USAGE_STATS", "BATTERY_STATS").mapTo(mutableSetOf()) {
            "pm grant ${BuildConfig.APPLICATION_ID} android.permission.$it"
        }
    }
}
