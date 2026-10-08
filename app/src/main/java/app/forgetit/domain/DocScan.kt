package app.forgetit.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.util.Locale

enum class DocKind(val label: String) {
    SUBSCRIPTION("Subscription"), EMI("EMI or loan"), BILL("Bill"), GROCERY("Groceries"), PAYMENT("Payment note"),
}

/** What a photo or screenshot most likely is, with the fields that could be read from it. Every field can be edited before it is saved. */
data class Detected(
    val kind: DocKind,
    val name: String,
    val amountMinor: Long? = null,
    val currency: String,
    val date: LocalDate? = null,
    val cycle: Cycle = Cycle.MONTHLY,
    val category: String = "Other",
    val billType: BillType = BillType.OTHER,
    val items: List<ScannedItem> = emptyList(),
    val tenureMonths: Int? = null,
    val ratePercent: BigDecimal? = null,
)

/** Decides which tracker a piece of recognised text belongs to. Pure text logic, so it is unit tested without a phone. */
object DocScan {
    private val IC = RegexOption.IGNORE_CASE
    private fun words(vararg w: String) = Regex("""\b(?:${w.joinToString("|")})\b""", IC)

    private val LOAN = words("emi", "e\\.m\\.i", "loan", "instalments?", "installments?", "principal", "tenure", "loan account", "loan a/c", "interest rate")
    private val BILL = words(
        "bill", "due date", "amount due", "total due", "minimum due", "payment due", "due on", "electricity", "kwh", "units consumed", "broadband",
        "postpaid", "recharge", "premium", "invoice", "statement", "dth", "rent", "school fees?", "tuition", "credit card", "outstanding",
    )
    private val SUB = words(
        "subscription", "renews?", "renewal", "renewed", "membership", "next billing", "billing period", "auto-?renew(?:s|ed|al)?", "your plan",
        "free trial", "cancel anytime", "billed monthly", "billed annually", "per month", "per year", "plan",
    )

    private fun hits(re: Regex, text: String) = re.findAll(text).map { it.value.lowercase(Locale.ROOT) }.toSet().size

    private val KEY_AMOUNT = Regex(
        """(amount due|total due|minimum due|amount payable|net payable|payable|emi amount|emi of|emi|instal+ment|total amount|bill amount|amount paid|amount|paid|total|charged)""",
        IC,
    )
    private val MARKER = "rs\\.?|inr|usd|eur|gbp|aed|[\$₹€£]"
    private val MONEY = Regex("""(?<![\d/:.-])(?:($MARKER)\s*)?(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+\.\d{1,2}|\d+)(?![\d/:-])""", IC)

    private fun currencyOf(marker: String, fallback: String): String = when (marker.lowercase(Locale.ROOT).trim('.')) {
        "rs", "inr", "₹" -> "INR"
        "usd", "$" -> "USD"
        "eur", "€" -> "EUR"
        "gbp", "£" -> "GBP"
        "aed" -> "AED"
        else -> fallback
    }

    private data class Found(val minor: Long, val currency: String, val marked: Boolean)

    private fun moneyIn(line: String, fallback: String, requireKey: Boolean): List<Found> = MONEY.findAll(line).mapNotNull { m ->
        val marker = m.groupValues[1]
        val raw = m.groupValues[2]
        val hasDecimals = '.' in raw
        if (marker.isEmpty() && !hasDecimals && !(requireKey && raw.replace(",", "").length >= 2)) return@mapNotNull null
        val cur = currencyOf(marker, fallback)
        val minor = Money.parseMinor(raw.replace(",", ""), cur)?.takeIf { it > 0 } ?: return@mapNotNull null
        Found(minor, cur, marker.isNotEmpty())
    }.toList()

