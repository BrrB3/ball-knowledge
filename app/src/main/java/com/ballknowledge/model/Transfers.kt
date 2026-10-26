package com.ballknowledge.model

/** Transfer windows are a separate part of the app from match pages. */

enum class WindowKind(val label: String) { SUMMER("Summer"), WINTER("Winter") }

enum class Currency(val symbol: String) { GBP("£"), EUR("€") }

/** Status of a player the club is linked with buying. */
enum class InStatus(val label: String, val short: String, val hasPrice: Boolean) {
    SIGNED("Signed", "SGN", true),
    LOAN("Loan", "LOAN", true),
    NEGOTIATING("Negotiating", "NEG", false),
    LINKED("Linked", "LNK", false),
    ELSEWHERE("Went elsewhere", "ELS", true),
    STAYS("Se queda", "SQ", false),
}

/** Status of a player who might leave the club. */
enum class OutStatus(val label: String, val short: String, val hasPrice: Boolean) {
    SOLD("Sold", "SOLD", true),
    LOANED_OUT("Loaned out", "LOAN", true),
    RELEASED("Released", "REL", false),
    NEGOTIATING("Negotiating", "NEG", false),
    LINKED("Linked", "LNK", false),
    STAYS("Stays", "STAY", false),
}

/** A fee as typed, in millions: main fee plus add-ons, in one currency. */
data class Fee(
    val main: String = "",
    val addons: String = "",
    val currency: Currency = Currency.GBP,
)

data class IncomingLink(
    val id: String,
    val name: String = "",
    val position: String = "",
    val firstLinked: String = "",
    val sources: String = "",
    val alsoLinked: String = "",
    val status: InStatus = InStatus.LINKED,
    val fee: Fee = Fee(),
    val note: String = "",
    val createdAt: Long = 0L,
)

data class OutgoingLink(
    val id: String,
    val name: String = "",
    val position: String = "",
    val linkedClubs: String = "",
    val status: OutStatus = OutStatus.LINKED,
    val fee: Fee = Fee(),
    val note: String = "",
    val createdAt: Long = 0L,
)

object SpendKeys {
    const val INCOMING = "incoming"
    const val OUTGOING = "outgoing"
    const val NET = "net"
}

data class TransferWindow(
    val id: String,
    val team: String,
    val kind: WindowKind,
    /** Summer 2026 -> 2026; Winter (January) 2027 -> 2027. */
    val year: Int,
    /** ISO dates, e.g. "2026-06-16"; blank if not set. */
    val startDate: String = "",
    val endDate: String = "",
    /** Pounds per euro, as typed or fetched (e.g. "0.8573"). */
    val rate: String = "",
    /** True once the user has typed a rate; then it's never re-fetched. */
    val rateEdited: Boolean = false,
    /** The ECB date the fetched rate is from. */
    val rateDate: String = "",
    val wishlist: String = "",
    val verdict: String = "",
    val incoming: List<IncomingLink> = emptyList(),
    val outgoing: List<OutgoingLink> = emptyList(),
    /** Manually typed totals, keyed by SpendKeys; absent means automatic. */
    val spendOverrides: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** Season the window belongs to: Summer 2026 and Winter 2027 are both 2026/27. */
    val seasonStart: Int get() = if (kind == WindowKind.SUMMER) year else year - 1

    val title: String get() = "$team · ${kind.label} $year"
}
