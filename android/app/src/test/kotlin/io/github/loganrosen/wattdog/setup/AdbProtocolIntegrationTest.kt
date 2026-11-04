package io.github.loganrosen.wattdog.setup

import io.github.muntashirakon.adb.AdbConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbProtocolIntegrationTest {
    @Test(timeout = 10_000)
    fun responseDrainsAndReachesEofWhenPeerClosesOnlyTheStream() {
        val executor = Executors.newSingleThreadExecutor()
        val releasePeer = CountDownLatch(1)
        try {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                val peer = executor.submit {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        assertEquals(CNXN, read(socket).command)
                        write(socket, CNXN, 0x01000001, 4096, "device::\u0000".toByteArray())
                        val request = read(socket)
                        assertEquals(OPEN, request.command)
                        assertEquals("shell:echo test\u0000", request.payload.toString(Charsets.UTF_8))
                        write(socket, OKAY, 1, request.arg0)
                        write(socket, WRTE, 1, request.arg0, byteArrayOf(-1, 42))
                        assertEquals(OKAY, read(socket).command)
                        write(socket, CLSE, 1, request.arg0)
                        releasePeer.await(5, TimeUnit.SECONDS)
                    }
                }
                val identity = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
                val certificate = object : Certificate("X.509") {
                    override fun getEncoded() = byteArrayOf()
                    override fun verify(key: PublicKey) = Unit
                    override fun verify(key: PublicKey, provider: String) = Unit
                    override fun getPublicKey() = identity.public
                    override fun toString() = "Local test identity"
                }
                AdbConnection.create("127.0.0.1", server.localPort, identity.private, certificate, 34).use { client ->
                    assertTrue(client.connect(3, TimeUnit.SECONDS, true))
                    client.open("shell:echo test").use { stream ->
                        val input = stream.openInputStream()
                        assertEquals(255, input.read())
                        assertArrayEquals(byteArrayOf(42), input.readBytes())
                        assertEquals(-1, input.read())
                    }
                }
                releasePeer.countDown()
                peer.get(3, TimeUnit.SECONDS)
            }
        } finally {
            releasePeer.countDown()
            executor.shutdownNow()
        }
    }

    private data class Packet(val command: Int, val arg0: Int, val payload: ByteArray)

    private fun read(socket: Socket): Packet {
        val input = java.io.DataInputStream(socket.getInputStream())
        val header = ByteBuffer.wrap(ByteArray(24).also { input.readFully(it) }).order(ByteOrder.LITTLE_ENDIAN)
        val command = header.int
        val arg0 = header.int
        header.int
        val size = header.int
        require(size in 0..4096)
        return Packet(command, arg0, ByteArray(size).also { input.readFully(it) })
    }

    private fun write(socket: Socket, command: Int, arg0: Int, arg1: Int, payload: ByteArray = byteArrayOf()) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(arg0).putInt(arg1).putInt(payload.size)
            .putInt(payload.sumOf { it.toInt() and 255 }).putInt(command.inv()).array()
        socket.getOutputStream().apply {
            write(header)
            write(payload)
            flush()
        }
    }

    private companion object {
        const val CNXN = 0x4e584e43
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val WRTE = 0x45545257
        const val CLSE = 0x45534c43
    }
}
