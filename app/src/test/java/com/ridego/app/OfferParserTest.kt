package com.ridego.app

import com.ridego.app.parser.OfferParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferParserTest {

    private val example1 = """
        17,20 RON

        La 5 min. (2.9 km) distanță
        Bulevardul Theodor Pallady 51G, București

        Cursă: 12 min. (5.2 km)
        Str. Liviu Rebreanu 5, București
    """.trimIndent()

    private val example2 = """
        30,29 RON

        La 17 min. (14.8 km) distanță
        Str. Biruinței 85, Popești-Leordeni

        Cursă: 19 min. (8.2 km)
        Str. Liviu Rebreanu 5, București
    """.trimIndent()

    private val example3 = """
        19,76 RON

        La 1 min. (0.6 km) distanță
        HILS Pallady 66A, București

        Cursă: 20 min. (9.7 km)
        Strada Sfântul Călinic 16, Pantelimon
    """.trimIndent()

    @Test
    fun `parses example 1`() {
        val offer = OfferParser.parse(example1)!!
        assertEquals(17.20, offer.price!!, 0.001)
        assertEquals(2.9, offer.pickupDistanceKm!!, 0.001)
        assertEquals(5, offer.pickupTimeMinutes)
        assertEquals(5.2, offer.tripDistanceKm!!, 0.001)
        assertEquals(12, offer.tripTimeMinutes)
        assertEquals("Bulevardul Theodor Pallady 51G, București", offer.pickupAddress)
        assertEquals("Str. Liviu Rebreanu 5, București", offer.destinationAddress)
        assertTrue(offer.isComplete)
    }

    @Test
    fun `parses example 2`() {
        val offer = OfferParser.parse(example2)!!
        assertEquals(30.29, offer.price!!, 0.001)
        assertEquals(14.8, offer.pickupDistanceKm!!, 0.001)
        assertEquals(17, offer.pickupTimeMinutes)
        assertEquals(8.2, offer.tripDistanceKm!!, 0.001)
        assertEquals(19, offer.tripTimeMinutes)
    }

    @Test
    fun `parses example 3`() {
        val offer = OfferParser.parse(example3)!!
        assertEquals(19.76, offer.price!!, 0.001)
        assertEquals(0.6, offer.pickupDistanceKm!!, 0.001)
        assertEquals(1, offer.pickupTimeMinutes)
        assertEquals(9.7, offer.tripDistanceKm!!, 0.001)
        assertEquals(20, offer.tripTimeMinutes)
    }

    @Test
    fun `accepts dot decimal price and lei currency`() {
        val offer = OfferParser.parse(
            """
            24.50 lei
            La 3 min. (1,4 km) distanta
            Calea Victoriei 10
            Cursa: 15 min. (6,8 km)
            Piata Unirii 1
            """.trimIndent()
        )!!
        assertEquals(24.50, offer.price!!, 0.001)
        assertEquals(1.4, offer.pickupDistanceKm!!, 0.001)
        assertEquals(6.8, offer.tripDistanceKm!!, 0.001)
    }

    @Test
    fun `handles separator layout without parentheses`() {
        val offer = OfferParser.parse(
            """
            41,00 RON
            La distanță 2,5 km • 6 min
            Bd. Unirii 3
            Cursă 11,0 km • 21 min
            Aeroport Otopeni
            """.trimIndent()
        )!!
        assertEquals(2.5, offer.pickupDistanceKm!!, 0.001)
        assertEquals(6, offer.pickupTimeMinutes)
        assertEquals(11.0, offer.tripDistanceKm!!, 0.001)
        assertEquals(21, offer.tripTimeMinutes)
    }

    @Test
    fun `falls back to positional order when labels are missing`() {
        val offer = OfferParser.parse(
            """
            17,20 RON
            5 min. (2.9 km)
            Bulevardul Theodor Pallady 51G
            12 min. (5.2 km)
            Str. Liviu Rebreanu 5
            """.trimIndent()
        )!!
        assertEquals(2.9, offer.pickupDistanceKm!!, 0.001)
        assertEquals(5.2, offer.tripDistanceKm!!, 0.001)
    }

    @Test
    fun `extracts service type payment and rating`() {
        val offer = OfferParser.parse(
            """
            UberX
            ★ 4,92
            17,20 RON  Card
            La 5 min. (2.9 km) distanță
            Bulevardul Theodor Pallady 51G
            Cursă: 12 min. (5.2 km)
            Str. Liviu Rebreanu 5
            """.trimIndent()
        )!!
        assertEquals("UberX", offer.serviceType)
        assertEquals("Card", offer.paymentMethod)
        assertEquals(4.92, offer.rating!!, 0.001)
    }

    @Test
    fun `rejects unrelated screen text`() {
        assertFalse(OfferParser.looksLikeOffer("Setări\nNotificări\nCont\nDeconectare"))
        assertNull(OfferParser.parse("Bună dimineața! Ai 3 mesaje necitite."))
    }

    @Test
    fun `rejects a map screen with distance but no fare`() {
        assertFalse(OfferParser.looksLikeOffer("Ești la 5 min. (2.9 km) de destinație"))
    }

    @Test
    fun `same offer produces a stable signature`() {
        val a = OfferParser.parse(example1)!!
        val b = OfferParser.parse(example1.replace("\n\n", "\n"))!!
        assertEquals(a.signature, b.signature)
    }

    @Test
    fun `different offers produce different signatures`() {
        // The overlay's 15s timer only restarts on a genuinely new offer, and
        // that decision rests entirely on this signature differing.
        val a = OfferParser.parse(example1)!!
        val b = OfferParser.parse(example2)!!
        val c = OfferParser.parse(example3)!!
        assertEquals(3, setOf(a.signature, b.signature, c.signature).size)
    }

    @Test
    fun `signature ignores fields that do not change the ride`() {
        val plain = OfferParser.parse(example1)!!
        val withExtras = OfferParser.parse("UberX\n★ 4,92\nCard\n$example1")!!
        assertEquals(plain.signature, withExtras.signature)
    }

    @Test
    fun `tolerates noisy ocr line merging`() {
        val offer = OfferParser.parse(
            "17,20 RON | La 5 min. (2.9 km) distanță | Cursă: 12 min. (5.2 km)"
        )
        assertNotNull(offer)
        assertEquals(17.20, offer!!.price!!, 0.001)
    }
}
