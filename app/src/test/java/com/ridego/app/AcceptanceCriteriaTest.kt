package com.ridego.app

import com.ridego.app.calculator.OfferCalculator
import com.ridego.app.calculator.RideSettings
import com.ridego.app.calculator.Verdict
import com.ridego.app.parser.Platform
import com.ridego.app.parser.RideOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three switchable acceptance rules.
 *
 * Every offer here is deliberately generous enough to clear the hourly bar on
 * its own, so an ACCEPT proves the rule stayed out of the way and a REJECT
 * proves the rule — and nothing else — did the rejecting.
 */
class AcceptanceCriteriaTest {

    private val settings = RideSettings()

    private fun offer(
        price: Double? = 65.0,
        pickupKm: Double? = 3.2,
        pickupMin: Int? = 5,
        tripKm: Double? = 24.0,
        tripMin: Int? = 20
    ) = RideOffer(
        platform = Platform.UBER,
        price = price,
        pickupDistanceKm = pickupKm,
        pickupTimeMinutes = pickupMin,
        tripDistanceKm = tripKm,
        tripTimeMinutes = tripMin,
        pickupAddress = null,
        destinationAddress = null,
        serviceType = null,
        rating = null,
        paymentMethod = null
    )

    @Test
    fun `baseline offer is accepted with no rule enabled`() {
        assertEquals(Verdict.ACCEPT, OfferCalculator.analyze(offer(), settings).verdict)
    }

    // --- rule 1: minimum cost per km ------------------------------------

    @Test
    fun `1 min cost per km on and offer above the bar is accepted`() {
        // 24 km x 2.50 = 60 RON required, offer pays 65.
        val rule = settings.copy(minCostPerKmEnabled = true, minCostPerKm = 2.50)
        assertEquals(Verdict.ACCEPT, OfferCalculator.analyze(offer(), rule).verdict)
    }

    @Test
    fun `2 min cost per km on and offer below the bar is rejected`() {
        val rule = settings.copy(minCostPerKmEnabled = true, minCostPerKm = 2.50)
        val a = OfferCalculator.analyze(offer(price = 55.0), rule)
        assertEquals(Verdict.REJECT, a.verdict)
        assertTrue(a.reason, a.reason.contains("Sub costul minim: 60 RON necesari"))
    }

    @Test
    fun `3 min cost per km off leaves the price out of the verdict`() {
        val off = settings.copy(minCostPerKmEnabled = false, minCostPerKm = 2.50)
        val a = OfferCalculator.analyze(offer(price = 55.0), off)
        assertEquals(Verdict.ACCEPT, a.verdict)
        assertTrue(a.reason, !a.reason.contains("costul minim"))
    }

    @Test
    fun `1b the rule measures the ride only, never the approach leg`() {
        val rule = settings.copy(minCostPerKmEnabled = true, minCostPerKm = 2.50)
        // The fare sits between the two possible bars: 60 RON on the ride's
        // 24 km, 110 RON on the 44 km total. Passing therefore proves the
        // rule used trip distance — a total-based bar would reject this.
        val longApproach = offer(price = 100.0, pickupKm = 20.0, pickupMin = 25)
        assertEquals(Verdict.ACCEPT, OfferCalculator.analyze(longApproach, rule).verdict)
    }

    // --- rule 2: maximum distance to the rider --------------------------

    @Test
    fun `4 max pickup on and distance under the limit is accepted`() {
        val rule = settings.copy(maxPickupKmEnabled = true, maxPickupKm = 5.0)
        assertEquals(Verdict.ACCEPT, OfferCalculator.analyze(offer(), rule).verdict)
    }

    @Test
    fun `5 max pickup on and distance over the limit is rejected`() {
        val rule = settings.copy(maxPickupKmEnabled = true, maxPickupKm = 5.0)
        val a = OfferCalculator.analyze(offer(pickupKm = 7.2), rule)
        assertEquals(Verdict.REJECT, a.verdict)
        // Romanian locale, so the decimal separator is a comma.
        assertTrue(a.reason, a.reason.contains("Preluare prea departe: 7,2 km / maxim 5 km"))
    }

