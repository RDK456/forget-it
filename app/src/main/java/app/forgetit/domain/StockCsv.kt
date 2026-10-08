package app.forgetit.domain

import java.time.LocalDate

data class StockBundle(val item: StockItem, val batches: List<StockBatch>, val logs: List<StockLog>)

/** One file for stock: ITEM rows, then BATCH and LOG rows pointing at an item_key. Log deltas keep their sign. */
object StockCsv {
    val HEADER = listOf(
        "record_type", "item_key", "name", "unit", "category", "low_threshold", "daily_usage", "expiry_alert_days",
        "baseline_date", "notes", "active", "quantity", "added_on", "expiry", "log_date", "log_delta", "log_kind",
        "pack_size", "lead_days", "brand", "store", "price_minor",
    )

    private fun line(vararg pairs: Pair<String, String>): String {
        val m = pairs.toMap()
        return Csv.row(HEADER.map { m[it].orEmpty() })
    }

    private fun signed(milli: Long) = (if (milli < 0) "-" else "") + Money.milliToPlain(kotlin.math.abs(milli))

    fun export(items: List<StockItem>, batches: List<StockBatch>, logs: List<StockLog>): String {
        val out = mutableListOf(Csv.row(HEADER))
        for (i in items) {
            val key = i.id.toString()
            out += line(
                "record_type" to "ITEM", "item_key" to key, "name" to Csv.guard(i.name), "unit" to i.unit, "category" to i.category,
                "low_threshold" to Money.milliToPlain(i.lowThresholdMilli), "daily_usage" to (i.dailyUsageMilli?.let(Money::milliToPlain) ?: ""),
                "expiry_alert_days" to i.expiryAlertDays.toString(), "baseline_date" to i.baselineDate.toString(),
                "notes" to Csv.guard(i.notes), "active" to i.active.toString(), "pack_size" to (i.packSizeMilli?.let(Money::milliToPlain) ?: ""),
                "lead_days" to i.leadDays.toString(), "brand" to Csv.guard(i.brand), "store" to Csv.guard(i.store),
            )
            batches.filter { it.itemId == i.id }.forEach { b ->
                out += line(
                    "record_type" to "BATCH", "item_key" to key, "quantity" to Money.milliToPlain(b.quantityMilli),
                    "added_on" to b.addedOn.toString(), "expiry" to (b.expiry?.toString() ?: ""),
                )
            }
            logs.filter { it.itemId == i.id }.sortedBy { it.date }.forEach { l ->
                out += line(
                    "record_type" to "LOG", "item_key" to key, "log_date" to l.date.toString(), "log_delta" to signed(l.deltaMilli),
                    "log_kind" to l.kind.name, "price_minor" to (l.priceMinor?.toString() ?: ""),
                )
            }
        }
        return out.joinToString("\r\n") + "\r\n"
    }
}
