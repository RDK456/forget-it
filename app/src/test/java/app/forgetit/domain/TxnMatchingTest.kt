package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TxnMatchingTest {
    private var next = 1L
    private fun debit(merchant: String?, amount: Long, date: String, cur: String = "USD") =
        Txn(id = next++, direction = TxnDirection.DEBIT, amountMinor = amount, currency = cur, merchant = merchant, date = d(date))

    @Test fun monthlyChargeIsSuggestedOnce() {
        val t = listOf(debit("Netflix", 1599, "2026-08-08"), debit("Netflix", 1599, "2026-09-08"), debit("Netflix", 1599, "2026-10-08"))
        val s = TxnMatching.recurring(t, emptyList()).single()
        assertEquals(Cycle.MONTHLY, s.cycle)
        assertEquals(3, s.count)
        assertEquals(1599L, s.amountMinor)
    }

    @Test fun yearlyAndWeeklyIntervals() {
        assertEquals(Cycle.YEARLY, TxnMatching.recurring(listOf(debit("Prime", 9900, "2025-10-01"), debit("Prime", 9900, "2026-10-01")), emptyList()).single().cycle)
        assertEquals(Cycle.WEEKLY, TxnMatching.recurring(listOf(debit("Milk Co", 500, "2026-10-01"), debit("Milk Co", 500, "2026-10-08")), emptyList()).single().cycle)
    }

    @Test fun irregularOrVaryingChargesAreNotSuggested() {
        assertTrue(TxnMatching.recurring(listOf(debit("Cafe", 300, "2026-09-01"), debit("Cafe", 300, "2026-09-12"), debit("Cafe", 300, "2026-10-08")), emptyList()).isEmpty())
        assertTrue(TxnMatching.recurring(listOf(debit("Uber", 300, "2026-08-08"), debit("Uber", 900, "2026-09-08")), emptyList()).isEmpty())
        assertTrue(TxnMatching.recurring(listOf(debit("Netflix", 1599, "2026-10-08")), emptyList()).isEmpty())
    }

    @Test fun alreadyTrackedNamesAreSkipped() {
        val t = listOf(debit("NETFLIX", 1599, "2026-09-08"), debit("NETFLIX", 1599, "2026-10-08"))
        assertTrue(TxnMatching.recurring(t, listOf("Netflix Premium")).isEmpty())
    }

    @Test fun debitNearEmiAmountAndDateMatchesInstallment() {
        val loan = Loan(id = 3, name = "Car", principalMinor = 100_000, currency = "USD", annualRatePercent = BigDecimal("12"),
            tenureMonths = 12, firstEmiDate = d("2026-10-10"))
        val today = d("2026-10-09")
        val hit = TxnMatching.emiMatches(listOf(debit("Bank", 8885, "2026-10-09"), debit("Shop", 8885, "2026-08-01")), listOf(loan), emptyList(), emptyList(), today)
        assertEquals(listOf(EmiMatch(1, 3, "Car", 1)), hit)
        assertTrue(TxnMatching.emiMatches(listOf(debit("Bank", 5000, "2026-10-09")), listOf(loan), emptyList(), emptyList(), today).isEmpty())
    }
}