    @Test
    fun `6 max pickup off leaves the approach distance out of the verdict`() {
        val off = settings.copy(maxPickupKmEnabled = false, maxPickupKm = 5.0)
        val a = OfferCalculator.analyze(offer(pickupKm = 7.2), off)
        assertEquals(Verdict.ACCEPT, a.verdict)
        assertTrue(a.reason, !a.reason.contains("Preluare prea departe"))
    }

    // --- rule 3: maximum length of the paid ride ------------------------

    @Test
    fun `7 max trip on and distance under the limit is accepted`() {
        val rule = settings.copy(maxTripKmEnabled = true, maxTripKm = 30.0)
        assertEquals(Verdict.ACCEPT, OfferCalculator.analyze(offer(), rule).verdict)
    }

    @Test
    fun `8 max trip on and distance over the limit is rejected`() {
        val rule = settings.copy(maxTripKmEnabled = true, maxTripKm = 30.0)
        val long = offer(price = 160.0, tripKm = 42.0, tripMin = 35)
        val a = OfferCalculator.analyze(long, rule)
        assertEquals(Verdict.REJECT, a.verdict)
        assertTrue(a.reason, a.reason.contains("Cursa prea lungă: 42 km / maxim 30 km"))
    }

    @Test
    fun `9 max trip off leaves the ride distance out of the verdict`() {
        val off = settings.copy(maxTripKmEnabled = false, maxTripKm = 30.0)
        val long = offer(price = 160.0, tripKm = 42.0, tripMin = 35)
        val a = OfferCalculator.analyze(long, off)
        assertEquals(Verdict.ACCEPT, a.verdict)
        assertTrue(a.reason, !a.reason.contains("Cursa prea lungă"))
    }

    // --- combinations ---------------------------------------------------

    @Test
    fun `10 all three rules on and all satisfied is accepted`() {
        val all = settings.copy(
            minCostPerKmEnabled = true, minCostPerKm = 2.50,
            maxPickupKmEnabled = true, maxPickupKm = 5.0,
            maxTripKmEnabled = true, maxTripKm = 30.0
        )
        // 65 >= 60, 3.2 <= 5, 24 <= 30.
        assertEquals(Verdict.ACCEPT, OfferCalculator.analyze(offer(), all).verdict)
    }

    @Test
    fun `11 every broken rule is reported, not just the first`() {
        val all = settings.copy(
            minCostPerKmEnabled = true, minCostPerKm = 2.50,
            maxPickupKmEnabled = true, maxPickupKm = 5.0,
            maxTripKmEnabled = true, maxTripKm = 30.0
        )
        val bad = offer(price = 55.0, pickupKm = 7.2, tripKm = 42.0, tripMin = 35)
        val a = OfferCalculator.analyze(bad, all)

        assertEquals(Verdict.REJECT, a.verdict)
        assertEquals(a.reasons.toString(), 3, a.reasons.size)
        assertTrue(a.reasons.any { it.contains("Sub costul minim") })
        assertTrue(a.reasons.any { it.contains("Preluare prea departe") })
        assertTrue(a.reasons.any { it.contains("Cursa prea lungă") })
        // Bulleted once there is more than one, so the overlay can show them all.
        assertTrue(a.reason, a.reason.startsWith("• "))
        assertEquals(3, a.reason.lines().size)
    }

    @Test
    fun `11b only the enabled rule of a mixed set applies`() {
        // Rule 1 off, rule 2 on, rule 3 off — the offer breaks all three.
        val mixed = settings.copy(
            minCostPerKmEnabled = false, minCostPerKm = 2.50,
            maxPickupKmEnabled = true, maxPickupKm = 5.0,
            maxTripKmEnabled = false, maxTripKm = 30.0
        )
        val bad = offer(price = 55.0, pickupKm = 7.2, tripKm = 42.0, tripMin = 35)
        val a = OfferCalculator.analyze(bad, mixed)

        assertEquals(Verdict.REJECT, a.verdict)
        assertEquals(a.reasons.toString(), 1, a.reasons.size)
        assertTrue(a.reason, a.reason.contains("Preluare prea departe"))
    }

