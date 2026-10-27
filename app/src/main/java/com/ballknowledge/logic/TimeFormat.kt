package com.ballknowledge.logic

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    val UK: ZoneId = ZoneId.of("Europe/London")

    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val dayYearFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val syncFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

    private fun ist(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(IST)
    private fun uk(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(UK)
    private fun time(z: ZonedDateTime): String = timeFmt.format(z).lowercase(Locale.ENGLISH)

    /** Pieces of a kickoff line, so the UI can draw the star as a superscript. */
    data class Kickoff(val date: String, val ist: String, val uk: String, val star: Boolean)

    fun kickoff(millis: Long, venueCountry: String?, withYear: Boolean = false): Kickoff {
        val i = ist(millis)
        return Kickoff(
            date = (if (withYear) dayYearFmt else dayFmt).format(i),
            ist = time(i),
            uk = time(uk(millis)),
            star = VenueZones.differsFromUk(millis, venueCountry),
        )
    }

    /** "Sat 31 Oct 2026 · 8:30 pm IST (3:00 pm UK*)" — plain text for the editable field. */
    fun kickoffLine(millis: Long, venueCountry: String?): String {
        val k = kickoff(millis, venueCountry, withYear = true)
        return "${k.date} · ${k.ist} IST (${k.uk} UK${if (k.star) "*" else ""})"
    }

    /** "Sat 31 Oct 2026" (IST date). */
    fun dateLine(millis: Long): String = dayYearFmt.format(ist(millis))

    /** "8:30 pm IST (3:00 pm UK*)" — plain text for the editable Kickoff field. */
    fun kickoffTimeLine(millis: Long, venueCountry: String?): String {
        val k = kickoff(millis, venueCountry)
        return "${k.ist} IST (${k.uk} UK${if (k.star) "*" else ""})"
    }

    private val editedFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", Locale.ENGLISH)

    /** "Tue 27 Oct 2026, 10:56 pm" (IST). */
    fun editedLine(millis: Long): String = editedFmt.format(ist(millis)).replace(" AM", " am").replace(" PM", " pm")

    /** "today", "yesterday", "12 days ago". */
    fun daysAgo(millis: Long, now: Long): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(ist(millis).toLocalDate(), ist(now).toLocalDate())
        return when {
            days <= 0L -> "today"
            days == 1L -> "yesterday"
            else -> "$days days ago"
        }
    }

    private val isoDayFmt =DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** "2026-06-16" → "16 Jun 2026"; blank or invalid → "". */
    fun isoToDisplay(iso: String): String =
        runCatching { isoDayFmt.format(java.time.LocalDate.parse(iso)) }.getOrDefault("")

    fun shortDate(millis: Long): String = dayFmt.format(ist(millis))

    fun monthHeader(millis: Long): String = monthFmt.format(ist(millis))

    fun syncTime(millis: Long): String = syncFmt.format(ist(millis)).lowercase(Locale.ENGLISH)
        .replaceFirstChar { it.uppercase() }

    /** Seasons run July to June. Returns the starting year (2026 for 2026/27). */
    fun seasonStartYear(millis: Long): Int {
        val d = uk(millis).toLocalDate()
        return if (d.monthValue >= 7) d.year else d.year - 1
    }

    fun seasonLabel(startYear: Int): String = "%d/%02d".format(startYear, (startYear + 1) % 100)

    fun seasonMonths(startYear: Int): List<YearMonth> =
        (0L until 12L).map { YearMonth.of(startYear, 7).plusMonths(it) }

    fun yearMonthUk(millis: Long): YearMonth = YearMonth.from(uk(millis))

    fun dayOfMonthUk(millis: Long): Int = uk(millis).dayOfMonth

    /** Parses ESPN dates such as "2026-10-31T15:00Z" (no seconds) or full ISO instants. */
    fun parseInstant(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        runCatching { return Instant.parse(t).toEpochMilli() }
        runCatching { return java.time.OffsetDateTime.parse(t).toInstant().toEpochMilli() }
        val withSeconds = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})(Z|[+-]\d{2}:?\d{2})$""").find(t)
        if (withSeconds != null) {
            val (dt, zone) = withSeconds.destructured
            val z = if (zone == "Z") "Z" else if (zone.contains(":")) zone else zone.substring(0, 3) + ":" + zone.substring(3)
            runCatching { return java.time.OffsetDateTime.parse("$dt:00$z").toInstant().toEpochMilli() }
        }
        return null
    }
}

/** Maps a venue's country to its time zone, to decide when to show the star. */
object VenueZones {
    private val byCountry: Map<String, String> = mapOf(
        "england" to "Europe/London", "scotland" to "Europe/London", "wales" to "Europe/London",
        "northern ireland" to "Europe/London", "united kingdom" to "Europe/London", "uk" to "Europe/London",
        "ireland" to "Europe/Dublin", "republic of ireland" to "Europe/Dublin",
        "portugal" to "Europe/Lisbon", "spain" to "Europe/Madrid", "france" to "Europe/Paris",
        "germany" to "Europe/Berlin", "italy" to "Europe/Rome", "netherlands" to "Europe/Amsterdam",
        "holland" to "Europe/Amsterdam", "belgium" to "Europe/Brussels", "switzerland" to "Europe/Zurich",
        "austria" to "Europe/Vienna", "denmark" to "Europe/Copenhagen", "norway" to "Europe/Oslo",
        "sweden" to "Europe/Stockholm", "finland" to "Europe/Helsinki", "poland" to "Europe/Warsaw",
        "czechia" to "Europe/Prague", "czech republic" to "Europe/Prague", "slovakia" to "Europe/Bratislava",
        "hungary" to "Europe/Budapest", "croatia" to "Europe/Zagreb", "serbia" to "Europe/Belgrade",
        "slovenia" to "Europe/Ljubljana", "bosnia and herzegovina" to "Europe/Sarajevo",
        "bosnia-herzegovina" to "Europe/Sarajevo", "montenegro" to "Europe/Podgorica",
        "north macedonia" to "Europe/Skopje", "macedonia" to "Europe/Skopje", "albania" to "Europe/Tirane",
        "kosovo" to "Europe/Belgrade", "greece" to "Europe/Athens", "cyprus" to "Asia/Nicosia",
        "turkey" to "Europe/Istanbul", "türkiye" to "Europe/Istanbul", "turkiye" to "Europe/Istanbul",
        "bulgaria" to "Europe/Sofia", "romania" to "Europe/Bucharest", "moldova" to "Europe/Chisinau",
        "ukraine" to "Europe/Kiev", "belarus" to "Europe/Minsk", "russia" to "Europe/Moscow",
        "lithuania" to "Europe/Vilnius", "latvia" to "Europe/Riga", "estonia" to "Europe/Tallinn",
        "israel" to "Asia/Jerusalem", "azerbaijan" to "Asia/Baku", "georgia" to "Asia/Tbilisi",
        "armenia" to "Asia/Yerevan", "kazakhstan" to "Asia/Almaty", "iceland" to "Atlantic/Reykjavik",
        "faroe islands" to "Atlantic/Faroe", "luxembourg" to "Europe/Luxembourg", "malta" to "Europe/Malta",
        "gibraltar" to "Europe/Gibraltar", "andorra" to "Europe/Andorra", "san marino" to "Europe/Rome",
        "liechtenstein" to "Europe/Zurich", "monaco" to "Europe/Monaco",
        "usa" to "America/New_York", "united states" to "America/New_York",
        "saudi arabia" to "Asia/Riyadh", "qatar" to "Asia/Qatar", "united arab emirates" to "Asia/Dubai",
    )

    fun zoneFor(country: String?): ZoneId? {
        val key = country?.trim()?.lowercase(Locale.ENGLISH)?.removePrefix("the ") ?: return null
        val id = byCountry[key] ?: return null
        return runCatching { ZoneId.of(id) }.getOrNull()
    }

    fun differsFromUk(millis: Long, country: String?): Boolean {
        val zone = zoneFor(country) ?: return false
        val instant = Instant.ofEpochMilli(millis)
        return zone.rules.getOffset(instant).totalSeconds != TimeFormat.UK.rules.getOffset(instant).totalSeconds
    }
}
