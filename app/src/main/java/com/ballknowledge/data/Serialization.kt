package com.ballknowledge.data

import com.ballknowledge.logic.Migration
import com.ballknowledge.model.ClockState
import com.ballknowledge.model.Currency
import com.ballknowledge.model.Fee
import com.ballknowledge.model.InStatus
import com.ballknowledge.model.IncomingLink
import com.ballknowledge.model.OutStatus
import com.ballknowledge.model.OutgoingLink
import com.ballknowledge.model.TransferWindow
import com.ballknowledge.model.WindowKind
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
        .putOpt2("stage", f.stage).putOpt2("matchday", f.matchday)

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
            stage = o.s("stage"),
            matchday = o.s("matchday"),
        )
    }

    fun fixturesToJson(list: List<Fixture>): JSONArray = JSONArray().apply { list.forEach { put(fixtureToJson(it)) } }

    fun fixturesFromJson(a: JSONArray?): List<Fixture> = a.objects().mapNotNull { fixtureFromJson(it) }

    fun cacheToJson(c: FixtureCache): JSONObject = JSONObject()
        .put("season", c.season).put("lastSync", c.lastSyncMillis).put("dataVersion", c.dataVersion)
        .put("fixtures", JSONArray().apply { c.fixtures.forEach { put(fixtureToJson(it)) } })
        .put("fplTeams", JSONObject().apply { c.fplTeams.forEach { (k, v) -> put(k.toString(), v) } })

    fun cacheFromJson(o: JSONObject): FixtureCache = FixtureCache(
        season = o.optInt("season"),
        lastSyncMillis = o.optLong("lastSync"),
        dataVersion = o.optInt("dataVersion"),
        fixtures = o.optJSONArray("fixtures").objects().mapNotNull { fixtureFromJson(it) },
        fplTeams = o.optJSONObject("fplTeams")?.let { t ->
            val map = mutableMapOf<Int, String>()
            t.keys().forEach { k -> k.toIntOrNull()?.let { id -> t.s(k)?.let { map[id] = it } } }
            map
        } ?: emptyMap(),
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
            .put("editedAt", p.editedAt)
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
        return Migration.page(MatchPage(
            id = id,
            fixtureId = o.s("fixtureId"),
            league = o.s("league"),
            sortMillis = o.optLong("sortMillis"),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            editedAt = if (o.has("editedAt")) o.optLong("editedAt") else o.optLong("updatedAt"),
            fields = fields,
            edited = edited,
            diary = diary,
            clock = clock,
        ))
    }

    fun pagesToJson(list: List<MatchPage>): JSONArray = JSONArray().apply { list.forEach { put(pageToJson(it)) } }

    fun pagesFromJson(a: JSONArray?): List<MatchPage> = a.objects().mapNotNull { pageFromJson(it) }

    // ---------- Transfer windows ----------

    private fun feeToJson(f: Fee): JSONObject = JSONObject()
        .put("main", f.main).put("addons", f.addons).put("currency", f.currency.name)

    private fun feeFromJson(o: JSONObject?): Fee {
        if (o == null) return Fee()
        return Fee(
            main = o.s("main") ?: "",
            addons = o.s("addons") ?: "",
            currency = runCatching { Currency.valueOf(o.s("currency") ?: "") }.getOrDefault(Currency.GBP),
        )
    }

    fun windowToJson(w: TransferWindow): JSONObject {
        val incoming = JSONArray()
        w.incoming.forEach { l ->
            incoming.put(JSONObject()
                .put("id", l.id).put("name", l.name).put("position", l.position)
                .put("firstLinked", l.firstLinked).put("sources", l.sources).put("alsoLinked", l.alsoLinked)
                .put("status", l.status.name).put("fee", feeToJson(l.fee)).put("note", l.note).put("createdAt", l.createdAt))
        }
        val outgoing = JSONArray()
        w.outgoing.forEach { l ->
            outgoing.put(JSONObject()
                .put("id", l.id).put("name", l.name).put("position", l.position)
                .put("linkedClubs", l.linkedClubs).put("status", l.status.name).put("fee", feeToJson(l.fee))
                .put("note", l.note).put("createdAt", l.createdAt))
        }
        val overrides = JSONObject()
        w.spendOverrides.forEach { (k, v) -> overrides.put(k, v) }
        return JSONObject()
            .put("id", w.id).put("team", w.team).put("kind", w.kind.name).put("year", w.year)
            .put("startDate", w.startDate).put("endDate", w.endDate)
            .put("rate", w.rate).put("rateEdited", w.rateEdited).put("rateDate", w.rateDate)
            .put("wishlist", w.wishlist).put("verdict", w.verdict)
            .put("incoming", incoming).put("outgoing", outgoing).put("spendOverrides", overrides)
            .put("createdAt", w.createdAt).put("updatedAt", w.updatedAt).put("editedAt", w.editedAt)
    }

    fun windowFromJson(o: JSONObject): TransferWindow? {
        val id = o.s("id") ?: return null
        val incoming = o.optJSONArray("incoming").objects().mapNotNull { l ->
            val lid = l.s("id") ?: return@mapNotNull null
            IncomingLink(
                id = lid, name = l.s("name") ?: "", position = l.s("position") ?: "",
                firstLinked = l.s("firstLinked") ?: "", sources = l.s("sources") ?: "", alsoLinked = l.s("alsoLinked") ?: "",
                status = runCatching { InStatus.valueOf(l.s("status") ?: "") }.getOrDefault(InStatus.LINKED),
                fee = feeFromJson(l.optJSONObject("fee")), note = l.s("note") ?: "", createdAt = l.optLong("createdAt"),
            )
        }
        val outgoing = o.optJSONArray("outgoing").objects().mapNotNull { l ->
            val lid = l.s("id") ?: return@mapNotNull null
            OutgoingLink(
                id = lid, name = l.s("name") ?: "", position = l.s("position") ?: "",
                linkedClubs = l.s("linkedClubs") ?: "",
                status = runCatching { OutStatus.valueOf(l.s("status") ?: "") }.getOrDefault(OutStatus.LINKED),
                fee = feeFromJson(l.optJSONObject("fee")), note = l.s("note") ?: "", createdAt = l.optLong("createdAt"),
            )
        }
        val overrides = mutableMapOf<String, String>()
        o.optJSONObject("spendOverrides")?.let { ov -> ov.keys().forEach { k -> ov.s(k)?.let { overrides[k] = it } } }
        return TransferWindow(
            id = id,
            team = o.s("team") ?: "",
            kind = runCatching { WindowKind.valueOf(o.s("kind") ?: "") }.getOrDefault(WindowKind.SUMMER),
            year = o.optInt("year"),
            startDate = o.s("startDate") ?: "",
            endDate = o.s("endDate") ?: "",
            rate = o.s("rate") ?: "",
            rateEdited = o.optBoolean("rateEdited"),
            rateDate = o.s("rateDate") ?: "",
            wishlist = o.s("wishlist") ?: "",
            verdict = o.s("verdict") ?: "",
            incoming = incoming,
            outgoing = outgoing,
            spendOverrides = overrides,
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            editedAt = if (o.has("editedAt")) o.optLong("editedAt") else o.optLong("updatedAt"),
        )
    }

    fun windowsToJson(list: List<TransferWindow>): JSONArray = JSONArray().apply { list.forEach { put(windowToJson(it)) } }

    fun windowsFromJson(a: JSONArray?): List<TransferWindow> = a.objects().mapNotNull { windowFromJson(it) }

    // ---------- Backup ----------

    const val BACKUP_FORMAT = "ball-knowledge-backup"

    /** Match pages and transfer windows are kept in separate sections of the one file. */
    data class Backup(val teams: List<TeamRef>, val pages: List<MatchPage>, val windows: List<TransferWindow>)

    fun backupToJson(teams: List<TeamRef>, pages: List<MatchPage>, windows: List<TransferWindow>, now: Long): String = JSONObject()
        .put("format", BACKUP_FORMAT)
        .put("version", 2)
        .put("exportedAt", now)
        .put("teams", teamsToJson(teams))
        .put("pages", pagesToJson(pages))
        .put("transferWindows", windowsToJson(windows))
        .toString(2)

    /** Throws IllegalArgumentException if the text isn't a Ball Knowledge backup. Reads v1 and v2. */
    fun backupFromJson(text: String): Backup {
        val o = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("This file isn't valid JSON.") }
        if (o.s("format") != BACKUP_FORMAT) throw IllegalArgumentException("This file isn't a Ball Knowledge backup.")
        return Backup(
            teams = teamsFromJson(o.optJSONArray("teams")),
            pages = pagesFromJson(o.optJSONArray("pages")),
            windows = windowsFromJson(o.optJSONArray("transferWindows")),
        )
    }
}
