package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.ZonedDateTime

class LoanReminderTest {
    private fun at(s: String) = ZonedDateTime.parse(s + "[Asia/Kolkata]")
    private val loan = Loan(
        id = 7, name = "Car", principalMinor = 100_000, currency = "USD", annualRatePercent = BigDecimal("12"),
        tenureMonths = 12, firstEmiDate = d("2026-10-20"), remindDaysBefore = 2,
    )
    private fun pay(no: Int) = LoanPayment(loanId = 7, installmentNo = no, paidOn = d("2026-10-01"), amountMinor = 0)
    private fun plan(now: String, payments: List<LoanPayment> = emptyList(), notified: Set<String> = emptySet(), l: Loan = loan) =
        ReminderPlanner.loans(listOf(l), emptyList(), payments, at(now), 540, notified)

    @Test fun remindsBeforeFirstInstallment() {
        val spec = plan("2026-10-08T12:00:00+05:30").single()
        assertEquals(ReminderKind.EMI_DUE, spec.kind)
        assertEquals(d("2026-10-20"), spec.eventDate)
        assertEquals(at("2026-10-18T09:00:00+05:30"), spec.triggerAt)
    }

    @Test fun markingPaidMovesReminderToTheNextInstallment() {
        val spec = plan("2026-10-21T12:00:00+05:30", payments = listOf(pay(1))).single()
        assertEquals(d("2026-11-20"), spec.eventDate)
    }

    @Test fun overdueInstallmentRemindsOnceThenMovesOn() {
        val now = "2026-10-25T12:00:00+05:30"
        val first = plan(now).single()
        assertEquals(d("2026-10-20"), first.eventDate)
        assertTrue(first.title.startsWith("EMI overdue"))
        val second = plan(now, notified = setOf(first.key)).single()
        assertEquals(d("2026-11-20"), second.eventDate)
    }

    @Test fun inactiveOrFinishedLoansGetNothing() {
        assertTrue(plan("2026-10-08T12:00:00+05:30", l = loan.copy(active = false)).isEmpty())
        assertTrue(plan("2028-01-01T12:00:00+05:30", payments = (1..12).map(::pay)).isEmpty())
    }
}
