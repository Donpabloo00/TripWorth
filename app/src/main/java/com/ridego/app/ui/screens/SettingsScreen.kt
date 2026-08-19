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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridego.app.calculator.OfferCalculator
import com.ridego.app.calculator.OverlayAnchor
import com.ridego.app.calculator.RuleProfile
import com.ridego.app.calculator.RideSettings
import com.ridego.app.overlay.OverlayService
import com.ridego.app.parser.PlatformMode
import com.ridego.app.ui.PrimaryButton
import com.ridego.app.ui.RideCard
import com.ridego.app.ui.SectionLabel
import com.ridego.app.ui.theme.RideGray
import com.ridego.app.ui.theme.RideGreen
import com.ridego.app.ui.theme.RideWhite
import com.ridego.app.ui.theme.RideYellow
import java.util.Locale
import kotlin.math.roundToInt

/** Average city speed used to translate an hourly target into a fare. */
private const val CITY_SPEED_KMH = 25.0

private val RO = Locale("ro", "RO")

private fun money(value: Double, decimals: Int = 2) =
    String.format(RO, "%,.${decimals}f", value)

/**
 * Rebuilt around the one question a driver actually asks: how much do I want
 * to make per hour. Everything else is either an input to that number or an
 * explanation of it.
 *
 * No free-text number fields: they invited typos mid-shift and surfaced float
 * noise like "9.699999809265137". Values move in fixed steps instead.
 */
