package com.ridego.app.parser

/**
 * Bolt's offer card is less predictable than Uber's: distance and duration
 * are often on separate lines, addresses sit under explicit labels, and OCR
 * frequently splits a single visual row into several text lines.
 *
 * So this parser does not lean on line-level patterns. It reads the whole
 * card as an ordered stream of km and min values and pairs them positionally
 * — the first pair is the approach to the rider, the second is the ride.
 *
 * WARNING: tuned against the generic layout in the spec, not against real
 * Bolt Driver captures. Treat field extraction as provisional until it has
 * been checked against genuine screenshots.
 */
object BoltOfferParser : PlatformParser {

    override val platform = Platform.BOLT

    private val CATEGORIES = listOf(
        "Bolt Basic", "Bolt XL", "Bolt Green", "Bolt Comfort", "Bolt Pet",
        "Bolt Assist", "Basic", "Comfort", "XL", "Green"
    )

    private val PICKUP_LABELS = listOf("pickup", "ridicare", "preluare", "plecare", "de la")
    private val DESTINATION_LABELS = listOf("destinaț", "destinat", "sosire", "spre", "către", "catre")

    private enum class Unit { KM, MIN }

    private class Token(val unit: Unit, val km: Double?, val minutes: Int?, val lineIndex: Int)

    override fun parse(rawText: String): RideOffer? {
        val text = OfferText.normalize(rawText)
        val lines = text.lines().map { it.trim() }

        val tokens = collectTokens(lines)
        val kmTokens = tokens.filter { it.unit == Unit.KM }
        val minTokens = tokens.filter { it.unit == Unit.MIN }

        // Two of each means both legs are present. With only one of each the
        // card is partial; attribute it to the ride and let the confidence
        // score report the gap rather than inventing a pickup leg.
        val pickupKm = kmTokens.getOrNull(0)?.takeIf { kmTokens.size >= 2 }
        val tripKm = if (kmTokens.size >= 2) kmTokens.getOrNull(1) else kmTokens.getOrNull(0)
        val pickupMin = minTokens.getOrNull(0)?.takeIf { minTokens.size >= 2 }
        val tripMin = if (minTokens.size >= 2) minTokens.getOrNull(1) else minTokens.getOrNull(0)

        val offer = RideOffer(
            platform = Platform.BOLT,
            price = OfferText.parsePrice(text),
            pickupDistanceKm = pickupKm?.km,
            pickupTimeMinutes = pickupMin?.minutes,
            tripDistanceKm = tripKm?.km,
            tripTimeMinutes = tripMin?.minutes,
            pickupAddress = addressFor(lines, PICKUP_LABELS)
                ?: pickupKm?.let { OfferText.addressAfter(lines, it.lineIndex) },
            destinationAddress = addressFor(lines, DESTINATION_LABELS)
                ?: tripKm?.let { OfferText.addressAfter(lines, it.lineIndex) },
            serviceType = CATEGORIES.firstOrNull { text.contains(it, ignoreCase = true) },
            rating = OfferText.parseRating(text),
            paymentMethod = OfferText.parsePaymentMethod(text)
        )
        return if (offer.price == null && kmTokens.isEmpty() && minTokens.isEmpty()) null else offer
    }

    /**
     * Reads km and min values in the order they appear on screen, whether
     * they share a line or are split across several.
     */
    private fun collectTokens(lines: List<String>): List<Token> {
        val tokens = mutableListOf<Token>()
        lines.forEachIndexed { index, line ->
            val perLine = mutableListOf<Pair<Int, Token>>()

            OfferText.BARE_KM.findAll(line).forEach { match ->
                val km = OfferText.toDouble(match.groupValues[1]) ?: return@forEach
                if (!OfferText.plausibleKm(km)) return@forEach
                perLine += match.range.first to Token(Unit.KM, km, null, index)
            }
            OfferText.BARE_MIN.findAll(line).forEach { match ->
                val minutes = match.groupValues[1].toIntOrNull() ?: return@forEach
                if (!OfferText.plausibleMinutes(minutes)) return@forEach
                perLine += match.range.first to Token(Unit.MIN, null, minutes, index)
            }

            // Preserve left-to-right order within the line as well as top-down
            // order between lines.
            perLine.sortBy { it.first }
            tokens += perLine.map { it.second }
        }
        return tokens
    }

    private fun addressFor(lines: List<String>, labels: List<String>): String? {
        lines.forEachIndexed { index, line ->
            val lower = line.lowercase()
            if (labels.none { lower.contains(it) }) return@forEachIndexed

            // "Pickup: Strada Exemplu 10" — the address may follow the label
            // on the same line.
            val inline = line.substringAfter(':', "").trim()
            if (inline.length >= 4 && inline.count { it.isLetter() } >= inline.length / 2) {
                return inline
            }
            OfferText.addressAfter(lines, index)?.let { return it }
        }
        return null
    }
}
