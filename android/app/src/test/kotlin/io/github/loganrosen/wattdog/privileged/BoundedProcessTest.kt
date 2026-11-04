package io.github.loganrosen.wattdog.privileged

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedProcessTest {
    @Test fun returnsCompleteOutputFromSuccessfulProcess() = runBlocking {
        val process = ProcessBuilder("printf", "diagnostic output").start()
        assertEquals("diagnostic output", readDumpProcess(process, 1000) { it.readText() })
    }

    @Test fun stalledProcessIsKilledAtItsDeadline() = runBlocking {
        val process = ProcessBuilder("sleep", "30").start()
        try {
            readDumpProcess(process, 100) { it.readText() }
            throw AssertionError("A stalled process must fail")
        } catch (_: IOException) {
            assertTrue(process.waitFor(2, TimeUnit.SECONDS))
            assertFalse(process.isAlive)
        } finally {
            process.destroyForcibly()
        }
    }

    @Test fun cancellationBeforeReadStartsStillTerminatesItsProcess() = runBlocking {
        val process = ProcessBuilder("sleep", "30").start()
        try {
            val read = launch(start = CoroutineStart.UNDISPATCHED) {
                coroutineContext.cancel()
                readDumpProcess(process, 30_000) { it.readText() }
            }
            read.join()
            assertTrue(process.waitFor(2, TimeUnit.SECONDS))
            assertFalse(process.isAlive)
        } finally {
            process.destroyForcibly()
        }
    }

    @Test fun cancellingAReadTerminatesItsProcess() = runBlocking {
        val process = ProcessBuilder("sleep", "30").start()
        try {
            val read = async { readDumpProcess(process, 30_000) { it.readText() } }
            delay(50)
            read.cancelAndJoin()
            assertTrue(process.waitFor(2, TimeUnit.SECONDS))
            assertFalse(process.isAlive)
        } finally {
            process.destroyForcibly()
        }
    }
}
