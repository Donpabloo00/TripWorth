package com.ridego.app.calculator

import com.ridego.app.parser.RideOffer


enum class Verdict { ACCEPT, CAUTION, REJECT }

data class OfferAnalysis(
    val offer: RideOffer,
    val totalKm: Double?,
    val totalMinutes: Int?,
    val ronPerKm: Double?,
    val ronPerHour: Double?,
    /** What the hour is actually worth once fuel is paid — the verdict runs on this. */
    val netRonPerHour: Double?,
    val ronPerMinute: Double?,
    val fuelCost: Double?,
    val estimatedProfit: Double?,
    val verdict: Verdict,
    /**
     * Every reason the verdict holds, not just the first one found.
     *
     * A driver who fixes the one problem the app mentioned only to be
     * rejected again for a second learns nothing; the whole picture has to
     * arrive at once.
     */
    val reasons: List<String>,
    /** Per-ride bar after the occupancy adjustment. */
    val effectiveHourlyTarget: Double
) {
    /** Flattened for the single-line surfaces; bulleted once there is more than one. */
    val reason: String
        get() = when {
            reasons.isEmpty() -> ""
            reasons.size == 1 -> reasons.first()
            else -> reasons.joinToString("\n") { "• $it" }
        }
}

object OfferCalculator {

    private val RO = java.util.Locale("ro", "RO")

    /**
     * The goal is stated per shift, but only part of a shift is spent on a
     * trip. A 60 RON/h goal at 70% occupancy means each ride has to pay
     * 60 / 0.70 = 86 RON/h for the shift to land on 60.
     */
    fun effectiveTarget(settings: RideSettings): Double {
        val share = settings.utilizationPercent.coerceIn(1, 100) / 100.0
        return settings.minimumRonPerHour / share
    }


    /** Within this band of a threshold the call is too close to be confident. */
    private const val BORDERLINE_MARGIN = 0.05

    fun analyze(offer: RideOffer, settings: RideSettings): OfferAnalysis {
        // Totals follow the driver's choice about the approach leg. Either
        // way a missing leg stays null — never silently treated as zero.
        val totalKm = if (settings.includePickup) {
            offer.totalDistanceKm
        } else {
            offer.tripDistanceKm
        }
        val implausible = offer.implausibleLeg(settings.includePickup)
        // Every per-minute figure inherits the mis-read minute's error, so
        // none of them are produced at all. A dash is honest; "1223 RON/oră"
        // is not, and it is the number the driver would have acted on.
        val totalMinutes = if (implausible != null) {
            null
        } else if (settings.includePickup) {
            offer.totalTimeMinutes
        } else {
            offer.tripTimeMinutes
        }
        val price = offer.price

        val ronPerKm = if (price != null && totalKm != null && totalKm > 0) price / totalKm else null
        val ronPerMinute =
            if (price != null && totalMinutes != null && totalMinutes > 0) price / totalMinutes else null
        val ronPerHour = ronPerMinute?.times(60)

        val costPerKm =
            settings.consumptionLPer100Km / 100.0 * settings.fuelPricePerLiter + settings.extraCostPerKm
        val fuelCost = totalKm?.times(costPerKm)
        val estimatedProfit = if (price != null && fuelCost != null) price - fuelCost else null

        val netRonPerHour =
            if (estimatedProfit != null && totalMinutes != null && totalMinutes > 0) {
                estimatedProfit / totalMinutes * 60
            } else {
                null
            }

        // A ride has to clear the hard filters before the maths gets a say:
        // some jobs are badly shaped whatever the ratio works out to.
        val (verdict, reasons) = if (!offer.isReliableFor(settings.includePickup)) {
            Verdict.CAUTION to listOf("Date incomplete (${offer.confidence}%) — verifică manual")
        } else if (implausible != null) {
            // Never a verdict on impossible arithmetic: a mis-read minute
            // makes every ratio below it wrong by the same factor, and the
            // result looks confident rather than broken.
            Verdict.CAUTION to listOf("Citire greșită — $implausible. Verifică manual.")
        } else {
            val blockers = hardFilterFailures(offer, settings)
            if (blockers.isNotEmpty()) {
                Verdict.REJECT to blockers
            } else {
                val (v, r) = decide(netRonPerHour, settings)
                v to listOf(r)
            }
        }

        return OfferAnalysis(
            offer = offer,
            totalKm = totalKm,
            totalMinutes = totalMinutes,
            ronPerKm = ronPerKm,
            ronPerHour = ronPerHour,
            netRonPerHour = netRonPerHour,
            ronPerMinute = ronPerMinute,
            fuelCost = fuelCost,
            estimatedProfit = estimatedProfit,
            verdict = verdict,
            reasons = reasons,
            effectiveHourlyTarget = effectiveTarget(settings)
        )
    }

