package com.ridego.app.data

import android.content.Context

data class MonetizationState(
    val isPremium: Boolean = false,
    val premiumExpiryEpochMs: Long = 0L,
    val freeRidesRemaining: Int = FREE_RIDES_PER_GRANT,
    val lifetimeOffersCounted: Int = 0
) {
    val needsUnlock: Boolean
        get() = !isPremium && freeRidesRemaining <= 0

    companion object {
        const val FREE_RIDES_PER_GRANT = 10
        const val WEEKLY_PRODUCT_ID = "tripworth_weekly"
    }
}

/**
 * Free-tier quota and Play Billing entitlement. Kept separate from
 * [SettingsStore] so clearing ride settings never wipes purchases or ads progress.
 */
class MonetizationStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): MonetizationState {
        val expiry = prefs.getLong(KEY_PREMIUM_EXPIRY, 0L)
        val storedPremium = prefs.getBoolean(KEY_PREMIUM, false)
        val isPremium = storedPremium && (expiry <= 0L || expiry > System.currentTimeMillis())
        return MonetizationState(
            isPremium = isPremium,
            premiumExpiryEpochMs = expiry,
            freeRidesRemaining = prefs.getInt(KEY_REMAINING, MonetizationState.FREE_RIDES_PER_GRANT)
                .coerceAtLeast(0),
            lifetimeOffersCounted = prefs.getInt(KEY_LIFETIME, 0).coerceAtLeast(0)
        )
    }

    fun save(state: MonetizationState) {
        prefs.edit()
            .putBoolean(KEY_PREMIUM, state.isPremium)
            .putLong(KEY_PREMIUM_EXPIRY, state.premiumExpiryEpochMs)
            .putInt(KEY_REMAINING, state.freeRidesRemaining.coerceAtLeast(0))
            .putInt(KEY_LIFETIME, state.lifetimeOffersCounted.coerceAtLeast(0))
            .apply()
    }

    private companion object {
        const val PREFS = "tripworth_monetization"
        const val KEY_PREMIUM = "premium"
        const val KEY_PREMIUM_EXPIRY = "premium_expiry"
        const val KEY_REMAINING = "free_remaining"
        const val KEY_LIFETIME = "lifetime_counted"
    }
}
