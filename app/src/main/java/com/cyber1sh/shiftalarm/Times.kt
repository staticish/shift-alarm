package com.cyber1sh.shiftalarm

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Times {
    /** "2026-10-25T08:00:00+0100": an offset without the colon, which ISO parsing refuses. */
    private val offsetNoColon = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss][.SSS]xx")

    /**
     * Reads one of Ubeya's time values. A value without an offset is a wall-clock time in [zone]:
     * the crew web app shows bookings' times as they are, in the phone's own time zone. A bare time
     * of day is placed on [date].
     */
    fun parse(raw: String?, zone: ZoneId, date: String? = null): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s == "null") return null
        s.toLongOrNull()?.let { return if (it > 100_000_000_000L) it else it * 1000L }
        val iso = if (s.length > 10 && s[10] == ' ') s.substring(0, 10) + "T" + s.substring(11) else s
        runCatching { return OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
        runCatching { return OffsetDateTime.parse(iso, offsetNoColon).toInstant().toEpochMilli() }
        runCatching { return LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli() }
        if (date != null) {
            val day = runCatching { LocalDate.parse(date.trim().take(10)) }.getOrNull()
            val time = runCatching { LocalTime.parse(s) }.getOrNull()
            if (day != null && time != null) return day.atTime(time).atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    /** The first [hour]:00 strictly after [after], in [after]'s zone. */
    fun nextAt(after: ZonedDateTime, hour: Int): ZonedDateTime {
        val today = after.toLocalDate().atTime(hour, 0).atZone(after.zone)
        return if (today.isAfter(after)) today else after.toLocalDate().plusDays(1).atTime(hour, 0).atZone(after.zone)
    }
}
