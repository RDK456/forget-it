package app.forgetit.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.forgetit.ForgetItApp
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
