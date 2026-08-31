package com.ridego.app

import com.ridego.app.parser.OwnBannerDetector
import com.ridego.app.parser.UberOfferParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RideGo reading its own banner back as an offer.
 *
 * The text below is a real capture from a driver's phone: the banner was on
 * screen over the Uber card, and both ended up in one frame. The parser read
 * "Preluare prea departe: 3,8 km" as a 3.8 km approach and RideGo's own
 * "4,2 km" as a 42 km ride.
 */
class OwnBannerTest {

    /** Verbatim from the device archive, diacritics and OCR noise included. */
    private val contaminated = """
        22:56 →
        Viorel Urse Nr Meu
        online
        2,87 RON/km
        4,03 RON/km
        CURSA
        Oferta
        - Preluare prea departe: 3,8 km / maxim 1,5
        km
        G PRAGUL TĂU
        Minim:
        CURSĂ
        16,91 RON
        X RESPINGE
        PRAGUL TAD
        peste
        & uberx Priority Exclusive
        RON16.91
        482 Net of service fee
        O +RON291 included for priority
        1 min (0 km) away
        Bd. Theodor Pallady 66A, Bucureşti
        7 mins (4.2 km) trip
        Popeşti-Leordeni
    """.trimIndent()

    @Test
    fun `the contaminated frame is recognised as our own banner`() {
        assertTrue(OwnBannerDetector.isOwnBanner(contaminated))
    }

    @Test
    fun `each banner phrase is caught despite OCR mangling the diacritics`() {
        assertTrue(OwnBannerDetector.isOwnBanner("PRAGUL TAD"))
        assertTrue(OwnBannerDetector.isOwnBanner("PRAGUL TĂU"))
        assertTrue(OwnBannerDetector.isOwnBanner("Trage pentru a muta"))
        assertTrue(OwnBannerDetector.isOwnBanner("120 RON/oră NET"))
        assertTrue(OwnBannerDetector.isOwnBanner("Cursa prea lungă: 42 km"))
        assertTrue(OwnBannerDetector.isOwnBanner("TRIPWORTH • UBER"))
        assertTrue(OwnBannerDetector.isOwnBanner("VERDICTCURSĂ • UBER"))
        assertTrue(OwnBannerDetector.isOwnBanner("RIDEGO • UBER"))
        assertTrue(OwnBannerDetector.isOwnBanner("● CURSĂ BUNĂ"))
        assertTrue(OwnBannerDetector.isOwnBanner("7,80 COST COMB."))
        assertTrue(OwnBannerDetector.isOwnBanner("Încasezi 43,50 RON"))
    }

    @Test
    fun `a clean Uber card is never mistaken for the banner`() {
        val clean = """
            & uberx Priority Exclusive
            RON16.91
            482 Net of service fee
            O +RON291 included for priority
            1 min (0 km) away
            Bd. Theodor Pallady 66A, Bucureşti
            7 mins (4.2 km) trip
            Popeşti-Leordeni
        """.trimIndent()
        assertFalse(OwnBannerDetector.isOwnBanner(clean))
    }

    @Test
    fun `the clean card still parses to the right legs`() {
        // The English layout, with the label trailing its measurements.
        val offer = UberOfferParser.parse(
            """
            & uberx Priority Exclusive
            RON16.91
            1 min (0 km) away
            Bd. Theodor Pallady 66A, Bucureşti
            7 mins (4.2 km) trip
            Popeşti-Leordeni
            """.trimIndent()
        )
        assertNotNull(offer)
        assertEquals(4.2, offer!!.tripDistanceKm!!, 0.001)
        assertEquals(7, offer.tripTimeMinutes)
    }

    @Test
    fun `the demo samples are not flagged as our own banner`() {
        // A false positive here would blind RideGo to real offers, which is a
        // worse failure than the one this guard prevents.
        com.ridego.app.data.DemoOffers.samples.forEach { sample ->
            assertFalse(sample.label, OwnBannerDetector.isOwnBanner(sample.ocrText))
        }
    }
}
