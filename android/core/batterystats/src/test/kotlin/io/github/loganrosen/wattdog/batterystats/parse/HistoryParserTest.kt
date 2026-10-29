package io.github.loganrosen.wattdog.batterystats.parse

import io.github.loganrosen.wattdog.batterystats.Fixtures
import io.github.loganrosen.wattdog.batterystats.analysis.HistorySummary
import io.github.loganrosen.wattdog.batterystats.model.AppUid
import io.github.loganrosen.wattdog.batterystats.model.BatteryHistory
import io.github.loganrosen.wattdog.batterystats.model.HistoryMarker
import io.github.loganrosen.wattdog.batterystats.model.HistorySpan
import io.github.loganrosen.wattdog.batterystats.model.LevelPoint
import io.github.loganrosen.wattdog.batterystats.model.coalesce
import io.github.loganrosen.wattdog.batterystats.model.coveredMillis
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryParserTest {
    private fun clock(text: String) = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `android 14 relative format`() {
        val h = HistoryParser.parse(Fixtures.text("real/emulator-api34-history.txt.gz"))
        val t0 = clock("2026-10-12T19:11:16")
        assertEquals(t0, h.startMillis)
        assertEquals(listOf(HistoryMarker(t0, HistoryMarker.Kind.RESET)), h.markers)
        assertEquals(
            listOf(100 to 0L, 90 to 47L, 87 to 33_438L, 85 to 58_651L, 83 to 162_841L),
            h.levels.map { it.level to it.millis - t0 },
        )
        assertEquals(
            listOf(HistorySpan(t0, t0 + 54_541), HistorySpan(t0 + 162_976, t0 + 173_851)),
            h.flag(BatteryHistory.SCREEN),
        )
        val idle = h.state(BatteryHistory.DEVICE_IDLE).first { it.value == "full" }
        assertEquals(58_622L to 163_213L, idle.startMillis - t0 to idle.endMillis - t0)
        assertEquals("hspa", h.state(BatteryHistory.DATA_CONN).first().value)

        val job = h.tagged(BatteryHistory.JOB).first { it.uid == AppUid.parse("u0a132") }
        assertEquals(23_851L to 23_888L, job.startMillis - t0 to job.endMillis - t0)
        assertTrue(h.tagged(BatteryHistory.JOB).any { it.uid == AppUid.parse("u0a125") && it.startMillis == t0 })
    }

    @Test
    fun `android 16 absolute format`() {
        val h = HistoryParser.parse(Fixtures.text("real/pixel9-api37-history.txt.gz"))
        assertEquals(clock("2026-10-10T21:49:40.175"), h.startMillis)
        assertEquals(clock("2026-10-11T00:26:21.648"), h.endMillis)
        assertEquals(LevelPoint(h.startMillis, 47), h.levels.first())
        assertEquals(16, h.levels.last().level)
        assertTrue(h.levels.zipWithNext().all { (a, b) -> a.millis <= b.millis && a.level != b.level })

        val screen = h.flag(BatteryHistory.SCREEN)
        assertEquals(9, screen.size)
        assertEquals(h.endMillis, screen.last().endMillis)
        assertEquals(clock("2026-10-10T21:50:41.164"), h.state(BatteryHistory.DEVICE_IDLE).first { it.value == "full" }.startMillis)

        val plugged = h.flag(BatteryHistory.PLUGGED)
        assertEquals(clock("2026-10-10T23:59:01.850"), plugged.first().startMillis)
        assertTrue(h.state("plug").any { it.value == "wireless" })

        val top = h.tagged(BatteryHistory.TOP)
        assertEquals("com.google.android.apps.messaging", top.first().tag)
        assertTrue(top.all { it.endMillis >= it.startMillis })
        assertTrue(h.tagged(BatteryHistory.WAKE_LOCK).isNotEmpty())
        assertTrue(h.flag(BatteryHistory.CELL_HIGH_TX).size > 100)
    }

    @Test
    fun `absolute clock anchors resolve the year before recording timestamps`() {
        for (anchor in listOf("TIME:", "RESET:TIME:")) {
            val h = HistoryParser.parse(
                """
                  12-31 23:00:00.125 $anchor 2026-12-31-23-00-00
                  12-31 23:00:00.125 090 +screen +running
                  01-01 01:00:00.125 080 -screen -running
                """.trimIndent().prependIndent("  ").lineSequence(),
                defaultYear = 2027,
            )
            val start = clock("2026-12-31T23:00:00.125")
            val end = clock("2027-01-01T01:00:00.125")
            assertEquals(start, h.startMillis, anchor)
            assertEquals(end, h.endMillis, anchor)
            assertEquals(7_200_000L, h.durationMillis, anchor)
            assertEquals(listOf(LevelPoint(start, 90), LevelPoint(end, 80)), h.levels, anchor)
            assertEquals(listOf(HistorySpan(start, end)), h.flag(BatteryHistory.SCREEN), anchor)
            val expectedMarkers = if (anchor == "RESET:TIME:") listOf(HistoryMarker(start, HistoryMarker.Kind.RESET)) else emptyList()
            assertEquals(expectedMarkers, h.markers, anchor)
            val summary = HistorySummary.from(h)
            assertEquals(7_200_000L, summary.screenOnMillis, anchor)
            assertEquals(5.0, summary.screenOnDropPerHour, anchor)
        }
    }

    @Test
    fun `absolute leap day uses the anchored year instead of the default year`() {
        val h = HistoryParser.parse(
            """
              02-29 12:00:00.000 RESET:TIME: 2024-02-29-12-00-00
              02-29 12:00:00.000 090 +screen
              02-29 12:01:00.000 089 -screen
            """.trimIndent().prependIndent("  ").lineSequence(),
            defaultYear = 2027,
        )
        assertEquals(clock("2024-02-29T12:00:00"), h.startMillis)
        assertEquals(clock("2024-02-29T12:01:00"), h.endMillis)
        assertEquals(60_000L, h.flag(BatteryHistory.SCREEN).single().lengthMillis)
    }

    @Test
    fun `absolute history without an anchor retains default year rollover`() {
        val h = HistoryParser.parse(
            """
              12-31 23:00:00.000 090 +screen
              01-01 01:00:00.000 080 -screen
            """.trimIndent().prependIndent("  ").lineSequence(),
            defaultYear = 2026,
        )
        assertEquals(clock("2026-12-31T23:00:00"), h.startMillis)
        assertEquals(clock("2027-01-01T01:00:00"), h.endMillis)
        assertEquals(7_200_000L, h.flag(BatteryHistory.SCREEN).single().lengthMillis)
    }

    @Test
    fun `window clips spans and carries the level in`() {
        val h = HistoryParser.parse(Fixtures.text("real/emulator-api34-history.txt.gz"))
        val w = h.window(h.startMillis + 60_000, h.startMillis + 170_000)
        assertEquals(LevelPoint(w.startMillis, 85), w.levels.first())
        assertEquals(listOf(HistorySpan(h.startMillis + 162_976, w.endMillis)), w.flag(BatteryHistory.SCREEN))
        assertEquals(w.startMillis, w.state(BatteryHistory.DEVICE_IDLE).first().startMillis)
    }

    @Test
    fun `quoted tags keep spaces and minus closes every wakelock`() {
        val h = HistoryParser.parse(
            """
            Battery History (0% used):
                                0 (14) RESET:TIME: 2026-01-01-00-00-00
                                0 (2) 050 status=discharging +running +wake_lock=u0a1:"a b" +wake_lock=1000:"c"
                             +5ms (2) 050 stats=0:"wifi-data: active"
                            +10ms (2) 049 -wake_lock -running
            Per process state tracking
                            +20ms (2) 048 +screen
            """.trimIndent(),
        )
        val wl = h.tagged(BatteryHistory.WAKE_LOCK)
        assertEquals(listOf("a b", "c"), wl.map { it.tag })
        assertTrue(wl.all { it.lengthMillis == 10L })
        assertEquals(10, h.flag(BatteryHistory.RUNNING).single().lengthMillis)
        assertTrue(h.flag(BatteryHistory.SCREEN).isEmpty())
    }

    @Test
    fun `wakelock release names a different tag than the acquire`() {
        val h = HistoryParser.parse(
            """
              01-01 00:00:00.000 TIME: 2026-01-01-00-00-00
              01-01 00:00:00.000 050 +running
              01-01 00:00:01.000 050 +wake_lock=u0a160:"*walarm*:A"
              01-01 00:00:03.000 050 -wake_lock=u0a154:"CallbackRunner"
            """.trimIndent().prependIndent("  "),
        )
        val wl = h.tagged(BatteryHistory.WAKE_LOCK).single()
        assertEquals("*walarm*:A" to 2000L, wl.tag to wl.lengthMillis)
        assertEquals(2000, h.flag(BatteryHistory.WAKE_LOCK).single().lengthMillis)
    }

    @Test
    fun `coalesce and coverage`() {
        val spans = listOf(HistorySpan(0, 10), HistorySpan(12, 20), HistorySpan(5, 8), HistorySpan(100, 110))
        assertEquals(listOf(HistorySpan(0, 20), HistorySpan(100, 110)), spans.coalesce(5))
        assertEquals(28, spans.coveredMillis())
    }
}
