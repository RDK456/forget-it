package app.forgetit.domain

import java.time.LocalDate
import java.util.Locale

/** A subscription worked out from one confirmation or receipt message: what was paid, when, and when it renews or ends. */
data class SubGuess(
    val name: String,
    val amountMinor: Long,
    val currency: String,
    val cycle: Cycle,
    val category: String,
    val paidOn: LocalDate,
    val startDate: LocalDate,
    val nextDate: LocalDate,
    val paymentMethod: String,
    val cancelUrl: String?,
    val presetKey: String?,
    val notes: String,
)

/** Finds the paid date and the renewal or end date in receipt text. */
object DocDates {
    private val IC = RegexOption.IGNORE_CASE
    private val PAID = Regex("""\b(paid on|date paid|payment date|invoice date|receipt date|date of issue|charged on|billed on|purchased on|order date|date of purchase|paid)\b""", IC)
    private val END = Regex(
        """\b(renews?(?: on| at)?|renewal date|auto-?renews?|next (?:billing|payment|charge)(?: date)?|valid (?:until|till|through)|expires?(?: on)?|expiry(?: date)?|ends?(?: on)?|until|active through|good through)\b""",
        IC,
    )
    private val PERIOD = Regex("""\b(billing|service|subscription|coverage) period\b""", IC)

    private fun datesNear(lines: List<String>, i: Int): List<LocalDate> =
        DocScan.datesIn(lines[i]).ifEmpty { lines.getOrNull(i + 1)?.let(DocScan::datesIn).orEmpty() }

    private fun lines(text: String) = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun paidOn(text: String): LocalDate? {
        val l = lines(text)
        for (i in l.indices) {
            if (END.containsMatchIn(l[i]) || PERIOD.containsMatchIn(l[i])) continue
            if (PAID.containsMatchIn(l[i])) datesNear(l, i).firstOrNull()?.let { return it }
        }
        for (i in l.indices) if (PERIOD.containsMatchIn(l[i])) DocScan.datesIn(l[i]).firstOrNull()?.let { return it }
        return null
    }

    fun endOn(text: String): LocalDate? {
        val l = lines(text)
        for (i in l.indices) {
            if (PERIOD.containsMatchIn(l[i])) DocScan.datesIn(l[i]).takeIf { it.size >= 2 }?.let { return it.last() }
        }
        for (i in l.indices) if (END.containsMatchIn(l[i])) datesNear(l, i).lastOrNull()?.let { return it }
        return null
    }

    /** "HDFC Bank", "Bajaj Finance"... when an EMI text has no header line to take the lender from. */
    fun lenderIn(text: String): String? =
        Regex("""\b([A-Z][A-Za-z&]*(?: [A-Z][A-Za-z&]*){0,2} (?:Bank|Finance|Capital|Housing Finance|Financial Services|Fincorp))\b""").find(text)?.groupValues?.get(1)
}

object AutoSub {
    private val IC = RegexOption.IGNORE_CASE
    private val PROMO = Regex("""\b(offers?|discounts?|save \d|\d+% off|win|claim|rewards?|cashback|limited time|click here|apply now|pre-?approved|coupon|expires soon)\b""", IC)
    private val CONFIRM = Regex(
        """\b(receipts?|invoice|payment (?:received|successful|confirmation|confirmed)|thank you for (?:your )?(?:payment|purchase|order|subscribing)|you(?:'|’)?re subscribed|you are subscribed|subscription (?:confirmation|confirmed|is active|has started|started|renewed|receipt)|order confirmation|has been renewed|was renewed|billed|charged|payment of|you(?:'|’)ve paid|you paid)\b""",
        IC,
    )
    private val METHOD = Regex("""(?:card|visa|mastercard|amex|rupay|upi|a/c|account)[^0-9\n]{0,24}(\d{4})\b""", IC)

    private fun forward(d: LocalDate, c: Cycle) = when (c) {
        Cycle.WEEKLY -> d.plusWeeks(1)
        Cycle.QUARTERLY -> d.plusMonths(3)
        Cycle.YEARLY -> d.plusYears(1)
        else -> d.plusMonths(1)
    }

    private fun back(d: LocalDate, c: Cycle) = when (c) {
        Cycle.WEEKLY -> d.minusWeeks(1)
        Cycle.QUARTERLY -> d.minusMonths(3)
        Cycle.YEARLY -> d.minusYears(1)
        else -> d.minusMonths(1)
    }

    private fun money(minor: Long, currency: String) = Money.format(minor, currency)

    /** Returns null unless the text reads like a confirmation for a paid subscription that is not already tracked. */
    fun fromMessage(text: String, defaultCurrency: String, receivedOn: LocalDate, tracked: Collection<String>, dismissed: Set<String>): SubGuess? {
        if (PROMO.containsMatchIn(text) || !CONFIRM.containsMatchIn(text)) return null
        val d = DocScan.classify(text, emptyList(), defaultCurrency, receivedOn) ?: return null
        if (d.kind != DocKind.SUBSCRIPTION) return null
        val amount = d.amountMinor?.takeIf { it > 0 } ?: return null
        val key = Categorizer.ruleKey(d.name)
        if (key.isEmpty() || AutoTrack.subKey(d.name) in dismissed) return null
        if (tracked.any { val t = Categorizer.ruleKey(it); t.isNotEmpty() && (t.contains(key) || key.contains(t)) }) return null

        val paid = DocDates.paidOn(text) ?: receivedOn
        val end = DocDates.endOn(text)?.takeIf { it.isAfter(paid) }
        val next = end ?: forward(paid, d.cycle)
        val anchor = if (end != null) back(end, d.cycle) else paid
        // A plan paid today or later renews one cycle on; otherwise the subscription list would show it due today.
        val start = if (!anchor.isBefore(receivedOn)) forward(anchor, d.cycle) else anchor
        val method = METHOD.find(text)?.let { "Card ending " + it.groupValues[1] }.orEmpty()
        val preset = PRESETS.firstOrNull { it.name.equals(d.name, ignoreCase = true) }
        val cycleWord = d.cycle.name.lowercase(Locale.ROOT)
        val notes = "Paid ${money(amount, d.currency)} on $paid, $cycleWord plan. " +
            (if (end != null) "Runs until or renews on $end. " else "Next payment around $next. ") +
            (if (method.isNotEmpty()) "Paid with $method. " else "") + "Added automatically from a confirmation message."
        return SubGuess(d.name, amount, d.currency, d.cycle, preset?.category ?: d.category, paid, start, next, method, preset?.accountUrl, preset?.key, notes)
    }
}
