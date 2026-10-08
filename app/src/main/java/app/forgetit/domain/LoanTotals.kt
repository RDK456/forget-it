package app.forgetit.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class LoanOutgo(val monthlyMinor: Long, val excluded: Int)

private fun summaryOf(l: Loan, adj: List<LoanAdjustment>, pay: List<LoanPayment>, today: LocalDate) =
    Amortization.build(l, adj.filter { it.loanId == l.id }, pay.filter { it.loanId == l.id }, today)

/** Current EMI of every active unfinished loan, converted to [default]. Loans without an exchange rate are excluded. */
fun loanMonthlyOutgo(
    loans: List<Loan>, adj: List<LoanAdjustment>, pay: List<LoanPayment>,
    today: LocalDate, default: String, rates: Map<String, BigDecimal>,
): LoanOutgo {
    var sum = BigDecimal.ZERO
    var excluded = 0
    for (l in loans) {
        if (!l.active) continue
        val emi = summaryOf(l, adj, pay, today).nextDue?.paymentMinor ?: continue
        val m = convertMinor(BigDecimal(emi), l.currency, default, rates)
        if (m == null) excluded++ else sum += m
    }
    return LoanOutgo(Cost.round(sum), excluded)
}

fun loanCalendarEntries(
    loans: List<Loan>, adj: List<LoanAdjustment>, pay: List<LoanPayment>, month: YearMonth, today: LocalDate,
): Map<LocalDate, List<CalendarEntry>> {
    val out = mutableListOf<CalendarEntry>()
    for (l in loans) {
        if (!l.active) continue
        val rows = summaryOf(l, adj, pay, today).rows
        for (r in rows) {
            if (YearMonth.from(r.dueDate) != month) continue
            val tail = if (r.status == RowStatus.PAID) " (paid)" else ""
            out += CalendarEntry(r.dueDate, EntryType.EMI, l.name, "EMI ${r.no}/${rows.size} - ${Money.format(r.paymentMinor, l.currency)}$tail")
        }
    }
    return out.groupBy { it.date }
}
