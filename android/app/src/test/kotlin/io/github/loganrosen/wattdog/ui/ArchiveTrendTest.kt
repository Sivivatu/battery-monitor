package io.github.loganrosen.wattdog.ui

import io.github.loganrosen.wattdog.data.ArchiveSummary
import io.github.loganrosen.wattdog.data.ArchivedDay
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveTrendTest {
    @Test
    fun dailyTrendFillsMissingUtcDaysWithoutTurningThemIntoZero() {
        val summary = ArchiveSummary(
            fromMillis = Instant.parse("2024-01-01T12:00:00Z").toEpochMilli(),
            toMillis = Instant.parse("2024-01-03T12:00:00Z").toEpochMilli(),
            days = listOf(
                ArchivedDay(Instant.parse("2024-01-01T00:00:00Z").toEpochMilli(), 4.5, null, null),
                ArchivedDay(Instant.parse("2024-01-03T00:00:00Z").toEpochMilli(), 0.0, null, null),
            ),
        )

        val trend = summary.dailyTrend()

        assertEquals(
            listOf(
                Instant.parse("2024-01-01T00:00:00Z").toEpochMilli() to 4.5,
                Instant.parse("2024-01-02T00:00:00Z").toEpochMilli() to null,
                Instant.parse("2024-01-03T00:00:00Z").toEpochMilli() to 0.0,
            ),
            trend.map { it.dayStartMillis to it.totalMah },
        )
    }

    @Test
    fun dailyTrendUsesUtcDatesAtBothRollingRangeEdges() {
        val summary = ArchiveSummary(
            fromMillis = Instant.parse("2024-03-01T23:59:00Z").toEpochMilli(),
            toMillis = Instant.parse("2024-03-02T00:01:00Z").toEpochMilli(),
        )

        val trend = summary.dailyTrend()

        assertEquals(
            listOf(
                Instant.parse("2024-03-01T00:00:00Z").toEpochMilli(),
                Instant.parse("2024-03-02T00:00:00Z").toEpochMilli(),
            ),
            trend.map { it.dayStartMillis },
        )
        assertEquals(listOf(null, null), trend.map { it.totalMah })
    }

    @Test
    fun dailyTrendSortsRecordedDaysAndIgnoresDataOutsideTheSelectedDates() {
        val summary = ArchiveSummary(
            fromMillis = Instant.parse("2024-01-10T15:00:00Z").toEpochMilli(),
            toMillis = Instant.parse("2024-01-12T15:00:00Z").toEpochMilli(),
            days = listOf(
                ArchivedDay(Instant.parse("2024-01-13T00:00:00Z").toEpochMilli(), 99.0, null, null),
                ArchivedDay(Instant.parse("2024-01-12T00:00:00Z").toEpochMilli(), 2.0, null, null),
                ArchivedDay(Instant.parse("2024-01-09T00:00:00Z").toEpochMilli(), 88.0, null, null),
                ArchivedDay(Instant.parse("2024-01-10T00:00:00Z").toEpochMilli(), 1.0, null, null),
            ),
        )

        val trend = summary.dailyTrend()

        assertEquals(
            listOf(1.0, null, 2.0),
            trend.map { it.totalMah },
        )
    }

    @Test
    fun dailyTrendIncludesOneDateForAZeroLengthRange() {
        val summary = ArchiveSummary(
            fromMillis = Instant.parse("2024-04-05T12:00:00Z").toEpochMilli(),
            toMillis = Instant.parse("2024-04-05T12:00:00Z").toEpochMilli(),
            days = listOf(ArchivedDay(Instant.parse("2024-04-05T00:00:00Z").toEpochMilli(), 3.0, null, null)),
        )

        val trend = summary.dailyTrend()

        assertEquals(1, trend.size)
        assertEquals(Instant.parse("2024-04-05T00:00:00Z").toEpochMilli(), trend.single().dayStartMillis)
        assertEquals(3.0, trend.single().totalMah)
    }

    @Test
    fun dailyTrendCanCoverThirtyOneUtcDatesInAThirtyDayRollingRange() {
        val summary = ArchiveSummary(
            fromMillis = Instant.parse("2024-08-01T12:00:00Z").toEpochMilli(),
            toMillis = Instant.parse("2024-08-31T12:00:00Z").toEpochMilli(),
        )

        val trend = summary.dailyTrend()

        assertEquals(31, trend.size)
        assertEquals(Instant.parse("2024-08-01T00:00:00Z").toEpochMilli(), trend.first().dayStartMillis)
        assertEquals(Instant.parse("2024-08-31T00:00:00Z").toEpochMilli(), trend.last().dayStartMillis)
    }
}
