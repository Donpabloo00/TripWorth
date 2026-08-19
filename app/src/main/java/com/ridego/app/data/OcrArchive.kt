package com.ridego.app.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Keeps the last few raw OCR reads on disk, so a mis-parse can be diagnosed
 * after the fact.
 *
 * [OverlayDiagnostics] already holds the most recent text, but only in memory
 * and only one: the read that produced a wrong verdict is usually several
 * offers back by the time anyone looks, and a restart loses it entirely. A
 * shift produces the evidence; this is what survives to be read afterwards.
 *
 * Only active while Debug Mode is on. Screen text can contain a rider's
 * address, so it is never written unless the driver has explicitly asked for
 * diagnostics, and it never leaves the device.
 */
object OcrArchive {

    private const val FILE_NAME = "ocr-archive.txt"
    private const val MAX_ENTRIES = 20
    private const val LOG_TAG = "RIDEGO_OCR"

    private var file: File? = null

    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, FILE_NAME)
    }

    /**
     * @param summary what the parser made of this text, so the file shows the
     * read and its interpretation side by side.
     */
    fun record(rawText: String, summary: String, enabled: Boolean) {
        if (!enabled) return

        // Logcat first: while a cable is attached this is the fastest way to
        // see a bad read as it happens, without touching the phone.
        Log.i(LOG_TAG, "--- OCR RAW ($summary) ---\n$rawText\n--- END ---")

        val target = file ?: return
        runCatching {
            val entry = buildString {
                appendLine("=== ${System.currentTimeMillis()} | $summary ===")
                appendLine(rawText)
            }
            val kept = if (target.exists()) {
                target.readText().split("=== ").filter { it.isNotBlank() }
                    .takeLast(MAX_ENTRIES - 1)
                    .joinToString("") { "=== $it" }
            } else {
                ""
            }
            target.writeText(kept + entry)
        }.onFailure {
            Log.w(LOG_TAG, "nu am putut scrie arhiva OCR", it)
        }
    }

    /** Everything kept so far, newest last. */
    fun dump(): String = file?.takeIf { it.exists() }?.runCatching { readText() }?.getOrNull()
        ?: "(nimic arhivat — pornește Debug Mode și lasă să treacă o ofertă)"

    fun clear() {
        runCatching { file?.delete() }
    }
}
