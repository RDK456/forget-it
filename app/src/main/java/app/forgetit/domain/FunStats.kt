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

data class ShoppingLine(
    val itemId: Long, val name: String, val unit: String, val leftMilli: Long, val buyMilli: Long, val reason: String,
    val store: String = "", val buyBy: LocalDate? = null,
)

/**
 * Items that are low, out, or have an expired batch. The suggested amount is a week of usage, else twice the warning level
 * (at least one unit), rounded up to whole packs. [ShoppingLine.buyBy] allows for the item delivery lead time.
 */
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
        val need = maxOf(week, i.lowThresholdMilli * 2, 1000L)
        val pack = i.packSizeMilli?.takeIf { it > 0 }
        val buy = if (pack != null) ((need + pack - 1) / pack) * pack else need
        val by = (st.runOut ?: today).minusDays(i.leadDays.toLong()).let { if (it.isBefore(today)) today else it }
        ShoppingLine(i.id, i.name, i.unit, st.estimatedMilli, buy, reason, i.store.trim(), by)
    }.sortedWith(compareBy({ it.store.lowercase() }, { it.name.lowercase() }))

fun shoppingText(lines: List<ShoppingLine>): String {
    val groups = lines.groupBy { it.store.ifBlank { "Anywhere" } }
    val body = groups.entries.joinToString("\n") { (store, list) ->
        (if (groups.size > 1) "$store:\n" else "") +
            list.joinToString("\n") { "- ${it.name}: about ${Money.milliToPlain(it.buyMilli)} ${it.unit} (${it.reason.lowercase()})" }
    }
    return "Shopping list from Forget-it\n$body"
}
