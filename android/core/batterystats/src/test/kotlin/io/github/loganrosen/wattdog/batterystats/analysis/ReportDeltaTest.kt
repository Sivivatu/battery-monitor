package io.github.loganrosen.wattdog.batterystats.analysis

import io.github.loganrosen.wattdog.batterystats.Fixtures
import io.github.loganrosen.wattdog.batterystats.model.AppUid
import io.github.loganrosen.wattdog.batterystats.model.AwakeTime
import io.github.loganrosen.wattdog.batterystats.model.BatteryStatsReport
import io.github.loganrosen.wattdog.batterystats.model.UidPower
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ReportDeltaTest {
    private val later = Fixtures.report

    /** The same session two hours earlier, before the audio leak and the sync storm started. */
    private val earlier: BatteryStatsReport = later.copy(
        onBattery = AwakeTime(8.hours, 4.hours),
        screenOff = AwakeTime(6.hours, 3.hours),
        discharge = later.discharge.copy(totalMah = 1000, screenOffMah = 500),
        partialWakelocks = later.partialWakelocks.map {
            if (it.tag == "AudioMix") it.copy(total = 2.hours + 50.minutes, count = 1) else it
        },
        uidPower = later.uidPower.map { if (it.uid == AppUid(10301)) it.copy(totalMah = it.totalMah - 10.0) else it },
        globalPower = later.globalPower.map { if (it.name == "mobile_radio") it.copy(totalMah = 200.0) else it },
        uids = later.uids.mapValues { (uid, d) ->
            if (uid == AppUid(10301)) d.copy(jobs = d.jobs.map { it.copy(count = it.count - 10) }) else d
        },
    )

    @Test
    fun `subtracts cumulative counters and drops unchanged entries`() {
        val delta = assertIs<DeltaResult.Ok>(ReportDelta.compute(earlier, later)).delta
        assertEquals(AwakeTime(2.hours, 2.hours), delta.onBattery)
        assertEquals(AwakeTime(2.hours, 2.hours), delta.screenOff)
        assertEquals(500, delta.discharge.totalMah)
        assertEquals(400, delta.discharge.screenOffMah)

        val audio = delta.partialWakelocks.single()
        assertEquals("AudioMix", audio.tag)
        assertEquals(2.hours, audio.total)
        assertEquals(1, audio.count)

        assertEquals(listOf(AppUid(10301)), delta.uidPower.map { it.uid })
        assertEquals(listOf("mobile_radio"), delta.globalPower.map { it.name })
        assertEquals(60.0, delta.globalPower.single().totalMah)

        assertEquals(setOf(AppUid(10301)), delta.uids.keys)
        assertEquals(listOf(10, 10), delta.uids.getValue(AppUid(10301)).jobs.map { it.count })
    }

    @Test
    fun `identical snapshots produce an empty delta`() {
        val delta = assertIs<DeltaResult.Ok>(ReportDelta.compute(later, later)).delta
        assertTrue(delta.partialWakelocks.isEmpty())
        assertTrue(delta.uids.isEmpty())
        assertTrue(delta.uidPower.isEmpty())
    }

    @Test
    fun `detects a stats reset`() {
        assertIs<DeltaResult.StatsReset>(ReportDelta.compute(later.copy(startClockTime = "2026-01-04-08-00-00"), later))
        assertIs<DeltaResult.StatsReset>(ReportDelta.compute(later, earlier.copy(startClockTime = later.startClockTime)))
    }

    @Test
    fun `uid counter rollback invalidates a delta despite increasing battery time`() {
        val before = earlier.copy(uidPower = listOf(UidPower(AppUid(10101), 12.0, backgroundMah = 10.0)))
        val after = later.copy(uidPower = listOf(UidPower(AppUid(10101), 8.0, backgroundMah = 6.0)))
        assertIs<DeltaResult.StatsReset>(ReportDelta.compute(before, after))
    }

    @Test
    fun `state counter rollback invalidates a delta even when uid total increases`() {
        val before = earlier.copy(uidPower = listOf(UidPower(AppUid(10101), 12.0, backgroundMah = 10.0)))
        val after = later.copy(uidPower = listOf(UidPower(AppUid(10101), 18.0, backgroundMah = 6.0)))
        assertIs<DeltaResult.StatsReset>(ReportDelta.compute(before, after))
    }

    @Test
    fun `whole battery discharge rollback invalidates the interval`() {
        val after = later.copy(discharge = later.discharge.copy(totalMah = 100))
        assertIs<DeltaResult.StatsReset>(ReportDelta.compute(earlier, after))
    }

    @Test
    fun `component energy rollback invalidates the interval`() {
        val after = later.copy(globalPower = earlier.globalPower.map { it.copy(totalMah = it.totalMah - 1.0) })
        assertIs<DeltaResult.StatsReset>(ReportDelta.compute(earlier, after))
        val beforeUid = earlier.copy(uidPower = listOf(UidPower(AppUid(10101), 12.0, components = mapOf("cpu" to 9.0))))
        val afterUid = later.copy(uidPower = listOf(UidPower(AppUid(10101), 18.0, components = mapOf("cpu" to 8.0))))
        assertIs<DeltaResult.StatsReset>(ReportDelta.compute(beforeUid, afterUid))
    }

    @Test
    fun `uptime rollback invalidates a delta despite increasing realtime`() {
        val after = later.copy(onBattery = AwakeTime(10.hours, 1.hours))
        assertIs<DeltaResult.StatsReset>(ReportDelta.compute(earlier, after))
    }

    @Test
    fun `a newly appearing uid is not assigned its whole session consumption`() {
        val before = earlier.copy(uidPower = emptyList())
        val after = later.copy(uidPower = listOf(UidPower(AppUid(10101), 12.0, backgroundMah = 10.0)))
        val result = assertIs<DeltaResult.Ok>(ReportDelta.compute(before, after))
        assertTrue(result.delta.uidPower.isEmpty())
        assertEquals(setOf(AppUid(10101)), result.missingBaselineUids)
    }

    @Test
    fun `a newly appearing uid detail is not assigned whole session activity`() {
        val uid = AppUid(10301)
        val before = earlier.copy(uids = earlier.uids - uid)
        val result = assertIs<DeltaResult.Ok>(ReportDelta.compute(before, later))
        assertTrue(uid !in result.delta.uids)
        assertTrue(uid in result.missingBaselineUids)
    }

    @Test
    fun `missing session identity does not establish a valid comparison`() {
        assertIs<DeltaResult.StatsReset>(ReportDelta.compute(earlier.copy(startClockTime = null), later))
        assertIs<DeltaResult.StatsReset>(ReportDelta.compute(earlier, later.copy(startClockTime = null)))
    }

    @Test
    fun `state energy remains unknown unless both interval endpoints report it`() {
        val uid = AppUid(10101)
        val before = earlier.copy(uidPower = listOf(UidPower(uid, 12.0, foregroundMah = 2.0)))
        val after = later.copy(uidPower = listOf(UidPower(uid, 18.0, backgroundMah = 8.0)))
        val energy = assertIs<DeltaResult.Ok>(ReportDelta.compute(before, after)).delta.uidPower.single()
        assertNull(energy.foregroundMah)
        assertNull(energy.backgroundMah)
        assertNull(energy.foregroundServiceMah)
        assertNull(energy.cachedMah)
    }

    @Test
    fun `explicit zero and increasing state energy can be differenced`() {
        val uid = AppUid(10101)
        val before = earlier.copy(uidPower = listOf(UidPower(uid, 12.0, foregroundMah = 0.0, backgroundMah = 12.0)))
        val after = later.copy(uidPower = listOf(UidPower(uid, 18.0, foregroundMah = 0.0, backgroundMah = 18.0)))
        val energy = assertIs<DeltaResult.Ok>(ReportDelta.compute(before, after)).delta.uidPower.single()
        assertEquals(6.0, energy.totalMah)
        assertEquals(0.0, energy.foregroundMah)
        assertEquals(6.0, energy.backgroundMah)
        assertNull(energy.cachedMah)
    }

    @Test
    fun `new uid wakelock cannot contribute historical activity to a short interval`() {
        val uid = AppUid(10999)
        val before = BatteryStatsReport(startClockTime = "session", onBattery = AwakeTime(10.hours, 10.hours))
        val after = before.copy(
            onBattery = AwakeTime(10.hours + 1.minutes, 10.hours + 1.minutes),
            partialWakelocks = listOf(io.github.loganrosen.wattdog.batterystats.model.PartialWakelock(uid, "old", 8.hours, 100)),
        )
        val result = assertIs<DeltaResult.Ok>(ReportDelta.compute(before, after))
        assertTrue(result.delta.partialWakelocks.isEmpty())
        assertEquals(setOf(uid), result.missingBaselineUids)
    }

    @Test
    fun `new tag on existing uid also requires a wakelock baseline`() {
        val uid = AppUid(10999)
        val old = io.github.loganrosen.wattdog.batterystats.model.PartialWakelock(uid, "known", 1.hours, 1)
        val before =
            BatteryStatsReport(startClockTime = "session", onBattery = AwakeTime(10.hours, 10.hours), partialWakelocks = listOf(old))
        val after = before.copy(
            onBattery = AwakeTime(10.hours + 1.minutes, 10.hours + 1.minutes),
            partialWakelocks = listOf(old, old.copy(tag = "new", total = 8.hours, count = 100)),
        )
        val result = assertIs<DeltaResult.Ok>(ReportDelta.compute(before, after))
        assertTrue(result.delta.partialWakelocks.isEmpty())
        assertEquals(setOf(uid), result.missingBaselineUids)
    }
}
