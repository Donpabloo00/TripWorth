package com.ridego.app.data

import android.content.Context
import android.content.SharedPreferences
import com.ridego.app.calculator.OverlayAnchor
import com.ridego.app.calculator.RideSettings
import com.ridego.app.parser.PlatformMode

/**
 * Settings live in SharedPreferences: a handful of scalars read on every
 * offer, so the synchronous API is a feature, not a compromise.
 */
class SettingsStore internal constructor(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    /**
     * SharedPreferences only stores Float, and widening one back to Double
     * exposes the binary rounding: 9.7f reads back as 9.699999809265137.
     * Two decimals is the precision these values are ever entered at.
     */
    private fun SharedPreferences.getMoney(key: String, fallback: Double): Double {
        val raw = getFloat(key, fallback.toFloat()).toDouble()
        return Math.round(raw * 100.0) / 100.0
    }

    /**
     * A rule's amount is only ever a positive quantity. A stored 0 or a
     * negative — from an older build's "0 means off" encoding, or a corrupt
     * write — would otherwise become an active limit that rejects everything.
     */
    private fun SharedPreferences.getPositiveMoney(key: String, fallback: Double): Double {
        val value = getMoney(key, fallback)
        return if (value > 0) value else fallback
    }

    fun load(): RideSettings {
        val d = RideSettings()
        return RideSettings(
            minimumRonPerHour = prefs.getMoney(KEY_RON_HOUR, d.minimumRonPerHour),
            minimumRonPerKm = prefs.getMoney(KEY_RON_KM, d.minimumRonPerKm),
            minimumRonPerMinute = prefs.getMoney(KEY_RON_MIN, d.minimumRonPerMinute),
            consumptionLPer100Km = prefs.getMoney(KEY_CONSUMPTION, d.consumptionLPer100Km),
            fuelPricePerLiter = prefs.getMoney(KEY_FUEL_PRICE, d.fuelPricePerLiter),
            extraCostPerKm = prefs.getMoney(KEY_EXTRA_COST, d.extraCostPerKm),
            autoRead = prefs.getBoolean(KEY_AUTO_READ, d.autoRead),
            soundOnNewOffer = prefs.getBoolean(KEY_SOUND, d.soundOnNewOffer),
            vibrate = prefs.getBoolean(KEY_VIBRATE, d.vibrate),
            overlayEnabled = prefs.getBoolean(KEY_OVERLAY, d.overlayEnabled),
            platformMode = runCatching {
                PlatformMode.valueOf(prefs.getString(KEY_PLATFORM, d.platformMode.name)!!)
            }.getOrDefault(d.platformMode),
            debugMode = prefs.getBoolean(KEY_DEBUG, d.debugMode),
            includePickup = prefs.getBoolean(KEY_INCLUDE_PICKUP, d.includePickup),
            ridesPerHour = prefs.getPositiveMoney(KEY_RIDES_PER_HOUR, d.ridesPerHour),
            minCostPerKmEnabled = prefs.getBoolean(KEY_MIN_COST_KM_ON, d.minCostPerKmEnabled),
            minCostPerKm = prefs.getPositiveMoney(KEY_MIN_COST_KM, d.minCostPerKm),
            maxPickupKmEnabled = prefs.getBoolean(KEY_MAX_PICKUP_KM_ON, d.maxPickupKmEnabled),
            maxPickupKm = prefs.getPositiveMoney(KEY_MAX_PICKUP_KM, d.maxPickupKm),
            maxTripKmEnabled = prefs.getBoolean(KEY_MAX_TRIP_KM_ON, d.maxTripKmEnabled),
            maxTripKm = prefs.getPositiveMoney(KEY_MAX_TRIP_KM, d.maxTripKm),
            minimumFareEnabled = prefs.getBoolean(KEY_MIN_FARE_ON, d.minimumFareEnabled),
            minimumFare = prefs.getPositiveMoney(KEY_MIN_FARE, d.minimumFare),
            overlayScalePercent = prefs.getInt(KEY_OV_SCALE, d.overlayScalePercent),
            overlayWidthPercent = prefs.getInt(KEY_OV_WIDTH, d.overlayWidthPercent),
            overlayMaxHeightPercent = prefs.getInt(KEY_OV_MAXH, d.overlayMaxHeightPercent),
            overlayAnchor = runCatching {
                OverlayAnchor.valueOf(prefs.getString(KEY_OV_ANCHOR, d.overlayAnchor.name)!!)
            }.getOrDefault(d.overlayAnchor),
            overlayMarginX = prefs.getInt(KEY_OV_MARGIN_X, d.overlayMarginX),
            overlayMarginY = prefs.getInt(KEY_OV_MARGIN_Y, d.overlayMarginY),
            overlayOpacityPercent = prefs.getInt(KEY_OV_OPACITY, d.overlayOpacityPercent),
            overlayDurationSeconds = prefs.getInt(KEY_OV_DURATION, d.overlayDurationSeconds),
            overlayDecisionButtons = prefs.getBoolean(KEY_OV_BUTTONS, d.overlayDecisionButtons),
            overlayX = prefs.getInt(KEY_OV_X, d.overlayX),
            overlayY = prefs.getInt(KEY_OV_Y, d.overlayY)
        )
    }

    fun save(settings: RideSettings) {
        prefs.edit()
            .putFloat(KEY_RON_HOUR, settings.minimumRonPerHour.toFloat())
            .putFloat(KEY_RON_KM, settings.minimumRonPerKm.toFloat())
            .putFloat(KEY_RON_MIN, settings.minimumRonPerMinute.toFloat())
            .putFloat(KEY_CONSUMPTION, settings.consumptionLPer100Km.toFloat())
            .putFloat(KEY_FUEL_PRICE, settings.fuelPricePerLiter.toFloat())
            .putFloat(KEY_EXTRA_COST, settings.extraCostPerKm.toFloat())
            .putBoolean(KEY_AUTO_READ, settings.autoRead)
            .putBoolean(KEY_SOUND, settings.soundOnNewOffer)
            .putBoolean(KEY_VIBRATE, settings.vibrate)
            .putBoolean(KEY_OVERLAY, settings.overlayEnabled)
            .putString(KEY_PLATFORM, settings.platformMode.name)
            .putBoolean(KEY_DEBUG, settings.debugMode)
            .putBoolean(KEY_INCLUDE_PICKUP, settings.includePickup)
            .putFloat(KEY_RIDES_PER_HOUR, settings.ridesPerHour.toFloat())
            .putBoolean(KEY_MIN_COST_KM_ON, settings.minCostPerKmEnabled)
            .putFloat(KEY_MIN_COST_KM, settings.minCostPerKm.toFloat())
            .putBoolean(KEY_MAX_PICKUP_KM_ON, settings.maxPickupKmEnabled)
            .putFloat(KEY_MAX_PICKUP_KM, settings.maxPickupKm.toFloat())
            .putBoolean(KEY_MAX_TRIP_KM_ON, settings.maxTripKmEnabled)
            .putFloat(KEY_MAX_TRIP_KM, settings.maxTripKm.toFloat())
            .putBoolean(KEY_MIN_FARE_ON, settings.minimumFareEnabled)
            .putFloat(KEY_MIN_FARE, settings.minimumFare.toFloat())
            .putInt(KEY_OV_SCALE, settings.overlayScalePercent)
            .putInt(KEY_OV_WIDTH, settings.overlayWidthPercent)
            .putInt(KEY_OV_MAXH, settings.overlayMaxHeightPercent)
            .putString(KEY_OV_ANCHOR, settings.overlayAnchor.name)
            .putInt(KEY_OV_MARGIN_X, settings.overlayMarginX)
            .putInt(KEY_OV_MARGIN_Y, settings.overlayMarginY)
            .putInt(KEY_OV_OPACITY, settings.overlayOpacityPercent)
            .putInt(KEY_OV_DURATION, settings.overlayDurationSeconds)
            .putBoolean(KEY_OV_BUTTONS, settings.overlayDecisionButtons)
            .putInt(KEY_OV_X, settings.overlayX)
            .putInt(KEY_OV_Y, settings.overlayY)
            .apply()
    }

    internal companion object {
        const val PREFS_NAME = "ridego_settings"

        const val KEY_RON_HOUR = "min_ron_hour"
        const val KEY_RON_KM = "min_ron_km"
        const val KEY_RON_MIN = "min_ron_minute"
        const val KEY_CONSUMPTION = "consumption"
        const val KEY_FUEL_PRICE = "fuel_price"
        const val KEY_EXTRA_COST = "extra_cost_km"
        const val KEY_AUTO_READ = "auto_read"
        const val KEY_SOUND = "sound"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_OVERLAY = "overlay"
        const val KEY_PLATFORM = "platform_mode"
        const val KEY_DEBUG = "debug_mode"
        const val KEY_INCLUDE_PICKUP = "include_pickup"
        // Replaces "utilization_percent", which measured the same idea in a
        // unit no driver could estimate. The old key stays on disk, unread.
        const val KEY_RIDES_PER_HOUR = "rides_per_hour"
        // Renamed from "max_pickup_km": the old key carried 0 to mean "off",
        // which under the new switch would read back as an active 0 km limit
        // that rejects every offer.
        const val KEY_MAX_PICKUP_KM = "max_pickup_km_v2"
        const val KEY_MAX_PICKUP_KM_ON = "max_pickup_km_on"
        const val KEY_MIN_COST_KM = "min_cost_per_km"
        const val KEY_MIN_COST_KM_ON = "min_cost_per_km_on"
        const val KEY_MAX_TRIP_KM = "max_trip_km"
        const val KEY_MAX_TRIP_KM_ON = "max_trip_km_on"
        // Renamed alongside the switch: the old key stored 0 for "no floor",
        // which under a checkbox would read back as an active 0 RON minimum.
        const val KEY_MIN_FARE = "minimum_fare_v2"
        const val KEY_MIN_FARE_ON = "minimum_fare_on"
        const val KEY_OV_SCALE = "overlay_scale"
        const val KEY_OV_WIDTH = "overlay_width"
        const val KEY_OV_MAXH = "overlay_max_height"
        const val KEY_OV_ANCHOR = "overlay_anchor"
        const val KEY_OV_MARGIN_X = "overlay_margin_x"
        const val KEY_OV_MARGIN_Y = "overlay_margin_y"
        const val KEY_OV_OPACITY = "overlay_opacity"
        const val KEY_OV_DURATION = "overlay_duration"
        const val KEY_OV_BUTTONS = "overlay_buttons"
        const val KEY_OV_X = "overlay_x"
        const val KEY_OV_Y = "overlay_y"
    }
}
