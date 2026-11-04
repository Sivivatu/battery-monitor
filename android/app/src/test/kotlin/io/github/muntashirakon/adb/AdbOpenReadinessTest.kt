package io.github.muntashirakon.adb

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.lang.reflect.Field
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.cert.Certificate
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression for an ADB OKAY packet arriving before open() starts waiting on its stream. */
class AdbOpenReadinessTest {
    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun openReturnsWhenPeerReadyNotificationArrivesBeforeOpenWaits() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { server ->
                val peer = executor.submit {
                    server.accept().use { socket ->
                        socket.soTimeout = SOCKET_TIMEOUT_MILLIS
                        assertEquals(CNXN, read(socket).command)
                        write(socket, CNXN, PROTOCOL_VERSION, MAX_DATA, "device::\u0000".toByteArray())
                        val open = read(socket)
                        assertEquals(OPEN, open.command)
                        write(socket, OKAY, REMOTE_STREAM_ID, open.arg0)
                        assertEquals(CLSE, read(socket).command)
                    }
                }
                val identity = KeyPairGenerator.getInstance(RSA).apply { initialize(RSA_KEY_BITS) }.generateKeyPair()
                val certificate = TestCertificate(identity.public)
                AdbConnection.create(LOOPBACK, server.localPort, identity.private, certificate, ANDROID_API).use { client ->
                    assertTrue(client.connect(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS, true))
                    forceOpenFlushToWaitForPeerSignal(client, READY_FIELD)

                    val opened = executor.submit(Callable { client.open(SERVICE) })
                    val stream = try {
                        opened.get(OPEN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    } catch (_: TimeoutException) {
                        opened.cancel(true)
                        throw AssertionError("open() lost the peer's early OKAY notification")
                    }
                    assertNotNull(stream)
                    stream.close()
                    peer.get(SOCKET_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
                }
            }
        } finally {
            executor.shutdownNow()
            assertTrue("ADB test workers must stop", executor.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun openThrowsWhenPeerRejectsBeforeOpenWaits() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { server ->
                val peer = executor.submit {
                    server.accept().use { socket ->
                        socket.soTimeout = SOCKET_TIMEOUT_MILLIS
                        assertEquals(CNXN, read(socket).command)
                        write(socket, CNXN, PROTOCOL_VERSION, MAX_DATA, "device::\u0000".toByteArray())
                        val open = read(socket)
                        assertEquals(OPEN, open.command)
                        write(socket, CLSE, REMOTE_STREAM_ID, open.arg0)
                    }
                }
                val identity = KeyPairGenerator.getInstance(RSA).apply { initialize(RSA_KEY_BITS) }.generateKeyPair()
                val certificate = TestCertificate(identity.public)
                AdbConnection.create(LOOPBACK, server.localPort, identity.private, certificate, ANDROID_API).use { client ->
                    assertTrue(client.connect(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS, true))
                    forceOpenFlushToWaitForPeerSignal(client, CLOSED_FIELD)

                    val opened = executor.submit(Callable { client.open(SERVICE) })
                    try {
                        opened.get(OPEN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                        throw AssertionError("A rejected ADB stream must not open")
                    } catch (failure: java.util.concurrent.ExecutionException) {
                        assertTrue("Rejected stream must report ConnectException", failure.cause is ConnectException)
                    } catch (_: TimeoutException) {
                        opened.cancel(true)
                        throw AssertionError("open() lost the peer's early CLSE notification")
                    }
                    peer.get(SOCKET_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
                }
            }
        } finally {
            executor.shutdownNow()
            assertTrue("ADB test workers must stop", executor.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    /** Hold OPEN flush until the connection thread processes its response, before open() waits. */
    private fun forceOpenFlushToWaitForPeerSignal(connection: AdbConnection, readinessField: String) {
        val outputField = field(AdbConnection::class.java, "mPlainOutputStream")
        val delegate = outputField.get(connection) as OutputStream
        outputField.set(
            connection,
            object : OutputStream() {
                private val packet = ByteArrayOutputStreamCompat()
                private var openingStream: AdbStream? = null

                override fun write(value: Int) {
                    packet.write(value)
                    rememberOpeningStream()
                    delegate.write(value)
                }

                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    packet.write(bytes, offset, length)
                    rememberOpeningStream()
                    delegate.write(bytes, offset, length)
                }

                override fun flush() {
                    delegate.flush()
                    if (packet.command() == OPEN) {
                        val stream = requireNotNull(openingStream)
                        val state = field(AdbStream::class.java, readinessField)
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(READY_TIMEOUT_SECONDS)
                        while (!isSet(state, stream) && System.nanoTime() < deadline) Thread.yield()
                        assertTrue("Fake daemon's stream response must be processed before OPEN flush returns", isSet(state, stream))
                    }
                    packet.reset()
                    openingStream = null
                }

                private fun rememberOpeningStream() {
                    if (openingStream == null && packet.command() == OPEN) {
                        // Capture before writing: an early CLSE removes the stream from this map.
                        openingStream = (
                            field(AdbConnection::class.java, "mOpenedStreams").get(connection)
                                as Map<*, *>
                            ).values.single() as AdbStream
                    }
                }
            },
        )
    }

    private fun isSet(state: Field, stream: Any): Boolean {
        val value = state.get(stream)
        return if (value is java.util.concurrent.atomic.AtomicBoolean) value.get() else state.getBoolean(stream)
    }

    private class ByteArrayOutputStreamCompat {
        private val bytes = java.io.ByteArrayOutputStream()
        fun write(value: Int) = bytes.write(value)
        fun write(source: ByteArray, offset: Int, length: Int) = bytes.write(source, offset, length)
        fun reset() = bytes.reset()
        fun command(): Int = if (bytes.size() < PACKET_HEADER_BYTES) {
            0
        } else {
            ByteBuffer.wrap(bytes.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).int
        }
    }

    private class TestCertificate(private val publicKey: PublicKey) : Certificate("X.509") {
        override fun getEncoded(): ByteArray = byteArrayOf()
        override fun verify(key: PublicKey) = Unit
        override fun verify(key: PublicKey, provider: String) = Unit
        override fun getPublicKey(): PublicKey = publicKey
        override fun toString(): String = "Local ADB test identity"
    }

    private data class Packet(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

    private fun read(socket: Socket): Packet {
        val input = DataInputStream(socket.getInputStream())
        val header = ByteBuffer.wrap(ByteArray(PACKET_HEADER_BYTES).also(input::readFully)).order(ByteOrder.LITTLE_ENDIAN)
        val command = header.int
        val arg0 = header.int
        val arg1 = header.int
        val size = header.int
        header.int // checksum
        assertEquals(command.inv(), header.int)
        require(size in 0..MAX_DATA)
        return Packet(command, arg0, arg1, ByteArray(size).also(input::readFully))
    }

    private fun write(socket: Socket, command: Int, arg0: Int, arg1: Int, payload: ByteArray = byteArrayOf()) {
        val output = DataOutputStream(socket.getOutputStream())
        output.writeInt(Integer.reverseBytes(command))
        output.writeInt(Integer.reverseBytes(arg0))
        output.writeInt(Integer.reverseBytes(arg1))
        output.writeInt(Integer.reverseBytes(payload.size))
        output.writeInt(Integer.reverseBytes(payload.sumOf { it.toInt() and 0xff }))
        output.writeInt(Integer.reverseBytes(command.inv()))
        output.write(payload)
        output.flush()
    }

    private fun field(type: Class<*>, name: String): Field = type.getDeclaredField(name).apply { isAccessible = true }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val RSA = "RSA"
        const val RSA_KEY_BITS = 2048
        const val ANDROID_API = 34
        const val PROTOCOL_VERSION = 0x01000001
        const val MAX_DATA = 4096
        const val REMOTE_STREAM_ID = 1
        const val PACKET_HEADER_BYTES = 24
        const val CONNECT_TIMEOUT_SECONDS = 2L
        const val READY_TIMEOUT_SECONDS = 2L
        const val OPEN_TIMEOUT_MILLIS = 3_000L
        const val READY_FIELD = "mWriteReady"
        const val CLOSED_FIELD = "mIsClosed"
        const val SOCKET_TIMEOUT_MILLIS = 5_000
        const val TEST_TIMEOUT_MILLIS = 10_000L
        const val CNXN = 0x4e584e43
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val CLSE = 0x45534c43
        const val SERVICE = "shell:echo ready"
    }
}
