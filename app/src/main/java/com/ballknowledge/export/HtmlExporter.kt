package com.ballknowledge.export

import com.ballknowledge.logic.MatchClock
import com.ballknowledge.logic.TimeFormat
import com.ballknowledge.logic.TransferMath
import com.ballknowledge.model.Fields
import com.ballknowledge.model.InStatus
import com.ballknowledge.model.MatchPage
import com.ballknowledge.model.OutStatus
import com.ballknowledge.model.SpendKeys
import com.ballknowledge.model.TransferWindow

/** Builds self-contained, print-friendly HTML files from match pages or transfer windows. */
object HtmlExporter {

    // ---------- Match pages ----------

    fun build(pages: List<MatchPage>, docTitle: String): String {
        val ordered = pages.sortedBy { it.sortMillis }
        val body = StringBuilder()
        var lastSeason: Int? = null
        val multiple = ordered.size > 1
        ordered.forEachIndexed { i, p ->
            val season = TimeFormat.seasonStartYear(p.sortMillis)
            if (multiple && season != lastSeason) {
                body.append(seasonHeading(season, i > 0))
                lastSeason = season
                body.append(article(p, breakBefore = false))
            } else {
                body.append(article(p, breakBefore = i > 0))
            }
        }
        if (ordered.isEmpty()) body.append("<p class=\"empty\">No match pages yet.</p>")
        return document(docTitle, body.toString())
    }

    fun teamsLine(p: MatchPage): String {
        val home = p.field(Fields.HOME).ifBlank { "Home" }
        val away = p.field(Fields.AWAY).ifBlank { "Away" }
        val score = p.field(Fields.SCORE)
        return if (score.isNotBlank()) "$home $score $away" else "$home vs $away"
    }

    /** Competition · matchday · date · kickoff · venue · season — everything that identifies the match. */
    fun matchMeta(p: MatchPage): List<String> = listOf(
        p.field(Fields.COMPETITION),
        p.field(Fields.MATCHDAY),
        p.field(Fields.DATE),
        p.field(Fields.KICKOFF),
        p.field(Fields.VENUE),
        "Season " + TimeFormat.seasonLabel(TimeFormat.seasonStartYear(p.sortMillis)),
    ).filter { it.isNotBlank() }

    fun ratingsLine(p: MatchPage): String = listOfNotNull(
        p.field(Fields.RATING).takeIf { it.isNotBlank() }?.let { "Match rating: $it/10" },
        p.field(Fields.ATTENTION).takeIf { it.isNotBlank() }?.let { "Match attention: $it/10" },
    ).joinToString(" · ")

    /** The writing sections in page order (label, text). Lineups and diary are handled separately. */
    fun beforeLineups(p: MatchPage): List<Pair<String, String>> = listOf(
        "Pre-match context" to p.field(Fields.PRE_MATCH),
    )

    fun duringMatch(p: MatchPage): List<Pair<String, String>> = listOf(
        "Scorers" to p.field(Fields.SCORERS),
        "Match events" to p.field(Fields.EVENTS),
    )

    fun afterMatch(p: MatchPage): List<Pair<String, String>> {
        val home = p.field(Fields.HOME).ifBlank { "Home" }
        val away = p.field(Fields.AWAY).ifBlank { "Away" }
        return listOf(
            "Player highlight — $home" to p.field(Fields.HOME_HIGHLIGHT),
            "Player highlight — $away" to p.field(Fields.AWAY_HIGHLIGHT),
            "Man of the match" to p.field(Fields.MOTM),
            "Moment of the match" to p.field(Fields.MOMENT),
            "Post-match verdict" to p.field(Fields.VERDICT),
            "The narrative" to p.field(Fields.NARRATIVE),
            "What happened elsewhere" to p.field(Fields.ELSEWHERE),
            "Next opponent" to p.field(Fields.NEXT_OPPONENT),
        )
    }

