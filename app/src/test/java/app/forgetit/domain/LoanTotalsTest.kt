package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

class LoanTotalsTest {
    private fun loan(id: Long, currency: String = "USD", active: Boolean = true) = Loan(
        id = id, name = "L$id", principalMinor = 100_000, currency = currency, annualRatePercent = BigDecimal("12"),
        tenureMonths = 12, firstEmiDate = d("2026-10-20"), active = active,
    )
    private val today = d("2026-10-08")

    @Test fun outgoSumsCurrentEmiAndSkipsInactiveOrMissingRate() {
        val out = loanMonthlyOutgo(listOf(loan(1), loan(2, active = false), loan(3, "EUR")), emptyList(), emptyList(), today, "USD", emptyMap())
        assertEquals(LoanOutgo(8885, 1), out)
        val withRate = loanMonthlyOutgo(listOf(loan(3, "EUR")), emptyList(), emptyList(), today, "USD", mapOf("EUR" to BigDecimal("2")))
        assertEquals(17770L, withRate.monthlyMinor)
    }

    @Test fun finishedLoanCostsNothing() {
        val pays = (1..12).map { LoanPayment(loanId = 1, installmentNo = it, paidOn = today, amountMinor = 0) }
        assertEquals(LoanOutgo(0, 0), loanMonthlyOutgo(listOf(loan(1)), emptyList(), pays, today, "USD", emptyMap()))
    }

    @Test fun calendarListsInstallmentsInTheMonth() {
        val m = loanCalendarEntries(listOf(loan(1)), emptyList(), emptyList(), YearMonth.of(2026, 11), today)
        val e = m.getValue(d("2026-11-20")).single()
        assertEquals(EntryType.EMI, e.type)
        assertTrue(e.detail.startsWith("EMI 2/12"))
        assertTrue(loanCalendarEntries(listOf(loan(1)), emptyList(), emptyList(), YearMonth.of(2025, 1), today).isEmpty())
    }
}
