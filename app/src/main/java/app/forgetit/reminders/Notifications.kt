package app.forgetit.reminders

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.forgetit.MainActivity
import app.forgetit.R
import app.forgetit.domain.ReminderKind
import app.forgetit.domain.ReminderSpec

object Notifications {
    const val CH_RENEWALS = "renewals"
    const val CH_TRIALS = "trials"
    const val CH_EMI = "emi"
    const val CH_LOW = "low_stock"
    const val CH_EXPIRY = "expiry"
    const val CH_BILLS = "bills"
    const val CH_DIGEST = "digest"
    const val CH_FOUND = "found"
    const val CH_BUDGET = "budget"
    const val CH_UPDATE = "app_update"

    private val CHANNELS = listOf(
        Triple(CH_RENEWALS, "Renewals", NotificationManager.IMPORTANCE_DEFAULT),
        Triple(CH_TRIALS, "Free trials ending", NotificationManager.IMPORTANCE_HIGH),
        Triple(CH_EMI, "EMI and loan dues", NotificationManager.IMPORTANCE_HIGH),
        Triple(CH_LOW, "Low stock", NotificationManager.IMPORTANCE_DEFAULT),
        Triple(CH_EXPIRY, "Expiry", NotificationManager.IMPORTANCE_HIGH),
        Triple(CH_BILLS, "Bills and utilities", NotificationManager.IMPORTANCE_HIGH),
        Triple(CH_DIGEST, "Weekly summary", NotificationManager.IMPORTANCE_LOW),
        Triple(CH_FOUND, "New payments found", NotificationManager.IMPORTANCE_LOW),
        Triple(CH_BUDGET, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT),
        Triple(CH_UPDATE, "App updates", NotificationManager.IMPORTANCE_LOW),
    )

    val channelIds get() = CHANNELS.map { it.first }

    fun channelFor(kind: ReminderKind) = when (kind) {
        ReminderKind.RENEWAL -> CH_RENEWALS
        ReminderKind.TRIAL_END -> CH_TRIALS
        ReminderKind.EMI_DUE -> CH_EMI
        ReminderKind.LOW_STOCK -> CH_LOW
        ReminderKind.EXPIRY -> CH_EXPIRY
        ReminderKind.BILL_DUE -> CH_BILLS
        ReminderKind.DIGEST -> CH_DIGEST
    }

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        CHANNELS.forEach { (id, name, importance) -> nm.createNotificationChannel(NotificationChannel(id, name, importance)) }
    }

    private fun snoozeAction(context: Context, spec: ReminderSpec, mode: String, label: String, slot: Int): NotificationCompat.Action {
        val intent = Intent(context, SnoozeReceiver::class.java).setAction("app.forgetit.SNOOZE_$mode")
            .putExtra(SnoozeReceiver.EXTRA_KIND, spec.kind.name).putExtra(SnoozeReceiver.EXTRA_TITLE, spec.title)
            .putExtra(SnoozeReceiver.EXTRA_TEXT, spec.text).putExtra(SnoozeReceiver.EXTRA_MODE, mode)
            .putExtra(SnoozeReceiver.EXTRA_NOTIFICATION_ID, spec.key.hashCode())
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(context, spec.key.hashCode() * 7 + slot, intent, flags)
        return NotificationCompat.Action.Builder(0, label, pi).build()
    }

    /** True when the notification was handed to the system; false when notifications are blocked. */
    @SuppressLint("MissingPermission")
    fun show(context: Context, spec: ReminderSpec, paydayDay: Int): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, channelFor(spec.kind))
            .setSmallIcon(R.drawable.ic_stat_forgetit)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.text))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (spec.kind != ReminderKind.DIGEST) {
            builder.addAction(snoozeAction(context, spec, SnoozeReceiver.MODE_DAY, "Snooze 1 day", 1))
            if (paydayDay in 1..31) builder.addAction(snoozeAction(context, spec, SnoozeReceiver.MODE_PAYDAY, "On payday", 2))
        }
        return try {
            nm.notify(spec.key.hashCode(), builder.build())
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** Quiet note after a background scan found new payments to review. */
    fun showFound(context: Context, count: Int) = showInfo(
        context, 7001, "Forget-it scanned your messages",
        if (count == 1) "1 new payment found. Open Transactions to review." else "$count new payments found. Open Transactions to review.",
    )

    @SuppressLint("MissingPermission")
    fun showInfo(context: Context, id: Int, title: String, text: String, channel: String = CH_FOUND) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_stat_forgetit)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).build()
        try { nm.notify(id, n) } catch (_: SecurityException) {}
    }
}
