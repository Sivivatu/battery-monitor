package io.github.loganrosen.wattdog.batterystats.analysis

import io.github.loganrosen.wattdog.batterystats.model.BatteryStatsReport
import io.github.loganrosen.wattdog.batterystats.model.KernelWakelock
import io.github.loganrosen.wattdog.batterystats.model.UidDetail

/** Only cumulative counters participate; maxima and current running durations do not. */
internal object ActivityContinuity {
    fun regressed(before: BatteryStatsReport, after: BatteryStatsReport): Boolean =
        globalRegressed(before, after) || radioRegressed(before, after) ||
            matched(before.partialWakelocks, after.partialWakelocks, { it.uid to it.tag }) { b, a ->
                a.total < b.total || a.count < b.count ||
                    (a.actualMs != null && b.actualMs != null && a.actualMs < b.actualMs)
            } || kernelRegressed(before.kernelWakelocks, after.kernelWakelocks) ||
            kernelRegressed(before.wakeupReasons, after.wakeupReasons) ||
            after.uids.any { (uid, current) -> before.uids[uid]?.let { uidRegressed(it, current) } ?: false }

    private fun globalRegressed(b: BatteryStatsReport, a: BatteryStatsReport) =
        a.screenOnCount < b.screenOnCount || a.totalPartialWakelock < b.totalPartialWakelock ||
            a.totalFullWakelock < b.totalFullWakelock || a.wifiMulticastWakelock < b.wifiMulticastWakelock ||
            a.bluetoothScan < b.bluetoothScan || a.doze.deepIdling < b.doze.deepIdling ||
            a.doze.lightIdling < b.doze.lightIdling || a.doze.deepIdlingCount < b.doze.deepIdlingCount ||
            a.doze.lightIdlingCount < b.doze.lightIdlingCount

    private fun radioRegressed(b: BatteryStatsReport, a: BatteryStatsReport): Boolean {
        val bc = b.cellular
        val ac = a.cellular
        val bw = b.wifi
        val aw = a.wifi
        return ac.kernelActive < bc.kernelActive || ac.sleep < bc.sleep || ac.idle < bc.idle || ac.rx < bc.rx || ac.tx < bc.tx ||
            ac.rxBytes < bc.rxBytes || ac.txBytes < bc.txBytes || ac.rxPackets < bc.rxPackets || ac.txPackets < bc.txPackets ||
            ac.radioAccessTech.any { (key, value) -> bc.radioAccessTech[key]?.let { value < it } ?: false } ||
            ac.signalStrength.any { (key, value) -> bc.signalStrength[key]?.let { value < it } ?: false } ||
            aw.kernelActive < bw.kernelActive || aw.scan < bw.scan || aw.rx < bw.rx || aw.tx < bw.tx ||
            aw.rxBytes < bw.rxBytes || aw.txBytes < bw.txBytes
    }

    private fun uidRegressed(b: UidDetail, a: UidDetail): Boolean =
        a.mobileRxBytes < b.mobileRxBytes || a.mobileTxBytes < b.mobileTxBytes ||
            a.wifiRxBytes < b.wifiRxBytes || a.wifiTxBytes < b.wifiTxBytes ||
            a.mobileRadioActive < b.mobileRadioActive || a.mobileRadioActiveCount < b.mobileRadioActiveCount ||
            a.bluetoothScanActual < b.bluetoothScanActual || a.bluetoothScanBackground < b.bluetoothScanBackground ||
            matched(b.jobs, a.jobs, { it.name }) { old, current ->
                current.total < old.total || current.count < old.count ||
                    current.background < old.background || current.backgroundCount < old.backgroundCount
            } || matched(b.wakeupAlarms, a.wakeupAlarms, { it.packageName to it.tag }) { old, current -> current.count < old.count }

    private fun kernelRegressed(b: List<KernelWakelock>, a: List<KernelWakelock>) =
        matched(b, a, { it.name }) { old, current -> current.total < old.total || current.count < old.count }

    private fun <T, K> matched(before: List<T>, after: List<T>, key: (T) -> K, regressed: (T, T) -> Boolean): Boolean {
        val baselines = before.associateBy(key)
        return after.any { current -> baselines[key(current)]?.let { regressed(it, current) } ?: false }
    }
}
