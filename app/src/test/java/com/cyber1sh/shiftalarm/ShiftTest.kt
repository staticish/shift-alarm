package com.cyber1sh.shiftalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZonedDateTime

class ShiftTest {
    private fun ms(text: String): Long = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    private val nine = ms("2026-10-24T21:00:00+01:00")
    private val todayEarly = Shift(ms("2026-10-24T08:00:00+01:00"), ms("2026-10-24T16:00:00+01:00"), "Today")
    private val todayLate = Shift(ms("2026-10-24T22:00:00+01:00"), ms("2026-10-25T02:00:00+01:00"), "Late tonight")
    private val tomorrow = Shift(ms("2026-10-25T08:00:00Z"), ms("2026-10-25T16:00:00Z"), "Tomorrow")
    private val later = Shift(ms("2026-10-28T10:00:00Z"), ms("2026-10-28T18:00:00Z"), "Later")

    @Test
    fun alarmIsNinetyMinutesBefore() {
        assertEquals(ms("2026-10-25T06:30:00Z"), tomorrow.alarmAt)
    }

    @Test
    fun at21TheNextShiftWithItsAlarmAheadIsChosen() {
        assertEquals(tomorrow, Shift.next(listOf(later, todayEarly, tomorrow), nine))
    }

    @Test
    fun aShiftLateTonightStillCountsIfItsAlarmIsAhead() {
        // 22:00 start, alarm 20:30: already gone at 21:00, so tomorrow's shift wins.
        assertEquals(tomorrow, Shift.next(listOf(todayLate, tomorrow), nine))
        val lateStart = todayLate.copy(start = ms("2026-10-24T23:00:00+01:00"))
        assertEquals(lateStart, Shift.next(listOf(lateStart, tomorrow), nine))
    }

    @Test
    fun at3amTheDaysOwnShiftIsChosen() {
        val three = ms("2026-10-25T03:00:00Z")
        val wednesday = Shift(ms("2026-10-28T16:00:00Z"), ms("2026-10-28T22:00:00Z"), "Wednesday")
        assertEquals(tomorrow, Shift.next(listOf(wednesday, tomorrow), three))
        // A shift starting before 04:30 has its alarm before 03:00: too late, so the next one.
        val early = Shift(ms("2026-10-25T04:00:00Z"), ms("2026-10-25T12:00:00Z"), "Early")
        assertEquals(tomorrow, Shift.next(listOf(early, tomorrow), three))
    }

    @Test
    fun anAlarmUnderThirtySecondsAwayIsSkipped() {
        val now = tomorrow.alarmAt - 10_000L
        assertEquals(later, Shift.next(listOf(tomorrow, later), now))
    }

    @Test
    fun nothingAheadMeansNoAlarm() {
        assertNull(Shift.next(listOf(todayEarly), nine))
        assertNull(Shift.next(emptyList(), nine))
    }

    @Test
    fun roundTripsThroughJson() {
        val list = listOf(tomorrow, later)
        assertEquals(list, Shift.listFromJson(Shift.listToJson(list)))
        assertEquals(tomorrow, Shift.fromJson(tomorrow.toJson().toString()))
        assertNull(Shift.fromJson("not json"))
    }
}
