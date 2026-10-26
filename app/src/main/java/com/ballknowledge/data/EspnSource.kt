package com.ballknowledge.data

import com.ballknowledge.model.Fixture
import com.ballknowledge.model.TeamRef
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth

/**
 * ESPN's public (but unofficial and undocumented) JSON API. No key, no cost.
 * If ESPN ever changes it, this file and EspnParser are the only things to fix.
 */
class EspnSource : MatchDataSource {

    private val base = "https://site.api.espn.com/apis/site/v2/sports/soccer"

    private suspend fun get(url: String): String = Http.get(url)

    override suspend fun fetchFixtures(leagues: List<String>, months: List<YearMonth>): FixtureFetch = coroutineScope {
        val gate = Semaphore(4)
        val jobs = leagues.flatMap { league -> months.map { month -> league to month } }.map { (league, month) ->
            async {
                gate.withPermit {
                    val ym = "%04d%02d".format(month.year, month.monthValue)
                    runCatching {
                        val json = get("$base/$league/scoreboard?dates=$ym&limit=500")
                        Triple(league, month, EspnParser.parseScoreboard(json, league))
                    }.getOrNull()
                }
            }
        }
        val results = jobs.awaitAll()
        val ok = results.filterNotNull()
        val fixtures: List<Fixture> = ok.flatMap { it.third }.distinctBy { it.id }
        FixtureFetch(
            fixtures = fixtures,
            covered = ok.map { it.first to it.second }.toSet(),
            failures = results.count { it == null },
        )
    }

    override suspend fun fetchDetails(league: String, fixtureId: String): MatchDetails? =
        runCatching {
            val json = get("$base/$league/summary?event=$fixtureId")
            withContext(Dispatchers.Default) { EspnParser.parseSummary(json, league) }
        }.getOrNull()

    override suspend fun fetchTeams(): List<TeamRef> {
        val json = get("$base/eng.1/teams")
        return EspnParser.parseTeams(json)
    }
}
