package com.ballknowledge

import com.ballknowledge.data.EspnParser
import com.ballknowledge.data.FplParser
import com.ballknowledge.data.HttpStatusException
import com.ballknowledge.data.Serialization
import com.ballknowledge.export.HtmlExporter
import com.ballknowledge.export.PlainTextExporter
import com.ballknowledge.logic.Autofill
import com.ballknowledge.logic.FetchErrors
import com.ballknowledge.logic.FplFixture
import com.ballknowledge.logic.FplXg
import com.ballknowledge.logic.TimeFormat
import com.ballknowledge.model.Fields
import com.ballknowledge.model.MatchPage
import com.ballknowledge.model.Stats
import com.ballknowledge.model.TransferWindow
import com.ballknowledge.model.WindowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException
import java.time.Instant

class V3Test {

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    // ---------- Stats ----------

    @Test fun parsesBoxScoreStats() {
        val json = """
        {"header":{"id":"9","competitions":[{"date":"2026-10-24T14:00Z","status":{"type":{"state":"post","completed":true}},
          "competitors":[{"homeAway":"home","score":"0","team":{"id":"360","displayName":"Manchester United"}},
                         {"homeAway":"away","score":"3","team":{"id":"349","displayName":"AFC Bournemouth"}}]}]},
         "boxscore":{"teams":[
           {"team":{"id":"349"},"statistics":[{"name":"possessionPct","displayValue":"37.4"},{"name":"totalShots","displayValue":"10"},
             {"name":"shotsOnTarget","displayValue":"4"},{"name":"wonCorners","displayValue":"4"}]},
           {"team":{"id":"360"},"statistics":[{"name":"possessionPct","displayValue":"62.6"},{"name":"totalShots","displayValue":"17"},
             {"name":"shotsOnTarget","displayValue":"5"},{"name":"wonCorners","displayValue":"5"}]}]}}
        """.trimIndent()
        val d = EspnParser.parseSummary(json, "eng.1")
        // Matched by team id even though ESPN listed the away side first.
        assertEquals(mapOf("possession" to "62.6%", "shots" to "17", "onTarget" to "5", "corners" to "5"), d.homeStats)
        assertEquals("37.4%", d.awayStats[Stats.POSSESSION])
        val values = Autofill.fromDetails(d, null)
        assertEquals("62.6%", values[Fields.stat(Stats.POSSESSION, true)])
        assertEquals("4", values[Fields.stat(Stats.CORNERS, false)])
    }

    // ---------- xG ----------

    private val fixtures = listOf(
        FplFixture(9, millis("2026-10-24T14:00:00Z"), 13, 4, id = 81, finished = true),
        FplFixture(9, millis("2026-10-24T16:30:00Z"), 1, 2, id = 82, finished = true),
        FplFixture(10, millis("2026-10-31T15:00:00Z"), 13, 1, id = 91, finished = false),
    )
    private val elementTeams = mapOf(100 to 13, 101 to 13, 200 to 4, 300 to 1)

    @Test fun sumsPlayerXgPerTeam() {
        val live = listOf(
            FplXg.LivePlayer(100, 0.85, listOf(81)),
            FplXg.LivePlayer(101, 0.40, listOf(81)),
            FplXg.LivePlayer(200, 1.62, listOf(81)),
            FplXg.LivePlayer(300, 2.00, listOf(82)), // other match
        )
        assertEquals(FplXg.Result.Found("1.25", "1.62"), FplXg.compute(fixtures[0], fixtures, elementTeams, live))
        assertTrue(FplXg.compute(fixtures[2], fixtures, elementTeams, live) is FplXg.Result.Unavailable)
        val doubled = fixtures + FplFixture(9, millis("2026-10-27T19:00:00Z"), 4, 7, id = 83, finished = true)
        assertTrue(FplXg.compute(fixtures[0], doubled, elementTeams, live) is FplXg.Result.Unavailable)
        assertTrue(FplXg.compute(fixtures[0], fixtures, elementTeams, emptyList()) is FplXg.Result.Unavailable)
    }

