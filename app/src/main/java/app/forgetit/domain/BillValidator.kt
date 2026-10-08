package app.forgetit.domain

object BillValidator {
    fun validate(b: Bill): List<ValidationError> = buildList {
        if (b.name.isBlank()) add(ValidationError("name", "Enter a name"))
        else if (b.name.length > 60) add(ValidationError("name", "Name is too long (max 60)"))
        if (!Money.isValidCurrency(b.currency)) add(ValidationError("currency", "Unknown currency code"))
        if (b.cycle == Cycle.CUSTOM_DAYS && (b.customDays == null || b.customDays !in 1..3650)) add(ValidationError("customDays", "Enter 1 to 3650 days"))
        if (b.remindDaysBefore !in 0..30) add(ValidationError("remindDaysBefore", "Choose 0 to 30 days"))
        if (b.extraRemindDays.any { it !in 0..30 }) add(ValidationError("extraRemind", "Extra reminders must be 0 to 30 days"))
    }
}

data class BillBundle(val bill: Bill, val entries: List<BillEntry>)
