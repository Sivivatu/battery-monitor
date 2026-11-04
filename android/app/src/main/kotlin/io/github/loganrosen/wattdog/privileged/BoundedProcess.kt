package io.github.loganrosen.wattdog.privileged

import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val DUMP_PROCESS_TIMEOUT_MILLIS = 30_000L

private val processDeadlines = ScheduledThreadPoolExecutor(1) { task ->
    Thread(task, "battery-diagnostic-deadlines").apply { isDaemon = true }
}.apply { removeOnCancelPolicy = true }

/** Killing the subprocess unblocks its pipe; a coroutine timeout alone cannot stop a pipe read. */
internal suspend fun <T> readDumpProcess(
    process: Process,
    timeoutMillis: Long = DUMP_PROCESS_TIMEOUT_MILLIS,
    block: (BufferedReader) -> T,
): T = try {
    withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val timedOut = AtomicBoolean(false)
            val deadline = processDeadlines.schedule({
                timedOut.set(true)
                process.destroyForcibly()
            }, timeoutMillis, TimeUnit.MILLISECONDS)
            continuation.invokeOnCancellation { process.destroyForcibly() }
            try {
                val result = process.inputStream.bufferedReader().use(block)
                val exitCode = process.waitFor()
                if (timedOut.get()) throw IOException("Battery diagnostics timed out. Try again.")
                if (exitCode != 0) throw IOException("Battery diagnostics exited with $exitCode.")
                continuation.resume(result)
            } catch (failure: Exception) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            } finally {
                deadline.cancel(false)
            }
        }
    }
} finally {
    // Own the process even if cancellation prevents the IO block from starting.
    process.destroyForcibly()
}