    @Test fun parsesFplLiveAndElements() {
        val live = FplParser.parseLive(
            """{"elements":[{"id":100,"stats":{"minutes":90,"expected_goals":"0.85"},"explain":[{"fixture":81,"stats":[]}]},
                             {"id":101,"stats":{"minutes":0,"expected_goals":"0.00"},"explain":[]}]}"""
        )
        assertEquals(2, live.size)
        assertEquals(0.85, live[0].xg, 0.0001)
        assertEquals(listOf(81), live[0].fixtures)
        val elements = FplParser.parseElementTeams("""{"teams":[],"elements":[{"id":100,"team":13},{"id":101,"team":4}]}""")
        assertEquals(mapOf(100 to 13, 101 to 4), elements)
        val f = FplParser.parseFixtures("""[{"id":81,"event":9,"kickoff_time":"2026-10-24T14:00:00Z","team_h":13,"team_a":4,"finished":false,"finished_provisional":true}]""")
        assertTrue(f.single().finished)
        assertEquals(81, f.single().id)
    }

    // ---------- Errors ----------

    @Test fun plainEnglishErrors() {
        assertEquals("Couldn't reach ESPN — check your internet connection.", FetchErrors.describe("ESPN", UnknownHostException("x")))
        assertTrue(FetchErrors.describe("ESPN", HttpStatusException(503, "u")).contains("error 503"))
        assertTrue(FetchErrors.describe("ESPN", HttpStatusException(404, "u")).contains("not found"))
        assertTrue(FetchErrors.describe("ESPN", org.json.JSONException("bad")).contains("format may have changed"))
    }

    // ---------- Last edited, seasons, storage ----------

    @Test fun lastEditedSurvivesAndDefaults() {
        val p = MatchPage("p", "f", "eng.1", 0, 1L, 5L, editedAt = 3L, fields = mapOf(Fields.FEVER_PITCH to "8", Fields.MEDIA_HEADLINE to "H"))
        val back = Serialization.backupFromJson(Serialization.backupToJson(emptyList(), listOf(p), emptyList(), 0)).pages.single()
        assertEquals(p, back)
        // Pages saved before v3 have no editedAt: fall back to their last saved time.
        val old = Serialization.pageFromJson(org.json.JSONObject("""{"id":"x","updatedAt":42,"fields":{}}"""))!!
        assertEquals(42L, old.editedAt)
        assertEquals("Tue 27 Oct 2026, 10:56 pm", TimeFormat.editedLine(millis("2026-10-27T17:26:00Z")))
        assertEquals("12 days ago", TimeFormat.daysAgo(millis("2026-10-15T10:00:00Z"), millis("2026-10-27T10:00:00Z")))
    }

    @Test fun windowSeasonsAndTitles() {
        assertEquals(2026, TransferWindow.yearFor(WindowKind.SUMMER, 2026))
        assertEquals(2027, TransferWindow.yearFor(WindowKind.WINTER, 2026))
        val w = TransferWindow("w", "Arsenal", WindowKind.WINTER, 2027)
        assertEquals("2026/27", w.seasonLabel)
        assertEquals("Arsenal · Winter 2026/27", w.title)
    }

    @Test fun exportsShowNewFields() {
        val p = MatchPage(
            "p", "f", "eng.1", millis("2026-11-08T14:00:00Z"), 0, 0, editedAt = millis("2026-11-08T18:00:00Z"),
            fields = mapOf(
                Fields.HOME to "Arsenal", Fields.AWAY to "Brentford", Fields.FEVER_PITCH to "9",
                Fields.MEDIA_HEADLINE to "Gunners cruise", Fields.stat(Stats.POSSESSION, true) to "61%",
                Fields.stat(Stats.XG, false) to "0.40",
            ),
        )
        val text = PlainTextExporter.match(p)
        assertTrue(text.contains("Fever pitch: 9"))
        assertTrue(text.contains("MEDIA HEADLINE\nGunners cruise"))
        assertTrue(text.contains("Possession: 61% – –"))
        assertTrue(text.contains("xG: – – 0.40"))
        assertTrue(text.contains("Last edited Sun 8 Nov 2026, 11:30 pm"))
        assertFalse(text.contains("Corners"))
        assertTrue(HtmlExporter.build(listOf(p), "t").contains("Match stats"))
    }
}
