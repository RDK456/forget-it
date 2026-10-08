package app.forgetit.domain

import java.math.BigDecimal
import java.time.LocalDate

object BillMath {
    const val HIGH_PERCENT = 25
    const val MIN_HISTORY = 3
    const val AVERAGE_OVER = 6

    /** Bills reuse the subscription date maths through a zero-amount schedule. */
    fun schedule(b: Bill) = Subscription(
        name = b.name, amountMinor = 0, currency = b.currency, cycle = b.cycle, customDays = b.customDays, startDate = b.anchorDate,
    )

    private fun windowDays(b: Bill): Long = when (b.cycle) {
        Cycle.WEEKLY -> 8
        Cycle.MONTHLY -> 32
        Cycle.QUARTERLY -> 93
        Cycle.YEARLY -> 367
        Cycle.CUSTOM_DAYS -> (b.customDays ?: 30) + 1L
    }

    /** The latest due date on or before today (if the bill has started), the first one on or after today, and the one after that. */
    fun recentAndNext(b: Bill, today: LocalDate): List<LocalDate> {
        val s = schedule(b)
        val w = windowDays(b)
        val dates = Renewal.between(s, today.minusDays(w), today.plusDays(2 * w + 2)).sorted()
        val past = dates.lastOrNull { !it.isAfter(today) }
        val next = dates.firstOrNull { !it.isBefore(today) }
        val after = next?.let { n -> dates.firstOrNull { it.isAfter(n) } }
        return listOfNotNull(past, next, after).distinct()
    }

    private fun isPaid(entries: List<BillEntry>, due: LocalDate) = entries.any { it.dueDate == due && it.paidOn != null }

    /** The due date still waiting for payment: the latest past one if unpaid, otherwise the next unpaid one. */
    fun pendingDue(b: Bill, entries: List<BillEntry>, today: LocalDate): LocalDate? {
        val near = recentAndNext(b, today)
        near.firstOrNull { !isPaid(entries, it) }?.let { return it }
        // Every near date is paid (bills paid well in advance): keep walking forward to the first unpaid cycle.
        val schedule = schedule(b)
        var date = near.lastOrNull() ?: return null
        repeat(60) {
            date = Renewal.next(schedule, date.plusDays(1))
            if (!isPaid(entries, date)) return date
        }
        return null
    }

    fun average(entries: List<BillEntry>, n: Int = AVERAGE_OVER): Long? {
        val recent = entries.sortedByDescending { it.dueDate }.take(n)
        if (recent.isEmpty()) return null
        return Cost.round(BigDecimal(recent.sumOf { it.amountMinor }).divide(BigDecimal(recent.size), Cost.MC))
    }

    fun lastEntry(entries: List<BillEntry>): BillEntry? = entries.maxByOrNull { it.dueDate }

    /** How much higher (positive) or lower a new amount is than the average of the earlier bills; null with too little history. */
    fun changePercent(newAmount: Long, earlier: List<BillEntry>): Int? {
        if (earlier.size < MIN_HISTORY) return null
        val avg = average(earlier) ?: return null
        if (avg <= 0) return null
        return Math.round((newAmount - avg) * 100.0 / avg).toInt()
    }

    fun isHigh(newAmount: Long, earlier: List<BillEntry>): Boolean = (changePercent(newAmount, earlier) ?: 0) >= HIGH_PERCENT

    /** Average amount per month, from the recent bills and the cycle length; null until a bill is recorded. */
    fun monthlyEquivalentMinor(b: Bill, entries: List<BillEntry>): Long? {
        val avg = average(entries) ?: return null
        val perMonth = when (b.cycle) {
            Cycle.WEEKLY -> BigDecimal(52).divide(BigDecimal(12), Cost.MC)
            Cycle.MONTHLY -> BigDecimal.ONE
            Cycle.QUARTERLY -> BigDecimal.ONE.divide(BigDecimal(3), Cost.MC)
            Cycle.YEARLY -> BigDecimal.ONE.divide(BigDecimal(12), Cost.MC)
            Cycle.CUSTOM_DAYS -> BigDecimal(365).divide(BigDecimal((b.customDays ?: 30) * 12), Cost.MC)
        }
        return Cost.round(BigDecimal(avg).multiply(perMonth))
    }
}
