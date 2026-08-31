package com.ridego.app.data

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import com.tripworth.app.R
import java.io.File

/**
 * The handful of system settings that decide whether RideGo survives a shift.
 *
 * Deliberately not a "phone cleaner": since Android 6 an app cannot touch
 * another app's cache, and freeing RAM makes Android slower rather than
 * faster because the OS uses spare memory as cache. Everything here is
 * something RideGo can actually check or actually fix.
 */
object DeviceHealth {

    data class Check(
        val title: String,
        val detail: String,
        val ok: Boolean,
        val actionLabel: String? = null,
        val action: Action? = null
    )

    enum class Action {
        BATTERY_OPTIMIZATION,
        OVERLAY_PERMISSION,
        NOTIFICATION_SETTINGS,
        APP_DETAILS,
        CLEAR_CACHE,
        CLEAR_HISTORY
    }

    fun checks(context: Context, historyCount: Int): List<Check> = listOf(
        batteryCheck(context),
        overlayCheck(context),
        notificationCheck(context),
        storageCheck(context),
        historyCheck(historyCount)
    )

    /**
     * The one that matters most on Samsung: without the exemption the system
     * is free to stop the capture service mid-shift, and RideGo simply goes
     * quiet without saying why.
     */
    private fun batteryCheck(context: Context): Check {
        val exempt = isBatteryExempt(context)
        return Check(
            title = "Baterie fără restricții",
            detail = if (exempt) {
                context.getString(R.string.battery_exempt_ok, context.getString(R.string.app_name))
            } else {
                "Android poate opri citirea în mijlocul turei, fără niciun avertisment. " +
                    "Pe Samsung se întâmplă des după o oră de rulare."
            },
            ok = exempt,
            actionLabel = if (exempt) null else "Deschide",
            action = if (exempt) null else Action.BATTERY_OPTIMIZATION
        )
    }

    private fun overlayCheck(context: Context): Check {
        val granted = Settings.canDrawOverlays(context)
        return Check(
            title = "Afișare peste alte aplicații",
            detail = if (granted) {
                "Acordată. Cardul de analiză apare sus, peste Uber și Bolt."
            } else {
                "Necesară pentru Bolt și Uber — fără ea nu vezi verdictul pe cursă."
            },
            ok = granted,
            actionLabel = if (granted) null else "Deschide",
            action = if (granted) null else Action.OVERLAY_PERMISSION
        )
    }

    private fun notificationCheck(context: Context): Check {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val enabled = manager?.areNotificationsEnabled() ?: true
        return Check(
            title = "Notificări",
            detail = if (enabled) {
                "Activate. Serviciul de citire poate rula în fundal."
            } else {
                "Dezactivate. Android 13+ cere notificarea pentru serviciul de citire; " +
                    "fără ea, sistemul îl poate opri mai devreme."
            },
            ok = enabled,
            actionLabel = if (enabled) null else "Deschide",
            action = if (enabled) null else Action.NOTIFICATION_SETTINGS
        )
    }

    private fun storageCheck(context: Context): Check {
        val cache = sizeOf(context.cacheDir)
        val files = sizeOf(context.filesDir)
        val total = cache + files
        return Check(
            title = context.getString(R.string.storage_title, context.getString(R.string.app_name)),
            detail = "Cache ${format(cache)}, date ${format(files)}. " +
                "Doar fișierele proprii — o aplicație nu poate șterge cache-ul altora.",
            ok = total < 50L * 1024 * 1024,
            actionLabel = if (cache > 0) "GOLEȘTE CACHE" else null,
            action = if (cache > 0) Action.CLEAR_CACHE else null
        )
    }

    private fun historyCheck(count: Int): Check = Check(
        title = "Istoric oferte",
        detail = "$count oferte salvate, din maximum 500. Statisticile de pe " +
            "ecranul principal se calculează din ele.",
        ok = count < 450,
        actionLabel = if (count > 0) "GOLEȘTE ISTORICUL" else null,
        action = if (count > 0) Action.CLEAR_HISTORY else null
    )

    data class Memory(val totalBytes: Long, val availableBytes: Long) {
        val usedBytes: Long get() = totalBytes - availableBytes
    }

    data class KillResult(
        val appsTargeted: Int,
        val freeBefore: Long,
        val freeAfter: Long
    ) {
        val gained: Long get() = freeAfter - freeBefore
    }

    /**
     * Apps that must never be stopped: the two RideGo reads, RideGo itself,
     * and the launcher. Killing the driver apps mid-shift would be worse than
     * any amount of memory it could free.
     */
    private val PROTECTED = setOf(
        "com.tripworth.app",
        "com.ubercab.driver",
        "ee.mtakso.driver",
        "com.ubercab",
        "com.google.android.apps.maps",
        "com.waze"
    )

    /** Common background apps — fixed list avoids QUERY_ALL_PACKAGES on Play Store. */
    private val OPTIONAL_KILL_TARGETS = listOf(
        "com.facebook.katana",
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.snapchat.android",
        "com.spotify.music"
    ).filterNot { PROTECTED.contains(it) }

    fun memory(context: Context): Memory {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return Memory(totalBytes = info.totalMem, availableBytes = info.availMem)
    }

    /**
     * Fixed package list for optional memory cleanup — no installed-app scan.
     *
     * Android 11+ blocks broad package visibility; [killBackgroundProcesses]
     * still works per package name when the app is installed.
     */
    fun killableApps(@Suppress("UNUSED_PARAMETER") context: Context): List<String> =
        OPTIONAL_KILL_TARGETS

    /**
     * Asks Android to stop background processes of each app. The system may
     * decline, and anything with work pending simply restarts — which is why
     * the before/after figures matter more than the app count.
     */
    fun killBackgroundApps(context: Context): KillResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = memory(context).availableBytes
        val targets = killableApps(context)
        targets.forEach { runCatching { am.killBackgroundProcesses(it) } }
        Thread.sleep(600) // let the system settle before re-reading
        val after = memory(context).availableBytes
        return KillResult(targets.size, before, after)
    }

    fun isBatteryExempt(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun clearCache(context: Context): Long {
        val before = sizeOf(context.cacheDir)
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        return before
    }

    private fun sizeOf(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    fun format(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
