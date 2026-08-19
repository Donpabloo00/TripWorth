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
        // 17 minutes of driving, but a 24-minute slot at 2.5 rides an hour.
        assertEquals(43.0, a.ronPerHour!!, 0.01)
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
        // 26 RON in a 24-minute slot grosses 65 RON/h and would clear the
        // 60 bar — but fuel leaves only 51 in hand.
        val a = OfferCalculator.analyze(offer(price = 26.0), settings)
        assertEquals(65.0, a.ronPerHour!!, 0.01)
        assertEquals(51.25, a.netRonPerHour!!, 0.01)
        assertEquals(Verdict.REJECT, a.verdict)
    }

    @Test
    fun `settings changes move the verdict`() {
        val relaxed = settings.copy(minimumRonPerHour = 25.0)
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
        // A goal the two sides of the accounting fall either side of, so the
        // difference shows up as a changed verdict and not just a nicer number.
        val goal = settings.copy(minimumRonPerHour = 30.0)
        val included = OfferCalculator.analyze(offer(), goal)
        val excluded = OfferCalculator.analyze(offer(), goal.copy(includePickup = false))
        // Same fare, same driving — only the accounting changed. Dropping the
        // approach removes its fuel, and nothing else.
        assertEquals(29.25, included.netRonPerHour!!, 0.01)
        assertEquals(34.17, excluded.netRonPerHour!!, 0.01)
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
    fun `the real 16 lei ride no longer claims 120 RON an hour`() {
        // Captured from the driver's phone: 16.91 RON, 4.2 km, 7 minutes,
        // with the approach excluded. The old model extrapolated the seven
        // minutes across a whole hour and printed 120 RON/oră NET — a figure
        // that assumed 8.5 such jobs chained without a pause.
        val real = offer(price = 16.91, tripKm = 4.2, tripMin = 7)
        val withoutPickup = settings.copy(includePickup = false, fuelPricePerLiter = 9.90)
        val a = OfferCalculator.analyze(real, withoutPickup)

        // 14.00 RON profit across the 24-minute slot 2.5 rides an hour implies.
        assertEquals(35.0, a.netRonPerHour!!, 0.2)
        assertEquals(42.3, a.ronPerHour!!, 0.2)
    }

    @Test
    fun `the threshold is simply the goal, with no hidden correction`() {
        assertEquals(60.0, OfferCalculator.effectiveTarget(settings), 0.01)
        assertEquals(
            90.0,
            OfferCalculator.effectiveTarget(settings.copy(minimumRonPerHour = 90.0)),
            0.01
        )
    }

    @Test
    fun `a short ride occupies the whole slot its pace implies`() {
        // 8 minutes of driving, but at 2.5 rides an hour the shift only fits
        // one such job every 24 minutes — the wait is part of its cost.
        assertEquals(24.0, OfferCalculator.slotMinutes(8, settings), 0.01)
        // Six rides an hour leaves a 10-minute slot, shorter than the ride,
        // so the ride's own duration governs.
        assertEquals(
            10.0,
            OfferCalculator.slotMinutes(8, settings.copy(ridesPerHour = 6.0)),
            0.01
        )
    }

    @Test
    fun `a long ride is never charged for idle time it cannot have`() {
        // 45 minutes does not fit into an hour 2.5 times, so there is no wait
        // to add: the slot is the ride itself.
        assertEquals(45.0, OfferCalculator.slotMinutes(45, settings), 0.01)
    }

    @Test
    fun `pace decides the verdict, not just the number shown`() {
        // 24 RON over 17 minutes: a fine rate in isolation, thin once the
        // shift only turns over 1.5 such jobs an hour.
        val ride = offer(price = 24.0, pickupMin = 3, tripMin = 14)
        val brisk = OfferCalculator.analyze(ride, settings.copy(ridesPerHour = 4.0))
        val slow = OfferCalculator.analyze(ride, settings.copy(ridesPerHour = 1.5))

        assertEquals(Verdict.ACCEPT, brisk.verdict)
        assertEquals(Verdict.REJECT, slow.verdict)
        // The same ride, two pictures of the hour it belongs to.
        assertTrue(brisk.netRonPerHour!! > slow.netRonPerHour!!)
    }

    @Test
    fun `a fare below the floor is rejected whatever the ratios say`() {
        // A 2-minute, 12 RON job. Against a modest 25 RON/h goal the ratios
        // pass it; the floor is what says a slot is worth more than 12 lei.
        val modest = settings.copy(minimumRonPerHour = 25.0)
        val quick = offer(price = 12.0, pickupKm = 0.1, pickupMin = 1, tripKm = 0.5, tripMin = 1)
        assertEquals(Verdict.ACCEPT, OfferCalculator.analyze(quick, modest).verdict)

        val withFloor = modest.copy(minimumFareEnabled = true, minimumFare = 15.0)
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
    fun `the verdict states the pace that produced its figure`() {
        // The hourly number is meaningless without it: the same fare reads
        // very differently at 1.5 rides an hour and at 4.
        val a = OfferCalculator.analyze(offer(price = 60.0), settings)
        assertTrue(a.reason, a.reason.contains("curse/oră"))
        assertTrue(a.reason, !a.reason.contains("ocupare"))
    }

    @Test
    fun `extra cost per km reduces estimated profit`() {
        val withExtra = settings.copy(extraCostPerKm = 1.0)
        val a = OfferCalculator.analyze(offer(), withExtra)
        assertEquals(5.50 + 8.1, a.fuelCost!!, 0.05)
    }
}
