package io.github.loganrosen.wattdog.setup

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import io.github.loganrosen.wattdog.batterystats.parse.BatteryStatsParser
import io.github.loganrosen.wattdog.privileged.DirectDumpSource
import io.github.loganrosen.wattdog.privileged.DumpTarget
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in smoke test for a real Android Wireless debugging pairing dialog.
 * It never accepts the one-time code from shell arguments or emits it in test output.
 * Open Settings' pairing-code dialog first, then pass only the two displayed ports:
 * `-e runActualAdbPairing yes -e pairingPort 12345 -e connectionPort 54321`.
 * Revoke DUMP and PACKAGE_USAGE_STATS from the target app before starting instrumentation.
 */
@RunWith(AndroidJUnit4::class)
class LiveAdbPairingInstrumentedTest {
    @Test fun visibleSettingsPairingGrantsDirectAccessAfterWirelessDebuggingIsOff() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Opt-in only; do not run without a live Settings pairing dialog", arguments.getString(RUN_KEY) == RUN_VALUE)

        val pairingPort = requirePort(arguments.getString(PAIRING_PORT_KEY), PAIRING_PORT_KEY)
        val connectionPort = requirePort(arguments.getString(CONNECTION_PORT_KEY), CONNECTION_PORT_KEY)
        require(pairingPort != connectionPort) { "Pairing and connection ports must come from their separate Settings screens." }

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val pairingCode = findVisiblePairingCode(device)
        assertTrue(
            "Keep Android's current Pair device with pairing code dialog open in split screen.",
            pairingCode != null,
        )
        val code = checkNotNull(pairingCode)
        assertTrue(
            "The visible pairing dialog must show the supplied pairing port.",
            visibleSettingsText(device).any {
                it.contains(":$pairingPort")
            },
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        for (permission in REQUIRED_PERMISSIONS) {
            assertEquals(
                "Revoke $permission before this test so pairing must grant fresh permissions.",
                PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(permission),
            )
        }
        assertEquals(
            "Turn on Wireless debugging before pairing.",
            "1",
            device.executeShellCommand("settings get global $ADB_WIFI_ENABLED").trim(),
        )

        val session = AndroidLocalAdbSession()
        try {
            session.pair(pairingPort, code)
            session.connect(connectionPort)
            session.execute(grantCommand(context, "DUMP"))
            session.execute(grantCommand(context, "PACKAGE_USAGE_STATS"))
            try {
                session.execute(grantCommand(context, "BATTERY_STATS"))
            } catch (_: IOException) {
                // The app also treats this compatibility grant as optional; direct reads decide success.
            }
        } finally {
            session.close()
        }

        disableWirelessDebugging(device)

        for (permission in REQUIRED_PERMISSIONS) {
            assertEquals(
                "The setup must grant $permission to the app UID.",
                PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(permission),
            )
        }
        val source = DirectDumpSource(context)
        assertTrue("The ordinary app UID must retain its direct DUMP permission.", source.isAvailable())
        val batterystats = source.dump(DumpTarget.BATTERYSTATS_CHARGED)
        assertTrue(
            "The app UID must read battery statistics after ADB disconnects.",
            batterystats.contains("Statistics since last charge:"),
        )
        assertTrue("The real batterystats output must parse.", BatteryStatsParser.parse(batterystats).startClockTime != null)
        assertTrue(
            "The app UID must read power diagnostics after ADB disconnects.",
            source.dump(DumpTarget.POWER).contains("POWER MANAGER"),
        )
    }

    private fun findVisiblePairingCode(device: UiDevice): String? = visibleSettingsText(device)
        .asSequence()
        .map(String::trim)
        .firstOrNull { PAIRING_CODE.matches(it) }

    private fun visibleSettingsText(device: UiDevice): List<String> = device
        .findObjects(By.pkg(ANDROID_SETTINGS_PACKAGE))
        .mapNotNull { it.text }

    private fun disableWirelessDebugging(device: UiDevice) {
        val output = device.executeShellCommand("settings put global $ADB_WIFI_ENABLED 0")
        check(output.isBlank()) { "Android did not switch off Wireless debugging." }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WIRELESS_DEBUGGING_STOP_TIMEOUT_SECONDS)
        while (device.executeShellCommand("settings get global $ADB_WIFI_ENABLED").trim() != "0" && System.nanoTime() < deadline) {
            Thread.sleep(WIRELESS_DEBUGGING_POLL_INTERVAL_MILLIS)
        }
        assertEquals(
            "Wireless debugging must be off before direct-collection verification.",
            "0",
            device.executeShellCommand("settings get global $ADB_WIFI_ENABLED").trim(),
        )
    }

    private fun requirePort(value: String?, name: String): Int {
        val port = value?.takeIf { PORT_PATTERN.matches(it) }?.toIntOrNull()
        require(port != null && port in VALID_PORT_RANGE) { "Pass the displayed $name as a number from 1 to 65535." }
        return port
    }

    private fun grantCommand(context: Context, permission: String) = "pm grant ${context.packageName} android.permission.$permission"

    private companion object {
        const val ANDROID_SETTINGS_PACKAGE = "com.android.settings"
        const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
        const val RUN_KEY = "runActualAdbPairing"
        const val RUN_VALUE = "yes"
        const val PAIRING_PORT_KEY = "pairingPort"
        const val CONNECTION_PORT_KEY = "connectionPort"
        const val WIRELESS_DEBUGGING_STOP_TIMEOUT_SECONDS = 5L
        const val WIRELESS_DEBUGGING_POLL_INTERVAL_MILLIS = 50L
        const val PORT_PATTERN_TEXT = "[0-9]{1,5}"
        val PORT_PATTERN = Regex(PORT_PATTERN_TEXT)
        val PAIRING_CODE = Regex("[0-9]{6}")
        val VALID_PORT_RANGE = 1..65535
        val REQUIRED_PERMISSIONS = listOf("android.permission.DUMP", "android.permission.PACKAGE_USAGE_STATS")
    }
}
