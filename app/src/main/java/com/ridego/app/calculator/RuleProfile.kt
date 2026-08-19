package com.ridego.app.calculator

/**
 * Complete starting points, organised by what the driver wants to earn.
 *
 * The hourly goal is the one number a driver already has an opinion about, so
 * it leads: everything else follows from it. A driver aiming at 40 RON/h and
 * one aiming at 100 are not running the same filters, and pairing a high goal
 * with permissive rules produces the worst outcome of all — the app accepts
 * work that cannot reach the goal it was told to hit.
 *
 * Calibrated against real Bucharest UberX fares (2-4 RON/km measured on the
 * ride's own distance), so even the strictest profile leaves real offers
 * passing. They are a place to start, not an answer.
 */
enum class RuleProfile(
    val label: String,
    val ronPerHour: Double,
    val summary: String,
    private val minimumFare: Double,
    private val minCostPerKm: Double,
    private val maxPickupKm: Double,
    private val maxTripKm: Double
) {
    CAUTIOUS(
        label = "Prudent",
        ronPerHour = 40.0,
        summary = "Prudent — 40 RON/oră. Aștepți puțin, accepți aproape tot. Bun când " +
            "începi, când e liniște sau când vrei să nu stai degeaba.",
        minimumFare = 12.0,
        minCostPerKm = 1.60,
        maxPickupKm = 8.0,
        maxTripKm = 60.0
    ),
    RELAXED(
        label = "Relaxat",
        ronPerHour = 50.0,
        summary = "Relaxat — 50 RON/oră. Refuzi doar ofertele clar slabe. Potrivit în " +
            "afara orelor de vârf.",
        minimumFare = 15.0,
        minCostPerKm = 1.80,
        maxPickupKm = 6.0,
        maxTripKm = 50.0
    ),
    BALANCED(
        label = "Echilibrat",
        ronPerHour = 60.0,
        summary = "Echilibrat — 60 RON/oră. Taie ofertele proaste, lasă restul să treacă. " +
            "Punctul de plecare recomandat dacă nu știi de unde să începi.",
        minimumFare = 20.0,
        minCostPerKm = 2.00,
        maxPickupKm = 5.0,
        maxTripKm = 40.0
    ),
    AMBITIOUS(
        label = "Ambițios",
        ronPerHour = 70.0,
        summary = "Ambițios — 70 RON/oră. Începi să refuzi curse mediocre. Are sens în " +
            "orele de vârf, când vin oferte des.",
        minimumFare = 25.0,
        minCostPerKm = 2.30,
        maxPickupKm = 4.0,
        maxTripKm = 30.0
    ),
    SELECTIVE(
        label = "Selectiv",
        ronPerHour = 85.0,
        summary = "Selectiv — 85 RON/oră. Aștepți curse clar bune și lași multe să treacă. " +
            "Doar în vârf de cerere sau cu tarife majorate.",
        minimumFare = 30.0,
        minCostPerKm = 2.60,
        maxPickupKm = 3.0,
        maxTripKm = 25.0
    ),
    MAXIMUM(
        label = "Maxim",
        ronPerHour = 100.0,
        summary = "Maxim — 100 RON/oră. Foarte strict: vei refuza majoritatea ofertelor. " +
            "Merită doar dacă cererea e mare și îți permiți să aștepți.",
        minimumFare = 35.0,
        minCostPerKm = 3.00,
        maxPickupKm = 2.5,
        maxTripKm = 20.0
    );

    /** Shown on the button: the adjective alone says nothing about the money. */
    val buttonLabel: String get() = "$label\n${ronPerHour.toInt()}/oră"

    /** Sets the goal and turns all four rules on with amounts that match it. */
    fun applyTo(settings: RideSettings): RideSettings = settings.copy(
        minimumRonPerHour = ronPerHour,
        minimumFareEnabled = true,
        minimumFare = minimumFare,
        minCostPerKmEnabled = true,
        minCostPerKm = minCostPerKm,
        maxPickupKmEnabled = true,
        maxPickupKm = maxPickupKm,
        maxTripKmEnabled = true,
        maxTripKm = maxTripKm
    )

    /** Whether the settings are exactly this profile, so it can be shown as chosen. */
    fun matches(settings: RideSettings): Boolean =
        settings.minimumFareEnabled && settings.minCostPerKmEnabled &&
            settings.maxPickupKmEnabled && settings.maxTripKmEnabled &&
            same(settings.minimumRonPerHour, ronPerHour) &&
            same(settings.minimumFare, minimumFare) &&
            same(settings.minCostPerKm, minCostPerKm) &&
            same(settings.maxPickupKm, maxPickupKm) &&
            same(settings.maxTripKm, maxTripKm)

    private fun same(a: Double, b: Double): Boolean = Math.abs(a - b) < 0.005
}
