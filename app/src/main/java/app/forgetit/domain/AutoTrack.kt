package app.forgetit.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A loan worked out from payment messages. Everything except the amount is an estimate and is marked as such on the loan. */
data class LoanGuess(
    val name: String,
    val emiMinor: Long,
    val currency: String,
    val firstDate: LocalDate,
    val paidDates: List<LocalDate>,
    val tenureMonths: Int?,
    val ratePercent: BigDecimal?,
    val principalMinor: Long?,
)

/**
 * Decides what can be tracked on its own from payments the phone has seen: subscriptions that repeat, and loans that show up as
 * the same EMI every month or in an EMI message that states the amount and the tenure. Pure logic, no Android, so it is unit tested.
 */
object AutoTrack {
    /** Notes that mark an item the app guessed. They stay until the user keeps the item, so the list can ask for a review. */
    const val SUB_GUESS_NOTE = "Added automatically from your payments."
    const val LOAN_GUESS_NOTE = "Created from your payment messages."
    fun isUnreviewed(notes: String) = notes.startsWith(SUB_GUESS_NOTE) || notes.startsWith(LOAN_GUESS_NOTE)
    fun reviewed(notes: String) = notes.replaceFirst(SUB_GUESS_NOTE, "Reviewed and kept.").replaceFirst(LOAN_GUESS_NOTE, "Reviewed and kept.")

    private val LENDER = listOf("finance", "finserv", "fincorp", "capital", "loan", "emi", "lending", "mortgage", "nbfc", "leasing")

    /** A loan instalment: filed under EMI and loans, or paid to a lender-sounding name that nothing else has claimed. */
    fun isEmiTxn(t: Txn, rules: Map<String, String> = emptyMap()): Boolean {
        val cat = t.categoryOr(rules)
        if (cat == "EMI and loans") return true
        val key = Categorizer.ruleKey(t.merchant)
        return cat == "Other" && LENDER.any { key.contains(it) }
    }

    fun subKey(name: String) = "sub:" + Categorizer.ruleKey(name)
    fun loanKey(name: String) = "loan:" + Categorizer.ruleKey(name)

    /** Repeating charges worth adding as subscriptions: not already tracked, not turned down before, and not loan instalments. */
    fun subscriptionGuesses(
        txns: List<Txn>, trackedNames: Collection<String>, dismissed: Set<String>, rules: Map<String, String> = emptyMap(), loans: List<Loan> = emptyList(),
    ): List<RecurringSuggestion> {
        val emiMerchants = txns.filter { isEmiTxn(it, rules) }.mapNotNull { it.merchant?.let(Categorizer::ruleKey) }.toSet()
        return TxnMatching.recurring(txns, trackedNames).filter { g ->
            val sameAsLoan = loans.any { l -> l.currency == g.currency && abs((l.emiOverrideMinor ?: Amortization.baseEmi(l)) - g.amountMinor) <= maxOf(1L, g.amountMinor / 100) }
            subKey(g.merchant) !in dismissed && Categorizer.ruleKey(g.merchant) !in emiMerchants && !sameAsLoan
        }
    }

    private fun isTracked(name: String, emiMinor: Long, currency: String, loans: List<Loan>): Boolean {
        val key = Categorizer.ruleKey(name)
        return loans.any { l ->
            val names = listOf(Categorizer.ruleKey(l.name), Categorizer.ruleKey(l.lender)).filter { it.isNotEmpty() }
            val sameName = key.isNotEmpty() && names.any { it.contains(key) || key.contains(it) }
            val emi = l.emiOverrideMinor ?: Amortization.baseEmi(l)
            val sameAmount = l.currency == currency && abs(emi - emiMinor) <= maxOf(1L, emiMinor / 100)
            sameName || sameAmount
        }
    }

