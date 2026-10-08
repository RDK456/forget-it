package app.forgetit.domain

import java.time.LocalDate

/** Reads a file written by [BillCsv.export]. Bad rows are reported and skipped; entries of a rejected bill are rejected too. */
fun BillCsv.import(text: String): CsvImport<BillBundle> {
    val rows = Csv.parse(text)
    if (rows.isEmpty()) return CsvImport(emptyList(), listOf("The file is empty"))
    val index = rows[0].mapIndexed { i, h -> h.trim().lowercase() to i }.toMap()
    val missing = listOf("record_type", "bill_key").filter { it !in index }
    if (missing.isNotEmpty()) return CsvImport(emptyList(), listOf("Missing columns: ${missing.joinToString()}"))

    fun cell(r: List<String>, name: String) = index[name]?.let { r.getOrNull(it) }?.trim().orEmpty()
    val errors = mutableListOf<String>()
    val bills = linkedMapOf<String, Bill>()
    val entries = mutableMapOf<String, MutableList<BillEntry>>()
    val body = rows.drop(1).mapIndexed { n, r -> (n + 2) to r }

    for ((line, r) in body) {
        if (cell(r, "record_type").uppercase() != "BILL") continue
        val key = cell(r, "bill_key")
        val anchor = runCatching { LocalDate.parse(cell(r, "anchor_date")) }.getOrNull()
        val cycle = runCatching { Cycle.valueOf(cell(r, "cycle").uppercase()) }.getOrNull() ?: Cycle.MONTHLY
        val type = runCatching { BillType.valueOf(cell(r, "type").uppercase()) }.getOrNull() ?: BillType.OTHER
        if (key.isEmpty() || anchor == null) {
            errors += "Row $line: ${if (key.isEmpty()) "missing bill_key" else "invalid anchor_date"}"; continue
        }
        val bill = Bill(
            name = Csv.unguard(cell(r, "name")), type = type, currency = cell(r, "currency").uppercase(), cycle = cycle,
            customDays = cell(r, "custom_days").toIntOrNull(), anchorDate = anchor,
            remindDaysBefore = cell(r, "remind_days_before").toIntOrNull() ?: 3,
            extraRemindDays = Offsets.parse(cell(r, "extra_remind"), (59).toChar()),
            notes = Csv.unguard(cell(r, "notes")), active = !cell(r, "active").equals("false", ignoreCase = true),
        )
        val invalid = BillValidator.validate(bill)
        if (invalid.isEmpty()) bills[key] = bill else errors += "Row $line: ${invalid.first().message}"
    }
    for ((line, r) in body) {
        if (cell(r, "record_type").uppercase() != "ENTRY") continue
        val key = cell(r, "bill_key")
        val bill = bills[key]
        if (bill == null) { errors += "Row $line: no valid bill with bill_key $key"; continue }
        val due = runCatching { LocalDate.parse(cell(r, "due_date")) }.getOrNull()
        val amount = Money.parseMinor(cell(r, "amount"), bill.currency)
        val paidText = cell(r, "paid_on")
        val paid = if (paidText.isEmpty()) null else runCatching { LocalDate.parse(paidText) }.getOrNull()
        if (due == null || amount == null || (paidText.isNotEmpty() && paid == null)) { errors += "Row $line: invalid entry"; continue }
        entries.getOrPut(key) { mutableListOf() } += BillEntry(billId = 0, dueDate = due, amountMinor = amount, paidOn = paid)
    }
    return CsvImport(bills.map { (key, b) -> BillBundle(b, entries[key].orEmpty()) }, errors)
}
