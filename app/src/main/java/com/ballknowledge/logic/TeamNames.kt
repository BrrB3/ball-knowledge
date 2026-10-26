package com.ballknowledge.logic

import com.ballknowledge.model.TeamRef
import java.util.Locale

/** Loose team-name matching, so "Man Utd", "Manchester United" and "Man United FC" agree. */
object TeamNames {
    private val aliases = mapOf(
        "man utd" to "manchester united",
        "man united" to "manchester united",
        "man u" to "manchester united",
        "man city" to "manchester city",
        "spurs" to "tottenham hotspur",
        "tottenham" to "tottenham hotspur",
        "nottm forest" to "nottingham forest",
        "notts forest" to "nottingham forest",
        "forest" to "nottingham forest",
        "wolves" to "wolverhampton wanderers",
        "sheffield utd" to "sheffield united",
        "brighton" to "brighton and hove albion",
        "newcastle" to "newcastle united",
        "west ham" to "west ham united",
        "leeds" to "leeds united",
        "leicester" to "leicester city",
        "ipswich" to "ipswich town",
        "luton" to "luton town",
        "hull" to "hull city",
        "coventry" to "coventry city",
        "norwich" to "norwich city",
        "stoke" to "stoke city",
        "cardiff" to "cardiff city",
        "swansea" to "swansea city",
        "villa" to "aston villa",
        "palace" to "crystal palace",
        "west brom" to "west bromwich albion",
        "qpr" to "queens park rangers",
        "boro" to "middlesbrough",
    )

    fun normalise(name: String): String {
        var n = name.lowercase(Locale.ENGLISH)
            .replace("&", " and ")
            .replace(Regex("""[.'’]"""), "")
            .replace(Regex("""\b(a?fc)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        aliases[n]?.let { n = it }
        return n
    }

    fun matches(a: String, b: String): Boolean {
        val x = normalise(a)
        val y = normalise(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        // "arsenal" vs "arsenal women" should not match, but "brighton and hove albion" vs "brighton" should.
        return (x.startsWith("$y ") && !x.endsWith("women")) || (y.startsWith("$x ") && !y.endsWith("women"))
    }

    /** Which of [teams] a typed or fetched name refers to, if any. */
    fun find(name: String, teams: List<TeamRef>): TeamRef? =
        teams.firstOrNull { matches(name, it.name) || (it.abbr.isNotBlank() && name.trim().equals(it.abbr, ignoreCase = true)) }
}
