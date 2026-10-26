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

    /** Lineups, scorers and events for one match, or null if unreachable. */
    suspend fun fetchDetails(league: String, fixtureId: String): MatchDetails?

    /** Teams that can be followed (Premier League clubs). */
    suspend fun fetchTeams(): List<TeamRef>
}

data class FixtureFetch(
    val fixtures: List<Fixture>,
    /** (league, month) pairs that were fetched successfully. */
    val covered: Set<Pair<String, YearMonth>>,
    val failures: Int,
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
)
