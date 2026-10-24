package com.cyber1sh.shiftalarm

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Opens the settings pages that decide whether vivo lets this app run in the background. The
 * actions are the ones the Y20i's com.vivo.permissionmanager registers (read with dumpsys on
 * 24-Oct-2026, Funtouch 11.5); each opener falls back to the app's own system page. None of these
 * settings can be changed by an app: they are yours to switch on.
 */
object Vivo {
    /** "Autostart": without it vivo withholds the restart broadcast and stops background starts. */
    fun openAutostart(c: Context): Boolean =
        first(
            c,
            Intent("com.iqoo.secure.BGSTARTUPMANAGER"),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            appDetails(c),
        )

    /** "Display on lock screen": lets the ringing screen show over the lock screen. */
    fun openLockScreen(c: Context): Boolean =
        first(
            c,
            Intent("com.iqoo.secure.SCREENLOCKACTION"),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.ScreenLockedActionControlActivity")),
            appDetails(c),
        )

    /** "Floating window": lets the ringing screen open while another app is in use. */
    fun openFloatingWindow(c: Context): Boolean =
        first(
            c,
            Intent("com.iqoo.secure.FLOATWINDOWMANAGER"),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName)),
            appDetails(c),
        )

    /** Battery: Android's own "let it always run in the background?" question, then its list. */
    @SuppressLint("BatteryLife")
    fun openBattery(c: Context): Boolean =
        first(
            c,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.packageName)),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            appDetails(c),
        )

    fun openNotifications(c: Context): Boolean =
        first(c, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName), appDetails(c))

    fun openExactAlarms(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 31) {
            first(c, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + c.packageName)), appDetails(c))
        } else {
            first(c, appDetails(c))
        }

    fun openFullScreen(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 34) {
            first(c, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + c.packageName)), appDetails(c))
        } else {
            first(c, appDetails(c))
        }

    private fun appDetails(c: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))

    private fun first(c: Context, vararg intents: Intent): Boolean {
        for (intent in intents) {
            try {
                c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                // Not on this Funtouch version, or not open to other apps: try the next.
            }
        }
        return false
    }
}
