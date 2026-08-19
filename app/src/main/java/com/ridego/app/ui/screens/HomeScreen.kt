package com.ridego.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ridego.app.calculator.OfferAnalysis
import com.ridego.app.data.PlatformStats
import com.ridego.app.data.Stats
import com.ridego.app.parser.PlatformMode
import com.ridego.app.ui.MetricRow
import com.ridego.app.ui.MetricTile
import com.ridego.app.ui.PrimaryButton
import com.ridego.app.ui.RideCard
import com.ridego.app.ui.SectionLabel
import com.ridego.app.ui.StatusDot
import com.ridego.app.ui.color
import com.ridego.app.ui.km
import com.ridego.app.ui.label
import com.ridego.app.ui.min
import com.ridego.app.ui.ron
import com.ridego.app.ui.theme.RideGray
import com.ridego.app.ui.theme.RideGreen
import com.ridego.app.ui.theme.RideRed
import com.ridego.app.ui.theme.RideWhite
import com.ridego.app.ui.theme.RideYellow

@Composable
fun HomeScreen(
    isActive: Boolean,
    stats: Stats,
    platformMode: PlatformMode,
    onPlatformMode: (PlatformMode) -> Unit,
    lastAnalysis: OfferAnalysis?,
    includePickup: Boolean,
    debugMode: Boolean,
    onOpenLastOffer: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenDemo: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("RideGo", style = MaterialTheme.typography.displayLarge, color = RideYellow)
        Text(
            "Decizii mai rapide. Curse mai profitabile.",
            style = MaterialTheme.typography.bodyMedium,
            color = RideGray
        )

        Spacer(Modifier.height(24.dp))

        RideCard {
            Column {
                SectionLabel("Status")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(isActive)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isActive) "ACTIV — CITEȘTE ECRANUL" else "INACTIV",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isActive) RideGreen else RideGray
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        RideCard {
            Column {
                SectionLabel("Platformă")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlatformMode.entries.forEach { mode ->
                        FilterChip(
                            selected = platformMode == mode,
                            onClick = { onPlatformMode(mode) },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RideYellow,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        RideCard {
            Column {
                SectionLabel("Oferte analizate")
                Spacer(Modifier.height(10.dp))
                MetricRow(
                    "Total" to stats.total.analyzed.toString(),
                    "Uber" to stats.uber.analyzed.toString(),
                    "Bolt" to stats.bolt.analyzed.toString()
                )
                Spacer(Modifier.height(20.dp))
                VerdictBreakdown("Acceptate", stats, RideGreen) { it.accepted }
                Spacer(Modifier.height(12.dp))
                VerdictBreakdown("Respinse", stats, RideRed) { it.rejected }
            }
        }

        Spacer(Modifier.height(14.dp))

        LastOfferCard(lastAnalysis, includePickup, onOpenLastOffer)

        Spacer(Modifier.height(24.dp))

        if (isActive) {
            PrimaryButton(
                text = "OPREȘTE CITIREA",
                onClick = onStop,
                container = RideRed,
                content = Color.White
            )
        } else {
            PrimaryButton(text = "PORNEȘTE CITIREA", onClick = onStart)
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onOpenDemo, modifier = Modifier.weight(1f)) { Text("DEMO") }
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) { Text("ISTORIC") }
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) { Text("SETĂRI") }
        }

        if (debugMode) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onOpenDebug, modifier = Modifier.fillMaxWidth()) {
                Text("DEBUG")
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Datele capturate sunt procesate local pe dispozitiv.",
            style = MaterialTheme.typography.bodyMedium,
            color = RideGray
        )
    }
}

/**
 * The last verdict, rendered from the same OfferAnalysis the overlay uses.
 * Present as soon as RideGo comes back to the front — no new offer needed.
 */
@Composable
private fun LastOfferCard(
    analysis: OfferAnalysis?,
    includePickup: Boolean,
    onOpenDetails: () -> Unit
) {
    RideCard {
        Column {
            SectionLabel("Ultima ofertă")

            if (analysis == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Aștept o ofertă...",
                    style = MaterialTheme.typography.titleLarge,
                    color = RideGray
                )
                return@RideCard
            }

            val offer = analysis.offer
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(offer.platform.label, offer.serviceType).joinToString(" • "),
                style = MaterialTheme.typography.titleMedium,
                color = RideWhite
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "${offer.price.ron()} RON",
                style = MaterialTheme.typography.displayLarge,
                color = RideYellow
            )

            Spacer(Modifier.height(12.dp))
            if (includePickup) {
                DetailRow(
                    "Pickup",
                    "${offer.pickupDistanceKm.km()} • ${offer.pickupTimeMinutes.min()}"
                )
            }
            DetailRow("Cursă", "${offer.tripDistanceKm.km()} • ${offer.tripTimeMinutes.min()}")
            if (includePickup) {
                DetailRow("Total", "${analysis.totalKm.km()} • ${analysis.totalMinutes.min()}")
            }
            DetailRow("Carburant", "${analysis.fuelCost.ron()} RON")
            DetailRow("Îți rămâne", "${analysis.estimatedProfit.ron()} RON")

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricTile(
                    "RON/oră NET",
                    analysis.netRonPerHour.ron(0),
                    Modifier.weight(1f),
                    analysis.verdict.color()
                )
                MetricTile("brut", analysis.ronPerHour.ron(0), Modifier.weight(1f), RideYellow)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                analysis.verdict.label(),
                style = MaterialTheme.typography.headlineMedium,
                color = analysis.verdict.color()
            )
            Text(
                "Motiv: ${analysis.reason}",
                style = MaterialTheme.typography.bodyMedium,
                color = RideGray
            )

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth()) {
                Text("DETALII COMPLETE")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RideGray)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = RideWhite)
    }
}

@Composable
private fun VerdictBreakdown(
    label: String,
    stats: Stats,
    color: Color,
    select: (PlatformStats) -> Int
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${select(stats.total)}  •  Uber ${select(stats.uber)}  •  Bolt ${select(stats.bolt)}",
            style = MaterialTheme.typography.bodyMedium,
            color = RideGray
        )
    }
}
