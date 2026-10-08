package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TxnIntelTest {
    private val today = d("2026-10-08")
    private var next = 1L
    private fun debit(merchant: String?, amount: Long, date: String, status: String = "NEW") =
        Txn(id = next++, direction = TxnDirection.DEBIT, amountMinor = amount, currency = "USD", merchant = merchant, date = d(date), status = status)

    @Test fun merchantSpellingsCollapseToOneName() {
        assertEquals("Netflix", Merchants.canonical("NETFLIX.COM"))
        assertEquals("Netflix", Merchants.canonical("Netflix India"))
        assertEquals("Amazon Prime", Merchants.canonical("AMAZON PRIME VIDEO"))
        assertEquals("Corner Cafe", Merchants.canonical("Corner Cafe"))
    }

    @Test fun parserUsesCanonicalMerchant() {
        val t = SmsParser.parse("Rs 499.00 spent on Card ending 1234 at NETFLIX.COM on 08-Oct-26.", today)!!
        assertEquals("Netflix", t.merchant)
    }

    private fun netflix(amount: Long = 1599) = sub(amount = amount, start = "2026-01-01").copy(name = "Netflix")

    @Test fun higherChargeIsReportedAsPriceChange() {
        val c = PriceWatch.changes(listOf(debit("Netflix", 1799, "2026-10-05")), listOf(netflix()), today).single()
        assertEquals(1599L, c.oldMinor)
        assertEquals(1799L, c.newMinor)
    }

    @Test fun sameAmountOldChargeAndIgnoredChargeAreNotReported() {
        assertTrue(PriceWatch.changes(listOf(debit("Netflix", 1599, "2026-10-05")), listOf(netflix()), today).isEmpty())
        assertTrue(PriceWatch.changes(listOf(debit("Netflix", 1799, "2026-07-01")), listOf(netflix()), today).isEmpty())
        assertTrue(PriceWatch.changes(listOf(debit("Netflix", 1799, "2026-10-05", "IGNORED")), listOf(netflix()), today).isEmpty())
        assertTrue(PriceWatch.changes(listOf(debit("Spotify", 1799, "2026-10-05")), listOf(netflix()), today).isEmpty())
    }

    private val loan = Loan(
        id = 1, name = "Car", principalMinor = 1_200_000, currency = "USD", annualRatePercent = BigDecimal("12"),
        tenureMonths = 12, firstEmiDate = d("2026-11-01"),
    )

    @Test fun prepayingReducingTenureSavesInterestAndInstallments() {
        val r = PrepayWhatIf.run(loan, emptyList(), emptyList(), today, 600_000, AdjustmentKind.PREPAYMENT_REDUCE_TENURE)
        assertTrue(r.interestSavedMinor > 0)
        assertTrue(r.installmentsSaved > 0)
    }

    @Test fun prepayingReducingEmiKeepsLengthAndLowersEmi() {
        val base = Amortization.build(loan, emptyList(), emptyList(), today).emiMinor
        val r = PrepayWhatIf.run(loan, emptyList(), emptyList(), today, 600_000, AdjustmentKind.PREPAYMENT_REDUCE_EMI)
        assertTrue(r.newEmiMinor < base)
        assertEquals(0, r.installmentsSaved)
        assertTrue(r.interestSavedMinor > 0)
    }

    @Test fun zeroPrepaymentSavesNothing() {
        val r = PrepayWhatIf.run(loan, emptyList(), emptyList(), today, 0, AdjustmentKind.PREPAYMENT_REDUCE_TENURE)
        assertEquals(0L, r.interestSavedMinor)
    }
}
