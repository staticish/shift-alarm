package com.cyber1sh.shiftalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Made-up answers in the shape the crew web app's bundle reads; not real Ubeya data. */
class BookingsTest {
    private val london = ZoneId.of("Europe/London")

    private fun ms(text: String): Long = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun readsTheDataArray() {
        val json =
            """
            {"data":[
              {"id":2,"startTime":"2026-10-28 10:00:00","endTime":"2026-10-28 18:00:00","date":"2026-10-28",
               "project":{"id":20,"name":"Evening shift","date":"2026-10-28 00:00:00"}},
              {"id":1,"startTime":"2026-10-25 08:00:00","endTime":"2026-10-25 16:00:00","date":"2026-10-25",
               "project":{"id":10,"name":"Morning shift","date":"2026-10-25 00:00:00"},"confirmation":{"id":5,"isConfirmed":true}}
            ]}
            """.trimIndent()
        val shifts = Bookings.parse(json, london)
        assertEquals(2, shifts.size)
        assertEquals(Shift(ms("2026-10-25T08:00:00Z"), ms("2026-10-25T16:00:00Z"), "Morning shift"), shifts[0])
        assertEquals("Evening shift", shifts[1].title)
        assertEquals(2, Bookings.count(json))
    }

    @Test
    fun overnightEndMovesToTheNextDay() {
        val json = """{"data":[{"startTime":"22:00","endTime":"02:00","date":"2026-10-20","project":{"name":"Night"}}]}"""
        val shift = Bookings.parse(json, london).single()
        assertEquals(ms("2026-10-20T21:00:00Z"), shift.start)
        assertEquals(ms("2026-10-21T01:00:00Z"), shift.end)
    }

    @Test
    fun bookingsWithoutAStartAreSkippedAndCounted() {
        val json = """{"data":[{"startTime":null,"project":{"name":"Open"}},{"startTime":"2026-10-25T08:00:00Z"}]}"""
        val shifts = Bookings.parse(json, london)
        assertEquals(1, shifts.size)
        assertEquals("", shifts[0].title)
        assertEquals(2, Bookings.count(json))
    }

    @Test
    fun aBareArrayOrJunkDoesNotThrow() {
        assertEquals(1, Bookings.parse("""[{"startTime":"2026-10-25T08:00:00Z"}]""", london).size)
        assertEquals(0, Bookings.parse("<html>sign in</html>", london).size)
        assertEquals(0, Bookings.parse("{}", london).size)
    }

    // The shape seen live on 24-Oct-2026: a wall-clock pair and a UTC pair, and a null project name.
    private val live =
        """
        {"data":[{"id":1,"locationId":9,"startTime":"2026-10-24 09:00:00","endTime":"2026-10-24 18:00:00",
          "startTimeUTC":"2026-10-24 08:00:00","endTimeUTC":"2026-10-24 17:00:00","date":"2026-10-24",
          "project":{"id":2,"name":null,"date":"2026-10-15 00:00:00","locationId":7}}]}
        """.trimIndent()

    @Test
    fun theUtcPairWinsSoAWrongPhoneZoneCannotMoveTheAlarm() {
        val shift = Bookings.parse(live, ZoneId.of("America/New_York")).single()
        assertEquals(ms("2026-10-24T08:00:00Z"), shift.start)
        assertEquals(ms("2026-10-24T17:00:00Z"), shift.end)
    }

    @Test
    fun aNamelessProjectTakesItsVenuesName() {
        assertTrue(Bookings.needsPlaces(live))
        val places = Bookings.places("""{"data":[{"id":9,"normalizedName":"Riverside Hall"},{"id":7,"normalizedName":"Car park"}]}""")
        assertEquals("Riverside Hall", Bookings.parse(live, london, places).single().title)
        assertEquals("", Bookings.parse(live, london).single().title)
        assertFalse(Bookings.needsPlaces("""{"data":[{"startTime":"2026-10-25 08:00:00","project":{"name":"Morning shift"}}]}"""))
    }

    @Test
    fun duplicatesCollapse() {
        val one = """{"startTime":"2026-10-25 08:00:00","project":{"name":"Morning shift"}}"""
        assertEquals(1, Bookings.parse("""{"data":[$one,$one]}""", london).size)
    }
}