    private fun article(p: MatchPage, breakBefore: Boolean): String {
        val sb = StringBuilder()
        sb.append("<article class=\"match").append(if (breakBefore) " newpage" else "").append("\">\n")
        sb.append("<div class=\"meta\">").append(matchMeta(p).joinToString(" · ") { esc(it) }).append("</div>\n")
        sb.append("<h2>").append(esc(teamsLine(p))).append("</h2>\n")
        p.field(Fields.TITLE).takeIf { it.isNotBlank() }?.let { sb.append("<p class=\"title\">").append(esc(it)).append("</p>\n") }
        ratingsLine(p).takeIf { it.isNotBlank() }?.let { sb.append("<p class=\"rating\">").append(esc(it)).append("</p>\n") }

        beforeLineups(p).forEach { (label, text) -> section(sb, label, text) }

        val home = p.field(Fields.HOME).ifBlank { "Home" }
        val away = p.field(Fields.AWAY).ifBlank { "Away" }
        val hasLineups = listOf(Fields.HOME_FORMATION, Fields.HOME_XI, Fields.HOME_SUBS, Fields.AWAY_FORMATION, Fields.AWAY_XI, Fields.AWAY_SUBS)
            .any { p.field(it).isNotBlank() }
        if (hasLineups) {
            sb.append("<section><h3>Lineups</h3><div class=\"lineups\">")
            sb.append(lineup(home, p.field(Fields.HOME_FORMATION), p.field(Fields.HOME_XI), p.field(Fields.HOME_SUBS)))
            sb.append(lineup(away, p.field(Fields.AWAY_FORMATION), p.field(Fields.AWAY_XI), p.field(Fields.AWAY_SUBS)))
            sb.append("</div></section>\n")
        }

        val diary = MatchClock.sorted(p.diary).filter { it.text.isNotBlank() || it.minute.isNotBlank() }
        val during = duringMatch(p).filter { it.second.isNotBlank() }
        if (diary.isNotEmpty() || during.isNotEmpty()) sb.append("<h4 class=\"part\">During the match</h4>\n")
        if (diary.isNotEmpty()) {
            sb.append("<section><h3>Match diary</h3><table class=\"diary\">")
            diary.forEach { d ->
                sb.append("<tr><td class=\"min\">").append(esc(MatchClock.label(d.minute))).append("</td><td class=\"text\">")
                    .append(esc(d.text)).append("</td></tr>")
            }
            sb.append("</table></section>\n")
        }
        during.forEach { (label, text) -> section(sb, label, text) }

        val after = afterMatch(p).filter { it.second.isNotBlank() }
        if (after.isNotEmpty()) sb.append("<h4 class=\"part\">After the match</h4>\n")
        after.forEach { (label, text) -> section(sb, label, text) }

        sb.append("</article>\n")
        return sb.toString()
    }

    private fun lineup(team: String, formation: String, xi: String, subs: String): String {
        val sb = StringBuilder("<div class=\"team\"><h4>").append(esc(team))
        if (formation.isNotBlank()) sb.append(" <span class=\"formation\">").append(esc(formation)).append("</span>")
        sb.append("</h4>")
        if (xi.isNotBlank()) sb.append("<div class=\"text xi\">").append(esc(xi.trim())).append("</div>")
        if (subs.isNotBlank()) sb.append("<div class=\"label\">Subs</div><div class=\"text subs\">").append(esc(subs.trim())).append("</div>")
        sb.append("</div>")
        return sb.toString()
    }

    // ---------- Transfer windows ----------

    fun buildTransfers(windows: List<TransferWindow>, docTitle: String): String {
        val ordered = windows.sortedWith(compareBy<TransferWindow> { it.seasonStart }.thenBy { it.kind.ordinal }.thenBy { it.team })
        val body = StringBuilder()
        var lastSeason: Int? = null
        val multiple = ordered.size > 1
        ordered.forEachIndexed { i, w ->
            if (multiple && w.seasonStart != lastSeason) {
                body.append(seasonHeading(w.seasonStart, i > 0))
                lastSeason = w.seasonStart
                body.append(windowArticle(w, breakBefore = false))
            } else {
                body.append(windowArticle(w, breakBefore = i > 0))
            }
        }
        if (ordered.isEmpty()) body.append("<p class=\"empty\">No transfer windows yet.</p>")
        return document(docTitle, body.toString())
    }

