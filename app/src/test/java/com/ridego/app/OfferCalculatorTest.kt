package com.ridego.app

import com.ridego.app.calculator.OfferCalculator
import com.ridego.app.calculator.RideSettings
import com.ridego.app.calculator.Verdict
import com.ridego.app.parser.Platform
import com.ridego.app.parser.RideOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class OfferCalculatorTest {

    private val settings = RideSettings()

    private fun offer(
        price: Double? = 17.20,
        pickupKm: Double? = 2.9,
        pickupMin: Int? = 5,
        tripKm: Double? = 5.2,
        tripMin: Int? = 12
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
    fun `computes the spec example end to end`() {
        val a = OfferCalculator.analyze(offer(), settings)
        assertEquals(8.1, a.totalKm!!, 0.001)
        assertEquals(17, a.totalMinutes)
        assertEquals(2.12, a.ronPerKm!!, 0.01)
        assertEquals(60.71, a.ronPerHour!!, 0.01)
        assertEquals(5.50, a.fuelCost!!, 0.01)
        assertEquals(11.70, a.estimatedProfit!!, 0.01)
        assertEquals(Verdict.REJECT, a.verdict)
    }

    @Test
    fun `accepts when both thresholds are cleared comfortably`() {
        val a = OfferCalculator.analyze(offer(price = 60.0), settings)
        assertEquals(Verdict.ACCEPT, a.verdict)
    }

    @Test
    fun `the per-km threshold no longer decides the verdict`() {
        // Real Bucharest fares sit near 2 RON/km, so any per-km bar worth
        // setting rejected everything and the hourly goal never applied.
        val strictPerKm = settings.copy(minimumRonPerKm = 99.0)
        val a = OfferCalculator.analyze(offer(price = 60.0), strictPerKm)
        assertEquals(Verdict.ACCEPT, a.verdict)
    }

    @Test
    fun `rejects when only the per-hour threshold fails`() {
        // 4 RON/km met, but a slow ride drags RON/h under the threshold.
        val a = OfferCalculator.analyze(
            offer(price = 33.0, pickupKm = 2.9, pickupMin = 15, tripKm = 5.2, tripMin = 30),
            settings
        )
        assertEquals(Verdict.REJECT, a.verdict)
    }

    @Test
    fun `flags caution when data is incomplete`() {
        val a = OfferCalculator.analyze(offer(tripKm = null, tripMin = null, price = null), settings)
        assertEquals(Verdict.CAUTION, a.verdict)
        assertNull(a.ronPerKm)
    }

    @Test
    fun `flags caution when the offer sits on the threshold`() {
        // 8.1 km burns 5.50 RON of fuel; 35.50 over 30 min leaves almost
        // exactly the 60 RON/h target, which is too close to call a win.
        val a = OfferCalculator.analyze(
            offer(price = 35.50, pickupMin = 0, tripMin = 30),
            settings
        )
        assertEquals(Verdict.CAUTION, a.verdict)
    }

    @Test
    fun `the verdict runs on take-home, not gross`() {
        val a = OfferCalculator.analyze(offer(), settings)
        // 17.20 gross over 17 min reads 60.71 RON/h, which would clear a
        // 60 bar — but fuel leaves only 41 RON/h in hand.
        assertEquals(60.71, a.ronPerHour!!, 0.01)
        assertEquals(41.29, a.netRonPerHour!!, 0.01)
        assertEquals(Verdict.REJECT, a.verdict)
    }

    @Test
    fun `settings changes move the verdict`() {
        val relaxed = settings.copy(minimumRonPerHour = 35.0)
        assertEquals(Verdict.ACCEPT, OfferCalculator.analyze(offer(), relaxed).verdict)
    }

    @Test
    fun `excluding the pickup leg counts only the paid ride`() {
        val withoutPickup = settings.copy(includePickup = false)
        val a = OfferCalculator.analyze(offer(), withoutPickup)
        // Trip alone: 5.2 km over 12 min, not 8.1 km over 17.
        assertEquals(5.2, a.totalKm!!, 0.001)
        assertEquals(12, a.totalMinutes)
    }

    @Test
    fun `excluding the pickup leg flatters the hourly figure`() {
        val included = OfferCalculator.analyze(offer(), settings)
        val excluded = OfferCalculator.analyze(offer(), settings.copy(includePickup = false))
        // Same fare, same driving — only the accounting changed.
        assertEquals(41.29, included.netRonPerHour!!, 0.01)
        assertEquals(68.34, excluded.netRonPerHour!!, 0.01)
        assertEquals(Verdict.REJECT, included.verdict)
        assertEquals(Verdict.ACCEPT, excluded.verdict)
    }

    @Test
    fun `a card with no pickup leg becomes usable once it is excluded`() {
        val partial = offer(pickupKm = null, pickupMin = null)
        assertFalse(partial.isReliableFor(includePickup = true))
        assertTrue(partial.isReliableFor(includePickup = false))
    }

    @Test
    fun `occupancy raises the bar a single ride has to clear`() {
        // 60 RON/h across a shift that is only 70% on-trip needs 86 per ride.
        val at70 = settings.copy(utilizationPercent = 70)
        assertEquals(85.71, OfferCalculator.effectiveTarget(at70), 0.01)
        assertEquals(60.0, OfferCalculator.effectiveTarget(settings), 0.01)
    }

    @Test
    fun `a ride that clears the goal can still fail on occupancy`() {
        // 65 RON/h net clears a 60 goal, but not the 86 the shift needs.
        // Same 17 minutes overall, split across both legs: a 2.9 km approach
        // in zero minutes is not a journey the plausibility check believes.
        val ride = offer(price = 24.0, pickupMin = 3, tripMin = 14)
        val full = OfferCalculator.analyze(ride, settings)
        val partial = OfferCalculator.analyze(ride, settings.copy(utilizationPercent = 70))
        assertEquals(Verdict.ACCEPT, full.verdict)
        assertEquals(Verdict.REJECT, partial.verdict)
    }

    @Test
    fun `a fare below the floor is rejected whatever the ratios say`() {
        // 12 RON over 2 minutes is a superb hourly rate and a terrible job.
        val quick = offer(price = 12.0, pickupKm = 0.1, pickupMin = 1, tripKm = 0.5, tripMin = 1)
        assertEquals(Verdict.ACCEPT, OfferCalculator.analyze(quick, settings).verdict)

        val withFloor = settings.copy(minimumFareEnabled = true, minimumFare = 15.0)
        val a = OfferCalculator.analyze(quick, withFloor)
        assertEquals(Verdict.REJECT, a.verdict)
        assertTrue(a.reason.contains("sub minimul"))
    }

    @Test
    fun `a long approach is rejected whatever it pays`() {
        val longPickup = offer(price = 200.0, pickupKm = 20.0, pickupMin = 40)
        assertEquals(Verdict.ACCEPT, OfferCalculator.analyze(longPickup, settings).verdict)

        val capped = settings.copy(maxPickupKmEnabled = true, maxPickupKm = 5.0)
        val a = OfferCalculator.analyze(longPickup, capped)
        assertEquals(Verdict.REJECT, a.verdict)
        assertTrue(a.reason, a.reason.contains("Preluare prea departe"))
    }

    @Test
    fun `the pickup limit is off while its checkbox is off`() {
        // The stored distance is deliberately one this offer breaks: an
        // unchecked rule must not reach the verdict at all.
        val longPickup = offer(price = 200.0, pickupKm = 20.0, pickupMin = 40)
        val a = OfferCalculator.analyze(
            longPickup,
            settings.copy(maxPickupKmEnabled = false, maxPickupKm = 5.0)
        )
        assertEquals(Verdict.ACCEPT, a.verdict)
    }

    @Test
    fun `the threshold reads the same everywhere in one sentence`() {
        // 50 / 0.95 = 52.63, once printed truncated as 52 and once rounded
        // as 53 — the same bar, two numbers, in a single line of text.
        val awkward = settings.copy(minimumRonPerHour = 50.0, utilizationPercent = 95)
        val a = OfferCalculator.analyze(offer(price = 60.0), awkward)

        val numbers = Regex("""\d+""").findAll(a.reason).map { it.value }.toList()
        val threshold = "53"
        assertTrue(a.reason, a.reason.contains("prag $threshold"))
        assertTrue(a.reason, !a.reason.contains("prag 52"))
        assertTrue(a.reason + numbers, numbers.contains(threshold))
    }

    @Test
    fun `extra cost per km reduces estimated profit`() {
        val withExtra = settings.copy(extraCostPerKm = 1.0)
        val a = OfferCalculator.analyze(offer(), withExtra)
        assertEquals(5.50 + 8.1, a.fuelCost!!, 0.05)
    }
}
