package com.ridego.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app mirror of the RIDEGO_OVERLAY logcat trail.
 *
 * Exists because the overlay can only be diagnosed while another app is in
 * front, and this phone has no ADB connection — so the evidence has to be
 * readable from inside RideGo itself.
 */
object OverlayDiagnostics {

    private const val MAX_LOG_LINES = 100

    data class Snapshot(
        val internalOverlay: Boolean? = null,
        val androidPermission: Boolean? = null,
        val overlayServiceRunning: Boolean = false,
        val showCalled: Boolean = false,
        val addViewResult: String = "NOT ATTEMPTED",
        val visibility: String = "—",
        val attached: Boolean? = null,
        val width: Int? = null,
        val height: Int? = null,
        val shown: Boolean? = null,
        val exceptionClass: String? = null,
        val exceptionMessage: String? = null,
        val stackTrace: String? = null,
        val lastEventAt: Long? = null,
        /** Untouched ML Kit output from the last Uber/Bolt screen that was read. */
        val rawOcr: String? = null,
        val rawOcrAt: Long? = null,
        val parsePlatform: String? = null,
        val parserSelected: String? = null,
        val parserConfidence: Int? = null,
        val parserFound: Boolean? = null,
        val gateReport: String? = null,
        val uiVisible: Boolean? = null,
        val overlayRequested: Boolean? = null,
        val lastVerdict: String? = null,
        val lastPrice: String? = null,
        val lastPickup: String? = null,
        val lastTrip: String? = null,
        val lastAnalysisAt: Long? = null
    )

    fun setOutputs(uiVisible: Boolean, overlayRequested: Boolean) {
        update { it.copy(uiVisible = uiVisible, overlayRequested = overlayRequested) }
    }

    fun recordAnalysis(
        verdict: String,
        price: String,
        pickup: String,
        trip: String,
        at: Long
    ) {
        update {
            it.copy(
                lastVerdict = verdict,
                lastPrice = price,
                lastPickup = pickup,
                lastTrip = trip,
                lastAnalysisAt = at
            )
        }
    }

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun update(transform: (Snapshot) -> Snapshot) {
        _state.update { transform(it).copy(lastEventAt = System.currentTimeMillis()) }
    }

    fun log(line: String) {
        _logs.update { existing ->
            // Newest first, oldest dropped past the cap.
            (listOf("${stamp.format(Date())}  $line") + existing).take(MAX_LOG_LINES)
        }
    }

    /**
     * Capture runs ~1.5 times a second, so an unchanging condition would push
     * every other line out of a 100-entry buffer within a minute. Repeats of
     * the same message collapse into a counter instead.
     */
    fun logRepeating(line: String) {
        _logs.update { existing ->
            val head = existing.firstOrNull()
            val body = head?.substringAfter("  ")
            val base = body?.substringBefore("  ×")
            if (base == line) {
                val count = body.substringAfter("  ×", "1").toIntOrNull() ?: 1
                listOf("${stamp.format(Date())}  $line  ×${count + 1}") + existing.drop(1)
            } else {
                (listOf("${stamp.format(Date())}  $line") + existing).take(MAX_LOG_LINES)
            }
        }
    }

    /** Marks the first step of the pipeline that refused to continue. */
    fun flowStop(step: String, reason: String) {
        logRepeating("RIDEGO_FLOW STOP la $step — $reason")
    }

    fun flow(step: String, detail: String) {
        logRepeating("RIDEGO_FLOW $step: $detail")
    }

    /**
     * Keeps the raw text from the last screen that was actually identified as
     * Uber or Bolt. Screens with no platform are ignored so that walking back
     * into RideGo does not overwrite the very capture we are trying to read.
     */
    fun recordParse(
        rawOcr: String,
        platform: String,
        platformKnown: Boolean,
        parser: String,
        confidence: Int,
        found: Boolean,
        gateReport: String
    ) {
        if (!platformKnown) return
        update {
            it.copy(
                rawOcr = rawOcr,
                rawOcrAt = System.currentTimeMillis(),
                parsePlatform = platform,
                parserSelected = parser,
                parserConfidence = confidence,
                parserFound = found,
                gateReport = gateReport
            )
        }
    }

