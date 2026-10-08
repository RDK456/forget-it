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

    private val CHANNELS = listOf(
        Triple(CH_RENEWALS, "Renewals", NotificationManager.IMPORTANCE_DEFAULT),
        Triple(CH_TRIALS, "Free trials ending", NotificationManager.IMPORTANCE_HIGH),
        Triple(CH_EMI, "EMI and loan dues", NotificationManager.IMPORTANCE_HIGH),
        Triple(CH_LOW, "Low stock", NotificationManager.IMPORTANCE_DEFAULT),
        Triple(CH_EXPIRY, "Expiry", NotificationManager.IMPORTANCE_HIGH),
    )

    val channelIds get() = CHANNELS.map { it.first }

    fun channelFor(kind: ReminderKind) = when (kind) {
        ReminderKind.RENEWAL -> CH_RENEWALS
        ReminderKind.TRIAL_END -> CH_TRIALS
        ReminderKind.EMI_DUE -> CH_EMI
        ReminderKind.LOW_STOCK -> CH_LOW
        ReminderKind.EXPIRY -> CH_EXPIRY
    }

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        CHANNELS.forEach { (id, name, importance) -> nm.createNotificationChannel(NotificationChannel(id, name, importance)) }
    }

    @SuppressLint("MissingPermission")
    /** True when the notification was handed to the system; false when notifications are blocked. */
    fun show(context: Context, spec: ReminderSpec): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channelFor(spec.kind))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(spec.key.hashCode(), n)
            return true
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the call; nothing to show.
            return false
        }
    }
}