@Composable
fun SettingsScreen(
    settings: RideSettings,
    onChange: (RideSettings) -> Unit,
    onOpenOverlayDebug: () -> Unit,
    onOpenOptimization: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val costPerKm = settings.consumptionLPer100Km / 100.0 * settings.fuelPricePerLiter +
        settings.extraCostPerKm
    val fuelPerHour = CITY_SPEED_KMH * costPerKm
    // The bar a single ride has to clear, once idle time is accounted for.
    val effectiveTarget = OfferCalculator.effectiveTarget(settings)
    val grossNeeded = effectiveTarget + fuelPerHour

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("SETĂRI", style = MaterialTheme.typography.headlineMedium, color = RideYellow)

        // --- the goal ---------------------------------------------------
        Spacer(Modifier.height(20.dp))
        SectionLabel("Obiectivul tău")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Cât vrei să îți rămână pe oră",
                    style = MaterialTheme.typography.bodyLarge,
                    color = RideWhite
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "după carburant, în buzunar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray
                )

                Spacer(Modifier.height(14.dp))
                Stepper(
                    value = settings.minimumRonPerHour,
                    step = 5.0,
                    range = 20.0..300.0,
                    format = { "${money(it, 0)} RON/oră" },
                    onValue = { onChange(settings.copy(minimumRonPerHour = it)) }
                )

                Spacer(Modifier.height(14.dp))
                // Horizontal scroll of the values a driver realistically picks.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(40, 50, 60, 70, 80, 90, 100, 120, 150).forEach { preset ->
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

        // --- what it means ----------------------------------------------
        Spacer(Modifier.height(14.dp))
        SectionLabel("Ce înseamnă asta")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column {
                Advice(
                    "La ${money(CITY_SPEED_KMH, 0)} km/h prin oraș, carburantul te costă " +
                        "${money(fuelPerHour)} RON în fiecare oră."
                )
                Spacer(Modifier.height(10.dp))
                Advice(
                    "Ca să îți rămână ${money(settings.minimumRonPerHour, 0)} RON pe oră de tură, " +
                        "o cursă trebuie să plătească circa ${money(grossNeeded, 0)} RON/oră brut."
                )
                Spacer(Modifier.height(10.dp))
                Advice(
                    "Practic: o cursă de 30 de minute merită de la " +
                        "${money(grossNeeded / 2)} RON în sus.",
                    highlight = true
                )
                Spacer(Modifier.height(10.dp))
                Advice(
                    "RON/km e afișat, dar nu mai decide verdictul. Tarifele reale sunt în " +
                        "jur de 2 RON/km, deci un prag pe kilometru respingea absolut tot."
                )
            }
        }

        // --- occupancy ------------------------------------------------------
        Spacer(Modifier.height(14.dp))
        SectionLabel("Grad de ocupare")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column {
                Text(
                    "Cât din tură ești efectiv în cursă",
                    style = MaterialTheme.typography.bodyLarge,
                    color = RideWhite
                )
                Spacer(Modifier.height(12.dp))
                LabelledStepper(
                    label = "Ocupare",
                    value = settings.utilizationPercent.toDouble(),
                    step = 5.0,
                    range = 30.0..100.0,
                    format = { "${money(it, 0)} %" },
                    onValue = { onChange(settings.copy(utilizationPercent = it.roundToInt())) }
                )
                Spacer(Modifier.height(12.dp))
                if (settings.utilizationPercent >= 100) {
                    Advice(
                        "La 100% fiecare cursă e judecată direct față de " +
                            "${money(settings.minimumRonPerHour, 0)} RON/oră. Dar dacă aștepți " +
                            "între curse, ora reală de la finalul turei va fi mai mică."
                    )
                } else {
                    Advice(
                        "Ca să scoți ${money(settings.minimumRonPerHour, 0)} RON/oră peste toată " +
                            "tura, fiecare cursă trebuie să plătească " +
                            "${money(effectiveTarget, 0)} RON/oră net.",
                        highlight = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Advice(
                        "Restul de ${100 - settings.utilizationPercent}% din tură stai fără " +
                            "cursă — timpul acela trebuie plătit tot din cursele pe care le faci."
                    )
                }
            }
        }

        // --- acceptance criteria --------------------------------------------
        Spacer(Modifier.height(14.dp))
        SectionLabel("Criterii de acceptare")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column {
                Text(
                    "Patru reguli dure. Bifată = se aplică; nebifată = ignorată complet. " +
                        "O ofertă care încalcă mai multe le vede pe toate în motiv.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    "PORNIRE RAPIDĂ",
                    style = MaterialTheme.typography.labelLarge,
                    color = RideYellow
                )
                Spacer(Modifier.height(8.dp))
                // Six of them: a scrolling row rather than a wrapped grid, so
                // the ladder from cheapest to strictest stays in one direction.
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
                Advice(
                    RuleProfile.entries.firstOrNull { it.matches(settings) }?.summary
                        ?: "Valori proprii. Apasă un profil ca să pornești de la o bază " +
                            "cunoscută, apoi ajustează — profilul setează și obiectivul " +
                            "pe oră, nu doar cele patru criterii.",
                    highlight = RuleProfile.entries.any { it.matches(settings) }
                )

                Spacer(Modifier.height(18.dp))
                Criterion(
                    label = "Preț minim total",
                    enabled = settings.minimumFareEnabled,
                    onEnabled = { onChange(settings.copy(minimumFareEnabled = it)) },
                    value = settings.minimumFare,
                    step = 1.0,
                    range = 1.0..300.0,
                    format = { "${money(it, 0)} RON" },
                    presets = listOf(15.0, 20.0, 25.0, 30.0),
                    presetFormat = { money(it, 0) },
                    what = "Respinge orice cursă care plătește mai puțin decât atât, " +
                        "indiferent cât de bine arată raportul pe kilometru sau pe oră.",
                    why = "Fiecare cursă are un cost fix pe care matematica pe oră nu îl " +
                        "vede: aștepți clientul, urcă, discutați, cauți adresa. O cursă " +
                        "de 12 lei poate ieși 150 RON/oră pe hârtie și tot să îți strice " +
                        "tura, pentru că ocupă un slot întreg.",
                    recommendation = "Recomandat: 20-30 RON. Sub 15 rar merită slotul; " +
                        "peste 35 începi să refuzi curse scurte și bine plătite.",
                    onValue = { onChange(settings.copy(minimumFare = it)) }
                )

                Criterion(
                    label = "Cost minim / km",
                    enabled = settings.minCostPerKmEnabled,
                    onEnabled = { onChange(settings.copy(minCostPerKmEnabled = it)) },
                    value = settings.minCostPerKm,
                    step = 0.10,
                    range = 0.10..20.0,
                    format = { "${money(it)} RON/km" },
                    presets = listOf(1.80, 2.00, 2.50, 3.00),
                    presetFormat = { money(it) },
                    what = "Folosește DOAR distanța cu clientul în mașină. Prețul minim " +
                        "cerut se recalculează pentru fiecare ofertă: " +
                        "km cu clientul × valoarea de aici.",
                    why = "O cursă lungă prost plătită arată acceptabil ca sumă totală. " +
                        "Regula asta o prinde: la ${money(settings.minCostPerKm)} RON/km, " +
                        "o cursă de 20 km trebuie să plătească minim " +
                        "${money(20 * settings.minCostPerKm, 0)} RON.",
                    recommendation = "Recomandat: 2,00-2,50 RON/km. Tarifele reale UberX " +
                        "din București sunt 2-4 RON/km pe distanța cu clientul, deci " +
                        "peste 3,00 respingi majoritatea ofertelor.",
                    onValue = { onChange(settings.copy(minCostPerKm = it)) }
                )

                Criterion(
                    label = "Distanța maximă până la preluare",
                    enabled = settings.maxPickupKmEnabled,
                    onEnabled = { onChange(settings.copy(maxPickupKmEnabled = it)) },
                    value = settings.maxPickupKm,
                    step = 0.5,
                    range = 0.5..60.0,
                    format = { "${money(it, 1)} km" },
                    presets = listOf(2.0, 3.0, 5.0, 8.0),
                    presetFormat = { money(it, 0) },
                    what = "Respinge oferta dacă drumul până la client depășește " +
                        "distanța setată. Se uită doar la preluare, nu la cursă.",
                    why = "Drumul până la client e singurul pe care nu ți-l plătește " +
                        "nimeni: consumi benzină și minute pe gratis. La 8 km până la " +
                        "client ai făcut deja o cursă întreagă înainte să începi.",
                    recommendation = "Recomandat: 3-5 km. Sub 2 km ratezi oferte bune în " +
                        "orele libere; peste 8 km lucrezi degeaba pe distanța de apropiere.",
                    onValue = { onChange(settings.copy(maxPickupKm = it)) }
                )

                Criterion(
                    label = "Cursa maximă acceptată",
                    enabled = settings.maxTripKmEnabled,
                    onEnabled = { onChange(settings.copy(maxTripKmEnabled = it)) },
                    value = settings.maxTripKm,
                    step = 1.0,
                    range = 1.0..300.0,
                    format = { "${money(it, 1)} km" },
                    presets = listOf(15.0, 25.0, 30.0, 50.0),
                    presetFormat = { money(it, 0) },
                    what = "Respinge cursele mai lungi decât atât, măsurat pe distanța " +
                        "cu clientul în mașină.",
                    why = "O cursă foarte lungă te scoate din zona în care primești " +
                        "comenzi și te poate lăsa să te întorci gol zeci de kilometri. " +
                        "Plătește bine pe moment și îți poate goli restul turei.",
                    recommendation = "Recomandat: 25-30 km. Ridică spre 50 dacă lucrezi " +
                        "și aeroport; coboară spre 15 dacă vrei să rămâi strict în oraș.",
                    onValue = { onChange(settings.copy(maxTripKm = it)) }
                )

                Spacer(Modifier.height(8.dp))
                val activeCount = listOf(
                    settings.minimumFareEnabled,
                    settings.minCostPerKmEnabled,
                    settings.maxPickupKmEnabled,
                    settings.maxTripKmEnabled
                ).count { it }
                if (activeCount == 0) {
                    Advice(
                        "Niciun criteriu activ — verdictul rămâne doar pe pragul orar."
                    )
                } else {
                    Advice(
                        "$activeCount din 4 criterii active.",
                        highlight = true
                    )
                }
            }
        }

        // --- what counts --------------------------------------------------
        Spacer(Modifier.height(14.dp))
        SectionLabel("Ce intră în calcul")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column {
                ToggleRow("Numără și drumul până la client", settings.includePickup) {
                    onChange(settings.copy(includePickup = it))
                }
                Spacer(Modifier.height(6.dp))
                if (settings.includePickup) {
                    Advice(
                        "PORNIT: se socotesc și kilometrii și minutele până la client. " +
                            "Ăsta e adevărul despre ora ta — drumul până acolo consumă " +
                            "benzină și timp, chiar dacă nu ți-l plătește nimeni."
                    )
                } else {
                    Advice(
                        "OPRIT: se socotește doar cursa plătită. Cifrele ies mai mari, " +
                            "dar nu mai reflectă ora reală — un pickup de 20 de minute " +
                            "dispare din calcul, deși l-ai condus.",
                        highlight = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Advice(
                        "Util ca să compari tarife între ele. Nu îl folosi ca să decizi " +
                            "dacă tura merită."
                    )
                }
            }
        }

        // --- the car ------------------------------------------------------
        Spacer(Modifier.height(14.dp))
        SectionLabel("Mașina ta")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column {
                LabelledStepper(
                    label = "Consum",
                    value = settings.consumptionLPer100Km,
                    step = 0.5,
                    range = 2.0..25.0,
                    format = { "${money(it, 1)} L/100km" },
                    onValue = { onChange(settings.copy(consumptionLPer100Km = it)) }
                )
                Spacer(Modifier.height(12.dp))
                LabelledStepper(
                    label = "Carburant",
                    value = settings.fuelPricePerLiter,
                    step = 0.10,
                    range = 3.0..20.0,
                    format = { "${money(it)} RON/L" },
                    onValue = { onChange(settings.copy(fuelPricePerLiter = it)) }
                )
                Spacer(Modifier.height(12.dp))
                LabelledStepper(
                    label = "Cost extra",
                    value = settings.extraCostPerKm,
                    step = 0.05,
                    range = 0.0..5.0,
                    format = { "${money(it)} RON/km" },
                    onValue = { onChange(settings.copy(extraCostPerKm = it)) }
                )

                Spacer(Modifier.height(14.dp))
                Advice(
                    "Te costă ${money(costPerKm)} RON fiecare kilometru — " +
                        "${money(costPerKm * 10)} RON la fiecare 10 km.",
                    highlight = true
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "„Cost extra\" e pentru uzură, anvelope, service — dacă vrei să le pui la " +
                        "socoteală, nu doar benzina.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray
                )
            }
        }

        // --- platform -----------------------------------------------------
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

        // --- popup ----------------------------------------------------------
        Spacer(Modifier.height(14.dp))
        SectionLabel("Popup peste Uber/Bolt")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column {
                LabelledStepper(
                    label = "Text",
                    value = settings.overlayScalePercent.toDouble(),
                    step = 10.0,
                    range = 50.0..200.0,
                    format = { "${money(it, 0)} %" },
                    onValue = { onChange(settings.copy(overlayScalePercent = it.roundToInt())) }
                )
                Spacer(Modifier.height(12.dp))
                LabelledStepper(
                    label = "Lățime",
                    value = settings.overlayWidthPercent.toDouble(),
                    step = 4.0,
                    range = 50.0..100.0,
                    format = { "${money(it, 0)} %" },
                    onValue = { onChange(settings.copy(overlayWidthPercent = it.roundToInt())) }
                )
                Spacer(Modifier.height(12.dp))
                LabelledStepper(
                    label = "Înălțime max",
                    value = settings.overlayMaxHeightPercent.toDouble(),
                    step = 5.0,
                    range = 30.0..100.0,
                    format = { "${money(it, 0)} %" },
                    onValue = {
                        onChange(settings.copy(overlayMaxHeightPercent = it.roundToInt()))
                    }
                )
                Spacer(Modifier.height(12.dp))
                LabelledStepper(
                    label = "Opacitate",
                    value = settings.overlayOpacityPercent.toDouble(),
                    step = 5.0,
                    range = 30.0..100.0,
                    format = { "${money(it, 0)} %" },
                    onValue = { onChange(settings.copy(overlayOpacityPercent = it.roundToInt())) }
                )
                Spacer(Modifier.height(12.dp))
                LabelledStepper(
                    label = "Durată",
                    value = settings.overlayDurationSeconds.toDouble(),
                    step = 5.0,
                    range = 5.0..60.0,
                    format = { "${money(it, 0)} sec" },
                    onValue = {
                        onChange(settings.copy(overlayDurationSeconds = it.roundToInt()))
                    }
                )

                Spacer(Modifier.height(12.dp))
                ToggleRow("Butoane accept / refuz", settings.overlayDecisionButtons) {
                    onChange(settings.copy(overlayDecisionButtons = it))
                }
                if (settings.overlayDecisionButtons) {
                    Advice(
                        "Butoanele notează ce ai decis TU. RideGo nu apasă nimic în " +
                            "Uber sau Bolt — citește ecranul, nu îl comandă."
                    )
                }

                Spacer(Modifier.height(14.dp))
                Advice(
                    "Cât timp bannerul e pe ecran, citirea e pusă pe pauză — altfel " +
                        "RideGo și-ar citi propriul banner. La ${settings.overlayDurationSeconds} " +
                        "secunde, atâta durează pauza după fiecare ofertă.",
                    highlight = settings.overlayDurationSeconds > 20
                )

                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { OverlayService.test(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("VEZI CUM ARATĂ") }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Apasă, apoi ieși din RideGo — bannerul se desenează peste ecranul " +
                        "de dedesubt. Poți să îl tragi cu degetul; poziția se ține minte.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray
                )
            }
        }

        // --- position -------------------------------------------------------
        Spacer(Modifier.height(14.dp))
        SectionLabel("Poziție pe ecran")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column {
                // A 3x3 grid that mirrors the screen, so the tapped square is
                // literally where the banner will sit.
                val grid = listOf(
                    listOf(
                        OverlayAnchor.TOP_LEFT,
                        OverlayAnchor.TOP_CENTER,
                        OverlayAnchor.TOP_RIGHT
                    ),
                    listOf(
                        OverlayAnchor.CENTER_LEFT,
                        OverlayAnchor.CENTER,
                        OverlayAnchor.CENTER_RIGHT
                    ),
                    listOf(
                        OverlayAnchor.BOTTOM_LEFT,
                        OverlayAnchor.BOTTOM_CENTER,
                        OverlayAnchor.BOTTOM_RIGHT
                    )
                )
                grid.forEach { rowAnchors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowAnchors.forEach { anchor ->
                            val selected = settings.overlayAnchor == anchor
                            OutlinedButton(
                                onClick = { onChange(settings.copy(overlayAnchor = anchor)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) RideYellow else Color.Transparent
                                )
                            ) {
                                Text(
                                    anchor.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = if (selected) Color.Black else RideWhite
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(6.dp))
                LabelledStepper(
                    label = "Distanță laterală",
                    value = settings.overlayMarginX.toDouble(),
                    step = 4.0,
                    range = 0.0..120.0,
                    format = { "${money(it, 0)} dp" },
                    onValue = { onChange(settings.copy(overlayMarginX = it.roundToInt())) }
                )
                Spacer(Modifier.height(12.dp))
                LabelledStepper(
                    label = "Distanță sus/jos",
                    value = settings.overlayMarginY.toDouble(),
                    step = 4.0,
                    range = 0.0..300.0,
                    format = { "${money(it, 0)} dp" },
                    onValue = { onChange(settings.copy(overlayMarginY = it.roundToInt())) }
                )

                Spacer(Modifier.height(14.dp))
                if (settings.overlayAnchor.isPreset) {
                    Advice(
                        "Bannerul stă fixat ${settings.overlayAnchor.label.lowercase()}. " +
                            "Rămâne acolo și după rotirea ecranului sau după repornire."
                    )
                } else {
                    Advice(
                        "Bannerul e la poziția în care l-ai tras cu degetul. Alege un " +
                            "pătrat de mai sus dacă vrei să îl fixezi înapoi pe o margine.",
                        highlight = true
                    )
                }
                Spacer(Modifier.height(8.dp))
                Advice(
                    "Dacă îl tragi cu degetul peste ecranul Uber, poziția aleasă aici " +
                        "e înlocuită de cea trasă."
                )
            }
        }

        // --- behaviour ----------------------------------------------------
        Spacer(Modifier.height(20.dp))
        SectionLabel("Comportament")
        Spacer(Modifier.height(4.dp))
        ToggleRow("Citire automată", settings.autoRead) {
            onChange(settings.copy(autoRead = it))
        }
        ToggleRow("Sunet ofertă nouă", settings.soundOnNewOffer) {
            onChange(settings.copy(soundOnNewOffer = it))
        }
        ToggleRow("Vibrație", settings.vibrate) {
            onChange(settings.copy(vibrate = it))
        }
        ToggleRow("Popup peste Uber/Bolt", settings.overlayEnabled) {
            onChange(settings.copy(overlayEnabled = it))
        }
        ToggleRow("Debug Mode", settings.debugMode) {
            onChange(settings.copy(debugMode = it))
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = "OPTIMIZARE TELEFON", onClick = onOpenOptimization)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onOpenOverlayDebug, modifier = Modifier.fillMaxWidth()) {
            Text("DEBUG OVERLAY")
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Datele capturate sunt procesate local pe dispozitiv. RideGo nu trimite " +
                "capturi de ecran sau date către niciun server.",
            style = MaterialTheme.typography.bodyMedium,
            color = RideGray
        )

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("ÎNAPOI") }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * One acceptance rule, presented so the driver can decide without guessing:
 * what it does, why it matters, what to set it to, and four values to tap.
 *
 * The stepper and the presets grey out while the rule is off, so a number on
 * screen is never mistaken for one the verdict is using.
 */
