package com.cyber1sh.shiftalarm

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** Times and days as the main screen and notifications write them. */
object Fmt {
    fun time(c: Context, ms: Long): String {
        val pattern = if (DateFormat.is24HourFormat(c)) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
    }

    fun day(ms: Long): String = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))

    /** "Today", "Tomorrow", or the date. */
    fun dayWord(c: Context, ms: Long): String {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> day(ms)
        }
    }

    /** "today at 21:00", "tomorrow at 06:30", "Sat 25 Oct at 06:30". */
    fun at(c: Context, ms: Long): String {
        val word = dayWord(c, ms)
        val lead = if (word == "Today" || word == "Tomorrow") word.lowercase(Locale.getDefault()) else word
        return "$lead at ${time(c, ms)}"
    }

    /** A compact stamp for the history: "Sat 25 Oct 08:00". */
    fun stamp(ms: Long): String = SimpleDateFormat("EEE d MMM HH:mm", Locale.UK).format(Date(ms))
}
