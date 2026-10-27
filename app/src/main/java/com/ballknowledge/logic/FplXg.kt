package com.ballknowledge.logic

import java.util.Locale

/**
 * Team xG for a Premier League match, from Fantasy Premier League's Opta player data:
 * the sum of each team's players' xG in that fixture.
 */
object FplXg {

    /** One player's line from FPL's live gameweek feed. */
    data class LivePlayer(val id: Int, val xg: Double, val fixtures: List<Int>)

    sealed interface Result {
        data class Found(val home: String, val away: String) : Result
        /** Not available (yet), with a short reason for the user. */
        data class Unavailable(val reason: String) : Result
    }

    fun compute(
        fixture: FplFixture,
        allFixtures: List<FplFixture>,
        elementTeams: Map<Int, Int>,
        live: List<LivePlayer>,
    ): Result {
        val gw = fixture.gameweek ?: return Result.Unavailable("the match has no gameweek yet")
        if (!fixture.finished) return Result.Unavailable("xG appears a few hours after full time")
        // In a double gameweek the live feed adds both matches together, so it can't be split.
        val inGw = allFixtures.filter { it.gameweek == gw }
        val doubled = listOf(fixture.homeTeam, fixture.awayTeam).any { t ->
            inGw.count { it.homeTeam == t || it.awayTeam == t } > 1
        }
        if (doubled) return Result.Unavailable("double gameweek — FPL combines both matches")

        var home = 0.0
        var away = 0.0
        var counted = 0
        live.filter { fixture.id in it.fixtures }.forEach { p ->
            when (elementTeams[p.id]) {
                fixture.homeTeam -> { home += p.xg; counted++ }
                fixture.awayTeam -> { away += p.xg; counted++ }
            }
        }
        if (counted == 0) return Result.Unavailable("FPL has no player data for this match yet")
        return Result.Found(fmt(home), fmt(away))
    }

    private fun fmt(v: Double) = String.format(Locale.ENGLISH, "%.2f", v)
}
