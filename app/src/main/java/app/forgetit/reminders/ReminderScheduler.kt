package app.forgetit.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import app.forgetit.domain.ReminderSpec

/** Sets one inexact alarm per reminder. No exact-alarm permission is used on purpose. */
class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private var scheduledKeys = emptySet<String>()

    private fun pendingIntent(key: String): PendingIntent = PendingIntent.getBroadcast(
        context, key.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(ACTION).putExtra(EXTRA_KEY, key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Replace the set of alarms: cancel those no longer planned, (re)set the planned ones. */
    fun sync(specs: List<ReminderSpec>) {
        val wanted = specs.map { it.key }.toSet()
        (scheduledKeys - wanted).forEach { alarms.cancel(pendingIntent(it)) }
        for (s in specs) {
            runCatching {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.triggerAt.toInstant().toEpochMilli(), pendingIntent(s.key))
            }.onFailure { Log.w(TAG, "Could not schedule ${s.key}", it) }
        }
        scheduledKeys = wanted
    }

    companion object {
        const val ACTION = "app.forgetit.REMINDER"
        const val EXTRA_KEY = "key"
        private const val TAG = "Reminders"
    }
}
