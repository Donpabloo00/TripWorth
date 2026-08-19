package com.ridego.app.data

import android.content.Context
import com.ridego.app.calculator.OfferAnalysis
import com.ridego.app.calculator.OfferCalculator
import com.ridego.app.calculator.RideSettings
import com.ridego.app.calculator.Verdict
import com.ridego.app.parser.OfferParserRouter
import com.ridego.app.parser.OwnBannerDetector
import com.ridego.app.parser.ParseResult
import com.ridego.app.parser.Platform
import com.ridego.app.parser.RideOffer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlatformStats(
    val analyzed: Int = 0,
    val accepted: Int = 0,
    val rejected: Int = 0
)

data class Stats(
    val total: PlatformStats = PlatformStats(),
    val uber: PlatformStats = PlatformStats(),
    val bolt: PlatformStats = PlatformStats()
)

/**
 * Single source of truth shared by the capture service, the overlay and the
 * UI. Process-scoped because all three live in the same process and must not
 * disagree about whether reading is active.
 */
object AppState {

    private lateinit var settingsStore: SettingsStore
    private lateinit var historyStore: HistoryStore

    private val _settings = MutableStateFlow(RideSettings())
    val settings: StateFlow<RideSettings> = _settings.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /**
     * True while RideGo's own UI is on screen.
     *
     * Screen capture mirrors the whole display, so without this the app reads
     * its own windows: the debug screen's help text mentions Uber, which was
     * enough for the platform detector to accept it as a real offer screen
     * and overwrite the capture being diagnosed.
     */
    private val _appInForeground = MutableStateFlow(false)
    val appInForeground: StateFlow<Boolean> = _appInForeground.asStateFlow()

    fun setAppInForeground(inForeground: Boolean) {
        _appInForeground.value = inForeground
    }

    /**
     * True while the overlay banner is drawn over another app.
     *
     * MediaProjection mirrors the whole display, banner included, and the
     * banner prints the words "pickup" and "cursă" — the very labels the
     * parser looks for. Reading it back produced a feedback loop where each
     * frame's total became the next frame's leg, drifting further from the
     * real offer every second.
     */
    private val _overlayVisible = MutableStateFlow(false)
    val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

    fun setOverlayVisible(visible: Boolean) {
        _overlayVisible.value = visible
    }

    /**
     * The one analysis, kept as pure state. It survives the overlay's 15s
     * timer, so returning to RideGo always shows the last offer.
     */
    private val _lastAnalysis = MutableStateFlow<OfferAnalysis?>(null)
    val lastAnalysis: StateFlow<OfferAnalysis?> = _lastAnalysis.asStateFlow()

    private val _lastAnalysisAt = MutableStateFlow<Long?>(null)
    val lastAnalysisAt: StateFlow<Long?> = _lastAnalysisAt.asStateFlow()

    /**
     * One-shot requests to draw the banner, kept separate from the state
     * above. Conflating the two made every analysis pop the overlay, even
     * ones produced while the driver was inside RideGo.
     */
    private val _overlayRequests = MutableSharedFlow<OfferAnalysis>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val overlayRequests: SharedFlow<OfferAnalysis> = _overlayRequests.asSharedFlow()

    /** Called by the capture loop when the verdict belongs on top of another app. */
    fun requestOverlay(analysis: OfferAnalysis): Boolean = _overlayRequests.tryEmit(analysis)

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    /** Latest pipeline trace, for Debug Mode. Kept in memory only. */
    private val _lastParse = MutableStateFlow<ParseResult?>(null)
    val lastParse: StateFlow<ParseResult?> = _lastParse.asStateFlow()

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    /**
     * Signature of the offer already shown, so it is analyzed once. Platform
     * is part of the signature, so Uber and Bolt dedupe independently.
     */
    private var lastSignature: String? = null

    fun init(context: Context) {
        if (::settingsStore.isInitialized) return
        settingsStore = SettingsStore(context)
        historyStore = HistoryStore(context)
        OcrArchive.init(context)
        _settings.value = settingsStore.load()
        refreshHistory()
    }

    fun updateSettings(settings: RideSettings) {
        _settings.value = settings
        settingsStore.save(settings)
    }

    fun setActive(active: Boolean) {
        _isActive.value = active
        if (!active) lastSignature = null
    }

