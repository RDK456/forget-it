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
    fun subKey(name: String) = "sub:" + Categorizer.ruleKey(name)
    fun loanKey(name: String) = "loan:" + Categorizer.ruleKey(name)

    /** Repeating charges worth adding as subscriptions: not already tracked, not turned down before, and not loan instalments. */
    fun subscriptionGuesses(
        txns: List<Txn>, trackedNames: Collection<String>, dismissed: Set<String>, rules: Map<String, String> = emptyMap(),
    ): List<RecurringSuggestion> {
        val emiMerchants = txns.filter { it.categoryOr(rules) == "EMI and loans" }.mapNotNull { it.merchant?.let(Categorizer::ruleKey) }.toSet()
        return TxnMatching.recurring(txns, trackedNames).filter {
            subKey(it.merchant) !in dismissed && Categorizer.ruleKey(it.merchant) !in emiMerchants
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
        val emi = txns.filter { it.direction == TxnDirection.DEBIT && it.status != "IGNORED" && it.categoryOr(rules) == "EMI and loans" }
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
            emiOverrideMinor = g.emiMinor, notes = "Created from your payment messages. The loan amount, rate and length are estimates, so check the terms.",
        )
    }
}
