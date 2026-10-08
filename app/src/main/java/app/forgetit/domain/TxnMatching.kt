package app.forgetit.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class Txn(
    val id: Long = 0,
    val direction: TxnDirection,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val accountHint: String? = null,
    val date: LocalDate,
    val source: String = "SMS",
    val status: String = "NEW",
    val snippet: String = "",
    val sender: String = "",
)

data class RecurringSuggestion(val merchant: String, val amountMinor: Long, val currency: String, val cycle: Cycle, val lastDate: LocalDate, val count: Int)

data class EmiMatch(val txnId: Long, val loanId: Long, val loanName: String, val installmentNo: Int)

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

object TxnMatching {
    private val CYCLES = listOf(Cycle.WEEKLY to 7L, Cycle.MONTHLY to 30L, Cycle.QUARTERLY to 91L, Cycle.YEARLY to 365L)

    /** Merchants charged at a steady interval with a steady amount, that are not already tracked. */
    fun recurring(txns: List<Txn>, trackedNames: Collection<String>): List<RecurringSuggestion> {
        val tracked = trackedNames.map(::norm).filter { it.isNotEmpty() }
        return txns.filter { it.direction == TxnDirection.DEBIT && it.merchant != null && it.status != "IGNORED" }
            .groupBy { norm(it.merchant!!) to it.currency }
            .mapNotNull { (key, group) ->
                if (key.first.isEmpty() || tracked.any { it.contains(key.first) || key.first.contains(it) }) return@mapNotNull null
                val sorted = group.sortedBy { it.date }
                if (sorted.size < 2) return@mapNotNull null
                val gaps = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }
                val cycle = CYCLES.firstOrNull { (_, len) ->
                    val tol = if (len <= 7) 1L else 4L
                    gaps.all { abs(it - len) <= tol }
                }?.first ?: return@mapNotNull null
                val amounts = sorted.map { it.amountMinor }
                val mid = amounts.sorted()[amounts.size / 2]
                if (amounts.any { abs(it - mid) > mid / 10 }) return@mapNotNull null
                RecurringSuggestion(sorted.last().merchant!!, sorted.last().amountMinor, key.second, cycle, sorted.last().date, sorted.size)
            }.sortedByDescending { it.lastDate }
    }

    /** A debit close to a loan EMI amount and due date is probably that installment being paid. */
    fun emiMatches(
        txns: List<Txn>, loans: List<Loan>, adjustments: List<LoanAdjustment>, payments: List<LoanPayment>, today: LocalDate,
    ): List<EmiMatch> {
        val out = mutableListOf<EmiMatch>()
        for (l in loans.filter { it.active }) {
            val row = Amortization.build(l, adjustments.filter { it.loanId == l.id }, payments.filter { it.loanId == l.id }, today).nextDue ?: continue
            val tolerance = maxOf(1L, row.paymentMinor / 100)
            val hit = txns.filter { it.direction == TxnDirection.DEBIT && it.currency == l.currency && it.status != "IGNORED" }
                .filter { abs(it.amountMinor - row.paymentMinor) <= tolerance && abs(ChronoUnit.DAYS.between(it.date, row.dueDate)) <= 5 }
                .maxByOrNull { it.date } ?: continue
            out += EmiMatch(hit.id, l.id, l.name, row.no)
        }
        return out
    }
}
