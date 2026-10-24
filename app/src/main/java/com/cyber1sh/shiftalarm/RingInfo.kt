package com.cyber1sh.shiftalarm

import android.content.Context
import android.content.Intent

/** What is ringing: a shift's alarm, a snoozed one, or the test alarm (no shift). */
data class RingInfo(val kind: String, val shift: Shift?) {

    fun headline(c: Context): String = if (shift == null) "Test alarm" else "Your shift starts at ${Fmt.time(c, shift.start)}"

    fun detail(c: Context): String {
        if (shift == null) return "This is how your shift alarm rings."
        return listOf(Fmt.dayWord(c, shift.start), shift.title).filter { it.isNotBlank() }.joinToString(" · ")
    }

    fun put(intent: Intent): Intent = intent.putExtra(EXTRA_KIND, kind).putExtra(EXTRA_SHIFT, shift?.toJson()?.toString())

    companion object {
        const val SHIFT = "shift"
        const val SNOOZE = "snooze"
        const val TEST = "test"

        private const val EXTRA_KIND = "ring_kind"
        private const val EXTRA_SHIFT = "ring_shift"

        fun from(intent: Intent?): RingInfo =
            RingInfo(intent?.getStringExtra(EXTRA_KIND) ?: TEST, Shift.fromJson(intent?.getStringExtra(EXTRA_SHIFT)))
    }
}
