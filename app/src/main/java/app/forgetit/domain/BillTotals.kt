package app.forgetit.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class BillOutgo(val monthlyMinor: Long, val excluded: Int)

/** Average monthly cost of all active bills that have at least one recorded amount, converted to [default]. */
fun billMonthlyOutgo(bills: List<Bill>, entries: List<BillEntry>, default: String, rates: Map<String, BigDecimal>): BillOutgo {
    var sum = BigDecimal.ZERO
    var excluded = 0
    for (b in bills) {
        if (!b.active) continue
        val m = BillMath.monthlyEquivalentMinor(b, entries.filter { it.billId == b.id }) ?: continue
        val c = convertMinor(BigDecimal(m), b.currency, default, rates)
        if (c == null) excluded++ else sum += c
    }
    return BillOutgo(Cost.round(sum), excluded)
}

fun billCalendarEntries(bills: List<Bill>, entries: List<BillEntry>, month: YearMonth, today: LocalDate): Map<LocalDate, List<CalendarEntry>> {
    val out = mutableListOf<CalendarEntry>()
    for (b in bills) {
        if (!b.active) continue
        val mine = entries.filter { it.billId == b.id }
        for (d in Renewal.between(BillMath.schedule(b), month.atDay(1), month.atEndOfMonth())) {
            val rec = mine.firstOrNull { it.dueDate == d }
            val usual = BillMath.average(mine)
            val detail = when {
                rec?.paidOn != null -> "Paid ${Money.format(rec.amountMinor, b.currency)}"
                rec != null -> "${Money.format(rec.amountMinor, b.currency)} to pay"
                usual != null -> "Usually ${Money.format(usual, b.currency)}"
                else -> "Due"
            }
            out += CalendarEntry(d, EntryType.BILL, b.name, detail)
        }
    }
    return out.groupBy { it.date }
}
