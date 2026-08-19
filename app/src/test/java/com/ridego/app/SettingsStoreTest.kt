package com.ridego.app

import android.content.SharedPreferences
import com.ridego.app.calculator.OverlayAnchor
import com.ridego.app.calculator.RideSettings
import com.ridego.app.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trips the acceptance rules through the store.
 *
 * Backed by an in-memory SharedPreferences rather than Robolectric: the store
 * touches nothing else from the framework, and the fake reproduces the one
 * behaviour that actually matters here — values narrowed to Float on the way
 * in, which is where 2.50 could come back as 2.4999999.
 */
class SettingsStoreTest {

    private class FakePrefs : SharedPreferences {
        val values = mutableMapOf<String, Any?>()

        private inner class FakeEditor : SharedPreferences.Editor {
            private val staged = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()

            override fun putString(key: String, value: String?) = apply { staged[key] = value }
            override fun putStringSet(key: String, value: MutableSet<String>?) =
                apply { staged[key] = value }

            override fun putInt(key: String, value: Int) = apply { staged[key] = value }
            override fun putLong(key: String, value: Long) = apply { staged[key] = value }
            override fun putFloat(key: String, value: Float) = apply { staged[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { staged[key] = value }
            override fun remove(key: String) = apply { removed += key }
            override fun clear() = apply { removed += values.keys }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                removed.forEach { values.remove(it) }
                values.putAll(staged)
            }
        }

        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String, def: String?) = values[key] as? String ?: def
        override fun getStringSet(key: String, def: MutableSet<String>?) =
            @Suppress("UNCHECKED_CAST") (values[key] as? MutableSet<String> ?: def)

        override fun getInt(key: String, def: Int) = values[key] as? Int ?: def
        override fun getLong(key: String, def: Long) = values[key] as? Long ?: def
        override fun getFloat(key: String, def: Float) = values[key] as? Float ?: def
        override fun getBoolean(key: String, def: Boolean) = values[key] as? Boolean ?: def
        override fun contains(key: String) = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(
            l: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            l: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
    }

    @Test
    fun `12 the acceptance rules survive a save and load`() {
        val prefs = FakePrefs()
        val saved = RideSettings(
            minCostPerKmEnabled = true,
            minCostPerKm = 2.50,
            maxPickupKmEnabled = true,
            maxPickupKm = 5.0,
            maxTripKmEnabled = true,
            maxTripKm = 30.0
        )
        SettingsStore(prefs).save(saved)

        val loaded = SettingsStore(prefs).load()
        assertTrue(loaded.minCostPerKmEnabled)
        assertEquals(2.50, loaded.minCostPerKm, 0.0001)
        assertTrue(loaded.maxPickupKmEnabled)
        assertEquals(5.0, loaded.maxPickupKm, 0.0001)
        assertTrue(loaded.maxTripKmEnabled)
        assertEquals(30.0, loaded.maxTripKm, 0.0001)
    }

    @Test
    fun `12b the switches round-trip independently of each other`() {
        val prefs = FakePrefs()
        SettingsStore(prefs).save(
            RideSettings(
                minCostPerKmEnabled = false,
                maxPickupKmEnabled = true,
                maxTripKmEnabled = false
            )
        )
        val loaded = SettingsStore(prefs).load()
        assertFalse(loaded.minCostPerKmEnabled)
        assertTrue(loaded.maxPickupKmEnabled)
        assertFalse(loaded.maxTripKmEnabled)
    }

    @Test
    fun `12c an empty store returns the defaults, with every rule off`() {
        val loaded = SettingsStore(FakePrefs()).load()
        assertFalse(loaded.minCostPerKmEnabled)
        assertFalse(loaded.maxPickupKmEnabled)
        assertFalse(loaded.maxTripKmEnabled)
        assertEquals(2.50, loaded.minCostPerKm, 0.0001)
        assertEquals(5.0, loaded.maxPickupKm, 0.0001)
        assertEquals(30.0, loaded.maxTripKm, 0.0001)
    }

    @Test
    fun `the pace round-trips and never loads as zero`() {
        val prefs = FakePrefs()
        SettingsStore(prefs).save(RideSettings(ridesPerHour = 3.5))
        assertEquals(3.5, SettingsStore(prefs).load().ridesPerHour, 0.0001)

        // A zero pace would mean an infinitely long slot and a hourly figure
        // of zero for every offer, so it falls back to the default.
        prefs.values["rides_per_hour"] = 0f
        assertEquals(2.5, SettingsStore(prefs).load().ridesPerHour, 0.0001)
    }

    @Test
    fun `the banner size and anchor round-trip`() {
        val prefs = FakePrefs()
        SettingsStore(prefs).save(
            RideSettings(
                overlayScalePercent = 130,
                overlayWidthPercent = 70,
                overlayMaxHeightPercent = 60,
                overlayAnchor = OverlayAnchor.BOTTOM_RIGHT,
                overlayMarginX = 16,
                overlayMarginY = 24
            )
        )
        val loaded = SettingsStore(prefs).load()
        assertEquals(130, loaded.overlayScalePercent)
        assertEquals(70, loaded.overlayWidthPercent)
        assertEquals(60, loaded.overlayMaxHeightPercent)
        assertEquals(OverlayAnchor.BOTTOM_RIGHT, loaded.overlayAnchor)
        assertEquals(16, loaded.overlayMarginX)
        assertEquals(24, loaded.overlayMarginY)
    }

    @Test
    fun `an unknown anchor falls back to the default instead of crashing`() {
        // A downgrade, or a hand-edited preferences file, must not take the
        // overlay down with it.
        val prefs = FakePrefs()
        SettingsStore(prefs).save(RideSettings())
        prefs.values["overlay_anchor"] = "SOMEWHERE_ELSE"

        assertEquals(OverlayAnchor.TOP_CENTER, SettingsStore(prefs).load().overlayAnchor)
    }

    @Test
    fun `every anchor except CUSTOM reports itself as a preset`() {
        val presets = OverlayAnchor.entries.filter { it.isPreset }
        assertEquals(9, presets.size)
        assertEquals(false, OverlayAnchor.CUSTOM.isPreset)
    }

    @Test
    fun `a stored zero never becomes an active zero-kilometre limit`() {
        // What an older build wrote for "no limit". Read back under the new
        // switch it would reject every offer, so it falls back to the default.
        val prefs = FakePrefs()
        SettingsStore(prefs).save(RideSettings(maxPickupKmEnabled = true))
        prefs.values["max_pickup_km_v2"] = 0f

        val loaded = SettingsStore(prefs).load()
        assertEquals(5.0, loaded.maxPickupKm, 0.0001)
    }
}
