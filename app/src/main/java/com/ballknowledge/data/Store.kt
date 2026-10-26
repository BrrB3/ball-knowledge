package com.ballknowledge.data

import android.util.AtomicFile
import com.ballknowledge.model.DefaultTeams
import com.ballknowledge.model.FixtureCache
import com.ballknowledge.model.MatchPage
import com.ballknowledge.model.TeamRef
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Everything lives in three small JSON files in the app's private storage.
 * AtomicFile means a crash mid-write can never corrupt them.
 */
class Store(dir: File) {
    private val pagesFile = AtomicFile(File(dir, "pages.json"))
    private val fixturesFile = AtomicFile(File(dir, "fixtures.json"))
    private val settingsFile = AtomicFile(File(dir, "settings.json"))
    private val lock = Any()

    private fun read(f: AtomicFile): String? = synchronized(lock) {
        runCatching { String(f.readFully(), Charsets.UTF_8) }.getOrNull()
    }

    private fun write(f: AtomicFile, text: String) = synchronized(lock) {
        val out = f.startWrite()
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
            throw e
        }
    }

    fun loadPages(): List<MatchPage> =
        read(pagesFile)?.let { runCatching { Serialization.pagesFromJson(JSONArray(it)) }.getOrNull() } ?: emptyList()

    fun savePages(pages: List<MatchPage>) = write(pagesFile, Serialization.pagesToJson(pages).toString())

    fun loadFixtures(): FixtureCache =
        read(fixturesFile)?.let { runCatching { Serialization.cacheFromJson(JSONObject(it)) }.getOrNull() } ?: FixtureCache()

    fun saveFixtures(cache: FixtureCache) = write(fixturesFile, Serialization.cacheToJson(cache).toString())

    fun loadTeams(): List<TeamRef> {
        val text = read(settingsFile) ?: return DefaultTeams.ALL
        return runCatching { Serialization.teamsFromJson(JSONObject(text).optJSONArray("teams")) }
            .getOrNull() ?: DefaultTeams.ALL
    }

    fun saveTeams(teams: List<TeamRef>) =
        write(settingsFile, JSONObject().put("teams", Serialization.teamsToJson(teams)).toString())
}
