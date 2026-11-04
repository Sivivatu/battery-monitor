package io.github.loganrosen.wattdog.batterystats.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DischargeFormatTest {
    @Test fun `Android fractional discharge buckets load instead of failing as activity counters`() {
        val report = BatteryStatsParser.parse(
            """
            Statistics since last charge:
              Discharge: 9.75 mAh
              Screen off discharge: 1.49 mAh
              Screen doze discharge: 0.125 mAh
              Screen on discharge: 8.26 mAh
              Device light doze discharge: 0.500 mAh
              Device deep doze discharge: 0.375 mAh
            """.trimIndent(),
        )
        assertEquals(10, report.discharge.totalMah)
        assertEquals(1, report.discharge.screenOffMah)
        assertEquals(0, report.discharge.screenDozeMah)
        assertEquals(8, report.discharge.screenOnMah)
        assertEquals(1, report.discharge.lightDozeMah)
        assertEquals(0, report.discharge.deepDozeMah)
    }

    @Test fun `invalid discharge retains strict validation with a safe field-specific message`() {
        listOf("-0.1", "NaN", "Infinity", "2147483648", "1e400", "abc", "1.2.3").forEach { value ->
            val error = assertFailsWith<BatteryStatsParseException> {
                BatteryStatsParser.parse("Statistics since last charge:\n  Screen off discharge: $value mAh")
            }
            assertTrue(error.message.orEmpty().contains("Screen off discharge"))
            assertFalse(error.message.orEmpty().contains(value), "Do not echo diagnostic values")
        }
    }

    @Test fun `whole mAh discharge accepts the maximum representable value`() {
        val report = BatteryStatsParser.parse("Statistics since last charge:\n  Discharge: 2147483647 mAh")
        assertEquals(Int.MAX_VALUE, report.discharge.totalMah)
    }
}
