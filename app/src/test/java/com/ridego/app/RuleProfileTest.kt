package com.ridego.app

import com.ridego.app.calculator.OfferCalculator
import com.ridego.app.calculator.RideSettings
import com.ridego.app.calculator.RuleProfile
import com.ridego.app.calculator.Verdict
import com.ridego.app.parser.Platform
import com.ridego.app.parser.RideOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleProfileTest {

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
    fun `applying a profile turns all four rules on`() {
        RuleProfile.entries.forEach { profile ->
            val applied = profile.applyTo(RideSettings())
            assertTrue(profile.label, applied.minimumFareEnabled)
            assertTrue(profile.label, applied.minCostPerKmEnabled)
            assertTrue(profile.label, applied.maxPickupKmEnabled)
            assertTrue(profile.label, applied.maxTripKmEnabled)
        }
    }

    @Test
    fun `a profile recognises the settings it just wrote`() {
        RuleProfile.entries.forEach { profile ->
            assertTrue(profile.label, profile.matches(profile.applyTo(RideSettings())))
        }
    }

    @Test
    fun `no two profiles claim the same settings`() {
        RuleProfile.entries.forEach { profile ->
            val applied = profile.applyTo(RideSettings())
            val claiming = RuleProfile.entries.filter { it.matches(applied) }
            assertEquals(applied.toString(), listOf(profile), claiming)
        }
    }

    @Test
    fun `defaults match no profile, so nothing is shown as chosen`() {
        RuleProfile.entries.forEach { profile ->
            assertFalse(profile.label, profile.matches(RideSettings()))
        }
    }

    @Test
    fun `every profile sets the hourly goal it advertises`() {
        RuleProfile.entries.forEach { profile ->
            val applied = profile.applyTo(RideSettings())
            assertEquals(profile.label, profile.ronPerHour, applied.minimumRonPerHour, 0.001)
            // The button has to name the money; the adjective alone does not.
            assertTrue(profile.buttonLabel, profile.buttonLabel.contains("/oră"))
        }
    }

    @Test
    fun `the ladder is ordered and each rung is stricter than the last`() {
        // Declaration order is what the settings row renders, so a profile
        // out of sequence would read as an arbitrary jumble of names.
        val ladder = RuleProfile.entries
        ladder.zipWithNext().forEach { (lower, higher) ->
            val a = lower.applyTo(RideSettings())
            val b = higher.applyTo(RideSettings())
            val where = "${lower.label} -> ${higher.label}"
            assertTrue(where, b.minimumRonPerHour > a.minimumRonPerHour)
            assertTrue(where, b.minimumFare > a.minimumFare)
            assertTrue(where, b.minCostPerKm > a.minCostPerKm)
            assertTrue(where, b.maxPickupKm < a.maxPickupKm)
            assertTrue(where, b.maxTripKm <= a.maxTripKm)
        }
    }

    @Test
    fun `every profile still accepts an ordinary good offer`() {
        // The point of a starting point is that it leaves real work passing.
        // 65 RON for a 24 km ride at 2.7 RON/km, two kilometres away, is a
        // plainly decent job by anyone's standard.
        // Two kilometres away and paying well over even the strictest bar.
        val good = offer(price = 120.0, pickupKm = 2.0, tripKm = 18.0, tripMin = 20)
        RuleProfile.entries.forEach { profile ->
            val a = OfferCalculator.analyze(good, profile.applyTo(RideSettings()))
            assertEquals(profile.label + ": " + a.reason, Verdict.ACCEPT, a.verdict)
        }
    }

    @Test
    fun `the strictest profile rejects what the most permissive allows`() {
        val marginal = offer(price = 18.0, pickupKm = 7.0, tripKm = 8.0, tripMin = 12)
        val strict = OfferCalculator.analyze(marginal, RuleProfile.MAXIMUM.applyTo(RideSettings()))
        val open = OfferCalculator.analyze(marginal, RuleProfile.CAUTIOUS.applyTo(RideSettings()))

        assertEquals(Verdict.REJECT, strict.verdict)
        assertTrue(open.reason, !open.reason.contains("Preluare prea departe"))
    }

    @Test
    fun `the minimum fare rule is off while its checkbox is off`() {
        val cheap = offer(price = 12.0, tripKm = 2.0, tripMin = 4)
        val off = RideSettings(minimumFareEnabled = false, minimumFare = 30.0)
        val a = OfferCalculator.analyze(cheap, off)
        assertTrue(a.reason, !a.reason.contains("sub minimul"))
    }

    @Test
    fun `the minimum fare rule bites once its checkbox is on`() {
        val cheap = offer(price = 12.0, tripKm = 2.0, tripMin = 4)
        val on = RideSettings(minimumFareEnabled = true, minimumFare = 30.0)
        val a = OfferCalculator.analyze(cheap, on)
        assertEquals(Verdict.REJECT, a.verdict)
        assertTrue(a.reason, a.reason.contains("sub minimul de 30 RON"))
    }
}
