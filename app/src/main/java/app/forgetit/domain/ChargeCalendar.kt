package app.forgetit.domain

import java.time.LocalDate
import java.time.YearMonth

enum class EntryType { SUBSCRIPTION, EMI, EXPIRY }

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
