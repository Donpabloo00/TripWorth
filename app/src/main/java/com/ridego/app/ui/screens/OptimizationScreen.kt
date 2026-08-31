package com.ridego.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import com.tripworth.app.R
import com.ridego.app.data.AppState
import com.ridego.app.data.DeviceHealth
import com.ridego.app.ui.PrimaryButton
import com.ridego.app.ui.theme.RideGray
import com.ridego.app.ui.theme.RideGreen
import com.ridego.app.ui.theme.RideOrange
import com.ridego.app.ui.theme.RideSurface
import com.ridego.app.ui.theme.RideWhite
import com.ridego.app.ui.theme.RideYellow

/**
 * Setup checklist styled like RideCheetah "Configurare": numbered steps,
 * progress bar, green checks for done items, Deschide for the rest.
 */
@Composable
fun OptimizationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val history by AppState.history.collectAsState()
    val settings by AppState.settings.collectAsState()
    var refreshToken by remember { mutableStateOf(0) }
    var lastAction by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Notifications → overlay → battery — the Configurare checklist.
    val setupSteps = remember(refreshToken, history.size) {
        val all = DeviceHealth.checks(context, history.size)
        listOfNotNull(
            all.find { it.title == "Notificări" },
            all.find { it.action == DeviceHealth.Action.OVERLAY_PERMISSION },
            all.find { it.action == DeviceHealth.Action.BATTERY_OPTIMIZATION }
        )
    }

    val done = setupSteps.count { it.ok }
    val total = setupSteps.size.coerceAtLeast(1)
    val progress = done.toFloat() / total

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "Configurare",
            style = MaterialTheme.typography.headlineMedium,
            color = RideOrange
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (done == total) "Totul e gata pentru tură."
            else stringResource(R.string.setup_intro, appName),
            style = MaterialTheme.typography.bodyMedium,
            color = RideGray
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = RideOrange,
                trackColor = RideSurface
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "$done/$total",
                style = MaterialTheme.typography.titleMedium,
                color = RideYellow
            )
        }

        Spacer(Modifier.height(20.dp))

        setupSteps.forEachIndexed { index, check ->
            SetupStepCard(
                number = index + 1,
                check = check,
                onOpen = {
                    check.action?.let { action ->
                        lastAction = runAction(context, action)
                        refreshToken++
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
        }

        // Auto-start style toggle — maps to overlay + autoRead.
        SetupToggleCard(
            title = "Pornire automată",
            detail = stringResource(R.string.setup_auto_start_detail, appName),
            checked = settings.autoRead && settings.overlayEnabled,
            onChecked = { on ->
                AppState.updateSettings(
                    settings.copy(autoRead = on, overlayEnabled = on)
                )
            }
        )

        lastAction?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = RideGreen)
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = if (done == total) "GATA" else "CONTINUĂ",
            onClick = onBack
        )

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("ÎNAPOI LA SETĂRI")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SetupStepCard(
    number: Int,
    check: DeviceHealth.Check,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (check.ok) RideGreen.copy(alpha = 0.5f) else RideOrange.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp)
            )
            .background(RideSurface, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (check.ok) RideGreen else RideOrange,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (check.ok) "✓" else number.toString(),
                color = if (check.ok) Color.White else Color.Black,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                check.title,
                style = MaterialTheme.typography.titleMedium,
                color = RideWhite
            )
            Spacer(Modifier.height(2.dp))
            Text(
                check.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = RideGray
            )
        }
        if (!check.ok && check.action != null) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onOpen) {
                Text("Deschide", color = RideOrange)
            }
        }
    }
}

@Composable
private fun SetupToggleCard(
    title: String,
    detail: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RideOrange.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .background(RideSurface, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = RideWhite)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = RideGray)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RideOrange
            )
        )
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
