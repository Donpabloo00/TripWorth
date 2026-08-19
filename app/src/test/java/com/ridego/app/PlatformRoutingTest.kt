package com.ridego.app

import com.ridego.app.calculator.OfferCalculator
import com.ridego.app.calculator.RideSettings
import com.ridego.app.calculator.Verdict
import com.ridego.app.parser.OfferParserRouter
import com.ridego.app.parser.Platform
import com.ridego.app.parser.PlatformDetector
import com.ridego.app.parser.PlatformMode
import com.ridego.app.parser.RideOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformRoutingTest {

    private val uberText = """
        Uber
        17,20 RON
        La 5 min. (2.9 km) distanță
        Bulevardul Theodor Pallady 51G, București
        Cursă: 12 min. (5.2 km)
        Str. Liviu Rebreanu 5, București
    """.trimIndent()

    private val boltText = """
        Bolt
        25,50 RON
        3,2 km
        6 min
        Strada Exemplu 10, București
        8,4 km
        18 min
        Bulevardul Exemplu 20, București
    """.trimIndent()

    // --- detection ------------------------------------------------------

    @Test
    fun `detects platform from branding text`() {
        assertEquals(Platform.UBER, PlatformDetector.detect(uberText))
        assertEquals(Platform.BOLT, PlatformDetector.detect(boltText))
    }

    @Test
    fun `package name wins over text`() {
        assertEquals(
            Platform.BOLT,
            PlatformDetector.detect(uberText, PlatformDetector.BOLT_DRIVER_PACKAGE)
        )
        assertEquals(
            Platform.UBER,
            PlatformDetector.detect(boltText, PlatformDetector.UBER_DRIVER_PACKAGE)
        )
    }

    @Test
    fun `unbranded text is unknown`() {
        assertEquals(Platform.UNKNOWN, PlatformDetector.detect("17,20 RON\n5 min (2.9 km)"))
    }

    @Test
    fun `food delivery cards are not ride offers`() {
        val food = "Bolt Food\n25,50 RON\n3,2 km\n6 min\nRestaurant Exemplu"
        assertEquals(Platform.UNKNOWN, PlatformDetector.detect(food))
        assertFalse(OfferParserRouter.looksLikeOffer(food))
    }

    // --- routing --------------------------------------------------------

    @Test
    fun `routes each platform to its own parser`() {
        assertEquals("UberOfferParser", OfferParserRouter.route(uberText).parserName)
        assertEquals("BoltOfferParser", OfferParserRouter.route(boltText).parserName)
    }

    @Test
    fun `unbranded text picks the parser that reads more of the card`() {
        // Uber's labelled layout with the branding cropped out of the capture.
        val result = OfferParserRouter.route(uberText.removePrefix("Uber\n"))
        assertEquals(Platform.UBER, result.platform)
        assertEquals(2.9, result.offer!!.pickupDistanceKm!!, 0.001)
        assertEquals(5.2, result.offer!!.tripDistanceKm!!, 0.001)
    }

    @Test
    fun `pinned mode ignores the other platform`() {
        val result = OfferParserRouter.route(boltText, mode = PlatformMode.UBER_ONLY)
        assertNull(result.offer)
        assertTrue(result.parserName.contains("ignorat"))
    }

    @Test
    fun `pinned mode still reads unbranded cards`() {
        // A missed logo must not silently drop a real offer.
        val result = OfferParserRouter.route(
            uberText.removePrefix("Uber\n"),
            mode = PlatformMode.UBER_ONLY
        )
        assertNotNull(result.offer)
        assertEquals(Platform.UBER, result.platform)
    }

    @Test
    fun `platform is part of the duplicate signature`() {
        val uber = OfferParserRouter.parse(uberText)!!
        val sameNumbersOnBolt = uber.copy(platform = Platform.BOLT)
        assertTrue(uber.signature != sameNumbersOnBolt.signature)
    }

    // --- confidence -----------------------------------------------------

    @Test
    fun `a fully read card scores 100`() {
        assertEquals(100, OfferParserRouter.route(uberText).confidence)
        assertEquals(100, OfferParserRouter.route(boltText).confidence)
    }

    @Test
    fun `a partly read card is not usable`() {
        val result = OfferParserRouter.route("Bolt\n25,50 RON\n7,5 km\n15 min")
        assertFalse(result.isUsable)
    }

    @Test
    fun `an unreliable offer never reaches a verdict`() {
        val offer = OfferParserRouter.parse("Bolt\n25,50 RON\n7,5 km\n15 min")!!
        val analysis = OfferCalculator.analyze(offer, RideSettings())
        assertEquals(Verdict.CAUTION, analysis.verdict)
        assertTrue(analysis.reason.contains("incomplete"))
    }
}
