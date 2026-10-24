package com.cyber1sh.shiftalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimesTest {
    private val london = ZoneId.of("Europe/London")

    private fun ms(text: String): Long = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun wallClockTimeIsReadInThePhonesZone() {
        // 27 Oct 2026 is after the clocks go back, so London is on GMT (+00:00).
        assertEquals(ms("2026-10-27T08:00:00Z"), Times.parse("2026-10-27 08:00:00", london))
        // 20 Oct is still BST (+01:00).
        assertEquals(ms("2026-10-20T07:00:00Z"), Times.parse("2026-10-20 08:00:00", london))
        assertEquals(ms("2026-10-20T07:00:00Z"), Times.parse("2026-10-20T08:00", london))
    }

    @Test
    fun offsetsAndUtcAreTakenAsGiven() {
        assertEquals(ms("2026-10-20T07:00:00Z"), Times.parse("2026-10-20T07:00:00.000Z", london))
        assertEquals(ms("2026-10-20T07:00:00Z"), Times.parse("2026-10-20T08:00:00+01:00", london))
        assertEquals(ms("2026-10-20T07:00:00Z"), Times.parse("2026-10-20T08:00:00+0100", london))
    }

    @Test
    fun epochNumbersInSecondsOrMilliseconds() {
        val at = ms("2026-10-20T07:00:00Z")
        assertEquals(at, Times.parse((at / 1000).toString(), london))
        assertEquals(at, Times.parse(at.toString(), london))
    }

    @Test
    fun aBareTimeGoesOnTheBookingsDate() {
        assertEquals(ms("2026-10-20T07:00:00Z"), Times.parse("08:00", london, "2026-10-20"))
        assertEquals(ms("2026-10-20T07:00:00Z"), Times.parse("08:00:00", london, "2026-10-20 00:00:00"))
        assertNull(Times.parse("08:00", london))
    }

    @Test
    fun junkIsNull() {
        assertNull(Times.parse(null, london))
        assertNull(Times.parse("", london))
        assertNull(Times.parse("null", london))
        assertNull(Times.parse("soon", london))
    }

    @Test
    fun nextCheckIsTonightOrTomorrowAt21() {
        val before = ZonedDateTime.parse("2026-10-24T18:30:00+01:00[Europe/London]")
        assertEquals(ZonedDateTime.parse("2026-10-24T21:00:00+01:00[Europe/London]"), Times.nextAt(before, 21))
        val exactly = ZonedDateTime.parse("2026-10-24T21:00:00+01:00[Europe/London]")
        assertEquals(ZonedDateTime.parse("2026-10-25T21:00:00Z[Europe/London]"), Times.nextAt(exactly, 21))
    }

    @Test
    fun the3amCheckLandsRightOnTheNightTheClocksGoBack() {
        // From 21:56 BST on Sat 24 Oct the next 03:00 is Sun 25 Oct, by then GMT.
        val evening = ZonedDateTime.parse("2026-10-24T21:56:00+01:00[Europe/London]")
        assertEquals(ms("2026-10-25T03:00:00Z"), Times.nextAt(evening, 3).toInstant().toEpochMilli())
        // The receiver re-arms from five minutes after delivery: a day later.
        val fired = ZonedDateTime.parse("2026-10-25T03:05:00Z[Europe/London]")
        assertEquals(ms("2026-10-26T03:00:00Z"), Times.nextAt(fired, 3).toInstant().toEpochMilli())
    }

    @Test
    fun nextCheckSurvivesTheClocksGoingBack() {
        // The night of 24 to 25 Oct 2026 has a 25-hour day; 21:00 on the 25th is GMT.
        val after = ZonedDateTime.parse("2026-10-24T21:05:00+01:00[Europe/London]")
        val next = Times.nextAt(after, 21)
        assertEquals(21, next.hour)
        assertEquals(ms("2026-10-25T21:00:00Z"), next.toInstant().toEpochMilli())
    }
}
