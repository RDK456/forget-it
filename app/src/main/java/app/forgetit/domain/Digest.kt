package app.forgetit.domain

import java.time.LocalDate

data class DigestSummary(val renewals: Int, val emis: Int, val bills: Int, val expiring: Int, val buy: List<String>) {
    private fun count(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

    fun text(): String {
        val parts = listOfNotNull(
            renewals.takeIf { it > 0 }?.let { count(it, "renewal", "renewals") },
            emis.takeIf { it > 0 }?.let { count(it, "EMI", "EMIs") },
            bills.takeIf { it > 0 }?.let { count(it, "bill", "bills") },
            expiring.takeIf { it > 0 }?.let { count(it, "item expiring", "items expiring") },
        )
        val first = if (parts.isEmpty()) "Nothing is due this week." else "This week: ${parts.joinToString(", ")}."
        val shop = if (buy.isEmpty()) "" else " Buy: " + buy.take(3).joinToString(", ") + (if (buy.size > 3) " and ${buy.size - 3} more" else "") + "."
        return first + shop
    }
}

/** Counts what falls due in the next seven days across all trackers, plus what to buy. */
fun buildDigest(
    subs: List<Subscription>, loans: List<Loan>, adj: List<LoanAdjustment>, pay: List<LoanPayment>,
    bills: List<Bill>, billEntries: List<BillEntry>,
    items: List<StockItem>, batches: List<StockBatch>, logs: List<StockLog>, today: LocalDate,
): DigestSummary {
    val end = today.plusDays(7)
    val renewals = subs.count { it.active && !Renewal.next(it, today).isAfter(end) }
    val emis = loans.count { l ->
        l.active && Amortization.build(l, adj.filter { it.loanId == l.id }, pay.filter { it.loanId == l.id }, today)
            .nextDue?.let { !it.dueDate.isAfter(end) } == true
    }
    val billCount = bills.count { b ->
        b.active && BillMath.pendingDue(b, billEntries.filter { it.billId == b.id }, today)?.let { !it.isAfter(end) } == true
    }
    val expiring = batches.count { b ->
        val item = items.firstOrNull { it.id == b.itemId }
        item?.active == true && b.quantityMilli > 0 && b.expiry?.let { !it.isAfter(end) } == true
    }
    return DigestSummary(renewals, emis, billCount, expiring, shoppingList(items, batches, logs, today).map { it.name })
}
