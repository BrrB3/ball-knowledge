package com.ballknowledge.data

import com.ballknowledge.logic.Autofill
import com.ballknowledge.logic.FixtureMerge
import com.ballknowledge.logic.TimeFormat
import com.ballknowledge.model.Competitions
import com.ballknowledge.model.Fields
import com.ballknowledge.model.Fixture
import com.ballknowledge.model.FixtureCache
import com.ballknowledge.model.MatchPage
import com.ballknowledge.model.TeamRef
import com.ballknowledge.model.isFollowedBy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class SyncStatus(val running: Boolean = false, val error: String? = null)

data class AutofillResult(val values: Map<String, String>, val sortMillis: Long?, val message: String?)

data class RestorePlan(val backup: Serialization.Backup, val newPages: Int, val conflicts: Int)

class Repository(private val store: Store, private val source: MatchDataSource) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _teams = MutableStateFlow(store.loadTeams())
    val teams: StateFlow<List<TeamRef>> = _teams

    private val _fixtures = MutableStateFlow(store.loadFixtures())
    val fixtures: StateFlow<FixtureCache> = _fixtures

    private val _pages = MutableStateFlow(store.loadPages())
    val pages: StateFlow<List<MatchPage>> = _pages

    private val _sync = MutableStateFlow(SyncStatus())
    val sync: StateFlow<SyncStatus> = _sync

    // ---------- Fixtures ----------

    fun refreshIfStale() {
        val c = _fixtures.value
        val now = System.currentTimeMillis()
        val stale = now - c.lastSyncMillis > 6 * 60 * 60 * 1000L
        val wrongSeason = c.season != TimeFormat.seasonStartYear(now)
        if (stale || wrongSeason || c.fixtures.isEmpty()) requestRefresh()
    }

    /** Starts a refresh that keeps running even if the screen that asked for it closes. */
    fun requestRefresh(fullSeason: Boolean = false) {
        scope.launch { refreshFixtures(fullSeason) }
    }

    /** Normal refresh covers last month to two months ahead; [fullSeason] re-fetches July to June. */
    suspend fun refreshFixtures(fullSeason: Boolean = false) {
        if (_sync.value.running) return
        _sync.value = SyncStatus(running = true)
        try {
            val now = System.currentTimeMillis()
            val season = TimeFormat.seasonStartYear(now)
            val cache = _fixtures.value
            val full = fullSeason || cache.season != season || cache.fixtures.isEmpty()
            val seasonMonths = TimeFormat.seasonMonths(season)
            val months = if (full) seasonMonths else {
                val current = TimeFormat.yearMonthUk(now)
                (-1L..2L).map { current.plusMonths(it) }.filter { it in seasonMonths }
            }
            val result = source.fetchFixtures(Competitions.SLUGS, months)
            if (result.covered.isEmpty()) {
                _sync.value = SyncStatus(error = "Couldn't reach the data source. Showing saved fixtures.")
                return
            }
            val followed = _teams.value
            val fresh = result.fixtures.filter { it.isFollowedBy(followed) }
            val base = if (cache.season == season) cache.fixtures else emptyList()
            val merged = FixtureMerge.merge(base, fresh, result.covered)
            val newCache = FixtureCache(season = season, lastSyncMillis = now, fixtures = merged)
            _fixtures.value = newCache
            withContext(Dispatchers.IO) { store.saveFixtures(newCache) }
            _sync.value = SyncStatus(
                error = if (result.failures > 0) "Some competitions couldn't be updated. Try refreshing again later." else null
            )
        } catch (e: Exception) {
            _sync.value = SyncStatus(error = "Couldn't update fixtures. Showing saved fixtures.")
        }
    }

    fun clearSyncError() {
        if (_sync.value.error != null) _sync.value = _sync.value.copy(error = null)
    }

    // ---------- Teams ----------

    suspend fun availableTeams(): List<TeamRef> = source.fetchTeams()

    fun addTeam(team: TeamRef) {
        if (_teams.value.any { it.id == team.id }) return
        val list = _teams.value + team
        _teams.value = list
        store.saveTeams(list)
        scope.launch { refreshFixtures(fullSeason = true) }
    }

    fun removeTeam(id: String) {
        val list = _teams.value.filterNot { it.id == id }
        _teams.value = list
        store.saveTeams(list)
    }

    // ---------- Pages ----------

    fun page(id: String): MatchPage? = _pages.value.firstOrNull { it.id == id }

    fun fixture(id: String?): Fixture? = id?.let { fid -> _fixtures.value.fixtures.firstOrNull { it.id == fid } }

    fun createPageFor(f: Fixture): MatchPage {
        _pages.value.firstOrNull { it.fixtureId == f.id }?.let { return it }
        val now = System.currentTimeMillis()
        val page = MatchPage(
            id = UUID.randomUUID().toString(),
            fixtureId = f.id,
            league = f.league,
            sortMillis = f.kickoffMillis,
            createdAt = now,
            updatedAt = now,
            fields = Autofill.fromFixture(f).filterValues { it.isNotBlank() },
        )
        _pages.value = _pages.value + page
        saveNow()
        return page
    }

    fun createManualPage(home: String, away: String, competition: String, dateMillis: Long): MatchPage {
        val now = System.currentTimeMillis()
        val fields = mapOf(
            Fields.HOME to home.trim(),
            Fields.AWAY to away.trim(),
            Fields.COMPETITION to competition.trim(),
            Fields.DATETIME to TimeFormat.kickoff(dateMillis, null, withYear = true).date,
        ).filterValues { it.isNotBlank() }
        val page = MatchPage(
            id = UUID.randomUUID().toString(),
            fixtureId = null,
            league = null,
            sortMillis = dateMillis,
            createdAt = now,
            updatedAt = now,
            fields = fields,
            edited = fields.keys,
        )
        _pages.value = _pages.value + page
        saveNow()
        return page
    }

    /** Called on every keystroke; the write to disk is debounced. */
    fun updatePage(page: MatchPage) {
        val list = _pages.value
        val index = list.indexOfFirst { it.id == page.id }
        if (index < 0) return // deleted meanwhile
        _pages.value = list.toMutableList().also { it[index] = page }
        scheduleSave()
    }

    fun deletePage(id: String) {
        _pages.value = _pages.value.filterNot { it.id == id }
        saveNow()
    }

    /** Fetches whatever the source knows about this page's match. */
    suspend fun fetchAutofill(page: MatchPage): AutofillResult {
        val fixtureId = page.fixtureId ?: return AutofillResult(emptyMap(), null, "This match was added by hand, so there's nothing to fetch.")
        val cached = fixture(fixtureId)
        val league = page.league ?: cached?.league ?: return AutofillResult(emptyMap(), null, "Unknown competition.")
        val details = source.fetchDetails(league, fixtureId)
            ?: return AutofillResult(emptyMap(), null, "Couldn't reach the data source. You can fill this in by hand.")
        details.fixture?.let { updateCachedFixture(it) }
        val values = Autofill.fromDetails(details, cached)
        val message = when {
            details.home == null && details.away == null &&
                (details.fixture?.state ?: cached?.state) == com.ballknowledge.model.MatchState.PRE ->
                "Lineups aren't out yet — usually about an hour before kickoff."
            details.home == null && details.away == null -> "No lineups available for this match."
            else -> null
        }
        return AutofillResult(values, details.fixture?.kickoffMillis ?: cached?.kickoffMillis, message)
    }

    private fun updateCachedFixture(f: Fixture) {
        val cache = _fixtures.value
        val index = cache.fixtures.indexOfFirst { it.id == f.id }
        if (index < 0) return
        val old = cache.fixtures[index]
        // Keep venue details from the scoreboard if the summary lacked them.
        val merged = f.copy(
            venue = f.venue ?: old.venue,
            venueCity = f.venueCity ?: old.venueCity,
            venueCountry = f.venueCountry ?: old.venueCountry,
            league = old.league,
        )
        val list = cache.fixtures.toMutableList().also { it[index] = merged }
        val newCache = cache.copy(fixtures = list.sortedBy { it.kickoffMillis })
        _fixtures.value = newCache
        scope.launch(Dispatchers.IO) { runCatching { store.saveFixtures(newCache) } }
    }

    // ---------- Saving ----------

    private var saveJob: Job? = null

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            val snapshot = _pages.value
            withContext(Dispatchers.IO) { runCatching { store.savePages(snapshot) } }
        }
    }

    private fun saveNow() {
        saveJob?.cancel()
        saveJob = null
        runCatching { store.savePages(_pages.value) }
    }

    /** Called when the app goes to the background. */
    fun flush() = saveNow()

    // ---------- Backup ----------

    fun backupJson(): String = Serialization.backupToJson(_teams.value, _pages.value, System.currentTimeMillis())

    fun planRestore(text: String): RestorePlan {
        val backup = Serialization.backupFromJson(text)
        var conflicts = 0
        var fresh = 0
        backup.pages.forEach { p ->
            val existing = findMatching(p)
            when {
                existing == null -> fresh++
                existing != p -> conflicts++
            }
        }
        return RestorePlan(backup, fresh, conflicts)
    }

    /** Merges a backup in. Returns the number of pages added or replaced. */
    fun applyRestore(plan: RestorePlan, replaceConflicts: Boolean): Int {
        val pages = _pages.value.toMutableList()
        var count = 0
        plan.backup.pages.forEach { p ->
            val index = pages.indexOfFirst { it.id == p.id || (p.fixtureId != null && it.fixtureId == p.fixtureId) }
            if (index < 0) {
                pages.add(p); count++
            } else if (replaceConflicts && pages[index] != p) {
                pages[index] = p; count++
            }
        }
        _pages.value = pages
        saveNow()

        val missingTeams = plan.backup.teams.filter { t -> _teams.value.none { it.id == t.id } }
        if (missingTeams.isNotEmpty()) {
            val list = _teams.value + missingTeams
            _teams.value = list
            store.saveTeams(list)
            scope.launch { refreshFixtures(fullSeason = true) }
        }
        return count
    }

    private fun findMatching(p: MatchPage): MatchPage? =
        _pages.value.firstOrNull { it.id == p.id || (p.fixtureId != null && it.fixtureId == p.fixtureId) }
}
