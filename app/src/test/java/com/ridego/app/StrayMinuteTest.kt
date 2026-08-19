package com.ridego.app

import com.ridego.app.parser.UberOfferParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The Uber map draws its own ETA badges — "1–8 min", "1–5 min" — outside the
 * offer card, and OCR sweeps the whole screen. Those numbers used to win over
 * the ride's real duration simply by appearing first, which is how an 8.5 km
 * trip came back as one minute.
 */
class StrayMinuteTest {

    @Test
    fun `an ETA badge above the card does not become the pickup duration`() {
        val offer = UberOfferParser.parse(
            """
            1–8 min
            Uber
            20,38 RON
            La 4 min. (2.2 km) distanță
            Strada Exemplu 1, București
            Cursă: 19 min. (8.5 km)
            Strada Exemplu 2, București
            """.trimIndent()
        )
        assertNotNull(offer)
        assertEquals(2.2, offer!!.pickupDistanceKm!!, 0.001)
        assertEquals(4, offer.pickupTimeMinutes)
        assertEquals(8.5, offer.tripDistanceKm!!, 0.001)
        assertEquals(19, offer.tripTimeMinutes)
    }

    @Test
    fun `an ETA badge below the card does not become the trip duration`() {
        // The reported failure: a stray minute inside the trip segment, which
        // "first one wins" happily preferred over the real 19.
        val offer = UberOfferParser.parse(
            """
            Uber
            20,38 RON
            La 4 min. (2.2 km) distanță
            Strada Exemplu 1, București
            Cursă: 19 min. (8.5 km)
            Strada Exemplu 2, București
            1 min
            """.trimIndent()
        )
        assertNotNull(offer)
        assertEquals(19, offer!!.tripTimeMinutes)
        assertEquals(8.5, offer.tripDistanceKm!!, 0.001)
    }

    @Test
    fun `a stray minute wins only when nothing plausible competes`() {
        // No real duration on the card at all. The stray value is still
        // reported rather than invented away — the plausibility guard in the
        // calculator is what stops it reaching a verdict.
        val offer = UberOfferParser.parse(
            """
            Uber
            20,38 RON
            La 4 min. (2.2 km) distanță
            Cursă: (8.5 km)
            1 min
            """.trimIndent()
        )
        assertNotNull(offer)
        assertEquals(8.5, offer!!.tripDistanceKm!!, 0.001)
        assertEquals(1, offer.tripTimeMinutes)
    }

    @Test
    fun `the canonical card is unaffected`() {
        val offer = UberOfferParser.parse(
            """
            17,20 RON
            La 5 min. (2.9 km) distanță
            Cursă: 12 min. (5.2 km)
            """.trimIndent()
        )
        assertNotNull(offer)
        assertEquals(5, offer!!.pickupTimeMinutes)
        assertEquals(12, offer.tripTimeMinutes)
    }
}
