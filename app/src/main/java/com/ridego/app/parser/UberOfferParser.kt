package com.ridego.app.parser

/**
 * Uber describes an offer as two legs — the approach to the rider and the
 * ride itself:
 *
 *     La 5 min. (2.9 km) distanță
 *     Bulevardul Theodor Pallady 51G, București
 *     Cursă: 12 min. (5.2 km)
 *     Str. Liviu Rebreanu 5, București
 *
 * Assignment is driven by the Romanian context words around each number, not
 * by position, because OCR reorders and fragments the card freely. Position
 * is used only as a last resort, when the card carries no labels at all.
 */
object UberOfferParser : PlatformParser {

    override val platform = Platform.UBER

    private val SERVICE_TYPES = listOf(
        "UberX", "Uber Go", "UberGo", "Uber Green", "Uber Black", "Uber XL",
        "Comfort", "Uber"
    )

    /**
     * Words that mark the leg between the driver and the rider.
     * Both languages, because the driver app follows the phone's locale and a
     * Romanian driver may well be running Uber in English.
     */
    private val PICKUP_WORDS = setOf(
        "la", "distanta", "distanță", "distanța", "pickup", "preluare",
        "ridicare", "client", "pasager",
        "away", "pick-up", "rider", "passenger"
    )

    /** Words that mark the paid ride itself. */
    private val TRIP_WORDS = setOf(
        "cursa", "cursă", "cursă:", "cursa:", "traseu", "trip", "ride",
        "destinatie", "destinație", "destinația",
        "dropoff", "drop-off", "destination"
    )

    private enum class LegKind { PICKUP, TRIP }

    private data class Marker(val tokenIndex: Int, val kind: LegKind)

    override fun parse(rawText: String): RideOffer? {
        val text = OfferText.normalize(rawText)
        val lines = text.lines().map { it.trim() }
        val tokens = OcrTokenizer.tokenize(text)
        val measurements = OcrTokenizer.measurements(tokens)
        val markers = collectMarkers(tokens)

        val assignment = if (markers.isEmpty() || measurements.isEmpty()) {
            assignByPosition(measurements)
        } else {
            assignByContext(measurements, markers)
        }

        val offer = RideOffer(
            platform = Platform.UBER,
            price = OfferText.parsePrice(text),
            pickupDistanceKm = assignment.pickupKm?.value,
            pickupTimeMinutes = assignment.pickupMin?.value?.toInt(),
            tripDistanceKm = assignment.tripKm?.value,
            tripTimeMinutes = assignment.tripMin?.value?.toInt(),
            pickupAddress = assignment.pickupKm?.let { OfferText.addressAfter(lines, it.lineIndex) },
            destinationAddress = assignment.tripKm?.let { OfferText.addressAfter(lines, it.lineIndex) },
            serviceType = SERVICE_TYPES.firstOrNull { text.contains(it, ignoreCase = true) },
            rating = OfferText.parseRating(text),
            paymentMethod = OfferText.parsePaymentMethod(text)
        )
        return if (offer.price == null && measurements.isEmpty()) null else offer
    }

    private class Assignment(
        val pickupKm: Measurement? = null,
        val pickupMin: Measurement? = null,
        val tripKm: Measurement? = null,
        val tripMin: Measurement? = null
    )

    private fun collectMarkers(tokens: List<OcrToken>): List<Marker> =
        tokens.mapNotNull { token ->
            val word = token.text.lowercase().trim('.', ',', ':', '•', '·', '(', ')')
            when {
                PICKUP_WORDS.contains(word) -> Marker(token.index, LegKind.PICKUP)
                TRIP_WORDS.contains(word) -> Marker(token.index, LegKind.TRIP)
                else -> null
            }
        }

