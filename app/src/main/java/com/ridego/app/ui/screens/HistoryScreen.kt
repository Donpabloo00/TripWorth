package com.ridego.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ridego.app.data.DriverDecision
import com.ridego.app.data.HistoryEntry
import com.ridego.app.parser.Platform
import com.ridego.app.ui.RideCard
import com.ridego.app.ui.color
import com.ridego.app.ui.formatTime
import com.ridego.app.ui.km
import com.ridego.app.ui.label
import com.ridego.app.ui.ron
import com.ridego.app.ui.theme.RideGray
import com.ridego.app.ui.theme.RideYellow

@Composable
fun HistoryScreen(
    allEntries: List<HistoryEntry>,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    var filter: Platform? by rememberSaveable { mutableStateOf(null) }
    val entries = if (filter == null) allEntries else allEntries.filter { it.platform == filter }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("ISTORIC", style = MaterialTheme.typography.displayLarge, color = RideYellow)
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryChip("TOATE", filter == null) { filter = null }
            HistoryChip("UBER", filter == Platform.UBER) { filter = Platform.UBER }
            HistoryChip("BOLT", filter == Platform.BOLT) { filter = Platform.BOLT }
        }
        Spacer(Modifier.height(16.dp))

        if (entries.isEmpty()) {
            Text(
                "Nicio ofertă analizată încă.",
                style = MaterialTheme.typography.bodyLarge,
                color = RideGray
            )
            Spacer(Modifier.height(20.dp))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries) { entry -> HistoryRow(entry) }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text("GOLEȘTE ISTORICUL")
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("ÎNAPOI") }
    }
}

@Composable
private fun HistoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = RideYellow,
            selectedLabelColor = Color.Black
        )
    )
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    RideCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.platform.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = RideGray
                )
                Text(
                    "${entry.price.ron()} RON",
                    style = MaterialTheme.typography.titleLarge,
                    color = RideYellow
                )
                Text(
                    "${entry.totalKm.km()} • ${entry.ronPerKm.ron()} RON/km • " +
                        "${entry.ronPerHour.ron()} RON/h",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray
                )
                Text(
                    formatTime(entry.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    entry.verdict.label(),
                    style = MaterialTheme.typography.titleMedium,
                    color = entry.verdict.color()
                )
                entry.driverDecision?.let { decision ->
                    Text(
                        when (decision) {
                            DriverDecision.ACCEPTED -> "ai acceptat"
                            DriverDecision.REJECTED -> "ai refuzat"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = RideGray
                    )
                }
            }
        }
    }
}
