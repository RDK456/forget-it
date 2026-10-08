package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockCsvTest {
    private val item = StockItem(
        id = 3, name = "+Milk, \"fresh\"", unit = "L", category = "Dairy", lowThresholdMilli = 500, dailyUsageMilli = 250,
        expiryAlertDays = 3, baselineDate = d("2026-10-08"), notes = "line1\nline2", active = true,
    )
    private val batches = listOf(
        StockBatch(itemId = 3, quantityMilli = 1500, addedOn = d("2026-10-01"), expiry = d("2026-10-12")),
        StockBatch(itemId = 3, quantityMilli = 2000, addedOn = d("2026-10-05"), expiry = null),
    )
    private val logs = listOf(
        StockLog(itemId = 3, date = d("2026-10-01"), deltaMilli = 3500, kind = LogKind.RESTOCK),
        StockLog(itemId = 3, date = d("2026-10-06"), deltaMilli = -250, kind = LogKind.USED),
    )

    @Test fun roundTripKeepsItemBatchesAndSignedLogs() {
        val r = StockCsv.import(StockCsv.export(listOf(item), batches, logs))
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        val b = r.items.single()
        assertEquals(item.copy(id = 0), b.item)
        assertEquals(batches.map { it.quantityMilli to it.expiry }, b.batches.map { it.quantityMilli to it.expiry })
        assertEquals(logs.map { Triple(it.date, it.deltaMilli, it.kind) }, b.logs.map { Triple(it.date, it.deltaMilli, it.kind) })
    }

    @Test fun rowsBelongToTheirOwnItem() {
        val other = item.copy(id = 4, name = "Rice", unit = "kg")
        val ob = StockBatch(itemId = 4, quantityMilli = 5000, addedOn = d("2026-10-02"))
        val r = StockCsv.import(StockCsv.export(listOf(item, other), batches + ob, logs))
        assertEquals(listOf(2, 1), r.items.map { it.batches.size })
        assertEquals(listOf(2, 0), r.items.map { it.logs.size })
    }

    @Test fun badRowsAreReported() {
        val csv = "record_type,item_key,name,unit,quantity,added_on,log_date,log_delta,log_kind\r\n" +
            "ITEM,1,Eggs,pcs,,,,,\r\n" +
            "BATCH,1,,,12,2026-10-01,,,\r\n" +
            "BATCH,1,,,abc,2026-10-01,,,\r\n" +
            "LOG,7,,,,,2026-10-01,-1,USED\r\n" +
            "LOG,1,,,,,2026-10-01,-1,EATEN\r\n"
        val r = StockCsv.import(csv)
        assertEquals(1, r.items.single().batches.size)
        assertEquals(listOf("Row 4: invalid batch", "Row 5: no valid item with item_key 7", "Row 6: invalid log"), r.errors)
    }

    @Test fun missingColumnsAreExplained() {
        assertEquals(listOf("Missing columns: record_type, item_key"), StockCsv.import("name\r\nx\r\n").errors)
    }
}
