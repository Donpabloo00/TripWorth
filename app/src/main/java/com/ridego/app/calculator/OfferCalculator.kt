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
    /**
     * Short, stable names of the rules this offer broke — "preț minim",
     * "preluare" and so on. The reasons carry numbers and read as sentences;
     * these are for counting, so the history can say which filter turns away
     * the most work.
     */
    val failedRules: List<String>,
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

    // Stable names for the rules, so history can count them without parsing
    // the Romanian sentences the driver reads.
    const val RULE_MIN_FARE = "preț minim"
    const val RULE_COST_PER_KM = "cost minim / km"
    const val RULE_PICKUP = "preluare prea departe"
    const val RULE_TRIP_LENGTH = "cursă prea lungă"
    const val RULE_HOURLY = "prag orar"
    const val RULE_UNREADABLE = "citire incompletă"

    /**
     * The bar a ride has to clear. Now simply the driver's goal.
     *
     * It used to be the goal divided by a "utilisation" percentage, because
     * the displayed rate assumed a shift of back-to-back driving and had to be
     * compensated for somewhere. The rate itself is honest now — see
     * [slotMinutes] — so the threshold needs no hidden correction.
     */
    fun effectiveTarget(settings: RideSettings): Double = settings.minimumRonPerHour

    /**
     * How much of the shift this ride really consumes.
     *
     * At N rides an hour, a ride occupies at least 60/N minutes: the waiting
     * that follows it is part of its cost, and has to be paid for out of the
     * rides that do happen. A ride longer than that slot occupies its own
     * duration instead — a 45-minute job does not fit into an hour 2.5 times,
     * so there is no idle time to charge it for.
     */
    fun slotMinutes(rideMinutes: Int, settings: RideSettings): Double {
        val rides = settings.ridesPerHour.coerceIn(0.5, 10.0)
        return Math.max(rideMinutes.toDouble(), 60.0 / rides)
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

        // Both hourly figures describe an hour of shift, not an hour of this
        // one ride repeated. Anything else overstates every short job.
        val slot = totalMinutes?.let { slotMinutes(it, settings) }
        val ronPerHour = if (price != null && slot != null && slot > 0) price / slot * 60 else null

        val costPerKm =
            settings.consumptionLPer100Km / 100.0 * settings.fuelPricePerLiter + settings.extraCostPerKm
        val fuelCost = totalKm?.times(costPerKm)
        val estimatedProfit = if (price != null && fuelCost != null) price - fuelCost else null

        val netRonPerHour =
            if (estimatedProfit != null && slot != null && slot > 0) {
                estimatedProfit / slot * 60
            } else {
                null
            }

        // A ride has to clear the hard filters before the maths gets a say:
        // some jobs are badly shaped whatever the ratio works out to.
        val (verdict, reasons, failedRules) = when {
            !offer.isReliableFor(settings.includePickup) -> Triple(
                Verdict.CAUTION,
                listOf("Date incomplete (${offer.confidence}%) — verifică manual"),
                listOf(RULE_UNREADABLE)
            )

            implausible != null -> Triple(
                // Never a verdict on impossible arithmetic: a mis-read minute
                // makes every ratio below it wrong by the same factor, and the
                // result looks confident rather than broken.
                Verdict.CAUTION,
                listOf("Citire greșită — $implausible. Verifică manual."),
                listOf(RULE_UNREADABLE)
            )

            else -> {
                val blockers = hardFilterFailures(offer, settings)
                if (blockers.isNotEmpty()) {
                    Triple(Verdict.REJECT, blockers.map { it.message }, blockers.map { it.rule })
                } else {
                    val (v, r) = decide(netRonPerHour, settings)
                    Triple(
                        v,
                        listOf(r),
                        if (v == Verdict.REJECT) listOf(RULE_HOURLY) else emptyList()
                    )
                }
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
            failedRules = failedRules,
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
    /** A broken rule: its stable name, and the sentence shown to the driver. */
    data class RuleFailure(val rule: String, val message: String)

    private fun hardFilterFailures(offer: RideOffer, settings: RideSettings): List<RuleFailure> {
        val failures = mutableListOf<RuleFailure>()
        val price = offer.price

        // Rule 1 — the floor under the fare itself.
        if (settings.minimumFareEnabled && settings.minimumFare > 0 &&
            price != null && price < settings.minimumFare
        ) {
            failures += RuleFailure(
                RULE_MIN_FARE,
                String.format(
                    RO,
                    "%.2f RON, sub minimul de %.0f RON pe cursă",
                    price,
                    settings.minimumFare
                )
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
                failures += RuleFailure(
                    RULE_COST_PER_KM,
                    String.format(
                        RO,
                        "Sub costul minim: %s RON necesari (%s km × %s RON/km)",
                        trim(required),
                        trim(tripKm),
                        trim(settings.minCostPerKm)
                    )
                )
            }
        }

        // Rule 3 — distance to the rider.
        val pickupKm = offer.pickupDistanceKm
        if (settings.maxPickupKmEnabled && settings.maxPickupKm > 0 &&
            pickupKm != null && pickupKm > settings.maxPickupKm
        ) {
            failures += RuleFailure(
                RULE_PICKUP,
                String.format(
                    RO,
                    "Preluare prea departe: %s km / maxim %s km",
                    trim(pickupKm),
                    trim(settings.maxPickupKm)
                )
            )
        }

        // Rule 4 — length of the paid ride.
        if (settings.maxTripKmEnabled && settings.maxTripKm > 0 &&
            tripKm != null && tripKm > settings.maxTripKm
        ) {
            failures += RuleFailure(
                RULE_TRIP_LENGTH,
                String.format(
                    RO,
                    "Cursa prea lungă: %s km / maxim %s km",
                    trim(tripKm),
                    trim(settings.maxTripKm)
                )
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
        // depending on whether the drive to the rider is counted, and on how
        // many rides the driver actually fits into an hour.
        val suffix = buildString {
            if (!settings.includePickup) append(" (fără pickup)")
            // The pace is what turns a single ride into an hour, so the figure
            // is meaningless without saying which pace produced it.
            append(String.format(RO, " la %.1f curse/oră", settings.ridesPerHour))
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
