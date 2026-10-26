package com.ballknowledge.data

import com.ballknowledge.logic.Autofill
import com.ballknowledge.logic.FixtureMerge
import com.ballknowledge.logic.Matchday
import com.ballknowledge.logic.TimeFormat
import com.ballknowledge.model.Competitions
import com.ballknowledge.model.Fields
import com.ballknowledge.model.Fixture
import com.ballknowledge.model.FixtureCache
import com.ballknowledge.model.MatchPage
import com.ballknowledge.model.MatchState
import com.ballknowledge.model.TeamRef
import com.ballknowledge.model.TransferWindow
import com.ballknowledge.model.WindowKind
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
import java.time.LocalDate
import java.util.UUID

data class SyncStatus(val running: Boolean = false, val error: String? = null)

data class AutofillResult(val values: Map<String, String>, val sortMillis: Long?, val message: String?)

data class RestorePlan(
    val backup: Serialization.Backup,
    val newPages: Int,
    val pageConflicts: Int,
    val newWindows: Int,
    val windowConflicts: Int,
) {
    val conflicts: Int get() = pageConflicts + windowConflicts
}

data class RestoreResult(val pages: Int, val windows: Int)

class Repository(
    private val store: Store,
    private val source: MatchDataSource,
    private val fpl: FplSource,
    private val rates: RateSource,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _teams = MutableStateFlow(store.loadTeams())
    val teams: StateFlow<List<TeamRef>> = _teams

    private val _fixtures = MutableStateFlow(store.loadFixtures())
    val fixtures: StateFlow<FixtureCache> = _fixtures

    private val _pages = MutableStateFlow(store.loadPages())
    val pages: StateFlow<List<MatchPage>> = _pages

    private val _windows = MutableStateFlow(store.loadWindows())
    val windows: StateFlow<List<TransferWindow>> = _windows

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

            // Official Premier League gameweeks from FPL; if unavailable, Matchday counts games.
            val fplTeams = if (cache.season == season && cache.fplTeams.isNotEmpty()) cache.fplTeams
            else runCatching { fpl.teams() }.getOrDefault(emptyMap())
            val fplFixtures = if (merged.any { it.league == "eng.1" }) runCatching { fpl.fixtures() }.getOrNull() else null
            val withMatchday = Matchday.assign(merged, followed, fplFixtures, fplTeams)

            val newCache = FixtureCache(season = season, lastSyncMillis = now, fixtures = withMatchday, fplTeams = fplTeams)
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

    // ---------- Match pages ----------

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
        savePagesNow()
        return page
    }

    fun createManualPage(home: String, away: String, competition: String, dateMillis: Long): MatchPage {
        val now = System.currentTimeMillis()
        val fields = mapOf(
            Fields.HOME to home.trim(),
            Fields.AWAY to away.trim(),
            Fields.COMPETITION to competition.trim(),
            Fields.DATE to TimeFormat.dateLine(dateMillis),
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
        savePagesNow()
        return page
    }

    /** Called on every keystroke; the write to disk is debounced. */
    fun updatePage(page: MatchPage) {
        val list = _pages.value
        val index = list.indexOfFirst { it.id == page.id }
        if (index < 0) return // deleted meanwhile
        _pages.value = list.toMutableList().also { it[index] = page }
        schedulePagesSave()
    }

    fun deletePage(id: String) {
        _pages.value = _pages.value.filterNot { it.id == id }
        savePagesNow()
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
                (details.fixture?.state ?: cached?.state) == MatchState.PRE ->
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
        // Keep what only the scoreboard knows (venue country, stage, matchday).
        val merged = f.copy(
            venue = f.venue ?: old.venue,
            venueCity = f.venueCity ?: old.venueCity,
            venueCountry = f.venueCountry ?: old.venueCountry,
            league = old.league,
            stage = f.stage ?: old.stage,
            matchday = f.matchday ?: old.matchday,
        )
        val list = cache.fixtures.toMutableList().also { it[index] = merged }
        val newCache = cache.copy(fixtures = list.sortedBy { it.kickoffMillis })
        _fixtures.value = newCache
        scope.launch(Dispatchers.IO) { runCatching { store.saveFixtures(newCache) } }
    }

    // ---------- Transfer windows ----------

    fun window(id: String): TransferWindow? = _windows.value.firstOrNull { it.id == id }

    fun createWindow(team: String, kind: WindowKind, year: Int): TransferWindow {
        val now = System.currentTimeMillis()
        val w = TransferWindow(
            id = UUID.randomUUID().toString(),
            team = team.trim(),
            kind = kind,
            year = year,
            createdAt = now,
            updatedAt = now,
        )
        _windows.value = _windows.value + w
        saveWindowsNow()
        return w
    }

    fun updateWindow(w: TransferWindow) {
        val list = _windows.value
        val index = list.indexOfFirst { it.id == w.id }
        if (index < 0) return
        _windows.value = list.toMutableList().also { it[index] = w }
        scheduleWindowsSave()
    }

    fun deleteWindow(id: String) {
        _windows.value = _windows.value.filterNot { it.id == id }
        saveWindowsNow()
    }

    /** True when the window needs an ECB rate: start date set and reached, and no rate typed or fetched. */
    fun wantsRate(w: TransferWindow): Boolean {
        if (w.rateEdited || w.rate.isNotBlank()) return false
        val start = runCatching { LocalDate.parse(w.startDate) }.getOrNull() ?: return false
        return !start.isAfter(LocalDate.now(TimeFormat.IST))
    }

    /** Looks up the EUR→GBP rate for the window's start date. Null if unavailable. */
    suspend fun fetchRate(w: TransferWindow): RateSource.Rate? {
        val start = runCatching { LocalDate.parse(w.startDate) }.getOrNull() ?: return null
        return runCatching { rates.eurToGbp(start) }.getOrNull()
    }

    // ---------- Saving ----------

    private var pagesJob: Job? = null
    private var windowsJob: Job? = null

    private fun schedulePagesSave() {
        pagesJob?.cancel()
        pagesJob = scope.launch {
            delay(400)
            val snapshot = _pages.value
            withContext(Dispatchers.IO) { runCatching { store.savePages(snapshot) } }
        }
    }

    private fun scheduleWindowsSave() {
        windowsJob?.cancel()
        windowsJob = scope.launch {
            delay(400)
            val snapshot = _windows.value
            withContext(Dispatchers.IO) { runCatching { store.saveWindows(snapshot) } }
        }
    }

    private fun savePagesNow() {
        pagesJob?.cancel()
        pagesJob = null
        runCatching { store.savePages(_pages.value) }
    }

    private fun saveWindowsNow() {
        windowsJob?.cancel()
        windowsJob = null
        runCatching { store.saveWindows(_windows.value) }
    }

    /** Called when leaving a page and when the app goes to the background. */
    fun flush() {
        savePagesNow()
        saveWindowsNow()
    }

    // ---------- Backup ----------

    fun backupJson(): String =
        Serialization.backupToJson(_teams.value, _pages.value, _windows.value, System.currentTimeMillis())

    fun planRestore(text: String): RestorePlan {
        val backup = Serialization.backupFromJson(text)
        var newPages = 0
        var pageConflicts = 0
        backup.pages.forEach { p ->
            val existing = matchingPage(_pages.value, p)
            when {
                existing == null -> newPages++
                existing != p -> pageConflicts++
            }
        }
        var newWindows = 0
        var windowConflicts = 0
        backup.windows.forEach { w ->
            val existing = _windows.value.firstOrNull { it.id == w.id }
            when {
                existing == null -> newWindows++
                existing != w -> windowConflicts++
            }
        }
        return RestorePlan(backup, newPages, pageConflicts, newWindows, windowConflicts)
    }

    /** Merges a backup in. Match pages and windows are restored independently. */
    fun applyRestore(plan: RestorePlan, replaceConflicts: Boolean): RestoreResult {
        val pages = _pages.value.toMutableList()
        var pageCount = 0
        plan.backup.pages.forEach { p ->
            val index = pages.indexOfFirst { it.id == p.id || (p.fixtureId != null && it.fixtureId == p.fixtureId) }
            if (index < 0) {
                pages.add(p); pageCount++
            } else if (replaceConflicts && pages[index] != p) {
                pages[index] = p; pageCount++
            }
        }
        _pages.value = pages
        savePagesNow()

        val windows = _windows.value.toMutableList()
        var windowCount = 0
        plan.backup.windows.forEach { w ->
            val index = windows.indexOfFirst { it.id == w.id }
            if (index < 0) {
                windows.add(w); windowCount++
            } else if (replaceConflicts && windows[index] != w) {
                windows[index] = w; windowCount++
            }
        }
        _windows.value = windows
        saveWindowsNow()

        val missingTeams = plan.backup.teams.filter { t -> _teams.value.none { it.id == t.id } }
        if (missingTeams.isNotEmpty()) {
            val list = _teams.value + missingTeams
            _teams.value = list
            store.saveTeams(list)
            scope.launch { refreshFixtures(fullSeason = true) }
        }
        return RestoreResult(pageCount, windowCount)
    }

    private fun matchingPage(list: List<MatchPage>, p: MatchPage): MatchPage? =
        list.firstOrNull { it.id == p.id || (p.fixtureId != null && it.fixtureId == p.fixtureId) }
}
