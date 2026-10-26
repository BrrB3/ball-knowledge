package com.ballknowledge.logic

import com.ballknowledge.model.ClockState
import com.ballknowledge.model.DiaryEntry
import com.ballknowledge.model.Phase

object MatchClock {

    fun cap(phase: Phase): Int? = when (phase) {
        Phase.H1 -> 45
        Phase.H2 -> 90
        Phase.ET1 -> 105
        Phase.ET2 -> 120
        else -> null
    }

    /** The minute a phase starts on, used to pre-fill the "minute now" box. */
    fun defaultStart(phase: Phase): Int = when (phase) {
        Phase.H1 -> 1
        Phase.H2 -> 46
        Phase.ET1 -> 91
        Phase.ET2 -> 106
        else -> 0
    }

    fun phaseName(phase: Phase): String = when (phase) {
        Phase.NONE -> "Not started"
        Phase.H1 -> "1st half"
        Phase.H2 -> "2nd half"
        Phase.ET1 -> "Extra time, 1st half"
        Phase.ET2 -> "Extra time, 2nd half"
        Phase.FT -> "Full time"
    }

    fun isRunning(clock: ClockState): Boolean = cap(clock.phase) != null

    /** Raw running minute (e.g. 47 in the first half means 45+2). */
    fun rawMinute(clock: ClockState, now: Long): Int? {
        if (!isRunning(clock)) return null
        val elapsed = ((now - clock.anchorMillis).coerceAtLeast(0L) / 60_000L).toInt()
        return clock.anchorMinute + elapsed
    }

    fun format(minute: Int, phase: Phase): String {
        val c = cap(phase) ?: return "$minute"
        return if (minute > c) "$c+${minute - c}" else "$minute"
    }

    /** What the clock shows: "23", "45+2", "FT" or "–". */
    fun display(clock: ClockState, now: Long): String = when (clock.phase) {
        Phase.NONE -> "–"
        Phase.FT -> "FT"
        else -> format(rawMinute(clock, now) ?: 0, clock.phase)
    }

    /** The minute stamped on a new diary entry. */
    fun stamp(clock: ClockState, now: Long): String = when (clock.phase) {
        Phase.NONE -> ""
        Phase.FT -> "FT"
        else -> format(rawMinute(clock, now) ?: 0, clock.phase)
    }

    /** Sets the clock so that it reads [minute] at [now] in [phase]. */
    fun anchor(phase: Phase, minute: Int, now: Long): ClockState =
        ClockState(phase = phase, anchorMillis = now, anchorMinute = minute.coerceIn(0, 200))

    /** Parses what the user types as a minute: "23", "45+2", "90 + 4", "67'". */
    fun parseMinute(text: String): Int? {
        val m = MINUTE.find(text.trim()) ?: return null
        val base = m.groupValues[1].toIntOrNull() ?: return null
        val added = m.groupValues[2].toIntOrNull() ?: 0
        return base + added
    }

    /** Sort order for diary minutes; unparseable or blank minutes go last. */
    fun sortKey(minute: String): Long {
        val t = minute.trim().uppercase()
        if (t.isEmpty()) return Long.MAX_VALUE
        if (t == "FT") return 1_000_000L
        if (t == "HT") return 45_999L
        val m = MINUTE.find(t) ?: return Long.MAX_VALUE - 1
        val base = m.groupValues[1].toLong()
        val added = m.groupValues[2].toLongOrNull() ?: 0L
        return base * 1000L + added
    }

    fun sorted(entries: List<DiaryEntry>): List<DiaryEntry> =
        entries.sortedWith(compareBy<DiaryEntry> { sortKey(it.minute) }.thenBy { it.createdAt })

    /** Diary minutes are shown with an apostrophe: 23'  45+2'. */
    fun label(minute: String): String {
        val t = minute.trim().removeSuffix("'").trim()
        if (t.isEmpty()) return "—"
        return if (t.any { it.isDigit() }) "$t'" else t
    }

    private val MINUTE = Regex("""^(\d{1,3})'?\s*(?:\+\s*(\d{1,2}))?'?$""")
}
