package com.ridego.app.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ridego.app.calculator.OfferAnalysis
import com.ridego.app.calculator.OverlayAnchor
import com.ridego.app.calculator.Verdict
import com.ridego.app.data.AppState
import com.ridego.app.data.DriverDecision
import com.ridego.app.data.OverlayDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Draggable card showing the latest verdict on top of Uber/Bolt.
 *
 * Plain Android views rather than Compose: a window-manager overlay has no
 * lifecycle owner, and wiring one up buys nothing here.
 *
 * It renders an [OfferAnalysis] and computes nothing of its own.
 */
class OverlayService : Service() {

    /** Appearance inputs that require rebuilding the view tree when changed. */
    private data class Appearance(
        val scalePercent: Int,
        val widthPercent: Int,
        val maxHeightPercent: Int,
        val opacityPercent: Int,
        val decisionButtons: Boolean,
        val anchor: OverlayAnchor,
        val marginX: Int,
        val marginY: Int
    )

    private var windowManager: WindowManager? = null
    private var root: LinearLayout? = null
    private var appliedAppearance: Appearance? = null

    private lateinit var platformView: TextView
    private lateinit var priceView: TextView
    private lateinit var perKmView: TextView
    private lateinit var perHourView: TextView
    private lateinit var verdictView: TextView
    private lateinit var reasonView: TextView
    private lateinit var legsCard: LinearLayout
    private lateinit var pickupRow: LinearLayout
    private lateinit var tripRow: LinearLayout
    private lateinit var totalRow: LinearLayout
    private lateinit var thresholdCard: LinearLayout
    private lateinit var thresholdMinView: TextView
    private lateinit var thresholdNeedView: TextView
    private lateinit var thresholdOfferView: TextView
    private lateinit var netView: TextView

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null
    private var testJob: Job? = null
    private var testMode = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        OverlayDiagnostics.update { it.copy(overlayServiceRunning = true) }
        OverlayDiagnostics.log("OverlayService onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            OverlayDiagnostics.log("OverlayService ACTION_HIDE")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!canDrawOverlays(this)) {
            Log.w(LOG_TAG, "RIDEGO_OVERLAY: onStartCommand ABORTED — android_permission=false")
            stopSelf()
            return START_NOT_STICKY
        }

