package app.forgetit.domain

import java.time.LocalDate
import java.time.ZonedDateTime

enum class ReminderKind { RENEWAL, TRIAL_END, EMI_DUE, LOW_STOCK, EXPIRY }

/** One notification that should be shown at [triggerAt]. [key] is unique per kind, record and event date. */
data class ReminderSpec(
    val key: String,
    val kind: ReminderKind,
    val entityId: Long,
    val eventDate: LocalDate,
    val triggerAt: ZonedDateTime,
    val title: String,
    val text: String,
)

object ReminderPlanner {
    fun key(kind: ReminderKind, entityId: Long, eventDate: LocalDate) = "${kind.name}:$entityId:$eventDate"

    /**
     * When to remind for an event: [leadDays] before it at [minuteOfDay]. A moment already in the past
     * becomes now + 1 minute, so a reminder that was missed still shows while the event is ahead.
     */
    fun triggerFor(eventDate: LocalDate, leadDays: Int, now: ZonedDateTime, minuteOfDay: Int): ZonedDateTime {
        val at = eventDate.minusDays(leadDays.toLong()).atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(now.zone)
        return if (at.isBefore(now)) now.plusMinutes(1) else at
    }

    /** At most one pending reminder per active subscription: the first charge not yet notified. */
    fun subscriptions(subs: List<Subscription>, now: ZonedDateTime, minuteOfDay: Int, notified: Set<String>): List<ReminderSpec> {
        val today = now.toLocalDate()
        val out = mutableListOf<ReminderSpec>()
        for (s in subs) {
            if (!s.active) continue
            var date = Renewal.next(s, today)
            for (attempt in 0 until 3) {
                val trial = s.isTrialActive(today) && date == s.trialEndsAt
                val kind = if (trial) ReminderKind.TRIAL_END else ReminderKind.RENEWAL
                val key = key(kind, s.id, date)
                if (key in notified) {
                    date = Renewal.next(s, date.plusDays(1))
                } else {
                    val amount = Money.format(s.amountMinor, s.currency)
                    out += ReminderSpec(
                        key, kind, s.id, date, triggerFor(date, s.remindDaysBefore, now, minuteOfDay),
                        title = if (trial) "Free trial ending: ${s.name}" else "${s.name} renews soon",
                        text = "$amount will be charged on $date",
                    )
                    break
                }
            }
        }
        return out
    }

    /** A fired alarm may only notify if the key is still part of the current plan (not deleted, paused or done). */
    fun find(specs: List<ReminderSpec>, key: String): ReminderSpec? = specs.firstOrNull { it.key == key }

    /** One pending reminder per active loan: the first unpaid installment (overdue ones included) not yet notified. */
    fun loans(
        loans: List<Loan>, adjustments: List<LoanAdjustment>, payments: List<LoanPayment>,
        now: ZonedDateTime, minuteOfDay: Int, notified: Set<String>,
    ): List<ReminderSpec> {
        val today = now.toLocalDate()
        val out = mutableListOf<ReminderSpec>()
        for (loan in loans) {
            if (!loan.active) continue
            val summary = Amortization.build(
                loan, adjustments.filter { it.loanId == loan.id }, payments.filter { it.loanId == loan.id }, today,
            )
            val row = summary.rows.filter { it.status != RowStatus.PAID }.take(4)
                .firstOrNull { key(ReminderKind.EMI_DUE, loan.id, it.dueDate) !in notified } ?: continue
            val overdue = row.dueDate.isBefore(today)
            out += ReminderSpec(
                key(ReminderKind.EMI_DUE, loan.id, row.dueDate), ReminderKind.EMI_DUE, loan.id, row.dueDate,
                triggerFor(row.dueDate, loan.remindDaysBefore, now, minuteOfDay),
                title = if (overdue) "EMI overdue: ${loan.name}" else "EMI due soon: ${loan.name}",
                text = "${Money.format(row.paymentMinor, loan.currency)} due on ${row.dueDate}",
            )
        }
        return out
    }
}
