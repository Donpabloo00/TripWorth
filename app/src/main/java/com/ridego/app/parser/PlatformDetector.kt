package com.ridego.app.parser

/**
 * Works out which driver app produced a screen.
 *
 * The package name is authoritative when available, but RideGo does not hold
 * usage-stats permission, so in practice detection almost always falls back
 * to branding words in the OCR text.
 */
object PlatformDetector {

    const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"

    /**
     * Bolt's driver app ships as `ee.mtakso.driver` (Bolt grew out of Taxify,
     * an Estonian company). The `com.taxsee.taxsee` id belongs to a different
     * operator, so it is not matched here.
     */
    const val BOLT_DRIVER_PACKAGE = "ee.mtakso.driver"

    private val UBER_HINTS = listOf(
        "uberx", "uberxl", "uber black", "uber green", "uber go", "uber"
    )
    private val BOLT_HINTS = listOf("bolt")

    /** Bolt Food is a delivery product; its cards are not ride offers. */
    private val EXCLUSIONS = listOf("bolt food", "uber eats", "livrare", "restaurant")

    fun detect(rawText: String, foregroundPackage: String? = null): Platform {
        when (foregroundPackage) {
            UBER_DRIVER_PACKAGE -> return Platform.UBER
            BOLT_DRIVER_PACKAGE -> return Platform.BOLT
        }

        val lower = OfferText.normalize(rawText).lowercase()
        if (EXCLUSIONS.any { lower.contains(it) }) return Platform.UNKNOWN

        val uberScore = UBER_HINTS.count { lower.contains(it) }
        val boltScore = BOLT_HINTS.count { lower.contains(it) }

        return when {
            uberScore > boltScore -> Platform.UBER
            boltScore > uberScore -> Platform.BOLT
            else -> Platform.UNKNOWN
        }
    }

    /** True when the screen is a food-delivery card rather than a ride. */
    fun isExcluded(rawText: String): Boolean {
        val lower = OfferText.normalize(rawText).lowercase()
        return EXCLUSIONS.any { lower.contains(it) }
    }
}
