package com.ridego.app.data

import com.ridego.app.calculator.Verdict

/**
 * What the driver's own history says about the shift, as opposed to what the
 * settings assume about it.
 *
 * @param measuredRidesPerHour accepted rides divided by hours actually worked,
 * or null when there is not enough evidence to say.
 * @param acceptedCount how many rides the figure rests on.
 * @param activeHours the time those rides were spread across.
 * @param averageNetRonPerHour take-home rate of the accepted rides.
 * @param topRejectionReason the rule that turns work away most often.
 */
data class ShiftSummary(
    val measuredRidesPerHour: Double?,
    val acceptedCount: Int,
    val activeHours: Double,
    val averageNetRonPerHour: Double?,
    val topRejectionReason: String?,
    val rejectedCount: Int
)

/**
 * Turns the decision log into a measurement of the shift.
 *
 * The whole verdict now rests on `ridesPerHour`, a number the driver types in
 * as a guess. The history already holds timestamps and, wherever the banner
 * buttons were used, what the driver actually did — so the guess can be
 * checked against the record instead of being taken on faith.
 *
 * Pure and Android-free on purpose: this is arithmetic about money, and it
 * should be testable without a device.
 */
object ShiftStats {

    /**
     * A gap longer than this ends the shift.
     *
     * Without it, a lunch break or a night's sleep counts as working time and
     * dilutes the pace toward zero — a driver who worked two good hours on
     * Monday and two on Friday would be told they average 0.05 rides an hour.
     */
    const val SESSION_GAP_MINUTES = 45L

    /**
     * Below this the pace is not reported at all.
     *
     * Three rides in twenty minutes extrapolate to nine an hour, which is not
     * a measurement, it is an accident. A missing figure is honest; a
     * confident wrong one is the failure this app spends most of its effort
     * avoiding.
     */
    const val MIN_SAMPLE = 5

    fun summarise(entries: List<HistoryEntry>): ShiftSummary {
        val accepted = entries
            .filter { it.driverDecision == DriverDecision.ACCEPTED }
            .sortedBy { it.timestamp }

        val activeMillis = activeMillis(accepted)
        val activeHours = activeMillis / 3_600_000.0

        // Enough rides, spread over enough time to mean something.
        val pace = if (accepted.size >= MIN_SAMPLE && activeHours > 0.25) {
            accepted.size / activeHours
        } else {
            null
        }

        val netRates = accepted.mapNotNull { it.netRonPerHour }
        val rejected = entries.count { it.driverDecision == DriverDecision.REJECTED }

        return ShiftSummary(
            measuredRidesPerHour = pace,
            acceptedCount = accepted.size,
            activeHours = activeHours,
            averageNetRonPerHour = netRates.takeIf { it.isNotEmpty() }?.average(),
            topRejectionReason = topRejectionReason(entries),
            rejectedCount = rejected
        )
    }

    /**
     * Working time, counted per session.
     *
     * A single ride spans no time by itself, so each session contributes at
     * least one slot: the interval to the ride that followed it, or — for the
     * last ride of a session — the session's own average interval. Otherwise a
     * session's final ride would be free, and the pace would read high.
     */
    private fun activeMillis(accepted: List<HistoryEntry>): Long {
        if (accepted.size < 2) return 0L
        val gapLimit = SESSION_GAP_MINUTES * 60_000L

        var total = 0L
        var sessionStart = accepted.first().timestamp
        var previous = accepted.first().timestamp
        var sessionRides = 1

        fun closeSession() {
            val span = previous - sessionStart
            // Add one average interval for the ride that ended the session.
            total += if (sessionRides > 1) span + span / (sessionRides - 1) else 0L
        }

        accepted.drop(1).forEach { entry ->
            if (entry.timestamp - previous > gapLimit) {
                closeSession()
                sessionStart = entry.timestamp
                sessionRides = 1
            } else {
                sessionRides++
            }
            previous = entry.timestamp
        }
        closeSession()
        return total
    }

    /**
     * The rule that turns away the most work.
     *
     * Counted from the name the calculator recorded, not guessed from the
     * numbers: a rejection knows which rule produced it, and inferring it
     * afterwards would be a different, worse answer wearing the same label.
     */
    private fun topRejectionReason(entries: List<HistoryEntry>): String? =
        entries.filter { it.verdict == Verdict.REJECT }
            .mapNotNull { it.rejectedBy }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
}
