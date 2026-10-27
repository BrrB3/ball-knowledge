package com.ballknowledge.data

import com.ballknowledge.model.Fixture
import com.ballknowledge.model.TeamRef
import java.time.YearMonth

/**
 * Everything the app needs from the outside world. ESPN is the only
 * implementation; swapping data source means writing another one of these.
 */
interface MatchDataSource {
    /** All fixtures in the given competitions and months (not filtered by team). */
    suspend fun fetchFixtures(leagues: List<String>, months: List<YearMonth>): FixtureFetch

    /** Lineups, scorers, events and stats for one match. Throws if the source can't be read. */
    suspend fun fetchDetails(league: String, fixtureId: String): MatchDetails

    /** Teams that can be followed (Premier League clubs). */
    suspend fun fetchTeams(): List<TeamRef>
}

data class FixtureFetch(
    val fixtures: List<Fixture>,
    /** (league, month) pairs that were fetched successfully. */
    val covered: Set<Pair<String, YearMonth>>,
    val failures: Int,
    /** The first thing that went wrong, for explaining it to the user. */
    val firstError: Throwable? = null,
)

data class Lineup(
    val formation: String,
    val startingXI: String,
    val subs: String,
)

data class MatchDetails(
    /** Up-to-date fixture info (score, state, venue) where available. */
    val fixture: Fixture?,
    val home: Lineup?,
    val away: Lineup?,
    val scorers: String,
    val events: String,
    /** Stat key (see Stats) -> value, e.g. "possession" -> "62.6%". */
    val homeStats: Map<String, String> = emptyMap(),
    val awayStats: Map<String, String> = emptyMap(),
)
