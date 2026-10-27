package com.ballknowledge

import com.ballknowledge.data.EspnParser
import com.ballknowledge.model.MatchState
import com.ballknowledge.model.Fixture
import com.ballknowledge.model.TeamRef
import com.ballknowledge.model.isFollowedBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Synthetic tests follow ESPN's documented shape. [probeRealData] runs the parser
 * over real responses that the CI build downloads into /probe, and prints what it got.
 */
class EspnParserTest {

    private val scoreboard = """
    {"events":[
      {"id":"700001","date":"2026-10-31T15:00Z","season":{"year":2026},
       "competitions":[{"date":"2026-10-31T15:00Z",
         "venue":{"fullName":"Emirates Stadium","address":{"city":"London","country":"England"}},
         "status":{"type":{"name":"STATUS_FULL_TIME","state":"post","completed":true}},
         "competitors":[
           {"homeAway":"home","score":"2","team":{"id":"359","displayName":"Arsenal","abbreviation":"ARS"}},
           {"homeAway":"away","score":"1","team":{"id":"363","displayName":"Chelsea","abbreviation":"CHE"}}]}]},
      {"id":"700002","date":"2026-11-08T16:30Z",
       "competitions":[{"date":"2026-11-08T16:30Z",
         "status":{"type":{"name":"STATUS_SCHEDULED","state":"pre","completed":false}},
         "competitors":[
           {"homeAway":"home","score":"0","team":{"id":"364","displayName":"Liverpool"}},
           {"homeAway":"away","score":"0","team":{"id":"382","displayName":"Manchester City"}}]}]},
      {"id":"700003","date":"2026-11-09T14:00Z",
       "competitions":[{"status":{"type":{"name":"STATUS_POSTPONED","state":"post","completed":false}},
         "competitors":[
           {"homeAway":"home","team":{"id":"1","displayName":"A"}},
           {"homeAway":"away","team":{"id":"2","displayName":"B"}}]}]}
    ]}
    """.trimIndent()

    @Test fun parsesScoreboard() {
        val list = EspnParser.parseScoreboard(scoreboard, "eng.1")
        assertEquals(3, list.size)
        val a = list[0]
        assertEquals("Arsenal", a.homeName)
        assertEquals("2", a.homeScore)
        assertEquals(MatchState.FINAL, a.state)
        assertEquals("England", a.venueCountry)
        val b = list[1]
        assertEquals(MatchState.PRE, b.state)
        assertNull(b.homeScore)
        assertEquals(MatchState.POSTPONED, list[2].state)
    }

    private val summary = """
    {"header":{"id":"700001","competitions":[{"date":"2026-10-31T15:00Z",
        "status":{"type":{"state":"post","completed":true}},
        "competitors":[
          {"homeAway":"home","score":"2","team":{"id":"359","displayName":"Arsenal","abbreviation":"ARS"}},
          {"homeAway":"away","score":"1","team":{"id":"363","displayName":"Chelsea","abbreviation":"CHE"}}]}]},
     "gameInfo":{"venue":{"fullName":"Emirates Stadium","address":{"city":"London","country":"England"}}},
     "rosters":[
       {"homeAway":"home","team":{"id":"359"},"formation":"4-3-3","roster":[
          {"starter":true,"jersey":"7","formationPlace":"9","athlete":{"displayName":"Bukayo Saka"}},
          {"starter":true,"jersey":"1","formationPlace":"1","athlete":{"displayName":"David Raya"}},
          {"starter":false,"jersey":"19","athlete":{"displayName":"Leandro Trossard"}},
          {"starter":false,"jersey":"12","athlete":{"displayName":"Jurrien Timber"}}]},
       {"homeAway":"away","team":{"id":"363"},"formation":"4231","roster":[
          {"starter":true,"jersey":"1","formationPlace":"1","athlete":{"displayName":"Robert Sanchez"}}]}],
     "keyEvents":[
       {"type":{"text":"Goal"},"scoringPlay":true,"clock":{"displayValue":"23'"},"team":{"id":"359"},
        "participants":[{"athlete":{"displayName":"Bukayo Saka"}},{"athlete":{"displayName":"Martin Odegaard"}}]},
       {"type":{"text":"Yellow Card"},"clock":{"displayValue":"41'"},"team":{"id":"363"},
        "participants":[{"athlete":{"displayName":"Enzo Fernandez"}}]},
       {"type":{"text":"Penalty - Scored"},"scoringPlay":true,"clock":{"displayValue":"45'+2'"},"team":{"id":"363"},
        "participants":[{"athlete":{"displayName":"Cole Palmer"}}]},
       {"type":{"text":"Goal - Header"},"scoringPlay":true,"clock":{"displayValue":"56'"},"team":{"id":"359"},
        "participants":[{"athlete":{"displayName":"Bukayo Saka"}}]},
       {"type":{"text":"Substitution"},"clock":{"displayValue":"67'"},"team":{"id":"359"},
        "text":"Substitution, Arsenal. Leandro Trossard replaces Gabriel Martinelli.",
        "participants":[{"athlete":{"displayName":"Leandro Trossard"}},{"athlete":{"displayName":"Gabriel Martinelli"}}]},
       {"type":{"text":"Halftime"},"clock":{"displayValue":"45'"}}
     ]}
    """.trimIndent()

