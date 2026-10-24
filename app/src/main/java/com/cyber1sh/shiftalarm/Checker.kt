package com.cyber1sh.shiftalarm

import android.content.Context
import java.io.File
import java.time.ZoneId

/**
 * One check: read the bookings, pick the next shift whose alarm is still ahead, arm its alarm.
 * If Ubeya cannot be read, the alarm already armed stays as it is.
 */
class Checker(private val context: Context) {
    private val store = Store(context)

    fun run(reason: String) =
        synchronized(LOCK) {
            store.checking = true
            try {
                runLocked(reason)
            } finally {
                store.checking = false
            }
        }

    private fun runLocked(reason: String) {
        val now = System.currentTimeMillis()
        store.checkPending = false
        if (reason == AlarmReceiver.REASON_DAILY) store.retries = 0
        val ubeya = Ubeya(context)
        try {
            val json = ubeya.bookingsJson()
            keepRaw(json)
            // Venue names only matter for display, so a failure here changes nothing else.
            val places = if (Bookings.needsPlaces(json)) runCatching { Bookings.places(ubeya.locationsJson()) }.getOrDefault(emptyMap()) else emptyMap()
            val shifts = Bookings.parse(json, ZoneId.systemDefault(), places)
            val next = Shift.next(shifts, now)
            store.upcoming = shifts.filter { it.alarmAt > now + Shift.MARGIN_MS }
            store.signedIn = 1
            store.retries = 0
            Alarms.cancelRetry(context)
            if (next != null) Alarms.armShift(context, next) else Alarms.cancelShift(context)

            val read = "${Bookings.count(json)} bookings, ${shifts.size} with times"
            if (next != null) {
                val title = "Alarm set for ${Fmt.at(context, next.alarmAt)}"
                val text = "90 minutes before your ${Fmt.time(context, next.start)} shift" + if (next.title.isNotBlank()) " · ${next.title}" else ""
                store.setLastCheck(now, true, "$title, $text.")
                store.log("$reason: $read; alarm ${Fmt.stamp(next.alarmAt)} for the ${Fmt.stamp(next.start)} shift")
                if (reason != AlarmReceiver.REASON_MANUAL) Notices.result(context, title, text)
            } else {
                store.setLastCheck(now, true, "No upcoming shifts on Ubeya, so no alarm is set.")
                store.log("$reason: $read; no shift ahead, no alarm")
                if (reason != AlarmReceiver.REASON_MANUAL) Notices.result(context, "No shift alarm set", "Ubeya shows no upcoming shifts.")
            }
            Notices.clearProblem(context)
        } catch (e: Ubeya.SignedOut) {
            store.signedIn = 0
            store.setLastCheck(now, false, "Signed out of Ubeya. Sign in again to keep the alarm up to date.")
            store.log("$reason: signed out of Ubeya")
            Notices.problem(context, "Sign in to Ubeya again", "Shift alarm could not read your shifts. ${kept()}", Notices.Open.LOGIN)
        } catch (e: Exception) {
            val tries = store.retries + 1
            store.retries = tries
            val again = tries <= MAX_RETRIES
            if (again) Alarms.armRetry(context)
            val why = e.message ?: e.javaClass.simpleName
            store.setLastCheck(now, false, "Couldn't read Ubeya ($why)." + if (again) " Trying again at ${Fmt.time(context, now + Alarms.RETRY_MS)}." else "")
            store.log("$reason: failed ($why), attempt $tries")
            if (!again) Notices.problem(context, "Couldn't read your Ubeya shifts", "Tried $tries times. ${kept()}", Notices.Open.MAIN)
        } finally {
            ubeya.cookieNotes.forEach { store.log("Ubeya set cookie $it") }
        }
    }

    private fun kept(): String {
        val alarm = store.alarm
        return if (alarm != null && alarm.alarmAt > System.currentTimeMillis()) "Your alarm for ${Fmt.at(context, alarm.alarmAt)} is still set." else "No alarm is set."
    }

    /**
     * The last answer, for checking what Ubeya sends: the app's own folder, which other apps
     * cannot read and which goes with an uninstall.
     */
    private fun keepRaw(json: String) {
        runCatching { File(context.getExternalFilesDir(null), "last-bookings.json").writeText(json) }
    }

    companion object {
        private val LOCK = Any()

        /** Every 15 minutes from 03:00 until about 05:00. */
        const val MAX_RETRIES = 8
    }
}
