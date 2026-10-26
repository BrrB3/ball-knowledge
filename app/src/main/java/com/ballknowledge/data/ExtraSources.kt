package com.ballknowledge.data

import com.ballknowledge.logic.FplFixture
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

    suspend fun teams(): Map<Int, String> = FplParser.parseTeams(Http.get("$base/bootstrap-static/"))
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
            FplFixture(gw, kickoff, home, away)
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

    /** The EUR→GBP rate for [date], or the last working day before it (weekends/holidays). */
    suspend fun eurToGbp(date: LocalDate): Rate? {
        for (back in 0L..6L) {
            val d = date.minusDays(back)
            val urls = listOf(
                "https://api.frankfurter.dev/v2/rate/EUR/GBP?date=$d",
                "https://api.frankfurter.dev/v1/$d?base=EUR&symbols=GBP",
            )
            for (url in urls) {
                val parsed = runCatching { RateParser.parse(Http.get(url)) }.getOrNull() ?: continue
                return Rate(parsed.first, parsed.second ?: d.toString())
            }
        }
        return null
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