    /**
     * Every hard rule this offer breaks, in the order the driver reads them.
     *
     * Collected rather than short-circuited: an offer that is both too cheap
     * and too far away should say both, so the driver knows there is nothing
     * to salvage here.
     *
     * A rule is consulted only when its switch is on — the stored amount is
     * irrelevant otherwise.
     */
    private fun hardFilterFailures(offer: RideOffer, settings: RideSettings): List<String> {
        val failures = mutableListOf<String>()
        val price = offer.price

        // Rule 1 — the floor under the fare itself.
        if (settings.minimumFareEnabled && settings.minimumFare > 0 &&
            price != null && price < settings.minimumFare
        ) {
            failures += String.format(
                RO,
                "%.2f RON, sub minimul de %.0f RON pe cursă",
                price,
                settings.minimumFare
            )
        }

        // Rule 2 — the paid ride has to cover its own distance at the
        // driver's floor rate. Measured on tripDistanceKm alone: the approach
        // leg is not what the fare is paying for.
        val tripKm = offer.tripDistanceKm
        if (settings.minCostPerKmEnabled && settings.minCostPerKm > 0 &&
            price != null && tripKm != null && tripKm > 0
        ) {
            val required = tripKm * settings.minCostPerKm
            if (price < required) {
                failures += String.format(
                    RO,
                    "Sub costul minim: %s RON necesari (%s km × %s RON/km)",
                    trim(required),
                    trim(tripKm),
                    trim(settings.minCostPerKm)
                )
            }
        }

        // Rule 3 — distance to the rider.
        val pickupKm = offer.pickupDistanceKm
        if (settings.maxPickupKmEnabled && settings.maxPickupKm > 0 &&
            pickupKm != null && pickupKm > settings.maxPickupKm
        ) {
            failures += String.format(
                RO,
                "Preluare prea departe: %s km / maxim %s km",
                trim(pickupKm),
                trim(settings.maxPickupKm)
            )
        }

        // Rule 4 — length of the paid ride.
        if (settings.maxTripKmEnabled && settings.maxTripKm > 0 &&
            tripKm != null && tripKm > settings.maxTripKm
        ) {
            failures += String.format(
                RO,
                "Cursa prea lungă: %s km / maxim %s km",
                trim(tripKm),
                trim(settings.maxTripKm)
            )
        }

        return failures
    }

    /** "60" rather than "60.00", but "2.5" stays "2.5". */
    private fun trim(value: Double): String =
        if (Math.abs(value - Math.round(value)) < 0.005) {
            String.format(RO, "%.0f", value)
        } else {
            String.format(RO, "%.2f", value).trimEnd('0')
        }

    /**
     * The driver's goal is an hourly take-home figure, so that is the only
     * thing the verdict tests.
     *
     * RON/km used to be an AND condition alongside it. Real Bucharest UberX
     * fares run around 2.1 RON/km, so any threshold high enough to be
     * meaningful rejected every offer and the hourly bar never got to decide
     * anything. It is still calculated and shown, just not used to judge.
     */
    private fun decide(
        netRonPerHour: Double?,
        settings: RideSettings
    ): Pair<Verdict, String> {
        if (netRonPerHour == null) {
            return Verdict.CAUTION to "Date incomplete — verifică manual oferta"
        }

        val target = effectiveTarget(settings)
        val shortfall = target - netRonPerHour
        // Say so on every verdict: the same fare reads very differently
        // depending on whether the drive to the rider is counted.
        val suffix = buildString {
            if (!settings.includePickup) append(" (fără pickup)")
            if (settings.utilizationPercent in 1..99) {
                // Rounded, not truncated: "%.0f" is used for the same number
                // further along, and 52.6 printed as both 52 and 53 in one
                // sentence reads as a bug in the maths.
                append(
                    " [prag ${Math.round(target)} la ${settings.utilizationPercent}% ocupare]"
                )
            }
        }

        if (netRonPerHour < target) {
            return Verdict.REJECT to String.format(
                java.util.Locale("ro", "RO"),
                "%.0f RON/oră net%s, cu %.0f sub pragul de %.0f",
                netRonPerHour,
                suffix,
                shortfall,
                target
            )
        }

        // Clears the bar, but only just — worth a flag rather than a green light.
        if (netRonPerHour < target * (1 + BORDERLINE_MARGIN)) {
            return Verdict.CAUTION to String.format(
                java.util.Locale("ro", "RO"),
                "%.0f RON/oră net%s, la limita pragului de %.0f",
                netRonPerHour,
                suffix,
                target
            )
        }

        return Verdict.ACCEPT to String.format(
            java.util.Locale("ro", "RO"),
            "%.0f RON/oră net%s, peste pragul de %.0f",
            netRonPerHour,
            suffix,
            target
        )
    }
}
