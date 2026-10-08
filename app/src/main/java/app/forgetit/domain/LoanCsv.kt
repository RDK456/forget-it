package app.forgetit.domain

import java.math.BigDecimal
import java.time.LocalDate

data class LoanBundle(val loan: Loan, val payments: List<LoanPayment>, val adjustments: List<LoanAdjustment>)

/** One file for everything about loans: LOAN rows, then PAYMENT and ADJUSTMENT rows pointing at a loan_key. */
object LoanCsv {
    val HEADER = listOf(
        "record_type", "loan_key", "name", "lender", "type", "principal", "currency", "rate_percent", "tenure_months",
        "first_emi_date", "emi_override", "remind_days_before", "notes", "active",
        "installment_no", "paid_on", "amount", "adj_date", "adj_kind", "adj_amount",
    )

    private fun line(vararg pairs: Pair<String, String>): String {
        val m = pairs.toMap()
        return Csv.row(HEADER.map { m[it].orEmpty() })
    }

    fun export(loans: List<Loan>, payments: List<LoanPayment>, adjustments: List<LoanAdjustment>): String {
        val out = mutableListOf(Csv.row(HEADER))
        for (l in loans) {
            val key = l.id.toString()
            out += line(
                "record_type" to "LOAN", "loan_key" to key, "name" to Csv.guard(l.name), "lender" to Csv.guard(l.lender),
                "type" to l.type.name, "principal" to Money.toPlain(l.principalMinor, l.currency), "currency" to l.currency,
                "rate_percent" to l.annualRatePercent.toPlainString(), "tenure_months" to l.tenureMonths.toString(),
                "first_emi_date" to l.firstEmiDate.toString(), "emi_override" to (l.emiOverrideMinor?.let { Money.toPlain(it, l.currency) } ?: ""),
                "remind_days_before" to l.remindDaysBefore.toString(), "notes" to Csv.guard(l.notes), "active" to l.active.toString(),
            )
            payments.filter { it.loanId == l.id }.sortedBy { it.installmentNo }.forEach { p ->
                out += line(
                    "record_type" to "PAYMENT", "loan_key" to key, "installment_no" to p.installmentNo.toString(),
                    "paid_on" to p.paidOn.toString(), "amount" to Money.toPlain(p.amountMinor, l.currency),
                )
            }
            adjustments.filter { it.loanId == l.id }.sortedBy { it.date }.forEach { a ->
                out += line(
                    "record_type" to "ADJUSTMENT", "loan_key" to key, "adj_date" to a.date.toString(),
                    "adj_kind" to a.kind.name, "adj_amount" to Money.toPlain(a.amountMinor, l.currency),
                )
            }
        }
        return out.joinToString("\r\n") + "\r\n"
    }

    fun import(text: String): CsvImport<LoanBundle> {
        val rows = Csv.parse(text)
        if (rows.isEmpty()) return CsvImport(emptyList(), listOf("The file is empty"))
        val index = rows[0].mapIndexed { i, h -> h.trim().lowercase() to i }.toMap()
        val missing = listOf("record_type", "loan_key").filter { it !in index }
        if (missing.isNotEmpty()) return CsvImport(emptyList(), listOf("Missing columns: ${missing.joinToString()}"))

        val errors = mutableListOf<String>()
        val loans = linkedMapOf<String, Loan>()
        val payments = mutableMapOf<String, MutableList<LoanPayment>>()
        val adjustments = mutableMapOf<String, MutableList<LoanAdjustment>>()
        val body = rows.drop(1).mapIndexed { n, r -> (n + 2) to r }

        fun cellOf(r: List<String>, name: String) = index[name]?.let { r.getOrNull(it) }?.trim().orEmpty()

        for ((line, r) in body) {
            if (cellOf(r, "record_type").uppercase() != "LOAN") continue
            val key = cellOf(r, "loan_key")
            val cur = cellOf(r, "currency").uppercase()
            val principal = Money.parseMinor(cellOf(r, "principal"), cur)
            val rate = cellOf(r, "rate_percent").replace(',', '.').toBigDecimalOrNull()
            val tenure = cellOf(r, "tenure_months").toIntOrNull()
            val first = runCatching { LocalDate.parse(cellOf(r, "first_emi_date")) }.getOrNull()
            val type = runCatching { LoanType.valueOf(cellOf(r, "type").uppercase()) }.getOrNull() ?: LoanType.OTHER
            val overrideText = cellOf(r, "emi_override")
            val override = if (overrideText.isEmpty()) null else Money.parseMinor(overrideText, cur)
            val problem = when {
                key.isEmpty() -> "missing loan_key"
                principal == null -> "invalid principal"
                rate == null -> "invalid rate_percent"
                tenure == null -> "invalid tenure_months"
                first == null -> "invalid first_emi_date"
                overrideText.isNotEmpty() && override == null -> "invalid emi_override"
                else -> null
            }
            if (problem != null) { errors += "Row $line: $problem"; continue }
            val loan = Loan(
                name = Csv.unguard(cellOf(r, "name")), lender = Csv.unguard(cellOf(r, "lender")), type = type,
                principalMinor = principal!!, currency = cur, annualRatePercent = rate!!, tenureMonths = tenure!!,
                firstEmiDate = first!!, emiOverrideMinor = override, remindDaysBefore = cellOf(r, "remind_days_before").toIntOrNull() ?: 2,
                notes = Csv.unguard(cellOf(r, "notes")), active = !cellOf(r, "active").equals("false", ignoreCase = true),
            )
            val invalid = Amortization.validate(loan)
            if (invalid.isEmpty()) loans[key] = loan else errors += "Row $line: ${invalid.first().message}"
        }
        for ((line, r) in body) {
            val kind = cellOf(r, "record_type").uppercase()
            if (kind != "PAYMENT" && kind != "ADJUSTMENT") continue
            val key = cellOf(r, "loan_key")
            val loan = loans[key]
            if (loan == null) { errors += "Row $line: no valid loan with loan_key $key"; continue }
            if (kind == "PAYMENT") {
                val no = cellOf(r, "installment_no").toIntOrNull()
                val on = runCatching { LocalDate.parse(cellOf(r, "paid_on")) }.getOrNull()
                val amount = Money.parseMinor(cellOf(r, "amount"), loan.currency)
                if (no == null || no < 1 || on == null || amount == null) { errors += "Row $line: invalid payment"; continue }
                payments.getOrPut(key) { mutableListOf() } += LoanPayment(loanId = 0, installmentNo = no, paidOn = on, amountMinor = amount)
            } else {
                val on = runCatching { LocalDate.parse(cellOf(r, "adj_date")) }.getOrNull()
                val k = runCatching { AdjustmentKind.valueOf(cellOf(r, "adj_kind").uppercase()) }.getOrNull()
                val amount = Money.parseMinor(cellOf(r, "adj_amount"), loan.currency)
                if (on == null || k == null || amount == null) { errors += "Row $line: invalid adjustment"; continue }
                adjustments.getOrPut(key) { mutableListOf() } += LoanAdjustment(loanId = 0, date = on, kind = k, amountMinor = amount)
            }
        }
        return CsvImport(loans.map { (key, l) -> LoanBundle(l, payments[key].orEmpty(), adjustments[key].orEmpty()) }, errors)
    }
}
