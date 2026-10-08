package app.forgetit.domain

import java.time.LocalDate

data class CsvImport<T>(val items: List<T>, val errors: List<String>)

object SubscriptionCsv {
    val HEADER = listOf(
        "name", "amount", "currency", "cycle", "custom_days", "start_date", "category", "notes",
        "cancel_url", "payment_method", "is_trial", "trial_ends_at", "remind_days_before", "active", "extra_remind",
    )

    fun export(subs: List<Subscription>): String = (listOf(Csv.row(HEADER)) + subs.map { s ->
        Csv.row(listOf(
            Csv.guard(s.name), Money.toPlain(s.amountMinor, s.currency), s.currency, s.cycle.name,
            s.customDays?.toString().orEmpty(), s.startDate.toString(), s.category, Csv.guard(s.notes),
            s.cancelUrl.orEmpty(), Csv.guard(s.paymentMethod), s.isTrial.toString(), s.trialEndsAt?.toString().orEmpty(),
            s.remindDaysBefore.toString(), s.active.toString(), Offsets.format(s.extraRemindDays, (59).toChar()),
        ))
    }).joinToString("\r\n") + "\r\n"

    fun import(text: String): CsvImport<Subscription> {
        val rows = Csv.parse(text)
        if (rows.isEmpty()) return CsvImport(emptyList(), listOf("The file is empty"))
        val index = rows[0].mapIndexed { i, h -> h.trim().lowercase() to i }.toMap()
        val missing = listOf("name", "amount", "currency", "cycle", "start_date").filter { it !in index }
        if (missing.isNotEmpty()) return CsvImport(emptyList(), listOf("Missing columns: ${missing.joinToString()}"))

        val items = mutableListOf<Subscription>()
        val errors = mutableListOf<String>()
        for ((n, r) in rows.drop(1).withIndex()) {
            val line = n + 2
            fun cell(name: String) = index[name]?.let { r.getOrNull(it) }?.trim().orEmpty()
            val currency = cell("currency").uppercase()
            val amount = Money.parseMinor(cell("amount"), currency)
            val cycle = runCatching { Cycle.valueOf(cell("cycle").uppercase()) }.getOrNull()
            val start = runCatching { LocalDate.parse(cell("start_date")) }.getOrNull()
            val trialText = cell("trial_ends_at")
            val trialEnds = trialText.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val problem = when {
                amount == null -> "invalid amount"
                cycle == null -> "unknown cycle"
                start == null -> "invalid start_date"
                trialText.isNotEmpty() && trialEnds == null -> "invalid trial_ends_at"
                else -> null
            }
            if (problem != null) { errors += "Row $line: $problem"; continue }
            val sub = Subscription(
                name = Csv.unguard(cell("name")), amountMinor = amount!!, currency = currency, cycle = cycle!!,
                customDays = cell("custom_days").toIntOrNull(), startDate = start!!,
                category = cell("category").ifEmpty { "Other" }, notes = Csv.unguard(cell("notes")),
                cancelUrl = cell("cancel_url").ifEmpty { null }, paymentMethod = Csv.unguard(cell("payment_method")),
                isTrial = cell("is_trial").equals("true", ignoreCase = true), trialEndsAt = trialEnds,
                remindDaysBefore = cell("remind_days_before").toIntOrNull() ?: 2,
                extraRemindDays = Offsets.parse(cell("extra_remind"), (59).toChar()),
                active = !cell("active").equals("false", ignoreCase = true),
            )
            val invalid = Validator.validate(sub)
            if (invalid.isEmpty()) items += sub else errors += "Row $line: ${invalid.first().message}"
        }
        return CsvImport(items, errors)
    }
}
