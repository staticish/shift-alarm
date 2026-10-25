package com.cyber1sh.shiftalarm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the app remembers. It is kept in device-protected storage, so after a restart the alarm can
 * be re-armed before the phone is unlocked for the first time (its PIN screen).
 *
 * Writes use commit(): they mostly happen in receivers and services that may be stopped right after.
 */
class Store(context: Context) {
    val prefs: SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The shift whose alarm is armed, or null when none is. */
    var alarm: Shift?
        get() = Shift.fromJson(prefs.getString(ALARM, null))
        set(value) {
            prefs.edit().putString(ALARM, value?.toJson()?.toString()).commit()
        }

    /** Shifts from the last good check whose alarms were still ahead. */
    var upcoming: List<Shift>
        get() = Shift.listFromJson(prefs.getString(UPCOMING, null))
        set(value) {
            prefs.edit().putString(UPCOMING, Shift.listToJson(value)).commit()
        }

    /** A snoozed ring: when it rings again, and what for (null shift = the test alarm). */
    val snoozeAt: Long get() = prefs.getLong(SNOOZE_AT, 0L)
    val snoozeRing: RingInfo? get() = if (snoozeAt > 0L) RingInfo(RingInfo.SNOOZE, Shift.fromJson(prefs.getString(SNOOZE_SHIFT, null))) else null

    fun setSnooze(at: Long, shift: Shift?) {
        prefs.edit().putLong(SNOOZE_AT, at).putString(SNOOZE_SHIFT, shift?.toJson()?.toString()).commit()
    }

    fun clearSnooze() {
        prefs.edit().remove(SNOOZE_AT).remove(SNOOZE_SHIFT).commit()
    }

    /** Start times of shifts whose alarm was skipped by hand; checks leave them alone. */
    var skipped: Set<Long>
        get() = prefs.getString(SKIPPED, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet()
        set(value) {
            val keep = value.filter { it > System.currentTimeMillis() - 24L * 60L * 60L * 1000L }
            prefs.edit().putString(SKIPPED, keep.joinToString(",")).commit()
        }

    var nextCheckAt: Long
        get() = prefs.getLong(NEXT_CHECK, 0L)
        set(value) {
            prefs.edit().putLong(NEXT_CHECK, value).commit()
        }

    var nextUpdateAt: Long
        get() = prefs.getLong(NEXT_UPDATE, 0L)
        set(value) {
            prefs.edit().putLong(NEXT_UPDATE, value).commit()
        }

    val lastCheckAt: Long get() = prefs.getLong(LAST_AT, 0L)
    val lastCheckOk: Boolean get() = prefs.getBoolean(LAST_OK, false)
    val lastCheckText: String get() = prefs.getString(LAST_TEXT, "").orEmpty()
    val lastGoodAt: Long get() = prefs.getLong(LAST_GOOD, 0L)

    fun setLastCheck(at: Long, ok: Boolean, text: String) {
        val edit = prefs.edit().putLong(LAST_AT, at).putBoolean(LAST_OK, ok).putString(LAST_TEXT, text)
        if (ok) edit.putLong(LAST_GOOD, at)
        edit.commit()
    }

    var retries: Int
        get() = prefs.getInt(RETRIES, 0)
        set(value) {
            prefs.edit().putInt(RETRIES, value).commit()
        }

    /** A check fell due while the phone was still locked after a restart; it runs after unlock. */
    var checkPending: Boolean
        get() = prefs.getBoolean(PENDING, false)
        set(value) {
            prefs.edit().putBoolean(PENDING, value).commit()
        }

    var checking: Boolean
        get() = prefs.getBoolean(CHECKING, false)
        set(value) {
            prefs.edit().putBoolean(CHECKING, value).commit()
        }

    /** 1 signed in, 0 signed out, -1 not known yet. */
    var signedIn: Int
        get() = prefs.getInt(SIGNED_IN, -1)
        set(value) {
            prefs.edit().putInt(SIGNED_IN, value).commit()
        }

    var who: String
        get() = prefs.getString(WHO, "").orEmpty()
        set(value) {
            prefs.edit().putString(WHO, value).commit()
        }

    /** The newest force stop Android recorded for this app (see [Stops]), or 0. */
    var lastForceStop: Long
        get() = prefs.getLong(LAST_FORCE_STOP, 0L)
        set(value) {
            prefs.edit().putLong(LAST_FORCE_STOP, value).commit()
        }

    /** Who made that stop, in a word: "vivo" or "Android". */
    var lastForceStopBy: String
        get() = prefs.getString(LAST_FORCE_STOP_BY, "").orEmpty()
        set(value) {
            prefs.edit().putString(LAST_FORCE_STOP_BY, value).commit()
        }

    /** The newest process-exit record already looked at, so each stop is logged once. */
    var lastExitSeen: Long
        get() = prefs.getLong(LAST_EXIT_SEEN, 0L)
        set(value) {
            prefs.edit().putLong(LAST_EXIT_SEEN, value).commit()
        }

    val history: List<String>
        get() {
            val arr = runCatching { JSONArray(prefs.getString(HISTORY, "[]")) }.getOrNull() ?: return emptyList()
            return (0 until arr.length()).map { arr.optString(it) }
        }

    /** One line in the history the main screen shows, newest first. Also goes to logcat. */
    fun log(line: String) {
        Log.w(TAG, line)
        synchronized(LOCK) {
            val stamped = SimpleDateFormat("d MMM HH:mm:ss", Locale.UK).format(Date()) + "  " + line
            val lines = listOf(stamped) + history.take(HISTORY_MAX - 1)
            prefs.edit().putString(HISTORY, JSONArray(lines).toString()).commit()
        }
    }

    companion object {
        const val TAG = "ShiftAlarm"
        private const val FILE = "state"
        private const val HISTORY_MAX = 40
        private val LOCK = Any()

        private const val ALARM = "alarm"
        private const val UPCOMING = "upcoming"
        private const val SNOOZE_AT = "snooze_at"
        private const val SNOOZE_SHIFT = "snooze_shift"
        private const val NEXT_CHECK = "next_check_at"
        private const val NEXT_UPDATE = "next_update_at"
        private const val SKIPPED = "skipped"
        private const val LAST_AT = "last_check_at"
        private const val LAST_OK = "last_check_ok"
        private const val LAST_TEXT = "last_check_text"
        private const val LAST_GOOD = "last_good_at"
        private const val RETRIES = "retries"
        private const val PENDING = "check_pending"
        private const val CHECKING = "checking"
        private const val SIGNED_IN = "signed_in"
        private const val WHO = "who"
        private const val LAST_FORCE_STOP = "last_force_stop"
        private const val LAST_EXIT_SEEN = "last_exit_seen"
        private const val LAST_FORCE_STOP_BY = "last_force_stop_by"
        private const val HISTORY = "history"
    }
}
