package com.ridego.app

import com.ridego.app.calculator.OfferCalculator
import com.ridego.app.calculator.RideSettings
import com.ridego.app.calculator.Verdict
import com.ridego.app.parser.OfferParserRouter
import com.ridego.app.parser.RideOffer
import com.ridego.app.parser.UberOfferParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression suite for the pickup/ride mix-up.
 *
 * Three defects are pinned here, each one observed in the parser before this
 * suite existed:
 *   1. fragmented OCR produced no legs at all;
 *   2. a missing leg was silently treated as zero in the totals;
 *   3. the ride listed before the approach swapped the two legs.
 */
class PickupTripSeparationTest {

    private val settings = RideSettings()

    private fun assertSpecExample(offer: RideOffer?) {
        requireNotNull(offer)
        assertEquals(2.9, offer.pickupDistanceKm!!, 0.001)
        assertEquals(5, offer.pickupTimeMinutes)
        assertEquals(5.2, offer.tripDistanceKm!!, 0.001)
        assertEquals(12, offer.tripTimeMinutes)
        assertEquals(8.1, offer.totalDistanceKm!!, 0.001)
        assertEquals(17, offer.totalTimeMinutes)
    }

    // --- A: the canonical layout ----------------------------------------

    @Test
    fun `A parses the labelled layout`() {
        assertSpecExample(
            UberOfferParser.parse(
                """
                17,20 RON
                La 5 min. (2.9 km) distanță
                Cursă: 12 min. (5.2 km)
                """.trimIndent()
            )
        )
    }

    // --- B: fragmented OCR ----------------------------------------------

    @Test
    fun `B parses fragmented ocr to the same values`() {
        // Every word on its own line, which is what ML Kit produces on a
        // narrow card. The old line-based regexes matched nothing here.
        assertSpecExample(
            UberOfferParser.parse(
                """
                La
                5
                min.
                (2.9 km)
                distanță

                Cursă:
                12
                min.
                (5.2 km)
                """.trimIndent()
            )
        )
    }

    @Test
    fun `B2 parses numbers split from their units`() {
        assertSpecExample(
            UberOfferParser.parse(
                """
                17,20 RON
                La
                5
                min
                2.9
                km
                distanță
                Cursă
                12
                min
                5.2
                km
                """.trimIndent()
            )
        )
    }

    @Test
    fun `B3 parses units glued to numbers`() {
        assertSpecExample(
            UberOfferParser.parse("17,20 RON La 5min 2.9km distanță Cursă: 12min 5.2km")
        )
    }

    // --- C: reordered OCR -----------------------------------------------

    @Test
    fun `C keeps legs apart when the ride is listed first`() {
        assertSpecExample(
            UberOfferParser.parse(
                """
                17,20 RON
                Cursă: 12 min. (5.2 km)
                Str. Liviu Rebreanu 5
                La 5 min. (2.9 km) distanță
                Bulevardul Theodor Pallady 51G
                """.trimIndent()
            )
        )
    }

    @Test
    fun `C2 handles labels printed after their numbers`() {
        val offer = UberOfferParser.parse(
            """
            17,20 RON
            2,9 km • 5 min distanță
            Cursă 5,2 km • 12 min
            """.trimIndent()
        )!!
        assertEquals(2.9, offer.pickupDistanceKm!!, 0.001)
        assertEquals(5.2, offer.tripDistanceKm!!, 0.001)
    }

    // --- D: pickup missing ----------------------------------------------

    @Test
    fun `D reports incomplete data when the pickup leg is missing`() {
        val offer = UberOfferParser.parse("17,20 RON\nCursă: 12 min. (5.2 km)")!!
        assertNull(offer.pickupDistanceKm)
        assertNull(offer.pickupTimeMinutes)
        // The ride's own numbers must not be copied into the pickup slots.
        assertEquals(5.2, offer.tripDistanceKm!!, 0.001)
        assertNull(offer.totalDistanceKm)
        assertNull(offer.totalTimeMinutes)
        assertFalse(offer.isReliable)

        val analysis = OfferCalculator.analyze(offer, settings)
        assertEquals(Verdict.CAUTION, analysis.verdict)
        assertNull(analysis.ronPerKm)
        assertNull(analysis.ronPerHour)
        assertTrue(analysis.reason.contains("incomplete"))
    }

    // --- E: ride missing ------------------------------------------------

    @Test
    fun `E reports incomplete data when the ride leg is missing`() {
        val offer = UberOfferParser.parse("17,20 RON\nLa 5 min. (2.9 km) distanță")!!
        assertEquals(2.9, offer.pickupDistanceKm!!, 0.001)
        assertNull(offer.tripDistanceKm)
        assertNull(offer.tripTimeMinutes)
        assertNull(offer.totalDistanceKm)
        assertFalse(offer.isReliable)
        assertEquals(Verdict.CAUTION, OfferCalculator.analyze(offer, settings).verdict)
    }

    @Test
    fun `E2 a missing leg is never counted as zero kilometres`() {
        // The specific arithmetic bug: 17.20 over 5.2 km reads 3.31 RON/km,
        // when the real figure over both legs is 2.12.
        val analysis = OfferCalculator.analyze(
            UberOfferParser.parse("17,20 RON\nCursă: 12 min. (5.2 km)")!!,
            settings
        )
        assertNull(analysis.totalKm)
        assertNull(analysis.ronPerKm)
    }

    // --- F: repeated values ---------------------------------------------

    @Test
    fun `F uses context when both legs carry the same numbers`() {
        val offer = UberOfferParser.parse(
            """
            17,20 RON
            La 12 min. (5.2 km) distanță
            Cursă: 12 min. (5.2 km)
            """.trimIndent()
        )!!
        // Genuinely equal legs, read from two distinct occurrences.
        assertEquals(5.2, offer.pickupDistanceKm!!, 0.001)
        assertEquals(5.2, offer.tripDistanceKm!!, 0.001)
        assertEquals(10.4, offer.totalDistanceKm!!, 0.001)
        assertEquals(24, offer.totalTimeMinutes)
    }

    @Test
    fun `F2 a single occurrence is never used for both legs`() {
        val offer = UberOfferParser.parse("17,20 RON\nCursă: 12 min. (5.2 km)")!!
        assertNotEquals(offer.tripDistanceKm, offer.pickupDistanceKm)
        assertNull(offer.pickupDistanceKm)
    }

    // --- totals feed the verdict ----------------------------------------

    @Test
    fun `totals drive RON per km and RON per hour`() {
        val analysis = OfferCalculator.analyze(
            UberOfferParser.parse(
                "17,20 RON\nLa 5 min. (2.9 km) distanță\nCursă: 12 min. (5.2 km)"
            )!!,
            settings
        )
        assertEquals(8.1, analysis.totalKm!!, 0.001)
        assertEquals(17, analysis.totalMinutes)
        // 17.20 / 8.1, not 17.20 / 5.2.
        assertEquals(2.12, analysis.ronPerKm!!, 0.01)
        // 17.20 over a 24-minute slot (2.5 rides an hour), not over the 17
        // minutes of driving alone — the wait after it is part of its cost.
        assertEquals(43.0, analysis.ronPerHour!!, 0.01)
    }

    @Test
    fun `the full pipeline keeps the legs apart`() {
        assertSpecExample(
            OfferParserRouter.parse(
                """
                Uber
                17,20 RON
                La
                5
                min.
                (2.9 km)
                distanță
                Bulevardul Theodor Pallady 51G, București
                Cursă:
                12
                min.
                (5.2 km)
                Str. Liviu Rebreanu 5, București
                """.trimIndent()
            )
        )
    }
}
