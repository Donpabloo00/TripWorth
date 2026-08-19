package com.ridego.app

import com.ridego.app.calculator.OfferCalculator
import com.ridego.app.calculator.Verdict
import com.ridego.app.data.DriverDecision
import com.ridego.app.data.HistoryEntry
import com.ridego.app.data.ShiftStats
import com.ridego.app.parser.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Measuring the shift from the driver's own record.
 *
 * The pace feeds every hourly figure and every verdict, and until now it was
 * a number typed in by hand. These fix the arithmetic that checks it.
 */
class ShiftStatsTest {

    private val start = 1_700_000_000_000L
    private val minute = 60_000L

    private fun entry(
        atMinute: Long,
        decision: DriverDecision? = DriverDecision.ACCEPTED,
        net: Double? = 55.0,
        verdict: Verdict = Verdict.ACCEPT,
        rejectedBy: String? = null
    ) = HistoryEntry(
        timestamp = start + atMinute * minute,
        platform = Platform.UBER,
        price = 30.0,
        totalKm = 8.0,
        totalMinutes = 15,
        ronPerKm = 3.75,
        ronPerHour = 75.0,
        netRonPerHour = net,
        verdict = verdict,
        rejectedBy = rejectedBy,
        driverDecision = decision
    )

    @Test
    fun `a steady shift measures the pace it actually ran at`() {
        // Six rides, one every 20 minutes: three an hour.
        val shift = ShiftStats.summarise((0..5).map { entry(it * 20L) })
        assertEquals(6, shift.acceptedCount)
        assertEquals(3.0, shift.measuredRidesPerHour!!, 0.05)
    }

    @Test
    fun `a break does not count as time worked`() {
        // Three rides, a two-hour gap, then three more. Both halves ran at
        // three an hour; counting the gap would report barely more than one.
        val morning = (0..2).map { entry(it * 20L) }
        val evening = (0..2).map { entry(180L + it * 20L) }
        val shift = ShiftStats.summarise(morning + evening)

        assertEquals(6, shift.acceptedCount)
        assertEquals(3.0, shift.measuredRidesPerHour!!, 0.05)
        assertTrue(shift.activeHours.toString(), shift.activeHours < 2.5)
    }

    @Test
    fun `too few rides yield no figure at all`() {
        // Two rides ten minutes apart extrapolate to six an hour. That is an
        // accident, not a measurement, so nothing is reported.
        val shift = ShiftStats.summarise(listOf(entry(0), entry(10)))
        assertNull(shift.measuredRidesPerHour)
        assertEquals(2, shift.acceptedCount)
    }

    @Test
    fun `rides the driver never marked are not counted`() {
        val unmarked = (0..5).map { entry(it * 20L, decision = null) }
        val shift = ShiftStats.summarise(unmarked)
        assertEquals(0, shift.acceptedCount)
        assertNull(shift.measuredRidesPerHour)
    }

    @Test
    fun `rejections are counted but never treated as work done`() {
        val accepted = (0..5).map { entry(it * 20L) }
        val refused = listOf(
            entry(5, decision = DriverDecision.REJECTED, verdict = Verdict.REJECT),
            entry(25, decision = DriverDecision.REJECTED, verdict = Verdict.REJECT)
        )
        val shift = ShiftStats.summarise(accepted + refused)
        assertEquals(6, shift.acceptedCount)
        assertEquals(2, shift.rejectedCount)
        assertEquals(3.0, shift.measuredRidesPerHour!!, 0.05)
    }

    @Test
    fun `the average take-home rate comes from the accepted rides only`() {
        val shift = ShiftStats.summarise(
            listOf(
                entry(0, net = 40.0),
                entry(20, net = 60.0),
                entry(40, net = 50.0),
                entry(60, net = 50.0),
                entry(80, net = 50.0),
                entry(100, decision = DriverDecision.REJECTED, net = 500.0)
            )
        )
        assertEquals(50.0, shift.averageNetRonPerHour!!, 0.01)
    }

    @Test
    fun `entries written before net was recorded do not break the average`() {
        val shift = ShiftStats.summarise(
            (0..5).map { entry(it * 20L, net = if (it < 3) null else 60.0) }
        )
        assertEquals(6, shift.acceptedCount)
        assertEquals(60.0, shift.averageNetRonPerHour!!, 0.01)
    }

    @Test
    fun `the rule that turns away the most work is named`() {
        val shift = ShiftStats.summarise(
            listOf(
                entry(0, verdict = Verdict.REJECT, rejectedBy = OfferCalculator.RULE_PICKUP),
                entry(5, verdict = Verdict.REJECT, rejectedBy = OfferCalculator.RULE_PICKUP),
                entry(9, verdict = Verdict.REJECT, rejectedBy = OfferCalculator.RULE_MIN_FARE)
            )
        )
        assertEquals(OfferCalculator.RULE_PICKUP, shift.topRejectionReason)
    }

    @Test
    fun `an empty history reports nothing rather than zero`() {
        val shift = ShiftStats.summarise(emptyList())
        assertNull(shift.measuredRidesPerHour)
        assertNull(shift.averageNetRonPerHour)
        assertNull(shift.topRejectionReason)
        assertEquals(0, shift.acceptedCount)
    }
}
