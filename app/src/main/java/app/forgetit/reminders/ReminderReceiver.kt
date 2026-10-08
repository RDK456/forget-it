package app.forgetit.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.forgetit.ForgetItApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Fires when an alarm goes off. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(ReminderScheduler.EXTRA_KEY) ?: return
        val c = (context.applicationContext as ForgetItApp).container
        val pending = goAsync()
        c.appScope.launch { try { c.reminders.fire(key) } finally { pending.finish() } }
    }
}

/** Boot, app update, time change and time-zone change: rebuild every alarm. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as ForgetItApp).container
        val pending = goAsync()
        c.appScope.launch {
            try {
                c.subscriptions.settleTrials(LocalDate.now(c.clock))
                c.reminders.sync()
            } finally { pending.finish() }
        }
    }
}

/** Handles the Snooze buttons on a reminder: show it again tomorrow or on payday. */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = runCatching { app.forgetit.domain.ReminderKind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty()) }.getOrNull() ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_DAY
        androidx.core.app.NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
        val c = (context.applicationContext as ForgetItApp).container
        val pending = goAsync()
        c.appScope.launch {
            try {
                val s = c.settings.flow.first()
                val now = java.time.ZonedDateTime.now(c.clock)
                val time = java.time.LocalTime.of(s.reminderMinuteOfDay / 60, s.reminderMinuteOfDay % 60)
                val payday = mode == MODE_PAYDAY && s.paydayDay in 1..31
                var day = if (payday) app.forgetit.domain.Payday.next(now.toLocalDate(), s.paydayDay) else now.toLocalDate().plusDays(1)
                var at = day.atTime(time).atZone(now.zone)
                if (payday && at.isBefore(now)) {
                    day = app.forgetit.domain.Payday.next(now.toLocalDate().plusDays(1), s.paydayDay)
                    at = day.atTime(time).atZone(now.zone)
                }
                c.reminders.snooze(kind, title, text, at)
            } finally { pending.finish() }
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_MODE = "mode"
        const val EXTRA_NOTIFICATION_ID = "nid"
        const val MODE_DAY = "DAY"
        const val MODE_PAYDAY = "PAYDAY"
    }
}
