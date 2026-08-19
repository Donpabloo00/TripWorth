package com.ridego.app.parser

/**
 * One whitespace-separated piece of OCR output, with the line it came from.
 *
 * Working in tokens rather than lines is what makes fragmented OCR readable:
 * "La 5 min. (2.9 km) distanță" and the same words split across six lines
 * produce an identical token stream, so the parser cannot tell them apart —
 * which is exactly the point.
 */
data class OcrToken(
    val text: String,
    val lineIndex: Int,
    val index: Int
) {
    private val letters: String = text.filter { it.isLetter() }.lowercase()
    private val digits: String = text.filter { it.isDigit() || it == '.' || it == ',' }

    /** The numeric value, ignoring brackets and trailing punctuation. */
    val number: Double?
        get() = if (digits.matches(NUMBER)) OfferText.toDouble(digits) else null

    /** The unit this token names on its own, e.g. "km" in "(2.9" + "km)". */
    val unit: MeasureUnit?
        get() = when {
            letters == "km" -> MeasureUnit.KM
            letters == "min" || letters == "mins" || letters == "minute" ||
                letters == "minut" || letters == "min." -> MeasureUnit.MIN
            else -> null
        }

    /** A glued "5min" / "2.9km" token, which OCR produces when spacing is lost. */
    val combined: Pair<Double, MeasureUnit>?
        get() {
            val m = COMBINED.find(text.lowercase()) ?: return null
            val value = OfferText.toDouble(m.groupValues[1]) ?: return null
            val unit = if (m.groupValues[2].startsWith("km")) MeasureUnit.KM else MeasureUnit.MIN
            return value to unit
        }

    private companion object {
        val NUMBER = Regex("""\d{1,4}(?:[.,]\d{1,2})?""")
        val COMBINED = Regex("""^\D*?(\d{1,4}(?:[.,]\d{1,2})?)\s*(km|min)\.?\D*$""")
    }
}

enum class MeasureUnit { KM, MIN }

/** A number that was successfully paired with its unit. */
data class Measurement(
    val value: Double,
    val unit: MeasureUnit,
    val tokenIndex: Int,
    val lineIndex: Int
)

internal object OcrTokenizer {

    fun tokenize(text: String): List<OcrToken> {
        val tokens = mutableListOf<OcrToken>()
        var index = 0
        text.lines().forEachIndexed { lineIndex, line ->
            line.split(' ', '\t').forEach { raw ->
                val piece = raw.trim()
                if (piece.isNotEmpty()) {
                    tokens += OcrToken(piece, lineIndex, index)
                    index++
                }
            }
        }
        return tokens
    }

    /**
     * Pairs each number with the unit that follows it. The unit may be glued
     * on, the next token, or one token further along — OCR routinely inserts
     * a stray bracket or period between the two.
     */
    fun measurements(tokens: List<OcrToken>): List<Measurement> {
        val found = mutableListOf<Measurement>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]

            val glued = token.combined
            if (glued != null) {
                if (plausible(glued.first, glued.second)) {
                    found += Measurement(glued.first, glued.second, i, token.lineIndex)
                }
                i++
                continue
            }

            val value = token.number
            if (value == null) {
                i++
                continue
            }

            var consumed = 1
            var unit: MeasureUnit? = null
            for (offset in 1..2) {
                val next = tokens.getOrNull(i + offset) ?: break
                if (next.number != null) break // the next number starts its own pair
                val candidate = next.unit
                if (candidate != null) {
                    unit = candidate
                    consumed = offset + 1
                    break
                }
            }

            if (unit != null && plausible(value, unit)) {
                found += Measurement(value, unit, i, token.lineIndex)
            }
            i += consumed
        }
        return found
    }

    private fun plausible(value: Double, unit: MeasureUnit): Boolean = when (unit) {
        MeasureUnit.KM -> OfferText.plausibleKm(value)
        MeasureUnit.MIN -> value >= 0 && value <= 600 && value == Math.floor(value)
    }
}