    @Test fun parsesSummary() {
        val d = EspnParser.parseSummary(summary, "eng.1")
        assertNotNull(d.fixture)
        assertEquals("England", d.fixture!!.venueCountry)
        assertEquals("4-3-3", d.home!!.formation)
        assertEquals("1 David Raya\n7 Bukayo Saka", d.home!!.startingXI)
        assertEquals("12 Jurrien Timber\n19 Leandro Trossard", d.home!!.subs)
        assertEquals("4-2-3-1", d.away!!.formation)
        assertEquals("Arsenal: Bukayo Saka 23', 56'\nChelsea: Cole Palmer 45+2' (pen)", d.scorers)
        val lines = d.events.lines()
        assertEquals(5, lines.size)
        assertEquals("23' ⚽ Bukayo Saka (Martin Odegaard) · ARS", lines[0])
        assertEquals("41' 🟨 Enzo Fernandez · CHE", lines[1])
        assertEquals("45+2' ⚽ Cole Palmer (pen) · CHE", lines[2])
        assertEquals("67' 🔁 Leandro Trossard on, Gabriel Martinelli off · ARS", lines[4])
    }

    @Test fun survivesGarbage() {
        val d = EspnParser.parseSummary("{}", "eng.1")
        assertNull(d.fixture)
        assertNull(d.home)
        assertEquals("", d.events)
        assertTrue(EspnParser.parseScoreboard("{\"events\":[{}]}", "eng.1").isEmpty())
    }

    /** Prints what the parser makes of real ESPN data, when CI has downloaded some. */
    @Test fun probeRealData() {
        val dir = listOf(File("../probe"), File("probe")).firstOrNull { it.isDirectory } ?: run {
            println("PROBE: no probe directory, skipping")
            return
        }
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            val text = f.readText()
            println("PROBE FILE ${f.name} (${text.length} chars)")
            runCatching {
                when {
                    f.name.startsWith("scoreboard-") -> {
                        val league = f.name.removePrefix("scoreboard-").substringBeforeLast("_")
                        val list = EspnParser.parseScoreboard(text, league)
                        println("  parsed ${list.size} fixtures")
                        list.take(4).forEach { println("  $it") }
                    }
                    f.name.startsWith("summary-") -> {
                        val league = f.name.removePrefix("summary-").substringBefore(".json").split("_").first()
                        val d = EspnParser.parseSummary(text, league)
                        println("  fixture: ${d.fixture}")
                        println("  home: ${d.home}")
                        println("  away: ${d.away}")
                        println("  STATS CHECK home=${d.homeStats} away=${d.awayStats}")
                        println("  scorers:\n${d.scorers}")
                        println("  events:\n${d.events}")
                    }
                    f.name.startsWith("rate-") -> println("  rate: ${com.ballknowledge.data.RateParser.parse(text)}  raw: ${text.take(160)}")
                    f.name == "fpl-fixtures.json" -> {
                        val fpl = com.ballknowledge.data.FplParser.parseFixtures(text)
                        val teams = dir.resolve("fpl-bootstrap.json").takeIf { it.exists() }
                            ?.let { com.ballknowledge.data.FplParser.parseTeams(it.readText()) }.orEmpty()
                        println("  FPL fixtures: ${fpl.size}, teams: ${teams.size} ${teams.values.joinToString()}")
                        val espn = dir.listFiles()!!.filter { it.name.startsWith("scoreboard-eng.1_") }
                            .flatMap { EspnParser.parseScoreboard(it.readText(), "eng.1") }.distinctBy { it.id }
                        val followed = com.ballknowledge.model.DefaultTeams.ALL
                        val mine = espn.filter { it.isFollowedByAny(followed) }
                        val withFpl = com.ballknowledge.logic.Matchday.assign(mine, followed, fpl, teams).associateBy { it.id }
                        val counted = com.ballknowledge.logic.Matchday.assign(mine, followed, null, emptyMap()).associateBy { it.id }
                        var official = 0
                        mine.sortedBy { it.kickoffMillis }.forEach { m ->
                            val a = withFpl[m.id]?.matchday
                            val b = counted[m.id]?.matchday
                            if (a != null) official++
                            println("  ${m.homeName} v ${m.awayName}: fpl=$a counted=$b")
                        }
                        println("  MATCHDAY CHECK: ${mine.size} followed PL fixtures, ${withFpl.values.count { it.matchday != counted[it.id]?.matchday }} differ from counting")
                    }
                    f.name == "fpl-live.json" -> {
                        val live = com.ballknowledge.data.FplParser.parseLive(text)
                        val boot = dir.resolve("fpl-bootstrap.json").readText()
                        val teams = com.ballknowledge.data.FplParser.parseTeams(boot)
                        val elementTeams = com.ballknowledge.data.FplParser.parseElementTeams(boot)
                        val all = com.ballknowledge.data.FplParser.parseFixtures(dir.resolve("fpl-fixtures.json").readText())
                        val withXg = live.count { it.xg > 0 }
                        println("  live players: ${live.size}, with xG > 0: $withXg, element teams: ${elementTeams.size}")
                        val gw = all.filter { it.finished }.maxOfOrNull { it.gameweek ?: 0 }
                        all.filter { it.gameweek == gw }.forEach { fx ->
                            val r = com.ballknowledge.logic.FplXg.compute(fx, all, elementTeams, live)
                            println("  XG CHECK GW$gw ${teams[fx.homeTeam]} v ${teams[fx.awayTeam]}: $r")
                        }
                    }
                    f.name.startsWith("teams") -> {
                        val t = EspnParser.parseTeams(text)
                        println("  parsed ${t.size} teams: ${t.joinToString { "${it.id}=${it.name}" }}")
                    }
                }
            }.onFailure { println("  PARSE FAILED: $it") }
        }
    }

    private fun Fixture.isFollowedByAny(teams: List<TeamRef>) = isFollowedBy(teams)
}
