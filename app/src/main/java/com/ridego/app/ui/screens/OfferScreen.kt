package com.ridego.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridego.app.calculator.OfferAnalysis
import com.ridego.app.data.AppState
import com.ridego.app.ui.MetricTile
import com.ridego.app.ui.RideCard
import com.ridego.app.ui.SectionLabel
import com.ridego.app.ui.color
import com.ridego.app.ui.km
import com.ridego.app.ui.label
import com.ridego.app.ui.min
import com.ridego.app.ui.ron
import com.ridego.app.ui.theme.RideGray
import com.ridego.app.ui.theme.RideWhite
import com.ridego.app.ui.theme.RideYellow

@Composable
fun OfferScreen(analysis: OfferAnalysis, onBack: () -> Unit) {
    val offer = analysis.offer

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        SectionLabel("${offer.platform.label} • ofertă detectată")
        Spacer(Modifier.height(8.dp))
        Text(
            "${offer.price.ron()} RON",
            style = MaterialTheme.typography.displayLarge,
            color = RideYellow
        )

        Spacer(Modifier.height(20.dp))

        // With the approach excluded it feeds no total, so showing it beside a
        // "Total" that equals the ride alone reads as an arithmetic error.
        val includePickup = AppState.settings.value.includePickup

        RideCard {
            Column {
                if (includePickup) {
                    LegBlock(
                        title = "Pickup",
                        distance = offer.pickupDistanceKm.km(),
                        duration = offer.pickupTimeMinutes.min(),
                        address = offer.pickupAddress
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = RideGray.copy(alpha = 0.2f))
                    Spacer(Modifier.height(16.dp))
                }
                LegBlock(
                    title = "Cursă",
                    distance = offer.tripDistanceKm.km(),
                    duration = offer.tripTimeMinutes.min(),
                    address = offer.destinationAddress
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        RideCard {
            Column {
                SectionLabel(if (includePickup) "Total" else "În calcul")
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricTile("distanță", analysis.totalKm.km(), Modifier.weight(1f))
                    MetricTile("durată", analysis.totalMinutes.min(), Modifier.weight(1f))
                }
                if (!includePickup) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Doar cursa plătită — drumul până la client nu e numărat, " +
                            "conform setării tale.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RideGray
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        RideCard {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricTile("RON/km", analysis.ronPerKm.ron(), Modifier.weight(1f), RideYellow)
                    MetricTile("RON/oră", analysis.ronPerHour.ron(), Modifier.weight(1f), RideYellow)
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricTile(
                        "cost carburant",
                        "${analysis.fuelCost.ron()} RON",
                        Modifier.weight(1f)
                    )
                    MetricTile(
                        "profit estimat",
                        "${analysis.estimatedProfit.ron()} RON",
                        Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(analysis.verdict.color(), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                analysis.verdict.label(),
                style = MaterialTheme.typography.displayLarge,
                color = androidx.compose.ui.graphics.Color.Black
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            analysis.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = RideGray,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("ÎNAPOI") }
    }
}

@Composable
private fun LegBlock(title: String, distance: String, duration: String, address: String?) {
    Column {
        SectionLabel(title)
        Spacer(Modifier.height(6.dp))
        Text(
            "$distance • $duration",
            style = MaterialTheme.typography.titleLarge,
            color = RideWhite
        )
        if (!address.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(address, style = MaterialTheme.typography.bodyMedium, color = RideGray)
        }
    }
}