    /** The same loan-type debit about a month apart, at least [minCount] times, for the same amount. */
    fun loanGuessesFromHistory(
        txns: List<Txn>, loans: List<Loan>, dismissed: Set<String>, rules: Map<String, String> = emptyMap(), minCount: Int = 2,
    ): List<LoanGuess> {
        val emi = txns.filter { it.direction == TxnDirection.DEBIT && it.status != "IGNORED" && isEmiTxn(it, rules) }
        val groups = emi.groupBy { (it.merchant?.let(Categorizer::ruleKey)?.takeIf { k -> k.isNotEmpty() } ?: "emi") to it.currency }
        val out = mutableListOf<LoanGuess>()
        for ((_, g) in groups) {
            // Split a merchant's debits by amount, so two different loans at one lender stay separate.
            var rest = g.sortedBy { it.date }
            while (rest.isNotEmpty()) {
                val anchor = rest.first()
                val same = rest.filter { abs(it.amountMinor - anchor.amountMinor) <= maxOf(1L, anchor.amountMinor / 100) }
                rest = rest - same.toSet()
                if (same.size < minCount) continue
                val gaps = same.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }
                if (gaps.any { it !in 26..35 }) continue
                val name = anchor.merchant?.trim()?.ifBlank { null } ?: "Loan"
                if (loanKey(name) in dismissed || isTracked(name, anchor.amountMinor, anchor.currency, loans)) continue
                out += LoanGuess(name, same.last().amountMinor, anchor.currency, same.first().date, same.map { it.date }, null, null, null)
            }
        }
        return out
    }

    /** One EMI message that says who, how much and for how many months is enough to create the loan. */
    fun loanGuessFromMessage(text: String, defaultCurrency: String, today: LocalDate, loans: List<Loan>, dismissed: Set<String>): LoanGuess? {
        val d = DocScan.classify(text, emptyList(), defaultCurrency, today) ?: return null
        val emi = d.amountMinor ?: return null
        val tenure = d.tenureMonths ?: return null
        if (d.kind != DocKind.EMI) return null
        val name = if (d.name == "Loan") DocDates.lenderIn(text) ?: d.name else d.name
        if (loanKey(name) in dismissed || isTracked(name, emi, d.currency, loans)) return null
        return LoanGuess(name, emi, d.currency, d.date ?: today.plusMonths(1), emptyList(), tenure, d.ratePercent, d.principalMinor)
    }

    /** What was borrowed, worked back from the EMI, the number of months and the rate. */
    fun estimatePrincipal(emiMinor: Long, months: Int, ratePercent: BigDecimal?): Long {
        val r = (ratePercent?.toDouble() ?: 0.0) / 1200
        if (r == 0.0) return emiMinor * months
        // Rounded down so the last instalment is never a leftover sliver.
        return Math.floor(emiMinor * (1 - Math.pow(1 + r, -months.toDouble())) / r).toLong()
    }

    /** The loan to save for a guess. Paid instalments seen in the history are marked paid by the caller. */
    fun toLoan(g: LoanGuess): Loan {
        val months = g.tenureMonths ?: maxOf(12, g.paidDates.size + 6)
        return Loan(
            name = g.name.take(60), lender = g.name.take(60), principalMinor = g.principalMinor ?: estimatePrincipal(g.emiMinor, months, g.ratePercent),
            currency = g.currency, annualRatePercent = g.ratePercent ?: BigDecimal.ZERO, tenureMonths = months, firstEmiDate = g.firstDate,
            emiOverrideMinor = g.emiMinor, active = false, notes = LOAN_GUESS_NOTE + " " + guessNote(g),
        )
    }

    /** Says plainly which numbers were read from the messages and which are placeholders. */
    private fun guessNote(g: LoanGuess): String {
        val known = buildList { add("the EMI amount"); if (g.tenureMonths != null) add("the months"); if (g.ratePercent != null) add("the rate"); if (g.principalMinor != null) add("the loan amount") }
        val missing = buildList { if (g.tenureMonths == null) add("months"); if (g.ratePercent == null) add("rate"); if (g.principalMinor == null) add("loan amount") }
        return "Read from your messages: " + known.joinToString(", ") + "." +
            (if (missing.isEmpty()) "" else " Placeholders, please correct: " + missing.joinToString(", ") + ".")
    }
}