    private fun findAmount(lines: List<String>, fallback: String): Found? {
        for ((i, line) in lines.withIndex()) {
            if (!KEY_AMOUNT.containsMatchIn(line)) continue
            val here = moneyIn(line, fallback, requireKey = true)
            if (here.isNotEmpty()) return here.first()
            lines.getOrNull(i + 1)?.let { moneyIn(it, fallback, requireKey = true).firstOrNull() }?.let { return it }
        }
        return lines.flatMap { moneyIn(it, fallback, requireKey = false) }.maxByOrNull { it.minor }
    }

    private val MONTHS = Month.entries.associateBy { it.name.take(3).lowercase(Locale.ROOT) }
    private val ISO = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val NUMERIC = Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b""")
    private val DAY_MONTH = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[ -]([A-Za-z]{3,9})\.?,?[ -](\d{2,4})\b""")
    private val MONTH_DAY = Regex("""\b([A-Za-z]{3,9})\.? (\d{1,2})(?:st|nd|rd|th)?,? (\d{4})\b""")

    private fun year(y: String) = y.toInt().let { if (it < 100) 2000 + it else it }

    internal fun datesIn(line: String): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        ISO.findAll(line).forEach { m -> runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()?.let(out::add) }
        NUMERIC.findAll(line).forEach { m ->
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            val (d, mo) = if (b > 12) b to a else a to b
            runCatching { LocalDate.of(year(m.groupValues[3]), mo, d) }.getOrNull()?.let(out::add)
        }
        DAY_MONTH.findAll(line).forEach { m ->
            val mo = MONTHS[m.groupValues[2].take(3).lowercase(Locale.ROOT)] ?: return@forEach
            runCatching { LocalDate.of(year(m.groupValues[3]), mo, m.groupValues[1].toInt()) }.getOrNull()?.let(out::add)
        }
        MONTH_DAY.findAll(line).forEach { m ->
            val mo = MONTHS[m.groupValues[1].take(3).lowercase(Locale.ROOT)] ?: return@forEach
            runCatching { LocalDate.of(m.groupValues[3].toInt(), mo, m.groupValues[2].toInt()) }.getOrNull()?.let(out::add)
        }
        return out
    }

    private val DUE_LINE = Regex("""\b(due|pay by|payable by|next billing|next payment|renews?|renewal|expires?|valid till|bill date)\b""", IC)

    private fun findDate(lines: List<String>): LocalDate? {
        for ((i, line) in lines.withIndex()) {
            if (!DUE_LINE.containsMatchIn(line)) continue
            (datesIn(line) + lines.getOrNull(i + 1)?.let(::datesIn).orEmpty()).firstOrNull()?.let { return it }
        }
        return lines.flatMap(::datesIn).firstOrNull()
    }

    private fun billType(text: String): BillType = when {
        Regex("""credit card|card statement|minimum due""", IC).containsMatchIn(text) -> BillType.CREDIT_CARD
        Regex("""electric|kwh|power""", IC).containsMatchIn(text) -> BillType.ELECTRICITY
        Regex("""\bwater\b""", IC).containsMatchIn(text) -> BillType.WATER
        Regex("""\b(gas|lpg|cylinder|png)\b""", IC).containsMatchIn(text) -> BillType.GAS
        Regex("""broadband|internet|fib(?:re|er)|wi-?fi""", IC).containsMatchIn(text) -> BillType.BROADBAND
        Regex("""postpaid|prepaid|recharge|mobile""", IC).containsMatchIn(text) -> BillType.MOBILE
        Regex("""\bdth\b|cable""", IC).containsMatchIn(text) -> BillType.DTH
        Regex("""insurance|premium|policy""", IC).containsMatchIn(text) -> BillType.INSURANCE
        Regex("""\brent\b""", IC).containsMatchIn(text) -> BillType.RENT
        Regex("""school|tuition""", IC).containsMatchIn(text) -> BillType.SCHOOL
        else -> BillType.OTHER
    }

    private fun cycleOf(text: String): Cycle = when {
        Regex("""annual|yearly|per year|/ ?yr|/ ?year""", IC).containsMatchIn(text) -> Cycle.YEARLY
        Regex("""quarter""", IC).containsMatchIn(text) -> Cycle.QUARTERLY
        Regex("""weekly|per week|/ ?week""", IC).containsMatchIn(text) -> Cycle.WEEKLY
        else -> Cycle.MONTHLY
    }

    private val HEADER_SKIP = Regex("""\b(invoice|statement|bill|receipt|date|total|amount|due|payment|tax|thank|page|account|number|customer)\b""", IC)

    private fun presetIn(text: String): Preset? {
        val squashed = text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        return PRESETS.filter { it.accountUrl != null }.firstOrNull { p ->
            listOf(p.key, p.name).map { n -> n.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() } }.any { it.length >= 4 && squashed.contains(it) }
        }
    }

    private fun titleCase(s: String) = s.trim().split(Regex("""\s+""")).joinToString(" ") { w ->
        if (w == w.uppercase(Locale.ROOT) || w == w.lowercase(Locale.ROOT)) w.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() } else w
    }

    private fun headerName(lines: List<String>, preferred: Regex? = null): String? {
        val candidates = lines.take(8).filter { l -> l.count { it.isLetter() } >= 3 && l.length <= 40 && !HEADER_SKIP.containsMatchIn(l) && !MONEY.containsMatchIn(l) }
        val pick = preferred?.let { p -> lines.firstOrNull { p.containsMatchIn(it) && it.length <= 50 } } ?: candidates.firstOrNull()
        return pick?.let { titleCase(it.replace(Regex("""[^A-Za-z0-9&' ./-]"""), " ").replace(Regex("""\s+"""), " ").trim()).take(40) }
    }

    /** Returns the most likely record, or null when nothing in the text looks like a payment or a purchase. */
    fun classify(text: String, labels: List<ScannedItem>, defaultCurrency: String, today: LocalDate): Detected? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val groceries = GroceryScan.fromReceipt(text, defaultCurrency)
        val preset = presetIn(text)
        val loan = hits(LOAN, text)
        val bill = hits(BILL, text)
        val sub = hits(SUB, text) + if (preset != null) 3 else 0
        val amount = findAmount(lines, defaultCurrency)
        val cur = amount?.currency ?: defaultCurrency

        if (groceries.size >= 3 && loan < 2 && bill < 3) return Detected(DocKind.GROCERY, "Groceries", amount?.minor, cur, items = groceries)
        return when {
            loan >= 2 && loan >= bill && loan >= sub -> {
                val tenure = Regex("""(\d{1,3})\s*(?:months|instal+ments|emis)""", IC).find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..600 }
                val rate = Regex("""(\d{1,2}(?:\.\d+)?)\s*%""").find(text)?.groupValues?.get(1)?.toBigDecimalOrNull()
                Detected(
                    DocKind.EMI, headerName(lines, Regex("""bank|finance|capital|housing|loans?\b""", IC)) ?: "Loan", amount?.minor, cur, findDate(lines),
                    tenureMonths = tenure, ratePercent = rate,
                )
            }
            sub >= 2 && sub >= bill -> Detected(
                DocKind.SUBSCRIPTION, preset?.name ?: headerName(lines) ?: "Subscription", amount?.minor, cur, findDate(lines), cycleOf(text),
                category = preset?.category ?: "Other",
            )
            bill >= 1 -> {
                val type = billType(text)
                Detected(DocKind.BILL, headerName(lines) ?: "Bill", amount?.minor, cur, findDate(lines), cycleOf(text), billType = type)
            }
            groceries.size >= 2 -> Detected(DocKind.GROCERY, "Groceries", amount?.minor, cur, items = groceries)
            amount != null -> Detected(DocKind.PAYMENT, headerName(lines) ?: "Payment", amount.minor, cur, findDate(lines) ?: today)
            labels.isNotEmpty() -> Detected(DocKind.GROCERY, "Groceries", null, cur, items = labels)
            else -> null
        }
    }
}
