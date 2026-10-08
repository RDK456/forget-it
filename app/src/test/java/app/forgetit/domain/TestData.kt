package app.forgetit.domain

import java.time.LocalDate

fun d(s: String): LocalDate = LocalDate.parse(s)

fun sub(
    cycle: Cycle = Cycle.MONTHLY,
    start: String = "2026-01-01",
    amount: Long = 1000,
    currency: String = "USD",
    customDays: Int? = null,
    trialEnds: String? = null,
    remind: Int = 2,
    active: Boolean = true,
    id: Long = 1,
) = Subscription(
    id = id, name = "Test", amountMinor = amount, currency = currency, cycle = cycle,
    customDays = customDays, startDate = d(start),
    isTrial = trialEnds != null, trialEndsAt = trialEnds?.let(::d),
    remindDaysBefore = remind, active = active,
)
