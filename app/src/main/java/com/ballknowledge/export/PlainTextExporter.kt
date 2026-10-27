package com.ballknowledge.export

import com.ballknowledge.logic.MatchClock
import com.ballknowledge.logic.TransferMath
import com.ballknowledge.model.Fields
import com.ballknowledge.model.MatchPage
import com.ballknowledge.model.SpendKeys
import com.ballknowledge.model.TransferWindow

/** Plain text for sharing one page by WhatsApp or email. Empty fields are skipped. */
object PlainTextExporter {

    private fun StringBuilder.block(heading: String, text: String) {
        if (text.isBlank()) return
        append("\n").append(heading.uppercase()).append("\n").append(text.trim()).append("\n")
    }

    fun match(p: MatchPage): String = buildString {
        append(HtmlExporter.teamsLine(p).uppercase()).append("\n")
        append(HtmlExporter.matchMeta(p).joinToString(" · ")).append("\n")
        p.field(Fields.TITLE).takeIf { it.isNotBlank() }?.let { append("\n").append(it.trim()).append("\n") }
        HtmlExporter.ratingsLine(p).takeIf { it.isNotBlank() }?.let { append(it).append("\n") }
        HtmlExporter.editedLine(p.editedAt).takeIf { it.isNotBlank() }?.let { append(it).append("\n") }

        HtmlExporter.beforeLineups(p).forEach { (h, t) -> block(h, t) }

        val home = p.field(Fields.HOME).ifBlank { "Home" }
        val away = p.field(Fields.AWAY).ifBlank { "Away" }
        fun lineup(team: String, formation: String, xi: String, subs: String) {
            if (formation.isBlank() && xi.isBlank() && subs.isBlank()) return
            append("\n").append(team.uppercase())
            if (formation.isNotBlank()) append(" (").append(formation.trim()).append(")")
            append("\n")
            if (xi.isNotBlank()) append(xi.trim()).append("\n")
            if (subs.isNotBlank()) append("Subs: ").append(subs.trim().lines().joinToString(", ")).append("\n")
        }
        lineup(home, p.field(Fields.HOME_FORMATION), p.field(Fields.HOME_XI), p.field(Fields.HOME_SUBS))
        lineup(away, p.field(Fields.AWAY_FORMATION), p.field(Fields.AWAY_XI), p.field(Fields.AWAY_SUBS))

        val diary = MatchClock.sorted(p.diary).filter { it.text.isNotBlank() }
        if (diary.isNotEmpty()) {
            append("\nMATCH DIARY\n")
            diary.forEach { append(MatchClock.label(it.minute)).append("  ").append(it.text.trim()).append("\n") }
        }
        HtmlExporter.duringMatch(p).forEach { (h, t) -> block(h, t) }
        val stats = HtmlExporter.statRows(p)
        if (stats.isNotEmpty()) {
            append("\nMATCH STATS (").append(home).append(" – ").append(away).append(")\n")
            stats.forEach { (label, h, a) -> append(label).append(": ").append(h).append(" – ").append(a).append("\n") }
        }
        HtmlExporter.afterMatch(p).forEach { (h, t) -> block(h, t) }
    }.trim()

    fun window(w: TransferWindow): String = buildString {
        append(w.title.uppercase()).append("\n")
        append(HtmlExporter.windowMeta(w).joinToString(" · ")).append("\n")
        append(TransferMath.summary(w)).append("\n")
        HtmlExporter.editedLine(w.editedAt).takeIf { it.isNotBlank() }?.let { append(it).append("\n") }
        block("Pre-transfer wishlist", w.wishlist)

        val incoming = w.incoming.sortedBy { it.status.ordinal }
        if (incoming.isNotEmpty()) {
            append("\nINCOMING\n")
            incoming.forEach { l ->
                append("• ").append(l.name.ifBlank { "Unnamed player" })
                if (l.position.isNotBlank()) append(" (").append(l.position.trim()).append(")")
                append(" — ").append(l.status.label)
                val fee = if (l.status.hasPrice) TransferMath.formatFee(l.fee) else ""
                if (fee.isNotBlank()) append(" — ").append(fee)
                append("\n")
                listOf("First linked" to l.firstLinked, "Sources" to l.sources, "Also linked with" to l.alsoLinked, "Note" to l.note)
                    .filter { it.second.isNotBlank() }
                    .forEach { (k, v) -> append("   ").append(k).append(": ").append(v.trim()).append("\n") }
            }
        }
        val outgoing = w.outgoing.sortedBy { it.status.ordinal }
        if (outgoing.isNotEmpty()) {
            append("\nEXITS\n")
            outgoing.forEach { l ->
                append("• ").append(l.name.ifBlank { "Unnamed player" })
                if (l.position.isNotBlank()) append(" (").append(l.position.trim()).append(")")
                append(" — ").append(l.status.label)
                val fee = if (l.status.hasPrice) TransferMath.formatFee(l.fee) else ""
                if (fee.isNotBlank()) append(" — ").append(fee)
                append("\n")
                listOf("Linked clubs" to l.linkedClubs, "Note" to l.note)
                    .filter { it.second.isNotBlank() }
                    .forEach { (k, v) -> append("   ").append(k).append(": ").append(v.trim()).append("\n") }
            }
        }
        val totals = TransferMath.displayTotals(w)
        append("\nSPEND\n")
        append("Incoming: ").append(totals[SpendKeys.INCOMING]).append("\n")
        append("Outgoing: ").append(totals[SpendKeys.OUTGOING]).append("\n")
        append("Net spend: ").append(totals[SpendKeys.NET]).append("\n")
        block("Window verdict", w.verdict)
    }.trim()
}
