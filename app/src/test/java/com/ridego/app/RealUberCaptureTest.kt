package com.ridego.app

import com.ridego.app.calculator.OfferCalculator
import com.ridego.app.calculator.RideSettings
import com.ridego.app.parser.OfferParserRouter
import com.ridego.app.parser.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first verbatim ML Kit capture from a real Uber Driver offer, taken on
 * the device with COPY RAW OCR.
 *
 * Everything before this was written from the spec; this is ground truth, and
 * it exposed three things the synthetic fixtures never could:
 *   - the app runs in English, so the leg labels are "away" and "trip";
 *   - the label follows its numbers instead of preceding them;
 *   - the fare prints as "RON2768", currency glued on and the decimal
 *     separator lost.
 */
class RealUberCaptureTest {

    private val capture = """
        RON2768
        4.91 Net of service fee
        16 mins (12.3 km) away
        Soseaua Berceni 110, Bucuresti
        14 mins (8.2 km) trip
        Strada Neagureni 26, Bucuresti
    """.trimIndent()

    @Test
    fun `the gate accepts a real offer card`() {
        assertTrue(OfferParserRouter.looksLikeOffer(capture))
    }

    @Test
    fun `parses the real capture end to end`() {
        val offer = OfferParserRouter.parse(capture)!!

        assertEquals(Platform.UBER, offer.platform)
        assertEquals(27.68, offer.price!!, 0.001)
        assertEquals(12.3, offer.pickupDistanceKm!!, 0.001)
        assertEquals(16, offer.pickupTimeMinutes)
        assertEquals(8.2, offer.tripDistanceKm!!, 0.001)
        assertEquals(14, offer.tripTimeMinutes)
        assertEquals(20.5, offer.totalDistanceKm!!, 0.001)
        assertEquals(30, offer.totalTimeMinutes)
        assertTrue(offer.isReliable)
    }

    @Test
    fun `the service fee is not mistaken for the fare`() {
        // "4.91 Net of service fee" is a second amount on the same card.
        val offer = OfferParserRouter.parse(capture)!!
        assertEquals(27.68, offer.price!!, 0.001)
    }

    @Test
    fun `reads addresses from the real capture`() {
        val offer = OfferParserRouter.parse(capture)!!
        assertEquals("Soseaua Berceni 110, Bucuresti", offer.pickupAddress)
        assertEquals("Strada Neagureni 26, Bucuresti", offer.destinationAddress)
    }

    @Test
    fun `produces a verdict from the real capture`() {
        val analysis = OfferCalculator.analyze(
            OfferParserRouter.parse(capture)!!,
            RideSettings()
        )
        assertEquals(20.5, analysis.totalKm!!, 0.001)
        assertEquals(30, analysis.totalMinutes)
        assertEquals(1.35, analysis.ronPerKm!!, 0.01)
        assertEquals(55.36, analysis.ronPerHour!!, 0.01)
    }

    /**
     * Second real card, read off the device on 18 Aug. Adds a priority bonus
     * line and a second RON amount, and the ride leg is longer than the
     * approach — which is what makes the leg order worth pinning down.
     */
    private val priorityCapture = """
        UberX Priority
        Exclusive
        RON28.79
        4.78
        Net of service fee
        +RON4.58 included for priority
        9 mins (4.9 km) away
        Bucharest
        23 mins (8.5 km) trip
        Bulevardul Unirii 43, Unirea
    """.trimIndent()

    @Test
    fun `parses the priority card without swapping the legs`() {
        val offer = OfferParserRouter.parse(priorityCapture)!!
        assertEquals(28.79, offer.price!!, 0.001)
        // "away" is the approach, "trip" is the paid ride — not the reverse.
        assertEquals(4.9, offer.pickupDistanceKm!!, 0.001)
        assertEquals(9, offer.pickupTimeMinutes)
        assertEquals(8.5, offer.tripDistanceKm!!, 0.001)
        assertEquals(23, offer.tripTimeMinutes)
        assertEquals(13.4, offer.totalDistanceKm!!, 0.001)
        assertEquals(32, offer.totalTimeMinutes)
    }

    @Test
    fun `the priority bonus is not mistaken for the fare`() {
        // "+RON4.58 included for priority" sits right under the real fare.
        assertEquals(28.79, OfferParserRouter.parse(priorityCapture)!!.price!!, 0.001)
    }

    @Test
    fun `a three digit fare is not rescaled as cents`() {
        // The cents rule must not turn a genuine RON350 ride into RON3.50.
        val offer = OfferParserRouter.parse(
            capture.replace("RON2768", "RON350")
        )!!
        assertEquals(350.0, offer.price!!, 0.001)
    }
}
