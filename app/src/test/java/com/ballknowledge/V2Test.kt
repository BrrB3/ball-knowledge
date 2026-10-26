package com.ballknowledge

import com.ballknowledge.data.FplParser
import com.ballknowledge.data.RateParser
import com.ballknowledge.data.Serialization
import com.ballknowledge.export.HtmlExporter
import com.ballknowledge.export.PlainTextExporter
import com.ballknowledge.logic.FplFixture
import com.ballknowledge.logic.Matchday
import com.ballknowledge.logic.Migration
import com.ballknowledge.logic.TeamNames
import com.ballknowledge.logic.TransferMath
import com.ballknowledge.logic.UndoHistory
import com.ballknowledge.model.Currency
import com.ballknowledge.model.DefaultTeams
import com.ballknowledge.model.Fee
import com.ballknowledge.model.Fields
import com.ballknowledge.model.Fixture
import com.ballknowledge.model.InStatus
import com.ballknowledge.model.IncomingLink
import com.ballknowledge.model.MatchPage
import com.ballknowledge.model.MatchState
import com.ballknowledge.model.OutStatus
import com.ballknowledge.model.OutgoingLink
import com.ballknowledge.model.SpendKeys
import com.ballknowledge.model.TransferWindow
import com.ballknowledge.model.WindowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class V2Test {

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun fx(
        id: String, iso: String, home: Pair<String, String>, away: Pair<String, String>,
        league: String = "eng.1", stage: String? = null,
    ) = Fixture(
        id = id, league = league, kickoffMillis = millis(iso),
        homeId = home.first, homeName = home.second, homeAbbr = "",
        awayId = away.first, awayName = away.second, awayAbbr = "",
        homeScore = null, awayScore = null, state = MatchState.PRE,
        venue = null, venueCity = null, venueCountry = null, stage = stage,
    )

    // ---------- Undo ----------

    @Test fun undoGroupsTypingAndRedoes() {
        val h = UndoHistory<String>()
        var s = ""
        fun type(t: String, key: String?, at: Long) { h.record(s, key, at); s = t }
        type("H", "title", 0); type("He", "title", 500); type("Hey", "title", 900)   // one burst
        type("Hey!", "title", 5_000)                                              // pause -> new step
        type("Hey!", "verdict", 5_100)                                            // other box -> new step
        assertEquals("Hey!", h.undo(s)!!.also { s = it })
        assertEquals("Hey", h.undo(s)!!.also { s = it })
        assertEquals("", h.undo(s)!!.also { s = it })
        assertFalse(h.canUndo)
        assertEquals("Hey", h.redo(s)!!.also { s = it })
        type("X", null, 6_000)
        assertFalse(h.canRedo)
    }

    // ---------- Team names ----------

    @Test fun teamNameMatching() {
        assertTrue(TeamNames.matches("Man Utd", "Manchester United"))
        assertTrue(TeamNames.matches("Spurs", "Tottenham Hotspur"))
        assertTrue(TeamNames.matches("Brighton", "Brighton & Hove Albion"))
        assertTrue(TeamNames.matches("Nott'm Forest", "Nottingham Forest"))
        assertTrue(TeamNames.matches("AFC Bournemouth", "Bournemouth"))
        assertFalse(TeamNames.matches("Man City", "Manchester United"))
        assertFalse(TeamNames.matches("Arsenal Women", "Arsenal"))
        assertEquals("Arsenal", TeamNames.find("arsenal", DefaultTeams.ALL)?.name)
        assertNull(TeamNames.find("Brentford", DefaultTeams.ALL))
    }

    // ---------- Matchday ----------

    @Test fun stageLabels() {
        assertNull(Matchday.stageLabel("eng.1", "2026-27-english-premier-league", null))
        assertEquals("R4", Matchday.stageLabel("eng.league_cup", "fourth-round", null))
        assertEquals("League phase", Matchday.stageLabel("uefa.champions", "league-phase", null))
        assertEquals("QF · Leg 1", Matchday.stageLabel("uefa.champions", "quarterfinals", "Quarterfinal - 1st Leg"))
        assertEquals("R16 · Leg 2", Matchday.stageLabel("uefa.europa", "round-of-16", "2nd Leg, Arsenal lead 2-0 on aggregate"))
        assertEquals("Some New Round", Matchday.stageLabel("eng.fa", "some-new-round", null))
    }

    @Test fun assignsOfficialGameweekCountsAndStages() {
        val ars = "359" to "Arsenal"
        val che = "363" to "Chelsea"
        val bre = "337" to "Brentford"
        val fixtures = listOf(
            fx("a", "2026-08-22T14:00:00Z", ars, bre),
            fx("b", "2026-08-30T15:30:00Z", che, ars),
            fx("c", "2026-09-16T19:00:00Z", ars, "1" to "Lille", league = "uefa.champions", stage = "League phase"),
            fx("d", "2026-09-30T19:00:00Z", "2" to "PSG", ars, league = "uefa.champions", stage = "League phase"),
            fx("e", "2026-10-28T19:45:00Z", "3" to "Fleetwood", ars, league = "eng.league_cup", stage = "R4"),
        )
        val fplTeams = mapOf(1 to "Arsenal", 4 to "Brentford", 7 to "Chelsea")
        val fpl = listOf(
            FplFixture(1, millis("2026-08-22T14:00:00Z"), 1, 4),
            FplFixture(1, millis("2026-08-22T14:00:00Z"), 9, 10), // another match, same kickoff
            FplFixture(3, millis("2026-08-30T15:30:00Z"), 7, 1),  // official GW3 although Arsenal's 2nd game
        )
        val out = Matchday.assign(fixtures, DefaultTeams.ALL, fpl, fplTeams).associateBy { it.id }
        assertEquals("MW1", out.getValue("a").matchday)
        assertEquals("MW3", out.getValue("b").matchday)
        assertEquals("League phase · MD1", out.getValue("c").matchday)
        assertEquals("League phase · MD2", out.getValue("d").matchday)
        assertEquals("R4", out.getValue("e").matchday)

        // Without FPL, Premier League falls back to counting the (home, if followed) team's games.
        val counted = Matchday.assign(fixtures, DefaultTeams.ALL, null, emptyMap()).associateBy { it.id }
        assertEquals("MW1", counted.getValue("a").matchday)
        assertEquals("MW1", counted.getValue("b").matchday) // Chelsea's first league game here
    }

    @Test fun parsesFplFeed() {
        val fixtures = FplParser.parseFixtures(
            """[{"id":1,"event":9,"kickoff_time":"2026-10-31T15:00:00Z","team_h":1,"team_a":7},
                {"id":2,"event":null,"kickoff_time":null,"team_h":3,"team_a":4}]"""
        )
        assertEquals(2, fixtures.size)
        assertEquals(9, fixtures[0].gameweek)
        assertEquals(millis("2026-10-31T15:00:00Z"), fixtures[0].kickoffMillis)
        assertNull(fixtures[1].gameweek)
        assertNull(fixtures[1].kickoffMillis)
        val teams = FplParser.parseTeams("""{"teams":[{"id":1,"name":"Arsenal"},{"id":7,"name":"Chelsea"}],"elements":[]}""")
        assertEquals("Chelsea", teams[7])
    }

    // ---------- Migration ----------

    @Test fun splitsLegacyDateTime() {
        val old = MatchPage(
            "p", "f", "eng.1", 0, 0, 0,
            fields = mapOf(Fields.LEGACY_DATETIME to "Sat 31 Oct 2026 · 8:30 pm IST (3:00 pm UK)", Fields.TITLE to "T"),
            edited = setOf(Fields.LEGACY_DATETIME),
        )
        val m = Migration.page(old)
        assertEquals("Sat 31 Oct 2026", m.field(Fields.DATE))
        assertEquals("8:30 pm IST (3:00 pm UK)", m.field(Fields.KICKOFF))
        assertFalse(m.fields.containsKey(Fields.LEGACY_DATETIME))
        assertTrue(Fields.DATE in m.edited && Fields.KICKOFF in m.edited)
        assertEquals(m, Migration.page(m)) // idempotent
    }

    @Test fun v1BackupStillRestores() {
        val v1 = """{"format":"ball-knowledge-backup","version":1,"teams":[],
            "pages":[{"id":"p","fields":{"datetime":"Sat 31 Oct 2026 · 8:30 pm IST (3:00 pm UK)"},"edited":[],"diary":[]}]}"""
        val b = Serialization.backupFromJson(v1)
        assertEquals("Sat 31 Oct 2026", b.pages.single().field(Fields.DATE))
        assertTrue(b.windows.isEmpty())
    }

    // ---------- Transfers ----------

    private fun window() = TransferWindow(
        id = "w", team = "Arsenal", kind = WindowKind.SUMMER, year = 2026,
        startDate = "2026-06-16", endDate = "2026-09-01", rate = "0.85",
        incoming = listOf(
            IncomingLink("1", "Player A", "ST", status = InStatus.SIGNED, fee = Fee("60", "10", Currency.GBP)),
            IncomingLink("2", "Player B", "CM", status = InStatus.SIGNED, fee = Fee("100", "20", Currency.EUR)),
            IncomingLink("3", "Player C", "LW", status = InStatus.LOAN, fee = Fee("5", "", Currency.GBP)),
            IncomingLink("4", "Player D", "CB", status = InStatus.ELSEWHERE, fee = Fee("50", "", Currency.GBP)),
            IncomingLink("5", "Player E", "GK", status = InStatus.LINKED),
        ),
        outgoing = listOf(
            OutgoingLink("6", "Player F", status = OutStatus.SOLD, fee = Fee("30", "5", Currency.GBP)),
            OutgoingLink("7", "Player G", status = OutStatus.STAYS),
        ),
    )

    @Test fun spendAddsMainAndAddonsSeparately() {
        val t = TransferMath.totals(window())
        // Incoming: 60 + 85 (€100m) + 5 = 150; add-ons 10 + 17 (€20m) = 27. Went elsewhere excluded.
        assertEquals(150.0, t.incoming.main, 0.001)
        assertEquals(27.0, t.incoming.addons, 0.001)
        assertEquals("£150m + £27m", TransferMath.format(t.incoming))
        assertEquals("£30m + £5m", TransferMath.format(t.outgoing))
        assertEquals("£120m + £22m", TransferMath.format(t.net))
        assertFalse(t.missingRate)
    }

    @Test fun spendFlagsMissingRateAndOverrides() {
        val w = window().copy(rate = "")
        val t = TransferMath.totals(w)
        assertTrue(t.missingRate)
        assertEquals("£65m + £10m", TransferMath.format(t.incoming)) // € fee left out
        val shown = TransferMath.displayTotals(w.copy(spendOverrides = mapOf(SpendKeys.NET to "£999m")))
        assertEquals("£999m", shown[SpendKeys.NET])
        assertEquals("£65m + £10m", shown[SpendKeys.INCOMING])
        assertEquals("−£12.5m", TransferMath.format(TransferMath.Amount(-12.5, 0.0)))
        assertEquals("£3m − £1m", TransferMath.format(TransferMath.Amount(3.0, -1.0)))
        assertEquals(45.5, TransferMath.parseMillions("£45,5m")!!, 0.001)
        assertNull(TransferMath.parseMillions("tbc"))
    }

    @Test fun summaryAndSeason() {
        val w = window()
        assertEquals("2 signed · 1 loan · 1 linked · 1 went elsewhere  |  Out: 1 sold · 1 stays", TransferMath.summary(w))
        assertEquals(2026, w.seasonStart)
        assertEquals(2026, w.copy(kind = WindowKind.WINTER, year = 2027).seasonStart)
    }

    @Test fun windowRoundTripAndBackup() {
        val w = window().copy(spendOverrides = mapOf(SpendKeys.NET to "x"), rateEdited = true, wishlist = "a\nb")
        val json = Serialization.backupToJson(listOf(), listOf(), listOf(w), 1L)
        val b = Serialization.backupFromJson(json)
        assertEquals(w, b.windows.single())
        assertTrue(b.pages.isEmpty())
    }

    @Test fun parsesBothRateShapes() {
        assertEquals("0.8573" to "2026-06-16", RateParser.parse("""{"base":"EUR","date":"2026-06-16","rates":{"GBP":0.85731}}"""))
        assertEquals("0.8500" to null, RateParser.parse("""{"rate":0.85}"""))
        assertNull(RateParser.parse("""{"message":"not found"}"""))
    }

    // ---------- Exports ----------

    @Test fun exportsIncludeSeasonAndAreSeparate() {
        val p = MatchPage(
            "p", "f", "eng.1", millis("2026-11-08T14:00:00Z"), 0, 0,
            fields = mapOf(
                Fields.HOME to "Arsenal", Fields.AWAY to "Brentford", Fields.SCORE to "2–0",
                Fields.COMPETITION to "PL", Fields.MATCHDAY to "MW11", Fields.DATE to "Sun 8 Nov 2026",
                Fields.VERDICT to "Controlled.", Fields.RATING to "7", Fields.ATTENTION to "9", Fields.SCORERS to "Arsenal: Saka 12'",
            ),
        )
        val text = PlainTextExporter.match(p)
        assertTrue(text.startsWith("ARSENAL 2–0 BRENTFORD"))
        assertTrue(text.contains("PL · MW11 · Sun 8 Nov 2026 · Season 2026/27"))
        assertTrue(text.contains("POST-MATCH VERDICT\nControlled."))
        assertTrue(text.contains("Match rating: 7/10 · Match attention: 9/10"))
        assertFalse(text.contains("THE NARRATIVE"))

        val html = HtmlExporter.build(listOf(p), "t")
        assertTrue(html.contains("During the match"))
        assertTrue(html.contains("Season 2026/27"))

        val wText = PlainTextExporter.window(window())
        assertTrue(wText.startsWith("ARSENAL · SUMMER 2026"))
        assertTrue(wText.contains("Net spend: £120m + £22m"))
        assertTrue(wText.contains("• Player B (CM) — Signed — €100m + €20m"))
        assertFalse(wText.contains("Player E (GK) — Linked —")) // no fee shown for linked
        assertTrue(HtmlExporter.buildTransfers(listOf(window()), "t").contains("Exits"))
    }
}
