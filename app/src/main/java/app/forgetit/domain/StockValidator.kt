package app.forgetit.domain

object StockValidator {
    const val MAX_MILLI = 1_000_000_000_000L

    fun validate(i: StockItem): List<ValidationError> = buildList {
        if (i.name.isBlank()) add(ValidationError("name", "Enter a name"))
        else if (i.name.length > 60) add(ValidationError("name", "Name is too long (max 60)"))
        if (i.unit.isBlank() || i.unit.length > 12) add(ValidationError("unit", "Enter a unit such as L, kg or pcs"))
        if (i.category !in STOCK_CATEGORIES) add(ValidationError("category", "Unknown category"))
        if (i.lowThresholdMilli !in 0..MAX_MILLI) add(ValidationError("threshold", "Amount is out of range"))
        if (i.dailyUsageMilli != null && i.dailyUsageMilli !in 0..MAX_MILLI) add(ValidationError("usage", "Amount is out of range"))
        if (i.expiryAlertDays !in 0..30) add(ValidationError("expiryAlertDays", "Choose 0 to 30 days"))
    }

    fun validateBatch(quantityMilli: Long): ValidationError? =
        if (quantityMilli <= 0 || quantityMilli > MAX_MILLI) ValidationError("quantity", "Enter an amount above zero") else null
}
