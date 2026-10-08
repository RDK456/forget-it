package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FunStatsTest {
    private val loan = Loan(
        id = 1, name = "Car", principalMinor = 100_000, currency = "USD", annualRatePercent = BigDecimal("12"),
        tenureMonths = 12, firstEmiDate = d("2026-02-01"),
    )
    private fun payOn(no: Int, date: String) = LoanPayment(loanId = 1, installmentNo = no, paidOn = d(date), amountMinor = 0)
    private fun rows(pay: List<LoanPayment>) = Amortization.build(loan, emptyList(), pay, d("2026-06-15")).rows

    @Test fun streakCountsConsecutiveOnTimePaymentsBackFromTheLatest() {
        val pay = listOf(payOn(1, "2026-02-01"), payOn(2, "2026-02-27"), payOn(3, "2026-03-30"))
        assertEquals(3, onTimeStreak(rows(pay), pay))
    }

    @Test fun aLatePaymentBreaksTheStreakOnlyBehindIt() {
        val late = listOf(payOn(1, "2026-02-01"), payOn(2, "2026-03-05"), payOn(3, "2026-04-01"))
        assertEquals(1, onTimeStreak(rows(late), late))
        val lastLate = listOf(payOn(1, "2026-02-01"), payOn(2, "2026-03-01"), payOn(3, "2026-04-09"))
        assertEquals(0, onTimeStreak(rows(lastLate), lastLate))
        assertEquals(0, onTimeStreak(rows(emptyList()), emptyList()))
    }

    private val today = d("2026-10-08")
    private fun item(id: Long, name: String, threshold: Long = 500, usage: Long? = null, active: Boolean = true) =
        StockItem(id = id, name = name, unit = "L", lowThresholdMilli = threshold, dailyUsageMilli = usage, baselineDate = today, active = active)
    private fun batch(itemId: Long, qty: Long, expiry: String? = null) =
        StockBatch(itemId = itemId, quantityMilli = qty, addedOn = d("2026-10-01"), expiry = expiry?.let(::d))

    @Test fun shoppingListHasLowOutAndExpiredButNotHealthyOrInactiveItems() {
        val items = listOf(item(1, "Milk"), item(2, "Rice"), item(3, "Eggs", usage = 300), item(4, "Salt", active = false), item(5, "Soap", threshold = 0))
        val batches = listOf(batch(1, 400), batch(2, 5000), batch(3, 100), batch(4, 0), batch(5, 2000, "2026-10-01"))
        val lines = shoppingList(items, batches, emptyList(), today)
        assertEquals(listOf("Eggs", "Milk", "Soap"), lines.map { it.name })
        assertEquals("Running low", lines.first { it.name == "Milk" }.reason)
        assertEquals("Has expired stock", lines.first { it.name == "Soap" }.reason)
        assertEquals(2100L, lines.first { it.name == "Eggs" }.buyMilli)
    }

    @Test fun brandNewEmptyItemsAreNotListed() {
        assertTrue(shoppingList(listOf(item(1, "Milk")), emptyList(), emptyList(), today).isEmpty())
    }

    @Test fun shoppingTextIsReadableWhenShared() {
        val t = shoppingText(listOf(ShoppingLine(1, "Milk", "L", 400, 1000, "Running low")))
        assertEquals("Shopping list from Forget-it\n- Milk: about 1 L (running low)", t)
    }
}

class ShoppingUpgradeTest {
    private val today = d("2026-10-08")
    private fun item(id: Long, name: String, store: String = "", pack: Long? = null, lead: Int = 0, usage: Long? = null) =
        StockItem(id = id, name = name, unit = "L", lowThresholdMilli = 500, dailyUsageMilli = usage, baselineDate = today, store = store, packSizeMilli = pack, leadDays = lead)
    private fun batch(itemId: Long, qty: Long) = StockBatch(itemId = itemId, quantityMilli = qty, addedOn = d("2026-10-01"))

    @Test fun suggestedAmountRoundsUpToWholePacks() {
        val l = shoppingList(listOf(item(1, "Water", pack = 5000)), listOf(batch(1, 100)), emptyList(), today).single()
        assertEquals(5000L, l.buyMilli)
        val week = shoppingList(listOf(item(2, "Rice", pack = 1000, usage = 600)), listOf(batch(2, 100)), emptyList(), today).single()
        assertEquals(5000L, week.buyMilli)
    }

    @Test fun buyByAllowsForDeliveryLeadTime() {
        val l = shoppingList(listOf(item(1, "Gas", lead = 3, usage = 100)), listOf(batch(1, 450)), emptyList(), today).single()
        assertEquals(d("2026-10-09"), l.buyBy)
        val ok = shoppingList(listOf(item(2, "Gas", lead = 1, usage = 100)), listOf(batch(2, 450)), emptyList(), today).single()
        assertEquals(d("2026-10-11"), ok.buyBy)
    }

    @Test fun sharedTextGroupsByStoreWhenThereAreSeveral() {
        val lines = shoppingList(listOf(item(1, "Milk", store = "Dairy shop"), item(2, "Soap", store = "Mall")), listOf(batch(1, 100), batch(2, 100)), emptyList(), today)
        assertEquals(
            "Shopping list from Forget-it\nDairy shop:\n- Milk: about 1 L (running low)\nMall:\n- Soap: about 1 L (running low)",
            shoppingText(lines),
        )
    }

    @Test fun priceHistoryAndTrend() {
        val logs = listOf(
            StockLog(itemId = 1, date = d("2026-09-01"), deltaMilli = 1000, kind = LogKind.RESTOCK, priceMinor = 5000),
            StockLog(itemId = 1, date = d("2026-10-01"), deltaMilli = 1000, kind = LogKind.RESTOCK, priceMinor = 5500),
            StockLog(itemId = 1, date = d("2026-10-02"), deltaMilli = -100, kind = LogKind.USED, priceMinor = 9999),
        )
        val h = StockPrices.history(logs)
        assertEquals(listOf(5000L, 5500L), h.map { it.priceMinor })
        assertEquals(10, StockPrices.trendPercent(h))
        assertEquals(null, StockPrices.trendPercent(h.take(1)))
    }
}
