package app.forgetit.txn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.forgetit.ForgetItApp
import kotlinx.coroutines.launch

/** Parses each incoming SMS on the phone. Messages that are not payments are dropped and never stored. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        if (body.isBlank()) return
        val c = (context.applicationContext as ForgetItApp).container
        val pending = goAsync()
        c.appScope.launch {
            try {
                AutoScan.ingest(c, body, "SMS")
            } finally { pending.finish() }
        }
    }
}
