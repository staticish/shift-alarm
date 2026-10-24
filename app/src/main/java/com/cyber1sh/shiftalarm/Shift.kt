package com.cyber1sh.shiftalarm

import org.json.JSONArray
import org.json.JSONObject

/** One booked shift, in absolute milliseconds. */
data class Shift(val start: Long, val end: Long, val title: String) {

    /** When this shift's alarm rings: [LEAD_MS] before it starts. */
    val alarmAt: Long get() = start - LEAD_MS

    fun toJson(): JSONObject = JSONObject().put("start", start).put("end", end).put("title", title)

    companion object {
        /** An hour and a half before the shift starts. */
        const val LEAD_MS = 90L * 60L * 1000L

        /** An alarm this close to now counts as already gone. */
        const val MARGIN_MS = 30L * 1000L

        /** The earliest shift whose alarm time is still ahead of [now]. */
        fun next(shifts: List<Shift>, now: Long): Shift? = shifts.filter { it.alarmAt > now + MARGIN_MS }.minByOrNull { it.start }

        fun fromJson(o: JSONObject?): Shift? {
            if (o == null) return null
            val start = o.optLong("start")
            if (start <= 0L) return null
            return Shift(start, o.optLong("end", start), o.optString("title", ""))
        }

        fun fromJson(raw: String?): Shift? = raw?.let { runCatching { JSONObject(it) }.getOrNull() }?.let { fromJson(it) }

        fun listToJson(shifts: List<Shift>): String = JSONArray().apply { shifts.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(raw: String?): List<Shift> {
            if (raw.isNullOrBlank()) return emptyList()
            val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            return (0 until arr.length()).mapNotNull { fromJson(arr.optJSONObject(it)) }
        }
    }
}
