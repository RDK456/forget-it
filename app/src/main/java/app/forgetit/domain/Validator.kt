package app.forgetit.domain

import java.net.URI

data class ValidationError(val field: String, val message: String)

object Validator {
    const val MAX_AMOUNT_MINOR = 100_000_000_000L

    fun validate(s: Subscription): List<ValidationError> = buildList {
        if (s.name.isBlank()) add(ValidationError("name", "Enter a name"))
        else if (s.name.length > 60) add(ValidationError("name", "Name is too long (max 60)"))
        if (s.amountMinor !in 0..MAX_AMOUNT_MINOR) add(ValidationError("amount", "Amount is out of range"))
        if (!Money.isValidCurrency(s.currency)) add(ValidationError("currency", "Unknown currency code"))
        if (s.cycle == Cycle.CUSTOM_DAYS && (s.customDays == null || s.customDays !in 1..3650))
            add(ValidationError("customDays", "Enter 1 to 3650 days"))
        if (s.isTrial && s.trialEndsAt == null) add(ValidationError("trialEndsAt", "Pick the trial end date"))
        if (s.remindDaysBefore !in 0..30) add(ValidationError("remindDaysBefore", "Choose 0 to 30 days"))
        if (s.extraRemindDays.any { it !in 0..30 }) add(ValidationError("extraRemind", "Extra reminders must be 0 to 30 days"))
        if (s.category !in CATEGORIES) add(ValidationError("category", "Unknown category"))
        if (!s.cancelUrl.isNullOrBlank() && !isHttpUrl(s.cancelUrl))
            add(ValidationError("cancelUrl", "Enter a web address starting with http:// or https://"))
    }

    fun isHttpUrl(url: String): Boolean = runCatching {
        val u = URI(url.trim())
        (u.scheme == "http" || u.scheme == "https") && !u.host.isNullOrEmpty()
    }.getOrDefault(false)
}