    /**
     * Runs the full pipeline on OCR text and records the result.
     * Returns null when nothing usable was found or the offer is a duplicate.
     */
    fun submitOcrText(rawText: String, foregroundPackage: String? = null): OfferAnalysis? {
        // RideGo's banner sits on top of the offer it describes, so a frame
        // caught mid-banner contains both. Parsing it invents an offer out of
        // RideGo's own words rather than failing honestly.
        OwnBannerDetector.matchedMarker(rawText)?.let { marker ->
            OcrArchive.record(rawText, "IGNORAT — propriul banner (\"$marker\")", _settings.value.debugMode)
            OverlayDiagnostics.flowStop(
                "2 OWN_BANNER_TEXT",
                "cadrul conține bannerul RideGo (\"$marker\") — ignorat"
            )
            return null
        }

        val result = OfferParserRouter.route(
            rawText = rawText,
            foregroundPackage = foregroundPackage,
            mode = _settings.value.platformMode
        )
        _lastParse.value = result

        OverlayDiagnostics.flow(
            "3 PARSER_RESULT",
            "platform=${result.platform.label} parser=${result.parserName} " +
                "confidence=${result.confidence}%"
        )
        // The evidence a mis-parse needs, written while it is still the
        // current read rather than reconstructed from the verdict later.
        OcrArchive.record(
            rawText = rawText,
            summary = "platform=${result.platform.label} parser=${result.parserName} " +
                "confidence=${result.confidence}% " +
                "pickup=${result.offer?.pickupDistanceKm}km/" +
                "${result.offer?.pickupTimeMinutes}min " +
                "trip=${result.offer?.tripDistanceKm}km/${result.offer?.tripTimeMinutes}min",
            enabled = _settings.value.debugMode
        )

        OverlayDiagnostics.recordParse(
            rawOcr = rawText,
            platform = result.platform.label,
            platformKnown = result.platform != Platform.UNKNOWN,
            parser = result.parserName,
            confidence = result.confidence,
            found = result.offer != null,
            gateReport = OfferParserRouter.gateReport(rawText)
        )

        val offer = result.offer
        if (offer == null) {
            OverlayDiagnostics.flowStop(
                "3 PARSER_RESULT",
                "parserul nu a găsit nicio ofertă (${result.parserName})"
            )
            return null
        }

        OverlayDiagnostics.flow(
            "4 OFFER_IS_RELIABLE",
            "${offer.isReliableFor(_settings.value.includePickup)} — pickup=${offer.pickupDistanceKm}km/" +
                "${offer.pickupTimeMinutes}min cursă=${offer.tripDistanceKm}km/" +
                "${offer.tripTimeMinutes}min preț=${offer.price}"
        )

        if (!offer.isReliableFor(_settings.value.includePickup)) {
            val missing = buildList {
                if (offer.price == null) add("preț")
                if (offer.pickupDistanceKm == null) add("pickup km")
                if (offer.pickupTimeMinutes == null) add("pickup min")
                if (offer.tripDistanceKm == null) add("cursă km")
                if (offer.tripTimeMinutes == null) add("cursă min")
            }
            OverlayDiagnostics.flowStop(
                "4 OFFER_IS_RELIABLE",
                "lipsesc: ${missing.joinToString(", ").ifEmpty { "—" }} " +
                    "(confidence=${offer.confidence}%, prag=${RideOffer.MIN_CONFIDENCE}%)"
            )
            return null
        }

        val analysis = submitOffer(offer)
        if (analysis == null) {
            OverlayDiagnostics.flowStop(
                "5 SUBMIT_OFFER_RESULT",
                "ofertă duplicat, deja analizată (signature identică)"
            )
            return null
        }

        OverlayDiagnostics.flow("6 ANALYSIS_CREATED", "verdict=${analysis.verdict}")
        OverlayDiagnostics.flow("7 LAST_ANALYSIS_EMITTED", "true")
        OverlayDiagnostics.recordAnalysis(
            verdict = analysis.verdict.name,
            price = offer.price?.toString() ?: "—",
            pickup = "${offer.pickupDistanceKm ?: "—"} km / ${offer.pickupTimeMinutes ?: "—"} min",
            trip = "${offer.tripDistanceKm ?: "—"} km / ${offer.tripTimeMinutes ?: "—"} min",
            at = _lastAnalysisAt.value ?: System.currentTimeMillis()
        )
        return analysis
    }

    /**
     * Analyzes an offer unless it is the one already showing.
     * Returns null when the offer was suppressed as a duplicate.
     */
    fun submitOffer(offer: RideOffer, recordDuplicate: Boolean = false): OfferAnalysis? {
        if (!recordDuplicate && offer.signature == lastSignature) return null
        lastSignature = offer.signature

        val analysis = OfferCalculator.analyze(offer, _settings.value)
        _lastAnalysis.value = analysis
        _lastAnalysisAt.value = System.currentTimeMillis()
        historyStore.add(analysis)
        refreshHistory()
        return analysis
    }

    /** Lets Demo record its pipeline trace so Debug Mode reflects it too. */
    fun setLastParse(result: ParseResult) {
        _lastParse.value = result
    }

    /** Demo taps should always re-run, even on the same sample offer. */
    fun submitDemoOffer(offer: RideOffer): OfferAnalysis =
        submitOffer(offer, recordDuplicate = true)!!

    /** Records what the driver chose on the banner. Never touches Uber. */
    fun recordDriverDecision(decision: DriverDecision) {
        historyStore.recordDecision(decision)
        refreshHistory()
        OverlayDiagnostics.log("decizie șofer: ${decision.name}")
    }

    fun clearHistory() {
        historyStore.clear()
        refreshHistory()
    }

    fun clearLastAnalysis() {
        _lastAnalysis.value = null
    }

    private fun refreshHistory() {
        val entries = historyStore.load()
        _history.value = entries
        // Stats are derived rather than counted separately, so they can never
        // drift out of step with the list the driver is looking at.
        _stats.value = Stats(
            total = entries.toStats(),
            uber = entries.filter { it.platform == Platform.UBER }.toStats(),
            bolt = entries.filter { it.platform == Platform.BOLT }.toStats()
        )
    }

    private fun List<HistoryEntry>.toStats() = PlatformStats(
        analyzed = size,
        accepted = count { it.verdict == Verdict.ACCEPT },
        rejected = count { it.verdict == Verdict.REJECT }
    )
}
