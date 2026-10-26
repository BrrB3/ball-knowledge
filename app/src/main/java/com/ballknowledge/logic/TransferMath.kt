package com.ballknowledge.logic

import com.ballknowledge.model.Currency
import com.ballknowledge.model.Fee
import com.ballknowledge.model.InStatus
import com.ballknowledge.model.OutStatus
import com.ballknowledge.model.SpendKeys
import com.ballknowledge.model.TransferWindow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Spend totals: main fees and add-ons are added up separately, both in pounds (millions). */
object TransferMath {

    data class Amount(val main: Double = 0.0, val addons: Double = 0.0) {
        operator fun plus(o: Amount) = Amount(main + o.main, addons + o.addons)
        operator fun minus(o: Amount) = Amount(main - o.main, addons - o.addons)
    }

    data class Totals(
        val incoming: Amount,
        val outgoing: Amount,
        val net: Amount,
        /** True if some € fee couldn't be converted because there's no exchange rate yet. */
        val missingRate: Boolean,
    )

    /** Reads what the user typed: "45", "45.5", "£45m", "€60,5" → millions. */
    fun parseMillions(text: String): Double? {
        val cleaned = text.trim().replace(",", ".").replace(Regex("""[^0-9.\-]"""), "")
        if (cleaned.isEmpty() || cleaned == "." || cleaned == "-") return null
        return cleaned.toDoubleOrNull()
    }

    fun parseRate(text: String): Double? = parseMillions(text)?.takeIf { it > 0 }

    /** A fee in pounds, or null (with [missingRate] set) if it's in euros and there's no rate. */
    private fun toPounds(fee: Fee, rate: Double?): Pair<Amount, Boolean> {
        val main = parseMillions(fee.main) ?: 0.0
        val addons = parseMillions(fee.addons) ?: 0.0
        if (main == 0.0 && addons == 0.0) return Amount() to false
        return when (fee.currency) {
            Currency.GBP -> Amount(main, addons) to false
            Currency.EUR -> if (rate == null) Amount() to true else Amount(main * rate, addons * rate) to false
        }
    }

    fun totals(w: TransferWindow): Totals {
        val rate = parseRate(w.rate)
        var missing = false
        var incoming = Amount()
        w.incoming.filter { it.status == InStatus.SIGNED || it.status == InStatus.LOAN }.forEach {
            val (a, m) = toPounds(it.fee, rate); incoming += a; missing = missing || m
        }
        var outgoing = Amount()
        w.outgoing.filter { it.status == OutStatus.SOLD || it.status == OutStatus.LOANED_OUT }.forEach {
            val (a, m) = toPounds(it.fee, rate); outgoing += a; missing = missing || m
        }
        return Totals(incoming, outgoing, incoming - outgoing, missing)
    }

    /** Totals as shown: a typed override wins over the automatic value. */
    fun displayTotals(w: TransferWindow): Map<String, String> {
        val t = totals(w)
        val auto = mapOf(
            SpendKeys.INCOMING to format(t.incoming),
            SpendKeys.OUTGOING to format(t.outgoing),
            SpendKeys.NET to format(t.net),
        )
        return auto.mapValues { (k, v) -> w.spendOverrides[k] ?: v }
    }

    /** "2 signed · 1 loan · 3 negotiating · 7 linked | Out: 1 sold · 2 linked". */
    fun summary(w: TransferWindow): String {
        val inParts = InStatus.values().mapNotNull { st ->
            val n = w.incoming.count { it.status == st }
            if (n == 0) null else "$n ${st.label.lowercase(Locale.ENGLISH)}"
        }
        val outParts = OutStatus.values().mapNotNull { st ->
            val n = w.outgoing.count { it.status == st }
            if (n == 0) null else "$n ${st.label.lowercase(Locale.ENGLISH)}"
        }
        val a = if (inParts.isEmpty()) "No incoming links yet" else inParts.joinToString(" · ")
        return if (outParts.isEmpty()) a else "$a  |  Out: ${outParts.joinToString(" · ")}"
    }

    /** "£200m + £20m", "£45.5m", "−£12m + £3m", "£0m". */
    fun format(a: Amount): String {
        val main = money(a.main)
        if (round1(a.addons) == 0.0) return main
        val sign = if (a.addons < 0) "−" else "+"
        return "$main $sign ${money(abs(a.addons))}"
    }

    /** Formats one amount typed by the user, keeping its currency: "€60m + €10m". */
    fun formatFee(fee: Fee): String {
        val main = parseMillions(fee.main)
        val addons = parseMillions(fee.addons)
        if (main == null && addons == null) return ""
        val s = fee.currency.symbol
        val m = "$s${num(main ?: 0.0)}m"
        return if (addons == null || addons == 0.0) m else "$m + $s${num(addons)}m"
    }

    private fun money(v: Double): String = (if (round1(v) < 0) "−" else "") + "£" + num(abs(v)) + "m"

    private fun round1(v: Double): Double = (v * 10).roundToLong() / 10.0

    private fun num(v: Double): String {
        val r = round1(v)
        return if (r == r.toLong().toDouble()) r.toLong().toString() else String.format(Locale.ENGLISH, "%.1f", r)
    }
}
