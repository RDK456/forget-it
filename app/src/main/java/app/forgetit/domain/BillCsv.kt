package app.forgetit.domain

import java.time.LocalDate

/** One file for bills: BILL rows, then ENTRY rows (the recorded amounts) pointing at a bill_key. */
object BillCsv {
    val HEADER = listOf(
        "record_type", "bill_key", "name", "type", "currency", "cycle", "custom_days", "anchor_date", "remind_days_before",
        "extra_remind", "notes", "active", "due_date", "amount", "paid_on",
    )

    private fun line(vararg pairs: Pair<String, String>): String {
        val m = pairs.toMap()
        return Csv.row(HEADER.map { m[it].orEmpty() })
    }

    fun export(bills: List<Bill>, entries: List<BillEntry>): String {
        val out = mutableListOf(Csv.row(HEADER))
        for (b in bills) {
            val key = b.id.toString()
            out += line(
                "record_type" to "BILL", "bill_key" to key, "name" to Csv.guard(b.name), "type" to b.type.name, "currency" to b.currency,
                "cycle" to b.cycle.name, "custom_days" to (b.customDays?.toString() ?: ""), "anchor_date" to b.anchorDate.toString(),
                "remind_days_before" to b.remindDaysBefore.toString(), "extra_remind" to Offsets.format(b.extraRemindDays, (59).toChar()),
                "notes" to Csv.guard(b.notes), "active" to b.active.toString(),
            )
            entries.filter { it.billId == b.id }.sortedBy { it.dueDate }.forEach { e ->
                out += line(
                    "record_type" to "ENTRY", "bill_key" to key, "due_date" to e.dueDate.toString(),
                    "amount" to Money.toPlain(e.amountMinor, b.currency), "paid_on" to (e.paidOn?.toString() ?: ""),
                )
            }
        }
        return out.joinToString("\r\n") + "\r\n"
    }
}
