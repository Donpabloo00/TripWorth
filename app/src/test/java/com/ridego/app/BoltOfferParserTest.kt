package com.ridego.app

import com.ridego.app.parser.BoltOfferParser
import com.ridego.app.parser.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SYNTHETIC FIXTURES. No real Bolt Driver capture was available when this
 * parser was written, so these encode the layout from the spec plus the
 * fragmentation OCR tends to introduce. They prove the parser is tolerant,
 * NOT that it matches the real Bolt app.
 */
class BoltOfferParserTest {

    private val standard = """
        Bolt
        25,50 RON

        3,2 km
        6 min

        Pickup:
        Strada Exemplu 10, București

        Destinație:
        Bulevardul Exemplu 20, București

        8,4 km
        18 min
    """.trimIndent()

    private val fragmented = """
        Bolt
        25,50
        RON
        3.2 km
        6 min
        Strada Exemplu 10
        București
        8.4 km
        18 min
        Bulevardul Exemplu 20
        București
    """.trimIndent()

    @Test
    fun `parses the standard layout`() {
        val offer = BoltOfferParser.parse(standard)!!
        assertEquals(Platform.BOLT, offer.platform)
        assertEquals(25.50, offer.price!!, 0.001)
        assertEquals(3.2, offer.pickupDistanceKm!!, 0.001)
        assertEquals(6, offer.pickupTimeMinutes)
        assertEquals(8.4, offer.tripDistanceKm!!, 0.001)
        assertEquals(18, offer.tripTimeMinutes)
        assertTrue(offer.isComplete)
    }

    @Test
    fun `parses the fragmented layout to the same numbers`() {
        val a = BoltOfferParser.parse(standard)!!
        val b = BoltOfferParser.parse(fragmented)!!
        assertEquals(a.price, b.price)
        assertEquals(a.pickupDistanceKm, b.pickupDistanceKm)
        assertEquals(a.tripDistanceKm, b.tripDistanceKm)
        assertEquals(a.pickupTimeMinutes, b.pickupTimeMinutes)
        assertEquals(a.tripTimeMinutes, b.tripTimeMinutes)
        // Same ride, so duplicate suppression must treat them as one offer.
        assertEquals(a.signature, b.signature)
    }

    @Test
    fun `reads addresses from labels`() {
        val offer = BoltOfferParser.parse(standard)!!
        assertEquals("Strada Exemplu 10, București", offer.pickupAddress)
        assertEquals("Bulevardul Exemplu 20, București", offer.destinationAddress)
    }

    @Test
    fun `reads addresses from inline labels`() {
        val offer = BoltOfferParser.parse(
            """
            Bolt Comfort
            48,00 RON • Card
            2,1 km • 4 min
            Pickup: Piața Victoriei 1, București
            Destinație: Aeroport Otopeni
            18,6 km • 26 min
            """.trimIndent()
        )!!
        assertEquals("Piața Victoriei 1, București", offer.pickupAddress)
        assertEquals("Aeroport Otopeni", offer.destinationAddress)
        assertEquals("Bolt Comfort", offer.serviceType)
        assertEquals("Card", offer.paymentMethod)
    }

    @Test
    fun `keeps left to right order within a line`() {
        val offer = BoltOfferParser.parse(
            """
            Bolt
            30,00 RON
            2,0 km • 5 min
            9,0 km • 20 min
            """.trimIndent()
        )!!
        assertEquals(2.0, offer.pickupDistanceKm!!, 0.001)
        assertEquals(5, offer.pickupTimeMinutes)
        assertEquals(9.0, offer.tripDistanceKm!!, 0.001)
        assertEquals(20, offer.tripTimeMinutes)
    }

    @Test
    fun `a single leg is attributed to the ride not the pickup`() {
        // Half-read cards must not invent a pickup leg; the missing fields
        // should pull confidence down instead.
        val offer = BoltOfferParser.parse(
            """
            Bolt
            22,00 RON
            7,5 km
            15 min
            """.trimIndent()
        )!!
        assertNull(offer.pickupDistanceKm)
        assertNull(offer.pickupTimeMinutes)
        assertEquals(7.5, offer.tripDistanceKm!!, 0.001)
        assertEquals(15, offer.tripTimeMinutes)
        // Confidence alone still reads 70% under the spec's weights, so
        // reliability is what has to reject it.
        assertFalse(offer.isReliable)
    }

    @Test
    fun `handles both decimal separators`() {
        val comma = BoltOfferParser.parse("Bolt\n25,50 RON\n3,2 km\n6 min\n8,4 km\n18 min")!!
        val dot = BoltOfferParser.parse("Bolt\n25.50 RON\n3.2 km\n6 min\n8.4 km\n18 min")!!
        assertEquals(comma.signature, dot.signature)
    }

    @Test
    fun `returns null for text with no offer data`() {
        assertNull(BoltOfferParser.parse("Bolt\nSetări\nProfil\nDeconectare"))
    }

    @Test
    fun `ignores implausible values`() {
        val offer = BoltOfferParser.parse("Bolt\n25,50 RON\n9999 km\n3,2 km\n6 min\n8,4 km\n18 min")
        assertNotNull(offer)
        // 9999 km is out of range and must not become the pickup leg.
        assertEquals(3.2, offer!!.pickupDistanceKm!!, 0.001)
    }
}
