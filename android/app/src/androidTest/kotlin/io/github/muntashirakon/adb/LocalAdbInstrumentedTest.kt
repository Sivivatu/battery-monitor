package io.github.muntashirakon.adb

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.loganrosen.wattdog.setup.AndroidLocalAdbSession
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAdbInstrumentedTest {
    @Test fun loopbackConnectionWorksWithoutLocalNetworkPermission() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            Socket("127.0.0.1", server.localPort).use { client ->
                server.accept().use { peer ->
                    client.getOutputStream().write(42)
                    assertEquals(42, peer.getInputStream().read())
                }
            }
        }
    }

    @Test fun nativePairingWithMatchingCodesProducesInteroperableKeys() {
        val password = "012345".toByteArray()
        val alice = checkNotNull(PairingAuthCtx.createAlice(password))
        val bob = checkNotNull(PairingAuthCtx.createBob(password))
        try {
            assertTrue(alice.initCipher(bob.msg))
            assertTrue(bob.initCipher(alice.msg))
            val plaintext = "local setup identity".toByteArray()
            val ciphertext = checkNotNull(alice.encrypt(plaintext))
            assertArrayEquals(plaintext, bob.decrypt(ciphertext))
        } finally {
            alice.destroy()
            bob.destroy()
        }
    }

    @Test fun nativePairingWithDifferentCodesRejectsPeerIdentity() {
        val alice = checkNotNull(PairingAuthCtx.createAlice("012345".toByteArray()))
        val bob = checkNotNull(PairingAuthCtx.createBob("543210".toByteArray()))
        try {
            assertTrue(alice.initCipher(bob.msg))
            assertTrue(bob.initCipher(alice.msg))
            assertNull(bob.decrypt(checkNotNull(alice.encrypt("local setup identity".toByteArray()))))
        } finally {
            alice.destroy()
            bob.destroy()
        }
    }

    @Test fun stalledTlsPairingTimesOutAndClosesItsSocket() = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 15_000
                val peerClosed = executor.submit<Boolean> {
                    server.accept().use { peer ->
                        peer.soTimeout = 15_000
                        val input = peer.getInputStream()
                        while (input.read() != -1) Unit
                        true
                    }
                }
                var failed = false
                try {
                    AndroidLocalAdbSession().use { it.pair(server.localPort, "012345") }
                } catch (_: java.io.IOException) {
                    failed = true
                }
                assertTrue("A peer that never completes TLS must fail", failed)
                assertTrue("Failed pairing must close the socket", peerClosed.get(20, TimeUnit.SECONDS))
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