    fun windowMeta(w: TransferWindow): List<String> {
        val dates = listOf(TimeFormat.isoToDisplay(w.startDate), TimeFormat.isoToDisplay(w.endDate))
        val range = when {
            dates[0].isNotBlank() && dates[1].isNotBlank() -> "${dates[0]} – ${dates[1]}"
            dates[0].isNotBlank() -> "Opens ${dates[0]}"
            dates[1].isNotBlank() -> "Closes ${dates[1]}"
            else -> ""
        }
        val rate = if (w.rate.isNotBlank()) {
            "€1 = £${w.rate}" + if (!w.rateEdited && w.rateDate.isNotBlank()) " (ECB, ${TimeFormat.isoToDisplay(w.rateDate)})" else ""
        } else ""
        return listOf("Season " + TimeFormat.seasonLabel(w.seasonStart), range, rate).filter { it.isNotBlank() }
    }

    private fun windowArticle(w: TransferWindow, breakBefore: Boolean): String {
        val sb = StringBuilder()
        sb.append("<article class=\"match").append(if (breakBefore) " newpage" else "").append("\">\n")
        sb.append("<div class=\"meta\">").append(windowMeta(w).joinToString(" · ") { esc(it) }).append("</div>\n")
        sb.append("<h2>").append(esc(w.title)).append("</h2>\n")
        sb.append("<p class=\"rating\">").append(esc(TransferMath.summary(w))).append("</p>\n")
        section(sb, "Pre-transfer wishlist", w.wishlist)

        val incoming = w.incoming.sortedBy { it.status.ordinal }
        if (incoming.isNotEmpty()) {
            sb.append("<section><h3>Incoming</h3>")
            incoming.forEach { l ->
                val fee = if (l.status.hasPrice) TransferMath.formatFee(l.fee) else ""
                sb.append("<div class=\"player\"><div class=\"phead\"><b>").append(esc(l.name.ifBlank { "Unnamed player" })).append("</b>")
                if (l.position.isNotBlank()) sb.append(" <span class=\"muted\">").append(esc(l.position)).append("</span>")
                sb.append(" <span class=\"chip\">").append(esc(l.status.label)).append("</span>")
                if (fee.isNotBlank()) sb.append(" <span class=\"fee\">").append(esc(fee)).append("</span>")
                sb.append("</div>")
                detail(sb, "First linked", l.firstLinked)
                detail(sb, "Sources", l.sources)
                detail(sb, "Also linked with", l.alsoLinked)
                detail(sb, "Note", l.note)
                sb.append("</div>")
            }
            sb.append("</section>\n")
        }

        val outgoing = w.outgoing.sortedBy { it.status.ordinal }
        if (outgoing.isNotEmpty()) {
            sb.append("<section><h3>Exits</h3>")
            outgoing.forEach { l ->
                val fee = if (l.status.hasPrice) TransferMath.formatFee(l.fee) else ""
                sb.append("<div class=\"player\"><div class=\"phead\"><b>").append(esc(l.name.ifBlank { "Unnamed player" })).append("</b>")
                if (l.position.isNotBlank()) sb.append(" <span class=\"muted\">").append(esc(l.position)).append("</span>")
                sb.append(" <span class=\"chip\">").append(esc(l.status.label)).append("</span>")
                if (fee.isNotBlank()) sb.append(" <span class=\"fee\">").append(esc(fee)).append("</span>")
                sb.append("</div>")
                detail(sb, "Linked clubs", l.linkedClubs)
                detail(sb, "Note", l.note)
                sb.append("</div>")
            }
            sb.append("</section>\n")
        }

        val totals = TransferMath.displayTotals(w)
        sb.append("<section><h3>Spend</h3><table class=\"spend\">")
        listOf("Incoming" to SpendKeys.INCOMING, "Outgoing" to SpendKeys.OUTGOING, "Net spend" to SpendKeys.NET).forEach { (label, key) ->
            sb.append("<tr><td>").append(label).append("</td><td>").append(esc(totals[key].orEmpty()))
            if (w.spendOverrides.containsKey(key)) sb.append(" <span class=\"muted\">(edited)</span>")
            sb.append("</td></tr>")
        }
        sb.append("</table></section>\n")

        section(sb, "Window verdict", w.verdict)
        sb.append("</article>\n")
        return sb.toString()
    }

