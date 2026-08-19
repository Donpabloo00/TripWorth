package com.ridego.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ridego.app.parser.ParseResult
import com.ridego.app.parser.RideOffer
import com.ridego.app.ui.RideCard
import com.ridego.app.ui.SectionLabel
import com.ridego.app.ui.theme.RideGray
import com.ridego.app.ui.theme.RideGreen
import com.ridego.app.ui.theme.RideOrange
import com.ridego.app.ui.theme.RideRed
import com.ridego.app.ui.theme.RideWhite
import com.ridego.app.ui.theme.RideYellow

/**
 * Shows exactly what the pipeline saw on the last screen it read. This is the
 * screen to open when RideGo "does nothing" — it separates an OCR problem
 * from a parser problem.
 */
@Composable
fun DebugScreen(result: ParseResult?, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("DEBUG", style = MaterialTheme.typography.displayLarge, color = RideYellow)
        Spacer(Modifier.height(16.dp))

        if (result == null) {
            Text(
                "Nicio citire încă. Pornește citirea sau rulează o ofertă din Demo.",
                style = MaterialTheme.typography.bodyLarge,
                color = RideGray
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("ÎNAPOI") }
            return
        }

        val offer = result.offer
        val confidence = result.confidence

        RideCard {
            Column {
                DebugRow("Platform", result.platform.label)
                DebugRow("Parser", result.parserName)
                DebugRow("Confidence", "$confidence%")
                Spacer(Modifier.height(10.dp))
                Text(
                    if (confidence >= RideOffer.MIN_CONFIDENCE) "OFERTĂ VALIDĂ"
                    else "OFERTĂ DATE INCOMPLETE",
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        confidence >= RideOffer.MIN_CONFIDENCE -> RideGreen
                        confidence > 0 -> RideOrange
                        else -> RideRed
                    }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        RideCard {
            Column {
                SectionLabel("Preț")
                Spacer(Modifier.height(8.dp))
                DebugField("price", offer?.price?.toString())

                Spacer(Modifier.height(16.dp))
                SectionLabel("Pickup client")
                Spacer(Modifier.height(8.dp))
                DebugField("distance", offer?.pickupDistanceKm?.let { "$it km" })
                DebugField("time", offer?.pickupTimeMinutes?.let { "$it min" })
                DebugField("address", offer?.pickupAddress)

                Spacer(Modifier.height(16.dp))
                SectionLabel("Cursă efectivă")
                Spacer(Modifier.height(8.dp))
                DebugField("distance", offer?.tripDistanceKm?.let { "$it km" })
                DebugField("time", offer?.tripTimeMinutes?.let { "$it min" })
                DebugField("address", offer?.destinationAddress)

                Spacer(Modifier.height(16.dp))
                SectionLabel("Total")
                Spacer(Modifier.height(8.dp))
                DebugField("distance", offer?.totalDistanceKm?.let { "$it km" })
                DebugField("time", offer?.totalTimeMinutes?.let { "$it min" })

                Spacer(Modifier.height(16.dp))
                SectionLabel("Alte câmpuri")
                Spacer(Modifier.height(8.dp))
                DebugField("service", offer?.serviceType)
                DebugField("rating", offer?.rating?.toString())
                DebugField("payment", offer?.paymentMethod)
            }
        }

        Spacer(Modifier.height(14.dp))

        RideCard {
            Column {
                SectionLabel("OCR text brut")
                Spacer(Modifier.height(10.dp))
                Text(
                    result.rawText.ifBlank { "(gol)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = RideGray
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("ÎNAPOI") }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RideGray)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = RideWhite)
    }
}

@Composable
private fun DebugField(label: String, value: String?) {
    Row(Modifier.padding(vertical = 3.dp).fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RideGray)
        Spacer(Modifier.weight(1f))
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = if (value == null) RideRed else RideWhite
        )
    }
}
