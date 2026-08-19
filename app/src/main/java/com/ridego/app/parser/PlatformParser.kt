package com.ridego.app.parser

/**
 * One parser per platform, all producing the same [RideOffer].
 *
 * Adding FreeNow or another operator means writing one of these and
 * registering it in [OfferParserRouter] — the calculator, verdict and overlay
 * stay untouched.
 */
interface PlatformParser {
    val platform: Platform
    fun parse(rawText: String): RideOffer?
}
