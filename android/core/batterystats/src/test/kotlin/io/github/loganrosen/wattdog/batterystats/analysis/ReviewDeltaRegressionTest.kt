package io.github.loganrosen.wattdog.batterystats.analysis

import io.github.loganrosen.wattdog.batterystats.model.AlarmStat
import io.github.loganrosen.wattdog.batterystats.model.AppUid
import io.github.loganrosen.wattdog.batterystats.model.AwakeTime
import io.github.loganrosen.wattdog.batterystats.model.BatteryStatsReport
import io.github.loganrosen.wattdog.batterystats.model.CellularStats
import io.github.loganrosen.wattdog.batterystats.model.JobStat
import io.github.loganrosen.wattdog.batterystats.model.KernelWakelock
import io.github.loganrosen.wattdog.batterystats.model.PartialWakelock
import io.github.loganrosen.wattdog.batterystats.model.PowerComponent
import io.github.loganrosen.wattdog.batterystats.model.UidDetail
import io.github.loganrosen.wattdog.batterystats.model.WifiStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ReviewDeltaRegressionTest {
    private val uid = AppUid(10101)
    private val before = BatteryStatsReport(startClockTime = "session", onBattery = AwakeTime(1.hours, 1.hours))
    private val after = before.copy(onBattery = AwakeTime(2.hours, 2.hours))

    @Test fun `radio energy needs two endpoints and preserves explicit zero`() {
        val current = after.copy(gpsDrainMah = 100.0, cellular = CellularStats(drainMah = 200.0), wifi = WifiStats(drainMah = 300.0))
        val unknown = assertIs<DeltaResult.Ok>(ReportDelta.compute(before, current)).delta
        assertNull(unknown.gpsDrainMah)
        assertNull(unknown.cellular.drainMah)
        assertNull(unknown.wifi.drainMah)
        val zero = current.copy(gpsDrainMah = 0.0, cellular = CellularStats(drainMah = 0.0), wifi = WifiStats(drainMah = 0.0))
        val delta = assertIs<DeltaResult.Ok>(ReportDelta.compute(zero, zero)).delta
        assertEquals(0.0, delta.gpsDrainMah)
        assertEquals(0.0, delta.cellular.drainMah)
        assertEquals(0.0, delta.wifi.drainMah)
    }

    @Test fun `new components and missing optional component baselines are excluded`() {
        val component = PowerComponent("cpu", 10.0, null)
        val current = component.copy(totalMah = 20.0, appsMah = 15.0, duration = 10.hours)
        val delta = assertIs<DeltaResult.Ok>(
            ReportDelta.compute(
                before.copy(globalPower = listOf(component)),
                after.copy(globalPower = listOf(current, PowerComponent("new", 500.0, 400.0))),
            ),
        ).delta
        assertEquals(10.0, delta.globalPower.single().totalMah)
        assertNull(delta.globalPower.single().appsMah)
        assertNull(delta.globalPower.single().duration)
        val zeroBefore = before.copy(globalPower = listOf(component.copy(appsMah = 0.0, duration = 0.minutes)))
        val zeroAfter = after.copy(globalPower = listOf(current.copy(appsMah = 0.0, duration = 0.minutes)))
        val zero = assertIs<DeltaResult.Ok>(ReportDelta.compute(zeroBefore, zeroAfter)).delta.globalPower.single()
        assertEquals(0.0, zero.appsMah)
        assertEquals(0.minutes, zero.duration)
    }

    @Test fun `job count reset from 100 to 2 invalidates increasing battery time`() {
        val detail = UidDetail(uid, jobs = listOf(JobStat("work", 10.minutes, 100)))
        val reset = detail.copy(jobs = listOf(JobStat("work", 11.minutes, 2)))
        assertIs<DeltaResult.StatsReset>(
            ReportDelta.compute(before.copy(uids = mapOf(uid to detail)), after.copy(uids = mapOf(uid to reset))),
        )
    }

    @Test fun `matched uid activity counters cannot go backwards`() {
        val detail = UidDetail(
            uid, mobileRxBytes = 100, mobileTxBytes = 100, wifiRxBytes = 100, wifiTxBytes = 100,
            mobileRadioActive = 10.minutes,
            mobileRadioActiveCount = 100,
            bluetoothScanActual = 10.minutes,
            bluetoothScanBackground = 10.minutes,
        )
        val regressions = listOf(
            detail.copy(mobileRxBytes = 2),
            detail.copy(mobileTxBytes = 2),
            detail.copy(wifiRxBytes = 2),
            detail.copy(wifiTxBytes = 2),
            detail.copy(mobileRadioActive = 2.minutes),
            detail.copy(mobileRadioActiveCount = 2),
            detail.copy(bluetoothScanActual = 2.minutes),
            detail.copy(bluetoothScanBackground = 2.minutes),
        )
        regressions.forEach { reset ->
            assertIs<DeltaResult.StatsReset>(
                ReportDelta.compute(before.copy(uids = mapOf(uid to detail)), after.copy(uids = mapOf(uid to reset))),
            )
        }
    }

    @Test fun `matched jobs alarms and wakelocks reject counter regressions`() {
        val job = JobStat("job", 10.minutes, 100, 5.minutes, 50)
        val detail = UidDetail(uid, jobs = listOf(job), wakeupAlarms = listOf(AlarmStat("pkg", "alarm", 100)))
        listOf(
            detail.copy(jobs = listOf(job.copy(total = 2.minutes))),
            detail.copy(jobs = listOf(job.copy(background = 2.minutes))),
            detail.copy(jobs = listOf(job.copy(backgroundCount = 2))),
            detail.copy(wakeupAlarms = listOf(AlarmStat("pkg", "alarm", 2))),
        ).forEach { reset ->
            assertIs<DeltaResult.StatsReset>(
                ReportDelta.compute(before.copy(uids = mapOf(uid to detail)), after.copy(uids = mapOf(uid to reset))),
            )
        }
        val lock = PartialWakelock(uid, "lock", 10.minutes, 100, maxMs = 5000, actualMs = 10000)
        listOf(lock.copy(total = 2.minutes), lock.copy(count = 2), lock.copy(actualMs = 2)).forEach { reset ->
            assertIs<DeltaResult.StatsReset>(
                ReportDelta.compute(before.copy(partialWakelocks = listOf(lock)), after.copy(partialWakelocks = listOf(reset))),
            )
        }
        val kernel = KernelWakelock("kernel", 10.minutes, 100)
        listOf(kernel.copy(total = 2.minutes), kernel.copy(count = 2)).forEach { reset ->
            assertIs<DeltaResult.StatsReset>(
                ReportDelta.compute(before.copy(kernelWakelocks = listOf(kernel)), after.copy(kernelWakelocks = listOf(reset))),
            )
            assertIs<DeltaResult.StatsReset>(
                ReportDelta.compute(before.copy(wakeupReasons = listOf(kernel)), after.copy(wakeupReasons = listOf(reset))),
            )
        }
    }

    @Test fun `maxima and running durations are not cumulative counters`() {
        val lock = PartialWakelock(uid, "lock", 10.minutes, 100, maxMs = 5000, runningFor = 5.minutes)
        val current = lock.copy(total = 11.minutes, maxMs = 2, runningFor = 1.minutes)
        val delta =
            assertIs<DeltaResult.Ok>(
                ReportDelta.compute(before.copy(partialWakelocks = listOf(lock)), after.copy(partialWakelocks = listOf(current))),
            )
        assertEquals(1.minutes, delta.delta.partialWakelocks.single().total)
    }

    @Test fun `missing kernel baseline cannot invent historical activity`() {
        val delta =
            assertIs<DeltaResult.Ok>(
                ReportDelta.compute(before, after.copy(kernelWakelocks = listOf(KernelWakelock("new", 10.hours, 100)))),
            )
        assertTrue(delta.delta.kernelWakelocks.isEmpty())
    }
}
