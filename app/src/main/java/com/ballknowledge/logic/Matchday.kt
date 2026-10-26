package com.ballknowledge.logic

import com.ballknowledge.model.Fixture
import com.ballknowledge.model.TeamRef
import com.ballknowledge.model.isFollowedBy
import java.util.Locale

/** One fixture from the Fantasy Premier League feed (the source of official gameweeks). */
data class FplFixture(val gameweek: Int?, val kickoffMillis: Long?, val homeTeam: Int, val awayTeam: Int)

object Matchday {

    private val stageLabels = mapOf(
        "league-phase" to "League phase",
        "group-stage" to "Group stage",
        "knockout-round-playoffs" to "KO play-offs",
        "knockout-playoffs" to "KO play-offs",
        "knockout-phase-playoffs" to "KO play-offs",
        "playoff-round" to "Play-off round",
        "round-of-32" to "R32",
        "round-of-16" to "R16",
        "quarterfinals" to "QF",
        "quarter-finals" to "QF",
        "quarterfinal" to "QF",
        "semifinals" to "SF",
        "semi-finals" to "SF",
        "semifinal" to "SF",
        "final" to "Final",
        "first-round" to "R1",
        "second-round" to "R2",
        "third-round" to "R3",
        "fourth-round" to "R4",
        "fifth-round" to "R5",
        "sixth-round" to "R6",
    )

    /**
     * Turns ESPN's season slug ("fourth-round", "league-phase") and any note
     * ("Quarterfinal - 1st Leg") into a short label. League matches get null.
     */
    fun stageLabel(league: String, slug: String?, note: String?): String? {
        if (league == "eng.1") return null
        val s = slug?.trim()?.lowercase(Locale.ENGLISH).orEmpty()
        val base = when {
            s.isEmpty() -> null
            stageLabels.containsKey(s) -> stageLabels[s]
            Regex("""^\d{4}""").containsMatchIn(s) -> null // a whole-season slug, no stage
            else -> s.split('-').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        }
        val leg = note?.let { legFrom(it) }
        return listOfNotNull(base, leg).joinToString(" · ").ifBlank { null }
    }

    fun legFrom(note: String): String? {
        val n = note.lowercase(Locale.ENGLISH)
        return when {
            Regex("""(1st|first)\s+leg|leg\s*1\b""").containsMatchIn(n) -> "Leg 1"
            Regex("""(2nd|second)\s+leg|leg\s*2\b""").containsMatchIn(n) -> "Leg 2"
            else -> null
        }
    }

    /**
     * Fills [Fixture.matchday] for every fixture:
     *  - Premier League: the official gameweek from FPL; if FPL can't be matched, the
     *    followed team's nth league game (correct unless a game was rescheduled).
     *  - European league phase: the team's nth league-phase game ("League phase · MD3").
     *  - Everything else: the stage label ("R4", "QF · Leg 1").
     */
    fun assign(
        fixtures: List<Fixture>,
        teams: List<TeamRef>,
        fpl: List<FplFixture>?,
        fplTeams: Map<Int, String>,
    ): List<Fixture> {
        val sorted = fixtures.sortedBy { it.kickoffMillis }
        val fplByKickoff = fpl.orEmpty().filter { it.kickoffMillis != null }.groupBy { it.kickoffMillis!! }

        fun followedSide(f: Fixture): Pair<String, String> {
            val homeFollowed = teams.any { it.id == f.homeId } || TeamNames.find(f.homeName, teams) != null
            return if (homeFollowed) f.homeId to f.homeName else f.awayId to f.awayName
        }

        fun nth(f: Fixture, sameKind: (Fixture) -> Boolean): Int {
            val (id, name) = followedSide(f)
            val list = sorted.filter { o ->
                sameKind(o) && (o.homeId == id || o.awayId == id || (id.isBlank() && (o.homeName == name || o.awayName == name)))
            }
            return list.indexOfFirst { it.id == f.id } + 1
        }

        return fixtures.map { f ->
            val label = when {
                f.league == "eng.1" -> {
                    val gw = officialGameweek(f, fplByKickoff, fplTeams)
                    val n = gw ?: nth(f) { it.league == "eng.1" }.takeIf { it > 0 }
                    n?.let { "MW$it" }
                }
                f.stage == "League phase" && f.league.startsWith("uefa.") -> {
                    val n = nth(f) { it.league == f.league && it.stage == "League phase" }
                    if (n > 0) "League phase · MD$n" else "League phase"
                }
                else -> f.stage
            }
            if (f.isFollowedBy(teams) || label != null) f.copy(matchday = label) else f
        }
    }

    private fun officialGameweek(f: Fixture, byKickoff: Map<Long, List<FplFixture>>, fplTeams: Map<Int, String>): Int? {
        val candidates = byKickoff[f.kickoffMillis].orEmpty().filter { it.gameweek != null }
        if (candidates.isEmpty()) return null
        val named = candidates.firstOrNull { c ->
            val h = fplTeams[c.homeTeam]
            val a = fplTeams[c.awayTeam]
            (h != null && TeamNames.matches(h, f.homeName)) || (a != null && TeamNames.matches(a, f.awayName))
        }
        if (named != null) return named.gameweek
        // Without team names, a unique kickoff slot is still unambiguous.
        return if (candidates.size == 1 && fplTeams.isEmpty()) candidates.single().gameweek else null
    }
}
