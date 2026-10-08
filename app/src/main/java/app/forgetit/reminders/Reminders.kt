package app.forgetit.reminders

import app.forgetit.AppContainer
import app.forgetit.data.ReminderLogEntity
import app.forgetit.data.SnoozeEntity
import app.forgetit.domain.ReminderKind
import app.forgetit.domain.ReminderPlanner
import app.forgetit.domain.ReminderSpec
import app.forgetit.domain.buildDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZonedDateTime

/** Builds the reminder plan from all trackers, keeps alarms in step with it, and shows fired reminders once. */
class Reminders(private val c: AppContainer) {
    private val scheduler = ReminderScheduler(c.context)
    private val mutex = Mutex()

    private suspend fun plan(now: ZonedDateTime): List<ReminderSpec> {
        val s = c.settings.flow.first()
        val minute = s.reminderMinuteOfDay
        val notified = c.db.reminderLogDao().allKeys().toSet()
        val subs = c.subscriptions.getAll()
        val loans = c.loans.getLoans(); val adj = c.loans.getAdjustments(); val pay = c.loans.getPayments()
        val bills = c.bills.getBills(); val entries = c.bills.getEntries()
        val items = c.stock.getItems(); val batches = c.stock.getBatches(); val logs = c.stock.getLogs()

        val specs = ReminderPlanner.subscriptions(subs, now, minute, notified) +
            ReminderPlanner.loans(loans, adj, pay, now, minute, notified) +
            ReminderPlanner.bills(bills, entries, now, minute, notified) +
            ReminderPlanner.stock(items, batches, logs, now, minute, notified)
        val digest = if (s.weeklyDigest) {
            ReminderPlanner.digest(now, minute, notified, buildDigest(subs, loans, adj, pay, bills, entries, items, batches, logs, now.toLocalDate()))
        } else emptyList()
        val snoozed = c.db.snoozeDao().all().filter { it.snoozeKey !in notified }.map {
            val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.triggerAtMillis), now.zone)
            ReminderSpec(it.snoozeKey, ReminderKind.valueOf(it.kind), 0, at.toLocalDate(), if (at.isBefore(now)) now.plusMinutes(1) else at, it.title, it.text)
        }
        return specs + digest + snoozed
    }

    suspend fun sync() {
        mutex.withLock { scheduler.sync(plan(ZonedDateTime.now(c.clock))) }
        app.forgetit.widget.refreshWidgets(c.context)
    }

    /** Called by the alarm receiver. Shows the notification only if the key is still planned and not yet shown. */
    suspend fun fire(key: String) = mutex.withLock {
        val now = ZonedDateTime.now(c.clock)
        var specs = plan(now)
        val spec = ReminderPlanner.find(specs, key)
        if (spec != null && !spec.triggerAt.isAfter(now.plusMinutes(2))) {
            if (Notifications.show(c.context, spec, c.settings.flow.first().paydayDay)) {
                c.db.reminderLogDao().insert(ReminderLogEntity(key, System.currentTimeMillis()))
                specs = plan(now)
            } else {
                // Notifications are blocked: keep the reminder unshown, and do not re-arm it (no retry loop).
                specs = specs.filter { it.key != key }
            }
        }
        scheduler.sync(specs)
    }

    /** Push a reminder back: it is shown again at [until]. */
    suspend fun snooze(kind: ReminderKind, title: String, text: String, until: ZonedDateTime) {
        val key = "SNOOZE:${kind.name}:${until.toEpochSecond()}:${title.hashCode()}"
        c.db.snoozeDao().insert(SnoozeEntity(key, kind.name, title, text, until.toInstant().toEpochMilli()))
        c.db.snoozeDao().deleteOlderThan(System.currentTimeMillis() - 3L * 24 * 3600 * 1000)
        sync()
    }

    fun sendTest() {
        val now = ZonedDateTime.now(c.clock)
        Notifications.show(
            c.context,
            ReminderSpec("TEST:0:${now.toLocalDate()}", ReminderKind.RENEWAL, 0, now.toLocalDate(), now, "Test reminder", "Reminders are working."),
            0,
        )
    }
}
