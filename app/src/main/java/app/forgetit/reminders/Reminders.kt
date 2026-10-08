package app.forgetit.reminders

import app.forgetit.AppContainer
import app.forgetit.data.ReminderLogEntity
import app.forgetit.domain.ReminderKind
import app.forgetit.domain.ReminderPlanner
import app.forgetit.domain.ReminderSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

/** Builds the reminder plan from all trackers, keeps alarms in step with it, and shows fired reminders once. */
class Reminders(private val c: AppContainer) {
    private val scheduler = ReminderScheduler(c.context)
    private val mutex = Mutex()

    private suspend fun plan(now: ZonedDateTime): List<ReminderSpec> {
        val minute = c.settings.flow.first().reminderMinuteOfDay
        val notified = c.db.reminderLogDao().allKeys().toSet()
        return ReminderPlanner.subscriptions(c.subscriptions.getAll(), now, minute, notified) +
            ReminderPlanner.loans(c.loans.getLoans(), c.loans.getAdjustments(), c.loans.getPayments(), now, minute, notified) +
            ReminderPlanner.stock(c.stock.getItems(), c.stock.getBatches(), c.stock.getLogs(), now, minute, notified)
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
            if (Notifications.show(c.context, spec)) {
                c.db.reminderLogDao().insert(ReminderLogEntity(key, System.currentTimeMillis()))
                specs = plan(now)
            } else {
                // Notifications are blocked: keep the reminder unshown, and do not re-arm it (no retry loop).
                // The daily check or the next app start tries again.
                specs = specs.filter { it.key != key }
            }
        }
        scheduler.sync(specs)
    }

    fun sendTest() {
        val now = ZonedDateTime.now(c.clock)
        Notifications.show(
            c.context,
            ReminderSpec("TEST:0:${now.toLocalDate()}", ReminderKind.RENEWAL, 0, now.toLocalDate(), now, "Test reminder", "Reminders are working."),
        )
    }
}
