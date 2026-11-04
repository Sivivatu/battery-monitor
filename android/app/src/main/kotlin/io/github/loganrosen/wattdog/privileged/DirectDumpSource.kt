package io.github.loganrosen.wattdog.privileged

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import java.io.BufferedReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs `dumpsys` as Wattdog itself after a one-time
 * `adb shell pm grant io.github.sivivatu.batterymonitor android.permission.DUMP`.
 *
 * batterystats additionally checks usage-stats access, which `pm grant ... PACKAGE_USAGE_STATS`
 * satisfies while the app op is in its default mode.
 */
class DirectDumpSource(private val context: Context) : DumpSource {

    // Preserve access for existing installations. Actual dump verification checks service-specific access.
    fun isAvailable(): Boolean = context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED

    override suspend fun <T> read(target: DumpTarget, block: (BufferedReader) -> T): T = withContext(Dispatchers.IO) {
        readDumpProcess(Dumpsys.start(target)) { it.consume(target, block) }
    }
}
