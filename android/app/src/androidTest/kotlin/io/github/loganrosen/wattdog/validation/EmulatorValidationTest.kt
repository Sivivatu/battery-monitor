package io.github.loganrosen.wattdog.validation

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.loganrosen.wattdog.WattdogApplication
import io.github.loganrosen.wattdog.batterystats.parse.BatteryStatsParser
import io.github.loganrosen.wattdog.data.SnapshotStore
import io.github.loganrosen.wattdog.data.StatsSession
import io.github.loganrosen.wattdog.privileged.DirectDumpSource
import io.github.loganrosen.wattdog.privileged.DumpTarget
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmulatorValidationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun directCollectionRunsWithAppPermissionRatherThanShellIdentity() = runBlocking {
        val source = DirectDumpSource(context)
        assertTrue("Grant DUMP before this suite", source.isAvailable())
        val raw = source.dump(DumpTarget.BATTERYSTATS_CHARGED)
        assertTrue(raw.contains("Statistics since last charge:"))
        val parsed = BatteryStatsParser.parse(raw)
        assertNotNull("Session identity is required", parsed.startClockTime)
        assertTrue(source.dump(DumpTarget.POWER).isNotBlank())
    }

    @Test fun snapshotRecreationAndPruningKeepManualMarks() = runBlocking {
        val dir = context.cacheDir.resolve("snapshot-validation-${UUID.randomUUID()}")
        try {
            val raw = """
                Statistics since last charge:
                  Start clock time: synthetic-session
                  Time on battery: 1h (100%) realtime, 1h (100%) uptime
                  Estimated power use (mAh):
                    UID u0a101: 12 fg: 4 bg: 8
            """.trimIndent()
            val report = BatteryStatsParser.parse(raw)
            val store = SnapshotStore(dir)
            val manual = store.save("Manual", raw, report, 80, capturedAtMillis = 1)
            store.save("Auto", raw, report, 80, auto = true, capturedAtMillis = 1)
            dir.resolve("broken.json").writeText("corrupt metadata")
            val recreated = SnapshotStore(dir)
            recreated.load()
            assertEquals(2, recreated.snapshots.value.size)
            recreated.pruneAuto(StatsSession.of(report), 200_000, 1_000)
            assertEquals(listOf(manual.id), recreated.snapshots.value.map { it.id })
            assertEquals(report.startClockTime, recreated.report(manual.id).startClockTime)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun liveCaptureAndSavedHistoryScreenOpen() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context.startActivity(
            context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        assertTrue(device.wait(Until.hasObject(By.desc("Refresh")), 30_000))
        device.findObject(By.desc("Saved trends")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Saved battery history")), 10_000))
        assertTrue(device.hasObject(By.text("7d")))
        device.findObject(By.text("30d")).click()
        assertTrue("The selected range must finish loading", device.wait(Until.hasObject(By.text("Recorded intervals")), 10_000))
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.text("Dashboard")), 10_000))
        device.findObject(By.text("Apps")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Apps")), 10_000))
        device.findObject(By.text("History")).click()
        assertTrue(device.wait(Until.hasObject(By.text("History")), 10_000))
    }

    @Test fun automaticCheckPersistsARealSnapshotAndDisablePreservesArchive() = runBlocking {
        val app = context.applicationContext as WattdogApplication
        val graph = app.container
        graph.snapshots.load()
        graph.backgroundChecks.setEnabled(true)
        try {
            // Run the same pass as WorkManager without waiting for its hourly schedule.
            graph.backgroundCheck.run()
            assertNotNull(graph.alertStore.state.value.lastCheck)
            assertFalse(graph.alertStore.state.value.lastCheck!!.summary.startsWith("Failed"))
            graph.snapshots.load()
            assertTrue("Unplug emulator battery first", graph.snapshots.snapshots.value.any { it.auto })
            assertTrue(context.getDatabasePath("battery-history.db").exists())
        } finally {
            graph.backgroundChecks.setEnabled(false)
        }
        assertTrue(graph.snapshots.snapshots.value.none { it.auto })
        assertTrue(context.getDatabasePath("battery-history.db").exists())
    }
}
