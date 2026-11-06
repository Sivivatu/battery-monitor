package io.github.loganrosen.wattdog.ui

import io.github.loganrosen.wattdog.data.ArchiveSummary
import java.time.Instant
import java.time.ZoneOffset

data class ArchiveTrendDay(val dayStartMillis: Long, val totalMah: Double?)

/** Keep unobserved UTC dates distinct from an explicitly recorded zero. */
fun ArchiveSummary.dailyTrend(): List<ArchiveTrendDay> {
    val first = Instant.ofEpochMilli(fromMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val last = Instant.ofEpochMilli(toMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val readings = days.associateBy { it.dayStartMillis }
    return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.map { date ->
        val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        ArchiveTrendDay(start, readings[start]?.totalMah)
    }.toList()
}
