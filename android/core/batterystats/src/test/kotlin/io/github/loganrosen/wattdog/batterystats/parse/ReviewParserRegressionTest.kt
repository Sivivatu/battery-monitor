package io.github.loganrosen.wattdog.batterystats.parse

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReviewParserRegressionTest {
    @Test fun `malformed and overflowing energy has a consistent parse error`() {
        listOf("..", "1.2.3", "9".repeat(400), "NaN", "Infinity", "-1").forEach { value ->
            assertFailsWith<BatteryStatsParseException>(value) {
                BatteryStatsParser.parse("Statistics since last charge:\n  Estimated power use (mAh):\n    UID u0a101: $value")
            }
        }
    }

    @Test fun `overflowing counters fail with a parse error rather than a number exception`() {
        listOf(
            "  Wake lock u0a101 tag: 1s (99999999999999999999999 times)",
            "  Kernel Wake lock tag: 1s (99999999999999999999999 times)",
            "  u0a101:\n    Job work: 1s realtime (99999999999999999999999 times)",
            "  u0a101:\n    Wakeup alarm tag: 99999999999999999999999 times",
            "  Time on battery: 99999999999999999999999h (100%) realtime, 1h (100%) uptime",
        ).forEach { line ->
            assertFailsWith<BatteryStatsParseException> { BatteryStatsParser.parse("Statistics since last charge:\n$line") }
        }
    }

    @Test fun `required timing distinguishes parsed zero from malformed text`() {
        val prefix = "Statistics since last charge:\n  Time on battery: "
        kotlin.test.assertTrue(BatteryStatsParser.parse(prefix + "0ms (0%) realtime, 0ms (0%) uptime").hasOnBatteryTime)
        listOf("invalid", "1h junk (100%) realtime, 1h (100%) uptime", "-1h (100%) realtime, 1h (100%) uptime").forEach {
            kotlin.test.assertFalse(BatteryStatsParser.parse(prefix + it).hasOnBatteryTime)
        }
    }
}
