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
import com.tripworth.app.R
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
 * Analysis card drawn over Uber/Bolt — sits at the top so the platform's
 * own offer sheet at the bottom stays readable and tappable.
 *
 * Layout mirrors the RideCheetah model: brand + verdict, big RON/km,
 * hourly / profit / fuel grid, then pickup earnings.
 */
class OverlayService : Service() {

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

    private lateinit var brandView: TextView
    private lateinit var badgeView: TextView
    private lateinit var perKmView: TextView
    private lateinit var hourlyHintView: TextView
    private lateinit var hourlyCellView: TextView
    private lateinit var profitCellView: TextView
    private lateinit var fuelCellView: TextView
    private lateinit var earningsView: TextView
    private lateinit var pickupView: TextView

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
        fun dpRaw(value: Int) = (value * density).roundToInt()
        fun sp(value: Float) = value * scale

        val alpha = appearance.opacityPercent.coerceIn(30, 100) * 255 / 100
        val panelColor = Color.argb(alpha, 0x12, 0x12, 0x14)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(panelColor)
                setStroke(dp(2).coerceAtLeast(1), Color.parseColor(ORANGE))
            }
        }

        // --- header: brand • platform | verdict badge | close ----------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brandView = TextView(this).apply {
            setTextColor(Color.parseColor(TEXT_MUTED))
            textSize = sp(12f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        badgeView = TextView(this).apply {
            textSize = sp(11f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            gravity = Gravity.CENTER
        }
        val closeView = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = sp(18f)
            setPadding(dp(14), dp(2), dp(2), dp(2))
            setOnClickListener { dismissBanner() }
        }
        header.addView(brandView)
        header.addView(badgeView)
        header.addView(closeView)
        card.addView(header)

        // --- hero: RON/km + ≈ RON/h ------------------------------------
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .apply { topMargin = dp(10) }
        }
        perKmView = TextView(this).apply {
            setTextColor(Color.parseColor(ORANGE))
            textSize = sp(34f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        hourlyHintView = TextView(this).apply {
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            textSize = sp(16f)
            gravity = Gravity.END or Gravity.BOTTOM
            setPadding(0, 0, 0, dp(4))
        }
        hero.addView(perKmView)
        hero.addView(hourlyHintView)
        card.addView(hero)

        // --- grid: oră | profit | combustibil --------------------------
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .apply { topMargin = dp(12) }
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#14FFFFFF"))
            }
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        hourlyCellView = gridCell(::dp, ::sp, grid, weight = 1f)
        profitCellView = gridCell(::dp, ::sp, grid, weight = 1.2f, accent = GREEN)
        fuelCellView = gridCell(::dp, ::sp, grid, weight = 1f)
        card.addView(grid)

        // --- detail lines ----------------------------------------------
        earningsView = detailLine(::dp, ::sp, card)
        pickupView = detailLine(::dp, ::sp, card)

        if (appearance.decisionButtons) {
            card.addView(spacer(dp(10)))
            card.addView(decisionRow(::dp, ::sp))
        }

        val scroller = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(card)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
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

        val width = (screenWidth * appearance.widthPercent.coerceIn(50, 100) / 100)
            .coerceIn((screenWidth * 0.4).roundToInt(), screenWidth)
        // Keep the card short so Uber/Bolt's bottom sheet stays free.
        val maxHeight = screenHeight * appearance.maxHeightPercent.coerceIn(25, 60) / 100

        val layout = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            if (appearance.anchor.isPreset) {
                gravity = gravityFor(appearance.anchor)
                x = dpRaw(appearance.marginX)
                y = dpRaw(appearance.marginY)
            } else {
                gravity = Gravity.TOP or Gravity.START
                x = if (settings.overlayX >= 0) settings.overlayX else dp(12)
                y = if (settings.overlayY >= 0) settings.overlayY else dp(48)
            }
        }

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

    private fun gridCell(
        dp: (Int) -> Int,
        sp: (Float) -> Float,
        parent: LinearLayout,
        weight: Float,
        accent: String = TEXT_PRIMARY
    ): TextView {
        val view = TextView(this).apply {
            setTextColor(Color.parseColor(accent))
            textSize = sp(13f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, WRAP, weight)
        }
        parent.addView(view)
        return view
    }

    private fun detailLine(
        dp: (Int) -> Int,
        sp: (Float) -> Float,
        parent: LinearLayout
    ): TextView {
        val view = TextView(this).apply {
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            textSize = sp(14f)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .apply { topMargin = dp(8) }
        }
        parent.addView(view)
        return view
    }

    private fun decisionRow(dp: (Int) -> Int, sp: (Float) -> Float): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        fun button(label: String, color: String, decision: DriverDecision): TextView =
            TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = sp(13f)
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(8), dp(11), dp(8), dp(11))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor(color))
                }
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    AppState.recordDriverDecision(decision)
                    dismissBanner()
                }
            }

        row.addView(button("✓  AM ACCEPTAT", GREEN, DriverDecision.ACCEPTED))
        row.addView(button("✕  AM REFUZAT", RED, DriverDecision.REJECTED))
        return row
    }

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

    // --- rendering ------------------------------------------------------

    private fun render(analysis: OfferAnalysis) {
        val ro = Locale("ro", "RO")
        val offer = analysis.offer

        brandView.text = "${getString(R.string.brand_banner)}  •  ${offer.platform.label}"

        val (badgeText, badgeBg, badgeFg) = when (analysis.verdict) {
            Verdict.ACCEPT -> Triple("●  CURSĂ BUNĂ", GREEN, Color.WHITE)
            Verdict.CAUTION -> Triple("●  ATENȚIE", ORANGE, Color.BLACK)
            Verdict.REJECT -> Triple("●  CURSĂ SLABĂ", RED, Color.WHITE)
        }
        badgeView.text = badgeText
        badgeView.setTextColor(badgeFg)
        badgeView.background = GradientDrawable().apply {
            cornerRadius = 999f
            setColor(Color.parseColor(badgeBg))
        }

        perKmView.text = analysis.ronPerKm
            ?.let { String.format(ro, "%.2f RON/km", it) } ?: "— RON/km"

        val hourly = analysis.netRonPerHour ?: analysis.ronPerHour
        hourlyHintView.text = hourly?.let { String.format(ro, "≈ %.0f RON/h", it) } ?: "≈ — RON/h"

        hourlyCellView.text = analysis.ronPerHour
            ?.let { String.format(ro, "%.0f RON/oră", it) } ?: "— RON/oră"

        profitCellView.text = analysis.estimatedProfit
            ?.let { String.format(ro, "%+.2f PROFIT", it) } ?: "— PROFIT"
        profitCellView.setTextColor(
            Color.parseColor(
                when {
                    analysis.estimatedProfit == null -> TEXT_MUTED
                    analysis.estimatedProfit >= 0 -> GREEN
                    else -> RED
                }
            )
        )

        fuelCellView.text = analysis.fuelCost
            ?.let { String.format(ro, "%.2f COST COMB.", it) } ?: "— COST COMB."

        earningsView.text = offer.price
            ?.let { String.format(ro, "Încasezi  %.2f RON", it) } ?: "Încasezi  —"

        val pickupKm = offer.pickupDistanceKm
        val pickupMin = offer.pickupTimeMinutes
        pickupView.text = when {
            pickupKm != null && pickupMin != null ->
                String.format(ro, "Distanță la client  %.1f km  •  %d min", pickupKm, pickupMin)
            pickupKm != null ->
                String.format(ro, "Distanță la client  %.1f km", pickupKm)
            pickupMin != null ->
                "Distanță la client  $pickupMin min"
            else -> "Distanță la client  —"
        }
        pickupView.visibility = View.VISIBLE
    }

    private fun startTestBanner() {
        val container = root ?: return
        testJob?.cancel()
        testMode = true
        testJob = scope.launch {
            brandView.text = "${getString(R.string.brand_banner)}  •  UBER"
            badgeView.text = "●  CURSĂ BUNĂ"
            badgeView.setTextColor(Color.WHITE)
            badgeView.background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(Color.parseColor(GREEN))
            }
            perKmView.text = "3,37 RON/km"
            hourlyHintView.text = "≈ 90 RON/h"
            hourlyCellView.text = "97 RON/oră"
            profitCellView.text = "+35,70 PROFIT"
            profitCellView.setTextColor(Color.parseColor(GREEN))
            fuelCellView.text = "7,80 COST COMB."
            earningsView.text = "Încasezi  43,50 RON"
            pickupView.text = "Distanță la client  1,8 km  •  4 min"
            pickupView.visibility = View.VISIBLE

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
        layoutParams = LinearLayout.LayoutParams(MATCH, heightPx)
    }

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
                if (dragged) onMoved(params.x, params.y)
                true
            }
            else -> false
        }
    }

    companion object {
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

        private const val ORANGE = "#FF9800"
        private const val GREEN = "#4CAF50"
        private const val RED = "#F44336"
        private const val TEXT_PRIMARY = "#E4E6E9"
        private const val TEXT_MUTED = "#9BA0A6"

        const val ACTION_HIDE = "com.tripworth.app.OVERLAY_HIDE"
        const val ACTION_TEST = "com.tripworth.app.OVERLAY_TEST"

        private const val LOG_TAG = "RIDEGO_OVERLAY"

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        private fun visibilityName(view: View): String = when (view.visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            else -> "GONE"
        }

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
