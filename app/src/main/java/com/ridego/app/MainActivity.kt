package com.ridego.app

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ridego.app.data.AppState
import com.ridego.app.overlay.OverlayService
import com.ridego.app.parser.OfferParserRouter
import com.ridego.app.parser.PlatformMode
import com.ridego.app.service.CaptureService
import com.ridego.app.ui.screens.DebugScreen
import com.ridego.app.ui.screens.DemoScreen
import com.ridego.app.ui.screens.HistoryScreen
import com.ridego.app.ui.screens.HomeScreen
import com.ridego.app.ui.screens.OfferScreen
import com.ridego.app.ui.screens.OptimizationScreen
import com.ridego.app.ui.screens.OverlayDebugScreen
import com.ridego.app.ui.screens.SettingsScreen
import com.ridego.app.ui.theme.RideBlack
import com.ridego.app.ui.theme.TripWorthTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppState.init(this)
        requestNotificationPermissionIfNeeded()
        setContent {
            TripWorthTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(RideBlack),
                    color = RideBlack
                ) {
                    TripWorthNavHost()
                }
            }
        }
    }

    // Foreground state is owned by ProcessLifecycleOwner in TripWorthApp; a
    // per-activity onResume/onPause would also fire on rotation and would
    // disagree with it during transitions.

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // A foreground service on Android 13+ is silent without this.
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val OFFER = "offer"
    const val DEMO = "demo"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val DEBUG = "debug"
    const val OVERLAY_DEBUG = "overlay_debug"
    const val OPTIMIZATION = "optimization"
}

@Composable
private fun TripWorthNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val isActive by AppState.isActive.collectAsState()
    val stats by AppState.stats.collectAsState()
    val settings by AppState.settings.collectAsState()
    val history by AppState.history.collectAsState()
    val lastAnalysis by AppState.lastAnalysis.collectAsState()
    val lastParse by AppState.lastParse.collectAsState()

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode != android.app.Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        CaptureService.start(context, result.resultCode, data)
    }

    // While capture is running the driver is inside Uber, so the verdict is
    // delivered by the overlay. Pushing a full-screen route here would only
    // stack up behind Uber and ambush them on their next app switch.

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                isActive = isActive,
                stats = stats,
                platformMode = settings.platformMode,
                onPlatformMode = { AppState.updateSettings(settings.copy(platformMode = it)) },
                lastAnalysis = lastAnalysis,
                includePickup = settings.includePickup,
                debugMode = settings.debugMode,
                onOpenLastOffer = { navController.navigate(Routes.OFFER) },
                onStart = {
                    // Without this the overlay silently never appears, which
                    // reads as "the app does nothing".
                    if (settings.overlayEnabled && !OverlayService.canDrawOverlays(context)) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } else {
                        val manager = context.getSystemService(MediaProjectionManager::class.java)
                        projectionLauncher.launch(manager.createScreenCaptureIntent())
                    }
                },
                onStop = { CaptureService.stop(context) },
                onOpenDemo = { navController.navigate(Routes.DEMO) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenConfig = { navController.navigate(Routes.OPTIMIZATION) },
                onOpenDebug = { navController.navigate(Routes.DEBUG) }
            )
        }

        composable(Routes.DEBUG) {
            DebugScreen(result = lastParse, onBack = { navController.popBackStack() })
        }

        composable(Routes.OFFER) {
            val analysis = lastAnalysis
            if (analysis == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                OfferScreen(analysis = analysis, onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.DEMO) {
            DemoScreen(
                onRun = { ocrText ->
                    // Demo runs the same router as live capture, but forces
                    // AUTO so a pinned platform cannot silently drop a sample.
                    val result = OfferParserRouter.route(ocrText, mode = PlatformMode.AUTO)
                    AppState.setLastParse(result)
                    result.offer?.let {
                        AppState.submitDemoOffer(it)
                        navController.navigate(Routes.OFFER)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                history = history,
                onChange = { updated ->
                    AppState.updateSettings(updated)
                    if (updated.overlayEnabled && !OverlayService.canDrawOverlays(context)) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    if (!updated.overlayEnabled) OverlayService.hide(context)
                },
                onOpenOverlayDebug = { navController.navigate(Routes.OVERLAY_DEBUG) },
                onOpenOptimization = { navController.navigate(Routes.OPTIMIZATION) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.OPTIMIZATION) {
            OptimizationScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.OVERLAY_DEBUG) {
            OverlayDebugScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                allEntries = history,
                onClear = { AppState.clearHistory() },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
