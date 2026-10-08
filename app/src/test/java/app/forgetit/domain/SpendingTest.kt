package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

class SpendingTest {
    private var next = 1L
    private fun debit(merchant: String?, minor: Long, date: String, category: String = "", cur: String = "USD", status: String = "NEW") =
        Txn(id = next++, direction = TxnDirection.DEBIT, amountMinor = minor, currency = cur, merchant = merchant, date = d(date), category = category, status = status)
    private fun credit(merchant: String?, minor: Long, date: String) =
        Txn(id = next++, direction = TxnDirection.CREDIT, amountMinor = minor, currency = "USD", merchant = merchant, date = d(date))

    @Test fun merchantsAreCategorised() {
        fun c(m: String, t: String = "") = Categorizer.categorize(m, t, TxnDirection.DEBIT)
        assertEquals("Food and dining", c("Swiggy"))
        assertEquals("Groceries", c("BigBasket"))
        assertEquals("Transport", c("Uber India"))
        assertEquals("Subscriptions", c("Netflix"))
        assertEquals("Subscriptions", c("Amazon Prime"))
        assertEquals("Shopping", c("Amazon"))
        assertEquals("EMI and loans", c("HDFC Bank", "Your EMI of Rs 500 debited"))
        assertEquals("Other", c("Corner Kiosk"))
    }

    @Test fun wordsMatchWholeWordStartsNotInsideOtherWords() {
        // "ola" must not match inside "Cola"; "bus" not inside "Business".
        assertEquals("Other", Categorizer.categorize("Cola House", "", TxnDirection.DEBIT))
        assertEquals("Other", Categorizer.categorize("Business Hub", "", TxnDirection.DEBIT))
    }

    @Test fun incomeIsCategorisedAndRulesWin() {
        assertEquals("Salary", Categorizer.categorize("Acme", "Salary credited", TxnDirection.CREDIT))
        assertEquals("Refund", Categorizer.categorize("Amazon", "refund processed", TxnDirection.CREDIT))
        assertEquals("Other income", Categorizer.categorize("Friend", "UPI", TxnDirection.CREDIT))
        val rules = mapOf(Categorizer.ruleKey("Corner Kiosk") to "Food and dining")
        assertEquals("Food and dining", Categorizer.categorize("Corner Kiosk", "", TxnDirection.DEBIT, rules))
    }

    @Test fun savedCategoryBeatsTheGuess() {
        assertEquals("Health", debit("Swiggy", 100, "2026-10-01", category = "Health").categoryOr())
        assertEquals("Food and dining", debit("Swiggy", 100, "2026-10-01").categoryOr())
    }

    private val oct = YearMonth.of(2026, 10)

    @Test fun summaryRanksCategoriesAndComparesWithLastMonth() {
        val t = listOf(
            debit("Swiggy", 3000, "2026-10-02"), debit("Zomato", 2000, "2026-10-09"), debit("Uber", 1000, "2026-10-03"),
            debit("Swiggy", 2500, "2026-09-12"), credit("Acme", 50_000, "2026-10-01"),
            debit("Ignored", 99_999, "2026-10-04", status = "IGNORED"),
        )
        val s = SpendStats.summary(t, oct, "USD", emptyMap())
        assertEquals(6000L, s.spentMinor)
        assertEquals(50_000L, s.incomeMinor)
        assertEquals(44_000L, s.netMinor)
        assertEquals("Food and dining", s.top!!.category)
        assertEquals(5000L, s.top!!.minor)
        assertEquals(83, s.top!!.percent)
        assertEquals(2500L, s.top!!.previousMinor)
        assertEquals(listOf("Food and dining", "Transport"), s.byCategory.map { it.category })
    }

    @Test fun otherCurrenciesConvertOrAreCountedAsExcluded() {
        val t = listOf(debit("Swiggy", 100_000, "2026-10-02", cur = "INR"), debit("Hotel", 500, "2026-10-03", cur = "EUR"))
        val withRate = SpendStats.summary(t, oct, "USD", mapOf("INR" to BigDecimal("0.012")))
        assertEquals(1200L, withRate.byCategory.first { it.category == "Food and dining" }.minor)
        assertEquals(1, withRate.excluded)
    }

    @Test fun trendHasOneEntryPerMonthOldestFirst() {
        val t = listOf(debit("Swiggy", 100, "2026-08-02"), debit("Swiggy", 300, "2026-10-02"))
        val tr = SpendStats.trend(t, oct, 3, "USD", emptyMap())
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), oct), tr.map { it.first })
        assertEquals(listOf(100L, 0L, 300L), tr.map { it.second })
    }

    @Test fun projectionAndDailyAllowance() {
        assertEquals(9300L, SpendStats.projected(3000, d("2026-10-10"), oct))
        assertEquals(3000L, SpendStats.projected(3000, d("2026-10-10"), YearMonth.of(2026, 9)))
        // 31-day month, on the 11th: 21 days left including today. 10_500 left / 21 = 500.
        assertEquals(500L, SpendStats.dailyAllowance(20_500, 10_000, d("2026-10-11")))
        assertEquals(0L, SpendStats.dailyAllowance(1_000, 1_500, d("2026-10-11")))
    }

    @Test fun budgetLevels() {
        assertEquals(BudgetLevel.OK, SpendStats.level(79, 100))
        assertEquals(BudgetLevel.WARN, SpendStats.level(80, 100))
        assertEquals(BudgetLevel.OVER, SpendStats.level(100, 100))
        assertEquals(BudgetLevel.OK, SpendStats.level(500, 0))
        assertNull(SpendStats.summary(emptyList(), oct, "USD", emptyMap()).top)
    }
}
