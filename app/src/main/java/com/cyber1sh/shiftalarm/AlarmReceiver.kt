package com.cyber1sh.shiftalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager

/** Every alarm this app sets lands here: a ring, or a schedule check. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RING -> ring(context, RingInfo.from(intent))
            ACTION_CHECK -> check(context, intent.getStringExtra(EXTRA_REASON) ?: REASON_DAILY)
        }
    }

    private fun ring(context: Context, ring: RingInfo) {
        when (ring.kind) {
            RingInfo.SHIFT -> Alarms.afterShiftRang(context, ring.shift)
            RingInfo.SNOOZE -> Store(context).clearSnooze()
        }
        RingService.start(context, ring)
    }

    private fun check(context: Context, reason: String) {
        val store = Store(context)
        // Tomorrow's run first, so the chain holds whatever happens to this one. Five minutes on, in
        // case this alarm was delivered a moment before 03:00.
        if (reason == REASON_DAILY) Alarms.armDailyCheck(context, System.currentTimeMillis() + 5L * 60L * 1000L)
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked) {
            // The Ubeya session is in storage that opens with the PIN. BOOT_COMPLETED runs it later.
            store.checkPending = true
            store.log("$reason check due while the phone is locked after a restart; it runs after unlock")
            return
        }
        CheckService.start(context, reason, goAsync())
    }

    companion object {
        const val ACTION_RING = "com.cyber1sh.shiftalarm.RING"
        const val ACTION_CHECK = "com.cyber1sh.shiftalarm.CHECK"
        const val EXTRA_REASON = "reason"

        const val REASON_DAILY = "nightly"
        const val REASON_RETRY = "retry"
        const val REASON_TEST = "test run"
        const val REASON_MANUAL = "check now"
        const val REASON_SIGNED_IN = "signed in"
        const val REASON_BOOT = "after restart"
        const val REASON_ZONE = "time zone change"
    }
}
