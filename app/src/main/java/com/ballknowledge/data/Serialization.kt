package com.ballknowledge.data

import com.ballknowledge.model.ClockState
import com.ballknowledge.model.DiaryEntry
import com.ballknowledge.model.Fixture
import com.ballknowledge.model.FixtureCache
import com.ballknowledge.model.MatchPage
import com.ballknowledge.model.MatchState
import com.ballknowledge.model.Phase
import com.ballknowledge.model.TeamRef
import org.json.JSONArray
import org.json.JSONObject

/** Converts app data to and from JSON (used for storage and backups). */
object Serialization {

    private fun JSONObject.s(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it != "null" }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.putOpt2(key: String, value: Any?): JSONObject {
        put(key, value ?: JSONObject.NULL)
        return this
    }

    // ---------- Teams ----------

    fun teamToJson(t: TeamRef): JSONObject = JSONObject()
        .put("id", t.id).put("name", t.name).put("abbr", t.abbr)

    fun teamFromJson(o: JSONObject): TeamRef? {
        val id = o.s("id") ?: return null
        return TeamRef(id, o.s("name") ?: "?", o.s("abbr") ?: "")
    }

    fun teamsToJson(list: List<TeamRef>): JSONArray = JSONArray().apply { list.forEach { put(teamToJson(it)) } }

    fun teamsFromJson(a: JSONArray?): List<TeamRef> = a.objects().mapNotNull { teamFromJson(it) }

    // ---------- Fixtures ----------

    fun fixtureToJson(f: Fixture): JSONObject = JSONObject()
        .put("id", f.id).put("league", f.league).put("kickoff", f.kickoffMillis)
        .put("homeId", f.homeId).put("homeName", f.homeName).put("homeAbbr", f.homeAbbr)
        .put("awayId", f.awayId).put("awayName", f.awayName).put("awayAbbr", f.awayAbbr)
        .putOpt2("homeScore", f.homeScore).putOpt2("awayScore", f.awayScore)
        .put("state", f.state.name)
        .putOpt2("venue", f.venue).putOpt2("venueCity", f.venueCity).putOpt2("venueCountry", f.venueCountry)

    fun fixtureFromJson(o: JSONObject): Fixture? {
        val id = o.s("id") ?: return null
        return Fixture(
            id = id,
            league = o.s("league") ?: "",
            kickoffMillis = o.optLong("kickoff"),
            homeId = o.s("homeId") ?: "",
            homeName = o.s("homeName") ?: "?",
            homeAbbr = o.s("homeAbbr") ?: "",
            awayId = o.s("awayId") ?: "",
            awayName = o.s("awayName") ?: "?",
            awayAbbr = o.s("awayAbbr") ?: "",
            homeScore = o.s("homeScore"),
            awayScore = o.s("awayScore"),
            state = runCatching { MatchState.valueOf(o.s("state") ?: "") }.getOrDefault(MatchState.UNKNOWN),
            venue = o.s("venue"),
            venueCity = o.s("venueCity"),
            venueCountry = o.s("venueCountry"),
        )
    }

    fun cacheToJson(c: FixtureCache): JSONObject = JSONObject()
        .put("season", c.season).put("lastSync", c.lastSyncMillis)
        .put("fixtures", JSONArray().apply { c.fixtures.forEach { put(fixtureToJson(it)) } })

    fun cacheFromJson(o: JSONObject): FixtureCache = FixtureCache(
        season = o.optInt("season"),
        lastSyncMillis = o.optLong("lastSync"),
        fixtures = o.optJSONArray("fixtures").objects().mapNotNull { fixtureFromJson(it) },
    )

    // ---------- Match pages ----------

    fun pageToJson(p: MatchPage): JSONObject {
        val fields = JSONObject()
        p.fields.forEach { (k, v) -> fields.put(k, v) }
        val diary = JSONArray()
        p.diary.forEach { d ->
            diary.put(JSONObject().put("id", d.id).put("minute", d.minute).put("text", d.text).put("createdAt", d.createdAt))
        }
        val clock = JSONObject()
            .put("phase", p.clock.phase.name)
            .put("anchorMillis", p.clock.anchorMillis)
            .put("anchorMinute", p.clock.anchorMinute)
        return JSONObject()
            .put("id", p.id)
            .putOpt2("fixtureId", p.fixtureId)
            .putOpt2("league", p.league)
            .put("sortMillis", p.sortMillis)
            .put("createdAt", p.createdAt)
            .put("updatedAt", p.updatedAt)
            .put("fields", fields)
            .put("edited", JSONArray().apply { p.edited.forEach { put(it) } })
            .put("diary", diary)
            .put("clock", clock)
    }

    fun pageFromJson(o: JSONObject): MatchPage? {
        val id = o.s("id") ?: return null
        val fieldsObj = o.optJSONObject("fields") ?: JSONObject()
        val fields = mutableMapOf<String, String>()
        fieldsObj.keys().forEach { k -> fieldsObj.s(k)?.let { fields[k] = it } }
        val editedArr = o.optJSONArray("edited")
        val edited = if (editedArr == null) emptySet() else (0 until editedArr.length()).mapNotNull { i ->
            editedArr.optString(i).takeIf { it.isNotEmpty() && it != "null" }
        }.toSet()
        val diary = o.optJSONArray("diary").objects().mapNotNull { d ->
            val did = d.s("id") ?: return@mapNotNull null
            DiaryEntry(did, d.s("minute") ?: "", d.s("text") ?: "", d.optLong("createdAt"))
        }
        val c = o.optJSONObject("clock")
        val clock = if (c == null) ClockState() else ClockState(
            phase = runCatching { Phase.valueOf(c.s("phase") ?: "") }.getOrDefault(Phase.NONE),
            anchorMillis = c.optLong("anchorMillis"),
            anchorMinute = c.optInt("anchorMinute"),
        )
        return MatchPage(
            id = id,
            fixtureId = o.s("fixtureId"),
            league = o.s("league"),
            sortMillis = o.optLong("sortMillis"),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            fields = fields,
            edited = edited,
            diary = diary,
            clock = clock,
        )
    }

    fun pagesToJson(list: List<MatchPage>): JSONArray = JSONArray().apply { list.forEach { put(pageToJson(it)) } }

    fun pagesFromJson(a: JSONArray?): List<MatchPage> = a.objects().mapNotNull { pageFromJson(it) }

    // ---------- Backup ----------

    const val BACKUP_FORMAT = "ball-knowledge-backup"

    data class Backup(val teams: List<TeamRef>, val pages: List<MatchPage>)

    fun backupToJson(teams: List<TeamRef>, pages: List<MatchPage>, now: Long): String = JSONObject()
        .put("format", BACKUP_FORMAT)
        .put("version", 1)
        .put("exportedAt", now)
        .put("teams", teamsToJson(teams))
        .put("pages", pagesToJson(pages))
        .toString(2)

    /** Throws IllegalArgumentException if the text isn't a Ball Knowledge backup. */
    fun backupFromJson(text: String): Backup {
        val o = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("This file isn't valid JSON.") }
        if (o.s("format") != BACKUP_FORMAT) throw IllegalArgumentException("This file isn't a Ball Knowledge backup.")
        return Backup(teamsFromJson(o.optJSONArray("teams")), pagesFromJson(o.optJSONArray("pages")))
    }
}
