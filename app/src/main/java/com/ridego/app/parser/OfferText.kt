package com.ridego.app.parser

/**
 * Text primitives shared by every platform parser.
 *
 * Prices, distances and durations look the same whoever renders them; only
 * the surrounding layout differs. Keeping these here means a new platform
 * parser starts from working number extraction instead of copying regexes.
 */
internal object OfferText {

    const val NUM = """\d{1,4}(?:[.,]\d{1,2})?"""

    /**
     * A fare, allowing the letters OCR substitutes for digits.
     *
     * Observed on a real card: "RON25.11" came back as "RON25.1l" — a
     * lowercase L for the final 1. The old pattern stopped at "25.1", which
     * is wrong by a ban and, worse, made the same offer produce two different
     * signatures on consecutive reads, so it was counted twice.
     *
     * Only l/I/O are admitted, and only where a digit is already expected;
     * [confusedDigits] then maps them back before parsing.
     */
    private const val PRICE_NUM = """[\dlIO]{1,4}(?:[.,][\dlIO]{1,2})?"""

    val PRICE = Regex("""($PRICE_NUM)\s*(?:RON|LEI)\b""", RegexOption.IGNORE_CASE)
    val PRICE_PREFIXED = Regex("""(?:RON|LEI)\s*($PRICE_NUM)""", RegexOption.IGNORE_CASE)

    /** "5 min. (2.9 km)" */
    val LEG_MIN_KM = Regex(
        """(\d{1,3})\s*min\.?[^()\n]{0,24}\(\s*($NUM)\s*km\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /** "2.9 km (5 min)" */
    val LEG_KM_MIN = Regex(
        """($NUM)\s*km[^()\n]{0,24}\(\s*(\d{1,3})\s*min\.?\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /** "2,9 km • 5 min" */
    val LEG_KM_SEP_MIN = Regex(
        """($NUM)\s*km\s*[•·\-–|,]\s*(\d{1,3})\s*min\.?""",
        RegexOption.IGNORE_CASE
    )

    val BARE_KM = Regex("""($NUM)\s*km\b""", RegexOption.IGNORE_CASE)
    /** "16 min", "16 mins", "16 minute" — the plural broke the old \b anchor. */
    val BARE_MIN = Regex(
        """(\d{1,3})\s*min(?:s|ute|utes|\.)?\b""",
        RegexOption.IGNORE_CASE
    )

    val RATING = Regex("""[★*]\s*($NUM)|($NUM)\s*[★*]""")

    fun normalize(text: String): String = text
        .replace(' ', ' ')
        .replace(' ', ' ')
        .replace(' ', ' ')
        .replace(Regex("""\r\n?"""), "\n")
        .replace(Regex("""[ \t]+"""), " ")

    /** Maps the letters OCR mistakes for digits back to the digits. */
    private fun confusedDigits(raw: String): String = raw
        .replace('l', '1').replace('L', '1')
        .replace('I', '1').replace('i', '1')
        .replace('O', '0').replace('o', '0')

    /** Accepts both "2.9" and "2,9" — OCR and locale both vary. */
    fun toDouble(raw: String): Double? = raw.replace(',', '.').toDoubleOrNull()

    /** Fares outside this band are OCR noise, not offers. */
    fun plausiblePrice(value: Double): Boolean = value in 1.0..2000.0

    fun plausibleKm(value: Double): Boolean = value in 0.0..500.0

    fun plausibleMinutes(value: Int): Boolean = value in 0..600

    fun parsePrice(text: String): Double? {
        val raw = PRICE.findAll(text).map { it.groupValues[1] } +
            PRICE_PREFIXED.findAll(text).map { it.groupValues[1] }

        for (original in raw) {
            // At least one genuine digit: "RON lO" is noise, not 10.
            if (original.none { it.isDigit() }) continue
            val candidate = confusedDigits(original)
            val value = toDouble(candidate) ?: continue
            if (plausiblePrice(value)) return value

            // Uber prints the headline fare as "RON2768" — currency glued to
            // the amount, decimal separator lost by OCR. A four-digit run with
            // no separator is cents; a genuine three-digit fare (RON350) has
            // to survive untouched, hence the 1000 floor.
            val hasSeparator = candidate.contains('.') || candidate.contains(',')
            if (!hasSeparator && value >= 1000) {
                val asCents = value / 100.0
                if (plausiblePrice(asCents)) return asCents
            }
        }
        return null
    }

    fun parseRating(text: String): Double? {
        val m = RATING.find(text) ?: return null
        val value = toDouble(m.groupValues[1].ifEmpty { m.groupValues[2] }) ?: return null
        return value.takeIf { it in 1.0..5.0 }
    }

    fun parsePaymentMethod(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("numerar") || lower.contains("cash") -> "Numerar"
            lower.contains("card") -> "Card"
            else -> null
        }
    }

    /**
     * Addresses sit on the line under their leg or label. Skip blanks and
     * lines that are themselves measurements.
     */
    fun addressAfter(lines: List<String>, fromIndex: Int, lookahead: Int = 2): String? {
        for (i in (fromIndex + 1)..minOf(fromIndex + lookahead, lines.lastIndex)) {
            val candidate = lines[i].trim()
            if (candidate.length < 4) continue
            val lower = candidate.lowercase()
            if (lower.contains(" km") || lower.contains("min")) continue
            if (PRICE.containsMatchIn(candidate)) continue
            if (candidate.count { it.isLetter() } < candidate.length / 2) continue
            return candidate
        }
        return null
    }
}
