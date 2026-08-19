package com.ridego.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ridego.app.data.AppState
import com.ridego.app.data.DeviceHealth
import com.ridego.app.ui.PrimaryButton
import com.ridego.app.ui.RideCard
import com.ridego.app.ui.SectionLabel
import com.ridego.app.ui.theme.RideGray
import com.ridego.app.ui.theme.RideGreen
import com.ridego.app.ui.theme.RideOrange
import com.ridego.app.ui.theme.RideWhite
import com.ridego.app.ui.theme.RideYellow

/**
 * Checks the system settings that decide whether RideGo survives a shift.
 *
 * Not a phone cleaner: since Android 6 an app cannot clear another app's
 * cache, and "freeing RAM" makes Android slower, because the OS uses spare
 * memory as cache. Everything on this screen is real.
 */
@Composable
fun OptimizationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val history by AppState.history.collectAsState()
    var refreshToken by remember { mutableStateOf(0) }
    var lastAction by remember { mutableStateOf<String?>(null) }
    var killResult by remember { mutableStateOf<DeviceHealth.KillResult?>(null) }

    // Permissions are granted in Android's own screens, so the state has to
    // be re-read when the driver comes back rather than cached.
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val checks = remember(refreshToken, history.size) {
        DeviceHealth.checks(context, history.size)
    }
    val problems = checks.count { !it.ok }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("OPTIMIZARE", style = MaterialTheme.typography.headlineMedium, color = RideYellow)
        Spacer(Modifier.height(8.dp))
        Text(
            if (problems == 0) "Totul e în regulă."
            else "$problems ${if (problems == 1) "problemă" else "probleme"} de rezolvat.",
            style = MaterialTheme.typography.titleLarge,
            color = if (problems == 0) RideGreen else RideOrange
        )

        Spacer(Modifier.height(20.dp))

        checks.forEach { check ->
            RideCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            check.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = RideWhite,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (check.ok) "OK" else "!",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (check.ok) RideGreen else RideOrange
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        check.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RideGray
                    )
                    val label = check.actionLabel
                    val action = check.action
                    if (label != null && action != null) {
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            text = label,
                            onClick = {
                                lastAction = runAction(context, action)
                                refreshToken++
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        lastAction?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = RideGreen)
            Spacer(Modifier.height(12.dp))
        }

        SectionLabel("Memorie")
        Spacer(Modifier.height(10.dp))
        RideCard {
            Column {
                val memory = remember(refreshToken) { DeviceHealth.memory(context) }
                Text(
                    "${DeviceHealth.format(memory.availableBytes)} liberi",
                    style = MaterialTheme.typography.headlineMedium,
                    color = RideYellow
                )
                Text(
                    "din ${DeviceHealth.format(memory.totalBytes)} total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray
                )

                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = "OPREȘTE APLICAȚIILE DIN FUNDAL",
                    onClick = {
                        val result = DeviceHealth.killBackgroundApps(context)
                        killResult = result
                        refreshToken++
                    }
                )

                killResult?.let { result ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${DeviceHealth.format(result.freeBefore)} → " +
                            "${DeviceHealth.format(result.freeAfter)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (result.gained > 0) RideGreen else RideOrange
                    )
                    Text(
                        if (result.gained > 0) {
                            "${DeviceHealth.format(result.gained)} eliberați, " +
                                "${result.appsTargeted} aplicații vizate."
                        } else {
                            "Nimic eliberat. Android a refuzat oprirea sau aplicațiile " +
                                "au repornit imediat — ${result.appsTargeted} vizate."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = RideGray
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "Uber, Bolt, Maps, Waze și RideGo sunt protejate — nu se opresc " +
                        "niciodată de aici.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideGray
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Citește cifra de mai sus înainte și după. Pe Android, memoria " +
                        "liberă nu e un lucru bun în sine — sistemul o umple la loc cu " +
                        "cache, iar aplicațiile oprite repornesc. Dacă vezi că nu se " +
                        "schimbă mai nimic, asta e realitatea, nu un defect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RideOrange
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        SectionLabel("Ce nu poate face RideGo")
        Spacer(Modifier.height(8.dp))
        RideCard {
            Text(
                "Nu poate șterge cache-ul altor aplicații — Android interzice asta din " +
                    "versiunea 6, oricărei aplicații nesemnate de producător.\n\n" +
                    "Nu „eliberează RAM\". Pe Android memoria liberă e folosită drept " +
                    "cache; golind-o, aplicațiile repornesc de la zero și consumi mai " +
                    "multă baterie, nu mai puțină.\n\n" +
                    "Nu închide aplicații din fundal. Android le repornește imediat.\n\n" +
                    "Aplicațiile care promit astea fac, în general, animații.",
                style = MaterialTheme.typography.bodyMedium,
                color = RideGray
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { runAction(context, DeviceHealth.Action.APP_DETAILS) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("SETĂRILE ANDROID PENTRU RIDEGO") }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("ÎNAPOI") }
        Spacer(Modifier.height(20.dp))
    }
}

private fun runAction(context: Context, action: DeviceHealth.Action): String? {
    fun open(intent: Intent): String? {
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        }.getOrElse { "Nu am putut deschide ecranul Android: ${it.javaClass.simpleName}" }
    }

    val pkg = context.packageName
    return when (action) {
        DeviceHealth.Action.BATTERY_OPTIMIZATION -> open(
            @android.annotation.SuppressLint("BatteryLife")
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$pkg")
            )
        )

        DeviceHealth.Action.OVERLAY_PERMISSION -> open(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg"))
        )

        DeviceHealth.Action.NOTIFICATION_SETTINGS -> open(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        )

        DeviceHealth.Action.APP_DETAILS -> open(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        )

        DeviceHealth.Action.CLEAR_CACHE -> {
            val freed = DeviceHealth.clearCache(context)
            "Cache golit: ${DeviceHealth.format(freed)} eliberați."
        }

        DeviceHealth.Action.CLEAR_HISTORY -> {
            AppState.clearHistory()
            "Istoricul a fost golit."
        }
    }
}
