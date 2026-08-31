package com.ridego.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ridego.app.MainActivity
import com.tripworth.app.R
import com.ridego.app.capture.ScreenCapturer
import com.ridego.app.data.AppState
import com.ridego.app.data.OverlayDiagnostics
import com.ridego.app.ocr.OcrEngine
import com.ridego.app.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the capture -> OCR -> parse -> verdict loop.
 *
 * Frames are read at [CAPTURE_INTERVAL_MS], recognized on device, and dropped
 * immediately afterwards. Nothing is persisted or transmitted.
 */
class CaptureService : Service() {

    private var capturer: ScreenCapturer? = null
    private var ocrEngine: OcrEngine? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture()
                return START_NOT_STICKY
            }
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode == 0 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // The foreground notification must be posted before the projection is
        // used, or Android 14+ kills the service.
        startForeground(NOTIFICATION_ID, buildNotification())

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, data)
        if (projection == null) {
            stopCapture()
            return START_NOT_STICKY
        }

        val screenCapturer = ScreenCapturer(this, projection).also { capturer = it }
        screenCapturer.start()
        ocrEngine = OcrEngine()
        AppState.setActive(true)
        startLoop()

        return START_NOT_STICKY
    }

    private fun startLoop() {
        loop?.cancel()
        // Latched while RideGo's banner is up, so the cycle after it vanishes
        // can also be discarded rather than trusted.
        var bannerWasVisible = false

        loop = scope.launch {
            while (isActive) {
                delay(CAPTURE_INTERVAL_MS)

                if (!AppState.settings.value.autoRead) {
                    capturer?.drop()
                    OverlayDiagnostics.flowStop("0 AUTO_READ", "citirea automată este oprită")
                    continue
                }

                if (AppState.overlayVisible.value) {
                    // Our own banner is on screen and would be read back as if
                    // it were the offer — it even prints "pickup" and "cursă",
                    // the labels the parser keys on. A verdict is already
                    // showing, so nothing is lost by pausing here.
                    //
                    // Skipping the read is not enough on its own: the display
                    // keeps queueing frames of the banner, and the first read
                    // after the pause would pick one up. Drain them here.
                    capturer?.drop()
                    bannerWasVisible = true
                    OverlayDiagnostics.flowStop(
                        "0 OWN_BANNER",
                        "TripWorth banner visible — frame ignored"
                    )
                    continue
                }

                if (bannerWasVisible) {
                    // The banner has just gone. The compositor may still have
                    // a frame of it in flight, so one more cycle is given up
                    // before anything is believed.
                    bannerWasVisible = false
                    capturer?.drop()
                    OverlayDiagnostics.flowStop(
                        "0 OWN_BANNER",
                        "bannerul tocmai s-a ascuns — un cadru lăsat să treacă"
                    )
                    continue
                }

                if (AppState.appInForeground.value) {
                    // Only this frame is skipped — the service, the engine and
                    // the last analysis all stay alive. RideGo's own windows
                    // are not offers, and reading them would overwrite the
                    // capture we care about.
                    OverlayDiagnostics.flowStop(
                        "0 SELF_SCREEN",
                        "${getString(R.string.app_name)} în prim-plan — cadrul e ignorat, motorul rămâne pornit"
                    )
                    capturer?.drop()
                    continue
                }

                val bitmap = capturer?.captureLatest()
                if (bitmap == null) {
                    // ImageReader only yields a frame when the screen content
                    // changed since the last read.
                    OverlayDiagnostics.flowStop("1 CAPTURE_FRAME", "niciun cadru nou de la ecran")
                    continue
                }

                val ocr = runCatching { ocrEngine?.recognize(bitmap) }
                bitmap.recycle()

                val failure = ocr.exceptionOrNull()
                if (failure != null) {
                    OverlayDiagnostics.flowStop(
                        "2 OCR_RECEIVED",
                        "ML Kit a eșuat: ${failure.javaClass.simpleName}: ${failure.message}"
                    )
                    continue
                }

                val text = ocr.getOrNull()
                if (text.isNullOrBlank()) {
                    OverlayDiagnostics.flowStop("2 OCR_RECEIVED", "text OCR gol")
                    continue
                }
                OverlayDiagnostics.flow("2 OCR_LENGTH", "${text.length} caractere")

                // Steps 3 to 7 are reported from inside AppState.
                val analysis = AppState.submitOcrText(text)
                if (analysis == null) continue

                notifyDriver()

                // One analysis, two outputs. It is already in AppState for the
                // UI; the overlay is asked to draw only when another app is in
                // front, so RideGo never covers itself.
                if (AppState.appInForeground.value) {
                    OverlayDiagnostics.flow(
                        "8 OUTPUT",
                        "${getString(R.string.app_name)} în prim-plan — rezultatul merge în UI, fără overlay"
                    )
                    OverlayDiagnostics.setOutputs(uiVisible = true, overlayRequested = false)
                } else {
                    val overlayEnabled = AppState.settings.value.overlayEnabled
                    OverlayDiagnostics.flow("8 OVERLAY_ENABLED", overlayEnabled.toString())
                    OverlayDiagnostics.flow("9 SHOW_IF_ENABLED_CALLED", "true")
                    OverlayDiagnostics.setOutputs(uiVisible = false, overlayRequested = true)

                    AppState.requestOverlay(analysis)
                    OverlayService.showIfEnabled(this@CaptureService)
                }
            }
        }
    }

    private fun notifyDriver() {
        val settings = AppState.settings.value
        if (settings.vibrate) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        if (settings.soundOnNewOffer) {
            runCatching {
                val uri = android.media.RingtoneManager
                    .getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                android.media.RingtoneManager.getRingtone(applicationContext, uri)?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    }
                    play()
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.capture_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Citește ecranul • procesare locală")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun stopCapture() {
        loop?.cancel()
        loop = null
        capturer?.stop()
        capturer = null
        ocrEngine?.close()
        ocrEngine = null
        AppState.setActive(false)
        OverlayService.hide(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        loop?.cancel()
        capturer?.stop()
        ocrEngine?.close()
        scope.cancel()
        AppState.setActive(false)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "tripworth_capture"
        private const val NOTIFICATION_ID = 1001

        /** ~1.5 reads per second: fast enough for an offer that shows for 10s. */
        private const val CAPTURE_INTERVAL_MS = 650L

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP = "com.tripworth.app.STOP"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, CaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CaptureService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