    fun recordException(t: Throwable) {
        update {
            it.copy(
                addViewResult = "FAILED",
                exceptionClass = t.javaClass.name,
                exceptionMessage = t.message ?: "(fără mesaj)",
                stackTrace = t.stackTraceToString()
            )
        }
        log("addView=FAILED ${t.javaClass.simpleName}: ${t.message}")
    }

    fun clear() {
        _logs.value = emptyList()
        _state.value = Snapshot()
    }

    /**
     * Everything the debug screen shows, as one block for clipboard or file.
     *
     * The live values are passed in rather than read from the snapshot: the
     * snapshot only fills in once showIfEnabled() runs, so a report made
     * before the first offer used to print "—" for the overlay toggle while
     * the screen above it showed the real state.
     */
    fun asText(
        captureRunning: Boolean,
        internalOverlayNow: Boolean,
        androidPermissionNow: Boolean,
        buildStamp: String
    ): String {
        val s = _state.value
        fun flag(value: Boolean?, on: String, off: String) =
            when (value) {
                true -> on
                false -> off
                null -> "—"
            }

        return buildString {
            appendLine("RIDEGO OVERLAY DEBUG")
            appendLine("generat: ${stamp.format(Date())}")
            appendLine("build:   $buildStamp")
            appendLine()
            appendLine("Internal overlay:        ${if (internalOverlayNow) "ON" else "OFF"}")
            appendLine("Android permission:      ${if (androidPermissionNow) "GRANTED" else "DENIED"}")
            appendLine("CaptureService:          ${if (captureRunning) "RUNNING" else "STOPPED"}")
            appendLine("OverlayService:          ${if (s.overlayServiceRunning) "RUNNING" else "STOPPED"}")
            appendLine("showIfEnabled():         ${if (s.showCalled) "CALLED" else "NOT CALLED"}")
            appendLine("addView():               ${s.addViewResult}")
            appendLine("View:                    ${s.visibility}")
            appendLine("Attached:                ${flag(s.attached, "TRUE", "FALSE")}")
            appendLine("Shown:                   ${flag(s.shown, "TRUE", "FALSE")}")
            appendLine("Width:                   ${s.width ?: "—"}")
            appendLine("Height:                  ${s.height ?: "—"}")
            appendLine()
            appendLine("Exception:")
            if (s.exceptionClass == null) {
                appendLine("  none")
            } else {
                appendLine("  class:   ${s.exceptionClass}")
                appendLine("  message: ${s.exceptionMessage}")
                appendLine("  stack:")
                appendLine(s.stackTrace?.prependIndent("    ") ?: "    —")
            }
            appendLine()
            appendLine("Platform detected:       ${s.parsePlatform ?: "—"}")
            appendLine("Parser selected:         ${s.parserSelected ?: "—"}")
            appendLine("Parser confidence:       ${s.parserConfidence?.let { "$it%" } ?: "—"}")
            appendLine(
                "Parser result:           " + when (s.parserFound) {
                    true -> "FOUND"
                    false -> "NOT FOUND"
                    null -> "—"
                }
            )
            appendLine("Poarta de detecție:      ${s.gateReport ?: "—"}")
            appendLine()
            appendLine("RAW OCR TEXT (${s.rawOcr?.length ?: 0} caractere, nemodificat):")
            appendLine("---8<--- START ---8<---")
            appendLine(s.rawOcr ?: "(niciun ecran Uber/Bolt citit)")
            appendLine("---8<--- END ---8<---")
            appendLine()
            appendLine("LOG (${_logs.value.size} linii, cele mai noi primele):")
            _logs.value.forEach { appendLine("  $it") }
        }
    }
}