        // Size, opacity and the buttons are baked into the view tree, so a
        // change in Settings means building it again rather than patching it.
        if (root != null && appliedAppearance != currentAppearance()) {
            OverlayDiagnostics.log("aspect schimbat — reconstruiesc bannerul")
            detach()
        }
        if (root == null) attachOverlay()
        if (intent?.action == ACTION_TEST) startTestBanner()
        return START_NOT_STICKY
    }

    private fun currentAppearance(): Appearance {
        val s = AppState.settings.value
        return Appearance(
            scalePercent = s.overlayScalePercent,
            widthPercent = s.overlayWidthPercent,
            maxHeightPercent = s.overlayMaxHeightPercent,
            opacityPercent = s.overlayOpacityPercent,
            decisionButtons = s.overlayDecisionButtons,
            anchor = s.overlayAnchor,
            marginX = s.overlayMarginX,
            marginY = s.overlayMarginY
        )
    }

    // --- window ---------------------------------------------------------

    private fun attachOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val settings = AppState.settings.value
        val appearance = currentAppearance()
        val scale = appearance.scalePercent.coerceIn(50, 200) / 100f
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density * scale).roundToInt()
        // Margins are literal screen distance, so they must not follow the
        // text scale — otherwise a bigger font also shoves the card off-edge.
        fun dpRaw(value: Int) = (value * density).roundToInt()
        fun sp(value: Float) = value * scale

        val alpha = appearance.opacityPercent.coerceIn(30, 100) * 255 / 100
        val panelColor = Color.argb(alpha, 0x1A, 0x1B, 0x1F)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(panelColor)
                setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#40FFFFFF"))
            }
        }

        // --- 1. header: which queue, and a way out ------------------------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        platformView = TextView(this).apply {
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            textSize = sp(19f)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val closeView = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = sp(22f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            // Generous padding: tapped one-handed, at a traffic light.
            setPadding(dp(20), dp(2), dp(2), dp(10))
            setOnClickListener { dismissBanner() }
        }
        header.addView(platformView)
        header.addView(closeView)
        card.addView(header)

        // --- 2. the fare, as large as the card allows ---------------------
        priceView = TextView(this).apply {
            setTextColor(Color.parseColor(YELLOW))
            textSize = sp(46f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        card.addView(priceView)

        // --- 3. the two ratios, side by side ------------------------------
        val ratios = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .apply { topMargin = dp(4) }
        }
        perKmView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = sp(21f)
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        perHourView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = sp(21f)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        ratios.addView(perKmView)
        ratios.addView(perHourView)
        card.addView(ratios)

        card.addView(divider(::dp))

        // --- 4. the verdict -----------------------------------------------
        verdictView = TextView(this).apply {
            textSize = sp(31f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        card.addView(verdictView)

        // --- 5. why -------------------------------------------------------
        // Was 12sp, the smallest text on a card read at speed. The reasons
        // are the point of the verdict, so they now match the body size.
        reasonView = TextView(this).apply {
            textSize = sp(15f)
            setLineSpacing(dp(3).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .apply { topMargin = dp(8) }
        }
        card.addView(reasonView)

        // --- 6. the legs --------------------------------------------------
        legsCard = innerCard(::dp)
        pickupRow = legRow(::dp, ::sp, "🚗", "PICKUP", BLUE)
        tripRow = legRow(::dp, ::sp, "📍", "CURSĂ", GREEN)
        totalRow = legRow(::dp, ::sp, "🏁", "TOTAL", PURPLE)
        legsCard.addView(pickupRow)
        legsCard.addView(rowDivider(::dp))
        legsCard.addView(tripRow)
        legsCard.addView(rowDivider(::dp))
        legsCard.addView(totalRow)
        card.addView(legsCard)

        // --- 7. the driver's own bar, when rule 1 is on -------------------
        thresholdCard = innerCard(::dp).apply {
            (background as GradientDrawable).setStroke(
                dp(1).coerceAtLeast(1),
                Color.parseColor(YELLOW)
            )
        }
        thresholdCard.addView(
            TextView(this).apply {
                text = "🎯  PRAGUL TĂU"
                setTextColor(Color.parseColor(YELLOW))
                textSize = sp(17f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    .apply { bottomMargin = dp(8) }
            }
        )
        thresholdMinView = thresholdRow(::dp, ::sp, thresholdCard, "Minim:")
        thresholdNeedView = thresholdRow(::dp, ::sp, thresholdCard, "Necesar:")
        thresholdOfferView = thresholdRow(::dp, ::sp, thresholdCard, "Oferta:")
        card.addView(thresholdCard)

        // --- 8. what is actually left -------------------------------------
        val netCard = innerCard(::dp)
        netCard.addView(
            TextView(this).apply {
                text = "💰  CÂȘTIG ESTIMAT NET"
                setTextColor(Color.parseColor(YELLOW))
                textSize = sp(17f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    .apply { bottomMargin = dp(4) }
            }
        )
        netView = TextView(this).apply {
            setTextColor(Color.parseColor(YELLOW))
            textSize = sp(28f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        netCard.addView(netView)
        card.addView(netCard)

        // --- 9. logging the driver's own call -----------------------------
        if (appearance.decisionButtons) {
            card.addView(spacer(dp(12)))
            card.addView(decisionRow(::dp, ::sp))
        }

        // --- 10. the drag handle ------------------------------------------
        card.addView(
            TextView(this).apply {
                text = "⠿   Trage pentru a muta"
                setTextColor(Color.parseColor(TEXT_MUTED))
                textSize = sp(13f)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    .apply { topMargin = dp(10) }
            }
        )

        // A tall rejection — three reasons plus every card — can outgrow a
        // short screen. Scrolling inside keeps the buttons reachable instead
        // of pushing them past the bottom edge.
        val scroller = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(card)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Nothing to say until an offer arrives.
            visibility = View.GONE
            addView(scroller)
        }

        root = container
        appliedAppearance = appearance

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        // Width is a share of the screen, chosen directly by the driver, so
        // it stays predictable when the text scale changes.
        val width = (screenWidth * appearance.widthPercent.coerceIn(50, 100) / 100)
            .coerceIn((screenWidth * 0.4).roundToInt(), screenWidth)
        val maxHeight = screenHeight * appearance.maxHeightPercent.coerceIn(30, 100) / 100
        scroller.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
            // ScrollView honours a bounded height; WRAP_CONTENT would let a
            // long card grow past the screen and take the buttons with it.
        }
        container.minimumWidth = 0

        val layout = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            if (appearance.anchor.isPreset) {
                // Let the window system hold it against the chosen edges, so
                // it survives rotation and screen-size changes untouched.
                gravity = gravityFor(appearance.anchor)
                x = dpRaw(appearance.marginX)
                y = dpRaw(appearance.marginY)
            } else {
                gravity = Gravity.TOP or Gravity.START
                // Where the driver last dragged it, or the default top spot.
                x = if (settings.overlayX >= 0) settings.overlayX else dp(12)
                y = if (settings.overlayY >= 0) settings.overlayY else dp(48)
            }
        }

        // Bound the height after measurement rather than guessing at build
        // time: the card's real height depends on how many reasons landed.
        card.viewTreeObserver.addOnGlobalLayoutListener {
            val lp = scroller.layoutParams
            val desired = if (card.height > maxHeight) maxHeight else WRAP
            if (lp.height != desired) {
                lp.height = desired
                scroller.layoutParams = lp
            }
        }

        container.setOnTouchListener(
            DragListener(wm, container, layout) { x, y ->
                // A drag is an explicit override of the preset, so record it
                // as one — otherwise the next rebuild would snap it back.
                AppState.updateSettings(
                    AppState.settings.value.copy(
                        overlayX = x,
                        overlayY = y,
                        overlayAnchor = OverlayAnchor.CUSTOM
                    )
                )
            }
        )

        Log.i(
            LOG_TAG,
            "RIDEGO_OVERLAY: addView attempt w=${layout.width} scale=$scale " +
                "x=${layout.x} y=${layout.y} type=$type"
        )

        val added = try {
            wm.addView(container, layout)
            true
        } catch (t: Throwable) {
            // Any failure here separates "RideGo never drew the banner" from
            // "RideGo drew it and something hid it", so report it in full.
            Log.e(
                LOG_TAG,
                "RIDEGO_OVERLAY:\naddView=FAILED\n" +
                    "exception=${t.javaClass.name}: ${t.message}",
                t
            )
            OverlayDiagnostics.recordException(t)
            false
        }

        if (!added) {
            root = null
            appliedAppearance = null
            stopSelf()
            return
        }

        OverlayDiagnostics.update {
            it.copy(
                addViewResult = "SUCCESS",
                exceptionClass = null,
                exceptionMessage = null,
                stackTrace = null,
                visibility = visibilityName(container),
                attached = container.isAttachedToWindow
            )
        }
        OverlayDiagnostics.log("addView=SUCCESS attached=${container.isAttachedToWindow}")

        container.post { reportGeometry(container, "după primul frame") }

        collectJob?.cancel()
        collectJob = scope.launch {
            // Explicit draw requests rather than the analysis state: hiding
            // after the timer must not imply the analysis is gone, and an
            // analysis produced inside RideGo must not draw.
            AppState.overlayRequests.collectLatest { analysis ->
                if (testMode) return@collectLatest

                render(analysis)
                container.visibility = View.VISIBLE
                AppState.setOverlayVisible(true)
                reportGeometry(container, "banner ofertă (${analysis.verdict})")

                val seconds = AppState.settings.value.overlayDurationSeconds.coerceIn(3, 120)
                delay(seconds * 1000L)
                container.visibility = View.GONE
                AppState.setOverlayVisible(false)
                OverlayDiagnostics.update { it.copy(visibility = "GONE") }
                OverlayDiagnostics.log("banner ascuns după ${seconds}s")
            }
        }
    }

    /**
     * Logs what the driver chose. RideGo never presses anything inside Uber —
     * it reads the screen, it does not drive it — so these record the
     * decision rather than making it.
     */
    private fun decisionRow(dp: (Int) -> Int, sp: (Float) -> Float): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        fun button(label: String, color: String, decision: DriverDecision): TextView =
            TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = sp(15f)
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(8), dp(12), dp(8), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor(color))
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginEnd = dp(6) }
                setOnClickListener {
                    AppState.recordDriverDecision(decision)
                    dismissBanner()
                }
            }

        row.addView(button("✓  AM ACCEPTAT", GREEN, DriverDecision.ACCEPTED))
        row.addView(button("✕  AM REFUZAT", RED, DriverDecision.REJECTED))
        return row
    }

    // --- view builders --------------------------------------------------

    private fun gravityFor(anchor: OverlayAnchor): Int = when (anchor) {
        OverlayAnchor.TOP_LEFT -> Gravity.TOP or Gravity.START
        OverlayAnchor.TOP_CENTER -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        OverlayAnchor.TOP_RIGHT -> Gravity.TOP or Gravity.END
        OverlayAnchor.CENTER_LEFT -> Gravity.CENTER_VERTICAL or Gravity.START
        OverlayAnchor.CENTER -> Gravity.CENTER
        OverlayAnchor.CENTER_RIGHT -> Gravity.CENTER_VERTICAL or Gravity.END
        OverlayAnchor.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START
        OverlayAnchor.BOTTOM_CENTER -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        OverlayAnchor.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END
        OverlayAnchor.CUSTOM -> Gravity.TOP or Gravity.START
    }

    private fun divider(dp: (Int) -> Int): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#33FFFFFF"))
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1).coerceAtLeast(1))
            .apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
    }

    private fun rowDivider(dp: (Int) -> Int): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#1FFFFFFF"))
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1).coerceAtLeast(1))
    }

    /** The recessed panel the legs, threshold and net figures sit in. */
    private fun innerCard(dp: (Int) -> Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor("#14FFFFFF"))
            setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#26FFFFFF"))
        }
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .apply { topMargin = dp(12) }
    }

    /**
     * One leg of the journey: icon, label, distance, time.
     *
     * The two figures are held in fixed-weight columns so PICKUP, CURSĂ and
     * TOTAL line up as a table — the driver compares them vertically, and
     * ragged columns defeat that at a glance.
     */
    private fun legRow(
        dp: (Int) -> Int,
        sp: (Float) -> Float,
        icon: String,
        label: String,
        color: String
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(9), 0, dp(9))

        addView(
            TextView(this@OverlayService).apply {
                text = icon
                textSize = sp(19f)
            }
        )
        addView(
            TextView(this@OverlayService).apply {
                text = label
                setTextColor(Color.parseColor(color))
                textSize = sp(17f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(10), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
        )
        // tag lets render() find the two value slots without more fields.
        addView(
            TextView(this@OverlayService).apply {
                tag = TAG_KM
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                textSize = sp(18f)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1.1f)
            }
        )
        addView(
            TextView(this@OverlayService).apply {
                tag = TAG_MIN
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                textSize = sp(18f)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 0.9f)
            }
        )
    }

    /** A "label: value" line inside the threshold card. */
    private fun thresholdRow(
        dp: (Int) -> Int,
        sp: (Float) -> Float,
        parent: LinearLayout,
        label: String
    ): TextView {
        val value = TextView(this).apply {
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            textSize = sp(16f)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1.7f)
        }
        parent.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
                addView(
                    TextView(this@OverlayService).apply {
                        text = label
                        setTextColor(Color.parseColor(TEXT_MUTED))
                        textSize = sp(16f)
                        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                    }
                )
                addView(value)
            }
        )
        return value
    }

    // --- rendering ------------------------------------------------------

    private fun render(analysis: OfferAnalysis) {
        // Pure rendering of the analysis produced by OfferCalculator — no
        // number here is computed locally.
        val ro = Locale("ro", "RO")
        val offer = analysis.offer
        val settings = AppState.settings.value

        platformView.text = listOfNotNull(
            offer.platform.label,
            offer.serviceType
        ).joinToString(" • ")

        priceView.text = offer.price?.let { String.format(ro, "%.2f RON", it) } ?: "—"

        perKmView.text = analysis.ronPerKm
            ?.let { String.format(ro, "%.2f RON/km", it) } ?: "— RON/km"
        perHourView.text = analysis.ronPerHour
            ?.let { String.format(ro, "%.0f RON/oră", it) } ?: "— RON/oră"

        val verdictColor = when (analysis.verdict) {
            Verdict.ACCEPT -> GREEN
            Verdict.CAUTION -> ORANGE
            Verdict.REJECT -> RED
        }
        verdictView.text = when (analysis.verdict) {
            Verdict.ACCEPT -> "✓  ACCEPTĂ"
            Verdict.CAUTION -> "⚠  ATENȚIE"
            Verdict.REJECT -> "✕  RESPINGE"
        }
        verdictView.setTextColor(Color.parseColor(verdictColor))

        // Each reason on its own line, always bulleted: a single reason and
        // the first of three should read the same way.
        reasonView.text = analysis.reasons.joinToString("\n") { "•  $it" }
        reasonView.setTextColor(
            Color.parseColor(if (analysis.verdict == Verdict.REJECT) RED else TEXT_MUTED)
        )
        reasonView.visibility = if (analysis.reasons.isEmpty()) View.GONE else View.VISIBLE

        // With the approach excluded it is not part of any total, so listing
        // it would imply it was counted, and TOTAL would just repeat CURSĂ.
        val includePickup = settings.includePickup
        fillLeg(pickupRow, offer.pickupDistanceKm, offer.pickupTimeMinutes, ro)
        fillLeg(tripRow, offer.tripDistanceKm, offer.tripTimeMinutes, ro)
        fillLeg(totalRow, analysis.totalKm, analysis.totalMinutes, ro)
        pickupRow.visibility = if (includePickup) View.VISIBLE else View.GONE
        totalRow.visibility = if (includePickup) View.VISIBLE else View.GONE
        // The separators belong to the rows they follow.
        legsCard.getChildAt(1).visibility = if (includePickup) View.VISIBLE else View.GONE
        legsCard.getChildAt(3).visibility = if (includePickup) View.VISIBLE else View.GONE

        // The threshold card explains a rule the driver switched on, so it
        // only appears while that rule is actually deciding anything.
        val tripKm = offer.tripDistanceKm
        if (settings.minCostPerKmEnabled && tripKm != null && tripKm > 0) {
            val required = tripKm * settings.minCostPerKm
            val actual = offer.price?.let { it / tripKm }
            thresholdMinView.text = String.format(ro, "%.2f RON/km", settings.minCostPerKm)
            thresholdNeedView.text = String.format(
                ro,
                "%.2f RON  (%.1f km × %.2f RON/km)",
                required,
                tripKm,
                settings.minCostPerKm
            )
            val below = offer.price != null && offer.price < required
            thresholdOfferView.text = buildString {
                append(actual?.let { String.format(ro, "%.2f RON/km", it) } ?: "—")
                append(if (below) "  →  sub minim" else "  →  peste minim")
            }
            thresholdOfferView.setTextColor(Color.parseColor(if (below) RED else GREEN))
            thresholdCard.visibility = View.VISIBLE
        } else {
            thresholdCard.visibility = View.GONE
        }

        // Net leads because it is what the verdict runs on and what the
        // driver actually keeps.
        netView.text = analysis.netRonPerHour
            ?.let { String.format(ro, "%.0f RON/oră NET", it) }
            ?: "date incomplete"
    }

    private fun fillLeg(row: LinearLayout, km: Double?, minutes: Int?, ro: Locale) {
        row.findViewWithTag<TextView>(TAG_KM).text =
            km?.let { String.format(ro, "%.1f km", it) } ?: "—"
        row.findViewWithTag<TextView>(TAG_MIN).text =
            minutes?.let { "$it min" } ?: "—"
    }

    /**
     * Paints a fixed banner through the same window live offers use, so a
     * success narrows the problem down to the OCR path and a failure narrows
     * it down to the window itself. Doubles as a size preview.
     */
    private fun startTestBanner() {
        val container = root ?: return
        testJob?.cancel()
        testMode = true
        testJob = scope.launch {
            val ro = Locale("ro", "RO")
            platformView.text = "RIDEGO TEST • UberX"
            priceView.text = "28,79 RON"
            perKmView.text = "2,15 RON/km"
            perHourView.text = "54 RON/oră"
            verdictView.text = "✓  EXEMPLU"
            verdictView.setTextColor(Color.parseColor(GREEN))
            reasonView.text = "•  așa va arăta bannerul la mărimea și poziția alese"
            reasonView.setTextColor(Color.parseColor(TEXT_MUTED))
            reasonView.visibility = View.VISIBLE
            fillLeg(pickupRow, 4.9, 9, ro)
            fillLeg(tripRow, 8.5, 23, ro)
            fillLeg(totalRow, 13.4, 32, ro)
            pickupRow.visibility = View.VISIBLE
            totalRow.visibility = View.VISIBLE
            legsCard.getChildAt(1).visibility = View.VISIBLE
            legsCard.getChildAt(3).visibility = View.VISIBLE
            thresholdCard.visibility = View.GONE
            netView.text = "37 RON/oră NET"
            container.visibility = View.VISIBLE
            AppState.setOverlayVisible(true)

            container.post { reportGeometry(container, "TEST banner") }
            OverlayDiagnostics.log("TEST banner afișat")

            val seconds = AppState.settings.value.overlayDurationSeconds.coerceIn(3, 120)
            delay(seconds * 1000L)
            container.visibility = View.GONE
            testMode = false
            AppState.setOverlayVisible(false)
            OverlayDiagnostics.update { it.copy(visibility = "GONE") }
            OverlayDiagnostics.log("TEST banner ascuns după ${seconds}s")
        }
    }

    // --- helpers --------------------------------------------------------

    private fun spacer(heightPx: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            heightPx
        )
    }

    /** Close button: hides the banner now and cancels the countdown. */
    private fun dismissBanner() {
        testJob?.cancel()
        testMode = false
        root?.visibility = View.GONE
        AppState.setOverlayVisible(false)
        OverlayDiagnostics.update { it.copy(visibility = "GONE") }
        OverlayDiagnostics.log("banner închis manual")
        Log.i(LOG_TAG, "RIDEGO_OVERLAY: banner dismissed by user")
    }

    private fun reportGeometry(container: View, label: String) {
        OverlayDiagnostics.update {
            it.copy(
                visibility = visibilityName(container),
                attached = container.isAttachedToWindow,
                shown = container.isShown,
                width = container.width,
                height = container.height
            )
        }
        OverlayDiagnostics.log(
            "$label attached=${container.isAttachedToWindow} " +
                "shown=${container.isShown} size=${container.width}x${container.height}"
        )
    }

    private fun detach() {
        collectJob?.cancel()
        collectJob = null
        testJob?.cancel()
        testMode = false
        root?.let { runCatching { windowManager?.removeView(it) } }
        root = null
        appliedAppearance = null
        AppState.setOverlayVisible(false)
    }

    override fun onDestroy() {
        OverlayDiagnostics.update { it.copy(overlayServiceRunning = false) }
        OverlayDiagnostics.log("OverlayService onDestroy")
        detach()
        scope.cancel()
        super.onDestroy()
    }

    private class DragListener(
        private val wm: WindowManager,
        private val view: View,
        private val params: WindowManager.LayoutParams,
        private val onMoved: (Int, Int) -> Unit
    ) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var dragged = false

        override fun onTouch(v: View, event: MotionEvent): Boolean = when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // An anchored window measures x/y from whichever edges its
                // gravity names, so dragging one straight from a preset would
                // send it the wrong way. Convert to absolute top-left first.
                if (params.gravity != (Gravity.TOP or Gravity.START)) {
                    val location = IntArray(2)
                    view.getLocationOnScreen(location)
                    params.gravity = Gravity.TOP or Gravity.START
                    params.x = location[0]
                    params.y = location[1]
                    wm.updateViewLayout(view, params)
                }
                startX = params.x
                startY = params.y
                touchX = event.rawX
                touchY = event.rawY
                dragged = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = startX + (event.rawX - touchX).roundToInt()
                params.y = startY + (event.rawY - touchY).roundToInt()
                dragged = true
                wm.updateViewLayout(view, params)
                true
            }
            MotionEvent.ACTION_UP -> {
                // Remember where it was put, so it comes back there tomorrow.
                if (dragged) onMoved(params.x, params.y)
                true
            }
            else -> false
        }
    }

    companion object {
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

        private const val TAG_KM = "km"
        private const val TAG_MIN = "min"

        private const val YELLOW = "#FFC400"
        private const val GREEN = "#4CAF50"
        private const val ORANGE = "#FF9800"
        private const val RED = "#F44336"
        private const val BLUE = "#42A5F5"
        private const val PURPLE = "#AB47BC"
        private const val TEXT_PRIMARY = "#E4E6E9"
        private const val TEXT_MUTED = "#9BA0A6"

        const val ACTION_HIDE = "com.ridego.app.OVERLAY_HIDE"
        const val ACTION_TEST = "com.ridego.app.OVERLAY_TEST"

        private const val LOG_TAG = "RIDEGO_OVERLAY"

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        private fun visibilityName(view: View): String = when (view.visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            else -> "GONE"
        }

        /**
         * Entry point for live capture. Applies the same gate as before, but
         * reports every branch — the silent early returns on this path are
         * what made an unset toggle look like a broken app.
         */
        fun showIfEnabled(context: Context) {
            val internalSetting = AppState.settings.value.overlayEnabled
            val androidPermission = canDrawOverlays(context)

            Log.i(
                LOG_TAG,
                "RIDEGO_OVERLAY:\nshow_called=true\n" +
                    "internal_setting=$internalSetting\n" +
                    "android_permission=$androidPermission"
            )
            OverlayDiagnostics.update {
                it.copy(
                    showCalled = true,
                    internalOverlay = internalSetting,
                    androidPermission = androidPermission
                )
            }
            OverlayDiagnostics.log(
                "showIfEnabled() internal=$internalSetting permission=$androidPermission"
            )

            if (!internalSetting) {
                Log.w(LOG_TAG, "RIDEGO_OVERLAY: ABORTED — internal_setting=false")
                OverlayDiagnostics.log("ABORTED — comutatorul Overlay din Setări este OPRIT")
                return
            }
            if (!androidPermission) {
                Log.w(LOG_TAG, "RIDEGO_OVERLAY: ABORTED — android_permission=false")
                OverlayDiagnostics.log("ABORTED — permisiunea Android de overlay lipsește")
                return
            }

            try {
                context.startService(Intent(context, OverlayService::class.java))
                OverlayDiagnostics.log("startService dispatched")
            } catch (t: Throwable) {
                Log.e(LOG_TAG, "RIDEGO_OVERLAY: startService FAILED", t)
                OverlayDiagnostics.recordException(t)
            }
        }

        /** Drives the real service, WindowManager and window type. */
        fun test(context: Context) {
            val androidPermission = canDrawOverlays(context)
            OverlayDiagnostics.update { it.copy(androidPermission = androidPermission) }
            OverlayDiagnostics.log("TEST OVERLAY apăsat, permission=$androidPermission")

            if (!androidPermission) {
                OverlayDiagnostics.log("TEST ABORTAT — permisiunea Android de overlay lipsește")
                return
            }
            try {
                context.startService(
                    Intent(context, OverlayService::class.java).setAction(ACTION_TEST)
                )
                OverlayDiagnostics.log("TEST startService dispatched")
            } catch (t: Throwable) {
                OverlayDiagnostics.recordException(t)
            }
        }

        fun show(context: Context) {
            if (!canDrawOverlays(context)) return
            context.startService(Intent(context, OverlayService::class.java))
        }

        fun hide(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).setAction(ACTION_HIDE)
            )
        }
    }
}
