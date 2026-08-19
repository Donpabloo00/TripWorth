package com.ridego.app

import com.ridego.app.parser.Platform
import com.ridego.app.parser.RideOffer
import com.ridego.app.parser.UberOfferParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * OCR substituting letters for digits, and the double-counting it caused.
 *
 * From a real capture: "RON25.11" came back as "RON25.1l". The fare parsed as
 * 25.10 on one read and 25.11 on the next, which made one offer look like two.
 */
class OcrDigitConfusionTest {

    private fun offer(price: Double?) = RideOffer(
        platform = Platform.UBER,
        price = price,
        pickupDistanceKm = 4.5,
        pickupTimeMinutes = 8,
        tripDistanceKm = 7.2,
        tripTimeMinutes = 18,
        pickupAddress = null,
        destinationAddress = null,
        serviceType = null,
        rating = null,
        paymentMethod = null
    )

    @Test
    fun `a lowercase L in the fare is read as the digit one`() {
        val parsed = UberOfferParser.parse(
            """
            2 uberx
            RON25.1l
            * 4.80 Net of service fee
            8 mins (4.5 km) away
            18 mins (7.2 km) trip
            """.trimIndent()
        )
        assertNotNull(parsed)
        assertEquals(25.11, parsed!!.price!!, 0.001)
    }

    @Test
    fun `the legs of that same real card are unaffected`() {
        val parsed = UberOfferParser.parse(
            """
            RON25.1l
            l stop
            8 mins (4.5 km) away
            18 mins (7.2 km) trip
            """.trimIndent()
        )
        assertNotNull(parsed)
        assertEquals(4.5, parsed!!.pickupDistanceKm!!, 0.001)
        assertEquals(8, parsed.pickupTimeMinutes)
        assertEquals(7.2, parsed.tripDistanceKm!!, 0.001)
        assertEquals(18, parsed.tripTimeMinutes)
    }

    @Test
    fun `letters alone are never a fare`() {
        assertNull(UberOfferParser.parse("RON lO\nno numbers here")?.price)
    }

    @Test
    fun `one cent of OCR jitter no longer splits the signature`() {
        // The exact failure seen on the device: 25.11 then 25.10, counted twice.
        assertEquals(offer(25.11).signature, offer(25.10).signature)
    }

    @Test
    fun `genuinely different fares still have different signatures`() {
        assertNotEquals(offer(25.11).signature, offer(26.90).signature)
        assertNotEquals(offer(25.11).signature, offer(24.20).signature)
    }
}
