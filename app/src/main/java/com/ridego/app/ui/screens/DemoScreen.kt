package com.ridego.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ridego.app.data.DemoOffers
import com.ridego.app.parser.Platform
import com.ridego.app.ui.PrimaryButton
import com.ridego.app.ui.SectionLabel
import com.ridego.app.ui.theme.RideGray
import com.ridego.app.ui.theme.RideOrange
import com.ridego.app.ui.theme.RideYellow

/**
 * Runs the real pipeline (detector -> parser -> calculator -> verdict) on
 * stored OCR text, so the app can be validated without Uber or Bolt open.
 */
@Composable
fun DemoScreen(onRun: (String) -> Unit, onBack: () -> Unit) {
    var filter: Platform? by rememberSaveable { mutableStateOf(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("DEMO", style = MaterialTheme.typography.displayLarge, color = RideYellow)
        Text(
            "Testează detectorul, parserul și calculatorul fără Uber/Bolt.",
            style = MaterialTheme.typography.bodyMedium,
            color = RideGray
        )

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoChip("TOATE", filter == null) { filter = null }
            DemoChip("UBER", filter == Platform.UBER) { filter = Platform.UBER }
            DemoChip("BOLT", filter == Platform.BOLT) { filter = Platform.BOLT }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Oferte de test")
        Spacer(Modifier.height(12.dp))

        DemoOffers.forPlatform(filter).forEach { sample ->
            PrimaryButton(
                text = "${sample.platform.label} • ${sample.label}",
                onClick = { onRun(sample.ocrText) }
            )
            if (sample.synthetic) {
                Text(
                    "text sintetic — nu provine dintr-o captură reală",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideOrange,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("ÎNAPOI") }
    }
}

@Composable
private fun DemoChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
