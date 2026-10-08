package app.forgetit.domain

import java.time.LocalDate

private fun parseSigned(text: String): Long? {
    val neg = text.startsWith("-")
    val v = Money.parseMilli(text.removePrefix("-")) ?: return null
    return if (neg) -v else v
}

/** Reads a file written by [StockCsv.export]. Bad rows are reported and skipped; rows of a rejected item are rejected too. */
fun StockCsv.import(text: String): CsvImport<StockBundle> {
    val rows = Csv.parse(text)
    if (rows.isEmpty()) return CsvImport(emptyList(), listOf("The file is empty"))
    val index = rows[0].mapIndexed { i, h -> h.trim().lowercase() to i }.toMap()
    val missing = listOf("record_type", "item_key").filter { it !in index }
    if (missing.isNotEmpty()) return CsvImport(emptyList(), listOf("Missing columns: ${missing.joinToString()}"))

    fun cell(r: List<String>, name: String) = index[name]?.let { r.getOrNull(it) }?.trim().orEmpty()
    val errors = mutableListOf<String>()
    val items = linkedMapOf<String, StockItem>()
    val batches = mutableMapOf<String, MutableList<StockBatch>>()
    val logs = mutableMapOf<String, MutableList<StockLog>>()
    val body = rows.drop(1).mapIndexed { n, r -> (n + 2) to r }

    for ((line, r) in body) {
        if (cell(r, "record_type").uppercase() != "ITEM") continue
        val key = cell(r, "item_key")
        val threshold = Money.parseMilli(cell(r, "low_threshold").ifEmpty { "0" })
        val usageText = cell(r, "daily_usage")
        val usage = if (usageText.isEmpty()) null else Money.parseMilli(usageText)
        val baseline = runCatching { LocalDate.parse(cell(r, "baseline_date")) }.getOrNull() ?: LocalDate.now()
        val problem = when {
            key.isEmpty() -> "missing item_key"
            threshold == null -> "invalid low_threshold"
            usageText.isNotEmpty() && usage == null -> "invalid daily_usage"
            else -> null
        }
        if (problem != null) { errors += "Row $line: $problem"; continue }
        val item = StockItem(
            name = Csv.unguard(cell(r, "name")), unit = cell(r, "unit").ifEmpty { "pcs" }, category = cell(r, "category").ifEmpty { "Other" },
            lowThresholdMilli = threshold!!, dailyUsageMilli = usage, expiryAlertDays = cell(r, "expiry_alert_days").toIntOrNull() ?: 2,
            baselineDate = baseline, notes = Csv.unguard(cell(r, "notes")), active = !cell(r, "active").equals("false", ignoreCase = true),
            packSizeMilli = cell(r, "pack_size").takeIf { it.isNotEmpty() }?.let { Money.parseMilli(it) },
            leadDays = cell(r, "lead_days").toIntOrNull()?.coerceIn(0, 60) ?: 0,
            brand = Csv.unguard(cell(r, "brand")), store = Csv.unguard(cell(r, "store")),
        )
        val invalid = StockValidator.validate(item)
        if (invalid.isEmpty()) items[key] = item else errors += "Row $line: ${invalid.first().message}"
    }
    for ((line, r) in body) {
        val kind = cell(r, "record_type").uppercase()
        if (kind != "BATCH" && kind != "LOG") continue
        val key = cell(r, "item_key")
        if (items[key] == null) { errors += "Row $line: no valid item with item_key $key"; continue }
        if (kind == "BATCH") {
            val qty = Money.parseMilli(cell(r, "quantity"))
            val added = runCatching { LocalDate.parse(cell(r, "added_on")) }.getOrNull()
            val expText = cell(r, "expiry")
            val expiry = if (expText.isEmpty()) null else runCatching { LocalDate.parse(expText) }.getOrNull()
            if (qty == null || StockValidator.validateBatch(qty) != null || added == null || (expText.isNotEmpty() && expiry == null)) {
                errors += "Row $line: invalid batch"; continue
            }
            batches.getOrPut(key) { mutableListOf() } += StockBatch(itemId = 0, quantityMilli = qty, addedOn = added, expiry = expiry)
        } else {
            val date = runCatching { LocalDate.parse(cell(r, "log_date")) }.getOrNull()
            val delta = parseSigned(cell(r, "log_delta"))
            val k = runCatching { LogKind.valueOf(cell(r, "log_kind").uppercase()) }.getOrNull()
            if (date == null || delta == null || k == null) { errors += "Row $line: invalid log"; continue }
            logs.getOrPut(key) { mutableListOf() } += StockLog(itemId = 0, date = date, deltaMilli = delta, kind = k, priceMinor = cell(r, "price_minor").toLongOrNull())
        }
    }
    return CsvImport(items.map { (key, i) -> StockBundle(i, batches[key].orEmpty(), logs[key].orEmpty()) }, errors)
}
