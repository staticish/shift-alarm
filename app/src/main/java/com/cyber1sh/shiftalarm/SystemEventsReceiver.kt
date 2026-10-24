package com.cyber1sh.shiftalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Android forgets every alarm on a restart. This puts them back: first at LOCKED_BOOT_COMPLETED,
 * before the PIN is entered, so a shift alarm still rings on a phone that restarted overnight; then
 * again at BOOT_COMPLETED (after unlock), which may also run a check that was missed.
 */
class SystemEventsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Alarms.rearm(context, "restart", forceDaily = true)
                store.log("restarted: alarms re-armed before unlock")
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val why = if (intent.action == Intent.ACTION_BOOT_COMPLETED) "unlocked after restart" else "app updated"
                Alarms.rearm(context, why, forceDaily = true)
                val stale = System.currentTimeMillis() - store.lastGoodAt > STALE_MS
                store.log("$why: alarms re-armed" + if (store.checkPending) "; running the check that was missed" else "")
                if (store.checkPending || (stale && store.signedIn == 1)) CheckService.start(context, AlarmReceiver.REASON_BOOT, goAsync())
            }
            Intent.ACTION_TIME_CHANGED -> {
                Alarms.rearm(context, "clock changed", forceDaily = true)
                store.log("clock changed: alarms re-armed")
            }
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // Shift times are wall-clock times, so a new zone moves them: read them again.
                Alarms.rearm(context, "time zone changed", forceDaily = true)
                store.log("time zone changed: checking Ubeya again")
                if (store.signedIn == 1) CheckService.start(context, AlarmReceiver.REASON_ZONE, goAsync())
            }
        }
    }

    companion object {
        /** A restart this long after the last good check runs a fresh one. */
        private const val STALE_MS = 20L * 60L * 60L * 1000L
    }
}
