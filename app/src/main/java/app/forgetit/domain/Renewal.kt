package app.forgetit.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Charge-date arithmetic for [Subscription]. */
object Renewal {
    /** n-th charge date (n >= 0), always computed from the anchor so month-end clamping never drifts. */
    fun occurrence(sub: Subscription, n: Long): LocalDate {
        val a = sub.anchor
        return when (sub.cycle) {
            Cycle.WEEKLY -> a.plusDays(7 * n)
            Cycle.CUSTOM_DAYS -> a.plusDays(checkNotNull(sub.customDays).toLong() * n)
            Cycle.MONTHLY -> a.plusMonths(n)
            Cycle.QUARTERLY -> a.plusMonths(3 * n)
            Cycle.YEARLY -> a.plusYears(n)
        }
    }

    /** First charge date on or after [today]. */
    fun next(sub: Subscription, today: LocalDate): LocalDate {
        val a = sub.anchor
        if (!a.isBefore(today)) return a
        // Lower bound for n, then step up; occurrence() is strictly increasing in n.
        var n = when (sub.cycle) {
            Cycle.WEEKLY -> ChronoUnit.DAYS.between(a, today) / 7
            Cycle.CUSTOM_DAYS -> ChronoUnit.DAYS.between(a, today) / checkNotNull(sub.customDays)
            Cycle.MONTHLY -> ChronoUnit.MONTHS.between(a, today)
            Cycle.QUARTERLY -> ChronoUnit.MONTHS.between(a, today) / 3
            Cycle.YEARLY -> ChronoUnit.YEARS.between(a, today)
        }
        while (occurrence(sub, n).isBefore(today)) n++
        return occurrence(sub, n)
    }

    /** All charge dates in [from, to], inclusive. */
    fun between(sub: Subscription, from: LocalDate, to: LocalDate): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = next(sub, from)
        while (!d.isAfter(to)) {
            out += d
            d = next(sub, d.plusDays(1))
        }
        return out
    }
}
