package app.forgetit.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

enum class ReminderKind { RENEWAL, TRIAL_END, EMI_DUE, LOW_STOCK, EXPIRY, BILL_DUE, DIGEST }

/** One notification that should be shown at [triggerAt]. [key] is unique per kind, record, event date and lead time. */
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
    /** The item main lead time keeps the plain key; extra lead times add a suffix, so older keys stay valid. */
    fun key(kind: ReminderKind, entityId: Long, eventDate: LocalDate, offset: Int? = null) =
        "${kind.name}:$entityId:$eventDate" + (offset?.let { "#d$it" } ?: "")

    /**
     * When to remind for an event: [leadDays] before it at [minuteOfDay]. A moment already in the past
     * becomes now + 1 minute, so a reminder that was missed still shows while the event is ahead.
     */
    fun triggerFor(eventDate: LocalDate, leadDays: Int, now: ZonedDateTime, minuteOfDay: Int): ZonedDateTime {
        val at = eventDate.minusDays(leadDays.toLong()).atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(now.zone)
        return if (at.isBefore(now)) now.plusMinutes(1) else at
    }

    private fun rawTrigger(eventDate: LocalDate, lead: Int, now: ZonedDateTime, minuteOfDay: Int) =
        eventDate.minusDays(lead.toLong()).atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(now.zone)

    /**
     * Reminders for one event with several lead times, or null when the event is finished (its closest reminder was shown).
     * Lead times already in the past are skipped quietly; if all are past, the closest one fires a minute from now.
     */
    internal fun forEvent(
        kind: ReminderKind, entityId: Long, date: LocalDate, primary: Int, extras: List<Int>,
        now: ZonedDateTime, minuteOfDay: Int, notified: Set<String>, title: String, text: String,
    ): List<ReminderSpec>? {
        val offsets = (listOf(primary) + extras).distinct().sortedDescending()
        val closest = offsets.last()
        fun k(off: Int) = key(kind, entityId, date, if (off == primary) null else off)
        if (k(closest) in notified) return null
        val future = offsets.filter { k(it) !in notified && !rawTrigger(date, it, now, minuteOfDay).isBefore(now) }
        return (future.ifEmpty { listOf(closest) }).map { off ->
            ReminderSpec(k(off), kind, entityId, date, triggerFor(date, off, now, minuteOfDay), title, text)
        }
    }

    /** Pending reminders for each active subscription: the first charge whose closest reminder is not yet shown. */
    fun subscriptions(subs: List<Subscription>, now: ZonedDateTime, minuteOfDay: Int, notified: Set<String>): List<ReminderSpec> {
        val today = now.toLocalDate()
        val out = mutableListOf<ReminderSpec>()
        for (s in subs) {
            if (!s.active) continue
            var date = Renewal.next(s, today)
            for (attempt in 0 until 3) {
                val trial = s.isTrialActive(today) && date == s.trialEndsAt
                val specs = forEvent(
                    if (trial) ReminderKind.TRIAL_END else ReminderKind.RENEWAL, s.id, date, s.remindDaysBefore, s.extraRemindDays,
                    now, minuteOfDay, notified,
                    title = if (trial) "Free trial ending: ${s.name}" else "${s.name} renews soon",
                    text = "${Money.format(s.amountMinor, s.currency)} will be charged on $date",
                )
                if (specs == null) date = Renewal.next(s, date.plusDays(1)) else { out += specs; break }
            }
        }
        return out
    }

    /** One pending event per active loan: the first unpaid installment (overdue ones included) not yet finished. */
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
            for (row in summary.rows.filter { it.status != RowStatus.PAID }.take(4)) {
                val overdue = row.dueDate.isBefore(today)
                val specs = forEvent(
                    ReminderKind.EMI_DUE, loan.id, row.dueDate, loan.remindDaysBefore, loan.extraRemindDays, now, minuteOfDay, notified,
                    title = if (overdue) "EMI overdue: ${loan.name}" else "EMI due soon: ${loan.name}",
                    text = "${Money.format(row.paymentMinor, loan.currency)} due on ${row.dueDate}",
                )
                if (specs != null) { out += specs; break }
            }
        }
        return out
    }

    /** Bills: the pending due date of each active bill, with the usual amount when there is history. */
    fun bills(bills: List<Bill>, entries: List<BillEntry>, now: ZonedDateTime, minuteOfDay: Int, notified: Set<String>): List<ReminderSpec> {
        val today = now.toLocalDate()
        val out = mutableListOf<ReminderSpec>()
        for (b in bills) {
            if (!b.active) continue
            val mine = entries.filter { it.billId == b.id }
            val usual = BillMath.average(mine)
            for (date in BillMath.recentAndNext(b, today).filter { d -> mine.none { it.dueDate == d && it.paidOn != null } }) {
                val overdue = date.isBefore(today)
                val specs = forEvent(
                    ReminderKind.BILL_DUE, b.id, date, b.remindDaysBefore, b.extraRemindDays, now, minuteOfDay, notified,
                    title = if (overdue) "${b.name} bill overdue" else "${b.name} bill due soon",
                    text = (usual?.let { "Usually ${Money.format(it, b.currency)}, due on $date" } ?: "Due on $date"),
                )
                if (specs != null) { out += specs; break }
            }
        }
        return out
    }

    /** A fired alarm may only notify if the key is still part of the current plan (not deleted, paused or done). */
    fun find(specs: List<ReminderSpec>, key: String): ReminderSpec? = specs.firstOrNull { it.key == key }

    /** The weekly summary, every Monday at the reminder time; a Monday whose time has passed waits for the next one. */
    fun digest(now: ZonedDateTime, minuteOfDay: Int, notified: Set<String>, summary: DigestSummary): List<ReminderSpec> {
        var monday: LocalDate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        while (true) {
            val at = monday.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(now.zone)
            if (at.isBefore(now) || key(ReminderKind.DIGEST, 0, monday) in notified) monday = monday.plusWeeks(1)
            else return listOf(ReminderSpec(key(ReminderKind.DIGEST, 0, monday), ReminderKind.DIGEST, 0, monday, at, "Your week ahead", summary.text()))
        }
    }

    /**
     * Low stock: one reminder per episode (an episode starts at the last stock action, so it is keyed by the baseline date),
     * set earlier by the item delivery lead time. Expiry: one reminder per batch that still has quantity.
     */
    fun stock(
        items: List<StockItem>, batches: List<StockBatch>, logs: List<StockLog>,
        now: ZonedDateTime, minuteOfDay: Int, notified: Set<String>,
    ): List<ReminderSpec> {
        val today = now.toLocalDate()
        val out = mutableListOf<ReminderSpec>()
        for (item in items) {
            if (!item.active) continue
            val mine = batches.filter { it.itemId == item.id }
            val myLogs = logs.filter { it.itemId == item.id }
            val st = StockEngine.status(item, mine, myLogs, today)

            val lowKey = key(ReminderKind.LOW_STOCK, item.id, item.baselineDate)
            val hasHistory = mine.isNotEmpty() || myLogs.isNotEmpty()
            if (hasHistory && lowKey !in notified) {
                val crossing = if (st.low) today else StockEngine.crossesThresholdOn(item, st.storedMilli, st.ratePerDayMilli)
                if (crossing != null) {
                    val left = "${Money.milliToPlain(st.estimatedMilli)} ${item.unit} left" + (st.runOut?.let { ", runs out about $it" } ?: "")
                    val lead = if (item.leadDays > 0) " Order ${item.leadDays} day(s) ahead." else ""
                    out += ReminderSpec(
                        lowKey, ReminderKind.LOW_STOCK, item.id, crossing,
                        if (st.low) now.plusMinutes(1) else triggerFor(crossing, item.leadDays, now, minuteOfDay),
                        "Running low: ${item.name}", left + lead,
                    )
                }
            }
            for (b in mine) {
                val expiry = b.expiry ?: continue
                if (b.quantityMilli <= 0) continue
                val k = key(ReminderKind.EXPIRY, b.id, expiry)
                if (k in notified) continue
                out += ReminderSpec(
                    k, ReminderKind.EXPIRY, b.id, expiry, triggerFor(expiry, item.expiryAlertDays, now, minuteOfDay),
                    if (expiry.isBefore(today)) "Expired: ${item.name}" else "${item.name} expires soon",
                    "${Money.milliToPlain(b.quantityMilli)} ${item.unit} expires on $expiry",
                )
            }
        }
        return out
    }
}
