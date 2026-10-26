package com.ballknowledge.data

import com.ballknowledge.logic.Matchday
import com.ballknowledge.logic.TimeFormat
import com.ballknowledge.model.Fixture
import com.ballknowledge.model.MatchState
import com.ballknowledge.model.TeamRef
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns ESPN's (undocumented) JSON into app models. Every lookup is defensive:
 * a missing or renamed field gives a blank value, never a crash.
 */
object EspnParser {

    // ---------- JSON helpers (Android's org.json returns "null" strings; avoid that) ----------

    private fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)
    private fun JSONObject.arr(key: String): JSONArray? = optJSONArray(key)

    private fun JSONObject.str(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = opt(key) ?: return null
        if (v is JSONObject || v is JSONArray) return null
        return v.toString().trim().takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun JSONObject.bool(key: String): Boolean {
        if (!has(key) || isNull(key)) return false
        return when (val v = opt(key)) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true)
            is JSONObject -> true
            else -> false
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    // ---------- Fixtures ----------

    fun parseScoreboard(json: String, league: String): List<Fixture> {
        val root = JSONObject(json)
        return root.arr("events").objects().mapNotNull { parseEvent(it, league, null) }
    }

    private fun parseEvent(ev: JSONObject, league: String, gameInfo: JSONObject?): Fixture? {
        val id = ev.str("id") ?: return null
        val comp = ev.arr("competitions").objects().firstOrNull() ?: return null
        val kickoff = TimeFormat.parseInstant(comp.str("date") ?: ev.str("date")) ?: return null
        val competitors = comp.arr("competitors").objects()
        val home = competitors.firstOrNull { it.str("homeAway") == "home" } ?: competitors.getOrNull(0) ?: return null
        val away = competitors.firstOrNull { it.str("homeAway") == "away" } ?: competitors.getOrNull(1) ?: return null
        val homeTeam = home.obj("team") ?: JSONObject()
        val awayTeam = away.obj("team") ?: JSONObject()
        val state = parseState(comp.obj("status") ?: ev.obj("status"))

        val venueObj = gameInfo?.obj("venue") ?: comp.obj("venue")
        val address = venueObj?.obj("address")

        val showScore = state == MatchState.FINAL || state == MatchState.LIVE
        val note = (comp.arr("notes").objects() + ev.arr("notes").objects())
            .mapNotNull { it.str("headline") ?: it.str("text") }
            .joinToString(" ")
            .ifBlank { null }
        val stage = Matchday.stageLabel(league, ev.obj("season")?.str("slug"), note)
        return Fixture(
            id = id,
            league = league,
            kickoffMillis = kickoff,
            homeId = homeTeam.str("id") ?: home.str("id") ?: "",
            homeName = teamName(homeTeam),
            homeAbbr = homeTeam.str("abbreviation") ?: "",
            awayId = awayTeam.str("id") ?: away.str("id") ?: "",
            awayName = teamName(awayTeam),
            awayAbbr = awayTeam.str("abbreviation") ?: "",
            homeScore = if (showScore) score(home) else null,
            awayScore = if (showScore) score(away) else null,
            state = state,
            venue = venueObj?.str("fullName") ?: venueObj?.str("shortName"),
            venueCity = address?.str("city"),
            venueCountry = address?.str("country"),
            stage = stage,
        )
    }

    private fun teamName(team: JSONObject): String =
        team.str("displayName") ?: team.str("name") ?: team.str("shortDisplayName") ?: "?"

    private fun score(competitor: JSONObject): String? {
        val o = competitor.obj("score")
        if (o != null) return o.str("displayValue") ?: o.str("value")?.removeSuffix(".0")
        return competitor.str("score")?.removeSuffix(".0")
    }

    private fun parseState(status: JSONObject?): MatchState {
        val type = status?.obj("type") ?: return MatchState.UNKNOWN
        val name = (type.str("name") ?: "").uppercase()
        val detail = (type.str("description") ?: type.str("detail") ?: "").uppercase()
        if (listOf("POSTPONED", "CANCELED", "CANCELLED", "SUSPENDED", "ABANDONED").any { name.contains(it) || detail.contains(it) }) {
            return MatchState.POSTPONED
        }
        if (type.bool("completed")) return MatchState.FINAL
        return when (type.str("state")) {
            "pre" -> MatchState.PRE
            "in" -> MatchState.LIVE
            "post" -> MatchState.FINAL
            else -> MatchState.UNKNOWN
        }
    }

    // ---------- Match summary ----------

    fun parseSummary(json: String, league: String): MatchDetails {
        val root = JSONObject(json)
        val header = root.obj("header")
        val fixture = header?.let { parseEvent(it, league, root.obj("gameInfo")) }

        val abbrById = mutableMapOf<String, String>()
        val nameById = mutableMapOf<String, String>()
        header?.arr("competitions").objects().firstOrNull()?.arr("competitors").objects().forEach { c ->
            val t = c.obj("team") ?: return@forEach
            val id = t.str("id") ?: return@forEach
            abbrById[id] = t.str("abbreviation") ?: teamName(t)
            nameById[id] = teamName(t)
        }

        var home: Lineup? = null
        var away: Lineup? = null
        for (r in root.arr("rosters").objects()) {
            val lineup = parseLineup(r) ?: continue
            when (r.str("homeAway")) {
                "home" -> home = lineup
                "away" -> away = lineup
                else -> if (home == null) home = lineup else if (away == null) away = lineup
            }
        }

        val keyEvents = root.arr("keyEvents").objects()
        val homeId = fixture?.homeId
        val awayId = fixture?.awayId
        return MatchDetails(
            fixture = fixture,
            home = home,
            away = away,
            scorers = formatScorers(keyEvents, listOfNotNull(homeId, awayId), nameById),
            events = formatEvents(keyEvents, abbrById),
        )
    }

    private fun parseLineup(roster: JSONObject): Lineup? {
        val players = roster.arr("roster").objects()
        if (players.isEmpty()) return null
        val starters = players.filter { it.bool("starter") }
        val subs = players.filter { !it.bool("starter") }
        if (starters.isEmpty()) return null

        fun line(p: JSONObject): String {
            val name = p.obj("athlete")?.let { a -> a.str("displayName") ?: a.str("fullName") ?: a.str("shortName") } ?: "?"
            val number = p.str("jersey")
            return if (number != null) "$number $name" else name
        }

        val orderedStarters = starters.sortedWith(
            compareBy<JSONObject> { it.str("formationPlace")?.toIntOrNull() ?: 99 }
                .thenBy { it.str("jersey")?.toIntOrNull() ?: 999 }
        )
        val orderedSubs = subs.sortedBy { it.str("jersey")?.toIntOrNull() ?: 999 }

        val formationRaw = roster.str("formation") ?: roster.obj("formation")?.let { it.str("name") ?: it.str("summary") }
        return Lineup(
            formation = normaliseFormation(formationRaw),
            startingXI = orderedStarters.joinToString("\n") { line(it) },
            subs = orderedSubs.joinToString("\n") { line(it) },
        )
    }

    fun normaliseFormation(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return ""
        return if (t.all { it.isDigit() } && t.length in 3..5) t.toCharArray().joinToString("-") else t
    }

    // ---------- Key events ----------

    private enum class Kind { GOAL, YELLOW, RED, SUB, OTHER }

    private data class Event(
        val kind: Kind,
        val minute: String,
        val teamId: String?,
        val primary: String?,
        val secondary: String?,
        val ownGoal: Boolean,
        val penalty: Boolean,
    )

    private fun parseKeyEvent(e: JSONObject): Event {
        val type = e.obj("type")
        val typeText = listOfNotNull(type?.str("text"), type?.str("type"), type?.str("name"))
            .joinToString(" ").lowercase()
        val text = e.str("text") ?: e.str("shortText") ?: ""
        val textLower = text.lowercase()
        val participants = e.arr("participants").objects().mapNotNull { p ->
            p.obj("athlete")?.let { a -> a.str("displayName") ?: a.str("fullName") }
        }
        val minute = cleanMinute(e.obj("clock")?.str("displayValue"))
        val teamId = e.obj("team")?.str("id")

        val isRed = Regex("""\bred\b""").containsMatchIn(typeText.replace('-', ' '))
        val kind = when {
            e.bool("scoringPlay") && !typeText.contains("shootout") -> Kind.GOAL
            typeText.contains("yellow") && !isRed -> Kind.YELLOW
            isRed -> Kind.RED
            typeText.contains("substitution") -> Kind.SUB
            else -> Kind.OTHER
        }

        var primary = participants.getOrNull(0)
        var secondary = participants.getOrNull(1)
        if (kind == Kind.SUB) {
            // e.g. "Substitution, Arsenal. Leandro Trossard replaces Gabriel Martinelli."
            val m = Regex("""([^.]+?) replaces ([^.]+?)(?:\s+because[^.]*)?\.?\s*$""").find(text.substringAfter(". ", text))
            if (m != null) {
                primary = m.groupValues[1].trim()
                secondary = m.groupValues[2].trim()
            }
        }
        return Event(
            kind = kind,
            minute = minute,
            teamId = teamId,
            primary = primary,
            secondary = secondary,
            ownGoal = typeText.contains("own") || textLower.contains("own goal"),
            penalty = typeText.contains("penalty") || textLower.contains("penalty"),
        )
    }

    /** "45'+2'" -> "45+2'", "23'" -> "23'". */
    fun cleanMinute(raw: String?): String {
        val t = raw?.replace("'", "")?.replace(" ", "")?.trim().orEmpty()
        return if (t.isEmpty()) "" else "$t'"
    }

    private fun formatEvents(keyEvents: List<JSONObject>, abbrById: Map<String, String>): String =
        keyEvents.map { parseKeyEvent(it) }
            .filter { it.kind != Kind.OTHER }
            .joinToString("\n") { e ->
                val team = e.teamId?.let { abbrById[it] }?.let { " · $it" } ?: ""
                val who = e.primary ?: "?"
                when (e.kind) {
                    Kind.GOAL -> {
                        val note = when {
                            e.ownGoal -> " (OG)"
                            e.penalty -> " (pen)"
                            e.secondary != null -> " (${e.secondary})"
                            else -> ""
                        }
                        "${e.minute} ⚽ $who$note$team"
                    }
                    Kind.YELLOW -> "${e.minute} 🟨 $who$team"
                    Kind.RED -> "${e.minute} 🟥 $who$team"
                    Kind.SUB -> {
                        val off = e.secondary?.let { ", $it off" } ?: ""
                        "${e.minute} 🔁 $who on$off$team"
                    }
                    Kind.OTHER -> ""
                }.trim()
            }

    private fun formatScorers(keyEvents: List<JSONObject>, teamOrder: List<String>, nameById: Map<String, String>): String {
        val goals = keyEvents.map { parseKeyEvent(it) }.filter { it.kind == Kind.GOAL }
        if (goals.isEmpty()) return ""
        val byTeam = LinkedHashMap<String, MutableList<Event>>()
        teamOrder.forEach { byTeam[it] = mutableListOf() }
        goals.forEach { byTeam.getOrPut(it.teamId ?: "") { mutableListOf() }.add(it) }

        return byTeam.filterValues { it.isNotEmpty() }.map { (teamId, list) ->
            // Group repeat scorers: "Saka 23', 56'"
            val grouped = LinkedHashMap<String, MutableList<String>>()
            list.forEach { g ->
                val note = when {
                    g.ownGoal -> " (OG)"
                    g.penalty -> " (pen)"
                    else -> ""
                }
                grouped.getOrPut(g.primary ?: "?") { mutableListOf() }.add(g.minute + note)
            }
            val scorers = grouped.entries.joinToString(", ") { (name, mins) -> "$name ${mins.joinToString(", ")}" }
            val team = nameById[teamId] ?: "Goals"
            "$team: $scorers"
        }.joinToString("\n")
    }

    // ---------- Teams ----------

    fun parseTeams(json: String): List<TeamRef> {
        val root = JSONObject(json)
        val teams = root.arr("sports").objects().firstOrNull()
            ?.arr("leagues").objects().firstOrNull()
            ?.arr("teams").objects()
        return teams.orEmpty().mapNotNull { wrapper ->
            val t = wrapper.obj("team") ?: wrapper
            val id = t.str("id") ?: return@mapNotNull null
            TeamRef(id = id, name = teamName(t), abbr = t.str("abbreviation") ?: "")
        }.sortedBy { it.name }
    }
}
