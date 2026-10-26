package com.ballknowledge.model

/** A team the user follows. [id] is the data source's team id (ESPN). */
data class TeamRef(val id: String, val name: String, val abbr: String = "")

enum class MatchState { PRE, LIVE, FINAL, POSTPONED, UNKNOWN }

/** One fixture as cached from the data source. */
data class Fixture(
    val id: String,
    val league: String,
    val kickoffMillis: Long,
    val homeId: String,
    val homeName: String,
    val homeAbbr: String,
    val awayId: String,
    val awayName: String,
    val awayAbbr: String,
    val homeScore: String?,
    val awayScore: String?,
    val state: MatchState,
    val venue: String?,
    val venueCity: String?,
    val venueCountry: String?,
    /** Cup / European stage label from the source, e.g. "R4", "League phase", "QF · Leg 1". */
    val stage: String? = null,
    /** What the Matchday field shows, e.g. "MW9", "League phase · MD3", "R16 · Leg 2". */
    val matchday: String? = null,
)

data class FixtureCache(
    val season: Int = 0,
    val lastSyncMillis: Long = 0L,
    val fixtures: List<Fixture> = emptyList(),
    /** Fantasy Premier League team id -> name, cached once per season for matching gameweeks. */
    val fplTeams: Map<Int, String> = emptyMap(),
)

enum class Phase { NONE, H1, H2, ET1, ET2, FT }

/**
 * The match clock. The displayed minute is [anchorMinute] plus whole minutes
 * elapsed since [anchorMillis]. Re-anchoring is how the user sets "it's 7' now".
 */
data class ClockState(
    val phase: Phase = Phase.NONE,
    val anchorMillis: Long = 0L,
    val anchorMinute: Int = 0,
)

data class DiaryEntry(
    val id: String,
    val minute: String,
    val text: String,
    val createdAt: Long,
)

data class MatchPage(
    val id: String,
    val fixtureId: String?,
    val league: String?,
    val sortMillis: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val fields: Map<String, String> = emptyMap(),
    /** Keys of fields the user has typed into; a refresh never overwrites these. */
    val edited: Set<String> = emptySet(),
    val diary: List<DiaryEntry> = emptyList(),
    val clock: ClockState = ClockState(),
) {
    fun field(key: String): String = fields[key].orEmpty()
}

/** Every text field on a match page, in display order. */
object Fields {
    const val COMPETITION = "competition"
    const val MATCHDAY = "matchday"
    const val DATE = "date"
    const val KICKOFF = "kickoff"

    /** v1 stored date and kickoff together; split on load (see Migration). */
    const val LEGACY_DATETIME = "datetime"
    const val VENUE = "venue"
    const val HOME = "homeTeam"
    const val AWAY = "awayTeam"
    const val SCORE = "score"
    const val TITLE = "title"
    const val PRE_MATCH = "preMatch"
    const val HOME_FORMATION = "homeFormation"
    const val HOME_XI = "homeXI"
    const val HOME_SUBS = "homeSubs"
    const val AWAY_FORMATION = "awayFormation"
    const val AWAY_XI = "awayXI"
    const val AWAY_SUBS = "awaySubs"
    const val SCORERS = "scorers"
    const val EVENTS = "events"
    const val HOME_HIGHLIGHT = "homeHighlight"
    const val AWAY_HIGHLIGHT = "awayHighlight"
    const val MOTM = "motm"
    const val MOMENT = "moment"
    const val VERDICT = "verdict"
    const val NARRATIVE = "narrative"
    const val ELSEWHERE = "elsewhere"
    const val NEXT_OPPONENT = "nextOpponent"
    const val RATING = "rating"
    const val ATTENTION = "attention"

    /** Fields that the data source may fill in. */
    val AUTO = setOf(
        COMPETITION, MATCHDAY, DATE, KICKOFF, VENUE, HOME, AWAY, SCORE,
        HOME_FORMATION, HOME_XI, HOME_SUBS, AWAY_FORMATION, AWAY_XI, AWAY_SUBS,
        SCORERS, EVENTS,
    )
}

object Competitions {
    data class Comp(val slug: String, val label: String, val name: String)

    val ALL = listOf(
        Comp("eng.1", "PL", "Premier League"),
        Comp("uefa.champions", "UCL", "Champions League"),
        Comp("uefa.europa", "UEL", "Europa League"),
        Comp("uefa.europa.conf", "UECL", "Conference League"),
        Comp("eng.fa", "FAC", "FA Cup"),
        Comp("eng.league_cup", "EFL", "Carabao Cup"),
        Comp("eng.charity", "CS", "Community Shield"),
    )

    val SLUGS = ALL.map { it.slug }

    fun label(slug: String?): String = ALL.firstOrNull { it.slug == slug }?.label ?: ""
}

object DefaultTeams {
    // ESPN team ids. Names are also matched as a fallback.
    val ALL = listOf(
        TeamRef("359", "Arsenal", "ARS"),
        TeamRef("360", "Manchester United", "MAN"),
        TeamRef("367", "Tottenham Hotspur", "TOT"),
        TeamRef("363", "Chelsea", "CHE"),
        TeamRef("364", "Liverpool", "LIV"),
        TeamRef("382", "Manchester City", "MNC"),
    )
}

fun Fixture.isFollowedBy(teams: List<TeamRef>): Boolean {
    val ids = teams.map { it.id }.toSet()
    if (homeId in ids || awayId in ids) return true
    val names = teams.map { it.name.lowercase() }.toSet()
    return homeName.lowercase() in names || awayName.lowercase() in names
}
