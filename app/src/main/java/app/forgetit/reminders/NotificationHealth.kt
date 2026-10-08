package app.forgetit.reminders

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

data class Health(val notificationsAllowed: Boolean, val blockedChannels: List<String>, val batteryOptimized: Boolean) {
    val allGood get() = notificationsAllowed && blockedChannels.isEmpty()
}

object NotificationHealth {
    fun check(context: Context): Health {
        val nm = context.getSystemService(NotificationManager::class.java)
        val blocked = Notifications.channelIds.filter { nm.getNotificationChannel(it)?.importance == NotificationManager.IMPORTANCE_NONE }
        val power = context.getSystemService(PowerManager::class.java)
        return Health(
            notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            blockedChannels = blocked,
            batteryOptimized = !power.isIgnoringBatteryOptimizations(context.packageName),
        )
    }

    fun notificationSettingsIntent(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun batterySettingsIntent() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
