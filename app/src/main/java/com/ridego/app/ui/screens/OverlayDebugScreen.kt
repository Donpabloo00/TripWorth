package com.ridego.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ridego.app.BuildConfig
import com.ridego.app.data.AppState
import com.ridego.app.data.OverlayDiagnostics
import com.ridego.app.overlay.OverlayService
import com.ridego.app.ui.PrimaryButton
import com.ridego.app.ui.RideCard
import com.ridego.app.ui.SectionLabel
import com.ridego.app.ui.theme.RideGray
import com.ridego.app.ui.theme.RideGreen
import com.ridego.app.ui.theme.RideOrange
import com.ridego.app.ui.theme.RideRed
import com.ridego.app.ui.theme.RideWhite
import com.ridego.app.ui.theme.RideYellow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live view of the overlay pipeline, readable on the phone itself. Built for
 * a device with no ADB connection, where the only way to see why the banner
 * did not appear is to ask the app.
 */
@Composable
fun OverlayDebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by OverlayDiagnostics.state.collectAsState()
    val logs by OverlayDiagnostics.logs.collectAsState()
    val captureRunning by AppState.isActive.collectAsState()
    val appForeground by AppState.appInForeground.collectAsState()
    val lastAnalysis by AppState.lastAnalysis.collectAsState()
    val settings by AppState.settings.collectAsState()
    var actionResult by remember { mutableStateOf<String?>(null) }

    // Read live rather than from the last recorded event, so the screen is
    // truthful even before any offer has been seen.
    val permissionNow = OverlayService.canDrawOverlays(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("OVERLAY DEBUG", style = MaterialTheme.typography.headlineMedium, color = RideYellow)
        Text(
            "build ${BuildConfig.BUILD_STAMP}",
            style = MaterialTheme.typography.bodyMedium,
            color = RideGray
        )
        Spacer(Modifier.height(16.dp))

        if (!captureRunning) {
            // Without the capture loop there is no OCR, so none of the flow
            // steps below can ever run — worth saying plainly.
            RideCard {
                Text(
                    "CITIREA NU RULEAZĂ.\n\nPentru testul cu o ofertă reală: întoarce-te pe " +
                        "ecranul principal, apasă PORNEȘTE CITIREA, apoi treci în Uber. " +
                        "Fără asta nu apare nicio linie RIDEGO_FLOW și bannerul nu are ce afișa.\n\n" +
                        "Butonul TEST OVERLAY de mai jos funcționează și fără citire — " +
                        "el testează doar fereastra.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideOrange
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        RideCard {
            Column {
                StatusLine("Internal overlay", if (settings.overlayEnabled) "ON" else "OFF", settings.overlayEnabled)
                StatusLine("Android permission", if (permissionNow) "GRANTED" else "DENIED", permissionNow)
                StatusLine("CaptureService", if (captureRunning) "RUNNING" else "STOPPED", captureRunning)
                StatusLine(
                    "OverlayService",
                    if (state.overlayServiceRunning) "RUNNING" else "STOPPED",
                    state.overlayServiceRunning
                )
                StatusLine(
                    "showIfEnabled()",
                    if (state.showCalled) "CALLED" else "NOT CALLED",
                    state.showCalled
                )
                StatusLine(
                    "addView()",
                    state.addViewResult,
                    state.addViewResult == "SUCCESS",
                    neutral = state.addViewResult == "NOT ATTEMPTED"
                )
                StatusLine("View", state.visibility, state.visibility == "VISIBLE", neutral = state.visibility == "—")
                StatusLine("Attached", boolLabel(state.attached), state.attached == true, neutral = state.attached == null)
                StatusLine("Shown", boolLabel(state.shown), state.shown == true, neutral = state.shown == null)
                val width = state.width
                val height = state.height
                StatusLine("Width", width?.toString() ?: "—", width != null && width > 0, neutral = width == null)
                StatusLine("Height", height?.toString() ?: "—", height != null && height > 0, neutral = height == null)
            }
        }

        Spacer(Modifier.height(14.dp))

        RideCard {
            Column {
                SectionLabel("Exception")
                Spacer(Modifier.height(8.dp))
                if (state.exceptionClass == null) {
                    Text("none", style = MaterialTheme.typography.bodyMedium, color = RideGray)
                } else {
                    Text(
                        state.exceptionClass!!,
                        style = MaterialTheme.typography.titleMedium,
                        color = RideRed
                    )
                    Text(
                        state.exceptionMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RideWhite
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.stackTrace ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = RideGray
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        RideCard {
            Column {
                SectionLabel("Live engine")
                Spacer(Modifier.height(8.dp))
                StatusLine(
                    "CaptureService",
                    if (captureRunning) "RUNNING" else "STOPPED",
                    captureRunning
                )
                // RideGo knows for certain only whether it is itself in front;
                // anything else is inferred from the last screen it read.
                StatusLine(
                    "Current foreground",
                    if (appForeground) "RIDEGO"
                    else state.parsePlatform?.takeIf { it != "NECUNOSCUT" } ?: "OTHER / UNKNOWN",
                    !appForeground
                )
                StatusLine("Last OCR", timeOf(state.rawOcrAt), state.rawOcrAt != null, neutral = state.rawOcrAt == null)
                StatusLine("Last platform", state.parsePlatform ?: "—", state.parsePlatform != null && state.parsePlatform != "NECUNOSCUT", neutral = state.parsePlatform == null)
                StatusLine("Last parser", state.parserSelected ?: "—", state.parserSelected != null, neutral = state.parserSelected == null)
                StatusLine("Last analysis", timeOf(state.lastAnalysisAt), state.lastAnalysisAt != null, neutral = state.lastAnalysisAt == null)
                StatusLine("Last verdict", state.lastVerdict ?: "—", state.lastVerdict != null, neutral = state.lastVerdict == null)
                StatusLine("Last price", state.lastPrice ?: "—", state.lastPrice != null, neutral = state.lastPrice == null)
                StatusLine("Last pickup", state.lastPickup ?: "—", state.lastPickup != null, neutral = state.lastPickup == null)
                StatusLine("Last trip", state.lastTrip ?: "—", state.lastTrip != null, neutral = state.lastTrip == null)
                StatusLine(
                    "HOME output",
                    if (lastAnalysis != null) "VISIBLE" else "EMPTY",
                    lastAnalysis != null
                )
                StatusLine(
                    "Overlay output",
                    when {
                        state.addViewResult == "FAILED" -> "FAILED"
                        state.overlayRequested == true && state.visibility == "VISIBLE" -> "SHOWN"
                        state.overlayRequested == true -> "REQUESTED"
                        else -> "HIDDEN"
                    },
                    state.overlayRequested == true && state.addViewResult != "FAILED",
                    neutral = state.overlayRequested == null
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        RideCard {
            Column {
                SectionLabel("Last analysis")
                Spacer(Modifier.height(8.dp))
                if (lastAnalysis == null) {
                    // Never fabricate values for the UI when the parser found
                    // nothing; say so and keep diagnosing.
                    Text(
                        "Analysis unavailable\nReason: parser returned null",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RideOrange
                    )
                } else {
                    val a = lastAnalysis!!
                    StatusLine("Platform", a.offer.platform.label, true)
                    StatusLine("Category", a.offer.serviceType ?: "—", a.offer.serviceType != null, neutral = a.offer.serviceType == null)
                    StatusLine("Price", a.offer.price?.toString() ?: "—", a.offer.price != null)
                    StatusLine("Pickup", "${a.offer.pickupDistanceKm ?: "—"} km / ${a.offer.pickupTimeMinutes ?: "—"} min", a.offer.pickupDistanceKm != null)
                    StatusLine("Trip", "${a.offer.tripDistanceKm ?: "—"} km / ${a.offer.tripTimeMinutes ?: "—"} min", a.offer.tripDistanceKm != null)
                    StatusLine("Total", "${a.totalKm ?: "—"} km / ${a.totalMinutes ?: "—"} min", a.totalKm != null)
                    StatusLine("RON/km", a.ronPerKm?.let { String.format("%.2f", it) } ?: "—", a.ronPerKm != null)
                    StatusLine("RON/hour", a.ronPerHour?.let { String.format("%.2f", it) } ?: "—", a.ronPerHour != null)
                    StatusLine("Verdict", a.verdict.name, true)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        RideCard {
            Column {
                SectionLabel("Rezultat parser")
                Spacer(Modifier.height(8.dp))
                StatusLine(
                    "Platform detected",
                    state.parsePlatform ?: "—",
                    state.parsePlatform != null && state.parsePlatform != "NECUNOSCUT",
                    neutral = state.parsePlatform == null
                )
                StatusLine(
                    "Parser selected",
                    state.parserSelected ?: "—",
                    state.parserSelected?.contains("Parser") == true,
                    neutral = state.parserSelected == null
                )
                StatusLine(
                    "Parser confidence",
                    state.parserConfidence?.let { "$it%" } ?: "—",
                    (state.parserConfidence ?: 0) >= 60,
                    neutral = state.parserConfidence == null
                )
                StatusLine(
                    "Parser result",
                    when (state.parserFound) {
                        true -> "FOUND"
                        false -> "NOT FOUND"
                        null -> "—"
                    },
                    state.parserFound == true,
                    neutral = state.parserFound == null
                )
                state.gateReport?.let {
                    Spacer(Modifier.height(10.dp))
                    SectionLabel("Poarta de detecție")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = RideGray
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        RideCard {
            Column {
                SectionLabel("RAW OCR TEXT")
                Spacer(Modifier.height(4.dp))
                Text(
                    "exact cum îl returnează ML Kit, fără nicio prelucrare",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray
                )
                Spacer(Modifier.height(10.dp))
                val raw = state.rawOcr
                if (raw == null) {
                    Text(
                        "Niciun ecran Uber/Bolt citit încă.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RideGray
                    )
                } else {
                    Text(
                        "${raw.length} caractere • capturat la " +
                            (state.rawOcrAt?.let {
                                SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(it))
                            } ?: "—"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RideGray
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        raw,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = RideWhite
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(context, raw)
                            actionResult = "RAW OCR copiat (${raw.length} caractere)"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("COPY RAW OCR") }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        PrimaryButton(text = "TEST OVERLAY", onClick = { OverlayService.test(context) })

        Spacer(Modifier.height(10.dp))
        Text(
            "Apasă, apoi ieși imediat din RideGo (Home sau Uber). Bannerul de " +
                "test trebuie să apară sus timp de 15 secunde.",
            style = MaterialTheme.typography.bodyMedium,
            color = RideGray
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    copyToClipboard(context, OverlayDiagnostics.asText(
                        captureRunning = captureRunning,
                        internalOverlayNow = settings.overlayEnabled,
                        androidPermissionNow = permissionNow,
                        buildStamp = BuildConfig.BUILD_STAMP
                    ))
                    actionResult = "Copiat în clipboard"
                },
                modifier = Modifier.weight(1f)
            ) { Text("COPY") }
            OutlinedButton(
                onClick = {
                    actionResult = exportDebug(context, OverlayDiagnostics.asText(
                        captureRunning = captureRunning,
                        internalOverlayNow = settings.overlayEnabled,
                        androidPermissionNow = permissionNow,
                        buildStamp = BuildConfig.BUILD_STAMP
                    ))
                },
                modifier = Modifier.weight(1f)
            ) { Text("EXPORT") }
            OutlinedButton(
                onClick = {
                    OverlayDiagnostics.clear()
                    actionResult = "Log golit"
                },
                modifier = Modifier.weight(1f)
            ) { Text("CLEAR") }
        }

        actionResult?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = RideGreen)
        }

        Spacer(Modifier.height(20.dp))

        RideCard {
            Column {
                SectionLabel("Log (${logs.size}/100, cele mai noi primele)")
                Spacer(Modifier.height(10.dp))
                if (logs.isEmpty()) {
                    Text("gol", style = MaterialTheme.typography.bodyMedium, color = RideGray)
                } else {
                    logs.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = RideGray,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("ÎNAPOI") }
    }
}

@Composable
private fun StatusLine(label: String, value: String, good: Boolean, neutral: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RideGray)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            color = when {
                neutral -> RideGray
                good -> RideGreen
                else -> RideRed
            }
        )
    }
}

private fun timeOf(at: Long?): String =
    at?.let { SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(it)) } ?: "—"

private fun boolLabel(value: Boolean?): String = when (value) {
    true -> "TRUE"
    false -> "FALSE"
    null -> "—"
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("RideGo overlay debug", text))
}

/**
 * Writes to the public Downloads folder on Android 10+, where a file manager
 * can reach it; older versions fall back to the app's external files dir.
 */
private fun exportDebug(context: Context, text: String): String {
    val name = "ridego_overlay_debug_" +
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return "EXPORT eșuat: nu s-a putut crea fișierul"
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            "Salvat în Downloads/$name"
        } else {
            val dir = context.getExternalFilesDir(null)
                ?: return "EXPORT eșuat: stocare indisponibilă"
            val file = File(dir, name)
            file.writeText(text)
            "Salvat: ${file.absolutePath}"
        }
    } catch (t: Throwable) {
        "EXPORT eșuat: ${t.javaClass.simpleName}: ${t.message}"
    }
}
