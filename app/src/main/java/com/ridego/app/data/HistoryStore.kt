package com.ridego.app.data

import android.content.Context
import com.ridego.app.calculator.OfferAnalysis
import com.ridego.app.calculator.Verdict
import com.ridego.app.parser.Platform
import org.json.JSONArray
import org.json.JSONObject

/** What the driver actually did, as opposed to what RideGo advised. */
enum class DriverDecision { ACCEPTED, REJECTED }

data class HistoryEntry(
    val timestamp: Long,
    val platform: Platform,
    val price: Double?,
    val totalKm: Double?,
    val totalMinutes: Int?,
    val ronPerKm: Double?,
    val ronPerHour: Double?,
    val verdict: Verdict,
    val driverDecision: DriverDecision? = null
)

/**
 * History is a short, capped list of decisions — Room would add a compiler
 * round-trip for what fits in one JSON blob.
 */
class HistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ridego_history", Context.MODE_PRIVATE)

    fun load(): List<HistoryEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                HistoryEntry(
                    timestamp = o.optLong("t"),
                    platform = runCatching { Platform.valueOf(o.optString("p")) }
                        .getOrDefault(Platform.UNKNOWN),
                    price = o.optDoubleOrNull("price"),
                    totalKm = o.optDoubleOrNull("km"),
                    totalMinutes = if (o.has("min")) o.optInt("min") else null,
                    ronPerKm = o.optDoubleOrNull("rk"),
                    ronPerHour = o.optDoubleOrNull("rh"),
                    verdict = runCatching { Verdict.valueOf(o.optString("v")) }
                        .getOrDefault(Verdict.CAUTION),
                    driverDecision = runCatching {
                        DriverDecision.valueOf(o.optString("d"))
                    }.getOrNull()
                )
            }
        }.getOrDefault(emptyList())
    }

    fun add(analysis: OfferAnalysis) {
        val entry = HistoryEntry(
            timestamp = System.currentTimeMillis(),
            platform = analysis.offer.platform,
            price = analysis.offer.price,
            totalKm = analysis.totalKm,
            totalMinutes = analysis.totalMinutes,
            ronPerKm = analysis.ronPerKm,
            ronPerHour = analysis.ronPerHour,
            verdict = analysis.verdict
        )
        val updated = (listOf(entry) + load()).take(MAX_ENTRIES)
        persist(updated)
    }

    /**
     * Stamps the driver's own call onto the most recent entry, so the record
     * shows both what RideGo advised and what actually happened.
     */
    fun recordDecision(decision: DriverDecision) {
        val entries = load()
        if (entries.isEmpty()) return
        persist(listOf(entries.first().copy(driverDecision = decision)) + entries.drop(1))
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private fun persist(entries: List<HistoryEntry>) {
        val array = JSONArray()
        entries.forEach { e ->
            val o = JSONObject()
            o.put("t", e.timestamp)
            o.put("p", e.platform.name)
            e.price?.let { o.put("price", it) }
            e.totalKm?.let { o.put("km", it) }
            e.totalMinutes?.let { o.put("min", it) }
            e.ronPerKm?.let { o.put("rk", it) }
            e.ronPerHour?.let { o.put("rh", it) }
            o.put("v", e.verdict.name)
            e.driverDecision?.let { o.put("d", it.name) }
            array.put(o)
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private companion object {
        const val KEY = "entries"
        // Home's counters are derived from this list, so it has to span a
        // full shift rather than just the recent-history view.
        const val MAX_ENTRIES = 500
    }
}
