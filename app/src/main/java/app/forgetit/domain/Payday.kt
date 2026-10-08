package app.forgetit.domain

import java.time.LocalDate
import java.time.YearMonth

object Payday {
    /** The next date on or after [from] that falls on [dayOfMonth] (1 to 31); short months use their last day. */
    fun next(from: LocalDate, dayOfMonth: Int): LocalDate {
        val day = dayOfMonth.coerceIn(1, 31)
        fun inMonth(m: YearMonth) = m.atDay(day.coerceAtMost(m.lengthOfMonth()))
        val thisMonth = inMonth(YearMonth.from(from))
        return if (!thisMonth.isBefore(from)) thisMonth else inMonth(YearMonth.from(from).plusMonths(1))
    }
}
