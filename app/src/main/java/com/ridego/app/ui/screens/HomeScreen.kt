package com.ridego.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tripworth.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridego.app.calculator.OfferAnalysis
import com.ridego.app.calculator.Verdict
import com.ridego.app.data.PlatformStats
import com.ridego.app.data.Stats
import com.ridego.app.overlay.OverlayService
import com.ridego.app.parser.PlatformMode
import com.ridego.app.ui.MetricRow
import com.ridego.app.ui.PrimaryButton
import com.ridego.app.ui.RideCard
import com.ridego.app.ui.SectionLabel
import com.ridego.app.ui.StatusDot
import com.ridego.app.ui.color
import com.ridego.app.ui.km
import com.ridego.app.ui.label
import com.ridego.app.ui.min
import com.ridego.app.ui.ron
import com.ridego.app.ui.theme.RideBlack
import com.ridego.app.ui.theme.RideGray
import com.ridego.app.ui.theme.RideGreen
import com.ridego.app.ui.theme.RideOrange
import com.ridego.app.ui.theme.RideRed
import com.ridego.app.ui.theme.RideSurface
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
    onOpenConfig: () -> Unit,
    onOpenDebug: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            color = RideOrange
        )
        Text(
            stringResource(R.string.slogan),
            style = MaterialTheme.typography.bodyMedium,
            color = RideGray
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onOpenConfig, modifier = Modifier.weight(1f)) {
                Text("CONFIGURARE", color = RideOrange)
            }
            OutlinedButton(
                onClick = { OverlayService.test(context) },
                modifier = Modifier.weight(1f)
            ) {
                Text("VEZI POPUP", color = RideOrange)
            }
        }

        Spacer(Modifier.height(16.dp))

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
                                selectedContainerColor = RideOrange,
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
 * Same analysis card the overlay draws over Uber — so Home already shows
 * the new look without waiting for a live offer.
 */
@Composable
private fun LastOfferCard(
    analysis: OfferAnalysis?,
    includePickup: Boolean,
    onOpenDetails: () -> Unit
) {
    if (analysis == null) {
        RideCard {
            Column {
                SectionLabel("Ultima ofertă")
                Spacer(Modifier.height(10.dp))
                Text(
                    "Aștept o ofertă... sau apasă DEMO / VEZI POPUP.",
                    style = MaterialTheme.typography.titleMedium,
                    color = RideGray
                )
            }
        }
        return
    }

    val offer = analysis.offer
    val hourly = analysis.netRonPerHour ?: analysis.ronPerHour

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
            HomeVerdictBadge(analysis.verdict)
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                "${analysis.ronPerKm.ron(2)} RON/km",
                fontSize = 30.sp,
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

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(vertical = 10.dp, horizontal = 4.dp)
        ) {
            HomeGridStat(
                analysis.ronPerHour?.let { "${it.ron(0)} RON/oră" } ?: "—",
                Modifier.weight(1f)
            )
            HomeGridStat(
                analysis.estimatedProfit?.let { "${it.ron()} PROFIT" } ?: "—",
                Modifier.weight(1.1f),
                color = when {
                    analysis.estimatedProfit == null -> RideGray
                    analysis.estimatedProfit >= 0 -> RideGreen
                    else -> RideRed
                }
            )
            HomeGridStat(
                analysis.fuelCost?.let { "${it.ron()} COMB." } ?: "—",
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Încasezi  ${offer.price.ron()} RON",
            style = MaterialTheme.typography.titleMedium,
            color = RideWhite
        )
        if (includePickup) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Distanță la client  ${offer.pickupDistanceKm.km()}  •  ${offer.pickupTimeMinutes.min()}",
                style = MaterialTheme.typography.bodyMedium,
                color = RideGray
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth()) {
            Text("DETALII COMPLETE", color = RideOrange)
        }
    }
}

@Composable
private fun HomeVerdictBadge(verdict: Verdict) {
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
private fun HomeGridStat(
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
