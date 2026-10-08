package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AmortizationTest {
    private val today = d("2026-01-01")

    private fun loan(rate: String = "12", n: Int = 12, p: Long = 100_000, override: Long? = null, first: String = "2026-02-01") = Loan(
        id = 1, name = "L", principalMinor = p, currency = "USD", annualRatePercent = BigDecimal(rate),
        tenureMonths = n, firstEmiDate = d(first), emiOverrideMinor = override,
    )

    private fun adj(date: String, kind: AdjustmentKind, amount: Long) = LoanAdjustment(loanId = 1, date = d(date), kind = kind, amountMinor = amount)

    private fun build(l: Loan, a: List<LoanAdjustment> = emptyList(), paid: List<Int> = emptyList(), now: String = "2026-01-01") =
        Amortization.build(l, a, paid.map { LoanPayment(loanId = 1, installmentNo = it, paidOn = d(now), amountMinor = 0) }, d(now))

    @Test fun zeroRateSplitsPrincipalEvenly() {
        val s = build(loan(rate = "0", p = 120_000))
        assertEquals(12, s.rows.size)
        assertTrue(s.rows.all { it.paymentMinor == 10_000L && it.interestMinor == 0L })
        assertEquals(0L, s.rows.last().balanceAfterMinor)
        assertEquals(0L, s.interestRemainingMinor)
    }

    @Test fun standardLoanMatchesHandCalculation() {
        val s = build(loan())
        assertEquals(8885L, s.emiMinor)
        assertEquals(12, s.rows.size)
        assertEquals(1000L, s.rows[0].interestMinor)
        assertEquals(7885L, s.rows[0].principalMinor)
        assertEquals(92_115L, s.rows[0].balanceAfterMinor)
        assertEquals(0L, s.rows.last().balanceAfterMinor)
        assertEquals(100_000L, s.rows.sumOf { it.principalMinor })
        assertEquals(d("2027-01-01"), s.payoffDate)
    }

    @Test fun overrideAboveComputedEmiEndsEarlier() {
        val s = build(loan(override = 10_000))
        assertTrue(s.rows.size < 12)
        assertTrue(s.rows.last().paymentMinor <= 10_000L)
        assertEquals(0L, s.rows.last().balanceAfterMinor)
    }

    @Test fun overrideBelowComputedEmiEndsLater() {
        val s = build(loan(override = 8000))
        assertTrue(s.rows.size > 12)
        assertEquals(0L, s.rows.last().balanceAfterMinor)
    }

    @Test fun prepaymentReduceTenureKeepsEmiAndShortensLoan() {
        val s = build(loan(), listOf(adj("2026-02-15", AdjustmentKind.PREPAYMENT_REDUCE_TENURE, 50_000)))
        assertTrue(s.rows.size < 12)
        assertEquals(8885L, s.rows[1].paymentMinor)
        assertEquals(0L, s.rows.last().balanceAfterMinor)
    }

    @Test fun prepaymentReduceEmiKeepsTenure() {
        val s = build(loan(), listOf(adj("2026-02-15", AdjustmentKind.PREPAYMENT_REDUCE_EMI, 50_000)))
        assertEquals(12, s.rows.size)
        assertTrue(s.rows[1].paymentMinor in 4050L..4075L)
        assertEquals(0L, s.rows.last().balanceAfterMinor)
    }

    @Test fun balanceResetKeepsEmiAndReinterestsFromNewBalance() {
        val s = build(loan(), listOf(adj("2026-02-15", AdjustmentKind.BALANCE_RESET, 30_000)))
        assertEquals(300L, s.rows[1].interestMinor)
        assertEquals(21_415L, s.rows[1].balanceAfterMinor)
        assertEquals(8885L, s.rows[1].paymentMinor)
    }

    @Test fun statusesOutstandingAndNextDue() {
        val s = build(loan(), paid = listOf(1, 2), now = "2026-04-10")
        assertEquals(listOf(RowStatus.PAID, RowStatus.PAID, RowStatus.OVERDUE, RowStatus.UPCOMING), s.rows.take(4).map { it.status })
        assertEquals(s.rows[1].balanceAfterMinor, s.outstandingMinor)
        assertEquals(3, s.nextDue!!.no)
        assertEquals(RowStatus.UPCOMING, s.rows[4].status)
        assertEquals(s.rows.drop(2).sumOf { it.interestMinor }, s.interestRemainingMinor)
    }

    @Test fun dueTodayAndCompletion() {
        assertEquals(RowStatus.DUE, build(loan(), now = "2026-02-01").rows[0].status)
        val done = build(loan(), paid = (1..12).toList(), now = "2027-02-01")
        assertTrue(done.completed)
        assertNull(done.nextDue)
        assertEquals(0L, done.outstandingMinor)
    }

    @Test fun dueDatesClampToMonthEnd() {
        val s = build(loan(first = "2026-01-31"))
        assertEquals(listOf(d("2026-01-31"), d("2026-02-28"), d("2026-03-31")), s.rows.take(3).map { it.dueDate })
    }

    @Test fun rejectsAnEmiThatNeverRepays() {
        assertEquals(listOf("emi"), Amortization.validate(loan(override = 500)).map { it.field })
        assertTrue(Amortization.validate(loan()).isEmpty())
        assertFalse(Amortization.validate(loan(n = 0)).isEmpty())
        assertFalse(Amortization.validate(loan(rate = "150")).isEmpty())
        val slow = build(loan(override = 1001, p = 100_000, rate = "12"))
        assertTrue(slow.rows.size in 600..Amortization.MAX_ROWS)
        assertEquals(0L, slow.rows.last().balanceAfterMinor)
    }
}
