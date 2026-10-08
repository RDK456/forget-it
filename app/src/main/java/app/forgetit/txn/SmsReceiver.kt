package app.forgetit.txn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.forgetit.ForgetItApp
import app.forgetit.domain.SmsParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

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
                val currency = c.settings.flow.first().defaultCurrency
                SmsParser.parse(body, LocalDate.now(c.clock), currency)?.let { c.txns.addIfNew(it, "SMS", body) }
            } finally { pending.finish() }
        }
    }
}
