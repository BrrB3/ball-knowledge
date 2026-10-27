package com.ballknowledge.data

import com.ballknowledge.logic.FplFixture
import com.ballknowledge.logic.FplXg
import com.ballknowledge.logic.Matchday
import com.ballknowledge.logic.TimeFormat
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Fantasy Premier League's public feed — used only for official Premier League
 * gameweek numbers. Unofficial; if it fails, the app counts games instead.
 */
class FplSource {
    private val base = "https://fantasy.premierleague.com/api"

    suspend fun fixtures(): List<FplFixture> = FplParser.parseFixtures(Http.get("$base/fixtures/"))

    suspend fun teams(): Map<Int, String> = bootstrap().first

    // bootstrap-static is large (~2 MB), so keep it for an hour.
    private var bootstrapCache: Pair<Map<Int, String>, Map<Int, Int>>? = null
    private var bootstrapAt = 0L

    private suspend fun bootstrap(): Pair<Map<Int, String>, Map<Int, Int>> {
        val now = System.currentTimeMillis()
        bootstrapCache?.let { if (now - bootstrapAt < 60 * 60 * 1000L) return it }
        val json = Http.get("$base/bootstrap-static/")
        val parsed = FplParser.parseTeams(json) to FplParser.parseElementTeams(json)
        bootstrapCache = parsed
        bootstrapAt = now
        return parsed
    }

    /** Team xG for one Premier League match (Opta data via FPL). */
    suspend fun matchXg(kickoff: Long, homeName: String, awayName: String): FplXg.Result {
        val all = fixtures()
        val (teams, elementTeams) = bootstrap()
        val f = Matchday.findFpl(kickoff, homeName, awayName, all, teams)
            ?: return FplXg.Result.Unavailable("couldn't match this game in the Fantasy PL feed")
        if (!f.finished || f.gameweek == null) return FplXg.compute(f, all, elementTeams, emptyList())
        val live = FplParser.parseLive(Http.get("$base/event/${f.gameweek}/live/"))
        return FplXg.compute(f, all, elementTeams, live)
    }
}

object FplParser {
    fun parseFixtures(json: String): List<FplFixture> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val home = o.optInt("team_h", -1)
            val away = o.optInt("team_a", -1)
            if (home < 0 || away < 0) return@mapNotNull null
            val gw = if (o.isNull("event")) null else o.optInt("event", -1).takeIf { it > 0 }
            val kickoff = if (o.isNull("kickoff_time")) null else TimeFormat.parseInstant(o.optString("kickoff_time"))
            val finished = o.optBoolean("finished") || o.optBoolean("finished_provisional")
            FplFixture(gw, kickoff, home, away, id = o.optInt("id", -1), finished = finished)
        }
    }

    /** Player id -> FPL team id. */
    fun parseElementTeams(json: String): Map<Int, Int> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyMap()
        return (0 until elements.length()).mapNotNull { i ->
            val e = elements.optJSONObject(i) ?: return@mapNotNull null
            val id = e.optInt("id", -1)
            val team = e.optInt("team", -1)
            if (id < 0 || team < 0) null else id to team
        }.toMap()
    }

    /** The live gameweek feed: each player's xG and which fixtures they played in. */
    fun parseLive(json: String): List<FplXg.LivePlayer> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        return (0 until elements.length()).mapNotNull { i ->
            val e = elements.optJSONObject(i) ?: return@mapNotNull null
            val id = e.optInt("id", -1)
            if (id < 0) return@mapNotNull null
            val stats = e.optJSONObject("stats")
            val xg = stats?.let { st ->
                if (!st.has("expected_goals") || st.isNull("expected_goals")) null
                else st.optString("expected_goals").toDoubleOrNull()
            } ?: 0.0
            val explain = e.optJSONArray("explain")
            val fixtures = if (explain == null) emptyList() else (0 until explain.length()).mapNotNull { j ->
                explain.optJSONObject(j)?.optInt("fixture", -1)?.takeIf { it >= 0 }
            }
            FplXg.LivePlayer(id, xg, fixtures)
        }
    }

    fun parseTeams(json: String): Map<Int, String> {
        val teams = JSONObject(json).optJSONArray("teams") ?: return emptyMap()
        return (0 until teams.length()).mapNotNull { i ->
            val t = teams.optJSONObject(i) ?: return@mapNotNull null
            val id = t.optInt("id", -1)
            val name = if (t.isNull("name")) "" else t.optString("name")
            if (id < 0 || name.isBlank()) null else id to name
        }.toMap()
    }
}

/** European Central Bank reference rates via the free Frankfurter service. */
class RateSource {

    data class Rate(val poundsPerEuro: String, val date: String)

    /** The EUR→GBP rate for [date], or the last working day before it (weekends/holidays). Throws if unreachable. */
    suspend fun eurToGbp(date: LocalDate): Rate {
        var lastError: Throwable? = null
        for (back in 0L..6L) {
            val d = date.minusDays(back)
            // v1 serves the ECB reference rate itself; v2 blends other providers, so it's
            // only a fallback in case v1 is ever switched off.
            val urls = listOf(
                "https://api.frankfurter.dev/v1/$d?base=EUR&symbols=GBP",
                "https://api.frankfurter.dev/v2/rate/EUR/GBP?date=$d",
            )
            for (url in urls) {
                val parsed = runCatching { RateParser.parse(Http.get(url)) }.onFailure { lastError = it }.getOrNull() ?: continue
                return Rate(parsed.first, parsed.second ?: d.toString())
            }
            // Couldn't connect at all: no point trying earlier days.
            if (lastError is java.io.IOException && lastError !is HttpStatusException) break
        }
        throw lastError ?: IllegalStateException("No rate found")
    }
}

object RateParser {
    /** Accepts both Frankfurter shapes: {"rate":0.85,"date":...} or {"rates":{"GBP":0.85},"date":...}. */
    fun parse(json: String): Pair<String, String?>? {
        val o = JSONObject(json)
        val rate = when {
            o.has("rate") && !o.isNull("rate") -> o.optDouble("rate")
            o.optJSONObject("rates")?.has("GBP") == true -> o.optJSONObject("rates")!!.optDouble("GBP")
            else -> return null
        }
        if (rate.isNaN() || rate <= 0.0) return null
        val date = if (o.has("date") && !o.isNull("date")) o.optString("date").take(10) else null
        return String.format(java.util.Locale.ENGLISH, "%.4f", rate) to date
    }
}
