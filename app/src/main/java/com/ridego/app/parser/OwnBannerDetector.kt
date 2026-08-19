package com.ridego.app.parser

/**
 * Recognises RideGo's own overlay in a captured frame.
 *
 * Draining the capture queue while the banner is up is the real fix, but it
 * depends on frame timing the app does not control. This is the check that
 * does not: the banner prints words no Uber or Bolt card ever contains, so a
 * frame carrying them is RideGo looking at itself and must never be parsed.
 *
 * The failure it prevents is not a missed offer — it is a fabricated one. The
 * banner shows "Preluare prea departe: 3,8 km" and "4,2 km", which the parser
 * happily read back as a 3.8 km approach and a 42 km ride.
 */
object OwnBannerDetector {

    /**
     * Phrases that only ever appear in RideGo's own banner. Kept deliberately
     * narrow — a word Uber might plausibly use would blind the app to real
     * offers, which is a far worse failure than reading one frame twice.
     */
    private val MARKERS = listOf(
        "TRAGE PENTRU A MUTA",
        // Not "PRAGUL TAU": the device archive shows OCR rendering it
        // "PRAGUL TAD". The first word alone is already a phrase no
        // ride-hailing card contains.
        "PRAGUL",
        "CASTIG ESTIMAT",
        "RON/ORA NET",
        "AM ACCEPTAT",
        "AM REFUZAT",
        "PRELUARE PREA DEPARTE",
        "SUB COSTUL MINIM",
        "CURSA PREA LUNGA",
        "CITIRE GRESITA",
        "RESPINGE"
    )

    fun isOwnBanner(rawText: String): Boolean {
        val normalized = normalize(rawText)
        return MARKERS.any { normalized.contains(it) }
    }

    /** Which marker matched, for the diagnostics log. */
    fun matchedMarker(rawText: String): String? {
        val normalized = normalize(rawText)
        return MARKERS.firstOrNull { normalized.contains(it) }
    }

    /**
     * Upper-cases and strips diacritics, because OCR renders "ă" as "a", "ǎ"
     * or nothing at all depending on the font and the frame.
     */
    private fun normalize(text: String): String = text.uppercase()
        .replace('Ă', 'A').replace('Â', 'A').replace('Ą', 'A')
        .replace('Î', 'I')
        .replace('Ș', 'S').replace('Ş', 'S')
        .replace('Ț', 'T').replace('Ţ', 'T')
}
