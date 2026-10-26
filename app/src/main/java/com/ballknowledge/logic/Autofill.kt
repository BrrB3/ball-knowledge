package com.ballknowledge.logic

import com.ballknowledge.data.MatchDetails
import com.ballknowledge.model.Competitions
import com.ballknowledge.model.Fields
import com.ballknowledge.model.Fixture
import com.ballknowledge.model.MatchPage
import com.ballknowledge.model.MatchState

/** Builds auto-filled values and merges them into a page without touching the user's edits. */
object Autofill {

    fun fromFixture(f: Fixture): Map<String, String> {
        val venue = listOfNotNull(f.venue, f.venueCity).distinct().joinToString(", ")
        val values = mutableMapOf(
            Fields.COMPETITION to Competitions.label(f.league),
            Fields.MATCHDAY to f.matchday.orEmpty(),
            Fields.DATE to TimeFormat.dateLine(f.kickoffMillis),
            Fields.KICKOFF to TimeFormat.kickoffTimeLine(f.kickoffMillis, f.venueCountry),
            Fields.VENUE to venue,
            Fields.HOME to f.homeName,
            Fields.AWAY to f.awayName,
        )
        if (f.state == MatchState.FINAL && f.homeScore != null && f.awayScore != null) {
            values[Fields.SCORE] = "${f.homeScore}–${f.awayScore}"
        }
        return values
    }

    fun fromDetails(d: MatchDetails, fallbackFixture: Fixture?): Map<String, String> {
        val values = mutableMapOf<String, String>()
        // The summary has no stage/matchday or (sometimes) venue country; keep the cached ones.
        val f = d.fixture?.let { fresh ->
            fresh.copy(
                stage = fresh.stage ?: fallbackFixture?.stage,
                matchday = fresh.matchday ?: fallbackFixture?.matchday,
                venueCountry = fresh.venueCountry ?: fallbackFixture?.venueCountry,
                venue = fresh.venue ?: fallbackFixture?.venue,
                venueCity = fresh.venueCity ?: fallbackFixture?.venueCity,
            )
        } ?: fallbackFixture
        if (f != null) values.putAll(fromFixture(f))
        d.home?.let {
            values[Fields.HOME_FORMATION] = it.formation
            values[Fields.HOME_XI] = it.startingXI
            values[Fields.HOME_SUBS] = it.subs
        }
        d.away?.let {
            values[Fields.AWAY_FORMATION] = it.formation
            values[Fields.AWAY_XI] = it.startingXI
            values[Fields.AWAY_SUBS] = it.subs
        }
        // Scorers and events are filled once the match is over.
        if (f?.state == MatchState.FINAL) {
            values[Fields.SCORERS] = d.scorers
            values[Fields.EVENTS] = d.events
        }
        return values
    }

    /**
     * Applies [values] to [page]. A field the user has edited is left alone
     * unless it's blank. Blank incoming values never clear anything.
     */
    fun apply(page: MatchPage, values: Map<String, String>, now: Long, sortMillis: Long? = null): MatchPage {
        val fields = page.fields.toMutableMap()
        val edited = page.edited.toMutableSet()
        var changed = false
        for ((key, value) in values) {
            if (value.isBlank()) continue
            val current = fields[key].orEmpty()
            if (key in edited && current.isNotBlank()) continue
            if (current != value) {
                fields[key] = value
                changed = true
            }
            edited.remove(key)
        }
        val newSort = sortMillis ?: page.sortMillis
        if (!changed && newSort == page.sortMillis && edited == page.edited) return page
        return page.copy(fields = fields, edited = edited, sortMillis = newSort, updatedAt = now)
    }

    /** Whether opening this page should quietly try to fetch missing data. */
    fun wantsDetails(page: MatchPage, fixture: Fixture?, now: Long): Boolean {
        if (page.fixtureId == null) return false
        val noLineups = page.field(Fields.HOME_XI).isBlank() && page.field(Fields.AWAY_XI).isBlank()
        val kickoff = fixture?.kickoffMillis ?: page.sortMillis
        val probablyOver = now > kickoff + 2 * 60 * 60 * 1000L
        val missingResult = probablyOver && page.field(Fields.SCORE).isBlank()
        // Lineups appear roughly an hour before kickoff.
        val lineupsLikely = now > kickoff - 90 * 60 * 1000L
        return (noLineups && lineupsLikely) || missingResult
    }
}
