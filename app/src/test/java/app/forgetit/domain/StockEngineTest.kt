package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class StockEngineTest {
    private val today = d("2026-10-08")
    private fun item(threshold: Long = 0, usage: Long? = null, baseline: String = "2026-10-08") =
        StockItem(id = 1, name = "Milk", unit = "L", lowThresholdMilli = threshold, dailyUsageMilli = usage, baselineDate = d(baseline))
    private fun batch(id: Long, qty: Long, expiry: String? = null, added: String = "2026-10-01") =
        StockBatch(id = id, itemId = 1, quantityMilli = qty, addedOn = d(added), expiry = expiry?.let(::d))
    private fun used(date: String, qty: Long) = StockLog(itemId = 1, date = d(date), deltaMilli = -qty, kind = LogKind.USED)

    @Test fun useTakesEarliestExpiryFirstAndNoExpiryLast() {
        val bs = listOf(batch(1, 1000, "2026-10-11"), batch(2, 500, "2026-10-09"), batch(3, 2000))
        val r = StockEngine.use(bs, 700, today)
        assertEquals(700L, r.consumedMilli)
        assertEquals(0L, r.shortfallMilli)
        assertEquals(listOf(2L), r.removedIds)
        assertEquals(listOf(800L, 2000L), r.remaining.map { it.quantityMilli })
    }

    @Test fun useNeverTouchesExpiredBatchesAndReportsShortfall() {
        val bs = listOf(batch(1, 1000, "2026-10-01"), batch(2, 300))
        val r = StockEngine.use(bs, 500, today)
        assertEquals(300L, r.consumedMilli)
        assertEquals(200L, r.shortfallMilli)
        assertEquals(listOf(1L), r.remaining.map { it.id })
    }

    @Test fun manualRateWinsAndAutoRateNeedsHistory() {
        assertEquals(BigDecimal(250), StockEngine.usageRate(item(usage = 250), emptyList(), today))
        assertNull(StockEngine.usageRate(item(), listOf(used("2026-10-01", 500)), today))
        assertNull(StockEngine.usageRate(item(), listOf(used("2026-10-01", 500), used("2026-10-04", 500)), today))
        val rate = StockEngine.usageRate(item(), listOf(used("2026-09-18", 1000), used("2026-09-28", 1000)), today)!!
        assertEquals(0, rate.compareTo(BigDecimal(100)))
    }

    @Test fun estimateFallsFromTheBaselineAndFloorsAtZero() {
        val i = item(usage = 100, baseline = "2026-10-03")
        assertEquals(500L, StockEngine.estimated(i, 1000, BigDecimal(100), today))
        assertEquals(0L, StockEngine.estimated(i, 300, BigDecimal(100), today))
        assertEquals(1000L, StockEngine.estimated(i, 1000, null, today))
    }

    @Test fun runOutDate() {
        assertEquals(d("2026-10-13"), StockEngine.runOut(500, BigDecimal(100), today))
        assertNull(StockEngine.runOut(500, null, today))
        assertEquals(today, StockEngine.runOut(0, BigDecimal(100), today))
    }

    @Test fun lowByThresholdOrByRunOutSoon() {
        val i = item(threshold = 500)
        assertTrue(StockEngine.isLow(i, 500, null, today))
        assertFalse(StockEngine.isLow(i, 501, null, today))
        assertTrue(StockEngine.isLow(i, 900, d("2026-10-10"), today))
        assertFalse(StockEngine.isLow(i, 900, d("2026-10-11"), today))
        assertTrue(StockEngine.isLow(item(), 0, null, today))
    }

    @Test fun thresholdCrossingDate() {
        val i = item(threshold = 200, usage = 100)
        assertEquals(d("2026-10-16"), StockEngine.crossesThresholdOn(i, 1000, BigDecimal(100)))
        assertNull(StockEngine.crossesThresholdOn(i, 1000, null))
    }

    @Test fun statusReportsExpiredAndNextExpiry() {
        val bs = listOf(batch(1, 1000, "2026-10-01"), batch(2, 500, "2026-10-12"), batch(3, 100, "2026-10-09"))
        val s = StockEngine.status(item(), bs, emptyList(), today)
        assertEquals(listOf(1L), s.expiredBatches.map { it.id })
        assertEquals(d("2026-10-09"), s.nextExpiry)
        assertEquals(1600L, s.storedMilli)
    }
}
