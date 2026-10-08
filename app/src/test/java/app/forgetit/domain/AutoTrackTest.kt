package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AutoTrackTest {
    private var next = 1L
    private val today = d("2026-10-08")
    private fun debit(merchant: String?, minor: Long, date: String, category: String = "", cur: String = "USD") =
        Txn(id = next++, direction = TxnDirection.DEBIT, amountMinor = minor, currency = cur, merchant = merchant, date = d(date), category = category)

    private fun loan(name: String = "Car loan", emi: Long = 50_000) = Loan(
        id = 1, name = name, lender = "Bank", principalMinor = 600_000, currency = "USD", annualRatePercent = BigDecimal.ZERO,
        tenureMonths = 12, firstEmiDate = d("2026-01-05"), emiOverrideMinor = emi,
    )

    @Test fun repeatedEmiDebitsBecomeALoanWithPaidInstalments() {
        val t = listOf(debit("HDFC Bank", 1_245_000, "2026-08-05", "EMI and loans"), debit("HDFC Bank", 1_245_000, "2026-09-05", "EMI and loans"))
        val g = AutoTrack.loanGuessesFromHistory(t, emptyList(), emptySet()).single()
        assertEquals("HDFC Bank", g.name)
        assertEquals(1_245_000L, g.emiMinor)
        assertEquals(d("2026-08-05"), g.firstDate)
        assertEquals(2, g.paidDates.size)
        val loan = AutoTrack.toLoan(g)
        assertEquals(12, loan.tenureMonths)
        assertEquals(1_245_000L * 12, loan.principalMinor)
        assertEquals(1_245_000L, loan.emiOverrideMinor)
        assertTrue(loan.notes.contains("Placeholders"))
        assertTrue(!loan.active)
        assertTrue(AutoTrack.isUnreviewed(loan.notes))
        assertTrue(!AutoTrack.isUnreviewed(AutoTrack.reviewed(loan.notes)))
    }

    @Test fun oneDebitOrIrregularDebitsAreNotALoan() {
        assertTrue(AutoTrack.loanGuessesFromHistory(listOf(debit("HDFC", 500, "2026-09-05", "EMI and loans")), emptyList(), emptySet()).isEmpty())
        val irregular = listOf(debit("HDFC", 500, "2026-07-05", "EMI and loans"), debit("HDFC", 500, "2026-09-20", "EMI and loans"))
        assertTrue(AutoTrack.loanGuessesFromHistory(irregular, emptyList(), emptySet()).isEmpty())
        val otherAmounts = listOf(debit("HDFC", 500, "2026-08-05", "EMI and loans"), debit("HDFC", 900, "2026-09-05", "EMI and loans"))
        assertTrue(AutoTrack.loanGuessesFromHistory(otherAmounts, emptyList(), emptySet()).isEmpty())
    }

    @Test fun knownLoansAndTurnedDownLoansAreNotCreatedAgain() {
        val t = listOf(debit("Bank", 50_000, "2026-08-05", "EMI and loans"), debit("Bank", 50_000, "2026-09-05", "EMI and loans"))
        assertTrue(AutoTrack.loanGuessesFromHistory(t, listOf(loan()), emptySet()).isEmpty())
        assertTrue(AutoTrack.loanGuessesFromHistory(t, emptyList(), setOf(AutoTrack.loanKey("Bank"))).isEmpty())
        assertEquals(1, AutoTrack.loanGuessesFromHistory(t, emptyList(), emptySet()).size)
    }

    @Test fun twoLoansAtOneLenderStaySeparate() {
        val t = listOf(
            debit("Bajaj Finance", 30_000, "2026-08-02", "EMI and loans"), debit("Bajaj Finance", 30_000, "2026-09-02", "EMI and loans"),
            debit("Bajaj Finance", 80_000, "2026-08-10", "EMI and loans"), debit("Bajaj Finance", 80_000, "2026-09-10", "EMI and loans"),
        )
        assertEquals(setOf(30_000L, 80_000L), AutoTrack.loanGuessesFromHistory(t, emptyList(), emptySet()).map { it.emiMinor }.toSet())
    }

    @Test fun anEmiMessageWithTenureCreatesALoanAtOnce() {
        val text = "HDFC Bank\nYour EMI of Rs 12,450.00 for Home Loan A/c XX1234 is due on 05/11/2026.\nTenure: 240 months. Interest rate 8.5%"
        val g = AutoTrack.loanGuessFromMessage(text, "INR", today, emptyList(), emptySet())!!
        assertEquals(1_245_000L, g.emiMinor)
        assertEquals(240, g.tenureMonths)
        assertEquals(d("2026-11-05"), g.firstDate)
        val loan = AutoTrack.toLoan(g)
        assertEquals(240, loan.tenureMonths)
        assertEquals(BigDecimal("8.5"), loan.annualRatePercent)
        // 12,450 a month for 20 years at 8.5% is a loan of roughly 14.3 lakh.
        assertTrue(loan.principalMinor in 140_000_000L..146_000_000L)
    }

    @Test fun anEmiMessageWithoutATenureIsLeftToTheHistoryRule() {
        assertNull(AutoTrack.loanGuessFromMessage("Your EMI of Rs 500 for Home Loan A/c XX1234 was debited.", "INR", today, emptyList(), emptySet()))
        assertNull(AutoTrack.loanGuessFromMessage("Hello there", "INR", today, emptyList(), emptySet()))
    }

    @Test fun repeatingChargesAreSubscriptionsButNotLoanInstalmentsOrDismissedOnes() {
        val netflix = listOf(debit("Netflix", 1599, "2026-08-08"), debit("Netflix", 1599, "2026-09-08"))
        assertEquals("Netflix", AutoTrack.subscriptionGuesses(netflix, emptyList(), emptySet()).single().merchant)
        assertTrue(AutoTrack.subscriptionGuesses(netflix, listOf("Netflix"), emptySet()).isEmpty())
        assertTrue(AutoTrack.subscriptionGuesses(netflix, emptyList(), setOf(AutoTrack.subKey("Netflix"))).isEmpty())
        val emis = listOf(debit("HDFC Bank", 5000, "2026-08-05", "EMI and loans"), debit("HDFC Bank", 5000, "2026-09-05", "EMI and loans"))
        assertTrue(AutoTrack.subscriptionGuesses(emis, emptyList(), emptySet()).isEmpty())
    }

    @Test fun principalEstimateMatchesTheEmiFormula() {
        assertEquals(120_000L, AutoTrack.estimatePrincipal(10_000, 12, null))
        val p = AutoTrack.estimatePrincipal(10_000, 12, BigDecimal("12"))
        assertNotNull(p)
        assertTrue(p in 112_000L..113_000L)
    }

    @Test fun lenderNamedPaymentsAndAmountsOfKnownLoansAreNotSubscriptions() {
        val finance = listOf(debit("Tata Capital Ltd", 800_000, "2026-08-05"), debit("Tata Capital Ltd", 800_000, "2026-09-05"))
        assertTrue(AutoTrack.subscriptionGuesses(finance, emptyList(), emptySet()).isEmpty())
        // The same lender shows up as a loan, never as a subscription.
        assertEquals(1, AutoTrack.loanGuessesFromHistory(finance, emptyList(), emptySet()).size)
        val odd = listOf(debit("Acme Co", 50_000, "2026-08-05"), debit("Acme Co", 50_000, "2026-09-05"))
        assertTrue(AutoTrack.subscriptionGuesses(odd, emptyList(), emptySet(), loans = listOf(loan())).isEmpty())
        assertEquals(1, AutoTrack.subscriptionGuesses(odd, emptyList(), emptySet()).size)
        assertTrue(AutoTrack.isEmiTxn(debit("Bajaj Finserv", 100, "2026-09-05")))
        assertTrue(!AutoTrack.isEmiTxn(debit("Netflix", 100, "2026-09-05")))
    }
}
