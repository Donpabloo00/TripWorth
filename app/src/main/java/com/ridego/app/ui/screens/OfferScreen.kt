package com.ridego.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.tripworth.app.R
import com.ridego.app.calculator.OfferAnalysis
import com.ridego.app.calculator.Verdict
import com.ridego.app.data.AppState
import com.ridego.app.ui.PrimaryButton
import com.ridego.app.ui.km
import com.ridego.app.ui.min
import com.ridego.app.ui.ron
import com.ridego.app.ui.theme.RideBlack
import com.ridego.app.ui.theme.RideGray
import com.ridego.app.ui.theme.RideGreen
import com.ridego.app.ui.theme.RideOrange
import com.ridego.app.ui.theme.RideRed
import com.ridego.app.ui.theme.RideSurface
import com.ridego.app.ui.theme.RideWhite

/**
 * Offer details in the RideCheetah / Uber split:
 * — top: dark analysis card (RON/km, profit, fuel)
 * — bottom: white Uber-style sheet with pickup, trip, accept cue
 */
@Composable
fun OfferScreen(analysis: OfferAnalysis, onBack: () -> Unit) {
    val offer = analysis.offer
    val includePickup = AppState.settings.value.includePickup
    val hourly = analysis.netRonPerHour ?: analysis.ronPerHour

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // --- top analysis card ------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, RideOrange, RoundedCornerShape(16.dp))
                .background(RideSurface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${stringResource(R.string.brand_banner)}  •  ${offer.platform.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = RideGray,
                    modifier = Modifier.weight(1f)
                )
                VerdictBadge(analysis.verdict)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    "${analysis.ronPerKm.ron(2)} RON/km",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = RideOrange,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    hourly?.let { "≈ ${it.ron(0)} RON/h" } ?: "≈ — RON/h",
                    style = MaterialTheme.typography.titleMedium,
                    color = RideWhite
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp, horizontal = 4.dp)
            ) {
                GridStat(
                    analysis.ronPerHour?.let { "${it.ron(0)} RON/oră" } ?: "— RON/oră",
                    modifier = Modifier.weight(1f)
                )
                GridStat(
                    analysis.estimatedProfit?.let { "${it.ron()} PROFIT" } ?: "— PROFIT",
                    color = when {
                        analysis.estimatedProfit == null -> RideGray
                        analysis.estimatedProfit >= 0 -> RideGreen
                        else -> RideRed
                    },
                    modifier = Modifier.weight(1.2f)
                )
                GridStat(
                    analysis.fuelCost?.let { "${it.ron()} COST COMB." } ?: "— COST COMB.",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Încasezi  ${offer.price.ron()} RON",
                style = MaterialTheme.typography.titleMedium,
                color = RideWhite
            )
            if (includePickup) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Distanță la client  ${offer.pickupDistanceKm.km()}  •  ${offer.pickupTimeMinutes.min()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray
                )
            }
            if (analysis.reason.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    analysis.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- bottom Uber-style sheet ------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RideWhite, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(RideBlack, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        offer.serviceType ?: offer.platform.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = RideWhite
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${offer.price.ron()} RON",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = RideBlack
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    offer.paymentMethod ?: "Plata cu cardul",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray,
                    modifier = Modifier.weight(1f)
                )
                offer.rating?.let {
                    Text(
                        it.ron(2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RideGray
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "La ${offer.pickupTimeMinutes.min()} (${offer.pickupDistanceKm.km()}) distanță",
                        style = MaterialTheme.typography.titleMedium,
                        color = RideBlack
                    )
                    if (!offer.pickupAddress.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            offer.pickupAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RideGray
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Cursă: ${offer.tripTimeMinutes.min()} (${offer.tripDistanceKm.km()})",
                        style = MaterialTheme.typography.titleMedium,
                        color = RideBlack
                    )
                    if (!offer.destinationAddress.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            offer.destinationAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RideGray
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = when (analysis.verdict) {
                    Verdict.ACCEPT -> "CURSĂ BUNĂ — ACCEPTĂ"
                    Verdict.CAUTION -> "ATENȚIE — VERIFICĂ"
                    Verdict.REJECT -> "CURSĂ SLABĂ — RESPINGE"
                },
                onClick = onBack,
                container = RideBlack,
                content = RideWhite
            )
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("ÎNAPOI")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun VerdictBadge(verdict: Verdict) {
    val (label, bg, fg) = when (verdict) {
        Verdict.ACCEPT -> Triple("●  CURSĂ BUNĂ", RideGreen, Color.White)
        Verdict.CAUTION -> Triple("●  ATENȚIE", RideOrange, RideBlack)
        Verdict.REJECT -> Triple("●  CURSĂ SLABĂ", RideRed, Color.White)
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun GridStat(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RideWhite
) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 2.dp)
    )
}
