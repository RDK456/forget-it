package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class CategoryDetailTest {
    private var next = 1L
    private fun debit(merchant: String?, minor: Long, date: String, category: String = "", note: String = "") =
        Txn(id = next++, direction = TxnDirection.DEBIT, amountMinor = minor, currency = "USD", merchant = merchant, date = d(date), category = category, note = note)

    private val oct = YearMonth.of(2026, 10)
    private val txns = listOf(
        debit("Swiggy", 1200, "2026-10-02"), debit("Swiggy", 800, "2026-10-09"), debit("Zomato", 2000, "2026-10-09"),
        debit("Corner Cafe", 500, "2026-10-30", category = "Food and dining"), debit(null, 300, "2026-10-15", category = "Food and dining", note = "Tea stall"),
        debit("Swiggy", 700, "2026-09-20"), debit("Uber", 999, "2026-10-03"),
    )

    private fun detail() = CategoryBreakdown.detail(txns, oct, "Food and dining", "USD", emptyMap())

    @Test fun totalsAndMerchantsRankedBySpend() {
        val d = detail()
        assertEquals(4800L, d.totalMinor)
        assertEquals(700L, d.previousMinor)
        assertEquals(listOf("Swiggy", "Zomato", "Corner Cafe", "Tea stall"), d.byMerchant.map { it.merchant })
        assertEquals(2, d.byMerchant.first().count)
        assertEquals(2000L, d.byMerchant.first().minor)
        assertEquals(42, d.byMerchant.first().percent)
    }

    @Test fun whenItWasSpentByDayAndWeek() {
        val d = detail()
        assertEquals(2800L, d.byDay[9])
        assertEquals(9 to 2800L, d.biggestDay)
        assertEquals(listOf(1 to 7, 8 to 14, 15 to 21, 22 to 28, 29 to 31), d.byWeek.map { it.fromDay to it.toDay })
        assertEquals(listOf(1200L, 2800L, 300L, 0L, 500L), d.byWeek.map { it.minor })
    }

    @Test fun paymentsListedNewestFirstAndBiggestFound() {
        val d = detail()
        assertEquals(5, d.txns.size)
        assertEquals("Corner Cafe", d.txns.first().merchant)
        assertEquals("Zomato", d.biggestTxn!!.merchant)
    }

    @Test fun unrelatedCategoriesAndMonthsAreLeftOut() {
        val d = CategoryBreakdown.detail(txns, oct, "Transport", "USD", emptyMap())
        assertEquals(999L, d.totalMinor)
        val none = CategoryBreakdown.detail(txns, YearMonth.of(2026, 1), "Transport", "USD", emptyMap())
        assertEquals(0L, none.totalMinor)
        assertNull(none.biggestDay)
    }

    @Test fun receiptLineWithQuantityTimesUnitPriceThenTotal() {
        val r = GroceryScan.fromReceipt("Tomato 3 x 1.50   4.50\nMilk 1L   2.49\nBread   1.80", "USD").associateBy { it.name }
        assertEquals(3000L, r.getValue("Tomato").quantityMilli)
        assertEquals(450L, r.getValue("Tomato").priceMinor)
        assertEquals("Produce", r.getValue("Tomato").category)
        assertEquals(1000L, r.getValue("Milk").quantityMilli)
        assertEquals(3, r.size)
    }
}
