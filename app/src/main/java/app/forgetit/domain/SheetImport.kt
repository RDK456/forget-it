package app.forgetit.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

data class SheetImportResult(val items: List<Detected>, val skipped: List<String>, val rows: Int)

/**
 * Turns spreadsheet rows (Excel or CSV) into records for the right trackers. Columns are found by their header names
 * ("Name", "Amount", "Due date", "Qty"...) in any order, and the tracker comes from a Type column, the sheet name, or the headers.
 */
object SheetImport {
    private enum class F { NAME, AMOUNT, PRINCIPAL, CURRENCY, DATE, CYCLE, CATEGORY, TYPE, BILLTYPE, QTY, UNIT, TENURE, RATE }

    private val ALIASES: Map<F, List<String>> = mapOf(
        F.NAME to listOf("name", "title", "item", "service", "description", "merchant", "lender", "bill", "subscription", "loan", "product"),
        F.AMOUNT to listOf("amount", "price", "cost", "emi", "payment", "total", "paid", "monthly amount", "emi amount", "usual amount", "installment", "instalment"),
        F.PRINCIPAL to listOf("principal", "loan amount", "loan principal"),
        F.CURRENCY to listOf("currency", "cur"),
        F.DATE to listOf("date", "due", "due date", "next date", "next due", "start", "start date", "renewal", "renews", "first emi", "first emi date", "next billing", "billing date", "renewal date"),
        F.CYCLE to listOf("cycle", "repeats", "frequency", "billing cycle", "period", "interval"),
        F.CATEGORY to listOf("category", "group"),
        F.TYPE to listOf("type", "kind", "tracker"),
        F.BILLTYPE to listOf("bill type", "utility"),
        F.QTY to listOf("quantity", "qty", "count", "stock", "units"),
        F.UNIT to listOf("unit", "uom"),
        F.TENURE to listOf("tenure", "months", "term", "tenure months", "remaining months"),
        F.RATE to listOf("rate", "interest", "interest rate", "roi", "annual rate"),
    )

    private val IC = RegexOption.IGNORE_CASE
    private const val MAX_ROWS = 1000
    private val SKIP_SHEET = Regex("""read ?me|how to|instruction|about""", IC)

    private fun normalise(h: String) = h.lowercase(Locale.ROOT).replace(Regex("""\(.*?\)"""), " ").replace("%", " ")
        .replace(Regex("""[^a-z ]"""), " ").replace(Regex("""\s+"""), " ").trim()

    private fun mapHeaders(row: List<String>): Map<F, Int> {
        val out = mutableMapOf<F, Int>()
        row.forEachIndexed { i, h ->
            val n = normalise(h)
            ALIASES.entries.firstOrNull { n in it.value }?.key?.let { f -> if (f !in out) out[f] = i }
        }
        return out
    }

    private fun parseMoney(raw: String, currency: String): Long? {
        var t = raw.replace(Regex("""[^0-9.,]"""), "")
        if (t.isEmpty()) return null
        t = when {
            ',' in t && '.' in t -> if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            ',' in t -> if (Regex(""",\d{1,2}$""").containsMatchIn(t)) t.replace(',', '.') else t.replace(",", "")
            else -> t
        }
        val v = runCatching { BigDecimal(t).setScale(Money.fractionDigits(currency), RoundingMode.HALF_UP) }.getOrNull() ?: return null
        return v.unscaledValue().toLong().takeIf { it > 0 }
    }

