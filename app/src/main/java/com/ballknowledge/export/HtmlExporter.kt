package com.ballknowledge.export

import com.ballknowledge.logic.MatchClock
import com.ballknowledge.logic.TimeFormat
import com.ballknowledge.model.Fields
import com.ballknowledge.model.MatchPage

/** Builds one self-contained, print-friendly HTML file from match pages. */
object HtmlExporter {

    fun build(pages: List<MatchPage>, docTitle: String): String {
        val ordered = pages.sortedBy { it.sortMillis }
        val body = StringBuilder()
        var lastSeason: Int? = null
        val multiple = ordered.size > 1
        ordered.forEachIndexed { i, p ->
            val season = TimeFormat.seasonStartYear(p.sortMillis)
            if (multiple && season != lastSeason) {
                body.append("<h1 class=\"season").append(if (i > 0) " newpage" else "").append("\">")
                    .append("Season ").append(esc(TimeFormat.seasonLabel(season))).append("</h1>\n")
                lastSeason = season
                body.append(article(p, breakBefore = false))
            } else {
                body.append(article(p, breakBefore = i > 0))
            }
        }
        if (ordered.isEmpty()) body.append("<p class=\"empty\">No match pages yet.</p>")
        return """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(docTitle)}</title>
<style>$CSS</style></head>
<body><header class="doc"><div class="brand">Ball Knowledge</div><div class="sub">${esc(docTitle)}</div></header>
$body
</body></html>"""
    }

    fun teamsLine(p: MatchPage): String {
        val home = p.field(Fields.HOME).ifBlank { "Home" }
        val away = p.field(Fields.AWAY).ifBlank { "Away" }
        val score = p.field(Fields.SCORE)
        return if (score.isNotBlank()) "$home $score $away" else "$home vs $away"
    }

    private fun article(p: MatchPage, breakBefore: Boolean): String {
        val sb = StringBuilder()
        sb.append("<article class=\"match").append(if (breakBefore) " newpage" else "").append("\">\n")

        val meta = listOf(p.field(Fields.COMPETITION), p.field(Fields.DATETIME), p.field(Fields.VENUE))
            .filter { it.isNotBlank() }
        if (meta.isNotEmpty()) sb.append("<div class=\"meta\">").append(meta.joinToString(" · ") { esc(it) }).append("</div>\n")
        sb.append("<h2>").append(esc(teamsLine(p))).append("</h2>\n")
        p.field(Fields.TITLE).takeIf { it.isNotBlank() }?.let { sb.append("<p class=\"title\">").append(esc(it)).append("</p>\n") }
        p.field(Fields.RATING).takeIf { it.isNotBlank() }?.let {
            sb.append("<p class=\"rating\">Match rating: <b>").append(esc(it)).append("/10</b></p>\n")
        }

        section(sb, "Pre-match context", p.field(Fields.PRE_MATCH))

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
        if (diary.isNotEmpty()) {
            sb.append("<section><h3>Match diary</h3><table class=\"diary\">")
            diary.forEach { d ->
                sb.append("<tr><td class=\"min\">").append(esc(MatchClock.label(d.minute))).append("</td><td class=\"text\">")
                    .append(esc(d.text)).append("</td></tr>")
            }
            sb.append("</table></section>\n")
        }

        section(sb, "Scorers", p.field(Fields.SCORERS))
        section(sb, "Match events", p.field(Fields.EVENTS))
        section(sb, "Player highlight — $home", p.field(Fields.HOME_HIGHLIGHT))
        section(sb, "Player highlight — $away", p.field(Fields.AWAY_HIGHLIGHT))
        section(sb, "Man of the match", p.field(Fields.MOTM))
        section(sb, "Moment of the match", p.field(Fields.MOMENT))
        section(sb, "Post-match verdict", p.field(Fields.VERDICT))
        section(sb, "The narrative", p.field(Fields.NARRATIVE))
        section(sb, "What happened elsewhere", p.field(Fields.ELSEWHERE))
        section(sb, "Next opponent", p.field(Fields.NEXT_OPPONENT))

        sb.append("</article>\n")
        return sb.toString()
    }

    private fun section(sb: StringBuilder, label: String, text: String) {
        if (text.isBlank()) return
        sb.append("<section><h3>").append(esc(label)).append("</h3><div class=\"text\">")
            .append(esc(text.trim())).append("</div></section>\n")
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
.rating b{color:var(--accent)}
section{margin:14px 0}
h3{font-size:12px;text-transform:uppercase;letter-spacing:.07em;color:var(--accent);margin:0 0 4px}
h4{font-size:15px;margin:0 0 4px}
.formation{font-weight:400;color:var(--muted);font-size:13px;margin-left:6px}
.text{white-space:pre-wrap;word-wrap:break-word}
.lineups{display:grid;grid-template-columns:1fr 1fr;gap:16px}
.team{background:var(--soft);border-radius:8px;padding:10px 12px}
.label{font-size:12px;color:var(--muted);margin-top:8px;text-transform:uppercase;letter-spacing:.06em}
.subs{color:var(--muted)}
table.diary{border-collapse:collapse;width:100%}
table.diary td{border-top:1px solid var(--line);padding:6px 4px;vertical-align:top}
table.diary td.min{width:62px;font-weight:700;color:var(--accent);white-space:nowrap}
.empty{color:var(--muted)}
@media (max-width:520px){.lineups{grid-template-columns:1fr}}
@media print{body{max-width:none;padding:0}article.match{border:none;padding:0}.newpage{break-before:page}}
"""
}
