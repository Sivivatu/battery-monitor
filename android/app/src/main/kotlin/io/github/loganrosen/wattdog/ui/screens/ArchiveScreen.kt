package io.github.loganrosen.wattdog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.loganrosen.wattdog.batterystats.analysis.formatMah
import io.github.loganrosen.wattdog.batterystats.analysis.formatShort
import io.github.loganrosen.wattdog.data.ArchiveSummary
import io.github.loganrosen.wattdog.data.ArchivedApp
import io.github.loganrosen.wattdog.ui.components.EmptyNote
import io.github.loganrosen.wattdog.ui.components.SectionCard
import io.github.loganrosen.wattdog.ui.components.StatRow
import java.text.DateFormat
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

/** Persistent intervals are separate from Android's rotating event-history timeline. */
@Composable
fun ArchiveScreen(
    summary: ArchiveSummary?,
    error: String?,
    selectedDays: Int,
    onRangeSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("Saved battery history") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 7, 30).forEach { days ->
                        FilterChip(selectedDays == days, { onRangeSelect(days) }, { Text("${days}d") })
                    }
                }
                EmptyNote(
                    "Stored on this device across charging and restarts. Enable background checks in Settings to collect about hourly; Android may delay checks.",
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                summary?.let { saved ->
                    StatRow("Recorded intervals", saved.intervalCount.toString())
                    StatRow("Recorded time on battery", saved.onBatteryMillis.milliseconds.formatShort())
                    StatRow("App charge estimate", formatMah(saved.apps.sumOf { it.totalMah }))
                    EmptyNote(
                        "Only whole recorded intervals inside this range are included; app coverage may be partial. Values are Android's estimates in mAh. Missing foreground/background readings appear as unknown.",
                    )
                } ?: if (error == null) Text("Loading saved history…") else Unit
            }
        }
        if (summary?.apps.isNullOrEmpty()) {
            item {
                SectionCard("App trends") {
                    EmptyNote(
                        "No comparable app readings yet. Two captures in the same battery-accounting session are needed. Gaps and resets are recorded rather than filled with zeros.",
                    )
                }
            }
        }
        items(summary?.apps.orEmpty()) { app -> ArchivedAppCard(app) }
        if (!summary?.boundaries.isNullOrEmpty()) {
            item {
                SectionCard("Collection boundaries") {
                    summary.boundaries.forEach { boundary ->
                        Text(
                            "${DateFormat.getDateTimeInstance(
                                DateFormat.SHORT,
                                DateFormat.SHORT,
                            ).format(Date(boundary.capturedAtMillis))} · ${boundary.status.name.lowercase()}",
                        )
                        EmptyNote(boundary.reason)
                    }
                    if (summary.boundaryCount >
                        summary.boundaries.size
                    ) {
                        EmptyNote("Showing the most recent 20 of ${summary.boundaryCount} boundaries in this range.")
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedAppCard(app: ArchivedApp) {
    SectionCard(app.label) {
        Text(
            app.packages.ifEmpty {
                listOf("UID ${app.uid}; package unresolved")
            }.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
        )
        StatRow("Total", formatMah(app.totalMah))
        StatRow("Foreground", app.foregroundMah?.let(::formatMah) ?: "Unknown")
        StatRow("Background", app.backgroundMah?.let(::formatMah) ?: "Unknown")
        StatRow("Foreground service", app.foregroundServiceMah?.let(::formatMah) ?: "Unknown")
        StatRow("Cached", app.cachedMah?.let(::formatMah) ?: "Unknown")
        if (app.dailyUsage.isNotEmpty()) {
            Text(
                "Daily trend · assigned by capture end date (UTC)",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            app.dailyUsage.take(7).forEach { day ->
                val date = java.time.Instant.ofEpochMilli(day.dayStartMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                StatRow(date.toString(), formatMah(day.totalMah))
                EmptyNote(
                    "Foreground ${day.foregroundMah?.let(
                        ::formatMah,
                    ) ?: "unknown"} · background ${day.backgroundMah?.let(::formatMah) ?: "unknown"}",
                )
            }
        }
    }
}
