package io.github.loganrosen.wattdog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.loganrosen.wattdog.batterystats.parse.BatteryStatsParser
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "archive-test-${UUID.randomUUID()}.db"
    private lateinit var store: ArchiveStore
    private val hour = 3_600_000L
    private val start = 1_800_000_000_000L

    @Before fun setup() {
        store = ArchiveStore(context, databaseName)
    }

    @After fun cleanup() {
        store.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun validIntervalsSurviveStoreRecreation() = runBlocking {
        record(1, 10.0, start)
        assertEquals(ArchiveCaptureStatus.VALID, record(2, 16.0, start + hour))
        store.close()
        store = ArchiveStore(context, databaseName)
        val summary = store.summary(start, start + 2 * hour)
        assertEquals(1, summary.intervalCount)
        assertEquals(6.0, summary.apps.single().totalMah, 0.0001)
        assertEquals(3_600_000L, summary.onBatteryMillis)
    }

    @Test fun osResetPreservesPriorIntervalsWithoutCrossSessionDelta() = runBlocking {
        record(1, 10.0, start)
        record(2, 16.0, start + hour)
        assertEquals(ArchiveCaptureStatus.RESET, record(1, 2.0, start + 2 * hour, "new-session"))
        record(2, 5.0, start + 3 * hour, "new-session")
        val summary = store.summary(start, start + 4 * hour)
        assertEquals(2, summary.intervalCount)
        assertEquals(9.0, summary.apps.sumOf { it.totalMah }, 0.0001)
        assertTrue(summary.boundaries.any { it.status == ArchiveCaptureStatus.RESET })
    }

    @Test fun absentStateRemainsUnknownWhileExplicitZeroRemainsZero() = runBlocking {
        record(1, 10.0, start, states = "fg: 0")
        record(2, 16.0, start + hour, states = "fg: 0")
        val app = store.summary(start, start + 2 * hour).apps.single()
        assertEquals(0.0, app.foregroundMah!!, 0.0001)
        assertNull(app.backgroundMah)
        assertNull(app.foregroundServiceMah)
        assertNull(app.cachedMah)
    }

    @Test fun counterRegressionCreatesBoundaryInsteadOfInventingUsage() = runBlocking {
        record(1, 10.0, start)
        assertEquals(ArchiveCaptureStatus.RESET, record(2, 5.0, start + hour))
        val summary = store.summary(start, start + 2 * hour)
        assertEquals(0, summary.intervalCount)
        assertTrue(summary.apps.isEmpty())
    }

    @Test fun incompleteOrLongGapDoesNotBecomeACompleteInterval() = runBlocking {
        record(1, 10.0, start)
        assertEquals(ArchiveCaptureStatus.GAP, record(2, 16.0, start + 8 * hour))
        assertEquals(0, store.summary(start, start + 9 * hour).intervalCount)
    }

    @Test fun pruningRawSnapshotsDoesNotRemoveArchive() = runBlocking<Unit> {
        record(1, 10.0, start)
        record(2, 16.0, start + hour)
        val dir = context.cacheDir.resolve("raw-${UUID.randomUUID()}")
        val rawStore = SnapshotStore(dir)
        val raw = dump(2, 16.0)
        val report = BatteryStatsParser.parse(raw)
        rawStore.save("Auto", raw, report, 50, auto = true, capturedAtMillis = start)
        rawStore.pruneAuto(StatsSession.of(report), start + 72 * hour, 48 * hour)
        assertTrue(rawStore.snapshots.value.isEmpty())
        assertEquals(6.0, store.summary(start, start + 2 * hour).apps.single().totalMah, 0.0001)
        dir.deleteRecursively()
    }

    @Test fun changedPackageOwnershipCannotBeMergedIntoOldIdentity() = runBlocking {
        val uid = io.github.loganrosen.wattdog.batterystats.model.AppUid(10101)
        val first = AppLabel(uid, "First", listOf("example.first"), false)
        val second = AppLabel(uid, "Second", listOf("example.second"), false)
        val raw1 = dump(1, 10.0)
        val raw2 = dump(2, 16.0)
        store.record(raw1, BatteryStatsParser.parse(raw1), mapOf(uid to first), start)
        assertEquals(ArchiveCaptureStatus.PARTIAL, store.record(raw2, BatteryStatsParser.parse(raw2), mapOf(uid to second), start + hour))
        assertTrue(store.summary(start, start + 2 * hour).apps.isEmpty())
    }

    @Test fun rangeDoesNotInterpolateAnIntervalAcrossItsBoundary() = runBlocking {
        record(1, 10.0, start)
        record(2, 16.0, start + hour)
        assertEquals(0, store.summary(start + hour / 2, start + 2 * hour).intervalCount)
    }

    @Test fun oneMissingStateMakesTheWholeRangeStateUnknown() = runBlocking {
        record(1, 10.0, start, states = "fg: 2 bg: 3")
        record(2, 16.0, start + hour, states = "fg: 4 bg: 5")
        record(3, 20.0, start + 2 * hour, states = "fg: 6")
        val app = store.summary(start, start + 3 * hour).apps.single()
        assertEquals(10.0, app.totalMah, 0.0001)
        assertEquals(4.0, app.foregroundMah!!, 0.0001)
        assertNull(app.backgroundMah)
    }

    @Test fun missingCaptureDataCreatesABoundaryAndStartsANewBaseline() = runBlocking {
        record(1, 10.0, start)
        val raw = "Statistics since last charge:\n  Start clock time: session"
        assertEquals(ArchiveCaptureStatus.PARTIAL, store.record(raw, BatteryStatsParser.parse(raw), capturedAtMillis = start + hour))
        assertEquals(ArchiveCaptureStatus.BASELINE, record(3, 20.0, start + 2 * hour))
        assertTrue(store.summary(start, start + 3 * hour).apps.isEmpty())
    }

    @Test fun archiveWriteFailureIsVisibleAndDoesNotThrowFromSafeRecording() = runBlocking {
        val badName = "blocked-${UUID.randomUUID()}.db"
        val blocked = context.getDatabasePath(badName)
        assertTrue(blocked.mkdirs())
        val brokenStore = ArchiveStore(context, badName)
        try {
            val raw = dump(1, 10.0)
            brokenStore.recordSafely(raw, BatteryStatsParser.parse(raw), emptyMap())
            assertTrue(brokenStore.error.value!!.startsWith("History could not be saved:"))
        } finally {
            brokenStore.close()
            blocked.deleteRecursively()
        }
    }

    @Test fun archivePrunesOnlyDataOlderThanNinetyDaysAndPreservesNewBaseline() = runBlocking {
        record(1, 10.0, start)
        record(2, 16.0, start + hour)
        val day91 = start + 91 * 24 * hour
        assertEquals(ArchiveCaptureStatus.RESET, record(1, 2.0, day91, "new-session"))
        assertEquals(ArchiveCaptureStatus.VALID, record(2, 5.0, day91 + hour, "new-session"))
        val summary = store.summary(start, day91 + 2 * hour)
        assertEquals(1, summary.intervalCount)
        assertEquals(3.0, summary.apps.single().totalMah, 0.0001)
        assertTrue(summary.boundaries.all { it.capturedAtMillis >= day91 })
    }

    @Test fun boundaryResultsAreBoundedWhileTotalCountRemainsAvailable() = runBlocking {
        repeat(25) { day -> record(1, 10.0, start + day * 24 * hour, "session-$day") }
        val summary = store.summary(start, start + 26 * 24 * hour)
        assertEquals(25, summary.boundaryCount)
        assertEquals(20, summary.boundaries.size)
    }

    @Test fun dailyTrendExplicitlyAssignsWholeIntervalToItsUtcCaptureEndDate() = runBlocking {
        val day = 24 * hour
        val midnight = (start / day + 1) * day
        record(1, 10.0, midnight - hour / 2, states = "fg: 2 bg: 3")
        record(2, 16.0, midnight + hour / 2, states = "fg: 4 bg: 5")
        val summary = store.summary(midnight - day, midnight + day)
        val trend = summary.apps.single().dailyUsage.single()
        assertEquals(midnight, trend.dayStartMillis)
        assertEquals(6.0, trend.totalMah, 0.0001)
        assertEquals(2.0, trend.foregroundMah!!, 0.0001)
        assertEquals(2.0, trend.backgroundMah!!, 0.0001)
        assertEquals(trend, summary.days.single())
    }

    @Test fun staleCaptureCannotMoveTheBaselineBackwards() = runBlocking {
        record(1, 10.0, start)
        record(3, 16.0, start + 2 * hour)
        assertEquals(ArchiveCaptureStatus.GAP, record(2, 12.0, start + hour))
        record(4, 18.0, start + 3 * hour)
        assertEquals(8.0, store.summary(start, start + 4 * hour).apps.single().totalMah, 0.0001)
    }

    @Test fun malformedBatteryTimeNeverEstablishesAnArchiveBaseline() = runBlocking {
        val raw = dump(1, 10.0).replace("1h (100%) realtime, 1h (100%) uptime", "invalid timing")
        assertEquals(ArchiveCaptureStatus.PARTIAL, store.record(raw, BatteryStatsParser.parse(raw), capturedAtMillis = start))
        assertEquals(ArchiveCaptureStatus.BASELINE, record(2, 16.0, start + hour))
        assertEquals(0, store.summary(start, start + 2 * hour).intervalCount)
    }

    @Test fun overlappingRepositoryCapturesAreCollectedAndRecordedInOrder() = runBlocking {
        val firstEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseFirst = kotlinx.coroutines.CompletableDeferred<Unit>()
        val secondEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        var reads = 0
        val source = object : io.github.loganrosen.wattdog.privileged.DumpSource {
            override suspend fun <T> read(
                target: io.github.loganrosen.wattdog.privileged.DumpTarget,
                block: (java.io.BufferedReader) -> T,
            ): T {
                if (target != io.github.loganrosen.wattdog.privileged.DumpTarget.BATTERYSTATS_CHARGED) error("No power dump")
                val index = ++reads
                if (index == 1) {
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                } else {
                    secondEntered.complete(Unit)
                }
                return dump(index, if (index == 1) 10.0 else 16.0).reader().buffered().use(block)
            }
        }
        val dir = context.cacheDir.resolve("capture-${UUID.randomUUID()}")
        var timestamp = start
        val repository = BatteryRepository(
            source,
            SnapshotStore(dir),
            UidResolver(context.packageManager),
            archive = store,
            clock = { timestamp.also { timestamp += hour } },
        )
        val first = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { repository.capture() }
        firstEntered.await()
        val second = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { repository.capture() }
        try {
            assertTrue("Second dump must wait until first recording completes", !secondEntered.isCompleted)
        } finally {
            releaseFirst.complete(Unit)
            kotlinx.coroutines.withTimeout(10_000) {
                first.await()
                second.await()
            }
            dir.deleteRecursively()
        }
        assertEquals(2, reads)
        assertEquals(start, first.await().capturedAtMillis)
        assertEquals(start + hour, second.await().capturedAtMillis)
        assertEquals(6.0, store.summary(start, start + 2 * hour).apps.single().totalMah, 0.0001)
    }

    @Test fun unchangedTotalStillCarriesMissingStateCoverage() = runBlocking {
        record(1, 10.0, start, states = "fg: 2 bg: 3")
        record(2, 16.0, start + hour, states = "fg: 4 bg: 5")
        record(3, 16.0, start + 2 * hour)
        val summary = store.summary(start, start + 3 * hour)
        val app = summary.apps.single()
        assertEquals(6.0, app.totalMah, 0.0001)
        assertNull(app.foregroundMah)
        assertNull(app.backgroundMah)
        assertNull(summary.days.single().foregroundMah)
    }

    @Test fun captureTriggeredRetentionRemainsConsistentWhenOpenedWithoutANewCapture() = runBlocking {
        record(1, 10.0, start)
        record(2, 16.0, start + hour)
        store.close()
        store = ArchiveStore(context, databaseName)
        val summary = store.summary(start, start + 91 * 24 * hour)
        assertEquals(6.0, summary.apps.single().totalMah, 0.0001)
        assertEquals(6.0, summary.days.single().totalMah, 0.0001)
        assertEquals(1, summary.intervalCount)
    }

    @Test fun reopenedStoreContinuesFromPersistedBaseline() = runBlocking {
        record(1, 10.0, start)
        record(2, 16.0, start + hour)
        store.close()
        store = ArchiveStore(context, databaseName)
        assertEquals(ArchiveCaptureStatus.VALID, record(3, 18.0, start + 2 * hour))
        val summary = store.summary(start, start + 3 * hour)
        assertEquals(2, summary.intervalCount)
        assertEquals(8.0, summary.apps.single().totalMah, 0.0001)
    }

    @Test fun sqliteFailureHalfwayThroughRecordingRollsBackRowsAndBaseline() = runBlocking {
        record(1, 10.0, start)
        val db = android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).path, null, 0)
        try {
            db.execSQL(
                "CREATE TRIGGER fail_usage BEFORE INSERT ON app_usage BEGIN SELECT RAISE(ABORT, 'injected transaction failure'); END",
            )
            try {
                record(2, 16.0, start + hour)
                org.junit.Assert.fail("Recording should fail after inserting capture and interval")
            } catch (expected: android.database.sqlite.SQLiteException) {
                assertTrue(expected.message!!.contains("injected transaction failure"))
            }
            val failed = store.summary(start, start + 3 * hour)
            assertEquals(0, failed.intervalCount)
            assertEquals(1, failed.boundaryCount)
            assertTrue(failed.apps.isEmpty())
            db.execSQL("DROP TRIGGER fail_usage")
            store.close()
            store = ArchiveStore(context, databaseName)
            assertEquals(ArchiveCaptureStatus.VALID, record(3, 18.0, start + 2 * hour))
            assertEquals(8.0, store.summary(start, start + 3 * hour).apps.single().totalMah, 0.0001)
        } finally {
            db.close()
        }
    }

    private suspend fun record(
        hours: Int,
        total: Double,
        at: Long,
        session: String = "session",
        states: String = "",
    ): ArchiveCaptureStatus {
        val raw = dump(hours, total, session, states)
        return store.record(raw, BatteryStatsParser.parse(raw), capturedAtMillis = at)
    }

    private fun dump(hours: Int, total: Double, session: String = "session", states: String = "") = """
        Statistics since last charge:
          Start clock time: $session
          System starts: 0, currently on battery: true
          Time on battery: ${hours}h (100%) realtime, ${hours}h (100%) uptime
          Estimated power use (mAh):
            UID u0a101: $total $states
    """.trimIndent()
}
