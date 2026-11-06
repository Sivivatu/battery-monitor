package io.github.loganrosen.wattdog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.loganrosen.wattdog.batterystats.analysis.formatMah
import io.github.loganrosen.wattdog.data.ArchiveSummary
import io.github.loganrosen.wattdog.ui.ArchiveTrendDay
import io.github.loganrosen.wattdog.ui.components.EmptyNote
import io.github.loganrosen.wattdog.ui.components.SectionCard
import io.github.loganrosen.wattdog.ui.components.StatRow
import io.github.loganrosen.wattdog.ui.dailyTrend
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun ArchiveTrendChart(summary: ArchiveSummary, modifier: Modifier = Modifier) {
    val days = summary.dailyTrend()
    val maximum = days.mapNotNull { it.totalMah }.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    var selectedDate by remember(summary.fromMillis, summary.toMillis) {
        mutableLongStateOf(days.lastOrNull { it.totalMah != null }?.dayStartMillis ?: days.lastOrNull()?.dayStartMillis ?: summary.toMillis)
    }
    val scroll = rememberScrollState()
    LaunchedEffect(days.lastOrNull()?.dayStartMillis, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    SectionCard("Daily app usage", modifier) {
        days.find { it.dayStartMillis == selectedDate }?.let { selected ->
            StatRow(selected.dateLabel(), selected.totalMah?.let(::formatMah) ?: "No reading")
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            days.forEach { day ->
                TrendBar(day, maximum, day.dayStartMillis == selectedDate, { selectedDate = day.dayStartMillis })
            }
        }
        EmptyNote("Swipe for more days; tap a bar for its app charge estimate.")
        EmptyNote(
            "UTC capture-end dates. Bars cover recorded intervals only; – means no reading, not zero usage. Partial days are not directly comparable.",
        )
    }
}

@Composable
private fun TrendBar(day: ArchiveTrendDay, maximum: Double, selected: Boolean, onSelect: () -> Unit) {
    val value = day.totalMah
    val date = day.dateLabel()
    Column(
        Modifier.width(48.dp).clickable(onClick = onSelect).semantics(mergeDescendants = true) {
            contentDescription = "$date UTC: ${value?.let(::formatMah) ?: "No reading"}"
            this.selected = selected
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.BottomCenter) {
            if (value == null) {
                Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Box(
                    Modifier.width(32.dp).height(((value / maximum * 112).toFloat().coerceAtLeast(2f)).dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            MaterialTheme.shapes.extraSmall,
                        ),
                )
            }
        }
        Text(date, style = MaterialTheme.typography.labelSmall)
    }
}

private fun ArchiveTrendDay.dateLabel(): String = DateTimeFormatter.ofPattern("MMM d").format(
    Instant.ofEpochMilli(dayStartMillis).atZone(ZoneOffset.UTC),
)
