package com.ballknowledge

import com.ballknowledge.data.Serialization
import com.ballknowledge.export.HtmlExporter
import com.ballknowledge.logic.Autofill
import com.ballknowledge.logic.FixtureMerge
import com.ballknowledge.logic.MatchClock
import com.ballknowledge.logic.TimeFormat
import com.ballknowledge.logic.VenueZones
import com.ballknowledge.model.ClockState
import com.ballknowledge.model.DiaryEntry
import com.ballknowledge.model.Fields
import com.ballknowledge.model.Fixture
import com.ballknowledge.model.MatchPage
import com.ballknowledge.model.MatchState
import com.ballknowledge.model.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class LogicTest {

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun fixture(id: String, iso: String, league: String = "eng.1", country: String? = "England") = Fixture(
        id = id, league = league, kickoffMillis = millis(iso),
        homeId = "359", homeName = "Arsenal", homeAbbr = "ARS",
        awayId = "363", awayName = "Chelsea", awayAbbr = "CHE",
        homeScore = "2", awayScore = "1", state = MatchState.FINAL,
        venue = "Emirates Stadium", venueCity = "London", venueCountry = country,
    )

    // ---------- Time ----------

    @Test fun kickoffLineUkWinter() {
        // 31 Oct 2026 is after the UK clock change: UK = UTC, IST = UTC+5:30
        val line = TimeFormat.kickoffLine(millis("2026-10-31T15:00:00Z"), "England")
        assertEquals("Sat 31 Oct 2026 · 8:30 pm IST (3:00 pm UK)", line)
    }

    @Test fun kickoffLineUkSummer() {
        // 22 Aug 2026: UK is BST (UTC+1); 15:00 BST = 14:00 UTC = 19:30 IST
        val line = TimeFormat.kickoffLine(millis("2026-08-22T14:00:00Z"), "England")
        assertEquals("Sat 22 Aug 2026 · 7:30 pm IST (3:00 pm UK)", line)
    }

    @Test fun starForDifferentZone() {
        val t = millis("2026-11-04T20:00:00Z")
        assertTrue(VenueZones.differsFromUk(t, "Spain"))
        assertTrue(VenueZones.differsFromUk(t, "Kazakhstan"))
        assertFalse(VenueZones.differsFromUk(t, "England"))
        assertFalse(VenueZones.differsFromUk(t, "Portugal"))
        assertFalse(VenueZones.differsFromUk(t, null))
        assertFalse(VenueZones.differsFromUk(t, "Atlantis"))
        assertTrue(TimeFormat.kickoffLine(t, "Spain").endsWith("UK*)"))
    }

    @Test fun parsesEspnDates() {
        assertEquals(millis("2026-10-31T15:00:00Z"), TimeFormat.parseInstant("2026-10-31T15:00Z"))
        assertEquals(millis("2026-10-31T15:00:00Z"), TimeFormat.parseInstant("2026-10-31T15:00:00Z"))
        assertEquals(null, TimeFormat.parseInstant("not a date"))
    }

    @Test fun seasons() {
        assertEquals(2026, TimeFormat.seasonStartYear(millis("2026-08-01T12:00:00Z")))
        assertEquals(2026, TimeFormat.seasonStartYear(millis("2027-05-30T12:00:00Z")))
        assertEquals(2025, TimeFormat.seasonStartYear(millis("2026-06-30T12:00:00Z")))
        assertEquals("2026/27", TimeFormat.seasonLabel(2026))
        assertEquals("2099/00", TimeFormat.seasonLabel(2099))
        val months = TimeFormat.seasonMonths(2026)
        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2026, 7), months.first())
        assertEquals(YearMonth.of(2027, 6), months.last())
    }

    // ---------- Clock ----------

    @Test fun clockRunsAndCaps() {
        val start = 1_000_000L
        val c = MatchClock.anchor(Phase.H1, 7, start)
        assertEquals("7", MatchClock.display(c, start))
        assertEquals("7", MatchClock.display(c, start + 59_000))
        assertEquals("8", MatchClock.display(c, start + 60_000))
        assertEquals("45+2", MatchClock.display(c, start + 40 * 60_000L))
        val h2 = MatchClock.anchor(Phase.H2, 46, start)
        assertEquals("90+3", MatchClock.display(h2, start + 47 * 60_000L))
        assertEquals("FT", MatchClock.stamp(ClockState(Phase.FT), start))
        assertEquals("", MatchClock.stamp(ClockState(), start))
    }

    @Test fun minuteParsingAndSorting() {
        assertEquals(47, MatchClock.parseMinute("45+2"))
        assertEquals(94, MatchClock.parseMinute("90 + 4"))
        assertEquals(67, MatchClock.parseMinute("67'"))
        assertEquals(null, MatchClock.parseMinute("abc"))
        val entries = listOf("FT", "46", "45+2", "", "3", "45", "HT").mapIndexed { i, m -> DiaryEntry("$i", m, "x", i.toLong()) }
        assertEquals(listOf("3", "45", "45+2", "HT", "46", "FT", ""), MatchClock.sorted(entries).map { it.minute })
        assertEquals("45+2'", MatchClock.label("45+2"))
        assertEquals("—", MatchClock.label(""))
    }

    // ---------- Autofill ----------

    private fun page(fields: Map<String, String> = emptyMap(), edited: Set<String> = emptySet()) =
        MatchPage("p1", "f1", "eng.1", 0L, 0L, 0L, fields, edited)

    @Test fun autofillRespectsEdits() {
        val p = page(
            fields = mapOf(Fields.HOME_XI to "my own lineup", Fields.SCORE to "", Fields.TITLE to "Mine"),
            edited = setOf(Fields.HOME_XI, Fields.SCORE),
        )
        val out = Autofill.apply(p, mapOf(Fields.HOME_XI to "1 Raya", Fields.SCORE to "2–1", Fields.AWAY_XI to "1 Sanchez", Fields.TITLE to ""), 5L)
        assertEquals("my own lineup", out.field(Fields.HOME_XI))   // edited & non-blank: kept
        assertEquals("2–1", out.field(Fields.SCORE))                // edited but blank: filled
        assertEquals("1 Sanchez", out.field(Fields.AWAY_XI))
        assertEquals("Mine", out.field(Fields.TITLE))               // blank incoming never clears
        assertTrue(Fields.HOME_XI in out.edited)
        assertFalse(Fields.SCORE in out.edited)
    }

    @Test fun autofillFromFixture() {
        val v = Autofill.fromFixture(fixture("1", "2026-10-31T15:00:00Z"))
        assertEquals("PL", v[Fields.COMPETITION])
        assertEquals("Emirates Stadium, London", v[Fields.VENUE])
        assertEquals("2–1", v[Fields.SCORE])
    }

    // ---------- Fixture merge ----------

    @Test fun mergeDropsMovedFixturesOnlyInCoveredMonths() {
        val oldA = fixture("a", "2026-10-15T15:00:00Z")          // Oct interior, missing from fresh -> drop
        val oldB = fixture("b", "2026-11-15T15:00:00Z")          // Nov not covered -> keep
        val oldC = fixture("c", "2026-10-31T19:00:00Z")          // month edge -> keep
        val oldD = fixture("d", "2026-10-20T15:00:00Z", league = "uefa.champions") // other league -> keep
        val fresh = listOf(fixture("e", "2026-10-18T15:00:00Z"))
        val merged = FixtureMerge.merge(listOf(oldA, oldB, oldC, oldD), fresh, setOf("eng.1" to YearMonth.of(2026, 10)))
        assertEquals(listOf("e", "d", "c", "b"), merged.map { it.id })
    }

    // ---------- Export & storage ----------

    @Test fun htmlEscapesAndSkipsEmpty() {
        val p = page(fields = mapOf(Fields.HOME to "Arsenal", Fields.AWAY to "Chelsea", Fields.SCORE to "2–1", Fields.VERDICT to "<b>great</b> & fun"))
            .copy(diary = listOf(DiaryEntry("d", "23", "Goal!", 1L)))
        val html = HtmlExporter.build(listOf(p), "Test")
        assertTrue(html.contains("Arsenal 2–1 Chelsea"))
        assertTrue(html.contains("&lt;b&gt;great&lt;/b&gt; &amp; fun"))
        assertTrue(html.contains("23&#39;"))
        assertFalse(html.contains("The narrative"))
    }

    @Test fun backupRoundTrip() {
        val p = page(fields = mapOf(Fields.TITLE to "Line 1\nLine \"2\""), edited = setOf(Fields.TITLE))
            .copy(diary = listOf(DiaryEntry("d", "45+2", "Text", 9L)), clock = ClockState(Phase.H2, 123L, 46))
        val json = Serialization.backupToJson(listOf(), listOf(p), 1L)
        val back = Serialization.backupFromJson(json)
        assertEquals(p, back.pages.single())
    }

    @Test fun rejectsNonBackup() {
        val r = runCatching { Serialization.backupFromJson("{\"hello\":1}") }
        assertTrue(r.isFailure)
        assertNotNull(r.exceptionOrNull()?.message)
    }
}