@Composable
private fun Criterion(
    label: String,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    value: Double,
    step: Double,
    range: ClosedFloatingPointRange<Double>,
    format: (Double) -> String,
    presets: List<Double>,
    presetFormat: (Double) -> String,
    what: String,
    why: String,
    recommendation: String,
    onValue: (Double) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 18.dp)) {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            StepButton("−", enabled) { onValue(clamp(value - step, range)) }
            Text(
                format(value),
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) RideYellow else RideGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(140.dp)
            )
            StepButton("+", enabled) { onValue(clamp(value + step, range)) }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = enabled && Math.abs(value - preset) < 0.005,
                    onClick = { onValue(preset) },
                    enabled = enabled,
                    label = { Text(presetFormat(preset)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RideYellow,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }

        if (enabled) {
            Spacer(Modifier.height(10.dp))
            Text(what, style = MaterialTheme.typography.bodyMedium, color = RideWhite)
            Spacer(Modifier.height(6.dp))
            Text(why, style = MaterialTheme.typography.bodyMedium, color = RideGray)
            Spacer(Modifier.height(6.dp))
            Text(
                recommendation,
                style = MaterialTheme.typography.bodyMedium,
                color = RideGreen
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Dezactivat — regula e ignorată complet.",
                style = MaterialTheme.typography.bodyMedium,
                color = RideGray
            )
        }
    }
}

@Composable
private fun Advice(text: String, highlight: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (highlight) RideGreen else RideGray
    )
}

/** Big central value with a round control either side — usable one-handed. */
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
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = RideGray,
            modifier = Modifier.weight(1f)
        )
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

/** Keeps values on clean steps: 9.7 + 0.1 must not become 9.799999999. */
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
