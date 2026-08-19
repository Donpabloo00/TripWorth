package com.ridego.app.parser

/** What the parsing pipeline saw, kept for Debug Mode. */
data class ParseResult(
    val platform: Platform,
    val parserName: String,
    val offer: RideOffer?,
    /** Exactly what ML Kit returned — never normalized, so diagnostics show reality. */
    val rawText: String
) {
    val confidence: Int get() = offer?.confidence ?: 0
    val isUsable: Boolean get() = offer?.isReliable == true
}

/**
 * Detects the platform, then hands the text to that platform's parser.
 *
 * This is the only place that knows platforms exist; everything downstream
 * works on the common [RideOffer].
 */
object OfferParserRouter {

    private val parsers: Map<Platform, PlatformParser> = mapOf(
        Platform.UBER to UberOfferParser,
        Platform.BOLT to BoltOfferParser
    )

    private val DETECTION_HINTS = listOf(
        "ron", "lei", "km", "min", "cursă", "cursa", " la ", "uber", "bolt"
    )

    /**
     * Cheap gate run before parsing so arbitrary screen text is not mistaken
     * for an offer: a currency amount plus a distance or duration, on top of
     * a general keyword count.
     */
    fun looksLikeOffer(rawText: String): Boolean {
        val text = OfferText.normalize(rawText)
        if (PlatformDetector.isExcluded(text)) return false

        val lower = text.lowercase()
        val hits = DETECTION_HINTS.count { lower.contains(it) }
        val hasMoney = OfferText.PRICE.containsMatchIn(text) ||
            OfferText.PRICE_PREFIXED.containsMatchIn(text)
        val hasDistance = OfferText.BARE_KM.containsMatchIn(text)
        val hasDuration = OfferText.BARE_MIN.containsMatchIn(text)
        return hits >= 3 && hasMoney && hasDistance && hasDuration
    }

    /**
     * Reports which of the gate's conditions passed, so a rejected screen can
     * be told apart from a screen that was never an offer.
     */
    fun gateReport(rawText: String): String {
        val text = OfferText.normalize(rawText)
        val lower = text.lowercase()
        val matched = DETECTION_HINTS.filter { lower.contains(it) }
        val hasMoney = OfferText.PRICE.containsMatchIn(text) ||
            OfferText.PRICE_PREFIXED.containsMatchIn(text)
        val hasDistance = OfferText.BARE_KM.containsMatchIn(text)
        val hasDuration = OfferText.BARE_MIN.containsMatchIn(text)

        return "cuvinte=${matched.size}/3 [${matched.joinToString(",")}] " +
            "sumă=$hasMoney km=$hasDistance min=$hasDuration " +
            "exclus=${PlatformDetector.isExcluded(text)}"
    }

    fun route(
        rawText: String,
        foregroundPackage: String? = null,
        mode: PlatformMode = PlatformMode.AUTO
    ): ParseResult {
        val text = OfferText.normalize(rawText)
        val detected = PlatformDetector.detect(text, foregroundPackage)

        // Only a positively identified foreign platform is filtered out. An
        // unbranded card while pinned to one platform is assumed to be that
        // platform, otherwise a missed logo would silently drop real offers.
        if (detected != Platform.UNKNOWN && !mode.accepts(detected)) {
            return ParseResult(detected, "ignorat (mod ${mode.label})", null, rawText)
        }
        if (!looksLikeOffer(text)) {
            return ParseResult(detected, "niciun parser", null, rawText)
        }

        val candidates = when {
            parsers.containsKey(detected) -> listOf(parsers.getValue(detected))
            mode == PlatformMode.UBER_ONLY -> listOf(UberOfferParser)
            mode == PlatformMode.BOLT_ONLY -> listOf(BoltOfferParser)
            // Branding was unreadable: run both and keep whichever read more
            // of the card. Uber wins ties because its layout is the more
            // specific of the two, so a full match there is less accidental.
            else -> listOf(UberOfferParser, BoltOfferParser)
        }

        val best = candidates
            .map { it to it.parse(text) }
            .maxByOrNull { (_, offer) -> offer?.confidence ?: -1 }
            ?: return ParseResult(detected, "niciun parser", null, rawText)

        return ParseResult(
            platform = best.second?.platform ?: detected,
            parserName = best.first::class.java.simpleName,
            offer = best.second,
            rawText = rawText
        )
    }

    fun parse(
        rawText: String,
        foregroundPackage: String? = null,
        mode: PlatformMode = PlatformMode.AUTO
    ): RideOffer? = route(rawText, foregroundPackage, mode).offer
}
