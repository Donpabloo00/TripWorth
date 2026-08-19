package com.ridego.app.parser

data class RideOffer(
    val platform: Platform,
    val price: Double?,
    val pickupDistanceKm: Double?,
    val pickupTimeMinutes: Int?,
    val tripDistanceKm: Double?,
    val tripTimeMinutes: Int?,
    val pickupAddress: String?,
    val destinationAddress: String?,
    val serviceType: String?,
    val rating: Double?,
    val paymentMethod: String?
) {
    /**
     * Totals are null unless BOTH legs are known.
     *
     * Treating a missing leg as zero is the specific bug this guards: it
     * shrinks totalDistanceKm and inflates RON/km, turning an unreadable card
     * into a confident, wrong recommendation.
     */
    val totalDistanceKm: Double?
        get() = if (pickupDistanceKm != null && tripDistanceKm != null) {
            pickupDistanceKm + tripDistanceKm
        } else {
            null
        }

    val totalTimeMinutes: Int?
        get() = if (pickupTimeMinutes != null && tripTimeMinutes != null) {
            pickupTimeMinutes + tripTimeMinutes
        } else {
            null
        }

    /** An offer is usable for a verdict only when price and both legs are known. */
    val isComplete: Boolean
        get() = price != null && price > 0 &&
            isNonNegative(pickupDistanceKm) && isNonNegative(pickupTimeMinutes?.toDouble()) &&
            isNonNegative(tripDistanceKm) && isNonNegative(tripTimeMinutes?.toDouble())

    private fun isNonNegative(value: Double?): Boolean = value != null && value >= 0

    /**
     * How much of the card was actually read, 0-100. Below
     * [MIN_CONFIDENCE] RideGo reports incomplete data instead of a verdict —
     * a confident wrong number is worse than an honest gap.
     */
    val confidence: Int
        get() = listOf(
            30 to (price != null),
            20 to (pickupDistanceKm != null),
            20 to (tripDistanceKm != null),
            10 to (pickupTimeMinutes != null),
            10 to (tripTimeMinutes != null),
            10 to (platform != Platform.UNKNOWN)
        ).sumOf { (points, present) -> if (present) points else 0 }

    /**
     * Stable key used to suppress duplicate detections. Platform is part of
     * it so an identical fare on Uber and Bolt is still two offers.
     */
    /**
     * Whether a verdict may be shown for this offer.
     *
     * Confidence alone is not enough: the spec's weights give a card missing
     * the whole pickup leg 70%, but totalKm would then cover only the ride,
     * inflating RON/km. Both legs have to be present before any number is put
     * in front of the driver.
     */
    val isReliable: Boolean
        get() = isReliableFor(includePickup = true)

    /**
     * With the pickup leg excluded from the maths, a card that only yielded
     * the paid ride is still enough to judge — so reliability has to follow
     * the same rule the calculator uses.
     */
    fun isReliableFor(includePickup: Boolean): Boolean {
        if (confidence < MIN_CONFIDENCE) return false
        if (price == null || price <= 0) return false
        val tripKnown = tripDistanceKm != null && tripDistanceKm >= 0 &&
            tripTimeMinutes != null && tripTimeMinutes >= 0
        if (!includePickup) return tripKnown
        val pickupKnown = pickupDistanceKm != null && pickupDistanceKm >= 0 &&
            pickupTimeMinutes != null && pickupTimeMinutes >= 0
        return tripKnown && pickupKnown
    }

    /**
     * Which leg, if any, claims a speed no car achieves — or none, when both
     * are believable.
     *
     * A single mis-read digit is enough to poison every ratio downstream:
     * 8.5 km in "1 min" is 510 km/h, and it turned a 20 RON fare into a
     * confident 1223 RON/oră. OCR mis-reads are normal, so the numbers built
     * on top of them have to be checked before they are shown, not after a
     * driver has acted on one.
     *
     * The approach leg is only judged when it feeds the totals; its distance
     * is used by the pickup rule either way, and a distance alone makes no
     * claim about speed.
     */
    fun implausibleLeg(includePickup: Boolean): String? {
        legSpeedProblem(tripDistanceKm, tripTimeMinutes, "cursa")?.let { return it }
        if (includePickup) {
            legSpeedProblem(pickupDistanceKm, pickupTimeMinutes, "preluarea")?.let { return it }
        }
        return null
    }

    private fun legSpeedProblem(km: Double?, minutes: Int?, label: String): String? {
        // A missing leg is the reliability check's business, not this one.
        if (km == null || minutes == null) return null
        if (km <= 0.0) return null
        val text = String.format(java.util.Locale("ro", "RO"), "%.1f km în %d min", km, minutes)
        if (minutes <= 0) return "$label: $text"
        val kmh = km / (minutes / 60.0)
        return if (kmh < MIN_PLAUSIBLE_KMH || kmh > MAX_PLAUSIBLE_KMH) {
            String.format(java.util.Locale("ro", "RO"), "%s: %s (%.0f km/h)", label, text, kmh)
        } else {
            null
        }
    }

    /**
     * Identity of the offer card, stable across repeated reads of it.
     *
     * The price is quantised to half a leu on purpose. The same card read a
     * second later yielded 25.11 and then 25.10 — OCR mistook the final digit
     * — and the exact price made those two different signatures, so one offer
     * was analysed and counted twice.
     *
     * Two genuinely distinct offers sharing a platform, both legs, both
     * durations and a fare within 50 bani do not occur in practice; counting
     * every offer twice does.
     */
    val signature: String
        get() = listOf(
            platform.name,
            price?.let { String.format("%.1f", Math.round(it * 2) / 2.0) },
            pickupDistanceKm?.let { String.format("%.1f", it) },
            pickupTimeMinutes?.toString(),
            tripDistanceKm?.let { String.format("%.1f", it) },
            tripTimeMinutes?.toString()
        ).joinToString("|")

    companion object {
        const val MIN_CONFIDENCE = 60

        /**
         * The band a real journey falls in, deliberately wide.
         *
         * The point is to catch a mis-read digit, not to police driving: a
         * motorway leg to the airport is allowed, and so is crawling through
         * a jam. Anything outside this is arithmetic, not traffic.
         */
        const val MIN_PLAUSIBLE_KMH = 2.0
        const val MAX_PLAUSIBLE_KMH = 120.0
    }
}
