package com.ballknowledge.logic

import com.ballknowledge.model.Fields
import com.ballknowledge.model.MatchPage

/** Upgrades data saved by older versions. Safe to run repeatedly. */
object Migration {

    /** v1 kept "Sat 31 Oct 2026 · 8:30 pm IST (3:00 pm UK)" in one field; v2 splits it. */
    fun page(p: MatchPage): MatchPage {
        val legacy = p.fields[Fields.LEGACY_DATETIME] ?: return p
        val fields = p.fields.toMutableMap()
        fields.remove(Fields.LEGACY_DATETIME)
        val (date, kickoff) = splitDateTime(legacy)
        if (fields[Fields.DATE].isNullOrBlank() && date.isNotBlank()) fields[Fields.DATE] = date
        if (fields[Fields.KICKOFF].isNullOrBlank() && kickoff.isNotBlank()) fields[Fields.KICKOFF] = kickoff
        val edited = p.edited.toMutableSet()
        if (edited.remove(Fields.LEGACY_DATETIME)) {
            edited.add(Fields.DATE)
            edited.add(Fields.KICKOFF)
        }
        return p.copy(fields = fields, edited = edited)
    }

    fun splitDateTime(text: String): Pair<String, String> {
        val i = text.indexOf(" · ")
        return if (i < 0) text.trim() to "" else text.substring(0, i).trim() to text.substring(i + 3).trim()
    }
}
