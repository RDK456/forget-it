package app.forgetit.domain

import java.time.LocalDate
import java.time.YearMonth

enum class EntryType { SUBSCRIPTION, EMI, EXPIRY, BILL }

data class CalendarEntry(val date: LocalDate, val type: EntryType, val title: String, val detail: String)

object ChargeCalendar {
    /** Every active subscription charge inside [month], grouped by day. */
    fun subscriptionEntries(subs: List<Subscription>, month: YearMonth): Map<LocalDate, List<CalendarEntry>> {
        val out = mutableListOf<CalendarEntry>()
        for (s in subs) {
            if (!s.active) continue
            for (d in Renewal.between(s, month.atDay(1), month.atEndOfMonth())) {
                val trialEnd = s.isTrial && s.trialEndsAt == d
                out += CalendarEntry(
                    d, EntryType.SUBSCRIPTION, s.name,
                    (if (trialEnd) "Free trial ends - " else "") + Money.format(s.amountMinor, s.currency),
                )
            }
        }
        return out.groupBy { it.date }
    }
}

/** Batch expiry dates falling inside [month], for active items and batches that still have stock. */
fun stockCalendarEntries(items: List<StockItem>, batches: List<StockBatch>, month: YearMonth): Map<LocalDate, List<CalendarEntry>> {
    val byId = items.filter { it.active }.associateBy { it.id }
    return batches.mapNotNull { b ->
        val item = byId[b.itemId] ?: return@mapNotNull null
        val e = b.expiry ?: return@mapNotNull null
        if (b.quantityMilli <= 0 || YearMonth.from(e) != month) return@mapNotNull null
        CalendarEntry(e, EntryType.EXPIRY, item.name, "Expires - ${Money.milliToPlain(b.quantityMilli)} ${item.unit}")
    }.groupBy { it.date }
}
