package com.cyber1sh.shiftalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.ZoneId

/**
 * Every alarm the app sets.
 * - The shift alarm, its snooze and the test alarm use setAlarmClock: a real alarm clock, exempt
 *   from Doze and from app-standby limits, and shown in the status bar and on the lock screen.
 * - The nightly check is an exact allow-while-idle alarm at 03:00, re-armed for the next day as soon
 *   as it fires, so the chain never depends on the check succeeding.
 * Android drops all of them on a restart; [rearm] puts them back from [Store].
 */
object Alarms {
    /** 03:00: late enough that "the next shift" is that day's shift. */
    const val CHECK_HOUR = 3
    const val RETRY_MS = 15L * 60L * 1000L

    /** Between nightly checks, look again this often, so a manager's change reaches the alarm. */
    const val UPDATE_MS = 3L * 60L * 60L * 1000L

    private const val RC_SHIFT = 1
    private const val RC_SNOOZE = 2
    private const val RC_TEST_RING = 3
    private const val RC_CHECK = 4
    private const val RC_RETRY = 5
    private const val RC_TEST_CHECK = 6
    private const val RC_SHOW = 7
    private const val RC_UPDATE = 8

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun manager(c: Context): AlarmManager = c.getSystemService(AlarmManager::class.java)

    fun canExact(c: Context): Boolean = Build.VERSION.SDK_INT < 31 || manager(c).canScheduleExactAlarms()

    // The nightly check

    /** Arms the check at the first 03:00 after [after]. */
    fun armDailyCheck(c: Context, after: Long = System.currentTimeMillis()) {
        val at = Times.nextAt(Instant.ofEpochMilli(after).atZone(ZoneId.systemDefault()), CHECK_HOUR).toInstant().toEpochMilli()
        exact(c, at, checkIntent(c, RC_CHECK, AlarmReceiver.REASON_DAILY))
        Store(c).nextCheckAt = at
    }

    fun armRetry(c: Context) = exact(c, System.currentTimeMillis() + RETRY_MS, checkIntent(c, RC_RETRY, AlarmReceiver.REASON_RETRY))

    fun cancelRetry(c: Context) = cancel(c, checkIntent(c, RC_RETRY, AlarmReceiver.REASON_RETRY))

    /** The next look for changed shifts, [delayMs] from now. */
    fun armUpdate(c: Context, delayMs: Long = UPDATE_MS) {
        val at = System.currentTimeMillis() + delayMs
        exact(c, at, checkIntent(c, RC_UPDATE, AlarmReceiver.REASON_UPDATE))
        Store(c).nextUpdateAt = at
    }

    /** The "run the nightly check in 2 minutes" button: the same path the 03:00 alarm takes. */
    fun armTestCheck(c: Context, delayMs: Long) =
        exact(c, System.currentTimeMillis() + delayMs, checkIntent(c, RC_TEST_CHECK, AlarmReceiver.REASON_TEST))

    // Ringing

    fun armShift(c: Context, shift: Shift) {
        clock(c, shift.alarmAt, ringIntent(c, RC_SHIFT, RingInfo(RingInfo.SHIFT, shift)))
        Store(c).alarm = shift
    }

    fun cancelShift(c: Context) {
        cancel(c, ringIntent(c, RC_SHIFT, RingInfo(RingInfo.SHIFT, null)))
        Store(c).alarm = null
    }

    fun armSnooze(c: Context, at: Long, ring: RingInfo) {
        clock(c, at, ringIntent(c, RC_SNOOZE, RingInfo(RingInfo.SNOOZE, ring.shift)))
        Store(c).setSnooze(at, ring.shift)
    }

    fun armTestRing(c: Context, delayMs: Long) =
        clock(c, System.currentTimeMillis() + delayMs, ringIntent(c, RC_TEST_RING, RingInfo(RingInfo.TEST, null)))

    /** After a shift's alarm rings, arm the following shift's from the last check's list. */
    fun afterShiftRang(c: Context, rang: Shift?) {
        val store = Store(c)
        val next = Shift.next(store.upcoming.filter { it != rang }, System.currentTimeMillis())
        if (next != null) armShift(c, next) else store.alarm = null
    }

    /**
     * Puts every alarm back from [Store]: after a restart (before and after the first unlock), an
     * update, a clock or time-zone change, and whenever the app is opened. [forceDaily] recomputes
     * the 03:00 check even if one looks armed (the clock moved).
     */
    fun rearm(c: Context, why: String, forceDaily: Boolean = false) {
        val store = Store(c)
        val now = System.currentTimeMillis()
        if (forceDaily || store.nextCheckAt <= now || !dailyArmed(c)) armDailyCheck(c)
        // A missed look (phone off, app stopped) comes soon rather than three hours on.
        if (store.nextUpdateAt <= now) armUpdate(c, RETRY_MS)

        val alarm = store.alarm
        if (alarm != null) {
            when {
                alarm.alarmAt > now -> armShift(c, alarm)
                // The phone was off when it should have rung, and the shift is still ahead: ring now.
                alarm.start > now -> {
                    clock(c, now + 5_000L, ringIntent(c, RC_SHIFT, RingInfo(RingInfo.SHIFT, alarm)))
                    store.log("$why: the ${Fmt.stamp(alarm.alarmAt)} alarm was missed while the phone was off; ringing now")
                }
                else -> afterShiftRang(c, alarm)
            }
        }

        val snooze = store.snoozeRing
        if (snooze != null) {
            val at = store.snoozeAt
            when {
                at > now -> clock(c, at, ringIntent(c, RC_SNOOZE, snooze))
                snooze.shift != null && snooze.shift.start > now -> clock(c, now + 5_000L, ringIntent(c, RC_SNOOZE, snooze))
                else -> store.clearSnooze()
            }
        }
    }

    private fun ringIntent(c: Context, code: Int, ring: RingInfo): PendingIntent {
        val intent = ring.put(Intent(c, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_RING))
        return PendingIntent.getBroadcast(c, code, intent, FLAGS)
    }

    private fun checkIntent(c: Context, code: Int, reason: String): PendingIntent {
        val intent = Intent(c, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_CHECK).putExtra(AlarmReceiver.EXTRA_REASON, reason)
        return PendingIntent.getBroadcast(c, code, intent, FLAGS)
    }

    /**
     * Whether the 03:00 check's PendingIntent still exists. A restart or a force stop deletes it.
     * It outlives a fired alarm, which is why [rearm] also looks at [Store.nextCheckAt].
     */
    private fun dailyArmed(c: Context): Boolean {
        // filterEquals ignores extras, so a bare intent with the same action finds the armed one.
        val probe = Intent(c, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_CHECK)
        return PendingIntent.getBroadcast(c, RC_CHECK, probe, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) != null
    }

    private fun clock(c: Context, at: Long, op: PendingIntent) {
        val show = PendingIntent.getActivity(c, RC_SHOW, Intent(c, MainActivity::class.java), FLAGS)
        try {
            manager(c).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
        } catch (e: SecurityException) {
            // "Alarms and reminders" was turned off. An inexact alarm may ring late, so say so.
            manager(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            Store(c).log("exact alarms are off; the alarm may ring late")
            Notices.problem(c, "Allow alarms and reminders", "Shift alarm needs it to ring on time. Open the app to turn it on.", Notices.Open.MAIN)
        }
    }

    private fun exact(c: Context, at: Long, op: PendingIntent) {
        val manager = manager(c)
        if (canExact(c)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op) else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
    }

    private fun cancel(c: Context, pi: PendingIntent) {
        manager(c).cancel(pi)
        pi.cancel()
    }
}