    private fun parseDate(raw: String): LocalDate? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (Regex("""\d{5}(\.\d+)?""").matches(t)) t.toDouble().toLong().takeIf { it in 20_000..80_000 }?.let { return LocalDate.of(1899, 12, 30).plusDays(it) }
        return DocScan.datesIn(t).firstOrNull()
    }

    private fun parseCycle(raw: String): Cycle = when {
        Regex("""week""", IC).containsMatchIn(raw) -> Cycle.WEEKLY
        Regex("""quarter""", IC).containsMatchIn(raw) -> Cycle.QUARTERLY
        Regex("""year|annual""", IC).containsMatchIn(raw) -> Cycle.YEARLY
        else -> Cycle.MONTHLY
    }

    private fun kindOf(typeCell: String, sheetName: String, fields: Set<F>): DocKind {
        fun byWord(s: String): DocKind? = when {
            Regex("""sub""", IC).containsMatchIn(s) -> DocKind.SUBSCRIPTION
            Regex("""emi|loan""", IC).containsMatchIn(s) -> DocKind.EMI
            Regex("""bill|utilit""", IC).containsMatchIn(s) -> DocKind.BILL
            Regex("""stock|grocer|household|inventory|item""", IC).containsMatchIn(s) -> DocKind.GROCERY
            Regex("""payment|transaction|expense""", IC).containsMatchIn(s) -> DocKind.PAYMENT
            else -> null
        }
        return byWord(typeCell) ?: byWord(sheetName) ?: when {
            F.QTY in fields -> DocKind.GROCERY
            F.TENURE in fields || F.PRINCIPAL in fields -> DocKind.EMI
            F.BILLTYPE in fields -> DocKind.BILL
            else -> DocKind.SUBSCRIPTION
        }
    }

    fun parse(sheets: List<Sheet>, defaultCurrency: String, today: LocalDate): SheetImportResult {
        val items = mutableListOf<Detected>()
        val groceries = mutableListOf<ScannedItem>()
        val skipped = mutableListOf<String>()
        var rowCount = 0
        for (sheet in sheets) {
            if (SKIP_SHEET.containsMatchIn(sheet.name)) continue
            val headerIdx = sheet.rows.take(5).indexOfFirst { mapHeaders(it).size >= 2 }
            if (headerIdx < 0) {
                if (sheet.rows.isNotEmpty()) skipped += "Sheet \"${sheet.name}\": no header row with names such as Name and Amount"
                continue
            }
            val cols = mapHeaders(sheet.rows[headerIdx])
            for ((i, row) in sheet.rows.drop(headerIdx + 1).withIndex()) {
                val where = "\"${sheet.name}\" row ${headerIdx + i + 2}"
                fun cell(f: F) = cols[f]?.let { row.getOrNull(it) }?.trim().orEmpty()
                if (row.all { it.isBlank() }) continue
                if (++rowCount > MAX_ROWS) { skipped += "Only the first $MAX_ROWS rows were read"; break }
                val name = cell(F.NAME)
                if (name.isBlank()) { skipped += "$where: no name"; continue }
                val kind = kindOf(cell(F.TYPE), sheet.name, cols.keys)
                val currency = cell(F.CURRENCY).uppercase(Locale.ROOT).takeIf { Money.isValidCurrency(it) } ?: defaultCurrency
                val amount = parseMoney(cell(F.AMOUNT), currency)
                val date = parseDate(cell(F.DATE))
                when (kind) {
                    DocKind.SUBSCRIPTION -> {
                        if (amount == null) { skipped += "$where: $name has no amount"; continue }
                        val category = CATEGORIES.firstOrNull { it.equals(cell(F.CATEGORY), true) } ?: PRESETS.firstOrNull { it.name.equals(name, true) }?.category ?: "Other"
                        items += Detected(DocKind.SUBSCRIPTION, name.take(60), amount, currency, date, parseCycle(cell(F.CYCLE)), category)
                    }
                    DocKind.EMI -> {
                        if (amount == null) { skipped += "$where: $name has no EMI amount"; continue }
                        items += Detected(
                            DocKind.EMI, name.take(60), amount, currency, date, tenureMonths = cell(F.TENURE).toDoubleOrNull()?.toInt()?.takeIf { it in 1..600 },
                            ratePercent = cell(F.RATE).replace(',', '.').toBigDecimalOrNull(), principalMinor = parseMoney(cell(F.PRINCIPAL), currency),
                        )
                    }
                    DocKind.BILL -> items += Detected(
                        DocKind.BILL, name.take(60), amount, currency, date, parseCycle(cell(F.CYCLE)),
                        billType = DocScan.billTypeOf(cell(F.BILLTYPE) + " " + name + " " + cell(F.CATEGORY)),
                    )
                    DocKind.GROCERY -> {
                        val qty = cell(F.QTY).replace(',', '.').toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
                        val category = STOCK_CATEGORIES.firstOrNull { it.equals(cell(F.CATEGORY), true) } ?: GroceryScan.categoryOf(name)
                        groceries += ScannedItem(
                            name.take(60), qty.movePointRight(3).toLong(), cell(F.UNIT).ifBlank { GroceryScan.defaultUnit(name) }.take(12), category, amount,
                        )
                    }
                    DocKind.PAYMENT -> {
                        if (amount == null) { skipped += "$where: $name has no amount"; continue }
                        items += Detected(DocKind.PAYMENT, name.take(60), amount, currency, date ?: today)
                    }
                }
            }
        }
        if (groceries.isNotEmpty()) items += Detected(DocKind.GROCERY, "Groceries", null, defaultCurrency, items = groceries)
        return SheetImportResult(items, skipped.take(20), rowCount)
    }

    /** Reads an uploaded file: .xlsx, or plain CSV text. Older .xls files are refused with advice. */
    fun fromBytes(bytes: ByteArray, fileName: String, defaultCurrency: String, today: LocalDate): SheetImportResult {
        val sheets = when {
            bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> Xlsx.read(bytes)
            bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() ->
                throw IllegalArgumentException("Old .xls files are not supported. In Excel choose Save As, then Excel Workbook (.xlsx).")
            else -> listOf(Sheet(fileName.substringBeforeLast('.').ifBlank { "Sheet" }, Csv.parse(String(bytes, Charsets.UTF_8).removePrefix("﻿"))))
        }
        return parse(sheets, defaultCurrency, today)
    }
}

