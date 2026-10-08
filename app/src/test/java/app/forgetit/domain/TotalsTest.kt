package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class TotalsTest {
    private val today = d("2026-10-08")

    @Test fun sameCurrencyNeedsNoRate() {
        val t = computeTotals(listOf(sub(amount = 1000), sub(amount = 500)), today, "USD", emptyMap())
        assertEquals(Totals(1500, 18000, 0), t)
    }

    @Test fun convertsUsingRate() {
        val t = computeTotals(listOf(sub(amount = 1000, currency = "EUR")), today, "USD", mapOf("EUR" to BigDecimal("1.10")))
        assertEquals(1100L, t.monthlyMinor)
    }

    @Test fun convertsZeroDecimalCurrency() {
        // 1000 JPY * 0.0067 USD/JPY = 6.70 USD = 670 cents
        val t = computeTotals(listOf(sub(amount = 1000, currency = "JPY")), today, "USD", mapOf("JPY" to BigDecimal("0.0067")))
        assertEquals(670L, t.monthlyMinor)
    }

    @Test fun missingRateIsExcludedAndCounted() {
        val t = computeTotals(listOf(sub(amount = 1000, currency = "EUR"), sub(amount = 500)), today, "USD", emptyMap())
        assertEquals(Totals(500, 6000, 1), t)
    }

    @Test fun activeTrialCostsZeroInactiveSkipped() {
        val subs = listOf(sub(amount = 1000, trialEnds = "2026-10-20"), sub(amount = 700, active = false), sub(amount = 300))
        assertEquals(Totals(300, 3600, 0), computeTotals(subs, today, "USD", emptyMap()))
    }

    @Test fun yearlyTotalSumsUnroundedMonthlies() {
        val subs = listOf(sub(Cycle.YEARLY, amount = 9999), sub(Cycle.YEARLY, amount = 9999))
        assertEquals(Totals(1667, 19998, 0), computeTotals(subs, today, "USD", emptyMap()))
    }
}
