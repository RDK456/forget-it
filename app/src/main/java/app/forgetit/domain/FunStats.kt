package app.forgetit.domain

import java.time.LocalDate

/** How many installments in a row were paid on or before their due date, counting back from the latest paid one. */
fun onTimeStreak(rows: List<ScheduleRow>, payments: List<LoanPayment>): Int {
    val byNo = payments.associateBy { it.installmentNo }
    var n = 0
    for (r in rows.filter { it.status == RowStatus.PAID }.sortedByDescending { it.no }) {
        val p = byNo[r.no] ?: break
        if (p.paidOn.isAfter(r.dueDate)) break
        n++
    }
    return n
}

data class ShoppingLine(val itemId: Long, val name: String, val unit: String, val leftMilli: Long, val buyMilli: Long, val reason: String)

/** Items that are low, out, or have an expired batch, with a suggested amount to buy (a week of usage, else twice the warning level). */
fun shoppingList(items: List<StockItem>, batches: List<StockBatch>, logs: List<StockLog>, today: LocalDate): List<ShoppingLine> =
    items.filter { it.active }.mapNotNull { i ->
        val mine = batches.filter { it.itemId == i.id }
        val myLogs = logs.filter { it.itemId == i.id }
        if (mine.isEmpty() && myLogs.isEmpty()) return@mapNotNull null
        val st = StockEngine.status(i, mine, myLogs, today)
        val reason = when {
            st.estimatedMilli == 0L -> "Out"
            st.low -> "Running low"
            st.expiredBatches.isNotEmpty() -> "Has expired stock"
            else -> return@mapNotNull null
        }
        val week = st.ratePerDayMilli?.multiply(java.math.BigDecimal(7))?.toLong() ?: 0L
        val buy = maxOf(week, i.lowThresholdMilli * 2, 1000L)
        ShoppingLine(i.id, i.name, i.unit, st.estimatedMilli, buy, reason)
    }.sortedBy { it.name.lowercase() }

fun shoppingText(lines: List<ShoppingLine>): String =
    "Shopping list from Forget-it\n" + lines.joinToString("\n") { "- ${it.name}: about ${Money.milliToPlain(it.buyMilli)} ${it.unit} (${it.reason.lowercase()})" }
