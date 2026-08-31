package com.ridego.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridego.app.calculator.RideSettings
import com.ridego.app.calculator.RuleProfile
import com.ridego.app.data.HistoryEntry
import com.ridego.app.data.ShiftStats
import com.ridego.app.overlay.OverlayService
import com.ridego.app.parser.PlatformMode
import com.ridego.app.ui.PrimaryButton
import com.ridego.app.ui.RideCard
import com.ridego.app.ui.SectionLabel
import com.ridego.app.ui.theme.RideGray
import com.ridego.app.ui.theme.RideWhite
import com.ridego.app.ui.theme.RideYellow
import java.util.Locale
import kotlin.math.roundToInt

private val RO = Locale("ro", "RO")

private fun money(value: Double, decimals: Int = 2) =
    String.format(RO, "%,.${decimals}f", value)

private fun roundPace(value: Double): Double =
    (Math.round(value * 2) / 2.0).coerceIn(1.0, 6.0)

/**
 * Driver-first settings: goal, profile, pace, fuel, popup, behaviour.
 * Advanced knobs stay collapsed so mid-shift changes stay one-handed.
 */
@Composable
fun SettingsScreen(
    settings: RideSettings,
    history: List<HistoryEntry>,
    onChange: (RideSettings) -> Unit,
    onOpenOverlayDebug: () -> Unit,
    onOpenOptimization: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val shift = remember(history) { ShiftStats.summarise(history) }
    var showAdvanced by remember { mutableStateOf(false) }
    val costPerKm =
        settings.consumptionLPer100Km / 100.0 * settings.fuelPricePerLiter + settings.extraCostPerKm

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("SETĂRI", style = MaterialTheme.typography.headlineMedium, color = RideYellow)

        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = "CONFIGURARE (PERMISIUNI)", onClick = onOpenOptimization)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { OverlayService.test(context) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("VEZI CUM ARATĂ POPUP-UL") }

        // --- obiectiv ---------------------------------------------------
        Spacer(Modifier.height(20.dp))
        SectionLabel("Cât vrei pe oră")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Stepper(
                    value = settings.minimumRonPerHour,
                    step = 5.0,
                    range = 20.0..300.0,
                    format = { "${money(it, 0)} RON/oră" },
                    onValue = { onChange(settings.copy(minimumRonPerHour = it)) }
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(40, 50, 60, 70, 80, 100).forEach { preset ->
                        FilterChip(
                            selected = settings.minimumRonPerHour.roundToInt() == preset,
                            onClick = {
                                onChange(settings.copy(minimumRonPerHour = preset.toDouble()))
                            },
                            label = { Text(preset.toString()) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RideYellow,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }
        }

        // --- profil -----------------------------------------------------
        Spacer(Modifier.height(14.dp))
        SectionLabel("Profil rapid")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RuleProfile.entries.forEach { profile ->
                        val active = profile.matches(settings)
                        OutlinedButton(
                            onClick = { onChange(profile.applyTo(settings)) },
                            modifier = Modifier
                                .width(104.dp)
                                .height(62.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (active) RideYellow else Color.Transparent
                            )
                        ) {
                            Text(
                                profile.buttonLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = if (active) Color.Black else RideWhite
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    RuleProfile.entries.firstOrNull { it.matches(settings) }?.summary
                        ?: "Alege un profil, sau ajustează manual mai jos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray
                )
            }
        }

        // --- ritm -------------------------------------------------------
        Spacer(Modifier.height(14.dp))
        SectionLabel("Ritmul tău")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column {
                LabelledStepper(
                    label = "Curse/oră",
                    value = settings.ridesPerHour,
                    step = 0.5,
                    range = 1.0..6.0,
                    format = { money(it, 1) },
                    onValue = { onChange(settings.copy(ridesPerHour = it)) }
                )
                shift.measuredRidesPerHour?.let { measured ->
                    if (Math.abs(measured - settings.ridesPerHour) >= 0.25) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                onChange(settings.copy(ridesPerHour = roundPace(measured)))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("FOLOSEȘTE RITMUL REAL (${money(roundPace(measured), 1)})")
                        }
                    }
                }
            }
        }

        // --- mașină -----------------------------------------------------
        Spacer(Modifier.height(14.dp))
        SectionLabel("Mașina")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column {
                LabelledStepper(
                    label = "Consum",
                    value = settings.consumptionLPer100Km,
                    step = 0.5,
                    range = 2.0..25.0,
                    format = { "${money(it, 1)} L" },
                    onValue = { onChange(settings.copy(consumptionLPer100Km = it)) }
                )
                Spacer(Modifier.height(12.dp))
                LabelledStepper(
                    label = "Benzină",
                    value = settings.fuelPricePerLiter,
                    step = 0.10,
                    range = 3.0..20.0,
                    format = { "${money(it)} RON" },
                    onValue = { onChange(settings.copy(fuelPricePerLiter = it)) }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Cost: ${money(costPerKm)} RON/km",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideYellow
                )
            }
        }

        // --- popup ------------------------------------------------------
        Spacer(Modifier.height(14.dp))
        SectionLabel("Popup pe Uber")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column {
                ToggleRow("Arată popup", settings.overlayEnabled) {
                    onChange(settings.copy(overlayEnabled = it))
                }
                LabelledStepper(
                    label = "Text",
                    value = settings.overlayScalePercent.toDouble(),
                    step = 10.0,
                    range = 70.0..160.0,
                    format = { "${money(it, 0)} %" },
                    onValue = { onChange(settings.copy(overlayScalePercent = it.roundToInt())) }
                )
                Spacer(Modifier.height(12.dp))
                LabelledStepper(
                    label = "Durată",
                    value = settings.overlayDurationSeconds.toDouble(),
                    step = 5.0,
                    range = 5.0..30.0,
                    format = { "${money(it, 0)} sec" },
                    onValue = {
                        onChange(settings.copy(overlayDurationSeconds = it.roundToInt()))
                    }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { OverlayService.test(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("VEZI CUM ARATĂ") }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Card sus: RON/km, profit, combustibil. Uber/Bolt rămân vizibile jos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray
                )
            }
        }

        // --- comportament -----------------------------------------------
        Spacer(Modifier.height(14.dp))
        SectionLabel("Comportament")
        Spacer(Modifier.height(4.dp))
        ToggleRow("Citire automată", settings.autoRead) {
            onChange(settings.copy(autoRead = it))
        }
        ToggleRow("Sunet", settings.soundOnNewOffer) {
            onChange(settings.copy(soundOnNewOffer = it))
        }
        ToggleRow("Vibrație", settings.vibrate) {
            onChange(settings.copy(vibrate = it))
        }

        // --- avansat ----------------------------------------------------
        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showAdvanced) "ASCUNDE AVANSAT" else "AVANSAT")
        }

        if (showAdvanced) {
            Spacer(Modifier.height(14.dp))
            SectionLabel("Filtre")
            Spacer(Modifier.height(10.dp))
            RideCard {
                Column {
                    SimpleFilter(
                        label = "Preț minim",
                        enabled = settings.minimumFareEnabled,
                        onEnabled = { onChange(settings.copy(minimumFareEnabled = it)) },
                        value = settings.minimumFare,
                        step = 1.0,
                        range = 1.0..300.0,
                        format = { "${money(it, 0)} RON" },
                        onValue = { onChange(settings.copy(minimumFare = it)) }
                    )
                    SimpleFilter(
                        label = "Min RON/km",
                        enabled = settings.minCostPerKmEnabled,
                        onEnabled = { onChange(settings.copy(minCostPerKmEnabled = it)) },
                        value = settings.minCostPerKm,
                        step = 0.10,
                        range = 0.10..20.0,
                        format = { "${money(it)} RON/km" },
                        onValue = { onChange(settings.copy(minCostPerKm = it)) }
                    )
                    SimpleFilter(
                        label = "Max pickup",
                        enabled = settings.maxPickupKmEnabled,
                        onEnabled = { onChange(settings.copy(maxPickupKmEnabled = it)) },
                        value = settings.maxPickupKm,
                        step = 0.5,
                        range = 0.5..60.0,
                        format = { "${money(it, 1)} km" },
                        onValue = { onChange(settings.copy(maxPickupKm = it)) }
                    )
                    SimpleFilter(
                        label = "Max cursă",
                        enabled = settings.maxTripKmEnabled,
                        onEnabled = { onChange(settings.copy(maxTripKmEnabled = it)) },
                        value = settings.maxTripKm,
                        step = 1.0,
                        range = 1.0..300.0,
                        format = { "${money(it, 1)} km" },
                        onValue = { onChange(settings.copy(maxTripKm = it)) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("Calcul")
            Spacer(Modifier.height(4.dp))
            ToggleRow("Include pickup", settings.includePickup) {
                onChange(settings.copy(includePickup = it))
            }
            LabelledStepper(
                label = "Extra/km",
                value = settings.extraCostPerKm,
                step = 0.05,
                range = 0.0..5.0,
                format = { "${money(it)} RON" },
                onValue = { onChange(settings.copy(extraCostPerKm = it)) }
            )

            Spacer(Modifier.height(14.dp))
            SectionLabel("Platformă")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.platformMode == mode,
                        onClick = { onChange(settings.copy(platformMode = mode)) },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RideYellow,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            ToggleRow("Debug", settings.debugMode) {
                onChange(settings.copy(debugMode = it))
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton(text = "OPTIMIZARE TELEFON", onClick = onOpenOptimization)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onOpenOverlayDebug, modifier = Modifier.fillMaxWidth()) {
                Text("DEBUG OVERLAY")
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("ÎNAPOI") }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SimpleFilter(
    label: String,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    value: Double,
    step: Double,
    range: ClosedFloatingPointRange<Double>,
    format: (Double) -> String,
    onValue: (Double) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) RideWhite else RideGray,
                modifier = Modifier.weight(1f)
            )
            Checkbox(
                checked = enabled,
                onCheckedChange = onEnabled,
                colors = CheckboxDefaults.colors(checkedColor = RideYellow)
            )
        }
        if (enabled) {
            LabelledStepper(
                label = "",
                value = value,
                step = step,
                range = range,
                format = format,
                onValue = onValue
            )
        }
    }
}

@Composable
private fun Stepper(
    value: Double,
    step: Double,
    range: ClosedFloatingPointRange<Double>,
    format: (Double) -> String,
    onValue: (Double) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StepButton("−") { onValue(clamp(value - step, range)) }
        Text(
            format(value),
            style = MaterialTheme.typography.headlineMedium,
            color = RideYellow,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        StepButton("+") { onValue(clamp(value + step, range)) }
    }
}

@Composable
private fun LabelledStepper(
    label: String,
    value: Double,
    step: Double,
    range: ClosedFloatingPointRange<Double>,
    format: (Double) -> String,
    onValue: (Double) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (label.isNotEmpty()) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = RideGray,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        StepButton("−") { onValue(clamp(value - step, range)) }
        Text(
            format(value),
            style = MaterialTheme.typography.titleMedium,
            color = RideWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp)
        )
        StepButton("+") { onValue(clamp(value + step, range)) }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier.size(46.dp)
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) RideYellow else RideGray
        )
    }
}

private fun clamp(value: Double, range: ClosedFloatingPointRange<Double>): Double {
    val rounded = Math.round(value * 100.0) / 100.0
    return rounded.coerceIn(range.start, range.endInclusive)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
