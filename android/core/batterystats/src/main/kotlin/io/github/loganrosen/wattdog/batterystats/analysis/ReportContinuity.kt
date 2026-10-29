package io.github.loganrosen.wattdog.batterystats.analysis

import io.github.loganrosen.wattdog.batterystats.model.BatteryStatsReport
import io.github.loganrosen.wattdog.batterystats.model.Discharge
import io.github.loganrosen.wattdog.batterystats.model.PowerComponent
import io.github.loganrosen.wattdog.batterystats.model.UidPower

/** A reset or an energy model change must not be hidden by clamping a negative delta to zero. */
internal object ReportContinuity {
    fun invalidReason(before: BatteryStatsReport, after: BatteryStatsReport): String? = when {
        before.startClockTime.isNullOrBlank() || after.startClockTime.isNullOrBlank() ->
            "Missing statistics session identity; an interval cannot be established"

        before.startClockTime != after.startClockTime -> "Statistics session changed between snapshots"

        timingRegressed(before, after) -> "Cumulative battery time went backwards"

        dischargeRegressed(before.discharge, after.discharge) -> "Cumulative discharge went backwards"

        componentsRegressed(before.globalPower, after.globalPower) ||
            componentsRegressed(before.globalPowerScreenOff, after.globalPowerScreenOff) ->
            "Cumulative component energy went backwards"

        uidEnergyRegressed(before.uidPower, after.uidPower) -> "Cumulative UID energy went backwards"

        regressed(before.gpsDrainMah, after.gpsDrainMah) ||
            regressed(before.cellular.drainMah, after.cellular.drainMah) ||
            regressed(before.wifi.drainMah, after.wifi.drainMah) -> "Cumulative radio energy went backwards"

        ActivityContinuity.regressed(before, after) -> "Cumulative activity counters went backwards"

        else -> null
    }

    private fun timingRegressed(before: BatteryStatsReport, after: BatteryStatsReport) =
        after.onBattery.realtime < before.onBattery.realtime ||
            after.onBattery.uptime < before.onBattery.uptime ||
            after.screenOff.realtime < before.screenOff.realtime ||
            after.screenOff.uptime < before.screenOff.uptime ||
            after.screenOn < before.screenOn || after.screenDoze < before.screenDoze

    private fun dischargeRegressed(before: Discharge, after: Discharge) =
        after.totalMah < before.totalMah || after.screenOnMah < before.screenOnMah ||
            after.screenOffMah < before.screenOffMah || after.screenDozeMah < before.screenDozeMah ||
            after.lightDozeMah < before.lightDozeMah || after.deepDozeMah < before.deepDozeMah

    private fun componentsRegressed(before: List<PowerComponent>, after: List<PowerComponent>): Boolean {
        val baselines = before.associateBy { it.name }
        return after.any { current ->
            baselines[current.name]?.let { baseline ->
                current.totalMah < baseline.totalMah || regressed(baseline.appsMah, current.appsMah) ||
                    (current.duration != null && baseline.duration != null && current.duration < baseline.duration)
            } ?: false
        }
    }

    private fun uidEnergyRegressed(before: List<UidPower>, after: List<UidPower>): Boolean {
        val baselines = before.associateBy { it.uid }
        return after.any { current ->
            baselines[current.uid]?.let { baseline ->
                current.totalMah < baseline.totalMah || stateRegressed(baseline, current) ||
                    current.components.any { (name, value) -> regressed(baseline.components[name], value) }
            } ?: false
        }
    }

    private fun stateRegressed(before: UidPower, after: UidPower) = regressed(before.foregroundMah, after.foregroundMah) ||
        regressed(before.backgroundMah, after.backgroundMah) ||
        regressed(before.foregroundServiceMah, after.foregroundServiceMah) ||
        regressed(before.cachedMah, after.cachedMah)

    private fun regressed(before: Double?, after: Double?) = before != null && after != null && after < before
}
