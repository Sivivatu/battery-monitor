package io.github.muntashirakon.adb

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.DataInputStream
import java.math.BigInteger
import java.net.InetAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.DestroyFailedException
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClientCertificateInstrumentedTest {
    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun productionClientContextSendsItsCertificateToMutualTlsServer() = runBlocking {
        val clientIdentity = newIdentity("Battery Monitor local ADB client")
        val serverIdentity = newIdentity("Battery Monitor local ADB server")
        val executor = Executors.newSingleThreadExecutor()
        try {
            val serverContext = serverContext(serverIdentity, clientIdentity.certificate)
            (
                serverContext.serverSocketFactory.createServerSocket(
                    0,
                    1,
                    InetAddress.getByName(LOOPBACK),
                ) as SSLServerSocket
                ).use { listener ->
                listener.soTimeout = SOCKET_TIMEOUT_MILLIS
                listener.needClientAuth = true
                listener.enabledProtocols = arrayOf(TLS_VERSION)

                val server = executor.submit(
                    Callable<Boolean> {
                        val acceptedSocket = listener.accept() as SSLSocket
                        acceptedSocket.use { socket ->
                            socket.soTimeout = SOCKET_TIMEOUT_MILLIS
                            socket.startHandshake()
                            val peerCertificate = socket.session.peerCertificates.single() as X509Certificate
                            val payload = ByteArray(REQUEST.size)
                            DataInputStream(socket.getInputStream()).readFully(payload)
                            assertEquals("The paired client certificate must reach the server", clientIdentity.certificate, peerCertificate)
                            assertArrayEquals(REQUEST, payload)
                            socket.getOutputStream().apply {
                                write(RESPONSE)
                                flush()
                            }
                            true
                        }
                    },
                )

                val clientContext = SslUtils.getSslContext(clientIdentity.identity)
                val clientSocket = clientContext.socketFactory.createSocket(LOOPBACK, listener.localPort) as SSLSocket
                clientSocket.use { client ->
                    client.soTimeout = SOCKET_TIMEOUT_MILLIS
                    // This context's provider is the one ADB uses; record it for diagnosis if key selection fails.
                    val clientSocketClass = client.javaClass.name
                    try {
                        client.startHandshake()
                    } catch (failure: Exception) {
                        throw AssertionError("TLS handshake failed for client socket class $clientSocketClass", failure)
                    }
                    client.getOutputStream().apply {
                        write(REQUEST)
                        flush()
                    }
                    assertArrayEquals(RESPONSE, client.getInputStream().readNBytes(RESPONSE.size))
                    assertTrue("Client socket class: $clientSocketClass", client.session.isValid)
                }

                assertTrue(
                    "Mutual TLS peer did not finish: ${clientContext.provider.name}",
                    server.get(SOCKET_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS),
                )
            }
        } finally {
            try {
                destroyIdentity(clientIdentity)
                destroyIdentity(serverIdentity)
            } finally {
                executor.shutdownNow()
                assertTrue("Mutual TLS server thread did not stop", executor.awaitTermination(2, TimeUnit.SECONDS))
            }
        }
    }

    private fun destroyIdentity(identity: Identity) {
        try {
            identity.identity.destroy()
        } catch (_: DestroyFailedException) {
            // Android software keys may refuse explicit destruction; the fixture releases its references.
        }
    }

    private fun serverContext(server: Identity, trustedClient: X509Certificate): SSLContext = SSLContext.getInstance(TLS_VERSION).apply {
        init(
            arrayOf(TestServerKeyManager(server)),
            arrayOf(TrustOnlyClientCertificate(trustedClient)),
            SecureRandom(),
        )
    }

    private fun newIdentity(commonName: String): Identity {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(RSA_KEY_BITS) }.generateKeyPair()
        val name = X500Name("CN=$commonName")
        val now = System.currentTimeMillis()
        val certificate = JcaX509v3CertificateBuilder(
            name,
            BigInteger(SERIAL_BITS, SecureRandom()).add(BigInteger.ONE),
            Date(now - CERTIFICATE_CLOCK_SKEW_MILLIS),
            Date(now + CERTIFICATE_VALIDITY_MILLIS),
            name,
            keyPair.public,
        ).build(JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keyPair.private))
        val x509 = JcaX509CertificateConverter().getCertificate(certificate)
        return Identity(io.github.muntashirakon.adb.KeyPair(keyPair.private, x509), x509)
    }

    private data class Identity(val identity: KeyPair, val certificate: X509Certificate)

    private class TestServerKeyManager(private val identity: Identity) : X509ExtendedKeyManager() {
        override fun getClientAliases(keyType: String?, issuers: Array<out java.security.Principal>?): Array<String>? = null

        override fun chooseClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out java.security.Principal>?,
            socket: Socket?,
        ): String? = null

        override fun getServerAliases(keyType: String?, issuers: Array<out java.security.Principal>?): Array<String>? =
            if (keyType == RSA_KEY_TYPE) arrayOf(SERVER_ALIAS) else null

        override fun chooseServerAlias(keyType: String?, issuers: Array<out java.security.Principal>?, socket: Socket?): String? =
            if (keyType == RSA_KEY_TYPE) SERVER_ALIAS else null

        override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
            if (alias == SERVER_ALIAS) arrayOf(identity.certificate) else null

        override fun getPrivateKey(alias: String?): PrivateKey? = if (alias == SERVER_ALIAS) identity.identity.privateKey else null

        override fun chooseEngineClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out java.security.Principal>?,
            engine: javax.net.ssl.SSLEngine?,
        ): String? = null

        override fun chooseEngineServerAlias(
            keyType: String?,
            issuers: Array<out java.security.Principal>?,
            engine: javax.net.ssl.SSLEngine?,
        ): String? = if (keyType == RSA_KEY_TYPE) SERVER_ALIAS else null
    }

    private class TrustOnlyClientCertificate(private val trustedClient: X509Certificate) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain?.any { it == trustedClient } != true) {
                throw CertificateException("Peer did not present the expected test identity")
            }
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty()) throw CertificateException("Peer did not present a server certificate")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(trustedClient)
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val TLS_VERSION = "TLSv1.3"
        const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        const val RSA_KEY_TYPE = "RSA"
        const val SERVER_ALIAS = "test-server"
        const val RSA_KEY_BITS = 2048
        const val SERIAL_BITS = 160
        const val CERTIFICATE_CLOCK_SKEW_MILLIS = 60_000L
        const val CERTIFICATE_VALIDITY_MILLIS = 60_000L
        const val SOCKET_TIMEOUT_MILLIS = 5_000
        const val TEST_TIMEOUT_MILLIS = 30_000L
        val REQUEST = "local-adb-client-auth".toByteArray(Charsets.US_ASCII)
        val RESPONSE = "client-certificate-received".toByteArray(Charsets.US_ASCII)
    }
}