    // --- implausible OCR ------------------------------------------------

    @Test
    fun `the 1223 RON per hour offer is refused a verdict`() {
        // The exact card from the field: 20,38 RON for 8.5 km read as one
        // minute. That is 510 km/h, and it produced a confident 1223 RON/oră.
        val misread = offer(price = 20.38, pickupKm = 2.2, pickupMin = 4, tripKm = 8.5, tripMin = 1)
        val a = OfferCalculator.analyze(misread, settings)

        assertEquals(Verdict.CAUTION, a.verdict)
        assertTrue(a.reason, a.reason.contains("Citire greșită"))
        assertTrue(a.reason, a.reason.contains("510 km/h"))
        // Nothing derived from the bad minute may reach the screen.
        assertNull(a.ronPerHour)
        assertNull(a.netRonPerHour)
        assertNull(a.ronPerMinute)
        assertNull(a.totalMinutes)
    }

    @Test
    fun `the distance survives an implausible time`() {
        // RON/km does not depend on the minute, so it is still worth showing.
        val misread = offer(price = 20.38, tripKm = 8.5, tripMin = 1)
        val a = OfferCalculator.analyze(misread, settings.copy(includePickup = false))
        assertEquals(8.5, a.totalKm!!, 0.001)
        assertEquals(20.38 / 8.5, a.ronPerKm!!, 0.001)
    }

    @Test
    fun `zero minutes is treated as a mis-read, not as infinite speed`() {
        val a = OfferCalculator.analyze(offer(tripMin = 0), settings)
        assertEquals(Verdict.CAUTION, a.verdict)
        assertTrue(a.reason, a.reason.contains("Citire greșită"))
    }

    @Test
    fun `a motorway leg is still believable`() {
        // 24 km in 15 minutes is 96 km/h — fast, legal, and not a mis-read.
        val fast = offer(price = 90.0, tripKm = 24.0, tripMin = 15, pickupKm = 3.2, pickupMin = 5)
        assertEquals(Verdict.ACCEPT, OfferCalculator.analyze(fast, settings).verdict)
    }

    @Test
    fun `crawling through a jam is still believable`() {
        // 24 km in 3 hours is 8 km/h. Miserable, but real.
        val jam = offer(price = 200.0, tripKm = 24.0, tripMin = 180, pickupKm = 3.2, pickupMin = 20)
        val a = OfferCalculator.analyze(jam, settings)
        assertTrue(a.reason, !a.reason.contains("Citire greșită"))
    }

    @Test
    fun `an implausible approach is ignored when the approach is not counted`() {
        // The bad minute is on a leg that feeds nothing, so the ride still
        // gets a verdict — but the pickup rule keeps using its distance.
        val badApproach = offer(pickupKm = 20.0, pickupMin = 1)
        val a = OfferCalculator.analyze(
            badApproach,
            settings.copy(includePickup = false, maxPickupKmEnabled = true, maxPickupKm = 5.0)
        )
        assertEquals(Verdict.REJECT, a.verdict)
        assertTrue(a.reason, a.reason.contains("Preluare prea departe"))
    }

    @Test
    fun `an unreadable card never reaches the rules`() {
        val all = settings.copy(
            minCostPerKmEnabled = true,
            maxPickupKmEnabled = true,
            maxTripKmEnabled = true
        )
        val a = OfferCalculator.analyze(offer(tripKm = null, tripMin = null), all)
        assertEquals(Verdict.CAUTION, a.verdict)
        assertTrue(a.reason, a.reason.contains("incomplete"))
    }
}
