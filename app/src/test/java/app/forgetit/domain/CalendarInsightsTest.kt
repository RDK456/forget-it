package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

class CalendarInsightsTest {
    private val oct = YearMonth.of(2026, 10)

    @Test fun weeklyAppearsEveryWeekMonthlyOnce() {
        val subs = listOf(sub(Cycle.WEEKLY, start = "2026-10-01", id = 1), sub(Cycle.MONTHLY, start = "2026-01-15", id = 2))
        val m = ChargeCalendar.subscriptionEntries(subs, oct)
        assertEquals(5 + 1, m.values.sumOf { it.size })
        assertEquals(1, m.getValue(d("2026-10-15")).count { it.title == "Test" } - 1)
    }

    @Test fun inactiveAreSkippedTrialEndIsLabelled() {
        val m = ChargeCalendar.subscriptionEntries(
            listOf(sub(active = false), sub(start = "2026-10-01", trialEnds = "2026-10-20")), oct,
        )
        assertEquals(listOf(d("2026-10-20")), m.keys.toList())
        assertTrue(m.getValue(d("2026-10-20")).single().detail.startsWith("Free trial ends"))
    }

    @Test fun categoryBreakdownAndTop() {
        val subs = listOf(
            sub(amount = 1000, id = 1).copy(category = "Streaming"),
            sub(amount = 500, id = 2).copy(category = "Streaming"),
            sub(amount = 3000, id = 3).copy(category = "Software"),
        )
        val ins = computeInsights(subs, d("2026-10-08"), "USD", emptyMap())
        assertEquals(listOf(CategorySlice("Software", 3000), CategorySlice("Streaming", 1500)), ins.slices)
        assertEquals(4500L, ins.monthlyMinor)
        assertEquals(54000L, ins.yearlyMinor)
        assertEquals(148L, ins.perDayMinor)
        assertEquals(listOf(3L, 1L, 2L), ins.top.map { it.first.id })
        assertEquals(3, ins.activeCount)
    }

    @Test fun extraSlicesAddToTotalsAndMissingRatesAreCounted() {
        val ins = computeInsights(
            listOf(sub(amount = 1000, currency = "EUR")), d("2026-10-08"), "USD", emptyMap<String, BigDecimal>(),
            extraSlices = listOf(CategorySlice("Loans", 20000)),
        )
        assertEquals(1, ins.excluded)
        assertEquals(20000L, ins.monthlyMinor)
        assertEquals(listOf(CategorySlice("Loans", 20000)), ins.slices)
    }
}
