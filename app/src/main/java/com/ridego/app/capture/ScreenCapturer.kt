package com.ridego.app.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Wraps MediaProjection behind a pull-based API: the service asks for the
 * latest frame when it is ready to do OCR, instead of being pushed every
 * frame the compositor produces. At 1-2 reads per second that keeps CPU and
 * battery cost negligible.
 */
class ScreenCapturer(
    private val context: Context,
    private val projection: MediaProjection
) {

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())
    private var width = 0
    private var height = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            releaseDisplay()
        }
    }

    fun start() {
        if (virtualDisplay != null) return

        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        // Halving the capture resolution is enough for ML Kit to read offer
        // text and roughly quarters the per-frame copy cost.
        width = metrics.widthPixels / 2
        height = metrics.heightPixels / 2

        projection.registerCallback(projectionCallback, handler)

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        virtualDisplay = projection.createVirtualDisplay(
            "RideGoCapture",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )
    }

    /** Returns the most recent frame, or null if none is available yet. */
    fun captureLatest(): Bitmap? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            // The buffer row is padded to a hardware-friendly stride, so the
            // bitmap must be created wider and then cropped back.
            val rowPadding = rowStride - pixelStride * width
            val paddedWidth = width + rowPadding / pixelStride

            val bitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(plane.buffer)
            if (paddedWidth == width) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, 0, 0, width, height).also { bitmap.recycle() }
            }
        } catch (e: IllegalStateException) {
            null
        } finally {
            image.close()
        }
    }

    /**
     * Throws away whatever the display has queued, without decoding it.
     *
     * The virtual display keeps mirroring while the service is deliberately
     * not reading — during RideGo's own banner, for instance. Those frames sit
     * in the reader's queue, so the first read after the pause returns a
     * picture of the banner rather than of the screen underneath, and RideGo
     * parses its own verdict back in as if it were an offer. Draining on every
     * skipped cycle keeps the queue empty, so the next frame accepted is one
     * produced after the pause ended.
     */
    fun drop() {
        val reader = imageReader ?: return
        while (true) {
            val image = reader.acquireLatestImage() ?: return
            image.close()
        }
    }

    fun stop() {
        runCatching { projection.unregisterCallback(projectionCallback) }
        releaseDisplay()
        runCatching { projection.stop() }
    }

    private fun releaseDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }
}