/** Builds the sheets for "Export to Excel" and for the blank template. The importer reads these same headers back. */
object SheetExport {
    private fun cycleText(c: Cycle, days: Int?) = if (c == Cycle.CUSTOM_DAYS) "Every ${days ?: 30} days" else c.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
    private fun plain(minor: Long, currency: String) = Money.toPlain(minor, currency)

    private val HELP = Sheet(
        "How to use",
        listOf(
            listOf("Forget-it spreadsheet"),
            listOf("Each sheet is one tracker: Subscriptions, Loans, Bills, Stock."),
            listOf("Keep the header row. Add one row per record. Columns can be in any order and extra columns are ignored."),
            listOf("Dates as 2026-11-05 or as Excel dates. Amounts as plain numbers. Cycle: Weekly, Monthly, Quarterly or Yearly."),
            listOf("Import it from Forget-it: Settings, Backup, Excel, Import. You review everything before it is added."),
        ),
    )

    fun workbook(
        subs: List<Subscription>, loans: List<Loan>, bills: List<Bill>, billEntries: List<BillEntry>,
        items: List<StockItem>, batches: List<StockBatch>, today: LocalDate, template: Boolean,
    ): List<Sheet> {
        val subRows = if (template && subs.isEmpty()) listOf(listOf("Netflix", "15.49", "USD", "Monthly", "2026-11-12", "Streaming", ""))
        else subs.map { listOf(it.name, plain(it.amountMinor, it.currency), it.currency, cycleText(it.cycle, it.customDays), it.startDate.toString(), it.category, it.notes) }
        val loanRows = if (template && loans.isEmpty()) listOf(listOf("Car loan", "HDFC Bank", "500000", "9.5", "60", "2026-11-05", "10500", "INR"))
        else loans.map {
            listOf(it.name, it.lender, plain(it.principalMinor, it.currency), it.annualRatePercent.toPlainString(), it.tenureMonths.toString(), it.firstEmiDate.toString(), plain(it.emiOverrideMinor ?: Amortization.baseEmi(it), it.currency), it.currency)
        }
        val billRows = if (template && bills.isEmpty()) listOf(listOf("Electricity", "Electricity", "84.20", "USD", "Monthly", "2026-11-15"))
        else bills.map { b ->
            val mine = billEntries.filter { it.billId == b.id }
            listOf(b.name, b.type.name.lowercase(Locale.ROOT).replace('_', ' '), BillMath.average(mine)?.let { plain(it, b.currency) }.orEmpty(), b.currency, cycleText(b.cycle, b.customDays), BillMath.pendingDue(b, mine, today)?.toString().orEmpty())
        }
        val stockRows = if (template && items.isEmpty()) listOf(listOf("Milk", "2", "L", "Dairy"))
        else items.map { i ->
            val qty = batches.filter { it.itemId == i.id }.sumOf { it.quantityMilli }
            listOf(i.name, BigDecimal(qty).movePointLeft(3).stripTrailingZeros().toPlainString(), i.unit, i.category)
        }
        return listOf(
            HELP,
            Sheet("Subscriptions", listOf(listOf("Name", "Amount", "Currency", "Cycle", "Start date", "Category", "Notes")) + subRows),
            Sheet("Loans", listOf(listOf("Name", "Lender", "Principal", "Rate %", "Tenure (months)", "First EMI date", "EMI", "Currency")) + loanRows),
            Sheet("Bills", listOf(listOf("Name", "Bill type", "Usual amount", "Currency", "Cycle", "Next due")) + billRows),
            Sheet("Stock", listOf(listOf("Name", "Quantity", "Unit", "Category")) + stockRows),
        )
    }
}
