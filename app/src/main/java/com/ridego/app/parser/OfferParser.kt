package com.ridego.app.parser

/**
 * Thin facade kept so callers (and the original Uber test suite) can parse
 * without knowing about platforms. New code should prefer
 * [OfferParserRouter.route], which also reports which parser ran and how
 * much of the card it read.
 */
object OfferParser {

    fun looksLikeOffer(rawText: String): Boolean = OfferParserRouter.looksLikeOffer(rawText)

    fun parse(rawText: String): RideOffer? = OfferParserRouter.parse(rawText)
}