    /**
     * Labels cut the card into segments, and a measurement belongs to the
     * segment it sits in.
     *
     * Nearest-label matching was tried first and is wrong: with the ride
     * listed before the approach, the ride's distance ends up closer to the
     * pickup label than to its own, and the two legs swap. A segment runs
     * from one label until the next label of the *other* kind, which holds
     * whichever order Uber renders the legs in.
     */
    private fun assignByContext(
        measurements: List<Measurement>,
        markers: List<Marker>
    ): Assignment {
        // Consecutive labels of the same kind ("La ... distanță") describe one
        // leg, so only a change of kind opens a new segment.
        val boundaries = mutableListOf<Marker>()
        markers.sortedBy { it.tokenIndex }.forEach { marker ->
            if (boundaries.lastOrNull()?.kind != marker.kind) boundaries += marker
        }

        val pickup = mutableListOf<Measurement>()
        val trip = mutableListOf<Measurement>()

        // Romanian Uber leads with the label ("Cursă: 12 min (5.2 km)");
        // English Uber trails it ("14 mins (8.2 km) trip"). One rule has to
        // cover the whole card, so the direction is decided once here.
        //
        // Comparing only the first label to the first measurement was not
        // enough: the Uber map's own "1–8 min" ETA badge is read before the
        // card begins, and a single stray number ahead of the first label
        // flipped the entire card into the wrong mode, swapping both legs.
        // Asking instead which side labels usually sit on lets the majority
        // of a real card outvote one piece of noise.
        val labelsTrail = trailingProximity(boundaries, measurements) <
            leadingProximity(boundaries, measurements)

        measurements.forEach { measurement ->
            val segment = if (labelsTrail) {
                boundaries.firstOrNull { it.tokenIndex >= measurement.tokenIndex }
                    ?: boundaries.last()
            } else {
                boundaries.lastOrNull { it.tokenIndex <= measurement.tokenIndex }
                    ?: boundaries.first()
            }

            when (segment.kind) {
                LegKind.PICKUP -> pickup += measurement
                LegKind.TRIP -> trip += measurement
            }
        }

        val (pickupKm, pickupMin) = pickLeg(pickup)
        val (tripKm, tripMin) = pickLeg(trip)
        return Assignment(
            pickupKm = pickupKm,
            pickupMin = pickupMin,
            tripKm = tripKm,
            tripMin = tripMin
        )
    }

    /**
     * Chooses the distance and the duration that describe one leg.
     *
     * Taking the first minute value in the segment is what produced the
     * 510 km/h read from the field: the Uber map carries its own "1–8 min"
     * ETA badges, and OCR sweeps them up into whichever segment they land in.
     * A segment can therefore hold several durations, only one of which
     * belongs to the leg.
     *
     * Uber always renders the pair together — "Cursă: 19 min. (8,5 km)" — so
     * the duration nearest its own distance is the right one. Speed breaks
     * ties first: a candidate that makes the leg physically possible beats a
     * closer one that does not.
     */
    private fun pickLeg(segment: List<Measurement>): Pair<Measurement?, Measurement?> {
        val km = segment.firstOrNull { it.unit == MeasureUnit.KM }
        val minutes = segment.filter { it.unit == MeasureUnit.MIN }
        if (km == null) return null to minutes.firstOrNull()

        val chosen = minutes.minWithOrNull(
            compareBy(
                { if (plausibleSpeed(km.value, it.value)) 0 else 1 },
                { Math.abs(it.tokenIndex - km.tokenIndex) }
            )
        )
        return km to chosen
    }

    /** The same band the calculator refuses to publish a verdict outside of. */
    private fun plausibleSpeed(km: Double, minutes: Double): Boolean {
        if (minutes <= 0.0) return false
        val kmh = km / (minutes / 60.0)
        return kmh >= RideOffer.MIN_PLAUSIBLE_KMH && kmh <= RideOffer.MAX_PLAUSIBLE_KMH
    }

    /** How closely measurements follow their labels, summed over the card. */
    private fun leadingProximity(
        boundaries: List<Marker>,
        measurements: List<Measurement>
    ): Int = boundaries.sumOf { marker ->
        measurements.filter { it.tokenIndex > marker.tokenIndex }
            .minOfOrNull { it.tokenIndex - marker.tokenIndex } ?: NO_MATCH
    }

    /** How closely measurements precede their labels, summed over the card. */
    private fun trailingProximity(
        boundaries: List<Marker>,
        measurements: List<Measurement>
    ): Int = boundaries.sumOf { marker ->
        measurements.filter { it.tokenIndex < marker.tokenIndex }
            .minOfOrNull { marker.tokenIndex - it.tokenIndex } ?: NO_MATCH
    }

    /** Stands in for "no measurement on this side at all" when scoring. */
    private const val NO_MATCH = 1_000

    /**
     * Unlabelled card: fall back to reading order, where Uber shows the
     * approach before the ride. Distinct occurrences only — the same number
     * is never used for both legs.
     */
    private fun assignByPosition(measurements: List<Measurement>): Assignment {
        val km = measurements.filter { it.unit == MeasureUnit.KM }
        val minutes = measurements.filter { it.unit == MeasureUnit.MIN }
        return Assignment(
            pickupKm = km.getOrNull(0)?.takeIf { km.size >= 2 },
            pickupMin = minutes.getOrNull(0)?.takeIf { minutes.size >= 2 },
            tripKm = if (km.size >= 2) km.getOrNull(1) else km.getOrNull(0),
            tripMin = if (minutes.size >= 2) minutes.getOrNull(1) else minutes.getOrNull(0)
        )
    }
}
