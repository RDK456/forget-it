package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class StockReminderTest {
    private fun at(s: String) = ZonedDateTime.parse(s + "[Asia/Kolkata]")
    private val now = at("2026-10-08T12:00:00+05:30")
    private fun item(threshold: Long = 500, usage: Long? = null, active: Boolean = true) =
        StockItem(id = 1, name = "Milk", unit = "L", lowThresholdMilli = threshold, dailyUsageMilli = usage, baselineDate = d("2026-10-08"), active = active)
    private fun batch(id: Long, qty: Long, expiry: String?) = StockBatch(id = id, itemId = 1, quantityMilli = qty, addedOn = d("2026-10-01"), expiry = expiry?.let(::d))
    private fun plan(i: StockItem, bs: List<StockBatch>, notified: Set<String> = emptySet()) =
        ReminderPlanner.stock(listOf(i), bs, emptyList(), now, 540, notified)

    @Test fun lowStockRemindsOncePerEpisode() {
        val spec = plan(item(), listOf(batch(1, 400, null))).single()
        assertEquals(ReminderKind.LOW_STOCK, spec.kind)
        assertEquals(now.plusMinutes(1), spec.triggerAt)
        assertTrue(plan(item(), listOf(batch(1, 400, null)), setOf(spec.key)).isEmpty())
    }

    @Test fun stockAboveThresholdWithARateRemindsOnTheCrossingDay() {
        val spec = plan(item(threshold = 200, usage = 100), listOf(batch(1, 1000, null))).single()
        assertEquals(d("2026-10-16"), spec.eventDate)
        assertEquals(at("2026-10-16T09:00:00+05:30"), spec.triggerAt)
    }

    @Test fun healthyStockWithoutARateNeedsNoReminder() {
        assertTrue(plan(item(), listOf(batch(1, 5000, null))).isEmpty())
    }

    @Test fun brandNewEmptyItemDoesNotNagImmediately() {
        assertTrue(plan(item(threshold = 0), emptyList()).isEmpty())
    }

    @Test fun expiryRemindsLeadDaysBeforeAndFlagsExpiredBatches() {
        val specs = plan(item(threshold = 0), listOf(batch(1, 2000, "2026-10-12"), batch(2, 1000, "2026-10-01")))
            .filter { it.kind == ReminderKind.EXPIRY }.associateBy { it.entityId }
        assertEquals(at("2026-10-10T09:00:00+05:30"), specs.getValue(1).triggerAt)
        assertTrue(specs.getValue(2).title.startsWith("Expired"))
        assertEquals(now.plusMinutes(1), specs.getValue(2).triggerAt)
    }

    @Test fun inactiveItemsAndEmptyBatchesAreIgnored() {
        assertTrue(plan(item(active = false), listOf(batch(1, 100, "2026-10-09"))).isEmpty())
        assertTrue(plan(item(threshold = 0), listOf(batch(1, 0, "2026-10-09"))).none { it.kind == ReminderKind.EXPIRY })
    }

    @Test fun validatorRules() {
        assertTrue(StockValidator.validate(item()).isEmpty())
        assertEquals(listOf("name"), StockValidator.validate(item().copy(name = " ")).map { it.field })
        assertEquals(listOf("unit"), StockValidator.validate(item().copy(unit = "")).map { it.field })
        assertTrue(StockValidator.validateBatch(0) != null)
        assertTrue(StockValidator.validateBatch(1500) == null)
    }
}
