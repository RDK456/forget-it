package app.forgetit.txn

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.forgetit.ForgetItApp
import kotlinx.coroutines.launch

/**
 * Reads new-mail notifications from email apps on this phone (no internet, no account login).
 * Only payment-looking text is kept; everything else is dropped. Needs Notification access, which the user grants in Android settings.
 */
class MailNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in MAIL_APPS) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val x = sbn.notification.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (x.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: x.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (body.isBlank() && title.isBlank()) return
        val c = (applicationContext as ForgetItApp).container
        c.appScope.launch { AutoScan.ingest(c, "$title. $body", "EMAIL", title) }
    }

    companion object {
        val MAIL_APPS = setOf(
            "com.google.android.gm", "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail",
            "ch.protonmail.android", "com.fsck.k9", "com.samsung.android.email.provider",
            "net.thunderbird.android", "me.bluemail.mail", "com.zoho.mail",
        )
    }
}