    private fun detail(sb: StringBuilder, label: String, text: String) {
        if (text.isBlank()) return
        sb.append("<div class=\"pdetail\"><span class=\"muted\">").append(esc(label)).append(":</span> ")
            .append(esc(text.trim())).append("</div>")
    }

    // ---------- Shared ----------

    private fun seasonHeading(season: Int, newPage: Boolean): String =
        "<h1 class=\"season" + (if (newPage) " newpage" else "") + "\">Season " + esc(TimeFormat.seasonLabel(season)) + "</h1>\n"

    private fun section(sb: StringBuilder, label: String, text: String) {
        if (text.isBlank()) return
        sb.append("<section><h3>").append(esc(label)).append("</h3><div class=\"text\">")
            .append(esc(text.trim())).append("</div></section>\n")
    }

    private fun document(docTitle: String, body: String): String = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(docTitle)}</title>
<style>$CSS</style></head>
<body><header class="doc"><div class="brand">Ball Knowledge</div><div class="sub">${esc(docTitle)}</div></header>
$body
</body></html>"""

    fun esc(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }

    private val CSS = """
:root{--ink:#1b1f1c;--muted:#5d6660;--line:#dfe5e0;--accent:#1f5130;--soft:#f3f6f3}
*{box-sizing:border-box}
body{font-family:-apple-system,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;color:var(--ink);background:#fff;margin:0 auto;max-width:780px;padding:24px 18px 48px;line-height:1.5;font-size:15px}
header.doc{border-bottom:3px solid var(--accent);padding-bottom:10px;margin-bottom:24px}
.brand{font-weight:800;font-size:24px;color:var(--accent);letter-spacing:-.01em}
.sub{color:var(--muted);font-size:13px}
h1.season{font-size:15px;text-transform:uppercase;letter-spacing:.08em;color:var(--accent);margin:32px 0 8px}
article.match{border:1px solid var(--line);border-radius:10px;padding:18px 18px 8px;margin:0 0 24px}
.meta{color:var(--muted);font-size:13px}
h2{font-size:22px;margin:4px 0 6px;line-height:1.25}
.title{font-size:17px;font-style:italic;margin:0 0 6px}
.rating{margin:0 0 8px;font-size:14px;color:var(--muted)}
section{margin:14px 0}
h3{font-size:12px;text-transform:uppercase;letter-spacing:.07em;color:var(--accent);margin:0 0 4px}
h4{font-size:15px;margin:0 0 4px}
h4.part{font-size:13px;text-transform:uppercase;letter-spacing:.09em;color:var(--ink);border-bottom:1px solid var(--line);padding-bottom:4px;margin:22px 0 8px}
.formation{font-weight:400;color:var(--muted);font-size:13px;margin-left:6px}
.text{white-space:pre-wrap;word-wrap:break-word}
.lineups{display:grid;grid-template-columns:1fr 1fr;gap:16px}
.team{background:var(--soft);border-radius:8px;padding:10px 12px}
.label{font-size:12px;color:var(--muted);margin-top:8px;text-transform:uppercase;letter-spacing:.06em}
.subs,.muted{color:var(--muted)}
table.diary,table.spend{border-collapse:collapse;width:100%}
table.diary td,table.spend td{border-top:1px solid var(--line);padding:6px 4px;vertical-align:top}
table.diary td.min{width:62px;font-weight:700;color:var(--accent);white-space:nowrap}
table.spend td:first-child{width:120px;color:var(--muted)}
.player{background:var(--soft);border-radius:8px;padding:8px 12px;margin:6px 0}
.phead{margin-bottom:2px}
.pdetail{font-size:14px;white-space:pre-wrap}
.chip{display:inline-block;font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.05em;background:#d5e8cf;color:#111f0f;border-radius:4px;padding:1px 6px;margin-left:4px}
.fee{font-weight:700;color:var(--accent);margin-left:4px}
.empty{color:var(--muted)}
@media (max-width:520px){.lineups{grid-template-columns:1fr}}
@media print{body{max-width:none;padding:0}article.match{border:none;padding:0}.newpage{break-before:page}}
"""
}
