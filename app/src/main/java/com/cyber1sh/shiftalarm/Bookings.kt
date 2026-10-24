package com.cyber1sh.shiftalarm

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Turns the answer of Ubeya's `GET crew.api.ubeya.com/bookings` into shifts.
 *
 * Seen live on 24-Oct-2026: `{data: [booking]}`, each booking with
 * - `startTimeUTC` / `endTimeUTC`: "2026-10-24 08:00:00", UTC without a zone mark;
 * - `startTime` / `endTime`: "2026-10-24 09:00:00", the same moment as wall-clock time at the
 *   venue (what the crew web app shows);
 * - `date`, `locationId`, and `project.{name, date, locationId}`; `project.name` can be null.
 * The UTC pair is used when present, so the phone's own time-zone setting cannot move an alarm.
 */
object Bookings {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** [places] names venues by location id, for bookings whose project has no name. */
    fun parse(json: String, zone: ZoneId, places: Map<Long, String> = emptyMap()): List<Shift> {
        val list = items(json)
        val shifts = ArrayList<Shift>()
        for (i in 0 until list.length()) {
            val booking = list.optJSONObject(i) ?: continue
            val project = booking.optJSONObject("project")
            val date = booking.text("date") ?: project?.text("date")
            val start = time(booking, "start", zone, date) ?: continue
            var end = time(booking, "end", zone, date) ?: start
            if (end < start) end += DAY_MS
            val title = project?.text("name") ?: placeOf(booking, places) ?: booking.text("name") ?: ""
            shifts += Shift(start, end, title)
        }
        return shifts.distinctBy { it.start to it.title }.sortedBy { it.start }
    }

    /** How many bookings the answer held, readable or not. */
    fun count(json: String): Int = items(json).length()

    /** Whether any booking lacks a project name, so the venue names are worth fetching. */
    fun needsPlaces(json: String): Boolean {
        val list = items(json)
        return (0 until list.length()).any { list.optJSONObject(it)?.optJSONObject("project")?.text("name") == null }
    }

    /** Ubeya's `GET /locations` answer, `{data: [{id, normalizedName}]}`, as id to name. */
    fun places(json: String): Map<Long, String> {
        val list = items(json)
        val out = HashMap<Long, String>()
        for (i in 0 until list.length()) {
            val place = list.optJSONObject(i) ?: continue
            val id = place.optLong("id")
            val name = place.text("normalizedName") ?: place.text("name") ?: continue
            if (id > 0L) out[id] = name
        }
        return out
    }

    private fun time(booking: JSONObject, which: String, zone: ZoneId, date: String?): Long? =
        Times.parse(booking.text("${which}TimeUTC"), ZoneOffset.UTC) ?: Times.parse(booking.text("${which}Time"), zone, date)

    private fun placeOf(booking: JSONObject, places: Map<Long, String>): String? {
        if (places.isEmpty()) return null
        val ids = listOf(booking.optLong("locationId"), booking.optJSONObject("project")?.optLong("locationId") ?: 0L)
        return ids.filter { it > 0L }.firstNotNullOfOrNull { places[it] }
    }

    private fun items(json: String): JSONArray =
        when (val root = runCatching { JSONTokener(json).nextValue() }.getOrNull()) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("data") ?: root.optJSONArray("bookings") ?: JSONArray()
            else -> JSONArray()
        }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }
}
