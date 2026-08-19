package com.ridego.app.calculator

import com.ridego.app.parser.PlatformMode

/** User-tunable economics. Defaults match the Bucharest baseline in the spec. */
data class RideSettings(
    val platformMode: PlatformMode = PlatformMode.AUTO,
    val debugMode: Boolean = false,
    val minimumRonPerHour: Double = 60.0,
    /**
     * Whether the drive to the rider counts toward the totals.
     *
     * On (the default) measures the whole job: the approach burns real fuel
     * and real minutes. Off measures the paid ride alone, which reads higher
     * — useful for comparing fares, misleading as a picture of the hour.
     */
    val includePickup: Boolean = true,

    /**
     * How many rides the driver completes in an average hour.
     *
     * This is what makes the hourly figure real. Judging a ride on its own
     * duration alone answers "what would an hour of nothing but this ride
     * pay" — for a 7-minute job that came out as 120 RON/oră, which assumes
     * 8.5 of them chained without a pause. At 2.5 rides an hour the same job
     * really occupies 24 minutes of the shift, and pays 35.
     *
     * Chosen over a percentage of "utilisation" because a driver knows this
     * number from experience and cannot estimate the other one at all.
     */
    val ridesPerHour: Double = 2.5,

    // --- acceptance criteria (each one independently switchable) ---------
    //
    // These four are checkbox rules: the flag alone decides whether the rule
    // is consulted. A value left over from a rule the driver has since turned
    // off must never leak back into a verdict, which is why the amount and the
    // switch are stored separately rather than overloading 0 to mean "off".

    /**
     * Rule 1 — absolute floor per ride.
     *
     * Every job carries fixed overhead the per-hour maths misses: waiting,
     * the rider getting in, the conversation. A 12 RON fare can post a superb
     * hourly rate and still be a bad use of the slot.
     */
    val minimumFareEnabled: Boolean = false,
    val minimumFare: Double = 30.0,

    /**
     * Rule 2 — floor on what the paid ride itself must earn per kilometre.
     *
     * Deliberately measured against [RideOffer.tripDistanceKm] only: this is
     * the driver's cost of covering the fare's own distance, and folding the
     * approach leg in would move the bar for reasons the fare never sees.
     */
    val minCostPerKmEnabled: Boolean = false,
    val minCostPerKm: Double = 2.50,

    /**
     * Rule 3 — how far the driver is willing to drive to reach the rider.
     *
     * The single limit on the approach leg. An earlier minutes-based cap sat
     * alongside it; two limits on one leg meant an offer could be refused by
     * whichever the driver had forgotten about, so distance — the one the
     * driver actually judges by — is now the only one.
     */
    val maxPickupKmEnabled: Boolean = false,
    val maxPickupKm: Double = 5.0,

    /** Rule 4 — longest paid ride the driver wants to take. */
    val maxTripKmEnabled: Boolean = false,
    val maxTripKm: Double = 30.0,

    val minimumRonPerKm: Double = 3.90,
    val minimumRonPerMinute: Double = 2.00,
    val consumptionLPer100Km: Double = 7.0,
    val fuelPricePerLiter: Double = 9.70,
    val extraCostPerKm: Double = 0.0,
    val autoRead: Boolean = true,
    val soundOnNewOffer: Boolean = true,
    val vibrate: Boolean = true,
    // On by default: while Uber is in front, the overlay is the only surface
    // that can actually show the driver a verdict.
    val overlayEnabled: Boolean = true,

    /** Text and spacing inside the banner, as a percentage of the default. */
    val overlayScalePercent: Int = 100,

    /**
     * Banner width, as a percentage of the screen.
     *
     * Separate from [overlayScalePercent] because the two solve different
     * problems: scale is about whether the driver can read it at a glance,
     * width is about how much of the Uber card underneath stays visible.
     */
    val overlayWidthPercent: Int = 92,

    /**
     * Vertical room the banner may take, as a percentage of screen height.
     * Anything taller scrolls inside, so a long list of rejection reasons can
     * never push the buttons off the bottom of the screen.
     */
    val overlayMaxHeightPercent: Int = 80,

    /** Corner or edge the banner snaps to. */
    val overlayAnchor: OverlayAnchor = OverlayAnchor.TOP_CENTER,

    /** Gap from the anchored edges, in dp. Nudges a preset off the edge. */
    val overlayMarginX: Int = 8,
    val overlayMarginY: Int = 40,

    /** Background opacity. Lower lets more of the offer card show through. */
    val overlayOpacityPercent: Int = 96,

    /**
     * How long the banner stays up. Capture pauses while it is visible — the
     * banner would otherwise be read back as if it were the offer — so a
     * longer banner means a longer blind spot.
     */
    val overlayDurationSeconds: Int = 15,

    /** Whether the banner offers buttons to log the driver's own decision. */
    val overlayDecisionButtons: Boolean = true,

    /** Last dragged position, in pixels. -1 means "use the default spot". */
    val overlayX: Int = -1,
    val overlayY: Int = -1
)
