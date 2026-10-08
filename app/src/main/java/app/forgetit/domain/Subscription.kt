package app.forgetit.domain

import java.time.LocalDate

enum class Cycle { WEEKLY, MONTHLY, QUARTERLY, YEARLY, CUSTOM_DAYS }

val CATEGORIES = listOf("Streaming", "Music", "Software", "Gaming", "News", "Cloud", "Fitness", "Utilities", "Other")

data class Subscription(
    val id: Long = 0,
    val name: String,
    val amountMinor: Long,
    val currency: String,
    val cycle: Cycle,
    val customDays: Int? = null,
    val startDate: LocalDate,
    val category: String = "Other",
    val notes: String = "",
    val cancelUrl: String? = null,
    val presetKey: String? = null,
    val paymentMethod: String = "",
    val isTrial: Boolean = false,
    val trialEndsAt: LocalDate? = null,
    val remindDaysBefore: Int = 2,
    val active: Boolean = true,
)

/** The date billing cycles count from: the trial end for trials (and after them), else the start date. */
internal val Subscription.anchor: LocalDate
    get() = if (isTrial && trialEndsAt != null) trialEndsAt else startDate

fun Subscription.isTrialActive(today: LocalDate): Boolean =
    isTrial && trialEndsAt != null && !today.isAfter(trialEndsAt)

/** After the trial end date has passed, turn it into a regular subscription anchored at that date. */
fun Subscription.settleTrial(today: LocalDate): Subscription =
    if (isTrial && trialEndsAt != null && today.isAfter(trialEndsAt)) {
        copy(isTrial = false, startDate = trialEndsAt, trialEndsAt = null)
    } else this
